package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.moduser.ModUserProtocol;
import ru.wilyfox.client.moduser.SocialGroup;

import java.util.List;

/**
 * Compact HUD readout of the keepalive {@link SocialGroup}: each member as "Nick | ур N" with an "♥ hp/max"
 * line under it, coloured by health. Data comes from {@link ModUserProtocol#getStatus} (live keepalive PMs).
 * Ability cooldowns will slot into the same per-member block later.
 */
public class GroupHudWidget extends AbstractWidget {
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int LINE_GAP = 1;
    private static final int MEMBER_GAP = 4;
    private static final int MAX_COOLDOWNS = 3;
    private static final int EMPTY_WIDTH = 120;
    private static final int EMPTY_HEIGHT = 40;

    public GroupHudWidget(int x, int y, HudLayer layer) {
        super(x, y, layer);
    }

    @Override
    public boolean isVisible() {
        return (ConfigManager.get().social.widgetActive && !SocialGroup.keepaliveTargets().isEmpty()) || isEditorPreview();
    }

    @Override
    public String getDisplayName() {
        return "Group";
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        List<String> members = SocialGroup.keepaliveTargets();

        int width = getUnscaledWidth(mc);
        int height = getUnscaledHeight(mc);

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        HudSurface.drawPanel(context, width, height);

        int y = PADDING_Y;
        if (WidgetUtils.showWidgetTitles()) {
            context.drawString(mc.font, "Group", PADDING_X, y, WidgetTheme.TITLE);
            y += mc.font.lineHeight + 2;
        }

        if (members.isEmpty()) {
            context.drawString(mc.font, "Группа пуста", PADDING_X, y, WidgetTheme.TEXT_MUTED);
            context.pose().popPose();
            return;
        }

        for (String name : members) {
            ModUserProtocol.Status status = ModUserProtocol.getStatus(name);
            boolean online = status != null;
            int nameColor = online ? WidgetTheme.TEXT_PRIMARY : WidgetTheme.TEXT_MUTED;

            String header = online ? (name + " | ур " + status.level()) : (name + " | оффлайн");
            context.drawString(mc.font, header, PADDING_X, y, nameColor);
            y += mc.font.lineHeight + LINE_GAP;

            if (online) {
                context.drawString(mc.font, "♥ " + status.hp() + "/" + status.maxHp(), PADDING_X, y, hpColor(status));
                y += mc.font.lineHeight;
                for (ModUserProtocol.CooldownView cd : cooldowns(name)) {
                    context.drawString(mc.font, cd.ability() + " " + cd.seconds() + "s", PADDING_X + 6, y, WidgetTheme.TEXT_MUTED);
                    y += mc.font.lineHeight;
                }
            }
            y += MEMBER_GAP;
        }

        context.pose().popPose();
    }

    @Override
    public int getWidth() {
        return Math.round(getUnscaledWidth(Minecraft.getInstance()) * getScale());
    }

    @Override
    public int getHeight() {
        return Math.round(getUnscaledHeight(Minecraft.getInstance()) * getScale());
    }

    private int getUnscaledWidth(Minecraft mc) {
        int max = mc.font.width("Group");
        for (String name : SocialGroup.keepaliveTargets()) {
            ModUserProtocol.Status status = ModUserProtocol.getStatus(name);
            String header = status != null ? (name + " | ур " + status.level()) : (name + " | оффлайн");
            max = Math.max(max, mc.font.width(header));
            if (status != null) {
                max = Math.max(max, mc.font.width("♥ " + status.hp() + "/" + status.maxHp()));
                for (ModUserProtocol.CooldownView cd : cooldowns(name)) {
                    max = Math.max(max, 6 + mc.font.width(cd.ability() + " " + cd.seconds() + "s"));
                }
            }
        }
        return Math.max(EMPTY_WIDTH, max + PADDING_X * 2);
    }

    private int getUnscaledHeight(Minecraft mc) {
        List<String> members = SocialGroup.keepaliveTargets();
        int h = PADDING_Y * 2;
        if (WidgetUtils.showWidgetTitles()) {
            h += mc.font.lineHeight + 2;
        }
        if (members.isEmpty()) {
            return Math.max(EMPTY_HEIGHT, h + mc.font.lineHeight);
        }
        for (String name : members) {
            h += mc.font.lineHeight + LINE_GAP; // header line
            if (ModUserProtocol.getStatus(name) != null) {
                h += mc.font.lineHeight;                          // hp line
                h += cooldowns(name).size() * mc.font.lineHeight; // cooldown lines
            }
            h += MEMBER_GAP;
        }
        return Math.max(EMPTY_HEIGHT, h);
    }

    private int hpColor(ModUserProtocol.Status status) {
        if (status.maxHp() <= 0) {
            return WidgetTheme.TEXT_SECONDARY;
        }
        double fraction = (double) status.hp() / status.maxHp();
        if (fraction >= 0.66) {
            return WidgetTheme.STATUS_SUCCESS;
        }
        if (fraction >= 0.33) {
            return WidgetTheme.STATUS_WARNING;
        }
        return WidgetTheme.STATUS_ERROR;
    }

    private List<ModUserProtocol.CooldownView> cooldowns(String name) {
        List<ModUserProtocol.CooldownView> all = ModUserProtocol.getCooldowns(name);
        return all.size() > MAX_COOLDOWNS ? all.subList(0, MAX_COOLDOWNS) : all;
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().screen instanceof HudEditingScreen;
    }
}
