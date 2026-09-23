package dev.astra.microblocks.client;

import dev.astra.microblocks.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.client.renderer.v1.render.FabricOrderedSubmitNodeCollector;
import net.fabricmc.fabric.api.client.renderer.v1.render.ChunkSectionLayerHelper;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.item.SpecialModelWrapper;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Inventory/hand/dropped-item geometry uses the same exposed-face mesher as world sculptures. */
public final class SculptureItemRenderer implements SpecialModelRenderer<Mesh> {
    private final SpriteGetter sprites;
    private final java.util.Map<MicroblockVolume,Mesh> cache=new java.util.LinkedHashMap<>(32,0.75f,true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<MicroblockVolume,Mesh> entry) { return size()>64; }
    };
    SculptureItemRenderer(SpriteGetter sprites) { this.sprites=sprites; }
    public static void register() {
        ModelLoadingPlugin.register(context -> context.modifyItemModelAfterBake().register((original,ctx) -> {
            if(!ctx.itemId().equals(AstraMicroblocks.id("test_host")) && !ctx.itemId().equals(AstraMicroblocks.id("oak_host"))) return original;
            var base=AstraMicroblocks.id(ctx.itemId().getPath().equals("oak_host")?"block/oak_host":"block/test_host");
            var sculpted=new SpecialModelWrapper.Unbaked(base,Optional.empty(),new Unbaked())
                    .bake(ctx.bakingContext(),ctx.transformation());
            return (state,stack,resolver,display,level,owner,seed) -> {
                var model=SculptureData.read(stack,SculptureData.ITEM_KEY).isPresent()?sculpted:original;
                model.update(state,stack,resolver,display,level,owner,seed);
            };
        }));
    }
    public record Unbaked() implements SpecialModelRenderer.Unbaked<Mesh> {
        public static final MapCodec<Unbaked> CODEC=MapCodec.unit(new Unbaked());
        @Override public SpecialModelRenderer<Mesh> bake(BakingContext context) { return new SculptureItemRenderer(context.sprites()); }
        @Override public MapCodec<Unbaked> type() { return CODEC; }
    }
    @Override public Mesh extractArgument(ItemStack stack) {
        var volume=SculptureData.read(stack,SculptureData.ITEM_KEY).orElse(null);
        if(volume==null) return null;
        return cache.computeIfAbsent(volume,key -> TestHostBlockEntityRenderer.buildMesh(key.occupancyCopy(),face ->
                sprites.get(new SpriteId(TextureAtlas.LOCATION_BLOCKS,key.materialAt(face.x(),face.y(),face.z()).texture()))));
    }
    @Override public void getExtents(Consumer<Vector3fc> output) {
        for(int x=0;x<=1;x++) for(int y=0;y<=1;y++) for(int z=0;z<=1;z++) output.accept(new Vector3f(x,y,z));
    }
    @Override public void submit(Mesh mesh,PoseStack poses,SubmitNodeCollector queue,int light,int overlay,boolean foil,int outline) {
        if(mesh==null) return;
        ((FabricOrderedSubmitNodeCollector)queue).submitBlockModel(poses,ChunkSectionLayerHelper::getMovingBlockRenderType,
                false,List.of(),mesh,new int[0],light,overlay,outline);
    }
}
