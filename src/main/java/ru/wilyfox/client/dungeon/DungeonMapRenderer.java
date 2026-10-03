package ru.wilyfox.client.dungeon;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import ru.wilyfox.client.protocol.DwDungeonPosition;

/** Draws a clipped dungeon map and a constant-size player arrow inside the FH panel. */
public final class DungeonMapRenderer {
    private DungeonMapRenderer() {}

    public static void render(GuiGraphicsExtractor graphics, Identifier texture, int left, int top,
                              DwDungeonPosition position, boolean anchor, boolean rotate,
                              int zoomPercent, float playerYaw) {
        DungeonMapTransform transform = DungeonMapTransform.create(position, anchor, rotate, zoomPercent, playerYaw);
        graphics.enableScissor(left, top, left + DungeonMapTransform.MAP_SIZE, top + DungeonMapTransform.MAP_SIZE);
        graphics.pose().pushMatrix();
        graphics.pose().translate(left, top);
        graphics.pose().pushMatrix();
        graphics.pose().translate(DungeonMapTransform.MAP_SIZE / 2f, DungeonMapTransform.MAP_SIZE / 2f);
        graphics.pose().rotate(transform.rotation());
        graphics.pose().scale(transform.zoom(), transform.zoom());
        graphics.pose().translate(-transform.anchorX(), -transform.anchorY());
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0,
                DungeonMapTransform.UV_OFFSET, DungeonMapTransform.UV_OFFSET,
                DungeonMapTransform.MAP_SIZE, DungeonMapTransform.MAP_SIZE,
                DungeonMapTransform.UV_SIZE, DungeonMapTransform.UV_SIZE,
                DungeonMapTransform.MAP_SIZE, DungeonMapTransform.MAP_SIZE);
        graphics.pose().popMatrix();
        if (DungeonMapTransform.hasPosition(position)) {
            var marker = transform.project(position);
            graphics.pose().translate(marker.x, marker.y);
            // A rotating map keeps the arrow pointing up; a fixed map shows the player's heading.
            graphics.pose().rotate(transform.rotation() + (float) Math.toRadians(playerYaw - 180f));
            drawArrow(graphics, 0xFF172329, 1);
            drawArrow(graphics, 0xFFFFFFFF, 0);
        }
        graphics.pose().popMatrix();
        graphics.disableScissor();
    }

    private static void drawArrow(GuiGraphicsExtractor graphics, int color, int border) {
        graphics.fill(-1 - border, -4 - border, 1 + border, -2, color);
        graphics.fill(-2 - border, -2, 2 + border, 0, color);
        graphics.fill(-3 - border, 0, 3 + border, 2 + border, color);
        graphics.fill(-1 - border, 2, 1 + border, 3 + border, color);
    }
}
