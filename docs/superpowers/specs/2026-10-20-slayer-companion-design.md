# Slayer Companion — RuneLite plugin design

## Why

Getting ready for one slayer task today means jumping between six sources:
a monster-stats site (what it's weak to), the OSRS Wiki (where it lives),
the interactive world map (where that place actually is), ChatGPT (best gear
for my style), the Wiki again (special items, antipoison), and Google (best
location, ideally with a safespot). The Slayer Companion puts all of that,
plus live stats while on task, in one RuneLite side panel.

The user is still learning slayer. The plugin should help them decide, not
decide for them: show the trade-offs plainly so they learn as they play.

## Who it's for

The author first, as a personal plugin. It may later go on the Plugin Hub
if it's good enough, so it must be built to Plugin Hub rules from day one.

## Goals (v1)

A side panel with three tabs. The approved mockup is
`docs/superpowers/specs/2026-10-20-slayer-companion-mockup.html`.

### 1. Prep
- Current task (monster, kills left, slayer master), detected automatically.
- **Weak to:** elemental weakness and %, the weakest attack style, max hit,
  the monster's attack style, and whether it poisons or venoms.
- **Bring:** required slayer items (e.g. facemask, leaf-bladed weapon) and
  recommended supplies (antipoison, prayer pots), each checked against what
  the player owns ("bank: 214" or "missing").
- **Best gear you own:** a Melee / Ranged / Magic switch. For each style,
  the highest-damage setup picked from the player's bank + inventory +
  worn items, plus the single biggest upgrade they don't own yet. The
  upgrade is the best item the player meets the stat and quest
  requirements for, at any price (the author wants to see the real next
  step, even if it's far off).
  "Highlight these in my bank" marks those items in the bank.

### 2. Where to go
- Every location for the task, ranked by default:
  1. Hide what the player can't use (quest-locked, item-locked, or not the
     location Konar assigned). Shown greyed with the reason.
  2. Safespots first when the chosen style is Ranged or Magic.
  3. Then by travel time from where the player is standing.
- The top pick says why it was picked ("has a safespot and is closest").
- Each location shows short tags: Safespot, Multi-combat (cannon),
  Catacombs bonus drops, Wilderness (risky), etc.
- Each location shows **XP/hr and GP/hr as two bars** so the player can
  weigh them and choose differently from the default. Estimated, and
  labelled as estimates.
- **Take me there:** hands the destination to the Shortest Path plugin,
  which draws the route.
- **Safespot tile highlighted in-game** when the player is at that location.
- **Mark your own safespot:** shift-right-click a tile → "Mark safespot".
  Saved per monster and location, highlighted like the built-in ones.
  Marking takes one click and an optional short note ("stand behind the
  rock, ranged only").
- **Send my safespots in:** a button that copies every safespot the player
  has marked, in exactly the format of the built-in safespot file, so the
  author can paste them into the repo and make them official. Already-
  official spots are left out.

### 3. On task
- Kills left out of total, with a progress bar.
- Time left (estimated), time per kill, kills/hr, slayer XP/hr.
- GP per kill (average), loot so far.
- **Supplies runway:** "Food runs out in ~38 kills", with a warning when
  the player won't finish the task on what they're carrying.
- Prayer points.
- Superiors seen this task.
- Best drop this task, with its value and drop rate ("Rune scimitar · 15k ·
  1/128").
- Progress toward the next slayer level by the end of the task.
- Task streak, flagging when the next task gives bonus points (10th, 50th…).
- Starts counting at the first kill of the task.

## Coverage

v1 must fully cover every task **Chaeldar** (the Zanaris slayer master)
assigns — that's who the user is using now. Hand-collected safespot data
starts with those tasks. Other masters' tasks still show everything that
comes from monster data (weakness, gear, locations, live stats); they just
may have no safespot data yet.

## Non-goals (v1)

- Player-contributed safespots shared between players (later; see below).
- A Plugin Hub release (later; but built ready for it).
- Bossing, or slayer bosses' mechanics.
- Picking the best task to skip or block.

## Anti-goals

- Requiring the player to leave the game to look something up for a
  Chaeldar task. That's the whole point.
- Stating estimates as facts. XP/hr and GP/hr are estimates and say so.
- Recommending gear the player doesn't own as their setup. Upgrades are a
  separate, clearly labelled line.
- Anything that would fail Plugin Hub review.

## Later

- **Player-contributed safespots.** Built-in and player-marked safespots
  use the same stored format, so shared ones can be added without a rewrite.
- Plugin Hub release.

## Constraints

- RuneLite plugin, Java, built to Plugin Hub rules (no automation, no
  input injection, no menu-swapping that would be disallowed).
- Shortest Path is a separate plugin the player may not have. If it's
  missing, "Take me there" says so instead of failing silently.
- Regular (non-Ironman) account. Loot and upgrades are valued at Grand
  Exchange prices.
- Gear comes from the bank as last seen; the panel says how old that is
  ("Gear read from your bank 12 min ago") and asks the player to open the
  bank if it's never been seen.

## Decisions I made (builder may revisit with reason)

- **Monster, location and drop data are bundled with the plugin**, built
  from the OSRS Wiki by a script in the repo, rather than fetched live.
  Works offline and avoids Plugin Hub review questions about outside
  sites. Item prices use RuneLite's own price data.
- **Safespot data** is a hand-maintained file in the repo, one entry per
  monster + location with the tile(s) and a short note.
- **Gear scoring** uses the standard OSRS damage formulas (as used by the
  Wiki's DPS calculator), against the task monster's real defences, so the
  same gear can rank differently per task.
- **XP/hr and GP/hr** come from estimated kill speed (damage from the
  player's best gear vs the monster's hitpoints, plus a per-location
  adjustment for things like multi-combat), slayer XP per kill, and the
  drop table priced at current prices plus location bonuses (e.g.
  Catacombs). Close enough to compare locations, not exact.
- **Safespot research:** the builder collects Chaeldar-task safespots from
  the Wiki and guides and enters them as *unverified*. The author confirms
  them in-game; a "Looks right / Wrong spot" option on the highlighted tile
  flips them to verified or removes them. A task counts as covered when all
  of its monster data is present; safespots are included wherever one is
  known, and tasks without one show "No known safespot".
- **Tasks with several monster variants** (kalphites, dagannoth, trolls…):
  the Prep tab shows the variant found at the location selected on
  Where to go (default: the top pick), and updates when the player picks
  a different location. Data is keyed by task + location.
- **Visual style** follows RuneLite's own panels (dark greys, RuneLite
  orange accent), as in the mockup.

## Left to the builder

- How the plugin detects the current task and kill count.
- Exactly how travel time is estimated.
- How gear-scoring edge cases (set effects, special weapons) are handled,
  as long as the common slayer setups rank correctly.
- Code structure, tests, and the wiki data script.
