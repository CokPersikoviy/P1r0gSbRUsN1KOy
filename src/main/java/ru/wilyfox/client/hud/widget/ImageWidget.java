package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import ru.wilyfox.client.chat.*;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;

public final class ImageWidget extends AbstractWidget {
    private String asset;
    private boolean animated;
    private FhImageTexture image;
    private long hiddenSince;
    public ImageWidget(int x, int y, HudLayer layer) { super(x, y, layer); }
    @Override public int getWidth() { return Math.round(ConfigManager.get().imageWidget.width * scale); }
    @Override public int getHeight() { return Math.round(ConfigManager.get().imageWidget.height * scale); }
    @Override public String getDisplayName() { return "Image"; }
    @Override public boolean isVisible() { return isInLayout(); }
    public void tick(Minecraft client) {
        var config = ConfigManager.get().imageWidget;
        boolean animate = config.animateGif && !ConfigManager.get().render.lightweightHud;
        if (client.level == null || config.asset.isEmpty()) { release(); return; }
        if (!isVisible()) {
            long now = System.nanoTime();
            if (hiddenSince == 0) hiddenSince = now;
            // The layout's last-frame snapshot needs its texture through the 500 ms departure fade.
            if (now - hiddenSince >= 550_000_000L) release();
            return;
        }
        hiddenSince = 0;
        if (!config.asset.equals(asset) || animated != animate || image == null) {
            release(); asset = config.asset; animated = animate; String selected = asset;
            image = new FhImageTexture(() -> FhImages.supply(() -> {
                try { return ImageWidgetStore.read(selected); }
                catch (java.io.IOException error) { throw new java.util.concurrent.CompletionException(error); }
            }), animate);
        }
    }
    public void release() { if (image != null) image.close(); image = null; asset = null; }
    @Override public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (!isVisible()) return;
        var config = ConfigManager.get().imageWidget;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(startX, startY).scale(scale, scale);
            HudSurface.drawPanel(graphics, config.width, config.height);
            if (image != null && image.loaded()) {
                image.advance();
                int w = config.width - 8, h = config.height - 8;
                if (config.keepAspectRatio) {
                    double ratio = Math.min(w / (double) image.width(), h / (double) image.height());
                    w = Math.max(1, (int) (image.width() * ratio)); h = Math.max(1, (int) (image.height() * ratio));
                }
                graphics.blit(RenderPipelines.GUI_TEXTURED, image.id(), (config.width - w) / 2, (config.height - h) / 2,
                        0, 0, w, h, image.width(), image.height(), image.width(), image.height());
            } else {
                String key = config.asset.isEmpty() ? "froghelper.image_widget.empty"
                        : image != null && image.failed() ? "froghelper.chat.image_failed" : "froghelper.chat.image_loading";
                var font = Minecraft.getInstance().font;
                graphics.text(font, font.plainSubstrByWidth(net.minecraft.network.chat.Component.translatable(key).getString(), config.width - 12),
                        6, config.height / 2 - font.lineHeight / 2, WidgetTheme.TEXT_SOFT);
            }
        } finally { graphics.pose().popMatrix(); }
    }
}
