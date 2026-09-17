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
 * Automated world-lifecycle and microblock-grid test.
 *
 * Phase 0:
 *   test 4096-cell grid
 *   place host
 *   carve
 *   save
 *   stop
 *
 * Phase 1:
 *   restart
 *   verify carve persisted
 *   undo
 *   save
 *   stop
 *
 * Phase 2:
 *   restart
 *   verify undo persisted
 *   PASS
 *   stop
 */
public final class LifecycleTest {

    private static final BlockPos TEST_POS =
            new BlockPos(0, 100, 0);

    private static final Path PHASE_FILE =
            Path.of("astra-test-phase.txt");

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

    private static void phaseZero(
            MinecraftServer server
    ) throws IOException {

        testMicroblockGrid();

        ServerLevel level = server.overworld();

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
                !host.isCarved(),
                "new host was already carved"
        );

        require(
                !host.canUndo(),
                "new host unexpectedly had undo"
        );

        require(
                host.revision() == 0,
                "new host revision was not 0"
        );

        host.carveCorner();

        require(
                host.isCarved(),
                "carve did not change state"
        );

        require(
                host.canUndo(),
                "carve did not enable undo"
        );

        require(
                host.revision() == 1,
                "carve revision was not 1"
        );

        require(
                AstraMicroblocks.TEST_HOST
                        .isCornerCarved(
                                level,
                                TEST_POS
                        ),
                "block did not expose carved geometry state"
        );

        writePhase(1);

        System.out.println(
                "ASTRA_TEST: PHASE0_PASS"
        );

        server.halt(false);
    }

    private static void phaseOne(
            MinecraftServer server
    ) throws IOException {

        ServerLevel level = server.overworld();

        require(
                level.getBlockState(TEST_POS)
                        .is(AstraMicroblocks.TEST_HOST),
                "host did not persist across restart"
        );

        TestHostBlockEntity host =
                requireHost(level);

        require(
                host.isCarved(),
                "carved state did not persist"
        );

        require(
                host.canUndo(),
                "undo state did not persist"
        );

        require(
                host.revision() == 1,
                "revision 1 did not persist"
        );

        require(
                AstraMicroblocks.TEST_HOST
                        .isCornerCarved(
                                level,
                                TEST_POS
                        ),
                "carved geometry state did not persist"
        );

        System.out.println(
                "ASTRA_TEST: CARVE_PERSISTENCE_PASS"
        );

        host.undo();

        require(
                !host.isCarved(),
                "undo did not restore solid state"
        );

        require(
                !host.canUndo(),
                "undo flag remained enabled"
        );

        require(
                host.revision() == 2,
                "undo revision was not 2"
        );

        require(
                !AstraMicroblocks.TEST_HOST
                        .isCornerCarved(
                                level,
                                TEST_POS
                        ),
                "geometry did not return to solid"
        );

        writePhase(2);

        System.out.println(
                "ASTRA_TEST: PHASE1_PASS"
        );

        server.halt(false);
    }

    private static void phaseTwo(
            MinecraftServer server
    ) throws IOException {

        ServerLevel level = server.overworld();

        require(
                level.getBlockState(TEST_POS)
                        .is(AstraMicroblocks.TEST_HOST),
                "host disappeared after undo restart"
        );

        TestHostBlockEntity host =
                requireHost(level);

        require(
                !host.isCarved(),
                "undo did not persist"
        );

        require(
                !host.canUndo(),
                "undo flag returned after restart"
        );

        require(
                host.revision() == 2,
                "revision 2 did not persist"
        );

        require(
                !AstraMicroblocks.TEST_HOST
                        .isCornerCarved(
                                level,
                                TEST_POS
                        ),
                "solid geometry state did not persist"
        );

        writePhase(3);

        System.out.println(
                "ASTRA_TEST: LIFECYCLE_PASS"
        );

        server.halt(false);
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
            throw new IllegalStateException(message);
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
    }    private static void testMicroblockGrid() {
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
                grid.remove(15, 15, 15),
                "could not remove corner cell"
        );

        require(
                !grid.isOccupied(15, 15, 15),
                "removed corner remained occupied"
        );

        require(
                grid.occupiedCount() == 4095,
                "corner removal did not produce 4095 cells"
        );

        require(
                !grid.remove(15, 15, 15),
                "duplicate removal reported a change"
        );

        require(
                grid.remove(0, 0, 0),
                "could not remove opposite corner"
        );

        require(
                !grid.isOccupied(0, 0, 0),
                "removed opposite corner remained occupied"
        );

        require(
                grid.occupiedCount() == 4094,
                "second removal did not produce 4094 cells"
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
         * Verify the serialization boundary is defensive.
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
                "fill did not restore full grid"
        );

        require(
                restored.occupiedCount() == 4096,
                "refilled grid did not contain 4096 cells"
        );

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
                "maximum index was not 4095"
        );

        /*
         * Prove all 4096 XYZ coordinates map to unique
         * bit positions.
         */
        boolean[] seen =
                new boolean[MicroblockGrid.CELL_COUNT];

        int visited = 0;

        for (int y = 0; y < MicroblockGrid.SIZE; y++) {
            for (int z = 0; z < MicroblockGrid.SIZE; z++) {
                for (int x = 0; x < MicroblockGrid.SIZE; x++) {

                    int index =
                            MicroblockGrid.index(
                                    x,
                                    y,
                                    z
                            );

                    require(
                            !seen[index],
                            "duplicate microblock index "
                            + index
                    );

                    seen[index] = true;
                    visited++;
                }
            }
        }

        require(
                visited == 4096,
                "did not visit all 4096 coordinates"
        );

        /*
         * Basic bounds tests.
         */
        boolean rejectedNegative = false;

        try {
            MicroblockGrid.index(-1, 0, 0);
        } catch (IndexOutOfBoundsException expected) {
            rejectedNegative = true;
        }

        require(
                rejectedNegative,
                "negative coordinate was accepted"
        );

        boolean rejectedSixteen = false;

        try {
            MicroblockGrid.index(16, 0, 0);
        } catch (IndexOutOfBoundsException expected) {
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
