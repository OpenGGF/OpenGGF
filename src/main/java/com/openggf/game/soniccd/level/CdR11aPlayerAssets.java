package com.openggf.game.soniccd.level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.release.CdRetailReleases;

/**
 * Bounded R11A player byte prerequisites at scddisasm 1ecc5f3a. No animation,
 * physics, CalcAngle/CalcSine arithmetic, DPLC residency or hardware parity is implemented.
 * Production bytes come through authenticated programme factories; this decoder grants no execution.
 * The explicit 167-frame source retains latent high-frame scripts; selected-frame bounds stay with animation.
 * Original R3 and R4 DATA borrow this common contract with explicit graphs and unowned PPZ ramp access.
 */
public final class CdR11aPlayerAssets {
    public static final int ANIMATION_COUNT = 60;
    private static final int ANIMATION_BYTES = 578;

    /** Includes the native header, commands, operands and actual trailing padding/duplicate FF bytes. */
    public record AnimationScript(int relativeOffset, CdAssetBytes bytes) {
        /** Nonnegative delay, or native FF/FE/FD/FC handler selector; never a generic S1 profile. */
        public byte signedDelayOrHandler() { return bytes.signedByte(0); }
    }

    private final CdSonicVisualAssets sonic;
    private final CdAssetBytes animationTable;
    private final List<AnimationScript> animations;
    private final CdAssetBytes miniAnimations;
    private final Optional<CdAssetBytes> rampChunks;
    private final CdProgrammeReader r3Source;
    private final CdAssetBytes specialChunks;
    private final CdAssetBytes sineTable;
    private final CdAssetBytes arcTanTable;

    private CdR11aPlayerAssets(byte[] animations, byte[] miniAnimations, byte[] rampChunks,
                                byte[] specialChunks, byte[] sineTable, byte[] arcTanTable,
                                CdSonicVisualAssets sonic) {
        this(animations, miniAnimations, new PpzRampInput(rampChunks), specialChunks, sineTable, arcTanTable, sonic, null);
    }

    /** Finite data graph only; it grants no routine/source-family execution admission. */
    public enum SourceGraph { PPZ, R3, R4 }
    /** The R3/R4 products deliberately have no PPZ table; access cannot manufacture an empty/default view. */
    public static final class UnownedPpzRamp extends UnsupportedOperationException {
        private UnownedPpzRamp(String graph) { super(graph+" source does not own the PPZ 3D ramp chunk table"); }
    }
    // Private tagged inputs preserve legacy validation order without supplying ANY ramp bytes for R3/R4.
    private sealed interface RampInput permits PpzRampInput, ProgrammeRampInput { }
    private record PpzRampInput(byte[] bytes) implements RampInput { }
    private enum ProgrammeRampInput implements RampInput { UNOWNED }

