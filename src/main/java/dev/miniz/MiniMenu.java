package dev.miniz;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The /miniz chest menu. Left-click = next / bigger, right-click = previous / smaller. */
public final class MiniMenu implements InventoryHolder {

    // slot layout (5 rows)
    private static final int HEAD = 4;
    private static final int OUT = 10, NAME = 11, NAMETAG = 12, MODE = 13, POSE = 14;
    private static final int TRAIL = 15, GLOW = 16;
    private static final int MIMIC = 19, OUTFIT = 20, HAND = 21, SUMMON = 22, RESET = 23;
    private static final int JOB = 28, HOSTILE = 29, PASSIVE = 30, REPLANT = 31, COLLECT = 32, MINING = 33,
            BACKPACK = 34, GEAR = 35;
    private static final int AGGRO = 37, EMOTES = 38, CLOSE = 40, RANDOM = 42, UPGRADES = 43, FESTIVE = 44;

    // emote page (6 rows): emotes fill the inner 7x4 grid
    private static final int[] EMOTE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};
    private static final int EMOTE_BACK = 45, EMOTE_RANDOM = 49, EMOTE_CLOSE = 53;

    private final MiniZPlugin plugin;
    private final Player player;
    private enum Page { MAIN, EMOTES, MINING }

    private final Page page;
    private final boolean emotePage;
    private final Inventory inventory;

    private MiniMenu(MiniZPlugin plugin, Player player, boolean emotePage) {
        this(plugin, player, emotePage ? Page.EMOTES : Page.MAIN);
    }

    private MiniMenu(MiniZPlugin plugin, Player player, Page page) {
        this.plugin = plugin;
        this.player = player;
        this.page = page;
        this.emotePage = page == Page.EMOTES;
        this.inventory = page == Page.MINING
                ? Bukkit.createInventory(this, 54, L.c("menu.mining.title", "<dark_purple>What should it mine?"))
                : emotePage
                ? Bukkit.createInventory(this, 54, L.c("menu.emotes.title", "<dark_purple>Mini Me emotes"))
                : Bukkit.createInventory(this, 45, L.c("menu.main.title", "<dark_purple>Customize your Mini Me"));
        if (page == Page.MINING) renderMining();
        else if (emotePage) renderEmotes();
        else render();
    }

    public static void open(MiniZPlugin plugin, Player player) {
        player.openInventory(new MiniMenu(plugin, player, false).getInventory());
    }

    public static void openMining(MiniZPlugin plugin, Player player) {
        player.openInventory(new MiniMenu(plugin, player, Page.MINING).getInventory());
    }

    public static void openEmotes(MiniZPlugin plugin, Player player) {
        player.openInventory(new MiniMenu(plugin, player, true).getInventory());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // ================================================================== drawing

    private void render() {
        MiniMe mini = plugin.manager().of(player);
        MiniSettings s = mini.settings();
        inventory.clear();

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);

        // header: your head (it wears your skin)
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skull = (SkullMeta) head.getItemMeta();
        skull.setPlayerProfile(player.getPlayerProfile());
        skull.displayName(plain(mini.displayName(player)));
        String status = mini.isKnockedOut()
                ? L.t("menu.main.head.status-knocked-out", "knocked out ({seconds}s)", "seconds", mini.knockedOutSeconds())
                : mini.isOut() ? L.t("menu.main.head.status-out", "out") : L.t("menu.main.head.status-away", "put away");
        String health = mini.isOut()
                ? (int) Math.ceil(mini.entity().getHealth()) + "/" + (int) mini.maxHealth() : "—";
        skull.lore(lines(L.t("menu.main.head.lore",
                "Status: {status}\nHealth: {health}\nJob: {job}\nBackpack: {used}/{size} slots used\nMode: {mode}",
                "status", L.parse(status), "health", health, "job", L.parse(s.job.label()),
                "used", mini.backpack().usedSlots(), "size", mini.backpack().size(), "mode", L.parse(s.mode.label()))));
        head.setItemMeta(skull);
        inventory.setItem(HEAD, head);

        inventory.setItem(OUT, mini.isOut()
                ? item(Material.ENDER_PEARL, L.t("menu.main.put-away.name", "<red>Put away"),
                        L.t("menu.main.put-away.lore", "Click to put your Mini Me away"))
                : item(Material.TOTEM_OF_UNDYING, L.t("menu.main.bring-out.name", "<green>Bring out"),
                        L.t("menu.main.bring-out.lore", "Click to bring your Mini Me out")));

        inventory.setItem(NAME, item(Material.NAME_TAG, L.t("menu.main.name.name", "<gold>Name"),
                L.t("menu.main.name.lore", "Left-click: type a new name\nRight-click: back to default\nColors work, e.g. <rainbow>Tiny</rainbow>")));

        inventory.setItem(NAMETAG, toggle(Material.OAK_SIGN, L.t("menu.main.nametag.name", "Show name"), s.nametag, ""));

        inventory.setItem(MODE, cycler(modeIcon(s.mode), L.t("menu.main.mode.name", "Mode"), s.mode.label(), s.mode.blurb()));
        inventory.setItem(POSE, cycler(Material.RED_BED, L.t("menu.main.pose.name", "Resting pose"), s.pose.label(), null));
        inventory.setItem(TRAIL, cycler(Material.BLAZE_POWDER, L.t("menu.main.trail.name", "Trail"), s.trail.label(), null));

        String glowName = s.glow == null ? L.t("menu.main.glow.off", "Off") : pretty(NamedTextColor.NAMES.key(s.glow));
        ItemStack glow = item(Material.GLOWSTONE_DUST,
                L.t("menu.main.glow.name", "Glow: {color}", "color", glowName),
                L.t("menu.main.glow.lore", "Left-click: next color\nRight-click: previous color"));
        if (s.glow != null) {
            ItemMeta meta = glow.getItemMeta();
            meta.displayName(meta.displayName().colorIfAbsent(s.glow));
            glow.setItemMeta(meta);
        }
        inventory.setItem(GLOW, glow);

        inventory.setItem(MIMIC, toggle(Material.SPYGLASS, L.t("menu.main.mimic.name", "Copy me"), s.mimic,
                L.t("menu.main.mimic.lore", "Copies your sneaking, swimming,\ngliding, arm swings and held items")));
        inventory.setItem(OUTFIT, toggle(Material.IRON_CHESTPLATE, L.t("menu.main.outfit.name", "Wear my armor"), s.outfit, ""));
        inventory.setItem(HAND, item(Material.STICK,
                s.leftHanded ? L.t("menu.main.hand.left", "<yellow>Main hand: left") : L.t("menu.main.hand.right", "<yellow>Main hand: right"),
                L.t("menu.main.hand.lore", "Click to switch")));
        inventory.setItem(SUMMON, item(Material.LEAD, L.t("menu.main.summon.name", "<green>Summon"),
                L.t("menu.main.summon.lore", "Pop it back next to you")));
        inventory.setItem(RESET, item(Material.BARRIER, L.t("menu.main.reset.name", "<red>Reset everything"),
                L.t("menu.main.reset.lore", "Shift-click to reset all settings")));

        // ---- jobs
        inventory.setItem(JOB, cycler(jobIcon(s.job), L.t("menu.main.job.name", "Job"), s.job.label(), s.job.blurb()));
        inventory.setItem(HOSTILE, toggle(Material.ZOMBIE_HEAD, L.t("menu.main.hunt-hostile.name", "Hunt hostile mobs"), s.huntHostile,
                L.t("menu.main.hunt-hostile.lore", "Zombies, skeletons, creepers, spiders...\n(when the job is Hunter)")));
        inventory.setItem(PASSIVE, toggle(Material.PORKCHOP, L.t("menu.main.hunt-passive.name", "Hunt passive mobs"), s.huntPassive,
                L.t("menu.main.hunt-passive.lore", "Cows, pigs, sheep, chickens...\nNever pets, named or leashed mobs,\nor babies")));
        inventory.setItem(REPLANT, toggle(Material.WHEAT_SEEDS, L.t("menu.main.replant.name", "Replant crops"), s.replant,
                L.t("menu.main.replant.lore", "Plants seeds back after harvesting")));
        inventory.setItem(COLLECT, toggle(Material.HOPPER, L.t("menu.main.collect.name", "Loot into backpack"), s.collect,
                L.t("menu.main.collect.lore", "Harvests, mob drops and nearby\ndropped items go into its backpack")));
        inventory.setItem(MINING, item(Material.IRON_PICKAXE,
                L.t("menu.main.mining.name", "<aqua>Mining: {count} picked", "count", s.mine.size()),
                L.t("menu.main.mining.lore", "Choose what the Miner digs up\n(when the job is Miner)")));
        inventory.setItem(GEAR, item(Material.IRON_CHESTPLATE, L.t("menu.main.gear.name", "<green>Gear"),
                L.t("menu.main.gear.lore", "Armor and weapons for your Mini Me")));
        MiniFestive.Holiday today = plugin.festive().current();
        inventory.setItem(FESTIVE, toggle(Material.SPRUCE_SAPLING, L.t("menu.main.festive.name", "Holiday looks"), s.festive,
                L.t("menu.main.festive.lore", "Dresses up for holidays by itself:\nSanta hat + snow at Christmas,\npumpkin at Halloween, and more.")
                        + "\n" + (today == MiniFestive.Holiday.NONE
                        ? L.t("menu.main.festive.no-holiday", "No holiday today")
                        : L.t("menu.main.festive.today", "Today: {holiday}!", "holiday", L.parse(today.label())))));
        inventory.setItem(UPGRADES, item(Material.GOLDEN_APPLE,
                L.t("menu.main.upgrades.name", "<red>Upgrades (health level {level})", "level", s.healthLevel),
                L.t("menu.main.upgrades.lore", "Spend dragon eggs to raise its max health")));
        inventory.setItem(AGGRO, toggle(Material.SKELETON_SKULL, L.t("menu.main.aggro.name", "Mobs can attack it"), s.aggro,
                L.t("menu.main.aggro.lore", "Mobs go after it and it fights back.\nArmor in its Gear protects it.\nIf it runs out of health it's knocked\nout for a bit (gear and loot are safe).")));
        inventory.setItem(BACKPACK, item(Material.CHEST, L.t("menu.main.backpack.name", "<green>Backpack"),
                L.t("menu.main.backpack.lore", "Click to open\nTip: sneak + right-click your Mini Me")));

        inventory.setItem(EMOTES, item(Material.JUKEBOX,
                L.t("menu.main.emotes.name", "<light_purple>Emotes ({count})", "count", MiniMe.Emote.values().length),
                L.t("menu.main.emotes.lore", "Click to pick an emote")));
        inventory.setItem(RANDOM, item(Material.AMETHYST_SHARD, L.t("menu.random-emote.name", "<light_purple>Random emote"),
                L.t("menu.random-emote.lore", "Surprise me!")));

        inventory.setItem(CLOSE, item(Material.OAK_DOOR, L.t("menu.close", "<gray>Close")));
    }

    // ================================================================== clicking

    void click(int slot, ClickType type) {
        if (page == Page.MINING) {
            clickMining(slot, type);
            return;
        }
        if (emotePage) {
            clickEmote(slot, type);
            return;
        }
        MiniMe mini = plugin.manager().of(player);
        MiniSettings s = mini.settings();
        boolean back = type.isRightClick();
        int step = back ? -1 : 1;
        boolean changed = true;

        switch (slot) {
            case OUT -> {
                if (mini.isOut()) mini.putAway();
                else mini.spawn(player);
            }
            case NAME -> {
                if (back) {
                    s.name = null;
                    mini.applyName(player);
                } else {
                    player.closeInventory();
                    prompt("/miniz name ");
                    return;
                }
            }
            case NAMETAG -> {
                s.nametag = !s.nametag;
                mini.applyName(player);
            }
            case MODE -> {
                s.mode = MiniSettings.cycle(s.mode, step);
                mini.applyMode();
            }
            case POSE -> s.pose = MiniSettings.cycle(s.pose, step);
            case TRAIL -> s.trail = MiniSettings.cycle(s.trail, step);
            case GLOW -> {
                s.glow = s.cycleGlow(step);
                mini.applyGlow();
            }
            case MIMIC -> s.mimic = !s.mimic;
            case OUTFIT -> s.outfit = !s.outfit;
            case HAND -> {
                s.leftHanded = !s.leftHanded;
                mini.applyHand();
            }
            case SUMMON -> mini.summon(player);
            case RESET -> {
                if (!type.isShiftClick()) {
                    plugin.sayError(player, L.t("menu.main.reset.shift", "Shift-click to reset everything."));
                    changed = false;
                } else {
                    s.reset(plugin);
                    mini.applyAll(player);
                    plugin.say(player, L.t("cmd.reset.done", "Mini Me reset to defaults."));
                }
            }
            case JOB -> {
                MiniSettings.Mode before = s.mode;
                s.job = MiniSettings.cycle(s.job, step);
                mini.onJobChanged();
                if (s.mode != before) plugin.say(player, L.t("cmd.job.switched-to-follow", "Switched to Follow mode so it can work."));
            }
            case HOSTILE -> { s.huntHostile = !s.huntHostile; mini.onJobChanged(); }
            case PASSIVE -> { s.huntPassive = !s.huntPassive; mini.onJobChanged(); }
            case REPLANT -> s.replant = !s.replant;
            case COLLECT -> s.collect = !s.collect;
            case MINING -> {
                openMining(plugin, player);
                return;
            }
            case FESTIVE -> {
                s.festive = !s.festive;
                mini.checkHoliday(player);
            }
            case UPGRADES -> {
                mini.openUpgrades(player);
                return;
            }
            case GEAR -> {
                mini.gear().open(player);
                return;
            }
            case AGGRO -> {
                s.aggro = !s.aggro;
                mini.applyAggro();
            }
            case BACKPACK -> {
                mini.backpack().open(player);
                return;
            }
            case EMOTES -> {
                openEmotes(plugin, player);
                return;
            }
            case RANDOM -> {
                emote(mini, MiniMe.Emote.random());
                changed = false;
            }
            case CLOSE -> {
                player.closeInventory();
                return;
            }
            default -> changed = false;
        }

        if (changed) {
            s.save(plugin, player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.4f);
            render();
        }
    }

    private void emote(MiniMe mini, MiniMe.Emote emote) {
        if (!mini.startEmote(emote)) plugin.sayError(player, L.t("cmd.not-out", "Bring your Mini Me out first (/miniz on)."));
    }

    // ================================================================== mining page

    private static final int MINE_BACK = 45, MINE_ORES = 47, MINE_NONE = 49, MINE_JOB = 51, MINE_CLOSE = 53;

    private void renderMining() {
        MiniSettings s = plugin.manager().of(player).settings();
        inventory.clear();
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);

        MiniSettings.MineTarget[] targets = MiniSettings.MineTarget.values();
        for (int i = 0; i < targets.length && i < EMOTE_SLOTS.length; i++) {
            MiniSettings.MineTarget t = targets[i];
            boolean on = s.mine.contains(t);
            boolean needsDiamond = t == MiniSettings.MineTarget.ANCIENT_DEBRIS || t == MiniSettings.MineTarget.OBSIDIAN;
            String lore = on ? L.t("menu.click-to-turn-off", "Click to turn off") : L.t("menu.click-to-turn-on", "Click to turn on");
            if (needsDiamond) lore = L.t("menu.mining.needs-diamond", "Needs a diamond pickaxe in its Gear") + "\n" + lore;
            inventory.setItem(EMOTE_SLOTS[i], item(on ? t.icon : Material.GRAY_DYE,
                    L.t("menu.toggle-format", "<yellow>{name}: {state}", "name", L.parse(t.label()), "state", L.parse(L.onOff(on))),
                    lore));
        }
        inventory.setItem(MINE_BACK, item(Material.ARROW, L.t("menu.back", "<gray>Back")));
        inventory.setItem(MINE_ORES, item(Material.DIAMOND_ORE, L.t("menu.mining.ores.name", "<aqua>Just ores"),
                L.t("menu.mining.ores.lore", "Turn on every ore, nothing else")));
        inventory.setItem(MINE_NONE, item(Material.BARRIER, L.t("menu.mining.clear.name", "<red>Clear all")));
        boolean miner = s.job == MiniSettings.Job.MINE;
        inventory.setItem(MINE_JOB, item(miner ? Material.DIAMOND_PICKAXE : Material.WOODEN_PICKAXE,
                miner ? L.t("menu.mining.job-on.name", "<gold>Job: Miner (working)") : L.t("menu.mining.job-off.name", "<gold>Make it a Miner"),
                (miner ? L.t("menu.mining.job-on.lore", "Click to stop mining") : L.t("menu.mining.job-off.lore", "Click to set its job to Miner"))
                        + "\n" + L.t("menu.mining.job.where", "It mines what it can see near you\n(or near its spot in Stay mode).") + "\n"
                        + L.t("menu.mining.point-tip", "<yellow>Tip:</yellow> look at any ore and sneak + punch\nthe air: it digs a tunnel to it!")));
        inventory.setItem(MINE_CLOSE, item(Material.OAK_DOOR, L.t("menu.close", "<gray>Close")));
    }

    private void clickMining(int slot, ClickType type) {
        MiniMe mini = plugin.manager().of(player);
        MiniSettings s = mini.settings();
        switch (slot) {
            case MINE_BACK -> { open(plugin, player); return; }
            case MINE_CLOSE -> { player.closeInventory(); return; }
            case MINE_ORES -> s.mine = MiniSettings.MineTarget.ores();
            case MINE_NONE -> s.mine.clear();
            case MINE_JOB -> {
                MiniSettings.Mode before = s.mode;
                s.job = s.job == MiniSettings.Job.MINE ? MiniSettings.Job.NONE : MiniSettings.Job.MINE;
                mini.onJobChanged();
                if (s.mode != before) plugin.say(player, L.t("cmd.job.switched-to-follow", "Switched to Follow mode so it can work."));
            }
            default -> {
                MiniSettings.MineTarget[] targets = MiniSettings.MineTarget.values();
                MiniSettings.MineTarget hit = null;
                for (int i = 0; i < targets.length && i < EMOTE_SLOTS.length; i++) {
                    if (EMOTE_SLOTS[i] == slot) { hit = targets[i]; break; }
                }
                if (hit == null) return;
                if (!s.mine.remove(hit)) s.mine.add(hit);
            }
        }
        mini.onJobChanged();
        s.save(plugin, player);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.4f);
        renderMining();
    }

    // ================================================================== emote page

    private void renderEmotes() {
        inventory.clear();
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);

        MiniMe.Emote[] emotes = MiniMe.Emote.values();
        for (int i = 0; i < emotes.length && i < EMOTE_SLOTS.length; i++) {
            MiniMe.Emote e = emotes[i];
            inventory.setItem(EMOTE_SLOTS[i], item(e.icon,
                    L.t("menu.emotes.emote-name", "<light_purple>{emote}", "emote", L.parse(e.label())),
                    e.blurb() + "\n" + L.t("menu.emotes.emote-lore",
                            "Click: play it\nShift-click: play it and keep this menu open\n/miniz emote {id}",
                            "id", e.name().toLowerCase(Locale.ROOT))));
        }
        inventory.setItem(EMOTE_BACK, item(Material.ARROW, L.t("menu.back", "<gray>Back")));
        inventory.setItem(EMOTE_RANDOM, item(Material.AMETHYST_SHARD, L.t("menu.random-emote.name", "<light_purple>Random emote"),
                L.t("menu.random-emote.lore", "Surprise me!")));
        inventory.setItem(EMOTE_CLOSE, item(Material.OAK_DOOR, L.t("menu.close", "<gray>Close")));
    }

    private void clickEmote(int slot, ClickType type) {
        MiniMe mini = plugin.manager().of(player);
        MiniMe.Emote chosen = null;
        if (slot == EMOTE_RANDOM) {
            chosen = MiniMe.Emote.random();
        } else if (slot == EMOTE_BACK) {
            open(plugin, player);
            return;
        } else if (slot == EMOTE_CLOSE) {
            player.closeInventory();
            return;
        } else {
            MiniMe.Emote[] emotes = MiniMe.Emote.values();
            for (int i = 0; i < emotes.length && i < EMOTE_SLOTS.length; i++) {
                if (EMOTE_SLOTS[i] == slot) { chosen = emotes[i]; break; }
            }
        }
        if (chosen == null) return;

        if (!mini.isOut()) {
            plugin.sayError(player, L.t("cmd.not-out", "Bring your Mini Me out first (/miniz on)."));
            return;
        }
        mini.startEmote(chosen);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.4f);
        // Close the menu so you can watch, unless they shift-clicked to queue up more.
        if (!type.isShiftClick()) player.closeInventory();
    }

    /** "Type /miniz name <your name> — click here to start." with the command clickable. */
    private void prompt(String suggest) {
        Component line = L.c("menu.main.name.prompt",
                "Type <click:suggest_command:'/miniz name '><hover:show_text:'Click to fill in the command'><yellow>/miniz name \\<your name></yellow></hover></click> — click here to start.");
        plugin.send(player, line.colorIfAbsent(NamedTextColor.GRAY));
    }

    // ================================================================== item helpers

    private static Material jobIcon(MiniSettings.Job job) {
        return switch (job) {
            case NONE -> Material.CAKE;
            case FARM -> Material.IRON_HOE;
            case HUNT -> Material.IRON_SWORD;
            case BOTH -> Material.DIAMOND_HOE;
            case MINE -> Material.IRON_PICKAXE;
        };
    }

    private static Material modeIcon(MiniSettings.Mode mode) {
        return switch (mode) {
            case FOLLOW -> Material.LEATHER_BOOTS;
            case STAY -> Material.COBWEB;
            case ORBIT -> Material.ENDER_EYE;
            case SHOULDER -> Material.SADDLE;
        };
    }

    private static ItemStack cycler(Material icon, String what, String value, String blurb) {
        String lore = L.t("menu.cycler-hint", "Left-click: next\nRight-click: previous");
        if (blurb != null) lore = blurb + "\n" + lore;
        return item(icon, L.t("menu.cycler-format", "<aqua>{name}: {value}", "name", L.parse(what), "value", L.parse(value)), lore);
    }

    private static ItemStack toggle(Material icon, String what, boolean on, String extraLore) {
        String hint = on ? L.t("menu.click-to-turn-off", "Click to turn off") : L.t("menu.click-to-turn-on", "Click to turn on");
        String lore = extraLore == null || extraLore.isEmpty() ? hint : extraLore + "\n" + hint;
        String name = L.t("menu.toggle-format", "<yellow>{name}: {state}", "name", L.parse(what), "state", L.parse(L.onOff(on)));
        return item(on ? icon : Material.GRAY_DYE, name, lore);
    }

    /** An item named with MiniMessage text; lore lines are split on new lines. */
    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(L.parse(name).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        List<Component> lines = new ArrayList<>();
        for (String block : lore) lines.addAll(lines(block));
        if (!lines.isEmpty()) meta.lore(lines);
        stack.setItemMeta(meta);
        return stack;
    }

    /** MiniMessage text -> gray, non-italic lore lines (one per line of text). */
    static List<Component> lines(String text) {
        List<Component> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        for (String line : text.split("\n", -1)) {
            out.add(L.parse(line).colorIfAbsent(NamedTextColor.GRAY)
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        return out;
    }

    private static Component plain(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }

    private static String pretty(String key) {
        String[] words = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }
}
