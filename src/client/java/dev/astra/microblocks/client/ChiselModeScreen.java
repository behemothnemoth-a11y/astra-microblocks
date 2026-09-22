package dev.astra.microblocks.client;

import dev.astra.microblocks.ChiselMode;
import dev.astra.microblocks.ChiselOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Choosing settings and history actions is always validated by the server. */
public final class ChiselModeScreen extends Screen {
    private final ChiselMode current;
    private final ChiselOperation operation;

    public ChiselModeScreen(ChiselMode current) { this(current,ChiselOperation.CUT); }
    public ChiselModeScreen(ChiselMode current, ChiselOperation operation) {
        super(Component.literal("Astra Chisel"));
        this.current = current;
        this.operation = operation;
    }

    private void send(String command) {
        if (minecraft.getConnection() != null && ChiselInspector.holdingChisel(minecraft))
            minecraft.getConnection().sendCommand(command);
        onClose();
    }

    @Override protected void init() {
        int left = width/2-152, top = height/2-70;
        for (var op : ChiselOperation.values()) {
            var button = Button.builder(Component.literal(op.label()), ignored -> send("astra operation " + op.id()))
                    .bounds(left+op.ordinal()*156,top,148,20).build();
            button.active = op != operation;
            addRenderableWidget(button);
        }
        for (ChiselMode mode : ChiselMode.values()) {
            int index = mode.ordinal();
            var button = Button.builder(Component.literal(mode.label()), ignored -> send("astra mode " + mode.id()))
                    .bounds(left+(index%2)*156,top+28+(index/2)*24,148,20).build();
            button.active = mode != current;
            addRenderableWidget(button);
        }
        addRenderableWidget(Button.builder(Component.literal("Undo last edit"), ignored -> send("astra undo"))
                .bounds(left,top+128,148,20).build());
        addRenderableWidget(Button.builder(Component.literal("Redo last edit"), ignored -> send("astra redo"))
                .bounds(left+156,top+128,148,20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), ignored -> onClose())
                .bounds(width/2-74,top+152,148,20).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractTransparentBackground(graphics);
        graphics.centeredText(font,title,width/2,height/2-99,0xFFFFFFFF);
        graphics.centeredText(font,operation.label()+" / "+current.label(),width/2,height/2-85,0xFFFFC04D);
        super.extractRenderState(graphics,mouseX,mouseY,delta);
    }
    @Override public boolean isPauseScreen() { return false; }
}
