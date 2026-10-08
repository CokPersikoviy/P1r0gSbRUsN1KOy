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
        return vanilla;
    }

    public static FogData fog(FogData fog, FogType fluid) {
        var config = ConfigManager.get().visuals;
        // Keep liquid/powder-snow fog and all distances/effects from vanilla.
        if (config.fogColorEnabled && fluid == FogType.NONE) {
            int color = config.fogColor;
            fog.color.set(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f, 1);
        }
        return fog;
    }
}
