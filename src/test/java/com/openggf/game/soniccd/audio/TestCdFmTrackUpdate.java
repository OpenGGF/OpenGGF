package com.openggf.game.soniccd.audio;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.openggf.game.soniccd.audio.CdFmPlaySound.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * UNRUN, tests-first literal specifications from the frozen FM frontier and pinned N source.
 * Declared fixtures are not retained-bank evidence. Every projected vector checks all 8192 cells.
 * Native pin: 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, FM/_Driver.asm:157-768,1130-1840.
 */
class TestCdFmTrackUpdate {
    private static final int IX = 0x1C40;
    private static final String PREFIX = "S1c4b:00 S1c40:80 S1c40:80 ";
    private static final String OFF = "S1c40:80 Y0:28:00 ";
    private static final String RESET = "S1c65:00 S1c62:00 S1c57:00 S1c5e:00 ";
    private static final String NOTE = PREFIX + OFF + "S1c4d:84 S1c4e:02 S1c4c:03 "
            + "S1c43:02 S1c44:10 S1c4b:03 " + RESET + "Y0:a4:02 Y0:a0:84 Y0:28:f0";
    private static final String STOP = "S1c40:00 S1c15:1f S1c40:00 Y0:28:00 S1c18:00";
    private static final String RELEASE = "Y0:80:ff Y0:84:ff Y0:88:ff Y0:8c:ff ";
    private static final String VOICE = RELEASE + "S1c48:00 Y0:b4:c0 Y0:b0:04 S1c5b:04 "
            + "Y0:30:37 Y0:38:72 Y0:34:77 Y0:3c:49 "
            + "Y0:50:1f Y0:58:1f Y0:54:1f Y0:5c:1f "
            + "Y0:60:07 Y0:68:0a Y0:64:07 Y0:6c:0d "
            + "Y0:70:00 Y0:78:0b Y0:74:00 Y0:7c:0b "
            + "Y0:80:1f Y0:88:0f Y0:84:1f Y0:8c:0f "
            + "S1c5c:15 S1c5d:11 Y0:40:23 Y0:48:05 Y0:44:23 Y0:4c:05 ";

    private static Map<TrackHandler,Integer> handlers() {
        return new EnumMap<>(Map.of(TrackHandler.PANNING,0x700, TrackHandler.INSTRUMENT,0x710,
                TrackHandler.LEGATO,0x720, TrackHandler.STOP,0x730,
                TrackHandler.JUMP,0x740, TrackHandler.REPEAT,0x750));
    }
    private static final class Fixture {
        final byte[] image = new byte[8192];
        final BitSet known = new BitSet(8192);
        final BitSet owned = new BitSet(8192);
        final BitSet code = new BitSet(8192);
        TrackLayout layout = new TrackLayout(0x600,0x620,0x635,0x640,handlers());
        Fixture() {
            Arrays.fill(image,(byte)0xA5); // Unknown backing is deliberately not a fabricated RAM image.
            code.set(8,11); put(8,0x21,0,6);
            for (int a : new int[]{0x700,0x710,0x720,0x730,0x740,0x750}) {
                code.set(a); put(a,0xC9); // Explicit declared label identity, not assembled native code.
            }
            owned.set(0x600,0x680); owned.set(0x1000,0x1200); owned.set(0x1C00,0x1D60);
            hex(0x600,"8402ab02d302fe022d035c038f03c503ff033c047c04c004");
            hex(0x620,"b03038343c5058545c6068646c7078747c8088848c4048444c");
            for (int op=0xE0;op<=0xFF;op++) row(op,0x760);
            row(0xE0,0x700); row(0xEF,0x710); row(0xE7,0x720);
            row(0xF2,0x730); row(0xF6,0x740); row(0xF7,0x750);
            put(IX,0x80,0,1,0,0x10,0,5,0,0);
            put(IX+0xA,0xC0,1,5,0x84,2);
            put(IX+0x10,0); put(IX+0x17,0xA6); put(IX+0x1E,0,0);
            put(IX+0x22,0xA8,0x5A); put(IX+0x25,0xA7);
            put(IX+0x28,0); put(IX+0x2A,0,0x11);
            put(0x1C15,0x66); put(0x1C18,0x77); put(0x1C70,0x33);
            hex(0x1100,"04377277491f1f1f1f070a070d000b000b1f0f1f0f23802380");
        }
        Fixture put(int address,int... values) {
            for (int value:values) { image[address]=(byte)value; known.set(address++); }
            return this;
        }
        Fixture hex(int address,String hex) {
            byte[] bytes=java.util.HexFormat.of().parseHex(hex);
            for (byte b:bytes) put(address++,Byte.toUnsignedInt(b));
            return this;
        }
        Fixture word(int address,int value) { return put(address,value&255,value>>>8); }
        Fixture row(int opcode,int target) { return word(0x640+2*(opcode-0xE0),target); }
        NativeView source() { return NativeView.declaredFormat("track-literal-r1",image,known,owned,code,new Layout(8,0x322,0x3BB)); }
    }

