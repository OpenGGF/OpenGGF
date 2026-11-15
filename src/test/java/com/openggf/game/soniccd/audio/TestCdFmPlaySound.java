package com.openggf.game.soniccd.audio;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static com.openggf.game.soniccd.audio.CdFmPlaySound.*;
import static org.junit.jupiter.api.Assertions.*;

/** Declared-format specifications plus optional retained USA-bank checks; no physical execution. */
class TestCdFmPlaySound {
    private static final Layout LAYOUT = new Layout(8, 0x322, 0x3BB);

    private static final class Fixture {
        final byte[] image = new byte[8192];
        final BitSet defined = new BitSet(8192);
        final BitSet writable = new BitSet(8192);
        final BitSet instructions = new BitSet(8192);
        Fixture() {
            writable.set(0, 8192);
            instructions.set(8, 11);
            writable.clear(8, 11);
            put(8, 0x21, 0x00, 0x02); // Declared GetList operand: DriverInfo=0200.
            word(0x206, 0x600);
            for (int i = 0; i < 80; i++) word(0x600 + 2 * i, 0x1000);
            put(0x322, 0x80,0, 0x80,1, 0x80,2, 0x80,4, 0x80,5, 0x80,6);
            put(0x3BB, 0x40,0x1C, 0x70,0x1C, 0xA0,0x1C,
                    0xD0,0x1C, 0x00,0x1D, 0x30,0x1D);
            for (int a = 0x1C00; a < 0x1D60; a++) put(a, 0xA5);
            put(0x1C09, 0x95);
            put(0x1C18, 0x71);
            put(0x1C3D, 0xA5, 1);
            put(0x1000, 0x34,0x12,2,1, 0x80,4,0x78,0x56,0xF4,0xFF);
        }
        Fixture put(int address, int... values) {
            for (int v : values) { image[address] = (byte) v; defined.set(address++); }
            return this;
        }
        Fixture word(int address, int value) { return put(address, value & 255, value >>> 8); }
        NativeView view() {
            return NativeView.declaredFormat("hand-derived-r1", image, defined, writable, instructions, LAYOUT);
        }
        State state(NativeView view) { return State.supplied(view, List.of()); }
    }

    private static Returned returned(Result r) { return assertInstanceOf(Returned.class, r); }
    private static BoundedFault fault(Result r, FaultKind kind, Access access, int address) {
        BoundedFault f = assertInstanceOf(BoundedFault.class, r);
        assertEquals(new Fault(kind, access, address), f.fault());
        return f;
    }
    private static List<YmRegisterIntent> ym(Result r) {
        return r.steps().stream().filter(YmRegisterIntent.class::isInstance)
                .map(YmRegisterIntent.class::cast).toList();
    }
    private static List<ByteStore> stores(Result r) {
        return r.steps().stream().filter(ByteStore.class::isInstance).map(ByteStore.class::cast).toList();
    }
    private static void untouched(State before, Result r) {
        BitSet written = new BitSet(8192);
        for (ByteStore s : stores(r)) written.set(s.address());
        for (int a = 0; a < 8192; a++) {
            if (!written.get(a)) {
                assertEquals(before.definedAt(a), r.projected().definedAt(a), "defined at " + a);
                if (before.definedAt(a)) assertEquals(before.unsignedByte(a), r.projected().unsignedByte(a), "byte at " + a);
            }
        }
    }
    private static List<Step> headerPrefix() {
        return List.of(new ByteStore(0x1C39,0x34), new ByteStore(0x1C3A,0x12),
                new ByteStore(0x1C15,0), new ByteStore(0x1C3B,2), new ByteStore(0x1C32,3));
    }

    @Test void allRawDispatchBytesKeepTheirNativeClasses() {
        for (int raw = 0; raw < 256; raw++) {
            Fixture f = new Fixture().put(0x1C09, raw);
            NativeView v = f.view(); State s = f.state(v);
            Returned r = returned(plan(Entry.PLAY_SOUND_ID, s, v));
            if (raw >= 0x80 && raw < 0x90) {
                assertEquals(s, r.projected()); assertTrue(r.steps().isEmpty());
            } else if (raw >= 0x90 && raw < 0xE0) {
                assertEquals(headerPrefix(), r.steps().subList(0,5));
                assertEquals(5, ym(r).size());
            } else {
                assertEquals(79, ym(r).size());
                assertEquals(0, r.projected().unsignedByte(0x1C18));
            }
            assertEquals(raw >= 0x80 && raw < 0x90 ? raw : 0x80, r.projected().unsignedByte(0x1C09));
            untouched(s, r);
        }
    }

