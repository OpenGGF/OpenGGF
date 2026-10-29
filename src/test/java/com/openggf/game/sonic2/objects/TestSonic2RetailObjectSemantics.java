package com.openggf.game.sonic2.objects;

import com.openggf.camera.Camera;
import com.openggf.data.RetailRomRevision;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2NativeDebugGeometry;
import com.openggf.game.sonic2.Sonic2RevisionProfile;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestSonic2RetailObjectSemantics {
    @BeforeEach void setUp() { TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2); }
    @AfterEach void tearDown() { SessionManager.clear(); GameModuleRegistry.reset(); }

    @Test void rev00SpringWallBouncesHurtAndDeadActorsWhileRev01RejectsThem() {
        for (boolean dead : new boolean[]{false, true}) {
            for (var revision : new RetailRomRevision[]{RetailRomRevision.S2_REV00, RetailRomRevision.S2_REV01}) {
                var player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
                NativePositionOps.writeXPosResetSubpixel(player, 0x1001);
                NativePositionOps.writeYPosResetSubpixel(player, 0x1000);
                player.setAir(true);
                player.setHurt(!dead);
                player.setDead(dead);
                var wall = new MTZSpringWallObjectInstance(
                        new ObjectSpawn(0x1000, 0x1000, 0x66, 0, 0, false, 0), "wall");
                wall.setServices(services(revision));
                wall.onSolidContact(player, new SolidContact(false, true, false, false, false), 0);
                assertEquals(revision.isRev00() ? 0x800 : 0, player.getXSpeed(), revision + " dead=" + dead);
                assertEquals(revision.isRev00() ? -0x800 : 0, player.getYSpeed(), revision + " dead=" + dead);
                assertEquals(dead, player.getDead(), "REV00 bounce does not clear the death routine");
            }
        }
    }

    @Test void bothRetailLaunchersCaptureHurtActorButRev01SkipsItsHeldRoutine() {
        for (var revision : new RetailRomRevision[]{RetailRomRevision.S2_REV00, RetailRomRevision.S2_REV01}) {
            var player = new TestablePlayableSprite("sonic", (short) 0x1000, (short) 0x1000);
            player.setHurt(true);
            var spring = new LauncherSpringObjectInstance(
                    new ObjectSpawn(0x1000, 0x1000, 0x85, 0, 0, false, 0), "launcher");
            spring.setServices(services(revision));
            spring.onSolidContact(player, new SolidContact(true, false, false, true, false), 0);
            assertTrue(player.isObjectControlled(), "Fresh capture has no routine-4 guard: " + revision);
            NativePositionOps.writeYPosResetSubpixel(player, 0x7E00);
            player.setJumpInputPressed(true);
            var identities = new RewindIdentityTable();
            identities.registerPlayer(player, PlayerRefId.mainPlayer());
            var context = RewindCaptureContext.withIdentityTable(identities);
            var springBefore = spring.captureRewindState(context);
            var playerBefore = player.captureRewindState();
            spring.update(0, player);
            assertTrue(player.isObjectControlled(), "REV01 returns without releasing the captured control byte");
            if (revision.isRev00()) assertNotEquals(0x7E00, player.getCentreY());
            else assertEquals(0x7E00, player.getCentreY(), "REV01 returns before the position tail");
            int heldY = player.getCentreY();

            player.setHurt(false);
            spring.update(1, player);
            assertNotEquals(0x7E00, player.getCentreY(), "The next ordinary pass writes the held position");

            spring.restoreRewindState(springBefore, context);
            player.restoreRewindState(playerBefore);
            spring.update(0, player);
            assertEquals(heldY, player.getCentreY(), "Replaying the held routine preserves the revision branch");
            assertTrue(player.isObjectControlled());
        }
    }

    @Test void actualSolidCheckpointAdmitsHurtWallContactsButRejectsFreshDeadContactsInBothRevisions() {
        for (boolean dead : new boolean[]{false, true}) {
            for (var revision : new RetailRomRevision[]{RetailRomRevision.S2_REV00, RetailRomRevision.S2_REV01}) {
                var player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
                NativePositionOps.writeXPosResetSubpixel(player, 0x1001);
                NativePositionOps.writeYPosResetSubpixel(player, 0x1000);
                player.setAir(true); player.setHurt(!dead); player.setDead(dead);
                var services = services(revision);
                var wall = new MTZSpringWallObjectInstance(
                        new ObjectSpawn(0x1000, 0x1000, 0x66, 0, 0, false, 0), "wall");
                wall.setServices(services);
                // The manager's frame snapshot retires the ordinary spawn-only
                // solid skip before an established object's checkpoint executes.
                wall.snapshotPreUpdatePosition();
                services.objectManager().processImmediateInlineSolidCheckpoint(wall, player, java.util.List.of());
                if (!dead && revision.isRev00()) assertEquals(0x800, player.getXSpeed());
                else assertNotEquals(0x800, player.getXSpeed());
                assertEquals(dead, player.getDead(), "Shared SolidObject_cont rejects routine >=6");
            }
        }
    }

    @Test void rev00LaunchResumesADeadCapturedActorWhileRev01PreservesItsHeldState() {
        for (var revision : new RetailRomRevision[]{RetailRomRevision.S2_REV00, RetailRomRevision.S2_REV01}) {
            var player = new TestablePlayableSprite("sonic", (short) 0x1000, (short) 0x1000);
            var spring = new LauncherSpringObjectInstance(
                    new ObjectSpawn(0x1000, 0x1000, 0x85, 0, 0, false, 0), "launcher");
            spring.setServices(services(revision));
            spring.onSolidContact(player, new SolidContact(true, false, false, true, false), 0);
            player.setJumpInputPressed(true); spring.update(0, player);
            player.setJumpInputPressed(false); spring.update(1, player);
            player.setDead(true); spring.update(2, player);
            assertEquals(!revision.isRev00(), player.getDead());
            assertEquals(!revision.isRev00(), player.isObjectControlled());
            if (revision.isRev00()) assertTrue(player.getYSpeed() < 0);
        }
    }

    @Test void nativeDebugGeometryRequiresRev00AndActualPlacementMode() {
        var player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        Camera camera = mock(Camera.class);
        when(camera.getFocusedSprite()).thenReturn(player);
        var early = services(RetailRomRevision.S2_REV00).withCamera(camera);
        var late = services(RetailRomRevision.S2_REV01).withCamera(camera);
        assertFalse(Sonic2NativeDebugGeometry.visible(early));
        player.setDebugMode(true);
        assertTrue(Sonic2NativeDebugGeometry.visible(early));
        assertFalse(Sonic2NativeDebugGeometry.visible(late));
        assertFalse(Sonic2NativeDebugGeometry.visible(services(RetailRomRevision.UNKNOWN).withCamera(camera)));
    }

    private TestObjectServices services(RetailRomRevision revision) {
        var profile = new Sonic2RevisionProfile(revision);
        return new TestObjectServices() {
            @Override public <T> T gameService(Class<T> type) {
                return type == Sonic2RevisionProfile.class ? type.cast(profile) : null;
            }
        }.withIsolatedObjectManager();
    }
}
