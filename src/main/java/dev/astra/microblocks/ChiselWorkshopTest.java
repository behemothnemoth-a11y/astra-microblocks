package dev.astra.microblocks;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Large mixed-operation gate, run in the existing disposable server world. */
public final class ChiselWorkshopTest {
    private ChiselWorkshopTest() {}
    private static void check(boolean condition,String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    private static TestHostBlockEntity host() {
        return new TestHostBlockEntity(BlockPos.ZERO,AstraMicroblocks.TEST_HOST.defaultBlockState());
    }
    private static MicroblockGrid single(int index) {
        var mask = new MicroblockGrid(); mask.clear();
        mask.add(index%16,(index/256)%16,(index/16)%16);
        return mask;
    }

    public static void run(ServerLevel level) {
        targeting();
        history(level);
        for (var block : new TestHostBlock[] {AstraMicroblocks.TEST_HOST, AstraMicroblocks.OAK_HOST}) {
            interaction(level, block);
            obstruction(level, block);
        }
        System.out.println("ASTRA_TEST: CHISEL_WORKSHOP_PASS");
    }

    private static void targeting() {
        BlockPos pos = new BlockPos(-7,100,-13);
        for (Direction face : Direction.values()) {
            var point = new Vec3(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5);
            var hit = new BlockHitResult(point,face,pos,false);
            var solid = MicroblockHitResolver.resolveForRemoval(hit);
            var added = MicroblockHitResolver.resolveForAddition(hit).orElseThrow();
            check(added.x()==solid.x()+face.getStepX() && added.y()==solid.y()+face.getStepY()
                    && added.z()==solid.z()+face.getStepZ(),"placement wrong side: "+face);
            var boundary = point.add(face.getStepX()*0.5,face.getStepY()*0.5,face.getStepZ()*0.5);
            check(MicroblockHitResolver.resolveForAddition(new BlockHitResult(boundary,face,pos,false)).isEmpty(),
                    "placement escaped host: "+face);
            for (ChiselMode mode : ChiselMode.values()) {
                var host = host();
                var cut = mode.selection(added,face);
                host.removeCells(cut);
                var before = host.gridCopy();
                var preview = ChiselPreview.create(before,mode,added,face,ChiselOperation.ADD);
                check(host.editCells(cut,ChiselOperation.ADD)==preview.affected(),"add preview mismatch: "+mode);
                check(host.isFull(),"brush failed to restore cut: "+mode);
                check(host.undoEdit() && host.gridCopy().equals(before),"add undo: "+mode);
                check(host.redoEdit() && host.isFull(),"add redo: "+mode);
                check(host.editCells(cut,ChiselOperation.ADD)==0,"occupied cells added twice");
                check(ChiselPreview.create(host.gridCopy(),mode,null,face,ChiselOperation.ADD).affected()==0,
                        "outside-host preview");
            }
        }
    }

    private static void history(ServerLevel level) {
        var host = host();
        var snapshots = new ArrayList<MicroblockGrid>(); snapshots.add(host.gridCopy());
        for (int i=0;i<40;i++) {
            check(host.editCells(single(i),ChiselOperation.CUT)==1,"history setup");
            snapshots.add(host.gridCopy());
        }
        check(host.undoDepth()==32,"history bound");
        var update = host.getUpdateTag(level.registryAccess());
        check(update.getIntOr("session_undo_count",-1)==32,"history count missing from client update");
        for (int i=39;i>=8;i--) {
            check(host.undoEdit() && host.gridCopy().equals(snapshots.get(i)),"undo order");
        }
        check(!host.undoEdit() && host.redoDepth()==32,"undo boundary");
        for (int i=9;i<=40;i++) check(host.redoEdit() && host.gridCopy().equals(snapshots.get(i)),"redo order");
        check(!host.redoEdit() && host.undoDepth()==32,"redo boundary");
        check(host.undoEdit(),"branch setup");
        long revision = host.revision();
        check(host.editCells(single(0),ChiselOperation.CUT)==0 && host.redoDepth()==1
                && host.revision()==revision,"no-op discarded redo");
        check(host.editCells(single(0),ChiselOperation.ADD)==1 && host.redoDepth()==0,"new edit did not discard redo");
        var beforeReload = host.gridCopy();
        var loaded = (TestHostBlockEntity) BlockEntity.loadStatic(BlockPos.ZERO,host.getBlockState(),
                host.saveWithFullMetadata(level.registryAccess()),level.registryAccess());
        check(loaded != null && loaded.gridCopy().equals(beforeReload) && loaded.undoDepth()==1
                && loaded.redoDepth()==0,"saved one-step fallback");
        check(loaded.undoEdit() && !loaded.canUndo(),"loaded undo fallback");
        check(loaded.redoEdit() && loaded.gridCopy().equals(beforeReload),"redo after saved undo");
        // Interleave cuts and additions, including no-ops, against exact captured states.
        host = host(); snapshots.clear(); snapshots.add(host.gridCopy());
        var random = new java.util.Random(106);
        for (int i=0;i<24;i++) {
            var mask = ChiselMode.values()[i%8].selection(new MicroblockHitResolver.Cell(
                    random.nextInt(16),random.nextInt(16),random.nextInt(16)),Direction.values()[i%6]);
            int changed = host.editCells(mask,i%3==0?ChiselOperation.ADD:ChiselOperation.CUT);
            if (changed>0) snapshots.add(host.gridCopy());
        }
        for (int i=snapshots.size()-2;i>=0;i--)
            check(host.undoEdit() && host.gridCopy().equals(snapshots.get(i)),"mixed undo");
        for (int i=1;i<snapshots.size();i++)
            check(host.redoEdit() && host.gridCopy().equals(snapshots.get(i)),"mixed redo");
    }

    private static void interaction(ServerLevel level, TestHostBlock block) {
        BlockPos pos = new BlockPos(9,100,9);
        var tool = new ItemStack(AstraMicroblocks.ASTRA_CHISEL);
        Player player = new Player(level,new GameProfile(UUID.randomUUID(),"AstraWorkshopTest")) {
            @Override public GameType gameMode() { return GameType.CREATIVE; }
            @Override public void sendOverlayMessage(Component message) {}
        };
        player.setPos(9,100,10);
        player.setItemInHand(InteractionHand.MAIN_HAND,tool);
        level.setBlock(pos,block.defaultBlockState(),Block.UPDATE_ALL);
        try {
            var host = (TestHostBlockEntity) level.getBlockEntity(pos);
            host.removeCell(8,15,8);
            ChiselOperation.ADD.store(tool);
            var hit = new BlockHitResult(new Vec3(9+8.5/16,100+15.0/16,9+8.5/16),Direction.UP,pos,false);
            var context = new UseOnContext(player,InteractionHand.MAIN_HAND,hit);
            AstraMicroblocks.ASTRA_CHISEL.useOn(context);
            check(host.isFull(),"actual item did not place cell");
            check(ChiselUndo.undoLast(player,tool) && !host.isFull(),"command add undo");
            check(ChiselUndo.redoLast(player,tool) && host.isFull(),"command add redo");
            long revision = host.revision();
            var neighborBefore = level.getBlockState(pos.above());
            var exterior = new UseOnContext(player,InteractionHand.MAIN_HAND,
                    new BlockHitResult(new Vec3(9.5,101,9.5),Direction.UP,pos,false));
            AstraMicroblocks.ASTRA_CHISEL.useOn(exterior);
            check(host.revision()==revision && level.getBlockState(pos.above()).equals(neighborBefore),"outside-host item placement");
            var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            var saved = ItemStack.CODEC.parse(ops,ItemStack.CODEC.encodeStart(ops,tool).getOrThrow()).getOrThrow();
            check(ChiselOperation.read(saved)==ChiselOperation.ADD,"operation persistence");
            ChiselOperation.CUT.store(tool);
            for (int i=0;i<3;i++) host.editCells(single(i),ChiselOperation.CUT);
            ChiselUndo.remember(tool,level,host);
            for (int i=0;i<3;i++) check(ChiselUndo.undoLast(player,tool),"repeated tool undo lost target");
            for (int i=0;i<3;i++) check(ChiselUndo.redoLast(player,tool),"repeated tool redo lost target");
        } finally { level.removeBlock(pos,false); }
    }

    private static void obstruction(ServerLevel level, TestHostBlock block) {
        BlockPos pos = new BlockPos(11,100,11);
        var chunk = new net.minecraft.world.level.ChunkPos(pos.getX() >> 4,pos.getZ() >> 4);
        var ready = level.getChunkSource().addTicketAndLoadWithRadius(net.minecraft.server.level.TicketType.FORCED,chunk,2);
        level.getServer().managedBlock(ready::isDone);
        ready.join();
        level.setBlock(pos,block.defaultBlockState(),Block.UPDATE_ALL);
        var host = (TestHostBlockEntity) level.getBlockEntity(pos);
        host.editCells(new MicroblockGrid(),ChiselOperation.CUT);
        var cow = EntityTypes.COW.create(level,EntitySpawnReason.COMMAND);
        check(cow!=null,"collision test entity");
        cow.setPos(11.5,100,11.5); cow.setNoAi(true);
        check(level.addFreshEntity(cow),"could not add collision test entity");
        check(level.getEntities((net.minecraft.world.entity.Entity)null,new net.minecraft.world.phys.AABB(pos),
                entity -> entity == cow).size()==1,"test entity must be queryable in an active chunk");
        try {
            long revision=host.revision();
            check(host.editCells(new MicroblockGrid(),ChiselOperation.ADD)==-1,"placement overlaps entity");
            check(!host.undoEdit() && host.revision()==revision && host.undoDepth()==1,"blocked undo consumed history");
            cow.setPos(14,100,14);
            check(host.undoEdit() && host.isFull(),"undo after obstruction removed");
            check(host.redoEdit() && host.isEmpty(),"redo cut");
            check(host.editCells(new MicroblockGrid(),ChiselOperation.ADD)==4096,"fill empty host data layer");
            check(host.undoEdit() && host.isEmpty(),"undo fill");
            cow.setPos(11.5,100,11.5);
            check(!host.redoEdit() && host.redoDepth()==1,"blocked redo consumed history");
            cow.setPos(14,100,14);
            check(host.redoEdit() && host.isFull(),"redo after obstruction removed");
        } finally {
            cow.discard(); level.removeBlock(pos,false);
            level.getChunkSource().removeTicketWithRadius(net.minecraft.server.level.TicketType.FORCED,chunk,2);
        }
    }
}
