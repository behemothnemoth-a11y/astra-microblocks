package dev.astra.microblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;

/** Two materials, the same edit pipeline, and adjacent hosts across three real boots. */
public final class MaterialWorkshopTest {
    private static final BlockPos STONE_POS = new BlockPos(24, 100, 0);
    private static final BlockPos OAK_POS = STONE_POS.east();
    private static final MicroblockHitResolver.Cell CORNER = new MicroblockHitResolver.Cell(7, 15, 8);

    private MaterialWorkshopTest() {}

    public static void phaseZero(ServerLevel level) {
        for (var block : new TestHostBlock[] {AstraMicroblocks.TEST_HOST, AstraMicroblocks.OAK_HOST}) {
            for (var mode : ChiselMode.values()) for (var face : Direction.values()) {
                var host = new TestHostBlockEntity(BlockPos.ZERO, block.defaultBlockState());
                var mask = mode.selection(CORNER, face);
                var expectedMaterial = HostMaterial.of(block.defaultBlockState());
                check(AstraMicroblocks.TEST_HOST_ENTITY.isValid(block.defaultBlockState()), "unsupported host entity");
                check(host.editCells(mask, ChiselOperation.CUT) == mask.occupiedCount(), "material brush cut");
                var cut = host.gridCopy();
                var preview = ChiselPreview.create(cut, mode, CORNER, face, ChiselOperation.ADD);
                check(host.editCells(mask, ChiselOperation.ADD) == preview.affected(), "material add preview");
                check(host.isFull() && host.undoEdit() && host.gridCopy().equals(cut), "material add undo");
                check(host.redoEdit() && host.isFull(), "material add redo");
                check(HostMaterial.of(host.getBlockState()) == expectedMaterial, "edit changed material");
            }
        }
        level.setBlock(STONE_POS, AstraMicroblocks.TEST_HOST.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(OAK_POS, AstraMicroblocks.OAK_HOST.defaultBlockState(), Block.UPDATE_ALL);
        for (var pos : new BlockPos[] {STONE_POS, OAK_POS}) {
            var host = host(level, pos);
            var neighbor = host(level, pos.equals(STONE_POS) ? OAK_POS : STONE_POS);
            var neighborGrid = neighbor.gridCopy();
            long neighborRevision = neighbor.revision();
            check(host.editCells(cavity(), ChiselOperation.CUT) == 64, "material cavity");
            check(host.editCells(repair(), ChiselOperation.ADD) == 8, "material partial repair");
            check(host.editCells(ChiselMode.LINE_Y.selection(new MicroblockHitResolver.Cell(12, 8, 12), Direction.UP),
                    ChiselOperation.CUT) == 16, "material line");
            check(host.undoEdit() && host.redoDepth() == 1, "material mixed undo");
            verify(level, pos, repaired());
            check(neighbor.gridCopy().equals(neighborGrid) && neighbor.revision() == neighborRevision,
                    "editing one material changed its neighbor");
        }
        System.out.println("ASTRA_TEST: MATERIAL_WORKSHOP_PASS");
    }

    public static void phaseOne(ServerLevel level) {
        for (var pos : new BlockPos[] {STONE_POS, OAK_POS}) {
            verify(level, pos, repaired());
            var host = host(level, pos);
            check(host.undoDepth() == 1 && host.redoDepth() == 0, "material saved history contract");
            check(host.undoEdit(), "material persisted undo");
            var unfilled = new MicroblockGrid();
            apply(unfilled, cavity(), false);
            verify(level, pos, unfilled);
            check(host.redoEdit(), "material redo after reload");
            verify(level, pos, repaired());
            check(host.editCells(bottomPlane(), ChiselOperation.CUT) == 256, "material edit after reload");
            verify(level, pos, layered());
        }
        System.out.println("ASTRA_TEST: MATERIAL_PERSISTENCE_PASS");
    }

    public static void phaseTwo(ServerLevel level) {
        for (var pos : new BlockPos[] {STONE_POS, OAK_POS}) {
            verify(level, pos, layered());
            var host = host(level, pos);
            check(host.undoEdit(), "second reload material undo");
            verify(level, pos, repaired());
            check(host.redoEdit(), "second reload material redo");
            verify(level, pos, layered());
        }
        System.out.println("ASTRA_TEST: MATERIAL_RESTART_PASS");
    }

    private static void verify(ServerLevel level, BlockPos pos, MicroblockGrid expected) {
        var block = pos.equals(STONE_POS) ? AstraMicroblocks.TEST_HOST : AstraMicroblocks.OAK_HOST;
        check(level.getBlockState(pos).is(block), "material block identity lost at " + pos);
        var host = host(level, pos);
        check(host.gridCopy().equals(expected), "material exact occupancy at " + pos);
        check(HostMaterial.of(host.getBlockState()) == (pos.equals(STONE_POS) ? HostMaterial.STONE : HostMaterial.OAK_PLANKS),
                "material lookup after reload");
        var state = level.getBlockState(pos);
        var shape = MicroblockShape.build(expected);
        check(!Shapes.joinIsNotEmpty(shape, state.getCollisionShape(level, pos, CollisionContext.empty()), BooleanOp.NOT_SAME),
                "material collision disagrees with grid");
        check(!Shapes.joinIsNotEmpty(shape, state.getShape(level, pos, CollisionContext.empty()), BooleanOp.NOT_SAME),
                "material selection disagrees with grid");
    }

    private static MicroblockGrid cavity() { return ChiselMode.CUBE_4.selection(CORNER, Direction.UP); }
    private static MicroblockGrid repair() {
        return ChiselMode.CUBE_2.selection(new MicroblockHitResolver.Cell(4, 12, 8), Direction.UP);
    }
    private static MicroblockGrid bottomPlane() {
        return ChiselMode.PLANE.selection(new MicroblockHitResolver.Cell(0, 0, 0), Direction.DOWN);
    }
    private static MicroblockGrid repaired() {
        var grid = new MicroblockGrid();
        apply(grid, cavity(), false);
        apply(grid, repair(), true);
        return grid;
    }
    private static MicroblockGrid layered() {
        var grid = repaired(); apply(grid, bottomPlane(), false); return grid;
    }
    private static void apply(MicroblockGrid grid, MicroblockGrid mask, boolean occupied) {
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++)
            if (mask.isOccupied(x,y,z)) grid.setOccupied(x,y,z,occupied);
    }
    private static TestHostBlockEntity host(ServerLevel level, BlockPos pos) {
        check(level.getBlockEntity(pos) instanceof TestHostBlockEntity, "material block entity missing");
        return (TestHostBlockEntity) level.getBlockEntity(pos);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
