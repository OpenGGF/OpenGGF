package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import com.openggf.game.soniccd.resources.CdR11bCameraArtResidency;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane;
import com.openggf.graphics.RetainedPlaneSceneRenderer;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure projection of the values already selected by the actual Past display service.
 * This package value neither performs a service nor grants Visit, Frame or World authority.
 * Native source pin 1ecc5f3a: Level/Main.asm:VInt_Level:821-847 (selected SAT/palette/scroll) and
 * Level/_Objects/Sonic/Main.asm:LoadSonicDynPLC:3055-3100 (the resident F000..F300 window). Complete physical planes are retained;
 * word zero names pattern zero. Unknown current pixels never acquire a default.
 */
final class SonicCdPastDisplay {
    private SonicCdPastDisplay() { }

    enum Boundary {
        MISSING_CPU_AUTHORITY, SAT_MISMATCH, SAT_COORDINATES, SAT_SIZE,
        UNKNOWN_PATTERN, OUTSIDE_OWNED_WINDOW
    }

    /** Exact finite location data, without a retained exception or execution token. */
    record DisplayFailure(Boundary boundary, int byteOffset, int nativePatternId, int destinationWord) {
        DisplayFailure {
            Objects.requireNonNull(boundary, "display boundary");
            boolean art = boundary == Boundary.UNKNOWN_PATTERN || boundary == Boundary.OUTSIDE_OWNED_WINDOW;
            if (art) {
                if (byteOffset != -1 || nativePatternId < 0 || nativePatternId > 0x7FF
                        || destinationWord != (nativePatternId << 5)) {
                    throw new IllegalArgumentException("Expected a final native pattern address");
                }
            } else if (byteOffset < 0 || byteOffset >= 640 || nativePatternId != -1 || destinationWord != -1) {
                throw new IllegalArgumentException("Expected an exact selected SAT byte location");
            }
        }
    }

