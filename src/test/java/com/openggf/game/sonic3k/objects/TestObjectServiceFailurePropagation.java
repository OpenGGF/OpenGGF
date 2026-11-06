package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.sprites.playable.Sonic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Engine service bugs must escape the object that encounters them. No ROM or device required. */
class TestObjectServiceFailurePropagation {
    @org.junit.jupiter.api.BeforeEach void setVisibleBounds() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 0x4000, 0x2000, 0);
    }

    @org.junit.jupiter.api.AfterEach void resetVisibleBounds() {
        AbstractObjectInstance.resetCameraBoundsForTests();
    }

    private static ObjectSpawn spawn() {
        return new ObjectSpawn(0x500, 0x300, 0, 0, 0, false, 0);
    }

    @Test void introDoesNotReplaceCameraFailureWithTheCandidatePlayer() {
        var failure = new IllegalStateException("camera owner failed");
        ObjectServices services = mock(ObjectServices.class);
        when(services.camera()).thenThrow(failure);
        var intro = new AizPlaneIntroInstance(spawn());
        intro.setServices(services);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> intro.update(0, null)));
    }

    @Test void introDoesNotHidePlayerArtReadFailure() throws java.io.IOException {
        var failure = new java.io.IOException("player ROM read failed");
        ObjectServices services = mock(ObjectServices.class);
        when(services.camera()).thenReturn(new com.openggf.camera.Camera());
        when(services.rom()).thenThrow(failure);
        var intro = new AizPlaneIntroInstance(spawn());
        intro.setServices(services);
        assertSame(failure, assertThrows(java.io.UncheckedIOException.class,
                () -> intro.update(0, null)).getCause());
    }

    @Test void emitterDoesNotReplaceLevelClockFailureWithVInt() {
        var failure = new IllegalStateException("level clock owner failed");
        ObjectServices services = mock(ObjectServices.class);
        when(services.levelManager()).thenThrow(failure);
        var fall = new LrzLavaFallObjectInstance(spawn());
        fall.setServices(services);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> fall.update(0, null)));
    }

    @Test void dropDoesNotReplaceLevelClockFailureWithVInt() {
        var failure = new IllegalStateException("level clock owner failed");
        ObjectServices services = mock(ObjectServices.class);
        when(services.levelManager()).thenThrow(failure);
        var drop = new LrzLavaFallDropInstance(0x500, 0x300, true, false);
        drop.setServices(services);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> drop.update(0, null)));
    }

    @Test void dropDoesNotHideSoundFailureOnItsLevelClockGate() {
        var failure = new IllegalStateException("audio owner failed");
        ObjectServices services = mock(ObjectServices.class, RETURNS_DEEP_STUBS);
        when(services.levelManager().getFrameCounter()).thenReturn(0x10);
        doThrow(failure).when(services).playSfx(anyInt());
        var drop = new LrzLavaFallDropInstance(0x500, 0x300, true, false);
        drop.setServices(services);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> drop.update(1, null)));
    }

    @Test void launcherDoesNotHideSoundFailure() {
        var failure = new IllegalStateException("audio owner failed");
        ObjectServices services = mock(ObjectServices.class, RETURNS_DEEP_STUBS);
        doThrow(failure).when(services).playSfx(anyInt());
        var launcher = new FbzDezPlayerLauncherInstance(spawn());
        launcher.setServices(services);
        var sonic = new Sonic("sonic", (short) 0x500, (short) 0x300);
        launcher.onSolidContact(sonic, new SolidContact(true, false, false, false, false), 0);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> launcher.update(1, sonic)));
    }
}
