package ru.wilyfox.client.dungeon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;

import java.util.Locale;

public final class DungeonDecorationHighlightRenderHook {
    private static final float RED = 1.0F;
    private static final float GREEN = 207.0F / 255.0F;
    private static final float BLUE = 36.0F / 255.0F;
    private static final float ALPHA = 1.0F;
    private static final double BOX_SIZE = 1.0D;

    private DungeonDecorationHighlightRenderHook() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(DungeonDecorationHighlightRenderHook::onAfterEntities);
    }

    private static void onAfterEntities(WorldRenderContext context) {
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("render/DungeonDecorationHighlightRenderHook")) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null || context.matrixStack() == null) {
                return;
            }

            if (!ConfigManager.get().render.dungeonDecorationHighlight) {
                return;
            }

            if (!isDungeonLocation()) {
                return;
            }

            Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
            MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
            VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());
            PoseStack poseStack = context.matrixStack();

            for (Entity entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof Display.ItemDisplay itemDisplay)) {
                    continue;
                }

                if (!shouldHighlight(itemDisplay)) {
                    continue;
                }

                Vec3 position = itemDisplay.position();
                AABB box = AABB.ofSize(
                                new Vec3(position.x, position.y + BOX_SIZE * 0.5D, position.z),
                                BOX_SIZE,
                                BOX_SIZE,
                                BOX_SIZE
                        )
                        .move(-cameraPos.x, -cameraPos.y, -cameraPos.z);
                ShapeRenderer.renderLineBox(poseStack, vertexConsumer, box, RED, GREEN, BLUE, ALPHA);
            }

            bufferSource.endBatch(RenderType.lines());
        }
    }

    private static boolean shouldHighlight(Display.ItemDisplay itemDisplay) {
        ItemStack stack = itemDisplay.getSlot(0).get();
        if (stack.isEmpty()) {
            return false;
        }

        CustomModelData customModelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (customModelData == null) {
            return false;
        }

        Float firstValue = customModelData.getFloat(0);
        if (firstValue == null) {
            return false;
        }

        int cmd = (int) (float) firstValue;
        return (cmd >= 10271 && cmd <= 10282) || (cmd >= 10311 && cmd <= 10327);
    }

    private static boolean isDungeonLocation() {
        String location = DiamondWorldProtocolClient.getCurrentGameLocation();
        return location != null && location.trim().toLowerCase(Locale.ROOT).startsWith("dungeon");
    }
}
