package ru.wilyfox.client.hud.menu;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.calculator.LevelCalculatorScreen;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Real menu entry, native fields/buttons, discount changes, resizing and return to the parent. */
public final class LevelCalculatorClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        int scale = context.computeOnClient(client -> client.options.guiScale().get());
        int width = context.computeOnClient(client -> client.getWindow().getScreenWidth());
        int height = context.computeOnClient(client -> client.getWindow().getScreenHeight());
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                client.options.guiScale().set(2);
                client.getWindow().setWindowed(1280, 720); client.resizeGui();
                client.gui.setScreen(null);
                var categories = new EnumMap<SettingsCategory, List<SettingsComponent>>(SettingsCategory.class);
                for (var category : SettingsCategory.values()) categories.put(category, new ArrayList<>());
                HudSettingsFeatureSections.populate(categories, () -> { });
                var entry = categories.get(SettingsCategory.LEVEL_CALCULATOR).getFirst();
                entry.setSize(200, 20);
                expect(entry.mouseClicked(100, 10, 0), "Calculator menu action was not clickable");
                var screen = (LevelCalculatorScreen) client.gui.screen();
                var inputs = inputs(screen);
                inputs.getFirst().setValue(""); inputs.getFirst().insertText("12bad5");
                expect(inputs.getFirst().getValue().equals("125"), "Numeric paste filtering failed");
                inputs.get(1).setValue("999");
                expect(inputs.get(1).getValue().equals("600"), "Levels above 600 were not clamped");
                inputs.get(1).setValue("0");
                expect(inputs.get(1).getValue().equals("1"), "Levels below 1 were not clamped");
                inputs.get(1).setValue("600");
                var discount = buttons(screen).get(2);
                expect(discount.active, "Discount did not become available");
                for (int i = 0; i < 5; i++) press(discount);
                expect(discount.getMessage().equals(LevelCalculatorScreenText.maximum()), "Maximum discount missing");
                var rightClick = new MouseButtonEvent(discount.getX() + 4, discount.getY() + 4, new MouseButtonInfo(1, 0));
                expect(screen.mouseClicked(rightClick, false), "Discount did not accept right-click");
                expect(discount.getMessage().getString().equals("20%"), "Right-click did not cycle backward");
                inputs.get(1).setValue("540");
                expect(discount.getMessage().equals(LevelCalculatorScreenText.none()), "Unavailable discount was not reset");
                inputs.get(1).setValue("480"); expect(!discount.active, "Low-level discount was not disabled");
                inputs.get(1).setValue("600"); press(discount); press(discount); press(discount); press(discount); press(discount);
                press(buttons(screen).get(1));
                expect(!buttons(screen).get(1).active, "EvoFast mode was not selected");
                checkBounds(screen);
            });
            context.waitTicks(3); context.takeScreenshot("level-calculator");
            context.runOnClient(client -> {
                client.options.guiScale().set(3);
                client.getWindow().setWindowed(960, 720); client.resizeGui();
                var screen = (LevelCalculatorScreen) client.gui.screen();
                expect(inputs(screen).getFirst().getValue().equals("125"), "Resize lost current level");
                expect(inputs(screen).get(1).getValue().equals("600"), "Resize lost target level");
                expect(buttons(screen).get(2).getMessage().equals(LevelCalculatorScreenText.maximum()), "Resize lost discount");
                expect(!buttons(screen).get(1).active, "Resize lost server mode");
                checkBounds(screen);
            });
            context.waitTicks(3); context.takeScreenshot("level-calculator-small");
            context.runOnClient(client -> {
                var screen = (LevelCalculatorScreen) client.gui.screen();
                var input = inputs(screen).get(1);
                screen.setFocused(input); input.setFocused(true);
                input.keyPressed(new KeyEvent(GLFW.GLFW_KEY_BACKSPACE, 0, GLFW.GLFW_MOD_CONTROL));
                expect(input.getValue().isEmpty(), "Ctrl+Backspace could not clear the level");
                screen.onClose(); expect(client.gui.screen() == null, "Calculator did not return to its parent");
            });
        } finally {
            context.runOnClient(client -> {
                client.gui.setScreen(null); client.options.guiScale().set(scale);
                client.getWindow().setWindowed(width, height); client.resizeGui();
            });
        }
    }
    private static List<EditBox> inputs(LevelCalculatorScreen screen) {
        return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
    }
    private static List<Button> buttons(LevelCalculatorScreen screen) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();
    }
    private static void press(Button button) { button.onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)); }
    private static void checkBounds(LevelCalculatorScreen screen) {
        for (var child : screen.children()) if (child instanceof AbstractWidget widget)
            expect(widget.getX() >= 0 && widget.getY() >= 0 && widget.getRight() <= screen.width
                    && widget.getBottom() <= screen.height, "Calculator control extends outside the screen");
    }
    private static void expect(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static final class LevelCalculatorScreenText {
        static net.minecraft.network.chat.Component maximum() { return net.minecraft.network.chat.Component.translatable("froghelper.calculator.maximum"); }
        static net.minecraft.network.chat.Component none() { return net.minecraft.network.chat.Component.translatable("froghelper.calculator.no_discount"); }
    }
}
