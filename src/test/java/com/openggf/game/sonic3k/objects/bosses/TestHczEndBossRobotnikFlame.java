package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.openggf.game.sonic3k.objects.bosses.TestHczEndBossRobotnikShip.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Obj_RobotnikShipFlame's actual child slot, INIT return and VInt-gated MAIN. */
class TestHczEndBossRobotnikFlame {
    @Test
    void readyAllocatesOneLaterFlameWhoseInitDoesNotDraw() {
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        when(renderer.isReady()).thenReturn(true);
        Fixture fixture = Fixture.create(renderer);
        assertTrue(fixture.flame().getSlotIndex() > fixture.family().ship().getSlotIndex());
        assertSame(fixture.family().ship(), getField(fixture.flame(), "ship"));
        assertTrue(fixture.flame().isHighPriority(), "creation copies art, without Child_SyncDraw");
        fixture.flame().update(2, null);
        fixture.flame().appendRenderCommands(new java.util.ArrayList<>());
        verifyNoInteractions(renderer);
        assertTrue(getBoolean(fixture.flame(), "initialized"));
        assertFalse(getBoolean(fixture.flame(), "visible"));
    }

    @Test
    void mainRefreshesExactShipAndDrawsFrameSixOnlyOnEvenVIntWithWordVelocity() {
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        when(renderer.isReady()).thenReturn(true);
        Fixture fixture = Fixture.create(renderer);
        fixture.flame().update(0, null);
        fixture.flame().update(1, null);
        assertFalse(getBoolean(fixture.flame(), "visible"));
        fixture.flame().appendRenderCommands(new java.util.ArrayList<>());
        verifyNoInteractions(renderer);
        fixture.flame().update(2, null);
        assertTrue(getBoolean(fixture.flame(), "visible"));
        fixture.flame().appendRenderCommands(new java.util.ArrayList<>());
        verify(renderer).drawFrameIndexForcedPriority(6, 0x4000 - 0x1E, 0x140, true, false, 0, true);
        setInt(fixture.family().ship(), "xVel", 0x10000);
        fixture.flame().update(4, null);
        assertFalse(getBoolean(fixture.flame(), "visible"), "tst.w sees zero, not the Java upper word");
        setInt(fixture.family().ship(), "xVel", -0x100);
        fixture.flame().update(6, null);
        assertTrue(getBoolean(fixture.flame(), "visible"));
    }

    @Test
    void mainCopiesFlipBitsAndUsesWordWrappedAdjustedOffset() {
        Fixture fixture = Fixture.create();
        fixture.flame().update(0, null);
        fixture.family().ship().setPosition(0xFFF8, 0xFFF0);
        setField(fixture.family().ship(), "shipFacingRight", false);
        setField(fixture.family().ship(), "shipFlipY", true);
        fixture.flame().update(2, null);
        assertEquals(0x16, fixture.flame().getX());
        assertEquals(0xFFF0, fixture.flame().getY());
        assertFalse(getBoolean(fixture.flame(), "flipX"));
        assertTrue(getBoolean(fixture.flame(), "flipY"));
    }

    @Test
    void shipDeleteBitDeletesFlameImmediatelyBeforePositionOrDraw() {
        Fixture fixture = Fixture.create();
        fixture.flame().update(0, null);
        setInt(fixture.family().ship(), "control", 0x10);
        fixture.family().ship().setPosition(0x1234, 0x5678);
        int oldX = fixture.flame().getX();
        fixture.flame().update(2, null);
        assertTrue(fixture.flame().isDestroyed());
        assertFalse(getBoolean(fixture.flame(), "visible"));
        assertNull(getField(fixture.flame(), "ship"));
        assertEquals(oldX, fixture.flame().getX(), "parent-delete test precedes adjusted refresh");
    }

    @Test
    void capturedFlameRestoresExactShipAndRepeatsParityForwardTwice() {
        Fixture fixture = Fixture.create();
        fixture.flame().update(0, null);
        var context = fixture.family().manager().captureIdentityContext();
        var snapshot = fixture.flame().captureRewindState(context);
        for (int cycle = 0; cycle < 2; cycle++) {
            var restored = ObjectConstructionContext.construct(fixture.family().services(),
                    () -> fixture.flame().recreateForRewind(new RewindRecreateContext(
                            fixture.flame().getSpawn(), snapshot, fixture.family().services(),
                            fixture.family().manager(), null)));
            restored.setServices(fixture.family().services());
            restored.restoreRewindState(snapshot, context);
            assertSame(fixture.family().ship(), getField(restored, "ship"));
            for (int vInt : List.of(1, 2, 3, 4)) {
                restored.update(vInt, null);
                assertEquals((vInt & 1) == 0, getBoolean(restored, "visible"));
                assertEquals(0x4000 - 0x1E, restored.getX());
            }
        }
    }

    @Test
    void exhaustedLaterSlotsLeaveFlameAbsentWithoutRetryAfterCapacityReturns() {
        var family = TestHczEndBossRobotnikShip.Fixture.create(0x100);
        family.ship().update(0, null);
        var slots = new java.util.ArrayList<Integer>();
        for (int slot; (slot = family.manager().allocateSlotAfter(family.ship().getSlotIndex())) >= 0;) {
            slots.add(slot);
        }
        seedReady(family.ship(), 0x140, 0);
        family.ship().update(1, null);
        assertEquals(8, getInt(family.ship(), "routine"));
        assertTrue(family.manager().activeObjectsOfType(HczEndBossRobotnikFlame.class).isEmpty());
        int earlier = family.manager().allocateDynamicSlot();
        assertTrue(earlier < family.ship().getSlotIndex());
        family.manager().releaseDynamicSlot(slots.getLast());
        family.ship().update(2, null);
        assertTrue(family.manager().activeObjectsOfType(HczEndBossRobotnikFlame.class).isEmpty());
        var context = family.manager().captureIdentityContext();
        var snapshot = family.ship().captureRewindState(context);
        var restored = ObjectConstructionContext.construct(family.services(),
                () -> family.ship().recreateForRewind(new RewindRecreateContext(
                        family.ship().getSpawn(), snapshot, family.services(), family.manager(), null)));
        restored.setServices(family.services());
        restored.restoreRewindState(snapshot, context);
        restored.update(3, null);
        assertTrue(family.manager().activeObjectsOfType(HczEndBossRobotnikFlame.class).isEmpty());
    }

    private record Fixture(TestHczEndBossRobotnikShip.Fixture family, HczEndBossRobotnikFlame flame) {
        static Fixture create() { return create(null); }
        static Fixture create(PatternSpriteRenderer renderer) {
            var family = TestHczEndBossRobotnikShip.Fixture.create(0x100, renderer);
            family.ship().update(0, null);
            seedReady(family.ship(), 0x140, 0);
            family.ship().update(1, null);
            var flames = family.manager().activeObjectsOfType(HczEndBossRobotnikFlame.class);
            assertEquals(1, flames.size());
            return new Fixture(family, flames.getFirst());
        }
    }
}
