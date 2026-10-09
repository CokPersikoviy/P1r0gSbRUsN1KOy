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
            minecraft.gui.hud.getChat().addRecentChat(normalized);
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
            case "", "status" -> {
                showLocalMessage(profiler.buildStatusLine());
                showLocalMessage(profiler.crashSaveStatus());
            }
            case "start" -> {
                profiler.reset();
                profiler.start();
                showLocalMessage("Profiler started. Long-running diagnostics retained.");
                showLocalMessage("Automatic crash checkpoints: " + profiler.crashSaveStatus());
            }
            case "stop" -> {
                profiler.stop();
                showLocalMessage(profiler.isEnabled() ? "Manual profiling stopped; automatic capture continues." : ru.wilyfox.client.hud.config.ConfigManager.get().render.debug
                        ? "Profiler stopped; Debug memory sampling continues." : "Profiler stopped.");
            }
            case "reset" -> {
                profiler.reset();
                showLocalMessage("Profiler session stats reset. Long-running diagnostics retained.");
            }
            case "report" -> showLines(profiler.buildReportLines(option));
            case "dump", "save" -> dumpReport(profiler, option);
            case "dump-full" -> dumpFullReport(profiler, option);
            default -> showLocalMessage("Usage: /fhprof <status|start|stop|reset|report [prefix]|dump [prefix]|dump-full [prefix]>");
        }

        return true;
    }

    private static void dumpReport(ModProfiler profiler, String prefixFilter) {
        dumpReport(profiler, prefixFilter, false);
    }

    private static void dumpFullReport(ModProfiler profiler, String prefixFilter) {
        showLocalMessage("Full dump collects a heap histogram and can temporarily freeze the client.");
        dumpReport(profiler, prefixFilter, true);
    }

    private static void dumpReport(ModProfiler profiler, String prefixFilter, boolean includeHistogram) {
        Minecraft minecraft = Minecraft.getInstance();
        Path outputDirectory = minecraft.gameDirectory.toPath().resolve("froghelper-profiler");
        try {
            Path outputFile = profiler.writeMarkdownReport(outputDirectory, prefixFilter, includeHistogram);
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
            minecraft.gui.hud.getChat().addClientSystemMessage(Component.literal("[FH Profiler] " + message));
        }
    }
}
