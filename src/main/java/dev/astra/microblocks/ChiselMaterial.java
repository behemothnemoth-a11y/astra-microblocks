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
    public static boolean custom(ItemStack tool) {return selected(tool).isPresent();}
    private static java.util.Optional<HostMaterial> selected(ItemStack tool) {
        return HostMaterial.find(tool.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getStringOr("astra_material_state",""));
    }
    public static HostMaterial resolve(ItemStack tool,TestHostBlockEntity host) {
        return selected(tool).orElseGet(() -> read(tool)==ORIGINAL?host.originalMaterial():read(tool).resolve(host.getBlockState()));
    }
    public static String label(ItemStack tool) {return selected(tool).map(HostMaterial::label).orElseGet(() -> read(tool).label());}
    public static java.util.List<HostMaterial> recent(ItemStack tool) {
        String text=tool.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getStringOr("astra_recent","");
        return java.util.Arrays.stream(text.split(";")).map(HostMaterial::find).flatMap(java.util.Optional::stream).distinct().limit(8).toList();
    }
    public static void select(ItemStack tool,HostMaterial material) {
        var recent=new java.util.ArrayList<>(recent(tool));recent.remove(material);recent.addFirst(material);
        if(material==HostMaterial.STONE) STONE.store(tool);else if(material==HostMaterial.OAK_PLANKS) OAK.store(tool);
        else CustomData.update(DataComponents.CUSTOM_DATA,tool,tag -> tag.putString("astra_material_state",material.id()));
        String ids=recent.stream().limit(8).map(HostMaterial::id).collect(java.util.stream.Collectors.joining(";"));
        CustomData.update(DataComponents.CUSTOM_DATA,tool,tag -> tag.putString("astra_recent",ids));
    }
    public void store(ItemStack tool) {
        CustomData.update(DataComponents.CUSTOM_DATA,tool,tag -> {tag.putString("astra_material",id);tag.remove("astra_material_state");});
    }
}
