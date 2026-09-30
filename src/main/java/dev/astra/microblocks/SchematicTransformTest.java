package dev.astra.microblocks;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.util.ProblemReporter;

/** Tests the vanilla block-state -> NBT loading contract used by schematic placement. */
public final class SchematicTransformTest {
    private static final BlockPos BASE=new BlockPos(120,200,0);
    private static void check(boolean ok,String message) {if(!ok) throw new IllegalStateException(message);}
    public static MicroblockVolume specimen() {
        var v=MicroblockVolume.empty(HostMaterial.find("oak_log[axis=x]").orElseThrow());
        for(int x=1;x<14;x++) v.add(x,2,3,HostMaterial.find("oak_log[axis=x]").orElseThrow());
        for(int z=3;z<12;z++) v.add(13,2,z,HostMaterial.find("oxidized_copper").orElseThrow());
        for(int y=2;y<14;y++) v.add(4,y,8,HostMaterial.find("bone_block[axis=z]").orElseThrow());
        v.add(0,0,0,HostMaterial.find("gold_block").orElseThrow());
        v.add(15,15,14,HostMaterial.find("diamond_block").orElseThrow());
        v.add(8,8,8,HostMaterial.find("blue_stained_glass").orElseThrow());
        v.add(8,9,8,HostMaterial.find("crimson_stem[axis=x]").orElseThrow());
        v.add(8,10,8,HostMaterial.find("sea_lantern").orElseThrow());
        v.add(12,12,12,HostMaterial.color(0x01fea3));
        return v;
    }
    /** Independent coordinate oracle using Minecraft's integer structure transform. */
    public static MicroblockVolume expected(MicroblockVolume source,Mirror mirror,Rotation rotation) {
        var origin=net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(BlockPos.ZERO,mirror,rotation,BlockPos.ZERO);
        var far=net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(new BlockPos(15,0,15),mirror,rotation,BlockPos.ZERO);
        int minX=Math.min(origin.getX(),far.getX()),minZ=Math.min(origin.getZ(),far.getZ());
        boolean swap=rotation==Rotation.CLOCKWISE_90 || rotation==Rotation.COUNTERCLOCKWISE_90;
        var result=MicroblockVolume.empty(swap?source.original().transformed(Direction.Axis.Y,false):source.original());
        for(int y=0;y<16;y++) for(int z=0;z<16;z++) for(int x=0;x<16;x++) if(source.isOccupied(x,y,z)) {
            var p=net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(new BlockPos(x,y,z),mirror,rotation,BlockPos.ZERO);
            var m=source.materialAt(x,y,z);
            result.add(p.getX()-minX,y,p.getZ()-minZ,swap?m.transformed(Direction.Axis.Y,false):m);
        }
        return result;
    }
    public static void load(TestHostBlockEntity host,CompoundTag tag,HolderLookup.Provider registries) {
        host.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,registries,tag));
    }
    public static void phaseZero(ServerLevel level) {
        var source=specimen();
        for(int base=0;base<8;base++) for(var mirror:Mirror.values()) for(var rotation:Rotation.values()) {
            var state=AstraMicroblocks.TEST_HOST.defaultBlockState().setValue(TestHostBlock.ORIENTATION,base);
            var host=new TestHostBlockEntity(BlockPos.ZERO,state);
            var initial=SculptureOrientation.transform(source,base);
            host.initializeDesign(initial);
            var before=host.volumeCopy();
            var cell=new MicroblockGrid();cell.clear();
            outer: for(int y=0;y<16;y++) for(int z=0;z<16;z++) for(int x=0;x<16;x++) if(before.isOccupied(x,y,z)) {cell.add(x,y,z);break outer;}
            host.editCells(cell,ChiselOperation.CUT);
            var after=host.volumeCopy();
            var saved=host.saveWithFullMetadata(level.registryAccess());
            var target=new TestHostBlockEntity(BlockPos.ZERO,state.mirror(mirror).rotate(rotation));
            load(target,saved,level.registryAccess());
            check(target.volumeCopy().equals(expected(after,mirror,rotation)),"transformed current volume");
            check(target.undoEdit() && target.volumeCopy().equals(expected(before,mirror,rotation)),"transformed undo");
            check(target.redoEdit() && target.volumeCopy().equals(expected(after,mirror,rotation)),"transformed redo");
            var next=new TestHostBlockEntity(BlockPos.ZERO,target.getBlockState());
            load(next,target.saveWithFullMetadata(level.registryAccess()),level.registryAccess());
            check(next.volumeCopy().equals(target.volumeCopy()),"save/reload double transformation");
            var client=new TestHostBlockEntity(BlockPos.ZERO,target.getBlockState());
            load(client,target.getUpdateTag(level.registryAccess()),level.registryAccess());
            check(client.volumeCopy().equals(target.volumeCopy()),"network double transformation");
            var overwrite=target.contentVersion();
            load(target,saved,level.registryAccess());
            check(target.contentVersion()>overwrite,"same-revision overwrite stale render version");
        }
        // Legacy schematics have no orientation property/tag and must rotate from identity.
        var legacy=new TestHostBlockEntity(BlockPos.ZERO,AstraMicroblocks.TEST_HOST.defaultBlockState());
        legacy.initializeDesign(source);
        var oldTag=legacy.saveWithFullMetadata(level.registryAccess());oldTag.remove("astra_orientation");
        for(int orientation=0;orientation<8;orientation++) {
            var pos=BASE.east(orientation*2);
            var state=AstraMicroblocks.TEST_HOST.defaultBlockState().setValue(TestHostBlock.ORIENTATION,orientation);
            level.setBlock(pos,state,Block.UPDATE_ALL);
            var host=(TestHostBlockEntity)level.getBlockEntity(pos);
            load(host,oldTag,level.registryAccess());host.setChanged();
            check(host.volumeCopy().equals(SculptureOrientation.transform(source,orientation)),"legacy schematic transform");
        }
        System.out.println("ASTRA_TEST: SCHEMATIC_TRANSFORM_PASS");
    }
    public static void verifySaved(ServerLevel level,String marker) {
        for(int i=0;i<8;i++) {
            var host=(TestHostBlockEntity)level.getBlockEntity(BASE.east(i*2));
            check(host!=null && host.volumeCopy().equals(SculptureOrientation.transform(specimen(),i)),"persisted transformed sculpture "+i);
            check(host.getBlockState().getValue(TestHostBlock.ORIENTATION)==i,"persisted orientation");
        }
        System.out.println("ASTRA_TEST: "+marker);
    }
}
