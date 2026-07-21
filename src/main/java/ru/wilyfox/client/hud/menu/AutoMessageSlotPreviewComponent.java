package ru.wilyfox.client.hud.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class AutoMessageSlotPreviewComponent extends SettingsComponent {
    private final Supplier<String> messageGetter;
    private final Supplier<Boolean> activeGetter;
    private final IntSupplier delayGetter;

    public AutoMessageSlotPreviewComponent(Supplier<String> messageGetter,
                                           Supplier<Boolean> activeGetter,
                                           IntSupplier delayGetter) {
        super(0, 0, 0, 0, "");
        this.messageGetter = messageGetter;
        this.activeGetter = activeGetter;
        this.delayGetter = delayGetter;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        boolean hovered = isHovered(mouseX, mouseY);
        HudSurface.fillRounded(context, x, y, width, height, 4, hovered ? WidgetTheme.PANEL_BG : WidgetTheme.PANEL_BG_SOFT);

        String message = messageGetter.get();
        if (message == null || message.isBlank()) {
            message = "Empty message";
        }

        String meta = (activeGetter.get() ? "ON" : "OFF") + " | " + delayGetter.getAsInt() + "s";
        int metaWidth = mc.font.width(meta);
        int availableWidth = Math.max(16, width - 20 - metaWidth - 12);
        String preview = trimToWidth(mc, message, availableWidth);
        int textY = y + (height - mc.font.lineHeight) / 2;

        context.drawString(mc.font, preview, x + 8, textY, messageGetter.get() == null || messageGetter.get().isBlank() ? WidgetTheme.TEXT_MUTED : WidgetTheme.TEXT_SOFT);
        context.drawString(mc.font, meta, x + width - 8 - metaWidth, textY, WidgetTheme.TEXT_SECONDARY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    private String trimToWidth(Minecraft mc, String text, int maxWidth) {
        if (mc.font.width(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        for (int end = text.length(); end >= 0; end--) {
            String candidate = text.substring(0, end) + ellipsis;
            if (mc.font.width(candidate) <= maxWidth) {
                return candidate;
            }
        }

        return ellipsis;
    }
}
