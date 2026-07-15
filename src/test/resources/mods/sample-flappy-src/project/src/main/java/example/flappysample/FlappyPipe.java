package example.flappysample;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Recycling pipe-pair obstacle. Contact/scoring is owned by {@link FlappyController}, which
 * scans the object manager for live {@code FlappyPipe} instances and reads their plain
 * contact-geometry accessors -- this object never touches the player itself and implements
 * no touch/solid marker interface.
 *
 * <p>Centre X, subpixel remainder, gap variant, and gate state are deliberately non-final
 * scalars so the compact rewind schema restores a recycled entry exactly.
 */
public final class FlappyPipe extends AbstractObjectInstance implements RewindRecreatable {
    private int centreX;
    private int xSubpixelRemainder;
    private int gapVariant;
    private boolean gateConsumed;

    public FlappyPipe(ObjectSpawn spawn) {
        this(spawn, Math.floorMod(spawn.subtype(), 5));
    }

    public FlappyPipe(ObjectSpawn spawn, int gapVariant) {
        super(spawn, "sample-flappy:pipe");
        this.centreX = spawn.x();
        this.gapVariant = Math.floorMod(gapVariant, 5);
    }

    public int centreX() { return centreX; }
    public int gapVariant() { return gapVariant; }
    public boolean gateConsumed() { return gateConsumed; }
    public int leftEdge()  { return centreX - 16; }
    public int rightEdge() { return centreX + 16; }
    public int gapTop()    { return gapCenter() - 48; }
    public int gapBottom() { return gapCenter() + 48; }

    public void advance(int speed) {
        int accumulated = xSubpixelRemainder - speed;
        centreX += Math.floorDiv(accumulated, 0x100);
        xSubpixelRemainder = Math.floorMod(accumulated, 0x100);
        updateDynamicSpawn(centreX, getSpawn().y());
    }

    public void recycleAfter(int x, int nextVariant) {
        centreX = x;
        xSubpixelRemainder = 0;
        gapVariant = Math.floorMod(nextVariant, 5);
        gateConsumed = false;
        updateDynamicSpawn(centreX, getSpawn().y());
    }

    public void consumeGate() {
        gateConsumed = true;
    }

    @Override
    public boolean isPersistent() {
        // The controller owns the complete lifecycle of this fixed pool. Several entries
        // deliberately wait beyond the stock ROM unload window until recycling brings them
        // on-screen, so ObjectManager must not despawn them first.
        return true;
    }

    private int gapCenter() {
        return 64 + gapVariant * 24;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        // Controller-owned movement avoids double-stepping dynamic entries.
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer("sample-flappy:pipe");
        if (renderer == null) return;
        int x = centreX;
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
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new FlappyPipe(context.dynamicEntry().spawn());
    }
}
