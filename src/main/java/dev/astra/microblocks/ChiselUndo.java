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

/** History controls for the last edited host, including hosts that are now empty. */
public final class ChiselUndo {
    private ChiselUndo() {}

    public static void remember(ItemStack tool, Level level, TestHostBlockEntity host) {
        CustomData.update(DataComponents.CUSTOM_DATA, tool, tag -> {
            tag.putString("astra_last_dimension", level.dimension().identifier().toString());
            tag.putLong("astra_last_pos", host.getBlockPos().asLong());
            tag.putLong("astra_last_revision", host.revision());
        });
    }

    public static boolean undoLast(Player player, ItemStack tool) { return changeHistory(player,tool,false); }
    public static boolean redoLast(Player player, ItemStack tool) { return changeHistory(player,tool,true); }

    private static boolean changeHistory(Player player, ItemStack tool, boolean redo) {
        if (!tool.is(AstraMicroblocks.ASTRA_CHISEL) || player.level().isClientSide()) return false;
        var tag = tool.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.getStringOr("astra_last_dimension", "").equals(player.level().dimension().identifier().toString())) return false;
        BlockPos pos = BlockPos.of(tag.getLongOr("astra_last_pos", 0));
        if (!player.isWithinBlockInteractionRange(pos, 0) || !player.level().hasChunkAt(pos)) return false;
        if (!(player.level().getBlockEntity(pos) instanceof TestHostBlockEntity host)
                || host.revision() != tag.getLongOr("astra_last_revision", -1)) return false;
        boolean changed = redo ? host.redoEdit() : host.undoEdit();
        if (changed) remember(tool, player.level(), host);
        return changed;
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> {
            var root = Commands.literal("astra");
            for (boolean redo : new boolean[] {false,true}) {
                root.then(Commands.literal(redo ? "redo" : "undo").executes(context -> {
                    Player player = context.getSource().getPlayerOrException();
                    boolean changed = changeHistory(player,player.getMainHandItem(),redo);
                    if (changed) context.getSource().sendSuccess(() -> Component.literal(
                            redo ? "Astra Chisel: edit redone" : "Astra Chisel: edit undone"),false);
                    else context.getSource().sendFailure(Component.literal(
                            "No available history, obstructed cells, or changed target. Hold the same chisel within reach of its last edit."));
                    return changed ? 1 : 0;
                }));
            }
            dispatcher.register(root);
        });
    }
}
