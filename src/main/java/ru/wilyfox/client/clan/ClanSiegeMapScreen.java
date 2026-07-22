package ru.wilyfox.client.clan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.WidgetChrome;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;

public final class ClanSiegeMapScreen extends Screen {
    public ClanSiegeMapScreen() {
        super(Component.literal("Clan Siege Map"));
    }

    public static boolean canOpen() {
        return ClanSiegeMapRenderer.canRender(Minecraft.getInstance());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void renderBlurredBackground() {
    }

    @Override
    public void tick() {
        if (!canOpen() && minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int size = Math.max(128, Math.min(width, height) - 32);
        int left = (width - size) / 2;
        int top = (height - size) / 2;
        graphics.fill(0, 0, width, height, WidgetTheme.withAlpha(WidgetTheme.PANEL_BG, 0x88));
        HudSurface.drawPanel(
                graphics,
                left - 2,
                top - 2,
                size + 4,
                size + 4,
                WidgetChrome.FROST,
                HudSurface.nativeRenderer()
        );

        float fitScale = size / 512.0F;
        float zoom = fitScale * (ConfigManager.get().dungeonMap.siegeZoomPercent / 100.0F);
        ClanSiegeMapRenderer.render(
                graphics,
                Minecraft.getInstance(),
                left,
                top,
                size,
                zoom,
                ConfigManager.get().dungeonMap.rotateSiegeMap
        );
    }
}
