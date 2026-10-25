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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

/**
 * The Mini Me's own inventory. Harvests and loot go in here; you can open it and take things
 * out (or put things in) like a chest. The bottom row is a tab bar (Backpack / Gear).
 * Saved on the player, so it survives restarts.
 */
public final class MiniBackpack implements InventoryHolder {

    private final MiniZPlugin plugin;
    private final MiniMe mini;
    private final NamespacedKey key;
    private final Inventory inventory;
    private final int storageSize;   // real storage slots; the last row is the tab bar
    private boolean dirty;
    private boolean loadFailed;

    MiniBackpack(MiniZPlugin plugin, MiniMe mini, Player owner) {
        this.plugin = plugin;
        this.mini = mini;
        this.key = new NamespacedKey(plugin, "backpack");
        int rows = Math.max(1, Math.min(5, plugin.getConfig().getInt("backpack-rows", 3)));
        this.storageSize = rows * 9;
        this.inventory = Bukkit.createInventory(this, storageSize + 9,
                L.c("backpack.title", "<dark_green>Mini Me's backpack"));
        drawTabBar();
        load(owner);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open(Player viewer) {
        drawTabBar();
        viewer.openInventory(inventory);
    }

    private void drawTabBar() {
        int bar = storageSize;
        for (int i = 0; i < 9; i++) inventory.setItem(bar + i, MiniItems.filler());
        inventory.setItem(bar, MiniItems.tabBackpack(true));
        inventory.setItem(bar + 1, MiniItems.tabGear(false));
        inventory.setItem(bar + 2, MiniItems.tabUpgrades(false));
        inventory.setItem(bar + 7, MiniItems.button(Material.HOPPER, "give_all",
                L.t("backpack.give-all.name", "<gold>Give everything to me"),
                L.t("backpack.give-all.lore", "Moves everything that fits\ninto your inventory")));
        inventory.setItem(bar + 8, MiniItems.close());
    }

    // ------------------------------------------------------------------ storage

    /** Puts items in. Returns whatever didn't fit (empty list = all stored). */
    public List<ItemStack> add(Collection<ItemStack> items) {
        List<ItemStack> leftover = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.isEmpty()) continue;
            // Tab buttons are tagged, so loot never stacks onto them.
            leftover.addAll(inventory.addItem(item.clone()).values());
            dirty = true;
        }
        return leftover;
    }

    /** Takes one of {@code type} out, if there is one. */
    public boolean takeOne(Material type) {
        for (int i = 0; i < storageSize; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack == null || stack.getType() != type) continue;
            stack.setAmount(stack.getAmount() - 1);
            inventory.setItem(i, stack.getAmount() <= 0 ? null : stack);
            dirty = true;
            return true;
        }
        return false;
    }

    /** Moves everything that fits into the player's inventory. Returns how many items moved. */
    public int giveTo(Player player) {
        int moved = 0;
        for (int i = 0; i < storageSize; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack == null || stack.isEmpty()) continue;
            int before = stack.getAmount();
            var left = player.getInventory().addItem(stack.clone());
            if (left.isEmpty()) {
                inventory.setItem(i, null);
                moved += before;
            } else {
                ItemStack rest = left.values().iterator().next();
                moved += before - rest.getAmount();
                inventory.setItem(i, rest);
            }
        }
        if (moved > 0) dirty = true;
        return moved;
    }

    public boolean isFull() {
        for (int i = 0; i < storageSize; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack == null || stack.isEmpty()) return false;
        }
        return true;
    }

    public int usedSlots() {
        int used = 0;
        for (int i = 0; i < storageSize; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack != null && !stack.isEmpty()) used++;
        }
        return used;
    }

    public int size() {
        return storageSize;
    }

    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    // ------------------------------------------------------------------ clicks

    void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        // Double-click "collect all" could pull buttons out: never allow it here.
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        int raw = event.getRawSlot();
        boolean inTop = raw < inventory.getSize();
        if (inTop && raw >= storageSize) {
            event.setCancelled(true);
            String id = MiniItems.buttonId(inventory.getItem(raw));
            if (id == null) return;
            switch (id) {
                case "tab_gear" -> mini.gear().open(player);
                case "tab_upgrades" -> mini.openUpgrades(player);
                case "give_all" -> {
                    int moved = giveTo(player);
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
                    plugin.say(player, moved == 0 ? L.t("backpack.give-all.nothing-fit", "Nothing fit in your inventory.")
                            : L.t("backpack.give-all.done", "Took {count} items.", "count", moved));
                    save(player);
                }
                case "close" -> player.closeInventory();
                default -> { }
            }
            return;
        }
        dirty = true;
    }

    void onDrag(InventoryDragEvent event) {
        for (int raw : event.getRawSlots()) {
            if (raw < inventory.getSize() && raw >= storageSize) {
                event.setCancelled(true);
                return;
            }
        }
        dirty = true;
    }

    // ------------------------------------------------------------------ persistence

    private void load(Player owner) {
        byte[] bytes = owner.getPersistentDataContainer().get(key, PersistentDataType.BYTE_ARRAY);
        if (bytes == null || bytes.length == 0) return;
        try {
            ItemStack[] saved = ItemStack.deserializeItemsFromBytes(bytes);
            List<ItemStack> overflow = new ArrayList<>();
            for (int i = 0; i < saved.length; i++) {
                ItemStack item = saved[i];
                if (item == null || item.isEmpty() || MiniItems.buttonId(item) != null) continue;
                if (i < storageSize) inventory.setItem(i, item);
                else overflow.add(item);
            }
            // Backpack got smaller in the config: hand back what doesn't fit.
            if (!overflow.isEmpty()) {
                for (ItemStack extra : owner.getInventory().addItem(overflow.toArray(new ItemStack[0])).values()) {
                    owner.getWorld().dropItemNaturally(owner.getLocation(), extra);
                }
                dirty = true;
            }
        } catch (Exception e) {
            // Never overwrite a backpack we couldn't read.
            loadFailed = true;
            plugin.getLogger().log(Level.WARNING, "Couldn't read " + owner.getName() + "'s Mini Me backpack", e);
        }
    }

    public void save(Player owner) {
        if (loadFailed) return;
        ItemStack[] storage = new ItemStack[storageSize];
        for (int i = 0; i < storageSize; i++) storage[i] = inventory.getItem(i);
        owner.getPersistentDataContainer().set(key, PersistentDataType.BYTE_ARRAY,
                ItemStack.serializeItemsAsBytes(storage));
        dirty = false;
    }
}