    /** Literal notation only: Saddress:value and Ypart:register:value, all hexadecimal. */
    private static List<Step> literals(String text) {
        var result=new ArrayList<Step>();
        if (text.isBlank()) return List.of();
        for (String token:text.trim().split("\\s+")) {
            String[] fields=token.substring(1).split(":");
            int a=Integer.parseInt(fields[0],16), b=Integer.parseInt(fields[1],16);
            result.add(token.charAt(0)=='S' ? new ByteStore(a,b)
                    : new YmRegisterIntent(a,b,Integer.parseInt(fields[2],16)));
        }
        return List.copyOf(result);
    }
    private static TrackResult checked(State before,TrackView view,int ix,int budget,
                                       String expected,TrackStop stop,TrackExit exit) {
        State captured=before.capture();
        TrackResult result=planTrack(new TrackCall(ix),before,view,new TrackLimits(budget));
        assertEquals(literals(expected),result.steps());
        if (stop==null) assertEquals(exit,assertInstanceOf(TrackReturned.class,result).exit());
        else assertEquals(stop,assertInstanceOf(TrackBoundedFault.class,result).stop());
        // This oracle uses the literal stores, not the implementation's emitted stores or final diff.
        int[] expectedBytes=new int[8192]; Arrays.fill(expectedBytes,-1);
        for (int a=0;a<8192;a++) if (before.definedAt(a)) expectedBytes[a]=before.unsignedByte(a);
        for (Step step:literals(expected)) if (step instanceof ByteStore s) expectedBytes[s.address()]=s.value();
        for (int a=0;a<8192;a++) {
            assertEquals(expectedBytes[a]>=0,result.projected().definedAt(a),"defined identity at "+a);
            if (expectedBytes[a]>=0) assertEquals(expectedBytes[a],result.projected().unsignedByte(a),"value at "+a);
        }
        assertEquals(captured,before);
        assertEquals(result,planTrack(new TrackCall(ix),captured,view,new TrackLimits(budget)));
        assertEquals(result.projected(),result.projected().capture());
        assertThrows(UnsupportedOperationException.class,()->result.steps().add(new ByteStore(0,0)));
        return result;
    }
    private static TrackResult vector(Fixture fixture,String expected,TrackStop stop) {
        NativeView source=fixture.source();
        return checked(State.supplied(source,List.of()),TrackView.declaredFormat(source,fixture.layout),
                IX,16,expected,stop,TrackExit.ORDINARY_RETURN);
    }
    private static MemoryStop memory(FaultKind kind,Access access,int address) {
        return new MemoryStop(new Fault(kind,access,address));
    }

    @Test void firstNoteAndHeldTicksAreExactWholeImageProjections() {
        Fixture f=new Fixture().hex(0x1000,"81038202f200");
        NativeView source=f.source(); TrackView view=TrackView.declaredFormat(source,f.layout);
        State before=State.supplied(source,List.of());
        TrackResult first=checked(before,view,IX,0,NOTE,null,TrackExit.ORDINARY_RETURN);
        TrackResult held=checked(first.projected(),view,IX,0,"S1c4b:02 Y0:a4:02 Y0:a0:84",null,TrackExit.ORDINARY_RETURN);
        TrackResult last=checked(held.projected(),view,IX,0,"S1c4b:01 Y0:a4:02 Y0:a0:84",null,TrackExit.ORDINARY_RETURN);
        checked(last.projected(),view,IX,0,PREFIX+OFF+"S1c4d:ab S1c4e:02 S1c4c:02 S1c43:04 S1c44:10 S1c4b:02 "
                +RESET+"Y0:a4:02 Y0:a0:ab Y0:28:f0",null,TrackExit.ORDINARY_RETURN);
        assertEquals(0x5A,first.projected().unsignedByte(0x1C63));
        assertFalse(first.projected().definedAt(0x1FFD));
        assertThrows(IllegalStateException.class,()->first.projected().unsignedByte(0x1FFD));
    }

