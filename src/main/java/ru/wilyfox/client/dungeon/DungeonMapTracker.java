package ru.wilyfox.client.dungeon;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.world.level.saveddata.maps.MapId;
import ru.wilyfox.client.clan.ClanSiegeMap;

public final class DungeonMapTracker {
    private static final DungeonMapTracker INSTANCE = new DungeonMapTracker();

    private MapId mapId;
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
    }

    public void updateMapId(MapId mapId) {
        if (!ClanSiegeMap.isSiegeMap(mapId)) {
            this.mapId = mapId;
        }
    }

    public MapId getMapId() {
        return mapId;
    }

    public boolean hasMapId() {
        return mapId != null;
    }

    public void clear() {
        mapId = null;
    }
}
