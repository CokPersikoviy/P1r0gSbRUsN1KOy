package ru.wilyfox.client.hud.healthbar;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.Optional;
import ru.wilyfox.mixin.RenderTypeInvoker;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.client.profiler.ModProfiler;

public final class PlayerHealthBarRenderer {
    private static final float WORLD_SCALE = 0.025f;
    private static final int BAR_WIDTH = 44;
    private static final int BAR_HEIGHT = 5;
    private static final int BG_PADDING = 2;
    private static final int ACCENT_HEIGHT = 1;
    private static final float PANEL_Z = 0.0f;
    private static final float ACCENT_Z = 0.001f;
    private static final float FILL_Z = 0.002f;
    private static final float TEXT_Z = 0.004f;
    private static final float OCCLUDED_BRIGHTNESS = 0.45f;
    private static final class SeeThroughType {
        private static final RenderType INSTANCE = createSeeThroughType();
    }

    private static RenderType createSeeThroughType() {
        RenderPipeline base = RenderPipelines.DEBUG_QUADS;
        var snippet = new RenderPipeline.Snippet(Optional.of(base.getVertexShader()),
                Optional.of(base.getFragmentShader()), Optional.of(base.getShaderDefines()),
                Optional.of(base.getBindGroupLayouts()), base.getColorTargetStates(),
                base.getColorTargetStates().length, Optional.empty(), Optional.of(base.getPolygonMode()),
                Optional.of(false), base.getVertexFormatBindings(), Optional.of(base.getPrimitiveTopology()));
        var pipeline = RenderPipeline.builder(snippet)
                .withLocation(net.minecraft.resources.Identifier.fromNamespaceAndPath("froghelper", "health_bar_see_through")).build();
        return RenderTypeInvoker.froghelper$create("froghelper_health_bar_see_through",
                RenderSetup.builder(pipeline).createRenderSetup());
    }
    private static final double BAR_Y_OFFSET = 0.85;
    private static final double FADE_START_DISTANCE = 8.0;
    private static final double FADE_END_DISTANCE = 20.0;
    private static final float MIN_DISTANCE_ALPHA = 0.12f;

    // Occlusion cache: isVisibleToCamera() raycasts the world (Level.clip) per player, and doing it every
    // frame for every nearby player scales badly on crowded bosses (~22 raycasts/frame here). Occlusion
    // changes slowly, so cache the result per player identity for a short TTL and re-raycast only when
    // it goes stale — a ~150 ms lag on a health bar appearing/hiding is imperceptible.
    private static final long VISIBILITY_TTL_MS = 150L;
    private static final long VISIBILITY_STALE_MS = 3000L; // evict players not queried this long (they left)
    private static final java.util.Map<Player, long[]> VISIBILITY_CACHE = new java.util.IdentityHashMap<>();
    private static ClientLevel visibilityLevel;
    private static long lastVisibilityPruneMs;

    private PlayerHealthBarRenderer() {
    }

    static void clearVisibilityCache() {
        VISIBILITY_CACHE.clear();
        lastVisibilityPruneMs = 0L;
        visibilityLevel = null;
    }

