package dev.astra.microblocks;

import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts MicroblockGrid occupancy into visible render faces.
 *
 * Unlike collision meshing, rendering only needs surfaces that
 * are exposed to air. Faces between two occupied neighboring
 * cells are deliberately omitted.
 *
 * Coordinates use microblock boundaries from 0..16.
 */
public final class MicroblockRenderMesh {

    private MicroblockRenderMesh() {
    }

    /**
     * One visible square face belonging to one occupied cell.
     */
    public record Face(
            int x,
            int y,
            int z,
            Direction direction
    ) {
        public Face {
            MicroblockGrid.index(
                    x,
                    y,
                    z
            );

            if (direction == null) {
                throw new IllegalArgumentException(
                        "direction cannot be null"
                );
            }
        }
    }

    /**
     * Generates every visible face in deterministic XYZ order.
     */
    public static List<Face> build(
            MicroblockGrid grid
    ) {
        if (grid == null) {
            throw new IllegalArgumentException(
                    "grid cannot be null"
            );
        }

        if (grid.isEmpty()) {
            return List.of();
        }

        List<Face> faces =
                new ArrayList<>();

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {

                    if (!grid.isOccupied(
                            x,
                            y,
                            z
                    )) {
                        continue;
                    }

                    addIfExposed(
                            grid,
                            faces,
                            x,
                            y,
                            z,
                            Direction.WEST
                    );

                    addIfExposed(
                            grid,
                            faces,
                            x,
                            y,
                            z,
                            Direction.EAST
                    );

                    addIfExposed(
                            grid,
                            faces,
                            x,
                            y,
                            z,
                            Direction.DOWN
                    );

                    addIfExposed(
                            grid,
                            faces,
                            x,
                            y,
                            z,
                            Direction.UP
                    );

                    addIfExposed(
                            grid,
                            faces,
                            x,
                            y,
                            z,
                            Direction.NORTH
                    );

                    addIfExposed(
                            grid,
                            faces,
                            x,
                            y,
                            z,
                            Direction.SOUTH
                    );
                }
            }
        }

        return List.copyOf(faces);
    }

    /**
     * A face is visible when its neighboring cell is either
     * outside the host block or empty.
     *
     * This is what creates the newly exposed interior walls
     * of a chisel cut.
     */
    private static void addIfExposed(
            MicroblockGrid grid,
            List<Face> faces,
            int x,
            int y,
            int z,
            Direction direction
    ) {
        int neighborX =
                x + direction.getStepX();

        int neighborY =
                y + direction.getStepY();

        int neighborZ =
                z + direction.getStepZ();

        boolean outside =
                neighborX < 0
                || neighborX >= 16
                || neighborY < 0
                || neighborY >= 16
                || neighborZ < 0
                || neighborZ >= 16;

        boolean exposed =
                outside
                || !grid.isOccupied(
                        neighborX,
                        neighborY,
                        neighborZ
                );

        if (exposed) {
            faces.add(
                    new Face(
                            x,
                            y,
                            z,
                            direction
                    )
            );
        }
    }

    /**
     * Useful diagnostic count for CI and future renderer
     * performance measurements.
     */
    public static int visibleFaceCount(
            MicroblockGrid grid
    ) {
        return build(grid).size();
    }
}
