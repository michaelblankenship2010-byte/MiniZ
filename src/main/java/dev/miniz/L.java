package dev.miniz;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Every bit of text players see comes from language.yml, so server owners can reword,
 * recolor or translate the whole plugin.
 *
 * <p>Text uses MiniMessage for colors (e.g. {@code <red>}, {@code <gradient:gold:yellow>}) and
 * {@code {placeholders}} for values like {@code {player}}. Each call carries the English default,
 * so a key missing from the file still works and is written back into language.yml.</p>
 */
public final class L {

    private static MiniZPlugin plugin;
    private static File file;
    private static YamlConfiguration lang = new YamlConfiguration();
    private static boolean dirty;

    private L() {}

    /** Loads plugins/MiniZ/language.yml, creating it first and adding any keys a newer version brought. */
    static void load(MiniZPlugin owner) {
        plugin = owner;
        file = new File(owner.getDataFolder(), "language.yml");
        if (!file.exists()) owner.saveResource("language.yml", false);
        lang = YamlConfiguration.loadConfiguration(file);

        // Keys added in an update get filled in from the built-in file (your edits are kept).
        try (InputStream in = owner.getResource("language.yml")) {
            if (in != null) {
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    YamlConfiguration builtIn = YamlConfiguration.loadConfiguration(reader);
                    for (String key : builtIn.getKeys(true)) {
                        if (builtIn.isConfigurationSection(key) || lang.contains(key)) continue;
                        lang.set(key, builtIn.get(key));
                        dirty = true;
                    }
                }
            }
        } catch (IOException e) {
            owner.getLogger().log(Level.WARNING, "Couldn't read the built-in language.yml", e);
        }
        saveIfChanged();
    }

    static void saveIfChanged() {
        if (!dirty || file == null) return;
        try {
            lang.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Couldn't save language.yml", e);
        }
    }

    /**
     * The text for {@code key} (MiniMessage markup) with placeholders filled in.
     * {@code args} are pairs: "player", name, "count", 3 ... Values are escaped so a player
     * can't sneak color/click tags in through their name; a Component value keeps its colors.
     */
    public static String t(String key, String def, Object... args) {
        String raw = lang.getString(key);
        if (raw == null) {
            raw = def;
            if (plugin != null) {
                lang.set(key, def);   // write missing keys back so they can be edited
                dirty = true;
            }
        }
        for (int i = 0; i + 1 < args.length; i += 2) {
            Object value = args[i + 1];
            String text = value instanceof Component c
                    ? MiniMessage.miniMessage().serialize(c)
                    : MiniMessage.miniMessage().escapeTags(String.valueOf(value));
            raw = raw.replace("{" + args[i] + "}", text);
        }
        return raw;
    }

    /** Same as {@link #t} but already turned into a Component. */
    public static Component c(String key, String def, Object... args) {
        return parse(t(key, def, args));
    }

    /** For item names and lore: same as {@link #c}, without Minecraft's default italics. */
    public static Component item(String key, String def, Object... args) {
        return parse(t(key, def, args)).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** MiniMessage markup -> Component (falls back to plain text if the markup is broken). */
    public static Component parse(String miniMessage) {
        try {
            return MiniMessage.miniMessage().deserialize(miniMessage);
        } catch (Exception e) {
            return Component.text(miniMessage);
        }
    }

    /** "on"/"off" words, so toggles can be translated too. */
    public static String onOff(boolean on) {
        return on ? L.t("word.on", "<green>ON") : L.t("word.off", "<red>OFF");
    }
}