    private CdR11aPlayerAssets(byte[] animations, byte[] miniAnimations, RampInput rampInput,
                                byte[] specialChunks, byte[] sineTable, byte[] arcTanTable,
                                CdSonicVisualAssets sonic, CdProgrammeReader r3Source) {
        this.r3Source = r3Source;
        this.sonic = Objects.requireNonNull(sonic, "sonic");
        this.animationTable = exact(animations, ANIMATION_BYTES, "Ani_Sonic");
        this.animations = decodeAnimations(animationTable, sonic);
        this.miniAnimations = exact(miniAnimations, ANIMATION_COUNT, "live MiniAnims");
        for (int id = 0; id < ANIMATION_COUNT; id++) {
            if (this.miniAnimations.unsignedByte(id) >= ANIMATION_COUNT) {
                throw new IllegalArgumentException("Mini animation ID outside Ani_Sonic");
            }
        }

        // ObjSonic_Handle3DRamp.ChunkList (Sonic/Main.asm:797-831): five
        // positive raw layout-byte identities followed by the actual $FF sentinel.
        if (rampInput instanceof PpzRampInput input) {
            var ramp = exact(input.bytes(), 6, "3D ramp chunks");
            for (int i = 0; i < 5; i++) {
                if (ramp.unsignedByte(i) == 0 || ramp.signedByte(i) < 0) {
                    throw new IllegalArgumentException("Early/invalid R11A ramp chunk sentinel");
                }
            }
            if (ramp.unsignedByte(5) != 0xFF) {
                throw new IllegalArgumentException("Missing reviewed R11A ramp chunk sentinel");
            }
            this.rampChunks = Optional.of(ramp);
        } else {
            if (rampInput != ProgrammeRampInput.UNOWNED || r3Source == null)
                throw new IllegalArgumentException("Only actual retained R3 programme owns the R3 core-data profile");
            this.rampChunks = Optional.empty();
        }
        // LevelSizeLoad copies one long; Sonic_SpecialChunks compares all four
        // raw bytes independently (Scroll:113-129; Sonic/Main.asm:2522-2544).
        this.specialChunks = exact(specialChunks, 4, "SpecChunks");

        // Functions (Misc).asm:69-101: 256 sine words plus the 64-word
        // cosine extension. Retain the real values, including native asymmetries.
        this.sineTable = exact(sineTable, 640, "SineTable");
        for (int i = 0; i < 320; i++) {
            int value = sineWord(i);
            if (value < -0x100 || value > 0x100
                    || (i >= 256 && value != sineWord(i - 256))) {
                throw new IllegalArgumentException("Unverified sine word/cosine extension");
            }
        }
        // CalcAngle (Functions (Misc).asm:119-219) can form quotient 256
        // for equal nonzero magnitudes. Do not clamp to 255 or retain the EVEN byte.
        this.arcTanTable = exact(arcTanTable, 257, "ArcTanTable reachable entries");
        int previous = 0;
        for (int i = 0; i <= 256; i++) {
            int value = this.arcTanTable.unsignedByte(i);
            if (value < previous || value > 0x20) {
                throw new IllegalArgumentException("Unverified arctangent table range/order");
            }
            previous = value;
        }
        if (this.arcTanTable.unsignedByte(0) != 0 || this.arcTanTable.unsignedByte(256) != 0x20) {
            throw new IllegalArgumentException("Unverified arctangent endpoints");
        }
    }

    /**
     * Component format decoder only; constructed bytes do not authenticate a release.
     * The supplied, already decoded visual owner is retained by identity, without a second decode/copy.
     */
    public static CdR11aPlayerAssets decode(byte[] animations, byte[] miniAnimations, byte[] rampChunks,
                                            byte[] specialChunks, byte[] sineTable, byte[] arcTanTable,
                                            CdSonicVisualAssets sonic) {
        return new CdR11aPlayerAssets(animations, miniAnimations, rampChunks, specialChunks, sineTable, arcTanTable, sonic);
    }