    @Test void implicitDurationRestAndDurationOnlyHaveDistinctNativeStores() {
        vector(new Fixture().hex(0x1000,"818204f200"),PREFIX+OFF+"S1c4d:84 S1c4e:02 S1c4b:05 "
                +"S1c43:01 S1c44:10 S1c4b:05 "+RESET+"Y0:a4:02 Y0:a0:84 Y0:28:f0",null);
        Fixture rest=new Fixture().hex(0x1000,"8001f200"); rest.known.clear(IX+0xD,IX+0xF);
        vector(rest,PREFIX+OFF+"S1c40:90 S1c4c:01 S1c43:02 S1c44:10 S1c4b:01 "+RESET,null);
        vector(new Fixture().hex(0x1000,"03f200"),PREFIX+OFF+"S1c4c:03 S1c43:01 S1c44:10 S1c4b:03 "
                +RESET+"Y0:a4:02 Y0:a0:84 Y0:28:f0",null);
        vector(new Fixture().put(IX+2,0).hex(0x1000,"8103"),PREFIX+OFF+"S1c4d:84 S1c4e:02 S1c4c:00 "
                +"S1c43:02 S1c44:10 S1c4b:00 "+RESET+"Y0:a4:02 Y0:a0:84 Y0:28:f0",null);
    }

    @Test void heldRestStaccatoUnderflowAndVibEndRespectEarlyReturns() {
        vector(new Fixture().put(IX,0x90).put(IX+0xB,2),"S1c4b:01",null);
        vector(new Fixture().put(IX+0xB,0),"S1c4b:ff Y0:a4:02 Y0:a0:84",null);
        vector(new Fixture().put(IX+0xB,2).put(IX+0x1E,1),"S1c4b:01 S1c5e:00 S1c40:80 Y0:28:00",null);
        vector(new Fixture().put(IX,0x82).put(IX+0xB,2).put(IX+0x1E,1),"S1c4b:01 S1c5e:00",null);
        vector(new Fixture().put(IX,0xC0).put(IX+0xB,2).put(IX+7,1),"S1c4b:01",null);
        vector(new Fixture().put(IX+0xB,2).put(IX+0x1E,2),"S1c4b:01 S1c5e:01 Y0:a4:02 Y0:a0:84",null);
    }

    @Test void portamentoArithmeticRunsEvenWhenItsWritebackFlagIsClear() {
        vector(new Fixture().put(IX+0xB,2).put(IX+0xD,0,0),"S1c4b:01 Y0:a4:fa Y0:a0:85",null);
        vector(new Fixture().put(IX,0xA0).put(IX+0xB,2).put(IX+0xD,0x83,2),
                "S1c4b:01 S1c4e:fd S1c4d:08 Y0:a4:fd Y0:a0:08",null);
        vector(new Fixture().put(IX,0xA0).put(IX+0xB,2).put(IX+0xD,8,5).put(IX+0x10,1),
                "S1c4b:01 S1c4e:0a S1c4d:85 Y0:a4:0a Y0:a0:85",null);
        vector(new Fixture().put(IX+0xB,2).put(IX+0xD,0x85,2).put(IX+0x10,0xFF),
                "S1c4b:01 Y0:a4:02 Y0:a0:84",null);
    }

    @Test void instrumentPanningAndLegatoKeepLiteralOrderingAndSuppressedReturnA() {
        vector(new Fixture().hex(0x1000,"ef00e0408103f200"),PREFIX+VOICE+"S1c4a:40 Y0:b4:40 "+OFF
                +"S1c4d:84 S1c4e:02 S1c4c:03 S1c43:06 S1c44:10 S1c4b:03 "+RESET
                +"Y0:a4:02 Y0:a0:84 Y0:28:f0",null);
        vector(new Fixture().put(IX,0x84).hex(0x1000,"ef00e0408103"),
                "S1c4b:00 S1c40:84 S1c40:84 S1c48:00 S1c5b:b0 S1c5c:15 S1c5d:11 S1c4a:40 "
                +"S1c4d:84 S1c4e:02 S1c4c:03 S1c43:06 S1c44:10 S1c4b:03 "+RESET,null);
        vector(new Fixture().hex(0x1000,"e78103f200"),PREFIX+"S1c40:82 S1c4d:84 S1c4e:02 S1c4c:03 "
                +"S1c43:03 S1c44:10 S1c4b:03 Y0:a4:02 Y0:a0:84",null);
        vector(new Fixture().hex(0x1000,"e7"),PREFIX,memory(FaultKind.UNKNOWN_READ,Access.READ,0x1001));
    }

