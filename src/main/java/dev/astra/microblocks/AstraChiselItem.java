package dev.astra.microblocks;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** Server-authoritative, atomic cuts; per-stack modes travel with the tool. */
public final class AstraChiselItem extends Item {
    public AstraChiselItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            ItemStack stack = player.getItemInHand(hand);
            ChiselMode next = ChiselMode.read(stack).next();
            next.store(stack);
            player.sendOverlayMessage(Component.literal("Astra Chisel: " + next.label()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof TestHostBlockEntity host)) {
            // Consume block clicks so crouching on ordinary terrain cannot cycle the mode.
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        if (context.isSecondaryUseActive()) {
            boolean hadUndo = host.canUndo();
            host.undo();
            if (player != null) player.sendOverlayMessage(Component.literal(
                    hadUndo ? "Astra Chisel: cut undone" : "Astra Chisel: nothing to undo"));
            return InteractionResult.SUCCESS;
        }
        BlockHitResult hit = new BlockHitResult(context.getClickLocation(),
                context.getClickedFace(), context.getClickedPos(), false);
        ChiselMode mode = ChiselMode.read(context.getItemInHand());
        int removed = host.removeCells(mode.selection(
                MicroblockHitResolver.resolveForRemoval(hit), context.getClickedFace()));
        if (removed > 0) ChiselUndo.remember(context.getItemInHand(), level, host);
        if (player != null) player.sendOverlayMessage(Component.literal(
                "Astra Chisel: " + mode.label() + " — " + removed + " cells removed"));
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        lines.accept(Component.literal("Mode: " + ChiselMode.read(stack).label()));
        lines.accept(Component.literal("Right-click host: cut"));
        lines.accept(Component.literal("Crouch + right-click air: next mode"));
        lines.accept(Component.literal("Crouch + right-click host: undo last cut"));
        lines.accept(Component.literal("Empty host? Hold chisel and use /astra undo nearby"));
    }
}
