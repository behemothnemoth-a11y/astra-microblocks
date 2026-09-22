package dev.astra.microblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Converts a Minecraft block hit into one exact 1/16-scale
 * microcell coordinate.
 *
 * Removal semantics:
 *
 * The hit position is biased a tiny distance INTO the block
 * along the opposite direction of the clicked face.
 *
 * This makes outer-face hits stable:
 *
 * east  face -> x = 15
 * west  face -> x = 0
 * top   face -> y = 15
 * bottom face -> y = 0
 */
public final class MicroblockHitResolver {

    /*
     * Much smaller than one microcell (1/16 = 0.0625),
     * but large enough to move a point off an exact boundary.
     */
    private static final double INWARD_EPSILON =
            1.0E-7;

    private MicroblockHitResolver() {
    }

    /** Adjacent empty-side cell; never clamp a placement back inside the host. */
    public static java.util.Optional<Cell> resolveForAddition(BlockHitResult hit) {
        Cell solid = resolveForRemoval(hit);
        Direction face = hit.getDirection();
        int x = solid.x() + face.getStepX();
        int y = solid.y() + face.getStepY();
        int z = solid.z() + face.getStepZ();
        if (x < 0 || x >= 16 || y < 0 || y >= 16 || z < 0 || z >= 16)
            return java.util.Optional.empty();
        return java.util.Optional.of(new Cell(x,y,z));
    }

    /**
     * Immutable resolved microcell coordinate.
     */
    public record Cell(
            int x,
            int y,
            int z
    ) {
        public Cell {
            MicroblockGrid.index(
                    x,
                    y,
                    z
            );
        }
    }

    /**
     * Resolves a real Minecraft block hit for removal.
     */
    public static Cell resolveForRemoval(
            BlockHitResult hit
    ) {
        if (hit == null) {
            throw new IllegalArgumentException(
                    "hit cannot be null"
            );
        }

        return resolveForRemoval(
                hit.getBlockPos(),
                hit.getLocation(),
                hit.getDirection()
        );
    }

    /**
     * Testable core resolver.
     */
    public static Cell resolveForRemoval(
            BlockPos blockPos,
            Vec3 hitLocation,
            Direction face
    ) {
        if (blockPos == null) {
            throw new IllegalArgumentException(
                    "blockPos cannot be null"
            );
        }

        if (hitLocation == null) {
            throw new IllegalArgumentException(
                    "hitLocation cannot be null"
            );
        }

        if (face == null) {
            throw new IllegalArgumentException(
                    "face cannot be null"
            );
        }

        /*
         * Convert world-space hit into block-local coordinates.
         */
        double localX =
                hitLocation.x
                - blockPos.getX();

        double localY =
                hitLocation.y
                - blockPos.getY();

        double localZ =
                hitLocation.z
                - blockPos.getZ();

        /*
         * Move very slightly inward from the clicked face.
         *
         * Direction's step vector points OUT of the clicked
         * block, so subtract it to move inward.
         */
        localX -=
                face.getStepX()
                * INWARD_EPSILON;

        localY -=
                face.getStepY()
                * INWARD_EPSILON;

        localZ -=
                face.getStepZ()
                * INWARD_EPSILON;

        int x =
                coordinate(localX);

        int y =
                coordinate(localY);

        int z =
                coordinate(localZ);

        return new Cell(
                x,
                y,
                z
        );
    }

    /**
     * Converts one local block coordinate into 0..15.
     *
     * Clamping is intentional. Minecraft hit math can produce
     * tiny floating-point excursions such as:
     *
     * -0.0000000001
     *  1.0000000001
     *
     * around exact block boundaries.
     */
    private static int coordinate(
            double local
    ) {
        int cell =
                (int) Math.floor(
                        local
                        * MicroblockGrid.SIZE
                );

        if (cell < 0) {
            return 0;
        }

        if (cell >= MicroblockGrid.SIZE) {
            return MicroblockGrid.SIZE - 1;
        }

        return cell;
    }
}