    @Test void fm4HasLiteralDefaultFieldsAndDoesNotDereferencePayloadOrVoice() {
        Fixture f = new Fixture(); NativeView v = f.view(); State s = f.state(v);
        Returned r = returned(plan(Entry.PLAY_SOUND_ID, s, v));
        int[] first = {0x80,4,2,0x78,0x56,0xF4,0xFF,0,0,0x30,0xC0,1};
        for (int i=0;i<first.length;i++) assertEquals(first[i], r.projected().unsignedByte(0x1CD0+i));
        for (int i=12;i<48;i++) assertEquals(i==42 ? 0x34 : i==43 ? 0x12 : 0,
                r.projected().unsignedByte(0x1CD0+i));
        assertEquals(List.of(new YmRegisterIntent(0,0x28,4), new YmRegisterIntent(1,0x90,0),
                new YmRegisterIntent(1,0x94,0), new YmRegisterIntent(1,0x98,0),
                new YmRegisterIntent(1,0x9C,0)), ym(r));
        assertEquals(2, stores(r).stream().filter(x -> x.equals(new ByteStore(0x1CD0,0x80))).count());
        assertEquals(new ByteStore(0x1C09,0x80), r.steps().getLast());
        assertEquals(0x71,r.projected().unsignedByte(0x1C18));
        assertEquals(0xA5,r.projected().unsignedByte(0x1C3D));
        assertEquals(1,r.projected().unsignedByte(0x1C3E));
        assertFalse(r.projected().definedAt(0x1234)); assertFalse(r.projected().definedAt(0x567));
        untouched(s,r);
    }

    @Test void oldChannelTwoControlsEarlyResetForEveryNativeChannel() {
        int[] channels={0,1,2,4,5,6};
        for(int i=0;i<channels.length;i++) for(int old : new int[]{0,2}) {
            int target=0x1C40+48*i;
            Fixture f=new Fixture().put(0x1005,channels[i]).put(target+1,old);
            NativeView v=f.view();State s=f.state(v);Returned r=returned(plan(Entry.PLAY_SOUND_ID,s,v));
            assertEquals(channels[i],r.projected().unsignedByte(target+1));
            assertEquals(old==2 ? 6:5,ym(r).size());
            assertEquals(old==2 ? 2:1,stores(r).stream().filter(x->x.address()==0x1C09).count());
            if(old==2) {
                int at=r.steps().indexOf(new ByteStore(target,0x80));
                assertEquals(List.of(new ByteStore(0x1C12,15),new YmRegisterIntent(0,0x27,15),
                        new ByteStore(0x1C09,0x80),new ByteStore(target+1,channels[i])),r.steps().subList(at+1,at+5));
            }
            untouched(s,r);
        }
    }

    @Test void flagsSeparateLegatoSoundOffAndVibratoRmw() {
        for(int flags : new int[]{0x80,0x82,0x84,0xC0,0xEF}) {
            Fixture f=new Fixture().put(0x1004,flags);NativeView v=f.view();State s=f.state(v);
            Returned r=returned(plan(Entry.PLAY_SOUND_ID,s,v));
            int expected=(flags&4)!=0 ? 0 : (flags&2)!=0 ? 4 : 5;
            assertEquals(expected,ym(r).size());
            assertEquals((flags&6)!=0 ? flags : flags&0xBF,r.projected().unsignedByte(0x1CD0));
            assertEquals((flags&6)!=0 ? 1:2,stores(r).stream().filter(x->x.address()==0x1CD0).count());
            untouched(s,r);
        }
    }

