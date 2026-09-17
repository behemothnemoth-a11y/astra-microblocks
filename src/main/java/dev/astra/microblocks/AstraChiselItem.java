package dev.astra.microblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;

/**
 * First player-facing Astra microblock tool.
 *
 * v0.1 behavior:
 *
 * right-click sculptable host
 * -> resolve exact 1/16 cell
 * -> remove that cell
 * -> block entity publishes the geometry update
 */
public final class AstraChiselItem extends Item {

    public AstraChiselItem(
            Properties properties
    ) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(
            UseOnContext context
    ) {
        Level level =
                context.getLevel();

        BlockPos pos =
                context.getClickedPos();

        BlockEntity blockEntity =
                level.getBlockEntity(pos);

        /*
         * This first version only operates on our existing
         * sculptable host.
         */
        if (!(blockEntity
                instanceof TestHostBlockEntity host)) {

            return InteractionResult.PASS;
        }

        /*
         * The server owns authoritative geometry edits.
         *
         * Client-side interaction succeeds immediately so
         * Minecraft doesn't try another use behavior while
         * waiting for the server update.
         */
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockHitResult hit =
                new BlockHitResult(
                        context.getClickLocation(),
                        context.getClickedFace(),
                        pos,
                        false
                );

        MicroblockHitResolver.Cell cell =
                MicroblockHitResolver
                        .resolveForRemoval(hit);

        boolean changed =
                host.removeCell(
                        cell.x(),
                        cell.y(),
                        cell.z()
                );

        if (!changed) {
            /*
             * This can happen if the target cell became empty
             * between client interaction and server handling.
             */
            return InteractionResult.PASS;
        }

        System.out.println(
                "ASTRA_CHISEL: removed "
                + cell.x()
                + ","
                + cell.y()
                + ","
                + cell.z()
                + " at "
                + pos.getX()
                + ","
                + pos.getY()
                + ","
                + pos.getZ()
        );

        return InteractionResult.SUCCESS;
    }
}
