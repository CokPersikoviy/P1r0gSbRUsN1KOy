package ru.wilyfox.client.hud.menu;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.HudRenderer;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.widget.HudBlur;

import java.util.List;

/** Native input/render coverage: global discovery, result controls, scrolling, focus and undo. */
public final class SettingsSearchClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        int oldScale = context.computeOnClient(client -> client.options.guiScale().get());
        int oldWidth = context.computeOnClient(client -> client.getWindow().getScreenWidth());
        int oldHeight = context.computeOnClient(client -> client.getWindow().getScreenHeight());
        var panel = new HudSettingsPanel();
        var renderer = new HudRenderer(panel);
        var screen = context.computeOnClient(client -> new TestScreen(renderer, panel));
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                client.options.guiScale().set(2);
                client.getWindow().setWindowed(1280, 720); client.resizeGui();
                renderer.setSettings(true); client.gui.setScreen(screen);
                setField(panel, "activeCategory", SettingsCategory.RENDER);
                setField(panel, "scrollOffset", 120);
                setField(panel, "categoryScrollOffset", 40);
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                expect((int) field(panel, "scrollOffset") == 120, "Initial scroll did not render");
                focus(screen, panel); type(renderer, "FISHING cast");
                expect(categories(panel).equals(List.of(SettingsCategory.FISHING)), "Search did not find another category ignoring case");
                expect(components(panel).size() == 1 && components(panel).getFirst().getLabel().equals("Cast preview"), "Multiword category/label match failed");
                expect((int) field(panel, "scrollOffset") == 0, "Search did not reset the old scroll");
            });
            context.waitTicks(2);
            context.takeScreenshot("settings-search-cast");
            context.runOnClient(client -> {
                var toggle = components(panel).getFirst();
                boolean before = ConfigManager.get().fishing.showCastPreview;
                click(screen, toggle.getX() + toggle.getWidth() - 15, toggle.getY() + 11);
                expect(ConfigManager.get().fishing.showCastPreview != before, "Result click did not change the actual setting");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL));
                expect(ConfigManager.get().fishing.showCastPreview == before, "Undo failed in search results");
                expect(components(panel).getFirst().getLabel().equals("Cast preview"), "Undo lost the result list");
                focus(screen, panel);
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
                expect(renderer.isSettingsOpen(), "Clearing search closed the menu");
                expect(field(panel, "activeCategory") == SettingsCategory.RENDER && (int) field(panel, "scrollOffset") == 120,
                        "Clearing search did not restore category/scroll");
                expect((int) field(panel, "categoryScrollOffset") == 40, "Clearing search did not restore sidebar scroll");
                type(renderer, "nothingshouldmatchthis");
                expect(categories(panel).isEmpty() && components(panel).isEmpty(), "Empty search results retained interactive settings");
            });
            context.waitTicks(2);
            context.takeScreenshot("settings-search-empty");
            context.runOnClient(client -> {
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL));
                client.keyboardHandler.setClipboard("  ChAt\n history  ");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_V, 0, GLFW.GLFW_MOD_CONTROL));
                expect(field(panel, "search").equals("  ChAt history  "), "Paste did not sanitize/replace the query");
                expect(components(panel).size() == 1 && components(panel).getFirst().getLabel().equals("Extra chat history"), "Pasted query did not filter");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_BACKSPACE, 0, GLFW.GLFW_MOD_CONTROL));
                expect(field(panel, "search").equals(""), "Ctrl+Backspace did not clear settings search");
                type(renderer, "😀");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_BACKSPACE, 0, 0));
                expect(field(panel, "search").equals(""), "Backspace split a Unicode code point");
                type(renderer, "x".repeat(80));
                expect(((String) field(panel, "search")).length() == 48, "Search exceeded its input bound");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_DELETE, 0, 0));
                type(renderer, "Boss Messages");
                expect(categories(panel).equals(List.of(SettingsCategory.BOSS_RESPAWN_MESSAGES)), "Category-name lookup failed");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                expect(!panel.charTyped('x', 0), "Enter did not release search focus");
                focus(screen, panel); screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_DELETE, 0, 0));
                String[] value = {"Full text, including text after the cursor"};
                var input = new TextInputSettingsComponent(0, 0, 100, 22, "Test input", () -> value[0], text -> value[0] = text, 100);
                expect(!input.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, GLFW.GLFW_MOD_CONTROL), "Unfocused field consumed clear shortcut");
                input.mouseClicked(5, 5, 0);
                input.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0);
                input.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, GLFW.GLFW_MOD_CONTROL);
                expect(value[0].isEmpty(), "Ctrl+Backspace did not clear text after the cursor");
                input.charTyped('a', 0);
                expect(value[0].equals("a"), "Clearing did not reset the cursor or retain focus");
                checkTabNameDraft(client);
                var sidebar = new ru.wilyfox.client.hud.internal.HudWidgetSidebar(renderer);
                sidebar.mousePressed(25, 45, 0);
                "Boss".codePoints().forEach(sidebar::charTyped);
                sidebar.keyPressed(GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_MOD_CONTROL);
                expect(field(sidebar, "search").equals(""), "Ctrl+Backspace did not clear widget search");
            });
            context.waitTicks(2);
            context.takeScreenshot("settings-search-header");
            context.runOnClient(client -> {
                ConfigManager.get().visuals = new ru.wilyfox.client.hud.config.VisualsConfig();
                ConfigManager.get().visuals.sanitize();
                changeEveryVisualField(ConfigManager.get().visuals);
                ConfigManager.save();
                setField(panel, "activeCategory", SettingsCategory.VISUALS);
                setField(panel, "scrollOffset", 0);
            });
            for (String section : List.of("SCREEN", "CROSSHAIR", "HITBOXES", "BLOCK_OUTLINE", "HAND", "CAMERA", "WORLD", "TAB")) {
                context.runOnClient(client -> selectVisualSection(section));
                context.waitTicks(2);
                var before = context.computeOnClient(client -> ConfigManager.snapshot());
                context.runOnClient(client -> {
                    clickAction(screen, panel, "Reset section");
                    expect(!ConfigManager.snapshot().equals(before), "Reset " + section + " did not change its settings");
                    expect(ConfigManager.snapshot().getAsJsonObject().get("render").equals(before.getAsJsonObject().get("render")), "Reset affected non-Visuals settings");
                    screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL));
                    expect(ConfigManager.snapshot().equals(before), "Undo did not fully restore " + section);
                });
                context.waitTicks(2);
                context.runOnClient(client -> clickAction(screen, panel, "Reset section"));
            }
            context.runOnClient(client -> {
                var defaults = new ru.wilyfox.client.hud.config.VisualsConfig(); defaults.sanitize();
                var gson = ru.wilyfox.client.hud.config.HudConfigCodec.createGson();
                expect(gson.toJsonTree(ConfigManager.get().visuals).equals(gson.toJsonTree(defaults)), "Reset sections missed a Visuals field");
                changeEveryVisualField(ConfigManager.get().visuals); ConfigManager.save();
                var before = ConfigManager.snapshot();
                clickAction(screen, panel, "Reset All Visuals");
                expect(gson.toJsonTree(ConfigManager.get().visuals).equals(gson.toJsonTree(defaults)), "Reset all missed a Visuals field");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL));
                expect(ConfigManager.snapshot().equals(before), "Undo failed after reset all");
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                clickAction(screen, panel, "Reset All Visuals");
                selectVisualSection("SCREEN");
            });
            context.waitTicks(2); context.takeScreenshot("visuals-reset-buttons");
            context.runOnClient(client -> { selectVisualSection("CAMERA"); setField(panel, "scrollOffset", 100); });
            context.waitTicks(2);
            context.runOnClient(client -> {
                ConfigManager.get().visuals.camera.key = GLFW.GLFW_KEY_F6;
                ConfigManager.get().visuals.camera.modifiers = GLFW.GLFW_MOD_CONTROL;
                var bind = components(panel).stream().filter(c -> c.getLabel().equals("Freelook key")).findFirst().orElseThrow();
                click(screen, bind.getX() + bind.getWidth() - 15, bind.getY() + 10);
                expect((boolean) field(bind, "listening"), "Keybind row was not armed by the click");
                expect(screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0)), "Listening bind did not consume Esc");
                expect(ConfigManager.get().visuals.camera.key == GLFW.GLFW_KEY_UNKNOWN && ConfigManager.get().visuals.camera.modifiers == 0,
                        "Esc did not remove the key and modifiers");
                expect(renderer.isSettingsOpen() && client.gui.screen() == screen, "Esc closed the settings while clearing a bind");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL));
                expect(ConfigManager.get().visuals.camera.key == GLFW.GLFW_KEY_F6 && ConfigManager.get().visuals.camera.modifiers == GLFW.GLFW_MOD_CONTROL,
                        "Undo did not restore a cleared bind");
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                var bind = components(panel).stream().filter(c -> c.getLabel().equals("Freelook key")).findFirst().orElseThrow();
                var rightClick = new MouseButtonEvent(bind.getX() + bind.getWidth() - 15, bind.getY() + 10, new MouseButtonInfo(1, 0));
                screen.mouseClicked(rightClick, false); screen.mouseReleased(rightClick);
                expect(ConfigManager.get().visuals.camera.key == GLFW.GLFW_KEY_UNKNOWN && ConfigManager.get().visuals.camera.modifiers == 0,
                        "Right-click did not clear the bind and modifiers");
                expect(!bind.keyPressed(GLFW.GLFW_KEY_F7, 0, 0), "Clearing left the bind listening for another key");
                var saved = ConfigManager.snapshot(); ConfigManager.restoreSnapshot(saved);
                expect(ConfigManager.get().visuals.camera.key == GLFW.GLFW_KEY_UNKNOWN && ConfigManager.get().visuals.camera.modifiers == 0,
                        "Cleared bind did not survive config reload");
                System.out.println("Keybind clearing: Esc/right-click, modifiers, menu stays open, undo and config reload passed");
            });
            System.out.println("Settings search: native input, categories, controls, undo, clipboard, Unicode and scroll restoration passed");
            System.out.println("Visuals reset: all eight sections, all fields, independent settings and Ctrl+Z passed");
        } finally {
            context.runOnClient(client -> {
                client.gui.setScreen(null); renderer.setSettings(false);
                ConfigManager.restoreSnapshot(original);
                client.options.guiScale().set(oldScale);
                client.getWindow().setWindowed(oldWidth, oldHeight); client.resizeGui();
            });
        }
    }

    private static void focus(HudEditingScreen screen, HudSettingsPanel panel) {
        click(screen, (int) field(panel, "x") + 160, (int) field(panel, "y") + 12);
    }
    private static void checkTabNameDraft(net.minecraft.client.Minecraft client) {
        var catalog = ru.wilyfox.client.hud.config.WidgetCatalog.CHAT_GLOBAL;
        var config = ConfigManager.get().chatWidgets.get(catalog.key());
        var oldChannel = config.channel;
        String oldTitle = config.title;
        try {
            config.channel = ru.wilyfox.client.chat.ChatTab.ALL; config.title = "Old name";
            ConfigManager.save();
            var input = (TextInputSettingsComponent) WidgetSettingsSections.create(catalog).getFirst();
            input.setSize(300, 22); input.mouseClicked(5, 5, 0);
            for (int i = 0; i < "Old name".length(); i++) input.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
            expect(invoke(input, "getValue").equals(""), "Sanitization put ALL back into the active field");
            input.mouseClicked(5, 5, 0); ConfigManager.save();
            "Boss alerts".codePoints().forEach(cp -> input.charTyped(cp, 0));
            input.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            expect(config.title.equals("Boss alerts"), "Rename retained ALL or lost the space between words");
            input.mouseClicked(5, 5, 0);
            input.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, GLFW.GLFW_MOD_CONTROL);
            expect(invoke(input, "getValue").equals(""), "Ctrl+Backspace restored the default inside the draft");
            input.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            expect(invoke(input, "getValue").equals("ALL"), "Empty completed name did not use the default");
            input.mouseClicked(5, 5, 0);
            input.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, GLFW.GLFW_MOD_CONTROL);
            client.keyboardHandler.setClipboard("Fishing tab");
            input.keyPressed(GLFW.GLFW_KEY_V, 0, GLFW.GLFW_MOD_CONTROL);
            input.onClickOutside();
            expect(config.title.equals("Fishing tab") && invoke(input, "getValue").equals("Fishing tab"),
                    "Paste after clearing retained ALL or blur lost the replacement name");
        } finally {
            config.channel = oldChannel; config.title = oldTitle; ConfigManager.save();
        }
    }
    private static void clickAction(HudEditingScreen screen, HudSettingsPanel panel, String label) {
        var action = components(panel).stream().filter(c -> c.getLabel().equals(label)).findFirst().orElseThrow();
        click(screen, action.getX() + 10, action.getY() + 11);
    }
    @SuppressWarnings({"unchecked", "rawtypes"}) private static void selectVisualSection(String name) {
        try {
            var field = HudSettingsVisualsSection.class.getDeclaredField("selected"); field.setAccessible(true);
            field.set(null, Enum.valueOf((Class<? extends Enum>) field.getType(), name));
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    /** Change every field so a forgotten hidden/default setting also fails the reset coverage. */
    private static void changeEveryVisualField(Object config) {
        try {
            for (var field : config.getClass().getFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.getName().equals("formatRevision")) continue;
                var type = field.getType();
                if (type == boolean.class) field.setBoolean(config, !field.getBoolean(config));
                else if (type == int.class) field.setInt(config, field.getName().toLowerCase(java.util.Locale.ROOT).endsWith("key") ? 32 : field.getInt(config) + 1);
                else if (type == double.class) field.setDouble(config, field.getDouble(config) + .25);
                else if (type.isEnum()) {
                    Object[] values = type.getEnumConstants(); field.set(config, values[(((Enum<?>) field.get(config)).ordinal() + 1) % values.length]);
                } else changeEveryVisualField(field.get(config));
            }
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void type(HudRenderer renderer, String text) { text.codePoints().forEach(cp -> expect(renderer.onCharTyped(cp, 0), "Search did not consume text")); }
    private static void click(HudEditingScreen screen, double x, double y) {
        var event = new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0));
        screen.mouseClicked(event, false); screen.mouseReleased(event);
    }
    @SuppressWarnings("unchecked") private static List<SettingsComponent> components(HudSettingsPanel panel) { return (List<SettingsComponent>) invoke(panel, "activeComponents"); }
    @SuppressWarnings("unchecked") private static List<SettingsCategory> categories(HudSettingsPanel panel) { return (List<SettingsCategory>) invoke(panel, "visibleCategories"); }
    private static Object invoke(Object target, String name) {
        try { var method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(target); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static Object field(Object target, String name) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void setField(Object target, String name, Object value) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void expect(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static final class TestScreen extends HudEditingScreen {
        private final HudSettingsPanel panel;
        TestScreen(HudRenderer renderer, HudSettingsPanel panel) { super(renderer); this.panel = panel; }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float tick) {
            HudBlur.beginFrame(graphics); panel.render(graphics, x, y);
        }
    }
}
