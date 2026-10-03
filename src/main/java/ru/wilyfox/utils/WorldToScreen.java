package ru.wilyfox.utils;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class WorldToScreen {
    private WorldToScreen() {
    }

    public static ScreenPoint project(Vec3 worldPos) {
        Projection projection = projectDetailed(worldPos);
        if (projection == null || !projection.onScreen()) {
            return null;
        }

        return new ScreenPoint(Math.round(projection.screenX()), Math.round(projection.screenY()));
    }

    public static Projection projectDetailed(Vec3 worldPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gameRenderer == null) {
            return null;
        }

        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 cameraPos = camera.position();
        Vec3 relative = worldPos.subtract(cameraPos);

        Vector3f cameraSpace = new Vector3f(
                (float) relative.x,
                (float) relative.y,
                (float) relative.z
        );

        Quaternionf rotation = new Quaternionf(camera.rotation());
        rotation.conjugate();
        cameraSpace.rotate(rotation);

        float depth = -cameraSpace.z;
        boolean behindCamera = depth <= 0.01f;
        float safeDepth = Math.max(Math.abs(depth), 0.01f);

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        float aspect = (float) screenWidth / (float) screenHeight;
        float fov = (float) mc.options.fov().get();
        float tanHalfFov = (float) Math.tan(Math.toRadians(fov / 2.0f));

        float ndcX = cameraSpace.x / (safeDepth * tanHalfFov * aspect);
        float ndcY = cameraSpace.y / (safeDepth * tanHalfFov);

        float indicatorX = ndcX;
        float indicatorY = -ndcY;

        if (behindCamera) {
            indicatorX = cameraSpace.x;
            indicatorY = -cameraSpace.y;

            if (Math.abs(indicatorX) < 1.0E-4f && Math.abs(indicatorY) < 1.0E-4f) {
                indicatorY = 1.0f;
            }
        }

        float screenX = (ndcX * 0.5f + 0.5f) * screenWidth;
        float screenY = (1.0f - (ndcY * 0.5f + 0.5f)) * screenHeight;
        boolean onScreen = !behindCamera && ndcX >= -1.0f && ndcX <= 1.0f && ndcY >= -1.0f && ndcY <= 1.0f;

        return new Projection(screenX, screenY, ndcX, ndcY, indicatorX, indicatorY, onScreen, behindCamera);
    }

    public record ScreenPoint(int x, int y) {
    }

    public record Projection(
            float screenX,
            float screenY,
            float ndcX,
            float ndcY,
            float indicatorX,
            float indicatorY,
            boolean onScreen,
            boolean behindCamera
    ) {
    }
}
