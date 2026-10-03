package ru.wilyfox.client.rune;

import java.util.List;

public class ActiveRunesStore {
    private List<String> runes = List.of();

    public void replace(List<String> updatedRunes) {
        this.runes = List.copyOf(updatedRunes);
    }

    public List<String> getAll() {
        return runes;
    }

    public boolean isEmpty() {
        return runes.isEmpty();
    }

    public void clear() {
        runes = List.of();
    }
}
