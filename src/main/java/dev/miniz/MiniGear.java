package dev.miniz;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.logging.Level;

/**
 * The Gear tab: armor and weapons your Mini Me wears and fights with.
 * Armor protects it when mobs attack; the weapon (or tool) in its hand is what it hits and mines with.
 */
public final class MiniGear implements InventoryHolder {

    // slots in the 27-slot tab
    static final int HEAD = 10, CHEST = 11, LEGS = 12, FEET = 13, MAIN = 15, OFF = 16;
    private static final int[] GEAR_SLOTS = {HEAD, CHEST, LEGS, FEET, MAIN, OFF};
    private static final EquipmentSlot[] EQUIPMENT = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND};
    private static final int TAB_BACKPACK = 18, TAB_GEAR = 19, TAB_UPGRADES = 20, INFO = 22, CLOSE = 26;

    private final MiniZPlugin plugin;
    private final MiniMe mini;
    private final NamespacedKey key;
    private final Inventory inventory;
    private boolean loadFailed;

    MiniGear(MiniZPlugin plugin, MiniMe mini, Player owner) {
        this.plugin = plugin;
        this.mini = mini;
        this.key = new NamespacedKey(plugin, "gear");
        this.inventory = Bukkit.createInventory(this, 27, L.c("gear.title", "<dark_aqua>Mini Me's gear"));
        drawFrame();
        load(owner);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open(Player viewer) {
        drawFrame();
        viewer.openInventory(inventory);
    }

    /** What's in a slot (null if empty). */
    public ItemStack get(EquipmentSlot slot) {
        for (int i = 0; i < EQUIPMENT.length; i++) {
            if (EQUIPMENT[i] == slot) {
                ItemStack item = inventory.getItem(GEAR_SLOTS[i]);
                return item == null || item.isEmpty() ? null : item;
            }
        }
        return null;
    }

    private void drawFrame() {
        for (int i = 0; i < 27; i++) {
            if (!isGearSlot(i)) inventory.setItem(i, MiniItems.filler());
        }
        inventory.setItem(1, label(Material.LEATHER_HELMET, L.t("gear.slot.helmet", "<aqua>Helmet ↓"), ""));
        inventory.setItem(2, label(Material.LEATHER_CHESTPLATE, L.t("gear.slot.chestplate", "<aqua>Chestplate ↓"), ""));
        inventory.setItem(3, label(Material.LEATHER_LEGGINGS, L.t("gear.slot.leggings", "<aqua>Leggings ↓"), ""));
        inventory.setItem(4, label(Material.LEATHER_BOOTS, L.t("gear.slot.boots", "<aqua>Boots ↓"), ""));
        inventory.setItem(6, label(Material.IRON_SWORD, L.t("gear.slot.weapon", "<aqua>Weapon / tool ↓"),
                L.t("gear.slot.weapon-lore", "Swords and axes hit harder,\npickaxes mine faster")));
        inventory.setItem(7, label(Material.SHIELD, L.t("gear.slot.offhand", "<aqua>Off hand ↓"), ""));

        inventory.setItem(TAB_BACKPACK, MiniItems.tabBackpack(false));
        inventory.setItem(TAB_GEAR, MiniItems.tabGear(true));
        inventory.setItem(TAB_UPGRADES, MiniItems.tabUpgrades(false));
        inventory.setItem(INFO, info());
        inventory.setItem(CLOSE, MiniItems.close());
    }

    private ItemStack label(Material icon, String name, String lore) {
        return MiniItems.button(icon, "label", name, lore);
    }

    private ItemStack info() {
        String health = mini.isOut()
                ? L.t("gear.info.health", "Health: {health} / {max}",
                        "health", (int) Math.ceil(mini.entity().getHealth()), "max", (int) mini.maxHealth())
                : L.t("gear.info.health-away", "Health: —");
        return MiniItems.button(Material.GOLDEN_APPLE, "info", L.t("gear.info.name", "<gold>How gear works"),
                health + "\n" + L.t("gear.info.lore",
                        "Armor protects it when mobs attack.\nIts weapon is used for hunting and\nfighting back; a pickaxe for mining.\nGear here beats Copy me / Wear my armor.\nGear doesn't wear out."));
    }

    private static boolean isGearSlot(int slot) {
        for (int s : GEAR_SLOTS) if (s == slot) return true;
        return false;
    }

    private static int gearIndex(int slot) {
        for (int i = 0; i < GEAR_SLOTS.length; i++) if (GEAR_SLOTS[i] == slot) return i;
        return -1;
    }

    /** Can this item go in this gear slot? Armor slots only take matching armor; hands take anything. */
    private static boolean fits(int slot, ItemStack item) {
        if (item == null || item.isEmpty()) return true;
        int i = gearIndex(slot);
        if (i < 0) return false;
        EquipmentSlot want = EQUIPMENT[i];
        if (want == EquipmentSlot.HAND || want == EquipmentSlot.OFF_HAND) return true;
        return item.getType().getEquipmentSlot() == want;
    }

    // ------------------------------------------------------------------ clicks

    void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        int raw = event.getRawSlot();
        boolean inTop = raw >= 0 && raw < inventory.getSize();

        if (inTop && !isGearSlot(raw)) {
            event.setCancelled(true);
            String id = MiniItems.buttonId(inventory.getItem(raw));
            if ("tab_backpack".equals(id)) mini.backpack().open(player);
            else if ("tab_upgrades".equals(id)) mini.openUpgrades(player);
            else if ("close".equals(id)) player.closeInventory();
            return;
        }

        // Shift-clicking from your inventory: put it in the slot it belongs in.
        if (!inTop && event.isShiftClick()) {
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (item == null || item.isEmpty()) return;
            int target = slotFor(item);
            if (target < 0) {
                plugin.sayError(player, L.t("gear.no-free-slot", "No free gear slot for that."));
                return;
            }
            inventory.setItem(target, item.clone());
            event.setCurrentItem(null);
            changed(player);
            return;
        }

        // Normal click on a gear slot (or anywhere in your own inventory): let it happen, then check it.
        Bukkit.getScheduler().runTask(plugin, () -> validate(player));
    }

    void onDrag(InventoryDragEvent event) {
        for (int raw : event.getRawSlots()) {
            if (raw < inventory.getSize() && (!isGearSlot(raw) || !fits(raw, event.getOldCursor()))) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getWhoClicked() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> validate(player));
        }
    }

    /** Best empty slot for an item: its armor slot, else main hand, else off hand. */
    private int slotFor(ItemStack item) {
        EquipmentSlot natural = item.getType().getEquipmentSlot();
        for (int i = 0; i < 4; i++) {
            if (EQUIPMENT[i] == natural && empty(GEAR_SLOTS[i])) return GEAR_SLOTS[i];
        }
        if (natural == EquipmentSlot.OFF_HAND && empty(OFF)) return OFF;   // shields
        if (empty(MAIN)) return MAIN;
        if (empty(OFF)) return OFF;
        return -1;
    }

    private boolean empty(int slot) {
        ItemStack item = inventory.getItem(slot);
        return item == null || item.isEmpty();
    }

    /** Kicks anything that ended up in the wrong slot back to you, then puts the gear on the Mini Me. */
    private void validate(Player player) {
        for (int i = 0; i < 4; i++) {
            int slot = GEAR_SLOTS[i];
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.isEmpty() && !fits(slot, item)) {
                inventory.setItem(slot, null);
                for (ItemStack extra : player.getInventory().addItem(item).values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), extra);
                }
                plugin.sayError(player, L.t("gear.wrong-slot", "That doesn't go in that slot."));
            }
        }
        changed(player);
    }

    private void changed(Player player) {
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.6f, 1.2f);
        mini.refreshEquipment(player);
        inventory.setItem(INFO, info());
        save(player);
    }

    // ------------------------------------------------------------------ persistence

    private void load(Player owner) {
        byte[] bytes = owner.getPersistentDataContainer().get(key, PersistentDataType.BYTE_ARRAY);
        if (bytes == null || bytes.length == 0) return;
        try {
            ItemStack[] saved = ItemStack.deserializeItemsFromBytes(bytes);
            for (int i = 0; i < saved.length && i < GEAR_SLOTS.length; i++) {
                ItemStack item = saved[i];
                if (item != null && !item.isEmpty()) inventory.setItem(GEAR_SLOTS[i], item);
            }
        } catch (Exception e) {
            loadFailed = true;
            plugin.getLogger().log(Level.WARNING, "Couldn't read " + owner.getName() + "'s Mini Me gear", e);
        }
    }

    public void save(Player owner) {
        if (loadFailed) return;
        ItemStack[] items = new ItemStack[GEAR_SLOTS.length];
        for (int i = 0; i < GEAR_SLOTS.length; i++) items[i] = inventory.getItem(GEAR_SLOTS[i]);
        owner.getPersistentDataContainer().set(key, PersistentDataType.BYTE_ARRAY,
                ItemStack.serializeItemsAsBytes(items));
    }
}
