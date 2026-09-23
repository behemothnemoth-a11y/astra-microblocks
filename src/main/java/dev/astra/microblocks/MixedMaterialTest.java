package dev.astra.microblocks;

import java.util.ArrayList;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Mixed cell snapshots, migration, no-op history, and two real world restarts. */
public final class MixedMaterialTest {
    private static final BlockPos STONE = new BlockPos(28,100,0), OAK = STONE.east();
    private MixedMaterialTest() {}
    private static void check(boolean ok,String message) { if (!ok) throw new IllegalStateException(message); }
    private static HostMaterial opposite(HostMaterial material) {
        return material == HostMaterial.STONE ? HostMaterial.OAK_PLANKS : HostMaterial.STONE;
    }
    private static MicroblockGrid mask(int index) {
        var grid=MicroblockGrid.fromLongArray(new long[64]);
        grid.add(index%16,index/256,(index/16)%16); return grid;
    }
    private static MicroblockGrid inlay() {
        return ChiselMode.CUBE_4.selection(new MicroblockHitResolver.Cell(7,15,8),Direction.UP);
    }
    public static void phaseZero(ServerLevel level) {
        for (var block : new TestHostBlock[]{AstraMicroblocks.TEST_HOST,AstraMicroblocks.OAK_HOST}) {
            var original=HostMaterial.of(block.defaultBlockState());
            var other=opposite(original);
            for (var mode : ChiselMode.values()) for (var face : Direction.values()) {
                var host=new TestHostBlockEntity(BlockPos.ZERO,block.defaultBlockState());
                var selection=mode.selection(new MicroblockHitResolver.Cell(7,15,8),face);
                check(host.editCells(selection,ChiselOperation.CUT)==selection.occupiedCount(),"mixed brush cut");
                var cut=host.volumeCopy();
                check(host.editCells(selection,ChiselOperation.ADD,other)==selection.occupiedCount(),"mixed brush add");
                check(host.materialCount(other)==selection.occupiedCount() && host.isFull(),"mixed brush counts");
                var mixed=host.volumeCopy();
                check(host.undoEdit() && host.volumeCopy().equals(cut),"mixed brush undo");
                check(host.redoEdit() && host.volumeCopy().equals(mixed),"mixed brush redo");
                long revision=host.revision();
                check(host.editCells(selection,ChiselOperation.ADD,original)==0 && host.revision()==revision
                        && host.volumeCopy().equals(mixed),"occupied cells repainted");
                check(host.undoEdit(),"mixed brush second undo");
                check(host.editCells(selection,ChiselOperation.CUT)==0 && host.redoDepth()==1,"mixed no-op lost redo");
                check(host.redoEdit(),"mixed no-op redo");
                var copy=host.volumeCopy(); copy.remove(0,0,0);
                check(host.isOccupied(0,0,0),"volume copy aliased host");
                var loaded=load(level,host,host.saveWithFullMetadata(level.registryAccess()));
                check(loaded.volumeCopy().equals(mixed),"mixed exact NBT roundtrip");
                check(loaded.undoEdit() && loaded.volumeCopy().equals(cut),"mixed persisted undo");
                check(loaded.redoEdit() && loaded.volumeCopy().equals(mixed),"mixed redo after loading");
                var packet=host.getUpdateTag(level.registryAccess());
                packet.putString("id","astra_microblocks:test_host");
                check(load(level,host,packet).volumeCopy().equals(mixed),"mixed update tag lost materials");
            }
            migration(level,block,original);
            history(block);
        }
        for (var pos : new BlockPos[]{STONE,OAK}) {
            var block=pos.equals(STONE)?AstraMicroblocks.TEST_HOST:AstraMicroblocks.OAK_HOST;
            level.setBlock(pos,block.defaultBlockState(),Block.UPDATE_ALL);
            var host=host(level,pos);
            host.editCells(inlay(),ChiselOperation.CUT);
            host.editCells(inlay(),ChiselOperation.ADD,opposite(HostMaterial.of(host.getBlockState())));
            check(host.isFull() && host.materialCount(opposite(HostMaterial.of(host.getBlockState())))==64,"world mixed setup");
        }
        System.out.println("ASTRA_TEST: MIXED_WORKSHOP_PASS");
    }
    private static TestHostBlockEntity load(ServerLevel level,TestHostBlockEntity source,net.minecraft.nbt.CompoundTag tag) {
        var loaded=BlockEntity.loadStatic(source.getBlockPos(),source.getBlockState(),tag,level.registryAccess());
        check(loaded instanceof TestHostBlockEntity,"mixed load missing entity");
        return (TestHostBlockEntity)loaded;
    }
    private static void migration(ServerLevel level,TestHostBlock block,HostMaterial original) {
        var host=new TestHostBlockEntity(BlockPos.ZERO,block.defaultBlockState());
        host.editCells(inlay(),ChiselOperation.CUT);
        var tag=host.saveWithFullMetadata(level.registryAccess());
        tag.remove("materials_v2");
        for(int i=0;i<64;i++) { tag.remove("oak_"+i); tag.remove("undo_oak_"+i); }
        var migrated=load(level,host,tag);
        check(migrated.occupiedCount()==4032 && migrated.materialCount(original)==4032,"old save material migration");
        check(migrated.undoEdit() && migrated.materialCount(original)==4096,"old saved undo migration");
        tag.remove("grid_format_v1"); tag.putBoolean("carved",true);
        migrated=load(level,host,tag);
        check(migrated.occupiedCount()==4095 && migrated.materialCount(original)==4095
                && !migrated.canUndo(),"boolean prototype migration");
        var occupied=MicroblockGrid.fromLongArray(new long[64]); occupied.add(1,2,3);
        var normalized=new MicroblockVolume(occupied,new MicroblockGrid(),original);
        check(normalized.count(HostMaterial.OAK_PLANKS)==1 && normalized.materialAt(0,0,0)==null,
                "air retained material after normalization");
    }
    private static void history(TestHostBlock block) {
        var host=new TestHostBlockEntity(BlockPos.ZERO,block.defaultBlockState());
        var snapshots=new ArrayList<MicroblockVolume>(); snapshots.add(host.volumeCopy());
        var other=opposite(HostMaterial.of(block.defaultBlockState()));
        for(int i=0;i<40;i++) {
            int cell=(i/2)*97;
            var selection=mask(cell);
            if(host.isOccupied(cell%16,cell/256,(cell/16)%16)) host.editCells(selection,ChiselOperation.CUT);
            else host.editCells(selection,ChiselOperation.ADD,other);
            snapshots.add(host.volumeCopy());
        }
        check(host.undoDepth()==32,"mixed history bound");
        for(int i=39;i>=8;i--) check(host.undoEdit() && host.volumeCopy().equals(snapshots.get(i)),"mixed history undo order");
        for(int i=9;i<=40;i++) check(host.redoEdit() && host.volumeCopy().equals(snapshots.get(i)),"mixed history redo order");
        check(host.undoEdit(),"mixed branch undo");
        var vacancy=host.gridCopy();
        int cell=0; while(!vacancy.isOccupied(cell%16,cell/256,(cell/16)%16)) cell++;
        check(host.editCells(mask(cell),ChiselOperation.CUT)==1 && host.redoDepth()==0,"mixed branch did not clear redo");
    }
    public static void phaseOne(ServerLevel level) {
        for(var pos:new BlockPos[]{STONE,OAK}) {
            var host=host(level,pos); var mixed=expected(host);
            check(host.volumeCopy().equals(mixed),"first restart mixed cells");
            check(host.undoDepth()==1 && host.redoDepth()==0 && host.undoEdit(),"mixed restart undo contract");
            check(host.occupiedCount()==4032 && host.materialCount(opposite(HostMaterial.of(host.getBlockState())))==0,
                    "mixed restart undo material");
            check(host.redoEdit() && host.volumeCopy().equals(mixed),"mixed restart redo material");
            host.editCells(mask(4095),ChiselOperation.CUT);
        }
        System.out.println("ASTRA_TEST: MIXED_PERSISTENCE_PASS");
    }
    public static void phaseTwo(ServerLevel level) {
        for(var pos:new BlockPos[]{STONE,OAK}) {
            var host=host(level,pos); var mixed=expected(host); var cut=mixed.copy(); cut.remove(15,15,15);
            check(host.volumeCopy().equals(cut),"second restart exact mixed volume");
            check(host.undoEdit() && host.volumeCopy().equals(mixed),"second restart mixed undo");
            check(host.redoEdit() && host.volumeCopy().equals(cut),"second restart mixed redo");
        }
        System.out.println("ASTRA_TEST: MIXED_RESTART_PASS");
    }
    private static MicroblockVolume expected(TestHostBlockEntity host) {
        var original=HostMaterial.of(host.getBlockState()); var volume=new MicroblockVolume(original); var mask=inlay();
        for(int y=0;y<16;y++) for(int z=0;z<16;z++) for(int x=0;x<16;x++) if(mask.isOccupied(x,y,z)) {
            volume.remove(x,y,z); volume.add(x,y,z,opposite(original));
        }
        return volume;
    }
    private static TestHostBlockEntity host(ServerLevel level,BlockPos pos) {
        check(level.getBlockEntity(pos) instanceof TestHostBlockEntity,"mixed world host missing");
        return (TestHostBlockEntity)level.getBlockEntity(pos);
    }
}
