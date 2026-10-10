package ru.wilyfox.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.chat.ChatDock;
import ru.wilyfox.client.chat.ChatTab;

/** Even slash-prefixed FH text must not request command completions from Minecraft. */
@Mixin(CommandSuggestions.class)
public class BackendChatSuggestionsMixin {
    @Inject(method = "updateCommandInfo", at = @At("HEAD"), cancellable = true)
    private void froghelper$backendOnly(CallbackInfo ci) {
        if (Minecraft.getInstance().gui.screen() instanceof ChatScreen && ChatDock.outgoingChannel() == ChatTab.FH) {
            ((CommandSuggestions) (Object) this).hide(); ci.cancel();
        }
    }
}
