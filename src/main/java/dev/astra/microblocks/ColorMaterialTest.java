package dev.astra.microblocks;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

/** RGB persistence and worst-case palette coverage without millions of registered objects. */
public final class ColorMaterialTest {
    private static final BlockPos POS=new BlockPos(208,200,0);
    private static void check(boolean value,String message) {if(!value) throw new IllegalStateException(message);}
    public static MicroblockVolume gradient() {
        var volume=MicroblockVolume.empty(HostMaterial.color(0x123456));
        for(int i=0;i<4096;i++) volume.add(i%16,i/256,(i/16)%16,HostMaterial.color(i*4097));
        return volume;
    }
    public static void phaseZero(ServerLevel level) {
        var retained=HostMaterial.color(0x123456);
        for(int i=0;i<12000;i++) HostMaterial.color(i);
        check(HostMaterial.colorCacheSize()<=8192,"RGB allocation cache grew without limit");
        var recovered=HostMaterial.find("astra_microblocks:rgb_123456").orElseThrow();
        check(recovered.equals(retained) && recovered.hashCode()==retained.hashCode(),"evicted color changed identity semantics");
        check(HostMaterial.find("astra_microblocks:rgb_ABCDEF").orElseThrow().id().endsWith("abcdef"),"RGB canonicalization");
        for(String bad:new String[]{"", "#123456", "astra_microblocks:rgb_12345", "astra_microblocks:rgb_1234567", "astra_microblocks:rgb_gggggg"})
            check(HostMaterial.find(bad).isEmpty(),"malformed RGB accepted");
        var pair=MicroblockVolume.empty(retained);pair.add(0,0,0,retained);pair.add(1,0,0,recovered);
        check(pair.materials().size()==1 && pair.count(recovered)==2,"same RGB made duplicate palette entries");
        check(!pair.replace(0,0,0,recovered),"same RGB creates no-op history");
        check(MicroblockRenderMesh.buildGreedy(pair).size()==6,"equal colors did not merge");
        check(MicroblockRenderMesh.buildGreedy(new MicroblockVolume(retained)).size()==6,"uniform color geometry grew");
        var gradient=gradient();var tag=VolumePalette.write(gradient);
        check(tag.getIntOr("size",0)==4096,"unique RGB cells missing");
        check(tag.getLongArray("cells").orElseThrow().length==1024,"worst-case palette packing is unbounded");
        long start=System.nanoTime();
        for(int i=0;i<8;i++) check(VolumePalette.read(tag).orElseThrow().equals(gradient),"4096-color palette roundtrip");
        long decodeMs=(System.nanoTime()-start)/1_000_000;
        start=System.nanoTime();var mesh=MicroblockRenderMesh.buildGreedy(gradient);
        check(mesh.size()==1536,"colors exposed hidden solid interior cells");
        long meshMs=(System.nanoTime()-start)/1_000_000;
        var isolated=MicroblockVolume.empty(retained);
        for(int i=0;i<4096;i++) if(((i%16)+(i/256)+((i/16)%16))%2==0)
            isolated.add(i%16,i/256,(i/16)%16,HostMaterial.color(i*4097));
        check(MicroblockRenderMesh.buildGreedy(isolated).size()==12288,"isolated RGB surface bound changed");
        var player=new net.minecraft.world.entity.player.Player(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"AstraRGB")) {
            @Override public net.minecraft.world.level.GameType gameMode() {return net.minecraft.world.level.GameType.CREATIVE;}
            @Override public void sendOverlayMessage(net.minecraft.network.chat.Component message) {}
        };
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(AstraMicroblocks.ASTRA_CHISEL));
        check(ChiselCommands.giveColorBlock(player,"FF0080"),"Creative color block command");
        check(!ChiselCommands.giveColorBlock(player,"badhex"),"invalid color block command");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);
        check(!ChiselCommands.giveColorBlock(player,"000000"),"color block without chisel");
        var item=SculptureData.item(gradient);
        check(SculptureData.read(item,SculptureData.ITEM_KEY).orElseThrow().equals(gradient),"RGB sculpture item");
        for(var axis:Direction.Axis.values()) {
            var transformed=gradient;for(int i=0;i<4;i++) transformed=SculptureData.transform(transformed,axis,false);
            check(transformed.equals(gradient),"RGB transform cycle");
        }
        level.setBlock(POS,AstraMicroblocks.TEST_HOST.defaultBlockState(),Block.UPDATE_ALL);
        var host=(TestHostBlockEntity)level.getBlockEntity(POS);host.initializeDesign(gradient);
        var selected=new MicroblockGrid();selected.clear();selected.add(1,1,1);
        host.editCells(selected,ChiselOperation.REPLACE,HostMaterial.color(0xff0080));
        check(host.undoEdit() && host.volumeCopy().equals(gradient) && host.redoEdit(),"RGB undo/redo");
        System.out.println("ASTRA_TEST: RGB_PERFORMANCE_PASS colors=4096 packed_longs=1024 solid_quads=1536 isolated_quads=12288 decode8_ms="+decodeMs+" mesh_ms="+meshMs);
        System.out.println("ASTRA_TEST: RGB_MATERIAL_PASS");
    }
    public static void persisted(ServerLevel level,boolean last) {
        var host=(TestHostBlockEntity)level.getBlockEntity(POS);
        check(host.materialAt(1,1,1).equals(HostMaterial.color(0xff0080)),"saved RGB edit lost");
        check(host.undoEdit() && host.volumeCopy().equals(gradient()) && host.redoEdit(),"saved RGB history lost");
        System.out.println("ASTRA_TEST: RGB_"+(last?"RESTART":"PERSISTENCE")+"_PASS");
    }
}
