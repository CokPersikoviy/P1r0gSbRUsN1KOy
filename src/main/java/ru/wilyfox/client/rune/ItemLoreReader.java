package ru.wilyfox.client.rune;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import ru.wilyfox.utils.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Reads server-authored lore without building a complete client tooltip. */
final class ItemLoreReader {
    private ItemLoreReader() {}

    static List<String> read(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null || lore.lines().isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>(lore.lines().size());
        for (Component component : lore.lines()) {
            String line = Formatting.stripMinecraftFormatting(component.getString()).trim();
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return List.copyOf(lines);
    }
}
