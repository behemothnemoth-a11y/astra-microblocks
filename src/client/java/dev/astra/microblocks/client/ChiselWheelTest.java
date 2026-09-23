package dev.astra.microblocks.client;

import dev.astra.microblocks.ChiselMode;
import dev.astra.microblocks.ChiselOperation;
import java.util.ArrayList;
import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.lwjgl.glfw.GLFW;

/** Click the actual controls, including angular seams, native keyboard input, and server acknowledgements. */
final class ChiselWheelTest {
    static void run(Minecraft client) {
        for (int[] size : new int[][] {{320,240},{427,240},{640,360},{854,480}}) {
            var commands = new ArrayList<String>();
            int[] closed = {0};
            var screen = new ChiselModeScreen(ChiselMode.SINGLE,ChiselOperation.CUT,commands::add,() -> closed[0]++);
            screen.init(size[0],size[1]);
            var wheel = screen.wheel();
            var pixels = new HashSet<Long>();
            for (int index=0;index<8;index++) {
                for (var span : wheel.spans(index)) for (int px=span.x();px<span.x()+span.width();px++) {
                    check(pixels.add(((long)span.y()<<32)|(px & 0xffffffffL)),"radial wedges overlap");
                    check(wheel.sectorAt(px+0.5,span.y()+0.5)==index,"paint and hit-test disagree");
                }
                double boundary=index*Math.PI/4+Math.PI/8;
                double r=(wheel.inner+wheel.radius)/2.0;
                double gapX=wheel.x+Math.sin(boundary)*r, gapY=wheel.y-Math.cos(boundary)*r;
                check(wheel.sectorAt(gapX,gapY)==-1,"angular seam selected a brush");
                int before=commands.size();
                screen.mouseClicked(click(gapX,gapY,0),false);
                check(commands.size()==before,"seam dispatched a command");
                var mode=ChiselMode.values()[index];
                screen.updateSelection(ChiselMode.values()[(index+1)%8],ChiselOperation.CUT);
                double cx=wheel.labelX(index)+0.5, cy=wheel.labelY(index)+0.5;
                check(screen.mouseClicked(click(cx,cy,0),false),"wheel click not handled");
                check(commands.size()==before+1 && commands.getLast().equals("astra mode "+mode.id()),"wrong brush command");
                check(closed[0]==0,"choosing a brush closed the wheel");
                check(widget(screen,mode.label()).active,"client optimistically changed authoritative selection");
                screen.updateSelection(mode,ChiselOperation.CUT);
                check(!widget(screen,mode.label()).active,"server selection not reflected");
                before=commands.size();
                screen.mouseClicked(click(cx,cy,0),false);
                screen.mouseClicked(click(cx,cy,1),false);
                screen.extractRenderState(new GuiGraphicsExtractor(client,new GuiRenderState(),(int)cx,(int)cy),(int)cx,(int)cy,0);
                check(commands.size()==before,"selected brush, right click or hover sent a command");
            }
            check(wheel.sectorAt(wheel.x,wheel.y)==-1,"wheel center selects brush");
            int before=commands.size();
            screen.mouseClicked(click(wheel.x,wheel.y,0),false);
            screen.mouseClicked(click(size[0]-1,size[1]-1,0),false);
            check(commands.size()==before,"empty menu space sends a command");
            for (var op : ChiselOperation.values()) {
                screen.updateSelection(ChiselMode.PLANE,op==ChiselOperation.CUT?ChiselOperation.ADD:ChiselOperation.CUT);
                press(screen,op.label());
                check(commands.getLast().equals("astra operation "+op.id()) && closed[0]==0,"operation action");
                screen.updateSelection(ChiselMode.PLANE,op);
            }
            // Tab cycles through native focusable widgets; Enter activates the focused history button.
            var visited=new HashSet<String>();
            for (int i=0;i<30;i++) {
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_TAB,0,0));
                if (screen.getFocused() instanceof AbstractWidget widget) visited.add(widget.getMessage().getString());
            }
            check(visited.size()==11 && visited.contains("Undo") && visited.contains("Redo") && visited.contains("Done"),
                    "keyboard cannot reach all active controls: "+visited);
            var redo=widget(screen,"Redo");
            screen.setFocused(redo);
            check(screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER,0,0)),"keyboard redo not handled");
            check(commands.getLast().equals("astra redo") && closed[0]==1,"keyboard redo action");
            press(screen,"Undo");
            check(commands.getLast().equals("astra undo") && closed[0]==2,"undo action");
            before=commands.size(); press(screen,"Done");
            check(closed[0]==3 && commands.size()==before,"Done edited the world");
            screen.init(size[0],size[1]);
            check(screen.children().size()==13,"resize duplicated controls");
            check(!screen.isPauseScreen(),"menu pauses singleplayer");
        }
        System.out.println("ASTRA_TEST: CHISEL_WHEEL_PASS");
    }
    private static MouseButtonEvent click(double x,double y,int button) {
        return new MouseButtonEvent(x,y,new MouseButtonInfo(button,0));
    }
    private static AbstractWidget widget(ChiselModeScreen screen,String label) {
        return screen.children().stream().map(c -> (AbstractWidget)c)
                .filter(w -> w.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
    private static void press(ChiselModeScreen screen,String label) {
        var widget=widget(screen,label);
        check(screen.mouseClicked(click(widget.getX()+widget.getWidth()/2.0,widget.getY()+widget.getHeight()/2.0,0),false),
                "button not clickable: "+label);
    }
    private static void check(boolean condition,String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
