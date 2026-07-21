package ru.wilyfox.client.hud.config;

import ru.wilyfox.client.hud.indicators.ScreenAnchor;
import ru.wilyfox.client.hud.widget.WidgetCorner;

public class WidgetLayoutConfig {
    public Integer x;
    public Integer y;
    // Resolution-independent position: fraction of the gui-scaled screen (0..1). The source of truth
    // for free widgets — x/y are kept only for reference and legacy migration. Anchored/snapped
    // widgets ignore these (they resolve from their anchor/target instead).
    public Double xFraction;
    public Double yFraction;
    public Float scale;
    public ScreenAnchor anchor;
    public String snapTarget;
    public WidgetCorner snapOwnCorner;
    public WidgetCorner snapTargetCorner;
    public Boolean hiddenInGameplay;
}
