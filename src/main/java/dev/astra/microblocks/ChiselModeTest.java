package dev.astra.microblocks;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Opt-in runtime gate: selection coverage, actual item controls, atomic edits and save round trips. */
public final class ChiselModeTest {
    private ChiselModeTest() {}

    public static void run(ServerLevel level) {
        testSelections();
        ItemStack tool = new ItemStack(AstraMicroblocks.ASTRA_CHISEL);
        require(tool.getMaxStackSize() == 1, "tool must be unstackable");
        require(ChiselMode.read(tool) == ChiselMode.SINGLE, "default mode");
        CustomData.update(DataComponents.CUSTOM_DATA, tool, tag -> tag.putString("other_mod", "preserved"));
        var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        for (ChiselMode mode : ChiselMode.values()) {
            mode.store(tool);
            var encoded = ItemStack.CODEC.encodeStart(ops, tool).getOrThrow();
            ItemStack restored = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
            require(ChiselMode.read(restored) == mode, "mode lost in item save: " + mode);
            require(restored.get(DataComponents.CUSTOM_DATA).copyTag()
                    .getStringOr("other_mod", "").equals("preserved"), "unrelated item data lost");
        }
        CustomData.update(DataComponents.CUSTOM_DATA, tool, tag -> tag.putString("astra_chisel_mode", "unknown"));
        require(ChiselMode.read(tool) == ChiselMode.SINGLE, "unknown mode fallback");
        testInteractions(level, tool);
        System.out.println("ASTRA_TEST: CHISEL_MODES_PASS");
    }

    private static void testSelections() {
        int[][] targets = {{0,0,0}, {15,15,15}, {0,15,0}, {15,0,15}, {7,8,3}, {8,7,12}};
        for (ChiselMode mode : ChiselMode.values()) for (Direction face : Direction.values())
            for (int[] hit : targets) {
                var mask = mode.selection(new MicroblockHitResolver.Cell(hit[0],hit[1],hit[2]), face);
                int size = switch (mode) { case CUBE_2 -> 2; case CUBE_4 -> 4; case CUBE_8 -> 8; default -> 1; };
                int count = switch (mode) { case LINE_X,LINE_Y,LINE_Z -> 16; case PLANE -> 256; default -> size*size*size; };
                require(mask.occupiedCount() == count, "selection size: " + mode);
                for (int y=0;y<16;y++) for (int z=0;z<16;z++) for (int x=0;x<16;x++) {
                    boolean expected = switch (mode) {
                        case SINGLE -> x==hit[0] && y==hit[1] && z==hit[2];
                        case LINE_X -> y==hit[1] && z==hit[2];
                        case LINE_Y -> x==hit[0] && z==hit[2];
                        case LINE_Z -> x==hit[0] && y==hit[1];
                        case PLANE -> switch (face.getAxis()) {
                            case X -> x==hit[0]; case Y -> y==hit[1]; case Z -> z==hit[2];
                        };
                        default -> x/size==hit[0]/size && y/size==hit[1]/size && z/size==hit[2]/size;
                    };
                    require(mask.isOccupied(x,y,z)==expected, "selection coverage: " + mode + " / " + face);
                }
            }
    }

