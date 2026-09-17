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
 * The MicroblockGrid is now the authoritative geometry state.
 */
public final class TestHostBlockEntity extends BlockEntity {

    private MicroblockGrid grid =
            new MicroblockGrid();

    /*
     * One-level undo for the current prototype.
     *
     * Later this becomes a proper edit-history system.
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

    /**
     * Removes one independently addressed microcell.
     */
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

    /**
     * Adds one independently addressed microcell.
     */
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
     * Compatibility method for the original proof-of-concept
     * lifecycle test.
     *
     * It is no longer special geometry. It simply edits cell
     * (15,15,15) through the real grid.
     */
    public void carveCorner() {
        removeCell(15, 15, 15);
    }

    /**
     * Compatibility query for the original block code.
     */
    public boolean isCarved() {
        return !grid.isOccupied(
                15,
                15,
                15
        );
    }

    /**
     * Restores the complete grid snapshot from immediately
     * before the most recent successful edit.
     */
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
    }    @Override
    protected void saveAdditional(
            ValueOutput output
    ) {
        output.putLongArray(
                "microblocks",
                grid.toLongArray()
        );

        output.putLong(
                "revision",
                revision
        );

        output.putBoolean(
                "has_undo",
                undoGrid != null
        );

        if (undoGrid != null) {
            output.putLongArray(
                    "undo_microblocks",
                    undoGrid.toLongArray()
            );
        }

        super.saveAdditional(output);
    }

    @Override
    protected void loadAdditional(
            ValueInput input
    ) {
        super.loadAdditional(input);

        /*
         * New 4096-cell format.
         *
         * If data is absent or malformed, retain a full grid
         * rather than allowing corrupt geometry into the host.
         */
        long[] stored =
                input.getLongArray(
                        "microblocks"
                ).orElse(null);

        if (stored != null
                && stored.length == 64) {

            grid =
                    MicroblockGrid.fromLongArray(
                            stored
                    );
        } else {
            grid =
                    new MicroblockGrid();
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

        if (hasUndo) {
            long[] storedUndo =
                    input.getLongArray(
                            "undo_microblocks"
                    ).orElse(null);

            if (storedUndo != null
                    && storedUndo.length == 64) {

                undoGrid =
                        MicroblockGrid.fromLongArray(
                                storedUndo
                        );
            } else {
                undoGrid = null;
            }
        } else {
            undoGrid = null;
        }

        /*
         * Migration support for our original boolean test
         * format. This can eventually disappear once that
         * prototype format is no longer relevant.
         */
        if (stored == null) {
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
