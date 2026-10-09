package ru.wilyfox.client.hud.widget;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.internal.HudFrameClock;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.protocol.ProtocolGraphTelemetry;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ProtocolGraphClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                boolean added = ConfigManager.get().mainLayout.widgets.add("ProtocolGraphWidget");
                ConfigManager.layoutChanged();
                boolean nativeRenderer = ConfigManager.get().render.nativeRenderer;
                ConfigManager.get().render.nativeRenderer = true;
                var telemetry = ProtocolGraphTelemetry.getInstance();
                telemetry.reset();
                try {
                    var widget = new ProtocolGraphWidget(0, 0, HudLayer.CONTENT);
                    HudFrameClock.advance();
                    int emptyHeight = widget.getHeight();
                    Object snapshot = field(widget, "cachedSnapshot");
                    for (int i = 0; i < 1000; i++) widget.getHeight();
                    expect(field(widget, "cachedSnapshot") == snapshot, "Sizing repeatedly rebuilt the graph snapshot");
                    var state = new GuiRenderState();
                    widget.render(new GuiGraphicsExtractor(client, state, 0, 0), DeltaTracker.ZERO);
                    int oldPixels = oldPixelCount(field(widget, "cachedLayout"), telemetry.snapshot());
                    var elements = new AtomicInteger();
                    var graphs = new AtomicInteger();
                    var vertices = new AtomicInteger();
                    VertexConsumer counter = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                            new Class<?>[]{VertexConsumer.class}, (proxy, method, args) -> {
                                if (method.getName().startsWith("addVertex")) vertices.incrementAndGet();
                                return method.getReturnType() == void.class ? null : proxy;
                            });
                    state.forEachElement(element -> {
                        elements.incrementAndGet();
                        if (element instanceof ProtocolGraphRenderState) {
                            graphs.incrementAndGet();
                            element.buildVertices(counter);
                        }
                    }, GuiRenderState.TraverseRange.ALL);
                    expect(graphs.get() == 1, "The graph must be one GUI element");
                    expect(elements.get() < 20, "Graph still submits a GUI element per pixel");
                    expect(vertices.get() < oldPixels, "Graph geometry did not reduce vertex load");
                    System.out.println("Protocol graph: old pixel elements=" + oldPixels + ", new GUI elements="
                            + elements.get() + ", graph vertices=" + vertices.get());
                    telemetry.onPayloadReceived("bosstimers", 30);
                    HudFrameClock.advance();
                    expect(widget.getHeight() > emptyHeight, "Activity must update the next frame's height");
                    state.reset();
                    widget.render(new GuiGraphicsExtractor(client, state, 0, 0), DeltaTracker.ZERO);
                    expect(field(widget, "cachedSnapshot") != snapshot, "Frame cache must refresh live data");
                } finally {
                    telemetry.reset();
                    ConfigManager.get().render.nativeRenderer = nativeRenderer;
                    if (added) ConfigManager.get().mainLayout.widgets.remove("ProtocolGraphWidget");
                    ConfigManager.layoutChanged();
                }
            });
        }
    }

    // Count the pixel submissions of the previous algorithm without running that expensive renderer.
    private static int oldPixelCount(Object layout, ProtocolGraphTelemetry.GraphSnapshot snapshot) {
        var positions = new LinkedHashMap<String, int[]>();
        for (Object positioned : (List<?>) field(layout, "nodes")) {
            var node = (ProtocolGraphTelemetry.GraphNodeSnapshot) field(positioned, "node");
            positions.put(node.id(), new int[]{(int) field(positioned, "dotX"), (int) field(positioned, "dotY")});
        }
        int result = positions.size() * (circlePixels(6) + circlePixels(4));
        for (var edge : snapshot.edges()) {
            int[] from = positions.get(edge.fromNodeId()), to = positions.get(edge.toNodeId());
            if (from != null && to != null) result += 5 * (1 + Math.max(Math.abs(from[0] - to[0]), Math.abs(from[1] - to[1])));
        }
        return result;
    }

    private static int circlePixels(int radius) {
        int pixels = 0;
        for (int y = -radius; y <= radius; y++) for (int x = -radius; x <= radius; x++)
            if (x * x + y * y <= radius * radius) pixels++;
        return pixels;
    }
    private static Object field(Object object, String name) {
        try {
            var field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void expect(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
