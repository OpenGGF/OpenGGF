package com.openggf.game.sonic2.competition.interlace;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.sonic2.competition.Sonic2CompetitionLevelProfile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.sonic2.competition.interlace.Sonic2InterlaceWorldInputs.*;

/**
 * Rewind-owned CPU display buffers and the explicit shipped P2 continuation.
 * This is a source model, NOT a physical video service or an activated renderer.
 * The root still owns resolved-object/ring projections, actual BuildSprites calls,
 * and the qualified physical field/handler/H-int transaction. No method here
 * advances an actor, deformation, VInt, PLC or DMA service.
 */
public final class Sonic2InterlaceCpuDisplay implements RewindSnapshottable<Sonic2InterlaceCpuDisplay.Snapshot> {
    public static final String REWIND_KEY = "sonic2-interlace-cpu-display";
    public enum HintState { UNKNOWN, PENDING, CLEAR }
    private final Sonic2InterlaceSatBuilder builder;
    private final Sonic2InterlaceProducer projector;
    private Sonic2CompetitionLevelProfile profile;
    private Sonic2InterlaceVramService art;
    private Sonic2InterlaceWorkRam ram;
    private Sonic2InterlaceSatBuilder.Pass first, second;
    private Build continuation;
    private HintState hint = HintState.UNKNOWN;

    public Sonic2InterlaceCpuDisplay(Rom rom, int patternBase, int capacity) throws IOException {
        builder = new Sonic2InterlaceSatBuilder(RomByteReader.fromRom(Objects.requireNonNull(rom)));
        projector = new Sonic2InterlaceProducer(patternBase, capacity);
    }

    /** The exact NativeArt.workRamBytes image, including TempArray spill, is mandatory. */
    public void bindAct(Sonic2CompetitionLevelProfile profile, byte[] actualWorkRamBytes,
                        Sonic2InterlaceVramService samePerActArt) {
        var next = new Sonic2InterlaceWorkRam(actualWorkRamBytes);
        Objects.requireNonNull(profile); Objects.requireNonNull(samePerActArt);
        this.profile = profile; art = samePerActArt; ram = next;
        first = second = null; continuation = null; hint = HintState.UNKNOWN;
        // Binding neither resets art/PLC nor publishes any initial plane/SAT bytes.
    }

    /** Until A/B/C are connected and independently qualified, this can never advertise a route. */
    public boolean supportsZone(int ignored) { return false; }

    /**
     * Exactly one actual BuildSprites call. When H-int has not occurred, P1 is
     * committed to CPU RAM and the captured P2 continuation parks. A second
     * object pass cannot run through this blocked CPU continuation.
     */
    public Publication buildAfterObjects(Build input, HintState actualHint) {
        requireBound(); requireKnown(actualHint);
        if (continuation != null) throw new IllegalStateException("Native CPU is waiting in BuildSprites_P2");
        var p1 = builder.buildPlayer1(ram, input);
        var p2 = actualHint == HintState.CLEAR ? builder.buildPlayer2(p1, input, false) : null;
        first = p1; second = p2; continuation = p2 == null ? input : null;
        ram = p2 == null ? p1.ram() : p2.ram(); hint = actualHint;
        return current();
    }

    /**
     * Capture OLD P2 RAM for the H-int upload, BEFORE Hint_flag is cleared and
     * before the parked P2 builder resumes. The root must put this write inside
     * its same pre/post-DMA VRAM transaction; this method publishes nothing.
     */
    public HInterruptUpload prepareHInterruptUpload() {
        requireBound();
        if (hint != HintState.PENDING) throw new IllegalStateException("No known pending H-int");
        return new HInterruptUpload(ram, new Sonic2InterlaceVramService.CpuWordWrite(0xF800, 2,
                words(ram, Sonic2InterlaceWorkRam.SAT_P2, 320)));
    }

    /** Only the explicit qualified source event may supply CLEAR; there is no draw/clock inference. */
    public Publication resumePlayer2AfterHintClear(HInterruptUpload uploadedOldPage, HintState actualHint) {
        requireBound(); requireKnown(actualHint);
        Objects.requireNonNull(uploadedOldPage, "old P2 upload");
        if (actualHint != HintState.CLEAR || hint != HintState.PENDING || uploadedOldPage.source != ram)
            throw new IllegalStateException("H-int clear does not match the captured old P2 page");
        var p2 = continuation == null ? null : builder.buildPlayer2(first, continuation, false);
        if (p2 != null) { ram = p2.ram(); second = p2; }
        continuation = null; hint = HintState.CLEAR;
        return current();
    }