    @Test void sourceVoiceAndRegisterTablesAreReadFromCurrentRamInOrder() {
        Fixture f=new Fixture().hex(0x1000,"ef00e0408103"); NativeView source=f.source();
        TrackView view=TrackView.declaredFormat(source,f.layout);
        State overlay=State.supplied(source,List.of(new ByteStore(0x1100,6),new ByteStore(0x620,0xB1),
                new ByteStore(0x635,0x41),new ByteStore(0x1130,0xCC)));
        checked(overlay,view,IX,16,(PREFIX+VOICE+"S1c4a:40 Y0:b4:40 "+OFF
                +"S1c4d:84 S1c4e:02 S1c4c:03 S1c43:06 S1c44:10 S1c4b:03 "+RESET
                +"Y0:a4:02 Y0:a0:84 Y0:28:f0").replace("Y0:b0:04 S1c5b:04","Y0:b1:06 S1c5b:06")
                .replace("Y0:40:23","Y0:41:23"),null,TrackExit.ORDINARY_RETURN);
        Fixture unknown=new Fixture().hex(0x1000,"ef00"); unknown.known.clear(0x1100);
        vector(unknown,PREFIX+RELEASE+"S1c48:00 Y0:b4:c0 ",memory(FaultKind.UNKNOWN_READ,Access.READ,0x1100));
        Fixture reg=new Fixture().hex(0x1000,"ef00"); reg.known.clear(0x620); reg.known.clear(0x1100);
        vector(reg,PREFIX+RELEASE+"S1c48:00 Y0:b4:c0 ",memory(FaultKind.UNKNOWN_READ,Access.READ,0x620));
        Fixture tl=new Fixture().hex(0x1000,"ef00"); tl.known.clear(IX+6);
        vector(tl,PREFIX+VOICE.substring(0,VOICE.indexOf("Y0:48:05")),memory(FaultKind.UNKNOWN_READ,Access.READ,IX+6));
    }

    @Test void dispatchUsesCurrentTargetNotRawCommandAndAlwaysReadsLookahead() {
        Fixture f=new Fixture().hex(0x1000,"ef408103"); NativeView source=f.source();
        TrackView view=TrackView.declaredFormat(source,f.layout);
        State overlay=State.supplied(source,List.of(new ByteStore(0x65E,0),new ByteStore(0x65F,7)));
        checked(overlay,view,IX,16,PREFIX+"S1c4a:40 Y0:b4:40 "+OFF
                +"S1c4d:84 S1c4e:02 S1c4c:03 S1c43:04 S1c44:10 S1c4b:03 "+RESET
                +"Y0:a4:02 Y0:a0:84 Y0:28:f0",null,TrackExit.ORDINARY_RETURN);
        vector(new Fixture().hex(0x1000,"e9088103"),PREFIX,new CommandStop(0x1000,0xE9,0x760));
        vector(new Fixture().hex(0x1000,"e9"),PREFIX,memory(FaultKind.UNKNOWN_READ,Access.READ,0x1001));
        Fixture low=new Fixture().hex(0x1000,"f200"); low.known.clear(0x664);
        vector(low,PREFIX,memory(FaultKind.UNKNOWN_READ,Access.READ,0x664));
        Fixture high=new Fixture().hex(0x1000,"f200"); high.known.clear(0x665);
        vector(high,PREFIX,memory(FaultKind.UNKNOWN_READ,Access.READ,0x665));
        vector(new Fixture().hex(0x1000,"f2"),PREFIX,memory(FaultKind.UNKNOWN_READ,Access.READ,0x1001));
    }

    @Test void stopIsNonlocalAndDoesNotFinishOrSaveDataPointer() {
        Fixture f=new Fixture().hex(0x1000,"f200"); NativeView source=f.source();
        checked(State.supplied(source,List.of()),TrackView.declaredFormat(source,f.layout),IX,1,
                PREFIX+STOP,null,TrackExit.STOP_COMMAND_EXIT);
        Fixture disabled=new Fixture().put(IX,0x84).hex(0x1000,"f200"); NativeView sd=disabled.source();
        checked(State.supplied(sd,List.of()),TrackView.declaredFormat(sd,disabled.layout),IX,1,
                "S1c4b:00 S1c40:84 S1c40:84 S1c40:04 S1c15:1f S1c40:04 Y0:28:00 S1c18:00",null,TrackExit.STOP_COMMAND_EXIT);
    }

    @Test void repeatsHonorZeroCountsUncheckedIndexAliasesAndUnconsumedTargets() {
        vector(new Fixture().hex(0x1000,"f700021010f200").hex(0x1010,"8101"),
                PREFIX+"S1c68:02 S1c68:01 "+OFF+"S1c4d:84 S1c4e:02 S1c4c:01 S1c43:12 S1c44:10 S1c4b:01 "
                +RESET+"Y0:a4:02 Y0:a0:84 Y0:28:f0",null);
        vector(new Fixture().hex(0x1000,"f700001010").hex(0x1010,"8101"),
                PREFIX+"S1c68:00 S1c68:ff "+OFF+"S1c4d:84 S1c4e:02 S1c4c:01 S1c43:12 S1c44:10 S1c4b:01 "
                +RESET+"Y0:a4:02 Y0:a0:84 Y0:28:f0",null);
        Fixture last=new Fixture().put(IX+0x28,1).hex(0x1000,"f70002").hex(0x1005,"f200");
        NativeView source=last.source();
        checked(State.supplied(source,List.of()),TrackView.declaredFormat(source,last.layout),IX,2,
                PREFIX+"S1c68:00 "+STOP,null,TrackExit.STOP_COMMAND_EXIT);
    }

