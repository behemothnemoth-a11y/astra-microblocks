package dev.astra.microblocks;

import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.BlockHitResult;

public enum ChiselOperation {
    CUT("cut", "Cut"), ADD("add", "Add"), REPLACE("replace", "Replace");

    private final String id;
    private final String label;
    ChiselOperation(String id, String label) { this.id = id; this.label = label; }
    public String id() { return id; }
    public String label() { return label; }

    public static ChiselOperation read(ItemStack tool) {
        String id=tool.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getStringOr("astra_operation","cut");
        for (var operation : values()) if (operation.id.equals(id)) return operation;
        return CUT;
    }

    public void store(ItemStack tool) {
        CustomData.update(DataComponents.CUSTOM_DATA, tool, tag -> tag.putString("astra_operation", id));
    }

    public Optional<MicroblockHitResolver.Cell> target(BlockHitResult hit) {
        return this != ADD ? Optional.of(MicroblockHitResolver.resolveForRemoval(hit))
                : MicroblockHitResolver.resolveForAddition(hit);
    }
}
