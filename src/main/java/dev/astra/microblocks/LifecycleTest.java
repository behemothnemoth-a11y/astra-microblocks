package dev.astra.microblocks;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * End-to-end Astra Microblocks persistence test.
 *
 * This test deliberately creates a nontrivial shape:
 *
 * - opposite corner
 * - one full 16-cell line
 * - one full 16x16 plane
 *
 * Then it proves:
 *
 * - exact occupancy
 * - exact persistence
 * - arbitrary additional editing
 * - whole-grid undo
 * - undo persistence
 */
public final class LifecycleTest {

    private static final BlockPos TEST_POS =
            new BlockPos(0, 100, 0);

    private static final Path PHASE_FILE =
            Path.of("astra-test-phase.txt");

    /*
     * Shape definition.
     *
     * Plane:
     * y = 0
     *
     * Line:
     * x = 7, z = 7, all y
     *
     * Extra corner:
     * 15,15,15
     *
     * The line intersects the plane once.
     *
     * Unique removed cells:
     * 256 + 16 - 1 + 1 = 272
     *
     * Expected occupied:
     * 4096 - 272 = 3824
     */
    private static final int EXPECTED_REMOVED =
            272;

    private static final int EXPECTED_OCCUPIED =
            4096 - EXPECTED_REMOVED;

