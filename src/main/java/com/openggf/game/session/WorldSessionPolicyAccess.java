package com.openggf.game.session;

import com.openggf.game.LevelLoadCause;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.sprites.managers.SpriteManager;

/** Internal access to the policy owner pinned by WorldSession, without exposing provider state on its API. */
public final class WorldSessionPolicyAccess {
    private WorldSessionPolicyAccess() { }

    public static boolean hasPolicies(WorldSession world) {
        return world != null && world.policies != null;
    }

    public static <T> T getService(WorldSession world, Class<T> type) {
        return hasPolicies(world) ? world.policies.getService(type) : null;
    }

    public static void registerRewind(WorldSession world, RewindRegistry registry) {
        if (hasPolicies(world)) registry.register(world.policies.rewindAdapter());
    }

    public static void beforeSpecialStageForwardTick(WorldSession world) {
        if (hasPolicies(world)) world.policies.beforeSpecialStageForwardTick();
    }

    public static void bindGameplay(GameplayModeContext context) {
        if(context!=null && hasPolicies(context.getWorldSession()))context.getWorldSession().policies.bindGameplay(context);
    }
    public static void bindRoster(WorldSession world, SpriteManager sprites) {
        if (hasPolicies(world)) world.policies.bindRoster(sprites);
    }

    public static void beforeAssembly(WorldSession world, LevelLoadCause cause) {
        if (hasPolicies(world)) {
            if(cause!=LevelLoadCause.DECODE_ONLY && cause!=LevelLoadCause.PREVIEW)world.policyHistory.clear();
            world.policies.beforeAssembly(cause);
        }
    }

    public static void failedAssembly(WorldSession world, LevelLoadCause cause) {
        if (hasPolicies(world)) world.policies.failedAssembly(cause);
    }

    public static void closeScreens(WorldSession world) {
        if (hasPolicies(world)) world.policies.closeScreens();
    }

    public static void onPresentation(GameplayModeContext context, com.openggf.control.LogicalInputSnapshot input,
                                      com.openggf.game.GameplayObservation.Phase phase) {
        if(context!=null && hasPolicies(context.getWorldSession())) context.getWorldSession().policies.onPresentation(context,input,phase);
    }
    public static void onMenuPresentation(WorldSession world,com.openggf.control.LogicalInputSnapshot input,int width,int height,
            java.util.function.Consumer<com.openggf.audio.GameSound> sounds) {
        if(hasPolicies(world))world.policies.onMenuPresentation(input,width,height,sounds);
    }
    public static void leaveOrdinaryGameplay(WorldSession world,com.openggf.game.GameplayObservation.Phase phase) {
        if(hasPolicies(world)) {
            world.policyHistory.beforeBoundary(world,phase);
            world.policies.leaveOrdinaryGameplay(phase);
        }
    }
    public static void drawOverlay(WorldSession world, com.openggf.mods.ui.PixelCanvas canvas) {
        if(hasPolicies(world))world.policies.drawOverlay(canvas);
    }
    public static boolean holdsGameplay(WorldSession world) { return hasPolicies(world)&&world.policies.holdsGameplay(); }
    public static boolean replaying(WorldSession world) { return hasPolicies(world)&&world.policyHistory.replaying(); }
    public static void beforeNativeTick(com.openggf.LevelFrameContext context) {
        var world=context.levelManager()==null?null:context.levelManager().getWorldSession();
        if(!hasPolicies(world))return;
        var input=context.spriteManager()==null?com.openggf.control.LogicalInputSnapshot.neutral():
                com.openggf.sprites.managers.SpriteManagerInternalAccess.publishedInput(context.spriteManager());
        if(!world.policyHistory.replaying())world.policies.observeTick(context,input);
        world.policyHistory.recordBefore(world);
        world.policies.beforeNativeTick(context,input);
    }
    public static void afterNativeTick(com.openggf.LevelFrameContext context) {
        var world=context.levelManager()==null?null:context.levelManager().getWorldSession();
        if(hasPolicies(world))world.policies.afterNativeTick(context,context.spriteManager()==null?
                com.openggf.control.LogicalInputSnapshot.neutral():
                com.openggf.sprites.managers.SpriteManagerInternalAccess.publishedInput(context.spriteManager()));
    }
    /** Opaque accepted-outcome history used only by live rewind, never trace comparisons. */
    public static Object captureHistory(WorldSession world) { return hasPolicies(world)?world.policyHistory.frame(world):null; }
    public static void restoreHistoryBefore(WorldSession world,Object frame) {
        if(hasPolicies(world)&&frame!=null)world.policyHistory.restoreBefore(world,(WorldPolicyHistory.Frame)frame);
    }
    public static void restoreHistoryAfter(WorldSession world,Object frame) {
        if(hasPolicies(world)&&frame!=null)world.policyHistory.restoreAfter(world,(WorldPolicyHistory.Frame)frame);
    }
    public static void endHistoryReplay(WorldSession world) { if(hasPolicies(world))world.policyHistory.endReplay(); }

    static void retire(WorldSession world) {
        if (hasPolicies(world)) world.policies.retire();
    }
}
