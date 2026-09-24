package dev.astra.microblocks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.astra.microblocks.AstraMicroblocks;
import dev.astra.microblocks.MicroblockRenderMesh;
import dev.astra.microblocks.MicroblockGrid;
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
            if (finished) { ChiselMenuCapture.tick(client); return; }
            if (!client.isGameLoadFinished()) return;
            finished = true;
            try {
                run(client);
                System.out.println("ASTRA_TEST: CLIENT_RENDER_PASS");
                if (Boolean.getBoolean("astra.menuScreenshot")) ChiselMenuCapture.start(client);
                else client.stop();
            } catch (Throwable failure) {
                failure.printStackTrace();
                System.out.println("ASTRA_TEST: CLIENT_RENDER_FAIL");
                client.stop();
            }
        });
    }

    private static void run(Minecraft client) {
        verifyChiselItem(client);
        ChiselWheelTest.run(client);
        verifyInspector(client);
        verifyMaterials(client);
        verifyMixedMaterials(client);
        verifyWorkflow(client);
        verifySculptures(client);
        verifyPalette(client);
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

        var workshop = new TestHostBlockEntity(BlockPos.ZERO,AstraMicroblocks.TEST_HOST.defaultBlockState());
        var brush = dev.astra.microblocks.ChiselMode.CUBE_4.selection(
                new dev.astra.microblocks.MicroblockHitResolver.Cell(7,15,8),net.minecraft.core.Direction.UP);
        workshop.editCells(brush,dev.astra.microblocks.ChiselOperation.CUT);
        var workshopState = renderer.createRenderState();
        renderer.extractRenderState(workshop,workshopState,0,Vec3.ZERO,null);
        var cutMesh = workshopState.mesh;
        workshop.editCells(brush,dev.astra.microblocks.ChiselOperation.ADD);
        renderer.extractRenderState(workshop,workshopState,0,Vec3.ZERO,null);
        require(workshopState.mesh != cutMesh && workshopState.mesh.size()==1536,"addition did not rebuild full mesh");
        require(workshop.undoEdit(),"client addition undo");
        renderer.extractRenderState(workshop,workshopState,0,Vec3.ZERO,null);
        verifyMesh(client,workshop,workshopState.mesh);
        require(workshopState.mesh.size()==cutMesh.size(),"addition undo geometry");
        require(workshop.redoEdit(),"client addition redo");
        renderer.extractRenderState(workshop,workshopState,0,Vec3.ZERO,null);
        require(workshopState.mesh.size()==1536,"addition redo mesh");

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

    private static void verifyPalette(Minecraft client) {
        var sprites=(net.minecraft.client.resources.model.sprite.SpriteGetter)client.getAtlasManager()::get;
        for(var material:dev.astra.microblocks.HostMaterial.catalog()) {
            var volume=dev.astra.microblocks.MicroblockVolume.empty(material);volume.add(5,6,7,material);
            var host=new TestHostBlockEntity(BlockPos.ZERO,dev.astra.microblocks.AstraMicroblocks.TEST_HOST.defaultBlockState());host.initializeDesign(volume);
            verifyMesh(client,host,TestHostBlockEntityRenderer.buildVolumeMesh(volume,sprites));
        }
        var mud=dev.astra.microblocks.HostMaterial.find("mud_bricks").orElseThrow();
        var vertex=new MicroblockRenderMesh.Vertex(3,4,5);
        require(Math.abs(mud.uv(net.minecraft.core.Direction.NORTH,vertex)[0]-3/16f)<0.00001f
                && Math.abs(mud.uv(net.minecraft.core.Direction.WEST,vertex)[0]-(1-5/16f))<0.00001f,"mirrored vanilla face UV ignored");
        var mosaic=dev.astra.microblocks.PaletteTest.mosaic();
        var host=new TestHostBlockEntity(BlockPos.ZERO,dev.astra.microblocks.AstraMicroblocks.TEST_HOST.defaultBlockState());host.initializeDesign(mosaic);
        verifyMesh(client,host,TestHostBlockEntityRenderer.buildVolumeMesh(mosaic,sprites));
        require(MaterialScreen.filtered("deepslate",dev.astra.microblocks.HostMaterial.catalog()).size()>5,"material search missing family");
        require(MaterialScreen.filtered("oak log (x)",dev.astra.microblocks.HostMaterial.catalog()).stream().anyMatch(m -> m.id().equals("minecraft:oak_log[axis=x]")),"axis search missing");
        for(var size:new int[][]{{320,240},{640,360}}) {
            var commands=new java.util.ArrayList<String>();
            var screen=new MaterialScreen(commands::add,java.util.List.of(dev.astra.microblocks.HostMaterial.find("bricks").orElseThrow()));
            screen.init(size[0],size[1]);screen.query("blue concrete");
            require(screen.children().size()==16,"material screen duplicate controls");
            var graphics=new net.minecraft.client.gui.GuiGraphicsExtractor(client,new net.minecraft.client.renderer.state.gui.GuiRenderState(),0,0);
            screen.extractRenderState(graphics,0,0,0);
            for(var child:screen.children()) {
                var widget=(net.minecraft.client.gui.components.AbstractWidget)child;
                require(widget.getX()>=0 && widget.getY()>=0 && widget.getRight()<=size[0] && widget.getBottom()<=size[1],"material controls outside screen");
                if(widget.visible && widget.getMessage().getString().equals("Blue concrete")) {
                    require(screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(widget.getX()+10,widget.getY()+10,
                            new net.minecraft.client.input.MouseButtonInfo(0,0)),false),"material click not handled");
                }
            }
            require(commands.equals(java.util.List.of("astra material minecraft:blue_concrete")),"picker sent wrong selection");
            screen.query("");pressMaterialButton(screen,"Recent");
            pressMaterialButton(screen,"Bricks");require(commands.getLast().equals("astra material minecraft:bricks"),"recent material selection failed");
            pressMaterialButton(screen,"All blocks");pressMaterialButton(screen,">");pressMaterialButton(screen,"<");
            screen.query("no_such_material");screen.extractRenderState(graphics,0,0,0);
            screen.init(size[0],size[1]);require(screen.children().size()==16,"material resize duplicated controls");
            require(!screen.isPauseScreen(),"material screen pauses game");
        }
        System.out.println("ASTRA_TEST: PALETTE_CLIENT_PASS");
    }

    private static void pressMaterialButton(MaterialScreen screen,String label) {
        var button=screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.Button b
                && b.visible && b.active && b.getMessage().getString().equals(label))
                .map(c -> (net.minecraft.client.gui.components.Button)c).findFirst().orElseThrow();
        require(screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(button.getX()+button.getWidth()/2.0,
                button.getY()+button.getHeight()/2.0,new net.minecraft.client.input.MouseButtonInfo(0,0)),false),"material control click failed "+label);
    }

    private static void verifySculptures(Minecraft client) {
        var renderer=new SculptureItemRenderer(client.getAtlasManager()::get);
        for(var original:dev.astra.microblocks.HostMaterial.values()) {
            var volume=dev.astra.microblocks.SculptureTest.pattern(original);
            for(var axis:net.minecraft.core.Direction.Axis.values()) {
                volume=dev.astra.microblocks.SculptureData.transform(volume,axis,false);
                var stack=DesignScreen.previewItem(volume);
                var host=new TestHostBlockEntity(BlockPos.ZERO,(original==dev.astra.microblocks.HostMaterial.STONE?AstraMicroblocks.TEST_HOST:AstraMicroblocks.OAK_HOST).defaultBlockState());
                host.initializeDesign(volume);
                var mesh=renderer.extractArgument(stack); verifyMesh(client,host,mesh);
                require(renderer.extractArgument(stack)==mesh,"sculpture item mesh not cached");
                for(var display:new net.minecraft.world.item.ItemDisplayContext[]{net.minecraft.world.item.ItemDisplayContext.GUI,
                        net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,net.minecraft.world.item.ItemDisplayContext.GROUND}) {
                    var state=new net.minecraft.client.renderer.item.ItemStackRenderState();
                    client.getItemModelResolver().updateForTopItem(state,stack,display,null,null,0);
                    require(!state.isEmpty() && state.getModelBoundingBox().getSize()>0,"dynamic sculpture item missing");
                    var queue=new SubmitNodeStorage(); state.submit(new PoseStack(),queue,15728880,0,0);
                    require(!queue.getSubmitsPerOrder().isEmpty(),"sculpture model did not submit");
                }
            }
        }
        for(int[] size:new int[][]{{320,240},{640,360}}) {
            var commands=new java.util.ArrayList<String>();
            var screen=new DesignScreen(commands::add,dev.astra.microblocks.SculptureTest.pattern(dev.astra.microblocks.HostMaterial.STONE));
            screen.init(size[0],size[1]); require(screen.children().size()==10,"design workbench controls missing");
            for(var child:screen.children()) {
                var widget=(net.minecraft.client.gui.components.AbstractWidget)child;
                require(widget.getX()>=0 && widget.getY()>=0 && widget.getRight()<=size[0] && widget.getBottom()<=size[1],"design controls outside GUI");
            }
            var graphics=new net.minecraft.client.gui.GuiGraphicsExtractor(client,new net.minecraft.client.renderer.state.gui.GuiRenderState(),0,0);
            screen.extractRenderState(graphics,0,0,0);
            for(var child:screen.children()) {
                var button=(net.minecraft.client.gui.components.Button)child;
                if(!button.getMessage().getString().equals("Done")) require(screen.mouseClicked(
                        new net.minecraft.client.input.MouseButtonEvent(button.getX()+button.getWidth()/2.0,
                                button.getY()+button.getHeight()/2.0,new net.minecraft.client.input.MouseButtonInfo(0,0)),false),
                        "design button did not accept click");
            }
            require(commands.size()==9 && commands.contains("astra design copy") && commands.contains("astra design stamp")
                    && commands.contains("astra design export") && commands.contains("astra design rotate_y")
                    && commands.contains("astra design mirror_z"),"design commands not connected");
        }
        System.out.println("ASTRA_TEST: SCULPTURE_CLIENT_PASS");
    }

    private static void verifyWorkflow(Minecraft client) {
        var commands=new java.util.ArrayList<String>();
        for(var action:java.util.List.of("undo","redo","sample")) {
            require(ChiselInspector.dispatchShortcut(action,true,true,false,commands::add),"shortcut not dispatched");
            require(commands.getLast().equals("astra "+action),"wrong shortcut command");
            int count=commands.size();
            require(!ChiselInspector.dispatchShortcut(action,false,true,false,commands::add),"shortcut outside world");
            require(!ChiselInspector.dispatchShortcut(action,true,false,false,commands::add),"shortcut without chisel");
            require(!ChiselInspector.dispatchShortcut(action,true,true,true,commands::add),"shortcut inside menu/chat/inventory");
            require(!ChiselInspector.dispatchShortcut(action,true,true,false,null),"shortcut without connection");
            require(commands.size()==count,"rejected shortcut sent command");
        }
        var host=new TestHostBlockEntity(BlockPos.ZERO,AstraMicroblocks.TEST_HOST.defaultBlockState());
        var renderer=(TestHostBlockEntityRenderer)client.getBlockEntityRenderDispatcher().<TestHostBlockEntity,TestHostRenderState>getRenderer(host);
        var state=renderer.createRenderState(); renderer.extractRenderState(host,state,0,Vec3.ZERO,null);
        var before=state.mesh;
        var cell=new dev.astra.microblocks.MicroblockHitResolver.Cell(8,15,8);
        var mask=dev.astra.microblocks.ChiselMode.CUBE_2.selection(cell,net.minecraft.core.Direction.UP);
        host.editCells(mask,dev.astra.microblocks.ChiselOperation.REPLACE,dev.astra.microblocks.HostMaterial.OAK_PLANKS);
        renderer.extractRenderState(host,state,0,Vec3.ZERO,null);
        require(state.mesh!=before && state.mesh.size()==before.size(),"material-only edit stale cache or changed topology");
        verifyMesh(client,host,state.mesh);
        require(ChiselInspector.materialCounts(host).equals("Stone: 4088 | Oak: 8 | Empty: 0"),"inspector material counts");
        var noChange=dev.astra.microblocks.ChiselPreview.create(host.volumeCopy(),dev.astra.microblocks.ChiselMode.CUBE_2,
                cell,net.minecraft.core.Direction.UP,dev.astra.microblocks.ChiselOperation.REPLACE,dev.astra.microblocks.HostMaterial.OAK_PLANKS);
        require(noChange.affected()==0 && noChange.boxes().isEmpty(),"same material preview visible");
        var preview=dev.astra.microblocks.ChiselPreview.create(host.volumeCopy(),dev.astra.microblocks.ChiselMode.CUBE_2,
                cell,net.minecraft.core.Direction.UP,dev.astra.microblocks.ChiselOperation.REPLACE,dev.astra.microblocks.HostMaterial.STONE);
        var collector=new net.minecraft.gizmos.SimpleGizmoCollector();
        try(var ignored=net.minecraft.gizmos.Gizmos.withCollector(collector)) { ChiselInspector.emit(preview,BlockPos.ZERO,dev.astra.microblocks.ChiselOperation.REPLACE); }
        var gizmos=collector.drainGizmos(); require(!gizmos.isEmpty(),"replace preview missing");
        for(var instance:gizmos) require(((net.minecraft.gizmos.CuboidGizmo)instance.gizmo()).style().stroke()==0xFFAA89FF,"replace preview not violet");
        host.undoEdit(); renderer.extractRenderState(host,state,0,Vec3.ZERO,null); verifyMesh(client,host,state.mesh);
        require(ChiselInspector.materialCounts(host).equals("Stone: 4096 | Oak: 0 | Empty: 0"),"counts stale after undo");
        host.redoEdit(); renderer.extractRenderState(host,state,0,Vec3.ZERO,null); verifyMesh(client,host,state.mesh);
        host.editCells(mask,dev.astra.microblocks.ChiselOperation.CUT);
        require(ChiselInspector.materialCounts(host).equals("Stone: 4088 | Oak: 0 | Empty: 8"),"empty count wrong");
        System.out.println("ASTRA_TEST: WORKFLOW_CLIENT_PASS");
    }

    private static void verifyMixedMaterials(Minecraft client) {
        for (var block : new dev.astra.microblocks.TestHostBlock[]{AstraMicroblocks.TEST_HOST,AstraMicroblocks.OAK_HOST}) {
            var host=new TestHostBlockEntity(BlockPos.ZERO,block.defaultBlockState());
            var renderer=(TestHostBlockEntityRenderer)client.getBlockEntityRenderDispatcher()
                    .<TestHostBlockEntity,TestHostRenderState>getRenderer(host);
            var state=renderer.createRenderState();
            var other=dev.astra.microblocks.HostMaterial.of(host.getBlockState())==dev.astra.microblocks.HostMaterial.STONE
                    ?dev.astra.microblocks.HostMaterial.OAK_PLANKS:dev.astra.microblocks.HostMaterial.STONE;
            for(var mode:dev.astra.microblocks.ChiselMode.values()) {
                var mask=mode.selection(new dev.astra.microblocks.MicroblockHitResolver.Cell(7,15,8),net.minecraft.core.Direction.UP);
                host.editCells(mask,dev.astra.microblocks.ChiselOperation.CUT);
                host.editCells(mask,dev.astra.microblocks.ChiselOperation.ADD,other);
                renderer.extractRenderState(host,state,0,Vec3.ZERO,null);
                require(state.mesh.size()==1536,"internal material boundaries rendered faces");
                verifyMesh(client,host,state.mesh);
                var mixed=state.mesh;
                renderer.extractRenderState(host,state,0,Vec3.ZERO,null);
                require(state.mesh==mixed,"mixed mesh cache miss");
                host.undoEdit(); renderer.extractRenderState(host,state,0,Vec3.ZERO,null);
                require(state.mesh!=mixed,"mixed undo cache stale"); verifyMesh(client,host,state.mesh);
                host.redoEdit(); renderer.extractRenderState(host,state,0,Vec3.ZERO,null);
                verifyMesh(client,host,state.mesh);
            }
            // Alternating materials at every cell stresses all texture transitions and cavity walls.
            host.editCells(host.gridCopy(),dev.astra.microblocks.ChiselOperation.CUT);
            for(int y=0;y<16;y++) for(int z=0;z<16;z++) for(int x=0;x<16;x++) {
                var mask=MicroblockGrid.fromLongArray(new long[64]); mask.add(x,y,z);
                if((x+y+z)%3!=0) host.editCells(mask,dev.astra.microblocks.ChiselOperation.ADD,
                        (x+y+z)%2==0?dev.astra.microblocks.HostMaterial.STONE:dev.astra.microblocks.HostMaterial.OAK_PLANKS);
            }
            renderer.extractRenderState(host,state,0,Vec3.ZERO,null); verifyMesh(client,host,state.mesh);
        }
        System.out.println("ASTRA_TEST: MIXED_CLIENT_PASS");
    }

    private static void verifyMaterials(Minecraft client) {
        var stone = new TestHostBlockEntity(BlockPos.ZERO, AstraMicroblocks.TEST_HOST.defaultBlockState());
        var renderer = (TestHostBlockEntityRenderer) client.getBlockEntityRenderDispatcher()
                .<TestHostBlockEntity, TestHostRenderState>getRenderer(stone);
        var stoneState = renderer.createRenderState();
        renderer.extractRenderState(stone, stoneState, 0, Vec3.ZERO, null);
        var originalStone = stoneState.mesh;
        for (var block : new dev.astra.microblocks.TestHostBlock[] {AstraMicroblocks.TEST_HOST, AstraMicroblocks.OAK_HOST}) {
            var host = new TestHostBlockEntity(BlockPos.ZERO, block.defaultBlockState());
            require(client.getBlockEntityRenderDispatcher().<TestHostBlockEntity, TestHostRenderState>getRenderer(host) == renderer, "material must share registered renderer");
            var state = renderer.createRenderState();
            renderer.extractRenderState(host, state, 0, Vec3.ZERO, null);
            require(state.mesh.size() == 1536, "full material mesh");
            verifyMesh(client, host, state.mesh);
            var fullMesh = state.mesh;
            renderer.extractRenderState(host, state, 0, Vec3.ZERO, null);
            require(state.mesh == fullMesh, "material cache miss without edits");
            for (var mode : dev.astra.microblocks.ChiselMode.values()) {
                var mask = mode.selection(new dev.astra.microblocks.MicroblockHitResolver.Cell(7,15,8),
                        net.minecraft.core.Direction.UP);
                require(host.editCells(mask,dev.astra.microblocks.ChiselOperation.CUT)>0,"material render cut");
                renderer.extractRenderState(host,state,0,Vec3.ZERO,null);
                verifyMesh(client,host,state.mesh);
                var cutMesh = state.mesh;
                host.editCells(mask,dev.astra.microblocks.ChiselOperation.ADD);
                renderer.extractRenderState(host,state,0,Vec3.ZERO,null);
                require(state.mesh!=cutMesh && state.mesh.size()==1536,"material repair mesh");
                verifyMesh(client,host,state.mesh);
                require(host.undoEdit(),"material mesh undo");
                renderer.extractRenderState(host,state,0,Vec3.ZERO,null);
                verifyMesh(client,host,state.mesh);
                require(host.redoEdit(),"material mesh redo");
                renderer.extractRenderState(host,state,0,Vec3.ZERO,null);
                verifyMesh(client,host,state.mesh);
            }
            verifyBlockItem(client, block);
        }
        renderer.extractRenderState(stone,stoneState,0,Vec3.ZERO,null);
        require(stoneState.mesh == originalStone, "other material polluted stone cache");
        // A same-position, same-revision replacement must use the new material's UVs.
        var oak = new TestHostBlockEntity(BlockPos.ZERO,AstraMicroblocks.OAK_HOST.defaultBlockState());
        var oakState = renderer.createRenderState();
        renderer.extractRenderState(oak,oakState,0,Vec3.ZERO,null);
        require(oakState.mesh != originalStone,"material replacement reused stone cache");
        verifyMesh(client,oak,oakState.mesh);
        oak.editCells(oak.gridCopy(),dev.astra.microblocks.ChiselOperation.CUT);
        renderer.extractRenderState(oak,oakState,0,Vec3.ZERO,null);
        require(oakState.mesh.size()==0,"empty oak rendered a cube");
        require(oak.undoEdit(),"empty oak undo");
        renderer.extractRenderState(oak,oakState,0,Vec3.ZERO,null);
        verifyMesh(client,oak,oakState.mesh);
        System.out.println("ASTRA_TEST: MATERIAL_CLIENT_PASS");
    }

    private static void verifyBlockItem(Minecraft client, dev.astra.microblocks.TestHostBlock block) {
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(block.asItem());
        var components = net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.ITEM_MODEL,id).build();
        var stack = new net.minecraft.world.item.ItemStack(new net.minecraft.core.Holder.Direct<>(block.asItem(),components),1);
        for (var context : new net.minecraft.world.item.ItemDisplayContext[] {
                net.minecraft.world.item.ItemDisplayContext.GUI,net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND}) {
            var state = new net.minecraft.client.renderer.item.ItemStackRenderState();
            client.getItemModelResolver().updateForTopItem(state,stack,context,null,null,0);
            require(!state.isEmpty() && state.getModelBoundingBox().getSize()>0,"material item model missing: "+id);
        }
    }

    private static void verifyInspector(Minecraft client) {
        var grid = new dev.astra.microblocks.MicroblockGrid();
        grid.remove(7,15,8);
        var preview = dev.astra.microblocks.ChiselPreview.create(grid, dev.astra.microblocks.ChiselMode.LINE_X,
                new dev.astra.microblocks.MicroblockHitResolver.Cell(7,15,8), net.minecraft.core.Direction.UP);
        var collector = new net.minecraft.gizmos.SimpleGizmoCollector();
        var pos = new BlockPos(-12,90,25);
        try (var ignored = net.minecraft.gizmos.Gizmos.withCollector(collector)) { ChiselInspector.emit(preview,pos); }
        var gizmos = collector.drainGizmos();
        require(gizmos.size()==preview.boxes().size() && gizmos.size()==2, "preview should split around empty cell");
        for (int i=0;i<gizmos.size();i++) {
            var instance = gizmos.get(i);
            require(instance.isAlwaysOnTop(), "preview hides cut depth");
            require(instance.gizmo() instanceof net.minecraft.gizmos.CuboidGizmo, "preview is not a cuboid");
            var drawn = (net.minecraft.gizmos.CuboidGizmo) instance.gizmo();
            var box = preview.boxes().get(i);
            var expected = new net.minecraft.world.phys.AABB(box.minX()/16.0,box.minY()/16.0,box.minZ()/16.0,
                    box.maxX()/16.0,box.maxY()/16.0,box.maxZ()/16.0).move(pos).inflate(0.0005);
            require(drawn.aabb().equals(expected), "preview moved or scaled incorrectly");
            require(drawn.style().hasFill() && drawn.style().hasStroke(), "preview is invisible");
        }
        try (var ignored = net.minecraft.gizmos.Gizmos.withCollector(collector)) {
            ChiselInspector.emit(preview,pos,dev.astra.microblocks.ChiselOperation.ADD);
        }
        for (var instance : collector.drainGizmos()) {
            var drawn = (net.minecraft.gizmos.CuboidGizmo) instance.gizmo();
            require(drawn.style().stroke()==0xFF52EF8B,"add preview must be green");
        }
        for (var operation : dev.astra.microblocks.ChiselOperation.values())
        for (int[] size : new int[][] {{320,240},{640,360}}) {
            var screen = new ChiselModeScreen(dev.astra.microblocks.ChiselMode.PLANE,operation);
            screen.init(size[0],size[1]);
            require(screen.children().size()==19, "menu missing mode or close button");
            int selected = 0;
            for (var child : screen.children()) {
                var widget = (net.minecraft.client.gui.components.AbstractWidget) child;
                require(widget.getX()>=0 && widget.getY()>=0 && widget.getRight()<=size[0]
                        && widget.getBottom()<=size[1], "menu widget outside screen");
                if (!widget.active) { selected++; require((widget.getMessage().getString().equals("Plane (clicked face)") || widget.getMessage().getString().equals(operation.label()) || widget.getMessage().getString().equals("Original")), "wrong selected mode"); }
            }
            require(selected==3 && !screen.isPauseScreen(), "menu selection/pause state");
            var state = new net.minecraft.client.renderer.state.gui.GuiRenderState();
            var graphics = new net.minecraft.client.gui.GuiGraphicsExtractor(client,state,0,0);
            screen.extractRenderState(graphics,0,0,0);
            int[] textCount = {0}; state.forEachText(text -> textCount[0]++);
            require(textCount[0]>=2, "menu did not extract title and current mode");
            for (var nextMode : dev.astra.microblocks.ChiselMode.values())
            for (var nextOp : dev.astra.microblocks.ChiselOperation.values()) {
                screen.updateSelection(nextMode, nextOp);
                int inactive = 0;
                for (var child : screen.children()) {
                    var widget = (net.minecraft.client.gui.components.AbstractWidget) child;
                    if (!widget.active) {
                        inactive++;
                        require(widget.getMessage().getString().equals(nextMode.label())
                                || widget.getMessage().getString().equals(nextOp.label()) || widget.getMessage().getString().equals("Original"), "menu stale server selection");
                    }
                }
                require(inactive == 3 && screen.children().size() == 19, "menu duplicated or lost controls on selection");
            }
        }
        System.out.println("ASTRA_TEST: CHISEL_INSPECTOR_CLIENT_PASS");
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
        var texture = AstraMicroblocks.id("item/astra_chisel");
        var sprite = client.getAtlasManager().get(new SpriteId(TextureAtlas.LOCATION_ITEMS,texture));
        require(sprite.contents().name().equals(texture),"custom chisel texture missing from atlas");
        require(sprite.contents().width()==64 && sprite.contents().height()==64,"unexpected chisel sprite size");
        try (var stream=ClientRenderTest.class.getResourceAsStream("/assets/astra_microblocks/textures/item/astra_chisel.png")) {
            require(stream!=null,"chisel PNG missing");
            var png=javax.imageio.ImageIO.read(stream);
            require(png.getColorModel().hasAlpha(),"chisel texture lacks transparency");
            int solid=0;
            for (int y=0;y<64;y++) for (int x=0;x<64;x++) {
                int alpha=png.getRGB(x,y)>>>24;
                if (alpha>128) solid++;
                if (x==0 || y==0 || x==63 || y==63) require(alpha==0,"chisel touches texture border");
            }
            require(solid>300 && solid<2800,"chisel is empty or has an opaque matte");
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        System.out.println("ASTRA_TEST: CHISEL_ITEM_MODEL_PASS");
    }

    private static void verifyMesh(Minecraft client, TestHostBlockEntity host, Mesh mesh) {
        var faces = MicroblockRenderMesh.build(host.gridCopy());
        int[] index = {0};
        mesh.forEach(quad -> {
            var face = faces.get(index[0]++);
            var texture = host.materialAt(face.x(),face.y(),face.z()).texture(face.direction());
            var sprite = client.getAtlasManager().get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, texture));
            require(sprite.contents().name().equals(texture), "material texture missing: " + texture);
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
            require(minU >= sprite.getU0() - 0.000001f && maxU <= sprite.getU1() + 0.000001f
                    && minV >= sprite.getV0() - 0.000001f && maxV <= sprite.getV1() + 0.000001f,
                    "quad uses another material's atlas coordinates: " + texture);
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
