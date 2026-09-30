package dev.astra.microblocks.client;

import dev.astra.microblocks.*;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Local preview only: moving a slider never sends edits or allocates sculpture meshes. */
public final class ColorScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> transport;
    private int rgb;
    private EditBox hex;
    private Button apply;
    private final Channel[] channels=new Channel[3];
    private boolean updating;
    ColorScreen(Screen parent,Consumer<String> transport,int rgb) {
        super(Component.literal("ASTRA COLORS"));this.parent=parent;this.transport=transport;this.rgb=rgb&0xffffff;
    }
    static Integer parseHex(String text) {
        String value=text.startsWith("#")?text.substring(1):text;
        return value.matches("[0-9a-fA-F]{6}")?Integer.parseInt(value,16):null;
    }
    private String hexValue() {return String.format(Locale.ROOT,"#%06X",rgb);}
    @Override protected void init() {
        int left=width/2-150;
        for(int i=0;i<3;i++) {channels[i]=new Channel(left,42+i*27,i);addRenderableWidget(channels[i]);}
        hex=new EditBox(font,left+202,113,98,20,Component.literal("Hex color"));
        hex.setMaxLength(7);hex.setValue(hexValue());addRenderableWidget(hex);
        apply=addRenderableWidget(Button.builder(Component.literal("Apply color"),ignored -> applyColor()).bounds(left,166,96,20).build());
        var getBlock=addRenderableWidget(Button.builder(Component.literal("Get block"),ignored -> {
            if(!apply.active) return;
            String command="astra colorblock "+String.format(Locale.ROOT,"%06x",rgb);
            if(transport!=null) transport.accept(command);
            else if(ChiselInspector.holdingChisel(minecraft) && minecraft.getConnection()!=null) minecraft.getConnection().sendCommand(command);
        }).bounds(left+102,166,96,20).build());
        getBlock.active=transport!=null || minecraft.player!=null && minecraft.player.isCreative();
        getBlock.setTooltip(Tooltip.create(Component.literal("Creative: get a full block in this color")));
        addRenderableWidget(Button.builder(Component.literal("Back"),ignored -> onClose()).bounds(left+204,166,96,20).build());
        hex.setResponder(text -> {
            if(updating) return;
            var value=parseHex(text);apply.active=value!=null;
            if(value!=null) {rgb=value;for(var channel:channels) channel.refresh();}
        });
    }
    void setHex(String text) {hex.setValue(text);}
    int color() {return rgb;}
    void applyColor() {
        if(!apply.active) return;
        String command="astra material "+HostMaterial.color(rgb).id();
        if(transport!=null) transport.accept(command);
        else if(ChiselInspector.holdingChisel(minecraft) && minecraft.getConnection()!=null) minecraft.getConnection().sendCommand(command);
    }
    private final class Channel extends AbstractSliderButton {
        private final int channel;
        Channel(int x,int y,int channel) {super(x,y,190,20,Component.empty(),((rgb>>((2-channel)*8))&255)/255.0);this.channel=channel;updateMessage();}
        void refresh() {value=((rgb>>((2-channel)*8))&255)/255.0;updateMessage();}
        @Override protected void updateMessage() {setMessage(Component.literal(new String[]{"Red","Green","Blue"}[channel]+": "+Math.round(value*255)));}
        @Override protected void applyValue() {
            int shift=(2-channel)*8;rgb=(rgb&~(255<<shift))|((int)Math.round(value*255)<<shift);
            if(hex!=null) {updating=true;hex.setValue(hexValue());updating=false;apply.active=true;}
        }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta) {
        int left=width/2-150;
        graphics.fill(0,0,width,height,0xEE081120);
        graphics.centeredText(font,title,width/2,12,0xFF6CE6FA);
        graphics.fill(left+200,40,left+300,104,0xFFE1EDF5);
        graphics.fill(left+202,42,left+298,102,0xff000000|rgb);
        graphics.text(font,"Hex color",left+202,104,0xFFBDD2E9);
        super.extractRenderState(graphics,mouseX,mouseY,delta);
        graphics.centeredText(font,apply.active?hexValue():"Enter six hex digits: #RRGGBB",width/2,145,apply.active?0xFFBDD2E9:0xFFFF9988);
        graphics.centeredText(font,"Apply, then Add/Replace. P samples colors.",width/2,199,0xFFBDD2E9);
        graphics.centeredText(font,"Flat texture; world lighting still shades the surface.",width/2,213,0xFF8FA7BC);
    }
    @Override public void tick() {if(transport==null && !ChiselInspector.holdingChisel(minecraft)) onClose();}
    @Override public void onClose() {minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen() {return false;}
}
