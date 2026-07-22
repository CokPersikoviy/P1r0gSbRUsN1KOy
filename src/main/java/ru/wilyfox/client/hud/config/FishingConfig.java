package ru.wilyfox.client.hud.config;

public class FishingConfig {
    public boolean showFishingMarkers = true;
    public boolean showFishingNibblesWidget = false;
    public FishingWidgetVisibility nibblesVisibility = FishingWidgetVisibility.FISHING_WARP;
    public FishingNibblesSort nibblesSort = FishingNibblesSort.DIMENSION;
    public boolean showFishingQuestsWidget = false;
    public FishingWidgetVisibility questsVisibility = FishingWidgetVisibility.FISHING_WARP;
    public FishingQuestTypeFilter questsTypeFilter = FishingQuestTypeFilter.ALL;
    public FishingQuestDescriptionMode questsDescription = FishingQuestDescriptionMode.FISHING_ROD;
    public boolean higherBitingNotification = true;
    public boolean autoFish = false;
    public int autoFishDelayTicks = 1;
}
