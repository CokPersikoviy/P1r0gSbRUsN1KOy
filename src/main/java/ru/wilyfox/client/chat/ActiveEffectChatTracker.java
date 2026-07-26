package ru.wilyfox.client.chat;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.effect.ActiveEffectMessageParser;
import ru.wilyfox.client.effect.ActiveEffectStore;

import java.util.Objects;

public final class ActiveEffectChatTracker {
    private static ActiveEffectStore store;

    private ActiveEffectChatTracker() {
    }

    public static void register(ActiveEffectStore activeEffectStore) {
        store = Objects.requireNonNull(activeEffectStore, "activeEffectStore");
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> store.clear());
    }

    public static void onIncomingMessage(Component component) {
        ActiveEffectStore target = store;
        if (target == null || component == null) {
            return;
        }

        ActiveEffectMessageParser.parse(component.getString()).ifPresent(effect ->
                target.activate(
                        effect.id(),
                        effect.displayName(),
                        effect.kind(),
                        effect.durationMillis()
                )
        );
    }
}
