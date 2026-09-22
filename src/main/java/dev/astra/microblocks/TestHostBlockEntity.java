package dev.astra.microblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Block entity backing one sculptable 16x16x16 host.
 *
 * The MicroblockGrid is the authoritative geometry state.
 */
public final class TestHostBlockEntity extends BlockEntity {

    private static final int GRID_WORDS = 64;

    private MicroblockGrid grid =
            new MicroblockGrid();

    /*
     * Persisted last-edit snapshot; the older session history remains bounded and transient.
     */
    private MicroblockGrid undoGrid;
    public static final int HISTORY_LIMIT = 32;
    private final java.util.ArrayDeque<MicroblockGrid> olderUndo = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<MicroblockGrid> redoHistory = new java.util.ArrayDeque<>();
    private int syncedUndo = -1, syncedRedo = -1;

    public int undoDepth() {
        return level != null && level.isClientSide() && syncedUndo >= 0 ? syncedUndo
                : (undoGrid == null ? 0 : 1 + olderUndo.size());
    }
    public int redoDepth() {
        return level != null && level.isClientSide() && syncedRedo >= 0 ? syncedRedo : redoHistory.size();
    }

    /** Player edits keep bounded session history; legacy data-layer helpers retain their contract. */
    public int editCells(MicroblockGrid selection, ChiselOperation operation) {
        MicroblockGrid edited = grid.copy();
        int changed = 0;
        for (int y=0;y<16;y++) for (int z=0;z<16;z++) for (int x=0;x<16;x++)
            if (selection.isOccupied(x,y,z) && edited.setOccupied(x,y,z,operation == ChiselOperation.ADD)) changed++;
        if (changed == 0) return 0;
        if (!canRestore(edited)) return -1;
        pushUndo();
        redoHistory.clear();
        grid = edited;
        finishEdit();
        return changed;
    }

    private void pushUndo() {
        if (undoGrid != null) olderUndo.addLast(undoGrid);
        while (olderUndo.size() >= HISTORY_LIMIT) olderUndo.removeFirst();
        undoGrid = grid.copy();
    }

    public boolean undoEdit() {
        if (undoGrid == null || !canRestore(undoGrid)) return false;
        redoHistory.addLast(grid.copy());
        grid = undoGrid;
        undoGrid = olderUndo.pollLast();
        finishEdit();
        return true;
    }

    public boolean redoEdit() {
        MicroblockGrid next = redoHistory.peekLast();
        if (next == null || !canRestore(next)) return false;
        pushUndo();
        grid = redoHistory.removeLast();
        finishEdit();
        return true;
    }

    private boolean canRestore(MicroblockGrid next) {
        if (level == null || level.isClientSide()) return true;
        MicroblockGrid added = next.copy();
        for (int y=0;y<16;y++) for (int z=0;z<16;z++) for (int x=0;x<16;x++)
            if (grid.isOccupied(x,y,z)) added.remove(x,y,z);
        if (added.isEmpty()) return true;
        var shape = MicroblockShape.build(added).move(worldPosition.getX(),worldPosition.getY(),worldPosition.getZ());
        for (var entity : level.getEntities((net.minecraft.world.entity.Entity) null,shape.bounds(), entity -> entity.isAlive() && !entity.isSpectator()
                && (entity instanceof net.minecraft.world.entity.LivingEntity || entity.blocksBuilding))) {
            if (net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(shape,
                    net.minecraft.world.phys.shapes.Shapes.create(entity.getBoundingBox()),
                    net.minecraft.world.phys.shapes.BooleanOp.AND)) return false;
        }
        return true;
    }

    private long revision;

