package ru.wilyfox.client.chat;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.moduser.BackendSocialClient;
import ru.wilyfox.client.hud.menu.FhDialogScreen;
import ru.wilyfox.client.hud.widget.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Explicit image submission: clipboard paste, file drops and F2 selection never send immediately. */
public final class FhImageAttachScreen extends FhDialogScreen {
    private final Screen parent;
    private final String parentInputKey;
    private String path = "", caption;
    private EditBox fileInput, captionInput;
    private Button send;
    private byte[] clipboardImage;
    private FhImageTexture preview;
    private boolean closed;
    private int left, top, panelWidth;
    public FhImageAttachScreen(Screen parent, String caption) { this(parent, caption, null); }
    public FhImageAttachScreen(Screen parent, String caption, byte[] image) {
        super(Component.translatable("froghelper.chat.image_attach"));
        this.parent = parent; this.caption = caption == null ? "" : caption;
        parentInputKey = parent instanceof ru.wilyfox.bridge.ChatInputHost host ? host.froghelper$inlineInputKey() : null;
        if (image != null) {
            if (image.length == 0 || image.length > FhImages.MAX_BYTES) throw new IllegalArgumentException("Invalid clipboard image size");
            clipboardImage = image.clone();
        }
    }
    @Override protected void init() {
        super.init(); closed = false;
        panelWidth = Math.min(384, width - 16); left = (width - panelWidth) / 2; top = Math.max(4, (height - 224) / 2);
        int x = left + 12, w = panelWidth - 24, half = (w - 8) / 2;
        if (clipboardImage == null) {
            fileInput = input(x, top + 64, w, Component.translatable("froghelper.chat.image_path"));
            fileInput.setMaxLength(2048); fileInput.setValue(path); fileInput.setResponder(value -> path = value);
        } else {
            fileInput = null;
            if (preview == null) preview = new FhImageTexture(() -> CompletableFuture.completedFuture(clipboardImage), false, "chat/imageAttach/preview");
        }
        captionInput = input(x, top + 140, w, Component.translatable("froghelper.chat.image_caption"));
        captionInput.setMaxLength(256); captionInput.setValue(caption); captionInput.setResponder(value -> caption = value);
        button(x, top + 168, half, Component.translatable("froghelper.chat.image_screenshot"), false, this::latestScreenshot);
        button(x + half + 8, top + 168, w - half - 8, Component.translatable("froghelper.chat.image_clipboard"), false, this::paste);
        send = button(x, top + 194, half, Component.translatable("froghelper.chat.image_send"), true, () -> {
            try {
                if (clipboardImage != null) BackendSocialClient.sendChatImage(clipboardImage, caption);
                else BackendSocialClient.sendChatImage(Path.of(path.strip().replaceAll("^\"|\"$", "")), caption);
                onClose();
            } catch (InvalidPathException invalid) { minecraft.gui.hud.setOverlayMessage(Component.translatable("froghelper.chat.image_send_failed"), false); }
        });
        button(x + half + 8, top + 194, w - half - 8, Component.translatable("gui.cancel"), false, this::onClose);
        send.active = clipboardImage == null;
        setInitialFocus(fileInput == null ? captionInput : fileInput);
    }
    private void paste() {
        if (!FhClipboardImages.tryPaste(this, this::acceptClipboard))
            minecraft.gui.hud.setOverlayMessage(Component.translatable("froghelper.chat.clipboard_empty"), false);
    }
    private void acceptClipboard(byte[] bytes) {
        try (var profile = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("chat/imageAttach/acceptClipboard")) {
            if (closed) return;
            if (preview != null) { preview.close(); preview = null; }
            clipboardImage = bytes; clearWidgets(); init();
        }
    }
    private void selectFile(Path file) {
        try (var profile = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("chat/imageAttach/selectFile")) {
            if (closed) return;
            if (preview != null) { preview.close(); preview = null; }
            clipboardImage = null; path = file.toAbsolutePath().toString(); clearWidgets(); init();
        }
    }
    private void latestScreenshot() {
        Path directory = minecraft.gameDirectory.toPath().resolve("screenshots");
        var client = minecraft;
        FhImages.supply(() -> {
            try (var profile = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("chat/imageAttach/findScreenshot")) {
                Path newest = null; long time = Long.MIN_VALUE;
                try (var files = Files.newDirectoryStream(directory, "*.png")) {
                    for (var file : files) { long stamp = Files.getLastModifiedTime(file).toMillis();
                        if (stamp > time) { time = stamp; newest = file; } }
                } catch (java.io.IOException ignored) { }
                return newest;
            }
        }).thenAccept(file -> client.execute(() -> {
            if (closed || client.gui.screen() != this) return;
            if (file != null) selectFile(file);
            else client.gui.hud.setOverlayMessage(Component.translatable("froghelper.chat.image_no_screenshot"), false);
        }));
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_V && (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0
                && FhClipboardImages.tryPaste(this, this::acceptClipboard)) return true;
        return super.keyPressed(event);
    }
    @Override public void onFilesDrop(List<Path> files) { if (!files.isEmpty()) selectFile(files.getFirst()); }
    @Override protected void extractDialogContents(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        dialog(graphics, left, top, panelWidth, 224);
        int contentX = left + 12, contentWidth = panelWidth - 24;
        String limits = font.plainSubstrByWidth(Component.translatable("froghelper.chat.image_limits").getString(), contentWidth);
        graphics.text(font, limits, contentX, top + 28, WidgetTheme.TEXT_SECONDARY);
        if (clipboardImage == null) {
            graphics.text(font, font.plainSubstrByWidth(Component.translatable("froghelper.chat.image_path").getString(), contentWidth),
                    contentX, top + 48, WidgetTheme.TEXT_SECONDARY);
        } else {
            HudSurface.fillRounded(graphics, contentX, top + 46, contentWidth, 74, 4, WidgetTheme.PANEL_BG_SOFT);
            if (preview != null && preview.loaded()) {
                float scale = Math.min((contentWidth - 8) / (float) preview.width(), 66f / preview.height());
                int w = Math.max(1, Math.round(preview.width() * scale)), h = Math.max(1, Math.round(preview.height() * scale));
                graphics.blit(RenderPipelines.GUI_TEXTURED, preview.id(), contentX + (contentWidth - w) / 2, top + 50 + (66 - h) / 2,
                        0, 0, w, h, preview.width(), preview.height(), preview.width(), preview.height());
            } else {
                var lines = font.split(Component.translatable(preview != null && preview.failed()
                        ? "froghelper.chat.image_send_failed" : "froghelper.chat.image_loading"), contentWidth - 16);
                for (int i = 0; i < lines.size(); i++) graphics.text(font, lines.get(i), contentX + 8,
                        top + 83 - lines.size() * font.lineHeight / 2 + i * font.lineHeight, WidgetTheme.TEXT_SECONDARY);
            }
            send.active = preview != null && preview.loaded();
        }
        graphics.text(font, Component.translatable("froghelper.chat.image_caption"), contentX, top + 128, WidgetTheme.TEXT_SECONDARY);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() {
        minecraft.gui.setScreen(parent);
        if (parentInputKey != null && parent instanceof ru.wilyfox.bridge.ChatInputHost host) host.froghelper$focusInput(parentInputKey);
    }
    @Override public void removed() { closed = true; if (preview != null) { preview.close(); preview = null; } super.removed(); }
}
