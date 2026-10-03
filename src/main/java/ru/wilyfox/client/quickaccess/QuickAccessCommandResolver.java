package ru.wilyfox.client.quickaccess;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

public final class QuickAccessCommandResolver {
    private QuickAccessCommandResolver() {
    }

    public static String resolveCommand(QuickAccessItemConfig item) {
        if (item == null || item.command == null || item.command.isBlank()) {
            return null;
        }

        String command = item.command.trim();
        if (!command.contains("{player}")) {
            return command;
        }

        String targetName = resolveTargetPlayerName();
        if (targetName == null || targetName.isBlank()) {
            return null;
        }

        return command.replace("{player}", targetName);
    }

    public static boolean requiresTarget(QuickAccessItemConfig item) {
        return item != null
                && ((item.command != null && item.command.contains("{player}"))
                || (item.title != null && item.title.contains("{player}")));
    }

    public static String resolveTargetPlayerName() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.hitResult instanceof EntityHitResult entityHitResult
                && entityHitResult.getEntity() instanceof Player player
                && minecraft.player != null
                && player != minecraft.player) {
            return player.getGameProfile().name();
        }

        return null;
    }

    public static String resolveDisplayTitle(QuickAccessItemConfig item) {
        if (item == null || item.title == null || item.title.isBlank()) {
            return "Action";
        }

        String title = item.title;
        if (!title.contains("{player}")) {
            return title;
        }

        String targetName = resolveTargetPlayerName();
        return targetName == null || targetName.isBlank()
                ? title.replace("{player}", "player")
                : title.replace("{player}", targetName);
    }

    public static void showMissingTargetFeedback() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui != null) {
            minecraft.gui.hud.getChat().addClientSystemMessage(Component.literal("No target player under crosshair."));
        }
    }
}
