package ru.wilyfox.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.chat.BossShareService;
import ru.wilyfox.client.chat.ChatMessageCopyExtractor;
import ru.wilyfox.client.chat.ChatTabOverlay;
import ru.wilyfox.client.hud.config.ConfigManager;
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

    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void froghelper$handleBossShareCommand(String input, boolean addToHistory, CallbackInfo ci) {
        if (BossShareService.handleOutgoingCommand(input, addToHistory)
                || TargetListCommand.handleOutgoingCommand(input, addToHistory)
                || ProtocolDebugCommand.handleOutgoingCommand(input, addToHistory)) {
            ci.cancel();
        }
    }
}
