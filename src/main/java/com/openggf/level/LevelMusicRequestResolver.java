package com.openggf.level;

import com.openggf.data.Game;

import java.io.IOException;
import java.util.OptionalInt;

/** Resolves the next playlist request under the transition suppression latch. */
final class LevelMusicRequestResolver {
    private LevelMusicRequestResolver() {
    }

    static OptionalInt prepare(LevelTransitionCoordinator transitions,
                               Game game, int levelIndex) throws IOException {
        if (transitions.consumeSuppressNextMusicChange()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(game.getMusicId(levelIndex));
    }
}
