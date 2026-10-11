package ru.wilyfox.mixin;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import ru.wilyfox.client.chat.ChatTabOverlay;

@Mixin(CommandSuggestions.class)
public abstract class ChatSuggestionsLayoutMixin {
    @Shadow @Final private Screen screen;
    @Shadow @Final private boolean anchorToBottom;

    @ModifyArg(method = "showSuggestions", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/components/CommandSuggestions$SuggestionsList;<init>(Lnet/minecraft/client/gui/components/CommandSuggestions;IIILjava/util/List;Z)V"), index = 2)
    private int froghelper$aboveTabs(int y) {
        if (!(screen instanceof ChatScreen) || !anchorToBottom) return y;
        // Vanilla places the bottom of the list three pixels above this anchor.
        // Move its actual rectangle so drawing, hovering and clicks agree.
        return Math.min(y, ChatTabOverlay.getInstance().rowTop(screen.height) + 1);
    }
}
