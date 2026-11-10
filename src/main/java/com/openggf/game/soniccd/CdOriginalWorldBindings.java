package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdStagePlayerSource;
import com.openggf.game.soniccd.objects.CdCheckpointLiveAux;
import com.openggf.game.soniccd.objects.CdLevelGlobals;
import com.openggf.game.soniccd.objects.CdOpeningObjectRuntime;
import com.openggf.game.soniccd.objects.CdRingGlobals;
import com.openggf.game.soniccd.resources.CdLevelHud;
import com.openggf.game.soniccd.resources.CdLevelPaletteCycle;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import com.openggf.game.soniccd.scroll.CdR11aForegroundScroll;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/** Source/semantic scene binding for the one original World, not an invocation capability.
 * The actual World retains the Module-issued access, physical walk, queue and capture owner.
 * Section state is typed by its existing owner; a strategy never substitutes an R11 section
 * or player source for a different programme. TT admission additionally needs its actual
 * deferred-water protocol and DATA facade; this interface creates neither.
 */
interface CdOriginalWorldBindings<S> {
    CdStagePlayerSource playerSource();
    CdObjectPlacements placements();
    int nativeZoneActWord();

    record ScrollResult(CdR11aForegroundScroll.Result foreground,SonicCdR31aLoadingVisit.Scroll scroll) {
        public ScrollResult {Objects.requireNonNull(foreground);Objects.requireNonNull(scroll);}
    }
    ScrollResult scroll(SonicCdR31aLoadingVisit.Scroll before,CdR11aForegroundScroll.Input input,
                        int rawTimeZoneByte,int playerXVelocity,int playerX,int playerControl);

    /** Complete existing aggregate counters; only the source-selected slots may change. */
    record PaletteResult(CdLevelPaletteCycle.Result nativeResult,CdAssetBytes timers7,CdAssetBytes steps7) {
        public PaletteResult {Objects.requireNonNull(nativeResult);Objects.requireNonNull(timers7);Objects.requireNonNull(steps7);}
    }
    record PaletteState(CdAssetBytes palette,CdAssetBytes timers7,CdAssetBytes steps7) {
        public PaletteState {Objects.requireNonNull(palette);Objects.requireNonNull(timers7);Objects.requireNonNull(steps7);}
    }
    /** Actual aggregate read remains after the source-selected script read, as in native order. */
    @FunctionalInterface interface PaletteAccess {PaletteState readPalette();}
    PaletteResult paletteCycle(PaletteAccess access)throws IOException;
    default PaletteResult paletteCycle(CdAssetBytes palette,CdAssetBytes timers7,CdAssetBytes steps7)throws IOException {
        return paletteCycle(()->new PaletteState(palette,timers7,steps7));
    }
    UnsupportedOperationException paletteFailure(CdLevelPaletteCycle.Fault fault);

    record HudState<S>(S section,CdRingGlobals ring,CdLevelGlobals level,
                       CdOpeningObjectRuntime.ScoreGlobals score,CdCheckpointLiveAux.State liveTime,
                       Optional<CdLevelHud.BonusValues> bonus) {
        public HudState {Objects.requireNonNull(section);Objects.requireNonNull(ring);Objects.requireNonNull(level);
            Objects.requireNonNull(score);Objects.requireNonNull(liveTime);Objects.requireNonNull(bonus);}
    }
    /** Synchronous completed-prefix publication, supplied only by the actual private World scope.
     * Values are data. Implementing this interface does not grant a Module/Frame publication token.
     */
    @FunctionalInterface interface HudAccess<S> {void retain(HudState<S> ready);}
    interface Hud<S> {void run(HudState<S> before,CdLevelHud.Inputs inputs,HudAccess<S> access);}

    /** The shared Sonic art runtime already owns this transfer shape for every admitted format. */
    static void requirePlayerArt(CdPlayerArtRuntime.DmaRequest request,CdPlayerArtRuntime.State complete) {
        if(request.vramByteAddress()!=CdPlayerArtRuntime.VRAM_BYTE_ADDRESS
                ||!Arrays.equals(request.bytes().copy(),complete.residentBank().slice(0,CdPlayerArtRuntime.DMA_BYTES).copy()))
            throw new IllegalStateException("Foreign actual Sonic publication");
    }
}
