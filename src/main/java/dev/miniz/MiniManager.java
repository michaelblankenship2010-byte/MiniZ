package dev.miniz;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Keeps one Mini Me controller per online player and ticks them all every server tick. */
public final class MiniManager {

    /** How long (ticks) after a Mini Me's hit a kill still counts as its kill. */
    private static final long KILL_CREDIT_TICKS = 100;

    private final MiniZPlugin plugin;
    private final Map<UUID, MiniMe> minis = new HashMap<>();
    /** mob UUID -> (owner UUID, tick of last hit) for mobs a Mini Me has hit. */
    private final Map<UUID, Hit> victims = new HashMap<>();
    private long now;

    private record Hit(UUID owner, long tick) {}

    MiniManager(MiniZPlugin plugin) {
        this.plugin = plugin;
    }

    /** The player's controller (made on demand; the entity itself may or may not be out). */
    public MiniMe of(Player player) {
        return minis.computeIfAbsent(player.getUniqueId(),
                id -> new MiniMe(plugin, player, MiniSettings.load(plugin, player)));
    }

    /** Join / enable: bring it back if the player left it out last time. */
    public void restore(Player player) {
        MiniMe mini = of(player);
        if (mini.settings().out && plugin.getConfig().getBoolean("spawn-on-join", true)) {
            mini.spawn(player);
        } else if (!plugin.getConfig().getBoolean("spawn-on-join", true)) {
            mini.settings().out = false;
        }
    }

    /** Quit: remove the entity but remember whether it was out. */
    public void unload(Player player) {
        MiniMe mini = minis.remove(player.getUniqueId());
        if (mini != null) {
            mini.save(player);
            mini.despawn();
        }
    }

    /** Is this entity somebody's Mini Me? Returns the controller, or null. */
    public MiniMe ofEntity(Entity entity) {
        if (!(entity instanceof Mannequin)) return null;
        String owner = entity.getPersistentDataContainer().get(plugin.ownerKey(), PersistentDataType.STRING);
        if (owner == null) return null;
        try {
            MiniMe mini = minis.get(UUID.fromString(owner));
            return mini != null && mini.entity() != null
                    && mini.entity().getUniqueId().equals(entity.getUniqueId()) ? mini : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isMini(Entity entity) {
        return entity instanceof Mannequin
                && entity.getPersistentDataContainer().has(plugin.ownerKey(), PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------ kill credit

    void recordHit(Entity victim, UUID owner) {
        victims.put(victim.getUniqueId(), new Hit(owner, now));
    }

    /** The Mini Me that recently hit this (now dying) mob, if any. */
    public MiniMe killerOf(Entity victim) {
        Hit hit = victims.remove(victim.getUniqueId());
        if (hit == null || now - hit.tick() > KILL_CREDIT_TICKS) return null;
        return minis.get(hit.owner());
    }

    // ------------------------------------------------------------------ ticking

    public void tick() {
        now++;
        for (MiniMe mini : new ArrayList<>(minis.values())) {
            Player owner = Bukkit.getPlayer(mini.ownerId());
            if (owner == null || !owner.isOnline()) {
                mini.despawn();
                minis.remove(mini.ownerId());
                continue;
            }
            mini.tick(owner);
        }
        if (now % 200 == 0) {
            for (Iterator<Hit> it = victims.values().iterator(); it.hasNext(); ) {
                if (now - it.next().tick() > KILL_CREDIT_TICKS) it.remove();
            }
        }
    }

    public void removeAll() {
        for (MiniMe mini : minis.values()) {
            Player owner = Bukkit.getPlayer(mini.ownerId());
            if (owner != null) mini.save(owner);
            mini.despawn();
        }
        minis.clear();
    }

    /** Minis are never saved with the world, but clean up any that slipped through (e.g. after a crash). */
    public void removeStrays() {
        for (World world : Bukkit.getWorlds()) {
            for (Mannequin m : world.getEntitiesByClass(Mannequin.class)) {
                if (isMini(m)) m.remove();
            }
        }
    }
}