    /** Original-USA R31A common data only. Legacy PlayerCheckBooster3dSurfaceType returns
     * before its table for raw0100/1 (N objects/player.asm:736-740); no PPZ ramp bytes are borrowed.
     * Raw80 Mini and258 Atan includes remain in the immutable aggregate; only live60/257 are consumed.
     * The same script/math/167 visual validators execute; no Body/Frame/World admission is granted. */
    public static CdR11aPlayerAssets fromR31aAssets(CdR31aAssets assets) {
        Objects.requireNonNull(assets, "Original R31A assets");assets.requireAuthenticated();
        var id = Objects.requireNonNull(assets.identity(), "R31A identity");
        if (id.variant() != CdReleaseVariant.USA || id.file() != CdNativeFile.R31A
                || !CdStagePayload.R31A.stage().equals(id.stage()) || id.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                || !"30bb932c3f7a18f0629dcee56fb982280e0b6d6063fb67392b23771f77e7682e".equals(id.mmdSha256())
                || !CdRetailReleases.USA.normalizedSha256().equals(id.observedWholeDiscSha256()))
            throw new IllegalArgumentException("R31A common data requires its original USA source identity");
        var sonic = Objects.requireNonNull(assets.sonic(), "R31A Sonic visuals");
        if (sonic.sourceFormat() != CdSonicVisualAssets.SourceFormat.FRAMES_167 || sonic.mappingFrames().size() != 167)
            throw new IllegalArgumentException("R31A common data requires its retained 167-frame visual product");
        var reader=Objects.requireNonNull(assets.internalReader(),"actual R31A source relation");
        if(sonic!=reader.sonic())throw new IllegalArgumentException("R31A projected visual differs");
        for(var role:List.of(CdProgrammeReader.Role.ANI,CdProgrammeReader.Role.MINI,CdProgrammeReader.Role.SPECIAL,CdProgrammeReader.Role.SINE,CdProgrammeReader.Role.ATAN)) {
            var key=switch(role){case ANI->CdR31aAssets.Asset.ANI_SONIC;case MINI->CdR31aAssets.Asset.MINI_ANIMATIONS;
                case SPECIAL->CdR31aAssets.Asset.SPEC_CHUNKS;case SINE->CdR31aAssets.Asset.SINE_TABLE;case ATAN->CdR31aAssets.Asset.ARC_TAN_TABLE;
                default->throw new IllegalArgumentException("Not a player table role");};
            if(!Arrays.equals(Objects.requireNonNull(assets.view(key)).copy(),reader.role(role).copy()))throw new IllegalArgumentException("R31A projected player table differs: "+key);
        }
        return fromR3Programme(reader);
    }
    static CdR11aPlayerAssets fromR3Programme(CdProgrammeReader actual) {
        Objects.requireNonNull(actual,"actual retained R3 programme").requireRetained();
        var sonic=actual.sonic();
        if(sonic.sourceFormat()!=CdSonicVisualAssets.SourceFormat.FRAMES_167 || sonic.mappingFrames().size()!=167)
            throw new IllegalArgumentException("R3 requires retained 167-frame visuals");
        return decodeProgramme(actual,sonic);
    }
    /** Retained semantic profile only; no gameplay-family admission follows from DATA compatibility. */
    static CdR11aPlayerAssets fromProgramme(CdProgrammeReader actual) {
        Objects.requireNonNull(actual,"actual retained programme");
        if(actual.definition().player().map(p->p.graph()==SourceGraph.PPZ).orElse(false))
            return actual.ppzPlayerAssets();
        if(actual.definition().tt().isEmpty())return fromR3Programme(actual);
        actual.requireRetained();var binding=actual.playerBinding();var sonic=actual.sonic();
        if(sonic.sourceFormat()!=binding.visual().format() || sonic.mappingFrames().size()!=binding.frames())
            throw new IllegalArgumentException("Programme requires its retained visual format");
        return decodeProgramme(actual,sonic);
    }
    /** Fixed original R11A DATA decoder, called only by that reader's cached product owner.
     * Reuses every existing validator and its argument order. No 167-frame/R3 relabel,
     * padded tables, future binding, runtime source admission or new arithmetic is supplied. */
    static CdR11aPlayerAssets fromOriginalR11Programme(CdProgrammeReader actual) {
        Objects.requireNonNull(actual,"actual retained original R11A").retainedOriginalR11Image();
        var binding=actual.playerBinding();var sonic=actual.sonic();
        if(binding.graph()!=SourceGraph.PPZ || binding.ordinaryZoneActWord()!=0 || binding.ordinaryPeriod()!=1
                || sonic.sourceFormat()!=CdSonicVisualAssets.SourceFormat.FRAMES_198_WITH_EVEN_BYTE
                || sonic.mappingFrames().size()!=198 || binding.visual().format()!=sonic.sourceFormat())
            throw new IllegalArgumentException("Original R11A requires its retained 198-frame PPZ player DATA");
        actual.requirePlayerRelation(sonic,actual.playerMappingAddress());
        return decodePpzProgrammeInputs(actual,sonic);
    }
    /** Same immutable PPZ format, with actual canonical programme roles and retained source.
     * Profile metadata selects the ordinary period; no R3/R4 relabel, runtime admission or
     * replacement A bridge is created. The common constructor retains all existing validators. */
    static CdR11aPlayerAssets fromPpzProgramme(CdProgrammeReader actual){
        Objects.requireNonNull(actual,"actual retained PPZ programme").requireRetained();
        var binding=actual.playerBinding();var sonic=actual.sonic();
        if(binding.graph()!=SourceGraph.PPZ
                || sonic.sourceFormat()!=CdSonicVisualAssets.SourceFormat.FRAMES_198_WITH_EVEN_BYTE
                || sonic.mappingFrames().size()!=198 || binding.visual().format()!=sonic.sourceFormat())
            throw new IllegalArgumentException("PPZ requires its retained 198-frame player DATA");
        actual.requirePlayerRelation(sonic,actual.playerMappingAddress());
        return decodePpzProgrammeInputs(actual,sonic);
    }
    private static CdR11aPlayerAssets decodePpzProgrammeInputs(CdProgrammeReader actual,CdSonicVisualAssets sonic){
        return new CdR11aPlayerAssets(actual.playerRole(CdProgrammeReader.Role.ANI).copy(),
            actual.playerRole(CdProgrammeReader.Role.MINI).copy(),new PpzRampInput(actual.playerRole(CdProgrammeReader.Role.RAMP).copy()),
            actual.playerRole(CdProgrammeReader.Role.SPECIAL).copy(),actual.playerRole(CdProgrammeReader.Role.SINE).copy(),
            actual.playerRole(CdProgrammeReader.Role.ATAN).copy(),sonic,actual);
    }
    private static CdR11aPlayerAssets decodeProgramme(CdProgrammeReader actual,CdSonicVisualAssets sonic) {
        if(actual.definition().tt().isPresent())actual.requirePlayerRelation(sonic,actual.playerMappingAddress());
        return new CdR11aPlayerAssets(actual.playerRole(CdProgrammeReader.Role.ANI).copy(),
            actual.playerRole(CdProgrammeReader.Role.MINI).slice(0,ANIMATION_COUNT).copy(),ProgrammeRampInput.UNOWNED,
            actual.playerRole(CdProgrammeReader.Role.SPECIAL).copy(),actual.playerRole(CdProgrammeReader.Role.SINE).copy(),
            actual.playerRole(CdProgrammeReader.Role.ATAN).slice(0,257).copy(),sonic,actual);
    }
    public SourceGraph sourceGraph() { return r3Source == null ? SourceGraph.PPZ : r3Source.playerBinding().graph(); }
    public boolean ownsR31aAssets(CdR31aAssets assets) { return assets!=null && r3Source!=null && r3Source==assets.internalReader(); }
    boolean ownsProgramme(CdProgrammeReader actual){return r3Source!=null && r3Source==actual;}

