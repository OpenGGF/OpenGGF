package example.platformer;

import com.openggf.audio.StreamedMusicPort.SfxRef;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectReconstructionContext;
import com.openggf.level.objects.ModRewindRecreatable;
import com.openggf.level.objects.SpringBounceHelper;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Authored 32x16 proximity contact; native launch/detachment/control-lock state uses the public helper.
 * Stock springs retain their checkpoint geometry, position correction and ROM animation owners. */
public final class SpringPad extends AbstractObjectInstance implements ModRewindRecreatable {
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
        if (contact != null && SpringBounceHelper.tryLaunchUp(contact, spawn.x(), spawn.y(),
                HALF_WIDTH, HALF_HEIGHT, SpringBounceHelper.STRENGTH_YELLOW, SpringBounceHelper.CONTROL_LOCK_FRAMES)) {
            services().playSfx(new SfxRef(OWNER, "spring"));
            extendedFramesRemaining = EXTENDED_FRAMES;
        } else if (extendedFramesRemaining > 0) {
            extendedFramesRemaining--;
        }
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer("sample-platformer:springpad");
        if (renderer != null) {
            renderer.drawFrameIndex(extendedFramesRemaining > 0 ? 1 : 0, spawn.x(), spawn.y(), false, false);
        }
    }

    @Override public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext ctx) {
        return new SpringPad(ctx.spawn());
    }
}
