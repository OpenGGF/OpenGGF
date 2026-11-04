package com.openggf.level.objects;

/**
 * Runs one object's share of the end-of-frame BuildSprites / Render_Sprites pass
 * ({@link ObjectManager#refreshPostCameraRenderState()}) against the cached camera
 * bounds, for unit tests that drive an object without an {@link ObjectManager}.
 */
public final class RenderFlagPassTestSupport {
    private RenderFlagPassTestSupport() {
    }

    /**
     * @param heightPixelsTest true for S3K Render_Sprites ({@code height_pixels});
     *                         false for S1/S2 BuildSprites (approximate 32-px band)
     */
    public static void pass(AbstractObjectInstance object, boolean heightPixelsTest) {
        object.captureRenderFlagOnScreen(heightPixelsTest);
    }
}
