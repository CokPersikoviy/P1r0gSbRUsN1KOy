package ru.wilyfox.client.hud.widget;

import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.resource.AccountRewardTimerTracker;

public final class DailyCreditsTimerWidget extends AccountRewardTimerWidget {
    public DailyCreditsTimerWidget(int x, int y, HudLayer layer) {
        super(x, y, layer, "Daily credits", "Daily Credits Timer", AccountRewardTimerTracker.INSTANCE::currentCredits);
    }
}
