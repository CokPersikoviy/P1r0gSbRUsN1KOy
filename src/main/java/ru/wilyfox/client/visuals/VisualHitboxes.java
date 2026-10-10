package ru.wilyfox.client.visuals;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.VisualsConfig;
import ru.wilyfox.client.profiler.ModProfiler;

public final class VisualHitboxes {
    private VisualHitboxes() {}
    public static void emit(Frustum frustum, float partialTick) {
        var mc = Minecraft.getInstance(); var c = ConfigManager.get().visuals.hitboxes;
        if (mc.level == null) return;
        var camera = mc.gameRenderer.mainCamera().position();
        try (var profile = ModProfiler.getInstance().scope("visuals/hitboxes");
             var collector = mc.levelExtractor.collectPerFrameMainThreadGizmos()) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity.isInvisible() || c.targets == VisualsConfig.Targets.LIVING && !(entity instanceof LivingEntity)
                        || c.targets == VisualsConfig.Targets.PLAYERS && !(entity instanceof Player)) continue;
                if (entity == mc.getCameraEntity() && (!c.self || mc.options.getCameraType().isFirstPerson())) continue;
                if (!mc.levelExtractor.isEntityVisible(entity, frustum, camera.x, camera.y, camera.z)) continue;
                draw(entity, partialTick, c);
                if (entity instanceof EnderDragon dragon) for (var part : dragon.getSubEntities()) draw(part, partialTick, c);
            }
        }
    }
    private static void draw(Entity entity, float partialTick, VisualsConfig.Hitboxes c) {
        var mc = Minecraft.getInstance();
        var pos = entity.getPosition(partialTick);
        AABB box = entity.getBoundingBox().move(pos.subtract(entity.position()));
        int color = c.hit && entity instanceof LivingEntity living && living.hurtTime > 0 ? c.hitColor
                : c.hover && mc.crosshairPickEntity == entity ? c.hoverColor : c.color;
        if (c.style == VisualsConfig.LineStyle.NORMAL) Gizmos.cuboid(box,
                c.fill ? GizmoStyle.strokeAndFill(color, (float) c.width, c.fillColor) : GizmoStyle.stroke(color, (float) c.width));
        else {
            if (c.fill) Gizmos.cuboid(box, GizmoStyle.fill(c.fillColor));
            VisualWorldLines.box(box, c.style, color, (float) c.width, false);
        }
        if (c.eyeLine) {
            double y = box.minY + entity.getEyeHeight();
            var a = new net.minecraft.world.phys.Vec3(box.minX, y, box.minZ);
            var b = new net.minecraft.world.phys.Vec3(box.maxX, y, box.minZ);
            var d = new net.minecraft.world.phys.Vec3(box.maxX, y, box.maxZ);
            var e = new net.minecraft.world.phys.Vec3(box.minX, y, box.maxZ);
            Gizmos.line(a, b, c.eyeColor, (float) c.width); Gizmos.line(b, d, c.eyeColor, (float) c.width);
            Gizmos.line(d, e, c.eyeColor, (float) c.width); Gizmos.line(e, a, c.eyeColor, (float) c.width);
        }
        if (c.lookArrow) {
            var eye = pos.add(0, entity.getEyeHeight(), 0);
            Gizmos.arrow(eye, eye.add(entity.getViewVector(partialTick).scale(2)), c.lookColor, (float) c.width);
        }
    }
}
