package dev.astra.microblocks;

import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.*;

/** Expanded-material and legacy migration coverage through real item actions and three world boots. */
public final class PaletteTest {
    private static final BlockPos HOST=new BlockPos(80,200,0),CHEST=HOST.east(3),PLACED=HOST.east(5);
    private static void check(boolean value,String message) {if(!value) throw new IllegalStateException(message);}
    private static HostMaterial material(String id) {return HostMaterial.find(id).orElseThrow();}
    public static MicroblockVolume mosaic() {
        var result=MicroblockVolume.empty(material("oak_log[axis=x]"));var catalog=HostMaterial.catalog();
        for(int i=0;i<4096;i++) if(i%7!=0) result.add(i%16,i/256,(i/16)%16,catalog.get(i%catalog.size()));
        return result;
    }
    private static Player player(ServerLevel level) {
        return new Player(level,new GameProfile(UUID.randomUUID(),"AstraPaletteTest")) {
            @Override public GameType gameMode() {return GameType.CREATIVE;}
            @Override public void sendOverlayMessage(Component message) {}
        };
    }
    private static TestHostBlockEntity host(ServerLevel level,BlockPos pos) {return (TestHostBlockEntity)level.getBlockEntity(pos);}
    private static MicroblockGrid cell(int i) {var grid=MicroblockGrid.fromLongArray(new long[64]);grid.add(i%16,i/256,(i/16)%16);return grid;}
    private static List<MicroblockVolume> sequence(TestHostBlockEntity host) {
        var states=new ArrayList<MicroblockVolume>();states.add(host.volumeCopy());
        for(int i=0;i<40;i++) {
            check(host.editCells(cell(i),ChiselOperation.REPLACE,HostMaterial.catalog().get(30+i))==1,"palette history edit was no-op");states.add(host.volumeCopy());
        }
        return states;
    }
    private static List<MicroblockVolume> expected() {
        var host=new TestHostBlockEntity(BlockPos.ZERO,AstraMicroblocks.TEST_HOST.defaultBlockState());
        host.initializeDesign(new MicroblockVolume(material("stone_bricks")));return sequence(host);
    }
    public static void phaseZero(ServerLevel level) {
        check(HostMaterial.catalog().size()==250,"supported state catalog changed without updating coverage");
        var tool=new ItemStack(AstraMicroblocks.ASTRA_CHISEL);var player=player(level);player.setItemInHand(InteractionHand.MAIN_HAND,tool);
        ChiselMode.SINGLE.store(tool);ChiselOperation.CUT.store(tool);
        var pos=HOST.south(4);
        for(int x=-2;x<=2;x++) for(int y=-1;y<=4;y++) for(int z=-2;z<=2;z++) level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
        player.setPos(pos.getX()+.5,pos.getY()+3-player.getEyeHeight(),pos.getZ()+.5);player.setXRot(90);
        var hit=new BlockHitResult(new Vec3(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5),Direction.UP,pos,false);
        for(var material:HostMaterial.catalog()) {
            level.setBlock(pos,material.state(),Block.UPDATE_ALL);
            check(HostMaterial.supported(material.state()).orElseThrow()==material,"catalog state round trip");
            check(ChiselCommands.sampleMaterial(player),"vanilla material sampling failed "+material);
            AstraMicroblocks.ASTRA_CHISEL.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
            var host=host(level,pos);check(host!=null && host.occupiedCount()==4095 && host.materialCount(material)==4095,"conversion lost material "+material);
            check(host.volumeCopy().original()==material && host.undoDepth()==1,"conversion history/original mismatch");
            ChiselMaterial.ORIGINAL.store(tool);check(ChiselMaterial.resolve(tool,host)==material,"Original repaired with wrong material");
            check(host.undoEdit() && host.materialCount(material)==4096 && host.redoEdit(),"converted host history failed");
            for(var direction:Direction.values()) {
                var texture=material.texture(direction);check(texture!=null,"missing directional texture");
                var uv=material.uv(direction,new MicroblockRenderMesh.Face(5,6,7,direction).vertex(0));
                check(uv[0]>=0 && uv[0]<=1 && uv[1]>=0 && uv[1]<=1,"invalid UV projection");
            }
        }
        for(var unsupported:new Block[]{Blocks.GLASS,Blocks.OAK_LEAVES,Blocks.CHEST,Blocks.OAK_STAIRS,Blocks.WATER,Blocks.AIR}) {
            level.setBlock(pos,unsupported.defaultBlockState(),Block.UPDATE_ALL);var before=level.getBlockState(pos);
            AstraMicroblocks.ASTRA_CHISEL.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
            check(level.getBlockState(pos)==before,"unsupported block was converted "+unsupported);
        }
        level.removeBlock(pos,false);
        brushMatrix();
        var all=mosaic();var encoded=VolumePalette.write(all);
        var stamped=new TestHostBlockEntity(BlockPos.ZERO,AstraMicroblocks.TEST_HOST.defaultBlockState());
        check(stamped.applyDesign(all)==1 && stamped.volumeCopy().equals(all.withOriginal(HostMaterial.STONE)),"palette stamp lost materials");
        check(stamped.undoEdit() && stamped.materialCount(HostMaterial.STONE)==4096 && stamped.redoEdit()
                && stamped.volumeCopy().equals(all.withOriginal(HostMaterial.STONE)),"palette stamp history failed");
        check(VolumePalette.read(encoded).orElseThrow().equals(all),"250-state palette storage corrupted");
        for(var axis:Direction.Axis.values()) {
            var transformed=all;for(int i=0;i<4;i++) transformed=SculptureData.transform(transformed,axis,false);
            check(transformed.equals(all),"palette/log rotation cycle corrupted");
            check(SculptureData.transform(SculptureData.transform(all,axis,true),axis,true).equals(all),"palette mirror cycle corrupted");
        }
        check(material("oak_log[axis=x]").transformed(Direction.Axis.Y,false)==material("oak_log[axis=z]"),"log grain axis not rotated");
        check(material("oak_log[axis=x]").texture(Direction.EAST).getPath().endsWith("oak_log_top"),"horizontal log missing end grain");
        for(String field:List.of("version","size","bits")) {var bad=encoded.copy();bad.putInt(field,99999);check(VolumePalette.read(bad).isEmpty(),"malformed palette accepted: "+field);}
        var bad=encoded.copy();bad.putString("material_0","minecraft:chest");check(VolumePalette.read(bad).isEmpty(),"unsafe material accepted");
        legacyItem();
        var stack=SculptureData.item(all);var ops=level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        var recovered=ItemStack.CODEC.parse(ops,ItemStack.CODEC.encodeStart(ops,stack).getOrThrow()).getOrThrow();
        check(SculptureData.read(recovered,SculptureData.ITEM_KEY).orElseThrow().equals(all),"palette item codec");
        level.setBlock(HOST,AstraMicroblocks.TEST_HOST.defaultBlockState(),Block.UPDATE_ALL);
        var host=host(level,HOST);host.initializeDesign(new MicroblockVolume(material("stone_bricks")));var states=sequence(host);
        for(int i=0;i<7;i++) host.undoEdit();check(host.volumeCopy().equals(states.get(33)),"palette history pre-save");
        var update=host.getUpdateTag(level.registryAccess());check(update.contains("volume_v4") && !update.contains("history_v4"),"palette sync sends history");
        update.putString("id","astra_microblocks:test_host");
        var client=(TestHostBlockEntity)BlockEntity.loadStatic(HOST,host.getBlockState(),update,level.registryAccess());
        check(client.volumeCopy().equals(host.volumeCopy()),"palette client sync lost cells");
        var saved=host.saveWithFullMetadata(level.registryAccess());saved.getCompound("history_v4").orElseThrow().putInt("undo",100);
        var malformed=(TestHostBlockEntity)BlockEntity.loadStatic(HOST,host.getBlockState(),saved,level.registryAccess());
        check(malformed.volumeCopy().equals(host.volumeCopy()) && malformed.undoDepth()==0,"malformed palette history harmed cells");
        level.setBlock(CHEST,Blocks.CHEST.defaultBlockState(),Block.UPDATE_ALL);var chest=(ChestBlockEntity)level.getBlockEntity(CHEST);
        chest.setItem(0,stack);SculptureData.write(tool,SculptureData.CLIPBOARD_KEY,all);
        for(var m:HostMaterial.catalog()) ChiselMaterial.select(tool,m);
        check(ChiselMaterial.recent(tool).size()==8,"recent material list unbounded");chest.setItem(1,tool);
        System.out.println("ASTRA_TEST: PALETTE_PASS");
    }
    private static void brushMatrix() {
        for(var id:List.of("stone_bricks","red_concrete","birch_log[axis=x]")) for(var mode:ChiselMode.values()) for(var face:Direction.values()) {
            var host=new TestHostBlockEntity(BlockPos.ZERO,AstraMicroblocks.TEST_HOST.defaultBlockState());
            host.initializeDesign(MicroblockVolume.empty(material(id)));
            var cell=new MicroblockHitResolver.Cell(7,8,9);var brush=mode.selection(cell,face);int count=brush.occupiedCount();
            check(host.editCells(brush,ChiselOperation.ADD,material(id))==count,"palette brush add");var before=host.volumeCopy();
            var fill=material("blue_wool");
            check(ChiselPreview.create(before,mode,cell,face,ChiselOperation.REPLACE,fill).affected()==count,"palette brush preview count");
            check(host.editCells(brush,ChiselOperation.REPLACE,fill)==count && host.gridCopy().equals(before.occupancyCopy()),"palette replace changed shape");
            check(host.undoEdit() && host.volumeCopy().equals(before) && host.redoEdit() && host.materialCount(fill)==count,"palette brush history");
        }
    }
    private static void legacyItem() {
        var expected=SculptureTest.pattern(HostMaterial.OAK_PLANKS);var tag=new CompoundTag();tag.putInt("version",1);tag.putString("original","oak");
        tag.putLongArray("cells",expected.occupancyCopy().toLongArray());tag.putLongArray("oak",expected.oakCopy().toLongArray());
        var stack=new ItemStack(AstraMicroblocks.OAK_HOST);CustomData.update(DataComponents.CUSTOM_DATA,stack,data -> data.put(SculptureData.ITEM_KEY,tag));
        check(SculptureData.read(stack,SculptureData.ITEM_KEY).orElseThrow().equals(expected),"0.2 sculpture item migration failed");
    }
    public static void phaseOne(ServerLevel level) {
        var states=expected();var host=host(level,HOST);
        check(host.volumeCopy().equals(states.get(33)) && host.undoDepth()==25 && host.redoDepth()==7,"palette saved history cursor");
        for(int i=32;i>=8;i--) check(host.undoEdit() && host.volumeCopy().equals(states.get(i)),"palette persisted undo");
        for(int i=9;i<=40;i++) check(host.redoEdit() && host.volumeCopy().equals(states.get(i)),"palette persisted redo");
        for(int i=0;i<5;i++) host.undoEdit();
        var chest=(ChestBlockEntity)level.getBlockEntity(CHEST);var all=mosaic();
        check(SculptureData.read(chest.getItem(0),SculptureData.ITEM_KEY).orElseThrow().equals(all),"palette chest item changed");
        var tool=chest.getItem(1);check(SculptureData.read(tool,SculptureData.CLIPBOARD_KEY).orElseThrow().equals(all)
                && ChiselMaterial.recent(tool).size()==8 && ChiselMaterial.custom(tool),"clipboard/material setting lost");
        var player=player(level);player.setPos(PLACED.getX()+2,PLACED.getY()+2,PLACED.getZ()+2);
        var stack=chest.getItem(0).copy();player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        level.setBlock(PLACED.below(),Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
        var hit=new BlockHitResult(Vec3.atLowerCornerOf(PLACED).add(.5,0,.5),Direction.UP,PLACED.below(),false);
        ((SculptureBlockItem)stack.getItem()).place(new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)));
        check(host(level,PLACED).volumeCopy().equals(all),"palette item placement lost cells/original");
        System.out.println("ASTRA_TEST: PALETTE_PERSISTENCE_PASS");
    }
    public static void phaseTwo(ServerLevel level) {
        var states=expected();var host=host(level,HOST);
        check(host.volumeCopy().equals(states.get(35)) && host.undoDepth()==27 && host.redoDepth()==5,"palette second reload cursor");
        for(int i=36;i<=40;i++) check(host.redoEdit() && host.volumeCopy().equals(states.get(i)),"palette second redo");
        for(int i=39;i>=8;i--) check(host.undoEdit() && host.volumeCopy().equals(states.get(i)),"palette second undo");
        check(host(level,PLACED).volumeCopy().equals(mosaic()),"palette placed sculpture restart");
        var drop=Block.getDrops(level.getBlockState(PLACED),level,PLACED,host(level,PLACED)).getFirst();
        check(SculptureData.read(drop,SculptureData.ITEM_KEY).orElseThrow().equals(mosaic()),"palette saved drops");
        System.out.println("ASTRA_TEST: PALETTE_RESTART_PASS");
    }
}
