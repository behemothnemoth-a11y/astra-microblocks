package dev.astra.microblocks.client;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/** Only immutable geometry crosses from extraction to submission. */
public final class TestHostRenderState extends BlockEntityRenderState {
    Mesh mesh;
}
