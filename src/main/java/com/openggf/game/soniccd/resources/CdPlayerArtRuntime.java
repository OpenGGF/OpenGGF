package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdSonicVisualAssets;

import java.util.Objects;
import java.util.Optional;

/**
 * CD-only Sonic art owner for one source-ordered V-int service. Consumes decoded,
 * bounded visual assets; production must obtain them from the authenticated CD loader.
 * No animation, drawing, graphics submission or hardware timing is performed here.
 *
 * <p>The caller supplies the previously production-published map frame and complete
 * ingress RAM/residency. There is no default RAM, initial cue, or renderer binding.
 * This owner covers the $300-byte VRAM window at $F000; bytes outside that window
 * belong to other owners. Each returned DMA is already reflected in the logical
 * resident snapshot. A future engine adapter must apply it at the same service
 * boundary and restore residency without reissuing cues.</p>
 *
 * <p>Source: scddisasm 1ecc5f3a, LoadSonicDynPLC
 * (Level/_Objects/Sonic/Main.asm:3055-3100), VInt_Level
 * (Level/Main.asm:821-847). This is a byte transform/order model, not a cycle model.</p>
 */
public final class CdPlayerArtRuntime {
    public static final int CPU_BUFFER_BYTES = 0x300;
    public static final int RESIDENT_WINDOW_BYTES = 0x300;
    public static final int DMA_BYTES = 0x2E0;
    public static final int VRAM_BYTE_ADDRESS = 0xF000;

    /**
     * Complete owned state. CdAssetBytes storage is immutable, so captures and
     * restores may safely share it. The array constructor discards caller storage,
     * including overlapping CPU/resident arrays. Dirty represents the native byte's
     * zero/nonzero test; lastFrame retains any native byte, without a -1 sentinel.
     * Supplying this state does not establish native entry/clear provenance.
     */
    public record State(CdAssetBytes cpuBuffer, CdAssetBytes residentBank, int lastFrame, boolean dirty) {
        public State {
            Objects.requireNonNull(cpuBuffer, "cpuBuffer");
            Objects.requireNonNull(residentBank, "residentBank");
            if (cpuBuffer.length() != CPU_BUFFER_BYTES || residentBank.length() != RESIDENT_WINDOW_BYTES) {
                throw new IllegalArgumentException("Complete CD Sonic CPU and resident windows are required");
            }
            if (lastFrame < 0 || lastFrame > 0xFF) {
                throw new IllegalArgumentException("Last frame must be a native byte");
            }
        }

        public State(byte[] cpuBuffer, byte[] residentBank, int lastFrame, boolean dirty) {
            this(CdAssetBytes.copyOf(cpuBuffer), CdAssetBytes.copyOf(residentBank), lastFrame, dirty);
        }
    }

    /** Immutable fixed-length VRAM request; creating/returning it touches no graphics state. */
    public record DmaRequest(CdAssetBytes bytes) {
        public DmaRequest {
            Objects.requireNonNull(bytes, "bytes");
            if (bytes.length() != DMA_BYTES) {
                throw new IllegalArgumentException("CD Sonic DMA must contain exactly $2E0 bytes");
            }
        }

        public int vramByteAddress() { return VRAM_BYTE_ADDRESS; }
    }

    private final CdSonicVisualAssets assets;
    private State state;

    public CdPlayerArtRuntime(CdSonicVisualAssets assets, State ingress) {
        this.assets = Objects.requireNonNull(assets, "assets");
        state = Objects.requireNonNull(ingress, "ingress");
    }

    /** Immutable capture of both complete windows and the native comparison/dirty state. */
    public State capture() { return state; }

    /** Restores owned bytes/flags only; source/world identity and renderer rebinding remain caller-owned. */
    public void restore(State snapshot) { state = Objects.requireNonNull(snapshot, "snapshot"); }

    /** Services a loaded player using the map frame published before this V-int boundary. */
    public Optional<DmaRequest> serviceVInt(int previousPublishedMapFrame) {
        return serviceVInt(true, previousPublishedMapFrame);
    }

    /**
     * LoadSonicDynPLC first, then VInt_Level's pending-art DMA and dirty clear.
     * An absent player skips the frame read/comparison/cue, but pending dirty RAM
     * still uploads. Invalid loaded frames fail before any state change or service.
     * This method is called only by a future normal engine owner, never by Draw.
     */
    public Optional<DmaRequest> serviceVInt(boolean playerLoaded, int previousPublishedMapFrame) {
        if (playerLoaded) { Objects.checkIndex(previousPublishedMapFrame, assets.dplcFrames().size()); }
        CdAssetBytes cpuBuffer = state.cpuBuffer();
        CdAssetBytes residentBank = state.residentBank();
        int lastFrame = state.lastFrame();
        boolean dirty = state.dirty();

        if (playerLoaded && previousPublishedMapFrame != lastFrame) {
            // Store before the empty-cue branch, not after a successful upload.
            lastFrame = previousPublishedMapFrame;
            var cue = assets.dplcFrames().get(lastFrame);
            // Decoder owns native count/read/output bounds. In its admitted 0..127
            // count domain, SUBQ.B/BMI skips zero; DBF copies exactly count entries.
            if (!cue.pieces().isEmpty()) {
                dirty = true;
                byte[] updated = cpuBuffer.copy(); // Preserve every unwritten CPU tail byte.
                int destination = 0;
                for (int piece = 0; piece < cue.pieces().size(); piece++) {
                    // Existing bounded slices implement (high nibble + 1) tiles,
                    // (low twelve bits * $20) art offset, including actual EVEN.
                    CdAssetBytes source = assets.artPiece(lastFrame, piece);
                    source.buffer().get(updated, destination, source.length());
                    destination += source.length();
                }
                cpuBuffer = CdAssetBytes.copyOf(updated);
            }
        }

        Optional<DmaRequest> request = Optional.empty();
        if (dirty) {
            var dma = new DmaRequest(cpuBuffer.slice(0, DMA_BYTES));
            byte[] updated = residentBank.copy();
            dma.bytes().buffer().get(updated, 0, DMA_BYTES);
            residentBank = CdAssetBytes.copyOf(updated); // $F2E0..$F2FF remain resident.
            dirty = false;
            request = Optional.of(dma);
        }
        state = new State(cpuBuffer, residentBank, lastFrame, dirty);
        return request;
    }
}
