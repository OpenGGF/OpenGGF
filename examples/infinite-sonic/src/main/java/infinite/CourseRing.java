package infinite;

import com.openggf.audio.GameSound;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;

/** Procedural pickup using the ordinary ROM ring renderer, award and touch contracts. */
public final class CourseRing extends AbstractObjectInstance
        implements ModRewindRecreatable, TouchResponseProvider, TouchResponseListener {
    private int x;
    private int y;
    private int ticks;
    private int sparkleAge = -1;

    public CourseRing(ObjectSpawn spawn) {
        super(spawn, "Course ring");
        x = spawn.x();
        y = spawn.y();
    }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    public boolean isCollected() { return sparkleAge >= 0; }
    @Override public boolean isPersistent() { return !isDestroyed(); }
    @Override public boolean participatesInLevelRepeatOffset() { return true; }
    @Override public void applyLevelRepeatOffset(int dx, int dy) {
        x += dx;
        y += dy;
        updateDynamicSpawn(x, y);
    }
    @Override public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) {
        return new CourseRing(context.spawn());
    }
    @Override public void update(int vIntRunCount, PlayableEntity player) {
        ticks++;
        if (isCollected()) {
            var rings = services().ringManager();
            int duration = rings == null ? 24
                    : rings.getSparkleFrameCount() * rings.getSparkleFrameDelay();
            if (++sparkleAge >= duration) setDestroyed(true);
            return;
        }
        var camera = services().camera();
        if (x < camera.getX() - 256 || x > camera.getX() + camera.getWidth() + 384) {
            setDestroyed(true);
        }
    }
    @Override public int getCollisionFlags() {
        // S1 Ring_Main: col_item | col_12x16 = $47.
        return isCollected() || isDestroyed() ? 0 : 0x47;
    }
    @Override public int getCollisionProperty() { return 0; }
    @Override public boolean requiresContinuousTouchCallbacks() { return true; }
    @Override public void onTouchResponse(PlayableEntity entity, TouchResponseResult result, int frameCounter) {
        if (isCollected() || isDestroyed() || result.category() != TouchCategory.SPECIAL
                || !(entity instanceof AbstractPlayableSprite player) || player.getDead()
                || player.isDebugMode() || player.isTouchResponseSuppressedByObjectControl()) return;
        // S1 ReactToItem col_item checks flashtime >= 90 before advancing Ring_Collect.
        if (player.getInvulnerableFrames() >= 90) return;
        sparkleAge = 0;
        services().audioManager().playSecondarySfx(GameSound.RING);
        // Reuse the HUD, hurt-protection and extra-life award path. Shipped FixBugs=0
        // CollectRing does not apply the fixed branch's 999-ring cap.
        player.addRings(1);
    }
    @Override public int getPriorityBucket() {
        // S1 Ring_Main priority 2; Ring_Collect switches sparkles to priority 1.
        return RenderPriority.bucket(isCollected() ? 1 : 2);
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) return;
        var rings = services().ringManager();
        if (rings == null) return;
        if (isCollected()) {
            rings.drawSparkleAt(x, y, sparkleAge / Math.max(1, rings.getSparkleFrameDelay()));
        } else {
            rings.drawRingAt(x, y, ticks);
        }
    }
}
