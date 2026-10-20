package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.openggf.game.sonic3k.objects.bosses.TestHczEndBossRobotnikShip.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual allocated Head dispatch. These component inlets do not certify a route or native cause. */
class TestHczEndBossRobotnikHead {
    @Test
    void actualEggmanHeadInitDoesNotAdvanceAndFirstMainUnderflowsTheZeroTimer() {
        Fixture fixture = Fixture.create();
        fixture.head().update(0, null);
        assertEquals(new HeadStep(2, 0, 0, 0), headStep(fixture.head()),
                "Obj_RobotnikHeadInit leaves raw state untouched and mapping frame zero");

        fixture.head().update(1, null);
        assertEquals(new HeadStep(2, 1, 5, 1), headStep(fixture.head()),
                "the first MAIN underflow reads script frame 1, then reloads delay 5");
    }

    @Test
    void actualEggmanHeadHoldsEachRawFrameForSixMainDispatchesIncludingFcRestart() {
        Fixture fixture = Fixture.create();
        fixture.head().update(0, null);
        List<Integer> expected = List.of(1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 1);
        for (int dispatch = 1; dispatch <= expected.size(); dispatch++) {
            fixture.head().update(dispatch, null);
            HeadStep step = headStep(fixture.head());
            assertEquals(expected.get(dispatch - 1).intValue(), step.mappingFrame(),
                    "script $05,$00,$01,$FC at MAIN dispatch " + dispatch);
            if (dispatch == 6 || dispatch == 12) {
                assertEquals(0, step.timer(), "zero still returns through bpl");
            }
        }
        assertEquals(new HeadStep(2, 1, 5, 1), headStep(fixture.head()));
    }

    @Test
    void actualEggmanHeadContinuesItsTimerDuringHurtAndKeepsTwoUntilTheNextRawWrite() {
        Fixture fixture = Fixture.create();
        fixture.head().update(0, null);
        seedHead(fixture.head(), 1, 2, 1);
        fixture.boss().getState().invulnerable = true;
        fixture.head().update(1, null);
        assertEquals(new HeadStep(2, 1, 1, 2), headStep(fixture.head()));

        fixture.boss().getState().invulnerable = false;
        fixture.head().update(2, null);
        assertEquals(new HeadStep(2, 1, 0, 2), headStep(fixture.head()),
                "clearing status bit 6 does not write mapping_frame while bpl returns");
        fixture.head().update(3, null);
        assertEquals(new HeadStep(2, 0, 5, 0), headStep(fixture.head()),
                "$FC writes frame zero only when the running raw timer underflows");
    }

    @Test
    void actualEggmanHeadAdvancesTheRawCursorBeforeHurtOverwritesItsMapping() {
        Fixture fixture = Fixture.create();
        fixture.head().update(0, null);
        seedHead(fixture.head(), 0, 0, 0);
        fixture.boss().getState().invulnerable = true;
        fixture.head().update(1, null);
        assertEquals(new HeadStep(2, 1, 5, 2), headStep(fixture.head()));
        fixture.head().update(2, null);
        fixture.head().update(3, null);
        assertEquals(new HeadStep(2, 1, 3, 2), headStep(fixture.head()),
                "hurt must neither freeze nor reset anim_frame_timer");

        fixture.boss().getState().invulnerable = false;
        fixture.head().update(4, null);
        assertEquals(new HeadStep(2, 1, 2, 2), headStep(fixture.head()));
        fixture.head().update(5, null);
        fixture.head().update(6, null);
        assertEquals(new HeadStep(2, 1, 0, 2), headStep(fixture.head()));
        fixture.head().update(7, null);
        assertEquals(new HeadStep(2, 0, 5, 0), headStep(fixture.head()));
    }

    @Test
    void actualEggmanHeadDefeatWritesThreeAfterRawAdvanceAndLatchesEnd() {
        Fixture fixture = Fixture.create();
        fixture.boss().getState().defeated = true;
        fixture.boss().getState().invulnerable = true;
        fixture.head().update(0, null);
        assertEquals(new HeadStep(2, 0, 0, 0), headStep(fixture.head()),
                "INIT does not execute MAIN's boss-status branches");

        fixture.head().update(1, null);
        HeadStep ended = new HeadStep(4, 1, 5, 3);
        assertEquals(ended, headStep(fixture.head()),
                "Animate_Raw precedes status bit 7, which wins over bit 6");
        fixture.boss().getState().defeated = false;
        for (int dispatch = 2; dispatch <= 9; dispatch++) {
            fixture.head().update(dispatch, null);
            assertEquals(ended, headStep(fixture.head()),
                    "Obj_RobotnikHeadEnd performs no more animation or live-status mapping writes");
        }
    }

