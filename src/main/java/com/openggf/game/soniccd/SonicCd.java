package com.openggf.game.soniccd;

import com.openggf.audio.GameSound;
import com.openggf.data.Game;
import com.openggf.game.GameDataSource;
import com.openggf.game.soniccd.level.SonicCdLevel;
import com.openggf.level.Level;

import java.util.List;
import java.util.Map;

/** Authenticated, detached R11A data consumer. This static slice executes no native program. */
public final class SonicCd extends Game {
    private final CdGameDataSource source;

    public SonicCd(GameDataSource source) {
        this.source = requireSource(source);
    }

    static CdGameDataSource requireSource(GameDataSource source) {
        // Check the final preparation capability's concrete type before reading any claimed
        // identity, named bytes or ROM capability from an unrelated provider.
        if (!(source instanceof CdGameDataSource cd)) {
            throw new IllegalArgumentException("Sonic CD static entry requires CdStockLaunch.prepare() data");
        }
        if (!cd.assets().identity().stage().equals(CdStageKey.parse("R11A"))) {
            throw unsupported("stage " + cd.assets().identity().stage().nativeId());
        }
        return cd;
    }

    static UnsupportedOperationException unsupported(String operation) {
        return new UnsupportedOperationException("Sonic CD development STATIC R11A does not support " + operation);
    }

    static void requireLevel(int index) {
        if (index != 0) throw unsupported("level index " + index + " (only R11A index 0 is admitted)");
    }

    public CdGameDataSource source() { return source; }

    @Override public boolean isCompatible() { return true; }
    @Override public String getIdentifier() { return "Sonic CD (development STATIC R11A)"; }
    @Override public List<String> getTitleCards() { return List.of("PALMTREE PANIC"); }

    /** A fresh mutable drawing view on every load; immutable provenance alone is shared. */
    @Override public SonicCdLevel loadLevel(int levelIdx) {
        requireLevel(levelIdx);
        return new SonicCdLevel(source.assets());
    }

    @Override public void queueFreshLevelRuntimeArt(int levelIdx) {
        throw unsupported("native runtime-art handoffs");
    }

    @Override public int getMusicId(int levelIdx) { requireLevel(levelIdx); return -1; }
    @Override public Map<GameSound, Integer> getSoundMap() { return Map.of(); }
    @Override public boolean canRelocateLevels() { return false; }
    @Override public boolean canSave() { return false; }
    @Override public boolean relocateLevels(boolean unsafe) { throw unsupported("disc relocation/export"); }
    @Override public boolean save(int levelIdx, Level level) { throw unsupported("disc saving/export"); }

    /** Deliberate fixed background origin for inspection, not native R11A parallax. */
    @Override public int[] getBackgroundScroll(int levelIdx, int cameraX, int cameraY) {
        requireLevel(levelIdx);
        return new int[]{0, 0};
    }
}
