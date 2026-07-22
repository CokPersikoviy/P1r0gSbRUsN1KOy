package ru.wilyfox.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.wilyfox.client.recipe.PotionRecipeTracker;
import ru.wilyfox.client.recipe.CraftRecipeTracker;
import ru.wilyfox.client.alchemy.AutoBrewingCostOverlay;
import ru.wilyfox.client.boss.BossMenuIconCollector;
import ru.wilyfox.client.hud.widget.HudBlur;
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import ru.wilyfox.client.rune.PetExperienceOverlay;
import ru.wilyfox.client.rune.RuneSetEffectOverlay;
import ru.wilyfox.client.rune.RuneSetSwitcher;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    @Shadow
    protected AbstractContainerMenu menu;

    @Shadow
    protected Slot hoveredSlot;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void froghelper$inspectRecipes(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 1) {
            return;
        }

        Screen screen = (Screen) (Object) this;
        boolean isAlchemyPotionList = ru.wilyfox.client.hud.config.ConfigManager.get().potionRecipe.active
                && screen instanceof ContainerScreen
                && screen.getTitle().getString().contains(PotionRecipeTracker.ALCHEMY_POTION_LIST_TITLE);
        if (isAlchemyPotionList && hoveredSlot != null && hoveredSlot.hasItem()) {
            ItemStack stack = hoveredSlot.getItem();
            if (PotionRecipeTracker.getInstance().inspect(stack, Minecraft.getInstance().player)) {
                cir.setReturnValue(true);
                return;
            }
        }

        if (hoveredSlot == null || !hoveredSlot.hasItem()) {
            CraftRecipeTracker.getInstance().clear();
            return;
        }

        ItemStack stack = hoveredSlot.getItem();
        CraftRecipeTracker.getInstance().inspect(stack, Minecraft.getInstance().player);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void froghelper$handleRuneSetSwitch(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        Screen screen = (Screen) (Object) this;
        if (RuneSetSwitcher.handleScreenKeyPressed(screen.getTitle(), menu, keyCode, scanCode)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void froghelper$handleRuneSetMouseSwitch(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        Screen screen = (Screen) (Object) this;
        if (RuneSetSwitcher.handleScreenMouseClicked(screen.getTitle(), menu, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void froghelper$renderRuneSetEffect(GuiGraphics context, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("ui/containerOverlay/render")) {
            froghelper$renderContainerOverlays(context);
        }
    }

    private void froghelper$renderContainerOverlays(GuiGraphics context) {
        Screen screen = (Screen) (Object) this;
        BossMenuIconCollector.inspect(screen.getTitle(), menu);

        AutoBrewingCostOverlay.OverlayData brewingCost = AutoBrewingCostOverlay.collect(
                screen.getTitle().getString(),
                menu,
                Minecraft.getInstance().player
        );
        if (brewingCost != null) {
            HudBlur.beginFrame(context);
            AutoBrewingCostOverlay.render(context, leftPos + 184, topPos + 8, brewingCost);
        }

        // Rune-set buff info now lives in the rune-bag window itself (moved off the player inventory).
        if (RuneSetEffectOverlay.isRuneBagScreen(screen.getTitle())) {
            RuneSetEffectOverlay.updateCooldownStore(menu); // feeds RuneSetCooldownStore (ActiveRunes bar)
            // Optimistic HUD update: sync active runes straight from the bag (swap / add / remove /
            // set-select) so changes show immediately, without waiting for the load-delayed packet.
            // Gated on a loaded bag so it doesn't clear the HUD on the empty frames right after opening.
            if (RuneSetEffectOverlay.isRuneBagLoaded(menu)) {
                DiamondWorldProtocolClient.updateActiveRunesFromBag(RuneSetEffectOverlay.collectActiveRuneNames(menu));
            }
            RuneSetEffectOverlay.OverlayData data = RuneSetEffectOverlay.collect(menu);
            if (data != null) {
                int panelX = leftPos - 8;
                int widestLine = Minecraft.getInstance().font.width(data.title());
                for (String line : data.lines()) {
                    widestLine = Math.max(widestLine, Minecraft.getInstance().font.width(line));
                }
                panelX -= widestLine + 12;
                int panelY = topPos + 8;

                HudBlur.beginFrame(context); // container screen: capture here for the frosted plate's blur
                RuneSetEffectOverlay.render(context, panelX, panelY, data);
            }
            return;
        }

        // Player inventory: only the pet-experience plate (rune buffs moved to the rune bag).
        if (!RuneSetEffectOverlay.isPlayerInventoryScreen(screen)) {
            return;
        }

        PetExperienceOverlay.OverlayData petExpData = PetExperienceOverlay.collect(menu);
        if (petExpData == null) {
            return;
        }

        HudBlur.beginFrame(context);
        int rightPanelX = leftPos + 176 + 8;
        int rightPanelY = topPos + 8;
        PetExperienceOverlay.render(context, rightPanelX, rightPanelY, petExpData);
    }
}
