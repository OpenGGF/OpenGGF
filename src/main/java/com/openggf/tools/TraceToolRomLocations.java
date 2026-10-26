package com.openggf.tools;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomLocationResolver;
import com.openggf.game.GameId;

import java.nio.file.Path;

final class TraceToolRomLocations {

    private TraceToolRomLocations() {
    }

    static Path resolve(String gameId, SonicConfigurationService configuration,
                        Path workingDirectory) {
        GameId resolvedGameId = GameId.fromCode(gameId);
        var cartridge = resolvedGameId.cartridgeGame().orElseThrow(() ->
                new IllegalArgumentException("ROM trace tools do not support game: " + gameId));
        return new RomLocationResolver(configuration, workingDirectory)
                .resolve(cartridge)
                .map(location -> location.resolvedPath())
                .orElseThrow(() -> new IllegalStateException(
                        "No ROM configured for game: " + gameId));
    }
}
