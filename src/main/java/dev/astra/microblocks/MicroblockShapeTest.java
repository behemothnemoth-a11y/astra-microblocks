package dev.astra.microblocks;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Runtime assertions for exact 1/16-scale MicroblockShape geometry.
 */
public final class MicroblockShapeTest {

    private static final double CELL =
            1.0 / 16.0;

    private static final double EPSILON =
            0.000001;

    private MicroblockShapeTest() {
    }

    public static void run() {
        testFullAndEmpty();
        testSingleCells();
        testArbitraryPattern();

        System.out.println(
                "ASTRA_TEST: MICROBLOCK_SHAPE_PASS"
        );
    }

    private static void testFullAndEmpty() {
        MicroblockGrid full =
                new MicroblockGrid();

        VoxelShape fullShape =
                MicroblockShape.build(full);

        require(
                !fullShape.isEmpty(),
                "full grid produced empty shape"
        );

        require(
                containsPoint(
                        fullShape,
                        0.5,
                        0.5,
                        0.5
                ),
                "full shape did not contain center"
        );

        MicroblockGrid empty =
                new MicroblockGrid();

        empty.clear();

        VoxelShape emptyShape =
                MicroblockShape.build(empty);

        require(
                emptyShape.isEmpty(),
                "empty grid produced geometry"
        );
    }

    private static void testSingleCells() {
        /*
         * Origin cell.
         */
        MicroblockGrid origin =
                new MicroblockGrid();

        origin.clear();

        require(
                origin.add(0, 0, 0),
                "could not add origin cell"
        );

        VoxelShape originShape =
                MicroblockShape.build(origin);

        require(
                containsPoint(
                        originShape,
                        CELL / 2.0,
                        CELL / 2.0,
                        CELL / 2.0
                ),
                "origin cell did not contain its center"
        );

        require(
                !containsPoint(
                        originShape,
                        CELL + CELL / 2.0,
                        CELL / 2.0,
                        CELL / 2.0
                ),
                "origin shape leaked into neighboring cell"
        );

        assertBounds(
                originShape,
                0.0,
                0.0,
                0.0,
                CELL,
                CELL,
                CELL,
                "origin cell"
        );

        /*
         * Maximum corner cell.
         */
        MicroblockGrid corner =
                new MicroblockGrid();

        corner.clear();

        require(
                corner.add(15, 15, 15),
                "could not add maximum corner"
        );

        VoxelShape cornerShape =
                MicroblockShape.build(corner);

        double cornerCenter =
                15.5 / 16.0;

        require(
                containsPoint(
                        cornerShape,
                        cornerCenter,
                        cornerCenter,
                        cornerCenter
                ),
                "maximum corner did not contain its center"
        );

        require(
                !containsPoint(
                        cornerShape,
                        14.5 / 16.0,
                        cornerCenter,
                        cornerCenter
                ),
                "maximum corner leaked into neighbor"
        );

        assertBounds(
                cornerShape,
                15.0 / 16.0,
                15.0 / 16.0,
                15.0 / 16.0,
                1.0,
                1.0,
                1.0,
                "maximum corner"
        );
    }

    private static void testArbitraryPattern() {
        MicroblockGrid grid =
                new MicroblockGrid();

        /*
         * Remove bottom plane.
         */
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                require(
                        grid.remove(x, 0, z),
                        "failed carving shape-test plane"
                );
            }
        }

        /*
         * Remove vertical line.
         */
        for (int y = 1; y < 16; y++) {
            require(
                    grid.remove(7, y, 7),
                    "failed carving shape-test line"
            );
        }

        /*
         * Remove unrelated corner.
         */
        require(
                grid.remove(15, 15, 15),
                "failed carving shape-test corner"
        );

        require(
                grid.occupiedCount() == 3824,
                "shape-test occupancy was not 3824"
        );

        VoxelShape shape =
                MicroblockShape.build(grid);

        require(
                !shape.isEmpty(),
                "arbitrary grid produced empty shape"
        );

        /*
         * Every point in the bottom 1/16 layer should
         * now be outside the physical shape.
         */
        require(
                !containsPoint(
                        shape,
                        0.5,
                        0.5 / 16.0,
                        0.5
                ),
                "bottom plane still had geometry"
        );

        /*
         * The carved vertical line should be empty.
         */
        require(
                !containsPoint(
                        shape,
                        7.5 / 16.0,
                        8.5 / 16.0,
                        7.5 / 16.0
                ),
                "vertical line still had geometry"
        );

        /*
         * Adjacent cell must remain solid.
         */
        require(
                containsPoint(
                        shape,
                        8.5 / 16.0,
                        8.5 / 16.0,
                        7.5 / 16.0
                ),
                "solid neighbor beside line disappeared"
        );

        /*
         * Unrelated corner must be empty.
         */
        require(
                !containsPoint(
                        shape,
                        15.5 / 16.0,
                        15.5 / 16.0,
                        15.5 / 16.0
                ),
                "carved corner still had geometry"
        );

        /*
         * Nearby upper corner cell remains solid.
         */
        require(
                containsPoint(
                        shape,
                        14.5 / 16.0,
                        15.5 / 16.0,
                        15.5 / 16.0
                ),
                "neighbor beside carved corner disappeared"
        );

        /*
         * Since only the bottom plane was removed,
         * overall Y bounds should begin exactly at 1/16.
         */
        AABB bounds =
                shape.bounds();

        requireClose(
                bounds.minY,
                CELL,
                "arbitrary shape minY"
        );

        requireClose(
                bounds.maxY,
                1.0,
                "arbitrary shape maxY"
        );
    }

    /**
     * Tests whether a point lies inside any AABB making
     * up the VoxelShape.
     *
     * We use strict interior points rather than boundaries
     * so edge ownership cannot make the test ambiguous.
     */
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

    private static void assertBounds(
            VoxelShape shape,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            String label
    ) {
        require(
                !shape.isEmpty(),
                label + " unexpectedly empty"
        );

        AABB bounds =
                shape.bounds();

        requireClose(
                bounds.minX,
                minX,
                label + " minX"
        );

        requireClose(
                bounds.minY,
                minY,
                label + " minY"
        );

        requireClose(
                bounds.minZ,
                minZ,
                label + " minZ"
        );

        requireClose(
                bounds.maxX,
                maxX,
                label + " maxX"
        );

        requireClose(
                bounds.maxY,
                maxY,
                label + " maxY"
        );

        requireClose(
                bounds.maxZ,
                maxZ,
                label + " maxZ"
        );
    }

    private static void requireClose(
            double actual,
            double expected,
            String label
    ) {
        require(
                Math.abs(actual - expected)
                        <= EPSILON,
                label
                + " was "
                + actual
                + " instead of "
                + expected
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
