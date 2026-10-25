# MiniZ

*by Redstoneskew*

A Paper **26.3** plugin that gives every player a **Mini Me**: a 45%-size copy of themselves, wearing their skin, that follows them around. Customize it with `/miniz`.

It uses the vanilla **Mannequin** entity (a player-shaped mob that can show any skin), shrunk with the `scale` attribute, so players don't need any client mods.

## Building the jar

You need **Java 25** (Paper 26.3 requires it). Gradle downloads it for you if it's missing.

```
./gradlew build          # Windows: gradlew.bat build
```

The jar ends up at `build/libs/MiniZ-1.0.0.jar`. Put it in your server's `plugins/` folder and restart.

**No Java set up?** Let GitHub build it. Every push runs the included workflow (`.github/workflows/build.yml`). Open the repo's **Actions** tab, click the latest run, and download **MiniZ-jar** under *Artifacts*.

## Using it

Type `/miniz` to bring your Mini Me out and open the customize menu. Right-click your own Mini Me to open the menu too. If someone else right-clicks it, it waves at them.

In the menu: **left-click** = next / bigger, **right-click** = previous / smaller.

| Command | What it does |
|---|---|
| `/miniz` | Bring it out + open the menu |
| `/miniz menu` | Just open the menu |
| `/miniz on` / `off` | Bring out / put away |
| `/miniz name <name>` | Colored name, e.g. `<gradient:aqua:light_purple>Tiny Me</gradient>`. `reset` to undo |
| `/miniz nametag on\|off` | Show or hide its name |
| `/miniz mode follow\|stay\|orbit\|shoulder` | Walk after you, wait, circle you, or ride your shoulder |
| `/miniz pose stand\|sneak\|sleep\|swim\|glide` | Resting pose |
| `/miniz trail <hearts\|flames\|souls\|notes\|sparkles\|happy\|clouds\|bubbles\|magic\|none>` | Particle trail |
| `/miniz glow <color>\|off` | Colored glowing outline |
| `/miniz mimic on\|off` | Copies your sneaking, swimming, gliding, sleeping, arm swings and held items |
| `/miniz outfit on\|off` | Wears whatever armor you're wearing |
| `/miniz hand left\|right` | Main hand (also which shoulder it rides) |
| `/miniz emote` | Open the emote picker |
| `/miniz emote <name>\|random\|stop` | Play an emote (see below) |
| `/miniz job farm\|hunt\|both\|mine\|off` | Give it work (see Jobs below) |
| `/miniz mine` | Open the "what should it mine?" menu |
| `/miniz mine <thing> on\|off` / `ores` / `none` / `list` | Pick what the Miner digs up from chat |
| `/miniz mine this` | Dig to the ore you're looking at (or sneak + punch the air) |
| `/miniz gear` | Give it armor and a weapon/tool |
| `/miniz festive on\|off` | Holiday looks on/off (`/miniz festive test <holiday>` for admins) |
| `/miniz upgrades` / `/miniz upgrade health` | Open the Upgrades tab / buy the next health level |
| `/miniz aggro on\|off` | Let mobs attack it (it fights back) |
| `/miniz hunt hostile\|passive on\|off` | Which mobs it fights |
| `/miniz replant on\|off` | Plant seeds back after harvesting |
| `/miniz collect on\|off` | Loot goes into its backpack (or stays on the ground) |
| `/miniz backpack` | Open its inventory (also: sneak + right-click it) |
| `/miniz summon` | Pop it back next to you |
| `/miniz reset` | Everything back to default |
| `/miniz reload` | Reload config (admin) |
| `/miniz clear <player>` | Put away someone's Mini Me (admin) |

## Jobs

Set a job from the menu (**Job** button) or `/miniz job`.

- **Farmer** harvests fully grown wheat, carrots, potatoes, beetroot, nether wart, cocoa, sweet berries, melons and pumpkins (only ones grown from a stem), sugar cane and bamboo (leaving the bottom block to regrow). With **Replant** on, it plants a seed back, from the harvest or from its backpack.
- **Hunter** fights mobs. Toggle **hostile** (zombies, skeletons, creepers...) and **passive** (cows, pigs, sheep...) separately. It never touches tamed pets, leashed or name-tagged mobs, baby animals, villagers, golems, or bosses (the dragon, wither, warden and elder guardian). Kills count as yours, so you get the XP. It hits harder holding a sword or axe; with **Copy me** on it holds whatever you're holding.
- **Farmer + Hunter** farms and drops everything to deal with mobs that come close.
- **Miner** mines what you pick in the **Mining** menu: coal, copper, iron, gold, redstone, lapis, diamond, emerald, nether quartz, nether gold, ancient debris, amethyst clusters, and optionally stone, netherrack, dirt, sand & gravel, clay and obsidian. It goes for ores first. It only mines blocks it can see (an open side), so walk it through caves or a mine. You'll see the cracking animation, it gets the normal drops (Fortune and Silk Touch work if its pickaxe has them) and ore XP. It never digs out the block you're standing on. Without a pickaxe in its Gear it uses a built-in iron one, so ancient debris and obsidian need a diamond pickaxe in its Gear.

