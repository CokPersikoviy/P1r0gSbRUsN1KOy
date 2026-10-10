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
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;
import java.util.function.Predicate;

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
        IncomingChatHandler.handle(component);
    }

    // The 26.2 native filter normally drops hidden messages from the archive too.
    // Retain them in the native queue; only their display lines are filtered.
    @WrapOperation(method = "addMessage", at = @At(value = "INVOKE", target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z"))
    private boolean froghelper$retainHiddenMessages(Predicate<GuiMessage> predicate, Object message, Operation<Boolean> original) {
        return predicate == ChatTabManager.getInstance().visibleFilter() || original.call(predicate, message);
    }

    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true)
    private void froghelper$filterDisplay(GuiMessage message, CallbackInfo ci) {
        if (!ChatTabManager.getInstance().isVisible(message)) ci.cancel();
    }

    @Inject(method = "addMessageToQueue", at = @At("TAIL"))
    private void froghelper$captureNative(GuiMessage message, CallbackInfo ci) {
        var chat = (ChatComponent) (Object) this;
        ChatTabManager.getInstance().captureIncoming(message, chat.isChatFocused());
    }

    @WrapOperation(method = "addMessageToDisplayQueue", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/GuiMessage;splitLines(Lnet/minecraft/client/gui/Font;I)Ljava/util/List;"))
    private List<FormattedCharSequence> froghelper$cacheWrappedLines(GuiMessage message, Font font, int width, Operation<List<FormattedCharSequence>> original) {
        return ChatTabManager.getInstance().wrappedLines(message, font, width);
    }

    @Inject(method = "refreshTrimmedMessages", at = @At("HEAD"))
    private void froghelper$beginRefresh(CallbackInfo ci) {
        ChatTabManager.getInstance().beginNativeRefresh((ChatComponent) (Object) this);
    }

    @Inject(method = "refreshTrimmedMessages", at = @At("RETURN"))
    private void froghelper$endRefresh(CallbackInfo ci) {
        ChatTabManager.getInstance().endNativeRefresh((ChatComponent) (Object) this);
    }

    @Inject(method = "clearMessages", at = @At("RETURN"))
    private void froghelper$clearArchive(boolean history, CallbackInfo ci) { ChatTabManager.getInstance().clearAll(); }

    @Inject(method = "rescaleChat", at = @At("HEAD"))
    private void froghelper$invalidateLines(CallbackInfo ci) { ChatTabManager.getInstance().clearLineCache(); }

    @ModifyConstant(
            method = {
                    "addMessageToDisplayQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V",
                    "addMessageToQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V"
            },
            constant = @Constant(intValue = 100)
    )
    private int froghelper$extendChatHistory(int original) {
        if (ChatTabManager.getInstance().isRebuilding() && ChatTabManager.getInstance().getActiveTab() == ru.wilyfox.client.chat.ChatTab.FH) return 5000;
        return Math.max(original, original + Math.max(0, ru.wilyfox.client.hud.config.ConfigManager.get().render.extraChatHistoryLines));
    }
    @Inject(method = "scrollChat", at = @At("RETURN"))
    private void froghelper$olderBackendChat(int amount, CallbackInfo ci) {
        if (amount <= 0 || ChatTabManager.getInstance().isRebuilding() || ChatTabManager.getInstance().getActiveTab() != ru.wilyfox.client.chat.ChatTab.FH) return;
        var chat = (ChatComponent) (Object) this;
        var accessor = (ru.wilyfox.bridge.ChatComponentAccessor) chat;
        if (accessor.froghelper$getScroll() >= accessor.froghelper$getTrimmedMessages().size() - chat.getLinesPerPage() - 10)
            ru.wilyfox.client.moduser.BackendSocialClient.requestOlderChat();
    }
}
