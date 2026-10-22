#!/usr/bin/env python3
"""Builds monsters.json and equipment.json for the Slayer Companion plugin.

Sources (network is used by this script only, never by the plugin):
  * weirdgloop/osrs-dps-calc  cdn/json/monsters.json + equipment.json  (stats, bonuses, weaknesses)
  * OSRS Wiki Bucket API      dropsline (drop tables), infobox_item (item name -> id)
  * OSRS Wiki page wikitext   equipment skill / quest requirements and "how to get" (parsed from the intro prose)
  * OSRS Wiki Bucket API      recipe (skill level to make an item, for "Made with 70 Crafting")
  * Joshua-F/osrs-dumps       config/dump.obj, the game cache's item configs: GE tradeability and the
                              cache's own wield-requirement params (used when the wiki prose names none)

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
OBJ_DUMP = "https://raw.githubusercontent.com/Joshua-F/osrs-dumps/master/config/dump.obj"
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
_SENT_SKIP = re.compile(r"creat|craft|make|made|enchant|smith|imbu|process|\bkill|drop|obtain|upgrad|combin|"
                        r"train|charg|recharg|purchas|\bbuy|access|mine[sd]?\b|cut\b", re.I)
# Skills that can gate wielding or wearing gear (this excludes crafting, smithing, slayer-to-kill etc.).
EQUIP_SKILLS = {"attack", "strength", "defence", "ranged", "magic", "prayer", "hitpoints",
                "mining", "woodcutting", "agility", "slayer"}

# A run of skills sharing one level: "Ranged and Defence", "Strength, Ranged, and Magic". "or" is not a
# separator, since "70 Attack or Strength" means either one.
_SKILL_LIST = r"((?:%s)(?:(?:\s*,\s*(?:and\s+)?|\s+(?:and|&|as well as)\s+)(?:%s)\b)*)" % (_SKILL_RE, _SKILL_RE)
# Each pattern yields (skills, level) groups, matched against link-free text (see _plain).
_SKILL_PATTERNS = [
    # "Attack and Strength levels of 70", "A Slayer level of 44", "a Hitpoints of 75"
    re.compile(r"\b%s\s+(?:levels?\s+(?:of\s+)?|of\s+)(?:at\s+least\s+)?(\d+)" % _SKILL_LIST),
    # "level 75 Ranged and Defence", "70 Strength, Ranged, and Magic", "level 60 in Attack"
    # (but not "53 Magic experience" or "a +4 Prayer bonus")
    re.compile(r"(?<![+\d])\b(\d+)\s+(?:in\s+(?:both\s+)?)?%s\b(?!\s+(?:experience|xp|bonus)\b)" % _SKILL_LIST),
]
# "... 75 Hitpoints to equip", "... to be wielded": the clause states the item's own requirement.
_WIELD = re.compile(r"\bto\s+(?:be\s+)?(?:equip|wield|wear|worn)\w*", re.I)
_QUEST_RE = re.compile(r"(?:completed?|completion of|finished)\s+(?:the\s+)?(?:quest\s+)?\[\[([^\]|]+)")


def _plain(text):
    """Wiki links reduced to their shown text ("[[Defence|Defence,]]" -> "Defence,"), bold/italic quotes removed."""
    text = re.sub(r"\[\[[^\]|]*\|([^\]]*)\]\]", r"\1", text)
    text = re.sub(r"\[\[([^\]]*)\]\]", r"\1", text)
    return text.replace("'''", "").replace("''", "")


def _mask_names(text):
    """Link targets and capitalised link text blanked out (same length), so skip words inside names
    ("[[Barbarian Training]]", "[[Skill mastery|99]]") are not read as verbs; "[[Enchanted bolts|enchanted]]"
    keeps its verb."""
    def blank(m):
        shown = m.group(2) if m.group(2) is not None else m.group(1)
        keep = "" if shown[:1].isupper() else shown
        return " " * (len(m.group()) - len(keep) - 2) + keep + "  "
    return re.sub(r"\[\[([^\]|]*)(?:\|([^\]]*))?\]\]", blank, text)


def parse_requirements(text):
    """Returns (skillReqs, questReqs) from a page intro's prose."""
    skills, quests = {}, []
    for sent in re.split(r"(?<=[.!?])\s+", text):
        if not _SENT_KEY.search(sent):
            continue
        # Skip per clause, so "obtained from X, requiring 70 Ranged to wield" keeps its own requirement;
        # a "which/requiring ..." clause belongs to the skipped clause before it unless it says wield/wear.
        kept, skipped = [], False
        for clause in re.split(r",\s+", sent):
            masked = _mask_names(clause)
            skips = [m.end() for m in _SENT_SKIP.finditer(masked)]
            wield = _WIELD.search(clause)
            if skips and wield:
                # "an enchanted amulet requiring 75 Hitpoints to equip", "requires 40 Defence to equip and is
                # obtained during ...": keep the span that leads up to the wield verb.
                start = max([e for e in skips if e <= wield.start()], default=0)
                clause, skips = clause[start:wield.end()], []
            skipped = bool(skips) or (
                skipped and re.match(r"(?:which|that|who|requiring)\b", clause) and not _SENT_KEY.search(
                    re.sub(r"requir\w*", "", clause)))
            if not skipped:
                kept.append(clause)
        sent = ", ".join(kept)
        plain = _plain(sent)
        for i, pat in enumerate(_SKILL_PATTERNS):
            for m in pat.finditer(plain):
                names, lvl = (m.group(1), m.group(2)) if i == 0 else (m.group(2), m.group(1))
                lvl = int(lvl)
                for sk in re.findall(_SKILL_RE, names):
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
    body = re.split(r"\n==", body, maxsplit=1)[0]
    # Image captions and switch templates ("Uncharged trident detail.png") are not prose.
    body = re.sub(r"\[\[File:[^\]]*\]\]", "", body)
    body = re.sub(r"\{\{SCP\|(\w+)\|(\d+)[^{}]*\}\}", lambda m: "%s [[%s]]" % (m.group(2), m.group(1).capitalize()),
                  body, flags=re.I)
    prev = None
    while prev != body:
        prev, body = body, re.sub(r"\{\{[^{}]*\}\}", "", body)
    return body


