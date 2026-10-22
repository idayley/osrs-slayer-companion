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


def test_image_captions_do_not_hide_requirements():
    intro = bd.intro_of("{{Infobox Item\n|x=1\n}}\n{{Synced switch\n"
                        "|version1 = [[File:Uncharged toxic trident detail.png|left|150px]]\n}}\n"
                        "The '''trident of the swamp''' is a one-handed [[powered staff]] requiring 78 [[Magic]] "
                        "to wield. It is a direct upgrade of the [[trident of the seas]], made with level 59 [[Crafting]].")
    assert bd.parse_requirements(intro) == ({"magic": 78}, [])


def test_level_n_in_skill():
    s, _ = bd.parse_requirements("The '''dark bow''' is a two-handed [[ranged weapon]] that requires level 60 in "
                                 "[[Ranged]] to wield.")
    assert s == {"ranged": 60}


def test_obtained_clause_does_not_skip_own_requirement():
    s, _ = bd.parse_requirements("The '''dragon hunter crossbow''' is a one-handed [[crossbow (weapon)|crossbow]] "
                                 "obtained as a possible reward from the [[Chambers of Xeric]], requiring 70 "
                                 "[[Ranged]] to wield.")
    assert s == {"ranged": 70}


def test_which_clause_after_a_skipped_one_stays_skipped():
    s, _ = bd.parse_requirements("It is dropped by the [[Kraken]], which requires 87 [[Slayer]] to kill.")
    assert s == {}


def test_scp_template_survives_intro_cleanup():
    intro = bd.intro_of("Like most [[adamant weapons]], it requires {{SCP|Attack|30|link=yes}} to wield.")
    assert bd.parse_requirements(intro) == ({"attack": 30}, [])


def test_requiring_after_a_making_clause_is_not_an_equip_requirement():
    s, _ = bd.parse_requirements("It is created by casting [[Lvl-6 Enchant]] on an [[onyx bracelet]], requiring "
                                 "level 87 [[Magic]] and granting 97 Magic [[experience]].")
    assert s == {}
    s, _ = bd.parse_requirements("'''Topaz dragon bolts (e)''' are [[topaz dragon bolts]] enchanted with the spell, "
                                 "requiring 29 [[Magic]], 1 [[cosmic rune]] and 2 [[fire rune]]s.")
    assert s == {}


def test_level_of_at_least():
    s, q = bd.parse_requirements("It can only be wielded by players who have an [[Attack]] level of at least 60 and "
                                 "who have completed the [[Heroes' Quest]].")
    assert s == {"attack": 60} and q == ["HEROES_QUEST"]


# ---------------------------------------------------------------- wiki text from real item pages


def test_one_level_shared_by_a_list_of_skills():
    s, _ = bd.parse_requirements("'''Pegasian boots''' are boots that require level 75 [[Ranged]] and [[Defence]] "
                                 "to wear.")
    assert s == {"ranged": 75, "defence": 75}
    s, _ = bd.parse_requirements("They are part of the [[Calamity armour]] set, requiring level 40 [[Defence]] "
                                 "alongside 70 [[Strength]], [[Ranged]], and [[Magic]] to be worn.")
    assert s == {"defence": 40, "strength": 70, "ranged": 70, "magic": 70}


def test_skill_list_level_of():
    s, _ = bd.parse_requirements("You must have an [[Attack]] and [[Thieving]] level of 10 to wield this blackjack.")
    assert s == {"attack": 10}  # Thieving never gates equipment


def test_skill_of_n():
    s, _ = bd.parse_requirements("The '''tormented bracelet''' is an enchanted [[zenyte bracelet]] that requires a "
                                 "[[Hitpoints]] of 75 to equip. This requires level 93 [[Magic]], 20 [[soul rune]]s.")
    assert s == {"hitpoints": 75}


def test_wield_phrase_survives_a_skip_word_in_its_clause():
    s, _ = bd.parse_requirements("The '''amulet of torture''' is an enchanted [[zenyte amulet]] requiring level 75 "
                                 "[[Hitpoints]] to equip.")
    assert s == {"hitpoints": 75}
    s, _ = bd.parse_requirements("The '''elite black full helm''' is a piece of [[elite black armour]] which "
                                 "requires 40 [[Defence]] to equip and is obtained during [[While Guthix Sleeps]].")
    assert s == {"defence": 40}


def test_skip_words_inside_names_are_ignored():
    s, _ = bd.parse_requirements("The '''dragon hasta''' is a one-handed [[melee]] [[weapon]] that requires 60 "
                                 "[[Attack]] and completion of the [[Barbarian Training#Smithing|Smithing]] section "
                                 "of the [[Barbarian Training]] [[miniquest]] to be wielded.")
    assert s == {"attack": 60}
    s, _ = bd.parse_requirements("A '''Prayer cape''' is a [[Cape of Accomplishment]] that can only be worn by "
                                 "players who have achieved a [[Prayer]] level of [[Skill mastery|99]].")
    assert s == {"prayer": 99}


def test_experience_and_bonuses_are_not_requirements():
    s, _ = bd.parse_requirements("Casting the [[Superheat Item|Superheat Item spell]] on a [[gold ore]] while "
                                 "wearing the gauntlets gives 56.2 [[Smithing]] experience and 53 [[Magic]] experience.")
    assert s == {}
    s, _ = bd.parse_requirements("The trimmed cape also provides a +4 [[Equipment Stats#Other bonuses|Prayer bonus]] "
                                 "when worn.")
    assert s == {}


