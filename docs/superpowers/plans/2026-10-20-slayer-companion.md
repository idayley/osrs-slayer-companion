# Slayer Companion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A RuneLite side-panel plugin with Prep / Where to go / On task tabs that answers everything a player needs for a slayer task without leaving the game, fully covering Chaeldar's tasks.

**Architecture:** Game data (monsters, equipment, drops) is generated offline from the OSRS Wiki and the Wiki DPS calculator's published data by a Python script and bundled as JSON resources; slayer-task, location and safespot data for Chaeldar is hand-maintained JSON. Pure-Java logic packages (gear scoring, location ranking, session stats, safespot store) are unit-tested without a game client; thin RuneLite-facing classes (plugin, overlays, menu, panel) wire them to game events.

**Tech Stack:** Java 11, Gradle, RuneLite client API (`latest.release`), Lombok, Gson (provided by RuneLite), JUnit 4 + Mockito; Python 3.11 + pytest for `tools/`.

**Spec:** `docs/superpowers/specs/2026-10-20-slayer-companion-design.md` (mockup: `docs/superpowers/specs/2026-10-20-slayer-companion-mockup.html`)

## Global Constraints

- Plugin Hub rules from day one: no automation, no input injection, no disallowed menu-swapping, **no runtime network calls** (all data is bundled; prices come from RuneLite's `ItemManager`).
- Regular (non-Ironman) account: loot and upgrades valued at Grand Exchange prices.
- Estimates are labelled as estimates everywhere they appear (XP/hr, GP/hr, time left).
- Gear recommendations use only items the player owns; upgrades are a separate, labelled line.
- Visual style follows the mockup: RuneLite dark greys (`ColorScheme`), RuneLite orange accent (`ColorScheme.BRAND_ORANGE`).
- Exact user-facing copy (verbatim): "Weak to", "Bring", "Best gear you own", "Highlight these in my bank", "Gear read from your bank {n} min ago", "Open your bank once so I can see your gear", "Recommended", "Take me there", "Shortest Path isn't installed", "No known safespot", "Mark safespot", "Looks right", "Wrong spot", "Send my safespots in", "Food runs out in ~{n} kills", "Starts counting when you get your first kill", "No slayer task".
- Package root: `com.slayercompanion`. Resources under `src/main/resources/com/slayercompanion/`.

## Review Focus

1. **Bank never opened** → Prep shows "Open your bank once so I can see your gear" instead of an empty or wrong setup (test in Task 5).
2. **No task, or task finished mid-session** → panel shows "No slayer task"; On task stats reset when a new task starts (test in Task 6 and Task 8).
3. **Shortest Path not installed** → "Take me there" shows "Shortest Path isn't installed" rather than doing nothing (test in Task 10).
4. **Task from another master with no hand data** (no locations/safespots) → Prep still shows monster data, Where to go shows "No known safespot" and no ranking crash (test in Task 7).
5. **Logout / world hop mid-task** → kill count stays correct (comes from the game's task counter, not counted locally) and the session timer pauses while logged out (test in Task 8).

---

## File Structure

```
build.gradle, settings.gradle, runelite-plugin.properties
tools/build_data.py, tools/test_build_data.py
src/main/resources/com/slayercompanion/
  monsters.json  equipment.json      (generated, Task 2)
  tasks.json  locations.json  safespots.json   (hand-maintained, Task 3)
src/main/java/com/slayercompanion/
  SlayerCompanionPlugin.java  SlayerCompanionConfig.java
  data/      Monster, Equipment, Drop, SlayerTask, Location, Safespot, GameData
  gear/      CombatStats, Loadout, DpsCalculator, OwnedItems, GearOptimizer
  task/      CurrentTask, TaskTracker
  locations/ LocationRanker, RankedLocation, Estimates
  session/   TaskSession
  safespot/  SafespotStore, SafespotOverlay, SafespotMenu, SafespotExport
  integration/ ShortestPath
  ui/        SlayerCompanionPanel, PrepTab, WhereToGoTab, OnTaskTab, BankHighlightOverlay
src/test/java/com/slayercompanion/...   (mirrors main)
src/test/java/com/slayercompanion/SlayerCompanionPluginTest.java  (launches client for manual runs)
```

---

### Task 1: Project scaffold, data model and loader

**Files:**
- Create: `build.gradle`, `settings.gradle`, `runelite-plugin.properties`, `SlayerCompanionPlugin.java`, `SlayerCompanionConfig.java`, `data/*.java`, `src/test/resources/com/slayercompanion/fixture/*.json`
- Test: `src/test/java/com/slayercompanion/data/GameDataTest.java`, `SlayerCompanionPluginTest.java`

**Interfaces:**
- Produces:
  - `Monster { int id; String name; int hitpoints; int slayerXp; int defenceLevel, magicLevel; Map<String,Integer> defensiveBonuses /* stab,slash,crush,magic,light,standard,heavy */; String elementalWeakness /* "water"|"air"|"earth"|"fire"|null */; int elementalWeaknessPercent; int maxHit; List<String> attackStyles; boolean poisonous, venomous; boolean undead, demon, dragon; List<Drop> drops; }`
  - `Drop { int itemId; String name; double rate /* per kill, e.g. 1/128 = 0.0078125 */; int qtyMin, qtyMax; }`
  - `Equipment { int itemId; String name; String slot; Map<String,Integer> bonuses; int speedTicks; String category; Map<String,Integer> skillReqs; List<String> questReqs; boolean twoHanded; }`
  - `SlayerTask { String id; String name; List<Integer> monsterIds; List<String> masters; List<String> requiredItems; List<String> recommendedSupplies; List<String> locationIds; }`
  - `Location { String id; String name; String taskId; int monsterId; WorldPointDto entrance; List<String> tags; List<String> skillReqs; List<String> questReqs; List<String> itemReqs; double overheadSeconds; double killSpeedFactor; double bonusGpPerKill; }`
  - `Safespot { String locationId; int monsterId; WorldPointDto tile; String note; boolean verified; String source /* "builtin"|"player" */; }`
  - `WorldPointDto { int x, y, plane; }` with `WorldPoint toWorldPoint()`
  - `GameData.load(String resourceDir) -> GameData` with `Optional<Monster> monster(int id)`, `Optional<Equipment> equipment(int itemId)`, `Collection<Equipment> allEquipment()`, `Optional<SlayerTask> taskByName(String)`, `List<Location> locations(String taskId)`, `List<Safespot> builtinSafespots()`

- [ ] **Step 1:** Scaffold from the RuneLite example-plugin layout: `build.gradle` with `runeLiteVersion = 'latest.release'`, `compileOnly` client + Lombok, `testImplementation` junit 4.13, mockito-core, client; Java 11. `runelite-plugin.properties`: `displayName=Slayer Companion`, `plugins=com.slayercompanion.SlayerCompanionPlugin`. `SlayerCompanionPluginTest.main` calls `ExternalPluginManager.loadBuiltin(SlayerCompanionPlugin.class); RuneLite.main(args);`.
- [ ] **Step 2: Write the failing test** `GameDataTest.loadsFixture`: load `fixture/`; assert `monster(2075).get().name == "Fire giant"`, `hitpoints == 111`, `elementalWeakness == "water"`; `taskByName("Fire giants").get().locationIds.size() == 2`; `builtinSafespots().get(0).verified == false`. Also `missingResourceFailsLoudly`: loading a dir without `monsters.json` throws `IllegalStateException` naming the file.
- [ ] **Step 3:** Run `./gradlew test --tests '*GameDataTest'` → FAIL (classes missing).
- [ ] **Step 4:** Implement the model classes (Lombok `@Value` or plain fields, Gson-friendly) and `GameData.load` (Gson, reads `monsters.json`, `equipment.json`, `tasks.json`, `locations.json`, `safespots.json` from the classpath dir; indexes by id/name, task name match case-insensitive).
- [ ] **Step 5:** Run `./gradlew test` → PASS; `./gradlew build` succeeds.
- [ ] **Step 6:** Commit `feat: scaffold plugin and game data model`.

### Task 2: Data build script (monsters, equipment, drops)

**Files:**
- Create: `tools/build_data.py`, `tools/test_build_data.py`, `tools/README.md`
- Output: `src/main/resources/com/slayercompanion/monsters.json`, `equipment.json`

**Interfaces:**
- Consumes: JSON shapes of `Monster`, `Drop`, `Equipment` from Task 1 (field names exactly as above).
- Produces: `python3 tools/build_data.py --out src/main/resources/com/slayercompanion` regenerates both files; `--monsters-from tasks.json` limits monsters to those referenced by tasks (keeps the jar small).

Sources: the Wiki DPS calculator's published data (`weirdgloop/osrs-dps-calc`, `cdn/json/monsters.json` and `equipment.json`) for stats, bonuses and weaknesses; the OSRS Wiki Bucket API for drop tables and equipment skill/quest requirements. Network is used only here, never by the plugin.

- [ ] **Step 1: Write failing tests** (pytest, against small saved source samples in `tools/samples/`, no network): `test_fire_giant` — id 2075 → hitpoints 111, slayerXp 111, weakness "water"; `test_whip` — itemId 4151 → slot "weapon", bonuses["slash"] == 82, bonuses["str"] == 82, speedTicks 4, skillReqs["attack"] == 70; `test_drop_rate_parsing` — "1/128" → 0.0078125, "2/128" → 0.015625, "Always" → 1.0; `test_filters_to_task_monsters`.
- [ ] **Step 2:** Run `python3 -m pytest tools -q` → FAIL.
- [ ] **Step 3:** Implement `build_data.py` (stdlib `urllib` + `json`; `--samples` flag reads from `tools/samples/` for tests). Write `tools/README.md`: how and when to regenerate.
- [ ] **Step 4:** Run tests → PASS. Run the real build once and commit the generated JSON.
- [ ] **Step 5:** Commit `feat: wiki data build script and generated data`.

### Task 3: Chaeldar task, location and safespot data

**Files:**
- Create: `src/main/resources/com/slayercompanion/tasks.json`, `locations.json`, `safespots.json`
- Test: `src/test/java/com/slayercompanion/data/BundledDataTest.java`

**Interfaces:**
- Consumes: `GameData` and model classes (Task 1), generated monsters (Task 2).

Fill from the Wiki's Chaeldar task list and each task's location list. For each location: entrance tile, tags (exact tag strings: "Safespot", "Multi-combat", "Single-combat", "Catacombs bonus drops", "Wilderness", "Cannon allowed"), requirements, and `overheadSeconds` (default 4.0), `killSpeedFactor` (1.0, >1 for multi/cannon spots), `bonusGpPerKill` (Catacombs: value of average ancient shard/totem drop per kill at build time). Safespots from Wiki strategy pages and guides, all `verified:false`, `source:"builtin"`, with a short note.

- [ ] **Step 1: Write failing tests:** `everyChaeldarTaskIsCovered` — for every task whose `masters` contains "Chaeldar": all `monsterIds` resolve via `monster()`, `locationIds` non-empty, and each location id resolves; `safespotsReferenceKnownLocations`; `tagsAreFromAllowedSet`; `builtinSafespotsAreUnverified`.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Enter the data.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat: Chaeldar task, location and safespot data`.

### Task 4: DPS calculator

**Files:**
- Create: `gear/CombatStats.java`, `gear/Loadout.java`, `gear/DpsCalculator.java`
- Test: `src/test/java/com/slayercompanion/gear/DpsCalculatorTest.java`

**Interfaces:**
- Produces:
  - `CombatStats { int attack, strength, defence, ranged, magic, hitpoints, prayer, slayer; }`
  - `enum Style { MELEE, RANGED, MAGIC }`
  - `Loadout { Map<String, Equipment> bySlot; Style style; String meleeType /* stab|slash|crush */; String spell /* e.g. "Water Wave", null */; }`
  - `DpsCalculator.maxHit(CombatStats, Loadout, Monster, boolean onTask) -> int`
  - `DpsCalculator.hitChance(CombatStats, Loadout, Monster, boolean onTask) -> double`
  - `DpsCalculator.dps(CombatStats, Loadout, Monster, boolean onTask) -> double`

Follow the Wiki DPS calculator's formulas for melee, ranged and standard spells, assuming accurate/aggressive best style and no prayers or boosts in v1. Must include: slayer helm / black mask on task (melee ×7/6; imbued ranged and magic ×1.15), salve amulet vs undead (overrides slayer helm when better), elemental weakness for spells matching the monster's weakness.

- [ ] **Step 1: Write failing tests:** `whipMaxHit` — 99 str, aggressive, whip only, not on task → 25; `slayerHelmMelee` — same + slayer helmet, on task → 29 (floor(25×7/6)); `slayerHelmIgnoredOffTask` → 25; `hitChanceAttackerHigher` — attack roll 20000, def roll 10000 → 1 − 10002/40002 ≈ 0.74996 (±1e-4); `hitChanceDefenderHigher` — 10000 vs 20000 → 10000/40002 ≈ 0.24999; `waterWaveVsFireGiantGetsWeaknessBonus` — dps with weakness > dps with the same monster's weakness set to null; `dpsFormula` — dps == hitChance × maxHit / 2 / (speedTicks × 0.6) within 1e-9.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat: DPS calculator`.

### Task 5: Owned items and gear optimizer

**Files:**
- Create: `gear/OwnedItems.java`, `gear/GearOptimizer.java`
- Test: `src/test/java/com/slayercompanion/gear/OwnedItemsTest.java`, `GearOptimizerTest.java`

**Interfaces:**
- Consumes: `DpsCalculator`, `Loadout`, `CombatStats` (Task 4); `GameData` (Task 1).
- Produces:
  - `OwnedItems { void updateBank(Map<Integer,Integer>, Instant seen); void updateInventory(Map<Integer,Integer>); void updateWorn(Map<Integer,Integer>); int count(int itemId); boolean bankSeen(); Optional<Instant> bankSeenAt(); String toJson(); static OwnedItems fromJson(String); }` — counts are bank + inventory + worn; noted and placeholder ids canonicalised via a `IntUnaryOperator canonical` passed to the constructor (wired to `ItemManager.canonicalize` in the plugin).
  - `GearOptimizer.best(CombatStats, Style, Monster, boolean onTask, OwnedItems) -> Optional<Loadout>` — empty when `!bankSeen()`.
  - `GearOptimizer.biggestUpgrade(CombatStats, Set<String> questsDone, Loadout current, Monster, boolean onTask) -> Optional<Upgrade>` where `Upgrade { Equipment item; double dpsGainPercent; }` — any price, only items whose `skillReqs` and `questReqs` the player meets, not already owned.

Search: greedy per slot (weapon first, honouring two-handed), repeated until no slot improves (max 3 passes).

- [ ] **Step 1: Write failing tests:** `prefersWhipOverRuneScimitar` vs Fire giant with both owned; `onlyUsesOwnedItems` — returned loadout's items all have `count > 0`; `emptyWhenBankNeverSeen` (Review Focus 1); `upgradeSkipsUnmetRequirements` — 60 Attack player never gets a 70-Attack item suggested; `upgradeIgnoresPrice` — suggests the highest-dps eligible item even if very expensive; `ownedItemsRoundTripJson`; `notedBankItemCountsAsOwned`.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat: owned items and gear optimizer`.

### Task 6: Current task tracking

**Files:**
- Create: `task/CurrentTask.java`, `task/TaskTracker.java`
- Test: `src/test/java/com/slayercompanion/task/TaskTrackerTest.java`

**Interfaces:**
- Consumes: `GameData.taskByName` (Task 1).
- Produces:
  - `CurrentTask { SlayerTask task; int remaining; int initialAmount; Optional<String> konarLocation; String master; int streak; }`
  - `TaskTracker(Client, GameData)`; `Optional<CurrentTask> read()`; `boolean isNewTask(Optional<CurrentTask> previous, Optional<CurrentTask> now)` (true when the task name changes or remaining goes up).

Read the same varps/varbits RuneLite's built-in `net.runelite.client.plugins.slayer.SlayerPlugin` reads for task creature, amount, Konar location and streak; resolve the creature name via the game's slayer task enum as that plugin does. Initial amount: the highest remaining seen for this task (persisted by the plugin).

- [ ] **Step 1: Write failing tests** (mocked `Client`): `readsFireGiantTask` → name "Fire giants", remaining 142; `noTaskWhenAmountZero` → empty (Review Focus 2); `newTaskWhenNameChanges`; `newTaskWhenRemainingIncreases`; `unknownTaskNameStillReturnsTaskWithoutData` → `CurrentTask` with a stub `SlayerTask` carrying just the name (Review Focus 4).
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat: current slayer task tracking`.

### Task 7: Location ranking and XP/GP estimates

**Files:**
- Create: `locations/RankedLocation.java`, `locations/LocationRanker.java`, `locations/Estimates.java`
- Test: `src/test/java/com/slayercompanion/locations/LocationRankerTest.java`, `EstimatesTest.java`

**Interfaces:**
- Consumes: `Location`, `Safespot`, `Monster` (Task 1); `DpsCalculator`, `Loadout` (Task 4); `CurrentTask` (Task 6).
- Produces:
  - `Estimates.killsPerHour(double dps, Monster, Location) -> double` = `3600 / (hitpoints / dps + overheadSeconds) × killSpeedFactor`
  - `Estimates.xpPerHour(double kph, Monster) -> double` = `kph × slayerXp`
  - `Estimates.gpPerKill(Monster, IntToLongFunction price) -> double` = Σ rate × (qtyMin+qtyMax)/2 × price
  - `Estimates.gpPerHour(double kph, double gpPerKill, Location) -> double` = `kph × (gpPerKill + bonusGpPerKill)`
  - `RankedLocation { Location location; boolean usable; String lockedReason; boolean hasSafespot; double travelSeconds; double xpPerHour, gpPerHour; boolean recommended; String why; }`
  - `LocationRanker.rank(List<Location>, Style, WorldPoint player, Predicate<Location> meetsReqs, Optional<String> konarLocation, Function<Location,Boolean> hasSafespot, ToDoubleFunction<Location> xpHr, ToDoubleFunction<Location> gpHr) -> List<RankedLocation>`

Order: usable before locked; then (Ranged/Magic only) safespot first; then travel time ascending. Travel time = Chebyshev tile distance from player to `entrance` × 0.3 s (running), or a fixed 120 s when on a different plane/region. First usable entry gets `recommended=true` and `why` built from its reasons, e.g. "Picked because it has a safespot and is closest." Locked reason is the first unmet requirement, e.g. "Needs boots of stone".

- [ ] **Step 1: Write failing tests:** `lockedLocationsGoLastWithReason`; `konarOnlyAllowsAssignedArea`; `safespotFirstForRanged`; `safespotIgnoredForMelee` — melee orders purely by travel; `recommendedWhyText` == "Picked because it has a safespot and is closest."; `killsPerHourFormula` — hp 111, dps 5.55, overhead 4 → 3600/24 = 150; `gpPerKillUsesAverageQuantity`; `noLocationsReturnsEmptyList` (Review Focus 4).
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat: location ranking and estimates`.

### Task 8: On-task session stats

**Files:**
- Create: `session/TaskSession.java`
- Test: `src/test/java/com/slayercompanion/session/TaskSessionTest.java`

**Interfaces:**
- Consumes: `CurrentTask` (Task 6), `Monster`, `Drop` (Task 1).
- Produces: `TaskSession(Clock)` with
  - `void onTaskUpdate(CurrentTask)` (remaining decrease = kills; new task resets), `void onLoot(int itemId, int qty, long gpEach)`, `void onSuperior()`, `void onLoggedIn()`, `void onLoggedOut()`, `void onFoodCount(int foodInInventory)`
  - Readouts: `int remaining()`, `int total()`, `OptionalDouble secondsPerKill()`, `OptionalDouble killsPerHour()`, `OptionalDouble secondsLeft()`, `OptionalDouble gpPerKill()`, `long lootTotal()`, `double slayerXpPerHour(int xpPerKill)`, `int superiors()`, `Optional<BestDrop> bestDrop()` (`BestDrop { String name; long value; double rate; }`), `OptionalInt killsUntilFoodRunsOut()`, `double levelProgressAtTaskEnd(int currentXp, int xpPerKill)` (fraction 0–1 toward next level), `boolean nextTaskIsBonus(int streak)` (next streak value divisible by 10).

Timing starts at the first kill (copy: "Starts counting when you get your first kill"). Time while logged out is excluded. Food per kill = food eaten since start ÷ kills.

- [ ] **Step 1: Write failing tests** (fixed `Clock`): `killsComeFromGameCounter` — remaining 142→139 → 3 kills; `secondsPerKillAndTimeLeft` — 3 kills over 96 s → 32 s/kill, 139 left → 4448 s; `pausesWhileLoggedOut` (Review Focus 5); `newTaskResets` (Review Focus 2); `bestDropKeepsHighestValueWithRate` — rune scimitar 15000 at 1/128; `foodRunway` — 6 food left, 0.16 eaten/kill → 37; `nextTaskBonusAt49` → true, `at50` → false; `noStatsBeforeFirstKill` → empty optionals.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat: on-task session stats`.

### Task 9: Safespots — store, marking, highlight, export

**Files:**
- Create: `safespot/SafespotStore.java`, `safespot/SafespotExport.java`, `safespot/SafespotMenu.java`, `safespot/SafespotOverlay.java`
- Test: `src/test/java/com/slayercompanion/safespot/SafespotStoreTest.java`, `SafespotExportTest.java`

**Interfaces:**
- Consumes: `Safespot`, `GameData.builtinSafespots()` (Task 1).
- Produces:
  - `SafespotStore(List<Safespot> builtin, String savedJson)`; `List<Safespot> forLocation(String locationId)`; `boolean hasAny(String locationId)`; `void mark(String locationId, int monsterId, WorldPoint, String note)`; `void confirm(WorldPoint)` (builtin → verified); `void reject(WorldPoint)` (hides it); `String toJson()` (player marks + verification decisions only)
  - `SafespotExport.export(SafespotStore) -> String` — JSON array in exactly the `safespots.json` entry format, containing player-marked spots plus builtin spots the player confirmed, excluding unchanged builtin spots.
  - `SafespotMenu` (event subscriber): on shift + right-click of a tile adds "Mark safespot"; on a highlighted tile adds "Looks right" / "Wrong spot". Note entry via `ChatboxPanelManager.openTextInput` (optional, may be blank).
  - `SafespotOverlay`: draws tile outlines for the current location's spots (verified: solid; unverified: dashed).

- [ ] **Step 1: Write failing tests:** `markedSpotIsReturnedForLocation`; `confirmFlipsVerified`; `rejectHidesBuiltin`; `persistsAcrossReload` (toJson → new store); `exportMatchesBundledFormat` — exported array parses with `GameData`'s Safespot parser and round-trips equal; `exportExcludesUntouchedBuiltin`; `exportIncludesConfirmedBuiltin`.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement store and export; implement menu and overlay (manual check in Task 11).
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat: safespot marking, highlight and export`.

### Task 10: Shortest Path integration

**Files:**
- Create: `integration/ShortestPath.java`
- Test: `src/test/java/com/slayercompanion/integration/ShortestPathTest.java`

**Interfaces:**
- Produces: `ShortestPath(EventBus, PluginManager)`; `boolean available()` (a loaded, enabled plugin named "Shortest Path"); `boolean pathTo(WorldPoint)` — posts `new PluginMessage("shortestpath", "path", Map.of("target", point))` and returns true, or returns false without posting when unavailable.

- [ ] **Step 1: Write failing tests** (mocked `EventBus`, `PluginManager`): `postsTargetWhenInstalled` — captures `PluginMessage` with namespace "shortestpath", name "path", data["target"] == point; `doesNothingWhenMissing` → false, no post (Review Focus 3).
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat: Shortest Path integration`.

### Task 11: Side panel and plugin wiring

**Files:**
- Create: `ui/SlayerCompanionPanel.java`, `ui/PrepTab.java`, `ui/WhereToGoTab.java`, `ui/OnTaskTab.java`, `ui/BankHighlightOverlay.java`
- Modify: `SlayerCompanionPlugin.java`, `SlayerCompanionConfig.java`

**Interfaces:**
- Consumes: everything above.
- Produces: the user-visible plugin.

Wiring in the plugin: `@Subscribe` to `VarbitChanged` / `GameTick` (→ `TaskTracker.read`, `TaskSession.onTaskUpdate`), `ItemContainerChanged` (bank / inventory / worn → `OwnedItems`; inventory food count → session), `NpcLootReceived` (→ `onLoot`, priced with `ItemManager.getItemPrice`), `ChatMessage` containing "A superior foe has appeared" (→ `onSuperior`), `GameStateChanged` (login/logout). Persist `OwnedItems`, `SafespotStore` and initial task amount via `ConfigManager.setRSProfileConfiguration`. Panel refreshes on the client thread → `SwingUtilities.invokeLater`, at most once per game tick.

Tabs follow the mockup layout and copy exactly:
- **Prep:** task header; Weak to chips; Bring list with "bank: n" / "missing"; Melee/Ranged/Magic switch → `GearOptimizer.best` slots + "Biggest upgrade: {item} (+{n}% dps)"; "Highlight these in my bank" toggles `BankHighlightOverlay` (outlines those items in the bank); footer "Gear read from your bank {n} min ago" or "Open your bank once so I can see your gear". Multi-variant tasks show the monster of the selected location.
- **Where to go:** ranked rows with tags, XP/hr and GP/hr bars (scaled to the max across rows, labelled "estimated"), "Recommended" + why on the top pick, greyed locked rows with reason, "No known safespot" where none; selecting a row sets the selected location; "Take me there" → `ShortestPath.pathTo(entrance)` or shows "Shortest Path isn't installed"; "Send my safespots in" copies `SafespotExport.export` to the clipboard and confirms "Copied {n} safespots".
- **On task:** big remaining / total + progress bar; 2×3 stat grid (Time left, Per kill, GP per kill, Loot so far, Kills / hr, Slayer XP / hr); Supplies with "Food runs out in ~{n} kills" warning when n < remaining, plus "Prayer {current} / {max}" from the client's boosted/real prayer level; Worth knowing (Superiors this task, Best drop "{name} · {value} {1/x}", Toward lvl {n+1} by task end, Next task bonus).
- No task: every tab shows "No slayer task".

- [ ] **Step 1:** Build the panel and wiring.
- [ ] **Step 2:** `./gradlew test` → all PASS.
- [ ] **Step 3: Manual check** via `SlayerCompanionPluginTest` on a logged-in account with a Chaeldar task: all three tabs populate; switching style changes gear; selecting a location changes Prep for a multi-variant task; "Take me there" draws a route with Shortest Path enabled and shows the message with it disabled; shift-right-click "Mark safespot" highlights the tile and survives relog; "Send my safespots in" puts JSON on the clipboard; kill count and timers update; logout pauses the timer. Screenshot each tab.
- [ ] **Step 4:** Commit `feat: side panel and plugin wiring`.
