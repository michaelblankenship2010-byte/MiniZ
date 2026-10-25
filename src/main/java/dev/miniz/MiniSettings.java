package dev.miniz;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Everything a player has customized about their Mini Me. Saved on the player (survives restarts). */
public final class MiniSettings {

    public enum Mode {
        FOLLOW("Follow", "Walks after you"),
        STAY("Stay", "Waits where it is"),
        ORBIT("Orbit", "Floats in circles around you"),
        SHOULDER("Shoulder", "Rides on your shoulder");

        public final String label, blurb;
        Mode(String label, String blurb) { this.label = label; this.blurb = blurb; }
        public String label() { return L.t("mode." + name().toLowerCase(Locale.ROOT) + ".name", label); }
        public String blurb() { return L.t("mode." + name().toLowerCase(Locale.ROOT) + ".description", blurb); }
    }

    public enum RestPose {
        STAND("Standing", Pose.STANDING),
        SNEAK("Sneaking", Pose.SNEAKING),
        SLEEP("Lying down", Pose.SLEEPING),
        SWIM("Swimming", Pose.SWIMMING),
        GLIDE("Gliding", Pose.FALL_FLYING);

        public final String label;
        public final Pose pose;
        RestPose(String label, Pose pose) { this.label = label; this.pose = pose; }
        public String label() { return L.t("pose." + name().toLowerCase(Locale.ROOT), label); }
    }

    public enum Trail {
        NONE("None", null),
        HEARTS("Hearts", Particle.HEART),
        FLAMES("Flames", Particle.FLAME),
        SOULS("Soul flames", Particle.SOUL_FIRE_FLAME),
        NOTES("Music notes", Particle.NOTE),
        SPARKLES("Sparkles", Particle.END_ROD),
        HAPPY("Happy", Particle.HAPPY_VILLAGER),
        CLOUDS("Clouds", Particle.CLOUD),
        BUBBLES("Bubbles", Particle.BUBBLE_POP),
        MAGIC("Magic", Particle.WITCH);

        public final String label;
        public final Particle particle;
        Trail(String label, Particle particle) { this.label = label; this.particle = particle; }
        public String label() { return L.t("trail." + name().toLowerCase(Locale.ROOT), label); }
    }

    public enum Job {
        NONE("None", "Just hangs out with you"),
        FARM("Farmer", "Harvests and replants grown crops"),
        HUNT("Hunter", "Fights mobs (pick hostile and/or passive)"),
        BOTH("Farmer + Hunter", "Farms, and fights mobs that come close"),
        MINE("Miner", "Mines the ores you pick in the Mining menu");

        public final String label, blurb;
        Job(String label, String blurb) { this.label = label; this.blurb = blurb; }
        public String label() { return L.t("job." + name().toLowerCase(Locale.ROOT) + ".name", label); }
        public String blurb() { return L.t("job." + name().toLowerCase(Locale.ROOT) + ".description", blurb); }

        public boolean farms() { return this == FARM || this == BOTH; }
        public boolean hunts() { return this == HUNT || this == BOTH; }
        public boolean mines() { return this == MINE; }
    }

    /** Things the Miner can be told to mine. */
    public enum MineTarget {
        COAL("Coal", Material.COAL, true, 0, 2, Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE),
        COPPER("Copper", Material.RAW_COPPER, true, 0, 0, Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE),
        IRON("Iron", Material.RAW_IRON, true, 0, 0, Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE),
        GOLD("Gold", Material.RAW_GOLD, true, 0, 0, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE),
        REDSTONE("Redstone", Material.REDSTONE, true, 1, 5, Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE),
        LAPIS("Lapis", Material.LAPIS_LAZULI, true, 2, 5, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE),
        DIAMOND("Diamond", Material.DIAMOND, true, 3, 7, Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE),
        EMERALD("Emerald", Material.EMERALD, true, 3, 7, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE),
        QUARTZ("Nether quartz", Material.QUARTZ, true, 2, 5, Material.NETHER_QUARTZ_ORE),
        NETHER_GOLD("Nether gold", Material.GOLD_NUGGET, true, 0, 1, Material.NETHER_GOLD_ORE),
        ANCIENT_DEBRIS("Ancient debris", Material.ANCIENT_DEBRIS, true, 0, 0, Material.ANCIENT_DEBRIS),
        AMETHYST("Amethyst clusters", Material.AMETHYST_SHARD, false, 0, 0, Material.AMETHYST_CLUSTER),
        STONE("Stone", Material.COBBLESTONE, false, 0, 0,
                Material.STONE, Material.COBBLESTONE, Material.DEEPSLATE, Material.COBBLED_DEEPSLATE,
                Material.ANDESITE, Material.DIORITE, Material.GRANITE, Material.TUFF),
        NETHERRACK("Netherrack", Material.NETHERRACK, false, 0, 0, Material.NETHERRACK),
        DIRT("Dirt", Material.DIRT, false, 0, 0, Material.DIRT, Material.COARSE_DIRT, Material.ROOTED_DIRT),
        SAND("Sand & gravel", Material.SAND, false, 0, 0, Material.SAND, Material.RED_SAND, Material.GRAVEL),
        CLAY("Clay", Material.CLAY_BALL, false, 0, 0, Material.CLAY),
        OBSIDIAN("Obsidian", Material.OBSIDIAN, false, 0, 0, Material.OBSIDIAN);