    @Test void duplicateChannelsReplaceTwiceWithoutDroppingEarlierIntents() {
        Fixture f=new Fixture().put(0x1002,1,2,0x80,4,0x11,0x11,1,2,0x80,4,0x22,0x22,3,4);
        NativeView v=f.view();State s=f.state(v);Returned r=returned(plan(Entry.PLAY_SOUND_ID,s,v));
        assertEquals(10,ym(r).size());assertEquals(0x22,r.projected().unsignedByte(0x1CD3));
        assertEquals(3,r.projected().unsignedByte(0x1CD5));assertEquals(4,r.projected().unsignedByte(0x1CD6));
        assertTrue(stores(r).contains(new ByteStore(0x1CD3,0x11)));untouched(s,r);
    }

    @Test void midCopyAndEarlyWordFaultsKeepOnlyReachedUncommittedPrefix() {
        Fixture f=new Fixture();f.defined.clear(0x1007);NativeView v=f.view();State s=f.state(v);
        BoundedFault r=fault(plan(Entry.PLAY_SOUND_ID,s,v),FaultKind.UNKNOWN_READ,Access.READ,0x1007);
        var expected=new ArrayList<Step>(headerPrefix());
        expected.addAll(List.of(new ByteStore(0x1CD0,0x80),new ByteStore(0x1CD1,4),
                new ByteStore(0x1CD2,2),new ByteStore(0x1CD3,0x78)));
        assertEquals(expected,r.steps());assertEquals(0xA5,r.projected().unsignedByte(0x1CD4));
        assertEquals(0x95,r.projected().unsignedByte(0x1C09));untouched(s,r);
        f=new Fixture();f.defined.clear(0x1001);v=f.view();s=f.state(v);
        assertTrue(fault(plan(Entry.PLAY_SOUND_ID,s,v),FaultKind.UNKNOWN_READ,Access.READ,0x1001).steps().isEmpty());
    }

    @Test void unknownOldChannelIsReadAfterFlagsStoreAndUnknownOwnedFlagsCanBeAssigned() {
        Fixture f=new Fixture();f.defined.clear(0x1CD0,0x1CD2);NativeView v=f.view();State s=f.state(v);
        BoundedFault r=fault(plan(Entry.PLAY_SOUND_ID,s,v),FaultKind.UNKNOWN_READ,Access.READ,0x1CD1);
        assertEquals(new ByteStore(0x1CD0,0x80),r.steps().getLast());
        assertTrue(r.projected().definedAt(0x1CD0));assertFalse(r.projected().definedAt(0x1CD1));untouched(s,r);
    }

    @Test void countZeroReaches256RowsAnd255Reaches255WithoutSixChannelClamp() {
        for(int count:new int[]{0,255}) {
            Fixture f=new Fixture().put(0x1003,count);
            int n=count==0 ? 256:255;
            for(int i=0;i<n;i++) f.put(0x1004+6*i,0x84,4,i&255,(i>>>8)&255,0,0);
            NativeView v=f.view();State s=f.state(v);Returned r=returned(plan(Entry.PLAY_SOUND_ID,s,v));
            assertEquals(n,stores(r).stream().filter(x->x.address()==0x1C32).count());
            assertEquals((n-1)&255,r.projected().unsignedByte(0x1CD3));assertTrue(ym(r).isEmpty());untouched(s,r);
        }
        Fixture f=new Fixture().put(0x1003,0);NativeView v=f.view();State s=f.state(v);
        BoundedFault r=fault(plan(Entry.PLAY_SOUND_ID,s,v),FaultKind.UNKNOWN_READ,Access.READ,0x100B);
        assertEquals(5,ym(r).size());assertEquals(0x95,r.projected().unsignedByte(0x1C09));untouched(s,r);
    }

    @Test void uncheckedSevenCanWriteReviewedBankDataAndHighChannelArithmeticWraps() {
        for(int channel:new int[]{7,255}) {
            Fixture f=new Fixture().put(0x1005,channel);
            int target=channel==7 ? 0x921:0x1CD0;
            f.word(channel==7 ? 0x3C7:0x5B7,target).put(target+1,0);
            NativeView v=f.view();State s=f.state(v);Returned r=returned(plan(Entry.PLAY_SOUND_ID,s,v));
            assertEquals(channel,r.projected().unsignedByte(target+1));
            assertEquals(channel==7 ? 6:254,r.projected().unsignedByte(0x1C32));
            assertEquals(new YmRegisterIntent(0,0x28,channel),ym(r).getFirst());
            assertEquals(new YmRegisterIntent(1,channel==7 ? 0x93:0x8B,0),ym(r).get(1));untouched(s,r);
        }
    }

