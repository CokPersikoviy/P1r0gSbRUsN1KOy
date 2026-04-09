package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;

import java.util.ArrayList;
import java.util.List;

public final class EntityInspectWidget extends AbstractWidget {
    private static final int CARD_WIDTH = 316;
    private static final int CARD_HEIGHT = 520;
    private static final int HEADER_HEIGHT = 18;
    private static final int PREVIEW_BOX_X = 8;
    private static final int PREVIEW_BOX_Y = 28;
    private static final int PREVIEW_BOX_SIZE = 84;
    private static final int LINE_HEIGHT = 11;
    private static final int SLOT_X = 102;
    private static final int SLOT_Y = 32;
    private static final int SLOT_ROW_GAP = 46;
    private static final int BLOCK_SECTION_Y = 132;
    private static final int MATCHER_SECTION_Y = 198;
    private static final int NEARBY_SECTION_Y = 270;
    private static final int TEXT_MAX_WIDTH = CARD_WIDTH - 16;
    private static final double NEARBY_SCAN_RADIUS = 1.6D;

    public EntityInspectWidget(int x, int y, HudLayer layer) {
        super(x, y, layer);
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!ConfigManager.get().entityInspect.active) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        InspectSnapshot snapshot = buildSnapshot(mc);
        if (snapshot == null) {
            if (!isEditorPreview()) {
                return;
            }
            snapshot = InspectSnapshot.editorPreview();
        }

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0F);

        renderCard(context, mc, snapshot);

        context.pose().popPose();
    }

    @Override
    public boolean isVisible() {
        return ConfigManager.get().entityInspect.active
                && (Minecraft.getInstance().crosshairPickEntity != null || isTargetingBlock(Minecraft.getInstance()) || isEditorPreview());
    }

    @Override
    public int getWidth() {
        return Math.round(CARD_WIDTH * getScale());
    }

    @Override
    public int getHeight() {
        return Math.round(CARD_HEIGHT * getScale());
    }

    @Override
    public String getDisplayName() {
        return "Entity Inspect";
    }

    private void renderCard(GuiGraphics context, Minecraft mc, InspectSnapshot snapshot) {
        context.fill(0, 0, CARD_WIDTH, CARD_HEIGHT, WidgetTheme.WIDGET_PANEL_BG);
        context.fill(0, 0, CARD_WIDTH, 1, WidgetTheme.WIDGET_ACCENT_LINE);
        context.fill(0, HEADER_HEIGHT, CARD_WIDTH, HEADER_HEIGHT + 1, WidgetTheme.BAR_BG);

        context.drawString(mc.font, "Entity Inspect", 8, 6, WidgetTheme.TITLE);
        context.drawString(mc.font, "EntityType: " + snapshot.entityType, 8, HEADER_HEIGHT + 4, WidgetTheme.TEXT_SOFT);

        int previewX2 = PREVIEW_BOX_X + PREVIEW_BOX_SIZE;
        int previewY2 = PREVIEW_BOX_Y + PREVIEW_BOX_SIZE;
        context.fill(PREVIEW_BOX_X, PREVIEW_BOX_Y, previewX2, previewY2, WidgetTheme.WIDGET_PANEL_BG_SOFT);
        context.fill(PREVIEW_BOX_X, PREVIEW_BOX_Y, previewX2, PREVIEW_BOX_Y + 1, WidgetTheme.WIDGET_ACCENT_LINE);

        context.renderItem(snapshot.previewItem, PREVIEW_BOX_X + 34, PREVIEW_BOX_Y + 18);
        context.drawCenteredString(mc.font, snapshot.previewLabel, PREVIEW_BOX_X + PREVIEW_BOX_SIZE / 2, PREVIEW_BOX_Y + 44, WidgetTheme.TEXT_SECONDARY);

        int nameColor = snapshot.displayName.isBlank() ? WidgetTheme.TEXT_MUTED : WidgetTheme.TEXT_PRIMARY;
        context.drawString(mc.font, "Name: " + (snapshot.displayName.isBlank() ? "-" : snapshot.displayName), PREVIEW_BOX_X, PREVIEW_BOX_Y + PREVIEW_BOX_SIZE + 8, nameColor);

        int y = SLOT_Y;
        for (SlotInfo slot : snapshot.slots) {
            renderSlotRow(context, mc, slot, y);
            y += SLOT_ROW_GAP;
        }

        context.drawString(mc.font, "Block:", PREVIEW_BOX_X, BLOCK_SECTION_Y, WidgetTheme.TEXT_SECONDARY);
        int blockY = BLOCK_SECTION_Y + LINE_HEIGHT + 1;
        for (String line : snapshot.blockLines) {
            context.drawString(mc.font, trimToWidth(mc, line), PREVIEW_BOX_X, blockY, WidgetTheme.TEXT_SOFT);
            blockY += LINE_HEIGHT;
        }

        context.drawString(mc.font, "Matcher:", PREVIEW_BOX_X, MATCHER_SECTION_Y, WidgetTheme.TEXT_SECONDARY);
        int matcherY = MATCHER_SECTION_Y + LINE_HEIGHT + 1;
        for (String line : snapshot.matcherLines) {
            context.drawString(mc.font, trimToWidth(mc, line), PREVIEW_BOX_X, matcherY, WidgetTheme.TEXT_SOFT);
            matcherY += LINE_HEIGHT;
        }

        context.drawString(mc.font, "Nearby:", PREVIEW_BOX_X, NEARBY_SECTION_Y, WidgetTheme.TEXT_SECONDARY);
        int nearbyY = NEARBY_SECTION_Y + LINE_HEIGHT + 1;
        for (String line : snapshot.nearbyLines) {
            context.drawString(mc.font, trimToWidth(mc, line), PREVIEW_BOX_X, nearbyY, WidgetTheme.TEXT_SOFT);
            nearbyY += LINE_HEIGHT;
        }
    }

    private void renderSlotRow(GuiGraphics context, Minecraft mc, SlotInfo slot, int y) {
        int rowWidth = CARD_WIDTH - SLOT_X - 8;
        context.fill(SLOT_X, y - 2, SLOT_X + rowWidth, y + 43, WidgetTheme.WIDGET_PANEL_BG_SOFT);
        context.fill(SLOT_X, y - 2, SLOT_X + 1, y + 43, WidgetTheme.WIDGET_ACCENT_LINE);

        context.drawString(mc.font, slot.label, SLOT_X + 6, y, WidgetTheme.TEXT_SECONDARY);
        context.drawString(mc.font, slot.material, SLOT_X + 54, y, slot.empty ? WidgetTheme.TEXT_MUTED : WidgetTheme.TEXT_SOFT);

        if (slot.empty) {
            context.drawString(mc.font, "empty", SLOT_X + 54, y + LINE_HEIGHT, WidgetTheme.TEXT_MUTED);
            return;
        }

        context.drawString(mc.font, "CMD: " + slot.customModelData, SLOT_X + 54, y + LINE_HEIGHT, WidgetTheme.TEXT_ACCENT);
        context.drawString(mc.font, trimToWidth(mc, "Model: " + slot.itemModel), SLOT_X + 6, y + LINE_HEIGHT * 2, WidgetTheme.TEXT_SECONDARY);
        context.drawString(mc.font, trimToWidth(mc, slot.name), SLOT_X + 6, y + LINE_HEIGHT * 3, WidgetTheme.TEXT_PRIMARY);
    }

    private InspectSnapshot buildSnapshot(Minecraft mc) {
        Entity target = mc.crosshairPickEntity;
        boolean hasBlockTarget = isTargetingBlock(mc);
        if (target == null && !hasBlockTarget) {
            return null;
        }

        String entityType = target == null ? "-" : target.getType().toShortString();
        String displayName = target == null ? "" : strip(target.getName().getString());
        ItemStack previewItem = target instanceof ArmorStand ? new ItemStack(Items.ARMOR_STAND) : new ItemStack(Items.SPYGLASS);
        String previewLabel = target instanceof ArmorStand ? "ArmorStand" : entityType;

        List<SlotInfo> slots = new ArrayList<>();
        if (target instanceof ArmorStand stand) {
            slots.add(slotInfo("Head", stand.getItemBySlot(EquipmentSlot.HEAD)));
            slots.add(slotInfo("Chest", stand.getItemBySlot(EquipmentSlot.CHEST)));
            slots.add(slotInfo("Legs", stand.getItemBySlot(EquipmentSlot.LEGS)));
            slots.add(slotInfo("Feet", stand.getItemBySlot(EquipmentSlot.FEET)));
        } else {
            slots.add(SlotInfo.empty("Slot"));
        }

        return new InspectSnapshot(
                entityType,
                displayName,
                previewItem,
                previewLabel,
                slots,
                buildBlockLines(mc),
                buildMatcherLines(mc, target),
                buildNearbyLines(mc, target)
        );
    }

    private List<String> buildBlockLines(Minecraft mc) {
        if (!isTargetingBlock(mc) || mc.level == null || !(mc.hitResult instanceof BlockHitResult blockHitResult)) {
            return List.of("none");
        }

        BlockPos pos = blockHitResult.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        List<String> lines = new ArrayList<>();
        lines.add("id: " + BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        lines.add("pos: " + pos.getX() + " " + pos.getY() + " " + pos.getZ());

        if (state.hasProperty(NoteBlock.INSTRUMENT)) {
            lines.add("instrument: " + state.getValue(NoteBlock.INSTRUMENT));
        }
        if (state.hasProperty(NoteBlock.NOTE)) {
            lines.add("note: " + state.getValue(NoteBlock.NOTE));
        }
        if (state.hasProperty(NoteBlock.POWERED)) {
            lines.add("powered: " + state.getValue(NoteBlock.POWERED));
        }

        return lines;
    }

    private List<String> buildMatcherLines(Minecraft mc, Entity target) {
        if (!(target instanceof Interaction interaction) || mc.level == null) {
            return List.of("cluster-golden: n/a");
        }

        List<Entity> nearbyEntities = mc.level.getEntities(
                interaction,
                interaction.getBoundingBox().inflate(NEARBY_SCAN_RADIUS),
                candidate -> candidate != interaction
        );

        boolean hasEmptyArmorStand = false;
        int boneDisplays = 0;
        int airDisplays = 0;
        for (Entity entity : nearbyEntities) {
            if (entity instanceof ArmorStand stand && isEmptyArmorStand(stand)) {
                hasEmptyArmorStand = true;
                continue;
            }

            if (entity instanceof Display.ItemDisplay itemDisplay) {
                ItemStack stack = itemDisplay.getItemStack();
                if (stack.is(Items.BONE)) {
                    boneDisplays++;
                } else if (stack.isEmpty()) {
                    airDisplays++;
                }
            }
        }

        boolean matches = hasEmptyArmorStand && boneDisplays >= 1;
        return List.of(
                "cluster-golden: " + matches,
                "empty-armor-stand: " + hasEmptyArmorStand,
                "bone-displays: " + boneDisplays,
                "air-displays: " + airDisplays
        );
    }

    private List<String> buildNearbyLines(Minecraft mc, Entity target) {
        if (mc.level == null || target == null) {
            return List.of("none");
        }

        List<Entity> nearbyEntities = mc.level.getEntities(
                target,
                target.getBoundingBox().inflate(NEARBY_SCAN_RADIUS),
                candidate -> candidate != target
        );
        if (nearbyEntities.isEmpty()) {
            return List.of("none");
        }

        List<String> lines = new ArrayList<>();
        nearbyEntities.stream()
                .sorted((left, right) -> Double.compare(left.distanceToSqr(target), right.distanceToSqr(target)))
                .forEach(entity -> lines.addAll(describeNearbyEntity(target, entity)));
        return lines;
    }

    private List<String> describeNearbyEntity(Entity anchor, Entity entity) {
        List<String> lines = new ArrayList<>();
        double distance = Math.sqrt(entity.distanceToSqr(anchor));
        String typeLine = entity.getType().toShortString() + " [" + String.format(java.util.Locale.ROOT, "%.2f", distance) + "m]";

        if (entity instanceof ArmorStand stand) {
            List<String> items = new ArrayList<>();
            collectStackSummary(items, stand.getItemBySlot(EquipmentSlot.HEAD));
            collectStackSummary(items, stand.getItemBySlot(EquipmentSlot.CHEST));
            collectStackSummary(items, stand.getItemBySlot(EquipmentSlot.LEGS));
            collectStackSummary(items, stand.getItemBySlot(EquipmentSlot.FEET));
            lines.add(typeLine);
            lines.add(items.isEmpty() ? "  slots: empty" : "  slots: " + String.join(", ", items));
            return lines;
        }

        if (entity instanceof Display.ItemDisplay itemDisplay) {
            ItemStack stack = itemDisplay.getItemStack();
            lines.add(typeLine);
            lines.add("  item: " + stack.getItem());
            lines.add("  cmd: " + readCustomModelData(stack));
            lines.add("  model: " + readItemModel(stack));
            lines.add("  name: " + fallbackBlank(strip(stack.getHoverName().getString())));
            return lines;
        }

        lines.add(typeLine);
        lines.add("  name: " + fallbackBlank(strip(entity.getName().getString())));
        return lines;
    }

    private void collectStackSummary(List<String> items, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        items.add(describeStack(stack));
    }

    private boolean isEmptyArmorStand(ArmorStand stand) {
        return stand.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                && stand.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                && stand.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                && stand.getItemBySlot(EquipmentSlot.FEET).isEmpty();
    }

    private SlotInfo slotInfo(String label, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return SlotInfo.empty(label);
        }

        return new SlotInfo(
                label,
                false,
                stack.getItem().toString(),
                readCustomModelData(stack),
                readItemModel(stack),
                strip(stack.getHoverName().getString())
        );
    }

    private String describeStack(ItemStack stack) {
        String material = stack.getItem().toString();
        int cmd = readCustomModelData(stack);
        return cmd >= 0 ? material + "#" + cmd : material;
    }

    private String fallbackBlank(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String trimToWidth(Minecraft mc, String value) {
        if (mc.font.width(value) <= TEXT_MAX_WIDTH) {
            return value;
        }

        String ellipsis = "...";
        String trimmed = value;
        while (!trimmed.isEmpty() && mc.font.width(trimmed + ellipsis) > TEXT_MAX_WIDTH) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + ellipsis;
    }

    private int readCustomModelData(ItemStack stack) {
        CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd == null) {
            return -1;
        }

        Float first = cmd.getFloat(0);
        return first == null ? -1 : Math.round(first);
    }

    private String readItemModel(ItemStack stack) {
        Object itemModel = stack.get(DataComponents.ITEM_MODEL);
        return itemModel == null ? "-" : itemModel.toString();
    }

    private String strip(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replaceAll("\u00A7.", "")
                .replaceAll("\u0412\u00A7.", "")
                .replaceAll("\u0420\u2019\u0412\u00A7.", "")
                .trim();
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().screen instanceof HudEditingScreen;
    }

    private boolean isTargetingBlock(Minecraft mc) {
        return mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK;
    }

    private record InspectSnapshot(
            String entityType,
            String displayName,
            ItemStack previewItem,
            String previewLabel,
            List<SlotInfo> slots,
            List<String> blockLines,
            List<String> matcherLines,
            List<String> nearbyLines
    ) {
        private static InspectSnapshot editorPreview() {
            return new InspectSnapshot(
                    "interaction",
                    "Debug Preview",
                    new ItemStack(Items.SPYGLASS),
                    "interaction",
                    List.of(SlotInfo.empty("Slot")),
                    List.of("id: minecraft:note_block", "pos: 0 64 0", "instrument: flute", "note: 21", "powered: false"),
                    List.of("cluster-golden: true", "empty-armor-stand: true", "bone-displays: 2", "air-displays: 1"),
                    List.of("interaction [0.00m]", "  name: -", "item_display [0.22m]", "  item: minecraft:paper", "  cmd: 271", "  model: froghelper:golden_crystal", "  name: Golden Crystal")
            );
        }
    }

    private record SlotInfo(String label, boolean empty, String material, int customModelData, String itemModel, String name) {
        private static SlotInfo empty(String label) {
            return new SlotInfo(label, true, "-", -1, "-", "");
        }
    }
}