        public final String label;
        public final Material icon;
        public final boolean defaultOn;
        public final int xpMin, xpMax;
        public final Set<Material> blocks;

        MineTarget(String label, Material icon, boolean defaultOn, int xpMin, int xpMax, Material... blocks) {
            this.label = label;
            this.icon = icon;
            this.defaultOn = defaultOn;
            this.xpMin = xpMin;
            this.xpMax = xpMax;
            this.blocks = Set.of(blocks);
        }

        public String label() { return L.t("mine." + name().toLowerCase(Locale.ROOT), label); }

        /** Which target (if any) this block counts as. */
        public static MineTarget of(Material block) {
            for (MineTarget t : values()) if (t.blocks.contains(block)) return t;
            return null;
        }

        public static EnumSet<MineTarget> defaults() {
            EnumSet<MineTarget> set = EnumSet.noneOf(MineTarget.class);
            for (MineTarget t : values()) if (t.defaultOn) set.add(t);
            return set;
        }

        public static EnumSet<MineTarget> ores() {
            EnumSet<MineTarget> set = EnumSet.noneOf(MineTarget.class);
            for (MineTarget t : values()) if (t.xpMax > 0 || t == COPPER || t == IRON || t == GOLD || t == ANCIENT_DEBRIS) set.add(t);
            return set;
        }
    }

    /** Glow colors in the order the menu cycles through them. null = no glow. */
    public static final NamedTextColor[] GLOW_COLORS = {
            null, NamedTextColor.WHITE, NamedTextColor.YELLOW, NamedTextColor.GOLD, NamedTextColor.RED,
            NamedTextColor.LIGHT_PURPLE, NamedTextColor.DARK_PURPLE, NamedTextColor.BLUE, NamedTextColor.AQUA,
            NamedTextColor.GREEN, NamedTextColor.DARK_GREEN, NamedTextColor.GRAY, NamedTextColor.BLACK
    };

    public boolean out;
    public String name;          // raw MiniMessage, null = default name
    public boolean nametag = true;
    public Mode mode = Mode.FOLLOW;
    public RestPose pose = RestPose.STAND;
    public Trail trail = Trail.NONE;
    public NamedTextColor glow;  // null = off
    public boolean mimic = true;
    public boolean outfit = false;
    public boolean leftHanded = false;

    // jobs
    public Job job = Job.NONE;
    public boolean huntHostile = true;
    public boolean huntPassive = false;
    public boolean replant = true;
    public boolean collect = true;   // picks up dropped items into its backpack while working
    public EnumSet<MineTarget> mine = MineTarget.defaults();
    public boolean aggro = true;     // mobs can target and hurt it (it fights back)
    public boolean festive = true;   // holiday looks (Santa hat at Christmas, etc.)
    public int healthLevel = 0;      // bought in the Upgrades tab; kept through /miniz reset

    // ------------------------------------------------------------------ persistence

    private static NamespacedKey key(MiniZPlugin plugin, String id) {
        return new NamespacedKey(plugin, id);
    }

    public static MiniSettings load(MiniZPlugin plugin, Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        MiniSettings s = new MiniSettings();
        boolean firstTime = !pdc.has(key(plugin, "mode"), PersistentDataType.STRING);

        s.out = firstTime
                ? plugin.getConfig().getBoolean("out-on-first-join", false)
                : pdc.getOrDefault(key(plugin, "out"), PersistentDataType.BOOLEAN, false);
        s.name = pdc.get(key(plugin, "name"), PersistentDataType.STRING);
        s.nametag = pdc.getOrDefault(key(plugin, "nametag"), PersistentDataType.BOOLEAN, true);
        s.mode = parse(Mode.class, pdc.get(key(plugin, "mode"), PersistentDataType.STRING), Mode.FOLLOW);
        s.pose = parse(RestPose.class, pdc.get(key(plugin, "pose"), PersistentDataType.STRING), RestPose.STAND);
        s.trail = parse(Trail.class, pdc.get(key(plugin, "trail"), PersistentDataType.STRING), Trail.NONE);
        String glow = pdc.get(key(plugin, "glow"), PersistentDataType.STRING);
        s.glow = glow == null ? null : NamedTextColor.NAMES.value(glow);
        s.mimic = pdc.getOrDefault(key(plugin, "mimic"), PersistentDataType.BOOLEAN, true);
        s.outfit = pdc.getOrDefault(key(plugin, "outfit"), PersistentDataType.BOOLEAN, false);
        s.leftHanded = pdc.getOrDefault(key(plugin, "left"), PersistentDataType.BOOLEAN, false);
        s.job = parse(Job.class, pdc.get(key(plugin, "job"), PersistentDataType.STRING), Job.NONE);
        s.huntHostile = pdc.getOrDefault(key(plugin, "hunt_hostile"), PersistentDataType.BOOLEAN, true);
        s.huntPassive = pdc.getOrDefault(key(plugin, "hunt_passive"), PersistentDataType.BOOLEAN, false);
        s.replant = pdc.getOrDefault(key(plugin, "replant"), PersistentDataType.BOOLEAN, true);
        s.collect = pdc.getOrDefault(key(plugin, "collect"), PersistentDataType.BOOLEAN, true);
        s.aggro = pdc.getOrDefault(key(plugin, "aggro"), PersistentDataType.BOOLEAN,
                plugin.getConfig().getBoolean("health.mobs-attack-by-default", true));
        s.festive = pdc.getOrDefault(key(plugin, "festive"), PersistentDataType.BOOLEAN, true);
        s.healthLevel = Math.max(0, pdc.getOrDefault(key(plugin, "health_level"), PersistentDataType.INTEGER, 0));
        String mine = pdc.get(key(plugin, "mine"), PersistentDataType.STRING);
        if (mine != null) {
            s.mine = EnumSet.noneOf(MineTarget.class);
            for (String part : mine.split(",")) {
                MineTarget t = parse(MineTarget.class, part.trim(), null);
                if (t != null) s.mine.add(t);
            }
        }
        return s;
    }