    private static List<AnimationScript> decodeAnimations(CdAssetBytes table, CdSonicVisualAssets sonic) {
        int[] offsets = new int[ANIMATION_COUNT];
        int first = ANIMATION_BYTES;
        for (int id = 0; id < ANIMATION_COUNT; id++) {
            // ObjSonic_Animate (Sonic/Main.asm:2659-2662) uses signed ADDA.W.
            int offset = (short) table.unsignedWord(id * 2);
            if (offset < ANIMATION_COUNT * 2 || offset >= table.length() || (offset & 1) != 0) {
                throw new IllegalArgumentException("Animation signed relative pointer outside reviewed bodies");
            }
            offsets[id] = offset;
            first = Math.min(first, offset);
        }
        if (first != ANIMATION_COUNT * 2) {
            throw new IllegalArgumentException("Missing first reviewed animation body");
        }
        List<AnimationScript> scripts = new ArrayList<>();
        for (int offset : offsets) {
            int end = table.length();
            // Bound by the next distinct body, preserving the native $3A/$36
            // and $3B/$37 aliases without assuming monotonically increasing IDs.
            for (int other : offsets) {
                if (other > offset) { end = Math.min(end, other); }
            }
            var body = table.slice(offset, end - offset);
            validateScript(body, sonic);
            scripts.add(new AnimationScript(offset, body));
        }
        return List.copyOf(scripts);
    }

