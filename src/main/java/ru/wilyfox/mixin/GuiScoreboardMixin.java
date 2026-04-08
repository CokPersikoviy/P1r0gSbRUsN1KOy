package ru.wilyfox.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.bridge.ScoreboardSidebarAccessor;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.utils.Formatting;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static ru.wilyfox.FrogHelper.LOGGER;
import static ru.wilyfox.client.debug.DebugLogger.info;

@Mixin(Gui.class)
public abstract class GuiScoreboardMixin implements ScoreboardSidebarAccessor {
    private static final Comparator<PlayerScoreEntry> SCORE_ORDER = Comparator
            .comparingInt(PlayerScoreEntry::value)
            .reversed()
            .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
    private static String froghelper$lastBoosterScoreboardLog = "";

    @Shadow
    private Minecraft minecraft;

    @Invoker("displayScoreboardSidebar")
    protected abstract void froghelper$invokeDisplayScoreboardSidebar(GuiGraphics guiGraphics, Objective objective);

    @Shadow
    public abstract Font getFont();

    @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void froghelper$cancelVanillaScoreboard(GuiGraphics guiGraphics, net.minecraft.client.DeltaTracker tickCounter, CallbackInfo ci) {
        froghelper$logBoosterLines();

        if (ConfigManager.get().scoreboard.active) {
            ci.cancel();
        }
    }

    @Override
    public void froghelper$renderAt(GuiGraphics context, int startX, int startY) {
        Objective objective = froghelper$getSidebarObjective();
        if (objective == null) {
            return;
        }

        int defaultX = froghelper$getDefaultX();
        int defaultY = froghelper$getDefaultY();

        context.pose().pushPose();
        context.pose().translate(startX - defaultX, startY - defaultY, 0);
        froghelper$invokeDisplayScoreboardSidebar(context, objective);
        context.pose().popPose();
    }

    @Override
    public int froghelper$getRenderedWidth() {
        Objective objective = froghelper$getSidebarObjective();
        if (objective == null) {
            return 0;
        }

        return froghelper$getLayout(objective).width();
    }

    @Override
    public int froghelper$getRenderedHeight() {
        Objective objective = froghelper$getSidebarObjective();
        if (objective == null) {
            return 0;
        }

        return froghelper$getLayout(objective).height();
    }

    @Override
    public int froghelper$getDefaultX() {
        Objective objective = froghelper$getSidebarObjective();
        if (objective == null) {
            return 0;
        }

        ScoreboardLayout layout = froghelper$getLayout(objective);
        return minecraft.getWindow().getGuiScaledWidth() - layout.width() - 1;
    }

    @Override
    public int froghelper$getDefaultY() {
        Objective objective = froghelper$getSidebarObjective();
        if (objective == null) {
            return 0;
        }

        ScoreboardLayout layout = froghelper$getLayout(objective);
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int totalEntriesHeight = layout.entryCount() * 9;
        int bottom = screenHeight / 2 + totalEntriesHeight / 3;

        return bottom - totalEntriesHeight - 10;
    }

    private Objective froghelper$getSidebarObjective() {
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }

        Scoreboard scoreboard = minecraft.level.getScoreboard();
        Objective objective = null;

        PlayerTeam playerTeam = scoreboard.getPlayersTeam(minecraft.player.getScoreboardName());
        if (playerTeam != null) {
            DisplaySlot teamSlot = DisplaySlot.teamColorToSlot(playerTeam.getColor());
            if (teamSlot != null) {
                objective = scoreboard.getDisplayObjective(teamSlot);
            }
        }

        if (objective != null) {
            return objective;
        }

        return scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
    }

    private ScoreboardLayout froghelper$getLayout(Objective objective) {
        Font font = getFont();
        List<PlayerScoreEntry> entries = froghelper$getEntries(objective);

        int titleWidth = font.width(objective.getDisplayName());
        int colonWidth = font.width(":");
        int maxWidth = titleWidth;

        for (PlayerScoreEntry entry : entries) {
            Component name = froghelper$getNameComponent(objective.getScoreboard(), entry);
            Component score = entry.formatValue(objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT));
            int scoreWidth = font.width(score);
            int rowWidth = font.width(name) + (scoreWidth > 0 ? colonWidth + scoreWidth : 0);
            maxWidth = Math.max(maxWidth, rowWidth);
        }

        return new ScoreboardLayout(maxWidth + 4, entries.size() * 9 + 10, entries.size());
    }

    private List<PlayerScoreEntry> froghelper$getEntries(Objective objective) {
        return objective.getScoreboard().listPlayerScores(objective).stream()
                .filter(entry -> !entry.isHidden())
                .sorted(SCORE_ORDER)
                .limit(15)
                .toList();
    }

    private Component froghelper$getNameComponent(Scoreboard scoreboard, PlayerScoreEntry entry) {
        PlayerTeam playerTeam = scoreboard.getPlayersTeam(entry.owner());
        return PlayerTeam.formatNameForTeam(playerTeam, entry.ownerName());
    }

    private void froghelper$logBoosterLines() {
        Objective objective = froghelper$getSidebarObjective();
        if (objective == null) {
            return;
        }

        List<String> lines = froghelper$getEntries(objective).stream()
                .map(entry -> Formatting.stripMinecraftFormatting(froghelper$getNameComponent(objective.getScoreboard(), entry).getString()))
                .filter(line -> !line.isBlank())
                .toList();

        String title = Formatting.stripMinecraftFormatting(objective.getDisplayName().getString());
        String snapshot = title + " | " + String.join(" | ", lines);
        String normalized = snapshot.toLowerCase(Locale.ROOT);

        if (!froghelper$looksLikeBoosterText(normalized) || snapshot.equals(froghelper$lastBoosterScoreboardLog)) {
            return;
        }

        froghelper$lastBoosterScoreboardLog = snapshot;
        info(LOGGER, "Booster debug: scoreboard={}", snapshot);
    }

    private static boolean froghelper$looksLikeBoosterText(String text) {
        return text.contains("буст")
                || text.contains("деньг")
                || text.contains("монет")
                || text.contains("шард")
                || text.contains("x2")
                || text.contains("x3")
                || text.contains("x4")
                || text.contains("x5")
                || text.contains("х2")
                || text.contains("х3")
                || text.contains("х4")
                || text.contains("х5");
    }

    private record ScoreboardLayout(int width, int height, int entryCount) {
    }
}