def test_denied_skills():
    assert bd.denied_skills("While these gloves lack a [[Defence]] requirement, they cannot be acquired at 1 "
                            "Defence.") == {"defence"}
    assert bd.denied_skills("While there is no direct [[Defence]] requirement for the heavy ballista") == {"defence"}


def test_obj_dump_reqs_and_tradeability():
    objs = bd.parse_obj_dump("// 4151\n[abyssal_whip]\nstockmarket=yes\nname=Abyssal whip\n"
                             "param=param_434,attack\nparam=param_436,70\n\n"
                             "// 1397\n[air_battlestaff]\nparam=param_434,crafting\nparam=param_436,66\n\n"
                             "// 21018\n[ancestral_hat]\nparam=param_434,magic\nparam=param_436,75\n"
                             "param=param_435,defence\nparam=param_437,65\n")
    assert objs[4151] == {"tradeable": True, "reqs": {"attack": 70}}
    assert objs[1397] == {"tradeable": False, "reqs": {}}  # a making level, not a wield requirement
    assert objs[21018]["reqs"] == {"magic": 75, "defence": 65}
    assert bd.SampleSource().obj_dump()[4151]["reqs"] == {"attack": 70}
    assert bd.parse_obj_dump("// 4151\r\n[abyssal_whip]\r\nstockmarket=yes\r\n")[4151]["tradeable"]


def test_cache_requirements_fill_items_whose_prose_names_none(monkeypatch):
    objs = dict(SRC.obj_dump())
    objs[24539] = {"tradeable": False, "reqs": {"attack": 20}}
    objs[6737] = {"tradeable": True, "reqs": {"defence": 30}}
    monkeypatch.setattr(SRC, "obj_dump", lambda: objs)
    eq = {e["itemId"]: e for e in bd.build_equipment(SRC)}
    assert eq[24539]["skillReqs"] == {"attack": 20}  # no wiki page in the samples
    assert eq[4151]["skillReqs"] == {"attack": 70}


def test_sources_from_real_intros():
    def src(text, **kw):
        return bd.source_of(text, **kw)
    assert src("The '''fire cape''' is a [[melee cape]] obtained as a reward for completing the "
               "[[TzHaar Fight Cave]].") == "TzHaar Fight Cave reward"
    assert src("The '''infernal cape''' is the most powerful melee cape in terms of overall bonuses, awarded for "
               "defeating [[TzKal-Zuk]] in the [[Inferno]].") == "Inferno reward"
    assert src("The rune defender is dropped by [[Cyclops|cyclopes]] on the top floor of the [[Warriors' Guild]] "
               "after showing an [[adamant defender]].") == "Warriors' Guild cyclopes"
    assert src("'''Barrows gloves''' are [[gloves]] that can be purchased for 130,000 [[coins]] (or 104,000 coins "
               "after completing the [[Elite Lumbridge & Draynor Diary]]) from the [[Culinaromancer's Chest]] in the "
               "[[Lumbridge Castle]] cellar.") == "Bought from Culinaromancer's Chest"
    assert src("'''Ava's accumulator''' is an item awarded to the player by completing the quest [[Animal Magnetism]] "
               "if the player is level 50 [[Ranged]] or above.") == "Animal Magnetism quest"
    assert src("'''Dinh's bulwark''' is a two-handed shield created by [[Dinh]], a [[Lovakengj]] smith of "
               "unparalleled skill. It requires 75 [[Attack]] and [[Defence]] to wield and is obtained as a rare "
               "reward from the [[Chambers of Xeric]].") == "Chambers of Xeric reward"
    assert src("The '''Neitiznot faceguard''' is an upgraded [[helm of Neitiznot]] that requires 70 [[Defence]]."
               ) == "Upgraded from helm of Neitiznot"
    helm = ("A '''Slayer helmet''' is a piece of headgear made by combining a [[nose peg]], [[facemask]], "
            "[[earmuffs]], [[spiny helmet]], [[enchanted gem]], and a [[black mask]] together.")
    assert src(helm) == "Made from nose peg"
    assert src(helm, made_with=(55, "Crafting")) == "Made with 55 Crafting"
    assert src("anything", title="Berserker ring (i)") == bd.IMBUED_SOURCE
    assert src("The '''Bandos halo''' is an item worn in the head slot that can be bought from [[Justine's stuff for "
               "the Last Shopper Standing]] for 450 [[Last Man Standing]] points.") == "Bought from Last Man Standing"
    assert src("A plain sentence with nothing about getting it.") is None


def test_sources_are_capped():
    s = bd.source_of("It is a reward from the [[Very Long Named Minigame Of The Ancient Elder Gods]].")
    assert len(s) <= bd.SOURCE_MAX and s.endswith("…")


def test_sources_only_on_untradeables():
    eq = equipment()
    assert "source" not in eq[4151]  # GE-tradeable
    assert all(len(e.get("source") or "") <= bd.SOURCE_MAX for e in eq.values())


def test_void_pieces_are_kept_for_set_bonuses():
    zero = {"str": 0, "ranged_str": 0, "magic_str": 0, "prayer": 0}
    assert bd._useful({"id": 8839, "name": "Void knight top", "slot": "body", "bonuses": zero, "offensive": {}})
    assert bd._useful({"id": 11665, "name": "Void melee helm", "slot": "head", "bonuses": zero, "offensive": {}})
    assert not bd._useful({"id": 1, "name": "Plain hat", "slot": "head", "bonuses": zero, "offensive": {}})
