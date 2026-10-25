package dev.miniz;

import com.destroystokyo.paper.ClientOption;
import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MainHand;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One player's Mini Me: a vanilla Mannequin (a player-shaped entity that can wear any skin)
 * shrunk with the scale attribute, moved around by this class every tick.
 */
public final class MiniMe {

    public enum Emote {
        WAVE(30, "Wave", Material.FEATHER, "Waves hello"),
        DANCE(60, "Dance", Material.JUKEBOX, "Busts a move"),
        JUMP(26, "Jump", Material.RABBIT_FOOT, "Two happy hops"),
        SPIN(20, "Spin", Material.COMPASS, "A full twirl"),
        LOVE(36, "Love", Material.POPPY, "Hearts for you"),
        CLAP(40, "Clap", Material.BONE_MEAL, "Applause!"),
        BOW(36, "Bow", Material.IRON_HELMET, "A polite bow"),
        NOD(30, "Nod", Material.LIME_DYE, "Yes, yes"),
        SHAKE(30, "Head shake", Material.RED_DYE, "Nope"),
        CHEER(44, "Cheer", Material.GOLD_INGOT, "Jumps and cheers"),
        LAUGH(40, "Laugh", Material.SLIME_BALL, "Giggles"),
        CRY(60, "Cry", Material.WATER_BUCKET, "Sad little tears"),
        ANGRY(44, "Angry", Material.MAGMA_CREAM, "Stomps and fumes"),
        SLEEPY(80, "Nap", Material.WHITE_BED, "Lies down for a quick nap"),
        FAINT(60, "Faint", Material.PHANTOM_MEMBRANE, "Wobbles... and flops over"),
        DIZZY(60, "Dizzy", Material.FERMENTED_SPIDER_EYE, "Seeing stars"),
        PUNCH(30, "Punch", Material.IRON_SWORD, "Shadow boxing"),
        LEAP(26, "Leap", Material.SLIME_BLOCK, "A big spinning jump"),
        MOONWALK(50, "Moonwalk", Material.MUSIC_DISC_CAT, "Slides backwards, smooth"),
        SALUTE(36, "Salute", Material.SHIELD, "Stands to attention"),
        CELEBRATE(60, "Celebrate", Material.FIREWORK_ROCKET, "Fireworks party"),
        MAGIC(46, "Magic", Material.ENCHANTED_BOOK, "A sparkly magic trick"),
        THINK(60, "Think", Material.BOOK, "Hmm... got it!"),
        WIGGLE(40, "Wiggle", Material.STRING, "Wiggly little dance");

        final int ticks;
        public final String label;
        public final Material icon;
        public final String blurb;

        Emote(int ticks, String label, Material icon, String blurb) {
            this.ticks = ticks;
            this.label = label;
            this.icon = icon;
            this.blurb = blurb;
        }

        public String label() { return L.t("emote." + name().toLowerCase(Locale.ROOT) + ".name", label); }
        public String blurb() { return L.t("emote." + name().toLowerCase(Locale.ROOT) + ".description", blurb); }

        public static Emote random() {
            Emote[] all = values();
            return all[ThreadLocalRandom.current().nextInt(all.length)];
        }
    }

    private final MiniZPlugin plugin;
    private final UUID ownerId;
    private final MiniSettings settings;

    private Mannequin entity;
    private String glowTeam;

    // movement state
    private boolean walking;
    private Vector lastPos;
    private int stuckTicks;
    private int blockedTicks;
    private Vector lastWalkPos;
    private double orbitAngle;
    private int ticks;

    // emote state
    private Emote emote;
    private int emoteTick;
    private float emoteYaw;
    private UUID emoteAudience;


    // health (when mobs are allowed to attack it)
    private int knockedOutUntil = -1;
    private int lastHurtTick = -100_000;

    // jobs
    private final MiniBackpack backpack;
    private final MiniGear gear;
    private final MiniWorker worker;
    private Location stayAnchor;      // where it waits / works in Stay mode
    private Location lookOverride;    // something it should look at this tick (crop, mob)

    MiniMe(MiniZPlugin plugin, Player owner, MiniSettings settings) {
        this.plugin = plugin;
        this.ownerId = owner.getUniqueId();
        this.settings = settings;
        this.backpack = new MiniBackpack(plugin, this, owner);
        this.gear = new MiniGear(plugin, this, owner);
        this.worker = new MiniWorker(plugin, this);
    }

    public UUID ownerId() { return ownerId; }
    public MiniSettings settings() { return settings; }
    public Mannequin entity() { return entity; }
    public boolean isOut() { return entity != null && entity.isValid(); }
    public MiniBackpack backpack() { return backpack; }
    public MiniGear gear() { return gear; }
    MiniZPlugin plugin() { return plugin; }
    int ticks() { return ticks; }

    /** Saves settings and backpack onto the player. */
    public void save(Player owner) {
        settings.save(plugin, owner);
        backpack.save(owner);
        gear.save(owner);
    }

    /** Called when the job changes: drop whatever it was working on. */
    /**
     * Sends it to mine the ore you're looking at (digging a tunnel there if needed).
     * Returns null if it's on its way, or the message to show if it can't.
     */
    public String mineThis(Player owner, org.bukkit.block.Block target) {
        if (!isOut()) return L.t("cmd.not-out", "Bring your Mini Me out first (/miniz on).");
        if (emote != null) endEmote();
        return worker.order(entity, owner, target);
    }

    public boolean isDigging() {
        return worker.hasOrder();
    }

    /** Orbit/Shoulder can't work: switch to Follow so its feet are on the ground. */
    void ensureGrounded() {
        if (settings.mode == MiniSettings.Mode.ORBIT || settings.mode == MiniSettings.Mode.SHOULDER) {
            settings.mode = MiniSettings.Mode.FOLLOW;
            boolean floaty = false;
            if (isOut()) {
                entity.setGravity(!floaty);
                applySize();
            }
        }
    }

    /** Gravity back to normal for its mode (after tunnelling, where it floats). */
    void restoreGravity() {
        if (!isOut()) return;
        boolean floaty = settings.mode == MiniSettings.Mode.ORBIT || settings.mode == MiniSettings.Mode.SHOULDER;
        entity.setGravity(!floaty);
    }

