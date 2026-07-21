package ru.wilyfox.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.chat.BossShareService;
import ru.wilyfox.client.moduser.ModUserStorage;
import ru.wilyfox.client.chat.ChatMessageCopyExtractor;
import ru.wilyfox.client.chat.ChatTabOverlay;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.profiler.ProfilerDebugCommand;
import ru.wilyfox.client.protocol.ProtocolDebugCommand;
import ru.wilyfox.client.target.TargetListCommand;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void froghelper$renderTabs(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ChatTabOverlay.getInstance().render(graphics, this.width, this.height, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void froghelper$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button == 0 && ChatTabOverlay.getInstance().mouseClicked(mouseX, mouseY, this.height)) {
            cir.setReturnValue(true);
            return;
        }

        Client client = Client.getInstance();
        if (client != null && client.getHudRenderer().handleChatClick(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"), cancellable = true)
    private void froghelper$copyChatMessage(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 1 || cir.getReturnValue() || !ConfigManager.get().render.copyChatMessages || this.minecraft == null || this.minecraft.gui == null) {
            return;
        }

        if (ChatMessageCopyExtractor.copyHoveredMessage(this.minecraft.gui.getChat(), mouseX, mouseY)) {
            cir.setReturnValue(true);
        }
    }

    // Append the visible FrogHelper beacon (ModUserStorage.MARKER = "Ⓕ", U+24BB) to normal outgoing chat so
    // other mod users can detect us. Placed at the END, NOT the start: DiamondWorld selects the chat channel
    // by a leading prefix ('!' global, '@' clan, ...), so a prepended beacon would eat that prefix and break
    // those channels. Skip commands ('/'), our own protocol lines ('{fh'), and skip if it would exceed the
    // chat length limit (the receiver's 2-miss rule tolerates the rare trailing truncation). The beacon is
    // stripped from chat display for mod users.
    @ModifyVariable(method = "handleChatInput", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String froghelper$injectModMarker(String input) {
        if (input == null || input.isBlank() || input.startsWith("/")
                || input.contains(ModUserStorage.MARKER) || input.contains("{fh")) {
            return input;
        }
        if (input.length() + ModUserStorage.MARKER.length() > 256) {
            return input;
        }
        return input + ModUserStorage.MARKER;
    }

    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void froghelper$handleBossShareCommand(String input, boolean addToHistory, CallbackInfo ci) {
        if (BossShareService.handleOutgoingCommand(input, addToHistory)
                || TargetListCommand.handleOutgoingCommand(input, addToHistory)
                || ProtocolDebugCommand.handleOutgoingCommand(input, addToHistory)
                || ProfilerDebugCommand.handleOutgoingCommand(input, addToHistory)) {
            ci.cancel();
        }
    }
}
