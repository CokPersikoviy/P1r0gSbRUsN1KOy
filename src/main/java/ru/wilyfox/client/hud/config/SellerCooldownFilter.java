package ru.wilyfox.client.hud.config;

public enum SellerCooldownFilter {
    ALL("All"),
    READY("Ready"),
    COOLDOWN("On cooldown");

    private final String title;

    SellerCooldownFilter(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public boolean matches(boolean ready) {
        return switch (this) {
            case ALL -> true;
            case READY -> ready;
            case COOLDOWN -> !ready;
        };
    }
}