    public void onJobChanged() {
        worker.reset();
        if (settings.job != MiniSettings.Job.NONE
                && (settings.mode == MiniSettings.Mode.ORBIT || settings.mode == MiniSettings.Mode.SHOULDER)) {
            settings.mode = MiniSettings.Mode.FOLLOW;   // it needs its feet on the ground to work
            applyMode();
        }
    }

    // ================================================================== spawning

    /** Bring it out next to the owner (does nothing if it's already out). */
    public void spawn(Player owner) {
        settings.out = true;
        if (isOut()) return;
        if (isKnockedOut()) return;   // comes back by itself when it's recovered
        if (owner.isDead() || owner.getGameMode() == GameMode.SPECTATOR) return; // tick() brings it out later

        Location spot = spawnSpot(owner);
        entity = owner.getWorld().spawn(spot, Mannequin.class, this::setup);
        applyAll(owner);
        walking = false;
        lastPos = null;
        if (stayAnchor == null || !stayAnchor.getWorld().equals(spot.getWorld())) stayAnchor = spot.clone();
        worker.reset();
        poof(spot);
    }

    /** Put it away for now (keeps settings.out as-is; callers decide). */
    public void despawn() {
        if (entity != null) {
            leaveGlowTeam();
            if (entity.isValid()) entity.remove();
            entity = null;
        }
        emote = null;
        emoteAudience = null;
    }

    public void putAway() {
        if (isOut()) poof(entity.getLocation());
        settings.out = false;
        despawn();
    }

    /** Hop over next to you without changing its Stay spot (used when it brings you a full backpack). */
    void popTo(Player owner) {
        if (!isOut()) return;
        poof(entity.getLocation());
        Location spot = spawnSpot(owner);
        entity.teleport(spot);
        entity.setVelocity(new Vector());
        poof(spot);
    }

    /** Pop it back next to you. */
    public void summon(Player owner) {
        if (!isOut()) { spawn(owner); return; }
        poof(entity.getLocation());
        Location spot = spawnSpot(owner);
        entity.teleport(spot);
        entity.setVelocity(new Vector());
        stayAnchor = spot.clone();
        worker.reset();
        poof(spot);
    }

    /** Runs before the entity is added to the world. */
    private void setup(Mannequin m) {
        m.setPersistent(false);           // never saved into the world
        m.setRemoveWhenFarAway(false);
        m.setInvulnerable(!settings.aggro);   // can only be hurt when mobs are allowed to attack it
        m.setCollidable(false);           // you can walk through it, it won't shove you
        m.setCanPickupItems(false);
        m.setSilent(true);
        m.getPersistentDataContainer().set(plugin.ownerKey(), PersistentDataType.STRING, ownerId.toString());

        setBase(m, Attribute.STEP_HEIGHT, 1.0);
        setBase(m, Attribute.SAFE_FALL_DISTANCE, 1024.0);
        setBase(m, Attribute.MAX_HEALTH, maxHealth());
        m.setHealth(maxHealth());
    }

    /** Health before upgrades. */
    public double baseHealth() {
        return Math.max(1.0, plugin.getConfig().getDouble("health.max-health", 20.0));
    }

    /** Health with the upgrades you've bought. */
    public double maxHealth() {
        int level = Math.min(settings.healthLevel, MiniUpgrades.maxLevel(plugin));
        return Math.min(1024.0, baseHealth() + level * MiniUpgrades.perLevel(plugin));
    }

    /** Puts the (upgraded) max health on the live entity; {@code healToFull} after buying an upgrade. */
    public void applyHealth(boolean healToFull) {
        if (!isOut()) return;
        setBase(entity, Attribute.MAX_HEALTH, maxHealth());
        if (healToFull || entity.getHealth() > maxHealth()) entity.setHealth(maxHealth());
        updateDescription();
    }

    public void openUpgrades(Player owner) {
        MiniUpgrades.open(plugin, this, owner);
    }

    // ================================================================== applying settings

    public void applyAll(Player owner) {
        if (!isOut()) return;
        applyMode();         // also applies size
        applyName(owner);
        applySkin(owner);
        applyHand();
        applyGlow();
        applyAggro();
        applyHealth(false);
        applyPose(Pose.STANDING);
        refreshEquipment(owner);
    }

    public double effectiveSize() {
        double size = plugin.miniSize();
        return settings.mode == MiniSettings.Mode.SHOULDER ? Math.min(size, 0.3) : size;
    }

    public void applySize() {
        if (isOut()) setBase(entity, Attribute.SCALE, effectiveSize());
    }

    public void applyMode() {
        if (!isOut()) return;
        boolean floaty = settings.mode == MiniSettings.Mode.ORBIT || settings.mode == MiniSettings.Mode.SHOULDER;
        entity.setGravity(!floaty);
        entity.setVelocity(new Vector());
        walking = false;
        if (settings.mode == MiniSettings.Mode.STAY) stayAnchor = entity.getLocation();
        worker.reset();
        applySize();
    }

    public void applyName(Player owner) {
        if (!isOut()) return;
        Component name;
        if (settings.name != null) {
            name = plugin.safeMiniMessage().deserialize(settings.name);
        } else {
            name = MiniMessage.miniMessage().deserialize(
                    plugin.getConfig().getString("default-name", "<gold>Mini <player>"),
                    Placeholder.unparsed("player", owner.getName()));
        }
        entity.customName(plugin.festive().namePrefix(holiday()).append(name));
        entity.setCustomNameVisible(settings.nametag);

        updateDescription();
    }

    /** The small line under its name: its health while mobs can attack it, otherwise the config text. */
    private void updateDescription() {
        if (!isOut()) return;
        if (settings.aggro && plugin.getConfig().getBoolean("health.show-health", true)) {
            double hp = Math.max(0, entity.getHealth());
            boolean low = hp <= maxHealth() * 0.3;
            entity.setDescription(low
                    ? L.c("health-tag.low", "<red>❤ {health}/{max}", "health", (int) Math.ceil(hp), "max", (int) maxHealth())
                    : L.c("health-tag.normal", "<green>❤ {health}/{max}", "health", (int) Math.ceil(hp), "max", (int) maxHealth()));
            return;
        }
        String description = plugin.getConfig().getString("description", "");
        entity.setDescription(description == null || description.isBlank()
                ? null
                : MiniMessage.miniMessage().deserialize(description));
    }

