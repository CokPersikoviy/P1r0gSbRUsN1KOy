package ru.wilyfox.client.utility;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import ru.wilyfox.client.chat.FrogChatProtocol;
import ru.wilyfox.client.keybinds.KeyBinds;
import ru.wilyfox.client.ping.PingMarkerManager;
import ru.wilyfox.client.ping.PingPayload;

public final class MousePingInputHandler {
    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                return;
            }

            while (KeyBinds.PING_MARKER.consumeClick()) {
                if (client.screen != null) {
                    continue;
                }

                PingPayload payload = PingPayload.captureCurrent();
                if (payload != null) {
                    PingMarkerManager.addLocalMarker(payload);
                    FrogChatProtocol.sendPingPayload(payload.toJson());
                }
            }
        });
    }
}