    @Test void repeatAliasOfFlagsCanReachRawFrequencyBoundaryBeforeAnyFinish() {
        // D8+28 wraps to displacement00, aliasing FLAGS; its post-parser value80 becomes7F.
        vector(new Fixture().hex(0x1000,"f7d8021010").hex(0x1010,"8101"),
                PREFIX+"S1c40:7f",new RoutineStop(TrackRoutine.RAW_FREQUENCY_PARSE));
    }

    @Test void commandLimitCountsCompletedCommandsAndCutsBeforeDispatchReads() {
        Fixture f=new Fixture().hex(0x1000,"f60010"); NativeView source=f.source();
        TrackView view=TrackView.declaredFormat(source,f.layout); State state=State.supplied(source,List.of());
        checked(state,view,IX,2,PREFIX,new CommandLimitStop(0x1000,0xF6,2),null);
        Fixture noTable=new Fixture().hex(0x1000,"f6"); noTable.known.clear(0x66C,0x66E);
        NativeView nt=noTable.source();
        checked(State.supplied(nt,List.of()),TrackView.declaredFormat(nt,noTable.layout),IX,0,PREFIX,
                new CommandLimitStop(0x1000,0xF6,0),null);
        Fixture one=new Fixture().hex(0x1000,"e78103"); NativeView os=one.source();
        checked(State.supplied(os,List.of()),TrackView.declaredFormat(os,one.layout),IX,1,
                PREFIX+"S1c40:82 S1c4d:84 S1c4e:02 S1c4c:03 S1c43:03 S1c44:10 S1c4b:03 Y0:a4:02 Y0:a0:84",
                null,TrackExit.ORDINARY_RETURN);
    }

    @Test void reachedRoutineStopsRetainTheirNativePrefix() {
        vector(new Fixture().put(IX+7,0x80).hex(0x1000,"8103"),
                PREFIX+OFF+"S1c4d:84 S1c4e:02 S1c4c:03 S1c43:02 S1c44:10 S1c4b:03 "+RESET,
                new RoutineStop(TrackRoutine.INIT_VIBRATO_BODY));
        vector(new Fixture().put(IX+7,1).put(IX+0xB,2),"S1c4b:01",new RoutineStop(TrackRoutine.UPDATE_VIBRATO_BODY));
        vector(new Fixture().put(IX,0x88).hex(0x1000,"8103"),
                "S1c4b:00 S1c40:88 S1c40:88 S1c40:88 Y0:28:00",new RoutineStop(TrackRoutine.RAW_FREQUENCY_PARSE));
        vector(new Fixture().put(IX,0xA0).hex(0x1000,"8103"),
                "S1c4b:00 S1c40:a0 S1c40:a0 S1c40:a0 Y0:28:00 S1c4d:84 S1c4e:02",
                new RoutineStop(TrackRoutine.PORTAMENTO_NOTE_PARSE));
        vector(new Fixture().put(IX,0x81).put(IX+1,2).put(IX+0xB,2),"S1c4b:01",
                new RoutineStop(TrackRoutine.SPECIAL_FM3_FREQUENCY));
        vector(new Fixture().put(IX,0x81).put(IX+0xB,2),"S1c4b:01 Y0:a4:02 Y0:a0:84",null);
        vector(new Fixture().put(IX,0x85).put(IX+1,2).put(IX+0xB,2),"S1c4b:01",null);
    }

