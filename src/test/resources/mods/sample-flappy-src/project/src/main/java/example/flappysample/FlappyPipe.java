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
        // Both piece definitions in pipe-sheet.yaml center-anchor on the (x, y) passed to
        // drawFrameIndex (xOffset/yOffset == -halfWidth/-halfHeight, the same convention
        // sample-sheet.yaml uses), so a tile drawn at y covers [y-halfHeight, y+halfHeight).
        // Anchor each stack's lip flush against its gap boundary first, then tile the body
        // frame outward from the lip so consecutive 32px tiles are contiguous by
        // construction; the outermost tile is allowed to overdraw past the level's top/
        // floor band, which is harmless offscreen overdraw.
        //
        // Top pipe stack: lip at the gap edge (flange faces down into the gap), then body
        // tiles walking upward past y=0.
        renderer.drawFrameIndex(1, x, gapTop(), false, true);
        for (int y = gapTop() - 16; y > -16; y -= 32) {
            renderer.drawFrameIndex(0, x, y, false, false);
        }
        // Bottom pipe stack: lip at the gap edge (flange faces up into the gap), then body
        // tiles walking downward past y=240. The bound is the tile's leading (top) edge
        // (y - 16 < 240), not its center (y < 240) -- using the center under-covers the
        // floor by up to 16px whenever gapBottom() isn't a multiple of 32 away from 240.
        renderer.drawFrameIndex(1, x, gapBottom(), false, false);
        for (int y = gapBottom() + 16; y - 16 < 240; y += 32) {
            renderer.drawFrameIndex(0, x, y, false, false);
        }
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new FlappyPipe(ctx.spawn());
    }
}
