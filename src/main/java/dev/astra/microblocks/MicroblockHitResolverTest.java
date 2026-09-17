package dev.astra.microblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Runtime assertions for converting Minecraft hit positions
 * into exact 1/16 microcell coordinates.
 */
public final class MicroblockHitResolverTest {

    private MicroblockHitResolverTest() {
    }

    public static void run() {
        testOuterFaces();
        testCorners();
        testInternalBoundaries();
        testOrdinaryHits();
        testNegativeWorldCoordinates();

        System.out.println(
                "ASTRA_TEST: MICROBLOCK_HIT_RESOLVER_PASS"
        );
    }

    private static void testOuterFaces() {
        BlockPos pos =
                new BlockPos(10, 20, 30);

        /*
         * West / east.
         */
        assertCell(
                pos,
                new Vec3(
                        10.0,
                        20.5,
                        30.5
                ),
                Direction.WEST,
                0,
                8,
                8,
                "west face"
        );

        assertCell(
                pos,
                new Vec3(
                        11.0,
                        20.5,
                        30.5
                ),
                Direction.EAST,
                15,
                8,
                8,
                "east face"
        );

        /*
         * Bottom / top.
         */
        assertCell(
                pos,
                new Vec3(
                        10.5,
                        20.0,
                        30.5
                ),
                Direction.DOWN,
                8,
                0,
                8,
                "bottom face"
        );

        assertCell(
                pos,
                new Vec3(
                        10.5,
                        21.0,
                        30.5
                ),
                Direction.UP,
                8,
                15,
                8,
                "top face"
        );

        /*
         * North / south.
         */
        assertCell(
                pos,
                new Vec3(
                        10.5,
                        20.5,
                        30.0
                ),
                Direction.NORTH,
                8,
                8,
                0,
                "north face"
        );

        assertCell(
                pos,
                new Vec3(
                        10.5,
                        20.5,
                        31.0
                ),
                Direction.SOUTH,
                8,
                8,
                15,
                "south face"
        );
    }

    private static void testCorners() {
        BlockPos pos =
                new BlockPos(0, 0, 0);

        /*
         * Exact top/east/south corner.
         *
         * The clicked face determines which axis receives the
         * inward bias. Clamping keeps the other two axes valid.
         */
        assertCell(
                pos,
                new Vec3(
                        1.0,
                        1.0,
                        1.0
                ),
                Direction.EAST,
                15,
                15,
                15,
                "maximum corner from east"
        );

        assertCell(
                pos,
                new Vec3(
                        1.0,
                        1.0,
                        1.0
                ),
                Direction.UP,
                15,
                15,
                15,
                "maximum corner from top"
        );

        assertCell(
                pos,
                new Vec3(
                        0.0,
                        0.0,
                        0.0
                ),
                Direction.WEST,
                0,
                0,
                0,
                "origin corner"
        );
    }

    private static void testInternalBoundaries() {
        BlockPos pos =
                new BlockPos(0, 0, 0);

        /*
         * Exact X boundary between cells 7 and 8.
         *
         * A hit reported on an east-facing surface belongs to
         * the material immediately west of that surface.
         */
        assertCell(
                pos,
                new Vec3(
                        8.0 / 16.0,
                        5.5 / 16.0,
                        9.5 / 16.0
                ),
                Direction.EAST,
                7,
                5,
                9,
                "internal east boundary"
        );

        /*
         * Same physical X coordinate but west-facing surface:
         * select the material immediately east of the surface.
         */
        assertCell(
                pos,
                new Vec3(
                        8.0 / 16.0,
                        5.5 / 16.0,
                        9.5 / 16.0
                ),
                Direction.WEST,
                8,
                5,
                9,
                "internal west boundary"
        );

        assertCell(
                pos,
                new Vec3(
                        4.5 / 16.0,
                        12.0 / 16.0,
                        6.5 / 16.0
                ),
                Direction.UP,
                4,
                11,
                6,
                "internal top boundary"
        );

        assertCell(
                pos,
                new Vec3(
                        4.5 / 16.0,
                        12.0 / 16.0,
                        6.5 / 16.0
                ),
                Direction.DOWN,
                4,
                12,
                6,
                "internal bottom boundary"
        );

        assertCell(
                pos,
                new Vec3(
                        3.5 / 16.0,
                        10.5 / 16.0,
                        5.0 / 16.0
                ),
                Direction.SOUTH,
                3,
                10,
                4,
                "internal south boundary"
        );

        assertCell(
                pos,
                new Vec3(
                        3.5 / 16.0,
                        10.5 / 16.0,
                        5.0 / 16.0
                ),
                Direction.NORTH,
                3,
                10,
                5,
                "internal north boundary"
        );
    }

    private static void testOrdinaryHits() {
        BlockPos pos =
                new BlockPos(100, 64, -50);

        /*
         * Centers of known cells.
         */
        assertCell(
                pos,
                new Vec3(
                        100.5 / 16.0 + 100.0,
                        3.5 / 16.0 + 64.0,
                        12.5 / 16.0 - 50.0
                ),
                Direction.UP,
                8,
                3,
                12,
                "ordinary cell center"
        );

        assertCell(
                pos,
                new Vec3(
                        1.5 / 16.0 + 100.0,
                        14.5 / 16.0 + 64.0,
                        6.5 / 16.0 - 50.0
                ),
                Direction.NORTH,
                1,
                14,
                6,
                "ordinary off-center hit"
        );
    }

    private static void testNegativeWorldCoordinates() {
        BlockPos pos =
                new BlockPos(
                        -20,
                        -5,
                        -100
                );

        assertCell(
                pos,
                new Vec3(
                        -19.0,
                        -4.5,
                        -99.5
                ),
                Direction.EAST,
                15,
                8,
                8,
                "negative-coordinate east face"
        );

        assertCell(
                pos,
                new Vec3(
                        -19.5,
                        -5.0,
                        -99.5
                ),
                Direction.DOWN,
                8,
                0,
                8,
                "negative-coordinate bottom face"
        );

        assertCell(
                pos,
                new Vec3(
                        -19.5,
                        -4.5,
                        -100.0
                ),
                Direction.NORTH,
                8,
                8,
                0,
                "negative-coordinate north face"
        );
    }

    private static void assertCell(
            BlockPos blockPos,
            Vec3 hit,
            Direction face,
            int expectedX,
            int expectedY,
            int expectedZ,
            String label
    ) {
        MicroblockHitResolver.Cell cell =
                MicroblockHitResolver
                        .resolveForRemoval(
                                blockPos,
                                hit,
                                face
                        );

        require(
                cell.x() == expectedX
                        && cell.y() == expectedY
                        && cell.z() == expectedZ,
                label
                        + " resolved to "
                        + cell.x()
                        + ","
                        + cell.y()
                        + ","
                        + cell.z()
                        + " instead of "
                        + expectedX
                        + ","
                        + expectedY
                        + ","
                        + expectedZ
        );
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