# The cache's requirement params: (skill, level) pairs 434/436 and 435/437. They hold at most two skills, some
# items reuse them for a making skill (crafting, fletching), and older items keep their requirements server-side,
# so they only fill in items whose wiki prose names nothing.
_OBJ_REQ_PARAMS = (("434", "436"), ("435", "437"))


def parse_obj_dump(text):
    """{item id: {"tradeable": bool, "reqs": {skill: level}}} from osrs-dumps' dump.obj."""
    out = {}
    text = text.replace("\r\n", "\n")  # the raw GitHub file has CRLF line endings
    for m in re.finditer(r"^// (\d+)\n\[[^\]\n]+\]\n((?:(?!// \d+\n\[).*\n?)*)", text, re.M):
        body, reqs = m.group(2), {}
        for sk_param, lvl_param in _OBJ_REQ_PARAMS:
            sk = re.search(r"^param=param_%s,(\w+)" % sk_param, body, re.M)
            lvl = re.search(r"^param=param_%s,(\d+)" % lvl_param, body, re.M)
            if sk and lvl and sk.group(1) in EQUIP_SKILLS and 1 < int(lvl.group(1)) <= 99:
                reqs[sk.group(1)] = int(lvl.group(1))
        out[int(m.group(1))] = {"tradeable": bool(re.search(r"^stockmarket=yes", body, re.M)), "reqs": reqs}
    return out


def denied_skills(text):
    """Skills the prose says the item has no requirement in: "these gloves lack a [[Defence]] requirement"."""
    return {s.lower() for s in re.findall(
        r"(?:\bno|\black(?:s)?\s+an?|\bwithout\s+an?)\s+(?:direct\s+)?(%s)\s+requirement" % _SKILL_RE, _plain(text))}


SOURCE_MAX = 40
_SRC_TRIGGER = re.compile(
    r"\b(?P<reward>(?:a|an|the)?\s*(?:rare\s+|possible\s+)?reward\s+(?:for|from|in|of)|awarded|rewarded)\b"
    r"|\b(?P<drop>dropped\s+by)\b"
    r"|\b(?P<buy>purchased|bought|sold)\b"
    r"|\b(?P<make>made\s+(?:by|from|with|using)|created\s+by|crafted|combining|upgraded(?:\s+from)?|obtained\s+by\s+"
    r"(?:using|combining|attaching))\b"
    r"|\b(?P<get>(?:obtained|obtainable|acquired|received|given)(?!\s+as\s+an?\s+(?:\w+\s+)?reward)|available\s+after)\b",
    re.I)
