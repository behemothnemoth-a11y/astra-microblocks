package dev.astra.microblocks;

import java.util.List;
import net.minecraft.core.Direction;

/** Immutable inspection snapshot. Empty cells never appear in the highlighted region. */
public record ChiselPreview(int occupied, int affected, List<MicroblockMesher.Cuboid> boxes) {
    public ChiselPreview { boxes = List.copyOf(boxes); }

    public static ChiselPreview create(MicroblockGrid grid, ChiselMode mode,
                                       MicroblockHitResolver.Cell cell, Direction face) {
        MicroblockGrid selected = mode.selection(cell, face);
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
            if (!grid.isOccupied(x, y, z)) selected.remove(x, y, z);
        return new ChiselPreview(grid.occupiedCount(), selected.occupiedCount(), MicroblockMesher.mesh(selected));
    }
}
