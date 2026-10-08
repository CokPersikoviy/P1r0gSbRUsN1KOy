package ru.wilyfox.client.hud.fishing;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

public final class FishingParticleTypes {
    private FishingParticleTypes() {}

    public static boolean isFishingSpot(ParticleOptions options) {
        if (options == null) return false;
        var type = options.getType();
        // EvoPlus 3.3.2 uses BUBBLE, LAVA and DUST. Keep the older bubble variants too.
        return type == ParticleTypes.BUBBLE || type == ParticleTypes.LAVA || type == ParticleTypes.DUST
                || type == ParticleTypes.BUBBLE_POP || type == ParticleTypes.BUBBLE_COLUMN_UP;
    }
}
