package com.openggf.level.render;

import java.util.List;
import java.util.function.Consumer;

/** Engine-internal, scoped observation of native mapping draws. Never executes object routines. */
public final class ObjectSpriteCapture {
    private static final ThreadLocal<Consumer<Draw>> CURRENT = new ThreadLocal<>();

    private ObjectSpriteCapture() {}

    public record Draw(
            PatternSpriteRenderer renderer,
            SpriteSheet<? extends SpriteFrame<? extends SpriteFramePiece>> sheet,
            List<? extends SpriteFramePiece> pieces,
            int x,
            int y,
            boolean flipX,
            boolean flipY,
            int palette,
            Boolean priorityFilter,
            Boolean forcedPriority,
            int occlusionMask,
            com.openggf.graphics.SpritePresentation.Subject subject,
            Runnable nativeDraw) {
        public int defaultPose() {
            for (int i = 0; i < sheet.getFrameCount(); i++) {
                if (sheet.getFrame(i).pieces().equals(pieces)) {
                    return i;
                }
            }
            return -1;
        }

        public void drawPose(int pose) {
            if (pose < 0) {
                nativeDraw.run();
            } else if (priorityFilter != null) {
                renderer.drawFrameIndexFilteredByPriority(
                        pose, x, y, flipX, flipY, palette, priorityFilter);
            } else if (forcedPriority != null) {
                renderer.drawFrameIndexForcedPriority(
                        pose, x, y, flipX, flipY, palette, forcedPriority);
            } else {
                renderer.drawFrameIndex(pose, x, y, flipX, flipY, palette);
            }
        }
    }

    public static boolean isObserving() {
        return CURRENT.get() != null;
    }

    /** Notify before SAT-only early guards, so CPU visual capture cannot silently drop controls. */
    public static void control(String description) {
        com.openggf.graphics.SpriteCaptureControl.control(description);
    }

    public static void observe(Consumer<Draw> observer, Runnable draw) {
        observe(
                observer,
                detail -> {
                    throw new UnsupportedOperationException(detail);
                },
                draw);
    }

    public static void observe(Consumer<Draw> observer, Consumer<String> control, Runnable draw) {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Nested object visual capture");
        }
        CURRENT.set(observer);
        try {
            com.openggf.graphics.SpriteCaptureControl.observe(control, draw);
        } finally {
            CURRENT.remove();
        }
    }

    static boolean intercept(
            PatternSpriteRenderer renderer,
            SpriteSheet<? extends SpriteFrame<? extends SpriteFramePiece>> sheet,
            List<? extends SpriteFramePiece> pieces,
            int x,
            int y,
            boolean flipX,
            boolean flipY,
            int palette,
            Boolean priorityFilter,
            Boolean forcedPriority,
            int occlusionMask,
            com.openggf.graphics.SpritePresentation.Subject subject,
            Runnable nativeDraw) {
        var observer = CURRENT.get();
        if (observer == null) {
            return false;
        }
        CURRENT.remove();
        try {
            observer.accept(
                    new Draw(
                            renderer,
                            sheet,
                            List.copyOf(pieces),
                            x,
                            y,
                            flipX,
                            flipY,
                            palette,
                            priorityFilter,
                            forcedPriority,
                            occlusionMask,
                            subject,
                            nativeDraw));
        } finally {
            CURRENT.set(observer);
        }
        return true;
    }
}
