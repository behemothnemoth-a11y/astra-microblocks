package dev.astra.microblocks;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import com.mojang.authlib.GameProfile;

/** Portable sculptures, command workflows and inventory persistence over three real world boots. */
public final class SculptureTest {
    private static final BlockPos CHEST=new BlockPos(48,200,0), PLACED=CHEST.east(2);
    private SculptureTest() {}
    private static void check(boolean value,String message) { if(!value) throw new IllegalStateException(message); }
    public static MicroblockVolume pattern(HostMaterial original) {
        var cells=MicroblockGrid.fromLongArray(new long[64]); var oak=MicroblockGrid.fromLongArray(new long[64]);
        for(int y=0;y<16;y++) for(int z=0;z<16;z++) for(int x=0;x<16;x++) {
            if(y<2 || (x<3 && z<5) || (y>11 && x<10 && z>7)) {
                cells.add(x,y,z); if((x+2*y+z)%5<2) oak.add(x,y,z);
            }
        }
        return new MicroblockVolume(cells,oak,original);
    }
    private static Player player(ServerLevel level,boolean creative) {
        return new Player(level,new GameProfile(UUID.randomUUID(),"AstraSculptureTest")) {
            @Override public GameType gameMode() {return creative?GameType.CREATIVE:GameType.SURVIVAL;}
            @Override public void sendOverlayMessage(Component message) {}
        };
    }
    private static TestHostBlockEntity host(ServerLevel level,BlockPos pos) {return (TestHostBlockEntity)level.getBlockEntity(pos);}
    private static void air(ServerLevel level,BlockPos pos) {
        for(int x=-3;x<=3;x++) for(int y=-3;y<=3;y++) for(int z=-3;z<=3;z++) level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
    }
    private static void place(ServerLevel level,BlockPos pos,ItemStack stack) {
        level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
        var player=player(level,false); player.setPos(pos.getX()+2,pos.getY()+1,pos.getZ()+2);
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var hit=new BlockHitResult(new Vec3(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5),Direction.UP,pos.below(),false);
        ((SculptureBlockItem)stack.getItem()).place(new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)));
    }
    public static void phaseZero(ServerLevel level) {
        for(var original:HostMaterial.values()) {
            var design=pattern(original);
            for(var axis:Direction.Axis.values()) {
                var rotated=design;
                for(int i=0;i<4;i++) rotated=SculptureData.transform(rotated,axis,false);
                check(rotated.equals(design),"four rotations did not restore design");
                check(SculptureData.transform(SculptureData.transform(design,axis,true),axis,true).equals(design),"double mirror mismatch");
                var turned=SculptureData.transform(design,axis,false);
                check(!turned.equals(design) && turned.occupiedCount()==design.occupiedCount()
                        && turned.count(HostMaterial.OAK_PLANKS)==design.count(HostMaterial.OAK_PLANKS),"transform lost shape/materials");
            }
            var stack=SculptureData.item(design);
            var rotatedItem=stack.copy(); var holder=player(level,true);
            holder.setItemInHand(InteractionHand.MAIN_HAND,rotatedItem); holder.setShiftKeyDown(true);
            ((SculptureBlockItem)rotatedItem.getItem()).use(level,holder,InteractionHand.MAIN_HAND);
            check(SculptureData.read(rotatedItem,SculptureData.ITEM_KEY).orElseThrow()
                    .equals(SculptureData.transform(design,Direction.Axis.Y,false)),"held sculpture did not rotate");
            check(SculptureData.read(stack,SculptureData.ITEM_KEY).orElseThrow().equals(design),"rotating copied item mutated original");
            check(!ItemStack.isSameItemSameComponents(stack,rotatedItem)
                    && ItemStack.isSameItemSameComponents(stack,stack.copy()),"different designs merge into one inventory stack");
            var ops=level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            var encoded=ItemStack.CODEC.encodeStart(ops,stack).getOrThrow();
            var decoded=ItemStack.CODEC.parse(ops,encoded).getOrThrow();
            check(SculptureData.read(decoded,SculptureData.ITEM_KEY).orElseThrow().equals(design),"sculpture item codec");
            var pos=new BlockPos(42,200,10); air(level,pos); var copy=stack.copy();
            place(level,pos,copy);
            check(copy.isEmpty(),"survival placement did not consume item");
            check(host(level,pos)!=null && host(level,pos).volumeCopy().equals(design),"real item placement changed cells");
            check(host(level,pos).undoDepth()==0,"placed item imported world history");
            var picked=level.getBlockState(pos).getCloneItemStack(level,pos,false);
            check(SculptureData.read(picked,SculptureData.ITEM_KEY).orElseThrow().equals(design),"normal pick lost sculpture");
            var drops=Block.getDrops(level.getBlockState(pos),level,pos,host(level,pos));
            check(drops.size()==1 && SculptureData.read(drops.getFirst(),SculptureData.ITEM_KEY).orElseThrow().equals(design),"break drops lost sculpture");
            var miner=player(level,false); miner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
            if(original==HostMaterial.STONE) check(miner.hasCorrectToolForDrops(level.getBlockState(pos)),"stone host missing mining tool tag");
            var clonePos=pos.east(2); place(level,clonePos,drops.getFirst().copy());
            check(host(level,clonePos).volumeCopy().equals(design),"drop-and-replace changed sculpture");
            level.removeBlock(pos,false); level.removeBlock(clonePos,false);
        }
        commands(level);
        obstruction(level);
        level.setBlock(CHEST,Blocks.CHEST.defaultBlockState(),Block.UPDATE_ALL);
        var chest=(ChestBlockEntity)level.getBlockEntity(CHEST);
        var transformed=SculptureData.transform(pattern(HostMaterial.OAK_PLANKS),Direction.Axis.Z,false);
        chest.setItem(0,SculptureData.item(transformed));
        var tool=new ItemStack(AstraMicroblocks.ASTRA_CHISEL); SculptureData.write(tool,SculptureData.CLIPBOARD_KEY,transformed);
        ChiselMaterial.OAK.store(tool); ChiselOperation.REPLACE.store(tool); chest.setItem(1,tool);
        System.out.println("ASTRA_TEST: SCULPTURE_PASS");
    }
    private static void obstruction(ServerLevel level) {
        var pos=new BlockPos(42,200,20); air(level,pos);
        var chunk=new net.minecraft.world.level.ChunkPos(pos.getX()>>4,pos.getZ()>>4);
        var ready=level.getChunkSource().addTicketAndLoadWithRadius(net.minecraft.server.level.TicketType.FORCED,chunk,2);
        level.getServer().managedBlock(ready::isDone); ready.join();
        var cow=net.minecraft.world.entity.EntityTypes.COW.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        check(cow!=null,"sculpture obstruction entity");
        cow.setNoAi(true); cow.setPos(pos.getX()+0.5,pos.getY()+0.25,pos.getZ()+0.5);
        check(level.addFreshEntity(cow),"sculpture obstruction entity not added");
        try {
            check(level.getEntities((net.minecraft.world.entity.Entity)null,new net.minecraft.world.phys.AABB(pos),e -> e==cow).size()==1,
                    "sculpture obstruction fixture not queryable");
            var full=SculptureData.item(new MicroblockVolume(HostMaterial.STONE));
            place(level,pos,full);
            check(level.getBlockState(pos).isAir() && full.getCount()==1,"sculpture placement overlapped entity");
            // A thin floor fits beneath the same entity: reject actual cells, not the full block bounds.
            var cells=MicroblockGrid.fromLongArray(new long[64]);
            for(int x=0;x<16;x++) for(int z=0;z<16;z++) cells.add(x,0,z);
            var floor=new MicroblockVolume(cells,MicroblockGrid.fromLongArray(new long[64]),HostMaterial.STONE);
            var item=SculptureData.item(floor); place(level,pos,item);
            check(item.isEmpty() && host(level,pos).volumeCopy().equals(floor),"empty sculpture space incorrectly blocked placement");
            var host=host(level,pos); long revision=host.revision();
            check(host.applyDesign(new MicroblockVolume(HostMaterial.OAK_PLANKS))==-1
                    && host.revision()==revision && host.undoDepth()==0 && host.volumeCopy().equals(floor),"blocked stamp mutated host/history");
            cow.setPos(pos.getX()+3,pos.getY(),pos.getZ()+3);
            check(host.applyDesign(new MicroblockVolume(HostMaterial.OAK_PLANKS))==1 && host.undoDepth()==1
                    && host.volumeCopy().count(HostMaterial.OAK_PLANKS)==4096,"stamp after obstruction removed");
            check(host.undoEdit() && host.volumeCopy().equals(floor),"stamp did not preserve target original/history");
            cow.setPos(pos.getX()+0.5,pos.getY()+0.25,pos.getZ()+0.5);
            check(!host.redoEdit() && host.redoDepth()==1 && host.volumeCopy().equals(floor),"blocked stamp redo consumed history");
            cow.setPos(pos.getX()+3,pos.getY(),pos.getZ()+3);
            check(host.redoEdit(),"stamp redo did not recover");
        } finally {
            cow.discard(); level.removeBlock(pos,false);
            level.getChunkSource().removeTicketWithRadius(net.minecraft.server.level.TicketType.FORCED,chunk,2);
        }
    }
    private static void commands(ServerLevel level) {
        var pos=new BlockPos(42,200,10); air(level,pos);
        level.setBlock(pos,AstraMicroblocks.TEST_HOST.defaultBlockState(),Block.UPDATE_ALL);
        var host=host(level,pos); var source=pattern(HostMaterial.STONE); host.initializeDesign(source);
        var player=player(level,true); player.setPos(42.5,203-player.getEyeHeight(),10.5); player.setXRot(90);
        var tool=new ItemStack(AstraMicroblocks.ASTRA_CHISEL); player.setItemInHand(InteractionHand.MAIN_HAND,tool);
        ChiselMode.CUBE_4.store(tool); long revision=host.revision();
        check(DesignCommands.execute(player,"copy") && host.revision()==revision,"design copy mutated source");
        check(SculptureData.read(tool,SculptureData.CLIPBOARD_KEY).orElseThrow().equals(source),"clipboard wrong cells");
        check(DesignCommands.execute(player,"rotate_y"),"design rotate command");
        var transformed=SculptureData.transform(source,Direction.Axis.Y,false);
        check(SculptureData.read(tool,SculptureData.CLIPBOARD_KEY).orElseThrow().equals(transformed),"clipboard transform wrong");
        check(DesignCommands.execute(player,"stamp") && host.volumeCopy().equals(transformed) && host.undoDepth()==1,"stamp not atomic");
        check(ChiselUndo.undoLast(player,tool) && host.volumeCopy().equals(source),"stamp undo wrong");
        check(ChiselUndo.redoLast(player,tool) && host.volumeCopy().equals(transformed),"stamp redo wrong");
        revision=host.revision(); check(DesignCommands.execute(player,"stamp") && host.revision()==revision,"same design added history");
        check(DesignCommands.execute(player,"export"),"design item export");
        check(ChiselMode.read(tool)==ChiselMode.CUBE_4,"clipboard overwrote tool settings");
        var survival=player(level,false); survival.setItemInHand(InteractionHand.MAIN_HAND,tool.copy());
        check(!DesignCommands.execute(survival,"export"),"survival exported free sculpture");
        var malformed=new ItemStack(AstraMicroblocks.TEST_HOST);
        CustomData.update(DataComponents.CUSTOM_DATA,malformed,tag -> tag.put(SculptureData.ITEM_KEY,new net.minecraft.nbt.CompoundTag()));
        var invalidPos=pos.east(2); place(level,invalidPos,malformed);
        check(level.getBlockState(invalidPos).isAir() && malformed.getCount()==1,"malformed design placed/consumed");
        player.setPos(42,210,10); check(!DesignCommands.execute(player,"copy") && !DesignCommands.execute(player,"stamp"),"design command escaped reach");
        level.removeBlock(pos,false);
    }
    public static void phaseOne(ServerLevel level) {
        var expected=SculptureData.transform(pattern(HostMaterial.OAK_PLANKS),Direction.Axis.Z,false);
        var chest=(ChestBlockEntity)level.getBlockEntity(CHEST);
        check(chest!=null && SculptureData.read(chest.getItem(0),SculptureData.ITEM_KEY).orElseThrow().equals(expected),"inventory sculpture lost after restart");
        check(SculptureData.read(chest.getItem(1),SculptureData.CLIPBOARD_KEY).orElseThrow().equals(expected)
                && ChiselOperation.read(chest.getItem(1))==ChiselOperation.REPLACE,"saved clipboard/tool settings lost");
        place(level,PLACED,chest.getItem(0).copy());
        check(host(level,PLACED).volumeCopy().equals(expected),"reloaded item placement mismatch");
        System.out.println("ASTRA_TEST: SCULPTURE_PERSISTENCE_PASS");
    }
    public static void phaseTwo(ServerLevel level) {
        var expected=SculptureData.transform(pattern(HostMaterial.OAK_PLANKS),Direction.Axis.Z,false);
        check(host(level,PLACED).volumeCopy().equals(expected),"placed sculpture changed on restart");
        var drops=Block.getDrops(level.getBlockState(PLACED),level,PLACED,host(level,PLACED));
        check(SculptureData.read(drops.getFirst(),SculptureData.ITEM_KEY).orElseThrow().equals(expected),"reloaded sculpture drops wrong");
        System.out.println("ASTRA_TEST: SCULPTURE_RESTART_PASS");
    }
}
