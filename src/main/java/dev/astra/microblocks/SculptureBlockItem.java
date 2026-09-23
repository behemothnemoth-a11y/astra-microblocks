package dev.astra.microblocks;

import java.util.function.Consumer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;

/** Ordinary host stacks still work; portable sculptures restore their exact cells on placement. */
public final class SculptureBlockItem extends BlockItem {
    public SculptureBlockItem(Block block,Properties properties) { super(block,properties); }
    @Override public net.minecraft.world.InteractionResult use(net.minecraft.world.level.Level level,
            net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand) {
        var stack=player.getItemInHand(hand);
        var design=SculptureData.read(stack,SculptureData.ITEM_KEY);
        if(!player.isShiftKeyDown() || design.isEmpty()) return net.minecraft.world.InteractionResult.PASS;
        if(!level.isClientSide()) {
            SculptureData.write(stack,SculptureData.ITEM_KEY,SculptureData.transform(design.get(),net.minecraft.core.Direction.Axis.Y,false));
            player.sendOverlayMessage(Component.literal("Sculpture rotated 90 degrees"));
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }
    @Override protected boolean canPlace(BlockPlaceContext context,BlockState state) {
        var design=SculptureData.read(context.getItemInHand(),SculptureData.ITEM_KEY);
        if(SculptureData.has(context.getItemInHand(),SculptureData.ITEM_KEY) && (design.isEmpty() || design.get().isEmpty())) return false;
        return design.isEmpty()?super.canPlace(context,state):fits(context,state,design.get());
    }
    public static boolean fits(BlockPlaceContext context,BlockState state,MicroblockVolume volume) {
        if(volume.isEmpty() || !state.canSurvive(context.getLevel(),context.getClickedPos())) return false;
        var pos=context.getClickedPos();
        var shape=MicroblockShape.build(volume.occupancyCopy()).move(pos.getX(),pos.getY(),pos.getZ());
        for(var entity:context.getLevel().getEntities((net.minecraft.world.entity.Entity)null,shape.bounds(),
                e -> e.isAlive() && !e.isSpectator() && (e instanceof net.minecraft.world.entity.LivingEntity || e.blocksBuilding))) {
            if(net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(shape,
                    net.minecraft.world.phys.shapes.Shapes.create(entity.getBoundingBox()),net.minecraft.world.phys.shapes.BooleanOp.AND)) return false;
        }
        return true;
    }
    @Override protected boolean placeBlock(BlockPlaceContext context,BlockState state) {
        if(!super.placeBlock(context,state)) return false;
        var design=SculptureData.read(context.getItemInHand(),SculptureData.ITEM_KEY);
        if(design.isPresent() && context.getLevel().getBlockEntity(context.getClickedPos()) instanceof TestHostBlockEntity host)
            host.initializeDesign(design.get());
        return true;
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,Consumer<Component> lines,TooltipFlag flag) {
        super.appendHoverText(stack,context,display,lines,flag);
        SculptureData.read(stack,SculptureData.ITEM_KEY).ifPresent(volume -> {
            lines.accept(Component.literal("Saved sculpture - exact shape and materials"));
            lines.accept(Component.literal("Crouch + right-click air: rotate 90 degrees"));
            lines.accept(Component.literal(SculptureData.summary(volume)));
        });
    }
}