    @Test void faultsStopAtTheReachedByteWithoutPrevalidationOrLostPrefixes() {
        Fixture code=new Fixture().hex(0x1000,"8103"); code.owned.clear(IX+0xE); code.code.set(IX+0xE);
        vector(code,PREFIX+OFF+"S1c4d:84",memory(FaultKind.SELF_MODIFICATION,Access.WRITE,IX+0xE));
        Fixture unowned=new Fixture().hex(0x1000,"8103"); unowned.owned.clear(IX+0xE);
        vector(unowned,PREFIX+OFF+"S1c4d:84",memory(FaultKind.UNOWNED_WRITE,Access.WRITE,IX+0xE));
        Fixture unknown=new Fixture().hex(0x1000,"81");
        vector(unknown,PREFIX+OFF+"S1c4d:84 S1c4e:02",memory(FaultKind.UNKNOWN_READ,Access.READ,0x1001));
        Fixture newlyDefined=new Fixture().hex(0x1000,"8103"); newlyDefined.known.clear(IX+0xD,IX+0xF);
        vector(newlyDefined,NOTE,null); // Only reached stores define these owned unknown destinations.
        Fixture freqHigh=new Fixture().hex(0x1000,"8103"); freqHigh.known.clear(0x601);
        vector(freqHigh,PREFIX+OFF,memory(FaultKind.UNKNOWN_READ,Access.READ,0x601));
        Fixture pointerHigh=new Fixture().hex(0x1000,"8103"); pointerHigh.known.clear(IX+4);
        vector(pointerHigh,"S1c4b:00",memory(FaultKind.UNKNOWN_READ,Access.READ,IX+4));
        Fixture last=new Fixture().word(IX+3,0x1FFF).put(0x1FFF,0x81);
        vector(last,PREFIX+OFF+"S1c4d:84 S1c4e:02",memory(FaultKind.OUTSIDE_RAM,Access.READ,0x2000));
        Fixture table=new Fixture().hex(0x1000,"e000").put(0x1FFF,0);
        table.layout=new TrackLayout(0x600,0x620,0x635,0x1FFF,handlers());
        vector(table,PREFIX,memory(FaultKind.OUTSIDE_RAM,Access.READ,0x2000));
        Fixture outside=new Fixture().word(IX+3,0xFFFF);
        vector(outside,PREFIX,memory(FaultKind.OUTSIDE_RAM,Access.READ,0xFFFF));
    }

    @Test void unsignedPitchChannelsAndDirectUncheckedIxAreNotNormalized() {
        vector(new Fixture().put(IX+5,0xFF).hex(0x1000,"8103"),PREFIX+OFF+"S1c4d:fe S1c4e:aa S1c4c:03 "
                +"S1c43:02 S1c44:10 S1c4b:03 "+RESET+"Y0:a4:aa Y0:a0:fe Y0:28:f0",null);
        vector(new Fixture().put(IX+5,0xFF).hex(0x1000,"8203"),NOTE,null);
        vector(new Fixture().put(IX+1,0xFF).put(IX+0xB,2),"S1c4b:01 Y1:9f:02 Y1:9b:84",null);
        vector(new Fixture().put(IX,0).put(IX+0xB,2),"S1c4b:01 Y0:a4:02 Y0:a0:84",null);
        Fixture f=new Fixture(); f.put(0xB,2).put(0,0x10); f.owned.set(0xB); NativeView source=f.source();
        checked(State.supplied(source,List.of(new ByteStore(0xB,2))),TrackView.declaredFormat(source,f.layout),
                0,0,"S000b:01",null,TrackExit.ORDINARY_RETURN);
        Fixture wrap=new Fixture().put(3,2); wrap.owned.set(3); NativeView sw=wrap.source();
        checked(State.supplied(sw,List.of()),TrackView.declaredFormat(sw,wrap.layout),
                0xFFF8,0,"S0003:01",memory(FaultKind.OUTSIDE_RAM,Access.READ,0xFFF8),null);
    }

    @Test void immutableContentBindingsDefensiveMapsMasksAndFreshFaultEntry() {
        Fixture f=new Fixture().hex(0x1000,"e9"), twin=new Fixture().hex(0x1000,"e9");
        NativeView source=f.source(), equal=twin.source();
        var map=handlers(); TrackLayout layout=new TrackLayout(0x600,0x620,0x635,0x640,map);
        TrackView view=TrackView.declaredFormat(source,layout), view2=TrackView.declaredFormat(equal,twin.layout);
        State before=State.supplied(source,List.of());
        assertFalse(view.authenticated()); assertEquals(view,view2); assertEquals(view.hashCode(),view2.hashCode());
        assertThrows(UnsupportedOperationException.class,()->layout.handlers().clear()); map.clear();
        assertEquals(6,layout.handlers().size());
        TrackResult fault=checked(before,view,IX,16,PREFIX,memory(FaultKind.UNKNOWN_READ,Access.READ,0x1001),null);
        checked(fault.projected(),view2,IX,16,"S1c4b:ff Y0:a4:02 Y0:a0:84",null,TrackExit.ORDINARY_RETURN);
        f.image[0]=1; f.known.clear(); f.owned.clear(); f.code.clear();
        assertEquals(view,view2); assertEquals(before,State.supplied(equal,List.of()));
        Fixture foreign=new Fixture().hex(0x1000,"e9").put(0x1C18,9); NativeView wrong=foreign.source();
        assertThrows(IllegalArgumentException.class,()->planTrack(new TrackCall(IX),before,
                TrackView.declaredFormat(wrong,foreign.layout),new TrackLimits(16)));
        Fixture mask=new Fixture().hex(0x1000,"e9"); mask.owned.clear(0x1C18); NativeView ms=mask.source();
        assertNotEquals(view,TrackView.declaredFormat(ms,mask.layout));
        assertThrows(IllegalArgumentException.class,()->planTrack(new TrackCall(IX),before,
                TrackView.declaredFormat(ms,mask.layout),new TrackLimits(16)));
        Fixture unknown=new Fixture().hex(0x1000,"e9"); unknown.image[0x1FFD]=0x11;
        assertEquals(view2,TrackView.declaredFormat(unknown.source(),unknown.layout));
    }

