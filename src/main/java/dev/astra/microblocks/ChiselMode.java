package dev.astra.microblocks;

import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Pure, block-local cut selection. Stable IDs are persisted on each tool. */
public enum ChiselMode {
    SINGLE("single", "Single cell", 1),
    LINE_X("line_x", "Line X (east/west)", 1),
    LINE_Y("line_y", "Line Y (vertical)", 1),
    LINE_Z("line_z", "Line Z (north/south)", 1),
    PLANE("plane", "Plane (clicked face)", 1),
    CUBE_2("cube_2", "2 × 2 × 2", 2),
    CUBE_4("cube_4", "4 × 4 × 4", 4),
    CUBE_8("cube_8", "8 × 8 × 8", 8);

    private static final String KEY = "astra_chisel_mode";
    private final String id;
    private final String label;
    private final int size;

    ChiselMode(String id, String label, int size) {
        this.id = id;
        this.label = label;
        this.size = size;
    }

    public String label() { return label; }
    public String id() { return id; }
    public ChiselMode next() { return values()[(ordinal() + 1) % values().length]; }

    public static ChiselMode read(ItemStack stack) {
        String id = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getStringOr(KEY, "single");
        for (ChiselMode mode : values()) if (mode.id.equals(id)) return mode;
        return SINGLE;
    }

    public void store(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(KEY, id));
    }

    /** A mask uses occupied cells to denote cells selected for removal. */
    public MicroblockGrid selection(MicroblockHitResolver.Cell hit, Direction face) {
        int minX = hit.x() / size * size, minY = hit.y() / size * size, minZ = hit.z() / size * size;
        int maxX = minX + size, maxY = minY + size, maxZ = minZ + size;
        if (this == LINE_X || (this == PLANE && face.getAxis() != Direction.Axis.X)) {
            minX = 0; maxX = 16;
        }
        if (this == LINE_Y || (this == PLANE && face.getAxis() != Direction.Axis.Y)) {
            minY = 0; maxY = 16;
        }
        if (this == LINE_Z || (this == PLANE && face.getAxis() != Direction.Axis.Z)) {
            minZ = 0; maxZ = 16;
        }
        MicroblockGrid mask = new MicroblockGrid();
        mask.clear();
        for (int y = minY; y < maxY; y++)
            for (int z = minZ; z < maxZ; z++)
                for (int x = minX; x < maxX; x++) mask.add(x, y, z);
        return mask;
    }
}
