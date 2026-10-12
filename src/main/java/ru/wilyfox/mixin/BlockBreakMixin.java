package ru.wilyfox.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.wilyfox.client.utility.BlockBreakCounter;

@Mixin(MultiPlayerGameMode.class)
public class BlockBreakMixin {
    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void froghelper$recordBlockBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            BlockBreakCounter.recordBreak();
            ru.wilyfox.client.statistic.MiningStatisticsTracker.recordBreak();
        }
    }
}
