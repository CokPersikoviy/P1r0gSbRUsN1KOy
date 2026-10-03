package ru.wilyfox.mixin;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import ru.wilyfox.bridge.ChatComponentAccessor;
import java.util.List;
@Mixin(ChatComponent.class)
public abstract class ChatComponentAccessorMixin implements ChatComponentAccessor {
    @Override @Accessor("trimmedMessages") public abstract List<GuiMessage.Line> froghelper$getTrimmedMessages();
}
