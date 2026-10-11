package ru.wilyfox.client.rune;

import java.util.Locale;
import ru.wilyfox.utils.Formatting;

final class RuneLore {
    private RuneLore() {
    }

    static boolean isActiveSet(String line) {
        return line != null && (line.contains("Используется") || line.contains("Used"));
    }

    static boolean canUseSet(String line) {
        return line != null && (line.contains("Нажмите, чтобы использовать") || line.contains("Click to use"));
    }

    /** Slot controls can also be paper items, just like equipped runes. */
    static boolean isSlotPlaceholder(String name) {
        if (name == null) return true;
        String text = Formatting.stripMinecraftFormatting(name).replace('\u00a0', ' ').strip().toLowerCase(Locale.ROOT);
        return text.isEmpty() || text.startsWith("разблокировать") || text.startsWith("unlock")
                || text.startsWith("нажмите") || text.startsWith("click ")
                || text.startsWith("пустой слот") || text.startsWith("пустая ячейка")
                || text.startsWith("заблокирован") || text.startsWith("empty slot") || text.startsWith("locked slot")
                || text.startsWith("слот для") || text.startsWith("slot for")
                || text.startsWith("поместите") || text.startsWith("place ");
    }
}
