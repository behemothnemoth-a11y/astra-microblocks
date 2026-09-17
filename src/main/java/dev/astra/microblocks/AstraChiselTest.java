package dev.astra.microblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * End-to-end runtime test for the core Astra Chisel operation.
 *
 * This exercises:
 *
 * hit position
 * -> microcell resolution
 * -> block-entity edit
 * -> occupancy change
 * -> revision change
 * -> physical geometry change
 */
public final class AstraChiselTest {

    private static final BlockPos TEST_POS =
            new BlockPos(4, 100, 4);

    private static final double EPSILON =
            0.000001;

    private AstraChiselTest() {
    }

    public static void run(
            ServerLevel level
    ) {
        /*
         * Place a fresh sculptable host.
         */
        require(
                level.setBlock(
                        TEST_POS,
                        AstraMicroblocks.TEST_HOST
                                .defaultBlockState(),
                        Block.UPDATE_ALL
                ),
                "could not place chisel-test host"
        );

        require(
                level.getBlockEntity(TEST_POS)
                        instanceof TestHostBlockEntity,
                "chisel-test host had no block entity"
        );

        TestHostBlockEntity host =
                (TestHostBlockEntity)
                        level.getBlockEntity(TEST_POS);

        require(
                host.occupiedCount() == 4096,
                "fresh chisel-test host was not full"
        );

        require(
                host.revision() == 0,
                "fresh chisel-test revision was not 0"
        );

        /*
         * Simulate clicking the EAST surface at the center
         * of microcell:
         *
         * x = 15
         * y = 8
         * z = 6
         */
        Vec3 hit =
                new Vec3(
                        TEST_POS.getX() + 1.0,
                        TEST_POS.getY()
                                + 8.5 / 16.0,
                        TEST_POS.getZ()
                                + 6.5 / 16.0
                );

        MicroblockHitResolver.Cell cell =
                MicroblockHitResolver
                        .resolveForRemoval(
                                TEST_POS,
                                hit,
                                Direction.EAST
                        );

        require(
                cell.x() == 15
                        && cell.y() == 8
                        && cell.z() == 6,
                "chisel hit resolved to "
                        + cell.x()
                        + ","
                        + cell.y()
                        + ","
                        + cell.z()
                        + " instead of 15,8,6"
        );

        /*
         * Verify geometry exists before the edit.
         */
        VoxelShape before =
                MicroblockShape.build(
                        host.gridCopy()
                );

        require(
                containsPoint(
                        before,
                        15.5 / 16.0,
                        8.5 / 16.0,
                        6.5 / 16.0
                ),
                "target cell had no geometry before chisel"
        );

        /*
         * Perform the same authoritative edit used by
         * AstraChiselItem.
         */
        require(
                host.removeCell(
                        cell.x(),
                        cell.y(),
                        cell.z()
                ),
                "chisel edit failed"
        );

        require(
                !host.isOccupied(
                        15,
                        8,
                        6
                ),
                "chiseled cell remained occupied"
        );

        require(
                host.occupiedCount() == 4095,
                "chisel did not produce 4095 occupied cells"
        );

        require(
                host.revision() == 1,
                "chisel revision was not 1"
        );

        require(
                host.canUndo(),
                "chisel did not create undo state"
        );

        /*
         * Verify physical geometry disappeared from exactly
         * the edited cell.
         */
        VoxelShape after =
                MicroblockShape.build(
                        host.gridCopy()
                );

        require(
                !containsPoint(
                        after,
                        15.5 / 16.0,
                        8.5 / 16.0,
                        6.5 / 16.0
                ),
                "chiseled cell still had physical geometry"
        );

        /*
         * Face-neighbor must remain untouched.
         */
        require(
                containsPoint(
                        after,
                        14.5 / 16.0,
                        8.5 / 16.0,
                        6.5 / 16.0
                ),
                "neighbor beside chiseled cell disappeared"
        );

        /*
         * Nearby Y neighbor must also remain untouched.
         */
        require(
                containsPoint(
                        after,
                        15.5 / 16.0,
                        9.5 / 16.0,
                        6.5 / 16.0
                ),
                "upper neighbor disappeared"
        );

        System.out.println(
                "ASTRA_TEST: CHISEL_CORE_PASS"
        );

        /*
         * Remove temporary test block so it cannot interfere
         * with the existing lifecycle host.
         */
        level.removeBlock(
                TEST_POS,
                false
        );
    }

    private static boolean containsPoint(
            VoxelShape shape,
            double x,
            double y,
            double z
    ) {
        for (AABB box : shape.toAabbs()) {
            if (
                    x > box.minX + EPSILON
                    && x < box.maxX - EPSILON
                    && y > box.minY + EPSILON
                    && y < box.maxY - EPSILON
                    && z > box.minZ + EPSILON
                    && z < box.maxZ - EPSILON
            ) {
                return true;
            }
        }

        return false;
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
