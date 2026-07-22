package ru.wilyfox.client.alchemy;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import ru.wilyfox.client.hud.widget.WidgetUtils;
import ru.wilyfox.utils.WorldToScreen;

public final class AlchemyIngredientOverlayRenderer {
    private static final long LIFETIME_MS = 2_000L;
    private static final int MARKER_RED = 0xFFE34B4B;
    private static final double WORLD_MARKER_SIZE = 1.5;
    private AlchemyIngredientOverlayRenderer() {
    }

    public static void render(GuiGraphics context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gameRenderer == null) {
            return;
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        long now = System.currentTimeMillis();

        for (AlchemyIngredientSpot spot : AlchemyIngredientTracker.getInstance().getActiveSpots()) {
            Vec3 renderPos = spot.center().add(0.0, 0.15, 0.0);
            WorldToScreen.ScreenPoint point = WorldToScreen.project(renderPos);
            if (point == null) {
                continue;
            }

            double distance = cameraPos.distanceTo(renderPos);
            int size = getMarkerSize(mc, distance);
            int alpha = getMarkerAlpha(now - spot.createdAtMillis());
            if (alpha <= 8) {
                continue;
            }

            int half = size / 2;
            int color = withAlpha(MARKER_RED, alpha);
            WidgetUtils.drawCorners(context, point.x() - half, point.y() - half, size, size, color);
        }
    }

    private static int getMarkerAlpha(long ageMs) {
        float fade = 1.0f - Math.max(0.0f, Math.min(1.0f, (ageMs - 1_600L) / 400.0f));
        return Math.round(255.0f * fade);
    }

    private static int withAlpha(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }

    private static int getMarkerSize(Minecraft minecraft, double distance) {
        double safeDistance = Math.max(0.25, distance);
        double halfFov = Math.toRadians(minecraft.options.fov().get() / 2.0);
        double projected = WORLD_MARKER_SIZE * minecraft.getWindow().getGuiScaledHeight()
                / (2.0 * safeDistance * Math.tan(halfFov));
        return Math.max(6, Math.min(minecraft.getWindow().getGuiScaledHeight(), (int) Math.round(projected)));
    }
}
