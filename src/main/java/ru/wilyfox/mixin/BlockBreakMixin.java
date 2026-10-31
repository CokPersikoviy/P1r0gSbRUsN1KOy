package ru.wilyfox.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.wilyfox.client.utility.PendingBlockBreaks;

@Mixin(MultiPlayerGameMode.class)
public class BlockBreakMixin {
    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void froghelper$captureOriginalState(BlockPos pos, CallbackInfoReturnable<Boolean> cir,
                                                 @Share("original") LocalRef<BlockState> original) {
        var level = Minecraft.getInstance().level;
        original.set(level == null ? null : level.getBlockState(pos));
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void froghelper$recordBlockBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir,
                                             @Share("original") LocalRef<BlockState> original) {
        if (!cir.getReturnValue()) return;
        var level = Minecraft.getInstance().level;
        var prediction = level == null ? null : ((ClientLevelPredictionAccessor) level).froghelper$getBlockStatePredictionHandler();
        if (prediction != null && prediction.isPredicting() && original.get() != null) {
            PendingBlockBreaks.predicted(pos, original.get(), prediction.currentSequence());
        } else {
            PendingBlockBreaks.confirmed();
        }
    }
}
