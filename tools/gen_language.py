"""Builds src/main/resources/language.yml from the L.t / L.c / L.item calls in the MiniZ source.

Every call carries its English default, so the generated file always matches the code.
Keys built at runtime (enum names, help rows) are read from their definitions.
"""
import re
import sys
from collections import OrderedDict
from pathlib import Path

root = Path(sys.argv[1])
src = root / "src/main/java/dev/miniz"
out = root / "src/main/resources/language.yml"

STR = r'"((?:[^"\\]|\\.)*)"'          # a Java string literal (captures the inside)


def unjava(s):
    """Decode a Java string literal body."""
    res, i = [], 0
    while i < len(s):
        ch = s[i]
        if ch == "\\" and i + 1 < len(s):
            nxt = s[i + 1]
            res.append({"n": "\n", "t": "\t", '"': '"', "\\": "\\", "'": "'"}.get(nxt, "\\" + nxt))
            i += 2
        else:
            res.append(ch)
            i += 1
    return "".join(res)


keys = OrderedDict()
problems = []


def add(key, default, where):
    if key in keys and keys[key] != default:
        problems.append(f"{key}: different defaults ({where})\n   1: {keys[key]!r}\n   2: {default!r}")
        return
    keys.setdefault(key, default)


# 1) direct calls: L.t("key", "default" ...), L.c(...), L.item(...)
call = re.compile(r'\bL\.(?:t|c|item)\(\s*"([a-z0-9.\-_]+)"\s*,\s*' + STR, re.S)
for path in sorted(src.glob("*.java")):
    text = path.read_text()
    for m in call.finditer(text):
        add(m.group(1), unjava(m.group(2)), path.name)

# 2) enum-driven keys
def enum_entries(text, enum_name):
    """(CONSTANT, [string args...]) for each constant of an enum, in order."""
    start = text.index(f"enum {enum_name}")
    body = text[text.index("{", start) + 1:]
    end = body.index(";")                       # constants end at the first ';'
    body = body[:end]
    entries = []
    for m in re.finditer(r'([A-Z_]+)\s*\((.*?)\)\s*(?:,|$)', body, re.S):
        entries.append((m.group(1), [unjava(x) for x in re.findall(STR, m.group(2))]))
    return entries

settings = (src / "MiniSettings.java").read_text()
for const, s in enum_entries(settings, "Mode"):
    add(f"mode.{const.lower()}.name", s[0], "Mode")
    add(f"mode.{const.lower()}.description", s[1], "Mode")
for const, s in enum_entries(settings, "RestPose"):
    add(f"pose.{const.lower()}", s[0], "RestPose")
for const, s in enum_entries(settings, "Trail"):
    add(f"trail.{const.lower()}", s[0], "Trail")
for const, s in enum_entries(settings, "Job"):
    add(f"job.{const.lower()}.name", s[0], "Job")
    add(f"job.{const.lower()}.description", s[1], "Job")
for const, s in enum_entries(settings, "MineTarget"):
    add(f"mine.{const.lower()}", s[0], "MineTarget")

mime = (src / "MiniMe.java").read_text()
for const, s in enum_entries(mime, "Emote"):
    add(f"emote.{const.lower()}.name", s[0], "Emote")
    add(f"emote.{const.lower()}.description", s[1], "Emote")

festive = (src / "MiniFestive.java").read_text()
start = festive.index("enum Holiday")
body = festive[festive.index("{", start) + 1:]
body = body[:body.index("final String label;")]
for m in re.finditer(r'\n\s+([A-Z_]+)\("', body):
    const = m.group(1)
    if const == "NONE":
        continue
    chunk = body[m.start():]
    chunk = chunk[:chunk.index("),\n") if "),\n" in chunk else chunk.index(");")]
    strs = [unjava(x) for x in re.findall(STR, chunk)]
    # label, prefix, greeting, hat name (+ dates for table holidays, which we skip)
    label, _prefix, greeting, hat = strs[0], strs[1], strs[2], strs[3]
    k = const.lower()
    add(f"holiday.{k}.name", label, "Holiday")
    add(f"holiday.{k}.greeting", greeting, "Holiday")
    add(f"holiday.{k}.hat", hat, "Holiday")

