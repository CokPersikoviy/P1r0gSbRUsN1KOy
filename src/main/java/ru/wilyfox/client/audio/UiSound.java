package ru.wilyfox.client.audio;

import ru.wilyfox.client.popup.PopUpSeverity;
import ru.wilyfox.client.popup.PopUpSource;

/** Prepared samples, shared by every mod UI and notification. */
public enum UiSound {
    SCROLL("scroll"), CLICK("click"), TOGGLE("toggle"), OPEN_CLOSE("open_close"),
    POP_UP("pop_up"), SUCCESS("success"), WARNING("warning"), READY("ready"),
    DISCOVERY("discovery"), PRIVATE_MESSAGE("private_message"), BOSS_RESPAWN("boss_respawn");

    private final String sample;
    UiSound(String sample) { this.sample = sample; }
    public String eventId() { return "froghelper:ui." + sample; }
    public String resourceId() { return "froghelper:sounds/ui/" + sample + ".ogg"; }

    public static UiSound forNotification(String source, PopUpSeverity severity) {
        if (severity == PopUpSeverity.ERROR || severity == PopUpSeverity.WARNING) return WARNING;
        if (source != null) {
            switch (source) {
                case PopUpSource.PRIVATE_MESSAGE: return PRIVATE_MESSAGE;
                case PopUpSource.BOSS_SPAWN: return BOSS_RESPAWN;
                case PopUpSource.BARREL_FOUND, PopUpSource.GOLDEN_CRYSTAL_FOUND: return DISCOVERY;
                case PopUpSource.ABILITY_READY, PopUpSource.WAND_READY, PopUpSource.SELLER_READY,
                     PopUpSource.MINER_RETURNED, PopUpSource.LEVEL_READY, PopUpSource.RUNE_SET_READY,
                     PopUpSource.FISHING_HIGHER_BITING: return READY;
                case PopUpSource.POTION_EXPIRED, PopUpSource.BOOSTER_EXPIRED: return WARNING;
                default: break;
            }
        }
        return severity == PopUpSeverity.SUCCESS ? SUCCESS : POP_UP;
    }
}
