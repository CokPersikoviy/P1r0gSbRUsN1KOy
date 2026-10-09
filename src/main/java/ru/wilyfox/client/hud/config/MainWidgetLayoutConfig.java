package ru.wilyfox.client.hud.config;

import java.util.LinkedHashSet;
import java.util.Set;

/** Membership of the main HUD layout; positions and appearance survive removing a widget. */
public final class MainWidgetLayoutConfig {
    public Set<String> widgets = new LinkedHashSet<>();
}
