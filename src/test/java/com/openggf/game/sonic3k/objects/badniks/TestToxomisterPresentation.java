package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Drawing and animation oracles from Obj_Toxomister and byte_90074/byte_90085. */
@RequiresRom(SonicGame.SONIC_3K)
class TestToxomisterPresentation {
    private TestObjectServices services;
    private PatternSpriteRenderer renderer;

    @BeforeEach
    void setup() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
        var level = mock(LevelManager.class);
        var renderManager = mock(ObjectRenderManager.class);
        renderer = mock(PatternSpriteRenderer.class);
        when(level.getObjectRenderManager()).thenReturn(renderManager);
        when(renderManager.getRenderer(Sonic3kObjectArtKeys.TOXOMISTER)).thenReturn(renderer);
        when(renderer.isReady()).thenReturn(true);
        services = new TestObjectServices().withIsolatedObjectManager();
        when(level.getObjectManager()).thenReturn(services.objectManager());
        services.withLevelManager(level);
        assertNotNull(services.romReader());
    }

    @AfterEach
    void cleanup() {
        AbstractObjectInstance.resetCameraBoundsForTests();
    }

    @Test
    void bodyDrawsLowerSpriteAtNativeCentreAndHonoursPlacementFlips() {
        for (int flags = 0; flags < 4; flags++) {
            var body = new ToxomisterBadnikInstance(new ObjectSpawn(160, 100, 0x9B, 0, flags, false, 0));
            body.setServices(services);
            body.update(1, null);
            body.appendRenderCommands(new ArrayList<>());
            boolean flipY = (flags & 2) != 0;
            verify(renderer).drawFrameIndex(1, 160, 100, (flags & 1) != 0, flipY);
            verify(renderer).drawFrameIndex(0, 160, flipY ? 76 : 124, (flags & 1) != 0, flipY);
            assertEquals((flags & 1) == 0 ? 148 : 172, body.cloud().getCentreX());
            clearInvocations(renderer);
        }
    }

    @Test
    void collisionParentNeverDrawsAnExtraPuff() {
        cloud().appendRenderCommands(new ArrayList<>());
        verifyNoInteractions(renderer);
    }

    @Test
    void puffsGrowCycleAndUseUpdateVIntForFlicker() {
        var puff = puff(cloud(), 12, 4, 4);
        puff.update(1, null); // zero-delay Obj_Wait callback; no Draw_Sprite yet
        puff.appendRenderCommands(new ArrayList<>());
        verifyNoInteractions(renderer);
        // Animate_Raw increments anim_frame before reading: 3,4,5,6,7 then
        // $F8,+9 jumps to the delay-7 loop, whose first mapping is 6.
        int[] expected = {3, 4, 5, 6, 7, 6, 5, 4, 5, 6, 7, 6};
        for (int frame = 2; frame < 2 + expected.length * 8; frame++) {
            puff.update(frame, null);
            puff.appendRenderCommands(new ArrayList<>());
            if ((frame & 1) == 0) {
                verify(renderer).drawFrameIndex(expected[(frame - 2) / 8], 164, 104, false, false);
            } else {
                verifyNoInteractions(renderer);
            }
            clearInvocations(renderer);
        }
    }

    @Test
    void dyingPuffKeepsOffsetShrinksAndDeletesWithReplayAfterParentRetires() {
        var cloud = cloud();
        var puff = puff(cloud, 12, 4, 4);
        puff.update(1, null);
        puff.update(2, null); // mapping 3, anim_frame 1, timer 7
        var player = new com.openggf.tests.TestablePlayableSprite("sonic", (short) 160, (short) 100);
        player.setAnimationId(com.openggf.game.sonic3k.constants.Sonic3kAnimationIds.WALK.id());
        cloud.onTouchResponse(player, null, 2);
        cloud.update(3, player);
        player.setAnimationId(com.openggf.game.sonic3k.constants.Sonic3kAnimationIds.SPINDASH.id());
        cloud.update(4, player);
        puff.update(4, player);
        assertEquals(164, puff.getCentreX(), "loc_8FF12 does not recenter the puff");
        assertEquals(104, puff.getCentreY());
        assertEquals(-0x100, puff.xVel(), "word offset 0; player faces right");
        var context = RewindCaptureContext.none();
        var saved = puff.captureRewindState(context); // detached: no stale parent identity
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) {
                puff.restoreRewindState(saved, context);
            }
            for (int tick = 1; tick <= 40; tick++) {
                puff.update(4 + tick, player);
                if (tick < 40) {
                    assertFalse(puff.isDestroyed(), "death callback before tick " + tick);
                }
                if (tick == 8) {
                    clearInvocations(renderer);
                    puff.appendRenderCommands(new ArrayList<>());
                    verify(renderer).drawFrameIndex(5, puff.getCentreX(), puff.getCentreY(), false, false);
                }
            }
            assertTrue(puff.isDestroyed(), "carried frame 1/timer 7 reaches $F4 in 40 ticks");
        }
    }

    @Test
    void growingPuffRecreationRestoresAnimationAndReplacementParent() {
        var originalCloud = cloud();
        var original = puff(originalCloud, 12, 4, 4);
        for (int frame = 1; frame <= 22; frame++) {
            original.update(frame, null);
        }
        var id = com.openggf.game.rewind.identity.ObjectRefId.layout(7, 1, 12);
        var before = new com.openggf.game.rewind.identity.RewindIdentityTable();
        before.registerObject(originalCloud, id);
        var saved = original.captureRewindState(RewindCaptureContext.withIdentityTable(before));
        var replacement = cloud();
        var after = new com.openggf.game.rewind.identity.RewindIdentityTable();
        after.registerObject(replacement, id);
        var restored = original.recreateForRewind(new com.openggf.level.objects.RewindRecreateContext(
                original.getSpawn(), saved, services));
        restored.setServices(services); // ObjectManager injects after recreation in production.
        restored.restoreRewindState(saved, RewindCaptureContext.withIdentityTable(after));
        for (int frame = 23; frame <= 100; frame++) {
            original.update(frame, null);
            restored.update(frame, null);
            assertEquals(original.getCentreX(), restored.getCentreX());
            assertEquals(original.getCentreY(), restored.getCentreY());
            clearInvocations(renderer);
            original.appendRenderCommands(new ArrayList<>());
            var expected = mockingDetails(renderer).getInvocations().stream()
                    .map(invocation -> java.util.Arrays.asList(invocation.getArguments())).toList();
            clearInvocations(renderer);
            restored.appendRenderCommands(new ArrayList<>());
            var actual = mockingDetails(renderer).getInvocations().stream()
                    .map(invocation -> java.util.Arrays.asList(invocation.getArguments())).toList();
            assertEquals(expected, actual, "recreated growth frame " + frame);
        }
        // The replacement owner, not the retired original, drives subsequent following.
        var player = new com.openggf.tests.TestablePlayableSprite("sonic", (short) 180, (short) 110);
        player.setCentreX((short) 180);
        player.setCentreY((short) 110);
        replacement.onTouchResponse(player, null, 101);
        replacement.update(101, player);
        replacement.update(102, player);
        restored.update(102, player);
        assertEquals(184, restored.getCentreX());
        assertEquals(114, restored.getCentreY());
    }

    @Test
    void ordinaryDispersalShrinksAllSevenPuffsWithoutHorizontalScatter() {
        var body = new ToxomisterBadnikInstance(new ObjectSpawn(160, 100, 0x9B, 0, 0, false, 0));
        var cloud = cloud();
        cloud.attachTo(body);
        var puffs = new ArrayList<ToxomisterPuffInstance>();
        for (int index = 0; index <= 12; index += 2) {
            puffs.add(puff(cloud, index, index - 6, 4));
        }
        for (int frame = 1; frame <= 70; frame++) {
            for (var puff : puffs) {
                puff.update(frame, null);
            }
        }
        body.setDestroyed(true);
        cloud.update(71, null);
        assertTrue(cloud.puffsDispersing());
        for (var puff : puffs) {
            int x = puff.getCentreX();
            puff.update(71, null);
            assertEquals(x, puff.getCentreX());
            assertEquals(0, puff.xVel());
            for (int tick = 1; tick <= 48 && !puff.isDestroyed(); tick++) {
                puff.update(71 + tick, null);
            }
            assertTrue(puff.isDestroyed(), "byte_90085 must delete while still onscreen");
            assertTrue(puff.getCentreY() > 0, "not an offscreen deletion");
        }
    }

    private ToxomisterCloudInstance cloud() {
        var cloud = new ToxomisterCloudInstance(160, 100);
        cloud.setServices(services);
        return cloud;
    }

    private ToxomisterPuffInstance puff(ToxomisterCloudInstance cloud, int index, int dx, int dy) {
        var puff = new ToxomisterPuffInstance(index, dx, dy);
        puff.setServices(services);
        puff.attachTo(cloud);
        return puff;
    }
}
