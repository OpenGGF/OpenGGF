package com.openggf.game.session;

/** Supplies only the immutable JDK caller-inspection service; no runtime or creator identity is exposed. */
public final class EngineCallerInspectionBootstrap {
    private EngineCallerInspectionBootstrap() { }

    public static StackWalker create() {
        return EngineContext.createCallerInspection();
    }
}
