package dev.astra.microblocks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.astra.microblocks.MicroblockGrid;
import dev.astra.microblocks.HostMaterial;
import dev.astra.microblocks.MicroblockRenderMesh;
import dev.astra.microblocks.TestHostBlockEntity;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.render.ChunkSectionLayerHelper;
import net.fabricmc.fabric.api.client.renderer.v1.render.FabricOrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Shared material-aware surface renderer; the common block entity owns occupancy. */
public final class TestHostBlockEntityRenderer
        implements BlockEntityRenderer<TestHostBlockEntity, TestHostRenderState> {
    private static final int[] NO_TINTS = new int[0];

    private final SpriteGetter sprites;
    // Values deliberately do not reference the host, so chunk unloading releases entries.
    private final Map<TestHostBlockEntity, CachedMesh> cache = new WeakHashMap<>();

    private record CachedMesh(long revision, TextureAtlasSprite sprite, Mesh mesh) {}

    public TestHostBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        sprites = context.sprites();
    }

    @Override
    public TestHostRenderState createRenderState() {
        return new TestHostRenderState();
    }

    @Override
    public void extractRenderState(TestHostBlockEntity host, TestHostRenderState state,
                                   float tickProgress, Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(host, state, tickProgress, cameraPos, crumblingOverlay);
        TextureAtlasSprite sprite = sprites.get(new SpriteId(TextureAtlas.LOCATION_BLOCKS,
                HostMaterial.of(host.getBlockState()).texture()));
        CachedMesh cached = cache.get(host);
        // Sprite identity also invalidates UVs when a resource pack/atlas is reloaded.
        if (cached == null || cached.revision() != host.revision() || cached.sprite() != sprite) {
            cached = new CachedMesh(host.revision(), sprite, buildMesh(host.gridCopy(), sprite));
            cache.put(host, cached);
        }
        state.mesh = cached.mesh();
    }

    static Mesh buildMesh(MicroblockGrid grid, TextureAtlasSprite sprite) {
        MutableMesh mesh = Renderer.get().mutableMesh();
        QuadEmitter emitter = mesh.emitter();
        Material.Baked material = new Material.Baked(sprite, false);
        for (MicroblockRenderMesh.Face face : MicroblockRenderMesh.build(grid)) {
            // Topology has already culled occupied neighbors. Never let a full-block
            // cull test remove interior cavity walls or faces beside another carved host.
            emitter.cullFace(null);
            // cullFace also resets nominalFace; set the UV projection direction afterward.
            emitter.nominalFace(face.direction());
            for (int corner = 0; corner < 4; corner++) {
                MicroblockRenderMesh.Vertex vertex = face.vertex(corner);
                emitter.pos(corner, vertex.x() / (float) MicroblockGrid.SIZE,
                        vertex.y() / (float) MicroblockGrid.SIZE, vertex.z() / (float) MicroblockGrid.SIZE);
            }
            // Block-space UVs keep the material continuous across cells instead of repeating
            // a whole 16x16 texture on every individual microcell.
            emitter.materialBake(material, MutableQuadView.BAKE_LOCK_UV);
            emitter.color(-1, -1, -1, -1);
            emitter.emit();
        }
        return mesh.immutableCopy();
    }

    @Override
    public void submit(TestHostRenderState state, PoseStack poses,
                       SubmitNodeCollector queue, CameraRenderState cameraState) {
        if (state.mesh == null) return;
        FabricOrderedSubmitNodeCollector collector = (FabricOrderedSubmitNodeCollector) queue;
        collector.submitBlockModel(poses, ChunkSectionLayerHelper::getMovingBlockRenderType,
                false, List.of(), state.mesh, NO_TINTS, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        if (state.breakProgress != null) {
            collector.submitBreakingBlockModel(poses, List.of(), state.mesh, state.breakProgress.progress());
        }
    }
}
