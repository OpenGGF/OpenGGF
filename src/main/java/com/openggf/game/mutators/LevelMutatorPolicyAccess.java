package com.openggf.game.mutators;

import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectServices;

/** Engine queries always use the injected world and admitted immutable policies. */
public final class LevelMutatorPolicyAccess {
    private LevelMutatorPolicyAccess() { }
    public static LevelMutatorPolicy policy(WorldSession world) {
        var source = WorldSessionPolicyAccess.getService(world, LevelMutatorPolicySource.class);
        return source == null ? LevelMutatorPolicy.STOCK : source.policy();
    }
    public static RingAcquisitionDomain domain(ObjectServices services) {
        var provider = services == null ? null : services.bonusStageProviderOrNull();
        var type = provider == null ? null : provider.getActiveType();
        return type != null && type != com.openggf.game.BonusStageType.NONE
                ? RingAcquisitionDomain.STAGE_INTERIOR : RingAcquisitionDomain.MAIN_LEVEL;
    }
    public static boolean ringsAllowed(WorldSession world, RingAcquisitionDomain domain) {
        return domain == RingAcquisitionDomain.STAGE_INTERIOR || !policy(world).noRings();
    }
    public static boolean ringsAllowed(ObjectServices services) {
        return services == null || ringsAllowed(services.worldSession(), domain(services));
    }
    public static boolean ringsAllowed(LevelManager manager) {
        return manager == null || manager.getObjectManager() == null
                || ringsAllowed(manager.getObjectManager().getObjectServices());
    }
    public static int mainLevelRingRestore(ObjectServices services, int count) {
        return services == null || ringsAllowed(services.worldSession(), RingAcquisitionDomain.MAIN_LEVEL) ? count : 0;
    }
    public static int mainLevelRingRestore(LevelManager manager, int count) {
        return manager == null || manager.getObjectManager() == null ? count
                : mainLevelRingRestore(manager.getObjectManager().getObjectServices(), count);
    }
    public static boolean entryAllowed(WorldSession world, StageEntryKind kind) {
        var policy = policy(world);
        return kind == StageEntryKind.SPECIAL ? !policy.noSpecialStages() : !policy.noBonusStages();
    }
    public static boolean entryAllowed(ObjectServices services, StageEntryKind kind) {
        return services == null || entryAllowed(services.worldSession(), kind);
    }
    public static LevelMutatorRuntime runtime(WorldSession world) {
        return WorldSessionPolicyAccess.getService(world, LevelMutatorRuntime.class);
    }
    public static long admit(ObjectServices services, StageEntryKind kind) {
        var runtime = services == null ? null : runtime(services.worldSession());
        return runtime != null ? runtime.tryAdmit(kind) : entryAllowed(services, kind) ? -1 : 0;
    }
    public static boolean publish(ObjectServices services, StageEntryKind kind, long permit, Runnable publication) {
        var runtime = services == null ? null : runtime(services.worldSession());
        if (runtime != null) return runtime.publish(kind, permit, publication);
        if (permit != -1) return false;
        publication.run(); return true;
    }
    public static void holdResultsPermit(ObjectServices services, long permit) {
        var runtime = services == null ? null : runtime(services.worldSession());
        if (runtime != null) runtime.holdResultsPermit(permit);
    }
    public static long resultsPermit(ObjectServices services) {
        var runtime = services == null ? null : runtime(services.worldSession());
        return runtime == null ? -1 : runtime.resultsPermit();
    }
    public static void discard(ObjectServices services, long permit) {
        var runtime = services == null ? null : runtime(services.worldSession());
        if (runtime != null) runtime.discard(permit);
    }
    public static boolean directEntryAllowed(WorldSession world, StageEntryKind kind) {
        var runtime = runtime(world);
        return runtime != null && runtime.isPublishing(kind) || entryAllowed(world, kind);
    }
}
