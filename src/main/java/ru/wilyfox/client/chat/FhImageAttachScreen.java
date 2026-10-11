package ru.wilyfox.client.chat;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.moduser.BackendSocialClient;
import java.nio.file.*;
import java.util.List;

/** Explicit image submission; dropping a file or choosing the latest F2 screenshot never sends it immediately. */
public final class FhImageAttachScreen extends Screen {
    private final Screen parent;
    private String path = "", caption;
    private EditBox fileInput, captionInput;
    private boolean closed;
    public FhImageAttachScreen(Screen parent, String caption) {
        super(Component.translatable("froghelper.chat.image_attach")); this.parent = parent; this.caption = caption == null ? "" : caption;
    }
    @Override protected void init() {
        int left = Math.max(12, width / 2 - 160), w = Math.min(320, width - 24), top = Math.max(36, height / 2 - 60);
        fileInput = addRenderableWidget(new EditBox(font, left, top + 18, w, 20, Component.translatable("froghelper.chat.image_path")));
        fileInput.setMaxLength(2048); fileInput.setValue(path); fileInput.setResponder(value -> path = value);
        captionInput = addRenderableWidget(new EditBox(font, left, top + 60, w, 20, Component.translatable("froghelper.chat.image_caption")));
        captionInput.setMaxLength(256); captionInput.setValue(caption); captionInput.setResponder(value -> caption = value);
        addRenderableWidget(Button.builder(Component.translatable("froghelper.chat.image_screenshot"), button -> latestScreenshot())
                .bounds(left, top + 88, w, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("froghelper.chat.image_send"), button -> {
            try { BackendSocialClient.sendChatImage(Path.of(path.strip().replaceAll("^\"|\"$", "")), caption); onClose(); }
            catch (InvalidPathException invalid) { minecraft.gui.hud.setOverlayMessage(Component.translatable("froghelper.chat.image_send_failed"), false); }
        }).bounds(left, top + 114, w / 2 - 3, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(left + w / 2 + 3, top + 114, w / 2 - 3, 20).build());
        setInitialFocus(fileInput);
    }
    private void latestScreenshot() {
        Path directory = minecraft.gameDirectory.toPath().resolve("screenshots");
        FhImages.supply(() -> {
            Path newest = null; long time = Long.MIN_VALUE;
            try (var files = Files.newDirectoryStream(directory, "*.png")) {
                for (var file : files) { long stamp = Files.getLastModifiedTime(file).toMillis();
                    if (stamp > time) { time = stamp; newest = file; } }
            } catch (java.io.IOException ignored) { }
            return newest;
        }).thenAccept(file -> minecraft.execute(() -> {
            if (closed || minecraft.gui.screen() != this) return;
            if (file != null) fileInput.setValue(file.toAbsolutePath().toString());
            else minecraft.gui.hud.setOverlayMessage(Component.translatable("froghelper.chat.image_no_screenshot"), false);
        }));
    }
    @Override public void onFilesDrop(List<Path> files) { if (!files.isEmpty()) fileInput.setValue(files.getFirst().toAbsolutePath().toString()); }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        graphics.fill(0, 0, width, height, 0xE8101010);
        int left = Math.max(12, width / 2 - 160), top = Math.max(36, height / 2 - 60);
        graphics.centeredText(font, title, width / 2, top - 24, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("froghelper.chat.image_path"), left, top + 4, 0xFFAAAAAA);
        graphics.text(font, Component.translatable("froghelper.chat.image_caption"), left, top + 46, 0xFFAAAAAA);
        graphics.centeredText(font, Component.translatable("froghelper.chat.image_limits"), width / 2, Math.max(16, top - 10), 0xFFAAAAAA);
        super.extractRenderState(graphics, x, y, delta);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public void removed() { closed = true; }
}