    public static void render(PoseStack poseStack, SubmitNodeCollector collector, float partialTick) {
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("render/PlayerHealthBarRenderer/frame")) {
        Minecraft mc = Minecraft.getInstance();
        if (visibilityLevel != mc.level) {
            clearVisibilityCache();
            visibilityLevel = mc.level;
        }
        if (mc.level == null || mc.player == null || mc.gameRenderer == null) {
            ModProfiler.getInstance().incrementCounter("render/PlayerHealthBarRenderer/skippedNoWorld");
            return;
        }

        var settings = ConfigManager.get().playerHealthBars;
        if (!settings.active || settings.opacityPercent <= 0) {
            ModProfiler.getInstance().incrementCounter("render/PlayerHealthBarRenderer/skippedDisabled");
            return;
        }

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        Camera camera = dispatcher.camera;
        if (camera == null) {
            ModProfiler.getInstance().incrementCounter("render/PlayerHealthBarRenderer/skippedNoCamera");
            return;
        }

        Vec3 cameraPos = camera.position();
        long now = System.currentTimeMillis();
        pruneVisibilityCache(now);
        int candidates = 0;
        int rendered = 0;
        int skippedSelf = 0;
        int skippedDead = 0;
        int skippedInvisible = 0;
        int skippedOccluded = 0;
        int skippedDistance = 0;
        int renderedOccluded = 0;
        try (ModProfiler.Scope iterateScope = ModProfiler.getInstance().scope("render/PlayerHealthBarRenderer/iteratePlayers")) {
            for (Player target : mc.level.players()) {
                candidates++;
                if (target == mc.player) {
                    skippedSelf++;
                    continue;
                }

                if (!target.isAlive() || target.isRemoved()) {
                    skippedDead++;
                    continue;
                }

                if (target.isInvisible()) {
                    skippedInvisible++;
                    continue;
                }

                double x = Mth.lerp(partialTick, target.xOld, target.getX());
                double y = Mth.lerp(partialTick, target.yOld, target.getY()) + target.getBbHeight() + BAR_Y_OFFSET;
                double z = Mth.lerp(partialTick, target.zOld, target.getZ());

                double distance = Math.sqrt(cameraPos.distanceToSqr(x, y, z));
                float distanceAlpha = settings.distanceFade
                        ? getDistanceAlpha(distance)
                        : 1.0f;
                if (distanceAlpha <= 0.01f) {
                    skippedDistance++;
                    continue;
                }

                boolean visibleToCamera;
                try (ModProfiler.Scope visibilityScope = ModProfiler.getInstance().scope("render/PlayerHealthBarRenderer/visibilityCheck")) {
                    visibleToCamera = isVisibleToCameraCached(target, now);
                }
                boolean occluded = !visibleToCamera;
                if (occluded && target.isDiscrete()) {
                    skippedOccluded++;
                    continue;
                }

                poseStack.pushPose();
                poseStack.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
                poseStack.mulPose(camera.rotation());
                poseStack.scale(WORLD_SCALE, -WORLD_SCALE, WORLD_SCALE);

                try (ModProfiler.Scope renderBarScope = ModProfiler.getInstance().scope("render/PlayerHealthBarRenderer/renderBar")) {
                    renderHealthBar(poseStack, collector, target, distanceAlpha, occluded);
                }

                poseStack.popPose();
                rendered++;
                if (occluded) {
                    renderedOccluded++;
                }
            }
        }
        ModProfiler.getInstance().incrementCounter("render/PlayerHealthBarRenderer/candidates", candidates);
        ModProfiler.getInstance().incrementCounter("render/PlayerHealthBarRenderer/rendered", rendered);
        ModProfiler.getInstance().incrementCounter("render/PlayerHealthBarRenderer/skippedSelf", skippedSelf);
        ModProfiler.getInstance().incrementCounter("render/PlayerHealthBarRenderer/skippedDead", skippedDead);
        ModProfiler.getInstance().incrementCounter("render/PlayerHealthBarRenderer/skippedInvisible", skippedInvisible);
        ModProfiler.getInstance().incrementCounter("render/PlayerHealthBarRenderer/skippedOccluded", skippedOccluded);
        ModProfiler.getInstance().incrementCounter("render/PlayerHealthBarRenderer/skippedDistance", skippedDistance);
        ModProfiler.getInstance().incrementCounter("render/PlayerHealthBarRenderer/renderedOccluded", renderedOccluded);
        }
    }

    private static void renderHealthBar(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            Player target,
            float distanceAlpha,
            boolean occluded
    ) {
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("render/PlayerHealthBarRenderer/renderHealthBar")) {
        var config = ConfigManager.get().playerHealthBars;
        float health = target.getHealth();
        float maxHealth = target.getMaxHealth();
        float progress = maxHealth <= 0.0f ? 0.0f : Mth.clamp(health / maxHealth, 0.0f, 1.0f);

        // "In the red": below the configurable threshold the colour shifts toward the hard accent.
        // The strength slider controls how forcefully it snaps, so the transition reads clearly
        // instead of the previous barely-visible fade.
        float threshold = Mth.clamp(config.hardAccentThresholdPercent / 100.0f, 0.0f, 1.0f);
        float strength = Mth.clamp(config.accentStrengthPercent / 100.0f, 0.0f, 1.0f);
        float criticalBlend = threshold <= 0.0f
                ? 0.0f
                : Mth.clamp((threshold - progress) / threshold, 0.0f, 1.0f) * strength;
        boolean critical = progress < threshold;

        float sizeScale = Math.max(1, config.sizePercent) / 100.0f;
        int barWidth = Math.max(1, Math.round(BAR_WIDTH * sizeScale));
        int barHeight = Math.max(1, Math.round(BAR_HEIGHT * sizeScale));
        int bgPadding = Math.max(1, Math.round(BG_PADDING * sizeScale));
        int accentHeight = Math.max(1, Math.round(ACCENT_HEIGHT * sizeScale));

        float opacityMultiplier = config.opacityPercent / 100.0f;
        int verticalOffset = config.verticalOffset;

        int x1 = -barWidth / 2;
        int y1 = verticalOffset;
        int x2 = x1 + barWidth;
        int y2 = y1 + barHeight;
        int fillWidth = Math.round(barWidth * progress);
        int fillEndX = x1 + fillWidth;


        float finalAlpha = distanceAlpha * opacityMultiplier;
        int panelBaseColor = critical
                ? WidgetTheme.withAlpha(WidgetTheme.PANEL_BG, 0x9E)
                : WidgetTheme.withAlpha(WidgetTheme.PANEL_BG_SOFT, 0x82);
        int panelColor = applyAlpha(panelBaseColor, finalAlpha);
        int accentColor = applyAlpha(getAccentColor(criticalBlend), finalAlpha);
        int fillColor = applyAlpha(getFillColor(criticalBlend), finalAlpha);
        if (occluded) {
            panelColor = scaleRgb(panelColor, OCCLUDED_BRIGHTNESS);
            accentColor = scaleRgb(accentColor, OCCLUDED_BRIGHTNESS);
            fillColor = scaleRgb(fillColor, OCCLUDED_BRIGHTNESS);
        }

        int capturedPanelColor = panelColor;
        int capturedAccentColor = accentColor;
        int capturedFillColor = fillColor;
        collector.submitCustomGeometry(poseStack, occluded ? SeeThroughType.INSTANCE : RenderTypes.debugQuads(),
                (pose, vertexConsumer) -> {
                    Matrix4f matrix = pose.pose();
                    fillQuad(vertexConsumer, matrix, x1 - bgPadding, y1 - bgPadding, x2 + bgPadding, y2 + bgPadding, PANEL_Z, capturedPanelColor);
                    fillQuad(vertexConsumer, matrix, x1 - bgPadding, y1 - bgPadding, x2 + bgPadding, y1 - bgPadding + accentHeight, ACCENT_Z, capturedAccentColor);
                    if (fillWidth > 0) {
                        fillQuad(vertexConsumer, matrix, x1, y1, fillEndX, y2, FILL_Z, capturedFillColor);
                    }
                });

        if (config.showNumericHp) {
            ModProfiler.getInstance().incrementCounter("render/PlayerHealthBarRenderer/numeric/enabled");
            renderHealthText(
                    poseStack,
                    collector,
                    health,
                    maxHealth,
                    barWidth,
                    barHeight,
                    y1,
                    finalAlpha,
                    occluded
            );
        }
        }
    }

    private static void renderHealthText(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            float health,
            float maxHealth,
            int barWidth,
            int barHeight,
            int barY,
            float alpha,
            boolean occluded
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        String text = formatHealth(health, maxHealth);
        int textWidth = font.width(text);
        if (textWidth <= 0) {
            return;
        }

        float availableWidth = Math.max(1.0F, barWidth - 2.0F);
        float availableHeight = Math.max(1.0F, barHeight - 1.0F);
        float textScale = Math.min(
                availableWidth / textWidth,
                availableHeight / font.lineHeight
        );
        if (textScale <= 0.0F) {
            return;
        }

        float renderedWidth = textWidth * textScale;
        float renderedHeight = font.lineHeight * textScale;
        float textX = -renderedWidth / 2.0F;
        float textY = barY + (barHeight - renderedHeight) / 2.0F;
        int textColor = applyAlpha(WidgetTheme.TEXT_SOFT, alpha);
        if (occluded) {
            textColor = scaleRgb(textColor, OCCLUDED_BRIGHTNESS);
        }

        poseStack.pushPose();
        poseStack.translate(textX, textY, TEXT_Z);
        poseStack.scale(textScale, textScale, 1.0F);
        var sequence = net.minecraft.network.chat.Component.literal(text).getVisualOrderText();
        collector.submitText(poseStack, 0, 0, sequence, false, Font.DisplayMode.SEE_THROUGH,
                0xF000F0, textColor, 0, 0);
        collector.submitText(poseStack, 0, 0, sequence, true, Font.DisplayMode.NORMAL,
                0xF000F0, textColor, 0, 0);
        poseStack.popPose();
        ModProfiler.getInstance().incrementCounter("render/PlayerHealthBarRenderer/numeric/queued");
    }

    static String formatHealth(float health, float maxHealth) {
        int current = Math.max(0, Mth.ceil(health));
        int maximum = Math.max(1, Mth.ceil(maxHealth));
        return current + "/" + maximum;
    }

    private static void fillQuad(VertexConsumer vertexConsumer, Matrix4f matrix, int x1, int y1, int x2, int y2, float z, int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        vertexConsumer.addVertex(matrix, x1, y2, z).setColor(r, g, b, a);
        vertexConsumer.addVertex(matrix, x2, y2, z).setColor(r, g, b, a);
        vertexConsumer.addVertex(matrix, x2, y1, z).setColor(r, g, b, a);
        vertexConsumer.addVertex(matrix, x1, y1, z).setColor(r, g, b, a);
    }

    private static int applyAlpha(int argb, float alphaMultiplier) {
        int alpha = (argb >>> 24) & 0xFF;
        int scaledAlpha = Mth.clamp(Math.round(alpha * alphaMultiplier), 0, 255);
        return (scaledAlpha << 24) | (argb & 0x00FFFFFF);
    }

    static int scaleRgb(int argb, float multiplier) {
        float clamped = Mth.clamp(multiplier, 0.0f, 1.0f);
        int alpha = (argb >>> 24) & 0xFF;
        int red = Math.round(((argb >>> 16) & 0xFF) * clamped);
        int green = Math.round(((argb >>> 8) & 0xFF) * clamped);
        int blue = Math.round((argb & 0xFF) * clamped);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int getFillColor(float criticalBlend) {
        return lerpArgb(
                WidgetTheme.withAlpha(WidgetTheme.BAR_FILL, 0xC4),
                WidgetTheme.withAlpha(WidgetTheme.HARD_ACCENT, 0xF0),
                criticalBlend
        );
    }

    private static int getAccentColor(float criticalBlend) {
        return lerpArgb(
                WidgetTheme.withAlpha(WidgetTheme.ACCENT_LINE, 0xAA),
                WidgetTheme.withAlpha(WidgetTheme.HARD_ACCENT, 0xF6),
                criticalBlend
        );
    }

    private static int lerpArgb(int fromArgb, int toArgb, float t) {
        int fromA = (fromArgb >>> 24) & 0xFF;
        int fromR = (fromArgb >> 16) & 0xFF;
        int fromG = (fromArgb >> 8) & 0xFF;
        int fromB = fromArgb & 0xFF;

        int toA = (toArgb >>> 24) & 0xFF;
        int toR = (toArgb >> 16) & 0xFF;
        int toG = (toArgb >> 8) & 0xFF;
        int toB = toArgb & 0xFF;

        int a = Mth.lerpInt(t, fromA, toA);
        int r = Mth.lerpInt(t, fromR, toR);
        int g = Mth.lerpInt(t, fromG, toG);
        int b = Mth.lerpInt(t, fromB, toB);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float getDistanceAlpha(double distance) {
        if (distance <= FADE_START_DISTANCE) {
            return 1.0f;
        }

        if (distance >= FADE_END_DISTANCE) {
            return MIN_DISTANCE_ALPHA;
        }

        float progress = (float) ((distance - FADE_START_DISTANCE) / (FADE_END_DISTANCE - FADE_START_DISTANCE));
        return 1.0f - (1.0f - MIN_DISTANCE_ALPHA) * progress;
    }

    /** {@link #isVisibleToCamera} with a short-TTL per-player cache to avoid raycasting every frame. */
    private static boolean isVisibleToCameraCached(Player target, long now) {
        long[] cached = VISIBILITY_CACHE.get(target);
        if (cached != null && now >= cached[0] && now - cached[0] < VISIBILITY_TTL_MS) {
            return cached[1] != 0L;
        }

        boolean visible = isVisibleToCamera(target);
        if (cached == null) {
            VISIBILITY_CACHE.put(target, new long[]{now, visible ? 1L : 0L});
        } else {
            cached[0] = now;
            cached[1] = visible ? 1L : 0L;
        }
        return visible;
    }

    /** Drop cache entries for players that haven't been queried recently (left the area). Runs at most
     *  once per {@link #VISIBILITY_STALE_MS}. */
    private static void pruneVisibilityCache(long now) {
        if (now >= lastVisibilityPruneMs && now - lastVisibilityPruneMs < VISIBILITY_STALE_MS) {
            return;
        }
        lastVisibilityPruneMs = now;
        VISIBILITY_CACHE.values().removeIf(entry -> now - entry[0] > VISIBILITY_STALE_MS);
    }

    public static boolean isVisibleToCamera(Player target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gameRenderer == null || mc.player == null) {
            return false;
        }

        Vec3 from = mc.gameRenderer.mainCamera().position();
        Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.85, 0.0);

        ClipContext context = new ClipContext(
                from,
                to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        );

        BlockHitResult hit = mc.level.clip(context);
        return hit.getType() == HitResult.Type.MISS;
    }
}
