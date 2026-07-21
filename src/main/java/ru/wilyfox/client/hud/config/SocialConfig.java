package ru.wilyfox.client.hud.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Social/mesh settings: the keepalive "group" state (a leader-led party of max 4 that exchanges frequent
 * status PMs), and whether the Group HUD widget is shown.
 */
public class SocialConfig {
    public boolean widgetActive = true;

    /** NONE | LEADER | MEMBER. */
    public String groupRole = "NONE";
    /** The leader's name (our own name when we are the LEADER; null when not in a group). */
    public String groupLeader = null;
    /** Everyone in the group, including the leader (max {@code SocialGroup.MAX}). */
    public List<String> groupRoster = new ArrayList<>();
}
