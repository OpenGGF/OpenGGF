package com.openggf.game.soniccd;

import com.openggf.audio.GameSound;
import com.openggf.game.Game;
import com.openggf.game.GameDataSource;
import com.openggf.game.soniccd.level.SonicCdLevel;
import com.openggf.level.Level;

import java.util.List;
import java.util.Map;

/** Authenticated, detached R11A data consumer. This static slice executes no native program. */
public final class SonicCd extends Game {
    private final CdGameDataSource source;
    private final com.openggf.game.soniccd.level.CdLevelLoadingSource loadingSource;

    public SonicCd(GameDataSource source) {
        this.source = requireSource(source);
        this.loadingSource=null;
    }

    /** Pure Game DATA binding, used only by the Module's private current FULL assembly. */
    SonicCd(CdGameDataSource source,com.openggf.game.soniccd.level.CdLevelLoadingSource loadingSource) {
        this.source=requireSource(source);this.loadingSource=java.util.Objects.requireNonNull(loadingSource);
        requireLoadingSource(source,loadingSource);
    }
    static void requireLoadingSource(CdGameDataSource source,com.openggf.game.soniccd.level.CdLevelLoadingSource loading) {
        java.util.Objects.requireNonNull(source);java.util.Objects.requireNonNull(loading);
        if(loading.ownsOriginalR11(source.assets())||loading.ownsRetainedStandardAssets(source.assets()))return;
        if(loading.file()==CdNativeFile.R11B&&source.initialProgramSource().isPresent()
                &&source.initialProgramSource().orElseThrow().pastLoadingSource()==loading){
            loading.requireRetained();return;
        }
        var actual=source.programmeData(loading.stage()).orElseThrow(()->new IllegalArgumentException("Programme source is absent from actual carrier"));
        if(actual.levelLoadingSource()!=loading)throw new IllegalArgumentException("Foreign actual programme loading source");
        try{loading.requirePlayerSource(actual.playerSource());}
        catch(java.io.IOException failure){throw new java.io.UncheckedIOException("Actual selected player source read failed",failure);}
    }
    static int sourceLevelIndex(com.openggf.game.soniccd.level.CdLevelLoadingSource loading) {
        int raw=loading.nativeZoneActWord();return (raw>>>8)*3+(raw&255);
    }
    private void requireBoundLevel(int index) {
        if(loadingSource==null){requireLevel(index);return;}
        if(index!=sourceLevelIndex(loadingSource))throw unsupported("foreign selected source level index "+index);
    }
    static String sourceTitle(com.openggf.game.soniccd.level.CdLevelLoadingSource loading) {
        return switch(loading.nativeZoneActWord()>>>8){case 0->"PALMTREE PANIC";case 2->"TIDAL TEMPEST";default->loading.stage().nativeId();};
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
    @Override public String getIdentifier() { return loadingSource==null?"Sonic CD (development STATIC R11A)":"Sonic CD (selected original "+sourceTitle(loadingSource)+")"; }
    @Override public List<String> getTitleCards() { return List.of(loadingSource==null?"PALMTREE PANIC":sourceTitle(loadingSource)); }

    /** A fresh mutable drawing view on every load; immutable provenance alone is shared. */
    @Override public SonicCdLevel loadLevel(int levelIdx) {
        requireBoundLevel(levelIdx);
        if(loadingSource==null)return new SonicCdLevel(source.assets());
        requireLoadingSource(source,loadingSource);
        try{return SonicCdLevel.fromLoadingSource(loadingSource);}
        catch(java.io.IOException failure){throw new java.io.UncheckedIOException("Actual selected CD geometry read failed",failure);}
    }

    @Override public void queueFreshLevelRuntimeArt(int levelIdx) {
        throw unsupported("native runtime-art handoffs");
    }

    @Override public int getMusicId(int levelIdx) { requireBoundLevel(levelIdx); return -1; }
    @Override public Map<GameSound, Integer> getSoundMap() { return Map.of(); }
    @Override public boolean canRelocateLevels() { return false; }
    @Override public boolean canSave() { return false; }
    @Override public boolean relocateLevels(boolean unsafe) { throw unsupported("disc relocation/export"); }
    @Override public boolean save(int levelIdx, Level level) { throw unsupported("disc saving/export"); }

    /** Deliberate fixed background origin for inspection, not native R11A parallax. */
    @Override public int[] getBackgroundScroll(int levelIdx, int cameraX, int cameraY) {
        requireBoundLevel(levelIdx);
        return new int[]{0, 0};
    }
}
