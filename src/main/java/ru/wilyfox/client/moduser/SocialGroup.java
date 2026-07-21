package ru.wilyfox.client.moduser;

import net.minecraft.client.Minecraft;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.SocialConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * The keepalive party state: a leader-led group of up to {@link #MAX} players (leader + members) who
 * exchange frequent status PMs and appear in the Group HUD widget. Backed by {@code ConfigManager.get().social}.
 * The leader owns the roster (invite/kick/disband); members can only leave. Membership is enforced by the
 * sync protocol in {@link ModUserProtocol} (an invited player who is already in a group refuses).
 */
public final class SocialGroup {
    public static final int MAX = 4;

    public enum Role {NONE, LEADER, MEMBER}

    private SocialGroup() {
    }

    private static SocialConfig cfg() {
        return ConfigManager.get().social;
    }

    public static Role role() {
        try {
            return Role.valueOf(cfg().groupRole);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return Role.NONE;
        }
    }

    public static boolean isLeader() {
        return role() == Role.LEADER;
    }

    public static boolean isMember() {
        return role() == Role.MEMBER;
    }

    public static boolean isInGroup() {
        return role() != Role.NONE && !roster().isEmpty();
    }

    public static String leader() {
        return cfg().groupLeader;
    }

    public static List<String> roster() {
        return cfg().groupRoster;
    }

    public static boolean inRoster(String name) {
        return containsCi(roster(), name);
    }

    public static boolean isFull() {
        return roster().size() >= MAX;
    }

    /** Everyone in the group except us — the keepalive targets. */
    public static List<String> keepaliveTargets() {
        List<String> targets = new ArrayList<>();
        for (String member : roster()) {
            if (!isSelf(member)) {
                targets.add(member);
            }
        }
        return targets;
    }

    public static void becomeLeader() {
        SocialConfig config = cfg();
        config.groupRole = "LEADER";
        config.groupLeader = myName();
        config.groupRoster = new ArrayList<>();
        ensurePresent(config.groupRoster, myName());
        ConfigManager.save();
    }

    public static void joinAsMember(String leader, List<String> roster) {
        SocialConfig config = cfg();
        config.groupRole = "MEMBER";
        config.groupLeader = leader;
        config.groupRoster = new ArrayList<>(roster);
        ensurePresent(config.groupRoster, leader);
        ensurePresent(config.groupRoster, myName());
        ConfigManager.save();
    }

    public static void setRoster(List<String> roster) {
        SocialConfig config = cfg();
        config.groupRoster = new ArrayList<>(roster);
        ensurePresent(config.groupRoster, config.groupLeader);
        ensurePresent(config.groupRoster, myName());
        ConfigManager.save();
    }

    public static void addToRoster(String name) {
        if (name == null || inRoster(name) || isFull()) {
            return;
        }
        roster().add(name.trim());
        ConfigManager.save();
    }

    public static void removeFromRoster(String name) {
        if (roster().removeIf(member -> member.equalsIgnoreCase(name))) {
            ConfigManager.save();
        }
    }

    public static void clear() {
        SocialConfig config = cfg();
        config.groupRole = "NONE";
        config.groupLeader = null;
        config.groupRoster = new ArrayList<>();
        ConfigManager.save();
    }

    public static boolean isSelf(String name) {
        return name != null && name.equalsIgnoreCase(myName());
    }

    public static String myName() {
        var player = Minecraft.getInstance().player;
        return player == null ? null : player.getGameProfile().getName();
    }

    private static void ensurePresent(List<String> list, String name) {
        if (name != null && !name.isBlank() && !containsCi(list, name)) {
            list.add(name.trim());
        }
    }

    private static boolean containsCi(List<String> list, String name) {
        if (name == null) {
            return false;
        }
        for (String item : list) {
            if (item.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
}
