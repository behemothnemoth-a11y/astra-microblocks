package dev.astra.microblocks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.chat.Component;
import com.mojang.authlib.GameProfile;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.context.UseOnContext;

/** End-to-end material workflow and full bounded history across three real server starts. */
public final class WorkflowTest {
    private static final BlockPos STONE=new BlockPos(32,100,0), OAK=STONE.east();
    private WorkflowTest() {}
    private static void check(boolean ok,String message) { if(!ok) throw new IllegalStateException(message); }
    private static HostMaterial opposite(HostMaterial material) {
        return material==HostMaterial.STONE?HostMaterial.OAK_PLANKS:HostMaterial.STONE;
    }
    private static MicroblockGrid single(int index) {
        var mask=MicroblockGrid.fromLongArray(new long[64]); mask.add(index%16,index/256,(index/16)%16); return mask;
    }
    private static TestHostBlockEntity fresh(TestHostBlock block) {
        return new TestHostBlockEntity(BlockPos.ZERO,block.defaultBlockState());
    }
    private static TestHostBlockEntity host(ServerLevel level,BlockPos pos) {
        check(level.getBlockEntity(pos) instanceof TestHostBlockEntity,"workflow world host missing");
        return (TestHostBlockEntity)level.getBlockEntity(pos);
    }
    private static List<MicroblockVolume> sequence(TestHostBlockEntity host) {
        var states=new ArrayList<MicroblockVolume>(); states.add(host.volumeCopy());
        var original=HostMaterial.of(host.getBlockState());
        for(int i=0;i<40;i++) {
            var operation=switch(i%4) { case 0 -> ChiselOperation.CUT; case 1 -> ChiselOperation.ADD; default -> ChiselOperation.REPLACE; };
            var material=i%4==2?original:opposite(original);
            check(host.editCells(single((i/4)*19),operation,material)==1,"workflow sequence no-op");
            states.add(host.volumeCopy());
        }
        return states;
    }
    private static List<MicroblockVolume> expected(TestHostBlockEntity host) {
        return sequence(fresh(HostMaterial.of(host.getBlockState())==HostMaterial.STONE?AstraMicroblocks.TEST_HOST:AstraMicroblocks.OAK_HOST));
    }
    private static TestHostBlockEntity reload(ServerLevel level,TestHostBlockEntity host) {
        var loaded=(TestHostBlockEntity)BlockEntity.loadStatic(host.getBlockPos(),host.getBlockState(),
                host.saveWithFullMetadata(level.registryAccess()),level.registryAccess());
        check(loaded!=null,"workflow deserialize failed"); return loaded;
    }
    public static void phaseZero(ServerLevel level) {
        for(var block:new TestHostBlock[]{AstraMicroblocks.TEST_HOST,AstraMicroblocks.OAK_HOST}) {
            replacement(block); serialization(level,block); samplingAndItem(level,block);
        }
        for(var pos:new BlockPos[]{STONE,OAK}) {
            level.setBlock(pos,(pos.equals(STONE)?AstraMicroblocks.TEST_HOST:AstraMicroblocks.OAK_HOST).defaultBlockState(),Block.UPDATE_ALL);
            var host=host(level,pos); var states=sequence(host);
            for(int i=39;i>=33;i--) check(host.undoEdit() && host.volumeCopy().equals(states.get(i)),"workflow pre-save undo");
            check(host.undoDepth()==25 && host.redoDepth()==7,"workflow pre-save history counts");
        }
        System.out.println("ASTRA_TEST: WORKFLOW_PASS");
    }
    private static void replacement(TestHostBlock block) {
        for(var mode:ChiselMode.values()) for(var face:Direction.values()) for(var material:HostMaterial.values()) {
            var host=fresh(block);
            host.editCells(ChiselMode.CUBE_4.selection(new MicroblockHitResolver.Cell(7,15,8),Direction.UP),ChiselOperation.CUT);
            host.editCells(ChiselMode.PLANE.selection(new MicroblockHitResolver.Cell(0,8,0),Direction.UP),ChiselOperation.REPLACE,HostMaterial.OAK_PLANKS);
            var before=host.volumeCopy(); var geometry=host.gridCopy(); long revision=host.revision(); int depth=host.undoDepth();
            var cell=new MicroblockHitResolver.Cell(7,15,8);
            var selection=mode.selection(cell,face);
            var preview=ChiselPreview.create(before,mode,cell,face,ChiselOperation.REPLACE,material);
            var covered=MicroblockGrid.fromLongArray(new long[64]);
            for(var box:preview.boxes()) for(int y=box.minY();y<box.maxY();y++) for(int z=box.minZ();z<box.maxZ();z++) for(int x=box.minX();x<box.maxX();x++)
                check(covered.add(x,y,z),"replace preview overlap");
            int changes=0;
            for(int y=0;y<16;y++) for(int z=0;z<16;z++) for(int x=0;x<16;x++) {
                boolean changesCell=selection.isOccupied(x,y,z)&&before.isOccupied(x,y,z)&&before.materialAt(x,y,z)!=material;
                check(covered.isOccupied(x,y,z)==changesCell,"replace preview wrong cell"); if(changesCell) changes++;
            }
            check(host.editCells(selection,ChiselOperation.REPLACE,material)==changes && preview.affected()==changes,"replace count mismatch");
            check(host.gridCopy().equals(geometry),"replace changed collision geometry");
            var after=host.volumeCopy();
            for(int y=0;y<16;y++) for(int z=0;z<16;z++) for(int x=0;x<16;x++)
                check(after.materialAt(x,y,z)==(covered.isOccupied(x,y,z)?material:before.materialAt(x,y,z)),"replace altered other cells");
            if(changes>0) {
                check(host.revision()==revision+1 && host.undoDepth()==depth+1,"replace not atomic");
                check(host.undoEdit() && host.volumeCopy().equals(before),"replace undo material");
                check(host.redoEdit() && host.volumeCopy().equals(after),"replace redo material");
            } else check(host.revision()==revision && host.undoDepth()==depth,"replace no-op history");
            long last=host.revision();
            check(host.editCells(selection,ChiselOperation.REPLACE,material)==0 && host.revision()==last,"same-material replacement changed history");
        }
    }
    private static void serialization(ServerLevel level,TestHostBlock block) {
        var source=fresh(block); var states=sequence(source);
        for(int i=0;i<7;i++) source.undoEdit();
        var loaded=reload(level,source);
        check(loaded.undoDepth()==25 && loaded.redoDepth()==7 && loaded.volumeCopy().equals(states.get(33)),"partial history save");
        for(int i=32;i>=8;i--) check(loaded.undoEdit() && loaded.volumeCopy().equals(states.get(i)),"full saved undo order");
        loaded=reload(level,loaded);
        check(loaded.undoDepth()==0 && loaded.redoDepth()==32,"redo-only history save");
        for(int i=9;i<=40;i++) check(loaded.redoEdit() && loaded.volumeCopy().equals(states.get(i)),"full saved redo order");
        loaded=reload(level,loaded);
        check(loaded.undoDepth()==32 && loaded.redoDepth()==0,"undo-only history save");
        check(loaded.undoEdit(),"history branch preparation");
        long revision=loaded.revision();
        check(loaded.editCells(single(4000),ChiselOperation.REPLACE,HostMaterial.of(loaded.getBlockState()))==0
                && loaded.redoDepth()==1 && loaded.revision()==revision,"replace no-op removed redo");
        check(loaded.editCells(single(4000),ChiselOperation.CUT)==1 && loaded.redoDepth()==0,"real edit did not branch");
        check(reload(level,loaded).redoDepth()==0,"discarded redo returned on load");
        var tag=source.saveWithFullMetadata(level.registryAccess()); tag.remove("history_v3");
        var legacy=(TestHostBlockEntity)BlockEntity.loadStatic(BlockPos.ZERO,source.getBlockState(),tag,level.registryAccess());
        check(legacy!=null && legacy.undoDepth()==1 && legacy.redoDepth()==0,"legacy one-undo migration");
        check(legacy.undoEdit() && legacy.volumeCopy().equals(states.get(32)),"legacy mixed undo corrupted");
        for(long[] bad:new long[][]{new long[]{33,0},new long[]{-1,0},new long[]{1,0},new long[]{0,33}}) {
            tag.putLongArray("history_v3",bad);
            var rejected=(TestHostBlockEntity)BlockEntity.loadStatic(BlockPos.ZERO,source.getBlockState(),tag,level.registryAccess());
            check(rejected!=null && rejected.undoDepth()==1 && rejected.volumeCopy().equals(source.volumeCopy()),"malformed history harmed sculpture");
        }
        check(!source.getUpdateTag(level.registryAccess()).contains("history_v3"),"full history sent to client");
    }
    private static void samplingAndItem(ServerLevel level,TestHostBlock block) {
        var pos=new BlockPos(36,100,4);
        // Ray tests need known air around the fixture, independent of random terrain height.
        for(int x=-3;x<=3;x++) for(int y=-3;y<=3;y++) for(int z=-3;z<=3;z++)
            level.setBlock(pos.offset(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
        level.setBlock(pos,block.defaultBlockState(),Block.UPDATE_ALL);
        var host=host(level,pos); var tool=new ItemStack(AstraMicroblocks.ASTRA_CHISEL);
        Player player=new Player(level,new GameProfile(UUID.randomUUID(),"AstraWorkflowTest")) {
            @Override public GameType gameMode() { return GameType.CREATIVE; }
            @Override public void sendOverlayMessage(Component message) {}
        };
        player.setItemInHand(InteractionHand.MAIN_HAND,tool);
        ChiselMode.CUBE_2.store(tool); ChiselOperation.REPLACE.store(tool);
        var other=opposite(HostMaterial.of(host.getBlockState()));
        (other==HostMaterial.STONE?ChiselMaterial.STONE:ChiselMaterial.OAK).store(tool);
        player.setPos(36,101,4);
        var hit=new BlockHitResult(new Vec3(36+8.5/16,101,4+8.5/16),Direction.UP,pos,false);
        AstraMicroblocks.ASTRA_CHISEL.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
        check(host.materialCount(other)==8 && host.isFull(),"actual replace item failed");
        check(ChiselUndo.undoLast(player,tool) && host.materialCount(other)==0,"replace shortcut undo pipeline");
        check(ChiselUndo.redoLast(player,tool) && host.materialCount(other)==8,"replace shortcut redo pipeline");
        var ops=level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        var saved=ItemStack.CODEC.parse(ops,ItemStack.CODEC.encodeStart(ops,tool).getOrThrow()).getOrThrow();
        check(ChiselOperation.read(saved)==ChiselOperation.REPLACE && ChiselMaterial.read(saved).resolve(host.getBlockState())==other,"workflow tool serialization");
        // Each ray hits a different 1/16 surface cell with the opposite material.
        for(var face:Direction.values()) {
            var point=Vec3.atCenterOf(pos).add(face.getStepX()*0.5,face.getStepY()*0.5,face.getStepZ()*0.5)
                    .add(face.getStepX()==0?1.0/32:0,face.getStepY()==0?1.0/32:0,face.getStepZ()==0?1.0/32:0);
            var cell=MicroblockHitResolver.resolveForRemoval(new BlockHitResult(point,face,pos,false));
            host.editCells(single(cell.x()+cell.z()*16+cell.y()*256),ChiselOperation.REPLACE,other);
            var eye=point.add(face.getStepX()*2,face.getStepY()*2,face.getStepZ()*2);
            player.setPos(eye.x,eye.y-player.getEyeHeight(),eye.z);
            player.setYRot((float)Math.toDegrees(Math.atan2(face.getStepX(),-face.getStepZ())));
            player.setXRot(face==Direction.UP?90:face==Direction.DOWN?-90:0);
            ChiselMaterial.ORIGINAL.store(tool); long revision=host.revision(); int depth=host.undoDepth();
            check(ChiselCommands.sampleMaterial(player),"sampling ray failed: "+face);
            check(ChiselMaterial.read(tool).resolve(host.getBlockState())==other,"sampled wrong cell: "+face);
            check(host.revision()==revision && host.undoDepth()==depth && ChiselOperation.read(tool)==ChiselOperation.REPLACE
                    && ChiselMode.read(tool)==ChiselMode.CUBE_2,"sampling edited world or other settings");
        }
        host.editCells(ChiselMode.CUBE_2.selection(new MicroblockHitResolver.Cell(8,15,8),Direction.UP),ChiselOperation.CUT);
        host.editCells(single(8+8*16+13*256),ChiselOperation.REPLACE,other);
        player.setPos(36+8.5/16,103-player.getEyeHeight(),4+8.5/16); player.setXRot(90);
        ChiselMaterial.ORIGINAL.store(tool);
        check(ChiselCommands.sampleMaterial(player) && ChiselMaterial.read(tool).resolve(host.getBlockState())==other,"cavity floor material sampling");
        level.setBlock(pos.above(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
        check(ChiselCommands.sampleMaterial(player) && ChiselMaterial.read(tool)==ChiselMaterial.STONE,"sampling did not stop at the supported occluding stone block");
        level.removeBlock(pos.above(),false);
        player.setPos(36,110,4); player.setXRot(90);
        check(!ChiselCommands.sampleMaterial(player),"sampling beyond reach");
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        check(!ChiselCommands.sampleMaterial(player),"sampling without tool");
        level.removeBlock(pos,false);
    }
    public static void phaseOne(ServerLevel level) {
        for(var pos:new BlockPos[]{STONE,OAK}) {
            var host=host(level,pos); var states=expected(host);
            check(host.volumeCopy().equals(states.get(33)) && host.undoDepth()==25 && host.redoDepth()==7,"restart lost history cursor");
            for(int i=32;i>=8;i--) check(host.undoEdit() && host.volumeCopy().equals(states.get(i)),"restart full undo mismatch");
            check(!host.undoEdit() && host.redoDepth()==32,"restart history limit");
            for(int i=9;i<=40;i++) check(host.redoEdit() && host.volumeCopy().equals(states.get(i)),"restart full redo mismatch");
            for(int i=39;i>=35;i--) check(host.undoEdit() && host.volumeCopy().equals(states.get(i)),"second save preparation");
        }
        System.out.println("ASTRA_TEST: WORKFLOW_PERSISTENCE_PASS");
    }
    public static void phaseTwo(ServerLevel level) {
        for(var pos:new BlockPos[]{STONE,OAK}) {
            var host=host(level,pos); var states=expected(host);
            check(host.volumeCopy().equals(states.get(35)) && host.undoDepth()==27 && host.redoDepth()==5,"second restart lost cursor");
            for(int i=36;i<=40;i++) check(host.redoEdit() && host.volumeCopy().equals(states.get(i)),"second restart redo mismatch");
            for(int i=39;i>=8;i--) check(host.undoEdit() && host.volumeCopy().equals(states.get(i)),"second restart full undo mismatch");
            for(int i=9;i<=40;i++) check(host.redoEdit() && host.volumeCopy().equals(states.get(i)),"second restart full redo mismatch");
        }
        System.out.println("ASTRA_TEST: WORKFLOW_RESTART_PASS");
    }
}
