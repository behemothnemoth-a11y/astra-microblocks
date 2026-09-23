package dev.astra.microblocks;

import java.util.List;
import net.minecraft.core.Direction;

/** Immutable inspection snapshot of cells that would change in the selected operation. */
public record ChiselPreview(int occupied, int affected, List<MicroblockMesher.Cuboid> boxes) {
    public ChiselPreview { boxes = List.copyOf(boxes); }

    public static ChiselPreview create(MicroblockGrid grid, ChiselMode mode,
                                       MicroblockHitResolver.Cell cell, Direction face) {
        return create(grid,mode,cell,face,ChiselOperation.CUT);
    }

    public static ChiselPreview create(MicroblockGrid grid, ChiselMode mode,
                                       MicroblockHitResolver.Cell cell, Direction face, ChiselOperation operation) {
        if (operation == ChiselOperation.REPLACE) throw new IllegalArgumentException("Replace preview requires materials");
        if (cell == null) return new ChiselPreview(grid.occupiedCount(),0,List.of());
        MicroblockGrid selected = mode.selection(cell, face);
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
            if (grid.isOccupied(x, y, z) == (operation == ChiselOperation.ADD)) selected.remove(x, y, z);
        return new ChiselPreview(grid.occupiedCount(), selected.occupiedCount(), MicroblockMesher.mesh(selected));
    }
    public static ChiselPreview create(MicroblockVolume volume, ChiselMode mode,
            MicroblockHitResolver.Cell cell, Direction face, ChiselOperation operation, HostMaterial material) {
        if (operation != ChiselOperation.REPLACE) return create(volume.occupancyCopy(),mode,cell,face,operation);
        if (cell == null) return new ChiselPreview(volume.occupiedCount(),0,List.of());
        var selected=mode.selection(cell,face);
        for (int y=0;y<16;y++) for (int z=0;z<16;z++) for (int x=0;x<16;x++)
            if (!volume.isOccupied(x,y,z) || volume.materialAt(x,y,z)==material) selected.remove(x,y,z);
        return new ChiselPreview(volume.occupiedCount(),selected.occupiedCount(),MicroblockMesher.mesh(selected));
    }
}
