package ru.wilyfox.client.hud.fishing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import ru.wilyfox.bridge.PlayerFishingAccessor;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.profiler.ModProfiler;

import java.util.List;

/** One cached billboard. The bounded trajectory is calculated on client ticks, never per frame. */
public final class FishingCastPreview {
    private static final long UPDATE_INTERVAL_NANOS = 100_000_000L;
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/fishing/fishing_hook.png");
    private static final int COLOR = 0xBFFFFFFF;
    private static boolean registered;
    private static ClientLevel cachedLevel;
    private static LocalPlayer cachedPlayer;
    private static Vec3 sourceEye;
    private static FishingCastPrediction.Landing landing;
    private static Entity hookedEntity;
    private static long nextUpdate;

    private static final class PreviewType {
        private static final RenderType INSTANCE = RenderTypes.entityTranslucent(TEXTURE);
    }

    private FishingCastPreview() { }

    public static void register() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(FishingCastPreview::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            if (!eligible(mc) || cachedLevel != mc.level || cachedPlayer != mc.player || landing == null) return;
            // Avoid a stale marker for a frame after teleporting, even within the same ClientLevel.
            if (sourceEye == null || mc.player.getEyePosition().distanceToSqr(sourceEye) > 16.0) return;
            try (var ignored = ModProfiler.getInstance().scope("render/FishingCastPreview")) {
                var camera = mc.gameRenderer.mainCamera();
                Vec3 marker = predictedPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
                if (marker == null) return;
                Vec3 relative = marker.add(0.0, 0.025, 0.0).subtract(camera.position());
                var poses = context.poseStack();
                poses.pushPose();
                try {
                    poses.translate(relative.x, relative.y, relative.z);
                    poses.mulPose(camera.rotation());
                    context.submitNodeCollector().submitCustomGeometry(poses, PreviewType.INSTANCE,
                            FishingCastPreview::drawBobber);
                } finally {
                    poses.popPose();
                }
            }
        });
    }

    static boolean eligible(Minecraft mc) {
        if (!ConfigManager.get().fishing.showCastPreview || mc.level == null || mc.player == null
                || mc.gui.screen() != null || mc.gui.hud.isHidden() || !mc.player.isAlive() || mc.player.isSpectator()) return false;
        var hook = ((PlayerFishingAccessor) mc.player).froghelper$getFishingHook();
        return (hook == null || hook.isRemoved())
                && (mc.player.getMainHandItem().getItem() instanceof FishingRodItem
                || mc.player.getOffhandItem().getItem() instanceof FishingRodItem);
    }

    static void tick(Minecraft mc) {
        if (!eligible(mc)) {
            clear();
            return;
        }
        if (cachedLevel != mc.level || cachedPlayer != mc.player) clear();
        long now = System.nanoTime();
        if (now < nextUpdate) return;
        nextUpdate = now + UPDATE_INTERVAL_NANOS;
        cachedLevel = mc.level;
        cachedPlayer = mc.player;
        sourceEye = mc.player.getEyePosition();
        try (var ignored = ModProfiler.getInstance().scope("fishing/CastPreview/predict")) {
            var launch = FishingCastPrediction.launch(sourceEye, mc.player.getYRot(), mc.player.getXRot());
            var probe = new CastProbe(mc.level, mc.player, launch);
            landing = FishingCastPrediction.predict(launch, probe::move);
            hookedEntity = probe.hitEntity;
        }
    }

    private record Target(Entity entity, AABB box) { }

    private static final class CastProbe {
        private final ClientLevel level;
        private final List<Target> targets;
        private int step;
        private Entity hitEntity;

        private CastProbe(ClientLevel level, LocalPlayer owner, FishingCastPrediction.Launch launch) {
            this.level = level;
            targets = level.getEntities(owner, FishingCastPrediction.searchBounds(launch), entity ->
                    !entity.isRemoved() && !owner.isPassengerOfSameVehicle(entity)
                            && (entity.canBeHitByProjectile() || entity instanceof ItemEntity && entity.isAlive()))
                    .stream().map(entity -> new Target(entity, entity.getBoundingBox())).toList();
        }

        private FishingCastPrediction.Step move(Vec3 start, Vec3 velocity) {
            // ProjectileUtil.computeMargin grows from zero to 0.3 during the hook's first eight ticks.
            float margin = Math.max(0F, Math.min(0.3F, (++step - 2) / 20F));
            return FishingCastPreview.move(level, start, velocity, targets, margin, this);
        }
    }

    private static FishingCastPrediction.Step move(ClientLevel level, Vec3 start, Vec3 velocity,
                                                    List<Target> targets, float margin, CastProbe probe) {
        Vec3 next = start.add(velocity);
        if (!level.hasChunkAt(BlockPos.containing(start)) || !level.hasChunkAt(BlockPos.containing(next))
                || !level.getWorldBorder().isWithinBounds(BlockPos.containing(next))) return null;
        // FishingHook has a 0.25 x 0.25 collision box. Use vanilla shape collision, not full block cubes.
        var box = new AABB(start.x - 0.125, start.y, start.z - 0.125,
                start.x + 0.125, start.y + 0.25, start.z + 0.125);
        Vec3 movement = Entity.collideBoundingBox(CollisionContext.empty(), velocity, box, level, List.of());
        Vec3 moved = start.add(movement);
        var fluidHit = level.clip(new ClipContext(start, moved, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.WATER, CollisionContext.empty()));
        boolean water = fluidHit.getType() != HitResult.Type.MISS
                && level.getFluidState(fluidHit.getBlockPos()).is(FluidTags.WATER);
        Vec3 end = water ? fluidHit.getLocation() : moved;
        double nearest = Double.POSITIVE_INFINITY;
        Entity targetEntity = null;
        for (Target target : targets) {
            var hit = target.box.inflate(margin).clip(start, end);
            if (hit.isEmpty()) continue;
            double distance = start.distanceToSqr(hit.get());
            if (distance < nearest) {
                nearest = distance;
                targetEntity = target.entity;
            }
        }
        if (targetEntity != null) {
            probe.hitEntity = targetEntity;
            return new FishingCastPrediction.Step(hookedPosition(targetEntity, 1F), true, false);
        }
        if (water) {
            return new FishingCastPrediction.Step(fluidHit.getLocation(), true, true);
        }
        boolean stopped = movement.distanceToSqr(velocity) > 1.0E-12;
        return new FishingCastPrediction.Step(moved, stopped, false);
    }

    private static void drawBobber(PoseStack.Pose pose, VertexConsumer vertices) {
        // Vanilla bobber sprite size and UVs; normal world depth testing hides it behind obstacles.
        vertex(vertices, pose, -0.25F, -0.25F, 0F, 1F);
        vertex(vertices, pose, 0.25F, -0.25F, 1F, 1F);
        vertex(vertices, pose, 0.25F, 0.25F, 1F, 0F);
        vertex(vertices, pose, -0.25F, 0.25F, 0F, 0F);
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float u, float v) {
        vertices.addVertex(pose, x, y, 0F).setColor(COLOR).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0F, 1F, 0F);
    }

    static void clear() {
        landing = null;
        hookedEntity = null;
        sourceEye = null;
        cachedLevel = null;
        cachedPlayer = null;
        nextUpdate = 0L;
    }

    private static Vec3 hookedPosition(Entity entity, float partialTick) {
        return entity.getPosition(partialTick).add(0.0, entity.getBbHeight() * 0.8, 0.0);
    }

    private static Vec3 predictedPosition(float partialTick) {
        if (landing == null) return null;
        if (hookedEntity == null) return landing.position();
        if (hookedEntity.isRemoved() || !hookedEntity.isAlive() || hookedEntity.level() != cachedLevel) return null;
        return hookedPosition(hookedEntity, partialTick);
    }

    static Vec3 predictedPosition() { return predictedPosition(1F); }
    static Entity predictedEntity() { return hookedEntity; }
}
