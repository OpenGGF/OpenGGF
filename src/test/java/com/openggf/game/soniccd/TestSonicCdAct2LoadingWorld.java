package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.level.CdR12aProfile;
import com.openggf.game.soniccd.level.SonicCdAct2Level;
import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.CdCapturedValueAssertions.assertCapturedValueEquals;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Genuine cold/Past source and native Results Next8 object-only ingress; all globals, actor, controls,
 * queues, art, clock and terrain remain real. Root's separate ordinary-controller route covers the
 * unmodified natural trigger. Native N1ecc5f3a Main129-430/Title23-191/Palette Fade11-100.
 * All authored Java UNRUN; no playable Act2 continuation or pixel parity is claimed.
 */
@Isolated
class TestSonicCdAct2LoadingWorld {
    private final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void actualReturnedProgrammeAdmitsSameWorldFullActTwoWithoutAnExtraClockOrPrematureOldRetirement() throws Exception {
        var p=oldReturned(); var h=p.harness(); var m=h.module(); var oldFrame=p.frame();
        var world=h.mode().getWorldSession(); var source=world.getDataSource();
        var clock=oldFrame.vIntClock(); long generation=h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        assertFalse(oldFrame.closed()); var full=h.mode().getRewindRegistry().capture(); var course=h.mode().getRewindRegistry().captureCourse();
        h.mode().getRewindRegistry().restore(full); h.mode().getRewindRegistry().restoreCourse(course); assertFalse(oldFrame.closed());
        step(h,neutral()); assertTrue(m.act2LoadingSelected()); assertFalse(m.pastLoadingSelected());
        var f=m.requirePlayerFrame(); var state=m.act2LoadState();
        assertNotSame(oldFrame,f); assertTrue(oldFrame.closed()); assertFalse(f.closed()); assertSame(clock,f.vIntClock());
        assertSame(world,h.mode().getWorldSession()); assertSame(source,world.getDataSource());
        assertEquals(generation+1,h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        var level=assertInstanceOf(SonicCdAct2Level.class,h.mode().getLevelManager().getCurrentLevel());
        var actual=m.requireRuntimeSource().act2Assets().orElseThrow(); assertSame(actual.terrain(),level.assets());
        assertSame(actual.terrain(),state.assets()); assertTrue(f.stageSource().ownsAct2(actual.terrain(),actual.player()));
        assertEquals(SonicCdGameModule.Act2LoadPhase.FIRST_PLC,state.phase()); assertEquals(0,f.state().header().slotId());
        assertEquals(1,f.environment().movement().gates().zoneAct()); assertEquals(1,f.environment().movement().gates().timeZone());
        assertEquals(0,state.section().queue().headSource()); assertEquals(0,state.section().remainingTilesWord());
        assertArrayEquals(new byte[128],state.cpuPalette().copy()); assertArrayEquals(new byte[128],state.displayedPalette().copy());
        assertArrayEquals(new byte[31*64],state.reservedObjects().copy());
        assertTrue(state.placement().pool().slots().stream().allMatch(row->row.id()==0));
        assertInstanceOf(SonicCdPastDisplay.Disabled.class,m.act2SelectedDisplay().projection());
    }

    @Test void normalLoaderSkipsSectionTwoThenFirstPhysicalWalkInitializesReservedTitleAndExecutesItsEightChildrenBeforeSatTail() throws Exception {
        var p=admitted(); var h=p.harness(); var m=h.module(); var f=m.requirePlayerFrame();
        step(h,neutral()); assertEquals(SonicCdGameModule.Act2LoadPhase.SECTION_PLC,m.act2LoadState().phase());
        assertTrue(m.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.IMMEDIATE_LEVEL));
        assertTrue(m.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.IMMEDIATE_STANDARD));
        assertEquals(80,f.state().motion().centreX16_16()>>>16); assertEquals(389,f.state().motion().centreY16_16()>>>16);
        step(h,neutral()); var from=m.act2LoadState(); assertEquals(SonicCdGameModule.Act2LoadPhase.FROM_BLACK,from.phase());
        assertEquals(0x3C,from.reservedObjects().unsignedByte((4-1)*64)); assertEquals(1,f.environment().controls().controlLocked());
        assertEquals(0,f.state().timers().hurt()); assertEquals(0,from.section().queue().headSource());
        var normalHead=from.cpuPalette().slice(0,32); var water=image(m,"waterPalette"); var target=image(m,"fadePalette");
        for(int wait=0;wait<22;wait++) {
            var previous=m.act2LoadState().cpuPalette(); step(h,neutral());
            assertArrayEquals(previous.copy(),m.act2LoadState().displayedPalette().copy(),"V-int publication precedes the colour step");
            assertArrayEquals(normalHead.copy(),m.act2LoadState().cpuPalette().slice(0,32).copy());
            assertSame(water,image(m,"waterPalette")); assertSame(target,image(m,"fadePalette"));
            assertEquals(0,f.environment().globals().levelFrames()); assertEquals(0,f.state().motion().routine());
        }
        assertEquals(SonicCdGameModule.Act2LoadPhase.BEGIN_LEVEL,m.act2LoadState().phase());
        assertEquals(23,f.vIntClock().absoluteValue().orElseThrow());
        step(h,neutral()); var actual=m.act2LoadState();
        assertEquals(1,f.environment().globals().levelFrames()); assertEquals(2,f.state().motion().routine()); assertFalse(f.closed());
        assertEquals(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN,actual.calls().getLast());
        assertEquals(9,actual.calls().stream().filter(c->c==SonicCdGameModule.Act2LoadCall.TITLE_CARD_BODY).count());
        assertEquals(2,actual.reservedObjects().unsignedByte((4-1)*64+0x24));
        assertEquals(0x30,actual.reservedObjects().unsignedWord((4-1)*64+0xA));
        for(int whole=38;whole<=45;whole++) {
            var child=actual.placement().pool().slots().get(whole-32); assertEquals(0x3C,child.id());
            assertEquals(CdR12aProfile.Asset.TITLE_CARD_MAPPINGS.nativeAddress(),child.longBits(MAPPINGS));
            assertTrue(child.routine()==4 || child.routine()==8);
        }
        assertEquals(0,actual.section().sectionWord()>>>8); assertEquals(0,actual.section().queue().headSource());
        assertEquals(1,f.environment().controls().controlLocked());
        assertNull(actualField(actualField(m,"act2Loading"),"drawQueue"));
        assertInstanceOf(SonicCdPastDisplay.Ready.class,m.act2SelectedDisplay().projection());
    }

    @Test void fullAndCourseRestoreHealthyLoadFadeAndFirstOrdinaryCutsBeforeAnyForwardServiceWithoutReplayingNativeWork() throws Exception {
        var p=admitted(); var h=p.harness(); var m=h.module();
        replayNext(h); replayNext(h);
        for(int i=0;i<21;i++) step(h,neutral());
        replayNext(h); assertEquals(SonicCdGameModule.Act2LoadPhase.BEGIN_LEVEL,m.act2LoadState().phase());
        replayNext(h); assertEquals(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN,m.act2LoadState().calls().getLast());
    }
    private static void assertAct2Cut(SonicCdGameModule.Act2LoadState expected,SonicCdGameModule.Act2LoadState actual) {
        assertSame(expected.assets(),actual.assets()); assertEquals(expected.phase(),actual.phase());
        assertCapturedValueEquals(expected.actor(),actual.actor()); assertCapturedValueEquals(expected.shared(),actual.shared());
        assertArrayEquals(expected.cpuPalette().copy(),actual.cpuPalette().copy());
        assertArrayEquals(expected.displayedPalette().copy(),actual.displayedPalette().copy());
        assertEquals(expected.ring(),actual.ring()); assertEquals(expected.level(),actual.level()); assertEquals(expected.section(),actual.section());
        assertEquals(expected.calls(),actual.calls()); assertEquals(expected.sounds(),actual.sounds());
        assertArrayEquals(expected.reservedObjects().copy(),actual.reservedObjects().copy());
        assertEquals(expected.placement().status(),actual.placement().status());
        assertArrayEquals(expected.placement().savedFlags().copy(),actual.placement().savedFlags().copy());
        assertEquals(expected.placement().pool().slots(),actual.placement().pool().slots());
    }
    private static void replayNext(Harness h) throws Exception {
        var m=h.module(); var f=m.requirePlayerFrame(); var source=f.stageSource(); var world=h.mode().getWorldSession();
        long generation=h.mode().getLevelManager().getCompletedProductionLoadGeneration(); var registry=h.mode().getRewindRegistry();
        var full=registry.capture(); var course=registry.captureCourse(); var before=m.act2LoadState(); var beforeDisplay=m.act2SelectedDisplay();
        registry.restore(full); assertAct2Cut(before,m.act2LoadState()); assertEquals(beforeDisplay,m.act2SelectedDisplay());
        registry.restoreCourse(course); assertAct2Cut(before,m.act2LoadState());
        step(h,neutral()); var after=m.act2LoadState(); var display=m.act2SelectedDisplay();
        registry.restore(full); assertAct2Cut(before,m.act2LoadState()); step(h,neutral()); assertAct2Cut(after,m.act2LoadState()); assertEquals(display,m.act2SelectedDisplay());
        registry.restoreCourse(course); assertAct2Cut(before,m.act2LoadState()); step(h,neutral()); assertAct2Cut(after,m.act2LoadState());
        assertSame(f,m.requirePlayerFrame()); assertSame(source,f.stageSource()); assertSame(world,h.mode().getWorldSession());
        assertEquals(generation,h.mode().getLevelManager().getCompletedProductionLoadGeneration()); assertFalse(f.closed());
    }
    @Test void absentOptionalAct2PairRejectsBeforeAdmissionWithoutRetiringTheActualReturnedSource() throws Exception {
        var actual = source();
        var withoutPair = new CdGameDataSource(actual.release(), actual.assets(), actual.fmSoundAssets(), actual.timeWarpPrograms());
        assertTrue(withoutPair.act2Assets().isEmpty());
        var p = oldReturned(ready(opening(withoutPair))); var h = p.harness();
        var oldFrame = p.frame(); var clock = oldFrame.vIntClock(); var before = captureNative(h);
        var world = h.mode().getWorldSession(); long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        for (int attempt = 0; attempt < 2; attempt++) {
            var stopped = assertThrows(SonicCdGameModule.UnsupportedPastLoading.class, () -> step(h, neutral()));
            assertEquals(SonicCdGameModule.PastLoadBoundary.NEXT_ACT_PROGRAMME_ADMISSION, stopped.boundary());
            assertEquals(0, stopped.getSuppressed().length); assertFalse(oldFrame.closed());
            assertSame(oldFrame, h.module().requirePlayerFrame()); assertSame(clock, oldFrame.vIntClock());
            assertFalse(h.module().act2LoadingSelected()); assertSame(world, h.mode().getWorldSession());
            assertEquals(generation, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
            assertNativeCapture(before, h);
        }
        var registry = h.mode().getRewindRegistry(); var full = registry.capture(); var course = registry.captureCourse();
        registry.restore(full); assertNativeCapture(before, h); registry.restoreCourse(course); assertNativeCapture(before, h);
    }

    private static Prepared admitted() throws Exception { var p=oldReturned(); step(p.harness(),neutral()); assertTrue(p.harness().module().act2LoadingSelected()); return p; }
    private static Prepared oldReturned() throws Exception { return oldReturned(ready()); }
    private static Prepared oldReturned(Prepared p) throws Exception {
        for(int call=p.returned()+1;call<=8278;call++) step(p.harness(),route(call));
        seed(p,fixed(p,127).moveByte(ROUTINE,8)); step(p.harness(),neutral());
        for(int wait=0;wait<22;wait++) step(p.harness(),neutral());
        step(p.harness(),neutral()); step(p.harness(),neutral());
        assertEquals(SonicCdGameModule.PastLoadPhase.OLD_IPX_RETURNED,p.harness().module().pastLoadState().phase());
        assertFalse(p.frame().closed()); return p;
    }
    private static CdAssetBytes image(SonicCdGameModule module,String name) throws Exception {
        return (CdAssetBytes)actualField(actualField(actualField(module,"act2Loading"),"values"),name);
    }
    private static CdObjectState fixed(Prepared p, int whole) {
        var old = p.placement().pool().state(whole); assertEquals(0, old.id());
        return old.moveByte(ID, 0x3A).moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 0).moveWord(X, 288)
                .moveWord(RESULTS_DEST_X, 288).moveWord(RESULTS_SCREEN_Y_WORD, 204).moveWord(Y, 0x2468)
                .moveWord(Y_FRACTION, 0x1357).moveByte(PRIORITY, 0).moveByte(MAP_FRAME, 0).moveWord(TILE, 0x83C4)
                .moveLong(MAPPINGS, p.assets().view(CdR11bAssets.Asset.MAP_SPR_RESULTS).range().nativeAddress());
    }
    private static void seed(Prepared p, CdObjectState row) {
        var before = p.placement().capture(); p.placement().pool().write(row);
        assertEquals(before.status(), p.placement().status());
        assertArrayEquals(before.savedFlags().copy(), p.placement().capture().savedFlags().copy());
        for (var other : before.pool().slots()) if (other.identity().wholeIndex() != row.identity().wholeIndex())
            assertEquals(other, p.placement().pool().state(other.identity().wholeIndex()));
    }
    private static LogicalInputSnapshot neutral() { return LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(), PlayerInputState.neutral()); }
    private static SonicCdPastLoadingVisit visit(Prepared p) { return (SonicCdPastLoadingVisit) p.visit(); }
}
