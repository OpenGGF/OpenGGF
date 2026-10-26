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

    /** Selected semantic targets for one direct UpdateTrack(IX), never a whole driver service. */
    public enum TrackHandler { PANNING, INSTRUMENT, LEGATO, STOP, JUMP, REPEAT }

    public record TrackLayout(int freqTableAddress, int instrumentRegsAddress, int insTlRegsAddress,
                              int commandTableAddress, java.util.Map<TrackHandler,Integer> handlers) {
        public TrackLayout {
            wordWidth(freqTableAddress); wordWidth(instrumentRegsAddress);
            wordWidth(insTlRegsAddress); wordWidth(commandTableAddress);
            if (insTlRegsAddress != add16(instrumentRegsAddress,0x15)) {
                throw new IllegalArgumentException("InsTLRegs must immediately follow 21 instrument register bytes");
            }
            handlers=java.util.Map.copyOf(Objects.requireNonNull(handlers));
            if (handlers.size()!=TrackHandler.values().length) {
                throw new IllegalArgumentException("Supply all six track handler identities");
            }
            var distinct=new java.util.HashSet<Integer>();
            for (TrackHandler handler:TrackHandler.values()) {
                Integer target=Objects.requireNonNull(handlers.get(handler),"Missing track handler");
                wordWidth(target);
                if (!distinct.add(target)) throw new IllegalArgumentException("Handler targets must be distinct");
            }
        }
    }

    /**
     * Source-bound finite support classification only. No RAM is installed or exported by this view.
     * Actual addresses are reconciled against N and the retained bank, not the superseded 06D8 ledger.
     */
    public static final class TrackView {
        private final NativeView source;
        private final TrackLayout layout;
        private final BitSet instructions;
        private final boolean authenticated;

        private TrackView(NativeView source, TrackLayout layout, BitSet instructions, boolean authenticated) {
            this.source=Objects.requireNonNull(source); this.layout=Objects.requireNonNull(layout);
            checkMask(instructions);
            this.instructions=(BitSet)instructions.clone(); this.authenticated=authenticated;
            BitSet conflict=(BitSet)this.instructions.clone(); conflict.and(source.writableData);
            if (!conflict.isEmpty()) throw new IllegalArgumentException("Track code intersects writable data");
            trackData(layout.freqTableAddress(),24); trackData(layout.instrumentRegsAddress(),21);
            trackData(layout.insTlRegsAddress(),4); trackData(layout.commandTableAddress(),64);
            for (int target:layout.handlers().values()) {
                if (target>=RAM_BYTES || !source.defined.get(target) || !this.instructions.get(target)) {
                    throw new IllegalArgumentException("Handler entry must be known, instruction-classified source");
                }
            }
        }

        /** Only the existing actual retained-USA capability can reach this constructor. */
        public static TrackView fromNative(NativeView source) {
            Objects.requireNonNull(source);
            if (!source.authenticated || source.actualSource==null || source.driverInfo!=0x6D7
                    || !source.layout.equals(new Layout(8,0x322,0x3BB))) {
                throw new IllegalArgumentException("Track view requires the authenticated retained USA format");
            }
            for (int a=0;a<INSTALLED_BYTES;a++) if (!source.defined.get(a)) {
                throw new IllegalArgumentException("Incomplete retained bank source");
            }
            byte[] bank=Arrays.copyOf(source.image,INSTALLED_BYTES);
            if (!BANK_SHA256.equals(sha256(bank))) throw new IllegalArgumentException("Retained bank identity differs");
            BitSet code=(BitSet)source.instructions.clone();
            // N FM UpdateTrack:157-188; UpdateFreq:197-218. The special body itself is excluded.
            trackCode(bank,code,0x074,"cdbb012017cdf800ddcb0066c0cdc301cd6702cdee01cdac00c33b02ddcb0066c0dd7e1eb72806dd351eca5402cd6702ddcb0076c0cdee01");
            trackCode(bank,code,0x0AC,"ddcb0056c0ddcb0046c2c5003ea44ccdbf023ea04dcdbf02c9");
            trackCode(bank,code,0x0C5,"dd7e01fe0220ec"); // CheckFM3 channel predicate; boundary00CC.
            // ParseNext/GetNote/ReadDuration:261-324; excluded SetPortaSpeed starts015C.
            trackCode(bank,code,0x0F8,"dd5e03dd5604ddcb008eddcb00a61a13fee0d28e0408cd540208ddcb005ec26301b7f28901d681f22801ddcb00e61821");
            trackCode(bank,code,0x128,"dd8605d516081e0c08af08933804088218f883217604df08b467d1dd750ddd740e");
            trackCode(bank,code,0x149,"ddcb006e200d1ab7f28801dd7e0cdd770b1833");
            trackCode(bank,code,0x188,"13cdb101dd770cdd7303dd7204dd7e0cdd770bddcb004ec0afdd7725dd7722dd7717dd7e1fdd771ec9");
            trackCode(bank,code,0x1B1,"dd460205c84f8110fdc9");
            trackCode(bank,code,0x1BB,"dd7e0b3ddd770bc9");
            trackCode(bank,code,0x1C3,"ddcb077ec8ddcb004ec0"); // InitVibrato predicates only.
            trackCode(bank,code,0x1EE,"dd7e07b7c8"); // UpdateVibrato predicate only.
            trackCode(bank,code,0x23B,"dd7e0dddb60ec8dd7e00e606c0dd7e01f6f04f3e28cdcd02c9");
            trackCode(bank,code,0x254,"dd7e00e606c0ddcb00b6dd4e013e28cdcd02c9");
            trackCode(bank,code,0x267,"0600dd7e10b7f2710205dd660edd6e0d4f09eb3e07a2474bb7218302ed4238062185fa19180eb7210805ed423005217c0519ebebddcb006ec8dd740edd750dc9");
            trackCode(bank,code,0x2AE,"dd6e2add662bafb028061119001910fdc9");
            trackCode(bank,code,0x2BF,"ddcb01562011ddcb0056c0dd86013200400079320140c9ddcb0056c0dd8601d6043202400079320340c9");
            trackCode(bank,code,0x2E9,"115504dd4e0a3eb4cdbf02cd0a03dd771b0614cd0a0310fbdd751cdd741dc3f905");
            trackCode(bank,code,0x30A,"1a134e23cdbf02c9");
            trackCode(bank,code,0x445,"3e800eff0604f5cdbf02f1c60410f7c9");
            trackCode(bank,code,0x48E,"219a04e5d6e0219e04df1ae913c30601");
            trackCode(bank,code,0x577,"ddcb00ce1bc9");
            trackCode(bank,code,0x5C4,"cd45041add7708d547cdae02cde902d1c9");
            trackCode(bank,code,0x5D5,"0e3fdd7e0aa1ebb6dd770a4f3eb4cdbf02ebc9");
            trackCode(bank,code,0x5F9,"d9116a04dd6e1cdd661d06047eb7f20d06dd8606e67f4f1acdbf02132310edd9c9");
            trackCode(bank,code,0x625,"eb5e23561bc9");
            trackCode(bank,code,0x64F,"ddcb00be3e1f32151cdd4e01cd5a02af32181ce1e1c9");
            trackCode(bank,code,0x6AA,"13c6284f0600dde5e1097eb720021a771335c2250613c9");
            // Interspersed register/frequency/command/detune DATA never becomes executable or owned.
            expect(bank,0x455,"b03038343c5058545c6068646c7078747c8088848c");
            expect(bank,0x46A,"4048444c"); expect(bank,0x46E,"9098949c"); expect(bank,0x472,"adaeaca6");
            expect(bank,0x476,"8402ab02d302fe022d035c038f03c503ff033c047c04c004");
            expect(bank,0x49E,"d50537053b053f054f06c106f30577054505e8054f060d0516054f055605c405630571054f0665064f064f062506aa067d06970621061a062b0641067d05ec04");
            expect(bank,0x5B4,"000032018e01e40134027e02c202f002");
            // Boundary authentication is not permission to execute Call, Return or RepeatEnd.
            expect(bank,0x67D,"4f131a47c5dde5e1dd3509dd4e09dd3509060009722b73d11bc9");
            expect(bank,0x697,"dde5e1dd4e090600095e2356dd3409dd3409c9");
            expect(bank,0x6C1,"13c6284f0600dde5e1097e3dcad20613c9af77c32506");
            expect(bank,0x6D7,"8b070000eb06eb060000000090000000001c0000");
            TrackLayout layout=new TrackLayout(0x476,0x455,0x46A,0x49E,java.util.Map.of(
                    TrackHandler.PANNING,0x5D5,TrackHandler.INSTRUMENT,0x5C4,TrackHandler.LEGATO,0x577,
                    TrackHandler.STOP,0x64F,TrackHandler.JUMP,0x625,TrackHandler.REPEAT,0x6AA));
            return new TrackView(source,layout,code,true);
        }

        /** Exact caller declaration, never an actual-bank fallback or a populated driver state. */
        public static TrackView declaredFormat(NativeView source, TrackLayout layout) {
            Objects.requireNonNull(source); Objects.requireNonNull(layout);
            if (source.authenticated) throw new IllegalArgumentException("Actual sources require fromNative");
            return new TrackView(source,layout,source.instructions,false);
        }
        public boolean authenticated() { return authenticated; }
        @Override public boolean equals(Object other) {
            if (this==other) return true;
            return other instanceof TrackView v && authenticated==v.authenticated && source.equals(v.source)
                    && layout.equals(v.layout) && instructions.equals(v.instructions);
        }
        @Override public int hashCode() { return Objects.hash(source,layout,instructions,authenticated); }
        private void trackData(int start,int length) {
            for (int i=0;i<length;i++) {
                int address=add16(start,i);
                if (address<RAM_BYTES && instructions.get(address)) {
                    throw new IllegalArgumentException("Track table intersects instruction classification");
                }
            }
        }
        private static void trackCode(byte[] bank,BitSet code,int address,String hex) {
            expect(bank,address,hex); code.set(address,address+hex.length()/2);
        }
    }

    public record TrackCall(int ix) { public TrackCall { wordWidth(ix); } }
    /** Completed-command planning bound, not a clock, note count or instruction budget. */
    public record TrackLimits(int maxCommands) {
        public TrackLimits { if (maxCommands<0) throw new IllegalArgumentException("Negative command bound"); }
    }
    public enum TrackExit { ORDINARY_RETURN, STOP_COMMAND_EXIT }
    public sealed interface TrackStop permits MemoryStop, CommandStop, RoutineStop, CommandLimitStop { }
    public record MemoryStop(Fault fault) implements TrackStop {
        public MemoryStop { Objects.requireNonNull(fault); }
    }
    public record CommandStop(int commandAddress, int opcode, int targetAddress) implements TrackStop {
        public CommandStop { wordWidth(commandAddress); trackOpcode(opcode); wordWidth(targetAddress); }
    }
    public enum TrackRoutine {
        INIT_VIBRATO_BODY, UPDATE_VIBRATO_BODY, RAW_FREQUENCY_PARSE,
        PORTAMENTO_NOTE_PARSE, SPECIAL_FM3_FREQUENCY
    }
    public record RoutineStop(TrackRoutine routine) implements TrackStop {
        public RoutineStop { Objects.requireNonNull(routine); }
    }
    public record CommandLimitStop(int commandAddress, int opcode, int completedCommands) implements TrackStop {
        public CommandLimitStop {
            wordWidth(commandAddress); trackOpcode(opcode);
            if (completedCommands<0) throw new IllegalArgumentException("Negative completed command count");
        }
    }
    public sealed interface TrackResult permits TrackReturned, TrackBoundedFault {
        State projected();
        List<Step> steps();
    }
    public record TrackReturned(State projected, List<Step> steps, TrackExit exit) implements TrackResult {
        public TrackReturned {
            Objects.requireNonNull(projected); steps=List.copyOf(Objects.requireNonNull(steps)); Objects.requireNonNull(exit);
        }
    }
    /** Reached prefix only, wholly UNCOMMITTED. Supplying projected to another call starts a new entry. */
    public record TrackBoundedFault(State projected, List<Step> steps, TrackStop stop) implements TrackResult {
        public TrackBoundedFault {
            Objects.requireNonNull(projected); steps=List.copyOf(Objects.requireNonNull(steps)); Objects.requireNonNull(stop);
        }
    }

    /**
     * One explicit UpdateTrack(IX), without play-bit scan, queue arbitration, PlaySoundID or a device.
     * The private bridge clones the exact current State; it never resupplies ingress or reinstalls source.
     */
    public static TrackResult planTrack(TrackCall call, State before, TrackView view, TrackLimits limits) {
        Objects.requireNonNull(call); Objects.requireNonNull(before); Objects.requireNonNull(view); Objects.requireNonNull(limits);
        if (!view.source.equals(before.source)) throw new IllegalArgumentException("State belongs to another track source/format");
        TrackProjection track=new TrackProjection(before,view,call.ix(),limits.maxCommands());
        try {
            TrackExit exit=track.update();
            return new TrackReturned(track.p.state(),track.p.steps,exit);
        } catch (Boundary boundary) {
            return new TrackBoundedFault(track.p.state(),track.p.steps,new MemoryStop(boundary.fault));
        } catch (TrackBoundary boundary) {
            return new TrackBoundedFault(track.p.state(),track.p.steps,boundary.stop);
        }
    }

    private static void trackOpcode(int opcode) {
        if (opcode<0xE0 || opcode>0xFF) throw new IllegalArgumentException("Expected primary track command");
    }
    private static final class TrackBoundary extends RuntimeException {
        private static final long serialVersionUID=1L;
        private final TrackStop stop;
        private TrackBoundary(TrackStop stop) { super(null,null,false,false); this.stop=stop; }
    }

    /**
     * Narrow same-enclosing-owner bridge over Projection's current RAM and ordered logical intentions.
     * No inherited Projection operation is changed. Additional code protection applies to track stores
     * only; it neither grants ownership nor defines unknown bytes. Native implicit CPU stack is outside
     * this byte projection; Stop's two POPs are represented solely by STOP_COMMAND_EXIT.
     */
    private static final class TrackProjection {
        private final Projection p;
        private final TrackView view;
        private final int ix;
        private final int maxCommands;
        private int de;
        private int completedCommands;
        private TrackProjection(State before,TrackView view,int ix,int maxCommands) {
            this.view=view; this.ix=ix; this.maxCommands=maxCommands;
            p=new Projection(before,view.source);
        }
        private int at(int displacement) { return add16(ix,displacement); }
        private int field(int displacement) { return p.read(at(displacement)); }
        private void write(int address,int value) {
            if (address<RAM_BYTES && view.instructions.get(address)) {
                p.boundary(FaultKind.SELF_MODIFICATION,Access.WRITE,address);
            }
            p.write(address,value);
        }
        private void store(int displacement,int value) { write(at(displacement),value); }
        private void routine(TrackRoutine routine) { throw new TrackBoundary(new RoutineStop(routine)); }
        private void forceKeyOff() {
            store(0,field(0)&0xBF);
            p.ym(0,0x28,field(1));
        }
        private void keyOff() { if ((field(0)&6)==0) forceKeyOff(); }
        private void keyOn() {
            int low=field(0xD), high=field(0xE); // OR always reads high, including when low is nonzero.
            if ((low|high)==0 || (field(0)&6)!=0) return;
            p.ym(0,0x28,field(1)|0xF0);
        }
        /** Return native A: suppression leaves the register ID, an emitted leaf returns its data. */
        private int ymTrack(int register,int value) {
            int part=(field(1)&4)!=0 ? 1:0;
            if ((field(0)&4)!=0) return register;
            int adjusted=(register+field(1)-(part==1 ? 4:0))&255;
            p.ym(part,adjusted,value);
            return value;
        }
        private TrackExit update() {
            int count=(field(0xB)-1)&255;
            store(0xB,count); // UpdateDuration:420-424, first effect even for an inactive raw IX.
            if (count==0) {
                if (parse()) return TrackExit.STOP_COMMAND_EXIT;
                if ((field(0)&0x10)!=0) return TrackExit.ORDINARY_RETURN;
                if ((field(7)&0x80)!=0 && (field(0)&2)==0) routine(TrackRoutine.INIT_VIBRATO_BODY);
                int frequency=portamento();
                vibrato(); frequency(frequency); keyOn();
            } else {
                if ((field(0)&0x10)!=0) return TrackExit.ORDINARY_RETURN;
                if (field(0x1E)!=0) {
                    int staccato=(field(0x1E)-1)&255;
                    store(0x1E,staccato);
                    if (staccato==0) { keyOff(); return TrackExit.ORDINARY_RETURN; }
                }
                int frequency=portamento();
                if ((field(0)&0x40)!=0) return TrackExit.ORDINARY_RETURN;
                vibrato(); frequency(frequency);
            }
            return TrackExit.ORDINARY_RETURN;
        }
        private void vibrato() {
            if (field(7)!=0) routine(TrackRoutine.UPDATE_VIBRATO_BODY);
        }
        private int portamento() {
            int speed=field(0x10);
            int high=field(0xE), low=field(0xD); // Native service reads HIGH before LOW.
            int frequency=(((high<<8)|low)+(byte)speed)&0xFFFF;
            int fnum=frequency&0x7FF;
            if (fnum<=0x283) frequency=add16(frequency,-0x57B);
            else if (fnum>0x508) frequency=add16(frequency,0x57C);
            if ((field(0)&0x20)!=0) {
                store(0xE,frequency>>>8); store(0xD,frequency&255); // Native HIGH then LOW writeback.
            }
            return frequency;
        }
        private void frequency(int frequency) {
            if ((field(0)&4)!=0) return;
            if ((field(0)&1)!=0 && field(1)==2) routine(TrackRoutine.SPECIAL_FM3_FREQUENCY);
            ymTrack(0xA4,frequency>>>8); ymTrack(0xA0,frequency&255);
        }
        /** True only for the native nonlocal stop exit; no Finish/DataPtr stores on that path. */
        private boolean parse() {
            de=p.word(at(3));
            store(0,field(0)&0xFD); store(0,field(0)&0xEF);
            while (true) {
                int commandAddress=de;
                int opcode=p.read(de); de=add16(de,1);
                if (opcode>=0xE0) {
                    // Explicit support limit: after fetch, before table or mandatory look-ahead.
                    if (completedCommands>=maxCommands) {
                        throw new TrackBoundary(new CommandLimitStop(commandAddress,opcode,completedCommands));
                    }
                    int target=p.word(add16(view.layout.commandTableAddress(),2*(opcode-0xE0)));
                    int parameter=p.read(de); // TrackCommand reads even for operandless handlers.
                    TrackHandler handler=null;
                    for (var entry:view.layout.handlers().entrySet()) if (entry.getValue()==target) {
                        handler=entry.getKey(); break;
                    }
                    if (handler==null) throw new TrackBoundary(new CommandStop(commandAddress,opcode,target));
                    switch (handler) {
                        case PANNING -> {
                            int pan=field(0xA)&0x3F;
                            pan|=p.read(de); store(0xA,pan); ymTrack(0xB4,pan);
                        }
                        case INSTRUMENT -> instrument();
                        case LEGATO -> { store(0,field(0)|2); de=add16(de,-1); }
                        case STOP -> {
                            store(0,field(0)&0x7F); write(0x1C15,0x1F);
                            field(1); forceKeyOff(); write(0x1C18,0);
                            return true;
                        }
                        case JUMP -> de=add16(p.word(de),-1);
                        case REPEAT -> repeat(parameter);
                    }
                    completedCommands++;
                    de=add16(de,1); // TrackCommand.Done, including the handler's own DE adjustments.
                    continue;
                }
                keyOff();
                if ((field(0)&8)!=0) routine(TrackRoutine.RAW_FREQUENCY_PARSE);
                if (opcode<0x80) {
                    duration(opcode);
                } else {
                    if (opcode==0x80) store(0,field(0)|0x10);
                    else {
                        int note=(opcode-0x81+field(5))&255;
                        int block=(note/12*8)&255;
                        int frequency=p.word(add16(view.layout.freqTableAddress(),2*(note%12)));
                        store(0xD,frequency&255); store(0xE,(frequency>>>8)|block);
                    }
                    if ((field(0)&0x20)!=0) routine(TrackRoutine.PORTAMENTO_NOTE_PARSE);
                    int next=p.read(de);
                    if (next<0x80) { de=add16(de,1); duration(next); }
                    else store(0xB,field(0xC)); // Implicit duration: this store precedes Finish's second store.
                }
                finish();
                return false;
            }
        }
        private void duration(int value) {
            int multiplier=field(2); // Native DEC B/DJNZ: multiplier0 yields 256*input modulo256=0.
            store(0xC,(value*multiplier)&255);
        }
        private void finish() {
            store(3,de&255); store(4,de>>>8); store(0xB,field(0xC));
            if ((field(0)&2)!=0) return;
            store(0x25,0); store(0x22,0); store(0x17,0); // Shipped Finish clears only LOW VibFreq.
            store(0x1E,field(0x1F));
        }
        private void instrument() {
            for (int register:new int[]{0x80,0x84,0x88,0x8C}) ymTrack(register,0xFF);
            int id=p.read(de); store(8,id);
            int voice=add16(p.word(at(0x2A)),0x19*id);
            int registers=view.layout.instrumentRegsAddress();
            ymTrack(0xB4,field(0xA));
            for (int n=0;n<21;n++) {
                int register=p.read(registers); registers=add16(registers,1);
                int value=p.read(voice); voice=add16(voice,1);
                int returnedA=ymTrack(register,value);
                if (n==0) store(0x1B,returnedA);
            }
            store(0x1C,voice&255); store(0x1D,voice>>>8);
            int tl=p.word(at(0x1C));
            int tlRegisters=view.layout.insTlRegsAddress();
            for (int n=0;n<4;n++) {
                int level=p.read(tl);
                if ((level&0x80)!=0) level=(level+field(6))&255;
                level&=0x7F;
                int register=p.read(tlRegisters);
                ymTrack(register,level);
                tl=add16(tl,1); tlRegisters=add16(tlRegisters,1);
            }
        }
        private void repeat(int index) {
            de=add16(de,1);
            int counter=add16(ix,(index+0x28)&255); // ADD A wraps before the unsigned IX displacement.
            if (p.read(counter)==0) write(counter,p.read(de));
            de=add16(de,1);
            int remaining=(p.read(counter)-1)&255; write(counter,remaining);
            if (remaining!=0) de=add16(p.word(de),-1);
            else de=add16(de,1); // Done adds the second skip; no target read when count expires.
        }
    }
}
