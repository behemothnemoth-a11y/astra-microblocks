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
        greedyTests();
        benchmarkFixture();
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

    private static void benchmarkFixture() {
        String path=System.getProperty("astra.meshFixture");
        if(path==null) return;
        try {
            var root=net.minecraft.nbt.NbtIo.readCompressed(java.nio.file.Path.of(path),net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            var regions=root.getCompound("Regions").orElseThrow();
            int hosts=0,oldFaces=0,newQuads=0;long nanos=0;
            for(String key:regions.keySet()) {
                var region=regions.getCompound(key).orElseThrow();
                for(var tag:region.getList("TileEntities").orElseThrow()) {
                    var host=(net.minecraft.nbt.CompoundTag)tag;
                    var volume=VolumePalette.read(host.getCompound("volume_v4").orElseThrow()).orElseThrow();
                    verifyGreedy(volume);
                    oldFaces+=MicroblockRenderMesh.build(volume.occupancyCopy()).size();
                    long start=System.nanoTime();
                    newQuads+=MicroblockRenderMesh.buildGreedy(volume).size();
                    nanos+=System.nanoTime()-start;hosts++;
                }
            }
            require(hosts>1000,"stress fixture missing hosts");
            require(newQuads<oldFaces/3,"stress mesh reduction below regression target");
            System.out.println("ASTRA_TEST: STRESS_MESH_PASS hosts="+hosts+" old_faces="+oldFaces+" new_quads="+newQuads+" build_ms="+nanos/1000000);
        } catch(java.io.IOException ex) {throw new IllegalStateException(ex);}
    }

    private static void greedyTests() {
        var full=new MicroblockVolume(HostMaterial.STONE);
        require(MicroblockRenderMesh.buildGreedy(full).size()==6,"solid greedy mesh must be six quads");
        verifyGreedy(full);
        full.remove(8,8,8);verifyGreedy(full);
        require(MicroblockRenderMesh.buildGreedy(full).size()==12,"enclosed cavity greedy surface");
        for(int z=0;z<16;z++) full.remove(8,8,z);
        verifyGreedy(full);
        var random=new Random(728194);
        for(int sample=0;sample<16;sample++) {
            var volume=MicroblockVolume.empty(HostMaterial.STONE);
            for(int y=0;y<16;y++) for(int z=0;z<16;z++) for(int x=0;x<16;x++)
                if(random.nextDouble()<(sample+1)/17.0) volume.add(x,y,z,random.nextBoolean()?HostMaterial.STONE:HostMaterial.OAK_PLANKS);
            verifyGreedy(volume);
        }
        var checker=MicroblockVolume.empty(HostMaterial.STONE);
        for(int y=0;y<16;y++) for(int z=0;z<16;z++) for(int x=0;x<16;x++)
            if((x+y+z)%2==0) checker.add(x,y,z,HostMaterial.STONE);
        verifyGreedy(checker);
        require(MicroblockRenderMesh.buildGreedy(checker).size()==12288,"isolated cells must retain every face");
        System.out.println("ASTRA_TEST: GREEDY_MESH_PASS");
    }
    private static void verifyGreedy(MicroblockVolume volume) {
        var expected=new HashSet<>(MicroblockRenderMesh.build(volume.occupancyCopy()));
        var actual=new HashSet<MicroblockRenderMesh.Face>();
        var quads=MicroblockRenderMesh.buildGreedy(volume);
        require(quads.equals(MicroblockRenderMesh.buildGreedy(volume)),"greedy determinism");
        for(var q:quads) {
            var material=volume.materialAt(q.x(),q.y(),q.z());
            for(int v=0;v<q.height();v++) for(int u=0;u<q.width();u++) {
                int x=q.x()+(q.direction().getAxis()==Direction.Axis.X?0:u);
                int y=q.y()+(q.direction().getAxis()==Direction.Axis.Y?0:v);
                int z=q.z()+(q.direction().getAxis()==Direction.Axis.Z?0:q.direction().getAxis()==Direction.Axis.X?u:v);
                require(volume.materialAt(x,y,z)==material,"greedy rectangle crossed materials");
                require(actual.add(new MicroblockRenderMesh.Face(x,y,z,q.direction())),"duplicate greedy surface");
            }
            var a=q.vertex(0);var b=q.vertex(1);var c=q.vertex(2);
            int nx=(b.y()-a.y())*(c.z()-a.z())-(b.z()-a.z())*(c.y()-a.y());
            int ny=(b.z()-a.z())*(c.x()-a.x())-(b.x()-a.x())*(c.z()-a.z());
            int nz=(b.x()-a.x())*(c.y()-a.y())-(b.y()-a.y())*(c.x()-a.x());
            require(nx*q.direction().getStepX()+ny*q.direction().getStepY()+nz*q.direction().getStepZ()==q.width()*q.height(),"greedy winding or area");
        }
        require(actual.equals(expected),"greedy changed exposed surface");
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