    @Test void busInstructionAndUnownedTargetsHaveDifferentReachedSupportBoundaries() {
        for(int target:new int[]{0x4000,8,0x921}) {
            Fixture f=new Fixture().put(0x1005,255).word(0x5B7,target);
            if(target==0x921) f.writable.clear(target);
            NativeView v=f.view();State s=f.state(v);
            FaultKind k=target==0x4000 ? FaultKind.OUTSIDE_RAM:target==8 ? FaultKind.SELF_MODIFICATION:FaultKind.UNOWNED_WRITE;
            BoundedFault r=fault(plan(Entry.PLAY_SOUND_ID,s,v),k,Access.WRITE,target);
            assertEquals(new ByteStore(0x1C32,254),r.steps().getLast());assertEquals(5,r.steps().size());untouched(s,r);
        }
    }

    @Test void indexAdditionWraps16ButWordHighAndLowNeverMirrorInto8kb() {
        Fixture f=new Fixture().word(0x206,0xFFFF).put(0x1C09,0x91).word(1,0x1000);
        NativeView v=f.view();State s=f.state(v);Returned wrapped=returned(plan(Entry.PLAY_SOUND_ID,s,v));
        assertEquals(5,ym(wrapped).size());untouched(s,wrapped);
        f=new Fixture().word(0x206,0x1FFF).put(0x1FFF,0x00).put(0x1C09,0x90);
        v=f.view();s=f.state(v);
        assertTrue(fault(plan(Entry.PLAY_SOUND_ID,s,v),FaultKind.OUTSIDE_RAM,Access.READ,0x2000).steps().isEmpty());
        f=new Fixture().word(0x206,0xFFFF).put(0x1C09,0x90);v=f.view();s=f.state(v);
        assertTrue(fault(plan(Entry.PLAY_SOUND_ID,s,v),FaultKind.OUTSIDE_RAM,Access.READ,0xFFFF).steps().isEmpty());
    }

    @Test void currentProjectedGlobalsAreReadAfterAliasingTrackWrites() {
        Fixture f=new Fixture().word(0x3C1,0x1C37);NativeView v=f.view();State s=f.state(v);
        Returned r=returned(plan(Entry.PLAY_SOUND_ID,s,v));
        assertEquals(2,r.projected().unsignedByte(0x1C61));
        assertEquals(0x78,r.projected().unsignedByte(0x1C62));
        assertEquals(0x56,r.projected().unsignedByte(0x1C3B));untouched(s,r);
    }

    @Test void stopHasExact343ClearSixSameValueRmwAnd79OrderedYmIntents() {
        Fixture f=new Fixture();NativeView v=f.view();State s=f.state(v);
        Returned r=returned(plan(Entry.STOP_SOUND,s,v));var expected=new ArrayList<Step>();
        for(int a=0x1C09;a<0x1D60;a++) expected.add(new ByteStore(a,0));
        int[] channels={0,1,2,4,5,6};
        for(int i=0;i<6;i++) {
            int c=channels[i],part=c>=4 ? 1:0,add=c>=4 ? c-4:c;
            for(int reg:new int[]{0x80,0x84,0x88,0x8C}) expected.add(new YmRegisterIntent(part,reg+add,255));
            for(int reg:new int[]{0x40,0x44,0x48,0x4C}) expected.add(new YmRegisterIntent(part,reg+add,127));
            expected.add(new ByteStore(0x322+2*i,0x80));expected.add(new YmRegisterIntent(0,0x28,c));
            for(int reg:new int[]{0x90,0x94,0x98,0x9C}) expected.add(new YmRegisterIntent(part,reg+add,0));
        }
        expected.addAll(List.of(new ByteStore(0x1C0D,0),new ByteStore(0x1C12,15),
                new YmRegisterIntent(0,0x27,15),new ByteStore(0x1C09,0x80)));
        assertEquals(expected,r.steps());assertEquals(79,ym(r).size());assertEquals(352,stores(r).size());
        for(int a=0x1C09;a<0x1D60;a++) assertEquals(a==0x1C09 ? 0x80:a==0x1C12 ? 15:0,r.projected().unsignedByte(a));
        untouched(s,r);
    }

