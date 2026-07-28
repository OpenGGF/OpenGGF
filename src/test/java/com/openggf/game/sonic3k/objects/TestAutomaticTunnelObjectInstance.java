package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.ObjectControlState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestAutomaticTunnelObjectInstance {
    @Test
    void sidekickProcessingKeepsNativeP2PrefixThenProcessesExtensions() {
        AutomaticTunnelObjectInstance tunnel = new AutomaticTunnelObjectInstance(
                new ObjectSpawn(0x0F60, 0x0578, 0x24, 0, 0, false, 0));
        TestPlayableSprite nativeP2 = new TestPlayableSprite();
        TestPlayableSprite extraSidekick = new TestPlayableSprite();
        tunnel.setServices(new TestObjectServices().withSidekicks(List.of(nativeP2, extraSidekick)));

        TestPlayableSprite main = new TestPlayableSprite();
        main.setCentreX((short) 0x0100);
        main.setCentreY((short) 0x0100);
        nativeP2.setCentreX((short) 0x0F60);
        nativeP2.setCentreY((short) 0x0578);
        extraSidekick.setCentreX((short) 0x0F60);
        extraSidekick.setCentreY((short) 0x0578);

        tunnel.update(0, main);

        assertFalse(main.isObjectControlled());
        assertTrue(nativeP2.isObjectControlled());
        assertTrue(extraSidekick.isObjectControlled(),
                "additional sidekicks must receive independent tunnel traversal state after native P2");
    }

    @Test
    void activeTunnelStateFollowsActorsAcrossNativeP2ReorderAndUnload() {
        AutomaticTunnelObjectInstance tunnel = new AutomaticTunnelObjectInstance(
                new ObjectSpawn(0x0F60, 0x0578, 0x24, 0, 0, false, 0));
        TestPlayableSprite main = playerAt(0x0100, 0x0100);
        TestPlayableSprite capturedP2 = playerAt(0x0F60, 0x0578);
        TestPlayableSprite replacementP2 = playerAt(0x0100, 0x0100);
        List<com.openggf.game.PlayableEntity> sidekicks = new ArrayList<>(List.of(capturedP2, replacementP2));
        tunnel.setServices(new StubObjectServices().withPlayerQuery(
                new ObjectPlayerQuery(() -> main, () -> sidekicks)));
        tunnel.update(0, main);

        sidekicks.clear();
        sidekicks.add(replacementP2);
        sidekicks.add(capturedP2);
        tunnel.update(1, main);

        assertTrue(capturedP2.isObjectControlled(), "demoted P2 must continue its own tunnel route");
        assertFalse(replacementP2.isObjectControlled(), "new P2 must not inherit the old actor's route state");
        tunnel.onUnload();
        assertFalse(capturedP2.isObjectControlled(), "unload must release the demoted actual owner");
    }

    @Test
    void omittedOrDeadExtensionTunnelRiderIsReleased() {
        AutomaticTunnelObjectInstance tunnel = new AutomaticTunnelObjectInstance(
                new ObjectSpawn(0x0F60, 0x0578, 0x24, 0, 0, false, 0));
        TestPlayableSprite main = playerAt(0x0100, 0x0100);
        TestPlayableSprite nativeP2 = playerAt(0x0100, 0x0100);
        TestPlayableSprite extra = playerAt(0x0F60, 0x0578);
        List<com.openggf.game.PlayableEntity> sidekicks = new ArrayList<>(List.of(nativeP2, extra));
        tunnel.setServices(new StubObjectServices().withPlayerQuery(
                new ObjectPlayerQuery(() -> main, () -> sidekicks)));
        tunnel.update(0, main);
        assertTrue(extra.isObjectControlled());

        sidekicks.remove(extra);
        tunnel.update(1, main);
        assertFalse(extra.isObjectControlled(), "omitted extension must not remain under tunnel control");

        sidekicks.add(extra);
        extra.setDead(false);
        extra.setCentreX((short) 0x0F60);
        extra.setCentreY((short) 0x0578);
        tunnel.update(2, main);
        extra.setDead(true);
        tunnel.update(3, main);
        assertFalse(extra.isObjectControlled(), "death must release extension tunnel control");
    }

    @Test
    void unrelatedControlTakeoverIsNotClearedByRosterOmissionOrUnload() {
        AutomaticTunnelObjectInstance tunnel = new AutomaticTunnelObjectInstance(
                new ObjectSpawn(0x0F60, 0x0578, 0x24, 0, 0, false, 0));
        TestPlayableSprite main = playerAt(0x0100, 0x0100);
        TestPlayableSprite nativeP2 = playerAt(0x0100, 0x0100);
        TestPlayableSprite extra = playerAt(0x0F60, 0x0578);
        List<com.openggf.game.PlayableEntity> sidekicks = new ArrayList<>(List.of(nativeP2, extra));
        tunnel.setServices(new StubObjectServices().withPlayerQuery(
                new ObjectPlayerQuery(() -> main, () -> sidekicks)));
        tunnel.update(0, main);

        ObjectControlState.nativeBit7FullControl().applyTo(extra);
        extra.setControlLocked(true);
        sidekicks.remove(extra);
        tunnel.update(1, main);
        tunnel.onUnload();

        assertTrue(extra.isObjectControlled(), "tunnel must not release a later owner's matching generic control bits");
        assertTrue(extra.isControlLocked());
    }

    @Test
    void rewindRelinksExtensionTunnelOwnerToReplacementPlayer() {
        AutomaticTunnelObjectInstance tunnel = new AutomaticTunnelObjectInstance(
                new ObjectSpawn(0x0F60, 0x0578, 0x24, 0, 0, false, 0));
        TestPlayableSprite oldMain = playerAt(0x0100, 0x0100);
        TestPlayableSprite oldP2 = playerAt(0x0100, 0x0100);
        TestPlayableSprite oldExtra = playerAt(0x0F60, 0x0578);
        tunnel.setServices(new StubObjectServices().withPlayerQuery(
                new ObjectPlayerQuery(() -> oldMain, () -> List.of(oldP2, oldExtra))));
        tunnel.update(0, oldMain);
        RewindIdentityTable captured = identities(oldMain, oldP2, oldExtra);
        var snapshot = tunnel.captureRewindState(RewindCaptureContext.withIdentityTable(captured));

        TestPlayableSprite newMain = playerAt(0x0100, 0x0100);
        TestPlayableSprite newP2 = playerAt(0x0100, 0x0100);
        TestPlayableSprite newExtra = playerAt(0x0F60, 0x0578);
        tunnel.setServices(new StubObjectServices().withPlayerQuery(
                new ObjectPlayerQuery(() -> newMain, () -> List.of(newP2, newExtra))));
        tunnel.restoreRewindState(snapshot,
                RewindCaptureContext.withIdentityTable(identities(newMain, newP2, newExtra)));
        ObjectControlState.nativeBit7FullControl().applyTo(newExtra);
        newExtra.setControlLocked(true);

        tunnel.onUnload();

        assertFalse(newExtra.isObjectControlled());
        assertTrue(oldExtra.isObjectControlled(), "rewind cleanup must not target the stale actor instance");
    }

    private static RewindIdentityTable identities(TestPlayableSprite main,
                                                   TestPlayableSprite p2,
                                                   TestPlayableSprite extra) {
        RewindIdentityTable table = new RewindIdentityTable();
        table.registerPlayer(main, PlayerRefId.mainPlayer());
        table.registerPlayer(p2, PlayerRefId.sidekick(0));
        table.registerPlayer(extra, PlayerRefId.sidekick(1));
        return table;
    }

    private static TestPlayableSprite playerAt(int x, int y) {
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) x);
        player.setCentreY((short) y);
        return player;
    }

    @Test
    void captureUsesFullControlAndRouteReleaseClearsControlPolicy() {
        AutomaticTunnelObjectInstance tunnel = new AutomaticTunnelObjectInstance(
                new ObjectSpawn(0x0F60, 0x0578, 0x24, 0, 0, false, 0));
        tunnel.setServices(new TestObjectServices());

        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x0F60);
        player.setCentreY((short) 0x0578);
        player.setSubpixelRaw(0x5100, 0x8500);
        player.setControlLocked(false);
        player.setObjectControlled(false);
        player.setRolling(false);

        tunnel.update(0, player);

        assertTrue(player.isObjectControlled());
        assertFalse(player.getRolling(),
                "Obj_AutoTunnelInit writes anim=2 without setting Status_Roll");
        assertEquals(0x5100, player.getXSubpixelRaw(),
                "ROM word writes to x_pos preserve the fractional word");
        assertEquals(0x8500, player.getYSubpixelRaw(),
                "ROM word writes to y_pos preserve the fractional word");
        assertFalse(player.isObjectControlAllowsCpu());
        assertTrue(player.isObjectControlSuppressesMovement());
        assertTrue(player.isTouchResponseSuppressedByObjectControl());
        assertTrue(player.isControlLocked());

        for (int frame = 1; frame <= 80 && player.isObjectControlled(); frame++) {
            tunnel.update(frame, player);
        }

        assertFalse(player.isObjectControlled());
        assertFalse(player.isObjectControlAllowsCpu());
        assertFalse(player.isObjectControlSuppressesMovement());
        assertFalse(player.isTouchResponseSuppressedByObjectControl());
        assertFalse(player.isControlLocked());
    }

    @Test
    void lbz2ModeSpawnsTunnelExhaustAtPlayerExitWithCurrentVelocity() {
        RecordingServices services = new RecordingServices();
        AutomaticTunnelObjectInstance tunnel = new AutomaticTunnelObjectInstance(
                new ObjectSpawn(0x0F60, 0x0578, 0x24, 0x60, 0, false, 0));
        tunnel.setServices(services);

        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x0F60);
        player.setCentreY((short) 0x0578);

        for (int frame = 0; frame < 120 && services.children.isEmpty(); frame++) {
            tunnel.update(frame, player);
        }

        assertEquals(1, services.children.size(),
                "ROM bit 5 creates Obj_TunnelExhaustControl when the automatic tunnel route exits");
        AbstractObjectInstance child = services.children.get(0);
        assertInstanceOf(TunnelExhaustControlObjectInstance.class, child);
        assertEquals(player.getCentreX(), child.getX());
        assertEquals(player.getCentreY() + 0x10, child.getY(),
                "ROM allocates the exhaust before loc_29768 applies the final upward path step");
        assertEquals(0, intField(child, "subtype"),
                "Obj_AutomaticTunnel exit spawn does not copy subtype; subtype 0 selects directional exhaust");
        assertEquals(0, intField(child, "xVel"));
        assertEquals(0xF000, intField(child, "yVel") & 0xFFFF);
    }

    @Test
    void tunnelExhaustParticleRendersRomWaterFrame() {
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        when(renderManager.getRenderer(Sonic3kObjectArtKeys.LBZ_TUNNEL_EXHAUST)).thenReturn(renderer);
        when(renderer.isReady()).thenReturn(true);

        TunnelExhaustParticleInstance particle = new TunnelExhaustParticleInstance(
                new ObjectSpawn(0x1800, 0x0640, 0, 0, 0, false, 0),
                0, 0x400, true);
        particle.setServices(new RenderingServices(renderManager));

        particle.appendRenderCommands(new ArrayList<GLCommand>());

        verify(renderManager).getRenderer(Sonic3kObjectArtKeys.LBZ_TUNNEL_EXHAUST);
        verify(renderer).drawFrameIndex(1, 0x1800, 0x0640, false, false, 2);
    }

    @Test
    void tunnelExhaustControlUsesRomVerticalWaterFrameForUpwardExit() {
        RecordingServices services = new RecordingServices();
        TunnelExhaustControlObjectInstance control = new TunnelExhaustControlObjectInstance(
                new ObjectSpawn(0x1800, 0x0640, 0, 0, 0, false, 0),
                0, 0, -0x1000);
        control.setServices(services);

        control.update(0, new TestPlayableSprite());

        assertEquals(1, services.children.size());
        AbstractObjectInstance child = services.children.get(0);
        assertInstanceOf(TunnelExhaustParticleInstance.class, child);
        assertEquals(1, intField(child, "mappingFrame"));
        assertEquals(0x86, intField(child, "renderFlags"));
        assertEquals(0, motionVelocity(child, "xVel"));
        assertEquals(0xFA00, motionVelocity(child, "yVel") & 0xFFFF);
        assertFalse(booleanField(child, "horizontal"));
    }

    @Test
    void tunnelExhaustControlUsesRomHorizontalWaterFrameForSideExit() {
        RecordingServices services = new RecordingServices();
        TunnelExhaustControlObjectInstance control = new TunnelExhaustControlObjectInstance(
                new ObjectSpawn(0x1800, 0x0640, 0, 0, 0, false, 0),
                0, 0x1000, 0);
        control.setServices(services);

        control.update(0, new TestPlayableSprite());

        assertEquals(1, services.children.size());
        AbstractObjectInstance child = services.children.get(0);
        assertInstanceOf(TunnelExhaustParticleInstance.class, child);
        assertEquals(0, intField(child, "mappingFrame"));
        assertEquals(0x84, intField(child, "renderFlags"));
        assertEquals(0x0600, motionVelocity(child, "xVel") & 0xFFFF);
        assertEquals(0, motionVelocity(child, "yVel"));
        assertTrue(booleanField(child, "horizontal"));
    }

    private static int intField(Object instance, String name) {
        try {
            var field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(instance);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static boolean booleanField(Object instance, String name) {
        try {
            var field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getBoolean(instance);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static int motionVelocity(Object instance, String fieldName) {
        try {
            var motionField = instance.getClass().getDeclaredField("motion");
            motionField.setAccessible(true);
            Object motion = motionField.get(instance);
            var velocityField = motion.getClass().getDeclaredField(fieldName);
            velocityField.setAccessible(true);
            return velocityField.getInt(motion);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static final class RecordingServices extends TestObjectServices {
        private final ObjectManager objectManager;
        private final List<AbstractObjectInstance> children = new ArrayList<>();

        private RecordingServices() {
            objectManager = mock(ObjectManager.class);
            doAnswer(invocation -> {
                children.add(invocation.getArgument(0));
                return null;
            }).when(objectManager).addDynamicObjectAfterCurrent(any(AbstractObjectInstance.class));
        }

        @Override
        public ObjectManager objectManager() {
            return objectManager;
        }
    }

    private static final class RenderingServices extends TestObjectServices {
        private final ObjectRenderManager renderManager;

        private RenderingServices(ObjectRenderManager renderManager) {
            this.renderManager = renderManager;
        }

        @Override
        public ObjectRenderManager renderManager() {
            return renderManager;
        }
    }
}
