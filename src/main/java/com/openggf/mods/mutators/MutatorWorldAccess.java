package com.openggf.mods.mutators;

import com.openggf.game.LevelLoadCause;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyAccess;

/** Internal typed view of this world's host-prepared mutator policy owner. */
public final class MutatorWorldAccess {
    private MutatorWorldAccess() { }
    private static MutatorWorldState owner(WorldSession world) {
        return WorldSessionPolicyAccess.getService(world, MutatorWorldState.class);
    }
    public static MutatorSessionState state(WorldSession world) {
        var owner = owner(world);
        return owner == null ? null : owner.state;
    }
    public static void bindRoster(WorldSession world, com.openggf.sprites.managers.SpriteManager sprites) {
        WorldSessionPolicyAccess.bindRoster(world, sprites);
    }
    public static void beforeAssembly(WorldSession world, LevelLoadCause cause) {
        WorldSessionPolicyAccess.beforeAssembly(world, cause);
    }
    public static void failedAssembly(WorldSession world, LevelLoadCause cause) {
        WorldSessionPolicyAccess.failedAssembly(world, cause);
    }
    public static String admissionError(WorldSession world) {
        var owner = owner(world); return owner == null ? "" : owner.admissionError;
    }
    public static boolean save(WorldSession world) {
        var owner = owner(world); return owner == null || owner.save();
    }
    public static String saveError(WorldSession world) {
        var owner = owner(world); return owner == null ? "" : owner.saveError;
    }
    public static MutatorSessionState.Admission prepareLaunch(WorldSession world) { return owner(world).prepareLaunch(); }
    public static boolean supportedCell(WorldSession world) {
        var owner = owner(world); return owner != null && owner.supportedCell();
    }
    public static void ownScreen(WorldSession world, MutatorConfigurationScreen screen) { owner(world).ownScreen(screen); }
    public static void closeScreens(WorldSession world) { WorldSessionPolicyAccess.closeScreens(world); }
}
