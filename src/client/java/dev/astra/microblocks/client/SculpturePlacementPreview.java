package dev.astra.microblocks.client;

import dev.astra.microblocks.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.gizmos.*;

/** Shape-aware placement ghost for saved sculpture items. Never alters the world. */
public final class SculpturePlacementPreview {
    private static MicroblockVolume cached;
    private static java.util.List<MicroblockMesher.Cuboid> boxes=java.util.List.of();
    private SculpturePlacementPreview() {}
    public static void register() {
        LevelExtractionEvents.END_EXTRACTION.register(event -> {
            var client=Minecraft.getInstance();
            if(client.level==null || client.player==null || client.gui.screen()!=null || client.gui.hud.isHidden()
                    || !(client.player.getMainHandItem().getItem() instanceof SculptureBlockItem item)
                    || !(client.hitResult instanceof BlockHitResult hit) || hit.getType()!=HitResult.Type.BLOCK) {cached=null;return;}
            var volume=SculptureData.read(client.player.getMainHandItem(),SculptureData.ITEM_KEY).orElse(null);
            if(volume==null || volume.isEmpty()) return;
            if(!volume.equals(cached)) { cached=volume;boxes=MicroblockMesher.mesh(volume.occupancyCopy()); }
            var context=new BlockPlaceContext(new UseOnContext(client.player,InteractionHand.MAIN_HAND,hit));
            var pos=context.getClickedPos();
            boolean valid=context.canPlace() && SculptureBlockItem.fits(context,item.getBlock().defaultBlockState(),volume);
            var style=GizmoStyle.strokeAndFill(valid?0xFF6CE6FA:0xFFFF6868,1.5f,valid?0x206CE6FA:0x20FF6868);
            var collector=new SimpleGizmoCollector();
            try(var ignored=Gizmos.withCollector(collector)) {
                for(var box:boxes) Gizmos.cuboid(new AABB(box.minX()/16.0,box.minY()/16.0,box.minZ()/16.0,
                        box.maxX()/16.0,box.maxY()/16.0,box.maxZ()/16.0).move(pos).inflate(0.0003),style).setAlwaysOnTop();
            }
            event.levelRenderer().addMainThreadGizmos(collector.drainGizmos());
        });
    }
}
