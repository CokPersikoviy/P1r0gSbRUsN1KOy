package ru.wilyfox.client.hud.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import ru.wilyfox.client.hud.config.WidgetChrome;

import java.util.List;
import java.util.function.IntUnaryOperator;

/** Inventory plates share their sizing and scale the text, surface and blur together. */
public final class InventoryOverlayPanel {
    private InventoryOverlayPanel() {}

    public static void render(GuiGraphicsExtractor context, int anchorX, int y, String title,
                              List<String> lines, int scalePercent, boolean alignRight,
                              IntUnaryOperator lineColor) {
        var minecraft = Minecraft.getInstance();
        var font = minecraft.font;
        int lineHeight = font.lineHeight + 1;
        int width = font.width(title);
        for (String line : lines) width = Math.max(width, font.width(line));
        width += 12;
        int height = 12 + lineHeight * (lines.size() + 1);
        var placement = place(anchorX, y, width, height, scalePercent, alignRight,
                minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());

        var pose = context.pose();
        pose.pushMatrix();
        try {
            pose.translate(placement.x(), placement.y());
            pose.scale(placement.scale(), placement.scale());
            HudSurface.drawPanel(context, 0, 0, width, height, WidgetChrome.FROST, HudSurface.nativeRenderer(), true);
            context.text(font, title, 6, 5, WidgetTheme.TITLE);
            int textY = 7 + lineHeight;
            for (int i = 0; i < lines.size(); i++) {
                context.text(font, lines.get(i), 6, textY, lineColor.applyAsInt(i));
                textY += lineHeight;
            }
        } finally {
            pose.popMatrix();
        }
    }

    static Placement place(int anchorX, int y, int width, int height, int scalePercent,
                           boolean alignRight, int screenWidth, int screenHeight) {
        float scale = Math.clamp(scalePercent, 50, 300) / 100f;
        // Very small GUI viewports must still contain the entire plate.
        scale = Math.min(scale, Math.min(Math.max(1, screenWidth - 4) / (float) width,
                Math.max(1, screenHeight - 4) / (float) height));
        float x = alignRight ? anchorX - width * scale : anchorX;
        x = Math.clamp(x, 2f, Math.max(2f, screenWidth - width * scale - 2f));
        float top = Math.clamp((float) y, 2f, Math.max(2f, screenHeight - height * scale - 2f));
        return new Placement(x, top, scale);
    }

    record Placement(float x, float y, float scale) {}
}
