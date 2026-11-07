package com.openggf.level.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Scoped CPU observation of mapping-frame requests; never submits graphics work. */
public final class SpriteFrameCaptureInternalAccess {
    private static final ThreadLocal<List<Call>> CURRENT = new ThreadLocal<>();
    private SpriteFrameCaptureInternalAccess() { }

    public sealed interface Call permits FrameCall, Untranscribable { }
    public record FrameCall(PatternSpriteRenderer renderer,
                            SpriteSheet<? extends SpriteFrame<? extends SpriteFramePiece>> sheet,
                            int frameIndex, int originX, int originY, boolean hFlip,
                            boolean vFlip, int paletteOverride) implements Call { }
    public record Untranscribable(String method) implements Call { }
    public record Capture(List<Call> calls) {
        public Capture { calls = List.copyOf(calls); }
    }

    public static Capture capture(Runnable draw) {
        Objects.requireNonNull(draw);
        if (CURRENT.get() != null) throw new IllegalStateException("Nested sprite frame capture");
        List<Call> calls = new ArrayList<>();
        CURRENT.set(calls);
        try {
            com.openggf.graphics.SpriteCaptureControl.observe(
                    detail -> calls.add(new Untranscribable(detail)), draw);
            return new Capture(calls);
        } finally {
            CURRENT.remove();
        }
    }

    static boolean frame(PatternSpriteRenderer renderer,
                         SpriteSheet<? extends SpriteFrame<? extends SpriteFramePiece>> sheet,
                         int frame, int x, int y, boolean hFlip, boolean vFlip, int palette) {
        var calls = CURRENT.get();
        if (calls == null) return false;
        calls.add(new FrameCall(renderer, sheet, frame, x, y, hFlip, vFlip, palette));
        return true;
    }

    static boolean unsupported(String method) {
        var calls = CURRENT.get();
        if (calls == null) return false;
        calls.add(new Untranscribable(method));
        return true;
    }
}
