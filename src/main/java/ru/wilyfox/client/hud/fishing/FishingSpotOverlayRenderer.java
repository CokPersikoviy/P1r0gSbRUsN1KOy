package ru.wilyfox.client.hud.fishing;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.client.hud.widget.WidgetUtils;
import ru.wilyfox.utils.WorldToScreen;

public final class FishingSpotOverlayRenderer {
    private static final long LIFETIME_MS = 1000L;
    private static final double HORIZONTAL_FOOTPRINT_SCALE = 3.5D;

    private FishingSpotOverlayRenderer() {
    }

    public static void render(GuiGraphics context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gameRenderer == null) {
            return;
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        long now = System.currentTimeMillis();

        for (FishingSpot spot : FishingSpotTracker.getInstance().getActiveSpots()) {
            Vec3 renderPos = spot.center();

            WorldToScreen.ScreenPoint point = WorldToScreen.project(renderPos);
            if (point == null) {
                continue;
            }

            double distance = cameraPos.distanceTo(renderPos);

            int size = getPulsingMarkerSize(spot, distance);
            int alpha = getMarkerAlpha(spot.bubbleCount(), now - spot.latestTimestamp());

            if (alpha <= 8) {
                continue;
            }

            int half = size / 2;
            int color = withAlpha(WidgetTheme.TEXT_SOFT, alpha);

            WidgetUtils.drawCorners(
                    context,
                    point.x() - half,
                    point.y() - half,
                    size,
                    size,
                    color
            );
        }
    }

    private static int getMarkerSize(double distance) {
        // Ближе = больше, дальше = меньше
        // ~0-3 блока -> крупнее
        // ~20+ блоков -> компактнее
        float t = (float) Math.max(0.0, Math.min(1.0, (distance - 2.0) / 18.0));
        return Math.round((float) ((7.0f - t * 4.0f) * HORIZONTAL_FOOTPRINT_SCALE));
    }

    private static int getMarkerAlpha(int bubbleCount, long ageMs) {
        // Сила по пузырькам:
        // 45+ = полностью видно
        float bubbleFactor = Math.max(0.0f, Math.min(1.0f, bubbleCount / 45.0f));

        // Мягкий fade-out по возрасту:
        // чем ближе к удалению, тем слабее
        float ageFactor = 1.0f - Math.max(0.0f, Math.min(1.0f, ageMs / (float) LIFETIME_MS));

        // Немного смягчаем затухание, чтобы не исчезал слишком рано
        ageFactor = 0.35f + ageFactor * 0.65f;

        return Math.round(255.0f * bubbleFactor * ageFactor);
    }

    private static int withAlpha(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }

    private static int getPulsingMarkerSize(FishingSpot spot, double distance) {
        float t = (float) Math.max(0.0, Math.min(1.0, (distance - 2.0) / 18.0));
        float baseSize = (float) ((7.0f - t * 4.0f) * HORIZONTAL_FOOTPRINT_SCALE);

        double time = System.currentTimeMillis() / 1000.0;
        double phase = (spot.center().x + spot.center().z) * 0.8;
        double pulse = Math.sin(time * 2.2 + phase);

        float amplitude = 2f - t * 0.5f;
        return Math.round(baseSize + (float) pulse * amplitude);
    }
}