    @Test
    void actualEggmanHeadHurtMappingAndRunningRawPhaseRecreateAndReplayTwice() {
        Fixture fixture = Fixture.create();
        fixture.head().update(0, null);
        seedHead(fixture.head(), 1, 2, 1);
        fixture.boss().getState().invulnerable = true;
        fixture.head().update(1, null);
        fixture.boss().getState().invulnerable = false;
        RewindCaptureContext context = fixture.manager().captureIdentityContext();
        PerObjectRewindSnapshot snapshot = fixture.head().captureRewindState(context);
        List<HeadStep> expected = List.of(
                new HeadStep(2, 1, 0, 2),
                new HeadStep(2, 0, 5, 0),
                new HeadStep(2, 0, 4, 0));

        for (int cycle = 0; cycle < 2; cycle++) {
            HczEndBossRobotnikHead restored = recreate(fixture, snapshot, context);
            assertEquals(new HeadStep(2, 1, 1, 2), headStep(restored));
            for (int offset = 0; offset < expected.size(); offset++) {
                restored.update(2 + offset, null);
                assertEquals(expected.get(offset), headStep(restored),
                        "captured raw phase and persistent hurt mapping replay together");
            }
        }
    }

    @Test
    void actualEggmanHeadEndLatchAndFrozenRawStateRecreateAndReplayTwice() {
        Fixture fixture = Fixture.create();
        fixture.head().update(0, null);
        fixture.boss().getState().defeated = true;
        fixture.head().update(1, null);
        fixture.boss().getState().defeated = false;
        fixture.boss().getState().invulnerable = true;
        RewindCaptureContext context = fixture.manager().captureIdentityContext();
        PerObjectRewindSnapshot snapshot = fixture.head().captureRewindState(context);
        HeadStep ended = new HeadStep(4, 1, 5, 3);

        for (int cycle = 0; cycle < 2; cycle++) {
            HczEndBossRobotnikHead restored = recreate(fixture, snapshot, context);
            assertEquals(ended, headStep(restored));
            for (int dispatch = 2; dispatch <= 4; dispatch++) {
                restored.update(dispatch, null);
                assertEquals(ended, headStep(restored),
                        "restored END ignores later boss-status changes and keeps frame three");
            }
        }
    }


    @Test
    void initDrawsItsOwnMappingAndCopiesActualShipArtPriority() {
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        when(renderer.isReady()).thenReturn(true);
        Fixture fixture = Fixture.create(renderer);
        fixture.head().update(0, null);
        fixture.head().appendRenderCommands(new java.util.ArrayList<>());
        assertSame(fixture.family().ship(), getField(fixture.head(), "ship"));
        assertSame(fixture.boss(), getField(fixture.head(), "boss"));
        assertTrue(fixture.head().isHighPriority());
        verify(renderer).drawFrameIndexForcedPriority(0, fixture.head().getX(), fixture.head().getY(),
                false, false, 0, true);
        assertEquals(0, getInt(fixture.head(), "animCounter"));
    }

    @Test
    void syncDrawControlBitChangesArtCopyButDoesNotSuppressEitherSprite() {
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        when(renderer.isReady()).thenReturn(true);
        Fixture fixture = Fixture.create(renderer);
        setInt(fixture.family().ship(), "control", 0x40);
        fixture.family().ship().update(1, null);
        fixture.head().update(0, null);
        assertEquals(0x40, getInt(fixture.head(), "control"));
        assertFalse(fixture.head().isHighPriority(),
                "Head attributes reset art before the control-bit branch returns without copying it");
        assertTrue(getBoolean(fixture.family().ship(), "visible"));
        assertTrue(getBoolean(fixture.head(), "visible"),
                "Draw_Sprite and Render_Sprites do not suppress drawing on $38.bit6");
        fixture.family().ship().appendRenderCommands(new java.util.ArrayList<>());
        fixture.head().appendRenderCommands(new java.util.ArrayList<>());
        verify(renderer).drawFrameIndexForcedPriority(5, fixture.family().ship().getX(),
                fixture.family().ship().getY(), false, false, 0, true);
        verify(renderer).drawFrameIndexForcedPriority(0, fixture.head().getX(), fixture.head().getY(),
                false, false, 0, false);
        // This tests the actual Ship->Head inlet, not an unmodeled natural hidden HCZ boss.
    }

