package ru.wilyfox.client.chat;

import net.minecraft.network.chat.Component;
import ru.wilyfox.client.clan.PlayerClanStorage;
import ru.wilyfox.client.combo.ComboTimerChatTracker;
import ru.wilyfox.client.profiler.ModProfiler;

import static ru.wilyfox.FrogHelper.LOGGER;

/** Routes one logical chat message through FrogHelper's independent chat consumers. */
public final class IncomingChatHandler {
    private IncomingChatHandler() {
    }

    /**
     * Filtering is applied to native display lines, after all independent logic consumers run.
     */
    public static boolean handle(Component component) {
        ChatTabManager tabManager = ChatTabManager.getInstance();
        if (tabManager.isRebuilding()) {
            return false;
        }

        Component logicalComponent;
        try {
            logicalComponent = ChatMessageSanitizer.forLogic(component);
        } catch (Exception exception) {
            LOGGER.warn("FrogHelper chat sanitizer failed", exception);
            return false;
        }

        run("dispatchQueue", () -> ChatDispatchQueue.handleIncomingMessage(logicalComponent));

        run("boosterDebug", () -> BoosterChatDebug.onIncomingMessage(logicalComponent));
        run("bossAnnouncer", () -> AutoBossAnnouncer.onIncomingMessage(logicalComponent));
        run("privateMessagePopup", () -> PrivateMessagePopUpNotifier.onIncomingMessage(logicalComponent));
        run("visibility", () -> VisibilityStatusTracker.onIncomingMessage(logicalComponent));
        run("comboTimer", () -> ComboTimerChatTracker.onIncomingMessage(logicalComponent));
        run("activeEffects", () -> ActiveEffectChatTracker.onIncomingMessage(logicalComponent));
        run("playerClan", () -> PlayerClanStorage.captureFromChat(logicalComponent));

        return false;
    }

    private static void run(String route, Runnable action) {
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("chat/" + route)) {
            action.run();
        } catch (Exception exception) {
            LOGGER.warn("FrogHelper chat route '{}' failed", route, exception);
        }
    }

}