    // ================================================================== holidays

    private MiniFestive.Holiday lastHoliday;   // null = not checked yet this session

    /** Today's holiday, or NONE if it's not a holiday or this player turned festive looks off. */
    MiniFestive.Holiday holiday() {
        return settings.festive ? plugin.festive().current() : MiniFestive.Holiday.NONE;
    }

    /** Called every few seconds: switches the holiday look on/off when the date (or setting) changes. */
    void checkHoliday(Player owner) {
        MiniFestive.Holiday now = holiday();
        if (now == lastHoliday) return;
        lastHoliday = now;
        applyName(owner);
        refreshEquipment(owner);
        if (now == MiniFestive.Holiday.NONE || !isOut()) return;

        Location at = entity.getLocation().add(0, effectiveSize() * 1.5, 0);
        Particle burst = now.particle == null ? Particle.HAPPY_VILLAGER : now.particle;
        at.getWorld().spawnParticle(burst, at, 15, 0.4, 0.4, 0.4, 0.02);
        if (plugin.getConfig().getBoolean("festive.greeting()", true)) {
            speak(owner, L.t("speech.holiday-greeting", "{greeting}, {player}!",
                    "greeting", L.parse(now.greeting()), "player", owner.getName()));
        }
        startEmote(Emote.WAVE);
        maybeGivePresent(owner, now);
    }

