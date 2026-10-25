package dev.miniz;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Holiday looks that switch on and off by themselves with the date: a festive hat, trail,
 * name symbol, greeting, and a small gift on the holiday itself. They only fill in what the
 * player hasn't chosen themselves.
 */
final class MiniFestive {

    // ------------------------------------------------------------------ date rules

    /** When a holiday's main day falls in a given year (null = not known for that year). */
    @FunctionalInterface
    interface Rule {
        LocalDate main(int year);
    }

    private static Rule fixed(int month, int day) {
        return year -> LocalDate.of(year, month, day);
    }

    /** e.g. the 2nd Sunday of May. */
    private static Rule nth(int month, DayOfWeek dow, int n) {
        return year -> LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, dow));
    }

    /** Western Easter Sunday (Anonymous Gregorian algorithm). */
    private static Rule easter() {
        return year -> {
            int a = year % 19, b = year / 100, c = year % 100, d = b / 4, e = b % 4;
            int f = (b + 8) / 25, g = (b - f + 1) / 3, h = (19 * a + b - d - g + 15) % 30;
            int i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
            int m = (a + 11 * h + 22 * l) / 451;
            int month = (h + l - 7 * m + 114) / 31, day = ((h + l - 7 * m + 114) % 31) + 1;
            return LocalDate.of(year, month, day);
        };
    }

    /** Holidays on the lunar / lunisolar calendars: a table of known dates (more can be added in the config). */
    private static Rule table(String... isoDates) {
        Map<Integer, LocalDate> byYear = new java.util.HashMap<>();
        for (String iso : isoDates) {
            LocalDate d = LocalDate.parse(iso);
            byYear.put(d.getYear(), d);
        }
        return byYear::get;
    }

    // ------------------------------------------------------------------ holidays

    /** A hat: an item, optionally dyed leather. */
    record Hat(Material material, Integer rgb, String name, TextColor color) {}

    /** Order matters only for ties: the holiday whose main day is closest to today wins. */
    enum Holiday {
        NONE("None", "", NamedTextColor.WHITE, null, "", null, null, 0, 0),

        NEW_YEAR("New Year", "✦ ", NamedTextColor.GOLD, Particle.FIREWORK, "Happy New Year",
                new Hat(Material.GOLDEN_HELMET, null, "Party Crown", NamedTextColor.GOLD), fixed(1, 1), 1, 0),
        LUNAR_NEW_YEAR("Lunar New Year", "✹ ", NamedTextColor.RED, Particle.SMALL_FLAME, "Happy Lunar New Year",
                new Hat(Material.LEATHER_HELMET, 0xD0201C, "Lucky Cap", NamedTextColor.RED),
                table("2026-02-17", "2027-02-06", "2028-01-26"), 1, 6),
        VALENTINES("Valentine's Day", "❤ ", NamedTextColor.LIGHT_PURPLE, Particle.HEART, "Happy Valentine's Day",
                new Hat(Material.LEATHER_HELMET, 0xFF69B4, "Sweetheart Cap", NamedTextColor.LIGHT_PURPLE), fixed(2, 14), 1, 0),
        ST_PATRICKS("St. Patrick's Day", "☘ ", NamedTextColor.GREEN, Particle.HAPPY_VILLAGER, "Happy St. Patrick's Day",
                new Hat(Material.LEATHER_HELMET, 0x1E8B3A, "Leprechaun Hat", NamedTextColor.GREEN), fixed(3, 17), 1, 0),
        EID_AL_FITR("Eid al-Fitr", "☪ ", NamedTextColor.GREEN, Particle.END_ROD, "Eid Mubarak",
                new Hat(Material.LEATHER_HELMET, 0x2E8B57, "Festive Cap", NamedTextColor.GREEN),
                table("2026-03-20", "2027-03-09", "2028-02-26", "2029-02-14", "2030-02-04"), 0, 2),
        APRIL_FOOLS("April Fools' Day", "? ", NamedTextColor.YELLOW, Particle.BUBBLE_POP,
                "Happy April Fools'! ...your shoe's untied",
                new Hat(Material.SLIME_BLOCK, null, "Very Serious Hat", NamedTextColor.GREEN), fixed(4, 1), 0, 0),
        EASTER("Easter", "✿ ", NamedTextColor.LIGHT_PURPLE, Particle.EGG_CRACK, "Happy Easter",
                new Hat(Material.LEATHER_HELMET, 0xFFFFFF, "Bunny Ears", NamedTextColor.WHITE), easter(), 2, 1),
        EARTH_DAY("Earth Day", "♻ ", NamedTextColor.GREEN, Particle.HAPPY_VILLAGER, "Happy Earth Day",
                new Hat(Material.OAK_LEAVES, null, "Leaf Crown", NamedTextColor.GREEN), fixed(4, 22), 0, 0),
        MOTHERS_DAY("Mother's Day", "❀ ", NamedTextColor.LIGHT_PURPLE, Particle.SPORE_BLOSSOM_AIR, "Happy Mother's Day",
                new Hat(Material.PINK_TULIP, null, "Flower Crown", NamedTextColor.LIGHT_PURPLE),
                nth(5, DayOfWeek.SUNDAY, 2), 0, 0),
        FATHERS_DAY("Father's Day", "★ ", NamedTextColor.AQUA, Particle.SMOKE, "Happy Father's Day",
                new Hat(Material.LEATHER_HELMET, 0x6B4423, "Dad Cap", NamedTextColor.GOLD),
                nth(6, DayOfWeek.SUNDAY, 3), 0, 0),
        INDEPENDENCE_DAY("Independence Day", "★ ", NamedTextColor.RED, Particle.FIREWORK, "Happy 4th of July",
                new Hat(Material.LEATHER_HELMET, 0x1C3F94, "Star Cap", NamedTextColor.BLUE), fixed(7, 4), 1, 0),
        DIWALI("Diwali", "✺ ", NamedTextColor.GOLD, Particle.SMALL_FLAME, "Happy Diwali",
                new Hat(Material.LEATHER_HELMET, 0xF5A623, "Festive Cap", NamedTextColor.GOLD),
                table("2026-11-08", "2027-10-29", "2028-10-17"), 2, 2),
        HALLOWEEN("Halloween", "☠ ", NamedTextColor.GOLD, Particle.SOUL, "Happy Halloween",
                new Hat(Material.CARVED_PUMPKIN, null, "Spooky Pumpkin", NamedTextColor.GOLD), fixed(10, 31), 7, 0),
        THANKSGIVING("Thanksgiving", "✾ ", NamedTextColor.GOLD, Particle.CRIMSON_SPORE, "Happy Thanksgiving",
                new Hat(Material.LEATHER_HELMET, 0x3B2A1A, "Pilgrim Hat", NamedTextColor.GOLD),
                nth(11, DayOfWeek.THURSDAY, 4), 0, 3),
        CHRISTMAS("Christmas", "❄ ", NamedTextColor.RED, Particle.SNOWFLAKE, "Merry Christmas",
                new Hat(Material.LEATHER_HELMET, 0xC0161C, "Santa Hat", NamedTextColor.RED), fixed(12, 25), 24, 1),
        HANUKKAH("Hanukkah", "✡ ", NamedTextColor.BLUE, Particle.END_ROD, "Happy Hanukkah",
                new Hat(Material.LEATHER_HELMET, 0x1F4FBF, "Festive Cap", NamedTextColor.BLUE),
                table("2026-12-05", "2027-12-25", "2028-12-12"), 0, 7);

        final String label;
        final String prefix;
        final TextColor prefixColor;
        final Particle particle;
        final String greeting;
        final Hat hat;
        final Rule rule;
        final int daysBefore, daysAfter;

        Holiday(String label, String prefix, TextColor prefixColor, Particle particle, String greeting,
                Hat hat, Rule rule, int daysBefore, int daysAfter) {
            this.label = label;
            this.prefix = prefix;
            this.prefixColor = prefixColor;
            this.particle = particle;
            this.greeting = greeting;
            this.hat = hat;
            this.rule = rule;
            this.daysBefore = daysBefore;
            this.daysAfter = daysAfter;
        }

        String configKey() {
            return name().toLowerCase(Locale.ROOT);
        }

        public String label() { return L.t("holiday." + configKey() + ".name", label); }
        public String greeting() { return L.t("holiday." + configKey() + ".greeting()", greeting); }
        String hatName() { return hat == null ? "" : L.t("holiday." + configKey() + ".hat", hat.name()); }
    }

    // ------------------------------------------------------------------ state

    private final MiniZPlugin plugin;
    private final Map<Holiday, ItemStack> hats = new EnumMap<>(Holiday.class);
    private Holiday testOverride;          // /miniz festive test <holiday> (admins)
    private Holiday cached = Holiday.NONE;
    private long cachedAt = Long.MIN_VALUE;

    MiniFestive(MiniZPlugin plugin) {
        this.plugin = plugin;
    }

    /** Today's holiday (NONE when there isn't one). Re-checked about once a minute. */
    Holiday current() {
        if (testOverride != null) return testOverride;
        long now = System.currentTimeMillis();
        if (now - cachedAt < 60_000) return cached;
        cachedAt = now;
        cached = compute(today());
        return cached;
    }

    void setTestOverride(Holiday holiday) {
        testOverride = holiday;
    }

    /** Forget cached answers (after /miniz reload). */
    void reload() {
        cachedAt = Long.MIN_VALUE;
        hats.clear();
    }

    LocalDate today() {
        String zone = plugin.getConfig().getString("festive.timezone", "");
        try {
            return zone == null || zone.isBlank() ? LocalDate.now() : LocalDate.now(ZoneId.of(zone));
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    private ConfigurationSection settingsFor(Holiday h) {
        return plugin.getConfig().getConfigurationSection("festive.holidays." + h.configKey());
    }

    /** The holiday's main day in {@code year}, with extra dates from the config for table holidays. */
    private LocalDate mainDay(Holiday h, int year) {
        ConfigurationSection cs = settingsFor(h);
        if (cs != null) {
            for (String iso : cs.getStringList("dates")) {
                try {
                    LocalDate d = LocalDate.parse(iso.trim());
                    if (d.getYear() == year) return d;
                } catch (Exception e) {
                    plugin.getLogger().warning("Bad festive date for " + h.configKey() + " (use YYYY-MM-DD): " + iso);
                }
            }
        }
        return h.rule.main(year);
    }

    private boolean enabled(Holiday h) {
        ConfigurationSection cs = settingsFor(h);
        return cs == null || cs.getBoolean("enabled", true);
    }

    private int before(Holiday h) {
        ConfigurationSection cs = settingsFor(h);
        return Math.max(0, cs == null ? h.daysBefore : cs.getInt("days-before", h.daysBefore));
    }

    private int after(Holiday h) {
        ConfigurationSection cs = settingsFor(h);
        return Math.max(0, cs == null ? h.daysAfter : cs.getInt("days-after", h.daysAfter));
    }

    /** Days between today and the nearest main day whose window covers today, or -1 if not active. */
    private long distance(Holiday h, LocalDate today) {
        long best = -1;
        for (int y = today.getYear() - 1; y <= today.getYear() + 1; y++) {
            LocalDate main = mainDay(h, y);
            if (main == null) continue;
            LocalDate start = main.minusDays(before(h));
            LocalDate end = main.plusDays(after(h));
            if (today.isBefore(start) || today.isAfter(end)) continue;
            long d = Math.abs(ChronoUnit.DAYS.between(today, main));
            if (best < 0 || d < best) best = d;
        }
        return best;
    }

    private Holiday compute(LocalDate today) {
        if (!plugin.getConfig().getBoolean("festive.enabled", true)) return Holiday.NONE;
        String forced = plugin.getConfig().getString("festive.force", "");
        if (forced != null && !forced.isBlank()) {
            Holiday h = MiniSettings.parse(Holiday.class, forced.trim(), Holiday.NONE);
            if (h != Holiday.NONE) return h;
        }
        Holiday best = Holiday.NONE;
        long bestDistance = Long.MAX_VALUE;
        for (Holiday h : Holiday.values()) {
            if (h == Holiday.NONE || !enabled(h)) continue;
            long d = distance(h, today);
            if (d >= 0 && d < bestDistance) {
                bestDistance = d;
                best = h;
            }
        }
        return best;
    }

    /** Is today the holiday itself (not just the run-up)? Gifts are handed out then. */
    boolean isMainDay(Holiday h) {
        if (h == Holiday.NONE) return false;
        if (testOverride == h) return true;   // previews always count, so admins can see the gift
        return distance(h, today()) == 0;
    }

    /** All holidays with today's state, for /miniz festive list. */
    List<String> describeAll() {
        LocalDate today = today();
        List<String> out = new ArrayList<>();
        for (Holiday h : Holiday.values()) {
            if (h == Holiday.NONE) continue;
            LocalDate next = null;
            for (int y = today.getYear(); y <= today.getYear() + 1 && next == null; y++) {
                LocalDate main = mainDay(h, y);
                if (main != null && !main.plusDays(after(h)).isBefore(today)) next = main;
            }
            String when = next == null
                    ? L.t("holiday.list.no-date", "date not set (add it under festive.holidays.{key}.dates)", "key", h.configKey())
                    : next.toString();
            out.add(enabled(h)
                    ? L.t("holiday.list.line", "{holiday} — {date}", "holiday", L.parse(h.label()), "date", when)
                    : L.t("holiday.list.line-off", "{holiday} (off) — {date}", "holiday", L.parse(h.label()), "date", when));
        }
        return out;
    }

    // ------------------------------------------------------------------ looks + gifts

    Component namePrefix(Holiday h) {
        if (h == Holiday.NONE || h.prefix.isEmpty()) return Component.empty();
        return Component.text(h.prefix, h.prefixColor).decoration(TextDecoration.BOLD, false);
    }

    /** What it wears on its head for the holiday (only when its head slot is otherwise empty). */
    ItemStack hat(Holiday h) {
        if (h == Holiday.NONE || h.hat == null || !plugin.getConfig().getBoolean("festive.hats", true)) return null;
        return hats.computeIfAbsent(h, k -> makeHat(k.hat, k.hatName()));
    }

    private static ItemStack makeHat(Hat hat, String name) {
        ItemStack item = new ItemStack(hat.material());
        if (hat.rgb() != null && item.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(Color.fromRGB(hat.rgb()));
            item.setItemMeta(meta);
        }
        return named(item, name, hat.color());
    }

    private static ItemStack named(ItemStack item, String name, TextColor color) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(L.parse(name).colorIfAbsent(color).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack stack(Material m, int amount) {
        return new ItemStack(m, amount);
    }

    /** A small gift for the holiday itself (once a year per player). */
    List<ItemStack> gift(Holiday h) {
        return switch (h) {
            case NEW_YEAR, INDEPENDENCE_DAY -> List.of(stack(Material.FIREWORK_ROCKET, 5));
            case LUNAR_NEW_YEAR -> List.of(named(stack(Material.GOLD_NUGGET, 8), L.t("holiday.gift.lucky-gold", "Lucky Gold"), NamedTextColor.GOLD));
            case VALENTINES -> List.of(named(stack(Material.POPPY, 3), L.t("holiday.gift.rose", "Rose"), NamedTextColor.RED),
                    stack(Material.COOKIE, 6));
            case ST_PATRICKS -> List.of(named(stack(Material.GOLD_NUGGET, 9), L.t("holiday.gift.pot-of-gold", "Pot of Gold"), NamedTextColor.GOLD),
                    stack(Material.EMERALD, 1));
            case EID_AL_FITR -> List.of(named(stack(Material.COOKIE, 8), L.t("holiday.gift.eid-sweets", "Eid Sweets"), NamedTextColor.GOLD),
                    stack(Material.HONEY_BOTTLE, 2));
            case APRIL_FOOLS -> List.of(named(stack(Material.DIRT, 1), L.t("holiday.gift.totally-real-diamond", "Totally Real Diamond"), NamedTextColor.AQUA));
            case EASTER -> List.of(named(stack(Material.EGG, 6), L.t("holiday.gift.easter-egg", "Easter Egg"), NamedTextColor.LIGHT_PURPLE),
                    stack(Material.GOLDEN_CARROT, 2));
            case EARTH_DAY -> List.of(stack(Material.OAK_SAPLING, 4), stack(Material.BONE_MEAL, 8));
            case MOTHERS_DAY -> List.of(stack(Material.PINK_TULIP, 2), stack(Material.POPPY, 2),
                    stack(Material.ALLIUM, 1), stack(Material.CAKE, 1));
            case FATHERS_DAY -> List.of(named(stack(Material.COOKED_BEEF, 8), L.t("holiday.gift.bbq-steak", "BBQ Steak"), NamedTextColor.GOLD));
            case DIWALI -> List.of(stack(Material.CANDLE, 4), stack(Material.FIREWORK_ROCKET, 3));
            case HALLOWEEN -> List.of(named(stack(Material.COOKIE, 8), L.t("holiday.gift.candy", "Candy"), NamedTextColor.GOLD),
                    stack(Material.PUMPKIN_PIE, 2));
            case THANKSGIVING -> List.of(named(stack(Material.COOKED_CHICKEN, 4), L.t("holiday.gift.turkey", "Turkey"), NamedTextColor.GOLD),
                    stack(Material.PUMPKIN_PIE, 2));
            case CHRISTMAS -> List.of(stack(Material.COOKIE, 16),
                    named(stack(Material.CAKE, 1), L.t("holiday.gift.christmas-cake", "Christmas Cake"), NamedTextColor.RED),
                    stack(Material.FIREWORK_ROCKET, 3), stack(Material.SNOWBALL, 16));
            case HANUKKAH -> List.of(named(stack(Material.GOLD_NUGGET, 8), L.t("holiday.gift.chocolate-gelt", "Chocolate Gelt"), NamedTextColor.GOLD),
                    stack(Material.CANDLE, 2));
            case NONE -> List.of();
        };
    }
}
