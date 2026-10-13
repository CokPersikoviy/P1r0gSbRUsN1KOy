package ru.wilyfox.client.hud.widget;

import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.highlight.*;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.*;
import ru.wilyfox.client.hud.internal.HudFrameClock;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.profiler.ModProfiler;
import java.util.*;

public final class NearbyMineWidget extends AbstractWidget {
    private final NearbyMineState.Kind kind;
    private final WidgetCatalog catalog;
    private final List<Component> lines = new ArrayList<>();
    private long frame = -1;
    private Object presentationKey;
    private int count, contentWidth=110,contentHeight=22;
    public NearbyMineWidget(int x,int y,HudLayer layer,NearbyMineState.Kind kind) {
        super(x,y,layer); this.kind=kind;
        catalog=switch(kind) { case WORMS->WidgetCatalog.NEARBY_WORMS; case BARRELS->WidgetCatalog.NEARBY_BARRELS; case CRYSTALS->WidgetCatalog.NEARBY_CRYSTALS; };
        setConfigKey(catalog.key());
    }
    private NearbyMineWidgetConfig settings() { return switch(kind) { case WORMS->ConfigManager.get().nearbyWorms;case BARRELS->ConfigManager.get().nearbyBarrels;case CRYSTALS->ConfigManager.get().nearbyCrystals; }; }
    private void prepare() {
        if(frame==HudFrameClock.current())return;frame=HudFrameClock.current();
        try(var ignored=ModProfiler.getInstance().scope("hud/NearbyMine/"+kind)) {
            var client=Minecraft.getInstance(); var config=settings();
            var player=client.player==null?new NearbyMineState.Point(0,0,0):new NearbyMineState.Point(client.player.getX(),client.player.getY(),client.player.getZ());
            var result=NearbyMineState.count(kind,player,config.range); count=result.count();
            double distance=Math.rint(result.nearestDistance()*10)/10;
            Object key=Arrays.asList(count,distance,result.nearest(),config.showCoordinates,config.showDistance,WidgetUtils.showWidgetTitles());
            if(key.equals(presentationKey))return;presentationKey=key;lines.clear();
            lines.add(Component.translatable("froghelper.nearby."+kind.name().toLowerCase(Locale.ROOT),count));
            if(result.nearest()!=null) {
                if(config.showDistance)lines.add(Component.translatable("froghelper.nearby.distance",String.format(Locale.ROOT,"%.1f",distance)));
                if(config.showCoordinates) {var p=result.nearest();lines.add(Component.literal((int)Math.floor(p.x())+", "+(int)Math.floor(p.y())+", "+(int)Math.floor(p.z())));}
            }
            var font=client.font;contentWidth=lines.stream().mapToInt(font::width).max().orElse(90)+12;
            contentHeight=10+lines.size()*(font.lineHeight+2)-2;
        }
    }
    @Override public void render(GuiGraphicsExtractor g,DeltaTracker tick) {
        if(!isVisible())return;prepare();var font=Minecraft.getInstance().font;
        g.pose().pushMatrix();g.pose().translate(startX,startY);g.pose().scale(scale,scale);HudSurface.drawPanel(g,contentWidth,contentHeight);
        for(int i=0;i<lines.size();i++)g.text(font,lines.get(i),6,5+i*(font.lineHeight+2),i==0?WidgetTheme.TITLE:WidgetTheme.TEXT_SECONDARY);
        g.pose().popMatrix();
    }
    @Override public int getWidth(){prepare();return Math.round(contentWidth*getScale());}
    @Override public int getHeight(){prepare();return Math.round(contentHeight*getScale());}
    @Override public boolean isVisible(){if(!isInLayout())return false;prepare();return Minecraft.getInstance().gui.screen() instanceof HudEditingScreen || UsefulWorldHighlightRenderHook.isMineLocation() && (settings().showZero || count>0);}
    @Override public String getDisplayName(){return catalog.title();}
}
