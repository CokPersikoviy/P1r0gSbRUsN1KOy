package ru.wilyfox.client.rune;

final class RuneLore {
    private RuneLore() {
    }

    static boolean isActiveSet(String line) {
        return line != null && (line.contains("Используется") || line.contains("Used"));
    }

    static boolean canUseSet(String line) {
        return line != null && (line.contains("Нажмите, чтобы использовать") || line.contains("Click to use"));
    }
}
