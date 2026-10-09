package ru.wilyfox.client.audio;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** One opening/closing cue per visit; resizing a screen does not replay it. */
public abstract class UiSoundScreen extends Screen {
    private boolean soundOpened;
    protected UiSoundScreen(Component title) { super(title); }
    @Override protected void init() {
        super.init();
        if (!soundOpened) {
            soundOpened = true;
            UiSounds.openClose();
        }
    }
    @Override public void removed() {
        if (soundOpened) {
            soundOpened = false;
            UiSounds.cancelScroll();
            UiSounds.openClose();
        }
        super.removed();
    }
}
