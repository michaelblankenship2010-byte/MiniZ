package dev.miniz;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A Mini Me's jobs: farming (harvest + replant grown crops) and hunting (hostile and/or passive mobs),
 * plus picking up dropped items into its backpack.
 */
final class MiniWorker {

    /** Crops that grow through ages and are harvested at max age (and can be replanted). */
    private static final Set<Material> AGE_CROPS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.COCOA);

    /** What to plant back for each crop. */
    private static final Map<Material, Material> SEEDS = Map.of(
            Material.WHEAT, Material.WHEAT_SEEDS,
            Material.CARROTS, Material.CARROT,
            Material.POTATOES, Material.POTATO,
            Material.BEETROOTS, Material.BEETROOT_SEEDS,
            Material.NETHER_WART, Material.NETHER_WART,
            Material.COCOA, Material.COCOA_BEANS);

    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    private final MiniZPlugin plugin;
    private final MiniMe mini;

    private LivingEntity targetMob;
    private Block targetBlock;
    private Item targetItem;
    private int targetAge;
    private int scanCooldown;
    private int actionCooldown;
    /** Things it gave up on for a while (unreachable, protected...): key -> tick it may try again. */
    private final Map<String, Integer> ignored = new HashMap<>();

    MiniWorker(MiniZPlugin plugin, MiniMe mini) {
        this.plugin = plugin;
        this.mini = mini;
    }

    // mining
    private Block mineBlock;
    private int mineProgress;

    // coming back to you with a full backpack
    private boolean delivering;
    private boolean announced;
    private boolean waitingForRoom;
    private int deliverAge;

    // a mob that attacked it
    private LivingEntity defendTarget;

    // "go mine that ore": the block you pointed at, and the tunnel it digs to get there
    private Block orderBlock;
    private Material orderType;
    private int orderAge;
    private int veinLeft;
    private int oresMined;
    private boolean tunnelling;      // gravity off, moving cell by cell through its own tunnel
    private Block digCell;           // tunnel block it's breaking right now
    private int digProgress;
    private int tunnelDug;
    private int moveStuck;

    void reset() {
        targetMob = null;
        targetBlock = null;
        targetItem = null;
        clearMine();
        targetAge = 0;
        scanCooldown = 0;
        delivering = false;
        announced = false;
        waitingForRoom = false;
        defendTarget = null;
        orderBlock = null;
        stopTunnelling();
    }

    /** Something hit it: fight back (when it's allowed to be attacked). */
    void defend(LivingEntity attacker) {
        if (attacker instanceof Mob && attacker.isValid() && !plugin.manager().isMini(attacker)) {
            defendTarget = attacker;
        }
    }

    boolean isDefending() {
        return defendTarget != null && defendTarget.isValid() && !defendTarget.isDead();
    }

    boolean isDelivering() {
        return delivering;
    }

    /**
     * One tick of work around {@code center} (you in Follow mode, its spot in Stay mode).
     * Returns whether it's moving, or null when there's nothing to do right now.
     */
    Boolean tick(Mannequin m, Player owner, Location center) {
        MiniSettings s = mini.settings();
        int now = mini.ticks();
        if (actionCooldown > 0) actionCooldown--;

        // Backpack full: walk back to you, hand it over and say so.
        if (delivering || (s.job != MiniSettings.Job.NONE && s.collect && mini.backpack().isFull()
                && plugin.getConfig().getBoolean("jobs.come-back-when-full", true))) {
            if (!delivering) startDelivery();
            return deliver(m, owner);
        }

        // Fighting back against something that attacked it beats any job.
        if (defendTarget != null && (!isDefending()
                || defendTarget.getLocation().distanceSquared(m.getLocation()) > sq(20))) {
            defendTarget = null;
        }
        if (defendTarget != null) {
            if (tunnelling) stopTunnelling();
            if (targetMob != defendTarget) {
                targetMob = defendTarget;
                targetBlock = null;
                targetItem = null;
                clearMine();
                targetAge = 0;
            }
            return fight(m, owner);
        }
        // You pointed at an ore: dig to it, mine it (and its vein), then come back.
        if (orderBlock != null) return doOrder(m, owner);

        if (s.job == MiniSettings.Job.NONE) return null;

        double farmRadius = plugin.getConfig().getDouble("jobs.farm-radius", 10);
        double huntRadius = plugin.getConfig().getDouble("jobs.hunt-radius", 10);
        double mineRadius = plugin.getConfig().getDouble("jobs.mine-radius", 8);
        double radius = Math.max(s.job.farms() ? farmRadius : 0, s.job.hunts() ? huntRadius : 0);
        if (s.job.mines()) radius = Math.max(radius, mineRadius);
        radius = Math.max(radius, 4);

        // Wandered too far from where it should be (e.g. you walked off): drop the work and come back.
        if (m.getLocation().distanceSquared(center) > sq(radius + 4)) {
            targetMob = null;
            targetBlock = null;
            targetItem = null;
            clearMine();
            return null;
        }

        // Is the current target still good?
        if (targetMob != null && (!huntable(targetMob, s) || targetMob.getLocation().distanceSquared(center) > sq(huntRadius + 3))) {
            targetMob = null;
        }
        if (targetBlock != null && (!s.job.farms() || !harvestable(targetBlock))) targetBlock = null;
        if (mineBlock != null && (!s.job.mines() || !mineable(mineBlock, s))) clearMine();
        if (targetItem != null && (!s.collect || !targetItem.isValid() || mini.backpack().isFull())) targetItem = null;

        // Grab anything right at its feet.
        if (s.collect && now % 5 == 0) vacuum(m, owner);

        // Mobs take priority: while farming, keep an eye out for them.
        if (s.job.hunts() && targetMob == null && (s.huntHostile || s.huntPassive)
                && (now % 20 == 0 || (targetBlock == null && targetItem == null && scanCooldown <= 0))) {
            LivingEntity mob = findMob(m, center, huntRadius, s);
            if (mob != null) {
                targetMob = mob;
                targetBlock = null;
                targetItem = null;
                targetAge = 0;
            }
        }
        boolean idle = targetMob == null && targetBlock == null && mineBlock == null && targetItem == null;
        if (idle && --scanCooldown <= 0) {
            scanCooldown = 10;
            if (s.job.farms()) targetBlock = findCrop(m, center, farmRadius);
            if (targetBlock == null && s.job.mines()) mineBlock = findOre(m, owner, center, mineRadius, s);
            if (targetBlock == null && mineBlock == null && s.collect && !mini.backpack().isFull()) {
                targetItem = findItem(m, center, radius);
            }
            targetAge = 0;
        }

        if (targetMob == null && targetBlock == null && mineBlock == null && targetItem == null) return null;

        // Can't get to it? Give up on it for a while.
        if (++targetAge > 200) {
            if (targetMob != null) ignore("e:" + targetMob.getUniqueId(), now, 600);
            if (targetBlock != null) ignore(blockKey(targetBlock), now, 1200);
            if (mineBlock != null) ignore(blockKey(mineBlock), now, 1200);
            if (targetItem != null) ignore("e:" + targetItem.getUniqueId(), now, 1200);
            targetMob = null;
            targetBlock = null;
            targetItem = null;
            clearMine();
            targetAge = 0;
            return null;
        }

        if (targetMob != null) return fight(m, owner);
        if (targetBlock != null) return farm(m, owner);
        if (mineBlock != null) return mine(m, owner);
        return fetch(m, owner);
    }

    // ================================================================== mining

    private boolean mineable(Block b, MiniSettings s) {
        MiniSettings.MineTarget t = MiniSettings.MineTarget.of(b.getType());
        return t != null && s.mine.contains(t);
    }

    /** Only blocks it can see: at least one side open to air (it doesn't tunnel through rock). */
    private static boolean exposed(Block b) {
        for (BlockFace face : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
                BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            if (b.getRelative(face).isPassable()) return true;
        }
        return false;
    }

    private Block findOre(Mannequin m, Player owner, Location center, double r, MiniSettings s) {
        if (s.mine.isEmpty()) return null;
        World world = center.getWorld();
        int radius = (int) Math.ceil(r);
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        Location from = m.getLocation();
        Location ownerLoc = owner.getLocation();
        int now = mini.ticks();
        Set<MiniSettings.MineTarget> ores = MiniSettings.MineTarget.ores();

        Block best = null;
        double bestScore = Double.MAX_VALUE;
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int y = cy - 4; y <= cy + 4; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    MiniSettings.MineTarget t = MiniSettings.MineTarget.of(b.getType());
                    if (t == null || !s.mine.contains(t)) continue;
                    // Never dig out the block you're standing on.
                    if (ownerLoc.getWorld().equals(world) && x == ownerLoc.getBlockX() && z == ownerLoc.getBlockZ()
                            && y == ownerLoc.getBlockY() - 1) continue;
                    if (!exposed(b) || isIgnored(blockKey(b), now)) continue;
                    double score = sq(x + 0.5 - from.getX()) + sq(y + 0.5 - from.getY()) + sq(z + 0.5 - from.getZ());
                    if (!ores.contains(t)) score += 400;   // ores first, then stone/dirt
                    if (score < bestScore) {
                        bestScore = score;
                        best = b;
                    }
                }
            }
        }
        // Make sure it can actually get something out of it with the tool it has.
        if (best != null && best.getDrops(miningTool(m, best), owner).isEmpty()) {
            ignore(blockKey(best), now, 2400);
            return null;
        }
        return best;
    }

    /** The pickaxe/shovel it uses: the one in its Gear (or copied hand) if that suits the block, else a built-in one. */
    private ItemStack miningTool(Mannequin m, Block b) {
        ItemStack held = m.getEquipment().getItemInMainHand();
        if (held != null && !held.isEmpty() && b.isPreferredTool(held)) return held;
        MiniSettings.MineTarget t = MiniSettings.MineTarget.of(b.getType());
        boolean shovel = t == MiniSettings.MineTarget.DIRT || t == MiniSettings.MineTarget.SAND
                || t == MiniSettings.MineTarget.CLAY;
        String name = shovel
                ? plugin.getConfig().getString("jobs.mine-shovel", "iron_shovel")
                : plugin.getConfig().getString("jobs.mine-tool", "iron_pickaxe");
        Material type = Material.matchMaterial(name == null ? "" : name);
        if (type == null) type = shovel ? Material.IRON_SHOVEL : Material.IRON_PICKAXE;
        return new ItemStack(type);
    }

    private int mineTicks(Block b, ItemStack tool) {
        float hardness = b.getType().getHardness();
        float speed = Math.max(1f, b.getDestroySpeed(tool, true));
        double ticks = hardness * (b.isPreferredTool(tool) ? 30.0 : 100.0) / speed;
        ticks *= plugin.getConfig().getDouble("jobs.mine-time-multiplier", 1.0);
        return (int) Math.max(4, Math.min(240, Math.ceil(ticks)));
    }

    private boolean mine(Mannequin m, Player owner) {
        Block b = mineBlock;
        Location c = b.getLocation().add(0.5, 0.5, 0.5);
        Location body = m.getLocation().add(0, Math.max(0.4, mini.effectiveSize() * 0.9), 0);
        mini.lookAt(c);

        double reach = plugin.getConfig().getDouble("jobs.mine-reach", 2.8);
        if (body.distanceSquared(c) <= sq(reach)) {
            holdInPlace(m);
            targetAge = 0;   // it's busy, not stuck
            ItemStack tool = miningTool(m, b);
            int total = mineTicks(b, tool);
            mineProgress++;
            if (mineProgress % 5 == 1) {
                m.swingMainHand();
                m.getWorld().playSound(c, b.getBlockData().getSoundGroup().getHitSound(), 0.6f, 0.9f);
            }
            crack(b, Math.min(1f, mineProgress / (float) total), m.getEntityId());
            if (mineProgress >= total) {
                breakBlock(m, owner, b, tool);
                mineBlock = null;
                mineProgress = 0;
                stopTunnelling();
                scanCooldown = 0;
                actionCooldown = 3;
            }
            return false;
        }
        if (mineProgress > 0) {
            crack(b, 0f, m.getEntityId());
            mineProgress = 0;
        }
        // Can't walk there (it's behind rock or up a wall)? Dig a little tunnel to it.
        if ((targetAge > 60 || tunnelling) && plugin.getConfig().getBoolean("jobs.auto-tunnel", true)) {
            Boolean moving = tunnelStep(m, owner, b, plugin.getConfig().getInt("jobs.auto-tunnel-max", 12));
            if (moving == null) {
                stopTunnelling();
                ignore(blockKey(b), mini.ticks(), 2400);
                mineBlock = null;
                return false;
            }
            return moving;
        }
        return mini.stepToward(m, c, mini.walkSpeed());
    }

    /** Mines an ore it reached. Returns false if a protection plugin said no. */
    private boolean breakBlock(Mannequin m, Player owner, Block b, ItemStack tool) {
        BlockData before = b.getBlockData();
        MiniSettings.MineTarget t = MiniSettings.MineTarget.of(b.getType());
        Location c = b.getLocation().add(0.5, 0.5, 0.5);

        if (plugin.getConfig().getBoolean("jobs.respect-protection", true)) {
            BlockBreakEvent check = new BlockBreakEvent(b, owner);
            Bukkit.getPluginManager().callEvent(check);
            if (check.isCancelled()) {
                crack(b, 0f, m.getEntityId());
                ignore(blockKey(b), mini.ticks(), 6000);
                return false;
            }
        }

        List<ItemStack> drops = new ArrayList<>(b.getDrops(tool, owner));
        b.setType(Material.AIR);
        World world = b.getWorld();
        world.spawnParticle(Particle.BLOCK, c, 16, 0.3, 0.3, 0.3, 0, before);
        world.playSound(c, before.getSoundGroup().getBreakSound(), 0.9f, 1.0f);
        m.swingMainHand();

        // Ores give XP like they do for you (not with Silk Touch).
        if (t != null && t.xpMax > 0 && !tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
            int xp = t.xpMin + rand(t.xpMax - t.xpMin + 1);
            if (xp > 0) world.spawn(c, ExperienceOrb.class, orb -> orb.setExperience(xp));
        }
        store(owner, drops, c);
        return true;
    }

    /** Shows the block-cracking animation to everyone nearby. */
    private static void crack(Block b, float progress, int sourceId) {
        Location loc = b.getLocation();
        for (Player p : b.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) < 48 * 48) p.sendBlockDamage(loc, progress, sourceId);
        }
    }

    private void clearMine() {
        if (orderBlock == null) stopTunnelling();
        if (mineBlock != null && mineProgress > 0 && mini.isOut()) crack(mineBlock, 0f, mini.entity().getEntityId());
        mineBlock = null;
        mineProgress = 0;
    }

    // ================================================================== "mine that ore" (pointing)

    boolean hasOrder() {
        return orderBlock != null;
    }

    /**
     * Sends it to mine a block you're looking at, digging a tunnel if it has to.
     * Returns null if it's on its way, otherwise the message saying why not.
     */
    String order(Mannequin m, Player owner, Block target) {
        MiniSettings.MineTarget type = MiniSettings.MineTarget.of(target.getType());
        if (type == null) return L.t("cmd.mine.this-not-ore", "That isn't something it can mine.");
        if (!target.getWorld().equals(m.getWorld())) return L.t("cmd.not-out", "Bring your Mini Me out first (/miniz on).");
        double range = plugin.getConfig().getDouble("jobs.point-range", 48);
        if (target.getLocation().add(0.5, 0.5, 0.5).distanceSquared(m.getLocation()) > sq(range + 8)) {
            return L.t("cmd.mine.this-too-far", "That's too far away for it to dig to.");
        }
        reset();
        orderBlock = target;
        orderType = target.getType();
        orderAge = 0;
        oresMined = 0;
        veinLeft = Math.max(0, plugin.getConfig().getInt("jobs.vein-limit", 16));
        mini.ensureGrounded();
        target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0.5, 0.5, 0.5), 12, 0.3, 0.3, 0.3, 0.02);
        mini.speak(owner, L.t("speech.order.start", "On it! Digging to that {thing}.", "thing", L.parse(type.label())));
        return null;
    }

    void cancelOrder() {
        orderBlock = null;
        stopTunnelling();
    }

    private Boolean doOrder(Mannequin m, Player owner) {
        Block b = orderBlock;
        // Gone already (someone else mined it)?
        if (b.getType() != orderType) {
            finishOrder(m, owner, oresMined > 0 ? null
                    : L.t("speech.order.gone", "Someone already mined that one!"));
            return false;
        }
        if (!owner.getWorld().equals(m.getWorld())
                || owner.getLocation().distanceSquared(m.getLocation()) > sq(plugin.getConfig().getDouble("jobs.point-range", 48) + 16)) {
            finishOrder(m, owner, L.t("speech.order.you-left", "You went too far away, so I came back."));
            return false;
        }
        if (++orderAge > 20 * 120) {
            finishOrder(m, owner, L.t("speech.order.gave-up", "I couldn't get to it, sorry."));
            return false;
        }

        Location c = b.getLocation().add(0.5, 0.5, 0.5);
        Location body = m.getLocation().add(0, Math.max(0.4, mini.effectiveSize() * 0.9), 0);
        mini.lookAt(c);
        if (body.distanceSquared(c) <= sq(plugin.getConfig().getDouble("jobs.mine-reach", 2.8))) {
            holdInPlace(m);
            ItemStack tool = miningTool(m, b);
            int total = mineTicks(b, tool);
            mineProgress++;
            if (mineProgress % 5 == 1) {
                m.swingMainHand();
                m.getWorld().playSound(c, b.getBlockData().getSoundGroup().getHitSound(), 0.6f, 0.9f);
            }
            crack(b, Math.min(1f, mineProgress / (float) total), m.getEntityId());
            if (mineProgress >= total) {
                mineProgress = 0;
                if (b.getDrops(tool, owner).isEmpty()) {
                    crack(b, 0f, m.getEntityId());
                    finishOrder(m, owner, L.t("speech.order.need-better-tool",
                            "I need a better pickaxe for that. Put one in my Gear!"));
                    return false;
                }
                if (!breakBlock(m, owner, b, tool)) {
                    finishOrder(m, owner, L.t("speech.order.protected", "I'm not allowed to dig there."));
                    return false;
                }
                oresMined++;
                // Keep going along the vein: touching blocks of the same kind.
                Block next = veinLeft-- > 0 ? nextInVein(b, orderType) : null;
                if (next != null) {
                    orderBlock = next;
                    orderAge = 0;
                } else {
                    finishOrder(m, owner, null);
                }
            }
            return false;
        }
        if (mineProgress > 0) {
            crack(b, 0f, m.getEntityId());
            mineProgress = 0;
        }
        Boolean moving = tunnelStep(m, owner, b, plugin.getConfig().getInt("jobs.tunnel-max", 64));
        if (moving == null) {
            finishOrder(m, owner, lastTunnelProblem);
            return false;
        }
        return moving;
    }

    /** Wrap up: say how it went, stop tunnelling and pop back to you. */
    private void finishOrder(Mannequin m, Player owner, String problem) {
        orderBlock = null;
        stopTunnelling();
        if (problem != null) {
            mini.speak(owner, problem);
        } else {
            mini.speak(owner, oresMined <= 1
                    ? L.t("speech.order.done", "Got it!")
                    : L.t("speech.order.done-vein", "Got it! I mined the whole vein ({count} blocks).", "count", oresMined));
        }
        if (plugin.getConfig().getBoolean("jobs.come-back-after-digging", true)) mini.popTo(owner);
    }

    private static Block nextInVein(Block from, Material type) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    Block b = from.getRelative(dx, dy, dz);
                    if (b.getType() == type) return b;
                }
        return null;
    }

    // ------------------------------------------------------------------ tunnelling

    private String lastTunnelProblem;

    /** Natural blocks it's allowed to dig through (never builds, chests, bedrock...). */
    private static boolean diggable(Block b) {
        Material t = b.getType();
        if (MiniSettings.MineTarget.of(t) != null) return true;
        if (Tag.BASE_STONE_OVERWORLD.isTagged(t) || Tag.BASE_STONE_NETHER.isTagged(t) || Tag.DIRT.isTagged(t)) return true;
        return t == Material.GRAVEL || t == Material.SAND || t == Material.RED_SAND || t == Material.CLAY
                || t == Material.GRASS_BLOCK || t == Material.SANDSTONE || t == Material.RED_SANDSTONE
                || t == Material.CALCITE || t == Material.DRIPSTONE_BLOCK || t == Material.SMOOTH_BASALT
                || t == Material.END_STONE || t == Material.SOUL_SAND || t == Material.SOUL_SOIL
                || t == Material.MAGMA_BLOCK || t == Material.TERRACOTTA || t == Material.SNOW_BLOCK
                || t == Material.POWDER_SNOW || t == Material.MOSS_BLOCK || t == Material.MUD
                || t == Material.PACKED_MUD || t == Material.AMETHYST_BLOCK || t == Material.BUDDING_AMETHYST
                || t.name().endsWith("_TERRACOTTA") && !t.name().contains("GLAZED");
    }

    private static boolean nearLava(Block b) {
        if (b.getType() == Material.LAVA) return true;
        for (BlockFace f : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST}) {
            if (b.getRelative(f).getType() == Material.LAVA) return true;
        }
        return false;
    }

    private void startTunnelling(Mannequin m) {
        if (tunnelling) return;
        tunnelling = true;
        tunnelDug = 0;
        moveStuck = 0;
        m.setGravity(false);
        m.setVelocity(new org.bukkit.util.Vector());
    }

    void stopTunnelling() {
        if (digCell != null && mini.isOut()) crack(digCell, 0f, mini.entity().getEntityId());
        digCell = null;
        digProgress = 0;
        if (tunnelling) {
            tunnelling = false;
            mini.restoreGravity();
        }
    }

    private void holdInPlace(Mannequin m) {
        if (tunnelling) m.setVelocity(new org.bukkit.util.Vector());
        else mini.holdStill(m);
    }

    /**
     * One tick of digging towards {@code target}: picks the next block along the straightest
     * path (trying other directions if one is blocked), breaks it, then floats into the gap.
     * Returns whether it's moving, or null if it can't go on (reason in lastTunnelProblem).
     */
    private Boolean tunnelStep(Mannequin m, Player owner, Block target, int maxBlocks) {
        startTunnelling(m);
        Block cell = m.getLocation().getBlock();
        int dx = target.getX() - cell.getX(), dy = target.getY() - cell.getY(), dz = target.getZ() - cell.getZ();

        // Already breaking a tunnel block: keep at it.
        if (digCell != null) {
            m.setVelocity(new org.bukkit.util.Vector());
            return digTunnelBlock(m, owner) ? false : null;
        }

        // Directions to try, best first: the axis with the most distance left, then the others.
        List<BlockFace> options = new ArrayList<>();
        int[][] axes = {{Math.abs(dx), 0}, {Math.abs(dy), 1}, {Math.abs(dz), 2}};
        java.util.Arrays.sort(axes, (a, b) -> b[0] - a[0]);
        for (int[] axis : axes) {
            if (axis[0] == 0) continue;
            options.add(switch (axis[1]) {
                case 0 -> dx > 0 ? BlockFace.EAST : BlockFace.WEST;
                case 1 -> dy > 0 ? BlockFace.UP : BlockFace.DOWN;
                default -> dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
            });
        }
        if (options.isEmpty()) return false;

        Location ownerFeet = owner.getLocation();
        lastTunnelProblem = L.t("speech.order.blocked", "I can't dig through that.");
        for (BlockFace face : options) {
            Block next = cell.getRelative(face);
            if (next.equals(target)) continue;                      // the ore itself gets mined, not tunnelled
            if (nearLava(next)) {
                lastTunnelProblem = L.t("speech.order.lava", "There's lava in the way. I'm not going in there!");
                continue;
            }
            if (next.isPassable()) return floatInto(m, next);      // open already
            // Never dig out the block you're standing on.
            if (ownerFeet.getWorld().equals(next.getWorld()) && next.getX() == ownerFeet.getBlockX()
                    && next.getZ() == ownerFeet.getBlockZ() && next.getY() == ownerFeet.getBlockY() - 1) continue;
            if (!diggable(next) || next.getType().getHardness() < 0) continue;
            if (tunnelDug >= maxBlocks) {
                lastTunnelProblem = L.t("speech.order.too-long", "That's a really long way to dig. Bring me closer!");
                return null;
            }
            digCell = next;
            digProgress = 0;
            return false;
        }
        return null;
    }

    /** Breaks the current tunnel block a bit more. Returns false if it isn't allowed to. */
    private boolean digTunnelBlock(Mannequin m, Player owner) {
        Block b = digCell;
        if (b.isPassable()) {   // something else cleared it
            digCell = null;
            return true;
        }
        Location c = b.getLocation().add(0.5, 0.5, 0.5);
        mini.lookAt(c);
        ItemStack tool = miningTool(m, b);
        int total = Math.max(3, (int) Math.ceil(mineTicks(b, tool)
                * plugin.getConfig().getDouble("jobs.tunnel-speed-multiplier", 0.6)));
        digProgress++;
        if (digProgress % 4 == 1) {
            m.swingMainHand();
            m.getWorld().playSound(c, b.getBlockData().getSoundGroup().getHitSound(), 0.5f, 1.0f);
        }
        crack(b, Math.min(1f, digProgress / (float) total), m.getEntityId());
        if (digProgress < total) return true;

        // Protection plugins get their say, as if you dug it.
        if (plugin.getConfig().getBoolean("jobs.respect-protection", true)) {
            BlockBreakEvent check = new BlockBreakEvent(b, owner);
            Bukkit.getPluginManager().callEvent(check);
            if (check.isCancelled()) {
                crack(b, 0f, m.getEntityId());
                lastTunnelProblem = L.t("speech.order.protected", "I'm not allowed to dig there.");
                digCell = null;
                return false;
            }
        }
        BlockData before = b.getBlockData();
        // Ores it digs through on the way are kept; plain stone/dirt only if the config says so.
        boolean keep = MiniSettings.MineTarget.ores().contains(MiniSettings.MineTarget.of(b.getType()))
                || plugin.getConfig().getBoolean("jobs.tunnel-keep-drops", false);
        List<ItemStack> drops = keep ? new ArrayList<>(b.getDrops(tool, owner)) : List.of();
        b.setType(Material.AIR);
        b.getWorld().spawnParticle(Particle.BLOCK, c, 10, 0.3, 0.3, 0.3, 0, before);
        b.getWorld().playSound(c, before.getSoundGroup().getBreakSound(), 0.7f, 1.0f);
        if (!drops.isEmpty()) store(owner, drops, c);
        tunnelDug++;
        digCell = null;
        digProgress = 0;
        return true;
    }

    /** Glides into an open neighbouring block (gravity is off while tunnelling). */
    private boolean floatInto(Mannequin m, Block next) {
        Location here = m.getLocation();
        Location dest = next.getLocation().add(0.5, 0.05, 0.5);
        org.bukkit.util.Vector to = dest.toVector().subtract(here.toVector());
        double dist = to.length();
        if (dist < 0.12) {
            m.teleport(new Location(dest.getWorld(), dest.getX(), dest.getY(), dest.getZ(), here.getYaw(), here.getPitch()));
            m.setVelocity(new org.bukkit.util.Vector());
            moveStuck = 0;
            return false;
        }
        double speed = Math.min(dist, plugin.getConfig().getDouble("jobs.tunnel-move-speed", 0.18));
        m.setVelocity(to.normalize().multiply(speed));
        // Wedged on a corner? Nudge it into the gap.
        if (++moveStuck > 30) {
            m.teleport(new Location(dest.getWorld(), dest.getX(), dest.getY(), dest.getZ(), here.getYaw(), here.getPitch()));
            moveStuck = 0;
        }
        return true;
    }

    // ================================================================== backpack full: come back to you

    private void startDelivery() {
        stopTunnelling();   // walk back normally (an unfinished dig order picks up again afterwards)
        delivering = true;
        announced = false;
        waitingForRoom = false;
        deliverAge = 0;
        targetMob = null;
        targetBlock = null;
        targetItem = null;
        clearMine();
    }

    private Boolean deliver(Mannequin m, Player owner) {
        MiniBackpack backpack = mini.backpack();

        // You made room: back to work.
        if (waitingForRoom && !backpack.isFull()) {
            mini.speak(owner, L.t("speech.back-to-work", "Thanks! Back to work."));
            delivering = false;
            waitingForRoom = false;
            return null;
        }

        if (!owner.getWorld().equals(m.getWorld())) {
            if (!announced) {
                announced = true;
                waitingForRoom = true;
                mini.speak(owner, L.t("speech.full-other-world", "I'm full! Come get your stuff, or empty my backpack."), true);
            }
            mini.holdStill(m);
            return false;
        }

        Location o = owner.getLocation();
        double dist = m.getLocation().distance(o);
        if (!announced) {
            // Too far, or stuck on the way: just pop over to you.
            if (dist > 48 || ++deliverAge > 200) {
                mini.popTo(owner);
                return false;
            }
            if (dist > 2.0) return mini.stepToward(m, o, mini.runSpeed());
            arrive(m, owner);
            return false;
        }

        // Waiting next to you for room in the backpack.
        if (dist > 3.0) return mini.stepToward(m, o, mini.runSpeed());
        mini.holdStill(m);
        mini.lookAt(owner.getEyeLocation());
        return false;
    }

    private void arrive(Mannequin m, Player owner) {
        announced = true;
        mini.holdStill(m);
        mini.lookAt(owner.getEyeLocation());
        m.swingMainHand();
        MiniBackpack backpack = mini.backpack();

        int given = plugin.getConfig().getBoolean("jobs.give-to-owner", true) ? backpack.giveTo(owner) : 0;
        if (given > 0) {
            owner.playSound(owner.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
        }

        if (!backpack.isFull()) {
            mini.speak(owner, L.t("speech.full-delivered", "I'm full! Here's what I found ({count} items).", "count", given));
            delivering = false;   // back to work next tick
            return;
        }
        waitingForRoom = true;
        if (given > 0) {
            mini.speak(owner, L.t("speech.full-you-too", "I'm full! Your inventory's full too, so I kept the rest."), true);
        } else {
            mini.speak(owner, L.t("speech.full-waiting", "I'm full! Empty my backpack and I'll get back to work."), true);
        }
    }

    // ================================================================== hunting

    private boolean fight(Mannequin m, Player owner) {
        LivingEntity target = targetMob;
        Location here = m.getLocation();
        Location there = target.getLocation();
        mini.lookAt(target.getEyeLocation());

        double reach = 1.4 + target.getWidth() / 2 + mini.effectiveSize() * 0.5;
        double flat = Math.hypot(there.getX() - here.getX(), there.getZ() - here.getZ());
        double dy = there.getY() - here.getY();
        if (flat <= reach && dy > -2.0 && dy < 2.0) {
            mini.holdStill(m);
            if (actionCooldown <= 0) {
                attack(m, owner, target);
                actionCooldown = Math.max(4, plugin.getConfig().getInt("jobs.attack-cooldown", 15));
                targetAge = 0;
            }
            return false;
        }
        return mini.stepToward(m, there, mini.runSpeed());
    }

    private void attack(Mannequin m, Player owner, LivingEntity target) {
        m.swingMainHand();
        double damage = weaponDamage(m.getEquipment().getItemInMainHand())
                * plugin.getConfig().getDouble("jobs.damage-multiplier", 1.0);
        // Caused by you (so it counts as your kill: XP + player-only drops), dealt by the Mini Me.
        DamageSource source = DamageSource.builder(DamageType.MOB_ATTACK)
                .withCausingEntity(owner)
                .withDirectEntity(m)
                .build();
        plugin.manager().recordHit(target, mini.ownerId());
        target.damage(damage, source);
        // When minis can be attacked, the mob turns on the Mini Me instead of you.
        if (mini.settings().aggro && target instanceof Mob mob && target.isValid() && !target.isDead()) {
            mob.setTarget(m);
        }
        target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, target.getHeight() * 0.6, 0),
                6, 0.2, 0.2, 0.2, 0.1);
    }

    private double weaponDamage(ItemStack item) {
        double base = plugin.getConfig().getDouble("jobs.base-damage", 3.0);
        if (item == null || item.isEmpty()) return base;
        String id = item.getType().getKey().getKey().toLowerCase(Locale.ROOT);
        int tier = id.startsWith("netherite_") ? 4
                : id.startsWith("diamond_") ? 3
                : id.startsWith("iron_") ? 2
                : (id.startsWith("stone_") || id.startsWith("copper_")) ? 1 : 0;
        if (id.endsWith("_sword")) return 4 + tier;
        if (id.endsWith("_axe")) return 7 + (tier >= 1 ? 2 : 0) + (tier >= 4 ? 1 : 0);
        if (id.equals("trident")) return 9;
        if (id.equals("mace")) return 6;
        return base;
    }

    private LivingEntity findMob(Mannequin m, Location center, double r, MiniSettings s) {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        int now = mini.ticks();
        for (LivingEntity e : center.getWorld().getNearbyLivingEntities(center, r, r / 2 + 2, r)) {
            if (!huntable(e, s) || isIgnored("e:" + e.getUniqueId(), now)) continue;
            if (!m.hasLineOfSight(e)) continue;
            double d = e.getLocation().distanceSquared(m.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    private boolean huntable(LivingEntity e, MiniSettings s) {
        if (!(e instanceof Mob)) return false;                    // no players, armor stands, mannequins...
        if (!e.isValid() || e.isDead() || e.isInvulnerable()) return false;
        if (plugin.manager().isMini(e)) return false;
        if (e instanceof Tameable t && t.isTamed()) return false; // never your (or anyone's) pets
        if (e.isLeashed()) return false;
        if (e.customName() != null && !plugin.getConfig().getBoolean("jobs.hunt-named", false)) return false;
        String type = e.getType().getKey().getKey().toLowerCase(Locale.ROOT);
        for (String never : plugin.getConfig().getStringList("jobs.never-hunt")) {
            if (never.equalsIgnoreCase(type)) return false;
        }

        if (e instanceof Enemy) return s.huntHostile;
        if (e instanceof Animals) {
            if (!s.huntPassive) return false;
            if (e instanceof org.bukkit.entity.Ageable baby && !baby.isAdult()
                    && !plugin.getConfig().getBoolean("jobs.hunt-babies", false)) return false;
            return true;
        }
        return false; // villagers, golems, etc.
    }

    // ================================================================== farming

    private boolean farm(Mannequin m, Player owner) {
        Block block = targetBlock;
        Location c = block.getLocation().add(0.5, 0.5, 0.5);
        Location here = m.getLocation();
        mini.lookAt(c);

        double flat = Math.hypot(c.getX() - here.getX(), c.getZ() - here.getZ());
        double dy = c.getY() - here.getY();
        if (flat <= 1.5 && dy > -1.5 && dy < 2.6) {
            mini.holdStill(m);
            if (actionCooldown <= 0) {
                harvest(m, owner, block);
                targetBlock = null;
                scanCooldown = 0;
                actionCooldown = Math.max(2, plugin.getConfig().getInt("jobs.harvest-cooldown", 6));
            }
            return false;
        }
        return mini.stepToward(m, c, mini.walkSpeed());
    }

    private Block findCrop(Mannequin m, Location center, double r) {
        World world = center.getWorld();
        int radius = (int) Math.ceil(r);
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        Location from = m.getLocation();
        int now = mini.ticks();

        Block best = null;
        double bestDist = Double.MAX_VALUE;
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int y = cy - 2; y <= cy + 3; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (!harvestable(b)) continue;
                    if (isIgnored(blockKey(b), now)) continue;
                    double d = sq(x + 0.5 - from.getX()) + sq(y - from.getY()) + sq(z + 0.5 - from.getZ());
                    if (d < bestDist) {
                        bestDist = d;
                        best = b;
                    }
                }
            }
        }
        return best;
    }

    private boolean harvestable(Block b) {
        Material type = b.getType();
        if (AGE_CROPS.contains(type)) {
            return b.getBlockData() instanceof Ageable a && a.getAge() >= a.getMaximumAge();
        }
        if (type == Material.SWEET_BERRY_BUSH) {
            return b.getBlockData() instanceof Ageable a && a.getAge() >= 2;
        }
        if (type == Material.MELON) return grownFromStem(b, Material.ATTACHED_MELON_STEM);
        if (type == Material.PUMPKIN) return grownFromStem(b, Material.ATTACHED_PUMPKIN_STEM);
        if (type == Material.SUGAR_CANE || type == Material.BAMBOO) {
            // The 2nd block of a stalk: harvesting there leaves the bottom to regrow.
            return b.getRelative(BlockFace.DOWN).getType() == type
                    && b.getRelative(BlockFace.DOWN, 2).getType() != type;
        }
        return false;
    }

    /** Only melons/pumpkins a stem grew, never ones you placed for decoration. */
    private static boolean grownFromStem(Block fruit, Material stemType) {
        for (BlockFace face : SIDES) {
            Block side = fruit.getRelative(face);
            if (side.getType() == stemType && side.getBlockData() instanceof Directional d
                    && d.getFacing() == face.getOppositeFace()) {
                return true;
            }
        }
        return false;
    }

    private void harvest(Mannequin m, Player owner, Block b) {
        MiniSettings s = mini.settings();
        BlockData before = b.getBlockData();
        Material type = b.getType();
        Location c = b.getLocation().add(0.5, 0.5, 0.5);

        // Ask protection plugins (claims, spawn protection...) whether you could break this.
        if (plugin.getConfig().getBoolean("jobs.respect-protection", true)) {
            BlockBreakEvent check = new BlockBreakEvent(b, owner);
            Bukkit.getPluginManager().callEvent(check);
            if (check.isCancelled()) {
                ignore(blockKey(b), mini.ticks(), 6000);
                return;
            }
        }

        List<ItemStack> drops = new ArrayList<>();
        if (type == Material.SWEET_BERRY_BUSH) {
            Ageable bush = (Ageable) before.clone();
            int count = bush.getAge() >= 3 ? 2 + rand(2) : 1 + rand(2);
            drops.add(new ItemStack(Material.SWEET_BERRIES, count));
            bush.setAge(1);
            b.setBlockData(bush);
        } else if (type == Material.SUGAR_CANE || type == Material.BAMBOO) {
            Block top = b;
            for (int i = 0; i < 32 && top.getRelative(BlockFace.UP).getType() == type; i++) {
                top = top.getRelative(BlockFace.UP);
            }
            // Top-down, without physics, so nothing pops off as loose items.
            for (Block cur = top; ; cur = cur.getRelative(BlockFace.DOWN)) {
                drops.add(new ItemStack(type));
                cur.setType(Material.AIR, false);
                if (cur.equals(b)) break;
            }
        } else if (type == Material.MELON || type == Material.PUMPKIN) {
            drops.addAll(b.getDrops(null, owner));
            b.setType(Material.AIR);
        } else {
            drops.addAll(b.getDrops(null, owner));
            Material seed = SEEDS.get(type);
            boolean replanted = false;
            if (s.replant && seed != null && (removeOne(drops, seed) || mini.backpack().takeOne(seed))) {
                Ageable crop = (Ageable) before.clone();
                crop.setAge(0);
                b.setBlockData(crop);
                replanted = true;
            }
            if (!replanted) b.setType(Material.AIR);
        }

        m.swingMainHand();
        World world = b.getWorld();
        world.spawnParticle(Particle.BLOCK, c, 12, 0.25, 0.25, 0.25, 0, before);
        world.playSound(c, before.getSoundGroup().getBreakSound(), 0.8f, 1.0f);
        store(owner, drops, c);
    }

    private static boolean removeOne(List<ItemStack> items, Material type) {
        for (Iterator<ItemStack> it = items.iterator(); it.hasNext(); ) {
            ItemStack item = it.next();
            if (item.getType() != type) continue;
            if (item.getAmount() <= 1) it.remove();
            else item.setAmount(item.getAmount() - 1);
            return true;
        }
        return false;
    }

    // ================================================================== picking things up

    private boolean fetch(Mannequin m, Player owner) {
        Location there = targetItem.getLocation();
        mini.lookAt(there);
        Location here = m.getLocation();
        if (Math.hypot(there.getX() - here.getX(), there.getZ() - here.getZ()) <= 0.9) {
            vacuum(m, owner);
            targetItem = null;
            return false;
        }
        return mini.stepToward(m, there, mini.walkSpeed());
    }

    private Item findItem(Mannequin m, Location center, double r) {
        Item best = null;
        double bestDist = Double.MAX_VALUE;
        int now = mini.ticks();
        for (Item item : center.getWorld().getNearbyEntitiesByType(Item.class, center, r, r / 2 + 2, r)) {
            if (!collectible(item) || isIgnored("e:" + item.getUniqueId(), now)) continue;
            double d = item.getLocation().distanceSquared(m.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = item;
            }
        }
        return best;
    }

    /** Scoops up items right next to it into the backpack. */
    void vacuum(Mannequin m, Player owner) {
        double reach = 1.2 + mini.effectiveSize();
        boolean picked = false;
        for (Item item : m.getWorld().getNearbyEntitiesByType(Item.class, m.getLocation(), reach)) {
            if (!collectible(item)) continue;
            List<ItemStack> left = mini.backpack().add(List.of(item.getItemStack()));
            if (left.isEmpty()) {
                item.remove();
                picked = true;
            } else {
                int before = item.getItemStack().getAmount();
                item.setItemStack(left.get(0));
                if (left.get(0).getAmount() < before) picked = true;
                mini.warnBackpackFull(owner);
            }
        }
        if (picked) {
            m.getWorld().playSound(m.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.6f);
        }
    }

    private boolean collectible(Item item) {
        if (!item.isValid() || item.getItemStack().isEmpty()) return false;
        if (item.getThrower() != null) return false;          // never grab things players dropped
        if (item.getPickupDelay() > 0) return false;
        UUID reservedFor = item.getOwner();
        return reservedFor == null || reservedFor.equals(mini.ownerId());
    }

    // ================================================================== helpers

    private void store(Player owner, List<ItemStack> items, Location at) {
        if (!mini.settings().collect) {
            for (ItemStack item : items) at.getWorld().dropItemNaturally(at, item);
            return;
        }
        List<ItemStack> left = mini.backpack().add(items);
        for (ItemStack item : left) at.getWorld().dropItemNaturally(at, item);
        if (!left.isEmpty()) mini.warnBackpackFull(owner);
    }

    private void ignore(String key, int now, int forTicks) {
        if (ignored.size() > 256) ignored.entrySet().removeIf(e -> e.getValue() <= now);
        ignored.put(key, now + forTicks);
    }

    private boolean isIgnored(String key, int now) {
        Integer until = ignored.get(key);
        return until != null && until > now;
    }

    private static String blockKey(Block b) {
        return "b:" + b.getWorld().getName() + ":" + b.getX() + "," + b.getY() + "," + b.getZ();
    }

    private static int rand(int bound) {
        return ThreadLocalRandom.current().nextInt(bound);
    }

    private static double sq(double v) {
        return v * v;
    }
}
