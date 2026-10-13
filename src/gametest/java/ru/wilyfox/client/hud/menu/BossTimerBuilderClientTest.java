package ru.wilyfox.client.hud.menu;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.wilyfox.boss.*;
import ru.wilyfox.client.hud.config.*;
import ru.wilyfox.client.hud.internal.HudFrameClock;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.hud.widget.BossHudWidget;
import ru.wilyfox.client.protocol.*;
import java.util.*;

public final class BossTimerBuilderClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var snapshot = context.computeOnClient(client -> ConfigManager.snapshot());
        Object state = get(DiamondWorldProtocolClient.class, null, "STATE");
        Object captured = get(state.getClass(), state, "capturedBossLevels"), event = get(state.getClass(), state, "currentGameEvent"), collections = get(state.getClass(), state, "bossCollectibles");
        var types = new HashMap<>((Map<String, DwBossType>)get(state.getClass(), state, "bossTypes"));
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                var config = new BossWidgetConfig(); ConfigManager.get().bossWidget = config;
                ConfigManager.get().render.unclutterWidgets = false;
                ConfigManager.get().render.lightweightHud = false;
                ConfigManager.get().mainLayout.widgets.add("BossHudWidget"); ConfigManager.layoutChanged();
                SocialProtocolFixture.bossTypes(Map.of("legion", new DwBossType("legion", "Бессмертный Легион", "CLOCK", 130, 0, 0, true)));
                set(state, "capturedBossLevels", Set.of(130)); set(state, "currentGameEvent", DwGameEvent.MYTHICAL_EVENT);
                set(state, "bossCollectibles", Map.of("legion", Set.of()));
                var repository = new BossRepository(); repository.upsertProtocol("legion", "Бессмертный Легион", System.currentTimeMillis() + 600_000, 130);
                var widget = new BossHudWidget(10, 10, HudLayer.CONTENT, repository);
                var boss = repository.getAllProtocol().iterator().next();
                HudFrameClock.advance(); widget.getWidth();
                Object row = ((Map<?,?>)get(BossHudWidget.class, widget, "rowCache")).get(boss);
                check(get(row.getClass(), row, "name").equals("✗ ✦ Бессмертный Легион ◇"), "Live row missing combined statuses");
                var controls = WidgetSettingsSections.create(WidgetCatalog.BOSS_TIMERS);
                click(controls, "Show capture"); HudFrameClock.advance(); widget.getWidth();
                check(get(row.getClass(),row,"name").equals("✦ Бессмертный Легион ◇"), "Capture toggle also hid the mythical marker");
                click(controls,"Show mythical event"); HudFrameClock.advance(); widget.getWidth();
                check(get(row.getClass(),row,"name").equals("Бессмертный Легион ◇"), "Mythical toggle did not affect live rows");
                var color = (TextInputSettingsComponent) controls.get(controls.indexOf(controls.stream().filter(c->c.getLabel().equals("Show name")).findFirst().orElseThrow()) + 1);
                color.setPosition(0,0); color.setSize(260,22); color.mouseClicked(200,10,0); color.charTyped('&',0); color.charTyped('c',0);
                HudFrameClock.advance(); widget.getWidth();
                var name = (Component)get(row.getClass(),row,"nameComponent");
                check(name.getSiblings().getFirst().getStyle().getColor().getValue()==0xFF5555, "Color code did not reach live row");
                for(int i=0;i<5;i++){HudFrameClock.advance();widget.getWidth();check(get(row.getClass(),row,"nameComponent")==name,"Unchanged labels are rebuilt each frame");}
                click(controls,"Show name"); HudFrameClock.advance(); widget.getWidth();
                check(get(row.getClass(),row,"compact").equals("◇"), "Hiding name removed collectible status");
                var preview = new BossHudWidget.Preview();
                check(!preview.text().contains("Легион") && preview.text().contains("◇"), "Preview disagrees with hidden name");
                click(controls,"Show collectibles"); HudFrameClock.advance(); widget.getWidth();
                check(get(row.getClass(),row,"compact").equals(""), "Collectible toggle did not reach live row");
                int height = widget.getHeight(); click(controls,"Show title"); HudFrameClock.advance();
                check(widget.getHeight()<height, "Title toggle did not update HUD bounds");
                config.showTitle = true; ConfigManager.get().render.unclutterWidgets = true; HudFrameClock.advance();
                check(widget.getHeight()<height && !preview.text().startsWith("Boss Timers"), "Preview or HUD ignores global UnClutter");
                ConfigManager.get().render.unclutterWidgets = false;
                config.showTitle=true; config.showName=true; config.showCapture=true; config.showMythical=true; config.showCollectibles=true;
                config.colorCode(BossTimerElement.TIMER,"&b");
                client.gui.setScreen(new PreviewScreen());
            });
            context.waitTicks(3); context.takeScreenshot("boss-row-builder-preview");
            context.runOnClient(client -> {
                ConfigManager.get().bossWidget.showName = false; ConfigManager.get().bossWidget.fullAligment = true;
            });
            context.waitTicks(2); context.takeScreenshot("boss-row-builder-compact");
        } finally {
            context.runOnClient(client -> {
                client.gui.setScreen(null); SocialProtocolFixture.bossTypes(types);
                set(state,"capturedBossLevels",captured); set(state,"currentGameEvent",event); set(state,"bossCollectibles",collections);
                ConfigManager.restoreSnapshot(snapshot);
            });
        }
    }
    private static void click(List<SettingsComponent> controls, String label) {
        var c=controls.stream().filter(part->part.getLabel().equals(label)).findFirst().orElseThrow();
        c.setPosition(0,0);c.setSize(260,22);check(c.mouseClicked(200,10,0),"Toggle did not respond: "+label);
    }
    private static Object get(Class<?> type,Object object,String name) {
        try {var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static void set(Object object,String name,Object value) {
        try {var f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static final class PreviewScreen extends Screen {
        private final BossTimerPreviewComponent preview = new BossTimerPreviewComponent();
        PreviewScreen(){super(Component.literal("Boss timer row builder"));}
        @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float delta){
            preview.setPosition(20,30);preview.setSize(Math.min(width-40,380),94);preview.render(graphics,x,y);
        }
    }
}
