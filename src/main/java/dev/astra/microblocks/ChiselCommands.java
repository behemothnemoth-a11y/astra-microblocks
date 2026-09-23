package dev.astra.microblocks;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** The menu requests a mode; only the server changes the held tool. */
public final class ChiselCommands {
    private ChiselCommands() {}

    public static boolean selectMode(Player player, ChiselMode mode) {
        if (player.level().isClientSide() || !player.getMainHandItem().is(AstraMicroblocks.ASTRA_CHISEL)) return false;
        mode.store(player.getMainHandItem());
        player.sendOverlayMessage(Component.literal("Astra Chisel: " + mode.label()));
        return true;
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> {
            var modes = Commands.literal("mode");
            for (ChiselMode mode : ChiselMode.values()) {
                modes.then(Commands.literal(mode.id()).executes(context -> {
                    if (selectMode(context.getSource().getPlayerOrException(), mode)) return 1;
                    context.getSource().sendFailure(Component.literal("Hold the Astra Chisel in your main hand."));
                    return 0;
                }));
            }
            var operations = Commands.literal("operation");
            for (ChiselOperation operation : ChiselOperation.values()) {
                operations.then(Commands.literal(operation.id()).executes(context -> {
                    Player player = context.getSource().getPlayerOrException();
                    if (!player.getMainHandItem().is(AstraMicroblocks.ASTRA_CHISEL)) return 0;
                    operation.store(player.getMainHandItem());
                    player.sendOverlayMessage(Component.literal("Astra Chisel: " + operation.label()));
                    return 1;
                }));
            }
            var materials = Commands.literal("material");
            for (var material : ChiselMaterial.values()) materials.then(Commands.literal(material.id()).executes(context -> {
                Player player = context.getSource().getPlayerOrException();
                if (!player.getMainHandItem().is(AstraMicroblocks.ASTRA_CHISEL)) return 0;
                material.store(player.getMainHandItem());
                player.sendOverlayMessage(Component.literal("Astra fill: " + material.label()));
                return 1;
            }));
            dispatcher.register(Commands.literal("astra").then(modes).then(operations).then(materials));
        });
    }
}
