package ru.wilyfox.mixin;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import ru.wilyfox.bridge.ChatComponentAccessor;
import java.util.List;
@Mixin(ChatComponent.class)
public abstract class ChatComponentAccessorMixin implements ChatComponentAccessor {
    @org.spongepowered.asm.mixin.gen.Invoker("addMessageToDisplayQueue")
    public abstract void froghelper$addDisplayMessage(net.minecraft.client.multiplayer.chat.GuiMessage message);
    @Override @Accessor("trimmedMessages") public abstract List<GuiMessage.Line> froghelper$getTrimmedMessages();
    @Override @Accessor("allMessages") public abstract List<GuiMessage> froghelper$getAllMessages();
    @Override @Accessor("chatScrollbarPos") public abstract int froghelper$getScroll();
    @Override @Accessor("newMessageSinceScroll") public abstract boolean froghelper$getNewMessageSinceScroll();
    @Override @Accessor("newMessageSinceScroll") public abstract void froghelper$setNewMessageSinceScroll(boolean value);
}
