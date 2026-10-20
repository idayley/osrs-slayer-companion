#!/usr/bin/env python3
"""Builds monsters.json and equipment.json for the Slayer Companion plugin.

Sources (network is used by this script only, never by the plugin):
  * weirdgloop/osrs-dps-calc  cdn/json/monsters.json + equipment.json  (stats, bonuses, weaknesses)
  * OSRS Wiki Bucket API      dropsline (drop tables), infobox_item (item name -> id)
  * OSRS Wiki page wikitext   equipment skill / quest requirements (parsed from the intro prose)

Usage:
  python3 tools/build_data.py --out src/main/resources/com/slayercompanion \
      --keep-from src/main/resources/com/slayercompanion/tasks.json \
      --keep-from src/main/resources/com/slayercompanion/locations.json
  python3 tools/build_data.py --samples --out /tmp/x      # offline, reads tools/samples/
"""
import argparse
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

USER_AGENT = "slayer-companion-data-build (RuneLite plugin)"
DPS_BASE = "https://raw.githubusercontent.com/weirdgloop/osrs-dps-calc/main/cdn/json/"
WIKI_API = "https://oldschool.runescape.wiki/api.php"
SAMPLES_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "samples")

SKILLS = ["attack", "strength", "defence", "ranged", "magic", "prayer", "slayer", "hitpoints",
          "mining", "smithing", "woodcutting", "agility", "herblore", "crafting", "fletching",
          "runecraft", "construction", "hunter", "firemaking", "cooking", "fishing", "thieving",
          "farming", "sailing"]


# ---------------------------------------------------------------- parsing helpers

def parse_rate(text):
    """'1/128' -> 0.0078125, 'Always' -> 1.0, unparseable -> None."""
    if text is None:
        return None
    t = str(text).strip().lower().replace(",", "").replace("~", "").replace("≈", "")
    if t == "always":
        return 1.0
    m = re.search(r"(\d+(?:\.\d+)?)\s*/\s*(\d+(?:\.\d+)?)", t)
    if m:
        den = float(m.group(2))
        return float(m.group(1)) / den if den else None
    return None


def _load_quests():
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "quests.txt")
    with open(path, encoding="utf-8") as f:
        return {l.strip() for l in f if l.strip()}


# RuneLite net.runelite.api.Quest constants (regenerate with javap, see README)
VALID_QUESTS = _load_quests()


def quest_enum_name(title):
    s = re.sub(r"[^A-Za-z0-9 ]", "", title.replace("-", " ")).strip()
    return re.sub(r"\s+", "_", s).upper()


_SKILL_RE = "|".join(s.capitalize() for s in SKILLS)
_SENT_KEY = re.compile(r"requir|wield|equip|wear|worn", re.I)
# Sentences about making, obtaining or fighting something are not the item's own equip requirement.
_SENT_SKIP = re.compile(r"creat|craft|make|made|enchant|smith|imbu|process|kill|drop|obtain|upgrad|combin|"
                        r"train|charg|recharg|purchas|\bbuy|access|mine[sd]?\b|cut\b", re.I)
# Skills that can gate wielding or wearing gear (this excludes crafting, smithing, slayer-to-kill etc.).
EQUIP_SKILLS = {"attack", "strength", "defence", "ranged", "magic", "prayer", "hitpoints",
                "mining", "woodcutting", "agility", "slayer"}

_SKILL_PATTERNS = [
    re.compile(r"(?:\[\[)?(%s)(?:\|[^\]]*)?(?:\]\])?\s+levels?\s+(?:of\s+)?(\d+)" % _SKILL_RE),
    re.compile(r"levels?\s+(\d+)\s+(?:\[\[)?(%s)\b" % _SKILL_RE),
    re.compile(r"\b(\d+)\s+(?:\[\[)?(%s)\b" % _SKILL_RE),
    re.compile(r"\{\{SCP\|(%s)\|(\d+)" % _SKILL_RE, re.I),
]
_QUEST_RE = re.compile(r"(?:completed?|completion of|finished)\s+(?:the\s+)?(?:quest\s+)?\[\[([^\]|]+)")


