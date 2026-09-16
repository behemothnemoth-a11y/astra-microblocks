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

public final class TestHostBlockEntity extends BlockEntity {

    private boolean carved;
    private boolean canUndo;
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

    public boolean isCarved() {
        return carved;
    }

    public boolean canUndo() {
        return canUndo;
    }

    public long revision() {
        return revision;
    }

    public void carveCorner() {
        if (carved) {
            return;
        }

        carved = true;
        canUndo = true;
        revision++;

        publish();
    }

    public void undo() {
        if (!canUndo || !carved) {
            return;
        }

        carved = false;
        canUndo = false;
        revision++;

        publish();
    }

    private void publish() {
        setChanged();

        if (level == null) {
            return;
        }

        BlockState state = getBlockState();

        level.sendBlockUpdated(
                worldPosition,
                state,
                state,
                Block.UPDATE_ALL
        );
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        output.putBoolean("carved", carved);
        output.putBoolean("can_undo", canUndo);
        output.putLong("revision", revision);

        super.saveAdditional(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);

        carved = input.getBooleanOr(
                "carved",
                false
        );

        canUndo = input.getBooleanOr(
                "can_undo",
                false
        );

        revision = input.getLongOr(
                "revision",
                0L
        );
    }

    @Override
    public CompoundTag getUpdateTag(
            HolderLookup.Provider registryLookup
    ) {
        return saveWithoutMetadata(registryLookup);
    }

    @Override
    public Packet<ClientGamePacketListener>
            getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
