package ru.wilyfox.client.event;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import ru.wilyfox.boss.BossTracker;
import ru.wilyfox.client.profiler.ModProfiler;

public class ClientEntityEventHandler {
    private final BossTracker bossTracker;

    public ClientEntityEventHandler(BossTracker b) {
        this.bossTracker = b;
    }

    public void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> bossTracker.reset());
        ClientEntityEvents.ENTITY_LOAD.register((Entity entity, ClientLevel world) -> {
            try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("world/ClientEntityEventHandler/entityLoad")) {
                this.bossTracker.onEntityLoad(entity);
            }
        });

        ClientTickEvents.END_LEVEL_TICK.register(world -> {
            try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("world/ClientEntityEventHandler/endWorldTick")) {
                bossTracker.onWorldTick(world);
            }
        });
    }
}
