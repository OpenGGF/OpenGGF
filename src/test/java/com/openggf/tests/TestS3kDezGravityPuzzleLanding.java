package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.S3kDezGravityPuzzleObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.CollisionTrace;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real full-solid landings: Obj_DEZGravityPuzzle's width byte differs from d1 - $B. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezGravityPuzzleLanding {
    @AfterEach void reset() {
        GameServices.collision().setTrace(null);
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @ParameterizedTest
    @CsvSource({"0,-32,true", "0,-33,false", "0,31,true", "0,32,false",
            "1,-32,true", "1,-33,false", "1,31,true", "1,32,false",
            "0,0,true", "1,0,true"})
    void newLandingUsesTheNativeUnsignedWidthByte(int participant, int dx, boolean lands) {
        var fixture = fixture(320);
        var puzzle = place();
        dispatch(fixture, 0);
        var player = players(fixture).get(participant);
        prepare(player, puzzle, dx);
        var other = players(fixture).get(1 - participant);
        var untouched = state(other);
        dispatch(fixture, 0);
        var manager = GameServices.level().getObjectManager();
        assertEquals(lands, manager.isRidingObject(player, puzzle), "native -$20 <= dx < $20");
        assertEquals(lands, manager.hasObjectStandingBit(player, puzzle));
        assertEquals(untouched, state(other), "other player has no contact");
        if (lands) assertSeat(player, puzzle);
        else {
            assertTrue(player.getAir());
            assertEquals(0x2F, player.getYSpeed());
            assertEquals((short) 0xFCC0, player.getGSpeed());
        }
    }

    @ParameterizedTest
    @CsvSource({"0,320", "1,320", "0,800", "1,800"})
    void controlledLandingRecreatesAndReplaysWithoutLeakingTheSeat(int participant, int width) {
        var fixture = fixture(width);
        var manager = GameServices.level().getObjectManager();
        var puzzle = place();
        dispatch(fixture, 0);
        var player = players(fixture).get(participant);
        var other = players(fixture).get(1 - participant);
        var untouched = state(other);
        prepare(player, puzzle, -0x1E);
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(player);
        // Positive $01: SolidObjectFull2 still admits this slot.
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var before = registry.capture();
        int slot = puzzle.getSlotIndex();
        dispatch(fixture, 0);
        assertSeat(player, puzzle);
        assertTrue(player.isObjectControlled());
        var contact = state(player);
        var forward = forward(fixture, player);
        manager.removeDynamicObject(puzzle);
        registry.restore(before);
        var restored = manager.getActiveObjects().stream()
                .filter(o -> o instanceof S3kDezGravityPuzzleObjectInstance p && p.getSlotIndex() == slot)
                .map(o -> (S3kDezGravityPuzzleObjectInstance) o).findFirst().orElseThrow();
        assertNotSame(puzzle, restored);
        dispatch(fixture, 0);
        assertSeat(player, restored);
        assertEquals(contact, state(player));
        assertEquals(forward, forward(fixture, player), "24 subsequent object dispatches replay exactly");
        assertEquals(untouched, state(other));
        manager.removeDynamicObject(restored);
        var replacement = new S3kDezGravityPuzzleObjectInstance(puzzle.getSpawn());
        manager.addDynamicObjectAtSlot(replacement, slot);
        assertEquals(slot, replacement.getSlotIndex());
        for (var p : players(fixture)) {
            assertFalse(manager.hasObjectStandingBit(p, replacement));
            assertFalse(manager.isRidingObject(p, replacement));
        }
    }

    @ParameterizedTest
    @CsvSource({"0", "1"})
    void bobbingAirborneSeatReturnsAtItsOwnCheckpoint(int participant) {
        var fixture = fixture(320);
        var manager = GameServices.level().getObjectManager();
        // A lower native slot runs first, outside either player's contact range.
        manager.addDynamicObject(new S3kDezGravityPuzzleObjectInstance(
                new ObjectSpawn(0x680, 0x500, 0x61, 0, 0, false, 0)));
        var puzzle = place();
        dispatch(fixture, 0);
        var player = players(fixture).get(participant);
        prepare(player, puzzle, 0);
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(player);
        dispatch(fixture, 1);
        assertSeat(player, puzzle);
        player.setAir(true); // The later turbine slot sets InAir after this solid's seat.
        player.setYSpeed((short) 0x2F);
        int y = player.getCentreY();
        int oldObjectY = puzzle.getY();
        var trace = mock(CollisionTrace.class);
        doAnswer(call -> {
            if ((int) call.getArgument(1) == puzzle.getX()) {
                assertTrue(player.isOnObject(), "an earlier solid must not consume this occupant's ride");
                assertTrue(manager.hasObjectStandingBit(player, puzzle),
                        "bobbing metadata must retain the native SST occupant's bit until its helper runs");
            }
            return null;
        }).when(trace).onSolidCheckpointStart(anyString(), anyInt(), anyInt());
        GameServices.collision().setTrace(trace);
        dispatch(fixture, 2);
        GameServices.collision().setTrace(null);
        verify(trace).onSolidCheckpointStart(eq("S3kDezGravityPuzzleObjectInstance"),
                eq(puzzle.getX()), eq(puzzle.getY()));
        assertNotEquals(oldObjectY, puzzle.getY(), "the shaft actually bobbed between solid calls");
        assertFalse(manager.hasObjectStandingBit(player, puzzle));
        assertFalse(manager.isRidingObject(player, puzzle));
        assertFalse(player.isOnObject());
        assertTrue(player.getAir());
        assertEquals(y, player.getCentreY(), "loc_1DCF0 returns without a new-contact Y lift");
        assertEquals(0x2F, player.getYSpeed(), "no same-dispatch re-landing velocity reset");
        assertEquals((short) 0xFD30, player.getGSpeed());
        assertEquals(0x0EE8, player.getYSubpixelRaw());
    }

    private void assertSeat(AbstractPlayableSprite player, S3kDezGravityPuzzleObjectInstance puzzle) {
        var manager = GameServices.level().getObjectManager();
        assertTrue(manager.isRidingObject(player, puzzle));
        assertTrue(manager.hasObjectStandingBit(player, puzzle));
        // loc_1E154 (sonic3k.asm:41651-41681); RideObject_SetRide (sonic3k.asm:42067-42091).
        assertEquals(puzzle.getY() - 0x30 - player.getYRadius() - 1, player.getCentreY());
        assertEquals(0, player.getYSpeed());
        assertEquals((short) 0xFD30, player.getGSpeed(), "seat copies the current X velocity");
        assertEquals(0x0EE8, player.getYSubpixelRaw(), "native word-only Y correction");
        assertFalse(player.getAir());
        assertTrue(player.isOnObject());
        assertEquals(0, puzzle.panelBitsForTest(), "a top landing is not a bumper-panel push");
    }

    private void prepare(AbstractPlayableSprite player, S3kDezGravityPuzzleObjectInstance puzzle, int dx) {
        player.setRenderFlagOnScreen(true);
        player.setAir(true);
        player.setOnObject(false);
        NativePositionOps.writeXPosResetSubpixel(player, puzzle.getX() + dx);
        NativePositionOps.writeYPosResetSubpixel(player, puzzle.getY() - 0x30 - player.getYRadius() - 1);
        player.setSubpixelRaw(0x1234, 0x0EE8);
        player.setXSpeed((short) 0xFD30);
        player.setYSpeed((short) 0x2F);
        player.setGSpeed((short) 0xFCC0);
    }

    private S3kDezGravityPuzzleObjectInstance place() {
        var puzzle = new S3kDezGravityPuzzleObjectInstance(
                new ObjectSpawn(0x600, 0x500, 0x61, 0, 0, false, 0));
        GameServices.level().getObjectManager().addDynamicObject(puzzle);
        return puzzle;
    }

    private List<AbstractPlayableSprite> players(HeadlessTestFixture fixture) {
        return List.of(fixture.sprite(), GameServices.sprites().getRegisteredSidekicks().getFirst());
    }

    private HeadlessTestFixture fixture(int width) {
        var c = SonicConfigurationService.getInstance();
        c.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        c.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        c.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                width == 320 ? WidescreenAspect.NATIVE_4_3.name() : WidescreenAspect.SUPER_32_9.name());
        c.resolveDisplayAspect();
        c.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(11, 0)
                .startPosition((short) 0x400, (short) 0x400).startPositionIsCentre().build();
        fixture.camera().setX((short) 0x500);
        fixture.camera().setY((short) 0x480);
        assertEquals(width, fixture.camera().getWidth());
        for (var p : players(fixture)) {
            NativePositionOps.writeXPosResetSubpixel(p, 0x400);
            NativePositionOps.writeYPosResetSubpixel(p, 0x400);
        }
        return fixture;
    }

    private void dispatch(HeadlessTestFixture fixture, int vInt) {
        GameServices.level().getObjectManager().update(fixture.camera().getX(), fixture.sprite(),
                GameServices.sprites().getRegisteredSidekicks(), vInt, false, true, true);
    }

    private List<Integer> state(AbstractPlayableSprite p) {
        return List.of((int) p.getCentreX(), (int) p.getCentreY(), p.getXSubpixelRaw(), p.getYSubpixelRaw(),
                (int) p.getXSpeed(), (int) p.getYSpeed(), (int) p.getGSpeed(), p.getAir() ? 1 : 0,
                p.isOnObject() ? 1 : 0, p.isObjectControlled() ? 1 : 0);
    }

    private List<List<Integer>> forward(HeadlessTestFixture fixture, AbstractPlayableSprite player) {
        var rows = new ArrayList<List<Integer>>();
        for (int i = 1; i <= 24; i++) {
            dispatch(fixture, i);
            rows.add(state(player));
        }
        return rows;
    }
}
