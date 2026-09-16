package dev.astra.microblocks;

import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class TestHostBlock extends BaseEntityBlock {

    private static final MapCodec<TestHostBlock> CODEC =
            simpleCodec(TestHostBlock::new);

    private static final VoxelShape SOLID =
            Shapes.block();

    /*
     * First real microblock geometry:
     *
     * Conceptual resolution: 16 x 16 x 16
     * Total cells: 4096
     *
     * CARVED removes exactly:
     * x = 15
     * y = 15
     * z = 15
     *
     * leaving 4095 occupied cells.
     */
    private static final VoxelShape CARVED =
            Shapes.or(
                    box(
                            0, 0, 0,
                            15, 16, 16
                    ),
                    box(
                            15, 0, 0,
                            16, 15, 16
                    ),
                    box(
                            15, 15, 0,
                            16, 16, 15
                    )
            );

    public TestHostBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
        return new TestHostBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        /*
         * Client acknowledges the interaction.
         * Actual state mutation happens only on the server.
         */
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(level.getBlockEntity(pos)
                instanceof TestHostBlockEntity host)) {
            return InteractionResult.PASS;
        }

        /*
         * Temporary testing controls:
         *
         * normal empty-hand click = carve
         * sneak + empty-hand click = undo
         *
         * These are NOT the eventual chisel controls.
         */
        if (player.isShiftKeyDown()) {
            host.undo();
        } else {
            host.carveCorner();
        }

        return InteractionResult.SUCCESS;
    }

    public boolean isCornerCarved(
            BlockGetter level,
            BlockPos pos
    ) {
        return level.getBlockEntity(pos)
                instanceof TestHostBlockEntity host
                && host.isCarved();
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return shapeFor(level, pos);
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return shapeFor(level, pos);
    }

    private VoxelShape shapeFor(
            BlockGetter level,
            BlockPos pos
    ) {
        if (isCornerCarved(level, pos)) {
            return CARVED;
        }

        return SOLID;
    }
}
