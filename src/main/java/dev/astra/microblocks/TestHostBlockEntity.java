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
     * Prototype one-level undo.
     */
    private MicroblockGrid undoGrid;

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
        grid = previous;

        revision++;

        publish();
    }

    private void beginEdit() {
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
        return saveWithoutMetadata(
                registryLookup
        );
    }

    @Override
    public Packet<ClientGamePacketListener>
            getUpdatePacket() {

        return ClientboundBlockEntityDataPacket.create(
                this
        );
    }
}