    /** On the holiday itself it brings you a little gift, once a year per holiday. */
    private void maybeGivePresent(Player owner, MiniFestive.Holiday now) {
        if (!plugin.festive().isMainDay(now)) return;
        if (!plugin.getConfig().getBoolean("festive.gifts", true)) return;
        List<ItemStack> gifts = plugin.festive().gift(now);
        if (gifts.isEmpty()) return;
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "gift_" + now.configKey());
        int year = plugin.festive().today().getYear();
        Integer last = owner.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        if (last != null && last == year) return;
        owner.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, year);

        for (ItemStack gift : gifts) {
            for (ItemStack extra : owner.getInventory().addItem(gift.clone()).values()) {
                for (ItemStack rest : backpack.add(List.of(extra))) {
                    owner.getWorld().dropItemNaturally(owner.getLocation(), rest);
                }
            }
        }
        owner.playSound(owner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
        speak(owner, L.t("speech.holiday-gift", "I got you a {holiday} present! Check your inventory.",
                "holiday", L.parse(now.label())));
    }

    // ================================================================== health & mobs

    /** Turns "mobs can attack it" on or off on the live entity. */
    public void applyAggro() {
        if (!isOut()) return;
        entity.setInvulnerable(!settings.aggro);
        if (!settings.aggro) {
            entity.setHealth(Math.min(maxHealth(), entity.getHealth()));
            // Stop anything that's after it.
            for (org.bukkit.entity.Mob mob : entity.getWorld().getNearbyEntitiesByType(
                    org.bukkit.entity.Mob.class, entity.getLocation(), 32)) {
                if (entity.equals(mob.getTarget())) mob.setTarget(null);
            }
            worker.reset();
        }
        updateDescription();
    }

    public boolean isKnockedOut() {
        return knockedOutUntil > ticks;
    }

    public int knockedOutSeconds() {
        return Math.max(0, (knockedOutUntil - ticks + 19) / 20);
    }

    /** It took a hit from a mob (called from the damage listener, after the hit is allowed). */
    void onHurt(org.bukkit.entity.LivingEntity attacker) {
        lastHurtTick = ticks;
        if (attacker != null) worker.defend(attacker);
        Bukkit.getScheduler().runTask(plugin, this::updateDescription);
    }

    /** A hit that would have killed it: it gets knocked out instead and comes back later. Keeps its gear and backpack. */
    void knockOut(Player owner) {
        if (!isOut()) return;
        Location at = entity.getLocation();
        World world = at.getWorld();
        world.spawnParticle(Particle.POOF, at.clone().add(0, 0.4, 0), 20, 0.3, 0.3, 0.3, 0.05);
        world.spawnParticle(Particle.SMOKE, at.clone().add(0, 0.4, 0), 10, 0.2, 0.2, 0.2, 0.02);
        world.playSound(at, Sound.ENTITY_PLAYER_HURT, 0.8f, 1.8f);
        int seconds = Math.max(1, plugin.getConfig().getInt("health.knockout-seconds", 30));
        knockedOutUntil = ticks + seconds * 20;
        worker.reset();
        despawn();
        if (owner != null) {
            speak(owner, L.t("speech.knocked-out", "Ouch! I got knocked out... back in {seconds} seconds.", "seconds", seconds));
        }
    }

    private void regenerate() {
        if (!isOut() || !settings.aggro) return;
        double max = maxHealth();
        double hp = entity.getHealth();
        if (hp >= max) return;
        int delay = plugin.getConfig().getInt("health.regen-delay-ticks", 100);
        if (ticks - lastHurtTick < delay) return;
        entity.setHealth(Math.min(max, hp + plugin.getConfig().getDouble("health.regen-amount", 1.0)));
        updateDescription();
    }

    /** Hostile mobs nearby with nothing better to do go after the Mini Me. */
    private void taunt(Mannequin m) {
        double r = plugin.getConfig().getDouble("health.aggro-radius", 8.0);
        if (r <= 0) return;
        for (org.bukkit.entity.Mob mob : m.getWorld().getNearbyEntitiesByType(org.bukkit.entity.Mob.class, m.getLocation(), r)) {
            if (!(mob instanceof org.bukkit.entity.Enemy) || mob.getTarget() != null) continue;
            if (mob instanceof org.bukkit.entity.Tameable t && t.isTamed()) continue;
            if (plugin.manager().isMini(mob)) continue;
            if (!mob.hasLineOfSight(m)) continue;
            mob.setTarget(m);
        }
    }

    /** Says something to you in chat, as the Mini Me. */
    void speak(Player owner, String message) {
        speak(owner, message, false);
    }

    /** {@code message} is MiniMessage text (usually from language.yml). */
    void speak(Player owner, String message, boolean backpackButton) {
        String line = L.t("speech.format", "{name} <dark_gray>»</dark_gray> <white>{message}",
                "name", displayName(owner), "message", L.parse(message));
        if (backpackButton) {
            line += L.t("speech.open-backpack-button",
                    " <click:run_command:'/miniz backpack'><yellow>[open my backpack]</yellow></click>");
        }
        plugin.send(owner, L.parse(line));
        owner.playSound(owner.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 2.0f);
    }

    public Component displayName(Player owner) {
        if (settings.name != null) return plugin.safeMiniMessage().deserialize(settings.name);
        return MiniMessage.miniMessage().deserialize(
                plugin.getConfig().getString("default-name", "<gold>Mini <player>"),
                Placeholder.unparsed("player", owner.getName()));
    }

    /** It always wears your skin (and your skin layers: hat, jacket, sleeves...). */
    public void applySkin(Player owner) {
        if (!isOut()) return;
        entity.setProfile(ResolvableProfile.resolvableProfile(owner.getPlayerProfile()));
        entity.setSkinParts(owner.getClientOption(ClientOption.SKIN_PARTS));
    }

    public void applyHand() {
        if (isOut()) entity.setMainHand(settings.leftHanded ? MainHand.LEFT : MainHand.RIGHT);
    }

    public void applyGlow() {
        if (!isOut()) return;
        leaveGlowTeam();
        if (settings.glow == null) {
            entity.setGlowing(false);
            return;
        }
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = "miniz_" + NamedTextColor.NAMES.key(settings.glow);
        Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
        team.color(settings.glow);
        team.addEntry(entity.getUniqueId().toString());
        glowTeam = teamName;
        entity.setGlowing(true);
    }

    private void leaveGlowTeam() {
        if (glowTeam == null || entity == null) return;
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(glowTeam);
        if (team != null) team.removeEntry(entity.getUniqueId().toString());
        glowTeam = null;
    }

    // ================================================================== emotes / mimic

    public boolean startEmote(Emote e) {
        return startEmote(e, null);
    }

    /** Starts an emote, performed towards {@code audience} (null = towards you). */
    public boolean startEmote(Emote e, Player audience) {
        if (!isOut()) return false;
        if (emote != null) endEmote();
        emote = e;
        emoteTick = 0;
        emoteYaw = entity.getYaw();
        emoteAudience = audience == null ? null : audience.getUniqueId();
        return true;
    }

    public Emote currentEmote() { return emote; }

    public void stopEmote() {
        if (emote != null) endEmote();
    }

    private void endEmote() {
        emote = null;
        emoteAudience = null;
        if (isOut() && entity.hasGravity()) {
            Vector v = entity.getVelocity();
            entity.setVelocity(new Vector(0, Math.min(v.getY(), 0), 0));
        }
    }

    /** Copies your arm swings (called from the listener). */
    public void swing(boolean offHand) {
        if (!isOut() || !settings.mimic || emote != null) return;
        if (offHand) entity.swingOffHand();
        else entity.swingMainHand();
    }

    /** Waves at whoever right-clicked it. */
    public void waveAt(Player who) {
        if (!isOut()) return;
        startEmote(Emote.WAVE, who);
    }

    // ================================================================== ticking

    void tick(Player owner) {
        ticks++;
        if (ticks % 200 == 0 && backpack.isDirty()) backpack.save(owner);
        boolean show = settings.out && !owner.isDead() && owner.getGameMode() != GameMode.SPECTATOR
                && !isKnockedOut();
        if (knockedOutUntil > 0 && !isKnockedOut()) {
            knockedOutUntil = -1;
            if (settings.out) speak(owner, L.t("speech.back", "I'm back!"));
        }
        if (!show) {
            if (entity != null) despawn();
            return;
        }
        if (!isOut()) {
            despawn();          // e.g. its chunk unloaded; clean up and bring it back
            spawn(owner);
            if (!isOut()) return;
        }

        Mannequin m = entity;
        Location here = m.getLocation();
        Location ownerLoc = owner.getLocation();
        boolean sameWorld = here.getWorld().equals(ownerLoc.getWorld());
        double dist = sameWorld ? here.distance(ownerLoc) : Double.MAX_VALUE;

        if (settings.mode == MiniSettings.Mode.STAY) {
            if (!sameWorld) return; // waits in the other world until you come back
        } else if (!worker.hasOrder() && dist > plugin.getConfig().getDouble("follow.teleport-distance", 14.0)) {
            Location spot = spawnSpot(owner);
            m.teleport(spot);
            m.setVelocity(new Vector());
            poof(spot);
            return;
        }

        // Emoting on the ground: stand and perform, unless you've wandered off — then stop and catch up.
        boolean grounded = settings.mode == MiniSettings.Mode.FOLLOW || settings.mode == MiniSettings.Mode.STAY;
        if (emote != null && grounded
                && dist > plugin.getConfig().getDouble("follow.start-distance", 3.0) * 2.5
                && settings.mode == MiniSettings.Mode.FOLLOW) {
            endEmote();
        }

        // Jobs: the worker drives movement while it has something to do (null = nothing to do).
        lookOverride = null;
        Boolean working = null;
        if (emote == null && grounded
                && (settings.job != MiniSettings.Job.NONE || worker.isDefending() || worker.hasOrder())) {
            Location center = settings.mode == MiniSettings.Mode.STAY ? stayAnchor : ownerLoc;
            if (center != null && here.getWorld().equals(center.getWorld())) {
                working = worker.tick(m, owner, center);
            }
        }

        boolean moving = switch (settings.mode) {
            case FOLLOW -> emote != null ? false : working != null ? working : walk(m, owner, dist);
            case STAY -> working != null ? working : (emote != null ? stand(m) : returnToAnchor(m));
            case ORBIT -> { orbit(m, owner); yield true; }
            case SHOULDER -> { shoulder(m, owner); yield false; }
        };

        if (emote != null) {
            runEmote(m, owner);
        } else {
            if (settings.mode == MiniSettings.Mode.FOLLOW || settings.mode == MiniSettings.Mode.STAY) {
                faceNaturally(m, owner, moving);
            }
            applyPose(choosePose(owner, moving));
        }

        if (ticks % 4 == 0) refreshEquipment(owner);
        if (ticks % 100 == 0 || lastHoliday == null) checkHoliday(owner);
        if (settings.aggro) {
            if (ticks % 20 == 0) regenerate();
            if (ticks % 20 == 10 && grounded) taunt(m);
        }
        trail(m, moving);
    }

    // ------------------------------------------------------------------ movement modes

    /** Leash-style following: it stays put in the world until you walk off, so turning your camera shows it. */
    private boolean walk(Mannequin m, Player owner, double dist) {
        double start = plugin.getConfig().getDouble("follow.start-distance", 3.0);
        double stop = plugin.getConfig().getDouble("follow.stop-distance", 1.7);
        if (!walking && dist > start) walking = true;
        if (walking && dist < stop) walking = false;

        Vector vel = m.getVelocity();
        if (!walking) {
            if (Math.abs(vel.getX()) + Math.abs(vel.getZ()) > 0.01) m.setVelocity(new Vector(0, vel.getY(), 0));
            lastPos = null;
            stuckTicks = 0;
            lastWalkPos = null;
            blockedTicks = 0;
            return false;
        }

        double speed = (owner.isSprinting() || dist > start * 2) ? runSpeed() : walkSpeed();
        Vector before = m.getLocation().toVector();
        boolean moving = stepToward(m, owner.getLocation(), speed);
        if (lastWalkPos != null && before.distanceSquared(lastWalkPos) < 0.0025) {
            if (++blockedTicks > 80) {   // stuck (in a hole, behind a wall...): just pop over to you
                blockedTicks = 0;
                popTo(owner);
            }
        } else {
            blockedTicks = 0;
        }
        lastWalkPos = before;
        return moving;
    }

    double walkSpeed() { return plugin.getConfig().getDouble("follow.walk-speed", 0.24); }
    double runSpeed() { return plugin.getConfig().getDouble("follow.run-speed", 0.36); }

    /** One tick of walking towards {@code target}, hopping over things in the way. */
    boolean stepToward(Mannequin m, Location target, double speed) {
        Location here = m.getLocation();
        Vector vel = m.getVelocity();
        Vector to = target.toVector().subtract(here.toVector());
        double dy = to.getY();
        to.setY(0);
        if (to.lengthSquared() < 1.0e-4) return false;
        if (to.lengthSquared() < speed * speed) speed = to.length();   // don't overshoot
        Vector step = to.normalize().multiply(speed);

        // Hop when something is in the way.
        Vector now = here.toVector();
        if (lastPos != null) {
            double moved = Math.hypot(now.getX() - lastPos.getX(), now.getZ() - lastPos.getZ());
            stuckTicks = moved < speed * 0.3 ? stuckTicks + 1 : 0;
        }
        lastPos = now;

        double vy = vel.getY();
        if (m.isInWater()) {
            vy = Math.max(vy, dy > -0.5 ? 0.12 : 0.0);   // paddle up
        } else if (m.isOnGround() && stuckTicks >= 3) {
            vy = 0.5;
            stuckTicks = 0;
        }
        m.setVelocity(new Vector(step.getX(), vy, step.getZ()));
        return true;
    }

    void holdStill(Mannequin m) {
        Vector vel = m.getVelocity();
        if (Math.abs(vel.getX()) + Math.abs(vel.getZ()) > 0.01) m.setVelocity(new Vector(0, vel.getY(), 0));
        lastPos = null;
        stuckTicks = 0;
    }

    private boolean stand(Mannequin m) {
        holdStill(m);
        return false;
    }

    /** Stay mode: if it got pushed or wandered off to work, walk back to its spot. */
    private boolean returnToAnchor(Mannequin m) {
        if (stayAnchor == null || !stayAnchor.getWorld().equals(m.getWorld())) return stand(m);
        Location here = m.getLocation();
        double flat = Math.hypot(here.getX() - stayAnchor.getX(), here.getZ() - stayAnchor.getZ());
        if (flat < 0.6) return stand(m);
        return stepToward(m, stayAnchor, walkSpeed());
    }

    private int fullWarnedAt = -100_000;

    /** Tells you (at most every 30 seconds) that the backpack is full. */
    void warnBackpackFull(Player owner) {
        if (ticks - fullWarnedAt < 600) return;
        fullWarnedAt = ticks;
        plugin.say(owner, L.t("backpack.full-warning",
                "Your Mini Me's backpack is full! <click:run_command:'/miniz backpack'><yellow>[open it]</yellow></click>"));
    }

    /** Lets the worker point its head at what it's busy with. */
    void lookAt(Location target) {
        lookOverride = target;
    }

    private void orbit(Mannequin m, Player owner) {
        double radius = plugin.getConfig().getDouble("orbit.radius", 1.6);
        orbitAngle += plugin.getConfig().getDouble("orbit.speed", 0.08);
        Location base = owner.getLocation();
        double x = base.getX() + Math.cos(orbitAngle) * radius;
        double z = base.getZ() + Math.sin(orbitAngle) * radius;
        double y = base.getY() + 0.35 + Math.sin(ticks * 0.12) * 0.15;
        // Face the way it's flying (the tangent of the circle).
        float yaw = yawOf(-Math.sin(orbitAngle), Math.cos(orbitAngle));
        m.teleport(new Location(base.getWorld(), x, y, z, yaw, 0f));
    }

    private void shoulder(Mannequin m, Player owner) {
        Location o = owner.getLocation();
        double ownerScale = attr(owner, Attribute.SCALE, 1.0);
        double rad = Math.toRadians(owner.getBodyYaw());
        double rx = -Math.cos(rad), rz = -Math.sin(rad);   // right-hand side
        double side = settings.leftHanded ? -0.42 : 0.42;  // left-handed minis sit on the left
        double height = (owner.isSneaking() ? 1.12 : 1.42) * ownerScale;
        Location spot = new Location(o.getWorld(),
                o.getX() + rx * side * ownerScale,
                o.getY() + height,
                o.getZ() + rz * side * ownerScale,
                owner.getYaw(), owner.getPitch() * 0.5f);
        m.teleport(spot);
        m.setBodyYaw(owner.getBodyYaw());
    }

    // ------------------------------------------------------------------ looking + poses

    private void faceNaturally(Mannequin m, Player owner, boolean moving) {
        Location here = m.getLocation();
        float targetYaw;
        float pitch;
        if (moving) {
            Vector v = m.getVelocity();
            targetYaw = yawOf(v.getX(), v.getZ());
            pitch = 0f;
        } else {
            // Standing still: look up at you (or at whatever it's working on),
            // so whenever you turn around it's facing you.
            Location eye = m.getEyeLocation();
            Location ownerEye = lookOverride != null ? lookOverride : owner.getEyeLocation();
            targetYaw = lookYaw(here, ownerEye);
            double dx = ownerEye.getX() - eye.getX(), dz = ownerEye.getZ() - eye.getZ();
            double dy = ownerEye.getY() - eye.getY();
            pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
            pitch = Math.max(-60f, Math.min(60f, pitch));
        }
        float yaw = lerpAngle(here.getYaw(), targetYaw, moving ? 0.5f : 0.25f);
        if (Math.abs(wrap(yaw - here.getYaw())) > 0.5f || Math.abs(pitch - here.getPitch()) > 0.5f) {
            face(yaw, pitch);
        }
    }

    private void face(float yaw, float pitch) {
        entity.setRotation(yaw, pitch);
        entity.setBodyYaw(yaw);
    }

    private Pose choosePose(Player owner, boolean moving) {
        if (settings.mimic) {
            if (owner.isGliding()) return Pose.FALL_FLYING;
            if (owner.isSwimming()) return Pose.SWIMMING;
            if (owner.isSleeping()) return Pose.SLEEPING;
            if (owner.isSneaking()) return Pose.SNEAKING;
        }
        Pose rest = settings.pose.pose;
        boolean lyingDown = rest == Pose.SLEEPING || rest == Pose.SWIMMING || rest == Pose.FALL_FLYING;
        if (moving && lyingDown && settings.mode == MiniSettings.Mode.FOLLOW) return Pose.STANDING;
        return rest;
    }

    private void applyPose(Pose pose) {
        if (!isOut()) return;
        if (!Mannequin.validPoses().contains(pose)) pose = Pose.STANDING;
        if (entity.getPose() != pose) entity.setPose(pose, true);
    }

    // ------------------------------------------------------------------ emotes

    private void runEmote(Mannequin m, Player owner) {
        int t = emoteTick++;
        Location here = m.getLocation();
        double s = effectiveSize();

        // Who it's performing for: whoever clicked it, otherwise you.
        Player audience = emoteAudience == null ? null : Bukkit.getPlayer(emoteAudience);
        if (audience == null || !audience.getWorld().equals(m.getWorld())) audience = owner;
        float look = lookYaw(here, audience.getLocation());

        switch (emote) {
            case WAVE -> {
                applyPose(Pose.STANDING);
                face(look, -15f);
                if (t % 6 == 0) swingHand(m, t / 6);
            }
            case DANCE -> {
                face(emoteYaw + (float) Math.sin(t * 0.45) * 40f, 0f);
                applyPose((t / 5) % 2 == 0 ? Pose.SNEAKING : Pose.STANDING);
                if (t % 5 == 0) swingHand(m, t / 5);
                if (t % 10 == 0) note(here, s);
            }
            case JUMP -> {
                applyPose(Pose.STANDING);
                if (t == 0 || t == 13) {
                    hop(m, 0.5);
                    fx(here, Particle.HAPPY_VILLAGER, 0.1, 5, 0.2, 0);
                }
            }
            case SPIN -> {
                applyPose(Pose.STANDING);
                face(emoteYaw + t * 18f, 0f);
                if (t == emote.ticks - 1) fx(here, Particle.END_ROD, s, 8, 0.25, 0.02);
            }
            case LOVE -> {
                applyPose(Pose.STANDING);
                face(look, -20f);
                if (t == 0) m.swingMainHand();
                if (t % 5 == 0) fx(here, Particle.HEART, s * 2.3, 1, 0.2, 0);
            }
            case CLAP -> {
                applyPose(Pose.STANDING);
                face(look, -5f);
                if (t % 4 == 0) m.swingMainHand();
                if (t % 4 == 2) m.swingOffHand();
                if (t % 8 == 0) fx(here, Particle.CRIT, s * 1.2, 3, 0.1, 0.1);
            }
            case BOW -> {
                float pitch = t < 8 ? t * 9f : t < 24 ? 70f : Math.max(0f, 70f - (t - 24) * 7f);
                applyPose(t >= 6 && t < 26 ? Pose.SNEAKING : Pose.STANDING);
                face(look, pitch);
            }
            case NOD -> {
                applyPose(Pose.STANDING);
                face(look, 10f + (float) Math.sin(t * 0.6) * 25f);
            }
            case SHAKE -> {
                applyPose(Pose.STANDING);
                face(look + (float) Math.sin(t * 0.7) * 35f, 10f);
            }
            case CHEER -> {
                applyPose(Pose.STANDING);
                face(look, -30f);
                if (t == 0 || t == 22) hop(m, 0.55);
                if (t % 5 == 0) swingHand(m, t / 5);
                if (t % 6 == 0) fx(here, Particle.HAPPY_VILLAGER, s * 2.0, 2, 0.3, 0);
                if (t == 0 || t == 22) fx(here, Particle.FIREWORK, s * 2.2, 10, 0.1, 0.06);
            }
            case LAUGH -> {
                applyPose(t < 36 && (t / 3) % 2 == 0 ? Pose.SNEAKING : Pose.STANDING);
                face(look, -20f + (float) Math.sin(t * 1.3) * 12f);
                if (t % 10 == 0) fx(here, Particle.HAPPY_VILLAGER, s * 2.0, 2, 0.25, 0);
            }
            case CRY -> {
                applyPose(Pose.SNEAKING);
                face(emoteYaw, 40f);
                if (t % 3 == 0) fx(here, Particle.FALLING_WATER, s * 1.5, 2, 0.12, 0);
            }
            case ANGRY -> {
                applyPose(Pose.STANDING);
                face(look, 0f);
                if (t % 6 == 0) swingHand(m, t / 6);
                if (t % 10 == 0) fx(here, Particle.ANGRY_VILLAGER, s * 2.1, 1, 0.2, 0);
                if (t % 15 == 0) {
                    hop(m, 0.3);
                    fx(here, Particle.CLOUD, 0.05, 4, 0.2, 0.01);
                }
            }
            case SLEEPY -> {
                applyPose(t < 6 || t > emote.ticks - 6 ? Pose.SNEAKING : Pose.SLEEPING);
                if (t >= 6 && t % 15 == 0) fx(here, Particle.CLOUD, s * 0.8, 1, 0.05, 0.01);
            }
            case FAINT -> {
                if (t < 20) {
                    applyPose(Pose.STANDING);
                    face(emoteYaw + (float) Math.sin(t * 0.9) * 25f, (float) Math.sin(t * 0.7) * 15f);
                    stars(here, s, t);
                } else if (t < 55) {
                    if (t == 20) fx(here, Particle.POOF, 0.2, 6, 0.2, 0.01);
                    applyPose(Pose.SLEEPING);
                } else {
                    applyPose(Pose.SNEAKING);   // getting back up
                }
            }
            case DIZZY -> {
                applyPose(Pose.STANDING);
                face(emoteYaw + (float) Math.sin(t * 0.4) * 45f, (float) Math.sin(t * 0.9) * 20f);
                stars(here, s, t);
            }
            case PUNCH -> {
                applyPose(Pose.STANDING);
                face(look, 0f);
                if (t % 3 == 0) swingHand(m, t / 3);
                if (t % 6 == 0) {
                    double rad = Math.toRadians(look);
                    Location front = here.clone().add(-Math.sin(rad) * (0.3 + s * 0.5), s * 1.2, Math.cos(rad) * (0.3 + s * 0.5));
                    front.getWorld().spawnParticle(Particle.CRIT, front, 3, 0.05, 0.05, 0.05, 0.1);
                }
            }
            case LEAP -> {
                applyPose(Pose.STANDING);
                if (t == 0) {
                    hop(m, 0.7);
                    fx(here, Particle.CLOUD, 0.05, 6, 0.2, 0.02);
                }
                if (t < 14) face(emoteYaw + t * (360f / 14f), -10f);
                if (t == emote.ticks - 1) fx(here, Particle.END_ROD, s, 8, 0.25, 0.03);
            }
            case MOONWALK -> {
                if (t == 0) emoteYaw = look;
                face(emoteYaw, 0f);
                applyPose(t < 40 && (t / 4) % 2 == 0 ? Pose.SNEAKING : Pose.STANDING);
                if (m.hasGravity()) {
                    double rad = Math.toRadians(emoteYaw);
                    double speed = t < 40 ? 0.11 : 0.0;
                    // backwards = away from where it's facing
                    m.setVelocity(new Vector(Math.sin(rad) * speed, m.getVelocity().getY(), -Math.cos(rad) * speed));
                }
                if (t % 8 == 0 && t < 40) note(here, s);
            }
            case SALUTE -> {
                applyPose(Pose.STANDING);
                face(look, -8f);
                if (t == 0 || t == 4) m.swingMainHand();
                if (t % 12 == 0) fx(here, Particle.END_ROD, s * 2.0, 1, 0.1, 0.01);
            }
            case CELEBRATE -> {
                applyPose(Pose.STANDING);
                face(emoteYaw + t * 6f, -25f);
                if (t % 20 == 0) hop(m, 0.5);
                if (t % 5 == 0) swingHand(m, t / 5);
                if (t % 10 == 0) fx(here, Particle.FIREWORK, s * 2.4 + 0.4, 12, 0.05, 0.08);
                if (t == 0) fx(here, Particle.TOTEM_OF_UNDYING, s * 1.5, 15, 0.3, 0.2);
            }
            case MAGIC -> {
                applyPose(Pose.STANDING);
                if (t < 40) {
                    face(emoteYaw + t * 8f, 0f);
                    double angle = t * 0.5;
                    double r = 0.2 + s * 0.6;
                    double h = s * (0.3 + (t % 20) / 20.0 * 1.6);
                    Location p = here.clone().add(Math.cos(angle) * r, h, Math.sin(angle) * r);
                    p.getWorld().spawnParticle(Particle.ENCHANT, p, 3, 0.02, 0.02, 0.02, 0.2);
                    if (t % 10 == 0) swingHand(m, t / 10);
                } else if (t == 40) {
                    face(look, -10f);
                    fx(here, Particle.TOTEM_OF_UNDYING, s * 1.4, 20, 0.3, 0.3);
                    fx(here, Particle.POOF, s, 6, 0.2, 0.02);
                    here.getWorld().playSound(here, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.4f);
                    m.swingMainHand();
                }
            }
            case THINK -> {
                applyPose(Pose.STANDING);
                if (t < 45) {
                    face(emoteYaw + (float) Math.sin(t * 0.15) * 20f, -30f);
                    if (t == 0) m.swingMainHand();
                } else {
                    face(look, 0f);
                    if (t == 45) {
                        m.swingMainHand();
                        fx(here, Particle.END_ROD, s * 2.3 + 0.2, 6, 0.1, 0.02);
                        fx(here, Particle.HAPPY_VILLAGER, s * 2.0, 3, 0.2, 0);
                    }
                }
            }
            case WIGGLE -> {
                face(emoteYaw + (float) Math.sin(t * 1.4) * 22f, 0f);
                applyPose((t / 3) % 2 == 0 ? Pose.SNEAKING : Pose.STANDING);
                if (t % 8 == 0) swingHand(m, t / 8);
            }
        }
        if (emoteTick >= emote.ticks) endEmote();
    }

    /** Alternates hands: even n = main hand, odd n = off hand. */
    private static void swingHand(Mannequin m, int n) {
        if (n % 2 == 0) m.swingMainHand();
        else m.swingOffHand();
    }

    private static void hop(Mannequin m, double strength) {
        if (m.hasGravity() && m.isOnGround()) {
            Vector v = m.getVelocity();
            m.setVelocity(new Vector(v.getX(), strength, v.getZ()));
        }
    }

    private static void fx(Location at, Particle particle, double height, int count, double spread, double speed) {
        at.getWorld().spawnParticle(particle, at.clone().add(0, height, 0), count, spread, spread, spread, speed);
    }

    private static void note(Location at, double size) {
        // count 0: the X offset picks the note's color
        at.getWorld().spawnParticle(Particle.NOTE, at.clone().add(0, size * 2.2, 0),
                0, ThreadLocalRandom.current().nextDouble(), 0, 0, 1);
    }

    /** Little stars circling its head. */
    private static void stars(Location at, double size, int t) {
        if (t % 2 != 0) return;
        double r = 0.15 + size * 0.35;
        for (int k = 0; k < 3; k++) {
            double angle = t * 0.35 + k * (Math.PI * 2 / 3);
            Location p = at.clone().add(Math.cos(angle) * r, size * 2.0 + 0.1, Math.sin(angle) * r);
            p.getWorld().spawnParticle(Particle.END_ROD, p, 1, 0, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ mimic + outfit

    /**
     * Puts on what it should be wearing/holding. For each slot: its Gear tab item if there is one,
     * otherwise your armor (Wear my armor) / your held items (Copy me), otherwise nothing.
     */
    public void refreshEquipment(Player owner) {
        if (!isOut()) return;
        EntityEquipment eq = entity.getEquipment();
        PlayerInventory inv = owner.getInventory();

        ItemStack head = pick(gear.get(EquipmentSlot.HEAD), settings.outfit ? inv.getHelmet() : null);
        if (head == null || head.isEmpty()) head = plugin.festive().hat(holiday());   // holiday hat, if nothing else
        ItemStack chest = pick(gear.get(EquipmentSlot.CHEST), settings.outfit ? inv.getChestplate() : null);
        ItemStack legs = pick(gear.get(EquipmentSlot.LEGS), settings.outfit ? inv.getLeggings() : null);
        ItemStack feet = pick(gear.get(EquipmentSlot.FEET), settings.outfit ? inv.getBoots() : null);
        ItemStack main = pick(gear.get(EquipmentSlot.HAND), settings.mimic ? inv.getItemInMainHand() : null);
        ItemStack off = pick(gear.get(EquipmentSlot.OFF_HAND), settings.mimic ? inv.getItemInOffHand() : null);

        copyIfDifferent(head, eq.getHelmet(), eq::setHelmet);
        copyIfDifferent(chest, eq.getChestplate(), eq::setChestplate);
        copyIfDifferent(legs, eq.getLeggings(), eq::setLeggings);
        copyIfDifferent(feet, eq.getBoots(), eq::setBoots);
        copyIfDifferent(main, eq.getItemInMainHand(), eq::setItemInMainHand);
        copyIfDifferent(off, eq.getItemInOffHand(), eq::setItemInOffHand);
    }

    private static ItemStack pick(ItemStack gearItem, ItemStack fallback) {
        if (gearItem != null && !gearItem.isEmpty()) return gearItem;
        return fallback;
    }

    private static void copyIfDifferent(ItemStack source, ItemStack current,
                                        java.util.function.Consumer<ItemStack> setter) {
        boolean sourceEmpty = source == null || source.isEmpty();
        boolean currentEmpty = current == null || current.isEmpty();
        if (sourceEmpty && currentEmpty) return;
        if (!sourceEmpty && !currentEmpty && source.isSimilar(current)) return;
        setter.accept(sourceEmpty ? null : source.asOne());
    }

    // ------------------------------------------------------------------ trail

    private void trail(Mannequin m, boolean moving) {
        Particle particle = settings.trail.particle;
        if (particle == null) particle = holiday().particle;   // holiday trail when it has none of its own
        if (particle == null) return;
        int every = Math.max(1, plugin.getConfig().getInt("trail-interval", 3));
        if (!moving) every *= 3;  // gentler while it's standing around
        if (ticks % every != 0) return;

        double s = effectiveSize();
        Location at = m.getLocation().add(0, s * 0.9, 0);
        World world = m.getWorld();
        if (particle == Particle.NOTE) {
            // count 0 = the X offset picks the note color
            world.spawnParticle(Particle.NOTE, at.add(0, s * 1.2, 0), 0,
                    ThreadLocalRandom.current().nextDouble(), 0, 0, 1);
        } else {
            world.spawnParticle(particle, at, 1, 0.15 * s, 0.25 * s, 0.15 * s, 0.0);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Just in front of you and a little to the right, so you see it pop in. */
    private Location spawnSpot(Player owner) {
        Location o = owner.getLocation();
        double rad = Math.toRadians(o.getYaw());
        double fx = -Math.sin(rad), fz = Math.cos(rad);
        double rx = -Math.cos(rad), rz = -Math.sin(rad);
        Location spot = o.clone().add(fx * 1.3 + rx * 0.8, 0, fz * 1.3 + rz * 0.8);
        if (!spot.getBlock().isPassable()) spot = o.clone();
        spot.setYaw(o.getYaw() + 180f);   // facing you
        spot.setPitch(0f);
        return spot;
    }

    private void poof(Location at) {
        World world = at.getWorld();
        if (world == null) return;
        world.spawnParticle(Particle.POOF, at.clone().add(0, 0.3, 0), 10, 0.2, 0.2, 0.2, 0.02);
        world.playSound(at, Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.7f);
    }

    private static float lookYaw(Location from, Location to) {
        return yawOf(to.getX() - from.getX(), to.getZ() - from.getZ());
    }

    private static float yawOf(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static float wrap(float degrees) {
        degrees %= 360f;
        if (degrees >= 180f) degrees -= 360f;
        if (degrees < -180f) degrees += 360f;
        return degrees;
    }

    private static float lerpAngle(float from, float to, float t) {
        return from + wrap(to - from) * t;
    }

    private static void setBase(Mannequin m, Attribute attribute, double value) {
        AttributeInstance a = m.getAttribute(attribute);
        if (a != null) a.setBaseValue(value);
    }

    private static double attr(Player p, Attribute attribute, double fallback) {
        AttributeInstance a = p.getAttribute(attribute);
        return a == null ? fallback : a.getValue();
    }
}