IMBUED_SOURCE = "Imbued (NMZ, Soul Wars or scroll)"
_LINK = re.compile(r"\[\[([^\]|]*)(?:\|([^\]]*))?\]\]([a-z]*)")


def _shown(m):
    return ((m.group(2) if m.group(2) is not None else m.group(1)) + m.group(3)).strip()


def _cap(text):
    text = text[:1].upper() + text[1:]
    if len(text) <= SOURCE_MAX:
        return text
    words = text[:SOURCE_MAX - 1].rsplit(" ", 1)[0].rstrip(",;:").split(" ")
    while words and words[-1].lower() in ("the", "of", "for", "a", "an", "and", "in", "at", "from"):
        words.pop()
    return " ".join(words) + "…"


# Tools and skills named in "made by using a [[hammer]] on ..." are not what the item is made from.
_NOT_MATERIAL = re.compile(r"^(?:hammer|chisel|needle|knife|saw|anvil|furnace|tinderbox|pestle and mortar|"
                           r"glassblowing pipe|%s)$" % _SKILL_RE, re.I)
_FACILITY = re.compile(r"\b(?:shrine|altar|forge|anvil|furnace|bench|table|well|fountain)\b", re.I)


def source_of(intro, made_with=None, title=""):
    """A short "how to get" from a page intro: "TzHaar Fight Cave reward", "Warriors' Guild cyclopes",
    "Bought from Culinaromancer's Chest", "Made with 55 Crafting". made_with is the wiki recipe's
    (level, skill), used for items the prose says are made. None when the intro says nothing usable."""
    if re.search(r"\(e?i\)$", title):
        return IMBUED_SOURCE  # every imbue has the same three routes; the prose lists them at length
    text = re.sub(r"\([^()]*\)", "", intro)  # asides: "(or 104,000 coins after the Elite diary)"
    for sent in re.split(r"(?<=[.!?])\s+", text):
        for t in _SRC_TRIGGER.finditer(sent):
            kind = t.lastgroup
            clause = re.split(r"[.;]|,\s|\s+(?:and|but|which|where|while|although|if|so)\s", sent[t.end():],
                              maxsplit=1)[0]
            links = list(_LINK.finditer(clause))
            proper = [l for l in links if l.group(1)[:1].isupper() and _shown(l)[:1].isupper()]
            if kind == "make":
                if made_with:
                    return _cap("Made with %d %s" % made_with)
                links = [l for l in links if not _NOT_MATERIAL.match(_shown(l))]
                if links and not (t.group().lower().startswith("created") and _shown(links[0])[:1].isupper()):
                    if "upgraded" in t.group().lower():
                        return _cap("Upgraded from " + _shown(links[0]))
                    return _cap(("Made at " if _FACILITY.search(_shown(links[0])) else "Made from ") + _shown(links[0]))
                continue  # "created by [[Dinh]], a smith": who made it, not how to get one
            if kind == "drop":
                if not links:
                    continue
                monster = _shown(links[0])
                place = re.search(r"\b(?:in|of|at)\s+the\s+" + _LINK.pattern, clause[links[0].end():])
                if place:
                    return _cap("%s %s" % (_shown(_LINK.match(place.group()[place.group().index("[["):])), monster))
                return _cap("Dropped by " + monster)
            if not proper:
                continue
            if kind == "buy":
                shop = "Bought from " + _shown(proper[0])
                # "bought from [[Justine's stuff for the Last Shopper Standing]] for 450 [[Last Man Standing]]
                # points": a long shop name gives way to the activity.
                return _cap(shop if len(shop) <= SOURCE_MAX else "Bought from " + _shown(proper[-1]))
            name = _shown(proper[-1] if kind == "reward" else proper[0])
            if quest_enum_name(name) in VALID_QUESTS:
                return _cap(name + " quest")
            return _cap(name + " reward" if kind == "reward" else "From " + name)
    return None


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

    def obj_dump(self):
        with open(os.path.join(self.dir, "dump.obj"), encoding="utf-8") as f:
            return parse_obj_dump(f.read())

    def recipe_skill(self, title):
        return {k: tuple(v) for k, v in self._load("recipes.json").items()}.get(title)

    def wikitext(self, titles):
        pages = self._load("wikitext_pages.json")["query"]["pages"].values()
        wanted = {t.replace("_", " ") for t in titles}
        return {p["title"]: p["revisions"][0]["slots"]["main"]["*"] for p in pages if p["title"] in wanted}