    public void save(MiniZPlugin plugin, Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(key(plugin, "out"), PersistentDataType.BOOLEAN, out);
        setOrRemove(pdc, key(plugin, "name"), name);
        pdc.set(key(plugin, "nametag"), PersistentDataType.BOOLEAN, nametag);
        pdc.set(key(plugin, "mode"), PersistentDataType.STRING, mode.name());
        pdc.set(key(plugin, "pose"), PersistentDataType.STRING, pose.name());
        pdc.set(key(plugin, "trail"), PersistentDataType.STRING, trail.name());
        setOrRemove(pdc, key(plugin, "glow"), glow == null ? null : NamedTextColor.NAMES.key(glow));
        pdc.set(key(plugin, "mimic"), PersistentDataType.BOOLEAN, mimic);
        pdc.set(key(plugin, "outfit"), PersistentDataType.BOOLEAN, outfit);
        pdc.set(key(plugin, "left"), PersistentDataType.BOOLEAN, leftHanded);
        pdc.set(key(plugin, "job"), PersistentDataType.STRING, job.name());
        pdc.set(key(plugin, "hunt_hostile"), PersistentDataType.BOOLEAN, huntHostile);
        pdc.set(key(plugin, "hunt_passive"), PersistentDataType.BOOLEAN, huntPassive);
        pdc.set(key(plugin, "replant"), PersistentDataType.BOOLEAN, replant);
        pdc.set(key(plugin, "collect"), PersistentDataType.BOOLEAN, collect);
        pdc.set(key(plugin, "aggro"), PersistentDataType.BOOLEAN, aggro);
        pdc.set(key(plugin, "health_level"), PersistentDataType.INTEGER, healthLevel);
        pdc.set(key(plugin, "festive"), PersistentDataType.BOOLEAN, festive);
        StringBuilder mineList = new StringBuilder();
        for (MineTarget t : mine) {
            if (mineList.length() > 0) mineList.append(',');
            mineList.append(t.name());
        }
        pdc.set(key(plugin, "mine"), PersistentDataType.STRING, mineList.toString());
    }

    /** Back to defaults (keeps whether it's out). */
    public void reset(MiniZPlugin plugin) {
        name = null;
        nametag = true;
        mode = Mode.FOLLOW;
        pose = RestPose.STAND;
        trail = Trail.NONE;
        glow = null;
        mimic = true;
        outfit = false;
        leftHanded = false;
        job = Job.NONE;
        huntHostile = true;
        huntPassive = false;
        replant = true;
        collect = true;
        mine = MineTarget.defaults();
        aggro = plugin.getConfig().getBoolean("health.mobs-attack-by-default", true);
    }

    private static void setOrRemove(PersistentDataContainer pdc, NamespacedKey key, String value) {
        if (value == null) pdc.remove(key);
        else pdc.set(key, PersistentDataType.STRING, value);
    }

    public static <E extends Enum<E>> E parse(Class<E> type, String raw, E fallback) {
        if (raw == null) return fallback;
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public static <E extends Enum<E>> E cycle(E current, int step) {
        E[] values = current.getDeclaringClass().getEnumConstants();
        int i = Math.floorMod(current.ordinal() + step, values.length);
        return values[i];
    }

    public NamedTextColor cycleGlow(int step) {
        int index = 0;
        for (int i = 0; i < GLOW_COLORS.length; i++) {
            if (GLOW_COLORS[i] == glow) { index = i; break; }
        }
        return GLOW_COLORS[Math.floorMod(index + step, GLOW_COLORS.length)];
    }
}
