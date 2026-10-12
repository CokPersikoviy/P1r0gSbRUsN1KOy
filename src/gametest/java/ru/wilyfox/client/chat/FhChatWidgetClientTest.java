package ru.wilyfox.client.chat;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ChatScreen;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.menu.HudSettingsPanel;
import java.util.List;

public final class FhChatWidgetClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original=context.computeOnClient(client->ConfigManager.snapshot());
        var renderer=Client.getInstance().getHudRenderer();
        try(var world=context.worldBuilder().create()) {
            context.runOnClient(client->{
                renderer.setEditing(false);renderer.setSettings(false);
                client.gui.setScreen(new ChatScreen("",false));
                String key=ChatDock.key(ChatTab.FH);
                ChatDock.dock(key);ChatDock.select(key);
                var chat=ChatTabManager.getInstance();chat.clearBackendMessages();
                chat.acceptBackendMessages(List.of(new BackendChatMessage(1,"OtherPlayer","FH widget message",System.currentTimeMillis())),false);
                int y=ChatTabOverlay.getInstance().rowTop(client.getWindow().getGuiScaledHeight());
                ChatDock.pressTab(key,40,y+4);ChatDock.drag(140,y+4);
                check(!ChatDock.settings(key).detached,"Horizontal drag detached pinned FH");ChatDock.cancelDrag();
                ChatDock.pressTab(key,40,y+4);ChatDock.drag(180,80);ChatDock.release(180,80);
                check(ChatDock.settings(key).detached,"FH tab could not be dragged out");
                check(!ChatDock.tabs().contains(key)&&ChatDock.tabs().getFirst().equals(ChatDock.key(ChatTab.ALL)),"FH duplicated in dock or ALL moved");
                var widget=ChatDock.widget(key);
                check(widget.isVisible()&&widget.view().channel()==ChatTab.FH&&!widget.view().lines(client.font,280,10).isEmpty(),"Detached FH lost backend messages");
                ChatDock.focusWindow(key);check(ChatDock.outgoingChannel()==ChatTab.FH,"FH widget did not route input to backend channel");
                check(new HudSettingsPanel().openWidget(widget,()->{}),"FH widget settings blocked");
                var config=ChatDock.settings(key);config.width=420;config.rows=7;
                ConfigManager.restoreSnapshot(ConfigManager.snapshot());renderer.refreshLayout();
                check(ChatDock.settings(key).detached&&ChatDock.settings(key).width==420&&ChatDock.settings(key).rows==7,"Save/reload docked FH or reset dimensions");
                ChatDock.delete(key);check(!ChatDock.settings(key).deleted&&ChatDock.settings(key).channel==ChatTab.FH,"FH identity/deletion protections lost");
                ChatDock.pressWindow(widget,widget.getStartX()+2,widget.getStartY()+2);
                int dockY=ChatTabOverlay.getInstance().rowTop(client.getWindow().getGuiScaledHeight())+4;
                ChatDock.drag(60,dockY);ChatDock.release(60,dockY);
                check(!ChatDock.settings(key).detached&&ChatDock.tabs().get(1).equals(key),"FH did not return to its fixed dock position");
                check(chat.getMessages(ChatTab.FH).size()==1,"FH docking duplicated or lost history");
                System.out.println("FH widget: drag out/in, backend messages, focus, settings and persistence passed");
            });
        } finally {context.runOnClient(client->{ChatDock.cancelDrag();client.gui.setScreen(null);ChatTabManager.getInstance().clearBackendMessages();ConfigManager.restoreSnapshot(original);renderer.refreshLayout();});}
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
