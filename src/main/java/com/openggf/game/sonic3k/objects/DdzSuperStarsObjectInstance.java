package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;

/** loc_8242A/82452: the fixed Super_stars slot used by DDZ's Super branch. */
public final class DdzSuperStarsObjectInstance extends AbstractDdzObjectInstance {
    private boolean initialized;
    private boolean drawing;
    private int x;
    private int y;
    private int frame = 6;
    private int timer;

    public DdzSuperStarsObjectInstance(ObjectSpawn spawn) { super(spawn, "DDZSuperStars", null); }

    @Override public DdzSuperStarsObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new DdzSuperStarsObjectInstance(context.spawn());
    }

    @Override protected void updateObject(int vIntRunCount, PlayableEntity entity) {
        drawing = false;
        if (!initialized) {
            // loc_8242A queues the 26 ROM tiles and returns without drawing.
            // The dedicated ROM-backed sheet represents that private shield-art upload.
            initialized = true;
            return;
        }
        if (!(entity instanceof AbstractPlayableSprite player) || !player.isSuperSonic()) return;
        // A zero Super flag returns without deleting the fixed slot or advancing it.
        x = (x - DdzObjectSupport.wrapOffset(services()) - 8) & 0xFFFF;
        timer = (byte) (timer - 1);
        if (timer < 0) {
            timer = 1;
            if (++frame >= 6) {
                frame = 0;
                x = player.getCentreX() & 0xFFFF;
                y = player.getCentreY() & 0xFFFF;
            }
        }
        drawing = true;
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isHighPriority() { return true; } // ObjDat3_8321A art bit15
    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(0x80); }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawing || !drawable()) return;
        var renderer = getRenderer(Sonic3kObjectArtKeys.DDZ_SUPER_STARS);
        if (renderer != null) renderer.drawFrameIndex(frame, x, y, false, false);
    }
    int mappingFrame() { return frame; }
    boolean isDrawing() { return drawing; }
}