    @Test void factoriesRejectMissingMapsWidthsUnknownTargetsAndContradictoryMasks() {
        Fixture f=new Fixture(); NativeView source=f.source(); State state=State.supplied(source,List.of());
        TrackView view=TrackView.declaredFormat(source,f.layout);
        assertThrows(IllegalArgumentException.class,()->TrackView.fromNative(source));
        assertThrows(NullPointerException.class,()->TrackView.fromNative(null));
        assertThrows(IllegalArgumentException.class,()->new TrackCall(-1));
        assertThrows(IllegalArgumentException.class,()->new TrackCall(65536));
        assertThrows(IllegalArgumentException.class,()->new TrackLimits(-1));
        assertThrows(IllegalArgumentException.class,()->new TrackLayout(65536,0x620,0x635,0x640,handlers()));
        assertThrows(IllegalArgumentException.class,()->new TrackLayout(0x600,0x620,0x636,0x640,handlers()));
        assertThrows(IllegalArgumentException.class,()->new TrackLayout(0x600,0x620,0x635,0x640,Map.of()));
        var duplicate=handlers(); duplicate.put(TrackHandler.STOP,0x700);
        assertThrows(IllegalArgumentException.class,()->new TrackLayout(0x600,0x620,0x635,0x640,duplicate));
        var outside=handlers(); outside.put(TrackHandler.STOP,65536);
        assertThrows(IllegalArgumentException.class,()->new TrackLayout(0x600,0x620,0x635,0x640,outside));
        assertEquals(0x10,new TrackLayout(0x600,0xFFFB,0x10,0x640,handlers()).insTlRegsAddress());
        assertThrows(NullPointerException.class,()->planTrack(null,state,view,new TrackLimits(1)));
        assertThrows(NullPointerException.class,()->planTrack(new TrackCall(IX),null,view,new TrackLimits(1)));
        assertThrows(NullPointerException.class,()->planTrack(new TrackCall(IX),state,null,new TrackLimits(1)));
        assertThrows(NullPointerException.class,()->planTrack(new TrackCall(IX),state,view,null));
        Fixture unknown=new Fixture(); unknown.known.clear(0x700); NativeView uk=unknown.source();
        assertThrows(IllegalArgumentException.class,()->TrackView.declaredFormat(uk,unknown.layout));
        Fixture unclassified=new Fixture(); unclassified.code.clear(0x700); NativeView uc=unclassified.source();
        assertThrows(IllegalArgumentException.class,()->TrackView.declaredFormat(uc,unclassified.layout));
        Fixture contradictory=new Fixture(); contradictory.owned.clear(0x600); contradictory.code.set(0x600);
        NativeView conflict=contradictory.source();
        assertThrows(IllegalArgumentException.class,()->TrackView.declaredFormat(conflict,contradictory.layout));
        assertThrows(IllegalArgumentException.class,()->State.supplied(source,List.of(new ByteStore(0x700,0))));
    }

