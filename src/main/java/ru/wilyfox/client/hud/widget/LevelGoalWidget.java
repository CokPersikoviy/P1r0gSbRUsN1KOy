package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.calculator.LevelCalculator;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.*;
import ru.wilyfox.client.hud.internal.HudFrameClock;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.level.*;
import ru.wilyfox.client.popup.*;
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import java.util.*;

public final class LevelGoalWidget extends AbstractWidget {
    private final LevelProgressStore store;
    private final List<Component> lines = new ArrayList<>();
    private LevelGoalCalculator.Goal goal;
    private Object presentationKey, notificationKey;
    private boolean previousReady;
    private long frame = -1;
    private int contentWidth = 150, contentHeight = 30;
    public LevelGoalWidget(int x, int y, HudLayer layer, LevelProgressStore store) { super(x,y,layer); this.store = store; }
    private LevelCalculator.Server server() {
        return switch (ConfigManager.get().levelGoal.server) {
            case EVO -> LevelCalculator.Server.EVO;
            case EVO_FAST -> LevelCalculator.Server.EVO_FAST;
            case AUTO -> DiamondWorldProtocolClient.getCurrentServerInfo().rawName().toUpperCase(Locale.ROOT).contains("FAST")
                    ? LevelCalculator.Server.EVO_FAST : LevelCalculator.Server.EVO;
        };
    }
    public void tick(Minecraft client) {
        try (var ignored = ModProfiler.getInstance().scope("tick/LevelGoal")) {
            var config = ConfigManager.get().levelGoal;
            if (client.player == null || client.level == null || !isInLayout() || config.targetLevel == 0) {
                notificationKey = null; return;
            }
            var value = LevelGoalCalculator.calculate(store.getSnapshot(), config.targetLevel, server(), config.discount);
            if (value == null) { notificationKey = null; return; }
            Object key = List.of(client.player.getUUID(), config.targetLevel, server(), config.discount);
            if (key.equals(notificationKey) && value.ready() && !previousReady && config.notifications) {
                client.gui.hud.getChat().addClientSystemMessage(Component.translatable(value.reached() ? "froghelper.goal.reached" : "froghelper.goal.ready", value.target()));
                PopUpManager.getInstance().publish(PopUpRequest.of("level.goal", "Level Goal",
                        Component.translatable(value.reached() ? "froghelper.goal.reached" : "froghelper.goal.ready", value.target()).getString(), PopUpSeverity.SUCCESS));
            }
            notificationKey = key; previousReady = value.ready();
        }
    }
    private void prepare() {
        if (frame == HudFrameClock.current()) return;
        frame = HudFrameClock.current();
        try (var ignored = ModProfiler.getInstance().scope("hud/LevelGoal/prepare")) {
            var config = ConfigManager.get().levelGoal;
            goal = LevelGoalCalculator.calculate(store.getSnapshot(), config.targetLevel, server(), config.discount);
            Object key = Arrays.asList(goal, config.targetLevel, config.showMoney, config.showBlocks, config.showBar, WidgetUtils.showWidgetTitles());
            if (key.equals(presentationKey)) return;
            presentationKey = key; lines.clear();
            if (goal == null) {
                lines.add(Component.literal("Level Goal"));
                lines.add(Component.translatable(config.targetLevel == 0 ? "froghelper.goal.set_target" : "froghelper.goal.waiting"));
            } else {
                // The target is content, not a decorative title: UnClutter must not hide it.
                lines.add(Component.translatable("froghelper.goal.title", goal.target(), goal.levelsLeft()));
                if (goal.reached()) lines.add(Component.translatable("froghelper.goal.reached", goal.target()));
                else {
                    if (config.showMoney) lines.add(Component.translatable("froghelper.goal.money", amount(goal.moneyLeft())));
                    if (config.showBlocks) lines.add(Component.translatable("froghelper.goal.blocks", amount(goal.blocksLeft())));
                    if (goal.ready()) lines.add(Component.translatable("froghelper.goal.ready", goal.target()));
                    if (lines.isEmpty()) lines.add(Component.translatable("froghelper.goal.title", goal.target(), goal.levelsLeft()));
                }
            }
            var font = Minecraft.getInstance().font;
            contentWidth = lines.stream().mapToInt(font::width).max().orElse(100) + 12;
            contentHeight = 10 + lines.size() * (font.lineHeight + 2) - 2 + (bar() ? 5 : 0);
        }
    }
    private boolean bar() { return goal != null && !goal.reached() && ConfigManager.get().levelGoal.showBar; }
    static String amount(double value) {
        String[] suffix = {"", "K", "M", "B", "T"}; int unit = 0;
        while (value >= 1000 && unit < suffix.length - 1) { value /= 1000; unit++; }
        return String.format(Locale.ROOT, unit == 0 ? "%.0f%s" : "%.2f%s", value, suffix[unit]);
    }
    @Override public void render(GuiGraphicsExtractor g, DeltaTracker tick) {
        if (!isVisible()) return; prepare(); var font = Minecraft.getInstance().font;
        g.pose().pushMatrix(); g.pose().translate(startX,startY); g.pose().scale(scale,scale);
        HudSurface.drawPanel(g,contentWidth,contentHeight);
        for (int i=0;i<lines.size();i++) g.text(font,lines.get(i),6,5+i*(font.lineHeight+2),i==0?WidgetTheme.TITLE:goal!=null&&goal.ready()?WidgetTheme.TEXT_ACCENT:WidgetTheme.TEXT_SECONDARY);
        if (bar()) HudSurface.drawBar(g,6,contentHeight-8,contentWidth-12,3,(float)goal.progress(),WidgetTheme.BAR_FILL);
        g.pose().popMatrix();
    }
    @Override public int getWidth() { prepare(); return Math.round(contentWidth * getScale()); }
    @Override public int getHeight() { prepare(); return Math.round(contentHeight * getScale()); }
    @Override public boolean isVisible() { return isInLayout() && (store.getSnapshot().available() || Minecraft.getInstance().gui.screen() instanceof HudEditingScreen); }
    @Override public String getDisplayName() { return "Level Goal"; }
}
