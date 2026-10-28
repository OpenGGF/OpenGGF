package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.level.objects.BakedSheetReader;

import java.util.Map;

public final class StandaloneRecoveryTestAccess {
    private StandaloneRecoveryTestAccess() { }

    public static GameModule wrap(GameModule delegate, ModFaultBoundary boundary,
                                  Map<String, BakedSheetReader.BakedSheet> preparedArt) {
        return OwnerAwareStandaloneModule.wrap("owner", delegate, boundary, Map.of(), preparedArt);
    }
}
