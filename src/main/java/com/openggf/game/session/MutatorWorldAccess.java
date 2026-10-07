package com.openggf.game.session;

import com.openggf.game.LevelLoadCause;
import com.openggf.mods.mutators.MutatorSessionState;

/** Engine-only bridge. Creators cannot construct or publish trusted policy owners. */
public final class MutatorWorldAccess {
    private MutatorWorldAccess() { }
    public static MutatorSessionState state(WorldSession world) {
        return world == null || world.mutators == null ? null : world.mutators.state;
    }
    public static void bindRoster(WorldSession world, com.openggf.sprites.managers.SpriteManager sprites) {
        if (world.mutators != null) world.mutators.bind(sprites);
    }
    public static void beforeAssembly(WorldSession world, LevelLoadCause cause) {
        if (world.mutators != null) world.mutators.beforeAssembly(cause);
    }
    public static void failedAssembly(WorldSession world, LevelLoadCause cause) {
        if (world.mutators != null && cause != LevelLoadCause.DECODE_ONLY && cause != LevelLoadCause.PREVIEW)
            world.mutators.state.close();
    }
    public static String admissionError(WorldSession world) { return world.mutators == null ? "" : world.mutators.admissionError; }
    public static boolean save(WorldSession world) { return world.mutators == null || world.mutators.save(); }
    public static String saveError(WorldSession world) { return world.mutators == null ? "" : world.mutators.saveError; }
    public static MutatorSessionState.Admission prepareLaunch(WorldSession world) { return world.mutators.prepareLaunch(); }
    public static boolean supportedCell(WorldSession world) { return world.mutators != null && world.mutators.supportedCell(); }
    public static void ownScreen(WorldSession world, com.openggf.mods.mutators.MutatorConfigurationScreen screen) { world.mutators.ownScreen(screen); }
    public static void closeScreens(WorldSession world) { if (world != null && world.mutators != null) world.mutators.closeScreens(); }
    static void retire(WorldSession world) { if (world != null && world.mutators != null) { world.mutators.closeScreens(); world.mutators.state.close(); } }
}
