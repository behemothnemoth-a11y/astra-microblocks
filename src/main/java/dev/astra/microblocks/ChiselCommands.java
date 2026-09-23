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

    /** Raycast on the server: sampling cannot reach through other blocks or trust client coordinates. */
    public static boolean sampleMaterial(Player player) {
        if (player.level().isClientSide() || !player.getMainHandItem().is(AstraMicroblocks.ASTRA_CHISEL)) return false;
        var eye=player.getEyePosition();
        var direction=net.minecraft.world.phys.Vec3.directionFromRotation(player.getXRot(),player.getYRot());
        var hit=player.level().clip(new net.minecraft.world.level.ClipContext(eye,
                eye.add(direction.scale(player.blockInteractionRange())),
                net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE,player));
        var blockHit=hit;
        if (hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK
                || !player.level().hasChunkAt(blockHit.getBlockPos())
                || !(player.level().getBlockEntity(blockHit.getBlockPos()) instanceof TestHostBlockEntity host)) return false;
        var cell=MicroblockHitResolver.resolveForRemoval(blockHit);
        var material=host.materialAt(cell.x(),cell.y(),cell.z());
        if (material==null) return false;
        (material==HostMaterial.STONE?ChiselMaterial.STONE:ChiselMaterial.OAK).store(player.getMainHandItem());
        player.sendOverlayMessage(Component.literal("Astra material sampled: "+material.label()));
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
            dispatcher.register(Commands.literal("astra").then(modes).then(operations).then(materials)
                    .then(Commands.literal("sample").executes(context -> {
                        if (sampleMaterial(context.getSource().getPlayerOrException())) return 1;
                        context.getSource().sendFailure(Component.literal("Hold the chisel and aim at a stone or oak cell within reach."));
                        return 0;
                    })));
        });
    }
}