**Point it at an ore.** Look at any ore (up to 48 blocks away) and **sneak + punch the air**, with an empty hand or a pickaxe, or type `/miniz mine this`. Your Mini Me says "On it!" and goes for it, even if it's far away or behind solid rock:
- it **digs a 1-block tunnel** straight to it (with the cracking animation),
- mines it, plus the **rest of the vein** touching it,
- then pops back to you and says "Got it!".

It only tunnels through natural blocks (stone, deepslate, dirt, gravel, sand, netherrack, ores…), never through builds, chests or bedrock. It won't dig into lava, never digs out the block you're standing on, and asks claim plugins first. Ores it passes on the way go in its backpack too. Pointing works with any job (or none). The normal Miner job also digs a short tunnel when it can't walk to an ore it found. Distances and limits are under `jobs:` in `config.yml` (`point-range`, `tunnel-max`, `vein-limit`…).

**When the backpack is full** (any job), it walks back to you, says **"I'm full!"** in chat and hands everything over into your inventory. If your inventory is full too, it stays next to you until there's room in its backpack, then says "Thanks! Back to work." In Stay mode it returns to its spot afterwards.

Where it works:
- **Follow mode:** around you (10 blocks by default). If you walk off, it stops and catches up.
- **Stay mode:** around the spot you left it. Park it in your farm and let it work, then it walks back to its spot when there's nothing to do. It only works while that area is loaded.
- Orbit and Shoulder modes pause the job. Picking a job switches you to Follow.

It respects claim/protection plugins (it asks as if you broke the crop yourself) and never tramples farmland.

### Gear tab

The backpack has two tabs along the bottom: **Backpack** and **Gear**. Gear has slots for a helmet, chestplate, leggings, boots, a weapon/tool, and an off-hand item. Shift-click armor from your inventory and it goes into the right slot. Your Mini Me wears it all, and:

- **Armor** protects it when mobs attack.
- **The weapon** is what it fights with (swords and axes hit harder).
- **A pickaxe or shovel** makes it mine faster and lets it mine tougher blocks.
- Gear doesn't wear out, and it beats **Copy me** / **Wear my armor** for any slot that has something in it.

Open it with the Gear button in the menu, the Gear tab in the backpack, or `/miniz gear`.

### Mobs can attack it

