package ru.wilyfox.client.utility;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import ru.wilyfox.bridge.PlayerFishingAccessor;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.profiler.ModProfiler;

public final class AutoFish {
    private static int consecutiveTouchTicks = 0;

    private AutoFish() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("tick/AutoFish")) {
                if (!ConfigManager.get().fishing.autoFish) {
                    resetHookState();
                    return;
                }

                if (client.player == null || client.level == null || client.gameMode == null) {
                    resetHookState();
                    return;
                }

                FishingHook hook = getFishingHook(client);
                if (hook == null || !hook.isAlive()) {
                    resetHookState();
                    return;
                }

                if (!isSubmergedInWaterOrLava(hook)) {
                    consecutiveTouchTicks = 0;
                    return;
                }

                consecutiveTouchTicks++;
                int requiredTicks = Math.max(0, ConfigManager.get().fishing.autoFishDelayTicks);
                if (consecutiveTouchTicks >= requiredTicks) {
                    consecutiveTouchTicks = 0;
                    hook.discard();
                    client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
                }
            }
        });
    }

    private static FishingHook getFishingHook(Minecraft client) {
        if (client.player instanceof PlayerFishingAccessor accessor) {
            return accessor.froghelper$getFishingHook();
        }
        return null;
    }

    private static void resetHookState() {
        consecutiveTouchTicks = 0;
    }

    private static boolean isSubmergedInWaterOrLava(FishingHook hook) {
        return hook.getFluidHeight(FluidTags.WATER) > 0.0D || hook.getFluidHeight(FluidTags.LAVA) > 0.0D;
    }
}
