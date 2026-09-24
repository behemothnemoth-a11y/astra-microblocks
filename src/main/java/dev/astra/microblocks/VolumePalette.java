package dev.astra.microblocks;

import java.util.*;
import net.minecraft.nbt.CompoundTag;

/** Versioned, bounded local palette: zero means empty; every other index is a supported block state. */
public final class VolumePalette {
    private VolumePalette() {}
    public static CompoundTag write(MicroblockVolume volume) {
        var tag=new CompoundTag(); tag.putInt("version",2);tag.putString("original",volume.original().id());
        var palette=new ArrayList<>(volume.materials().keySet());tag.putInt("size",palette.size());
        for(int i=0;i<palette.size();i++) tag.putString("material_"+i,palette.get(i).id());
        int bits=Math.max(1,32-Integer.numberOfLeadingZeros(palette.size()));int stride=64/bits;
        var indices=new long[(4096+stride-1)/stride];var lookup=new HashMap<HostMaterial,Integer>();
        for(int i=0;i<palette.size();i++) lookup.put(palette.get(i),i+1);
        for(int i=0;i<4096;i++) {var material=volume.materialAt(i%16,i/256,(i/16)%16);int index=material==null?0:lookup.get(material);indices[i/stride]|=(long)index<<((i%stride)*bits);}
        tag.putInt("bits",bits);tag.putLongArray("cells",indices);return tag;
    }
    public static Optional<MicroblockVolume> read(CompoundTag tag) {
        if(tag.getIntOr("version",0)!=2) return Optional.empty();
        int size=tag.getIntOr("size",-1); if(size<0 || size>4096) return Optional.empty();
        var original=HostMaterial.find(tag.getStringOr("original","")); if(original.isEmpty()) return Optional.empty();
        var palette=new ArrayList<HostMaterial>();
        for(int i=0;i<size;i++) {
            var material=HostMaterial.find(tag.getStringOr("material_"+i,""));
            if(material.isEmpty() || palette.contains(material.get())) return Optional.empty();palette.add(material.get());
        }
        int bits=tag.getIntOr("bits",0);if(bits!=Math.max(1,32-Integer.numberOfLeadingZeros(size))) return Optional.empty();
        int stride=64/bits;long[] indices=tag.getLongArray("cells").orElse(new long[0]);if(indices.length!=(4096+stride-1)/stride) return Optional.empty();
        var result=MicroblockVolume.empty(original.get());
        for(int i=0;i<4096;i++) {int index=(int)((indices[i/stride]>>>((i%stride)*bits))&((1L<<bits)-1));if(index<0 || index>size) return Optional.empty();if(index>0) result.add(i%16,i/256,(i/16)%16,palette.get(index-1));}
        return Optional.of(result);
    }
}
