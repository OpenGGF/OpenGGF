package com.openggf.game.soniccd.audio;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Pure, uncommitted byte-state projection of the shipped FM PlaySoundID/StopSound closure.
 * No instruction execution, CPU stack, timer, bus transaction, synthesis or audio ownership.
 * Native authority: scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * Sound Drivers/FM/_Driver.asm:545-556,670-722,774-995,1050-1088.
 * FixBugs=0: StopSound deliberately uses TrackInitData and stride two, not six track structs.
 */
public final class CdFmPlaySound {
    private static final int RAM_BYTES = 0x2000;
    private static final int INSTALLED_BYTES = 0x1AD6;
    private static final int SND_PLAY = 0x1C09;
    private static final int END_VARIABLES = 0x1D60;
    private static final String BANK_SHA256 =
            "23f3df846b228c8de5b55cb6503318f05df1a49c5bdfe282ceecf2502ce5bc99";

    private CdFmPlaySound() { }

    public enum Entry { PLAY_SOUND_ID, STOP_SOUND }
    public enum Access { READ, WRITE }
    /** Support boundaries of this planner, never native memory protection or CPU exceptions. */
    public enum FaultKind { UNKNOWN_READ, OUTSIDE_RAM, UNOWNED_WRITE, SELF_MODIFICATION }

    public record Layout(int getListAddress, int trackInitDataAddress, int sfxTrackPtrsAddress) {
        public Layout {
            wordWidth(getListAddress); wordWidth(trackInitDataAddress); wordWidth(sfxTrackPtrsAddress);
        }
    }
    public sealed interface Step permits ByteStore, YmRegisterIntent { }
    public record ByteStore(int address, int value) implements Step {
        public ByteStore { ramAddress(address); byteWidth(value); }
    }
    /** Untimed logical part/register/value only; no physical operation is performed. */
    public record YmRegisterIntent(int part, int register, int value) implements Step {
        public YmRegisterIntent {
            if (part != 0 && part != 1) throw new IllegalArgumentException("YM part must be 0 or 1");
            byteWidth(register); byteWidth(value);
        }
    }
    public record Fault(FaultKind kind, Access access, int address) {
        public Fault { Objects.requireNonNull(kind); Objects.requireNonNull(access); wordWidth(address); }
    }
    public sealed interface Result permits Returned, BoundedFault {
        State projected();
        List<Step> steps();
    }
    public record Returned(State projected, List<Step> steps) implements Result {
        public Returned { Objects.requireNonNull(projected); steps = List.copyOf(Objects.requireNonNull(steps)); }
    }
    /** Both the state and ordered prefix remain UNCOMMITTED; this is not an execution receipt. */
    public record BoundedFault(State projected, List<Step> steps, Fault fault) implements Result {
        public BoundedFault {
            Objects.requireNonNull(projected); steps = List.copyOf(Objects.requireNonNull(steps));
            Objects.requireNonNull(fault);
        }
    }

    /**
     * Immutable bound input and reviewed ownership. Actual assets and declared test format are distinct.
     * Neither a bank address nor an SFX command byte alone classifies a byte as an instruction.
     */
    public static final class NativeView {
        private final byte[] image;
        private final BitSet defined;
        private final BitSet writableData;
        private final BitSet instructions;
        private final Layout layout;
        private final int driverInfo;
        private final boolean authenticated;
        private final CdFmSoundAssets.Source actualSource;
        private final String declaration;

