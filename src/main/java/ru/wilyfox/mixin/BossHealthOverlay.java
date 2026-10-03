package ru.wilyfox.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.bridge.BossHealthOverlayAccessor;
import ru.wilyfox.client.hud.config.ConfigManager;

import java.util.Map;
import java.util.UUID;
import java.util.List;

@Mixin(net.minecraft.client.gui.components.BossHealthOverlay.class)
public abstract class BossHealthOverlay implements BossHealthOverlayAccessor {
    @Shadow @Final
    private Minecraft minecraft;

    @Shadow @Final
    private Map<UUID, LerpingBossEvent> events;

    @Shadow
    protected abstract void extractBar(GuiGraphicsExtractor guiGraphics, int i, int j, BossEvent bossEvent);

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void froghelper$cancelVanillaRender(GuiGraphicsExtractor guiGraphics, CallbackInfo ci) {
        if (ConfigManager.get().bossBar.active) {
            ci.cancel();
        }
    }

    @Override
    public void froghelper$renderAt(GuiGraphicsExtractor context, int startX, int startY) {
        if (events.isEmpty()) {
            return;
        }

        int widgetWidth = froghelper$getRenderedWidth();
        int rowTop = startY;
        int rendered = 0;
        int maxHeight = minecraft.getWindow().getGuiScaledHeight() / 3;

        for (LerpingBossEvent event : events.values()) {
            int textWidth = minecraft.font.width(event.getName());
            int rowWidth = Math.max(182, textWidth);

            int barX = startX + (widgetWidth - 182) / 2;
            int textX = startX + (widgetWidth - textWidth) / 2;

            int textY = rowTop;
            int barY = rowTop + 8;

            extractBar(context, barX, barY, event);
            context.text(minecraft.font, event.getName(), textX, textY, 0xFFFFFFFF);

            rowTop += 19;
            rendered++;

            if (rowTop > maxHeight) {
                break;
            }
        }
    }

    @Override
    public int froghelper$getRenderedHeight() {
        if (events.isEmpty()) {
            return 0;
        }

        int maxHeight = minecraft.getWindow().getGuiScaledHeight() / 3;
        int usedHeight = 0;
        int rendered = 0;
        int verticalPadding = -6;

        for (int i = 0; i < events.size(); i++) {
            if (usedHeight + 19 > maxHeight) {
                break;
            }

            usedHeight += 19;
            rendered++;
        }

        return Math.max(0, rendered * 19 + verticalPadding);
    }

    @Override
    public int froghelper$getRenderedWidth() {
        if (events.isEmpty()) {
            return 182;
        }

        int maxWidth = 182;
        int horizontalPadding = 94;

        for (LerpingBossEvent event : events.values()) {
            int textWidth = minecraft.font.width(event.getName());
            if (textWidth > maxWidth) {
                maxWidth = textWidth;
            }
        }

        return maxWidth + horizontalPadding;
    }

    @Override
    public List<LerpingBossEvent> froghelper$getEvents() {
        return List.copyOf(events.values());
    }
}
