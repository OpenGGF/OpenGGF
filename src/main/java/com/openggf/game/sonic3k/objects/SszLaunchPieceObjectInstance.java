package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** loc_581F2/58234/582AC/58360 and their detached loc_583A6 pieces. */
public final class SszLaunchPieceObjectInstance extends AbstractObjectInstance implements RewindRecreatable {
    private final SubpixelMotion.State motion = new SubpixelMotion.State(0, 0, 0, 0, 0, 0);
    private int kind;
    private int frame;
    private boolean falling;
    private boolean detached;
    private boolean renderOnScreen;
    private boolean displayed;

    public SszLaunchPieceObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSZSpiralPiece");
        kind = spawn.subtype();
        motion.x = spawn.x(); motion.y = spawn.y();
        frame = switch (kind) { case 0 -> 6; case 1 -> 10; case 2 -> 11; default -> 9; };
    }
    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new SszLaunchPieceObjectInstance(context.spawn());
    }
    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (falling) { fall(); return; }
        if (player != null) {
            int stage = kind == 0 ? frame - 6 : frame - 11;
            int threshold = kind == 0 || kind == 2 ? new int[]{0x1C, 0x24, 0x2C}[stage] : 0x24;
            if (((motion.y - threshold) & 65535) >= (player.getCentreY() & 65535)) {
                if ((kind == 0 || kind == 2) && stage < 2) {
                    frame++;
                    int dx = (stage == 0 ? 0x40 : 0x20) * (kind == 0 ? -1 : 1);
                    int dy = stage == 0 ? 0x10 : 8;
                    spawnChild(() -> {
                        var child = new SszLaunchPieceObjectInstance(new ObjectSpawn(
                                (motion.x + dx) & 65535, (motion.y + dy) & 65535, 0, kind, 0, false, 0));
                        child.frame = kind == 0 ? 8 : 13;
                        child.falling = true;
                        child.detached = true;
                        child.renderOnScreen = true; // move.b #$84,render_flags(a1)
                        return child;
                    });
                } else {
                    falling = true;
                    fall();
                    return;
                }
            }
        }
        displayed = true;
    }
    private void fall() {
        if (!renderOnScreen) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        SubpixelMotion.objectFall(motion, 0x38);
        displayed = true;
    }
    @Override public void refreshPostCameraRenderState() {
        if (displayed) { displayed = false; renderOnScreen = isWithinRenderSpriteBounds(0, getOnScreenHalfHeight()); }
    }
    @Override public int getX() { return motion.x; }
    @Override public int getY() { return motion.y; }
    @Override public boolean isPersistent() { return true; }
    @Override public boolean isHighPriority() { return kind != 2; }
    @Override public int getPriorityBucket() { return kind == 2 ? 4 : kind == 1 ? 2 : 1; }
    @Override public int getOnScreenHalfWidth() { return 0; }
    @Override public int getOnScreenHalfHeight() { return detached ? 0x10 : kind == 0 || kind == 2 ? 0x18 : 0x1C; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var renderer = getRenderer(kind == 1 || kind == 3 ? Sonic3kObjectArtKeys.SSZ_LAUNCH_RAMP
                : Sonic3kObjectArtKeys.SSZ_CUTSCENE_BRIDGE);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(frame, motion.x, motion.y, false, false);
    }
}
