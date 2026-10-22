#!/usr/bin/env python3
"""Pick the tile "Take me there" sends to Shortest Path for each location.

A location's `entrance` is the Wiki map pin for its monsters, which often sits in a wall or in a cave
Shortest Path has no way into ("destination can not be reached"). This checks every entrance against
Shortest Path's own collision map and transports and writes a `route` tile where it differs:

1. the entrance itself, if Shortest Path can reach it (no `route` written);
2. else the cave's outside entrance from tools/route_entrances.json (matched by location name prefix),
   nudged to the nearest reachable tile within OVERRIDE_RADIUS;
3. else the nearest reachable tile you can walk to from the pin, trying its own floor first (fixes pins
   in walls or on the wrong floor, without jumping into a separate cave next door);
4. else nothing, with a warning: add the cave's outside entrance to route_entrances.json.

Reachability is generous on purpose: every teleport and shortcut counts, whatever the player's levels or
quests, so a target that fails here fails for everyone.

    git clone --depth 1 https://github.com/Skretzo/shortest-path.git /tmp/shortest-path
    R=src/main/resources/com/slayercompanion
    python3 tools/route_targets.py --shortest-path /tmp/shortest-path/src/main/resources --locations $R/locations.json
"""
import argparse
import glob
import json
import os
import sys
import zipfile
from collections import deque

REGION = 64
FLAGS = 4  # per tile: north open, east open, north wall, east wall
OVERRIDE_RADIUS = 10
PIN_RADIUS = 3  # pins often sit a few tiles inside a wall
AREA_CAP = 20000
START = (3222, 3218, 0)  # Lumbridge


