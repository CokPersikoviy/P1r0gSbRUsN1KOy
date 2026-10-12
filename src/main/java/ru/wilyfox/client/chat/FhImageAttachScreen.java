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
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Explicit image submission: clipboard paste, file drops and F2 selection never send immediately. */
public final class FhImageAttachScreen extends Screen {
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
        closed = false;
        panelWidth = Math.min(360, width - 24); left = (width - panelWidth) / 2; top = Math.max(4, (height - 216) / 2);
        if (clipboardImage == null) {
            fileInput = addRenderableWidget(new EditBox(font, left, top + 54, panelWidth, 20, Component.translatable("froghelper.chat.image_path")));
            fileInput.setMaxLength(2048); fileInput.setValue(path); fileInput.setResponder(value -> path = value);
        } else {
            fileInput = null;
            if (preview == null) preview = new FhImageTexture(() -> CompletableFuture.completedFuture(clipboardImage), false);
        }
        captionInput = addRenderableWidget(new EditBox(font, left, top + 132, panelWidth, 20, Component.translatable("froghelper.chat.image_caption")));
        captionInput.setMaxLength(256); captionInput.setValue(caption); captionInput.setResponder(value -> caption = value);
        addRenderableWidget(Button.builder(Component.translatable("froghelper.chat.image_screenshot"), button -> latestScreenshot())
                .bounds(left, top + 160, panelWidth / 2 - 3, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("froghelper.chat.image_clipboard"), button -> paste())
                .bounds(left + panelWidth / 2 + 3, top + 160, panelWidth / 2 - 3, 20).build());
        send = addRenderableWidget(Button.builder(Component.translatable("froghelper.chat.image_send"), button -> {
            try {
                if (clipboardImage != null) BackendSocialClient.sendChatImage(clipboardImage, caption);
                else BackendSocialClient.sendChatImage(Path.of(path.strip().replaceAll("^\"|\"$", "")), caption);
                onClose();
            } catch (InvalidPathException invalid) { minecraft.gui.hud.setOverlayMessage(Component.translatable("froghelper.chat.image_send_failed"), false); }
        }).bounds(left, top + 186, panelWidth / 2 - 3, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(left + panelWidth / 2 + 3, top + 186, panelWidth / 2 - 3, 20).build());
        send.active = clipboardImage == null;
        setInitialFocus(fileInput == null ? captionInput : fileInput);
    }
    private void paste() {
        if (!FhClipboardImages.tryPaste(this, this::acceptClipboard))
            minecraft.gui.hud.setOverlayMessage(Component.translatable("froghelper.chat.clipboard_empty"), false);
    }
    private void acceptClipboard(byte[] bytes) {
        if (closed) return;
        if (preview != null) { preview.close(); preview = null; }
        clipboardImage = bytes; clearWidgets(); init();
    }
    private void selectFile(Path file) {
        if (closed) return;
        if (preview != null) { preview.close(); preview = null; }
        clipboardImage = null; path = file.toAbsolutePath().toString(); clearWidgets(); init();
    }
    private void latestScreenshot() {
        Path directory = minecraft.gameDirectory.toPath().resolve("screenshots");
        var client = minecraft;
        FhImages.supply(() -> {
            Path newest = null; long time = Long.MIN_VALUE;
            try (var files = Files.newDirectoryStream(directory, "*.png")) {
                for (var file : files) { long stamp = Files.getLastModifiedTime(file).toMillis();
                    if (stamp > time) { time = stamp; newest = file; } }
            } catch (java.io.IOException ignored) { }
            return newest;
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
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        graphics.fill(0, 0, width, height, 0xE8101010);
        graphics.centeredText(font, title, width / 2, top + 10, 0xFFFFFFFF);
        String limits = font.plainSubstrByWidth(Component.translatable("froghelper.chat.image_limits").getString(), panelWidth);
        graphics.centeredText(font, limits, width / 2, top + 26, 0xFFAAAAAA);
        if (clipboardImage == null) graphics.text(font, Component.translatable("froghelper.chat.image_path"), left, top + 40, 0xFFAAAAAA);
        else {
            graphics.fill(left, top + 40, left + panelWidth, top + 114, 0xFF181818);
            if (preview != null && preview.loaded()) {
                float scale = Math.min(panelWidth / (float) preview.width(), 70f / preview.height());
                int w = Math.max(1, Math.round(preview.width() * scale)), h = Math.max(1, Math.round(preview.height() * scale));
                graphics.blit(RenderPipelines.GUI_TEXTURED, preview.id(), left + (panelWidth - w) / 2, top + 42 + (70 - h) / 2,
                        0, 0, w, h, preview.width(), preview.height(), preview.width(), preview.height());
            } else graphics.centeredText(font, Component.translatable(preview != null && preview.failed()
                    ? "froghelper.chat.image_send_failed" : "froghelper.chat.image_loading"), width / 2, top + 72, 0xFFAAAAAA);
            send.active = preview != null && preview.loaded();
        }
        graphics.text(font, Component.translatable("froghelper.chat.image_caption"), left, top + 120, 0xFFAAAAAA);
        super.extractRenderState(graphics, x, y, delta);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() {
        minecraft.gui.setScreen(parent);
        if (parentInputKey != null && parent instanceof ru.wilyfox.bridge.ChatInputHost host) host.froghelper$focusInput(parentInputKey);
    }
    @Override public void removed() { closed = true; if (preview != null) { preview.close(); preview = null; } }
}