    /**
     * Borrowed immutable selected values. A genuine prior CPU table is compared by its
     * bytes, active count and provenance value, not by the private object's identity.
     * Disabled display can retain absent, unsampled plane/art owners.
     */
    record Inputs(Optional<CdObjectDrawQueue.CpuSat> selectedCpu, CdAssetBytes selectedSat,
                  CdAssetBytes palette, CdAssetBytes hscroll, int vscrollLongBits, boolean displayEnabled,
                  CdR11aBackgroundPlane.State foreground, CdR11aBackgroundPlane.State background,
                  CdR11bCameraArtResidency.State current, CdPlayerArtRuntime.State player) implements SonicCdCurrentDisplay.Inputs {
        Inputs {
            Objects.requireNonNull(selectedCpu, "selected CPU optional");
            Objects.requireNonNull(selectedSat, "selected SAT");
            Objects.requireNonNull(palette, "selected palette");
            Objects.requireNonNull(hscroll, "selected HScroll");
            if (selectedSat.length() != 640 || palette.length() != 128 || hscroll.length() != 1024) {
                throw new IllegalArgumentException("Selected display requires SAT640, palette128 and HScroll1024");
            }
            if (displayEnabled) {
                Objects.requireNonNull(foreground, "enabled physical foreground");
                Objects.requireNonNull(background, "enabled physical background");
                Objects.requireNonNull(current, "enabled current B patterns");
                Objects.requireNonNull(player, "enabled resident player art");
            }
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Inputs value)) return false;
            return cpuEquals(selectedCpu, value.selectedCpu)
                    && Arrays.equals(selectedSat.copy(), value.selectedSat.copy())
                    && Arrays.equals(palette.copy(), value.palette.copy())
                    && Arrays.equals(hscroll.copy(), value.hscroll.copy())
                    && vscrollLongBits == value.vscrollLongBits && displayEnabled == value.displayEnabled
                    && (foreground == value.foreground || foreground != null && value.foreground != null
                        && Arrays.equals(foreground.nameTable().copy(), value.foreground.nameTable().copy()))
                    && (background == value.background || background != null && value.background != null
                        && Arrays.equals(background.nameTable().copy(), value.background.nameTable().copy()))
                    && Objects.equals(current, value.current)
                    && (player == value.player || player != null && value.player != null
                        && player.lastFrame() == value.player.lastFrame() && player.dirty() == value.player.dirty()
                        && Arrays.equals(player.cpuBuffer().copy(), value.player.cpuBuffer().copy())
                        && Arrays.equals(player.residentBank().copy(), value.player.residentBank().copy()));
        }

        @Override public int hashCode() {
            int hash = Objects.hash(vscrollLongBits, displayEnabled, current,
                    player == null ? null : player.lastFrame(), player == null ? null : player.dirty());
            hash = 31 * hash + cpuHash(selectedCpu);
            hash = 31 * hash + Arrays.hashCode(selectedSat.copy());
            hash = 31 * hash + Arrays.hashCode(palette.copy());
            hash = 31 * hash + Arrays.hashCode(hscroll.copy());
            hash = 31 * hash + (foreground == null ? 0 : Arrays.hashCode(foreground.nameTable().copy()));
            hash = 31 * hash + (background == null ? 0 : Arrays.hashCode(background.nameTable().copy()));
            hash = 31 * hash + (player == null ? 0 : Arrays.hashCode(player.cpuBuffer().copy()));
            return 31 * hash + (player == null ? 0 : Arrays.hashCode(player.residentBank().copy()));
        }
    }

    sealed interface Projection permits Ready, Disabled, Blocked { }

    record Ready(RetainedPlaneSceneRenderer.Scene scene) implements Projection {
        Ready {
            Objects.requireNonNull(scene, "ready scene");
            if (!scene.displayEnabled()) throw new IllegalArgumentException("Ready requires enabled display");
        }
    }

    record Disabled(RetainedPlaneSceneRenderer.Scene scene) implements Projection {
        Disabled {
            Objects.requireNonNull(scene, "disabled scene");
            if (scene.displayEnabled()) throw new IllegalArgumentException("Disabled requires disabled display");
        }
    }

    record Blocked(DisplayFailure failure) implements Projection {
        Blocked { Objects.requireNonNull(failure, "display failure"); }
    }

    record Selection(Inputs inputs, Projection projection) {
        Selection {
            Objects.requireNonNull(inputs, "selected inputs");
            Objects.requireNonNull(projection, "projection");
            if (inputs.displayEnabled() == (projection instanceof Disabled)) {
                throw new IllegalArgumentException("Projection kind disagrees with selected display enable");
            }
        }
    }

    static Selection project(Inputs inputs) {
        Objects.requireNonNull(inputs, "selected display inputs");
        return new Selection(inputs, SonicCdCurrentDisplay.project(inputs, id -> {
            try { return CdR11bCameraArtResidency.readTile(inputs.current(), id); }
            catch (CdR11bCameraArtResidency.OwnershipBoundary fault) {
                Boundary kind = switch (fault.boundary()) {
                    case UNKNOWN_PATTERN -> Boundary.UNKNOWN_PATTERN;
                    case OUTSIDE_OWNED_WINDOW -> Boundary.OUTSIDE_OWNED_WINDOW;
                };
                throw new SonicCdCurrentDisplay.ReadStopped(new DisplayFailure(kind, -1,
                        fault.nativePatternId(), fault.destinationWord()));
            }
        }));
    }

    /**
     * Observational value validation at an owner-controlled cut. It performs no projection,
     * native work or CPU/art reborrow, and cannot authenticate a forged package value.
     */
    static void validateSelected(Selection selected, Inputs expected) {
        Objects.requireNonNull(selected, "retained selection");
        Objects.requireNonNull(expected, "expected selected inputs");
        if (!selected.inputs().equals(expected)) {
            throw new IllegalArgumentException("Retained selection differs from the expected selected inputs");
        }
    }

    private static boolean cpuEquals(Optional<CdObjectDrawQueue.CpuSat> first,
                                     Optional<CdObjectDrawQueue.CpuSat> second) {
        if (first.isEmpty() || second.isEmpty()) return first.isEmpty() && second.isEmpty();
        var a = first.orElseThrow();
        var b = second.orElseThrow();
        return a.spriteCount() == b.spriteCount() && Arrays.equals(a.bytes(), b.bytes())
                && a.provenance().equals(b.provenance());
    }

    private static int cpuHash(Optional<CdObjectDrawQueue.CpuSat> value) {
        if (value.isEmpty()) return 0;
        var cpu = value.orElseThrow();
        return Objects.hash(Arrays.hashCode(cpu.bytes()), cpu.spriteCount(), cpu.provenance());
    }

}
