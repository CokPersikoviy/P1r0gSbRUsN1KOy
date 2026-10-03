package ru.wilyfox.client.dungeon;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.saveddata.maps.MapId;
import ru.wilyfox.client.clan.ClanSiegeMap;

public final class DungeonMapTracker {
    private static final DungeonMapTracker INSTANCE = new DungeonMapTracker();

    private MapId mapId;
    private ClientLevel mapLevel;
    private boolean registered;

    private DungeonMapTracker() {
    }

    public static DungeonMapTracker getInstance() {
        return INSTANCE;
    }

    public void register() {
        if (registered) {
            return;
        }

        registered = true;
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (mapLevel != client.level) {
                clear();
            }
        });
    }

    public void updateMapId(MapId mapId) {
        if (!ClanSiegeMap.isSiegeMap(mapId)) {
            this.mapId = mapId;
            this.mapLevel = Minecraft.getInstance().level;
        }
    }

    public MapId getMapId() {
        if (mapLevel != Minecraft.getInstance().level) {
            clear();
        }
        return mapId;
    }

    public boolean hasMapId() {
        return getMapId() != null;
    }

    public void clear() {
        mapId = null;
        mapLevel = null;
    }
}
