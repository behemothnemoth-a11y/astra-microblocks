package dev.astra.microblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

public final class MicroblockLightTest {
    private static void check(boolean ok,String message) {if(!ok) throw new IllegalStateException(message);}
    public static void run(ServerLevel level) {
        var sea=HostMaterial.find("sea_lantern").orElseThrow();
        var volume=MicroblockVolume.empty(HostMaterial.STONE);
        check(MicroblockLight.emission(volume)==0,"empty host emits");
        volume.add(8,8,8,sea);
        check(MicroblockLight.emission(volume)==15,"one sea lantern cell must emit 15");
        check(MicroblockLight.emission(VolumePalette.read(VolumePalette.write(volume)).orElseThrow())==15,"light palette roundtrip");
        var pos=new BlockPos(224,200,0);
        level.setBlock(pos,AstraMicroblocks.TEST_HOST.defaultBlockState(),Block.UPDATE_ALL);
        var host=(TestHostBlockEntity)level.getBlockEntity(pos);host.initializeDesign(volume);
        check(level.getBlockState(pos).getLightEmission()==15,"host emission not published");
        var selected=new MicroblockGrid();selected.clear();selected.add(8,8,8);
        host.editCells(selected,ChiselOperation.REPLACE,HostMaterial.color(0xffffff));
        check(level.getBlockState(pos).getLightEmission()==0,"white RGB emits light");
        check(host.undoEdit() && level.getBlockState(pos).getLightEmission()==15,"undo did not restore light");
        check(host.redoEdit() && level.getBlockState(pos).getLightEmission()==0,"redo did not remove light");
        host.undoEdit();
        level.setBlock(pos,level.getBlockState(pos).setValue(TestHostBlock.LIGHT,0),Block.UPDATE_ALL);
        host.refreshLight();
        check(level.getBlockState(pos).getLightEmission()==15,"stale imported light state not repaired");
        check(level.getBlockEntity(pos)==host,"lighting replaced host entity");
        System.out.println("ASTRA_TEST: MICROBLOCK_LIGHT_PASS");
    }
    public static void persisted(ServerLevel level) {
        var pos=new BlockPos(224,200,0);
        var host=(TestHostBlockEntity)level.getBlockEntity(pos);
        check(host!=null && MicroblockLight.emission(host.volumeCopy())==15,"saved luminous cell lost");
        check(level.getBlockState(pos).getLightEmission()==15,"saved light state lost");
        check(host.redoEdit() && level.getBlockState(pos).getLightEmission()==0,"saved redo did not extinguish light");
        check(host.undoEdit() && level.getBlockState(pos).getLightEmission()==15,"saved undo did not restore light");
        System.out.println("ASTRA_TEST: MICROBLOCK_LIGHT_PERSISTENCE_PASS");
    }
}
