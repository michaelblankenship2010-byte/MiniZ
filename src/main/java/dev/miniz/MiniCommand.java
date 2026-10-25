package dev.miniz;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.ParsingException;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** /miniz — bring out and customize your Mini Me. */
public final class MiniCommand implements BasicCommand {

    private static final List<String> SUBCOMMANDS = List.of(
            "menu", "on", "off", "name", "nametag", "mode", "pose", "trail", "glow",
            "mimic", "outfit", "hand", "emote", "job", "hunt", "replant", "collect", "backpack",
            "gear", "mine", "aggro", "upgrades", "upgrade", "festive",
            "summon", "reset", "help");
    private static final List<String> ADMIN = List.of("reload", "clear");

    private final MiniZPlugin plugin;

    MiniCommand(MiniZPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String permission() {
        return "miniz.use";
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);

        // ---- admin commands (console allowed)
        if (sub.equals("reload")) {
            if (!sender.hasPermission("miniz.admin")) { plugin.sayError(sender, L.t("cmd.no-permission", "You can't do that.")); return; }
            plugin.reloadConfig();
            L.load(plugin);
            plugin.festive().reload();
            for (Player p : Bukkit.getOnlinePlayers()) plugin.manager().of(p).applyAll(p);
            plugin.say(sender, L.t("cmd.reloaded", "Config and language.yml reloaded."));
            return;
        }
        if (sub.equals("clear")) {
            if (!sender.hasPermission("miniz.admin")) { plugin.sayError(sender, L.t("cmd.no-permission", "You can't do that.")); return; }
            if (args.length < 2) { plugin.sayError(sender, L.t("cmd.clear.usage", "Usage: /miniz clear \\<player>")); return; }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { plugin.sayError(sender, L.t("cmd.player-offline", "That player isn't online.")); return; }
            MiniMe mini = plugin.manager().of(target);
            mini.putAway();
            mini.settings().save(plugin, target);
            plugin.say(sender, L.t("cmd.clear.done", "Put away {player}'s Mini Me.", "player", target.getName()));
            return;
        }

        if (!(sender instanceof Player player)) {
            plugin.sayError(sender, L.t("cmd.players-only", "Only players have a Mini Me."));
            return;
        }

        MiniMe mini = plugin.manager().of(player);
        MiniSettings s = mini.settings();
        String value = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : null;

        switch (sub) {
            case "" -> {
                mini.spawn(player);
                s.save(plugin, player);
                MiniMenu.open(plugin, player);
                return;
            }
            case "menu" -> { MiniMenu.open(plugin, player); return; }
            case "help" -> { help(player); return; }
            case "on" -> {
                if (mini.isOut()) { plugin.say(player, L.t("cmd.on.already", "Your Mini Me is already out.")); return; }
                if (mini.isKnockedOut()) {
                    s.out = true;
                    s.save(plugin, player);
                    plugin.say(player, L.t("cmd.on.knocked-out", "Your Mini Me is knocked out. It'll be back in {seconds}s.",
                            "seconds", mini.knockedOutSeconds()));
                    return;
                }
                mini.spawn(player);
                plugin.say(player, L.t("cmd.on.done", "Here's your Mini Me!"));
            }
            case "off" -> {
                mini.putAway();
                plugin.say(player, L.t("cmd.off.done", "Mini Me put away. /miniz on to bring it back."));
            }
            case "name" -> {
                if (args.length < 2) { plugin.sayError(player, L.t("cmd.name.usage", "Usage: /miniz name \\<name>  (or reset)")); return; }
                String raw = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                if (raw.equalsIgnoreCase("reset")) {
                    s.name = null;
                } else {
                    int max = plugin.getConfig().getInt("max-name-length", 64);
                    if (raw.length() > max) { plugin.sayError(player, L.t("cmd.name.too-long", "That name is too long (max {max} characters).", "max", max)); return; }
                    Component parsed;
                    try {
                        parsed = plugin.safeMiniMessage().deserialize(raw);
                    } catch (ParsingException e) {
                        plugin.sayError(player, L.t("cmd.name.bad-tags", "Those color tags don't look right."));
                        return;
                    }
                    String visible = PlainTextComponentSerializer.plainText().serialize(parsed);
                    if (visible.isBlank()) { plugin.sayError(player, L.t("cmd.name.empty", "The name can't be empty.")); return; }
                    if (visible.length() > 32) { plugin.sayError(player, L.t("cmd.name.too-many-letters", "Keep the name to 32 letters or fewer.")); return; }
                    s.name = raw;
                }
                mini.applyName(player);
                plugin.say(player, L.t("cmd.name.done", "Name set to {name}", "name", mini.displayName(player)));
            }
            case "nametag" -> {
                Boolean on = onOff(value);
                if (on == null) { plugin.sayError(player, L.t("cmd.nametag.usage", "Usage: /miniz nametag on|off")); return; }
                s.nametag = on;
                mini.applyName(player);
                plugin.say(player, on ? L.t("cmd.nametag.shown", "Name tag shown.") : L.t("cmd.nametag.hidden", "Name tag hidden."));
            }
            case "mode" -> {
                MiniSettings.Mode mode = value == null ? null : MiniSettings.parse(MiniSettings.Mode.class, value, null);
                if (mode == null) { plugin.sayError(player, L.t("cmd.mode.usage", "Usage: /miniz mode follow|stay|orbit|shoulder")); return; }
                s.mode = mode;
                mini.applyMode();
                plugin.say(player, L.t("cmd.mode.done", "Mode: {mode} — {description}.", "mode", L.parse(mode.label()),
                        "description", L.parse(mode.blurb())));
            }
            case "pose" -> {
                MiniSettings.RestPose pose = value == null ? null : MiniSettings.parse(MiniSettings.RestPose.class, value, null);
                if (pose == null) { plugin.sayError(player, L.t("cmd.pose.usage", "Usage: /miniz pose stand|sneak|sleep|swim|glide")); return; }
                s.pose = pose;
                plugin.say(player, L.t("cmd.pose.done", "Resting pose: {pose}.", "pose", L.parse(pose.label())));
            }
            case "trail" -> {
                MiniSettings.Trail trail = value == null ? null : MiniSettings.parse(MiniSettings.Trail.class, value, null);
                if (trail == null) { plugin.sayError(player, L.t("cmd.trail.usage", "Usage: /miniz trail {options}", "options", names(MiniSettings.Trail.values()))); return; }
                s.trail = trail;
                plugin.say(player, L.t("cmd.trail.done", "Trail: {trail}.", "trail", L.parse(trail.label())));
            }
            case "glow" -> {
                if (value == null) { plugin.sayError(player, L.t("cmd.glow.usage", "Usage: /miniz glow \\<color>|off")); return; }
                if (value.equals("off")) {
                    s.glow = null;
                } else {
                    NamedTextColor color = NamedTextColor.NAMES.value(value);
                    if (color == null) { plugin.sayError(player, L.t("cmd.glow.unknown", "Unknown color. Try: red, gold, aqua, light_purple...")); return; }
                    s.glow = color;
                }
                mini.applyGlow();
                plugin.say(player, s.glow == null ? L.t("cmd.glow.off", "Glow off.")
                        : L.t("cmd.glow.done", "Glowing {color}.", "color", value.replace('_', ' ')));
            }
            case "mimic" -> {
                Boolean on = onOff(value);
                if (on == null) { plugin.sayError(player, L.t("cmd.mimic.usage", "Usage: /miniz mimic on|off")); return; }
                s.mimic = on;
                plugin.say(player, on ? L.t("cmd.mimic.on", "It'll copy what you do.") : L.t("cmd.mimic.off", "It'll stop copying you."));
            }
            case "outfit" -> {
                Boolean on = onOff(value);
                if (on == null) { plugin.sayError(player, L.t("cmd.outfit.usage", "Usage: /miniz outfit on|off")); return; }
                s.outfit = on;
                plugin.say(player, on ? L.t("cmd.outfit.on", "It'll wear whatever armor you wear.") : L.t("cmd.outfit.off", "Armor off."));
            }
            case "hand" -> {
                if (!"left".equals(value) && !"right".equals(value)) { plugin.sayError(player, L.t("cmd.hand.usage", "Usage: /miniz hand left|right")); return; }
                s.leftHanded = value.equals("left");
                mini.applyHand();
                plugin.say(player, value.equals("left") ? L.t("cmd.hand.left", "Main hand: left.") : L.t("cmd.hand.right", "Main hand: right."));
            }
            case "emote", "emotes" -> {
                if (value == null) { MiniMenu.openEmotes(plugin, player); return; }
                if (value.equals("stop")) { mini.stopEmote(); return; }
                MiniMe.Emote emote = value.equals("random")
                        ? MiniMe.Emote.random()
                        : MiniSettings.parse(MiniMe.Emote.class, value, null);
                if (emote == null) {
                    plugin.sayError(player, L.t("cmd.emote.unknown", "Unknown emote. Try: {options}|random|stop",
                            "options", names(MiniMe.Emote.values())));
                    return;
                }
                if (!mini.startEmote(emote)) plugin.sayError(player, L.t("cmd.not-out", "Bring your Mini Me out first (/miniz on)."));
                return;
            }
            case "job" -> {
                MiniSettings.Job job = value == null ? null : switch (value) {
                    case "off", "none", "stop" -> MiniSettings.Job.NONE;
                    case "farm", "farmer", "farming" -> MiniSettings.Job.FARM;
                    case "hunt", "hunter", "hunting", "fight" -> MiniSettings.Job.HUNT;
                    case "both", "all" -> MiniSettings.Job.BOTH;
                    case "mine", "miner", "mining", "dig" -> MiniSettings.Job.MINE;
                    default -> null;
                };
                if (job == null) { plugin.sayError(player, L.t("cmd.job.usage", "Usage: /miniz job farm|hunt|both|mine|off")); return; }
                MiniSettings.Mode before = s.mode;
                s.job = job;
                mini.onJobChanged();
                plugin.say(player, job == MiniSettings.Job.NONE
                        ? L.t("cmd.job.off", "Job off. It'll just hang out with you.")
                        : L.t("cmd.job.done", "Job: {job} — {description}.", "job", L.parse(job.label()),
                                "description", L.parse(job.blurb())));
                if (s.mode != before) plugin.say(player, L.t("cmd.job.switched-to-follow", "Switched to Follow mode so it can work."));
                if (job.hunts() && !s.huntHostile && !s.huntPassive) {
                    plugin.say(player, L.t("cmd.job.hunt-tip", "Tip: turn on /miniz hunt hostile on (or passive) so it has something to fight."));
                }
            }
            case "hunt" -> {
                // /miniz hunt hostile on|off   /miniz hunt passive on|off
                Boolean on = args.length >= 3 ? onOff(args[2].toLowerCase(Locale.ROOT)) : null;
                if (value == null || on == null || !(value.equals("hostile") || value.equals("passive"))) {
                    plugin.sayError(player, L.t("cmd.hunt.usage", "Usage: /miniz hunt hostile|passive on|off"));
                    return;
                }
                if (value.equals("hostile")) s.huntHostile = on;
                else s.huntPassive = on;
                mini.onJobChanged();
                String msg;
                if (value.equals("hostile")) {
                    msg = on ? L.t("cmd.hunt.hostile-on", "Hostile mobs: will be hunted.")
                            : L.t("cmd.hunt.hostile-off", "Hostile mobs: left alone.");
                } else {
                    msg = on ? L.t("cmd.hunt.passive-on", "Passive mobs: will be hunted.")
                            : L.t("cmd.hunt.passive-off", "Passive mobs: left alone.");
                }
                plugin.say(player, msg + (s.job.hunts() ? "" : " " + L.t("cmd.hunt.not-hunter", "(Set /miniz job hunt to start hunting.)")));
            }
            case "replant" -> {
                Boolean on = onOff(value);
                if (on == null) { plugin.sayError(player, L.t("cmd.replant.usage", "Usage: /miniz replant on|off")); return; }
                s.replant = on;
                plugin.say(player, on ? L.t("cmd.replant.on", "It'll replant crops after harvesting.") : L.t("cmd.replant.off", "It won't replant."));
            }
            case "collect" -> {
                Boolean on = onOff(value);
                if (on == null) { plugin.sayError(player, L.t("cmd.collect.usage", "Usage: /miniz collect on|off")); return; }
                s.collect = on;
                plugin.say(player, on ? L.t("cmd.collect.on", "Loot goes into its backpack.") : L.t("cmd.collect.off", "Loot stays on the ground."));
            }
            case "festive", "holiday" -> {
                // /miniz festive            -> what's on today
                // /miniz festive on|off     -> your own holiday looks
                // /miniz festive test <holiday>|off  -> admins: preview a holiday now
                if (value != null && value.equals("test")) {
                    if (!player.hasPermission("miniz.admin")) { plugin.sayError(player, L.t("cmd.no-permission", "You can't do that.")); return; }
                    String which = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "";
                    if (which.equals("off") || which.isEmpty()) {
                        plugin.festive().setTestOverride(null);
                        plugin.say(player, L.t("cmd.festive.test-off", "Holiday preview off — back to the real date."));
                    } else {
                        MiniFestive.Holiday h = MiniSettings.parse(MiniFestive.Holiday.class, which, null);
                        if (h == null) {
                            plugin.sayError(player, L.t("cmd.festive.test-unknown", "Holidays: {options}", "options", names(MiniFestive.Holiday.values())));
                            return;
                        }
                        plugin.festive().setTestOverride(h);
                        plugin.say(player, L.t("cmd.festive.test-on", "Previewing {holiday} for everyone. /miniz festive test off to stop.",
                                "holiday", L.parse(h.label())));
                    }
                    for (Player p : Bukkit.getOnlinePlayers()) plugin.manager().of(p).checkHoliday(p);
                    return;
                }
                if (value != null && value.equals("list")) {
                    plugin.say(player, L.t("cmd.festive.list-header", "Holidays (next date):"));
                    for (String line : plugin.festive().describeAll()) {
                        plugin.send(player, L.parse(line).colorIfAbsent(NamedTextColor.GRAY));
                    }
                    return;
                }
                if (value == null) {
                    MiniFestive.Holiday h = plugin.festive().current();
                    plugin.say(player, (h == MiniFestive.Holiday.NONE
                            ? L.t("cmd.festive.none-today", "No holiday today.")
                            : L.t("cmd.festive.today", "It's {holiday}!", "holiday", L.parse(h.label())))
                            + " " + (s.festive
                            ? L.t("cmd.festive.yours-on", "Your festive looks are on (/miniz festive on|off).")
                            : L.t("cmd.festive.yours-off", "Your festive looks are off (/miniz festive on|off).")));
                    return;
                }
                Boolean on = onOff(value);
                if (on == null) { plugin.sayError(player, L.t("cmd.festive.usage", "Usage: /miniz festive on|off")); return; }
                s.festive = on;
                mini.checkHoliday(player);
                plugin.say(player, on ? L.t("cmd.festive.on", "Your Mini Me will dress up for holidays.") : L.t("cmd.festive.off", "No more holiday looks."));
            }
            case "upgrades" -> {
                mini.openUpgrades(player);
                return;
            }
            case "upgrade" -> {
                if (value == null) { mini.openUpgrades(player); return; }
                if (!value.equals("health")) { plugin.sayError(player, L.t("cmd.upgrade.usage", "Usage: /miniz upgrade health")); return; }
                MiniUpgrades.buyHealth(plugin, mini, player);
                return;
            }
            case "gear", "armor", "equipment" -> {
                mini.gear().open(player);
                return;
            }
            case "aggro" -> {
                Boolean on = onOff(value);
                if (on == null) { plugin.sayError(player, L.t("cmd.aggro.usage", "Usage: /miniz aggro on|off")); return; }
                s.aggro = on;
                mini.applyAggro();
                plugin.say(player, on
                        ? L.t("cmd.aggro.on", "Mobs can attack your Mini Me now (it fights back). Give it armor in /miniz gear!")
                        : L.t("cmd.aggro.off", "Mobs will leave your Mini Me alone."));
            }
            case "mine" -> {
                // /miniz mine            -> open the mining menu
                // /miniz mine list
                // /miniz mine <thing> on|off,  /miniz mine ores|none
                if (value == null) { MiniMenu.openMining(plugin, player); return; }
                if (value.equals("this") || value.equals("that") || value.equals("here") || value.equals("target")) {
                    pointAndMine(plugin, mini, player);
                    return;
                }
                if (value.equals("list")) {
                    List<String> names = new ArrayList<>();
                    for (MiniSettings.MineTarget t : s.mine) names.add(t.label());
                    plugin.say(player, names.isEmpty() ? L.t("cmd.mine.list-empty", "It isn't set to mine anything.")
                            : L.t("cmd.mine.list", "Mining: {list}", "list", L.parse(String.join(", ", names))));
                    return;
                }
                if (value.equals("ores")) {
                    s.mine = MiniSettings.MineTarget.ores();
                    plugin.say(player, L.t("cmd.mine.ores", "It'll mine every ore."));
                } else if (value.equals("none")) {
                    s.mine.clear();
                    plugin.say(player, L.t("cmd.mine.none", "It won't mine anything."));
                } else {
                    MiniSettings.MineTarget t = MiniSettings.parse(MiniSettings.MineTarget.class, value, null);
                    Boolean on = args.length >= 3 ? onOff(args[2].toLowerCase(Locale.ROOT)) : null;
                    if (t == null) {
                        plugin.sayError(player, L.t("cmd.mine.unknown", "Unknown thing to mine. Try: {options}",
                                "options", names(MiniSettings.MineTarget.values())));
                        return;
                    }
                    if (on == null) on = !s.mine.contains(t);   // no on/off = toggle
                    if (on) s.mine.add(t); else s.mine.remove(t);
                    plugin.say(player, (on
                            ? L.t("cmd.mine.on", "{thing}: will be mined.", "thing", L.parse(t.label()))
                            : L.t("cmd.mine.off", "{thing}: left alone.", "thing", L.parse(t.label())))
                            + (s.job.mines() ? "" : " " + L.t("cmd.mine.not-miner", "(Set /miniz job mine to start mining.)")));
                }
                mini.onJobChanged();
            }
            case "backpack", "bp", "inv", "inventory" -> {
                mini.backpack().open(player);
                return;
            }
            case "summon" -> {
                mini.summon(player);
                plugin.say(player, L.t("cmd.summon.done", "Poof!"));
            }
            case "reset" -> {
                s.reset(plugin);
                mini.applyAll(player);
                plugin.say(player, L.t("cmd.reset.done", "Mini Me reset to defaults."));
            }
            default -> {
                plugin.sayError(player, L.t("cmd.unknown", "Unknown option. /miniz help for the list."));
                return;
            }
        }
        s.save(plugin, player);
    }