# 3) help rows: {"id", "/command", "description"}
cmd = (src / "MiniCommand.java").read_text()
rows = cmd[cmd.index("String[][] rows = {"):cmd.index("};", cmd.index("String[][] rows = {"))]
for m in re.finditer(r'\{\s*' + STR + r'\s*,\s*' + STR + r'\s*,\s*' + STR + r'\s*\}', rows):
    add(f"help.{unjava(m.group(1))}", unjava(m.group(3)), "help rows")

if problems:
    print("PROBLEMS:\n" + "\n".join(problems))
    sys.exit(1)

# ---------------------------------------------------------------- write YAML
SECTIONS = [
    ("prefix", "The tag in front of every chat line from MiniZ."),
    ("word", "Little words used in many places."),
    ("cmd", "Replies to /miniz commands."),
    ("help", "/miniz help. {command} and {description} are filled in by help.format."),
    ("speech", "Things your Mini Me says in chat. {name} = its name, {message} = what it says."),
    ("health-tag", "The health line under its name (while mobs can attack it)."),
    ("menu", "The /miniz menu, the emote picker and the mining menu."),
    ("tab", "The tab bar at the bottom of the Backpack / Gear / Upgrades screens."),
    ("backpack", "The Backpack tab."),
    ("gear", "The Gear tab."),
    ("upgrades", "The Upgrades tab."),
    ("mode", "Movement modes."),
    ("pose", "Resting poses."),
    ("trail", "Particle trails."),
    ("job", "Jobs."),
    ("mine", "Things the Miner can mine (the Mining menu)."),
    ("emote", "Emotes."),
    ("holiday", "Holidays: their names, greetings, hats and gifts."),
]
order = {name: i for i, (name, _) in enumerate(SECTIONS)}


def yq(v):
    v = v.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n")
    return f'"{v}"'


# build a tree
tree = OrderedDict()
for key in sorted(keys, key=lambda k: (order.get(k.split(".")[0], 99), list(keys).index(k))):
    node = tree
    parts = key.split(".")
    for p in parts[:-1]:
        node = node.setdefault(p, OrderedDict())
        if not isinstance(node, dict):
            raise SystemExit(f"key {key} clashes with a value at {p}")
    if parts[-1] in node and isinstance(node[parts[-1]], dict):
        raise SystemExit(f"key {key} clashes with a section")
    node[parts[-1]] = keys[key]

lines = [
    "# ------------------------------------------------------------",
    "#  MiniZ - language.yml",
    "# ------------------------------------------------------------",
    "# Every bit of text players see. Change anything you like, then run /miniz reload.",
    "#",
    "#  * Colors and styles use MiniMessage: <red>, <gold>, <bold>, <gradient:aqua:light_purple>...</gradient>,",
    "#    <rainbow>...</rainbow>, even <click:run_command:'/miniz'>...</click>.",
    "#    Guide: https://docs.advntr.dev/minimessage/format.html",
    "#  * {words} in curly brackets are filled in by the plugin (a player's name, a number...). Keep them.",
    "#  * \\n starts a new line (used in menu item descriptions).",
    "#  * To show a < character as text, write \\\\< (e.g. /miniz name \\\\<name>).",
    "#  * Missing lines fall back to English and are added back to this file automatically.",
    "",
]
comments = dict(SECTIONS)


YAML_WORDS = {"on", "off", "yes", "no", "true", "false", "y", "n", "null", "~"}


def yk(k):
    """Quote keys YAML would otherwise read as true/false (on, off, yes, no...)."""
    return f'"{k}"' if k.lower() in YAML_WORDS else k


def emit(node, indent):
    for k, v in node.items():
        pad = "  " * indent
        k = yk(k)
        if isinstance(v, dict):
            lines.append(f"{pad}{k}:")
            emit(v, indent + 1)
        else:
            lines.append(f"{pad}{k}: {yq(v)}")


for top, v in tree.items():
    if top in comments:
        lines.append(f"# {comments[top]}")
    if isinstance(v, dict):
        lines.append(f"{top}:")
        emit(v, 1)
    else:
        lines.append(f"{top}: {yq(v)}")
    lines.append("")

out.write_text("\n".join(lines))
print(f"wrote {len(keys)} keys to {out}")
