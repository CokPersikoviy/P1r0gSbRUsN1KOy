package ru.wilyfox.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import ru.wilyfox.bridge.ChatComponentAccessor;
import java.util.List;
@Mixin(ChatComponent.class)
public abstract class ChatComponentAccessorMixin implements ChatComponentAccessor {
    @Shadow private Minecraft minecraft;
    @Shadow private int chatScrollbarPos;
    @Override @Accessor("trimmedMessages") public abstract List<GuiMessage.Line> froghelper$getTrimmedMessages();
    @Override public double froghelper$screenToChatX(double mouseX) {
        return mouseX / minecraft.options.chatScale().get() - 4;
    }
    @Override public double froghelper$screenToChatY(double mouseY) {
        double scale = minecraft.options.chatScale().get();
        return Math.floor((minecraft.getWindow().getGuiScaledHeight() - 40) / scale) - mouseY / scale;
    }
    @Override public int froghelper$getMessageLineIndexAt(double chatX, double chatY) {
        ChatComponent chat = (ChatComponent) (Object) this;
        double scale = minecraft.options.chatScale().get();
        int width = (int) Math.ceil(ChatComponent.getWidth(minecraft.options.chatWidth().get()) / scale);
        int lineHeight = (int) (9 * (minecraft.options.chatLineSpacing().get() + 1));
        int line = (int) Math.floor(chatY / lineHeight);
        if (!chat.isChatFocused() || chatX < -4 || chatX > width + 4 || chatY < 0 || line >= chat.getLinesPerPage()) return -1;
        int index = line + chatScrollbarPos;
        return index < froghelper$getTrimmedMessages().size() ? index : -1;
    }
}
