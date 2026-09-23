package dev.astra.microblocks.client;

import dev.astra.microblocks.AstraMicroblocks;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public final class AstraMicroblocksClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRenderers.register(AstraMicroblocks.TEST_HOST_ENTITY, TestHostBlockEntityRenderer::new);
        SculptureItemRenderer.register();
        SculpturePlacementPreview.register();
        ChiselInspector.register();
        if (Boolean.getBoolean("astra.renderTest")) ClientRenderTest.register();
    }
}
