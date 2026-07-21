package ru.wilyfox.client.rune;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.RunesBagConfig;
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.utils.Formatting;

import java.util.ArrayList;
import java.util.List;

public final class RuneSetSwitcher {
    private static final List<Integer> RUNE_SET_SLOTS = List.of(0, 1, 3, 4, 5, 6, 8);
    private static final String ACTIVE_SET_MARKER = "\u0418\u0441\u043f\u043e\u043b\u044c\u0437\u0443\u0435\u0442\u0441\u044f";
    private static final String CLICK_TO_USE_MARKER = "\u041d\u0430\u0436\u043c\u0438\u0442\u0435, \u0447\u0442\u043e\u0431\u044b \u0438\u0441\u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u044c";

    private RuneSetSwitcher() {
    }

    public static void register() {
    }

    public static boolean handleScreenKeyPressed(Component title, AbstractContainerMenu menu, int keyCode, int scanCode) {
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("ui/RuneSetSwitcher/handleScreenKeyPressed")) {
            if (!RuneSetEffectOverlay.isRuneBagScreen(title)) {
                return false;
            }

            int directIndex = matchDirectSelection(keyCode, scanCode);
            if (directIndex >= 0) {
                return switchToSet(Minecraft.getInstance(), menu, directIndex);
            }

            Minecraft client = Minecraft.getInstance();

            if (matches(client.options.keyLeft, keyCode, scanCode)) {
                return moveSelection(Minecraft.getInstance(), menu, -1);
            }

            if (matches(client.options.keyRight, keyCode, scanCode)) {
                return moveSelection(Minecraft.getInstance(), menu, 1);
            }

            return false;
        }
    }

    /** Mouse-button selectors (mouse4 / mouse5 / middle) from the same "Runes Bag Keybinds" binds. */
    public static boolean handleScreenMouseClicked(Component title, AbstractContainerMenu menu, int button) {
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("ui/RuneSetSwitcher/handleScreenMouseClicked")) {
            if (!RuneSetEffectOverlay.isRuneBagScreen(title)) {
                return false;
            }

            int directIndex = matchDirectSelectionMouse(button);
            if (directIndex >= 0) {
                return switchToSet(Minecraft.getInstance(), menu, directIndex);
            }

            return false;
        }
    }

    private static boolean matches(KeyMapping mapping, int keyCode, int scanCode) {
        return mapping.matches(keyCode, scanCode);
    }

    private static int matchDirectSelection(int keyCode, int scanCode) {
        // Custom keys from the "Runes Bag Keybinds" settings tab (default 1-7). Read only here, inside
        // the rune-bag screen, so they never collide with the vanilla hotbar keys during gameplay. A raw
        // keyCode is always < MOUSE_CODE_OFFSET, so it never matches a slot bound to a mouse button.
        int[] keys = ConfigManager.get().runesBag.setSelectorKeys;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] != GLFW.GLFW_KEY_UNKNOWN && keys[i] == keyCode) {
                return i;
            }
        }
        return -1;
    }

    private static int matchDirectSelectionMouse(int button) {
        int code = RunesBagConfig.MOUSE_CODE_OFFSET + button;
        int[] keys = ConfigManager.get().runesBag.setSelectorKeys;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == code) {
                return i;
            }
        }
        return -1;
    }

    private static boolean moveSelection(Minecraft client, AbstractContainerMenu menu, int direction) {
        if (menu == null) {
            return false;
        }

        int current = findSelectedSetIndex(menu);
        if (current < 0) {
            current = 0;
        }

        int target = current + direction;
        if (target < 0) {
            target = RUNE_SET_SLOTS.size() - 1;
        } else if (target >= RUNE_SET_SLOTS.size()) {
            target = 0;
        }

        return switchToSet(client, menu, target);
    }

    private static boolean switchToSet(Minecraft client, AbstractContainerMenu menu, int targetIndex) {
        MultiPlayerGameMode gameMode = client.gameMode;
        if (menu == null || gameMode == null || client.player == null) {
            return false;
        }

        if (targetIndex < 0 || targetIndex >= RUNE_SET_SLOTS.size()) {
            return false;
        }

        int slotIndex = RUNE_SET_SLOTS.get(targetIndex);
        if (slotIndex < 0 || slotIndex >= menu.slots.size()) {
            return false;
        }

        Slot slot = menu.getSlot(slotIndex);
        if (!slot.hasItem()) {
            return false;
        }

        String firstLoreLine = getFirstLoreLine(slot.getItem());
        if (firstLoreLine == null || !firstLoreLine.contains(CLICK_TO_USE_MARKER)) {
            return false;
        }

        gameMode.handleInventoryMouseClick(menu.containerId, slotIndex, 0, ClickType.PICKUP, client.player);
        return true;
    }

    private static int findSelectedSetIndex(AbstractContainerMenu menu) {
        for (int i = 0; i < RUNE_SET_SLOTS.size(); i++) {
            int slotIndex = RUNE_SET_SLOTS.get(i);
            if (slotIndex < 0 || slotIndex >= menu.slots.size()) {
                continue;
            }

            Slot slot = menu.getSlot(slotIndex);
            if (!slot.hasItem()) {
                continue;
            }

            String firstLoreLine = getFirstLoreLine(slot.getItem());
            if (firstLoreLine != null && firstLoreLine.contains(ACTIVE_SET_MARKER)) {
                return i;
            }
        }

        return -1;
    }

    private static String getFirstLoreLine(ItemStack stack) {
        List<String> loreLines = getLoreLines(stack);
        return loreLines.isEmpty() ? null : loreLines.getFirst();
    }

    private static List<String> getLoreLines(ItemStack stack) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return List.of();
        }

        // NORMAL only — ADVANCED adds the registry-id/durability lines that pollute lore-marker matching.
        List<Component> tooltip = stack.getTooltipLines(
                Item.TooltipContext.of(client.player.level()),
                client.player,
                TooltipFlag.NORMAL
        );

        List<String> lines = new ArrayList<>();
        for (int i = 1; i < tooltip.size(); i++) {
            String line = Formatting.stripMinecraftFormatting(tooltip.get(i).getString()).trim();
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }

        return lines;
    }
}