    /** Optional actual capability, independently annotated and gated; no worker opened media. UNRUN. */
    @Test void retainedUsaCompositionFirstNoteRestRepeatStopAndUnsupportedBoundaries() throws Exception {
        String root=System.getProperty("soniccd.media.root");
        org.junit.jupiter.api.Assumptions.assumeTrue(root!=null&&!root.isBlank(),"Opt in with soniccd.media.root");
        CdFmSoundAssets assets;
        try (var files=java.nio.file.Files.list(java.nio.file.Path.of(root).resolve("USA"))) {
            var cues=files.filter(p->p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1,cues.size());
            try (var programs=com.openggf.game.soniccd.media.CdProgramAssets.open(cues.getFirst(),
                    java.util.Optional.of(com.openggf.game.soniccd.CdReleaseVariant.USA),java.util.Optional.empty())) {
                assets=CdFmSoundAssets.fromPrograms(programs);
            }
        }
        NativeView source=NativeView.fromAssets(assets); TrackView view=TrackView.fromNative(source);
        assertTrue(view.authenticated()); assertNotEquals(view,TrackView.declaredFormat(new Fixture().source(),new Fixture().layout));
        assertThrows(IllegalArgumentException.class,()->TrackView.declaredFormat(source,new Fixture().layout));
        assertThrows(IllegalArgumentException.class,()->State.supplied(source,List.of(new ByteStore(0x74,0))));
        State start=actualPlay(source,0x90); // Exact old projected State; never resupplied for track projection.
        String v90="Y0:80:ff Y0:84:ff Y0:88:ff Y0:8c:ff S1c48:00 Y0:b4:c0 Y0:b0:07 S1c5b:07 "
                +"Y0:30:07 Y0:38:07 Y0:34:08 Y0:3c:08 Y0:50:1f Y0:58:1f Y0:54:1f Y0:5c:1f "
                +"Y0:60:00 Y0:68:00 Y0:64:00 Y0:6c:00 Y0:70:00 Y0:78:00 Y0:74:00 Y0:7c:00 "
                +"Y0:80:0f Y0:88:0f Y0:84:0f Y0:8c:0f S1c5c:19 S1c5d:08 "
                +"Y0:40:10 Y0:48:10 Y0:44:10 Y0:4c:10 ";
        String note90=OFF+"S1c4d:84 S1c4e:1a S1c4c:01 S1c43:ee S1c44:07 S1c4b:01 "+RESET
                +"Y0:a4:1a Y0:a0:84 Y0:28:f0";
        State note=checked(start,view,IX,1,PREFIX+v90+note90,null,TrackExit.ORDINARY_RETURN).projected();
        String rest90=PREFIX+OFF+"S1c40:90 S1c4c:01 S1c43:f0 S1c44:07 S1c4b:01 "+RESET;
        State rest=checked(note,view,IX,0,rest90,null,TrackExit.ORDINARY_RETURN).projected();
        State repeated=checked(rest,view,IX,1,"S1c4b:00 S1c40:90 S1c40:80 S1c68:0b S1c68:0a "+note90,
                null,TrackExit.ORDINARY_RETURN).projected();
        // Fixed source count sequence: nine further jumps, then the zero/nonlocal-stop iteration.
        for (String counter:List.of("09","08","07","06","05","04","03","02","01")) {
            rest=checked(repeated,view,IX,0,rest90,null,TrackExit.ORDINARY_RETURN).projected();
            repeated=checked(rest,view,IX,1,"S1c4b:00 S1c40:90 S1c40:80 S1c68:"+counter+" "+note90,
                    null,TrackExit.ORDINARY_RETURN).projected();
        }
        rest=checked(repeated,view,IX,0,rest90,null,TrackExit.ORDINARY_RETURN).projected();
        checked(rest,view,IX,2,"S1c4b:00 S1c40:90 S1c40:80 S1c68:00 "+STOP,null,TrackExit.STOP_COMMAND_EXIT);
        checked(actualPlay(source,0x95),view,0x1CD0,1,"S1cdb:00 S1cd0:80 S1cd0:80",
                new CommandStop(0x968,0xF3,0x665),null);
        String v98="Y1:82:ff Y1:86:ff Y1:8a:ff Y1:8e:ff S1d38:00 Y1:b6:c0 Y1:b2:20 S1d4b:20 "
                +"Y1:32:36 Y1:3a:35 Y1:36:30 Y1:3e:31 Y1:52:df Y1:5a:df Y1:56:9f Y1:5e:9f "
                +"Y1:62:07 Y1:6a:06 Y1:66:09 Y1:6e:06 Y1:72:07 Y1:7a:06 Y1:76:06 Y1:7e:08 "
                +"Y1:82:2f Y1:8a:1f Y1:86:1f Y1:8e:ff S1d4c:43 S1d4d:0a "
                +"Y1:42:16 Y1:4a:30 Y1:46:13 Y1:4e:07 ";
        State rest98=checked(actualPlay(source,0x98),view,0x1D30,1,"S1d3b:00 S1d30:80 S1d30:80 "+v98
                +"S1d30:80 Y0:28:06 S1d30:90 S1d3c:01 S1d33:19 S1d34:0a S1d3b:01 "
                +"S1d55:00 S1d52:00 S1d47:00 S1d4e:00",null,TrackExit.ORDINARY_RETURN).projected();
        checked(rest98,view,0x1D30,1,"S1d3b:00 S1d30:90 S1d30:80",new CommandStop(0xA19,0xF0,0x563),null);
    }

    private static State actualPlay(NativeView source,int id) {
        var ingress=new ArrayList<ByteStore>();
        for (int a=0x1C00;a<0x1D60;a++) ingress.add(new ByteStore(a,0));
        ingress.add(new ByteStore(0x1C09,id));
        return assertInstanceOf(Returned.class,plan(Entry.PLAY_SOUND_ID,State.supplied(source,ingress),source)).projected();
    }
}
