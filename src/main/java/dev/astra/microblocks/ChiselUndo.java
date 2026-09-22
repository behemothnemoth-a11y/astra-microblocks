package dev.astra.microblocks;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** A fallback for the last cut when an empty host can no longer be targeted. */
public final class ChiselUndo {
    private ChiselUndo() {}

    public static void remember(ItemStack tool, Level level, TestHostBlockEntity host) {
        CustomData.update(DataComponents.CUSTOM_DATA, tool, tag -> {
            tag.putString("astra_last_dimension", level.dimension().identifier().toString());
            tag.putLong("astra_last_pos", host.getBlockPos().asLong());
            tag.putLong("astra_last_revision", host.revision());
        });
    }

    public static boolean undoLast(Player player, ItemStack tool) {
        if (!tool.is(AstraMicroblocks.ASTRA_CHISEL) || player.level().isClientSide()) return false;
        var tag = tool.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.getStringOr("astra_last_dimension", "").equals(player.level().dimension().identifier().toString())) return false;
        BlockPos pos = BlockPos.of(tag.getLongOr("astra_last_pos", 0));
        if (!player.isWithinBlockInteractionRange(pos, 0) || !player.level().hasChunkAt(pos)) return false;
        if (!(player.level().getBlockEntity(pos) instanceof TestHostBlockEntity host)
                || !host.canUndo() || host.revision() != tag.getLongOr("astra_last_revision", -1)) return false;
        host.undo();
        return true;
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) ->
                dispatcher.register(Commands.literal("astra").then(Commands.literal("undo").executes(context -> {
                    Player player = context.getSource().getPlayerOrException();
                    boolean changed = undoLast(player, player.getMainHandItem());
                    if (changed) context.getSource().sendSuccess(() -> Component.literal("Astra Chisel: cut undone"), false);
                    else context.getSource().sendFailure(Component.literal(
                            "Hold the chisel and stand within reach of its last cut. That cut must still be the block's latest edit."));
                    return changed ? 1 : 0;
                }))));
    }
}
