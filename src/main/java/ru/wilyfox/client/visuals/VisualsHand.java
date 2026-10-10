package ru.wilyfox.client.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.VisualsConfig;

public final class VisualsHand {
    private VisualsHand() {}
    public static VisualsConfig.Transform transform(InteractionHand hand) {
        var c = ConfigManager.get().visuals.hand;
        return hand == InteractionHand.MAIN_HAND || c.mirror ? c.main : c.off;
    }
    public static void apply(PoseStack pose, InteractionHand hand) {
        if (!ConfigManager.get().visuals.hand.enabled) return;
        var t = transform(hand); if (t.identity()) return;
        var player = Minecraft.getInstance().player;
        var arm = player == null ? HumanoidArm.RIGHT : player.getMainArm();
        if (hand == InteractionHand.OFF_HAND) arm = arm.getOpposite();
        int sign = arm == HumanoidArm.RIGHT ? 1 : -1;
        pose.translate(t.x * sign, t.y, t.z);
        if (t.pitch != 0) pose.mulPose(Axis.XP.rotationDegrees(t.pitch));
        if (t.yaw != 0) pose.mulPose(Axis.YP.rotationDegrees(t.yaw * sign));
        if (t.roll != 0) pose.mulPose(Axis.ZP.rotationDegrees(t.roll * sign));
    }
    public static void scale(PoseStack pose, InteractionHand hand) {
        if (!ConfigManager.get().visuals.hand.enabled) return;
        float scale = (float) transform(hand).scale;
        if (scale != 1) pose.scale(scale, scale, scale);
    }
    public static float equip(float value) {
        var c = ConfigManager.get().visuals.hand;
        return c.enabled && !c.equipAnimation ? 0 : value;
    }
    public static float fov(float vanilla) {
        var c = ConfigManager.get().visuals.hand;
        return c.enabled && c.fovEnabled ? vanilla * c.fov / 70f : vanilla;
    }
    public static int swing(LivingEntity entity, int duration) {
        var c = ConfigManager.get().visuals.hand;
        return c.enabled && c.swingSpeed != 1 && entity == Minecraft.getInstance().player
                ? Math.max(1, (int) Math.round(duration / c.swingSpeed)) : duration;
    }
}