        private NativeView(byte[] image, BitSet defined, BitSet writableData, BitSet instructions,
                           Layout layout, boolean authenticated, CdFmSoundAssets.Source actualSource,
                           String declaration) {
            Objects.requireNonNull(image); Objects.requireNonNull(layout);
            if (image.length != RAM_BYTES) throw new IllegalArgumentException("Supply exactly 8192 backing bytes");
            checkMask(defined); checkMask(writableData); checkMask(instructions);
            BitSet contradiction = (BitSet) writableData.clone(); contradiction.and(instructions);
            if (!contradiction.isEmpty()) throw new IllegalArgumentException("Writable data intersects instructions");
            this.image = canonicalBytes(image, defined);
            this.defined = (BitSet) defined.clone();
            this.writableData = (BitSet) writableData.clone();
            this.instructions = (BitSet) instructions.clone();
            this.layout = layout; this.authenticated = authenticated;
            this.actualSource = actualSource; this.declaration = Objects.requireNonNull(declaration);
            int start = layout.getListAddress();
            for (int i = 0; i < 3; i++) {
                int a = add16(start, i);
                if (a >= RAM_BYTES || !this.defined.get(a) || !this.instructions.get(a)) {
                    throw new IllegalArgumentException("GetList immediate must be supplied, known instruction content");
                }
            }
            if (u8(this.image[start]) != 0x21) throw new IllegalArgumentException("GetList must start with LD HL,nn");
            driverInfo = u8(this.image[add16(start,1)]) | (u8(this.image[add16(start,2)]) << 8);
        }

        /**
         * Uses the existing private usable owner at J; no new raw-media or forged-asset capability.
         * The complete bank identity and finite code/data anchors are checked against retained bytes.
         */
        public static NativeView fromAssets(CdFmSoundAssets assets) {
            Objects.requireNonNull(assets, "actual retained FM assets");
            byte[] bank = assets.bank().copy();
            if (bank.length != INSTALLED_BYTES || !BANK_SHA256.equals(sha256(bank))) {
                throw new IllegalArgumentException("Unsupported retained FM bank identity");
            }
            expect(bank,0x008,"21d70606000908e708c9"); // GetList:42-49; DriverInfo=06D7.
            expect(bank,0x018,"4f06000909");           // ReadTablePtr:60-64.
            expect(bank,0x020,"7e23666fc9");           // ReadHLPtr:76-81.
            expect(bank,0x254,"dd7e00e606c0ddcb00b6dd4e013e28cdcd02c9"); // SetKeyOff/ForceKeyOff.
            expect(bank,0x2BF,"ddcb01562011");
            expect(bank,0x2CD,"3200400079320140c9"); // WriteYM1:687-692.
            expect(bank,0x2E0,"3202400079320340c9"); // WriteYM2:717-722.
            expect(bank,0x312,"3a091ccb7fcac703fee0da2e03c3c703");
            expect(bank,0x322,"800080018002800480058006"); // TrackInitData:788-794.
            expect(bank,0x32E,"d690f80e06cfdf");
            expect(bank,0x388,"3e8032091cc9");
            expect(bank,0x38E,"79cb5728013d32321cf521bb03dfe5dde1f1c9");
            expect(bank,0x3A1,"08af1213121308eb36302336c0233601062423360010fb23ebc9");
            expect(bank,0x3BB,"401c701ca01cd01c001d301d"); // SFXTrackPtrs:933-939.
            expect(bank,0x3C7,"21091c110a1c0156013600edb0dd2122030606");
            expect(bank,0x3EE,"3e0f32121c4f3e27cdcd02c38803");
            expect(bank,0x3FC,"3e900e00c34904");
            expect(bank,0x435,"cd45043e400e7fcd4904dd4e01c35a02");
            expect(bank,0x445,"3e800eff0604f5cdbf02f1c60410f7c9");
            expect(bank,0x6D7,"8b070000eb06eb060000000090000000001c0000");
            byte[] image = new byte[RAM_BYTES]; System.arraycopy(bank,0,image,0,bank.length);
            BitSet defined = new BitSet(RAM_BYTES); defined.set(0,bank.length);
            BitSet writable = new BitSet(RAM_BYTES);
            writable.set(0x322,0x32E); writable.set(0x3BB,0x3C7);
            writable.set(0x6D7,INSTALLED_BYTES); writable.set(0x1C00,END_VARIABLES);
            BitSet code = new BitSet(RAM_BYTES);
            code.set(8,0x12); code.set(0x18,0x1D); code.set(0x20,0x25);
            code.set(0x254,0x267); code.set(0x2BF,0x2E9); code.set(0x312,0x322);
            code.set(0x32E,0x3BB); code.set(0x3C7,0x403); code.set(0x435,0x455);
            // Other pre-06D7 bytes contain interspersed tables and unreviewed code.
            // They remain readable when defined, but writes are UNOWNED_WRITE, not guessed self-modification.
            return new NativeView(image,defined,writable,code,new Layout(8,0x322,0x3BB),true,
                    Objects.requireNonNull(assets.source()),"retained-USA-asset");
        }

