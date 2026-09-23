package dev.astra.microblocks;

/** Two-material cell data. Geometry remains the existing occupancy grid. */
public final class MicroblockVolume {
    private final MicroblockGrid occupied;
    private final MicroblockGrid oak;
    private final HostMaterial original;

    public MicroblockVolume(HostMaterial original) {
        this(new MicroblockGrid(), original == HostMaterial.OAK_PLANKS ? new MicroblockGrid()
                : MicroblockGrid.fromLongArray(new long[64]), original);
    }
    public MicroblockVolume(MicroblockGrid occupied, MicroblockGrid oak, HostMaterial original) {
        this.occupied = occupied.copy();
        long[] mask = oak.toLongArray(), cells = occupied.toLongArray();
        for (int i=0;i<mask.length;i++) mask[i] &= cells[i];
        this.oak = MicroblockGrid.fromLongArray(mask);
        this.original = original;
    }
    public static MicroblockVolume legacy(MicroblockGrid grid, HostMaterial original) {
        return new MicroblockVolume(grid, original == HostMaterial.OAK_PLANKS ? grid
                : MicroblockGrid.fromLongArray(new long[64]), original);
    }
    public HostMaterial original() { return original; }
    public MicroblockVolume copy() { return new MicroblockVolume(occupied, oak, original); }
    public MicroblockGrid occupancyCopy() { return occupied.copy(); }
    public MicroblockGrid oakCopy() { return oak.copy(); }
    public boolean isOccupied(int x,int y,int z) { return occupied.isOccupied(x,y,z); }
    public HostMaterial materialAt(int x,int y,int z) {
        return !isOccupied(x,y,z) ? null : oak.isOccupied(x,y,z) ? HostMaterial.OAK_PLANKS : HostMaterial.STONE;
    }
    public boolean remove(int x,int y,int z) {
        if (!occupied.remove(x,y,z)) return false;
        oak.remove(x,y,z);
        return true;
    }
    public boolean add(int x,int y,int z) { return add(x,y,z,original); }
    public boolean add(int x,int y,int z,HostMaterial material) {
        java.util.Objects.requireNonNull(material);
        if (!occupied.add(x,y,z)) return false;
        oak.setOccupied(x,y,z,material == HostMaterial.OAK_PLANKS);
        return true;
    }
    public boolean replace(int x,int y,int z,HostMaterial material) {
        java.util.Objects.requireNonNull(material);
        if (!isOccupied(x,y,z) || materialAt(x,y,z) == material) return false;
        oak.setOccupied(x,y,z,material == HostMaterial.OAK_PLANKS);
        return true;
    }
    public int occupiedCount() { return occupied.occupiedCount(); }
    public int count(HostMaterial material) {
        return material == HostMaterial.OAK_PLANKS ? oak.occupiedCount() : occupiedCount()-oak.occupiedCount();
    }
    public boolean isFull() { return occupied.isFull(); }
    public boolean isEmpty() { return occupied.isEmpty(); }
    @Override public boolean equals(Object other) {
        return other instanceof MicroblockVolume volume && occupied.equals(volume.occupied) && oak.equals(volume.oak)
                && original == volume.original;
    }
    @Override public int hashCode() { return java.util.Objects.hash(occupied,oak,original); }
}
