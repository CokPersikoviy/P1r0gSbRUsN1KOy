package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.FishingQuestDescriptionMode;
import ru.wilyfox.client.hud.config.FishingQuestTypeFilter;
import ru.wilyfox.client.hud.config.FishingWidgetVisibility;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import ru.wilyfox.client.protocol.DwHourlyQuestProgress;
import ru.wilyfox.client.protocol.DwHourlyQuestType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FishingQuestsWidget extends AbstractWidget {
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 4;
    private static final int ROW_GAP = 3;
    private static final int EMPTY_WIDTH = 154;
    private static final int EMPTY_HEIGHT = 28;

    public FishingQuestsWidget(int x, int y, HudLayer layer) {
        super(x, y, layer);
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        List<QuestView> quests = buildViews();
        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0F);

        if (quests.isEmpty()) {
            HudSurface.drawPlaceholderPanel(context, EMPTY_WIDTH, EMPTY_HEIGHT);
            context.drawString(mc.font, "Fishing Quests", PADDING_X, 6, WidgetTheme.TITLE);
            context.drawString(mc.font, "No active quests", PADDING_X, 15, WidgetTheme.TEXT_MUTED);
            context.pose().popPose();
            return;
        }

        int width = getUnscaledWidth(quests, mc);
        HudSurface.drawPanel(context, width, getUnscaledHeight(quests, mc));
        int y = PADDING_Y;
        if (WidgetUtils.showWidgetTitles()) {
            context.drawString(mc.font, "Fishing Quests", PADDING_X, y, WidgetTheme.TITLE);
            y += mc.font.lineHeight + 3;
        }

        ItemStack icon = new ItemStack(Items.PAPER);
        for (QuestView quest : quests) {
            context.renderItem(icon, PADDING_X, y);
            int textX = PADDING_X + ICON_SIZE + ICON_GAP;
            for (QuestLine line : quest.lines()) {
                context.drawString(mc.font, line.text(), textX, y, line.color());
                y += mc.font.lineHeight + 1;
            }
            y += ROW_GAP;
        }
        context.pose().popPose();
    }

    @Override
    public boolean isVisible() {
        return ConfigManager.get().fishing.showFishingQuestsWidget
                && (isEditorPreview() || matchesVisibility(ConfigManager.get().fishing.questsVisibility));
    }

    @Override
    public int getWidth() {
        Minecraft mc = Minecraft.getInstance();
        List<QuestView> views = buildViews();
        return Math.round((views.isEmpty() ? EMPTY_WIDTH : getUnscaledWidth(views, mc)) * scale);
    }

    @Override
    public int getHeight() {
        Minecraft mc = Minecraft.getInstance();
        List<QuestView> views = buildViews();
        return Math.round((views.isEmpty() ? EMPTY_HEIGHT : getUnscaledHeight(views, mc)) * scale);
    }

    @Override
    public String getDisplayName() {
        return "Fishing Quests";
    }

    private List<QuestView> buildViews() {
        Map<Integer, DwHourlyQuestType> types = DiamondWorldProtocolClient.getHourlyQuestTypes();
        Map<Integer, DwHourlyQuestProgress> progress = DiamondWorldProtocolClient.getHourlyQuestProgress();
        boolean showDescription = matchesDescription(ConfigManager.get().fishing.questsDescription);
        FishingQuestTypeFilter filter = ConfigManager.get().fishing.questsTypeFilter;

        List<QuestView> result = progress.values().stream()
                .filter(entry -> entry.remainingMillis() > 0L)
                .map(entry -> createView(types.get(entry.id()), entry, showDescription, filter))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(view -> typeOrder(view.type())))
                .toList();

        if (!result.isEmpty() || !isEditorPreview()) {
            return result;
        }
        return List.of(new QuestView("NORMAL", List.of(
                new QuestLine("Catch 20 fish 01:51", WidgetTheme.TEXT_PRIMARY),
                new QuestLine("Progress: 8/20", WidgetTheme.TEXT_SOFT)
        )));
    }

    private QuestView createView(DwHourlyQuestType type, DwHourlyQuestProgress progress,
                                 boolean showDescription, FishingQuestTypeFilter filter) {
        if (type == null) {
            return null;
        }
        String questType = normalizeType(type.type());
        if (filter != FishingQuestTypeFilter.ALL && !filter.name().equals(questType)) {
            return null;
        }

        List<QuestLine> lines = new ArrayList<>();
        lines.add(new QuestLine(type.name() + " " + formatRemaining(progress.remainingMillis()), WidgetTheme.TEXT_PRIMARY));
        boolean completed = progress.progress() >= type.needed();
        boolean claimed = progress.progress() < 0;
        if (!completed && !claimed && showDescription && type.lore() != null && !type.lore().isBlank()) {
            for (String line : wrap(type.lore(), 44)) {
                lines.add(new QuestLine(line, WidgetTheme.TEXT_MUTED));
            }
        }
        if (completed) {
            lines.add(new QuestLine("Claim reward", WidgetTheme.TEXT_PRIMARY));
        } else if (!claimed) {
            lines.add(new QuestLine("Progress: " + progress.progress() + "/" + type.needed(), WidgetTheme.TEXT_SOFT));
        }
        return new QuestView(questType, List.copyOf(lines));
    }

    private int getUnscaledWidth(List<QuestView> quests, Minecraft mc) {
        int max = WidgetUtils.showWidgetTitles() ? mc.font.width("Fishing Quests") : 0;
        for (QuestView quest : quests) {
            for (QuestLine line : quest.lines()) {
                max = Math.max(max, ICON_SIZE + ICON_GAP + mc.font.width(line.text()));
            }
        }
        return max + PADDING_X * 2;
    }

    private int getUnscaledHeight(List<QuestView> quests, Minecraft mc) {
        int height = PADDING_Y * 2 + (WidgetUtils.showWidgetTitles() ? mc.font.lineHeight + 3 : 0);
        for (QuestView quest : quests) {
            height += Math.max(ICON_SIZE, quest.lines().size() * (mc.font.lineHeight + 1)) + ROW_GAP;
        }
        return height;
    }

    private static boolean matchesVisibility(FishingWidgetVisibility visibility) {
        Minecraft mc = Minecraft.getInstance();
        return switch (visibility) {
            case ALWAYS -> true;
            case FISHING_WARP -> DiamondWorldProtocolClient.isCurrentFishingLocation();
            case FISHING_ROD -> mc.player != null && mc.player.getMainHandItem().is(Items.FISHING_ROD);
        };
    }

    private static boolean matchesDescription(FishingQuestDescriptionMode mode) {
        Minecraft mc = Minecraft.getInstance();
        return switch (mode) {
            case ALWAYS -> true;
            case FISHING_WARP -> DiamondWorldProtocolClient.isCurrentFishingLocation();
            case FISHING_ROD -> mc.player != null && mc.player.getMainHandItem().is(Items.FISHING_ROD);
        };
    }

    private static String normalizeType(String value) {
        String normalized = value == null ? "NORMAL" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "NETHER", "END" -> normalized;
            default -> "NORMAL";
        };
    }

    private static int typeOrder(String type) {
        return switch (type) {
            case "NORMAL" -> 0;
            case "NETHER" -> 1;
            case "END" -> 2;
            default -> 3;
        };
    }

    private static String formatRemaining(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return hours > 0L
                ? String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    private static List<String> wrap(String text, int maxLength) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (!line.isEmpty() && line.length() + word.length() + 1 > maxLength) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().screen instanceof HudEditingScreen;
    }

    private record QuestView(String type, List<QuestLine> lines) {
    }

    private record QuestLine(String text, int color) {
    }
}
