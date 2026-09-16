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
 * Temporary automated world-lifecycle test.
 *
 * Phase 0:
 *   place host -> carve -> save -> stop
 *
 * Phase 1:
 *   restart -> verify carve persisted -> undo -> save -> stop
 *
 * Phase 2:
 *   restart -> verify undo persisted -> PASS -> stop
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
