package com.openggf.graphics;


/** Engine-only geometry and private art bridge. The bank handle is bound to the actual render host and owner. */
public final class PresentationGraphicsAccess {
    private PresentationGraphicsAccess() { }
    private static void engineCaller() {
        Class<?> caller = com.openggf.util.EngineCallerAccess.callerOutside(PresentationGraphicsAccess.class);
        if(caller.getClassLoader()!=PresentationGraphicsAccess.class.getClassLoader())
            throw new SecurityException("Presentation bridge belongs to the engine renderer");
    }
    public static final class TrailBank {
        private final GraphicsManager graphics;
        private final Object owner;
        private final int base;
        private final long generation;
        private boolean released;
        private TrailBank(GraphicsManager graphics,Object owner,int base) {
            this.graphics=graphics;this.owner=owner;this.base=base;generation=graphics.presentationTrailGeneration(owner);
        }
    }
    public static TrailBank acquire(GraphicsManager graphics,Object owner) {
        engineCaller();return new TrailBank(graphics,owner,graphics.presentationTrailBank(owner));
    }
    public static int base(TrailBank bank,GraphicsManager graphics,Object owner) {
        engineCaller();
        if(!valid(bank,graphics,owner))
            throw new IllegalArgumentException("Trail bank belongs to another owner or render host");
        return bank.base;
    }
    public static boolean valid(TrailBank bank,GraphicsManager graphics,Object owner) {
        engineCaller();return bank!=null&&!bank.released&&bank.graphics==graphics&&bank.owner==owner
                &&graphics.presentationTrailBankValid(owner,bank.base,bank.generation);
    }
    public static void release(TrailBank bank,Object owner) {
        engineCaller();if(bank==null)return;
        if(bank.owner!=owner)throw new IllegalArgumentException("Trail bank belongs to another owner");
        if(!bank.released) {
            if(bank.graphics.presentationTrailBankValid(owner,bank.base,bank.generation))bank.graphics.releasePresentationTrailBank(owner);
            bank.released=true;
        }
    }
    public static void restoreFrozenPattern(GraphicsManager graphics, SpritePresentation.PatternVersion version, int id) {
        engineCaller(); graphics.restoreFrozenPattern(version, id);
    }
    public static boolean maskIsPerInstance(GraphicsManager graphics) {
        engineCaller(); return graphics.presentationMaskIsPerInstance();
    }

    /** Bounds of the supported current orthographic projection, in logical top-left pixels. */
    public record Viewport(float left, float top, float right, float bottom) {
        public boolean outside(float left, float top, float right, float bottom) {
            // NaNs fail open; retain boundary-touching quads conservatively.
            return right < this.left || left > this.right || bottom < this.top || top > this.bottom;
        }
        public boolean outside(float[] quad, int cameraX, int cameraY) {
            float left = Float.POSITIVE_INFINITY, top = left;
            float right = Float.NEGATIVE_INFINITY, bottom = right;
            for (int i = 0; i < 8; i += 2) {
                if (!Float.isFinite(quad[i]) || !Float.isFinite(quad[i + 1])) return false;
                left = Math.min(left, quad[i] - cameraX); right = Math.max(right, quad[i] - cameraX);
                top = Math.min(top, quad[i + 1] - cameraY); bottom = Math.max(bottom, quad[i + 1] - cameraY);
            }
            return outside(left, top, right, bottom);
        }
    }
    public static Viewport viewport(GraphicsManager graphics) {
        engineCaller();
        if (SpritePresentation.isPreparing(graphics)) return null;
        float[] m = graphics.getProjectionMatrixBuffer();
        if (m == null || m.length != 16 || m[0] == 0 || m[5] == 0 || m[15] != 1) return null;
        for (int i = 0; i < 16; i++) {
            if (!Float.isFinite(m[i])) return null;
            if (i != 0 && i != 5 && i != 10 && i != 12 && i != 13 && i != 14 && i != 15 && m[i] != 0) return null;
        }
        float x0 = (-1 - m[12]) / m[0], x1 = (1 - m[12]) / m[0];
        float y0 = (-1 - m[13]) / m[5], y1 = (1 - m[13]) / m[5];
        var projection = graphics.getProjectionSource();
        int height = projection != null && projection.isFBOProjectionActive()
                ? projection.getCurrentDisplayHeight() : graphics.getConfiguredScreenHeightPx();
        return new Viewport(Math.min(x0, x1), height - Math.max(y0, y1),
                Math.max(x0, x1), height - Math.min(y0, y1));
    }

    public static void render(GraphicsManager graphics, int id, int palette, boolean hFlip, boolean vFlip,
                              boolean priority, int rowStart, int rowEnd, float[] quad, float hue, float opacity) {
        engineCaller();
        graphics.renderPresentationPattern(id, palette, hFlip, vFlip, priority, rowStart, rowEnd, quad, hue, opacity);
    }
}
