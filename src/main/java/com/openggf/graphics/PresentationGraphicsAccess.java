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
    public static void render(GraphicsManager graphics, int id, int palette, boolean hFlip, boolean vFlip,
                              boolean priority, int rowStart, int rowEnd, float[] quad, float hue, float opacity) {
        engineCaller();
        graphics.renderPresentationPattern(id, palette, hFlip, vFlip, priority, rowStart, rowEnd, quad, hue, opacity);
    }
}
