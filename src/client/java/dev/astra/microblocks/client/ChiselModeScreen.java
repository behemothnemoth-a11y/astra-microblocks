package dev.astra.microblocks.client;

import dev.astra.microblocks.ChiselMode;
import dev.astra.microblocks.ChiselMaterial;
import dev.astra.microblocks.ChiselOperation;
import dev.astra.microblocks.HostMaterial;
import dev.astra.microblocks.TestHostBlockEntity;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.CustomData;

/** Holographic brush wheel. Selection is click-to-confirm and server-authoritative. */
public final class ChiselModeScreen extends Screen {
    static final int CYAN = 0xFF6CE6FA, VIOLET = 0xFFAA89FF;
    static final int AMBER = 0xFFFFBE57, GREEN = 0xFF68F2A0;
    private ChiselMode current;
    private ChiselOperation operation;
    private ChiselWheelLayout wheel;
    private ChiselMaterial material = ChiselMaterial.ORIGINAL;
    private HoloButton materialButton;
    private final Map<ChiselMode, WheelButton> modeButtons = new EnumMap<>(ChiselMode.class);
    private final Map<ChiselOperation, HoloButton> operationButtons = new EnumMap<>(ChiselOperation.class);
    private final Consumer<String> testCommands;
    private final Runnable testClose;

    public ChiselModeScreen(ChiselMode current) { this(current, ChiselOperation.CUT); }
    public ChiselModeScreen(ChiselMode current, ChiselOperation operation) {
        this(current, operation, null, null);
    }
    // Injected transport lets the runtime gate exercise actual mouse/keyboard controls without editing a world.
    ChiselModeScreen(ChiselMode current, ChiselOperation operation, Consumer<String> commands, Runnable close) {
        super(Component.literal("ASTRA CHISEL"));
        this.current = current;
        this.operation = operation;
        testCommands = commands;
        testClose = close;
    }

    private void send(String command, boolean close) {
        if (testCommands != null) testCommands.accept(command);
        else if (minecraft.getConnection() != null && ChiselInspector.holdingChisel(minecraft))
            minecraft.getConnection().sendCommand(command);
        if (close) closeMenu();
    }
    private void closeMenu() { if (testClose != null) testClose.run(); else onClose(); }

    @Override protected void init() {
        modeButtons.clear();
        operationButtons.clear();
        wheel = new ChiselWheelLayout(width, height);
        // Clockwise tab order follows the visible wheel.
        for (var mode : ChiselMode.values()) {
            var button = new WheelButton(mode);
            modeButtons.put(mode, button);
            addRenderableWidget(button);
        }
        for (var op : ChiselOperation.values()) {
            var button = new HoloButton(wheel.x - 27, wheel.y - 21 + op.ordinal() * 24,
                    54, 20, op.label(), op == ChiselOperation.CUT ? AMBER : GREEN,
                    ignored -> send("astra operation " + op.id(), false));
            operationButtons.put(op, button);
            addRenderableWidget(button);
        }
        addRenderableWidget(new HoloButton(wheel.sideX(), wheel.y - 18, 88, 22, "Undo", CYAN,
                ignored -> send("astra undo", true)));
        addRenderableWidget(new HoloButton(wheel.sideX(), wheel.y + 10, 88, 22, "Redo", VIOLET,
                ignored -> send("astra redo", true)));
        materialButton = addRenderableWidget(new HoloButton(wheel.sideX(), wheel.y + 38, 88, 22,
                "Fill: " + material.label(), GREEN, ignored -> send("astra material " + material.next().id(), false)));
        addRenderableWidget(new HoloButton(wheel.sideX(), wheel.y + 66, 88, 22, "Done", CYAN,
                ignored -> closeMenu()));
        updateSelection(current, operation);
        updateMaterial(testCommands == null && ChiselInspector.holdingChisel(minecraft)
                ? ChiselMaterial.read(minecraft.player.getMainHandItem()) : material);
    }

    @Override public void tick() {
        super.tick();
        if (testCommands != null) return;
        if (!ChiselInspector.holdingChisel(minecraft)) { onClose(); return; }
        var tool = minecraft.player.getMainHandItem();
        updateSelection(ChiselMode.read(tool), ChiselOperation.read(tool));
        updateMaterial(ChiselMaterial.read(tool));
    }

