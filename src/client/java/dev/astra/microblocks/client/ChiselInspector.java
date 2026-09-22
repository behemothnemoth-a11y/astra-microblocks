package dev.astra.microblocks.client;

import dev.astra.microblocks.AstraMicroblocks;
import dev.astra.microblocks.ChiselMode;
import dev.astra.microblocks.ChiselOperation;
import dev.astra.microblocks.ChiselPreview;
import dev.astra.microblocks.MicroblockHitResolver;
import dev.astra.microblocks.TestHostBlockEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

/** Extracts a cached, read-only preview on the client, before rendering consumes it. */
public final class ChiselInspector {
    private static KeyMapping menuKey;
    private static TestHostBlockEntity cachedHost;
    private static long cachedRevision;
    private static MicroblockHitResolver.Cell cachedCell;
    private static Direction cachedFace;
    private static ChiselMode cachedMode;
    private static ChiselOperation cachedOperation;
    private static ChiselPreview cachedPreview;

    private ChiselInspector() {}

    public static void register() {
        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.astra_microblocks.modes", GLFW.GLFW_KEY_G,
                KeyMapping.Category.register(AstraMicroblocks.id("chisel"))));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.consumeClick()) {
                if (client.gui.screen() == null && holdingChisel(client))
                    client.gui.setScreen(new ChiselModeScreen(ChiselMode.read(client.player.getMainHandItem()), ChiselOperation.read(client.player.getMainHandItem())));
            }
            if (client.level == null || !holdingChisel(client)) clearCache();
        });
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            Minecraft client = Minecraft.getInstance();
            Target target = target(client);
            if (target == null || client.player.isShiftKeyDown()) return;
            var collector = new SimpleGizmoCollector();
            try (var ignored = Gizmos.withCollector(collector)) {
                emit(target.preview(), target.pos(), ChiselOperation.read(client.player.getMainHandItem()));
            }
            context.levelRenderer().addMainThreadGizmos(collector.drainGizmos());
        });
        HudElementRegistry.addLast(AstraMicroblocks.id("chisel_inspector"), (graphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (!visible(client)) return;
            ChiselMode mode = ChiselMode.read(client.player.getMainHandItem());
            Target target = target(client);
            var operation = ChiselOperation.read(client.player.getMainHandItem());
            String first = operation.label() + ": " + mode.label()
                    + (target == null ? "" : " | " + dev.astra.microblocks.HostMaterial.of(cachedHost.getBlockState()).label());
            String second = target == null ? "Aim at a sculptable block" :
                    "Cells: " + target.preview().occupied() + "/4096  |  " +
                    (client.player.isShiftKeyDown() ? "Undo last edit" : (target.outside() ? "Outside host" : "Next " + operation.id() + ": " + target.preview().affected()));
            String third = menuKey.getTranslatedKeyMessage().getString() + ": modes  |  Crouch + click air: cycle";
            int width = Math.max(client.font.width(first), Math.max(client.font.width(second), client.font.width(third)));
            String fourth = target == null ? (operation == ChiselOperation.ADD ? "Add fills cavities within this host" : "Cut removes cells within one host") : "Undo: " + cachedHost.undoDepth() + "  |  Redo: " + cachedHost.redoDepth();
            width = Math.max(width,client.font.width(fourth));
            graphics.fill(5, 5, width + 13, 55, 0xA0000000);
            graphics.text(client.font, first, 9, 9, 0xFFFFC04D);
            graphics.text(client.font, second, 9, 21, 0xFFFFFFFF);
            graphics.text(client.font, third, 9, 33, 0xFFD0D0D0);
            graphics.text(client.font, fourth, 9, 45, 0xFFD0D0D0);
        });
    }

    static boolean holdingChisel(Minecraft client) {
        return client.player != null && client.player.getMainHandItem().is(AstraMicroblocks.ASTRA_CHISEL);
    }

    private static boolean visible(Minecraft client) {
        return client.level != null && holdingChisel(client) && client.gui.screen() == null && !client.gui.hud.isHidden();
    }

    private record Target(BlockPos pos, ChiselPreview preview, boolean outside) {}

    private static Target target(Minecraft client) {
        if (!visible(client) || !(client.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK
                || !(client.level.getBlockEntity(hit.getBlockPos()) instanceof TestHostBlockEntity host)) {
            clearCache();
            return null;
        }
        var operation = ChiselOperation.read(client.player.getMainHandItem());
        var cell = operation.target(hit).orElse(null);
        var mode = ChiselMode.read(client.player.getMainHandItem());
        if (cachedHost != host || cachedRevision != host.revision() || !java.util.Objects.equals(cell,cachedCell)
                || cachedFace != hit.getDirection() || cachedMode != mode || cachedOperation != operation) {
            cachedPreview = ChiselPreview.create(host.gridCopy(), mode, cell, hit.getDirection(), operation);
            cachedHost = host;
            cachedRevision = host.revision();
            cachedCell = cell;
            cachedFace = hit.getDirection();
            cachedMode = mode;
            cachedOperation = operation;
        }
        return new Target(hit.getBlockPos(), cachedPreview, cell == null);
    }

    static void emit(ChiselPreview preview, BlockPos pos) {
        emit(preview,pos,ChiselOperation.CUT);
    }

    static void emit(ChiselPreview preview, BlockPos pos, ChiselOperation operation) {
        var style = operation == ChiselOperation.ADD ? GizmoStyle.strokeAndFill(0xFF52EF8B,2f,0x3052EF8B)
                : GizmoStyle.strokeAndFill(0xFFFFB52E,2f,0x30FFB52E);
        for (var box : preview.boxes()) {
            var bounds = new AABB(box.minX()/16.0, box.minY()/16.0, box.minZ()/16.0,
                    box.maxX()/16.0, box.maxY()/16.0, box.maxZ()/16.0).move(pos).inflate(0.0005);
            // Show the full cut depth through the host, including cells behind its front surface.
            Gizmos.cuboid(bounds, style).setAlwaysOnTop();
        }
    }

    private static void clearCache() { cachedHost = null; cachedPreview = null; }
}
