package example.platformer;

import com.openggf.audio.StreamedMusicPort.SfxRef;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SpringBounceHelper;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Spring gimmick, registered with {@code art/springpad.ggfs}. Mod API 0.7 does not publish
 * any solid-object marker interface ({@code SolidObjectProvider} and friends are engine-internal),
 * so this cannot ride the stock {@code SolidObject}/checkpoint-contact pipeline the way
 * {@code com.openggf.game.sonic2.objects.SpringObjectInstance} does. Instead it does simple
 * proximity + velocity detection each frame: is the main player's centre inside the pad's
 * 32x16 rect, and are they moving downward ({@link PlayableEntity#getYSpeed()} {@code > 0})?
 * If so, launch them with {@link SpringBounceHelper#STRENGTH_YELLOW} (already negative =
 * upward -- do not negate it) and mirror the published subset of what
 * {@code SpringObjectInstance#applyUpSpring} does ({@code setAir(true)}, {@code setOnObject(false)}).
 * {@link SpringBounceHelper#CONTROL_LOCK_FRAMES} cannot be mirrored: the input-lock setter
 * ({@code AbstractPlayableSprite#setSpringing}) is not part of the published {@code PlayableEntity}
 * surface, so this gimmick does not lock player input after launch (a known, documented gap
 * versus a ROM-accurate spring).
 */
public final class SpringPad extends AbstractObjectInstance implements RewindRecreatable {
    private static final String OWNER = "sample-platformer";

    /** Half-extents of the pad's 32x16 contact rect (matches springpad-sheet.yaml's piece size). */
    private static final int HALF_WIDTH = 16;
    private static final int HALF_HEIGHT = 8;

    /** Frames the extended (triggered) sprite frame stays shown after a launch. */
    private static final int EXTENDED_FRAMES = 8;

    /** Non-final: rewind must round-trip this so a seek mid-extended-pose restores the pose. */
    private int extendedFramesRemaining;

    public SpringPad(ObjectSpawn spawn) { super(spawn, "sample-platformer:springpad"); }

    @Override public void update(int frameCounter, PlayableEntity player) {
        PlayableEntity contact = services().playerQuery().mainPlayerOrNull();
        if (contact != null && contact.getYSpeed() > 0 && isWithinPad(contact)) {
            // STRENGTH_YELLOW is already negative (= upward); do not negate it.
            contact.setYSpeed((short) SpringBounceHelper.STRENGTH_YELLOW);
            contact.setAir(true);
            contact.setOnObject(false);
            services().playSfx(new SfxRef(OWNER, "spring"));
            extendedFramesRemaining = EXTENDED_FRAMES;
        } else if (extendedFramesRemaining > 0) {
            extendedFramesRemaining--;
        }
    }

    private boolean isWithinPad(PlayableEntity player) {
        int springX = spawn.x();
        int springY = spawn.y();
        int playerX = player.getCentreX();
        int playerY = player.getCentreY();
        return playerX >= springX - HALF_WIDTH && playerX <= springX + HALF_WIDTH
                && playerY >= springY - HALF_HEIGHT && playerY <= springY + HALF_HEIGHT;
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer("sample-platformer:springpad");
        if (renderer != null) {
            renderer.drawFrameIndex(extendedFramesRemaining > 0 ? 1 : 0, spawn.x(), spawn.y(), false, false);
        }
    }

    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new SpringPad(ctx.spawn());
    }
}