    /** Sends the Mini Me to mine the block the player is looking at (also used by sneak + punch). */
    static void pointAndMine(MiniZPlugin plugin, MiniMe mini, Player player) {
        int range = (int) plugin.getConfig().getDouble("jobs.point-range", 48);
        org.bukkit.block.Block target = player.getTargetBlockExact(range, org.bukkit.FluidCollisionMode.NEVER);
        if (target == null || target.getType().isAir()) {
            plugin.sayError(player, L.t("cmd.mine.this-none", "Look at an ore first (up to {range} blocks away).", "range", range));
            return;
        }
        String problem = mini.mineThis(player, target);
        if (problem != null) plugin.sayError(player, problem);
    }

    private void help(Player player) {
        plugin.say(player, L.t("help.header", "<gold>Customize your Mini Me:"));
        String[][] rows = {
                {"menu", "/miniz", "bring it out + open the menu"},
                {"on-off", "/miniz on|off", "bring out / put away"},
                {"name", "/miniz name \\<name>|reset", "colors work: <gradient:aqua:light_purple>Tiny</gradient>"},
                {"nametag", "/miniz nametag on|off", "show or hide its name"},
                {"mode", "/miniz mode follow|stay|orbit|shoulder", "how it moves"},
                {"pose", "/miniz pose stand|sneak|sleep|swim|glide", "resting pose"},
                {"trail", "/miniz trail \\<type>|none", "particle trail"},
                {"glow", "/miniz glow \\<color>|off", "glowing outline"},
                {"mimic", "/miniz mimic on|off", "copy your moves + held items"},
                {"outfit", "/miniz outfit on|off", "wear your armor"},
                {"hand", "/miniz hand left|right", "main hand"},
                {"emotes", "/miniz emote", "pick from {count} emotes"},
                {"emote", "/miniz emote \\<name>|random|stop", "e.g. dance, moonwalk, faint, magic"},
                {"job", "/miniz job farm|hunt|both|mine|off", "give it work to do"},
                {"hunt", "/miniz hunt hostile|passive on|off", "which mobs it fights"},
                {"replant", "/miniz replant on|off", "plant seeds back after harvesting"},
                {"collect", "/miniz collect on|off", "loot into its backpack"},
                {"backpack", "/miniz backpack", "open its inventory"},
                {"gear", "/miniz gear", "give it armor and a weapon/tool"},
                {"upgrades", "/miniz upgrades", "spend dragon eggs to upgrade its health"},
                {"festive", "/miniz festive on|off|list", "holiday looks (Santa hat at Christmas...)"},
                {"mine", "/miniz mine", "choose what the Miner digs up"},
                {"mine-this", "/miniz mine this", "dig to the ore you're looking at (or sneak + punch the air)"},
                {"aggro", "/miniz aggro on|off", "let mobs attack it (it fights back)"},
                {"summon", "/miniz summon", "pop it back next to you"},
                {"reset", "/miniz reset", "everything back to default"},
        };
        String format = L.t("help.format", "<yellow>{command}</yellow> <gray>— {description}");
        for (String[] row : rows) {
            String description = L.t("help." + row[0], row[2], "count", MiniMe.Emote.values().length);
            plugin.send(player, L.parse(format.replace("{command}", row[1]).replace("{description}", description)));
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length <= 1) {
            List<String> options = new ArrayList<>(SUBCOMMANDS);
            if (sender.hasPermission("miniz.admin")) options.addAll(ADMIN);
            return filter(options, args.length == 0 ? "" : args[0]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("festive") && args[1].equalsIgnoreCase("test")) {
            List<String> holidays = lower(MiniFestive.Holiday.values());
            holidays.remove("none");
            holidays.add("off");
            return filter(holidays, args[2]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("hunt") || args[0].equalsIgnoreCase("mine"))) {
            return filter(List.of("on", "off"), args[2]);
        }
        if (args.length != 2) return List.of();
        String typed = args[1];
        List<String> options = switch (args[0].toLowerCase(Locale.ROOT)) {
            case "name" -> List.of("reset", "<rainbow>Tiny Me</rainbow>", "<gradient:aqua:light_purple>Mini</gradient>");
            case "nametag", "mimic", "outfit", "replant", "collect", "aggro" -> List.of("on", "off");
            case "job" -> List.of("farm", "hunt", "both", "mine", "off");
            case "mine" -> {
                List<String> opts = lower(MiniSettings.MineTarget.values());
                opts.add(0, "this");
                opts.add(1, "list");
                opts.add(2, "ores");
                opts.add(3, "none");
                yield opts;
            }
            case "hunt" -> List.of("hostile", "passive");
            case "clear" -> {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
                yield names;
            }
            case "mode" -> lower(MiniSettings.Mode.values());
            case "pose" -> lower(MiniSettings.RestPose.values());
            case "trail" -> lower(MiniSettings.Trail.values());
            case "glow" -> {
                List<String> colors = new ArrayList<>(NamedTextColor.NAMES.keys());
                colors.add(0, "off");
                yield colors;
            }
            case "hand" -> List.of("left", "right");
            case "upgrade" -> List.of("health");
            case "festive" -> sender.hasPermission("miniz.admin")
                    ? List.of("on", "off", "list", "test") : List.of("on", "off", "list");
            case "emote", "emotes" -> {
                List<String> emotes = lower(MiniMe.Emote.values());
                emotes.add("random");
                emotes.add("stop");
                yield emotes;
            }
            default -> List.of();
        };
        return filter(options, typed);
    }

    private static Boolean onOff(String value) {
        if (value == null) return null;
        return switch (value) {
            case "on", "true", "yes" -> true;
            case "off", "false", "no" -> false;
            default -> null;
        };
    }

    private static List<String> lower(Enum<?>[] values) {
        List<String> out = new ArrayList<>();
        for (Enum<?> v : values) out.add(v.name().toLowerCase(Locale.ROOT));
        return out;
    }

    private static String names(Enum<?>[] values) {
        return String.join("|", lower(values));
    }

    private static List<String> filter(List<String> options, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(t)) out.add(o);
        return out;
    }
}
