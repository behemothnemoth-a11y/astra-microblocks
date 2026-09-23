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
            boolean changed = host.undoEdit();
            if (changed) ChiselUndo.remember(context.getItemInHand(), level, host);
            if (player != null) player.sendOverlayMessage(Component.literal(
                    changed ? "Astra Chisel: edit undone" : hadUndo ? "Undo blocked: move away from the cells being restored" : "Astra Chisel: nothing to undo"));
            return InteractionResult.SUCCESS;
        }
        BlockHitResult hit = new BlockHitResult(context.getClickLocation(),
                context.getClickedFace(), context.getClickedPos(), false);
        ChiselMode mode = ChiselMode.read(context.getItemInHand());
        ChiselOperation operation = ChiselOperation.read(context.getItemInHand());
        var target = operation.target(hit);
        if (target.isEmpty()) {
            if (player != null) player.sendOverlayMessage(Component.literal("Add stays inside this block: aim at an inner face of a cavity"));
            return InteractionResult.SUCCESS;
        }
        int changed = host.editCells(mode.selection(target.get(), context.getClickedFace()), operation,
                ChiselMaterial.read(context.getItemInHand()).resolve(host.getBlockState()));
        if (changed > 0) ChiselUndo.remember(context.getItemInHand(), level, host);
        if (player != null) player.sendOverlayMessage(Component.literal(changed < 0
                ? "Placement blocked: an entity occupies the cells"
                : "Astra Chisel: " + operation.label() + " " + mode.label() + " — " + changed + " cells changed"));
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        lines.accept(Component.literal("Fill material: " + ChiselMaterial.read(stack).label()));
        lines.accept(Component.literal("Mode: " + ChiselMode.read(stack).label()));
        lines.accept(Component.literal("Right-click host: " + ChiselOperation.read(stack).label()));
        lines.accept(Component.literal("Crouch + right-click air: next mode"));
        lines.accept(Component.literal("Crouch + right-click host: undo last edit"));
        lines.accept(Component.literal("Menu: Cut/Add, Undo/Redo. Commands: /astra undo or /astra redo"));
    }
}
