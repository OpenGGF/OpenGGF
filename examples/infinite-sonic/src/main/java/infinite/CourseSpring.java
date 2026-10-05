package infinite;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;

/**
 * Red launch spring drawn with the ROM spring art (Map_Spring, palette line 0). It launches
 * Sonic with the stock red power, -$1000, over a spring chasm. Unlike the solid stock spring
 * (Obj41), which fires only when stood on and blocks Sonic from the side, this one fires
 * whenever Sonic passes over it on the ground or low in a jump, so a jump started at the
 * pit's edge cannot miss it and running into it never stalls him against the scrolling edge.
 */
public final class CourseSpring extends AbstractObjectInstance implements RewindRecreatable {
    // Spring_Powers: red -$1000.
    static final int STRENGTH = -0x1000;
    // Mod design: at least 4px per frame (the minimum scroll) carries Sonic over the widest
    // 448px chasm from the spring (146 frames of flight before he falls back to bank height).
    static final int MIN_LAUNCH_SPEED = 0x400;
    // Mod design: fires from 16px behind its centre to 24px past it (more than Sonic's top
    // per-tick movement), up to 128px above the plate (a held jump peaks near 96px).
    static final int REACH_BEHIND = 16;
    static final int REACH_AHEAD = 24;
    static final int REACH_ABOVE = 128;
    // Spring_BounceUp: move.b #id_Spring,obAnim(a1); spring control time $F (SpringBounceHelper).
    private static final int ANIM_SPRING = 0x10;
    private static final int CONTROL_FRAMES = 15;
    private static final int COOLDOWN = 30;
    // S1 sfx_Spring ($CC).
    private static final int SFX_SPRING = 0xcc;
    // Ani_Spring .up: speed 0, frames 1, 0, 0, 2, 2, 2, 2, 2, 2, then back to the idle frame 0.
    private static final String TRIGGERED = "100222222";
    private int x;
    private int y;
    private int animation = -1;
    // Re-arms after the flight has carried Sonic out of reach, so a CONTINUE before the chasm still launches.
    private int cooldown;
    private int launches;

    public CourseSpring(ObjectSpawn spawn) {
        super(spawn, "Course launch spring");
        x = spawn.x();
        y = spawn.y();
    }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    public int launches() { return launches; }
    @Override public boolean isPersistent() { return !isDestroyed(); }
    @Override public boolean participatesInLevelRepeatOffset() { return true; }
    @Override public void applyLevelRepeatOffset(int dx, int dy) {
        x += dx;
        y += dy;
        updateDynamicSpawn(x, y);
    }
    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new CourseSpring(context.spawn());
    }
    @Override public void update(int vIntRunCount, PlayableEntity entity) {
        if (animation >= 0 && ++animation >= TRIGGERED.length()) animation = -1;
        if (cooldown > 0) cooldown--;
        var camera = services().camera();
        if (x < camera.getX() - 256 || x > camera.getX() + camera.getWidth() + 384) {
            setDestroyed(true);
            return;
        }
        if (cooldown > 0 || !(entity instanceof AbstractPlayableSprite player) || player.getDead()
                || player.isDebugMode() || player.isCpuControlled()) return;
        int bottom = player.getCentreY() + player.getYRadius();
        int plate = y - SpringPlan.FLOOR_OFFSET;
        if (player.getCentreX() < x - REACH_BEHIND || player.getCentreX() > x + REACH_AHEAD
                || bottom < plate - REACH_ABOVE || bottom > y + SpringPlan.FLOOR_OFFSET + 4) return;
        launch(player);
    }

    private void launch(AbstractPlayableSprite player) {
        launches++;
        cooldown = COOLDOWN;
        animation = 0;
        player.setYSpeed((short) STRENGTH);
        if (player.getXSpeed() < MIN_LAUNCH_SPEED) player.setXSpeed((short) MIN_LAUNCH_SPEED);
        if (player.getGSpeed() < MIN_LAUNCH_SPEED) player.setGSpeed((short) MIN_LAUNCH_SPEED);
        player.setAir(true);
        player.setOnObject(false);
        // A launch from a jump must not be cut short by releasing the button (Sonic_JumpHeight
        // only caps rising speed while the jump flag is set); a stock spring always fires from
        // the ground, where that flag is already clear.
        player.setJumping(false);
        player.setSpringing(CONTROL_FRAMES);
        player.setAnimationId(ANIM_SPRING);
        // Spring_BounceUp forces Sonic's routine back to control, ending a hurt state.
        player.setHurt(false);
        services().playSfx(SFX_SPRING);
    }

    @Override public int getPriorityBucket() {
        return RenderPriority.bucket(4); // Spring_Main obPriority 4.
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) return;
        var renderer = getRenderer(ObjectArtKeys.SPRING_VERTICAL);
        if (renderer == null) return;
        renderer.drawFrameIndex(animation >= 0 ? TRIGGERED.charAt(animation) - '0' : 0, x, y, false, false);
    }
}
