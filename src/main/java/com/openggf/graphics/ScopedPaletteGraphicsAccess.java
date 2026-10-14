package com.openggf.graphics;

import com.openggf.level.Palette;

/** Engine-only, exact-owner palette handles. The numeric bank is derived GPU state and is never captured as gameplay state. */
public final class ScopedPaletteGraphicsAccess {
    private ScopedPaletteGraphicsAccess() { }
    private static void engineCaller() {
        var caller=com.openggf.util.EngineCallerAccess.callerOutside(ScopedPaletteGraphicsAccess.class);
        if(caller.getClassLoader()!=ScopedPaletteGraphicsAccess.class.getClassLoader())
            throw new SecurityException("Scoped palette admission belongs to the engine renderer");
    }
    public static final class Bank {
        private final GraphicsManager graphics;private final Object owner;private final int base;private final long generation;
        private boolean released;
        private Bank(GraphicsManager graphics,Object owner,int base) {
            this.graphics=graphics;this.owner=owner;this.base=base;generation=graphics.scopedPaletteGeneration(owner);
        }
    }
    public static Bank acquire(GraphicsManager graphics,Object owner,Palette[] palettes) {
        engineCaller();return new Bank(graphics,owner,graphics.scopedPaletteBank(owner,palettes));
    }
    public static boolean valid(Bank bank,GraphicsManager graphics,Object owner) {
        engineCaller();return bank!=null&&!bank.released&&bank.graphics==graphics&&bank.owner==owner
                &&graphics.scopedPaletteBankValid(owner,bank.base,bank.generation);
    }
    public static int base(Bank bank,GraphicsManager graphics,Object owner) {
        engineCaller();if(!valid(bank,graphics,owner))throw new IllegalArgumentException("Palette bank belongs to another owner, generation or render host");
        return bank.base;
    }
    public static void release(Bank bank,Object owner) {
        engineCaller();if(bank==null)return;
        if(bank.owner!=owner)throw new IllegalArgumentException("Palette bank belongs to another owner");
        if(!bank.released) {
            if(valid(bank,bank.graphics,owner))bank.graphics.releaseScopedPaletteBank(owner);
            bank.released=true;
        }
    }
    public static void bindWorld(GraphicsManager graphics,Object world) {
        engineCaller();graphics.bindScopedPaletteWorld(world);
    }
    public static void leaveAssembly(GraphicsManager graphics,Object world) {
        engineCaller();if(graphics!=null)graphics.clearScopedUnderwaterBasis(world);
    }
}
