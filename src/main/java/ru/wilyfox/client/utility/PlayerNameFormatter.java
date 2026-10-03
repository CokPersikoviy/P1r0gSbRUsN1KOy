package ru.wilyfox.client.utility;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.clan.PlayerClanNameFormatter;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.RenderConfig;
import ru.wilyfox.client.moduser.ModUserBadge;
import ru.wilyfox.client.moduser.ModUserStorage;

/** Keeps TAB and world nametags consistent without changing player identity. */
public final class PlayerNameFormatter {
    private PlayerNameFormatter() {}

    public static Component apply(Component serverName, String playerName) {
        RenderConfig config = ConfigManager.get().render;
        Component result = baseName(serverName, playerName, config.cleanPlayerNames);
        if (config.cleanPlayerNames) {
            return result;
        }

        result = PlayerClanNameFormatter.apply(result, playerName);
        if (config.modUserBadge && ModUserStorage.isKnown(playerName)) {
            result = ModUserBadge.prefix(result);
        }
        return result;
    }

    static Component baseName(Component serverName, String playerName, boolean clean) {
        if (clean) {
            return Component.literal(playerName).withStyle(ChatFormatting.WHITE);
        }
        return serverName != null ? serverName : Component.literal(playerName);
    }
}
