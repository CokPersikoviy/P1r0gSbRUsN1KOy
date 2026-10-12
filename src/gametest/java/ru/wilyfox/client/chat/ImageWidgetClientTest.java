package ru.wilyfox.client.chat;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.hud.config.*;
import ru.wilyfox.client.hud.widget.ImageWidget;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;

public final class ImageWidgetClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original=context.computeOnClient(client->ConfigManager.snapshot());
        var renderer=Client.getInstance().getHudRenderer();
        var widget=(ImageWidget)renderer.getWidgets().stream().filter(w->w instanceof ImageWidget).findFirst().orElseThrow();
        String asset=null;
        try(var world=context.worldBuilder().create()) {
            byte[] bytes;
            try {var output=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(64,32,BufferedImage.TYPE_INT_RGB),"png",output);bytes=output.toByteArray();}
            catch(java.io.IOException error){throw new AssertionError(error);}
            context.runOnClient(client->{renderer.setSettings(false);renderer.setEditing(false);ConfigManager.get().mainLayout.widgets.clear();ConfigManager.get().locationLayouts.clear();ConfigManager.get().imageWidget.asset="";ConfigManager.layoutChanged();});
            var viewer=context.computeOnClient(client->new FhImageScreen(new ChatScreen("",false),()->CompletableFuture.completedFuture(bytes)));
            context.runOnClient(client->client.gui.setScreen(viewer));
            Button pin=null;
            for(int i=0;i<100&&pin==null;i++){
                pin=context.computeOnClient(client->viewer.children().stream().filter(c->c instanceof Button b&&b.active).map(c->(Button)c).findFirst().orElse(null));
                context.waitTicks(1);
            }
            check(pin!=null,"Image pin button did not become available");
            final Button selected=pin; context.runOnClient(client->selected.onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,0,0)));
            for(int i=0;i<100&&asset==null;i++){
                String current=context.computeOnClient(client->ConfigManager.get().imageWidget.asset);
                if(!current.isEmpty())asset=current;else context.waitTicks(1);
            }
            check(asset!=null,"Pin did not persist the image");
            context.runOnClient(client->{check(ConfigManager.get().mainLayout.widgets.contains(WidgetCatalog.IMAGE.key()),"Pin did not add Image to Main");viewer.onClose();renderer.refreshLayout();widget.tick(client);});
            context.waitTicks(10);context.takeScreenshot("fh-image-widget");
            var image=context.computeOnClient(client->field(widget,"image"));
            check(image instanceof FhImageTexture texture&&texture.loaded(),"Widget did not load saved image");
            context.runOnClient(client->{
                check(widget.getWidth()==160&&widget.getHeight()==120,"Image bounds ignore settings");widget.setScale(2);check(widget.getWidth()==320,"Image scale ignored");widget.setScale(1);
                widget.release();ConfigManager.restoreSnapshot(ConfigManager.snapshot());widget.tick(client);
            });
            context.waitTicks(10);
            check(context.computeOnClient(client->((FhImageTexture)field(widget,"image")).loaded()),"Saved image did not reload after config restoration");
            context.runOnClient(client->{ConfigManager.get().mainLayout.widgets.remove(WidgetCatalog.IMAGE.key());ConfigManager.layoutChanged();widget.tick(client);});
            context.waitTicks(15);
            context.runOnClient(client->{widget.tick(client);check(field(widget,"image")==null,"Removing image widget retained texture after departure fade");});
            String gifAsset;
            try {gifAsset=ImageWidgetStore.save(ChatGifClientTest.gif());}catch(java.io.IOException error){throw new AssertionError(error);}
            context.runOnClient(client->{ConfigManager.get().imageWidget.asset=gifAsset;ConfigManager.get().imageWidget.animateGif=true;ConfigManager.get().render.lightweightHud=false;ConfigManager.get().mainLayout.widgets.add(WidgetCatalog.IMAGE.key());ConfigManager.layoutChanged();widget.tick(client);});
            boolean red=false,blue=false;
            for(int i=0;i<100&&!(red&&blue);i++){
                int color=context.computeOnClient(client->{
                    var current=(FhImageTexture)field(widget,"image");if(current==null||!current.loaded())return 0;
                    current.advance();var texture=(net.minecraft.client.renderer.texture.DynamicTexture)client.getTextureManager().getTexture(current.id());
                    return texture.getPixels().getPixel(2,2);
                });
                red|=(color&0xFFFFFF)==0xFF0000;blue|=(color&0xFFFFFF)==0x0000FF;context.waitTicks(1);
            }
            check(red&&blue,"GIF widget did not advance actual texture frames");
            var gifTexture=context.computeOnClient(client->((net.minecraft.client.renderer.texture.DynamicTexture)client.getTextureManager().getTexture(((FhImageTexture)field(widget,"image")).id())).getPixels());
            context.runOnClient(client->{ConfigManager.get().mainLayout.widgets.remove(WidgetCatalog.IMAGE.key());ConfigManager.layoutChanged();widget.tick(client);});
            context.waitTicks(15);context.runOnClient(widget::tick);
            check(gifTexture.isClosed(),"Removing GIF widget retained native pixels");
            System.out.println("FH image widget: pin action, persistence, reload, scaling, GIF playback and removal cleanup passed");
        } finally {
            context.runOnClient(client->{widget.release();client.gui.setScreen(null);renderer.setEditing(false);renderer.setSettings(false);ConfigManager.restoreSnapshot(original);renderer.refreshLayout();});
        }
    }
    private static Object field(Object target,String name){try{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(ReflectiveOperationException error){throw new AssertionError(error);}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
