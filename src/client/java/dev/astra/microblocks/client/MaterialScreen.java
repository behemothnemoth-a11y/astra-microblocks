package dev.astra.microblocks.client;

import dev.astra.microblocks.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Search and recent selections operate on the same validated catalog as the server. */
public final class MaterialScreen extends Screen {
    private final Consumer<String> transport;
    private final List<HostMaterial> testRecent;
    private final List<MaterialButton> choices=new ArrayList<>();
    private EditBox search;
    private List<HostMaterial> matches=List.of();
    private Button previous,next,recentButton;
    private boolean recentOnly;
    private int page;
    public MaterialScreen() {this(null,List.of());}
    MaterialScreen(Consumer<String> transport,List<HostMaterial> recent) {super(Component.literal("ASTRA MATERIAL LIBRARY"));this.transport=transport;testRecent=recent;}
    static List<HostMaterial> filtered(String query,List<HostMaterial> source) {
        String text=query.toLowerCase(Locale.ROOT).trim();
        return source.stream().filter(m -> m.id().contains(text.replace(' ','_')) || m.label().toLowerCase(Locale.ROOT).contains(text)).toList();
    }
    private void send(String command) {
        if(transport!=null) transport.accept(command);
        else if(minecraft.getConnection()!=null && ChiselInspector.holdingChisel(minecraft)) minecraft.getConnection().sendCommand(command);
    }
    @Override protected void init() {
        String oldQuery=search==null?"":search.getValue();
        choices.clear();int left=width/2-150;
        search=new EditBox(font,left,34,300,20,Component.literal("Search materials"));
        search.setMaxLength(100);search.setValue(oldQuery);addRenderableWidget(search);
        for(int i=0;i<10;i++) {
            final int index=i;
            var button=new MaterialButton(left+(i%2)*153,62+(i/2)*23,index);
            choices.add(button);addRenderableWidget(button);
        }
        previous=addRenderableWidget(Button.builder(Component.literal("<"),ignored -> {page--;refresh();}).bounds(left,181,28,20).build());
        next=addRenderableWidget(Button.builder(Component.literal(">"),ignored -> {page++;refresh();}).bounds(left+272,181,28,20).build());
        recentButton=addRenderableWidget(Button.builder(Component.literal("Recent"),ignored -> {recentOnly=!recentOnly;page=0;refresh();}).bounds(left+34,181,82,20).build());
        addRenderableWidget(Button.builder(Component.literal("Original"),ignored -> send("astra material original")).bounds(left+122,181,80,20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"),ignored -> onClose()).bounds(left+208,181,58,20).build());
        search.setResponder(value -> {page=0;refresh();});refresh();setInitialFocus(search);
    }
    private List<HostMaterial> recent() {
        return transport!=null?testRecent:ChiselInspector.holdingChisel(minecraft)?ChiselMaterial.recent(minecraft.player.getMainHandItem()):List.of();
    }
    void refresh() {
        matches=filtered(search.getValue(),recentOnly?recent():HostMaterial.catalog());
        page=Math.max(0,Math.min(page,Math.max(0,(matches.size()-1)/10)));
        for(int i=0;i<choices.size();i++) {
            int index=page*10+i;var button=choices.get(i);button.visible=index<matches.size();
            if(button.visible) {
                var material=matches.get(index);String label=material.label();
                button.material=material;button.setMessage(Component.literal(font.plainSubstrByWidth(label,120)));
                button.setTooltip(Tooltip.create(Component.literal(material.label()+"\n"+material.id())));
            }
        }
        previous.active=page>0;next.active=(page+1)*10<matches.size();recentButton.setMessage(Component.literal(recentOnly?"All blocks":"Recent"));
    }
    void query(String text) {search.setValue(text);}
    private final class MaterialButton extends Button {
        HostMaterial material,rendered;
        net.minecraft.world.item.ItemStack icon;
        MaterialButton(int x,int y,int index) {
            super(x,y,147,20,Component.empty(),ignored -> {
                int selected=page*10+index;if(selected<matches.size()) send("astra material "+matches.get(selected).id());
            },DEFAULT_NARRATION);
        }
        @Override protected void extractContents(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta) {
            graphics.fill(getX(),getY(),getRight(),getBottom(),isHoveredOrFocused()?0xFF6CE6FA:0xFF405774);
            graphics.fill(getX()+1,getY()+1,getRight()-1,getBottom()-1,0xFF142238);
            if(material!=rendered) {icon=DesignScreen.previewItem(new MicroblockVolume(material));rendered=material;}
            graphics.item(icon,getX()+2,getY()+2);
            graphics.text(font,getMessage(),getX()+22,getY()+6,0xFFE1EDF5);
        }
    }
    @Override public void tick() {
        if(transport==null && !ChiselInspector.holdingChisel(minecraft)) onClose();
        else if(recentOnly) refresh();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta) {
        graphics.fill(0,0,width,height,0xE0081120);graphics.centeredText(font,title,width/2,10,0xFF6CE6FA);
        super.extractRenderState(graphics,mouseX,mouseY,delta);
        graphics.centeredText(font,matches.size()+" states / Page "+(page+1)+" of "+Math.max(1,(matches.size()+9)/10),width/2,207,0xFFAA89FF);
        String selected=transport!=null?"Select a material, then Add or Replace":ChiselInspector.holdingChisel(minecraft)?"Selected: "+ChiselMaterial.label(minecraft.player.getMainHandItem()):"Hold the chisel";
        graphics.centeredText(font,selected,width/2,221,0xFFBDD2E9);
    }
    @Override public boolean isPauseScreen() {return false;}
}