def parse_requirements(text):
    """Returns (skillReqs, questReqs) from a page intro's prose."""
    skills, quests = {}, []
    for sent in re.split(r"(?<=[.!?])\s+", text):
        if not _SENT_KEY.search(sent) or _SENT_SKIP.search(sent):
            continue
        for i, pat in enumerate(_SKILL_PATTERNS):
            for m in pat.finditer(sent):
                a, b = m.group(1), m.group(2)
                if i == 1 or i == 2:
                    lvl, sk = int(a), b
                else:
                    sk, lvl = a, int(b)
                sk = sk.lower()
                if sk in EQUIP_SKILLS and 1 < lvl <= 99:
                    skills[sk] = max(skills.get(sk, 0), lvl)
        for m in _QUEST_RE.finditer(sent):
            q = quest_enum_name(m.group(1))
            if q in VALID_QUESTS and q not in quests:
                quests.append(q)
        if skills or quests:
            break  # the first requirement sentence is the item's own; later ones are asides
    return skills, quests


def intro_of(wikitext):
    """Prose after the Infobox Item template, up to the first heading."""
    body = wikitext
    m = re.search(r"\{\{Infobox Item.*?\n\}\}", body, re.S | re.I)
    if m:
        body = body[m.end():]
    return re.split(r"\n==", body, maxsplit=1)[0]


# ---------------------------------------------------------------- sources

def _poison_by_id(rows):
    """Wiki infobox_monster 'poisonous' text per NPC id: 'Yes (4)' poisons, 'Yes ([[venom]])' venoms."""
    out = {}
    for r in rows:
        val = str(r.get("poisonous") or "")
        if val.lower().startswith("yes"):
            kind = "venom" if "venom" in val.lower() else "poison"
            for i in r.get("id") or []:
                try:
                    out[int(i)] = kind
                except ValueError:
                    pass
    return out


class SampleSource:
    """Reads tools/samples/ so tests run offline."""

    def __init__(self, directory=SAMPLES_DIR):
        self.dir = directory

    def _load(self, name):
        with open(os.path.join(self.dir, name), encoding="utf-8") as f:
            return json.load(f)

    def monsters_raw(self):
        return self._load("monsters.json")

    def equipment_raw(self):
        return self._load("equipment.json")

    def drops(self, page_name):
        path = "drops_%s.json" % page_name.replace(" ", "_")
        return self._load(path) if os.path.exists(os.path.join(self.dir, path)) else []

    def poison_info(self):
        return _poison_by_id(self._load("poison_info.json"))

    def item_ids(self):
        return {r["page_name"]: int(r["item_id"][0]) for r in self._load("item_ids.json") if r.get("item_id")}

    def wikitext(self, titles):
        pages = self._load("wikitext_pages.json")["query"]["pages"].values()
        wanted = {t.replace("_", " ") for t in titles}
        return {p["title"]: p["revisions"][0]["slots"]["main"]["*"] for p in pages if p["title"] in wanted}


class LiveSource:
    def __init__(self):
        self._last = 0.0

    def _get(self, url, retries=4):
        for attempt in range(retries):
            wait = 0.15 - (time.time() - self._last)
            if wait > 0:
                time.sleep(wait)
            self._last = time.time()
            try:
                req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
                with urllib.request.urlopen(req, timeout=60) as r:
                    return json.load(r)
            except Exception as e:  # network hiccup: back off and retry
                if attempt == retries - 1:
                    raise
                print("retry after %s" % e, file=sys.stderr)
                time.sleep(2 * (attempt + 1))

    def _bucket(self, query):
        d = self._get(WIKI_API + "?" + urllib.parse.urlencode({"action": "bucket", "format": "json", "query": query}))
        if "error" in d:
            raise RuntimeError("bucket error: %s" % d["error"])
        return d["bucket"]

    def monsters_raw(self):
        return self._get(DPS_BASE + "monsters.json")

    def equipment_raw(self):
        return self._get(DPS_BASE + "equipment.json")

    def drops(self, page_name):
        esc = page_name.replace("\\", "\\\\").replace("'", "\\'")
        rows, off = [], 0
        while True:
            q = ("bucket('dropsline').select('page_name','item_name','drop_json')"
                 ".where('page_name','%s').limit(500).offset(%d).run()" % (esc, off))
            got = self._bucket(q)
            rows += got
            if len(got) < 500:
                return rows
            off += 500

    def poison_info(self):
        rows, off = [], 0
        while True:
            got = self._bucket("bucket('infobox_monster').select('page_name','id','poisonous')"
                               ".limit(5000).offset(%d).run()" % off)
            rows += got
            if len(got) < 5000:
                return _poison_by_id(rows)
            off += 5000

    def item_ids(self):
        out, off = {}, 0
        while True:
            got = self._bucket("bucket('infobox_item').select('page_name','item_id')"
                               ".limit(5000).offset(%d).run()" % off)
            for r in got:
                if r.get("item_id") and r["page_name"] not in out:
                    try:
                        out[r["page_name"]] = int(r["item_id"][0])
                    except (ValueError, TypeError):
                        pass
            if len(got) < 5000:
                return out
            off += 5000

    def wikitext(self, titles):
        out = {}
        titles = list(titles)
        for i in range(0, len(titles), 50):
            chunk = titles[i:i + 50]
            d = self._get(WIKI_API + "?" + urllib.parse.urlencode({
                "action": "query", "prop": "revisions", "rvprop": "content", "rvslots": "main",
                "format": "json", "redirects": 1, "titles": "|".join(chunk)}))
            q = d.get("query", {})
            back = {}  # redirect target -> requested title
            for r in q.get("redirects", []):
                back[r["to"]] = r["from"]
            for p in q.get("pages", {}).values():
                if "revisions" not in p:
                    continue
                text = p["revisions"][0]["slots"]["main"]["*"]
                out[p["title"]] = text
                if p["title"] in back:
                    out[back[p["title"]]] = text
        return out