    @Test void suppliedTableOverlayControlsStopWithoutMutatingItsSourceView() {
        Fixture f=new Fixture();NativeView v=f.view();State original=f.state(v);
        State overlaid=State.supplied(v,List.of(new ByteStore(0x324,0x84)));
        Returned r=returned(plan(Entry.STOP_SOUND,overlaid,v));
        assertEquals(67,ym(r).size()); // Sound-off suppresses 12 track writes, never ForceKeyOff.
        assertEquals(0x84,r.projected().unsignedByte(0x324));
        assertEquals(0x80,original.unsignedByte(0x324));
        assertEquals(79,ym(returned(plan(Entry.STOP_SOUND,original,v))).size());untouched(overlaid,r);
    }

    @Test void stopDoesNotPrevalidateLaterTableReadOrDefineGapStackAndLeadingReserve() {
        Fixture f=new Fixture();f.defined.clear(0x323);f.defined.clear(0x1C00,0x1C09);
        NativeView v=f.view();State s=f.state(v);
        BoundedFault r=fault(plan(Entry.STOP_SOUND,s,v),FaultKind.UNKNOWN_READ,Access.READ,0x323);
        assertEquals(343,r.steps().size());assertTrue(ym(r).isEmpty());
        assertFalse(r.projected().definedAt(0x1C00));assertFalse(r.projected().definedAt(0x1AD6));
        assertFalse(r.projected().definedAt(0x1FFD));untouched(s,r);
    }

    @Test void immutableContentBindingMaskCaptureAndNewEntryReplay() {
        Fixture f=new Fixture();NativeView v=f.view();State s=f.state(v);
        Fixture other=new Fixture();NativeView v2=other.view();State s2=other.state(v2);
        assertEquals(v,v2);assertEquals(v.hashCode(),v2.hashCode());assertEquals(s,s2);assertEquals(s.hashCode(),s2.hashCode());
        Result r=plan(Entry.PLAY_SOUND_ID,s,v);assertEquals(r,plan(Entry.PLAY_SOUND_ID,s2,v2));
        assertEquals(s,s.capture());assertEquals(s2,s);untouched(s,r);
        assertThrows(UnsupportedOperationException.class,()->r.steps().add(new ByteStore(0,0)));
        f.image[0x1C18]=0;f.defined.clear();f.writable.clear();f.instructions.clear();
        assertEquals(s2,s);assertEquals(v2,v);
        other.defined.clear(0x1C00);NativeView v3=other.view();assertNotEquals(v2,v3);
        assertNotEquals(s2,other.state(v3));
        Fixture bad=new Fixture();bad.defined.clear(0x1007);NativeView vb=bad.view();State sb=bad.state(vb);
        Result prefix=plan(Entry.PLAY_SOUND_ID,sb,vb);
        assertEquals(prefix,plan(Entry.PLAY_SOUND_ID,sb.capture(),vb));
        Result newInvocation=plan(Entry.PLAY_SOUND_ID,prefix.projected(),vb);
        assertEquals(headerPrefix(),newInvocation.steps().subList(0,5)); // New entry, never a resume cursor.
    }

