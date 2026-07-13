package example.flappysample;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Static pipe-pair obstacle. Contact/scoring is owned by {@link FlappyController}, which
 * scans the object manager for live {@code FlappyPipe} instances and reads their plain
 * contact-geometry accessors -- this object never touches the player itself and implements
 * no touch/solid marker interface.
 *
 * <p>Gap geometry is derived entirely from the immutable spawn ({@code spawn.x()} and
 * {@code spawn.subtype()} cycling 0-4), so {@code gapCenter} is safe to keep {@code final}:
 * {@link #recreateForRewind} re-derives it from the (unchanged) spawn rather than needing to
 * capture/restore it across a rewind. {@code @RewindTransient} tells the generic capturer (and
 * {@code ModValidator}'s FINAL_SCALAR_REWIND_GAP check) that this final scalar needs no explicit
 * capture/restore -- recreation always reproduces it deterministically from the spawn.
 */
public final class FlappyPipe extends AbstractObjectInstance implements RewindRecreatable {
    @RewindTransient(reason = "derived deterministically from spawn.subtype(); recreateForRewind "
            + "re-derives it from the unchanged spawn, so no capture/restore is needed")
    private final int gapCenter;

    public FlappyPipe(ObjectSpawn spawn) {
        super(spawn, "sample-flappy:pipe");
        this.gapCenter = 64 + (spawn.subtype() % 5) * 24;
    }

    public int leftEdge()  { return spawn.x() - 16; }
    public int rightEdge() { return spawn.x() + 16; }
    public int gapTop()    { return gapCenter - 48; }
    public int gapBottom() { return gapCenter + 48; }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        // Static obstacle; contact and scoring are owned by FlappyController.
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer("sample-flappy:pipe");
        if (renderer == null) return;
        int x = spawn.x();
        // Top pipe stack: tile the 32x32 body frame downward from the level top band
        // (y=16) up to the gap, then cap it with the lip frame flipped so its flange
        // faces down into the gap.
        for (int y = 16; y + 32 <= gapTop(); y += 32) {
            renderer.drawFrameIndex(0, x, y, false, false);
        }
        renderer.drawFrameIndex(1, x, gapTop() - 16, false, true);
        // Bottom pipe stack: lip frame at the gap (flange faces up into the gap), then
        // tile the body frame downward to the level floor band (y=240).
        renderer.drawFrameIndex(1, x, gapBottom(), false, false);
        for (int y = gapBottom() + 16; y < 240; y += 32) {
            renderer.drawFrameIndex(0, x, y, false, false);
        }
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new FlappyPipe(ctx.spawn());
    }
}
