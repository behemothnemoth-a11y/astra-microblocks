package dev.astra.microblocks;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

/** Reusable per-chisel clipboard, with server-authoritative capture, transforms and stamping. */
public final class DesignCommands {
    private DesignCommands() {}
    public static TestHostBlockEntity aimedHost(Player player) {
        var eye=player.getEyePosition();
        var end=eye.add(Vec3.directionFromRotation(player.getXRot(),player.getYRot()).scale(player.blockInteractionRange()));
        var hit=player.level().clip(new ClipContext(eye,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        return hit.getType()==HitResult.Type.BLOCK && player.level().hasChunkAt(hit.getBlockPos())
                && player.level().getBlockEntity(hit.getBlockPos()) instanceof TestHostBlockEntity host?host:null;
    }
    public static boolean execute(Player player,String action) {
        var tool=player.getMainHandItem();
        if(player.level().isClientSide() || !tool.is(AstraMicroblocks.ASTRA_CHISEL)) return false;
        var saved=SculptureData.read(tool,SculptureData.CLIPBOARD_KEY);
        if(action.equals("copy")) {
            var host=aimedHost(player); if(host==null || host.isEmpty()) return false;
            SculptureData.write(tool,SculptureData.CLIPBOARD_KEY,host.volumeCopy());
            player.sendOverlayMessage(Component.literal("Design copied: "+SculptureData.summary(host.volumeCopy()))); return true;
        }
        if(action.equals("stamp")) {
            var host=aimedHost(player); if(host==null || saved.isEmpty()) return false;
            int changed=host.applyDesign(saved.get());
            if(changed<0) return false;
            if(changed>0) ChiselUndo.remember(tool,player.level(),host);
            player.sendOverlayMessage(Component.literal(changed==0?"Design already matches":"Design stamped - one undo step")); return true;
        }
        if(action.equals("export")) {
            if(saved.isEmpty() || !player.isCreative()) return false;
            var stack=SculptureData.item(saved.get()); if(stack.isEmpty()) return false;
            if(!player.getInventory().add(stack)) player.drop(stack,false);
            player.sendOverlayMessage(Component.literal("Sculpture item added to inventory")); return true;
        }
        if(saved.isEmpty()) return false;
        for(var axis:Direction.Axis.values()) for(boolean mirror:new boolean[]{false,true}) {
            if(action.equals((mirror?"mirror_":"rotate_")+axis.getName())) {
                SculptureData.write(tool,SculptureData.CLIPBOARD_KEY,SculptureData.transform(saved.get(),axis,mirror));
                player.sendOverlayMessage(Component.literal("Design "+(mirror?"mirrored":"rotated 90 degrees")+" on "+axis.getName().toUpperCase())); return true;
            }
        }
        return false;
    }
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher,registries,environment) -> {
            var design=Commands.literal("design");
            for(String action:new String[]{"copy","stamp","export","rotate_x","rotate_y","rotate_z","mirror_x","mirror_y","mirror_z"}) {
                design.then(Commands.literal(action).executes(context -> {
                    if(execute(context.getSource().getPlayerOrException(),action)) return 1;
                    context.getSource().sendFailure(Component.literal("Hold the chisel; copy a design first and aim within reach. Export requires Creative; stamping cannot overlap an entity."));
                    return 0;
                }));
            }
            dispatcher.register(Commands.literal("astra").then(design));
        });
    }
}