# ---------------------------------------------------------------- builders

def _first_int(s):
    m = re.search(r"\d+", str(s or ""))
    return int(m.group()) if m else 0


MONSTERS_MAX_BYTES = 3_000_000


def task_monster_ids(tasks_path):
    """Monster ids referenced by tasks.json ("monsterIds") or locations.json ("monsterId")."""
    with open(tasks_path, encoding="utf-8") as f:
        ids = set()
        for t in json.load(f):
            ids.update(t.get("monsterIds", []))
            if t.get("monsterId") is not None:
                ids.add(t["monsterId"])
        return ids


def cap_monster_size(monsters, keep_ids, limit=MONSTERS_MAX_BYTES):
    """Drops the drop tables of monsters nobody references when the file would exceed `limit` bytes."""
    if len(json.dumps(monsters, separators=(",", ":"), ensure_ascii=False).encode("utf-8")) <= limit:
        return monsters
    for m in monsters:
        if m["id"] not in keep_ids:
            m["drops"] = []
    return monsters


def build_monsters(src, only_ids=None, keep_ids=None):
    """One record per calc monster that is a Slayer monster (slayer XP > 0), plus any in `keep_ids`.

    Slayer XP per kill equals the monster's base hitpoints (Wiki rule), which is
    what the calc's skills.hp holds.
    """
    ids = src.item_ids()
    poison = src.poison_info()
    drop_cache = {}
    out = {}
    for r in src.monsters_raw():
        if only_ids is not None:
            if r["id"] not in only_ids:
                continue
        elif not r.get("is_slayer_monster") and r["id"] not in (keep_ids or ()):
            continue
        hp = int(r.get("skills", {}).get("hp", 0))
        if only_ids is None and hp <= 0 and r["id"] not in (keep_ids or ()):
            continue
        if r["id"] in out:
            continue
        attrs = [a.lower() for a in (r.get("attributes") or [])]
        d = r.get("defensive", {})
        weak = r.get("weakness") or {}
        name = r["name"]
        if name not in drop_cache:
            drop_cache[name] = build_drops(src.drops(name), ids)
        out[r["id"]] = {
            "id": r["id"],
            "name": _display_name(name, r.get("version")),
            "hitpoints": hp,
            "slayerXp": hp,
            "defenceLevel": int(r.get("skills", {}).get("def", 0)),
            "magicLevel": int(r.get("skills", {}).get("magic", 0)),
            "defensiveBonuses": {k: int(d.get(k, 0)) for k in
                                 ("stab", "slash", "crush", "magic", "light", "standard", "heavy")},
            "elementalWeakness": weak.get("element"),
            "elementalWeaknessPercent": int(weak.get("severity", 0)) if weak.get("element") else 0,
            "maxHit": _first_int(r.get("max_hit")),
            "attackStyles": [s.lower() for s in (r.get("style") or []) if s],
            "poisonous": poison.get(r["id"]) == "poison",
            "venomous": poison.get(r["id"]) == "venom",
            "undead": "undead" in attrs,
            "demon": "demon" in attrs,
            "dragon": "dragon" in attrs,
            "drops": drop_cache[name],
        }
    return list(out.values())


def build_drops(rows, item_ids):
    seen, drops = set(), []
    for row in rows:
        try:
            j = json.loads(row["drop_json"])
        except (KeyError, ValueError):
            continue
        rate = parse_rate(j.get("Rarity"))
        name = row.get("item_name") or j.get("Dropped item")
        iid = item_ids.get(name)
        if rate is None or rate <= 0 or not iid:
            continue
        lo = int(j.get("Quantity Low") or _first_int(j.get("Drop Quantity")) or 1)
        hi = int(j.get("Quantity High") or lo)
        key = (iid, rate, lo, hi)
        if key in seen:
            continue
        seen.add(key)
        drops.append({"itemId": iid, "name": name, "rate": rate, "qtyMin": lo, "qtyMax": hi})
    return drops


