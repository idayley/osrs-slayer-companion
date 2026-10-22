"""Offline tests for route_targets: a hand-made set of reachable tiles stands in for Shortest Path's map."""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import route_targets as rt  # noqa: E402


def loc(name, x, y, plane=0):
    return {"id": name.lower().replace(" ", "-"), "name": name, "entrance": {"x": x, "y": y, "plane": plane}}


class Corridor:
    """Fake collision map: you can only walk east-west along y=50, between x=40 and x=60."""

    def neighbours(self, x, y, p):
        if y == 50 and p == 0:
            for nx in (x - 1, x + 1):
                if 40 <= nx <= 60:
                    yield nx, y, p


def test_routes():
    seen = {(10, 10, 0), (20, 20, 3), (101, 100, 0), (60, 50, 0), (50, 58, 0)}
    entrances = {"Deep Cave": {"x": 100, "y": 100, "plane": 0}}
    reachable = loc("Field", 10, 10)
    wrong_floor = loc("Tower basement", 20, 20, 0)
    cave = loc("Deep Cave (lower level)", 5000, 9000)
    lost = loc("Nowhere", 4000, 4000)
    in_wall = loc("Corridor", 45, 52)
    left = rt.assign_routes([reachable, wrong_floor, cave, lost, in_wall], Corridor(), seen, entrances)

    assert "route" not in reachable
    assert wrong_floor["route"] == {"x": 20, "y": 20, "plane": 3}
    assert cave["route"] == {"x": 101, "y": 100, "plane": 0}  # nudged off the entrance object
    # (50, 58) is closer but not walkable from the pin: a separate cave next door.
    assert in_wall["route"] == {"x": 60, "y": 50, "plane": 0}
    assert left == ["nowhere"]
