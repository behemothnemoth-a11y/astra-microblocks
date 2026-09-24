package dev.astra.microblocks;

import java.util.*;

/** Exact block-state cells with the established occupancy grid for geometry. */
public final class MicroblockVolume {
    private final MicroblockGrid occupied;
    private final HostMaterial[] cells;
    private final HostMaterial original;
    public MicroblockVolume(HostMaterial original) {
        this.occupied=new MicroblockGrid(); this.original=Objects.requireNonNull(original);
        cells=new HostMaterial[4096]; Arrays.fill(cells,original);
    }
    public MicroblockVolume(MicroblockGrid occupied,MicroblockGrid oak,HostMaterial original) {
        this.occupied=occupied.copy(); this.original=Objects.requireNonNull(original); cells=new HostMaterial[4096];
        for(int y=0;y<16;y++) for(int z=0;z<16;z++) for(int x=0;x<16;x++) if(occupied.isOccupied(x,y,z))
            cells[MicroblockGrid.index(x,y,z)]=oak.isOccupied(x,y,z)?HostMaterial.OAK_PLANKS:HostMaterial.STONE;
    }
    private MicroblockVolume(HostMaterial[] cells,HostMaterial original) {
        this.cells=cells.clone();this.original=Objects.requireNonNull(original);occupied=MicroblockGrid.fromLongArray(new long[64]);
        for(int i=0;i<4096;i++) if(cells[i]!=null) occupied.add(i%16,i/256,(i/16)%16);
    }
    public static MicroblockVolume empty(HostMaterial original) {return new MicroblockVolume(new HostMaterial[4096],original);}
    public static MicroblockVolume legacy(MicroblockGrid grid,HostMaterial original) {
        var result=empty(original);
        for(int y=0;y<16;y++) for(int z=0;z<16;z++) for(int x=0;x<16;x++) if(grid.isOccupied(x,y,z)) result.add(x,y,z,original);
        return result;
    }
    public HostMaterial original() {return original;}
    public MicroblockVolume copy() {return new MicroblockVolume(cells,original);}
    public MicroblockVolume withOriginal(HostMaterial value) {return new MicroblockVolume(cells,value);}
    public MicroblockGrid occupancyCopy() {return occupied.copy();}
    public MicroblockGrid oakCopy() {
        var result=MicroblockGrid.fromLongArray(new long[64]);
        for(int i=0;i<4096;i++) if(cells[i]==HostMaterial.OAK_PLANKS) result.add(i%16,i/256,(i/16)%16);
        return result;
    }
    public boolean isOccupied(int x,int y,int z) {return occupied.isOccupied(x,y,z);}
    public HostMaterial materialAt(int x,int y,int z) {return cells[MicroblockGrid.index(x,y,z)];}
    public boolean remove(int x,int y,int z) {
        if(!occupied.remove(x,y,z)) return false; cells[MicroblockGrid.index(x,y,z)]=null;return true;
    }
    public boolean add(int x,int y,int z) {return add(x,y,z,original);}
    public boolean add(int x,int y,int z,HostMaterial material) {
        Objects.requireNonNull(material); if(!occupied.add(x,y,z)) return false;
        cells[MicroblockGrid.index(x,y,z)]=material;return true;
    }
    public boolean replace(int x,int y,int z,HostMaterial material) {
        Objects.requireNonNull(material);int index=MicroblockGrid.index(x,y,z);
        if(cells[index]==null || cells[index]==material) return false;cells[index]=material;return true;
    }
    public int occupiedCount() {return occupied.occupiedCount();}
    public int count(HostMaterial material) {int count=0;for(var cell:cells) if(cell==material) count++;return count;}
    public Map<HostMaterial,Integer> materials() {
        var counts=new LinkedHashMap<HostMaterial,Integer>();for(var cell:cells) if(cell!=null) counts.merge(cell,1,Integer::sum);return Collections.unmodifiableMap(counts);
    }
    public boolean legacyOnly() {return (original==HostMaterial.STONE || original==HostMaterial.OAK_PLANKS)
            && Arrays.stream(cells).allMatch(m -> m==null || m==HostMaterial.STONE || m==HostMaterial.OAK_PLANKS);}
    public boolean isFull() {return occupied.isFull();}
    public boolean isEmpty() {return occupied.isEmpty();}
    @Override public boolean equals(Object other) {return other instanceof MicroblockVolume v && original==v.original && Arrays.equals(cells,v.cells);}
    @Override public int hashCode() {return 31*Arrays.hashCode(cells)+original.hashCode();}
}
