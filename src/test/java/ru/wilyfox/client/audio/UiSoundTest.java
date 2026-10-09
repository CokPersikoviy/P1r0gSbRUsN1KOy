package ru.wilyfox.client.audio;

import org.junit.jupiter.api.Test;
import ru.wilyfox.client.popup.PopUpSeverity;
import ru.wilyfox.client.popup.PopUpSource;
import static org.junit.jupiter.api.Assertions.*;

class UiSoundTest {
    @Test void specificEventsUseTheirOwnSampleInsteadOfGenericPopup() {
        assertEquals(UiSound.BOSS_RESPAWN, UiSound.forNotification(PopUpSource.BOSS_SPAWN, PopUpSeverity.INFO));
        assertEquals(UiSound.PRIVATE_MESSAGE, UiSound.forNotification(PopUpSource.PRIVATE_MESSAGE, PopUpSeverity.INFO));
        assertEquals(UiSound.DISCOVERY, UiSound.forNotification(PopUpSource.BARREL_FOUND, PopUpSeverity.INFO));
        assertEquals(UiSound.DISCOVERY, UiSound.forNotification(PopUpSource.GOLDEN_CRYSTAL_FOUND, PopUpSeverity.INFO));
        for (String source : new String[]{PopUpSource.ABILITY_READY, PopUpSource.WAND_READY,
                PopUpSource.SELLER_READY, PopUpSource.MINER_RETURNED, PopUpSource.LEVEL_READY,
                PopUpSource.RUNE_SET_READY, PopUpSource.FISHING_HIGHER_BITING}) {
            assertEquals(UiSound.READY, UiSound.forNotification(source, PopUpSeverity.SUCCESS), source);
        }
    }
    @Test void warningAndSuccessFallbacksAlsoHandleUnknownOrMissingSource() {
        assertEquals(UiSound.WARNING, UiSound.forNotification(PopUpSource.ALCHEMY_ACTION, PopUpSeverity.WARNING));
        assertEquals(UiSound.WARNING, UiSound.forNotification("test", PopUpSeverity.ERROR));
        assertEquals(UiSound.WARNING, UiSound.forNotification(PopUpSource.BOOSTER_EXPIRED, PopUpSeverity.INFO));
        assertEquals(UiSound.SUCCESS, UiSound.forNotification(PopUpSource.CHAT_COPY, PopUpSeverity.SUCCESS));
        assertEquals(UiSound.POP_UP, UiSound.forNotification(PopUpSource.GAME_EVENT, PopUpSeverity.INFO));
        assertEquals(UiSound.POP_UP, UiSound.forNotification(null, null));
    }
}
