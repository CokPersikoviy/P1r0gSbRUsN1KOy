package ru.wilyfox.client.hud.widget;

import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.resource.AccountRewardTimerTracker;

public final class RouletteTimerWidget extends AccountRewardTimerWidget {
    public RouletteTimerWidget(int x, int y, HudLayer layer) {
        super(x, y, layer, "Roulette", "Roulette Timer", AccountRewardTimerTracker.INSTANCE::current);
    }
}
