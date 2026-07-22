package ru.wilyfox.client.protocol;

public record DwClanSiegePosition(int x, int y) {
    public static final DwClanSiegePosition UNAVAILABLE = new DwClanSiegePosition(-1, -1);

    public boolean isAvailable() {
        return x != -1 && y != -1;
    }
}
