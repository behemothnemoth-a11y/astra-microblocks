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

    private record CachedMesh(long revision, TextureAtlasSprite stone, TextureAtlasSprite oak, Mesh mesh) {}

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
        TextureAtlasSprite stone = sprites.get(new SpriteId(TextureAtlas.LOCATION_BLOCKS,HostMaterial.STONE.texture()));
        TextureAtlasSprite oak = sprites.get(new SpriteId(TextureAtlas.LOCATION_BLOCKS,HostMaterial.OAK_PLANKS.texture()));
        CachedMesh cached = cache.get(host);
        if (cached == null || cached.revision() != host.revision() || cached.stone() != stone || cached.oak() != oak) {
            var volume = host.volumeCopy();
            cached = new CachedMesh(host.revision(), stone, oak, buildMesh(volume.occupancyCopy(),
                    face -> volume.materialAt(face.x(),face.y(),face.z()) == HostMaterial.OAK_PLANKS ? oak : stone));
            cache.put(host, cached);
        }
        state.mesh = cached.mesh();
    }

    static Mesh buildMesh(MicroblockGrid grid, TextureAtlasSprite sprite) {
        return buildMesh(grid, face -> sprite);
    }

    static Mesh buildMesh(MicroblockGrid grid,
            java.util.function.Function<MicroblockRenderMesh.Face,TextureAtlasSprite> texture) {
        MutableMesh mesh = Renderer.get().mutableMesh();
        QuadEmitter emitter = mesh.emitter();

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
            emitter.materialBake(new Material.Baked(texture.apply(face),false), MutableQuadView.BAKE_LOCK_UV);
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
