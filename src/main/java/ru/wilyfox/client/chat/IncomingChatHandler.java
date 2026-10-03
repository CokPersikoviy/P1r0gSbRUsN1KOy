package ru.wilyfox.client.chat;

import net.minecraft.network.chat.Component;
import ru.wilyfox.client.clan.PlayerClanStorage;
import ru.wilyfox.client.combo.ComboTimerChatTracker;
import ru.wilyfox.client.profiler.ModProfiler;

import java.util.function.BooleanSupplier;

import static ru.wilyfox.FrogHelper.LOGGER;

/** Routes one logical chat message through FrogHelper's independent chat consumers. */
public final class IncomingChatHandler {
    private IncomingChatHandler() {
    }

    /**
     * @return {@code true} when the line is an internal protocol message or is filtered by the active tab.
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

        // Service messages must be consumed before ordinary chat consumers publish popups
        // or interpret their encoded text as a player message.
        if (test("bossShare", () -> BossShareService.handleIncomingShare(logicalComponent))) {
            return true;
        }

        run("boosterDebug", () -> BoosterChatDebug.onIncomingMessage(logicalComponent));
        run("autoThanks", () -> AutoThanks.onIncomingMessage(logicalComponent));
        run("bossAnnouncer", () -> AutoBossAnnouncer.onIncomingMessage(logicalComponent));
        run("privateMessagePopup", () -> PrivateMessagePopUpNotifier.onIncomingMessage(logicalComponent));
        run("visibility", () -> VisibilityStatusTracker.onIncomingMessage(logicalComponent));
        run("comboTimer", () -> ComboTimerChatTracker.onIncomingMessage(logicalComponent));
        run("activeEffects", () -> ActiveEffectChatTracker.onIncomingMessage(logicalComponent));
        run("playerClan", () -> PlayerClanStorage.captureFromChat(logicalComponent));

        run("tabCapture", () -> tabManager.captureIncoming(logicalComponent));
        return !test("tabVisibility", () -> tabManager.shouldDisplayInActiveTab(logicalComponent), true);
    }

    private static void run(String route, Runnable action) {
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("chat/" + route)) {
            action.run();
        } catch (Exception exception) {
            LOGGER.warn("FrogHelper chat route '{}' failed", route, exception);
        }
    }

    private static boolean test(String route, BooleanSupplier action) {
        return test(route, action, false);
    }

    private static boolean test(String route, BooleanSupplier action, boolean fallback) {
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("chat/" + route)) {
            return action.getAsBoolean();
        } catch (Exception exception) {
            LOGGER.warn("FrogHelper chat route '{}' failed", route, exception);
            return fallback;
        }
    }
}
