package com.openggf.game.internal;

import com.openggf.game.BonusStageProvider;
import com.openggf.game.BonusStageBootstrapInstaller;
import com.openggf.game.BonusStageDebugControls;
import com.openggf.game.BonusStagePlayerPriorityPolicy;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.graphics.RenderPriority;
import com.openggf.game.BonusStageType;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.level.objects.ObjectManager;

/** Ordinary bonus-frame hooks shared by the host loop and recorded-input replay. */
public final class BonusStageFrameLifecycle {
    private BonusStageFrameLifecycle() { }

    public static void updateOverlay(TitleCardProvider title) {
        if (title != null && title.isOverlayActive()) title.update();
    }

    public static void updateProvider(BonusStageProvider provider) {
        if (provider != null && !provider.updateDuringLevelFrame()) provider.onFrameUpdate();
    }

    /** Entry selects/loads the zone; frame repair follows the active provider's declared bootstrap. */
    public static void ensureBootstrapPresent(BonusStageProvider provider, ObjectManager objects) {
        var type = provider != null ? provider.getActiveType() : null;
        if (type != null && type != BonusStageType.NONE) {
            BonusStageBootstrapInstaller.ensurePresent(provider, type, objects);
        }
    }

    public static void cycleObjectRenderFilter(BonusStageProvider provider) {
        if (provider instanceof BonusStageDebugControls controls) controls.cycleObjectRenderFilter();
    }

    public static void cycleObjectSourceFilter(BonusStageProvider provider) {
        if (provider instanceof BonusStageDebugControls controls) controls.cycleObjectSourceFilter();
    }

    public static void beforeReplayFrame(GameplayModeContext context) {
        if (activeProvider(context) != null) updateOverlay(context.getWorldSession().getGameModule().getTitleCardProvider());
    }

    public static void afterReplayFrame(GameplayModeContext context) {
        var provider = activeProvider(context);
        if (provider != null) restorePlayerPriority(provider, context.getSpriteManager());
        updateProvider(provider);
    }

    /** Hurt landing may clear this bit; ordinary ownership is restored after physics. */
    public static void restorePlayerPriority(BonusStageProvider provider, SpriteManager sprites) {
        if (provider instanceof BonusStagePlayerPriorityPolicy policy && !policy.shouldForcePlayerHighPriority()) return;
        boolean changed = false;
        for (var sprite : sprites.getAllSprites()) {
            if (sprite instanceof AbstractPlayableSprite playable
                    && playable.getPriorityBucket() == RenderPriority.PLAYER_DEFAULT && !playable.isHighPriority()) {
                playable.setHighPriority(true);
                changed = true;
            }
        }
        if (changed) sprites.invalidateRenderBuckets();
    }

    private static BonusStageProvider activeProvider(GameplayModeContext context) {
        if (context == null) return null;
        var provider = context.getActiveBonusStageProvider();
        return provider != null && provider.getActiveType() != null
                && provider.getActiveType() != BonusStageType.NONE ? provider : null;
    }
}
