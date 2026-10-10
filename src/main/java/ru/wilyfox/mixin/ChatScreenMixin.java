package ru.wilyfox.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.chat.ChatMessageCopyExtractor;
import ru.wilyfox.client.chat.ChatTabOverlay;
import ru.wilyfox.client.chat.ChatTabManager;
import ru.wilyfox.client.chat.ChatOutgoingRouter;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.profiler.ProfilerDebugCommand;
import ru.wilyfox.client.protocol.ProtocolDebugCommand;
import ru.wilyfox.client.hud.widget.ChatWidget;
import net.minecraft.network.chat.Style;
import ru.wilyfox.client.chat.ChatDock;
import com.llamalad7.mixinextras.injector.WrapWithCondition;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    @Shadow protected EditBox input;
    @Shadow private CommandSuggestions commandSuggestions;
    @Unique private EditBox froghelper$searchInput;
    @Unique private boolean froghelper$searchActive;
    @Unique private ChatWidget froghelper$focusedWidget;
    @Unique private ChatWidget froghelper$searchWidget;
    @Shadow private boolean handleComponentClicked(Style style, boolean allowInsertions) { throw new AssertionError(); }

    @Inject(method = "init", at = @At("TAIL"))
    private void froghelper$initSearch(CallbackInfo ci) {
        froghelper$searchInput = new EditBox(this.minecraft.fontFilterFishy, 4, this.height - 12,
                Math.max(40, this.width - 100), 12, Component.translatable("froghelper.chat.search"));
        froghelper$searchInput.setMaxLength(128);
        froghelper$searchInput.setBordered(false);
        froghelper$searchInput.setHint(Component.translatable("froghelper.chat.search_hint"));
        froghelper$searchInput.setValue(ChatTabManager.getInstance().search());
        froghelper$searchInput.setResponder(value -> {
            if (froghelper$searchWidget != null) froghelper$searchWidget.view().search(value);
            else ChatTabManager.getInstance().setSearch(value);
        });
        froghelper$searchInput.setVisible(froghelper$searchActive);
        this.addRenderableWidget(froghelper$searchInput);
        if (froghelper$searchActive) froghelper$applySearchFocus();
        ChatTabManager.getInstance().markActiveRead();
    }

    @Unique
    private void froghelper$applySearchFocus() {
        input.setVisible(!froghelper$searchActive);
        input.setCanLoseFocus(froghelper$searchActive);
        froghelper$searchInput.setVisible(froghelper$searchActive);
        if (froghelper$searchActive) {
            commandSuggestions.setAllowSuggestions(false);
            this.setFocused(froghelper$searchInput);
        } else {
            froghelper$searchInput.setValue("");
            froghelper$searchWidget = null;
            this.setFocused(input);
            commandSuggestions.updateCommandInfo();
        }
    }

    @Unique
    private void froghelper$toggleSearch() {
        froghelper$searchActive = !froghelper$searchActive;
        if (froghelper$searchActive) {
            froghelper$searchWidget = froghelper$focusedWidget;
            froghelper$searchInput.setValue(froghelper$searchWidget == null ? ChatTabManager.getInstance().search()
                    : froghelper$searchWidget.view().search());
        }
        froghelper$applySearchFocus();
        ru.wilyfox.client.audio.UiSounds.openClose();
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void froghelper$chatShortcuts(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        froghelper$validateWidgetFocus();
        boolean control = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        if (control && event.key() == GLFW.GLFW_KEY_F) {
            froghelper$toggleSearch();
            cir.setReturnValue(true);
        } else if (control && event.key() == GLFW.GLFW_KEY_TAB) {
            froghelper$focusWidget(null);
            ChatDock.cycle((event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0);
            ru.wilyfox.client.audio.UiSounds.click();
            cir.setReturnValue(true);
        } else if (froghelper$searchActive && (event.key() == GLFW.GLFW_KEY_ESCAPE || event.isConfirmation())) {
            froghelper$toggleSearch();
            cir.setReturnValue(true);
        } else if (froghelper$searchActive) {
            froghelper$searchInput.keyPressed(event);
            cir.setReturnValue(true);
        } else if (!froghelper$searchActive && event.isConfirmation()
                && ChatDock.outgoingChannel() != ru.wilyfox.client.chat.ChatTab.FH
                && ChatOutgoingRouter.format(input.getValue()) == null) {
            var error = Component.translatable(ChatDock.outgoingChannel() == ru.wilyfox.client.chat.ChatTab.PRIVATE
                    && ChatOutgoingRouter.replyCommand().isEmpty() ? "froghelper.chat.pm_hint" : "froghelper.chat.too_long");
            ru.wilyfox.client.popup.PopUpManager.getInstance().publish(ru.wilyfox.client.popup.PopUpRequest.of(
                    "chat.send", Component.translatable("froghelper.chat.send_error").getString(), error.getString(),
                    ru.wilyfox.client.popup.PopUpSeverity.WARNING));
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void froghelper$closeSearch(CallbackInfo ci) {
        ChatDock.cancelDrag();
        ChatDock.focusWindow(null);
        froghelper$searchActive = false;
        if (froghelper$searchWidget != null) froghelper$searchWidget.view().search("");
        froghelper$searchWidget = froghelper$focusedWidget = null;
        ChatTabManager.getInstance().setSearch("");
    }

    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void froghelper$renderTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        froghelper$validateWidgetFocus();
        ChatTabOverlay.getInstance().render(graphics, this.width, this.height, mouseX, mouseY);
        if (froghelper$searchActive) {
            graphics.text(this.font, Component.translatable("froghelper.chat.matches", froghelper$searchWidget == null
                            ? ChatTabManager.getInstance().matchingMessages() : froghelper$searchWidget.view().matchingMessages()),
                    this.width - 92, this.height - 12, ru.wilyfox.client.hud.widget.WidgetTheme.TEXT_MUTED);
        }
    }

    @Inject(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void froghelper$renderChatWindows(GuiGraphicsExtractor graphics, int x, int y, float delta, CallbackInfo ci) {
        if (Client.getInstance() != null) Client.getInstance().getHudRenderer().renderChatWindows(graphics);
    }

    @WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/CommandSuggestions;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V"))
    private boolean froghelper$hideCommandsDuringSearch(CommandSuggestions suggestions, GuiGraphicsExtractor graphics, int x, int y) {
        return !froghelper$searchActive && ChatDock.outgoingChannel() != ru.wilyfox.client.chat.ChatTab.FH;
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void froghelper$mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
        String tab = ChatTabOverlay.getInstance().tabAt(mouseX, mouseY, this.height);
        if (tab != null && (button == 0 || button == 1)) {
            froghelper$focusWidget(null);
            if (button == 0) ChatDock.pressTab(tab, mouseX, mouseY);
            else ChatDock.edit(tab);
            cir.setReturnValue(true);
            return;
        }
        if (button == 0 && ChatTabOverlay.getInstance().isSearchButton(mouseX, mouseY, this.height)) {
            froghelper$toggleSearch();
            cir.setReturnValue(true);
            return;
        }
        if (button == 0 && ChatTabOverlay.getInstance().mouseClicked(mouseX, mouseY, this.height)) {
            froghelper$focusWidget(null);
            cir.setReturnValue(true);
            return;
        }

        Client client = Client.getInstance();
        var widget = client == null || mouseY >= height - 16 ? null : client.getHudRenderer().chatWidgetAt(mouseX, mouseY);
        if (widget != null && (button == 0 || button == 1)) {
            if (button == 0) {
                froghelper$focusWidget(widget);
                if (widget.isHeader(mouseX, mouseY)) ChatDock.pressWindow(widget, mouseX, mouseY);
                Style style = widget.styleAt(mouseX, mouseY);
                if (style != null) handleComponentClicked(style, minecraft.hasShiftDown());
            } else if (widget.isHeader(mouseX, mouseY)) ChatDock.edit(widget.getConfigKey());
            else if (ConfigManager.get().render.copyChatMessages) widget.copyAt(mouseX, mouseY);
            cir.setReturnValue(true);
            return;
        }
        if (button == 0 && mouseY < height - 16) froghelper$focusWidget(null);
        if (client != null && client.getHudRenderer().handleChatClick(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Unique private void froghelper$focusWidget(ChatWidget widget) {
        if (widget == froghelper$focusedWidget) return;
        if (froghelper$searchActive) froghelper$toggleSearch();
        froghelper$focusedWidget = widget;
        ChatDock.focusWindow(widget == null ? null : widget.getConfigKey());
    }

    @Unique private void froghelper$validateWidgetFocus() {
        if (froghelper$focusedWidget != null && (!froghelper$focusedWidget.isVisible()
                || Client.getInstance().getHudRenderer().isSettingsOpen() || Client.getInstance().getHudRenderer().isEditing())) {
            froghelper$focusWidget(null);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void froghelper$scrollWidget(double x, double y, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
        if (ChatTabOverlay.getInstance().scrollTabs(x, y, scrollY)) { cir.setReturnValue(true); return; }
        var client = Client.getInstance();
        var widget = client == null ? null : client.getHudRenderer().chatWidgetAt(x, y);
        if (widget == null) return;
        widget.view().lines(minecraft.font, widget.settings().width - 14, widget.settings().rows);
        widget.view().scroll((int) Math.signum(scrollY) * (minecraft.hasShiftDown() ? 1 : 7));
        if (widget.settings().channel == ru.wilyfox.client.chat.ChatTab.FH && scrollY > 0 && !widget.view().hasOlder())
            ru.wilyfox.client.moduser.BackendSocialClient.requestOlderChat();
        cir.setReturnValue(true);
    }

    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (event.button() == 0 && ChatDock.drag(event.x(), event.y())) return true;
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (event.button() == 0 && ChatDock.release(event.x(), event.y())) return true;
        return super.mouseReleased(event);
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"), cancellable = true)
    private void froghelper$copyChatMessage(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
        if (button != 1 || cir.getReturnValue() || !ConfigManager.get().render.copyChatMessages || this.minecraft == null || this.minecraft.gui == null) {
            return;
        }

        if (ChatMessageCopyExtractor.copyHoveredMessage(
                this.minecraft.gui.hud.getChat(),
                mouseX,
                mouseY,
                ConfigManager.get().render.fullMessageCopy
        )) {
            cir.setReturnValue(true);
        }
    }

    @ModifyVariable(method = "handleChatInput", at = @At("HEAD"), argsOnly = true)
    private String froghelper$routeManualChat(String value) {
        if (!froghelper$searchActive && ChatDock.outgoingChannel() == ru.wilyfox.client.chat.ChatTab.FH) return value;
        String routed = ChatOutgoingRouter.format(value);
        return froghelper$searchActive || routed == null ? "" : routed;
    }

    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void froghelper$handleDebugCommand(String input, boolean addToHistory, CallbackInfo ci) {
        if (ChatDock.outgoingChannel() == ru.wilyfox.client.chat.ChatTab.FH) {
            if (!froghelper$searchActive) {
                if (addToHistory && !input.isBlank()) minecraft.gui.hud.getChat().addRecentChat(input);
                ru.wilyfox.client.moduser.BackendSocialClient.sendChat(input);
            }
            ci.cancel(); return;
        }
        if (ProtocolDebugCommand.handleOutgoingCommand(input, addToHistory)
                || ProfilerDebugCommand.handleOutgoingCommand(input, addToHistory)) {
            ci.cancel();
        }
    }
}
