package starpost.realtown;

import com.openggf.level.objects.ObjectServices;
import starpost.valley.Ground;

/** Reads the act's decoded collision profiles through the public level API, never scene art. */
public final class ActGround implements Ground {
    private final ObjectServices services;
    private final int width;
    private final int originY;
    public ActGround(ObjectServices services, int width, int originY) {
        this.services=services; this.width=width; this.originY=originY;
    }
    public int originY() { return originY; }
    public int left() { return 0; }
    public int right() { return width; }
    public boolean solid(int x,int y) {
        if (x<0||x>=width||y<0||y>=4096) return false;
        var level=services.levelManager();
        var desc=level.getChunkDescAt((byte)0,x,y);
        if (desc == null || !desc.isSolidityBitSet(12)) return false;
        var tile=level.getSolidTileForChunkDesc(desc,0x0C,false);
        if (tile==null) return false;
        int column=x&15, row=y&15;
        if (desc.getHFlip()) column=15-column;
        if (desc.getVFlip()) row=15-row;
        // Decoded signed heights: positive bottom-solid, negative top-solid. The player's
        // physics still uses its ordinary native sensors; this is only a NPC placement query.
        int height=tile.getHeightAt((byte)column);
        return height>0?row>=16-height:height<0&&row<-height;
    }
    public int floorBelow(int x,int fromY) {
        for (int y=Math.max(0,fromY);y<4096;y++) if (solid(x,y)) return y;
        return 4096;
    }
}
