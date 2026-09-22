package dev.astra.microblocks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.astra.microblocks.AstraMicroblocks;
import dev.astra.microblocks.MicroblockRenderMesh;
import dev.astra.microblocks.TestHostBlockEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Opt-in client smoke gate: real Fabric renderer, loaded atlas and submission queue. */
public final class ClientRenderTest {
    private static boolean finished;

    private ClientRenderTest() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // The initial atlas/model reload completes before the loading overlay closes.
            if (finished || !client.isGameLoadFinished()) return;
            finished = true;
            try {
                run(client);
                System.out.println("ASTRA_TEST: CLIENT_RENDER_PASS");
            } catch (Throwable failure) {
                failure.printStackTrace();
                System.out.println("ASTRA_TEST: CLIENT_RENDER_FAIL");
            } finally {
                client.stop();
            }
        });
    }

    private static void run(Minecraft client) {
        verifyChiselItem(client);
        var host = new TestHostBlockEntity(BlockPos.ZERO, AstraMicroblocks.TEST_HOST.defaultBlockState());
        var registered = client.getBlockEntityRenderDispatcher()
                .<TestHostBlockEntity, TestHostRenderState>getRenderer(host);
        require(registered instanceof TestHostBlockEntityRenderer, "host renderer not registered");
        var renderer = (TestHostBlockEntityRenderer) registered;
        TestHostRenderState original = renderer.createRenderState();
        renderer.extractRenderState(host, original, 0, Vec3.ZERO, null);
        require(original.mesh.size() == 1536, "full host mesh");
        TestHostRenderState repeated = renderer.createRenderState();
        renderer.extractRenderState(host, repeated, 0, Vec3.ZERO, null);
        require(original.mesh == repeated.mesh, "unchanged host did not reuse mesh");

        host.removeCell(8, 15, 8);
        TestHostRenderState carved = renderer.createRenderState();
        renderer.extractRenderState(host, carved, 0, Vec3.ZERO, null);
        require(carved.mesh != original.mesh && carved.mesh.size() == 1540, "carve did not invalidate mesh");
        require(original.mesh.size() == 1536, "previous render snapshot was mutated");
        verifyMesh(client, host, carved.mesh);

        // Exercise Fabric's injected submission overloads, including the break overlay.
        var queue = new SubmitNodeStorage();
        var poses = new PoseStack();
        carved.breakProgress = new ModelFeatureRenderer.CrumblingOverlay(3, poses.last());
        renderer.submit(carved, poses, queue, new CameraRenderState());
        require(!queue.getSubmitsPerOrder().isEmpty(), "mesh was not submitted");

        host.undo();
        TestHostRenderState restored = renderer.createRenderState();
        renderer.extractRenderState(host, restored, 0, Vec3.ZERO, null);
        require(restored.mesh != carved.mesh && restored.mesh.size() == 1536, "undo did not restore mesh");

        for (var mode : dev.astra.microblocks.ChiselMode.values()) {
            host.removeCells(mode.selection(new dev.astra.microblocks.MicroblockHitResolver.Cell(7,15,8),
                    net.minecraft.core.Direction.UP));
            TestHostRenderState batch = renderer.createRenderState();
            renderer.extractRenderState(host, batch, 0, Vec3.ZERO, null);
            require(batch.mesh != restored.mesh, "batch cut did not invalidate mesh: " + mode);
            verifyMesh(client, host, batch.mesh);
            TestHostRenderState cached = renderer.createRenderState();
            renderer.extractRenderState(host, cached, 0, Vec3.ZERO, null);
            require(cached.mesh == batch.mesh, "batch mesh not cached: " + mode);
            host.undo();
            renderer.extractRenderState(host, restored, 0, Vec3.ZERO, null);
            require(restored.mesh.size() == 1536 && restored.mesh != batch.mesh, "batch undo mesh: " + mode);
        }

        var replacement = new TestHostBlockEntity(BlockPos.ZERO, AstraMicroblocks.TEST_HOST.defaultBlockState());
        replacement.removeCell(0, 0, 0);
        TestHostRenderState replaced = renderer.createRenderState();
        renderer.extractRenderState(replacement, replaced, 0, Vec3.ZERO, null);
        require(replaced.mesh != carved.mesh, "replacement entity reused old host's cache");
        verifyMesh(client, replacement, replaced.mesh);

        var empty = host.gridCopy();
        empty.clear();
        var sprite = client.getAtlasManager().get(new SpriteId(
                TextureAtlas.LOCATION_BLOCKS, Identifier.withDefaultNamespace("block/stone")));
        require(TestHostBlockEntityRenderer.buildMesh(empty, sprite).size() == 0, "empty grid rendered surfaces");
    }

    private static void verifyChiselItem(Minecraft client) {
        // The title-screen smoke client has no world's bound item components yet.
        // Supply an isolated holder to test the loaded icon/hand model only.
        // Registered stack defaults are checked by AstraChiselTest in a real server world.
        var components = net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.ITEM_MODEL, AstraMicroblocks.id("astra_chisel"))
                .build();
        var stack = new net.minecraft.world.item.ItemStack(
                new net.minecraft.core.Holder.Direct<>(AstraMicroblocks.ASTRA_CHISEL, components), 1);
        for (var context : new net.minecraft.world.item.ItemDisplayContext[] {
                net.minecraft.world.item.ItemDisplayContext.GUI,
                net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND}) {
            var state = new net.minecraft.client.renderer.item.ItemStackRenderState();
            client.getItemModelResolver().updateForTopItem(state, stack, context, null, null, 0);
            require(!state.isEmpty(), "chisel has no model in " + context);
            require(state.getModelBoundingBox().getSize() > 0, "chisel has no visible geometry in " + context);
        }
        System.out.println("ASTRA_TEST: CHISEL_ITEM_MODEL_PASS");
    }

    private static void verifyMesh(Minecraft client, TestHostBlockEntity host, Mesh mesh) {
        var faces = MicroblockRenderMesh.build(host.gridCopy());
        var sprite = client.getAtlasManager().get(new SpriteId(
                TextureAtlas.LOCATION_BLOCKS, Identifier.withDefaultNamespace("block/stone")));
        require(sprite.contents().name().equals(Identifier.withDefaultNamespace("block/stone")), "stone texture missing");
        int[] index = {0};
        mesh.forEach(quad -> {
            var face = faces.get(index[0]++);
            require(quad.lightFace() == face.direction(), "quad normal changed");
            require(quad.cullFace() == null, "cavity quad incorrectly culled");
            float minU = Float.POSITIVE_INFINITY, maxU = Float.NEGATIVE_INFINITY;
            float minV = Float.POSITIVE_INFINITY, maxV = Float.NEGATIVE_INFINITY;
            for (int corner = 0; corner < 4; corner++) {
                var vertex = face.vertex(corner);
                require(quad.x(corner) == vertex.x() / 16f && quad.y(corner) == vertex.y() / 16f
                        && quad.z(corner) == vertex.z() / 16f, "quad coordinates changed");
                minU = Math.min(minU, quad.u(corner)); maxU = Math.max(maxU, quad.u(corner));
                minV = Math.min(minV, quad.v(corner)); maxV = Math.max(maxV, quad.v(corner));
            }
            float spanU = sprite.getU1() - sprite.getU0(), spanV = sprite.getV1() - sprite.getV0();
            require(Math.abs((maxU - minU) / spanU - 1 / 16f) < 0.002f, "incorrect texture U scale");
            require(Math.abs((maxV - minV) / spanV - 1 / 16f) < 0.002f, "incorrect texture V scale");
        });
        require(index[0] == faces.size(), "mesh dropped faces");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
