package dev.astra.microblocks;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/** Eight horizontal symmetries: R^rotation after an optional X reflection. */
public final class SculptureOrientation {
    private SculptureOrientation() {}
    public static int compose(int outer,int inner) {
        return Math.floorMod((outer&3)+((outer&4)==0?(inner&3):-(inner&3)),4) | ((outer^inner)&4);
    }
    public static int inverse(int value) {return (value&4)!=0?value:Math.floorMod(-value,4);}
    public static int rotation(Rotation rotation) {
        return switch(rotation) {case NONE -> 0;case CLOCKWISE_90 -> 1;case CLOCKWISE_180 -> 2;case COUNTERCLOCKWISE_90 -> 3;};
    }
    public static int mirror(Mirror mirror) {
        return switch(mirror) {case NONE -> 0;case FRONT_BACK -> 4;case LEFT_RIGHT -> 6;};
    }
    public static MicroblockVolume transform(MicroblockVolume source,int value) {
        var result=source;
        if((value&4)!=0) result=SculptureData.transform(result,Direction.Axis.X,true);
        for(int i=0;i<(value&3);i++) result=SculptureData.transform(result,Direction.Axis.Y,false);
        return result;
    }
}
