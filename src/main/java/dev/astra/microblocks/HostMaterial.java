package dev.astra.microblocks;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

/** One material per registered host. The block state persists it, not the cell grid. */
public enum HostMaterial {
    STONE("Stone", "block/stone"),
    OAK_PLANKS("Oak planks", "block/oak_planks");

    private final String label;
    private final Identifier texture;

    HostMaterial(String label, String texture) {
        this.label = label;
        this.texture = Identifier.withDefaultNamespace(texture);
    }

    public String label() { return label; }
    public Identifier texture() { return texture; }

    public static HostMaterial of(BlockState state) {
        if (state.is(AstraMicroblocks.OAK_HOST)) return OAK_PLANKS;
        if (state.is(AstraMicroblocks.TEST_HOST)) return STONE;
        throw new IllegalArgumentException("Not an Astra sculptable host: " + state);
    }
}
