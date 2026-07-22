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
import ru.wilyfox.client.chat.AutoBossAnnouncer;
import ru.wilyfox.client.chat.AutoThanks;
import ru.wilyfox.client.chat.BoosterChatDebug;
import ru.wilyfox.client.chat.BossShareService;
import ru.wilyfox.client.chat.ChatDispatchQueue;
import ru.wilyfox.client.chat.ChatMessageDecorator;
import ru.wilyfox.client.chat.ChatMessageSanitizer;
import ru.wilyfox.client.chat.ChatTabManager;
import ru.wilyfox.client.chat.HigherBitingNotifier;
import ru.wilyfox.client.chat.PrivateMessagePopUpNotifier;
import ru.wilyfox.client.chat.VisibilityStatusTracker;
import ru.wilyfox.client.clan.PlayerClanStorage;
import ru.wilyfox.client.combo.ComboTimerChatTracker;
import ru.wilyfox.client.moduser.ModUserProtocol;

@Mixin(ChatComponent.class)
public class ChatHudMixin {
    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), argsOnly = true)
    private Component froghelper$decorateChat(Component component) {
        if (!ChatTabManager.getInstance().isRebuilding()) {
            HigherBitingNotifier.onIncomingMessage(ChatMessageSanitizer.forLogic(component));
        }
        return ChatMessageDecorator.decorate(component);
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    private void froghelper$captureSimple(Component component, CallbackInfo ci) {
        ChatTabManager manager = ChatTabManager.getInstance();
        if (manager.isRebuilding()) {
            return;
        }

        Component logicalComponent = ChatMessageSanitizer.forLogic(component);

        ChatDispatchQueue.handleIncomingMessage(logicalComponent);
        BoosterChatDebug.onIncomingMessage(logicalComponent);
        AutoThanks.onIncomingMessage(logicalComponent);
        AutoBossAnnouncer.onIncomingMessage(logicalComponent);
        PrivateMessagePopUpNotifier.onIncomingMessage(logicalComponent);
        VisibilityStatusTracker.onIncomingMessage(logicalComponent);
        ComboTimerChatTracker.onIncomingMessage(logicalComponent);
        PlayerClanStorage.captureFromChat(logicalComponent);
        // ModUserStorage.captureFromChat runs in ChatMessageDecorator.decorate (before the Ⓕ beacon is
        // stripped for display) so detection still sees the raw marker.

        if (BossShareService.handleIncomingShare(logicalComponent)) {
            ci.cancel();
            return;
        }

        if (ModUserProtocol.handleIncoming(logicalComponent)) {
            ci.cancel(); // silent mesh sync PM — hide from chat
            return;
        }

        manager.captureIncoming(logicalComponent);

        if (!manager.shouldDisplayInActiveTab(logicalComponent)) {
            ci.cancel();
        }
    }

    @ModifyConstant(
            method = {
                    "addMessageToDisplayQueue(Lnet/minecraft/client/GuiMessage;)V",
                    "addMessageToQueue(Lnet/minecraft/client/GuiMessage;)V"
            },
            constant = @Constant(intValue = 100)
    )
    private int froghelper$extendChatHistory(int original) {
        return Math.max(original, original + Math.max(0, ru.wilyfox.client.hud.config.ConfigManager.get().render.extraChatHistoryLines));
    }
}
