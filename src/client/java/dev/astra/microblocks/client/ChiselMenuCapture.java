package dev.astra.microblocks.client;

import dev.astra.microblocks.ChiselMode;
import dev.astra.microblocks.ChiselOperation;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Optional visual QA in the disposable smoke client; never runs in a normal game. */
final class ChiselMenuCapture {
    private static boolean active, waiting, prepared;
    private static long readyAt;
    private static volatile boolean captured, failed;
    private static int stage, ticks;

    static void start(Minecraft client) {
        active=true;
        client.options.guiScale().set(2);
    }
    private static void show(Minecraft client) {
        ticks=0; waiting=false; captured=false; prepared=true;
        readyAt=System.nanoTime()+1_000_000_000L;
        client.getWindow().setWindowed(stage==0?640:1280,stage==0?480:720);
        client.resizeGui();
        client.gui.setScreen(new DesignScreen(ignored -> {},
                dev.astra.microblocks.SculptureTest.pattern(dev.astra.microblocks.HostMaterial.STONE)));
    }
    static void tick(Minecraft client) {
        if (!active) return;
        if (failed || ++ticks>300) {
            System.out.println("ASTRA_TEST: MENU_CAPTURE_FAIL"); active=false; client.stop(); return;
        }
        if (client.gui.overlay()!=null) return;
        if (!prepared) { show(client); return; }
        if (waiting) {
            if (!captured) return;
            if (++stage==2) {
                System.out.println("ASTRA_TEST: MENU_CAPTURE_PASS"); active=false; client.stop();
            } else show(client);
            return;
        }
        if (System.nanoTime()<readyAt) return;
        waiting=true;
        String name=stage==0?"astra-design-small.png":"astra-design-large.png";
        Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(),image -> {
            try (image) {
                Files.createDirectories(Path.of("screenshots"));
                image.writeToFile(Path.of("screenshots",name));
                captured=true;
            } catch (Exception failure) { failure.printStackTrace(); failed=true; }
        });
    }
}