    @Test void factoriesRefuseDefaultsWidthsContradictionsAndMutatedInstructionIngress() {
        assertThrows(NullPointerException.class,()->NativeView.fromAssets(null));
        assertThrows(IllegalArgumentException.class,()->new ByteStore(-1,0));
        assertThrows(IllegalArgumentException.class,()->new ByteStore(0x2000,0));
        assertThrows(IllegalArgumentException.class,()->new ByteStore(0,-1));
        assertThrows(IllegalArgumentException.class,()->new ByteStore(0,256));
        assertThrows(IllegalArgumentException.class,()->new Layout(65536,0,0));
        assertThrows(IllegalArgumentException.class,()->new Layout(-1,0,0));
        assertThrows(IllegalArgumentException.class,()->new YmRegisterIntent(2,0,0));
        assertThrows(IllegalArgumentException.class,()->new YmRegisterIntent(0,256,0));
        Fixture f=new Fixture();NativeView v=f.view();State s=f.state(v);assertFalse(v.authenticated());
        assertThrows(NullPointerException.class,()->plan(null,s,v));
        assertThrows(NullPointerException.class,()->plan(Entry.PLAY_SOUND_ID,null,v));
        assertThrows(NullPointerException.class,()->State.supplied(v,null));
        assertThrows(IllegalStateException.class,()->s.unsignedByte(0x1234));
        assertThrows(IllegalArgumentException.class,()->State.supplied(v,List.of(new ByteStore(8,0))));
        assertEquals(s,State.supplied(v,List.of(new ByteStore(8,0x21))));
        var ingress=new ArrayList<ByteStore>();ingress.add(new ByteStore(0x1FFD,0x7A));
        State supplied=State.supplied(v,ingress);ingress.clear();assertEquals(0x7A,supplied.unsignedByte(0x1FFD));
        f.instructions.set(0x1234);f.writable.clear(0x1234);NativeView unknownCode=f.view();
        assertThrows(IllegalArgumentException.class,()->State.supplied(unknownCode,List.of(new ByteStore(0x1234,0))));
        Fixture mismatch=new Fixture().put(0x1C00,0);NativeView wrong=mismatch.view();
        assertThrows(IllegalArgumentException.class,()->plan(Entry.PLAY_SOUND_ID,s,wrong));
        f=new Fixture();f.writable.set(8);Fixture overlap=f;
        assertThrows(IllegalArgumentException.class,overlap::view);
        for(int mask=0;mask<3;mask++) {
            Fixture invalid=new Fixture();(mask==0 ? invalid.defined:mask==1 ? invalid.writable:invalid.instructions).set(8192);
            assertThrows(IllegalArgumentException.class,invalid::view);
        }
        Fixture anchor=new Fixture().put(8,0);assertThrows(IllegalArgumentException.class,anchor::view);
        assertThrows(IllegalArgumentException.class,()->NativeView.declaredFormat("bad",new byte[0],new BitSet(),new BitSet(),new BitSet(),LAYOUT));
        assertThrows(IllegalArgumentException.class,()->NativeView.declaredFormat("",new byte[8192],new BitSet(),new BitSet(),new BitSet(),LAYOUT));
    }

    /** Optional caller-supplied retained asset check; no media opening or execution is supplied here. UNRUN. */
    static void assertRetainedUsaAsset(CdFmSoundAssets assets) {
        NativeView v=NativeView.fromAssets(assets);assertTrue(v.authenticated());
        var ingress=new ArrayList<ByteStore>();
        for(int a=0x1C00;a<0x1D60;a++) ingress.add(new ByteStore(a,0xA5));
        ingress.add(new ByteStore(0x1C09,0x95));
        State before=State.supplied(v,ingress);Returned r=returned(plan(Entry.PLAY_SOUND_ID,before,v));
        assertEquals(0x68,r.projected().unsignedByte(0x1CD3));assertEquals(9,r.projected().unsignedByte(0x1CD4));
        assertEquals(0x74,r.projected().unsignedByte(0x1CFA));assertEquals(9,r.projected().unsignedByte(0x1CFB));
        assertEquals(5,ym(r).size());untouched(before,r);
        Returned stop=returned(plan(Entry.STOP_SOUND,before,v));assertEquals(79,ym(stop).size());
        assertEquals(0x80,stop.projected().unsignedByte(0x322));untouched(before,stop);
    }

    @Test void actualRetainedUsaBankAdmitsAnchorsAndPlansAfterTheDiscOwnerCloses() throws Exception {
        String root=System.getProperty("soniccd.media.root");
        org.junit.jupiter.api.Assumptions.assumeTrue(root!=null&&!root.isBlank(),"Set soniccd.media.root for actual retained USA FM checks");
        CdFmSoundAssets retained;
        try(var files=java.nio.file.Files.list(java.nio.file.Path.of(root).resolve("USA"))) {
            var cues=files.filter(p->p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1,cues.size(),"Actual USA directory must identify exactly one CUE");
            try(var programs=com.openggf.game.soniccd.media.CdProgramAssets.open(cues.getFirst(),
                    java.util.Optional.of(com.openggf.game.soniccd.CdReleaseVariant.USA),java.util.Optional.empty())) {
                retained=CdFmSoundAssets.fromPrograms(programs);
            }
        }
        assertRetainedUsaAsset(retained); // Detached actual owner, never a declared/fabricated authenticated image.
    }
}
