package starfall;

import java.util.Arrays;

/** Presentation only: retarget from the current mix, and advance once per playing update. */
final class BackdropBlend {
    static final int DURATION=45;
    private final double[] weights=new double[Biome.values().length*2];
    private double[] start=weights.clone();
    private int target=-1,elapsed=DURATION;
    static int key(World world) {
        Biome biome=world.region();
        return biome.ordinal()*2+biome.act(world,(int)(world.x/World.T),(int)(world.y/World.T));
    }
    void snap(World world) {snap(key(world));}
    void snap(int key) {Arrays.fill(weights,0);weights[key]=1;target=key;elapsed=DURATION;}
    void update(World world) {update(key(world));}
    void update(int key) {
        if(target<0){snap(key);return;}
        if(key!=target){start=weights.clone();target=key;elapsed=0;}
        if(elapsed==DURATION)return;
        double t=++elapsed/(double)DURATION;t=t*t*(3-2*t);
        for(int n=0;n<weights.length;n++)weights[n]=start[n]*(1-t)+(n==target?t:0);
    }
    double weight(int key){return weights[key];}
}