    public TestHostBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
        super(
                AstraMicroblocks.TEST_HOST_ENTITY,
                pos,
                state
        );
    }

    public MicroblockGrid gridCopy() {
        return grid.copy();
    }

    public boolean isOccupied(
            int x,
            int y,
            int z
    ) {
        return grid.isOccupied(x, y, z);
    }

    public int occupiedCount() {
        return grid.occupiedCount();
    }

    public boolean isFull() {
        return grid.isFull();
    }

    public boolean isEmpty() {
        return grid.isEmpty();
    }

    public boolean canUndo() {
        return undoGrid != null;
    }

    public long revision() {
        return revision;
    }

    public boolean removeCell(
            int x,
            int y,
            int z
    ) {
        if (!grid.isOccupied(x, y, z)) {
            return false;
        }

        beginEdit();

        boolean changed =
                grid.remove(x, y, z);

        if (changed) {
            finishEdit();
        }

        return changed;
    }

    /** Commit a whole tool stroke with one undo snapshot and one published revision. */
    public int removeCells(MicroblockGrid selection) {
        MicroblockGrid edited = grid.copy();
        int removed = 0;
        for (int y = 0; y < MicroblockGrid.SIZE; y++)
            for (int z = 0; z < MicroblockGrid.SIZE; z++)
                for (int x = 0; x < MicroblockGrid.SIZE; x++)
                    if (selection.isOccupied(x, y, z) && edited.remove(x, y, z)) removed++;
        if (removed == 0) return 0;
        beginEdit();
        grid = edited;
        finishEdit();
        return removed;
    }

    public boolean addCell(
            int x,
            int y,
            int z
    ) {
        if (grid.isOccupied(x, y, z)) {
            return false;
        }

        beginEdit();

        boolean changed =
                grid.add(x, y, z);

        if (changed) {
            finishEdit();
        }

        return changed;
    }

    /**
     * Compatibility wrapper for the original proof-of-concept.
     */
    public void carveCorner() {
        removeCell(
                15,
                15,
                15
        );
    }

    public boolean isCarved() {
        return !grid.isOccupied(
                15,
                15,
                15
        );
    }

    public void undo() {
        if (undoGrid == null) {
            return;
        }

        MicroblockGrid previous =
                undoGrid;

        undoGrid = null;
        olderUndo.clear();
        redoHistory.clear();
        grid = previous;

        revision++;

        publish();
    }

    private void beginEdit() {
        olderUndo.clear();
        redoHistory.clear();
        undoGrid = grid.copy();
    }

    private void finishEdit() {
        revision++;
        publish();
    }

    private void publish() {
        setChanged();

        if (level == null) {
            return;
        }

        BlockState state =
                getBlockState();

        level.sendBlockUpdated(
                worldPosition,
                state,
                state,
                Block.UPDATE_ALL
        );
    }

    /**
     * Writes one grid as 64 individual longs.
     *
     * This avoids relying on a long-array read API that
     * Minecraft 26.2 ValueInput does not expose.
     */
    private static void writeGrid(
            ValueOutput output,
            String prefix,
            MicroblockGrid source
    ) {
        long[] data =
                source.toLongArray();

        for (int i = 0; i < GRID_WORDS; i++) {
            output.putLong(
                    prefix + i,
                    data[i]
            );
        }
    }

    /**
     * Reads one grid from 64 individual longs.
     */
    private static MicroblockGrid readGrid(
            ValueInput input,
            String prefix
    ) {
        long[] data =
                new long[GRID_WORDS];

        for (int i = 0; i < GRID_WORDS; i++) {
            data[i] =
                    input.getLongOr(
                            prefix + i,
                            -1L
                    );
        }

        return MicroblockGrid.fromLongArray(
                data
        );
    }    @Override
    protected void saveAdditional(
            ValueOutput output
    ) {
        output.putBoolean(
                "grid_format_v1",
                true
        );

        writeGrid(
                output,
                "grid_",
                grid
        );

        output.putLong(
                "revision",
                revision
        );

        boolean hasUndo =
                undoGrid != null;

        output.putBoolean(
                "has_undo",
                hasUndo
        );

        if (hasUndo) {
            writeGrid(
                    output,
                    "undo_",
                    undoGrid
            );
        }

        super.saveAdditional(output);
    }

    @Override
    protected void loadAdditional(
            ValueInput input
    ) {
        super.loadAdditional(input);
        olderUndo.clear();
        redoHistory.clear();
        syncedUndo = input.getIntOr("session_undo_count", -1);
        syncedRedo = input.getIntOr("session_redo_count", -1);

        boolean gridFormat =
                input.getBooleanOr(
                        "grid_format_v1",
                        false
                );

        if (gridFormat) {
            grid =
                    readGrid(
                            input,
                            "grid_"
                    );
        } else {
            /*
             * Migration from the original boolean prototype.
             */
            grid =
                    new MicroblockGrid();

            boolean oldCarved =
                    input.getBooleanOr(
                            "carved",
                            false
                    );

            if (oldCarved) {
                grid.remove(
                        15,
                        15,
                        15
                );
            }
        }

        revision =
                input.getLongOr(
                        "revision",
                        0L
                );

        boolean hasUndo =
                input.getBooleanOr(
                        "has_undo",
                        false
                );

        if (gridFormat && hasUndo) {
            undoGrid =
                    readGrid(
                            input,
                            "undo_"
                    );
        } else {
            /*
             * Old-format undo cannot restore a complete grid,
             * so we deliberately discard it during migration.
             */
            undoGrid = null;
        }
    }

    @Override
    public CompoundTag getUpdateTag(
            HolderLookup.Provider registryLookup
    ) {
        CompoundTag tag = saveWithoutMetadata(registryLookup);
        tag.putInt("session_undo_count", undoDepth());
        tag.putInt("session_redo_count", redoDepth());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener>
            getUpdatePacket() {

        return ClientboundBlockEntityDataPacket.create(
                this
        );
    }
}
