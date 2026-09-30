package dev.astra.microblocks;

/** Vanilla light is block-level: any remaining luminous cell retains source brightness. */
public final class MicroblockLight {
    private MicroblockLight() {}
    public static int emission(MicroblockVolume volume) {
        int result=0;
        for(var material:volume.materials().keySet()) {
            if(!material.isColor()) result=Math.max(result,material.state().getLightEmission());
            if(result==15) break;
        }
        return result;
    }
}

