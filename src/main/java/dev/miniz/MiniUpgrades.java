package dev.miniz;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * The Upgrades tab: spend dragon eggs to make your Mini Me tougher (1 egg = 2 health levels by default).
 * Upgrades are saved on you and survive /miniz reset.
 */
public final class MiniUpgrades implements InventoryHolder {

    private static final int HEALTH_INFO = 11, HEALTH_BUY = 13, HEALTH_MAX = 15;
    private static final int PIPS_START = 1;   // row 0: one pip per level (up to 7 shown)
    private static final int TAB_BACKPACK = 18, TAB_GEAR = 19, TAB_UPGRADES = 20, CLOSE = 26;

    private final MiniZPlugin plugin;
    private final MiniMe mini;
    private final Player player;
    private final Inventory inventory;

    private MiniUpgrades(MiniZPlugin plugin, MiniMe mini, Player player) {
        this.plugin = plugin;
        this.mini = mini;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, 27, L.c("upgrades.title", "<dark_red>Mini Me upgrades"));
        render();
    }

    public static void open(MiniZPlugin plugin, MiniMe mini, Player player) {
        player.openInventory(new MiniUpgrades(plugin, mini, player).getInventory());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // ------------------------------------------------------------------ numbers (all from config)

    static int maxLevel(MiniZPlugin plugin) {
        return Math.max(0, plugin.getConfig().getInt("upgrades.health.max-level", 10));
    }

    static double perLevel(MiniZPlugin plugin) {
        return plugin.getConfig().getDouble("upgrades.health.per-level", 4.0);
    }

    /** How many health levels one purchase gives (2 by default). */
    static int levelsPerPurchase(MiniZPlugin plugin) {
        return Math.max(1, plugin.getConfig().getInt("upgrades.health.levels-per-purchase", 2));
    }

    /** What one purchase costs: this many of the cost item (1 dragon egg by default). */
    static int costAmount(MiniZPlugin plugin) {
        return Math.max(0, plugin.getConfig().getInt("upgrades.health.cost-amount", 1));
    }

    private Material costItem() {
        Material m = Material.matchMaterial(plugin.getConfig().getString("upgrades.health.cost-item", "dragon_egg"));
        return m == null ? Material.DRAGON_EGG : m;
    }

    /** "dragon egg" / "dragon eggs" (the default item's name is translatable; others use the item id). */
    private String itemName(int amount) {
        if (costItem() == Material.DRAGON_EGG) {
            return amount == 1 ? L.t("upgrades.item.dragon-egg", "dragon egg") : L.t("upgrades.item.dragon-eggs", "dragon eggs");
        }
        String name = costItem().getKey().getKey().replace('_', ' ').toLowerCase(Locale.ROOT);
        return amount == 1 || name.endsWith("s") ? name : name + "s";
    }

    private String costText(int amount) {
        return L.t("upgrades.cost-format", "{amount} {item}", "amount", amount, "item", itemName(amount));
    }

    private boolean canAfford(int amount) {
        if (player.getGameMode() == GameMode.CREATIVE || amount <= 0) return true;
        return player.getInventory().containsAtLeast(new ItemStack(costItem()), amount);
    }

    private void pay(int amount) {
        if (player.getGameMode() == GameMode.CREATIVE || amount <= 0) return;
        ItemStack stack = new ItemStack(costItem());
        stack.setAmount(amount);
        player.getInventory().removeItem(stack);
    }

    // ------------------------------------------------------------------ drawing

    private void render() {
        for (int i = 0; i < 27; i++) inventory.setItem(i, MiniItems.filler());
        MiniSettings s = mini.settings();
        int level = s.healthLevel;
        int max = maxLevel(plugin);
        double now = mini.maxHealth();
        double per = perLevel(plugin);

        // progress pips
        int shown = Math.min(7, max);
        for (int i = 0; i < shown; i++) {
            // with more than 7 levels, each pip stands for a share of them
            int needed = (int) Math.ceil((i + 1) * (max / (double) shown));
            boolean lit = level >= needed;
            inventory.setItem(PIPS_START + i, MiniItems.button(
                    lit ? Material.RED_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE, "pip",
                    lit ? L.t("upgrades.pip-done", "<red>Health level {level} ✔", "level", needed)
                        : L.t("upgrades.pip-todo", "<gray>Health level {level}", "level", needed)));
        }

        inventory.setItem(HEALTH_INFO, MiniItems.button(Material.APPLE, "info",
                L.t("upgrades.info.name", "<red>Health: {health} ❤", "health", fmt(now)),
                L.t("upgrades.info.lore",
                        "Level {level} of {max}\nEach level: +{per} max health\n1 {item} = {levels} levels\nHealth matters while\n\"Mobs can attack it\" is on.",
                        "level", level, "max", max, "per", fmt(per), "item", itemName(1),
                        "levels", levelsPerPurchase(plugin))));

        if (level >= max) {
            inventory.setItem(HEALTH_BUY, MiniItems.button(Material.ENCHANTED_GOLDEN_APPLE, "maxed",
                    L.t("upgrades.maxed.name", "<gold>Health maxed out!"),
                    L.t("upgrades.maxed.lore", "Level {level} — {health} max health", "level", level, "health", fmt(now))));
        } else {
            int price = costAmount(plugin);
            int gain = Math.min(levelsPerPurchase(plugin), max - level);
            boolean afford = canAfford(price);
            String name = afford
                    ? L.t("upgrades.buy.name", "<green>Upgrade health (+{levels} levels)", "levels", gain)
                    : L.t("upgrades.buy.name-cant-afford", "<red>Upgrade health (+{levels} levels)", "levels", gain);
            String lore = L.t("upgrades.buy.lore", "{from} → {to} max health\nCost: {cost}",
                    "from", fmt(now), "to", fmt(now + per * gain), "cost", costText(price))
                    + "\n" + (afford ? L.t("upgrades.buy.click", "Click to upgrade")
                            : L.t("upgrades.buy.need", "You need {cost} in your inventory", "cost", costText(price)));
            inventory.setItem(HEALTH_BUY, MiniItems.button(Material.DRAGON_EGG, "buy_health", name, lore));
        }

        inventory.setItem(HEALTH_MAX, MiniItems.button(Material.TOTEM_OF_UNDYING, "info",
                L.t("upgrades.max.name", "<gold>At max level: {health} ❤", "health", fmt(mini.baseHealth() + per * max)),
                L.t("upgrades.max.lore", "Upgrades are kept forever,\neven through /miniz reset.")));

        inventory.setItem(TAB_BACKPACK, MiniItems.tabBackpack(false));
        inventory.setItem(TAB_GEAR, MiniItems.tabGear(false));
        inventory.setItem(TAB_UPGRADES, MiniItems.tabUpgrades(true));
        inventory.setItem(CLOSE, MiniItems.close());
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    // ------------------------------------------------------------------ clicks

    void onClick(InventoryClickEvent event) {
        event.setCancelled(true);   // nothing in here can be taken
        if (event.getClickedInventory() != inventory) return;
        String id = MiniItems.buttonId(inventory.getItem(event.getRawSlot()));
        if (id == null) return;
        switch (id) {
            case "tab_backpack" -> mini.backpack().open(player);
            case "tab_gear" -> mini.gear().open(player);
            case "close" -> player.closeInventory();
            case "buy_health" -> {
                buyHealth(plugin, mini, player);
                render();
            }
            default -> { }
        }
    }

    void onDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    /** Buys the next health levels (also used by /miniz upgrade health). Returns true if it worked. */
    static boolean buyHealth(MiniZPlugin plugin, MiniMe mini, Player player) {
        MiniUpgrades shop = new MiniUpgrades(plugin, mini, player);   // just for the cost helpers
        MiniSettings s = mini.settings();
        int max = maxLevel(plugin);
        if (s.healthLevel >= max) {
            plugin.sayError(player, L.t("upgrades.already-maxed", "Its health is already maxed out."));
            return false;
        }
        int price = costAmount(plugin);
        if (!shop.canAfford(price)) {
            plugin.sayError(player, L.t("upgrades.too-expensive", "That costs {cost}.", "cost", shop.costText(price)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
            return false;
        }
        shop.pay(price);
        s.healthLevel = Math.min(max, s.healthLevel + levelsPerPurchase(plugin));
        s.save(plugin, player);
        mini.applyHealth(true);

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        if (mini.isOut()) {
            mini.entity().getWorld().spawnParticle(Particle.HEART,
                    mini.entity().getLocation().add(0, mini.effectiveSize() * 2.0, 0), 6, 0.3, 0.2, 0.3, 0);
        }
        mini.speak(player, L.t("speech.stronger", "I feel stronger! Max health is now {health}.", "health", fmt(mini.maxHealth())));
        return true;
    }
}