With **Mobs can attack it** on (menu button or `/miniz aggro on`, on by default), hostile mobs nearby go after your Mini Me, and any mob it hits turns on it instead of you. It fights back against whatever hits it, even with no job. Its health shows under its name (❤ 18/20) and slowly comes back when it isn't being hit. Only mobs can hurt it: never players, falling, fire or drowning. If it runs out of health it's **knocked out** for 30 seconds, then comes back by itself. Its gear and backpack are never lost. Turn it off and mobs leave it alone again (it can't be hurt).

### Upgrades tab

The third tab is **Upgrades** (also the Upgrades button in the menu, or `/miniz upgrades`). Spend **dragon eggs** to raise your Mini Me's **max health**: **1 dragon egg = 2 health levels**. Each level adds 4 health (2 hearts), up to level 10 (60 health), so maxing out takes **5 dragon eggs**. Buying heals it to full right away. Upgrades are yours for good: they survive restarts and `/miniz reset`. `/miniz upgrade health` buys from chat. Creative-mode players upgrade for free. The item, how many it costs, levels per purchase and health per level are in `config.yml` under `upgrades.health`.

### Holiday looks (automatic)

Your Mini Me dresses up for holidays all year on its own. It goes by the date, so there's nothing to set up. Each holiday has a hat, a particle trail, a symbol before its name and a greeting. **On the holiday itself it brings you a small gift** (once a year per holiday).

| Holiday | When | Look | Gift |
|---|---|---|---|
| New Year | Dec 31 – Jan 1 | Gold party crown, fireworks, ✦ | Fireworks |
| Lunar New Year | ~Jan/Feb (built-in dates) for a week | Red lucky cap, little flames, ✹ | Lucky gold |
| Valentine's Day | Feb 13–14 | Pink cap, hearts, ❤ | Roses + cookies |
| St. Patrick's Day | Mar 16–17 | Green leprechaun hat, green sparkles, ☘ | Pot of gold + emerald |
| Eid al-Fitr | (built-in dates) 3 days | Festive cap, glowing lights, ☪ | Sweets + honey |
| April Fools' Day | Apr 1 | A slime block on its head, ? | A "totally real diamond" |
| Easter | Good Friday – Easter Monday | Bunny ears, egg cracks, ✿ | Eggs + golden carrots |
| Earth Day | Apr 22 | Leaf crown, ♻ | Saplings + bone meal |
| Mother's Day | 2nd Sunday of May | Flower crown, blossoms, ❀ | Flowers + cake |
| Father's Day | 3rd Sunday of June | Dad cap, BBQ smoke, ★ | BBQ steak |
| Independence Day | Jul 3–4 | Blue star cap, fireworks, ★ | Fireworks |
| Diwali | (built-in dates) 5 days | Gold cap, little flames, ✺ | Candles + fireworks |
| Halloween | Oct 24–31 | Pumpkin head, soul fire, ☠ | Candy + pumpkin pie |
| Thanksgiving | 4th Thursday of Nov – Sunday | Pilgrim hat, autumn spores, ✾ | Turkey + pumpkin pie |
| Hanukkah | (built-in dates) 8 days | Blue cap, candle light, ✡ | Chocolate gelt + candles |
| Christmas | Dec 1–26 | Santa hat, snowflakes, ❄ | Cookies, cake, fireworks, snowballs |

When a holiday starts, it waves and greets you ("Merry Christmas, Steve!"). When it ends, the look goes away by itself. If two holidays overlap (like Hanukkah during the Christmas season), the one whose day is closest wins. Holiday hats and trails only show if you haven't given it your own hat (Gear / Wear my armor) or picked a trail.

- `/miniz festive` shows today's holiday. `/miniz festive list` shows every holiday and its next date.
- `/miniz festive on|off`, or the **Holiday looks** button in the menu, turns it on or off for you.
- Admins: `/miniz festive test <holiday>` previews any holiday right now (including its gift). `/miniz festive test off` stops the preview.
- Lunar New Year, Eid, Diwali and Hanukkah change date every year. Their dates are built in for the next few years, and you can add more under `festive.holidays.<name>.dates` in `config.yml`. Mother's Day, Father's Day and Thanksgiving use the US dates. Every holiday can be turned off or lengthened in the config.

### Backpack

Each Mini Me has its own 27-slot inventory (`backpack-rows` in the config). Harvests, mob drops, and items lying around nearby go in there. It never grabs items a player dropped. Open it with the menu's **Backpack** button, `/miniz backpack`, or sneak + right-click your Mini Me, and take things out or put things in like a chest. It's saved on you, so it survives restarts and logouts. When it's full, you get a message and new loot stays on the ground.

All radiuses, damage, speeds and the never-hunt list are in `config.yml` under `jobs:`.

## Emotes

24 of them. Open the picker from the menu's **Emotes** button or `/miniz emote`. Click one to play it (the menu closes so you can watch), or shift-click to keep the menu open.

| | | | |
|---|---|---|---|
| wave | dance | jump | spin |
| love | clap | bow | nod |
| shake (head shake) | cheer | laugh | cry |
| angry | sleepy (nap) | faint | dizzy |
| punch | leap | moonwalk | salute |
| celebrate | magic | think | wiggle |

While an emote plays, a Mini Me in follow mode stops to perform it. If you walk far away it cuts the emote short and catches up. Emotes played for someone who right-clicked your mini are aimed at them.

## language.yml (all text is customizable)

Every bit of text players see is in **`plugins/MiniZ/language.yml`**: chat messages, the `[MiniZ]` tag, what the Mini Me says, every menu title, button and description, the Backpack/Gear/Upgrades tabs, the health tag, and the names of modes, poses, trails, jobs, mine targets, emotes and holidays (including holiday greetings, hats and gift names). That's 430+ lines, grouped by where they show up.

- Colors and styles use [MiniMessage](https://docs.advntr.dev/minimessage/format.html): `<red>`, `<bold>`, `<gradient:gold:yellow>…</gradient>`, even clickable text.
- `{words}` in curly brackets are filled in by the plugin (`{player}`, `{count}`, `{health}`…).
- `\n` makes a new line in menu descriptions.
- Run `/miniz reload` after editing. You don't need a restart.
- When an update adds new text, it's added to your file automatically and your own edits are kept. If you delete a line, it falls back to English and is written back.

To translate the plugin, translate the text in quotes and leave the keys and `{words}` as they are.

(For developers: `tools/gen_language.py` rebuilds the built-in `language.yml` from the source code.)

## Permissions

- `miniz.use` — your own Mini Me (default: everyone)
- `miniz.admin` — `reload` and `clear` (default: op)

## Notes

- **Follow mode works like a leash**, not glued to your camera: it stays where it is until you walk more than a few blocks away, then trots after you. Turn around and it's there, looking up at you.
- Settings are saved on the player, so they survive restarts. No database.
- Minis are never saved into the world. They're removed on logout/shutdown and come back when you rejoin.
- Minis can't be hurt and mobs ignore them.
- Hidden while you're in spectator mode or dead.
- It always wears your own skin (on an offline-mode server that's the default skin).
- Every chat message from the plugin starts with **[MiniZ]**, including your Mini Me talking (`[MiniZ] Mini Steve » I'm full!`), so players can tell which plugin is speaking. Change or remove the tag with `prefix` in `language.yml`.
- Its size (45%), distances, speeds, the default name and trail rate are all in `config.yml`.