    private static void validateScript(CdAssetBytes body, CdSonicVisualAssets sonic) {
        if (body.length() < 3) { throw new IllegalArgumentException("Truncated animation body"); }
        int header = body.unsignedByte(0);
        if (header >= 0x80 && header < 0xFC) {
            throw new UnsupportedOperationException("Unreviewed CD animation handler byte");
        }
        // RunAnimScript (Sonic/Main.asm:2675-2715) treats $80..$FC as
        // frame IDs, not generic negative-byte commands. Only $FF/$FE/$FD
        // terminate these reviewed scripts. FC headers select FrozenAnim
        // (2888-2893); retaining that script does not execute its external frame selection.
        for (int cursor = 1; cursor < body.length(); cursor++) {
            int value = body.unsignedByte(cursor);
            if (value < 0xFD) {
                // The original 167-frame sources retain six latent high-frame scripts verbatim.
                // Syntax stays fully validated here; CdPlayerAnimation.result checks a frame only
                // when the unchanged runtime actually selects it. The 198 format remains eager.
                if (sonic.sourceFormat() != CdSonicVisualAssets.SourceFormat.FRAMES_167
                        && value >= sonic.mappingFrames().size()) {
                    throw new IllegalArgumentException("Animation frame outside Sonic mappings");
                }
                continue;
            }
            if (cursor == 1) { throw new IllegalArgumentException("Animation has no frame before command"); }
            int end = cursor + 1;
            if (value != 0xFF) {
                if (end == body.length()) { throw new IllegalArgumentException("Truncated animation command operand"); }
                int operand = body.unsignedByte(end++);
                if (value == 0xFE && (operand == 0 || operand > cursor - 1)) {
                    throw new IllegalArgumentException("Animation loop-back outside its frame sequence");
                }
                if (value == 0xFD && operand >= ANIMATION_COUNT) {
                    throw new IllegalArgumentException("Animation change outside Ani_Sonic");
                }
            }
            for (int padding = end; padding < body.length(); padding++) {
                int tail = body.unsignedByte(padding);
                if (tail != 0 && tail != 0xFF) {
                    throw new IllegalArgumentException("Unreviewed bytes beyond animation terminal command");
                }
            }
            return;
        }
        throw new IllegalArgumentException("Animation body has no bounded terminal command");
    }

    public CdSonicVisualAssets sonic() { return sonic; }
    public CdAssetBytes animationTable() { return animationTable; }
    public List<AnimationScript> animations() { return animations; }
    public AnimationScript animation(int nativeId) { return animations.get(nativeId); }
    public CdAssetBytes miniAnimations() { return miniAnimations; }
    public int miniAnimationId(int nativeId) { return miniAnimations.unsignedByte(nativeId); }
    /** PPZ-only five raw layout-byte IDs and signed-negative sentinel; R3 access is explicitly unowned. */
    public CdAssetBytes rampChunks() { return rampChunks.orElseThrow(()->new UnownedPpzRamp(sourceGraph().name())); }
    /** Native order: loop, special loop, special roll tunnel, regular roll tunnel. */
    public CdAssetBytes specialChunks() { return specialChunks; }
    public CdAssetBytes sineTable() { return sineTable; }
    public CdAssetBytes arcTanTable() { return arcTanTable; }
    /** Signed native table word; index 0..319 includes the cosine extension. No angle arithmetic. */
    public int sineWord(int tableIndex) {
        Objects.checkIndex(tableIndex, 320);
        return (short) sineTable.unsignedWord(tableIndex * 2);
    }
    /** Native quotient 0..256; values outside the admitted table fail without clamping. */
    public int arcTanByte(int nativeQuotient) { return arcTanTable.unsignedByte(nativeQuotient); }

    private static CdAssetBytes exact(byte[] bytes, int length, String name) {
        if (bytes.length != length) { throw new IllegalArgumentException(name + " extent"); }
        return CdAssetBytes.copyOf(bytes);
    }
}
