package ru.wilyfox.client.profiler;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class ProfilerDebugCommand {
    private static final String COMMAND = "/fhprof";

    private ProfilerDebugCommand() {
    }

    public static boolean handleOutgoingCommand(String rawInput, boolean addToHistory) {
        if (rawInput == null) {
            return false;
        }

        String normalized = rawInput.trim();
        if (!normalized.toLowerCase(Locale.ROOT).startsWith(COMMAND)) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (addToHistory && minecraft.gui != null) {
            minecraft.gui.getChat().addRecentChat(normalized);
        }

        String args = normalized.length() > COMMAND.length()
                ? normalized.substring(COMMAND.length()).trim()
                : "status";

        ModProfiler profiler = ModProfiler.getInstance();
        String command;
        String option;
        int separatorIndex = args.indexOf(' ');
        if (separatorIndex >= 0) {
            command = args.substring(0, separatorIndex).trim().toLowerCase(Locale.ROOT);
            option = args.substring(separatorIndex + 1).trim();
        } else {
            command = args.toLowerCase(Locale.ROOT);
            option = "";
        }

        switch (command) {
            case "", "status" -> showLocalMessage(profiler.buildStatusLine());
            case "start" -> {
                profiler.reset();
                profiler.start();
                showLocalMessage("Profiler started.");
            }
            case "stop" -> {
                profiler.stop();
                showLocalMessage("Profiler stopped.");
            }
            case "reset" -> {
                profiler.reset();
                showLocalMessage("Profiler stats reset.");
            }
            case "report" -> showLines(profiler.buildReportLines(option));
            case "dump", "save" -> dumpReport(profiler, option);
            default -> showLocalMessage("Usage: /fhprof <status|start|stop|reset|report [prefix]|dump [prefix]>");
        }

        return true;
    }

    private static void dumpReport(ModProfiler profiler, String prefixFilter) {
        Minecraft minecraft = Minecraft.getInstance();
        Path outputDirectory = minecraft.gameDirectory.toPath().resolve("froghelper-profiler");
        try {
            Path outputFile = profiler.writeMarkdownReport(outputDirectory, prefixFilter);
            showLocalMessage("Profiler report saved to " + outputFile);
        } catch (IOException exception) {
            showLocalMessage("Failed to save profiler report: " + exception.getMessage());
        }
    }

    private static void showLines(List<String> lines) {
        for (String line : lines) {
            showLocalMessage(line);
        }
    }

    private static void showLocalMessage(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui != null) {
            minecraft.gui.getChat().addMessage(Component.literal("[FH Profiler] " + message));
        }
    }
}