    @Test
    void actualShipReferenceControlsAdjustedPositionAndOwnDispatchAdvancesRawState() {
        Fixture fixture = Fixture.create();
        fixture.head().update(0, null);
        fixture.family().ship().setPosition(0xFFFF, 0xFFF8);
        setField(fixture.family().ship(), "shipFacingRight", true);
        setField(fixture.family().ship(), "shipFlipY", true);
        fixture.head().update(0, null); // Same VInt, another native object dispatch.
        assertEquals(0xFFFF, fixture.head().getX());
        assertEquals(0x14, fixture.head().getY());
        assertEquals(new HeadStep(2, 1, 5, 1), headStep(fixture.head()));
        assertTrue(getBoolean(fixture.head(), "flipX"));
        assertTrue(getBoolean(fixture.head(), "flipY"));
    }

    @Test
    void capturedNonNullShipRequiresARegisteredExactIdentity() {
        Fixture fixture = Fixture.create();
        assertThrows(IllegalStateException.class, () -> fixture.head().captureRewindState(
                RewindCaptureContext.withIdentityTable(new RewindIdentityTable())));
    }

    @Test
    void noLaterSlotMeansNoHeadAndNoRetryOrSyntheticHeadDrawing() {
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        when(renderer.isReady()).thenReturn(true);
        var family = TestHczEndBossRobotnikShip.Fixture.create(0x100, renderer);
        var reserved = new java.util.ArrayList<Integer>();
        for (int slot; (slot = family.manager().allocateSlotAfter(family.ship().getSlotIndex())) >= 0;) {
            reserved.add(slot);
        }
        assertFalse(reserved.isEmpty());
        family.ship().update(0, null);
        assertEquals(0, family.manager().activeObjectsOfType(HczEndBossRobotnikHead.class).size());
        family.ship().appendRenderCommands(new java.util.ArrayList<>());
        verify(renderer, atLeastOnce()).isReady();
        verify(renderer).drawFrameIndexForcedPriority(5, family.ship().getX(), family.ship().getY(),
                false, false, 0, true);
        verifyNoMoreInteractions(renderer);
        int earlier = family.manager().allocateDynamicSlot();
        assertTrue(earlier < family.ship().getSlotIndex(), "a lower hole cannot satisfy FindNextFreeObj");
        family.manager().releaseDynamicSlot(reserved.getLast());
        family.ship().update(1, null);
        assertEquals(0, family.manager().activeObjectsOfType(HczEndBossRobotnikHead.class).size());
        assertEquals(2, getInt(family.ship(), "routine"));
    }

    private static void seedHead(HczEndBossRobotnikHead head, int cursor, int timer, int mapping) {
        setInt(head, "routine", 2);
        setInt(head, "animFrame", cursor);
        setInt(head, "animCounter", timer);
        setInt(head, "mappingFrame", mapping);
    }

    private static HeadStep headStep(HczEndBossRobotnikHead head) {
        return new HeadStep(getInt(head, "routine"), getInt(head, "animFrame"),
                getInt(head, "animCounter"), getInt(head, "mappingFrame"));
    }

    private record HeadStep(int routine, int cursor, int timer, int mappingFrame) {}

    private static HczEndBossRobotnikHead recreate(
            Fixture fixture, PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        var restored = ObjectConstructionContext.construct(fixture.family().services(),
                () -> fixture.head().recreateForRewind(new RewindRecreateContext(
                        fixture.head().getSpawn(), snapshot, fixture.family().services(),
                        fixture.family().manager(), null)));
        restored.setServices(fixture.family().services());
        restored.restoreRewindState(snapshot, context);
        assertSame(fixture.family().ship(), getField(restored, "ship"));
        return restored;
    }

    private record Fixture(TestHczEndBossRobotnikShip.Fixture family, HczEndBossRobotnikHead head) {
        static Fixture create() { return create(null); }
        static Fixture create(PatternSpriteRenderer renderer) {
            var family = TestHczEndBossRobotnikShip.Fixture.create(0x100, renderer);
            family.ship().update(0, null);
            var heads = family.manager().activeObjectsOfType(HczEndBossRobotnikHead.class);
            assertEquals(1, heads.size());
            var head = heads.getFirst();
            assertTrue(head.getSlotIndex() > family.ship().getSlotIndex());
            return new Fixture(family, head);
        }
        HczEndBossInstance boss() { return family.boss(); }
        com.openggf.level.objects.ObjectManager manager() { return family.manager(); }
    }
}
