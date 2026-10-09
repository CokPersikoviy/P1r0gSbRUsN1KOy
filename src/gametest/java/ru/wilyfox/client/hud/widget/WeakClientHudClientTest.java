package ru.wilyfox.client.hud.widget;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.wilyfox.boss.BossInfo;
import ru.wilyfox.boss.BossRepository;
import ru.wilyfox.client.hud.config.BossWidgetConfig;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.WidgetChrome;
import ru.wilyfox.client.hud.internal.HudFrameClock;
import ru.wilyfox.client.hud.layer.HudLayer;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class WeakClientHudClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                var original = ConfigManager.get().bossWidget;
                boolean wasAdded = ConfigManager.get().mainLayout.widgets.contains("BossHudWidget");
                ConfigManager.get().mainLayout.widgets.add("BossHudWidget");
                ConfigManager.layoutChanged();
                var render = ConfigManager.get().render;
                boolean oldLight = render.lightweightHud;
                boolean oldNative = render.nativeRenderer;
                var oldChrome = render.widgetChrome;
                ConfigManager.get().bossWidget = new BossWidgetConfig();
                ConfigManager.get().bossWidget.minLevel = 0;
                render.nativeRenderer = false;
                render.widgetChrome = WidgetChrome.FROST;
                render.lightweightHud = false;
                var repository = new BossRepository();
                repository.upsertProtocol("boss", "Кригер", System.currentTimeMillis() + 3_700_000L, 5);
                var widget = new BossHudWidget(10, 10, HudLayer.CONTENT, repository);
                try {
                    HudFrameClock.advance();
                    int width = widget.getWidth();
                    BossInfo boss = repository.getAllProtocol().iterator().next();
                    Object first = row(widget, boss);
                    String name = (String) field(first, "name");
                    String timer = (String) field(first, "timer");
                    int height = widget.getHeight();
                    for (int i = 0; i < 20; i++) {
                        HudFrameClock.advance();
                        expect(widget.getWidth() == width, "Repeated frames changed unchanged dimensions");
                        expect(row(widget, boss) == first, "Unchanged boss must reuse its row");
                        expect(field(first, "name") == name, "Static name must reuse its string");
                    }
                    ItemStack icon = icon(widget, boss);
                    expect(icon(widget, boss) == icon, "Repeated icon requests must reuse their stack");
                    repository.rememberDiscoveredIcon("Кригер", 5, new ItemStack(Items.DIAMOND));
                    expect(icon(widget, boss).is(Items.DIAMOND), "New discovered icon did not invalidate cached fallback");
                    repository.clearProtocol();
                    expect(!icon(widget, boss).is(Items.DIAMOND), "Cleared connection retained the discovered icon");
                    repository.upsertProtocol("boss", "Кригер", System.currentTimeMillis() + 3_000L, 5);
                    HudFrameClock.advance();
                    widget.getWidth();
                    BossInfo replacement = repository.getAllProtocol().iterator().next();
                    expect(!timer.equals(field(row(widget, replacement), "timer")), "A new deadline must refresh the displayed countdown");
                    replacement.setRespawnAt(System.currentTimeMillis() - 1_000L);
                    HudFrameClock.advance(); widget.getWidth();
                    expect(((String) field(row(widget, replacement), "timer")).startsWith("-"), "Spawned countdown lost its sign");
                    ConfigManager.get().bossWidget.showName = false;
                    HudFrameClock.advance();
                    expect(widget.getWidth() < width, "Display option must refresh layout");

                    var state = new GuiRenderState();
                    var graphics = new GuiGraphicsExtractor(client, state, 0, 0);
                    render.lightweightHud = false;
                    WidgetUtils.drawItemIcon(graphics, new ItemStack(Items.CLOCK), 0, 0);
                    expect(itemCount(state) == 1, "Regular HUD must submit its item model");
                    state.reset(); render.lightweightHud = true;
                    HudBlur.beginFrame(graphics);
                    HudBlur.blurBehind(graphics, 0, 0, 80, 30, 5);
                    expect(itemCount(state) == 0 && elementCount(state) == 0, "Lightweight HUD requested blur");
                    expect(field(HudBlur.class, "blurred") == null, "Lightweight HUD retained the old blur framebuffer");
                    expect(HudSurface.nativeRenderer(), "Lightweight HUD must select flat panels");
                    WidgetUtils.drawItemIcon(graphics, new ItemStack(Items.CLOCK), 0, 0);
                    expect(itemCount(state) == 0, "Lightweight HUD submitted an item model");
                    repository.upsertProtocol("boss", "Кригер", System.currentTimeMillis() + 3_700_000L, 5);
                    ConfigManager.get().bossWidget.showName = true;
                    HudFrameClock.advance();
                    expect(widget.getWidth() == width && widget.getHeight() == height, "Lightweight mode changed timer spacing");
                    widget.render(graphics, DeltaTracker.ZERO);
                    expect(itemCount(state) == 0 && elementCount(state) > 0, "Lightweight timer must render text/panels without items");
                    repository.upsertProtocol("boss", "Renamed Boss", System.currentTimeMillis() + 60_000L, 6);
                    HudFrameClock.advance(); widget.getWidth();
                    expect(((Map<?, ?>) field(widget, "rowCache")).size() == 1, "Packet replacements retained obsolete rows");
                    repository.upsertProtocol("guardian", "§bДревний Страж", System.currentTimeMillis() + 60_000L, 1);
                    HudFrameClock.advance(); widget.getWidth();
                    expect(((Map<?, ?>) field(widget, "rowCache")).size() == 1, "Excluded event boss entered the timer");
                    repository.clearProtocol();
                    HudFrameClock.advance(); widget.getWidth();
                    expect(((Map<?, ?>) field(widget, "rowCache")).isEmpty(), "Clearing timers retained cached rows");
                    expect(((Map<?, ?>) field(widget, "resolvedIcons")).isEmpty(), "Clearing timers retained resolved icons");
                } finally {
                    ConfigManager.get().bossWidget = original;
                    if (!wasAdded) ConfigManager.get().mainLayout.widgets.remove("BossHudWidget");
                    ConfigManager.layoutChanged();
                    render.lightweightHud = oldLight;
                    render.nativeRenderer = oldNative;
                    render.widgetChrome = oldChrome;
                    HudBlur.close();
                }
            });
        }
    }

    private static Object row(BossHudWidget widget, BossInfo boss) { return ((Map<?, ?>) field(widget, "rowCache")).get(boss); }
    private static ItemStack icon(BossHudWidget widget, BossInfo boss) {
        try {
            var method = BossHudWidget.class.getDeclaredMethod("getBossIcon", BossInfo.class);
            method.setAccessible(true); return (ItemStack) method.invoke(widget, boss);
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
    private static Object field(Object object, String name) {
        try {
            var field = (object instanceof Class<?> type ? type : object.getClass()).getDeclaredField(name);
            field.setAccessible(true); return field.get(object instanceof Class<?> ? null : object);
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
    private static int elementCount(GuiRenderState state) {
        var count = new AtomicInteger();
        state.forEachElement(element -> count.incrementAndGet(), GuiRenderState.TraverseRange.ALL);
        return count.get();
    }
    private static int itemCount(GuiRenderState state) {
        var count = new AtomicInteger();
        state.forEachItem(element -> count.incrementAndGet());
        return count.get();
    }
    private static void expect(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
