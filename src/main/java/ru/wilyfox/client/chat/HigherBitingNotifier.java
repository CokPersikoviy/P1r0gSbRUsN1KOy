package ru.wilyfox.client.chat;

import net.minecraft.network.chat.Component;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.popup.PopUpManager;
import ru.wilyfox.client.popup.PopUpRequest;
import ru.wilyfox.client.popup.PopUpSeverity;
import ru.wilyfox.client.popup.PopUpSource;
import ru.wilyfox.utils.Formatting;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HigherBitingNotifier {
    private static final Pattern MESSAGE = Pattern.compile(
            "^(?:На локации \"([\\S\\s]+)\" повышенный клёв!|Nibble rate has increased in location: ([\\S\\s]+)!)$");

    private HigherBitingNotifier() {
    }

    public static void onIncomingMessage(Component component) {
        if (component == null || !ConfigManager.get().fishing.higherBitingNotification) {
            return;
        }
        String location = higherBitingLocation(component.getString());
        if (location == null) {
            return;
        }
        PopUpManager.getInstance().publish(PopUpRequest.of(
                PopUpSource.FISHING_HIGHER_BITING,
                "Повышенный клёв",
                "На локации " + location,
                PopUpSeverity.INFO
        ));
    }

    static String higherBitingLocation(String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = MESSAGE.matcher(Formatting.stripMinecraftFormatting(message).trim());
        return matcher.matches() ? (matcher.group(1) != null ? matcher.group(1) : matcher.group(2)) : null;
    }
}