class CollisionMap:
    def __init__(self, regions):
        self.regions = regions  # (regionX, regionY) -> int bitset

    @classmethod
    def from_zip(cls, path):
        regions = {}
        with zipfile.ZipFile(path) as z:
            for name in z.namelist():
                rx, ry = map(int, name.split("_"))
                regions[(rx, ry)] = int.from_bytes(z.read(name), "little")
        return cls(regions)

    def _get(self, x, y, p, flag):
        bits = self.regions.get((x // REGION, y // REGION))
        if bits is None or p < 0:
            return False
        bit = (p * REGION * REGION + (y % REGION) * REGION + (x % REGION)) * FLAGS + flag
        return (bits >> bit) & 1 == 1

    def neighbours(self, x, y, p):
        if self._get(x, y, p, 0):
            yield x, y + 1, p
        if self._get(x, y - 1, p, 0):
            yield x, y - 1, p
        if self._get(x, y, p, 1):
            yield x + 1, y, p
        if self._get(x - 1, y, p, 1):
            yield x - 1, y, p


def _point(text):
    parts = text.split()
    return tuple(map(int, parts)) if len(parts) == 3 else None


def load_transports(transport_dir):
    """Returns (edges origin -> [destinations], teleport destinations reachable from anywhere)."""
    edges, anywhere, networks = {}, set(), {}
    for path in glob.glob(os.path.join(transport_dir, "*.tsv")):
        header = None
        with open(path, encoding="utf-8") as f:
            for line in f:
                if line.startswith("# Origin") or line.startswith("# Destination"):
                    header = line[2:].rstrip("\n").split("\t")
                    continue
                if line.startswith("#") or not line.strip() or header is None:
                    continue
                row = dict(zip(header, line.rstrip("\n").split("\t")))
                origin = _point(row.get("Origin", ""))
                dest = _point(row.get("Destination", ""))
                if origin and dest:
                    edges.setdefault(origin, []).append(dest)
                elif dest:
                    anywhere.add(dest)
                elif origin:
                    # Origin-only rows (fairy rings, spirit trees...) connect to every other stop in the file.
                    networks.setdefault(path, []).append(origin)
    for stops in networks.values():
        for a in stops:
            edges.setdefault(a, []).extend(b for b in stops if b != a)
    return edges, anywhere


def reachable(cmap, edges, anywhere, start=START):
    seen = {start} | set(anywhere)
    queue = deque(seen)
    while queue:
        tile = queue.popleft()
        for nxt in list(cmap.neighbours(*tile)) + edges.get(tile, []):
            if nxt not in seen:
                seen.add(nxt)
                queue.append(nxt)
    return seen


def nearest(tile, seen, radius):
    """Closest reachable tile on any floor, preferring the same floor at equal distance."""
    x, y, p = tile
    best = None
    for r in range(radius + 1):
        for dx in range(-r, r + 1):
            for dy in ((-r, r) if abs(dx) != r else range(-r, r + 1)):
                for plane in range(4):
                    q = (x + dx, y + dy, plane)
                    if q in seen:
                        d = dx * dx + dy * dy + (0 if plane == p else 0.5)
                        if best is None or d < best[0]:
                            best = (d, q)
        if best:
            return best[1]
    return None


def snap(tile, cmap, seen):
    """Nearest reachable tile in the area you can walk to from the pin, on its own floor first, else None."""
    x, y, p = tile
    for plane in [p] + [q for q in range(4) if q != p]:
        starts = [(x + dx, y + dy, plane) for dx in range(-PIN_RADIUS, PIN_RADIUS + 1)
                  for dy in range(-PIN_RADIUS, PIN_RADIUS + 1)]
        area, queue = set(starts), deque(starts)
        while queue and len(area) < AREA_CAP:
            for nxt in cmap.neighbours(*queue.popleft()):
                if nxt not in area:
                    area.add(nxt)
                    queue.append(nxt)
        hits = area & seen
        if hits:
            return min(hits, key=lambda q: ((q[0] - x) ** 2 + (q[1] - y) ** 2, q))
    return None


def _dto(t):
    return {"x": t[0], "y": t[1], "plane": t[2]}


def assign_routes(locations, cmap, seen, entrances):
    """Sets or clears `route` on each location; returns ids left without a reachable target."""
    unreachable = []
    for loc in locations:
        loc.pop("route", None)
        e = loc.get("entrance")
        if not e:
            continue
        tile = (e["x"], e["y"], e["plane"])
        if tile in seen:
            continue
        override = next((v for k, v in entrances.items() if loc["name"].startswith(k)), None)
        if override:
            # Wiki pins often sit on the cave entrance object itself; step to the nearest standable tile.
            o = (override["x"], override["y"], override["plane"])
            loc["route"] = _dto(o if o in seen else nearest(o, seen, OVERRIDE_RADIUS) or o)
            continue
        snapped = snap(tile, cmap, seen)
        if snapped:
            loc["route"] = _dto(snapped)
        else:
            unreachable.append(loc["id"])
    return unreachable


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--shortest-path", required=True, help="Shortest Path's src/main/resources folder")
    ap.add_argument("--locations", required=True)
    ap.add_argument("--entrances", default=os.path.join(os.path.dirname(__file__), "route_entrances.json"))
    args = ap.parse_args()

    cmap = CollisionMap.from_zip(os.path.join(args.shortest_path, "collision-map.zip"))
    edges, anywhere = load_transports(os.path.join(args.shortest_path, "transports"))
    seen = reachable(cmap, edges, anywhere)
    with open(args.entrances, encoding="utf-8") as f:
        entrances = {k: v for k, v in json.load(f).items() if not k.startswith("_")}
    with open(args.locations, encoding="utf-8") as f:
        locations = json.load(f)

    unreachable = assign_routes(locations, cmap, seen, entrances)
    for loc in locations:
        r = loc.get("route")
        if r and (r["x"], r["y"], r["plane"]) not in seen:
            print(f"warning: {loc['id']}: entrance override {r} is not reachable either", file=sys.stderr)
    for loc_id in unreachable:
        print(f"warning: {loc_id}: no reachable tile; add it to route_entrances.json", file=sys.stderr)

    with open(args.locations, "w", encoding="utf-8") as f:
        json.dump(locations, f, indent=1)
        f.write("\n")
    routed = sum(1 for l in locations if "route" in l)
    print(f"{len(locations)} locations: {routed} rerouted, {len(unreachable)} still unreachable")


if __name__ == "__main__":
    main()
