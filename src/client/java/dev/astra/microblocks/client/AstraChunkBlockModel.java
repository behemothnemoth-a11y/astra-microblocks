package dev.astra.microblocks.client;

import dev.astra.microblocks.AstraMicroblocks;
import dev.astra.microblocks.HostMaterial;
import dev.astra.microblocks.MicroblockRenderMesh;
import dev.astra.microblocks.TestHostBlockEntity;
import net.fabricmc.fabric.api.blockgetter.v2.FabricBlockGetter;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Chunk-baked world renderer for Astra hosts.
 *
 * The block entity publishes an immutable render snapshot. Fabric copies that
 * snapshot into the chunk build view, so the expensive geometry submission is
 * paid during chunk compilation instead of once per host, per frame.
 */
public final class AstraChunkBlockModel extends WrapperBlockStateModel {
    private static final ModelDebugName DEBUG_NAME = () -> "astra_microblocks:chunk_geometry";
    private static final Map<ModelBaker, Map<Material, Material.Baked>> MATERIAL_CACHE = new WeakHashMap<>();

    private final Map<Material, Material.Baked> bakedMaterials;

    private AstraChunkBlockModel(BlockStateModel wrapped, ModelBaker baker) {
        super(wrapped);
        this.bakedMaterials = materialsFor(baker);
    }

    public static void register() {
        ModelLoadingPlugin.register(context ->
                context.modifyBlockModelAfterBake().register((model, bakeContext) -> {
                    BlockState state = bakeContext.state();
                    if (state.is(AstraMicroblocks.TEST_HOST) || state.is(AstraMicroblocks.OAK_HOST)) {
                        return new AstraChunkBlockModel(model, bakeContext.baker());
                    }
                    return model;
                }));
    }

    private static synchronized Map<Material, Material.Baked> materialsFor(ModelBaker baker) {
        return MATERIAL_CACHE.computeIfAbsent(baker, AstraChunkBlockModel::bakeMaterials);
    }

    private static Map<Material, Material.Baked> bakeMaterials(ModelBaker baker) {
        Map<Material, Material.Baked> result = new HashMap<>();
        var materialBaker = baker.materials();

        for (HostMaterial hostMaterial : HostMaterial.catalog()) {
            for (Direction direction : Direction.values()) {
                Material material = new Material(
                        hostMaterial.texture(direction),
                        hostMaterial.forceTranslucent(direction));
                result.computeIfAbsent(material, key -> materialBaker.get(key, DEBUG_NAME));
            }
        }

        HostMaterial rgb = HostMaterial.color(0xffffff);
        Material flat = new Material(rgb.texture(), false);
        result.computeIfAbsent(flat, key -> materialBaker.get(key, DEBUG_NAME));

        return Map.copyOf(result);
    }

    private Material.Baked baked(HostMaterial material, Direction direction) {
        Material key = new Material(material.texture(direction), material.forceTranslucent(direction));
        Material.Baked baked = bakedMaterials.get(key);
        if (baked == null) {
            throw new IllegalStateException("Missing baked Astra material: " + key);
        }
        return baked;
    }

    @Override
    public void emitQuads(
            QuadEmitter emitter,
            BlockAndTintGetter blockView,
            BlockPos pos,
            BlockState state,
            RandomSource random,
            Predicate<Direction> cullingPredicate
    ) {
        if (!(blockView instanceof FabricBlockGetter fabricView)) {
            return;
        }

        Object raw = fabricView.getBlockEntityRenderData(pos);
        if (!(raw instanceof TestHostBlockEntity.RenderData renderData)) {
            return;
        }

        var volume = renderData.volume();

        for (var face : MicroblockRenderMesh.buildGreedy(volume)) {
            HostMaterial material = volume.materialAt(face.x(), face.y(), face.z());

            // Astra micro-surfaces can expose cavity walls and partial boundary faces.
            // Leave cullFace null so Minecraft does not remove them as full-cube faces.
            emitter.cullFace(null);
            emitter.nominalFace(face.direction());

            for (int corner = 0; corner < 4; corner++) {
                var vertex = face.vertex(corner);
                emitter.pos(
                        corner,
                        vertex.x() / 16.0f,
                        vertex.y() / 16.0f,
                        vertex.z() / 16.0f);

                float[] uv = material.uv(face.direction(), vertex);
                emitter.uv(corner, uv[0], uv[1]);
            }

            emitter.materialBake(
                    baked(material, face.direction()),
                    MutableQuadView.BAKE_NORMALIZED);

            int color = material.argb();
            emitter.color(color, color, color, color);
            emitter.emit();
        }
    }
}
