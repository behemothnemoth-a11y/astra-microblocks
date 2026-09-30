package dev.astra.microblocks.client;

import dev.astra.microblocks.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.*;

/** Opt-in visual fixture in a fresh disposable world, never the user's world. */
final class MaterialRenderCapture {
    private static int stage;
    private static long readyAt, startedAt;
    private static volatile boolean captured, failed;
    static void tick(Minecraft client) {
        try {
            if(stage==0) {
                stage=1;startedAt=System.nanoTime();
                client.options.renderDistance().set(10);

                client.getWindow().setWindowed(1280,720);
                var settings=new LevelSettings("Astra glass QA",GameType.CREATIVE,
                    new LevelSettings.DifficultySettings(net.minecraft.world.Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT);
                client.createWorldOpenFlows().createFreshLevel("astra-glass-qa-"+System.currentTimeMillis(),settings,
                    new net.minecraft.world.level.levelgen.WorldOptions(184,false,false),
                    net.minecraft.world.level.levelgen.presets.WorldPresets::createTestWorldDimensions,null);
                return;
            }
            if(failed || System.nanoTime()-startedAt>180_000_000_000L) throw new IllegalStateException("visual fixture timeout/failure");
            if(client.level==null || client.player==null || client.getSingleplayerServer()==null) return;
            if(stage==1) {
                client.getSingleplayerServer().submit(()-> {
                    var server=client.getSingleplayerServer();var level=server.overworld();
                    String[] ids={"glass","blue_stained_glass","tinted_glass","copper_grate","magma_block","sea_lantern","prismarine","crimson_stem[axis=y]"};
                    for(int x=-2;x<26;x++) for(int z=-2;z<9;z++) level.setBlock(new BlockPos(x,99,z),Blocks.SMOOTH_QUARTZ.defaultBlockState(),3);
                    for(int i=0;i<ids.length;i++) for(int x=0;x<2;x++) for(int y=0;y<3;y++) {
                        var pos=new BlockPos(i*3+x,100+y,2);
                        var material=HostMaterial.find(ids[i]).orElseThrow();var volume=new MicroblockVolume(material);
                        // A real cavity, plus an opaque center behind the glass in the same host.
                        for(int cy=5;cy<11;cy++) for(int cx=5;cx<11;cx++) for(int cz=0;cz<5;cz++) volume.remove(cx,cy,cz);
                        if(i<4) for(int cy=3;cy<13;cy++) for(int cx=3;cx<13;cx++) for(int cz=10;cz<14;cz++) volume.replace(cx,cy,cz,HostMaterial.find("gold_block").orElseThrow());
                        level.setBlock(pos,AstraMicroblocks.TEST_HOST.defaultBlockState(),3);
                        ((TestHostBlockEntity)level.getBlockEntity(pos)).initializeDesign(volume);
                        level.sendBlockUpdated(pos,level.getBlockState(pos),level.getBlockState(pos),3);
                    }
                    var source=server.createCommandSourceStack();
                    server.getCommands().performPrefixedCommand(source,"time set noon");
                    server.getCommands().performPrefixedCommand(source,"weather clear");
                    server.getCommands().performPrefixedCommand(source,"gamemode spectator @a");
                    server.getCommands().performPrefixedCommand(source,"tp @a 11 105 -18 0 10");
                }).get(30,java.util.concurrent.TimeUnit.SECONDS);
                stage=2;readyAt=System.nanoTime()+12_000_000_000L;return;
            }
            if(stage==3 || stage==5) {
                if(!captured) return;
                if(stage==5) {stage=6;System.out.println("ASTRA_TEST: MATERIAL_VISUAL_CAPTURE_PASS");client.stop();return;}
                captured=false;stage=4;readyAt=System.nanoTime()+2_150_000_000L;return;
            }
            if((stage==2 || stage==4) && System.nanoTime()>readyAt) {
                String name=stage==2?"glass-animation-a.png":"glass-animation-b.png";stage++;
                Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(),image -> {
                    try(image) {Files.createDirectories(Path.of("screenshots"));image.writeToFile(Path.of("screenshots",name));captured=true;}
                    catch(Exception error) {error.printStackTrace();failed=true;}
                });
            }
        } catch(Throwable error) {stage=6;error.printStackTrace();System.out.println("ASTRA_TEST: MATERIAL_VISUAL_CAPTURE_FAIL");client.stop();}
    }
}

