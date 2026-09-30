package dev.astra.microblocks;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Prototype sculptable host block.
 *
 * Its physical Minecraft geometry is now generated directly
 * from the authoritative 16x16x16 MicroblockGrid.
 */
public final class TestHostBlock extends BaseEntityBlock {

    public static final net.minecraft.world.level.block.state.properties.IntegerProperty ORIENTATION =
            net.minecraft.world.level.block.state.properties.IntegerProperty.create("orientation",0,7);
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty LIGHT =
            net.minecraft.world.level.block.state.properties.IntegerProperty.create("light",0,15);

    public static final MapCodec<TestHostBlock> CODEC =
            simpleCodec(TestHostBlock::new);

    public TestHostBlock(Properties properties) {
        super(properties.lightLevel(state -> state.getValue(LIGHT)));
        registerDefaultState(stateDefinition.any().setValue(ORIENTATION,0).setValue(LIGHT,0));
    }

    @Override protected void createBlockStateDefinition(
            net.minecraft.world.level.block.state.StateDefinition.Builder<Block,BlockState> builder) {
        builder.add(ORIENTATION,LIGHT);
    }
    @Override protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level,
            BlockPos pos, net.minecraft.util.RandomSource random) {
        if(level.getBlockEntity(pos) instanceof TestHostBlockEntity host) host.refreshLight();
    }
    @Override protected BlockState rotate(BlockState state,net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(ORIENTATION,SculptureOrientation.compose(SculptureOrientation.rotation(rotation),state.getValue(ORIENTATION)));
    }
    @Override protected BlockState mirror(BlockState state,net.minecraft.world.level.block.Mirror mirror) {
        return state.setValue(ORIENTATION,SculptureOrientation.compose(SculptureOrientation.mirror(mirror),state.getValue(ORIENTATION)));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
        return new TestHostBlockEntity(
                pos,
                state
        );
    }

    @Override
    protected RenderShape getRenderShape(
            BlockState state
    ) {
        // The block entity renderer owns all visible surfaces, including cavities.
        return RenderShape.INVISIBLE;
    }

    /**
     * Generates the actual physical collision geometry
     * from the block entity's 4096-cell occupancy grid.
     */
    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return microblockShape(
                level,
                pos
        );
    }

    /**
     * Generates the player selection/outline geometry from
     * the same grid so collision and selection agree.
     */
    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return microblockShape(
                level,
                pos
        );
    }

    /**
     * Shared geometry lookup.
     *
     * A missing block entity gets a conservative full-block
     * fallback. That prevents transient loading states from
     * accidentally creating holes in the world.
     */
    private VoxelShape microblockShape(
            BlockGetter level,
            BlockPos pos
    ) {
        BlockEntity blockEntity =
                level.getBlockEntity(pos);

        if (!(blockEntity
                instanceof TestHostBlockEntity host)) {

            return Shapes.block();
        }

        return host.physicalShape();
    }

    @Override protected net.minecraft.world.item.ItemStack getCloneItemStack(
            net.minecraft.world.level.LevelReader level,BlockPos pos,BlockState state,boolean includeData) {
        if(level.getBlockEntity(pos) instanceof TestHostBlockEntity host) return SculptureData.item(host.volumeCopy());
        return new net.minecraft.world.item.ItemStack(this);
    }
    @Override protected java.util.List<net.minecraft.world.item.ItemStack> getDrops(
            BlockState state,net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        if(params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY)
                instanceof TestHostBlockEntity host) {
            var stack=SculptureData.item(host.volumeCopy());
            return stack.isEmpty()?java.util.List.of():java.util.List.of(stack);
        }
        return java.util.List.of(new net.minecraft.world.item.ItemStack(this));
    }

    /**
     * Compatibility query retained for the lifecycle harness.
     */
    public boolean isCornerCarved(
            Level level,
            BlockPos pos
    ) {
        BlockEntity blockEntity =
                level.getBlockEntity(pos);

        return blockEntity
                instanceof TestHostBlockEntity host
                && !host.isOccupied(
                        15,
                        15,
                        15
                );
    }
}
