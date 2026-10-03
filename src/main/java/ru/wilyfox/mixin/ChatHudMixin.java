package ru.wilyfox.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.chat.IncomingChatHandler;
import ru.wilyfox.client.chat.ChatMessageDecorator;
import ru.wilyfox.client.chat.ChatMessageSanitizer;
import ru.wilyfox.client.chat.ChatTabManager;
import ru.wilyfox.client.chat.HigherBitingNotifier;

@Mixin(ChatComponent.class)
public class ChatHudMixin {
    @ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true)
    private Component froghelper$decorateChat(Component component) {
        if (!ChatTabManager.getInstance().isRebuilding()) {
            HigherBitingNotifier.onIncomingMessage(ChatMessageSanitizer.forLogic(component));
        }
        return ChatMessageDecorator.decorate(component);
    }

    @Inject(method = "addMessage", at = @At("HEAD"), cancellable = true)
    private void froghelper$captureSimple(Component component, net.minecraft.network.chat.MessageSignature signature, net.minecraft.client.multiplayer.chat.GuiMessageSource source, net.minecraft.client.multiplayer.chat.GuiMessageTag tag, CallbackInfo ci) {
        if (IncomingChatHandler.handle(component)) {
            ci.cancel();
        }
    }

    @ModifyConstant(
            method = {
                    "addMessageToDisplayQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V",
                    "addMessageToQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V"
            },
            constant = @Constant(intValue = 100)
    )
    private int froghelper$extendChatHistory(int original) {
        return Math.max(original, original + Math.max(0, ru.wilyfox.client.hud.config.ConfigManager.get().render.extraChatHistoryLines));
    }
}
