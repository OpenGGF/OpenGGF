package com.openggf.game.sonic3k.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PlaceholderObjectInstance;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Legacy render fallback for visible CNZ traversal objects when a concrete
 * implementation wants to reuse a ROM-backed sheet before providing bespoke
 * draw logic.
 *
 * <p>No current CNZ traversal implementation extends this class, but keeping
 * the fallback available avoids reintroducing placeholder visuals if a future
 * visible controller needs a temporary first-frame renderer.
 */
abstract class AbstractCnzTraversalVisibleStubInstance extends AbstractObjectInstance {

    private final String artKey;
    private PlaceholderObjectInstance placeholder;

    protected AbstractCnzTraversalVisibleStubInstance(ObjectSpawn spawn, String name, String artKey) {
        super(spawn, name);
        this.artKey = artKey;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager != null) {
            PatternSpriteRenderer renderer = renderManager.getRenderer(artKey);
            if (renderer != null && renderer.isReady()) {
                boolean hFlip = (spawn.renderFlags() & 0x01) != 0;
                boolean vFlip = (spawn.renderFlags() & 0x02) != 0;
                renderer.drawFrameIndex(initialFrameIndex(), spawn.x(), spawn.y(), hFlip, vFlip);
                return;
            }
        }

        if (placeholder == null) {
            placeholder = new PlaceholderObjectInstance(spawn, name);
        }
        placeholder.appendRenderCommands(commands);
    }

    /**
     * Returns the frame used by this fallback renderer.
     *
     * <p>The fallback path should still honor any ROM-defined initial mapping
     * frame or subtype-based starting frame.
     */
    protected int initialFrameIndex() {
        return 0;
    }
}
