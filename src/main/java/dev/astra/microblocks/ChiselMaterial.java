package dev.astra.microblocks;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;

/** Original preserves the old repair behavior; explicit choices allow inlays. */
public enum ChiselMaterial {
    ORIGINAL("original", "Original"), STONE("stone", "Stone"), OAK("oak", "Oak planks");
    private final String id, label;
    ChiselMaterial(String id,String label) { this.id=id; this.label=label; }
    public String id() { return id; }
    public String label() { return label; }
    public ChiselMaterial next() { return values()[(ordinal()+1)%values().length]; }
    public HostMaterial resolve(BlockState host) {
        return this == ORIGINAL ? HostMaterial.of(host) : this == STONE ? HostMaterial.STONE : HostMaterial.OAK_PLANKS;
    }
    public static ChiselMaterial read(ItemStack tool) {
        String id=tool.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getStringOr("astra_material","original");
        for (var material : values()) if (material.id.equals(id)) return material;
        return ORIGINAL;
    }
    public void store(ItemStack tool) {
        CustomData.update(DataComponents.CUSTOM_DATA,tool,tag -> tag.putString("astra_material",id));
    }
}