    void updateSelection(ChiselMode mode, ChiselOperation op) {
        current = mode;
        operation = op;
        modeButtons.forEach((value, button) -> { button.active = value != current; button.selected = value == current; });
        operationButtons.forEach((value, button) -> { button.active = value != operation; button.selected = value == operation; });
    }
    void updateMaterial(ChiselMaterial value) {
        material = value;
        materialButton.setMessage(Component.literal("Fill: " + (value == ChiselMaterial.OAK ? "Oak" : value.label())));
    }
    ChiselWheelLayout wheel() { return wheel; }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // A light tint preserves the sculpture rather than hiding it behind a solid dialog.
        graphics.fill(0, 0, width, height, 0x55050B18);
        graphics.centeredText(font, title, width / 2, 9, CYAN);
        graphics.centeredText(font, operation.label() + " / " + current.label(), width / 2, 22,
                operation == ChiselOperation.CUT ? AMBER : GREEN);
        for (int row = -wheel.inner; row <= wheel.inner; row++) {
            int half = (int) Math.sqrt(wheel.inner * wheel.inner - row * row);
            graphics.fill(wheel.x-half,wheel.y+row,wheel.x+half+1,wheel.y+row+1,0xDA091526);
        }
        int side = wheel.sideX() + 44;
        graphics.centeredText(font,"LAST EDIT",side,wheel.y-57,VIOLET);
        String[] history = historyReadout(minecraft);
        graphics.centeredText(font,history[0],side,wheel.y-44,0xFFE1EDF5);
        graphics.centeredText(font,history[1],side,wheel.y-32,0xFF9BB3CB);
        super.extractRenderState(graphics,mouseX,mouseY,delta);
        int hovered = wheel.sectorAt(mouseX,mouseY);
        String footer = materialButton.isMouseOver(mouseX,mouseY) ? "Fill empty cells: Original / Stone / Oak planks" : hovered >= 0 ? description(ChiselMode.values()[hovered]) : "Click to choose / Tab to navigate / Esc to close";
        graphics.centeredText(font,footer,width/2,height-14,0xFFBDD2E9);
    }

    static String[] historyReadout(Minecraft client) {
        if (client.level == null || !ChiselInspector.holdingChisel(client)) return new String[] {"No target", "Undo -- / Redo --"};
        var tool = client.player.getMainHandItem();
        var tag = tool.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if (!tag.getStringOr("astra_last_dimension", "").equals(client.level.dimension().identifier().toString()))
            return new String[] {"No target", "Undo -- / Redo --"};
        var pos = BlockPos.of(tag.getLongOr("astra_last_pos",0));
        if (!client.player.isWithinBlockInteractionRange(pos,0) || !client.level.hasChunkAt(pos))
            return new String[] {"Out of reach", "Move closer"};
        if (!(client.level.getBlockEntity(pos) instanceof TestHostBlockEntity host))
            return new String[] {"Target gone", "Edit a host"};
        if (host.revision() != tag.getLongOr("astra_last_revision",-1))
            return new String[] {"Target changed", "Edit a host"};
        return new String[] {host.materialLabel(), "U " + host.undoDepth() + " / R " + host.redoDepth()};
    }

    static String shortName(ChiselMode mode) {
        return switch (mode) {
            case SINGLE -> "Single"; case LINE_X -> "Line X"; case LINE_Y -> "Line Y";
            case LINE_Z -> "Line Z"; case PLANE -> "Plane"; case CUBE_2 -> "2x2x2";
            case CUBE_4 -> "4x4x4"; case CUBE_8 -> "8x8x8";
        };
    }
    private static String description(ChiselMode mode) {
        return switch (mode) {
            case SINGLE -> "Single cell / 1/16 of a block";
            case LINE_X -> "Line X / 16 cells east-west";
            case LINE_Y -> "Line Y / 16 cells vertically";
            case LINE_Z -> "Line Z / 16 cells north-south";
            case PLANE -> "Plane / one layer parallel to the clicked face";
            case CUBE_2 -> "2x2x2 / up to 8 cells";
            case CUBE_4 -> "4x4x4 / up to 64 cells";
            case CUBE_8 -> "8x8x8 / up to 512 cells";
        };
    }

    private class HoloButton extends Button {
        final int accent;
        boolean selected;
        HoloButton(int x,int y,int width,int height,String label,int accent,OnPress press) {
            super(x,y,width,height,Component.literal(label),press,DEFAULT_NARRATION);
            this.accent = accent;
        }
        @Override protected void extractContents(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta) {
            boolean lit = selected || isHoveredOrFocused();
            graphics.fill(getX(),getY(),getRight(),getBottom(),lit ? accent : 0xFF405774);
            graphics.fill(getX()+1,getY()+1,getRight()-1,getBottom()-1,lit ? 0xEF1C3047 : 0xDD091525);
            if (selected) graphics.fill(getX()+2,getY()+3,getX()+4,getBottom()-3,accent);
            graphics.centeredText(font,getMessage(),getX()+getWidth()/2,getY()+(getHeight()-8)/2,
                    lit ? accent : 0xFFE1EDF5);
        }
    }

    private final class WheelButton extends HoloButton {
        final ChiselMode mode;
        WheelButton(ChiselMode mode) {
            super(wheel.x-wheel.radius,wheel.y-wheel.radius,wheel.radius*2,wheel.radius*2,
                    mode.label(),CYAN,ignored -> send("astra mode " + mode.id(),false));
            this.mode = mode;
            // Tight bounds improve native directional keyboard navigation and narration focus.
            var spans = wheel.spans(mode.ordinal());
            int minX = spans.stream().mapToInt(ChiselWheelLayout.Span::x).min().orElseThrow();
            int maxX = spans.stream().mapToInt(s -> s.x()+s.width()).max().orElseThrow();
            int minY = spans.stream().mapToInt(ChiselWheelLayout.Span::y).min().orElseThrow();
            int maxY = spans.stream().mapToInt(ChiselWheelLayout.Span::y).max().orElseThrow()+1;
            setX(minX); setY(minY); setWidth(maxX-minX); setHeight(maxY-minY);
        }
        @Override public boolean isMouseOver(double x,double y) {
            return visible && wheel.sectorAt(x,y) == mode.ordinal();
        }
        @Override protected void extractContents(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta) {
            boolean hovered = isMouseOver(mouseX,mouseY) || isFocused();
            int color = selected ? (operation == ChiselOperation.CUT ? AMBER : GREEN) : hovered ? VIOLET : CYAN;
            for (var span : wheel.spans(mode.ordinal())) {
                graphics.fill(span.x(),span.y(),span.x()+span.width(),span.y()+1,
                        span.edge() ? color : selected ? 0xE02A3651 : hovered ? 0xDD252943 : 0xBD0A192B);
            }
            int cx=wheel.labelX(mode.ordinal()), cy=wheel.labelY(mode.ordinal());
            icon(graphics,cx,cy-8,mode,color);
            graphics.centeredText(font,shortName(mode),cx,cy+6,selected || hovered ? color : 0xFFE3EEF8);
        }
    }

    private static void icon(GuiGraphicsExtractor g,int x,int y,ChiselMode mode,int color) {
        int w = 16, h = 12;
        switch (mode) {
            case LINE_X -> { w=23; h=5; }
            case LINE_Y -> { w=5; h=19; }
            case LINE_Z -> {
                for (int i=-8;i<=8;i++) g.fill(x+i,y-i/2,x+i+2,y-i/2+4,color);
                return;
            }
            case PLANE -> { w=24; h=5; }
            case SINGLE -> { w=10; h=10; }
            default -> {}
        }
        int left=x-w/2, top=y-h/2;
        g.fill(left,top,left+w,top+h,color);
        g.fill(left+1,top+1,left+w-1,top+h-1,0xFF14283A);
        int divisions = switch (mode) { case CUBE_2 -> 2; case CUBE_4 -> 3; case CUBE_8 -> 4; default -> 1; };
        for (int i=1;i<divisions;i++) {
            g.fill(left+i*w/divisions,top,left+i*w/divisions+1,top+h,color);
            g.fill(left,top+i*h/divisions,left+w,top+i*h/divisions+1,color);
        }
        if (mode == ChiselMode.PLANE) for (int i=4;i<w;i+=4) g.fill(left+i,top,left+i+1,top+h,color);
    }
    @Override public boolean isPauseScreen() { return false; }
}
