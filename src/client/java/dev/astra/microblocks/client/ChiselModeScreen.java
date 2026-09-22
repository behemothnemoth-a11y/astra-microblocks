package dev.astra.microblocks.client;

import dev.astra.microblocks.ChiselMode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Direct mode selection; closing the menu does not make an edit. */
public final class ChiselModeScreen extends Screen {
    private final ChiselMode current;

    public ChiselModeScreen(ChiselMode current) {
        super(Component.literal("Astra Chisel — Cut mode"));
        this.current = current;
    }

    @Override protected void init() {
        int left = width/2 - 152;
        int top = height/2 - 48;
        for (ChiselMode mode : ChiselMode.values()) {
            int index = mode.ordinal();
            var button = Button.builder(Component.literal(mode.label()), ignored -> {
                if (minecraft.getConnection() != null && ChiselInspector.holdingChisel(minecraft))
                    minecraft.getConnection().sendCommand("astra mode " + mode.id());
                onClose();
            }).bounds(left + (index%2)*156, top + (index/2)*24, 148, 20).build();
            button.active = mode != current;
            addRenderableWidget(button);
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), ignored -> onClose())
                .bounds(width/2-74,top+110,148,20).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractTransparentBackground(graphics);
        graphics.centeredText(font, title, width/2, height/2-79, 0xFFFFFFFF);
        graphics.centeredText(font, "Current: " + current.label(), width/2, height/2-64, 0xFFFFC04D);
        super.extractRenderState(graphics,mouseX,mouseY,delta);
    }

    @Override public boolean isPauseScreen() { return false; }
}