        /**
         * Explicit constructed format, with no authenticity claim or implicit populated RAM.
         * Defined mask identifies supplied bytes; unknown backing is discarded and never exposed.
         * Writable data and instructions must be disjoint. GetList's three immediate bytes are required.
         */
        public static NativeView declaredFormat(String declaration, byte[] image, BitSet defined,
                                                BitSet writableData, BitSet instructions, Layout layout) {
            Objects.requireNonNull(declaration);
            if (declaration.isBlank()) throw new IllegalArgumentException("Name the constructed declaration");
            return new NativeView(image,defined,writableData,instructions,layout,false,null,declaration);
        }
        public boolean authenticated() { return authenticated; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof NativeView v)) return false;
            return authenticated == v.authenticated && driverInfo == v.driverInfo
                    && Objects.equals(actualSource,v.actualSource) && declaration.equals(v.declaration)
                    && layout.equals(v.layout) && Arrays.equals(image,v.image) && defined.equals(v.defined)
                    && writableData.equals(v.writableData) && instructions.equals(v.instructions);
        }
        @Override public int hashCode() {
            return 31 * Objects.hash(authenticated,actualSource,declaration,layout,driverInfo,
                    defined,writableData,instructions) + Arrays.hashCode(image);
        }
    }

    /**
     * Immutable current resident image, including writable installed-data overlays and defined identity.
     * A capture is a byte-state value only; it carries no execution cursor, YM replay or session authority.
     */
    public static final class State {
        private final NativeView source;
        private final byte[] values;
        private final BitSet defined;
        private final BitSet owned;
        private State(NativeView source, byte[] values, BitSet defined, BitSet owned) {
            this.source=source; this.values=canonicalBytes(values,defined);
            this.defined=(BitSet)defined.clone(); this.owned=(BitSet)owned.clone();
        }

        /**
         * EXPLICIT installed-image-plus-resident-ingress projection, never native cold install or boot.
         * Actual view supplies only [0000,1AD6); gap/variables/stack stay unknown unless explicitly supplied.
         * Declared view supplies exactly its declared mask. Owned variables need not be defined.
         * Ingress may define resident cells and reviewed writable data; same bound instruction bytes are
         * allowed, but unknown/different executable content rejects before planning. Unreviewed installed
         * bytes cannot be changed or gain ownership merely through this ingress.
         */
        public static State supplied(NativeView source, List<ByteStore> residentIngress) {
            Objects.requireNonNull(source); List<ByteStore> ingress=List.copyOf(Objects.requireNonNull(residentIngress));
            for (ByteStore s:ingress) {
                int a=s.address();
                if (source.instructions.get(a)) {
                    if (!source.defined.get(a) || u8(source.image[a])!=s.value()) {
                        throw new IllegalArgumentException("Instruction ingress differs from known bound source at " + a);
                    }
                } else if (!source.writableData.get(a) && source.defined.get(a)
                        && u8(source.image[a])!=s.value()) {
                    throw new IllegalArgumentException("Unreviewed installed/declaration write ownership at " + a);
                }
            }
            byte[] values=source.image.clone(); BitSet known=(BitSet)source.defined.clone();
            BitSet owned=(BitSet)source.writableData.clone();
            for (ByteStore s:ingress) {
                int a=s.address(); values[a]=(byte)s.value(); known.set(a);
                if (!source.instructions.get(a) && !source.defined.get(a)) owned.set(a);
            }
            return new State(source,values,known,owned);
        }
        public boolean definedAt(int address) { ramAddress(address); return defined.get(address); }
        public int unsignedByte(int address) {
            ramAddress(address);
            if (!defined.get(address)) throw new IllegalStateException("Byte is unknown at " + address);
            return u8(values[address]);
        }
        public State capture() { return this; }
        @Override public boolean equals(Object other) {
            if (this==other) return true;
            return other instanceof State s && source.equals(s.source) && Arrays.equals(values,s.values)
                    && defined.equals(s.defined) && owned.equals(s.owned);
        }
        @Override public int hashCode() {
            return 31*Objects.hash(source,defined,owned)+Arrays.hashCode(values);
        }
    }

    /**
     * Each call starts a NEW entry invocation, even when before is a prior fault's projected prefix.
     * Fault prefixes are uncommitted projections, never instruction-resume/retry tokens or native traps.
     */
    public static Result plan(Entry entry, State before, NativeView source) {
        Objects.requireNonNull(entry); Objects.requireNonNull(before); Objects.requireNonNull(source);
        if (!source.equals(before.source)) throw new IllegalArgumentException("State belongs to another source/format");
        Projection p=new Projection(before,source);
        try {
            if (entry==Entry.STOP_SOUND) p.stopSound(); else p.playSoundId();
            return new Returned(p.state(),p.steps);
        } catch (Boundary boundary) {
            return new BoundedFault(p.state(),p.steps,boundary.fault);
        }
    }

    private static final class Boundary extends RuntimeException {
        private static final long serialVersionUID=1L;
        private final Fault fault;
        private Boundary(Fault fault) { super(null,null,false,false); this.fault=fault; }
    }
    /** One mutable local projection only; never aliases a State, view or external device. */
    private static final class Projection {
        private final NativeView source;
        private final byte[] values;
        private final BitSet defined;
        private final BitSet owned;
        private final List<Step> steps=new ArrayList<>();
        private Projection(State before, NativeView source) {
            this.source=source; values=before.values.clone(); defined=(BitSet)before.defined.clone();
            owned=(BitSet)before.owned.clone();
        }
        private State state() { return new State(source,values,defined,owned); }
        private void boundary(FaultKind kind, Access access, int address) {
            throw new Boundary(new Fault(kind,access,address));
        }
        private int read(int address) {
            if (address>=RAM_BYTES) boundary(FaultKind.OUTSIDE_RAM,Access.READ,address);
            if (!defined.get(address)) boundary(FaultKind.UNKNOWN_READ,Access.READ,address);
            return u8(values[address]);
        }
        private int word(int address) {
            int low=read(address); // Native ReadHLPtr:76-80: low FIRST, then high after wrapping INC HL.
            return low | (read(add16(address,1))<<8);
        }
        private void write(int address, int value) {
            if (address>=RAM_BYTES) boundary(FaultKind.OUTSIDE_RAM,Access.WRITE,address);
            if (source.instructions.get(address)) boundary(FaultKind.SELF_MODIFICATION,Access.WRITE,address);
            if (!owned.get(address)) boundary(FaultKind.UNOWNED_WRITE,Access.WRITE,address);
            values[address]=(byte)value; defined.set(address); steps.add(new ByteStore(address,value));
        }
        private void ym(int part,int register,int value) { steps.add(new YmRegisterIntent(part,register,value)); }
        private void resetSoundQueue() { write(SND_PLAY,0x80); }
        private void disableSpecFm3() {
            write(0x1C12,0x0F); ym(0,0x27,0x0F); resetSoundQueue(); // Native tail jump also resets sndPlay.
        }
        private void playSoundId() {
            int raw=read(SND_PLAY);
            if ((raw&0x80)==0 || raw>=0xE0) { stopSound(); return; }
            int index=(raw-0x90)&255;
            if ((index&0x80)!=0) return; // 80..8F: RET M, no queue reset or invented idle effects.
            int list=word(add16(source.driverInfo,6)); // GetList/DriverInfo, never per-ID metadata maps.
            int header=word(add16(list,index*2));
            int instruments=word(header);
            write(0x1C39,instruments&255); write(0x1C3A,instruments>>>8);
            write(0x1C15,0);
            write(0x1C3B,read(add16(header,2)));
            int count=read(add16(header,3));
            int row=add16(header,4);
            int iterations=count==0 ? 256:count; // Saved outer B restored before DJNZ: 0 wraps to FF.
            for (int n=0;n<iterations;n++) {
                int channel=read(add16(row,1));
                int normalized=(channel&4)!=0 ? (channel-1)&255:channel;
                write(0x1C32,normalized);
                int ix=word(add16(source.layout.sfxTrackPtrsAddress(),normalized*2));
                copy(row,ix); // LDI source read before destination write, including overlapping rows.
                if (read(add16(ix,1))==2) disableSpecFm3(); // OLD destination channel, shipped branch.
                copy(add16(row,1),add16(ix,1));
                write(add16(ix,2),read(0x1C3B));
                for (int i=2;i<6;i++) copy(add16(row,i),add16(ix,i+1));
                finishTrackInit(ix);
                int currentInstruments=word(0x1C39);
                write(add16(ix,0x2A),currentInstruments&255);
                write(add16(ix,0x2B),currentInstruments>>>8);
                setKeyOff(ix); writeYm4Op(ix,0x90,0);
                row=add16(row,6);
            }
            resetSoundQueue();
        }
        private void copy(int from,int to) { int value=read(from); write(to,value); }
        private void finishTrackInit(int ix) {
            write(add16(ix,7),0); write(add16(ix,8),0);
            write(add16(ix,9),0x30); write(add16(ix,10),0xC0); write(add16(ix,11),1);
            for (int i=12;i<0x30;i++) write(add16(ix,i),0);
        }
        private void setKeyOff(int ix) {
            if ((read(ix)&6)!=0) return;
            forceKeyOff(ix);
        }
        private void forceKeyOff(int ix) {
            int flags=read(ix); write(ix,flags&0xBF); // RES 6,(IX+flags) is a reached RMW even if unchanged.
            ym(0,0x28,read(add16(ix,1)));
        }
        private void writeYm4Op(int ix,int base,int value) {
            for (int op=0;op<4;op++) {
                int part=(read(add16(ix,1))&4)!=0 ? 1:0;
                if ((read(ix)&4)!=0) continue;
                int channel=read(add16(ix,1));
                ym(part,(base+4*op+channel-(part==1 ? 4:0))&255,value);
            }
        }
        private void stopSound() {
            write(SND_PLAY,0);
            for (int a=SND_PLAY+1;a<END_VARIABLES;a++) write(a,read(a-1)); // Overlapping native LDIR.
            for (int i=0;i<6;i++) {
                // FixBugs=0: wrong IX points to writable TrackInitData; do not correct to fm1Track.
                int ix=add16(source.layout.trackInitDataAddress(),2*i);
                writeYm4Op(ix,0x80,0xFF); writeYm4Op(ix,0x40,0x7F);
                read(add16(ix,1)); // SilenceTrack:1056 reads channel before ForceKeyOff reads it again.
                forceKeyOff(ix); writeYm4Op(ix,0x90,0);
            }
            write(0x1C0D,0); disableSpecFm3();
        }
    }

    private static int u8(byte value) { return Byte.toUnsignedInt(value); }
    private static int add16(int address,int delta) { return (address+delta)&0xFFFF; }
    private static void byteWidth(int value) {
        if (value<0 || value>255) throw new IllegalArgumentException("Expected unsigned byte: " + value);
    }
    private static void wordWidth(int value) {
        if (value<0 || value>0xFFFF) throw new IllegalArgumentException("Expected native u16: " + value);
    }
    private static void ramAddress(int address) {
        if (address<0 || address>=RAM_BYTES) throw new IllegalArgumentException("Expected owned-domain RAM coordinate: " + address);
    }
    private static void checkMask(BitSet mask) {
        Objects.requireNonNull(mask);
        if (mask.length()>RAM_BYTES) throw new IllegalArgumentException("Mask extends beyond 8192 bytes");
    }
    private static byte[] canonicalBytes(byte[] values,BitSet defined) {
        byte[] copy=values.clone();
        for (int a=0;a<copy.length;a++) if (!defined.get(a)) copy[a]=0;
        return copy;
    }
    private static void expect(byte[] bank,int address,String hex) {
        byte[] expected=HexFormat.of().parseHex(hex);
        if (address<0 || address+expected.length>bank.length) throw new IllegalArgumentException("Format anchor outside bank");
        for (int i=0;i<expected.length;i++) if (bank[address+i]!=expected[i]) {
            throw new IllegalArgumentException("Retained format anchor differs at " + Integer.toHexString(address+i));
        }
    }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
