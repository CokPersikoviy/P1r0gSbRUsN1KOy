package ru.wilyfox.client.visuals;

import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.material.FogType;
import ru.wilyfox.client.hud.config.ConfigManager;

/** Small local render overrides. No extra framebuffer, geometry cache or server state. */
public final class Visuals {
    private Visuals() {}

    public static float aspect(float vanilla) {
        double ratio = ConfigManager.get().visuals.ratio();
        return ratio > 0 ? (float) ratio : vanilla;
    }

    public static float width(float vanillaWidth, float height) {
        double ratio = ConfigManager.get().visuals.ratio();
        return ratio > 0 ? (float) (height * ratio) : vanillaWidth;
    }

    public static float handWidth(float width, float height) {
        return ConfigManager.get().visuals.stretchHand ? width(width, height) : width;
    }

    public static Object environmentColor(EnvironmentAttribute<?> attribute, Object vanilla) {
        var config = ConfigManager.get().visuals;
        if (attribute == EnvironmentAttributes.SKY_COLOR && config.skyColorEnabled) return config.skyColor & 0xFFFFFF;
        if (attribute == EnvironmentAttributes.FOG_COLOR && config.fogColorEnabled) return config.fogColor & 0xFFFFFF;
        if (attribute == EnvironmentAttributes.CLOUD_COLOR && config.cloudColorEnabled) return config.cloudColor;
        return vanilla;
    }

    public static void lightmap(net.minecraft.client.renderer.state.LightmapRenderState state) {
        var c = ConfigManager.get().visuals;
        if (c.brightnessEnabled) {
            state.brightness = Math.min(c.brightness / 100f, 1);
            if (c.brightness > 100) {
                state.nightVisionEffectIntensity = Math.max(state.nightVisionEffectIntensity, (c.brightness - 100) / 100f);
                state.darknessEffectScale = 0;
            }
        }
        if (c.skyLightEnabled) state.skyLightColor = rgb(c.skyLightColor);
        if (c.blockLightEnabled) state.blockLightTint = rgb(c.blockLightColor);
    }
    private static org.joml.Vector3f rgb(int color) {
        return new org.joml.Vector3f((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f);
    }
    public static long clock(long ticks) {
        var c = ConfigManager.get().visuals;
        return c.timeEnabled ? Math.floorDiv(ticks, 24000L) * 24000 + c.time : ticks;
    }
    public static float rain(float value) {
        return switch (ConfigManager.get().visuals.weather) { case SERVER -> value; case CLEAR -> 0; case RAIN, THUNDER -> 1; };
    }
    public static float thunder(float value) {
        return switch (ConfigManager.get().visuals.weather) { case SERVER -> value; case CLEAR, RAIN -> 0; case THUNDER -> 1; };
    }
    public static int alpha(int color, double multiplier) {
        return color & 0xFFFFFF | (int) Math.round((color >>> 24) * Math.clamp(multiplier, 0, 1)) << 24;
    }
    public static int mix(int a, int b, double t) {
        t = Math.clamp(t, 0, 1); int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) out |= (int) Math.round((a >>> shift & 255) * (1 - t) + (b >>> shift & 255) * t) << shift;
        return out;
    }

    public static FogData fog(FogData fog, FogType fluid) {
        var config = ConfigManager.get().visuals;
        if (fluid != FogType.NONE && !config.fluidFog || fluid == FogType.NONE && config.fogMode == ru.wilyfox.client.hud.config.VisualsConfig.FogMode.NONE) {
            fog.environmentalStart = fog.environmentalEnd = fog.renderDistanceStart = fog.renderDistanceEnd = 1_000_000;
        } else if (fluid == FogType.NONE && config.fogMode == ru.wilyfox.client.hud.config.VisualsConfig.FogMode.CUSTOM) {
            fog.environmentalStart *= (float) config.fogDistance; fog.environmentalEnd *= (float) config.fogDistance;
            fog.renderDistanceStart *= (float) config.fogDistance; fog.renderDistanceEnd *= (float) config.fogDistance;
        }
        if (config.fogColorEnabled && fluid == FogType.NONE) {
            int color = config.fogColor;
            fog.color.set(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f, 1);
        }
        return fog;
    }
}
