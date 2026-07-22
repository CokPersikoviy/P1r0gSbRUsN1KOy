package ru.wilyfox.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.clan.PlayerClanNameFormatter;
import ru.wilyfox.client.moduser.ModUserBadge;
import ru.wilyfox.client.moduser.ModUserStorage;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import ru.wilyfox.client.target.TargetListStore;
import ru.wilyfox.client.potion.PotionStore;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.utils.Formatting;

import java.util.List;
import java.util.Locale;

import static ru.wilyfox.FrogHelper.LOGGER;
import static ru.wilyfox.client.debug.DebugLogger.info;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    private static String froghelper$lastBoosterTabLog = "";

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private Component footer;

    @Shadow
    private Component header;

    @Shadow
    protected abstract List<PlayerInfo> getPlayerInfos();

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void froghelper$highlightTargetTabName(PlayerInfo playerInfo, CallbackInfoReturnable<Component> cir) {
        if (playerInfo == null) {
            return;
        }

        String name = playerInfo.getProfile().getName();
        Component base = cir.getReturnValue();
        if (base == null) {
            base = Component.literal(name);
        }

        base = PlayerClanNameFormatter.apply(base, name);

        if (TargetListStore.isTarget(name)) {
            base = base.copy().withStyle(style -> style.withColor(0xFF3030).withBold(true));
        }

        if (ConfigManager.get().render.modUserBadge && ModUserStorage.isKnown(name)) {
            base = ModUserBadge.prefix(base);
        }

        cir.setReturnValue(base);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void froghelper$renderCurrentServer(GuiGraphics context, int screenWidth, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
        froghelper$logBoosterTabText();

        Font font = minecraft.font;
        int y = froghelper$getTabBottom(screenWidth, font) + 4;
        y = froghelper$renderActivePotions(context, screenWidth, font, y);

        if (!ConfigManager.get().render.showCurrentServerInTab) {
            return;
        }

        String serverName = DiamondWorldProtocolClient.getCurrentServerDisplayName(footer);
        if (serverName == null || serverName.isBlank()) {
            return;
        }

        int textWidth = font.width(serverName);
        int x = (screenWidth - textWidth) / 2;

        int padding = 4;
        context.fill(x - padding, y - 2, x + textWidth + padding, y + 9, 0x78111111);
        context.drawString(font, serverName, x, y, 0xFFD8D8D8, false);
    }

    private int froghelper$renderActivePotions(GuiGraphics context, int screenWidth, Font font, int y) {
        Client client = Client.getInstance();
        if (!ConfigManager.get().alchemy.potionsInTab || client == null) {
            return y;
        }

        List<PotionStore.ActivePotionEntry> potions = client.getPotionStore().getCurrentlyActiveEntries();
        if (potions.isEmpty()) {
            return y;
        }

        String title = "\u0410\u043a\u0442\u0438\u0432\u043d\u044b\u0435 \u0437\u0435\u043b\u044c\u044f";
        List<String> lines = potions.stream()
                .map(potion -> potion.name() + " (" + potion.quality() + "%) - "
                        + froghelper$formatRemaining(potion.remainingMillis()))
                .toList();

        int width = font.width(title);
        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }
        width += 12;
        int height = 7 + (lines.size() + 1) * 10;
        int x = (screenWidth - width) / 2;

        context.fill(x, y - 2, x + width, y + height, 0xB0111111);
        context.drawCenteredString(font, title, screenWidth / 2, y, WidgetTheme.TITLE);
        int textY = y + 10;
        for (String line : lines) {
            context.drawCenteredString(font, line, screenWidth / 2, textY, WidgetTheme.TEXT_SOFT);
            textY += 10;
        }
        return y + height + 4;
    }

    private static String froghelper$formatRemaining(long remainingMillis) {
        long totalSeconds = Math.max(0L, remainingMillis) / 1_000L;
        long hours = totalSeconds / 3_600L;
        long minutes = totalSeconds % 3_600L / 60L;
        long seconds = totalSeconds % 60L;
        return hours > 0L
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    private int froghelper$getTabBottom(int screenWidth, Font font) {
        int y = 10;

        if (header != null) {
            y += font.split(header, Math.max(120, screenWidth - 50)).size() * 9 + 1;
        }

        List<PlayerInfo> infos = getPlayerInfos();
        int totalPlayers = infos.size();
        int rows = totalPlayers;
        int columns = 1;

        while (rows > 20) {
            columns++;
            rows = (totalPlayers + columns - 1) / columns;
        }

        y += rows * 9 + 1;

        if (footer != null) {
            y += font.split(footer, Math.max(120, screenWidth - 50)).size() * 9 + 1;
        }

        return y;
    }

    private void froghelper$logBoosterTabText() {
        String headerText = header != null ? Formatting.stripMinecraftFormatting(header.getString()) : "";
        String footerText = footer != null ? Formatting.stripMinecraftFormatting(footer.getString()) : "";
        String snapshot = "header=" + headerText + " | footer=" + footerText;
        String normalized = snapshot.toLowerCase(Locale.ROOT);

        if (!froghelper$looksLikeBoosterText(normalized) || snapshot.equals(froghelper$lastBoosterTabLog)) {
            return;
        }

        froghelper$lastBoosterTabLog = snapshot;
        info(LOGGER, "Booster debug: tab={}", snapshot);
    }

    private static boolean froghelper$looksLikeBoosterText(String text) {
        return text.contains("x2")
                || text.contains("x3")
                || text.contains("x4")
                || text.contains("x5")
                || text.contains("boost")
                || text.contains("money")
                || text.contains("shard");
    }
}
