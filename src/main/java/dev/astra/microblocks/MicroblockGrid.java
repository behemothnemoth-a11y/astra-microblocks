package dev.astra.microblocks;

import java.util.Arrays;

/**
 * Occupancy storage for one sculptable Minecraft block.
 *
 * Resolution:
 * 16 x 16 x 16 = 4096 independently addressable cells.
 *
 * Storage:
 * 4096 bits = 64 longs = 512 raw bytes.
 *
 * A set bit means the microcell contains material.
 * A clear bit means the microcell is empty.
 */
public final class MicroblockGrid {

    public static final int SIZE = 16;
    public static final int CELL_COUNT =
            SIZE * SIZE * SIZE;

    private static final int BITS_PER_WORD =
            Long.SIZE;

    private static final int WORD_COUNT =
            CELL_COUNT / BITS_PER_WORD;

    private final long[] words =
            new long[WORD_COUNT];

    /**
     * Creates a completely solid block.
     */
    public MicroblockGrid() {
        fill();
    }

    private MicroblockGrid(long[] words) {
        if (words.length != WORD_COUNT) {
            throw new IllegalArgumentException(
                    "Expected "
                    + WORD_COUNT
                    + " words, got "
                    + words.length
            );
        }

        System.arraycopy(
                words,
                0,
                this.words,
                0,
                WORD_COUNT
        );
    }

    /**
     * Returns whether this microcell contains material.
     */
    public boolean isOccupied(
            int x,
            int y,
            int z
    ) {
        int index = index(x, y, z);
        int word = index >>> 6;
        int bit = index & 63;

        return (words[word] & (1L << bit)) != 0;
    }

    /**
     * Changes one microcell.
     *
     * @return true only if the grid actually changed.
     */
    public boolean setOccupied(
            int x,
            int y,
            int z,
            boolean occupied
    ) {
        int index = index(x, y, z);
        int word = index >>> 6;
        int bit = index & 63;

        long mask = 1L << bit;
        boolean old =
                (words[word] & mask) != 0;

        if (old == occupied) {
            return false;
        }

        if (occupied) {
            words[word] |= mask;
        } else {
            words[word] &= ~mask;
        }

        return true;
    }

    /**
     * Removes exactly one 1/16-scale cell.
     */
    public boolean remove(
            int x,
            int y,
            int z
    ) {
        return setOccupied(
                x,
                y,
                z,
                false
        );
    }

    /**
     * Adds exactly one 1/16-scale cell.
     */
    public boolean add(
            int x,
            int y,
            int z
    ) {
        return setOccupied(
                x,
                y,
                z,
                true
        );
    }

    /**
     * Makes all 4096 cells solid.
     */
    public void fill() {
        Arrays.fill(words, -1L);
    }

    /**
     * Makes all 4096 cells empty.
     */
    public void clear() {
        Arrays.fill(words, 0L);
    }

    public boolean isFull() {
        for (long word : words) {
            if (word != -1L) {
                return false;
            }
        }

        return true;
    }

    public boolean isEmpty() {
        for (long word : words) {
            if (word != 0L) {
                return false;
            }
        }

        return true;
    }

    /**
     * Counts occupied cells.
     *
     * Full block = 4096.
     * Empty block = 0.
     */
    public int occupiedCount() {
        int count = 0;

        for (long word : words) {
            count += Long.bitCount(word);
        }

        return count;
    }

    /**
     * Returns an independent copy.
     */
    public MicroblockGrid copy() {
        return new MicroblockGrid(words);
    }

    /**
     * Serialization boundary.
     *
     * Returns a defensive copy so callers cannot mutate
     * the grid without going through its API.
     */
    public long[] toLongArray() {
        return Arrays.copyOf(
                words,
                WORD_COUNT
        );
    }

    /**
     * Restores a grid from serialized occupancy data.
     */
    public static MicroblockGrid fromLongArray(
            long[] data
    ) {
        if (data == null) {
            throw new IllegalArgumentException(
                    "Microblock data cannot be null"
            );
        }

        return new MicroblockGrid(data);
    }

    /**
     * Converts local XYZ coordinates to one stable
     * bit index in the range 0..4095.
     *
     * Layout:
     * x changes fastest,
     * then z,
     * then y.
     */
    public static int index(
            int x,
            int y,
            int z
    ) {
        checkCoordinate("x", x);
        checkCoordinate("y", y);
        checkCoordinate("z", z);

        return x
                | (z << 4)
                | (y << 8);
    }

    private static void checkCoordinate(
            String axis,
            int value
    ) {
        if (value < 0 || value >= SIZE) {
            throw new IndexOutOfBoundsException(
                    axis
                    + " must be between 0 and 15, got "
                    + value
            );
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof MicroblockGrid other)) {
            return false;
        }

        return Arrays.equals(
                words,
                other.words
        );
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(words);
    }

    @Override
    public String toString() {
        return "MicroblockGrid{occupied="
                + occupiedCount()
                + "/"
                + CELL_COUNT
                + "}";
    }
}
