package ru.wilyfox.client.recipe;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import ru.wilyfox.utils.Formatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CraftRecipeTracker {
    private static final String START_MARKER_PRIMARY = "Ваши ингридиенты:";
    private static final String START_MARKER_SECONDARY = "Ваши ингредиенты:";
    private static final String END_MARKER = "Нажмите для начала крафта";
    private static final String TIME_MARKER = "Время крафта:";
    private static final long REFRESH_NANOS = 250_000_000L;
    private static final CraftRecipeTracker INSTANCE = new CraftRecipeTracker();

    private String title;
    private String craftTimeLine;
    private List<String> recipeLines = List.of();
    private List<Ingredient> ingredients = List.of();
    private long refreshedAt;
    private ItemStack icon = ItemStack.EMPTY;

    private CraftRecipeTracker() {
    }

    public static CraftRecipeTracker getInstance() {
        return INSTANCE;
    }

    public boolean hasRecipe() {
        return title != null && !recipeLines.isEmpty();
    }

    public String getTitle() {
        return title == null ? "" : title;
    }

    public List<String> getRecipeLines() {
        refreshCounts();
        return List.copyOf(recipeLines);
    }

    public String getCraftTimeLine() {
        return craftTimeLine == null ? "" : craftTimeLine;
    }

    public ItemStack getIcon() {
        return icon.isEmpty() ? ItemStack.EMPTY : icon.copy();
    }

    public void inspect(ItemStack stack, Player player) {
        if (stack == null || stack.isEmpty() || player == null) {
            clear();
            return;
        }

        List<Component> tooltip = stack.getTooltipLines(Item.TooltipContext.of(player.level()), player, TooltipFlag.NORMAL);
        List<String> parsedRecipe = parseRecipeLines(tooltip);

        if (parsedRecipe.isEmpty()) {
            clear();
            return;
        }

        this.title = sanitize(stack.getHoverName().getString());
        this.craftTimeLine = parseCraftTimeLine(tooltip);
        this.recipeLines = parsedRecipe;
        this.icon = stack.copy();
        this.icon.setCount(1);

        // The server bakes "have/need" into the lore once; keep it live via inventory deltas.
        Map<String, Integer> inventory = countInventory(player);
        List<Ingredient> parsedIngredients = new ArrayList<>(parsedRecipe.size());
        for (String line : parsedRecipe) {
            parsedIngredients.add(Ingredient.parse(line, inventory));
        }
        this.ingredients = parsedIngredients;
        this.refreshedAt = System.nanoTime();
    }

    public void clear() {
        this.title = null;
        this.craftTimeLine = null;
        this.recipeLines = List.of();
        this.ingredients = List.of();
        this.icon = ItemStack.EMPTY;
    }

    private void refreshCounts() {
        long now = System.nanoTime();
        if (ingredients.isEmpty() || now - refreshedAt < REFRESH_NANOS) {
            return;
        }
        refreshedAt = now;

        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        Map<String, Integer> inventory = countInventory(player);
        List<String> lines = new ArrayList<>(ingredients.size());
        for (Ingredient ingredient : ingredients) {
            lines.add(ingredient.display(inventory));
        }
        recipeLines = lines;
    }

    private Map<String, Integer> countInventory(Player player) {
        Map<String, Integer> counts = new HashMap<>();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                counts.merge(sanitize(stack.getHoverName().getString()), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    /** One "• Name have/need" lore line; lines without a counter stay static. */
    record Ingredient(String raw, String prefix, String name, long have, long need, int inventoryAtSnapshot) {
        private static final Pattern LINE = Pattern.compile("^([•·*>\\-\\s]*)(\\S.*?)\\s+(\\d+)\\s*/\\s*(\\d+)$");

        static Ingredient parse(String line, Map<String, Integer> inventory) {
            Matcher matcher = LINE.matcher(line);
            if (!matcher.matches()) {
                return new Ingredient(line, "", null, 0, 0, 0);
            }
            try {
                String name = matcher.group(2);
                return new Ingredient(line, matcher.group(1), name, Long.parseLong(matcher.group(3)),
                        Long.parseLong(matcher.group(4)), inventory.getOrDefault(name, 0));
            } catch (NumberFormatException ignored) {
                return new Ingredient(line, "", null, 0, 0, 0);
            }
        }

        String display(Map<String, Integer> inventory) {
            if (name == null) {
                return raw;
            }
            // Server totals may include storage the client can't see, so apply only the inventory delta.
            long current = Math.max(0, have + inventory.getOrDefault(name, 0) - inventoryAtSnapshot);
            return prefix + name + " " + current + "/" + need;
        }
    }

    private List<String> parseRecipeLines(List<Component> tooltip) {
        List<String> result = new ArrayList<>();
        boolean readingRecipe = false;

        for (Component line : tooltip) {
            String text = sanitize(line.getString());

            if (!readingRecipe) {
                if (text.equals(START_MARKER_PRIMARY) || text.equals(START_MARKER_SECONDARY)) {
                    readingRecipe = true;
                }
                continue;
            }

            if (text.startsWith(END_MARKER)) {
                break;
            }

            if (!text.isEmpty()) {
                result.add(text);
            }
        }

        return result;
    }

    private String parseCraftTimeLine(List<Component> tooltip) {
        for (Component line : tooltip) {
            String text = sanitize(line.getString());
            if (text.startsWith(TIME_MARKER)) {
                return text;
            }
        }

        return "";
    }

    private String sanitize(String text) {
        return Formatting.stripMinecraftFormatting(text)
                .replace('\u00A0', ' ')
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
