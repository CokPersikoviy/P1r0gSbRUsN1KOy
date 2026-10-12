package ru.wilyfox.client.calculator;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.audio.UiSoundScreen;
import ru.wilyfox.client.audio.UiSounds;
import ru.wilyfox.client.hud.config.WidgetChrome;
import ru.wilyfox.client.hud.widget.HudBlur;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;

import java.text.NumberFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class LevelCalculatorScreen extends UiSoundScreen {
    private final Screen parent;
    private final NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.US);
    private final DecimalFormat compactNumbers = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private String current, target;
    private LevelCalculator.Server server;
    private LevelCalculator.Discount discount = LevelCalculator.Discount.NONE;
    private EditBox currentInput, targetInput;
    private Button evo, fast, discountButton;
    private int left, top, panelWidth, panelHeight;

    public LevelCalculatorScreen(Screen parent) {
        super(text("title"));
        this.parent = parent;
        int level = DiamondWorldProtocolClient.getCurrentLevel();
        current = Integer.toString(Math.clamp(level, 1, LevelCalculator.MAX_LEVEL));
        target = Integer.toString(Math.min(LevelCalculator.MAX_LEVEL, Math.max(2, level + 1)));
        server = DiamondWorldProtocolClient.getCurrentServerInfo().rawName().toUpperCase(Locale.ROOT).contains("FAST")
                ? LevelCalculator.Server.EVO_FAST : LevelCalculator.Server.EVO;
    }

    @Override protected void init() {
        super.init();
        panelWidth = Math.min(440, width - 16);
        panelHeight = 202;
        left = (width - panelWidth) / 2;
        top = Math.max(4, (height - panelHeight) / 2);
        int x = left + 14, w = panelWidth - 28, half = (w - 10) / 2;
        evo = button(x, top + 29, half, 20, Component.literal("Evo"), () -> selectServer(LevelCalculator.Server.EVO));
        fast = button(x + half + 10, top + 29, half, 20, Component.literal("EvoFast"), () -> selectServer(LevelCalculator.Server.EVO_FAST));
        currentInput = input(x, top + 66, half, "current", current);
        targetInput = input(x + half + 10, top + 66, half, "target", target);
        currentInput.setResponder(value -> {
            current = bounded(value);
            if (!current.equals(value)) currentInput.setValue(current);
            refresh();
        });
        targetInput.setResponder(value -> {
            target = bounded(value);
            if (!target.equals(value)) targetInput.setValue(target);
            refresh();
        });
        discountButton = button(x + w / 2, top + 94, w / 2, 20, Component.empty(), () -> cycleDiscount(1));
        button(x, top + 177, half, 18, text("my_level"), () -> {
            int level = DiamondWorldProtocolClient.getCurrentLevel();
            if (level > 0) currentInput.setValue(Integer.toString(Math.min(level, LevelCalculator.MAX_LEVEL)));
        }).active = DiamondWorldProtocolClient.getCurrentLevel() > 0;
        button(x + half + 10, top + 177, half, 18, Component.translatable("gui.done"), this::onClose);
        refresh();
        setInitialFocus(targetInput);
    }

    private EditBox input(int x, int y, int w, String label, String value) {
        var box = addRenderableWidget(new EditBox(font, x, y, w, 20, text(label)) {
            @Override public void insertText(String value) { super.insertText(value.replaceAll("[^0-9]", "")); }
        });
        box.setMaxLength(3);
        box.setValue(value);
        return box;
    }

    private void selectServer(LevelCalculator.Server value) { server = value; refresh(); }
    private static String bounded(String value) {
        return value.isEmpty() ? "" : Integer.toString(Math.clamp(level(value), 1, LevelCalculator.MAX_LEVEL));
    }
    private Button button(int x, int y, int w, int h, Component label, Runnable action) {
        return addRenderableWidget(new CalculatorButton(x, y, w, h, label, action));
    }
    private void cycleDiscount(int direction) {
        var options = LevelCalculator.discounts(level(target));
        discount = options.get(Math.floorMod(options.indexOf(discount) + direction, options.size()));
        refresh();
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1 && discountButton.active && discountButton.isMouseOver(event.x(), event.y())) {
            cycleDiscount(-1);
            UiSounds.click();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
    private void refresh() {
        if (!LevelCalculator.discounts(level(target)).contains(discount)) discount = LevelCalculator.Discount.NONE;
        evo.active = server != LevelCalculator.Server.EVO;
        fast.active = server != LevelCalculator.Server.EVO_FAST;
        discountButton.active = LevelCalculator.discounts(level(target)).size() > 1;
        discountButton.setMessage(discount == LevelCalculator.Discount.NONE ? text("no_discount")
                : discount == LevelCalculator.Discount.MAX ? text("maximum") : Component.literal(discount.percent() + "%"));
    }

    static int level(String value) {
        try { return Integer.parseInt(value); } catch (NumberFormatException invalid) { return 0; }
    }
    static Component text(String key, Object... arguments) {
        return Component.translatable("froghelper.calculator." + key, arguments);
    }
    private String number(long value) { return numbers.format(value).replace(',', ' '); }
    private String amount(long value) {
        if (value < 1_000) return number(value);
        String[] suffixes = {"K", "M", "B", "T"};
        double scaled = value / 1_000.0;
        int unit = 0;
        while (Math.round(scaled * 100) >= 100_000 && unit < suffixes.length - 1) {
            scaled /= 1_000;
            unit++;
        }
        return compactNumbers.format(scaled) + suffixes[unit];
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, WidgetTheme.withAlpha(WidgetTheme.PANEL_BG, 0x66));
        HudBlur.beginFrame(graphics);
        HudSurface.drawPanel(graphics, left, top, panelWidth, panelHeight, WidgetChrome.FROST, HudSurface.nativeRenderer());
        graphics.text(font, title, left + 14, top + 12, WidgetTheme.TITLE);
        graphics.text(font, text("current"), left + 14, top + 54, WidgetTheme.TEXT_SECONDARY);
        graphics.text(font, text("target"), targetInput.getX(), top + 54, WidgetTheme.TEXT_SECONDARY);
        graphics.text(font, text("discount"), left + 14, top + 100, WidgetTheme.TEXT_SECONDARY);
        var result = LevelCalculator.calculate(level(current), level(target), server, discount);
        row(graphics, 123, "levels", result == null ? "—" : number(result.levels()));
        row(graphics, 140, "money", result == null ? "—" : amount(result.money()));
        row(graphics, 157, "blocks", result == null ? "—" : amount(result.blocks()));
        if (result != null && mouseX >= left + 14 && mouseX < left + panelWidth - 14) {
            if (mouseY >= top + 138 && mouseY < top + 151)
                graphics.setTooltipForNextFrame(font, Component.literal(number(result.money())), mouseX, mouseY);
            else if (mouseY >= top + 155 && mouseY < top + 168)
                graphics.setTooltipForNextFrame(font, Component.literal(number(result.blocks())), mouseX, mouseY);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void row(GuiGraphicsExtractor graphics, int y, String label, String value) {
        graphics.text(font, text(label), left + 14, top + y, WidgetTheme.TEXT_SECONDARY);
        graphics.text(font, value, left + panelWidth - 14 - font.width(value), top + y, WidgetTheme.TEXT_PRIMARY);
    }
    @Override protected void extractBlurredBackground(GuiGraphicsExtractor graphics) { }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }

    private final class CalculatorButton extends Button {
        CalculatorButton(int x, int y, int w, int h, Component label, Runnable action) {
            super(x, y, w, h, label, button -> action.run(), DEFAULT_NARRATION);
        }
        @Override protected void extractContents(GuiGraphicsExtractor graphics, int x, int y, float delta) {
            boolean selected = !active && (this == evo || this == fast);
            boolean hovered = active && isMouseOver(x, y);
            HudSurface.fillRounded(graphics, getX(), getY(), getWidth(), getHeight(), 4,
                    hovered || selected ? WidgetTheme.PANEL_BG : WidgetTheme.PANEL_BG_SOFT);
            if (hovered || selected) graphics.fill(getX() + 4, getY(), getRight() - 4, getY() + 1, WidgetTheme.ACCENT_LINE);
            graphics.centeredText(font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - font.lineHeight) / 2,
                    selected || hovered ? WidgetTheme.TITLE : active ? WidgetTheme.TEXT_PRIMARY : WidgetTheme.TEXT_MUTED);
        }
        @Override public void playDownSound(net.minecraft.client.sounds.SoundManager manager) { UiSounds.click(); }
    }
}
