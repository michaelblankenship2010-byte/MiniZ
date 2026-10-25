package dev.miniz;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class MiniListener implements Listener {

    private final MiniZPlugin plugin;

    MiniListener(MiniZPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // A few ticks later so the player is fully placed in the world first.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) plugin.manager().restore(player);
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.manager().unload(event.getPlayer());
    }

    /** Right-click your own Mini Me to open the menu; anyone else gets a wave. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!plugin.manager().isMini(event.getRightClicked())) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;

        MiniMe mini = plugin.manager().ofEntity(event.getRightClicked());
        if (mini == null) return;
        Player clicker = event.getPlayer();
        if (mini.ownerId().equals(clicker.getUniqueId())) {
            if (clicker.isSneaking()) mini.backpack().open(clicker);   // sneak + right-click = backpack
            else MiniMenu.open(plugin, clicker);
        } else {
            mini.waveAt(clicker);
        }
    }

    /** Sneak + punch the air while looking at an ore (empty hand or a pickaxe): your Mini Me digs to it. */
    @EventHandler
    public void onPoint(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.LEFT_CLICK_AIR) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!player.isSneaking() || !plugin.getConfig().getBoolean("jobs.point-to-mine", true)) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean pickaxe = held.getType().getKey().getKey().endsWith("_pickaxe");
        if (!held.isEmpty() && !pickaxe) return;
        MiniMe mini = plugin.manager().of(player);
        if (!mini.isOut()) return;
        int range = (int) plugin.getConfig().getDouble("jobs.point-range", 48);
        org.bukkit.block.Block target = player.getTargetBlockExact(range, org.bukkit.FluidCollisionMode.NEVER);
        if (target == null || MiniSettings.MineTarget.of(target.getType()) == null) return;   // not an ore: ignore quietly
        MiniCommand.pointAndMine(plugin, mini, player);
    }

    /** Mobs your Mini Me kills drop their loot into its backpack. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        MiniMe mini = plugin.manager().killerOf(event.getEntity());
        if (mini == null || !mini.settings().collect) return;
        List<ItemStack> drops = event.getDrops();
        if (drops.isEmpty()) return;
        List<ItemStack> left = mini.backpack().add(new ArrayList<>(drops));
        drops.clear();
        drops.addAll(left);
        Player owner = Bukkit.getPlayer(mini.ownerId());
        if (!left.isEmpty() && owner != null) mini.warnBackpackFull(owner);
    }

    /** Minis never trample farmland. */
    @EventHandler(ignoreCancelled = true)
    public void onTrample(EntityInteractEvent event) {
        if (event.getBlock().getType() == Material.FARMLAND && plugin.manager().isMini(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ backpack + gear tabs

    @EventHandler
    public void onTabClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Object holder = event.getInventory().getHolder(false);
        if (holder instanceof MiniBackpack backpack) {
            if (plugin.manager().of(player).backpack() == backpack) backpack.save(player);
        } else if (holder instanceof MiniGear gear) {
            MiniMe mini = plugin.manager().of(player);
            if (mini.gear() == gear) {
                gear.save(player);
                mini.refreshEquipment(player);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTabClick(InventoryClickEvent event) {
        Object holder = event.getInventory().getHolder(false);
        if (holder instanceof MiniBackpack backpack) backpack.onClick(event);
        else if (holder instanceof MiniGear gear) gear.onClick(event);
        else if (holder instanceof MiniUpgrades upgrades) upgrades.onClick(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTabDrag(InventoryDragEvent event) {
        Object holder = event.getInventory().getHolder(false);
        if (holder instanceof MiniBackpack backpack) backpack.onDrag(event);
        else if (holder instanceof MiniGear gear) gear.onDrag(event);
        else if (holder instanceof MiniUpgrades upgrades) upgrades.onDrag(event);
    }

    // ------------------------------------------------------------------ getting hurt

    /**
     * Minis only take damage from mobs, and only while "Mobs can attack it" is on.
     * Players (including you), falling, fire, drowning etc. never hurt it.
     * A hit that would kill it knocks it out instead, so its gear is never lost.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!plugin.manager().isMini(event.getEntity())) return;
        MiniMe mini = plugin.manager().ofEntity(event.getEntity());
        if (mini == null || !mini.settings().aggro || !(event instanceof EntityDamageByEntityEvent byEntity)) {
            event.setCancelled(true);
            return;
        }
        LivingEntity attacker = attackerOf(byEntity.getDamager());
        if (!(attacker instanceof Mob) || plugin.manager().isMini(attacker)) {
            event.setCancelled(true);   // no players, no other minis, no stray potions
            return;
        }

        Mannequin m = (Mannequin) event.getEntity();
        if (m.getHealth() - event.getFinalDamage() <= 0) {
            event.setCancelled(true);
            mini.knockOut(Bukkit.getPlayer(mini.ownerId()));
            if (attacker instanceof Mob mob && m.equals(mob.getTarget())) mob.setTarget(null);
            return;
        }
        mini.onHurt(attacker);
    }

    private static LivingEntity attackerOf(Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            return shooter;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof LivingEntity source) return source;
        return damager instanceof LivingEntity living ? living : null;
    }

    /** If a mini somehow dies anyway (e.g. /kill), it never drops its gear or loot. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onMiniDeath(EntityDeathEvent event) {
        if (!plugin.manager().isMini(event.getEntity())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    /** Mobs may only go after minis while "Mobs can attack it" is on. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() == null || !plugin.manager().isMini(event.getTarget())) return;
        MiniMe mini = plugin.manager().ofEntity(event.getTarget());
        if (mini == null || !mini.settings().aggro || !(event.getEntity() instanceof Mob)) event.setCancelled(true);
    }

    /** Copy-me mode: your arm swings make it swing too. */
    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerArmSwingEvent event) {
        MiniMe mini = plugin.manager().of(event.getPlayer());
        mini.swing(event.getHand() == EquipmentSlot.OFF_HAND);
    }

    // ------------------------------------------------------------------ menu

    @EventHandler(ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof MiniMenu menu)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory()) return; // clicks in your own inventory do nothing
        menu.click(event.getRawSlot(), event.getClick());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof MiniMenu) event.setCancelled(true);
    }
}
