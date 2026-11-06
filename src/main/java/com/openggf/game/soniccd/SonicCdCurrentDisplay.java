package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.SonicCdLevel;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.resources.CdObjectPresentation;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.graphics.RetainedPlaneSceneRenderer;
import com.openggf.graphics.SpritePresentation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.openggf.game.soniccd.SonicCdPastDisplay.*;

/**
 * One shared selected SAT/plane converter. Native N1ecc5f3a Main.VInt_Level:821-847 selects
 * these immutable values; Sonic.LoadSonicDynPLC:3055-3100 owns the separate F000..F300 pixels.
 * Typed facades supply their finite current-pattern reader. This converter performs no service,
 * current-art mutation, source/World admission or live reborrow. Disabled display samples no art.
 * LevelStart selects line 2/color 0 before fade enables the level (Main:225-243,314-325).
 * Retain that existing level-geometry policy beside the selected palette, never query a live
 * level at draw time. The initial disabled black output does not sample this index.
 */
final class SonicCdCurrentDisplay {
    private SonicCdCurrentDisplay() { }
    @FunctionalInterface interface TileReader { CdAssetBytes read(int patternId); }
    static final class ReadStopped extends RuntimeException {
        final SonicCdPastDisplay.DisplayFailure failure;
        ReadStopped(SonicCdPastDisplay.DisplayFailure failure) { super(null, null, false, false); this.failure = failure; }
    }
    interface Inputs {
        Optional<CdObjectDrawQueue.CpuSat> selectedCpu(); CdAssetBytes selectedSat(); CdAssetBytes palette();
        CdAssetBytes hscroll(); int vscrollLongBits(); boolean displayEnabled();
        CdR11aBackgroundPlane.State foreground(); CdR11aBackgroundPlane.State background(); CdPlayerArtRuntime.State player();
    }
    static SonicCdPastDisplay.Projection project(Inputs inputs, TileReader reader) {
        Objects.requireNonNull(inputs, "selected display inputs");
        if (!inputs.displayEnabled()) {
            return new Disabled(new RetainedPlaneSceneRenderer.Scene(
                    null, null, hscrollWords(inputs.hscroll()), inputs.vscrollLongBits(), inputs.palette().copy(),
                    false, SpritePresentation.Frame.empty(), Map.of(), SonicCdLevel.LEVEL_BACKDROP_PALETTE_INDEX));
        }
        DisplayFailure cpuFailure = selectedCpuFailure(inputs);
        if (cpuFailure != null) return new Blocked(cpuFailure);
        Objects.requireNonNull(reader, "enabled current pattern reader");
        var samples = new HashMap<Integer, SpritePresentation.PatternVersion>();
        CdObjectPresentation.PatternSource resolver = patterns(reader, inputs.player(),
                PatternAtlasRange.OBJECTS.base());
        CdObjectPresentation.PatternSource selected = id -> {
            var sample = resolver.resolve(id);
            samples.put(id, sample.version());
            return sample;
        };
        try {
            var foreground = plane(inputs.foreground(), selected);
            var background = plane(inputs.background(), selected);
            SpritePresentation.Frame sprites = inputs.selectedCpu().isPresent()
                    ? CdObjectPresentation.convert(inputs.selectedCpu().orElseThrow(), selected)
                    : SpritePresentation.Frame.empty();
            return new Ready(new RetainedPlaneSceneRenderer.Scene(
                    foreground, background, hscrollWords(inputs.hscroll()), inputs.vscrollLongBits(),
                    inputs.palette().copy(), true, sprites, samples, SonicCdLevel.LEVEL_BACKDROP_PALETTE_INDEX));
        } catch (ReadStopped stopped) {
            return new Blocked(stopped.failure);
        }
    }
    private static CdObjectPresentation.PatternSource patterns(TileReader reader,
                                                               CdPlayerArtRuntime.State player, int logicalBase) {
        var cache = new HashMap<Integer, CdObjectPresentation.TilePattern>();
        return id -> {
            Objects.checkIndex(id, 2048);
            var sample = cache.get(id);
            if (sample != null) return sample;
            CdAssetBytes raw;
            if (id >= 0x780 && id < 0x798) {
                // Include the exact last resident tile, beyond the ordinary $2E0-byte DMA.
                raw = player.residentBank().slice((id - 0x780) * 32, 32);
            } else {
                raw = reader.read(id);
            }
            sample = new CdObjectPresentation.TilePattern(logicalBase + id,
                    CdObjectPresentation.patternVersion(raw));
            cache.put(id, sample);
            return sample;
        };
    }

    private static RetainedPlaneSceneRenderer.PlaneImage plane(CdR11aBackgroundPlane.State state,
                                                                CdObjectPresentation.PatternSource source) {
        int[] words = new int[64 * 32];
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                int descriptor = state.word(x, y);
                words[y * 64 + x] = descriptor;
                source.resolve(descriptor & 0x7FF);
            }
        }
        return new RetainedPlaneSceneRenderer.PlaneImage(words);
    }

    /** Mirror only the existing converter's bounded geometry checks, before any art lookup. */
    private static DisplayFailure selectedCpuFailure(Inputs inputs) {
        byte[] raw = inputs.selectedSat().copy();
        if (inputs.selectedCpu().isEmpty()) {
            for (int i = 0; i < raw.length; i++) {
                if (raw[i] != 0) return new DisplayFailure(Boundary.MISSING_CPU_AUTHORITY, i, -1, -1);
            }
            return null;
        }
        var cpu = inputs.selectedCpu().orElseThrow();
        byte[] genuine = cpu.bytes();
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] != genuine[i]) return new DisplayFailure(Boundary.SAT_MISMATCH, i, -1, -1);
        }
        for (int index = cpu.spriteCount() - 1; index >= 0; index--) {
            int at = index * 8;
            int x = word(genuine, at + 6);
            if (x < 1 || x > 511) return new DisplayFailure(Boundary.SAT_COORDINATES, at + 6, -1, -1);
            if ((genuine[at + 2] & 255) > 15) return new DisplayFailure(Boundary.SAT_SIZE, at + 2, -1, -1);
        }
        return null;
    }

    private static int[] hscrollWords(CdAssetBytes bytes) {
        int[] packed = new int[256];
        for (int i = 0; i < packed.length; i++) packed[i] = (int) bytes.unsignedLong(i * 4);
        return packed;
    }

    private static int word(byte[] bytes, int at) {
        return ((bytes[at] & 255) << 8) | (bytes[at + 1] & 255);
    }

}
