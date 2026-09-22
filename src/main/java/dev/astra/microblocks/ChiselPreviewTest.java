package dev.astra.microblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Compare preview coverage with actual authoritative edits, without mutating the inspected grid. */
public final class ChiselPreviewTest {
    private ChiselPreviewTest() {}

    public static void run() {
        var random = new java.util.Random(4105);
        for (int pattern = 0; pattern < 4; pattern++) {
            var grid = new MicroblockGrid();
            for (int y=0;y<16;y++) for (int z=0;z<16;z++) for (int x=0;x<16;x++)
                if (pattern == 1 || (pattern == 2 && (x+y+z)%3==0) || (pattern == 3 && random.nextBoolean()))
                    grid.remove(x,y,z);
            var before = grid.copy();
            for (ChiselMode mode : ChiselMode.values()) for (Direction face : Direction.values()) {
                var cell = new MicroblockHitResolver.Cell(7,15,8);
                var preview = ChiselPreview.create(grid,mode,cell,face);
                var selection = mode.selection(cell,face);
                var covered = new MicroblockGrid(); covered.clear();
                for (var box : preview.boxes()) {
                    for (int y=box.minY();y<box.maxY();y++) for (int z=box.minZ();z<box.maxZ();z++)
                        for (int x=box.minX();x<box.maxX();x++)
                            require(covered.add(x,y,z), "overlapping preview cuboids");
                }
                for (int y=0;y<16;y++) for (int z=0;z<16;z++) for (int x=0;x<16;x++)
                    require(covered.isOccupied(x,y,z) == (grid.isOccupied(x,y,z) && selection.isOccupied(x,y,z)),
                            "preview coverage disagrees with selection: " + mode);
                require(preview.occupied()==grid.occupiedCount() && preview.affected()==covered.occupiedCount(),
                        "incorrect inspector counts");
                var host = new TestHostBlockEntity(BlockPos.ZERO,AstraMicroblocks.TEST_HOST.defaultBlockState());
                var missing = new MicroblockGrid();
                for (int y=0;y<16;y++) for (int z=0;z<16;z++) for (int x=0;x<16;x++)
                    if (grid.isOccupied(x,y,z)) missing.remove(x,y,z);
                host.removeCells(missing);
                require(host.removeCells(selection)==preview.affected(), "preview count differs from real cut");
                require(grid.equals(before), "inspection mutated the input grid");
                try { preview.boxes().clear(); throw new IllegalStateException("mutable preview snapshot"); }
                catch (UnsupportedOperationException expected) { }
            }
        }
        System.out.println("ASTRA_TEST: CHISEL_PREVIEW_PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
