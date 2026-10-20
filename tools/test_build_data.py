"""Offline tests: run against tools/samples, never the network."""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import build_data as bd  # noqa: E402

SRC = bd.SampleSource(os.path.join(os.path.dirname(__file__), "samples"))


def monsters():
    return {m["id"]: m for m in bd.build_monsters(SRC)}


def equipment():
    return {e["itemId"]: e for e in bd.build_equipment(SRC)}


def test_fire_giant():
    m = monsters()[2075]
    assert m["hitpoints"] == 111
    assert m["slayerXp"] == 111
    assert m["elementalWeakness"] == "water"
    assert m["elementalWeaknessPercent"] == 100
    assert m["maxHit"] == 11
    assert m["attackStyles"] == ["slash"]
    assert m["defensiveBonuses"]["slash"] == 3
    assert any(d["name"] == "Big bones" and d["itemId"] == 532 and d["rate"] == 1.0 for d in m["drops"])


def test_whip():
    w = equipment()[4151]
    assert w["slot"] == "weapon"
    assert w["bonuses"]["slash"] == 82
    assert w["bonuses"]["str"] == 82
    assert w["speedTicks"] == 4
    assert w["skillReqs"]["attack"] == 70


def test_drop_rate_parsing():
    assert bd.parse_rate("1/128") == 0.0078125
    assert bd.parse_rate("2/128") == 0.015625
    assert bd.parse_rate("Always") == 1.0
    assert bd.parse_rate("~1/1,000") == 0.001
    assert bd.parse_rate("Varies") is None


def test_requirement_parsing():
    s, q = bd.parse_requirements(
        "It can only be wielded by players who have 60 [[Attack]] and have completed the [[Monkey Madness I]] quest.")
    assert s == {"attack": 60}
    assert q == ["MONKEY_MADNESS_I"]
    s, _ = bd.parse_requirements("It requires level 60 [[Defence]] to equip.")
    assert s == {"defence": 60}


def test_filters_to_task_monsters(tmp_path):
    f = tmp_path / "tasks.json"
    f.write_text('[{"id":"fire_giants","name":"Fire giants","monsterIds":[2075]}]')
    ids = bd.task_monster_ids(str(f))
    out = [m["id"] for m in bd.build_monsters(SRC, only_ids=ids)]
    assert out == [2075]


def test_keep_ids_are_emitted_even_if_not_slayer_flagged(monkeypatch):
    raw = SRC.monsters_raw()
    raw = [dict(r, is_slayer_monster=False) if r["id"] == 2075 else r for r in raw]
    monkeypatch.setattr(SRC, "monsters_raw", lambda: raw)
    assert 2075 not in [m["id"] for m in bd.build_monsters(SRC)]
    assert 2075 in [m["id"] for m in bd.build_monsters(SRC, keep_ids={2075})]


def test_location_monster_ids_are_read(tmp_path):
    f = tmp_path / "locations.json"
    f.write_text('[{"id":"a","monsterId":415},{"id":"b","monsterId":2075}]')
    assert bd.task_monster_ids(str(f)) == {415, 2075}


def test_size_cap_drops_unreferenced_drop_tables():
    ms = [{"id": 1, "drops": [{"itemId": 5}] * 50}, {"id": 2, "drops": [{"itemId": 6}] * 50}]
    out = bd.cap_monster_size(ms, keep_ids={1}, limit=10)
    assert len(out[0]["drops"]) == 50 and out[1]["drops"] == []
    small = [{"id": 2, "drops": [{"itemId": 6}]}]
    assert bd.cap_monster_size(small, keep_ids=set(), limit=10_000)[0]["drops"] == [{"itemId": 6}]


def test_poison_and_venom():
    ms = monsters()
    assert ms[2075]["poisonous"] is False and ms[2075]["venomous"] is False
    assert ms[408]["poisonous"] is True  # Cave crawler
    assert ms[957]["poisonous"] is True  # Kalphite Soldier
    assert bd._poison_by_id([{"id": ["1"], "poisonous": "Yes ([[venom]])"}]) == {1: "venom"}


def test_berserker_ring_has_no_reqs():
    r = equipment()[6737]
    assert r["skillReqs"] == {} and r["questReqs"] == []


def test_black_mask_uses_wiki_values():
    r = equipment()[8919]["skillReqs"]
    assert r["strength"] == 20 and r["defence"] == 10


def test_whip_or_inherits_attack_70():
    assert equipment()[26482]["skillReqs"]["attack"] == 70


def test_slayer_is_an_equip_skill():
    skills, _ = bd.parse_requirements("The slayer helmet requires 10 [[Defence]] and 55 [[Slayer]] to wear.")
    assert skills == {"defence": 10, "slayer": 55}


def test_kit_variant_inherits_base_requirements(monkeypatch):
    raw = SRC.equipment_raw()
    whip = next(e for e in raw if e["id"] == 4151)
    raw = raw + [dict(whip, id=12774, name="Frozen abyssal whip", version="")]
    monkeypatch.setattr(SRC, "equipment_raw", lambda: raw)
    eq = {e["itemId"]: e for e in bd.build_equipment(SRC)}
    assert eq[12774]["skillReqs"]["attack"] == 70


def test_kit_base_needs_known_prefix_and_page():
    known = {"Abyssal whip", "Scythe of vitur"}
    assert bd._kit_base("Volcanic abyssal whip", known) == "Abyssal whip"
    assert bd._kit_base("Sanguine scythe of vitur", known) == "Scythe of vitur"
    assert bd._kit_base("Dragon scimitar", known) is None
    assert bd._kit_base("Frozen key", known) is None