class LiveSource:
    def __init__(self):
        self._last = 0.0

    def _get(self, url, retries=4, parse=json.load):
        for attempt in range(retries):
            wait = 0.15 - (time.time() - self._last)
            if wait > 0:
                time.sleep(wait)
            self._last = time.time()
            try:
                req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
                with urllib.request.urlopen(req, timeout=60) as r:
                    return parse(r)
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

    def obj_dump(self):
        return parse_obj_dump(self._get(OBJ_DUMP, parse=lambda r: r.read().decode("utf-8")))

    def recipe_skill(self, title):
        """(level, Skill) of the hardest skill in the wiki recipe that makes this page's item, or None."""
        esc = title.replace("\\", "\\\\").replace("'", "\\'")
        best = None
        for r in self._bucket("bucket('recipe').select('production_json').where('page_name','%s').limit(20).run()"
                              % esc):
            try:
                prod = json.loads(r.get("production_json") or "{}")
            except ValueError:
                continue
            if prod.get("output", {}).get("name", title) != title:
                continue
            for sk in prod.get("skills", []):
                lvl = _first_int(str(sk.get("level", "")))
                if lvl and lvl > 1 and (best is None or lvl > best[0]):
                    best = (lvl, sk.get("name", "").capitalize())
        return best

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


# Pieces with no offensive bonuses of their own that the plugin's DPS maths still needs for set bonuses.
_SET_PIECES = ("Void knight top", "Void knight robe", "Void knight gloves", "Void melee helm", "Void ranger helm",
               "Void mage helm")


def _useful(e):
    if not e.get("slot") or not e.get("id"):
        return False
    b, o = e.get("bonuses", {}), e.get("offensive", {})
    return (e["slot"] in ("weapon", "2h") or e.get("name", "").startswith(_SET_PIECES)
            or any(v for v in list(b.values()) + list(o.values())))


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

    objs = src.obj_dump()
    intros = {t: intro_of(w) for t, w in pages.items()}

    def reqs_for(title):
        text = intros.get(title)
        return parse_requirements(text) if text else ({}, [])

    sources = {}

    def source_for(title):
        if title not in sources:
            text = intros.get(title)
            got = source_of(text, title=title) if text else None
            if got and got.startswith(("Made from ", "Made at ")):
                made_with = src.recipe_skill(title)
                if made_with:
                    got = source_of(text, made_with, title)
            sources[title] = got
        return sources[title]

    for x in out:
        title = x.pop("_page")
        skills, quests = reqs_for(title)
        skills, quests = dict(skills), list(quests)
        base = _base_title(title)
        kit = _kit_base(base, known)
        parents = ([base] if base != title else []) + ([kit] if kit else [])
        # (or), (a), (bh), (deadman) and Frozen/Volcanic... variants inherit the base item's skills when their
        # own page names none, and always its quests, which variant pages often leave out (e.g. (bh) pages).
        for parent in parents:
            ps, pq = reqs_for(parent)
            if not skills:
                skills = dict(ps)
            quests += [q for q in pq if q not in quests]
        obj = objs.get(x["itemId"], {})
        if not skills:
            # Ornament-kit and newer items often state no requirement in prose; the cache has them.
            denied = set().union(*(denied_skills(intros.get(t, "")) for t in [title] + parents))
            skills = {k: v for k, v in obj.get("reqs", {}).items() if k not in denied}
        x["skillReqs"], x["questReqs"] = skills, quests
        if obj and not obj["tradeable"]:
            source = source_for(title)
            for parent in parents:
                source = source or source_for(parent)
            if source:
                x["source"] = source
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
