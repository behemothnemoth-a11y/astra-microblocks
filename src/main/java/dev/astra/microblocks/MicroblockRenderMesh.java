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

        /** Corners wind counterclockwise when viewed from the exposed (air) side. */
        public Vertex vertex(int corner) {
            if (corner < 0 || corner > 3) {
                throw new IndexOutOfBoundsException("corner must be between 0 and 3");
            }
            int right = corner >= 2 ? 1 : 0;
            int top = corner == 0 || corner == 3 ? 1 : 0;
            return switch (direction) {
                case WEST -> new Vertex(x, y + top, z + right);
                case EAST -> new Vertex(x + 1, y + top, z + 1 - right);
                case DOWN -> new Vertex(x + right, y, z + top);
                case UP -> new Vertex(x + right, y + 1, z + 1 - top);
                case NORTH -> new Vertex(x + 1 - right, y + top, z);
                case SOUTH -> new Vertex(x + right, y + top, z + 1);
            };
        }
    }

    /** Exact grid-boundary position; divide by SIZE when submitting block-local geometry. */
    public record Vertex(int x, int y, int z) {}

    /** Rectangle of coplanar exposed cells with the same complete material state. */
    public record Quad(int x, int y, int z, Direction direction, int width, int height) {
        public Vertex vertex(int corner) {
            var unit = new Face(x, y, z, direction).vertex(corner);
            return switch (direction.getAxis()) {
                case X -> new Vertex(unit.x(), y + (unit.y()-y)*height, z + (unit.z()-z)*width);
                case Y -> new Vertex(x + (unit.x()-x)*width, unit.y(), z + (unit.z()-z)*height);
                case Z -> new Vertex(x + (unit.x()-x)*width, y + (unit.y()-y)*height, unit.z());
            };
        }
    }

    /** Greedy surface meshing never merges across material states or cavity boundaries. */
    public static List<Quad> buildGreedy(MicroblockVolume volume) {
        var result = new ArrayList<Quad>();
        var mask = new HostMaterial[256];
        for (var direction : Direction.values()) for (int slice=0; slice<16; slice++) {
            java.util.Arrays.fill(mask, null);
            for (int v=0; v<16; v++) for (int u=0; u<16; u++) {
                int x=direction.getAxis()==Direction.Axis.X?slice:u;
                int y=direction.getAxis()==Direction.Axis.Y?slice:v;
                int z=direction.getAxis()==Direction.Axis.Z?slice:direction.getAxis()==Direction.Axis.X?u:v;
                var material=volume.materialAt(x,y,z);
                if(material==null) continue;
                int nx=x+direction.getStepX(),ny=y+direction.getStepY(),nz=z+direction.getStepZ();
                if(nx<0 || nx>=16 || ny<0 || ny>=16 || nz<0 || nz>=16 || volume.materialAt(nx,ny,nz)==null)
                    mask[v*16+u]=material;
            }
            for(int v=0;v<16;v++) for(int u=0;u<16;u++) {
                var material=mask[v*16+u]; if(material==null) continue;
                int width=1,height=1;
                while(u+width<16 && mask[v*16+u+width]==material) width++;
                outer: while(v+height<16) {
                    for(int k=0;k<width;k++) if(mask[(v+height)*16+u+k]!=material) break outer;
                    height++;
                }
                int x=direction.getAxis()==Direction.Axis.X?slice:u;
                int y=direction.getAxis()==Direction.Axis.Y?slice:v;
                int z=direction.getAxis()==Direction.Axis.Z?slice:direction.getAxis()==Direction.Axis.X?u:v;
                result.add(new Quad(x,y,z,direction,width,height));
                for(int j=0;j<height;j++) for(int k=0;k<width;k++) mask[(v+j)*16+u+k]=null;
            }
        }
        return List.copyOf(result);
    }

    /**
     * Generates every exposed face in grid index order (X fastest, then Z, then Y).
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
