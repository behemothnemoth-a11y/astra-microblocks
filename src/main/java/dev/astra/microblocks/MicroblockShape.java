package dev.astra.microblocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Converts a 16x16x16 MicroblockGrid into Minecraft geometry.
 *
 * This first implementation intentionally favors correctness
 * over optimization: every occupied microcell contributes one
 * exact 1/16-scale cuboid.
 *
 * Later we can replace the internal implementation with greedy
 * box merging without changing callers.
 */
public final class MicroblockShape {

    private static final double CELL_SIZE =
            1.0 / MicroblockGrid.SIZE;

    private MicroblockShape() {
    }

    /**
     * Builds the complete occupied shape for one host block.
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

        VoxelShape shape =
                Shapes.empty();

        for (int y = 0;
             y < MicroblockGrid.SIZE;
             y++) {

            for (int z = 0;
                 z < MicroblockGrid.SIZE;
                 z++) {

                for (int x = 0;
                     x < MicroblockGrid.SIZE;
                     x++) {

                    if (!grid.isOccupied(
                            x,
                            y,
                            z
                    )) {
                        continue;
                    }

                    VoxelShape cell =
                            cellShape(
                                    x,
                                    y,
                                    z
                            );

                    shape =
                            Shapes.joinUnoptimized(
                                    shape,
                                    cell,
                                    BooleanOp.OR
                            );
                }
            }
        }

        return shape.optimize();
    }

    /**
     * Builds exactly one 1/16-scale cell.
     */
    public static VoxelShape cellShape(
            int x,
            int y,
            int z
    ) {
        /*
         * Reuse MicroblockGrid's coordinate validation.
         */
        MicroblockGrid.index(
                x,
                y,
                z
        );

        double minX =
                x * CELL_SIZE;

        double minY =
                y * CELL_SIZE;

        double minZ =
                z * CELL_SIZE;

        double maxX =
                (x + 1) * CELL_SIZE;

        double maxY =
                (y + 1) * CELL_SIZE;

        double maxZ =
                (z + 1) * CELL_SIZE;

        return Block.box(
                minX * 16.0,
                minY * 16.0,
                minZ * 16.0,
                maxX * 16.0,
                maxY * 16.0,
                maxZ * 16.0
        );
    }
}
