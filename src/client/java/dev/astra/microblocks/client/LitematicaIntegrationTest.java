package dev.astra.microblocks.client;

import dev.astra.microblocks.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import java.nio.file.Path;

/** Optional real-Litematica integration gate in a disposable integrated world. */
public final class LitematicaIntegrationTest {
    private static boolean started,finished;
    private static long startedAt;
    private LitematicaIntegrationTest() {}
    public static void tick(Minecraft client) {
        if(finished) return;
        try {
            if(!started) {
                started=true;startedAt=System.nanoTime();
                Class.forName("fi.dy.masa.litematica.schematic.LitematicaSchematic");
                var settings=new LevelSettings("Astra integration",GameType.CREATIVE,
                        new LevelSettings.DifficultySettings(net.minecraft.world.Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT);
                client.createWorldOpenFlows().createFreshLevel("astra-integration-"+System.currentTimeMillis(),settings,
                        new net.minecraft.world.level.levelgen.WorldOptions(9417,false,false),
                        net.minecraft.world.level.levelgen.presets.WorldPresets::createTestWorldDimensions,null);
                return;
            }
            if(System.nanoTime()-startedAt>180_000_000_000L) throw new IllegalStateException("integration world startup timeout");
            if(client.level==null || client.player==null || client.getSingleplayerServer()==null) return;
            finished=true;
            run(client);
            System.out.println("ASTRA_TEST: LITEMATICA_INTEGRATION_PASS");client.stop();
        } catch(Throwable error) {finished=true;error.printStackTrace();System.out.println("ASTRA_TEST: LITEMATICA_INTEGRATION_FAIL");client.stop();}
    }
    private static CompoundTag vec(int x,int y,int z) {var t=new CompoundTag();t.putInt("x",x);t.putInt("y",y);t.putInt("z",z);return t;}
    private static void check(boolean value,String message) {if(!value) throw new IllegalStateException(message);}
    private static void run(Minecraft client) throws Exception {
        var configClass=Class.forName("fi.dy.masa.litematica.config.Configs$Generic");
        var all=Class.forName("fi.dy.masa.litematica.util.ReplaceBehavior").getField("ALL").get(null);
        for(var key:new String[]{"PASTE_REPLACE_BEHAVIOR","PLACEMENT_REPLACE_BEHAVIOR"}) {
            var option=configClass.getField(key).get(null);
            option.getClass().getMethod("setOptionListValue",Class.forName("fi.dy.masa.malilib.config.IConfigOptionListEntry")).invoke(option,all);
        }
        var source=SchematicTransformTest.specimen();
        var host=new TestHostBlockEntity(BlockPos.ZERO,AstraMicroblocks.TEST_HOST.defaultBlockState());host.initializeDesign(source);
        var root=NbtIo.readCompressed(Path.of(System.getProperty("astra.meshFixture")),NbtAccounter.unlimitedHeap());
        var regions=root.getCompound("Regions").orElseThrow();
        var region=regions.getCompound(regions.keySet().iterator().next()).orElseThrow().copy();
        var tiles=new ListTag();tiles.add(host.saveWithFullMetadata(client.level.registryAccess()));
        region.put("TileEntities",tiles);region.put("Size",vec(1,1,1));region.put("Position",vec(0,0,0));region.putLongArray("BlockStates",new long[]{1});
        var newRegions=new CompoundTag();newRegions.put("test",region);root.put("Regions",newRegions);
        var meta=root.getCompound("Metadata").orElseThrow();meta.put("EnclosingSize",vec(1,1,1));meta.putInt("TotalBlocks",1);meta.putInt("TotalVolume",1);
        Class<?> schematicType=Class.forName("fi.dy.masa.litematica.schematic.LitematicaSchematic");
        Class<?> fileType=Class.forName("fi.dy.masa.litematica.util.FileType");
        var schematic=schematicType.getConstructor(Path.class,CompoundTag.class,fileType)
                .newInstance(Path.of("astra-integration.litematic"),root,fileType.getField("LITEMATICA_SCHEMATIC").get(null));
        Class<?> placementType=Class.forName("fi.dy.masa.litematica.schematic.placement.SchematicPlacement");
        Class<?> consumerType=Class.forName("fi.dy.masa.malilib.gui.interfaces.IMessageConsumer");
        Class<?> rendererType=Class.forName("fi.dy.masa.litematica.render.IWorldSchematicRenderer");
        var renderer=java.lang.reflect.Proxy.newProxyInstance(rendererType.getClassLoader(),new Class<?>[]{rendererType},(p,m,a)->null);
        var preview=(Level)Class.forName("fi.dy.masa.litematica.world.WorldSchematic").getConstructors()[0].newInstance(
                new net.minecraft.client.multiplayer.ClientLevel.ClientLevelData(net.minecraft.world.Difficulty.PEACEFUL,false,true),
                client.level.registryAccess(),client.level.dimensionTypeRegistration(),renderer);
        var paste=schematicType.getMethod("placeToWorld",Level.class,placementType,boolean.class);
        int count=0;
        for(var mirror:Mirror.values()) for(var rotation:Rotation.values())
        for(var subMirror:Mirror.values()) for(var subRotation:Rotation.values()) {
            var pos=new BlockPos(8+(count%12)*3,100,8+(count/12)*3);count++;
            var placement=placementType.getMethod("createFor",schematicType,BlockPos.class,String.class,boolean.class,boolean.class)
                    .invoke(null,schematic,pos,"test",true,true);
            placementType.getMethod("setMirror",Mirror.class,consumerType).invoke(placement,mirror,null);
            placementType.getMethod("setRotation",Rotation.class,consumerType).invoke(placement,rotation,null);
            var sub=placementType.getMethod("getRelativeSubRegionPlacement",String.class).invoke(placement,"test");
            var setSubMirror=sub.getClass().getDeclaredMethod("setMirror",Mirror.class);setSubMirror.setAccessible(true);setSubMirror.invoke(sub,subMirror);
            var setSubRotation=sub.getClass().getDeclaredMethod("setRotation",Rotation.class);setSubRotation.setAccessible(true);setSubRotation.invoke(sub,subRotation);
            // PositionUtils applies the whole placement first, then the subregion transform.
            var expected=SchematicTransformTest.expected(SchematicTransformTest.expected(source,mirror,rotation),subMirror,subRotation);
            check((boolean)paste.invoke(schematic,preview,placement,false),"schematic preview placement failed");
            var previewHost=(TestHostBlockEntity)preview.getBlockEntity(pos);
            check(previewHost!=null && previewHost.volumeCopy().equals(expected),"Litematica hologram cells "+mirror+" "+rotation+" / "+subMirror+" "+subRotation);
            var server=client.getSingleplayerServer();
            server.submit(()-> {
                try {
                    var level=server.overworld();
                    check((boolean)paste.invoke(schematic,level,placement,false),"server paste failed");
                    var pasted=(TestHostBlockEntity)level.getBlockEntity(pos);
                    check(pasted!=null && pasted.volumeCopy().equals(expected),"Litematica pasted cells "+mirror+" "+rotation+" / "+subMirror+" "+subRotation);
                    var saved=pasted.saveWithFullMetadata(level.registryAccess());
                    SchematicTransformTest.load(pasted,saved,level.registryAccess());
                    check(pasted.volumeCopy().equals(expected),"pasted NBT reload changed cells");
                    // Repeat exact same paste over an edited host: same block state/revision must not retain stale cells.
                    pasted.applyDesign(new MicroblockVolume(HostMaterial.STONE));
                    paste.invoke(schematic,level,placement,false);
                    check(pasted.volumeCopy().equals(expected) || ((TestHostBlockEntity)level.getBlockEntity(pos)).volumeCopy().equals(expected),"Litematica overwrite failed");
                } catch(Exception e) {throw new RuntimeException(e);}
            }).get(30,java.util.concurrent.TimeUnit.SECONDS);
        }
        System.out.println("ASTRA_TEST: LITEMATICA_TRANSFORMS="+count+" preview+paste+overwrite+reload");
    }
}
