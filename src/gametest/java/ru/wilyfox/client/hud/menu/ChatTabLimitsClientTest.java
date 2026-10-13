package ru.wilyfox.client.hud.menu;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.chat.*;
import ru.wilyfox.client.hud.config.*;
import java.util.ArrayList;

public final class ChatTabLimitsClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var snapshot=context.computeOnClient(client->ConfigManager.snapshot());
        var added=new ArrayList<String>();
        try(var world=context.worldBuilder().create()) {
            context.runOnClient(client->{
                var config=ConfigManager.get();
                config.chatWidgets.keySet().removeIf(WidgetCatalog::isCustomChatKey);
                config.chatTabOrder.removeIf(WidgetCatalog::isCustomChatKey);
                ConfigManager.layoutChanged();
                for(int i=0;i<20;i++) {
                    var key=ChatDock.create();check(key!=null,"Could not create tab "+(i+1));added.add(key);
                }
                check(ChatDock.create()==null,"21st custom tab allowed");
                check(ChatDock.tabs().containsAll(added),"Created tabs missing from dock");
                var key=added.getLast();var input=new ChatFilterSettingsComponent(()->ChatDock.settings(key));
                input.setPosition(0,0);input.setSize(240,22);input.mouseClicked(200,10,0);
                String regex="^"+"a".repeat(500)+"(?:b|c){1}$";
                try {
                    var insert=TextInputSettingsComponent.class.getDeclaredMethod("insertText",String.class);
                    insert.setAccessible(true); insert.invoke(input,regex+"x");
                }catch(ReflectiveOperationException error){throw new AssertionError(error);}
                check(ChatDock.settings(key).textFilter.equals(regex),"Field did not keep exactly 512 characters");
                var filter=ChatTextFilter.compile(ChatDock.settings(key).textFilter,true);
                check(filter.matches("a".repeat(500)+"b"),"512-character regex became invalid");
                ConfigManager.restoreSnapshot(ConfigManager.snapshot());
                check(ConfigManager.get().chatWidgets.keySet().containsAll(added),"Reload discarded tabs 13–20");
                check(ChatDock.settings(key).textFilter.equals(regex),"Reload truncated the long regex");
                check(ChatDock.tabs().getFirst().equals(ChatDock.key(ChatTab.ALL))&&ChatDock.tabs().get(1).equals(ChatDock.key(ChatTab.FH)),"Pinned tabs changed order");
            });
        }finally{
            context.runOnClient(client->{
                client.gui.setScreen(null);
                for(var key:added)if(ChatDock.canDelete(key))ChatDock.delete(key);
                ConfigManager.restoreSnapshot(snapshot);ChatDock.select(ChatDock.key(ChatTab.ALL));
                Client.getInstance().getHudRenderer().refreshLayout();
            });
        }
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
