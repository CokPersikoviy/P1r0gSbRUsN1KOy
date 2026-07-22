package ru.wilyfox.client.recipe;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PotionRecipeTracker {
    public static final String ALCHEMY_POTION_LIST_TITLE = "\uB109";

    private static final String RECIPE_MARKER = "\u0420\u0435\u0446\u0435\u043f\u0442";
    private static final String MASTERY_MARKER = "\u0412\u0430\u0448 \u0443\u0440\u043e\u0432\u0435\u043d\u044c \u043c\u0430\u0441\u0442\u0435\u0440\u0441\u0442\u0432\u0430";
    private static final Pattern ACTION_PATTERN = Pattern.compile(
            "(\\d+)\u0441\\. - (\\S+) \\\"?([\u0430-\u044F\u0410-\u042F ]+)\\\"?[x ]*(\\d+|)"
    );
    private static final PotionRecipeTracker INSTANCE = new PotionRecipeTracker();

    private String title;
    private List<String> recipeLines = List.of();
    private List<RecipeAction> actions = List.of();
    private long revision;

    private PotionRecipeTracker() {
    }

    public static PotionRecipeTracker getInstance() {
        return INSTANCE;
    }

    public boolean hasRecipe() {
        return title != null && !recipeLines.isEmpty();
    }

    public String getTitle() {
        return title == null ? "" : title;
    }

    public List<String> getRecipeLines() {
        return List.copyOf(recipeLines);
    }

    public List<RecipeAction> getActions() {
        return List.copyOf(actions);
    }

    public long getRevision() {
        return revision;
    }

    public boolean inspect(ItemStack stack, Player player) {
        if (stack == null || stack.isEmpty() || player == null) {
            return false;
        }

        List<Component> tooltip = stack.getTooltipLines(Item.TooltipContext.of(player.level()), player, TooltipFlag.NORMAL);
        List<String> lore = new ArrayList<>(tooltip.size());
        for (Component line : tooltip) {
            lore.add(normalizeLine(line.getString()));
        }

        ParsedRecipe parsed = parseLore(lore);
        if (parsed == null || parsed.displayLines().isEmpty()) {
            return false;
        }

        title = stack.getHoverName().getString().trim();
        recipeLines = parsed.displayLines();
        actions = parsed.actions();
        revision++;
        return true;
    }

    public void clear() {
        title = null;
        recipeLines = List.of();
        actions = List.of();
        revision++;
    }

    static ParsedRecipe parseLore(List<String> sourceLines) {
        if (sourceLines == null || sourceLines.isEmpty()) {
            return null;
        }

        List<String> lines = sourceLines.stream().map(PotionRecipeTracker::normalizeLine).toList();
        int recipeStart = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(RECIPE_MARKER)) {
                recipeStart = i;
                break;
            }
        }
        if (recipeStart < 0) {
            return null;
        }

        int masteryEnd = -1;
        for (int i = lines.size() - 1; i > recipeStart; i--) {
            if (lines.get(i).contains(MASTERY_MARKER)) {
                masteryEnd = i;
                break;
            }
        }
        if (masteryEnd < 0) {
            return null;
        }

        int displayFrom = recipeStart + 1;
        int displayTo = masteryEnd - 1;
        if (displayTo <= displayFrom) {
            return null;
        }

        List<String> displayLines = List.copyOf(lines.subList(displayFrom, displayTo));
        List<String> actionLines = displayLines.size() > 2
                ? displayLines.subList(1, displayLines.size() - 1)
                : List.of();
        return new ParsedRecipe(displayLines, parseActions(actionLines));
    }

    static List<RecipeAction> parseActions(List<String> lines) {
        Map<Integer, RecipeAction> actionsByTime = new LinkedHashMap<>();
        for (String line : lines) {
            Matcher matcher = ACTION_PATTERN.matcher(normalizeLine(line));
            if (!matcher.find()) {
                continue;
            }

            int timingSeconds = Integer.parseInt(matcher.group(1));
            String count = matcher.group(4).isEmpty() ? "1" : matcher.group(4);
            String message = matcher.group(2) + " " + matcher.group(3).trim() + " x" + count;
            actionsByTime.put(timingSeconds, new RecipeAction(timingSeconds, message));
        }
        return List.copyOf(actionsByTime.values());
    }

    private static String normalizeLine(String line) {
        return line == null ? "" : line.replace('\u00A0', ' ').trim();
    }

    public record RecipeAction(int timingSeconds, String message) {
    }

    record ParsedRecipe(List<String> displayLines, List<RecipeAction> actions) {
    }
}
