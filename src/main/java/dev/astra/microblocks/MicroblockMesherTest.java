package dev.astra.microblocks;

import java.util.List;
import java.util.Random;

/**
 * Exhaustive correctness tests for MicroblockMesher.
 *
 * The mesher is not allowed to:
 *
 * - lose occupied cells
 * - cover empty cells
 * - overlap cuboids
 * - change total occupied volume
 */
public final class MicroblockMesherTest {

    private MicroblockMesherTest() {
    }

    public static void run() {
        testEmpty();
        testFull();
        testSingleCell();
        testArchitecturalPattern();
        testCheckerboard();
        testRandomPatterns();

        System.out.println(
                "ASTRA_TEST: MICROBLOCK_MESHER_PASS"
        );
    }

    private static void testEmpty() {
        MicroblockGrid grid =
                new MicroblockGrid();

        grid.clear();

        List<MicroblockMesher.Cuboid> cuboids =
                MicroblockMesher.mesh(grid);

        require(
                cuboids.isEmpty(),
                "empty grid produced cuboids"
        );

        verifyExactCoverage(
                grid,
                cuboids,
                "empty"
        );
    }

    private static void testFull() {
        MicroblockGrid grid =
                new MicroblockGrid();

        List<MicroblockMesher.Cuboid> cuboids =
                MicroblockMesher.mesh(grid);

        require(
                cuboids.size() == 1,
                "full grid produced "
                        + cuboids.size()
                        + " cuboids instead of 1"
        );

        MicroblockMesher.Cuboid only =
                cuboids.getFirst();

        require(
                only.minX() == 0
                        && only.minY() == 0
                        && only.minZ() == 0
                        && only.maxX() == 16
                        && only.maxY() == 16
                        && only.maxZ() == 16,
                "full-grid cuboid had incorrect bounds"
        );

        require(
                only.volume() == 4096,
                "full-grid cuboid volume was not 4096"
        );

        verifyExactCoverage(
                grid,
                cuboids,
                "full"
        );
    }

    private static void testSingleCell() {
        MicroblockGrid grid =
                new MicroblockGrid();

        grid.clear();

        grid.add(
                9,
                4,
                13
        );

        List<MicroblockMesher.Cuboid> cuboids =
                MicroblockMesher.mesh(grid);

        require(
                cuboids.size() == 1,
                "single cell did not produce one cuboid"
        );

        MicroblockMesher.Cuboid only =
                cuboids.getFirst();

        require(
                only.contains(
                        9,
                        4,
                        13
                ),
                "single-cell cuboid missed its cell"
        );

        require(
                only.volume() == 1,
                "single-cell cuboid volume was not 1"
        );

        verifyExactCoverage(
                grid,
                cuboids,
                "single"
        );
    }

    private static void testArchitecturalPattern() {
        MicroblockGrid grid =
                new MicroblockGrid();

        /*
         * Same 272-cell carve used by LifecycleTest:
         *
         * bottom plane
         * + vertical line
         * + unrelated corner
         */
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                grid.remove(
                        x,
                        0,
                        z
                );
            }
        }

        for (int y = 1; y < 16; y++) {
            grid.remove(
                    7,
                    y,
                    7
            );
        }

        grid.remove(
                15,
                15,
                15
        );

        require(
                grid.occupiedCount() == 3824,
                "architectural test grid count was incorrect"
        );

        List<MicroblockMesher.Cuboid> cuboids =
                MicroblockMesher.mesh(grid);

        verifyExactCoverage(
                grid,
                cuboids,
                "architectural"
        );

        /*
         * This isn't a strict optimality requirement.
         * It simply guards against accidentally degenerating
         * into roughly one box per occupied cell.
         */
        require(
                cuboids.size() < 100,
                "architectural pattern produced too many cuboids: "
                        + cuboids.size()
        );

        System.out.println(
                "ASTRA_TEST: MESHER_ARCHITECTURAL_CUBOIDS="
                        + cuboids.size()
        );
    }

    private static void testCheckerboard() {
        MicroblockGrid grid =
                new MicroblockGrid();

        grid.clear();

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {

                    if (((x + y + z) & 1) == 0) {
                        grid.add(
                                x,
                                y,
                                z
                        );
                    }
                }
            }
        }

        require(
                grid.occupiedCount() == 2048,
                "checkerboard count was not 2048"
        );

        List<MicroblockMesher.Cuboid> cuboids =
                MicroblockMesher.mesh(grid);

        verifyExactCoverage(
                grid,
                cuboids,
                "checkerboard"
        );

        /*
         * Every occupied checkerboard cell is isolated by
         * empty face-neighbors, so no rectangular merge is
         * possible.
         */
        require(
                cuboids.size() == 2048,
                "checkerboard unexpectedly produced "
                        + cuboids.size()
                        + " cuboids"
        );
    }

    private static void testRandomPatterns() {
        /*
         * Fixed seed = deterministic CI.
         */
        Random random =
                new Random(
                        0xA57A4096L
                );

        for (int test = 0; test < 32; test++) {

            MicroblockGrid grid =
                    new MicroblockGrid();

            grid.clear();

            /*
             * Vary density from sparse to dense.
             */
            double density =
                    0.05
                    + (
                            test
                            / 31.0
                    ) * 0.90;

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {

                        if (random.nextDouble()
                                < density) {

                            grid.add(
                                    x,
                                    y,
                                    z
                            );
                        }
                    }
                }
            }

            List<MicroblockMesher.Cuboid> cuboids =
                    MicroblockMesher.mesh(grid);

            verifyExactCoverage(
                    grid,
                    cuboids,
                    "random-" + test
            );
        }

        System.out.println(
                "ASTRA_TEST: MESHER_RANDOM_PATTERNS_PASS"
        );
    }

    /**
     * Reconstructs the entire 4096-cell occupancy map from
     * the generated cuboids and compares it cell-for-cell
     * against the source grid.
     */
    private static void verifyExactCoverage(
            MicroblockGrid grid,
            List<MicroblockMesher.Cuboid> cuboids,
            String label
    ) {
        boolean[] covered =
                new boolean[
                        MicroblockGrid.CELL_COUNT
                ];

        int totalVolume = 0;

        for (
                MicroblockMesher.Cuboid cuboid
                : cuboids
        ) {
            totalVolume +=
                    cuboid.volume();

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

                        require(
                                !covered[index],
                                label
                                        + " cuboid overlap at "
                                        + x + ","
                                        + y + ","
                                        + z
                        );

                        require(
                                grid.isOccupied(
                                        x,
                                        y,
                                        z
                                ),
                                label
                                        + " cuboid covered empty cell at "
                                        + x + ","
                                        + y + ","
                                        + z
                        );

                        covered[index] = true;
                    }
                }
            }
        }

        require(
                totalVolume
                        == grid.occupiedCount(),
                label
                        + " cuboid volume "
                        + totalVolume
                        + " != occupied count "
                        + grid.occupiedCount()
        );

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {

                    int index =
                            MicroblockGrid.index(
                                    x,
                                    y,
                                    z
                            );

                    require(
                            covered[index]
                                    == grid.isOccupied(
                                            x,
                                            y,
                                            z
                                    ),
                            label
                                    + " coverage mismatch at "
                                    + x + ","
                                    + y + ","
                                    + z
                    );
                }
            }
        }
    }

    private static void require(
            boolean condition,
            String message
    ) {
        if (!condition) {
            throw new IllegalStateException(
                    message
            );
        }
    }
}
