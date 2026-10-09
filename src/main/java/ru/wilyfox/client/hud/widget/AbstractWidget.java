package ru.wilyfox.client.hud.widget;

import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.hud.indicators.ScreenAnchor;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.WidgetCatalog;

public abstract class AbstractWidget implements Widget {
    protected int startX;
    protected int startY;
    protected int width;
    protected int height;
    protected float scale = 1.0f;
    protected boolean visible;
    protected HudLayer layer;
    protected ScreenAnchor screenAnchor;
    protected String configKey;
    protected String snapTargetKey;
    protected WidgetCorner snapOwnCorner;
    protected WidgetCorner snapTargetCorner;

    protected AbstractWidget(int x, int y, HudLayer l) {
        this.startX = x;
        this.startY = y;
        this.width = 10;
        this.height = 10;
        this.layer = l;
        this.visible = true;
    }

    @Override
    public int getStartX() {
        return startX;
    }

    @Override
    public int getStartY() {
        return startY;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public HudLayer getLayer() {
        return layer;
    }

    @Override
    public boolean isVisible() {
        return visible && isInLayout();
    }

    protected final boolean isInLayout() {
        String key = configKey != null ? configKey : getClass().getSimpleName();
        return WidgetCatalog.find(key) == null || ConfigManager.isWidgetInCurrentLayout(key);
    }

    @Override
    public void setStartX(int startX) {
        this.startX = startX;
    }

    @Override
    public void setStartY(int startY) {
        this.startY = startY;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        float clamped = Math.max(0.50f, Math.min(3.00f, scale));
        this.scale = Math.round(clamped * 100.0f) / 100.0f;
    }

    public void adjustScale(float delta) {
        setScale(this.scale + delta);
    }

    public ScreenAnchor getScreenAnchor() {
        return screenAnchor;
    }

    public void setScreenAnchor(ScreenAnchor screenAnchor) {
        this.screenAnchor = screenAnchor;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getSnapTargetKey() {
        return snapTargetKey;
    }

    public WidgetCorner getSnapOwnCorner() {
        return snapOwnCorner;
    }

    public WidgetCorner getSnapTargetCorner() {
        return snapTargetCorner;
    }

    public boolean hasWidgetSnap() {
        return snapTargetKey != null && !snapTargetKey.isBlank() && snapOwnCorner != null && snapTargetCorner != null;
    }

    public void setWidgetSnap(String snapTargetKey, WidgetCorner snapOwnCorner, WidgetCorner snapTargetCorner) {
        this.snapTargetKey = snapTargetKey;
        this.snapOwnCorner = snapOwnCorner;
        this.snapTargetCorner = snapTargetCorner;
    }

    public void clearWidgetSnap() {
        this.snapTargetKey = null;
        this.snapOwnCorner = null;
        this.snapTargetCorner = null;
    }

}
