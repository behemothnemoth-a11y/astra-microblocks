package dev.astra.microblocks.client;

import dev.astra.microblocks.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Design workbench; all changes are server commands and clipboard state follows the held tool. */
public final class DesignScreen extends Screen {
    private final java.util.function.Consumer<String> testCommands;
    private final MicroblockVolume sample;
    public DesignScreen() { this(null,null); }
    DesignScreen(java.util.function.Consumer<String> commands,MicroblockVolume sample) {
        super(Component.literal("ASTRA DESIGN WORKBENCH"));this.testCommands=commands;this.sample=sample;
    }
    private void action(String action,boolean close) {
        if(testCommands!=null) testCommands.accept("astra design "+action);
        else if(minecraft.getConnection()!=null && ChiselInspector.holdingChisel(minecraft))
            minecraft.getConnection().sendCommand("astra design "+action);
        if(close) onClose();
    }
    private void button(int x,int y,int w,String label,String action,boolean close) {
        addRenderableWidget(Button.builder(Component.literal(label),ignored -> action(action,close)).bounds(x,y,w,20).build());
    }
    @Override protected void init() {
        int left=width/2-144,top=Math.max(55,height/2-55);
        button(left,top,140,"Copy aimed block","copy",false);
        button(left+148,top,140,"Stamp onto aimed block","stamp",true);
        String[] axes={"x","y","z"};
        for(int i=0;i<3;i++) {
            button(left+i*98,top+28,92,"Rotate "+axes[i].toUpperCase()+" 90","rotate_"+axes[i],false);
            button(left+i*98,top+54,92,"Mirror "+axes[i].toUpperCase(),"mirror_"+axes[i],false);
        }
        button(left,top+82,140,"Get sculpture item","export",false);
        addRenderableWidget(Button.builder(Component.literal("Done"),ignored -> onClose()).bounds(left+148,top+82,140,20).build());
    }
    @Override public void tick() { if(testCommands==null && !ChiselInspector.holdingChisel(minecraft)) onClose(); }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta) {
        graphics.fill(0,0,width,height,0xD0081120);
        graphics.centeredText(font,title,width/2,15,0xFF6CE6FA);
        var design=sample!=null?java.util.Optional.of(sample):ChiselInspector.holdingChisel(minecraft)?SculptureData.read(minecraft.player.getMainHandItem(),SculptureData.CLIPBOARD_KEY):java.util.Optional.<MicroblockVolume>empty();
        graphics.centeredText(font,design.map(SculptureData::summary).orElse("Aim at a sculpture, then Copy"),width/2,33,0xFFAA89FF);
        super.extractRenderState(graphics,mouseX,mouseY,delta);
        if(design.isPresent()) {
            var stack=previewItem(design.get());
            graphics.pose().pushMatrix();
            graphics.pose().translate(width/2f-16,Math.max(55,height/2-55)+110).scale(2f);
            graphics.item(stack,0,0);
            graphics.pose().popMatrix();
        }
        graphics.centeredText(font,"Stamp replaces the target shape / one undo step",width/2,height-24,0xFFBDD2E9);
        graphics.centeredText(font,"Copied designs stay on this chisel / item export is Creative",width/2,height-12,0xFFBDD2E9);
    }
    /** A render-only stack also works in the standalone UI gate before world components are bound. */
    static net.minecraft.world.item.ItemStack previewItem(MicroblockVolume volume) {
        var block=volume.original()==HostMaterial.OAK_PLANKS?AstraMicroblocks.OAK_HOST:AstraMicroblocks.TEST_HOST;
        var components=net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.ITEM_MODEL,
                        AstraMicroblocks.id(volume.original()==HostMaterial.OAK_PLANKS?"oak_host":"test_host")).build();
        var stack=new net.minecraft.world.item.ItemStack(new net.minecraft.core.Holder.Direct<>(block.asItem(),components),1);
        SculptureData.write(stack,SculptureData.ITEM_KEY,volume);
        return stack;
    }
    @Override public boolean isPauseScreen() { return false; }
}
