package ru.wilyfox.client.combo;

import net.minecraft.network.chat.Component;
import ru.wilyfox.utils.Formatting;

import java.util.OptionalLong;

public final class ComboTimerChatTracker {
    private static ComboProgressStore store;

    private ComboTimerChatTracker() {
    }

    public static void bindStore(ComboProgressStore comboProgressStore) {
        store = comboProgressStore;
    }

    public static void onIncomingMessage(Component component) {
        if (component == null || store == null) {
            return;
        }

        String text = Formatting.stripMinecraftFormatting(component.getString())
                .replace('\u00A0', ' ')
                .trim();
        OptionalLong remainingSeconds = ComboTimerMessageParser.parseRemainingSeconds(text);
        remainingSeconds.ifPresent(store::setRemainingSeconds);
    }
}
