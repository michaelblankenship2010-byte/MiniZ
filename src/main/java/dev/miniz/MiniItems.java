package dev.miniz;

import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Builds the locked "button" items used in the backpack / gear / upgrades tabs. */
final class MiniItems {

    private static NamespacedKey buttonKey;

    private MiniItems() {}

    static void init(MiniZPlugin plugin) {
        buttonKey = new NamespacedKey(plugin, "button");
    }

    /**
     * A tagged button (so it never stacks with real loot). {@code name} and {@code lore} are
     * MiniMessage text, usually straight from language.yml; lore lines are split on new lines.
     */
    static ItemStack button(Material material, String id, String name, String lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(L.parse(name).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        var lines = MiniMenu.lines(lore);
        if (!lines.isEmpty()) meta.lore(lines);
        meta.getPersistentDataContainer().set(buttonKey, PersistentDataType.STRING, id);
        stack.setItemMeta(meta);
        return stack;
    }

    static ItemStack button(Material material, String id, String name) {
        return button(material, id, name, "");
    }

    static ItemStack filler() {
        return button(Material.GRAY_STAINED_GLASS_PANE, "filler", " ");
    }

    static ItemStack close() {
        return button(Material.OAK_DOOR, "close", L.t("menu.close", "<gray>Close"));
    }

    // ------------------------------------------------------------------ the tab bar

    static ItemStack tabBackpack(boolean selected) {
        return tab(Material.CHEST, "tab_backpack", L.t("tab.backpack.name", "Backpack"),
                L.t("tab.backpack.hint", "Its loot"), selected);
    }

    static ItemStack tabGear(boolean selected) {
        return tab(Material.IRON_CHESTPLATE, "tab_gear", L.t("tab.gear.name", "Gear"),
                L.t("tab.gear.hint", "Armor and weapons for your Mini Me"), selected);
    }

    static ItemStack tabUpgrades(boolean selected) {
        return tab(Material.GOLDEN_APPLE, "tab_upgrades", L.t("tab.upgrades.name", "Upgrades"),
                L.t("tab.upgrades.hint", "Make your Mini Me tougher"), selected);
    }

    private static ItemStack tab(Material icon, String id, String label, String hint, boolean selected) {
        String name = selected
                ? L.t("tab.selected-format", "<green>▶ {tab}", "tab", L.parse(label))
                : L.t("tab.format", "<yellow>{tab}", "tab", L.parse(label));
        ItemStack tab = button(icon, id, name, selected ? L.t("tab.you-are-here", "You're here") : hint);
        if (selected) {
            ItemMeta meta = tab.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            tab.setItemMeta(meta);
        }
        return tab;
    }

    /** The button id of an item, or null if it's a normal item. */
    static String buttonId(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) return null;
        return stack.getItemMeta().getPersistentDataContainer().get(buttonKey, PersistentDataType.STRING);
    }
}
