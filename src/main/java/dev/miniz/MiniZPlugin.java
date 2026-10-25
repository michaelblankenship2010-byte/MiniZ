package dev.miniz;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class MiniZPlugin extends JavaPlugin {

    private MiniManager manager;
    private MiniFestive festive;
    private NamespacedKey ownerKey;

    /** MiniMessage limited to colors / gradients / styles, so player names can't use click events etc. */
    private final MiniMessage safeMiniMessage = MiniMessage.builder()
            .tags(TagResolver.resolver(
                    StandardTags.color(),
                    StandardTags.decorations(),
                    StandardTags.gradient(),
                    StandardTags.rainbow(),
                    StandardTags.reset()))
            .build();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        L.load(this);
        ownerKey = new NamespacedKey(this, "owner");
        MiniItems.init(this);
        festive = new MiniFestive(this);
        manager = new MiniManager(this);

        getServer().getPluginManager().registerEvents(new MiniListener(this), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register("miniz",
                        "Bring out and customize your Mini Me",
                        List.of(),
                        new MiniCommand(this)));

        // Clean up any minis left behind by a crash, then bring back minis for players already online.
        manager.removeStrays();
        for (Player player : getServer().getOnlinePlayers()) {
            manager.restore(player);
        }
        getServer().getScheduler().runTaskTimer(this, manager::tick, 1L, 1L);
        getLogger().info("MiniZ " + getPluginMeta().getVersion() + " by "
                + String.join(", ", getPluginMeta().getAuthors()) + " is enabled.");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.removeAll();
        L.saveIfChanged();
    }

    public MiniManager manager() { return manager; }

    MiniFestive festive() { return festive; }

    public NamespacedKey ownerKey() { return ownerKey; }

    public MiniMessage safeMiniMessage() { return safeMiniMessage; }

    /** Every Mini Me's size compared to a normal player (45% by default). */
    public double miniSize() {
        return Math.max(0.1, Math.min(1.0, getConfig().getDouble("size", 0.45)));
    }

    // ------------------------------------------------------------------ chat helpers

    /** The tag in front of every MiniZ chat line ("prefix" in language.yml), so players can tell which plugin is talking. */
    public Component prefix() {
        return L.c("prefix", "<dark_gray>[<gold>MiniZ</gold>]</dark_gray> ");
    }

    /** Sends a line with the [MiniZ] tag in front. */
    public void send(CommandSender to, Component line) {
        to.sendMessage(prefix().append(line));
    }

    /** A message (MiniMessage markup, usually from {@link L#t}); gray unless it sets its own color. */
    public void say(CommandSender to, String message) {
        send(to, L.parse(message).colorIfAbsent(NamedTextColor.GRAY));
    }

    public void say(CommandSender to, Component message) {
        send(to, message.colorIfAbsent(NamedTextColor.GRAY));
    }

    /** An error message; red unless it sets its own color. */
    public void sayError(CommandSender to, String message) {
        send(to, L.parse(message).colorIfAbsent(NamedTextColor.RED));
    }
}
