package com.openggf.game.sonic3k.specialstage;

import com.openggf.game.ResultsScreen;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.session.GameplayModeContext;

/** Explicit per-invocation results context; never retained on a reused module/provider. */
public final class S3kSpecialStageWorldAccess {
    private S3kSpecialStageWorldAccess() { }

    /** Preserved by the existing owned callback proxy without bypassing its fault boundary. */
    public interface ContextualResults {
        ResultsScreen createResultsScreenInWorld(int ringsCollected, boolean gotEmerald,
                int stageIndex, int totalEmeraldCount, GameplayModeContext context);
    }

    /** Exact native operation proof, including authenticated host projections. */
    public static boolean isNativeWorldCreation(Object provider) {
        return com.openggf.game.internal.NativeProviderDispatch.matchesNative(provider,
                Sonic3kSpecialStageProvider.class, "createResultsScreenInWorld",
                int.class, boolean.class, int.class, int.class, GameplayModeContext.class);
    }

    public static ResultsScreen createResults(SpecialStageProvider provider, GameplayModeContext context,
            int ringsCollected, boolean gotEmerald, int stageIndex, int totalEmeraldCount) {
        if (provider instanceof ContextualResults s3k
                && isNativeWorldCreation(provider))
            return s3k.createResultsScreenInWorld(ringsCollected, gotEmerald, stageIndex, totalEmeraldCount, context);
        return provider.createResultsScreen(ringsCollected, gotEmerald, stageIndex, totalEmeraldCount);
    }
}
