# Data build

`build_data.py` generates `monsters.json` and `equipment.json` under
`src/main/resources/com/slayercompanion/`. The plugin never touches the network; only this script does.

    R=src/main/resources/com/slayercompanion
    python3 tools/build_data.py --out $R --keep-from $R/tasks.json --keep-from $R/locations.json

Regenerate after an OSRS update that changes monster stats, drop tables or gear. Every slayer-flagged
monster is emitted (so other masters' tasks still resolve by name), plus anything tasks.json/locations.json
reference. If monsters.json would exceed 3 MB, drop tables are removed for monsters those files don't
reference. `--monsters-from tasks.json` still exists to emit only task monsters. Takes ~2.5 minutes.
Commit the output.

Sources: weirdgloop/osrs-dps-calc `cdn/json/{monsters,equipment}.json` (stats, bonuses, weaknesses),
OSRS Wiki Bucket `dropsline` (drops) and `infobox_item` (item name to id), and Wiki page wikitext
(equipment skill and quest requirements, parsed heuristically from the intro prose).

Notes
- Slayer XP = base hitpoints (the Wiki rule); monsters are those flagged `is_slayer_monster` by the calc.
- `poisonous`/`venomous` come from the Wiki `infobox_monster` bucket's `poisonous` text per NPC id ("Yes (4)" = poisons, "Yes ([[venom]])" = venoms).
- Equipment requirements: only wield/wear/equip sentences, only equip-gating skills, creation/kill sentences skipped; `(or)`/`(a)`/`(bh)` variants inherit the base item's requirements.
- Quest names are filtered through `tools/quests.txt` (RuneLite `Quest` enum constants). Refresh with
  `javap -cp runelite-api.jar net.runelite.api.Quest | grep 'public static final'`.
- Tests: `python3 -m pytest tools -q` (offline, uses `tools/samples/`; `--samples` runs the build on them).
