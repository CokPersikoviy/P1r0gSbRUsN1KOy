package ru.wilyfox.bridge;

/** The native chat editor may be hosted inside a detached FH window. */
public interface ChatInputHost {
    String froghelper$inlineInputKey();
    void froghelper$focusInput(String key);
}
