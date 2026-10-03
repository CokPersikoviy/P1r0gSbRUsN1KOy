package ru.wilyfox.client.rune;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.RunesBagConfig;
import ru.wilyfox.client.profiler.ModProfiler;

import java.util.List;

public final class RuneSetSwitcher {
    private static final List<Integer> RUNE_SET_SLOTS = List.of(0, 1, 3, 4, 5, 6, 8);

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
        return mapping.matches(new net.minecraft.client.input.KeyEvent(keyCode, scanCode, 0));
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
        if (!RuneLore.canUseSet(firstLoreLine)) {
            return false;
        }

        gameMode.handleContainerInput(menu.containerId, slotIndex, 0, ContainerInput.PICKUP, client.player);
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
            if (RuneLore.isActiveSet(firstLoreLine)) {
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
        return ItemLoreReader.read(stack);
    }
}
