package com.openggf.game.sonic2;

import com.openggf.data.Rom;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.session.GameplayModeContext;

import java.io.IOException;
import java.util.Objects;

/** Engine-only feature composition; competition bumpers have their own raw ROM owner. */
public final class Sonic2ZoneFeatureInternalAccess {
    private Sonic2ZoneFeatureInternalAccess() { }

    public static void initializeNativeCompetitionFeatures(Sonic2ZoneFeatureProvider provider,
            GameplayModeContext context, NativeCompetitionRoster roster, Rom rom,
            int zone, int act, int cameraX) throws IOException {
        Objects.requireNonNull(provider, "native features").initNativeCompetitionFeatures(
                context, roster, rom, zone, act, cameraX);
    }
}
