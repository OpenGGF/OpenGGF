package com.openggf.game.sonic3k.titlecard;

import com.openggf.game.TitleCardProvider;
import com.openggf.game.session.WorldPlcLifecycleAccess;
import com.openggf.game.session.WorldSession;
import com.openggf.game.sonic3k.Sonic3kLevelTitlePlcService;

/** Per-call engine context; the shared title provider never remembers a last caller or queue. */
public final class S3kTitleCardWorldAccess {
    private S3kTitleCardWorldAccess() { }

    /** Existing owned callback proxies preserve this engine-only per-call capability. */
    public interface ContextualUpdate {
        void updateInWorld(WorldSession world);
    }

    public static void update(TitleCardProvider provider, WorldSession world) {
        if (provider instanceof ContextualUpdate title
                && isNativeWorldUpdate(provider))
            title.updateInWorld(world);
        else provider.update();
    }

    public static void updateExternalChildren(Sonic3kTitleCardManager title, WorldSession world) {
        if (isNativeWorldUpdate(title))
            title.updateExternalInLevelChildrenWithPlc(plc(world));
        else title.updateExternalInLevelChildren();
    }

    /** Exact native operation proof, including authenticated host projections. */
    public static boolean isNativeWorldUpdate(Object provider) {
        return com.openggf.game.internal.NativeProviderDispatch.matchesNative(provider,
                Sonic3kTitleCardManager.class, "updateInWorld", WorldSession.class);
    }

    private static Sonic3kLevelTitlePlcService plc(WorldSession world) {
        return world == null ? null : WorldPlcLifecycleAccess.require(world, Sonic3kLevelTitlePlcService.class);
    }
}
