package ru.wilyfox.client.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import ru.wilyfox.bridge.ChatComponentAccessor;
import ru.wilyfox.client.popup.PopUpManager;
import ru.wilyfox.utils.Formatting;

import java.util.List;
import java.util.regex.Pattern;

public final class ChatMessageCopyExtractor {
    private static final Pattern MESSAGE_BODY = Pattern.compile("^([^:]{1,32}:\\s+)(.+)$", Pattern.DOTALL);

    private ChatMessageCopyExtractor() {
    }

    public static boolean copyHoveredMessage(ChatComponent chat, double mouseX, double mouseY) {
        return copyHoveredMessage(chat, mouseX, mouseY, false);
    }

    public static boolean copyHoveredMessage(ChatComponent chat, double mouseX, double mouseY, boolean fullMessage) {
        GuiMessage message = hoveredMessage(chat, mouseX, mouseY);
        if (message == null) return false;
        return copyText(selectCopiedText(ChatMessageSanitizer.forLogic(
                Formatting.stripMinecraftFormatting(message.content().getString())), fullMessage));
    }

    public static boolean copyText(String copied) {
        if (copied == null || copied.isBlank()) return false;
        Minecraft.getInstance().keyboardHandler.setClipboard(copied);
        PopUpManager.getInstance().notifyChatCopied();
        return true;
    }

    public static GuiMessage hoveredMessage(ChatComponent chat, double mouseX, double mouseY) {
        if (!(chat instanceof ChatComponentAccessor accessor)) {
            return null;
        }

        List<GuiMessage.Line> visibleMessages = accessor.froghelper$getTrimmedMessages();
        if (visibleMessages.isEmpty()) {
            return null;
        }

        Minecraft minecraft = Minecraft.getInstance();
        double scale = minecraft.options.chatScale().get();
        double chatX = mouseX / scale - 4.0;
        int width = (int) Math.ceil(ChatComponent.getWidth(minecraft.options.chatWidth().get()) / scale);
        if (!chat.isChatFocused() || chatX < -4 || chatX > width + 4) {
            return null;
        }

        HoveredLineFinder finder = new HoveredLineFinder(mouseY);
        chat.captureClickableText(finder, minecraft.getWindow().getGuiScaledHeight(),
                minecraft.gui.hud.getGuiTicks(), ChatComponent.DisplayMode.FOREGROUND);

        for (var line : visibleMessages) if (line.content() == finder.result) return line.parent();
        return null;
    }

    public static List<GuiMessage> displayedMessages(ChatComponent chat) {
        return ((ChatComponentAccessor) chat).froghelper$getTrimmedMessages().stream().map(GuiMessage.Line::parent).toList();
    }

    public static void renderSelection(ChatComponent chat, net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        var selection = ChatMessageSelection.getInstance();
        if (!selection.hasSelection()) return;
        var messages = new java.util.IdentityHashMap<FormattedCharSequence, GuiMessage>();
        var accessor = (ChatComponentAccessor) chat;
        var lines = accessor.froghelper$getTrimmedMessages();
        int first = Math.max(0, accessor.froghelper$getScroll());
        for (int i = first; i < Math.min(lines.size(), first + chat.getLinesPerPage() + 1); i++) {
            var line = lines.get(i);
            messages.put(line.content(), line.parent());
        }
        chat.captureClickableText(new ActiveTextCollector() {
            private Parameters defaults = new Parameters(new Matrix3x2f());
            @Override public Parameters defaultParameters() { return defaults; }
            @Override public void defaultParameters(Parameters value) { defaults = value; }
            @Override public void accept(TextAlignment alignment, int x, int y, Parameters p, FormattedCharSequence text) {
                if (!selection.isSelected(messages.get(text))) return;
                var top = p.pose().transformPosition(new Vector2f(x, y - 1));
                var bottom = p.pose().transformPosition(new Vector2f(x + Minecraft.getInstance().font.width(text), y + 8));
                graphics.fill((int) Math.floor(top.x), (int) Math.floor(top.y), (int) Math.ceil(bottom.x), (int) Math.ceil(bottom.y),
                        ru.wilyfox.client.hud.widget.WidgetTheme.withAlpha(ru.wilyfox.client.hud.widget.WidgetTheme.ACCENT_LINE, 70));
            }
            @Override public void acceptScrolling(Component text, int x, int y, int left, int right, int color, Parameters p) { }
        }, Minecraft.getInstance().getWindow().getGuiScaledHeight(), Minecraft.getInstance().gui.hud.getGuiTicks(),
                ChatComponent.DisplayMode.FOREGROUND);
    }

    static String messageText(List<GuiMessage.Line> visibleMessages, FormattedCharSequence selectedLine) {
        if (selectedLine != null) {
            for (GuiMessage.Line line : visibleMessages) {
                if (line.content() == selectedLine) {
                    return Formatting.stripMinecraftFormatting(line.parent().content().getString());
                }
            }
        }
        return "";
    }

    static final class HoveredLineFinder implements ActiveTextCollector {
        private final double mouseY;
        private Parameters parameters = new Parameters(new Matrix3x2f());
        FormattedCharSequence result;

        HoveredLineFinder(double mouseY) {
            this.mouseY = mouseY;
        }

        @Override
        public Parameters defaultParameters() {
            return parameters;
        }

        @Override
        public void defaultParameters(Parameters parameters) {
            this.parameters = parameters;
        }

        @Override
        public void accept(TextAlignment alignment, int x, int y, Parameters parameters, FormattedCharSequence text) {
            if (result != null) {
                return;
            }
            float top = parameters.pose().transformPosition(new Vector2f(x, y - 1.0f)).y;
            float bottom = parameters.pose().transformPosition(new Vector2f(x, y + 8.0f)).y;
            if (mouseY >= top && mouseY < bottom) {
                result = text;
            }
        }

        @Override
        public void acceptScrolling(Component text, int centerX, int y, int left, int right, int color, Parameters parameters) {
        }
    }

    public static String selectCopiedText(String displayedText, boolean fullMessage) {
        if (fullMessage) {
            return displayedText == null ? "" : displayedText;
        }
        return normalizeCopiedText(displayedText);
    }

    private static String normalizeCopiedText(String raw) {
        String text = raw == null ? "" : raw.replace('\u00A0', ' ').strip();
        text = ChatTimestampFormatter.stripTimestampPrefix(text);

        ChatTab tab = ChatPrefixRouter.resolve(text);
        text = ChatPrefixRouter.stripKnownPrefix(text, tab).strip();

        java.util.regex.Matcher matcher = MESSAGE_BODY.matcher(text);
        if (matcher.matches()) {
            return matcher.group(2).strip();
        }

        return text;
    }
}
