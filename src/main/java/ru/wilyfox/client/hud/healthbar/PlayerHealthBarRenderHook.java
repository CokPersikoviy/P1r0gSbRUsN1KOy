package ru.wilyfox.client.hud.healthbar;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

public final class PlayerHealthBarRenderHook {
    private static boolean registered;

    private PlayerHealthBarRenderHook() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PlayerHealthBarRenderer.clearVisibilityCache());
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            PlayerHealthBarRenderer.render(context.poseStack(), context.submitNodeCollector(),
                    mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        });
    }
}