    /** Immutable CPU upload plan, not a timing job/readiness token. */
    public static final class HInterruptUpload {
        private final Sonic2InterlaceWorkRam source;
        private final Sonic2InterlaceVramService.CpuWordWrite write;
        private HInterruptUpload(Sonic2InterlaceWorkRam source, Sonic2InterlaceVramService.CpuWordWrite write) {
            this.source = source; this.write = write;
        }
        public Sonic2InterlaceVramService.CpuWordWrite write() { return write; }
    }

    /**
     * Retained SOURCE publication only. It has no physical field, handler or
     * scanline timing, and must not be presented as a completed hardware field.
     */
    public record Publication(Sonic2CompetitionLevelProfile profile, Sonic2InterlaceWorkRam ram,
                              Sonic2InterlaceVram artImage, HintState hintState,
                              List<Visibility> visibility, boolean consumeDisplayLists) {
        public Publication {
            Objects.requireNonNull(profile); Objects.requireNonNull(ram); Objects.requireNonNull(artImage);
            Objects.requireNonNull(hintState); visibility = List.copyOf(visibility);
        }
        public List<Integer> satWords(int player) {
            int base = switch (player) {
                case 0 -> Sonic2InterlaceWorkRam.SAT_P1;
                case 1 -> Sonic2InterlaceWorkRam.SAT_P2;
                default -> throw new IllegalArgumentException("SAT page");
            };
            return words(ram, base, 320);
        }
        public List<Integer> horizontalScrollWords() { return words(ram, Sonic2InterlaceWorkRam.HSCROLL, 448); }
        public List<Integer> normalPaletteWords() { return words(ram, 0xFB00, 64); }
    }

    public Publication current() {
        requireBound();
        var result = second != null ? second : first;
        return new Publication(profile, ram, art.current(), hint,
                result == null ? List.of() : result.visibility(), second != null && second.consumeDisplayLists());
    }

    /** Explicit CPU-page/field projection for source checks; caller supplies parity, never a guessed clock. */
    public Sonic2InterlaceProducer.PreparedField prepareCpuPage(Publication publication, int player,
                                                               Sonic2InterlaceDecoder.Field physicalField) {
        Objects.requireNonNull(physicalField, "explicit physical field");
        var page = publication.satWords(player);
        byte[] bytes = new byte[640];
        for (int i = 0; i < page.size(); i++) {
            bytes[i * 2] = (byte) (page.get(i) >>> 8); bytes[i * 2 + 1] = (byte) (int) page.get(i);
        }
        var image = publication.artImage().withWrite(0xF800, bytes);
        return projector.prepareSprites(new Sonic2InterlaceDecoder.Input(image, physicalField,
                Sonic2InterlaceDecoder.readSat(image, 0xF800, true)));
    }

    private static List<Integer> words(Sonic2InterlaceWorkRam ram, int address, int count) {
        var result = new ArrayList<Integer>(count);
        for (int i = 0; i < count; i++) result.add(ram.readWord(address + i * 2));
        return List.copyOf(result);
    }
    private void requireBound() { if (ram == null) throw new IllegalStateException("Native act is not bound"); }
    private static void requireKnown(HintState state) {
        if (state == null || state == HintState.UNKNOWN) throw new IllegalArgumentException("Actual Hint_flag is unavailable");
    }

    public record Snapshot(Sonic2CompetitionLevelProfile profile, Sonic2InterlaceVramService art,
                           Sonic2InterlaceWorkRam ram, Sonic2InterlaceSatBuilder.Pass first,
                           Sonic2InterlaceSatBuilder.Pass second, Build continuation, HintState hint) {
        public Snapshot {
            Objects.requireNonNull(hint);
            if ((ram == null) != (profile == null) || (ram == null) != (art == null)
                    || (ram == null && (first != null || second != null || continuation != null || hint != HintState.UNKNOWN))
                    || (first == null && (continuation != null || hint != HintState.UNKNOWN))
                    || (first != null && first.player() != 0) || (second != null && (second.player() != 1 || first == null))
                    || (first != null && second == null && continuation == null)
                    || (second != null && hint != HintState.CLEAR)
                    || (continuation != null && (first == null || second != null || hint != HintState.PENDING
                    || first.ram() != ram)) || (second != null && second.ram() != ram))
                throw new IllegalArgumentException("Invalid native CPU display snapshot");
        }
    }
    @Override public String key() { return REWIND_KEY; }
    @Override public Snapshot capture() { return new Snapshot(profile, art, ram, first, second, continuation, hint); }
    @Override public void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        profile = snapshot.profile(); art = snapshot.art(); ram = snapshot.ram();
        first = snapshot.first(); second = snapshot.second(); continuation = snapshot.continuation(); hint = snapshot.hint();
    }
    @Override public void resetForMissingSnapshot() {
        profile = null; art = null; ram = null; first = second = null; continuation = null; hint = HintState.UNKNOWN;
    }
}
