package dev.astra.microblocks;

import net.minecraft.core.Direction;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Surface topology tests, also run on a dedicated server by the lifecycle gate. */
public final class MicroblockRenderMeshTest {
    private MicroblockRenderMeshTest() {}

    public static void run() {
        MicroblockGrid grid = new MicroblockGrid();
        check(grid, 1536, "full");
        grid.remove(8, 8, 8);
        check(grid, 1542, "enclosed cavity");
        grid.remove(9, 8, 8);
        check(grid, 1546, "adjacent cavities");
        grid.fill();
        grid.remove(8, 15, 8);
        check(grid, 1540, "surface recess");
        grid.fill();
        grid.remove(15, 15, 15);
        check(grid, 1536, "corner recess");
        grid.clear();
        check(grid, 0, "empty");
        grid.add(9, 4, 13);
        check(grid, 6, "isolated cell");
        grid.add(10, 4, 13);
        check(grid, 10, "adjacent solid cells");

        // A through-hole must expose its walls without either end being capped.
        grid.fill();
        for (int z = 0; z < 16; z++) grid.remove(8, 8, z);
        check(grid, 1598, "through tunnel");

        Random random = new Random(0xA57A_FACE);
        for (int sample = 0; sample < 24; sample++) {
            grid.clear();
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        grid.setOccupied(x, y, z, random.nextDouble() < (sample + 1) / 25.0);
                    }
                }
            }
            verify(grid, "random " + sample);
        }

        grid.fill();
        List<MicroblockRenderMesh.Face> snapshot = MicroblockRenderMesh.build(grid);
        grid.clear();
        require(snapshot.size() == 1536, "mesh changed with source grid");
        expect(UnsupportedOperationException.class, snapshot::clear);
        expect(IllegalArgumentException.class, () -> MicroblockRenderMesh.build(null));
        expect(IllegalArgumentException.class, () -> new MicroblockRenderMesh.Face(0, 0, 0, null));
        expect(IndexOutOfBoundsException.class, () -> new MicroblockRenderMesh.Face(16, 0, 0, Direction.UP));
        expect(IndexOutOfBoundsException.class, () -> new MicroblockRenderMesh.Face(0, -1, 0, Direction.UP));
        expect(IndexOutOfBoundsException.class, () -> new MicroblockRenderMesh.Face(0, 0, 16, Direction.UP));
        expect(IndexOutOfBoundsException.class, () -> snapshot.getFirst().vertex(4));
        expect(IndexOutOfBoundsException.class, () -> snapshot.getFirst().vertex(-1));
        System.out.println("ASTRA_TEST: MICROBLOCK_RENDER_MESH_PASS");
    }

    private static void check(MicroblockGrid grid, int count, String label) {
        require(MicroblockRenderMesh.visibleFaceCount(grid) == count, label + " face count");
        verify(grid, label);
    }

    private static void verify(MicroblockGrid grid, String label) {
        MicroblockGrid before = grid.copy();
        List<MicroblockRenderMesh.Face> mesh = MicroblockRenderMesh.build(grid);
        Set<MicroblockRenderMesh.Face> actual = new HashSet<>(mesh);
        require(actual.size() == mesh.size(), label + " duplicate face");
        require(mesh.equals(MicroblockRenderMesh.build(grid)), label + " nondeterministic mesh");
        require(grid.equals(before), label + " mutated grid");
        for (MicroblockRenderMesh.Face face : mesh) verifyVertices(face);

        // Independent oracle: scan each axis boundary once. A solid/air transition
        // requires exactly one face, directed from the solid side toward the air.
        Set<MicroblockRenderMesh.Face> expected = new HashSet<>();
        for (int axis = 0; axis < 3; axis++) {
            for (int plane = 0; plane <= 16; plane++) {
                for (int a = 0; a < 16; a++) {
                    for (int b = 0; b < 16; b++) {
                        int x = axis == 0 ? plane : a;
                        int y = axis == 1 ? plane : (axis == 0 ? a : b);
                        int z = axis == 2 ? plane : b;
                        int dx = axis == 0 ? 1 : 0;
                        int dy = axis == 1 ? 1 : 0;
                        int dz = axis == 2 ? 1 : 0;
                        boolean low = plane > 0 && grid.isOccupied(x - dx, y - dy, z - dz);
                        boolean high = plane < 16 && grid.isOccupied(x, y, z);
                        if (low != high) {
                            Direction positive = axis == 0 ? Direction.EAST : axis == 1 ? Direction.UP : Direction.SOUTH;
                            expected.add(low
                                    ? new MicroblockRenderMesh.Face(x - dx, y - dy, z - dz, positive)
                                    : new MicroblockRenderMesh.Face(x, y, z, positive.getOpposite()));
                        }
                    }
                }
            }
        }
        require(actual.equals(expected), label + " missing or hidden faces");
    }

    private static void verifyVertices(MicroblockRenderMesh.Face face) {
        Set<MicroblockRenderMesh.Vertex> vertices = new HashSet<>();
        for (int corner = 0; corner < 4; corner++) {
            MicroblockRenderMesh.Vertex vertex = face.vertex(corner);
            vertices.add(vertex);
            require(vertex.x() >= face.x() && vertex.x() <= face.x() + 1
                    && vertex.y() >= face.y() && vertex.y() <= face.y() + 1
                    && vertex.z() >= face.z() && vertex.z() <= face.z() + 1, "vertex outside cell");
            int plane = switch (face.direction()) {
                case WEST -> vertex.x() - face.x();
                case EAST -> face.x() + 1 - vertex.x();
                case DOWN -> vertex.y() - face.y();
                case UP -> face.y() + 1 - vertex.y();
                case NORTH -> vertex.z() - face.z();
                case SOUTH -> face.z() + 1 - vertex.z();
            };
            require(plane == 0, "vertex on wrong face plane");
        }
        require(vertices.size() == 4, "degenerate quad");
        var a = face.vertex(0);
        var b = face.vertex(1);
        var c = face.vertex(2);
        int ux = b.x() - a.x(), uy = b.y() - a.y(), uz = b.z() - a.z();
        int vx = c.x() - a.x(), vy = c.y() - a.y(), vz = c.z() - a.z();
        require(uy * vz - uz * vy == face.direction().getStepX()
                && uz * vx - ux * vz == face.direction().getStepY()
                && ux * vy - uy * vx == face.direction().getStepZ(), "wrong winding or area");
    }

    private static void expect(Class<? extends RuntimeException> type, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            require(type.isInstance(exception), "wrong validation exception: " + exception);
            return;
        }
        throw new IllegalStateException("Expected " + type.getSimpleName());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