    private static void testInteractions(ServerLevel level, ItemStack tool) {
        BlockPos pos = new BlockPos(6,100,6);
        Player player = new Player(level, new GameProfile(UUID.randomUUID(), "AstraModeTest")) {
            @Override public GameType gameMode() { return GameType.CREATIVE; }
            @Override public void sendOverlayMessage(Component message) {}
        };
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        ChiselMode.SINGLE.store(tool);
        player.setShiftKeyDown(true);
        for (int i=0;i<ChiselMode.values().length;i++) {
            ChiselMode expected = ChiselMode.read(tool).next();
            AstraMicroblocks.ASTRA_CHISEL.use(level, player, InteractionHand.MAIN_HAND);
            require(ChiselMode.read(tool)==expected, "air cycle");
        }
        require(ChiselMode.read(tool)==ChiselMode.SINGLE, "mode cycle wrap");
        player.setShiftKeyDown(false);
        AstraMicroblocks.ASTRA_CHISEL.use(level, player, InteractionHand.MAIN_HAND);
        require(ChiselMode.read(tool)==ChiselMode.SINGLE, "normal air click changed mode");
        BlockHitResult hit = new BlockHitResult(new Vec3(6+7.5/16,101,6+8.5/16), Direction.UP,pos,false);
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
        try {
            for (ChiselMode mode : ChiselMode.values()) {
                level.removeBlock(pos,false);
                level.setBlock(pos,AstraMicroblocks.TEST_HOST.defaultBlockState(),Block.UPDATE_ALL);
                TestHostBlockEntity host = (TestHostBlockEntity) level.getBlockEntity(pos);
                mode.store(tool);
                AstraMicroblocks.ASTRA_CHISEL.useOn(context);
                int removed = switch (mode) {
                    case SINGLE -> 1; case LINE_X,LINE_Y,LINE_Z -> 16; case PLANE -> 256;
                    case CUBE_2 -> 8; case CUBE_4 -> 64; case CUBE_8 -> 512;
                };
                require(host.occupiedCount()==4096-removed && host.revision()==1, "atomic item cut: " + mode);
                var carved = host.gridCopy();
                AstraMicroblocks.ASTRA_CHISEL.useOn(context);
                require(host.revision()==1 && host.gridCopy().equals(carved), "no-op consumed undo: " + mode);
                var restored = (TestHostBlockEntity) BlockEntity.loadStatic(pos, host.getBlockState(),
                        host.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
                require(restored != null && restored.gridCopy().equals(carved) && restored.revision()==1,
                        "batch save round trip: " + mode);
                restored.undo();
                require(restored.isFull() && restored.revision()==2, "saved batch undo: " + mode);
                require(!MicroblockRenderMesh.build(carved).equals(MicroblockRenderMesh.build(new MicroblockGrid())),
                        "cut did not change render mesh: " + mode);
                player.setShiftKeyDown(true);
                AstraMicroblocks.ASTRA_CHISEL.useOn(context);
                require(host.isFull() && host.revision()==2 && !host.canUndo(), "item undo: " + mode);
                require(ChiselMode.read(tool)==mode, "undo changed mode");
                AstraMicroblocks.ASTRA_CHISEL.useOn(context);
                require(host.revision()==2, "empty undo changed revision");
                player.setShiftKeyDown(false);
            }
            // No raycast surface remains after this cut; the command fallback must still work.
            TestHostBlockEntity host = (TestHostBlockEntity) level.getBlockEntity(pos);
            host.removeCells(new MicroblockGrid());
            require(host.isEmpty(), "empty-host setup");
            ChiselUndo.remember(tool, level, host);
            player.setPos(100,100,100);
            require(!ChiselUndo.undoLast(player,tool) && host.isEmpty(), "remote undo accepted");
            player.setPos(6,100,7);
            require(!ChiselUndo.undoLast(player,ItemStack.EMPTY), "undo without chisel");
            var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            ItemStack savedTool = ItemStack.CODEC.parse(ops,
                    ItemStack.CODEC.encodeStart(ops,tool).getOrThrow()).getOrThrow();
            require(ChiselUndo.undoLast(player,savedTool) && host.isFull(), "empty host undo after item reload");
            require(!ChiselUndo.undoLast(player,savedTool), "undo repeated");
            host.removeCell(0,0,0);
            ChiselUndo.remember(tool,level,host);
            host.removeCell(1,0,0);
            require(!ChiselUndo.undoLast(player,tool), "stale undo accepted");
            // A partly empty brush only removes remaining cells and restores that exact prior shape.
            var before = host.gridCopy();
            var mask = ChiselMode.CUBE_2.selection(new MicroblockHitResolver.Cell(0,0,0),Direction.UP);
            long revision = host.revision();
            require(host.removeCells(mask)==6 && host.revision()==revision+1, "partly empty brush");
            mask.clear(); // The caller must not retain a mutable alias into the edited host.
            require(host.occupiedCount()==4088, "selection alias mutated host");
            host.undo();
            require(host.gridCopy().equals(before), "partial brush undo restored wrong shape");
        } finally { level.removeBlock(pos,false); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