    private LifecycleTest() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(
                LifecycleTest::run
        );
    }

    private static void run(MinecraftServer server) {
        try {
            int phase = readPhase();

            System.out.println(
                    "ASTRA_TEST: START phase=" + phase
            );

            switch (phase) {
                case 0 -> phaseZero(server);
                case 1 -> phaseOne(server);
                case 2 -> phaseTwo(server);

                default -> fail(
                        server,
                        "unexpected phase " + phase
                );
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();

            System.out.println(
                    "ASTRA_TEST: FAIL exception="
                    + throwable.getClass().getName()
            );

            server.halt(false);
        }
    }

    /*
     * BOOT 1
     *
     * Create a real arbitrary microblock pattern
     * and save it.
     */
    private static void phaseZero(
            MinecraftServer server
    ) throws IOException {

        testMicroblockGrid();

testMicroblockGrid();
MicroblockShapeTest.run();
MicroblockMesherTest.run();

ServerLevel level =
        server.overworld();
        require(
                level.setBlock(
                        TEST_POS,
                        AstraMicroblocks.TEST_HOST
                                .defaultBlockState(),
                        Block.UPDATE_ALL
                ),
                "could not place test host"
        );

        TestHostBlockEntity host =
                requireHost(level);

        require(
                host.isFull(),
                "new host was not completely solid"
        );

        require(
                host.occupiedCount() == 4096,
                "new host did not contain 4096 cells"
        );

        require(
                host.revision() == 0,
                "new host revision was not 0"
        );

        /*
         * Remove the entire bottom plane.
         */
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                require(
                        host.removeCell(
                                x,
                                0,
                                z
                        ),
                        "could not remove plane cell "
                        + x + ",0," + z
                );
            }
        }

        /*
         * Remove a vertical line.
         *
         * y=0 is already empty because it intersects
         * the plane, so begin at y=1.
         */
        for (int y = 1; y < 16; y++) {
            require(
                    host.removeCell(
                            7,
                            y,
                            7
                    ),
                    "could not remove line cell 7,"
                    + y + ",7"
            );
        }

        /*
         * Remove an unrelated opposite corner.
         */
        require(
                host.removeCell(
                        15,
                        15,
                        15
                ),
                "could not remove opposite corner"
        );

        verifyPattern(host);

        require(
                host.occupiedCount()
                        == EXPECTED_OCCUPIED,
                "phase 0 occupied count was "
                + host.occupiedCount()
                + " instead of "
                + EXPECTED_OCCUPIED
        );

        /*
         * Every successful individual edit currently
         * increments revision once:
         *
         * 256 plane
         * + 15 remaining line
         * + 1 corner
         * = 272
         */
        require(
                host.revision()
                        == EXPECTED_REMOVED,
                "phase 0 revision was "
                + host.revision()
                + " instead of "
                + EXPECTED_REMOVED
        );

        require(
                host.canUndo(),
                "arbitrary edits did not create undo state"
        );

        writePhase(1);

        System.out.println(
                "ASTRA_TEST: ARBITRARY_SHAPE_CREATED"
        );

        System.out.println(
                "ASTRA_TEST: PHASE0_PASS"
        );

        server.halt(false);
    }

    /*
     * BOOT 2
     *
     * Prove the arbitrary pattern survived.
     *
     * Then make one additional arbitrary edit,
     * undo it, and prove the complete previous
     * grid was restored.
     */
    private static void phaseOne(
            MinecraftServer server
    ) throws IOException {

        ServerLevel level =
                server.overworld();

        require(
                level.getBlockState(TEST_POS)
                        .is(AstraMicroblocks.TEST_HOST),
                "host did not persist across restart"
        );

        TestHostBlockEntity host =
                requireHost(level);

        verifyPattern(host);

        require(
                host.occupiedCount()
                        == EXPECTED_OCCUPIED,
                "arbitrary shape count did not persist"
        );

        require(
                host.revision()
                        == EXPECTED_REMOVED,
                "arbitrary shape revision did not persist"
        );

        System.out.println(
                "ASTRA_TEST: ARBITRARY_SHAPE_PERSISTENCE_PASS"
        );

        /*
         * Choose a cell that must still be solid.
         */
        require(
                host.isOccupied(
                        3,
                        8,
                        12
                ),
                "temporary-edit target was already empty"
        );

        MicroblockGrid beforeEdit =
                host.gridCopy();

        long revisionBefore =
                host.revision();

        require(
                host.removeCell(
                        3,
                        8,
                        12
                ),
                "temporary arbitrary edit failed"
        );

        require(
                !host.isOccupied(
                        3,
                        8,
                        12
                ),
                "temporary edit did not remove target"
        );

        require(
                host.occupiedCount()
                        == EXPECTED_OCCUPIED - 1,
                "temporary edit count was incorrect"
        );

        require(
                host.revision()
                        == revisionBefore + 1,
                "temporary edit did not increment revision"
        );

        host.undo();

        require(
                host.gridCopy().equals(
                        beforeEdit
                ),
                "undo did not restore complete grid"
        );

        require(
                host.occupiedCount()
                        == EXPECTED_OCCUPIED,
                "undo did not restore occupied count"
        );

        require(
                host.revision()
                        == revisionBefore + 2,
                "undo did not increment revision"
        );

        require(
                !host.canUndo(),
                "undo state remained after undo"
        );

        verifyPattern(host);

        writePhase(2);

        System.out.println(
                "ASTRA_TEST: ARBITRARY_UNDO_PASS"
        );

        System.out.println(
                "ASTRA_TEST: PHASE1_PASS"
        );

        server.halt(false);
    }

    /*
     * BOOT 3
     *
     * Prove the exact post-undo arbitrary shape
     * survived another real Minecraft restart.
     */
    private static void phaseTwo(
            MinecraftServer server
    ) throws IOException {

        ServerLevel level =
                server.overworld();

        require(
                level.getBlockState(TEST_POS)
                        .is(AstraMicroblocks.TEST_HOST),
                "host disappeared after second restart"
        );

        TestHostBlockEntity host =
                requireHost(level);

        verifyPattern(host);

        require(
                host.occupiedCount()
                        == EXPECTED_OCCUPIED,
                "post-undo shape count did not persist"
        );

        require(
                host.revision()
                        == EXPECTED_REMOVED + 2,
                "post-undo revision did not persist"
        );

        require(
                !host.canUndo(),
                "undo unexpectedly returned after restart"
        );

        require(
                host.isOccupied(
                        3,
                        8,
                        12
                ),
                "undone arbitrary cell did not persist"
        );

        writePhase(3);

        System.out.println(
                "ASTRA_TEST: ARBITRARY_RESTART_PASS"
        );

        System.out.println(
                "ASTRA_TEST: LIFECYCLE_PASS"
        );

        server.halt(false);
    }    /**
     * Verifies every cell against the expected pattern.
     *
     * This is intentionally exhaustive: all 4096 cells
     * are checked after each relevant world load.
     */
    private static void verifyPattern(
            TestHostBlockEntity host
    ) {
        int occupied = 0;
        int empty = 0;

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {

                    boolean expectedEmpty =
                            y == 0
                            || (x == 7 && z == 7)
                            || (
                                    x == 15
                                    && y == 15
                                    && z == 15
                            );

                    boolean actual =
                            host.isOccupied(
                                    x,
                                    y,
                                    z
                            );

                    require(
                            actual != expectedEmpty,
                            "pattern mismatch at "
                            + x + ","
                            + y + ","
                            + z
                    );

                    if (actual) {
                        occupied++;
                    } else {
                        empty++;
                    }
                }
            }
        }

        require(
                empty == EXPECTED_REMOVED,
                "pattern contained "
                + empty
                + " empty cells instead of "
                + EXPECTED_REMOVED
        );

        require(
                occupied == EXPECTED_OCCUPIED,
                "pattern contained "
                + occupied
                + " occupied cells instead of "
                + EXPECTED_OCCUPIED
        );
    }

    private static TestHostBlockEntity requireHost(
            ServerLevel level
    ) {
        BlockEntity blockEntity =
                level.getBlockEntity(TEST_POS);

        require(
                blockEntity instanceof TestHostBlockEntity,
                "expected TestHostBlockEntity"
        );

        return (TestHostBlockEntity) blockEntity;
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

    private static void fail(
            MinecraftServer server,
            String message
    ) {
        System.out.println(
                "ASTRA_TEST: FAIL " + message
        );

        server.halt(false);
    }

    /**
     * Standalone MicroblockGrid invariants.
     */
    private static void testMicroblockGrid() {
        MicroblockGrid grid =
                new MicroblockGrid();

        require(
                grid.isFull(),
                "new grid was not full"
        );

        require(
                grid.occupiedCount() == 4096,
                "full grid did not contain 4096 cells"
        );

        require(
                grid.remove(
                        15,
                        15,
                        15
                ),
                "could not remove corner cell"
        );

        require(
                !grid.isOccupied(
                        15,
                        15,
                        15
                ),
                "removed corner remained occupied"
        );

        require(
                grid.occupiedCount() == 4095,
                "corner removal count was incorrect"
        );

        require(
                !grid.remove(
                        15,
                        15,
                        15
                ),
                "duplicate removal reported a change"
        );

        require(
                grid.remove(
                        0,
                        0,
                        0
                ),
                "could not remove opposite corner"
        );

        require(
                grid.occupiedCount() == 4094,
                "second removal count was incorrect"
        );

        MicroblockGrid copy =
                grid.copy();

        require(
                copy.equals(grid),
                "grid copy was not identical"
        );

        require(
                copy != grid,
                "grid copy was not independent"
        );

        long[] serialized =
                grid.toLongArray();

        require(
                serialized.length == 64,
                "serialized grid was not 64 longs"
        );

        MicroblockGrid restored =
                MicroblockGrid.fromLongArray(
                        serialized
                );

        require(
                restored.equals(grid),
                "serialized grid did not round-trip"
        );

        /*
         * Verify defensive serialization.
         */
        serialized[0] = 0L;

        require(
                restored.equals(grid),
                "external array mutated restored grid"
        );

        restored.clear();

        require(
                restored.isEmpty(),
                "clear did not empty grid"
        );

        require(
                restored.occupiedCount() == 0,
                "empty grid count was not 0"
        );

        restored.fill();

        require(
                restored.isFull(),
                "fill did not restore grid"
        );

        require(
                restored.occupiedCount() == 4096,
                "refilled grid count was incorrect"
        );

        /*
         * Prove all XYZ coordinates map uniquely onto
         * exactly 4096 bit positions.
         */
        boolean[] seen =
                new boolean[
                        MicroblockGrid.CELL_COUNT
                ];

        int visited = 0;

        for (int y = 0;
             y < MicroblockGrid.SIZE;
             y++) {

            for (int z = 0;
                 z < MicroblockGrid.SIZE;
                 z++) {

                for (int x = 0;
                     x < MicroblockGrid.SIZE;
                     x++) {

                    int index =
                            MicroblockGrid.index(
                                    x,
                                    y,
                                    z
                            );

                    require(
                            index >= 0
                            && index < 4096,
                            "index outside 0..4095"
                    );

                    require(
                            !seen[index],
                            "duplicate index "
                            + index
                    );

                    seen[index] = true;
                    visited++;
                }
            }
        }

        require(
                visited == 4096,
                "did not visit all coordinates"
        );

        for (int i = 0; i < seen.length; i++) {
            require(
                    seen[i],
                    "index "
                    + i
                    + " was never mapped"
            );
        }

        require(
                MicroblockGrid.index(
                        0,
                        0,
                        0
                ) == 0,
                "origin index was incorrect"
        );

        require(
                MicroblockGrid.index(
                        15,
                        15,
                        15
                ) == 4095,
                "maximum index was incorrect"
        );

        boolean rejectedNegative = false;

        try {
            MicroblockGrid.index(
                    -1,
                    0,
                    0
            );
        } catch (
                IndexOutOfBoundsException expected
        ) {
            rejectedNegative = true;
        }

        require(
                rejectedNegative,
                "negative coordinate was accepted"
        );

        boolean rejectedSixteen = false;

        try {
            MicroblockGrid.index(
                    16,
                    0,
                    0
            );
        } catch (
                IndexOutOfBoundsException expected
        ) {
            rejectedSixteen = true;
        }

        require(
                rejectedSixteen,
                "coordinate 16 was accepted"
        );

        System.out.println(
                "ASTRA_TEST: MICROBLOCK_GRID_PASS"
        );
    }

    private static int readPhase()
            throws IOException {

        if (!Files.exists(PHASE_FILE)) {
            return 0;
        }

        return Integer.parseInt(
                Files.readString(
                        PHASE_FILE,
                        StandardCharsets.UTF_8
                ).trim()
        );
    }

    private static void writePhase(int phase)
            throws IOException {

        Files.writeString(
                PHASE_FILE,
                Integer.toString(phase),
                StandardCharsets.UTF_8
        );
    }
}
