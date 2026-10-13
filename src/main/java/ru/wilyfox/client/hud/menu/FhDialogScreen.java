package ru.wilyfox.client.hud.menu;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.audio.UiSoundScreen;
import ru.wilyfox.client.audio.UiSounds;
import ru.wilyfox.client.hud.config.WidgetChrome;
import ru.wilyfox.client.hud.widget.*;
import ru.wilyfox.client.profiler.ModProfiler;

/** Shared FH dialog chrome; keeps native input editing and accessibility. */
public abstract class FhDialogScreen extends UiSoundScreen {
    private final String renderSection;
    protected FhDialogScreen(Component title) {
        super(title);
        renderSection = "ui/" + getClass().getSimpleName() + "/render";
    }

    @Override public final void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        try (var ignored = ModProfiler.getInstance().scope(renderSection)) {
            extractDialogContents(g, mx, my, delta);
            try (var widgets = ModProfiler.getInstance().scope("ui/dialog/widgets")) {
                super.extractRenderState(g, mx, my, delta);
            }
        }
    }

    protected abstract void extractDialogContents(GuiGraphicsExtractor g, int mx, int my, float delta);

    protected void dialog(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        try (var profile = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("ui/dialog/surface")) {
            g.fill(0, 0, width, height, WidgetTheme.withAlpha(WidgetTheme.PANEL_BG, 0x80));
            HudBlur.beginFrame(g);
            HudSurface.drawPanel(g, x, y, w, h, WidgetChrome.FROST, HudSurface.nativeRenderer());
            g.text(font, font.plainSubstrByWidth(title.getString(), w - 24), x + 12, y + 12, WidgetTheme.TITLE);
        }
    }

    protected Button button(int x, int y, int w, Component label, boolean primary, Runnable action) {
        return addRenderableWidget(new Button(x, y, w, 20, label, ignored -> action.run(), java.util.function.Supplier::get) {
            @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                boolean highlighted = active && (primary || isMouseOver(mx, my) || isFocused());
                HudSurface.fillRounded(g, getX(), getY(), getWidth(), getHeight(), 4,
                        highlighted ? WidgetTheme.PANEL_BG : WidgetTheme.PANEL_BG_SOFT);
                if (highlighted) g.fill(getX() + 4, getY(), getRight() - 4, getY() + 1, WidgetTheme.ACCENT_LINE);
                String full = getMessage().getString(), shown = font.plainSubstrByWidth(full, Math.max(1, getWidth() - 12));
                if (!shown.equals(full)) shown = font.plainSubstrByWidth(full, Math.max(1, getWidth() - 12 - font.width("…"))) + "…";
                g.centeredText(font, shown, getX() + getWidth() / 2, getY() + (getHeight() - font.lineHeight) / 2,
                        !active ? WidgetTheme.TEXT_SECONDARY : highlighted ? WidgetTheme.TITLE : WidgetTheme.TEXT_PRIMARY);
                if (!shown.equals(full) && isMouseOver(mx, my)) g.setTooltipForNextFrame(font, getMessage(), mx, my);
            }
            @Override public void playDownSound(net.minecraft.client.sounds.SoundManager manager) { UiSounds.click(); }
        });
    }

    protected EditBox input(int x, int y, int w, Component label) {
        return addRenderableWidget(new DialogInput(font, x, y, w, label));
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int x, int y, float delta) { }
    @Override protected void extractBlurredBackground(GuiGraphicsExtractor g) { }

    private static final class DialogInput extends EditBox {
        private final Font font;
        DialogInput(Font font, int x, int y, int w, Component label) {
            super(font, x, y, w, 20, label); this.font = font;
            setBordered(false); setTextShadow(false);
        }
        @Override public int getInnerWidth() { return Math.max(1, getWidth() - 12); }
        @Override public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            if (!isVisible()) return;
            HudSurface.fillRounded(g, getX(), getY(), getWidth(), getHeight(), 4, WidgetTheme.PANEL_BG_SOFT);
            if (isFocused()) g.fill(getX() + 4, getBottom() - 1, getRight() - 4, getBottom(), WidgetTheme.ACCENT_LINE);
            setTextColor(WidgetTheme.TEXT_PRIMARY); setTextColorUneditable(WidgetTheme.TEXT_SECONDARY);
            g.pose().pushMatrix();
            g.pose().translate(6, (getHeight() - font.lineHeight) / 2f);
            super.extractWidgetRenderState(g, mx - 6, my, delta);
            g.pose().popMatrix();
        }
        private static MouseButtonEvent inset(MouseButtonEvent event) {
            return new MouseButtonEvent(event.x() - 6, event.y(), new MouseButtonInfo(event.button(), event.modifiers()));
        }
        @Override public void onClick(MouseButtonEvent event, boolean doubleClick) { super.onClick(inset(event), doubleClick); }
        @Override protected void onDrag(MouseButtonEvent event, double dx, double dy) { super.onDrag(inset(event), dx, dy); }
    }
}
