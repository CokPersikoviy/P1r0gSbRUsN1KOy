package ru.wilyfox.client.clan;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import ru.wilyfox.client.hud.widget.WidgetTheme;

public final class PlayerClanNameFormatter {
    private PlayerClanNameFormatter() {
    }

    public static Component apply(Component baseComponent, String playerName) {
        String clanName = PlayerClanStorage.getClan(playerName);
        if (clanName == null || clanName.isBlank()) {
            return baseComponent;
        }

        MutableComponent result = Component.empty();
        result.append(Component.literal("[" + clanName + "] ").withColor(WidgetTheme.TEXT_ACCENT));
        result.append(baseComponent != null ? baseComponent.copy() : Component.literal(playerName));
        return result;
    }
}