def _display_name(name, version):
    if not version:
        return name
    return "%s %s" % (name, version) if version.startswith("(") else "%s (%s)" % (name, version)


def _base_title(title):
    """'Abyssal whip (or)' -> 'Abyssal whip'; names without a trailing (...) are returned unchanged."""
    return re.sub(r"\s*\([^)]*\)\s*$", "", title) or title


# Cosmetic-kit variants named with a prefix, e.g. "Frozen abyssal whip" -> "Abyssal whip".
_KIT_PREFIXES = ("Frozen", "Volcanic", "Holy", "Sanguine", "Blood", "Ice", "Smoke", "Shadow", "Twisted")


def _kit_base(title, known):
    """The base item a prefix-kit variant was made from, if that base is a known page; else None."""
    first, _, rest = title.partition(" ")
    if first not in _KIT_PREFIXES or not rest:
        return None
    base = rest[0].upper() + rest[1:]
    return base if base in known else None


def _useful(e):
    if not e.get("slot") or not e.get("id"):
        return False
    b, o = e.get("bonuses", {}), e.get("offensive", {})
    return e["slot"] in ("weapon", "2h") or any(v for v in list(b.values()) + list(o.values()))


def build_equipment(src):
    seen, out = set(), []
    for e in src.equipment_raw():
        if not _useful(e) or e["id"] in seen:
            continue
        seen.add(e["id"])
        b, o = e.get("bonuses", {}), e.get("offensive", {})
        out.append({
            "itemId": e["id"],
            "name": _display_name(e["name"], e.get("version")),
            "_page": e["name"],
            "slot": "weapon" if e["slot"] == "2h" else e["slot"],
            "bonuses": {
                "stab": int(o.get("stab", 0)), "slash": int(o.get("slash", 0)),
                "crush": int(o.get("crush", 0)), "magic": int(o.get("magic", 0)),
                "ranged": int(o.get("ranged", 0)), "str": int(b.get("str", 0)),
                "ranged_str": int(b.get("ranged_str", 0)), "magic_str": int(b.get("magic_str", 0)),
                "prayer": int(b.get("prayer", 0)),
            },
            "speedTicks": int(e.get("speed") or 0),
            "category": e.get("category") or "",
            "skillReqs": {},
            "questReqs": [],
            "twoHanded": bool(e.get("isTwoHanded")) or e["slot"] == "2h",
        })
    known = {x["_page"] for x in out}
    titles = known | {_base_title(t) for t in known}
    titles |= {b for b in (_kit_base(_base_title(t), known) for t in known) if b}
    pages = src.wikitext(sorted(titles))

    def reqs_for(title):
        text = pages.get(title)
        return parse_requirements(intro_of(text)) if text else ({}, [])

    for x in out:
        title = x.pop("_page")
        skills, quests = reqs_for(title)
        base = _base_title(title)
        if not skills and not quests and base != title:  # (or), (a), (bh), (deadman)... inherit the base item
            skills, quests = reqs_for(base)
        kit = _kit_base(base, known)
        if not skills and not quests and kit:  # Frozen/Volcanic abyssal whip... inherit the base item
            skills, quests = reqs_for(kit)
        x["skillReqs"], x["questReqs"] = dict(skills), list(quests)
    return out


# ---------------------------------------------------------------- main

def write_json(path, data):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, separators=(",", ":"), ensure_ascii=False)
        f.write("\n")
    print("wrote %s: %d records, %d bytes" % (path, len(data), os.path.getsize(path)))


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", required=True, help="output directory")
    ap.add_argument("--monsters-from", help="tasks.json: only emit monsters referenced by tasks")
    ap.add_argument("--keep-from", action="append", default=[],
                    help="tasks.json / locations.json: always emit (and keep drops for) monsters they reference")
    ap.add_argument("--samples", action="store_true", help="read tools/samples/ instead of the network")
    a = ap.parse_args(argv)
    src = SampleSource() if a.samples else LiveSource()
    only = task_monster_ids(a.monsters_from) if a.monsters_from else None
    keep = set()
    for path in a.keep_from:
        keep |= task_monster_ids(path)
    os.makedirs(a.out, exist_ok=True)
    write_json(os.path.join(a.out, "monsters.json"), cap_monster_size(build_monsters(src, only, keep), keep))
    write_json(os.path.join(a.out, "equipment.json"), build_equipment(src))


if __name__ == "__main__":
    main()
