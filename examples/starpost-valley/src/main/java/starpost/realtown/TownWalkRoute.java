package starpost.realtown;

import starpost.valley.Ground;

/** Log footpath above cliffs and gaps, with steps at most eight pixels high.
 * The same immutable route locates villagers and real solid deck objects. */
public final class TownWalkRoute {
    public static final int STEP=16;
    private final int[] height;
    private final Ground ground;
    public TownWalkRoute(Ground ground) {
        this.ground=ground; height=new int[ground.right()/STEP];
        for(int i=0;i<height.length;i++) {
            int floor=ground.floorBelow(i*STEP+STEP/2,ground.originY());
            height[i]=floor>=ground.originY()+512?ground.originY()+192:floor;
        }
        // An elevated continuous footpath follows the lower envelope of the terrain surface.
        for(int i=1;i<height.length;i++) height[i]=Math.min(height[i],height[i-1]+8);
        for(int i=height.length-2;i>=0;i--) height[i]=Math.min(height[i],height[i+1]+8);
    }
    public int floor(int x) { return height[Math.max(0,Math.min(height.length-1,x/STEP))]; }
    public boolean deck(int i) { return height[i]<ground.floorBelow(i*STEP+STEP/2,ground.originY())-2; }
    public int size() { return height.length; }
    public int x(int i) { return i*STEP+STEP/2; }
}
