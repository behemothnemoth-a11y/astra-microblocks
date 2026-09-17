package dev.astra.microblocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts occupied microcells into non-overlapping rectangular
 * cuboids.
 *
 * Coordinates use half-open cell ranges:
 *
 * [minX, maxX)
 * [minY, maxY)
 * [minZ, maxZ)
 *
 * Example:
 *
 * cell (0,0,0) -> Cuboid(0,0,0,1,1,1)
 *
 * full grid -> Cuboid(0,0,0,16,16,16)
 */
public final class MicroblockMesher {

    private MicroblockMesher() {
    }

    public record Cuboid(
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ
    ) {
        public Cuboid {
            checkMin("minX", minX);
            checkMin("minY", minY);
            checkMin("minZ", minZ);

            checkMax("maxX", maxX);
            checkMax("maxY", maxY);
            checkMax("maxZ", maxZ);

            if (minX >= maxX
                    || minY >= maxY
                    || minZ >= maxZ) {

                throw new IllegalArgumentException(
                        "cuboid must have positive volume"
                );
            }
        }

        public int width() {
            return maxX - minX;
        }

        public int height() {
            return maxY - minY;
        }

        public int depth() {
            return maxZ - minZ;
        }

        public int volume() {
            return width()
                    * height()
                    * depth();
        }

        public boolean contains(
                int x,
                int y,
                int z
        ) {
            return x >= minX
                    && x < maxX
                    && y >= minY
                    && y < maxY
                    && z >= minZ
                    && z < maxZ;
        }

        private static void checkMin(
                String name,
                int value
        ) {
            if (value < 0
                    || value >= MicroblockGrid.SIZE) {

                throw new IllegalArgumentException(
                        name
                        + " outside 0..15: "
                        + value
                );
            }
        }

        private static void checkMax(
                String name,
                int value
        ) {
            if (value <= 0
                    || value > MicroblockGrid.SIZE) {

                throw new IllegalArgumentException(
                        name
                        + " outside 1..16: "
                        + value
                );
            }
        }
    }

    /**
     * Greedily covers every occupied cell exactly once.
     *
     * Algorithm:
     *
     * 1. Find the next unused occupied cell.
     * 2. Expand along X as far as possible.
     * 3. Expand that complete strip along Z.
     * 4. Expand that complete rectangle along Y.
     * 5. Mark the resulting cuboid consumed.
     *
     * This is deterministic and lossless. It is not guaranteed
     * to produce the mathematically minimum number of cuboids,
     * but it dramatically reduces common architectural shapes.
     */
    public static List<Cuboid> mesh(
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

        if (grid.isFull()) {
            return List.of(
                    new Cuboid(
                            0,
                            0,
                            0,
                            16,
                            16,
                            16
                    )
            );
        }

        boolean[] consumed =
                new boolean[
                        MicroblockGrid.CELL_COUNT
                ];

        List<Cuboid> cuboids =
                new ArrayList<>();

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {

                    if (!available(
                            grid,
                            consumed,
                            x,
                            y,
                            z
                    )) {
                        continue;
                    }

                    int maxX =
                            expandX(
                                    grid,
                                    consumed,
                                    x,
                                    y,
                                    z
                            );

                    int maxZ =
                            expandZ(
                                    grid,
                                    consumed,
                                    x,
                                    y,
                                    z,
                                    maxX
                            );

                    int maxY =
                            expandY(
                                    grid,
                                    consumed,
                                    x,
                                    y,
                                    z,
                                    maxX,
                                    maxZ
                            );

                    Cuboid cuboid =
                            new Cuboid(
                                    x,
                                    y,
                                    z,
                                    maxX,
                                    maxY,
                                    maxZ
                            );

                    consume(
                            consumed,
                            cuboid
                    );

                    cuboids.add(cuboid);
                }
            }
        }

        return List.copyOf(cuboids);
    }

    private static int expandX(
            MicroblockGrid grid,
            boolean[] consumed,
            int startX,
            int y,
            int z
    ) {
        int x = startX;

        while (x < 16
                && available(
                        grid,
                        consumed,
                        x,
                        y,
                        z
                )) {

            x++;
        }

        return x;
    }

    private static int expandZ(
            MicroblockGrid grid,
            boolean[] consumed,
            int minX,
            int y,
            int startZ,
            int maxX
    ) {
        int z =
                startZ + 1;

        while (z < 16) {

            if (!rectangleAvailable(
                    grid,
                    consumed,
                    minX,
                    maxX,
                    y,
                    z
            )) {
                break;
            }

            z++;
        }

        return z;
    }

    private static int expandY(
            MicroblockGrid grid,
            boolean[] consumed,
            int minX,
            int startY,
            int minZ,
            int maxX,
            int maxZ
    ) {
        int y =
                startY + 1;

        while (y < 16) {

            if (!layerAvailable(
                    grid,
                    consumed,
                    minX,
                    maxX,
                    y,
                    minZ,
                    maxZ
            )) {
                break;
            }

            y++;
        }

        return y;
    }

    private static boolean rectangleAvailable(
            MicroblockGrid grid,
            boolean[] consumed,
            int minX,
            int maxX,
            int y,
            int z
    ) {
        for (int x = minX; x < maxX; x++) {

            if (!available(
                    grid,
                    consumed,
                    x,
                    y,
                    z
            )) {
                return false;
            }
        }

        return true;
    }

    private static boolean layerAvailable(
            MicroblockGrid grid,
            boolean[] consumed,
            int minX,
            int maxX,
            int y,
            int minZ,
            int maxZ
    ) {
        for (int z = minZ; z < maxZ; z++) {
            for (int x = minX; x < maxX; x++) {

                if (!available(
                        grid,
                        consumed,
                        x,
                        y,
                        z
                )) {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean available(
            MicroblockGrid grid,
            boolean[] consumed,
            int x,
            int y,
            int z
    ) {
        int index =
                MicroblockGrid.index(
                        x,
                        y,
                        z
                );

        return grid.isOccupied(
                x,
                y,
                z
        ) && !consumed[index];
    }

    private static void consume(
            boolean[] consumed,
            Cuboid cuboid
    ) {
        for (
                int y = cuboid.minY();
                y < cuboid.maxY();
                y++
        ) {
            for (
                    int z = cuboid.minZ();
                    z < cuboid.maxZ();
                    z++
            ) {
                for (
                        int x = cuboid.minX();
                        x < cuboid.maxX();
                        x++
                ) {
                    int index =
                            MicroblockGrid.index(
                                    x,
                                    y,
                                    z
                            );

                    if (consumed[index]) {
                        throw new IllegalStateException(
                                "mesher overlap at "
                                + x + ","
                                + y + ","
                                + z
                        );
                    }

                    consumed[index] = true;
                }
            }
        }
    }
}
