package com.openggf.game.animation.strategies;

import com.openggf.game.animation.ApplyStrategy;
import com.openggf.game.animation.ChannelContext;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.animation.AniPlcScriptState;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * {@link ApplyStrategy} bridge that reuses an existing {@link AniPlcScriptState}.
 *
 * <p>This lets legacy script-driven animated tiles participate in the runtime-owned
 * {@code AnimatedTileChannelGraph} without rewriting their frame-application logic.
 */
public final class ScriptFramesApplyStrategy implements ApplyStrategy {

    private final AniPlcScriptState script;
    private final Supplier<? extends GraphicsManager> graphics;

    /** Creates a strategy that resolves the current graphics owner once per application. */
    public ScriptFramesApplyStrategy(AniPlcScriptState script,
                                    Supplier<? extends GraphicsManager> graphics) {
        this.script = Objects.requireNonNull(script, "script");
        this.graphics = Objects.requireNonNull(graphics, "graphics");
    }

    @Override
    public void apply(ChannelContext context) {
        GraphicsManager currentGraphics = Objects.requireNonNull(graphics.get(), "graphics.get()");
        script.tick(context.level(), currentGraphics);
    }
}
