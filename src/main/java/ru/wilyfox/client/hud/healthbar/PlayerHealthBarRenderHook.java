package ru.wilyfox.client.hud.healthbar;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import ru.wilyfox.client.profiler.ModProfiler;

public final class PlayerHealthBarRenderHook {
    private PlayerHealthBarRenderHook() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(PlayerHealthBarRenderHook::onAfterEntities);
    }

    private static void onAfterEntities(WorldRenderContext context) {
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("render/PlayerHealthBarRenderHook")) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                ModProfiler.getInstance().incrementCounter("render/PlayerHealthBarRenderHook/skippedNoWorld");
                return;
            }

            if (context.matrixStack() == null) {
                ModProfiler.getInstance().incrementCounter("render/PlayerHealthBarRenderHook/skippedNoMatrix");
                return;
            }

            MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
            try (ModProfiler.Scope renderScope = ModProfiler.getInstance().scope("render/PlayerHealthBarRenderHook/renderBars")) {
                PlayerHealthBarRenderer.render(
                        context.matrixStack(),
                        bufferSource,
                        context.tickCounter().getGameTimeDeltaPartialTick(true)
                );
            }
            try (ModProfiler.Scope batchScope = ModProfiler.getInstance().scope("render/PlayerHealthBarRenderHook/endBatch")) {
                // Flush only the bar geometry. Numeric HP remains in the shared font batch and is
                // flushed by the normal world renderer; the no-arg endBatch() walks the entire shared
                // buffer registry and was ~90% of this feature's cost.
                bufferSource.endBatch(RenderType.debugQuads());
            }
        }
    }
}
