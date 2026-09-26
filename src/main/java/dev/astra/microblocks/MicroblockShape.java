package dev.astra.microblocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;


/**
 * Converts a 16x16x16 MicroblockGrid into Minecraft geometry.
 *
 * Production implementation populates a fixed 16-cubed Minecraft voxel lattice.
 * It avoids repeated boolean unions and shape.optimize() on fragmented grids.
 *
 * The public geometry contract remains exact 1/16 resolution.
 */
public final class MicroblockShape {

    private MicroblockShape() {
    }

    /**
     * Builds the complete occupied physical shape.
     */
    public static VoxelShape build(
            MicroblockGrid grid
    ) {
        if (grid == null) {
            throw new IllegalArgumentException(
                    "grid cannot be null"
            );
        }

        if (grid.isEmpty()) {
            return Shapes.empty();
        }

        if (grid.isFull()) {
            return Shapes.block();
        }

        // Populate the fixed lattice once. Repeated boolean unions and optimize()
        // scale poorly on disconnected/checkerboard shapes.
        var voxels = new net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape(16,16,16);
        for(int y=0;y<16;y++) for(int z=0;z<16;z++) for(int x=0;x<16;x++)
            if(grid.isOccupied(x,y,z)) voxels.fill(x,y,z);
        return new net.minecraft.world.phys.shapes.CubeVoxelShape(voxels);
    }

    /**
     * Converts one mesher cuboid into Minecraft's
     * 0..16 block-model coordinate system.
     *
     * Mesher coordinates already use microcell boundaries,
     * so no floating-point conversion is necessary here:
     *
     * cell boundary 0  -> Block.box coordinate 0
     * cell boundary 1  -> Block.box coordinate 1
     * ...
     * cell boundary 16 -> Block.box coordinate 16
     */
    public static VoxelShape cuboidShape(
            MicroblockMesher.Cuboid cuboid
    ) {
        if (cuboid == null) {
            throw new IllegalArgumentException(
                    "cuboid cannot be null"
            );
        }

        return Block.box(
                cuboid.minX(),
                cuboid.minY(),
                cuboid.minZ(),
                cuboid.maxX(),
                cuboid.maxY(),
                cuboid.maxZ()
        );
    }

    /**
     * Builds exactly one 1/16-scale microcell.
     *
     * Retained as part of the geometry API and for regression
     * testing even though production build() now uses merged
     * cuboids.
     */
    public static VoxelShape cellShape(
            int x,
            int y,
            int z
    ) {
        MicroblockGrid.index(
                x,
                y,
                z
        );

        return Block.box(
                x,
                y,
                z,
                x + 1,
                y + 1,
                z + 1
        );
    }
}
