package dev.astra.microblocks;

import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Portable shape/material data. History belongs to its world host, not the copied design. */
public final class SculptureData {
    public static final String ITEM_KEY="astra_sculpture", CLIPBOARD_KEY="astra_clipboard";
    private SculptureData() {}
    public static Optional<MicroblockVolume> read(ItemStack stack,String key) {
        var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound(key).orElse(null);
        if(tag==null || tag.getIntOr("version",0)!=1) return Optional.empty();
        var cells=tag.getLongArray("cells").orElse(new long[0]);
        var oak=tag.getLongArray("oak").orElse(new long[0]);
        String original=tag.getStringOr("original","");
        if(cells.length!=64 || oak.length!=64 || !(original.equals("stone")||original.equals("oak"))) return Optional.empty();
        return Optional.of(new MicroblockVolume(MicroblockGrid.fromLongArray(cells),MicroblockGrid.fromLongArray(oak),
                original.equals("oak")?HostMaterial.OAK_PLANKS:HostMaterial.STONE));
    }
    public static boolean has(ItemStack stack,String key) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().contains(key);
    }
    public static void write(ItemStack stack,String key,MicroblockVolume volume) {
        CompoundTag tag=new CompoundTag(); tag.putInt("version",1);
        tag.putString("original",volume.original()==HostMaterial.OAK_PLANKS?"oak":"stone");
        tag.putLongArray("cells",volume.occupancyCopy().toLongArray()); tag.putLongArray("oak",volume.oakCopy().toLongArray());
        CustomData.update(DataComponents.CUSTOM_DATA,stack,data -> data.put(key,tag));
    }
    public static ItemStack item(MicroblockVolume volume) {
        if(volume.isEmpty()) return ItemStack.EMPTY;
        var stack=new ItemStack(volume.original()==HostMaterial.OAK_PLANKS?AstraMicroblocks.OAK_HOST:AstraMicroblocks.TEST_HOST);
        write(stack,ITEM_KEY,volume); return stack;
    }
    public static MicroblockVolume transform(MicroblockVolume source,Direction.Axis axis,boolean mirror) {
        var cells=MicroblockGrid.fromLongArray(new long[64]); var oak=MicroblockGrid.fromLongArray(new long[64]);
        for(int y=0;y<16;y++) for(int z=0;z<16;z++) for(int x=0;x<16;x++) if(source.isOccupied(x,y,z)) {
            int nx=x,ny=y,nz=z;
            if(mirror) { switch(axis) { case X -> nx=15-x; case Y -> ny=15-y; case Z -> nz=15-z; } }
            else { switch(axis) { case X -> {ny=15-z;nz=y;} case Y -> {nx=15-z;nz=x;} case Z -> {nx=15-y;ny=x;} } }
            cells.add(nx,ny,nz); if(source.materialAt(x,y,z)==HostMaterial.OAK_PLANKS) oak.add(nx,ny,nz);
        }
        return new MicroblockVolume(cells,oak,source.original());
    }
    public static String summary(MicroblockVolume volume) {
        return "Stone "+volume.count(HostMaterial.STONE)+" / Oak "+volume.count(HostMaterial.OAK_PLANKS)+" / Empty "+(4096-volume.occupiedCount());
    }
}
