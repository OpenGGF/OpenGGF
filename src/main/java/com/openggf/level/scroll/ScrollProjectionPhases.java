package com.openggf.level.scroll;

/** Explicit held event-plane phase, for native event owners that write no camera-derived output. */
public final class ScrollProjectionPhases {
    private ScrollProjectionPhases() {}

    public static ScrollProjectionPhase held(ScrollProjectionContext context) {
        return view -> {
            ScrollProjection nativeOutput = context.nativeOutput();
            int[] original = nativeOutput.horizontal(), words = new int[view.height()];
            int dx = view.cameraX() - context.cameraX(), dy = view.cameraY() - context.cameraY();
            for (int i = 0; i < words.length; i++) {
                int value = original[Math.min(i, original.length - 1)];
                words[i] = ((short) ((short) (value >>> 16) - dx) << 16) | (value & 0xffff);
            }
            return new ScrollProjection(
                    words,
                    nativeOutput.backgroundLines(),
                    nativeOutput.backgroundColumns(),
                    nativeOutput.foregroundColumns(),
                    (short) (nativeOutput.foregroundY() + dy),
                    nativeOutput.backgroundY(),
                    nativeOutput.backgroundX(),
                    nativeOutput.backgroundPeriod(),
                    nativeOutput.shakeX(),
                    nativeOutput.shakeY(),
                    nativeOutput.tilemapMode());
        };
    }
}
