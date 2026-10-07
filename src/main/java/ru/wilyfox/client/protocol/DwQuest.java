package ru.wilyfox.client.protocol;

/** General quest data from questssetup, separate from expiring hourly fishing quests. */
public record DwQuest(String id, Dimension dimension, Category category, String name,
                      String description, int progress, int required) {
    public enum Dimension { OVERWORLD, NETHER, END }
    public enum Category { HUNTING, FISHING, ALCHEMY }

    public DwQuest withProgress(int value) {
        return new DwQuest(id, dimension, category, name, description, value, required);
    }
}
