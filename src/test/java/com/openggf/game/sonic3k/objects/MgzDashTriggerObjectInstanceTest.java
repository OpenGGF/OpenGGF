package com.openggf.game.sonic3k.objects;

import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.CompactFieldCapturer;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.rewind.schema.RewindObjectStateBlob;
import com.openggf.game.rules.CrossGameRuleComposer;
import com.openggf.game.rules.GameRules;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic3k.Sonic3kLevelTriggerManager;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SlopedSolidProvider;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class MgzDashTriggerObjectInstanceTest {

    private static final int FALLBACK_HOLD_FRAMES = 12;

    @BeforeEach
    void resetLevelTriggers() {
        Sonic3kLevelTriggerManager.reset();
    }

    @Test
    void appendRenderCommandsWhileArmedDrawsMainAndChildSpriteFrames() {
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        MGZDashTriggerObjectInstance trigger = new TestDashTrigger(renderer,
                new ObjectSpawn(0x1200, 0x0340, 0x59, 0, 0, false, 0));
        trigger.setServices(new TestObjectServices());

        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        doReturn(Sonic3kAnimationIds.SPINDASH.id()).when(player).getAnimationId();
        doReturn(true).when(player).getSpindash();
        doReturn((short) 0x1200).when(player).getCentreX();
        doReturn((short) 0x0340).when(player).getCentreY();
        doReturn((short) 9).when(player).getXRadius();
        doReturn((short) 19).when(player).getYRadius();

        trigger.update(1, player);
        trigger.appendRenderCommands(new ArrayList<>());

        ArgumentCaptor<Integer> frameCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(renderer, times(2)).drawFrameIndex(frameCaptor.capture(),
                eq(0x1200), eq(0x0340), eq(false), eq(false));

        assertEquals(2, frameCaptor.getAllValues().size());
        assertTrue(frameCaptor.getAllValues().stream().anyMatch(frame -> frame >= 4),
                "Armed trigger should render its main sprite frame");
        assertTrue(frameCaptor.getAllValues().stream().anyMatch(frame -> frame >= 0 && frame < 4),
                "Armed trigger should also render the child shine frame");
    }

    @Test
    void dashTriggerExposesRomSlopeTableForRidingPlayers() {
        MGZDashTriggerObjectInstance trigger = new MGZDashTriggerObjectInstance(
                new ObjectSpawn(0x0950, 0x0E04, Sonic3kObjectIds.MGZ_DASH_TRIGGER,
                        0x04, 0, false, 0));

        SlopedSolidProvider sloped = assertInstanceOf(SlopedSolidProvider.class, trigger,
                "ROM Obj_MGZDashTrigger calls sub_1DD0E with byte_25F0E "
                        + "(sonic3k.asm:51489-51493,51611-51639)");
        int relX = 0x0948 - trigger.getX() + trigger.getSolidParams().halfWidth();
        int sampleX = relX >> 1;

        assertEquals(0x0F, sloped.getSlopeData()[sampleX],
                "MGZ F1451 rides sample 9 from byte_25F0E, not the flat 0x10 top");
    }

    @Test
    void dashTriggerSlopeFlipFollowsStatusBitZero() {
        MGZDashTriggerObjectInstance trigger = new MGZDashTriggerObjectInstance(
                new ObjectSpawn(0x0950, 0x0E04, Sonic3kObjectIds.MGZ_DASH_TRIGGER,
                        0x04, 1, false, 0));

        SlopedSolidProvider sloped = assertInstanceOf(SlopedSolidProvider.class, trigger);

        assertTrue(sloped.isSlopeFlipped(),
                "SolidObjSloped2 mirrors samples when object status bit 0 is set "
                        + "(sonic3k.asm:41730-41737)");
    }

    @Test
    void dashTriggerUsesFlatTopForNewLandingAndSlopeOnlyForExistingRiders() {
        MGZDashTriggerObjectInstance trigger = new MGZDashTriggerObjectInstance(
                new ObjectSpawn(0x0950, 0x0E04, Sonic3kObjectIds.MGZ_DASH_TRIGGER,
                        0x04, 0, false, 0));

        SlopedSolidProvider sloped = assertInstanceOf(SlopedSolidProvider.class, trigger);

        assertFalse(sloped.usesSlopeForNewLanding(),
                "sub_1DD0E only calls SolidObjSloped2 after the standing bit is already set "
                        + "(sonic3k.asm:41112-41142,41727-41753)");
    }

    @Test
    void nativeSpindashCapabilityNeverSubstitutesSustainedRunForAnimationNine() {
        MGZDashTriggerObjectInstance trigger = triggerAtTestPosition();
        AbstractPlayableSprite player = groundedRightIntentPlayer(GameRules.SONIC_3K);

        for (int frame = 1; frame <= FALLBACK_HOLD_FRAMES + 4; frame++) {
            trigger.update(frame, player);
        }

        assertFalse(Sonic3kLevelTriggerManager.testAny(0),
                "Native S3K capability must retain the ROM animation-9-only arm gate");
    }

    @Test
    void spindashFlagWithoutAnimationNineDoesNotNativeArm() {
        MGZDashTriggerObjectInstance trigger = triggerAtTestPosition();
        AbstractPlayableSprite player = groundedRightIntentPlayer(GameRules.SONIC_3K);
        doReturn(true).when(player).getSpindash();

        trigger.update(1, player);

        assertFalse(Sonic3kLevelTriggerManager.testAny(0),
                "ROM Obj_MGZDashTrigger gates on animation 9, not spin_dash_flag");
    }

    @Test
    void missingSpindashCapabilityRequiresExactSustainedGroundedIntentThreshold() {
        MGZDashTriggerObjectInstance trigger = triggerAtTestPosition();
        AbstractPlayableSprite player = groundedRightIntentPlayer(noSpindashDonorRules());

        for (int frame = 1; frame < FALLBACK_HOLD_FRAMES; frame++) {
            trigger.update(frame, player);
        }
        assertFalse(Sonic3kLevelTriggerManager.testAny(0),
                "Incidental contact shorter than the fallback threshold must not arm");

        trigger.update(FALLBACK_HOLD_FRAMES, player);

        assertTrue(Sonic3kLevelTriggerManager.testAny(0));
    }

    @Test
    void interruptedNoSpindashIntentRestartsTheThreshold() {
        MGZDashTriggerObjectInstance trigger = triggerAtTestPosition();
        AbstractPlayableSprite player = groundedRightIntentPlayer(noSpindashDonorRules());
        for (int frame = 1; frame < FALLBACK_HOLD_FRAMES; frame++) {
            trigger.update(frame, player);
        }
        doReturn(false).when(player).isRightPressed();
        trigger.update(FALLBACK_HOLD_FRAMES, player);
        doReturn(true).when(player).isRightPressed();

        for (int frame = FALLBACK_HOLD_FRAMES + 1;
             frame < FALLBACK_HOLD_FRAMES * 2;
             frame++) {
            trigger.update(frame, player);
        }
        assertFalse(Sonic3kLevelTriggerManager.testAny(0));

        trigger.update(FALLBACK_HOLD_FRAMES * 2, player);
        assertTrue(Sonic3kLevelTriggerManager.testAny(0));
    }

    @Test
    void noSpindashIntentProgressRestoresByStablePlayerIdentity() {
        MGZDashTriggerObjectInstance trigger = triggerAtTestPosition();
        AbstractPlayableSprite capturedPlayer = groundedRightIntentPlayer(noSpindashDonorRules());
        for (int frame = 1; frame <= FALLBACK_HOLD_FRAMES / 2; frame++) {
            trigger.update(frame, capturedPlayer);
        }
        RewindObjectStateBlob blob = CompactFieldCapturer.capture(
                trigger, rewindContext(capturedPlayer));

        AbstractPlayableSprite restoredPlayer = groundedRightIntentPlayer(noSpindashDonorRules());
        CompactFieldCapturer.restore(trigger, blob, rewindContext(restoredPlayer));
        for (int frame = FALLBACK_HOLD_FRAMES / 2 + 1; frame <= FALLBACK_HOLD_FRAMES; frame++) {
            trigger.update(frame, restoredPlayer);
        }

        assertTrue(Sonic3kLevelTriggerManager.testAny(0),
                "Fallback progress must follow PlayerRefId rather than the pre-rewind Java instance");
    }

    @Test
    void threePlayerIntentCountersRestoreToReplacementIdentitiesWithoutTransfer() throws Exception {
        MGZDashTriggerObjectInstance trigger = triggerAtTestPosition();
        AbstractPlayableSprite capturedMain = groundedRightIntentPlayer(noSpindashDonorRules());
        AbstractPlayableSprite capturedSidekick0 = groundedRightIntentPlayer(noSpindashDonorRules());
        AbstractPlayableSprite capturedSidekick1 = groundedRightIntentPlayer(noSpindashDonorRules());
        advanceIntent(trigger, capturedMain, 2);
        advanceIntent(trigger, capturedSidekick0, 5);
        advanceIntent(trigger, capturedSidekick1, 8);
        RewindObjectStateBlob blob = CompactFieldCapturer.capture(
                trigger, rewindContext(capturedMain, capturedSidekick0, capturedSidekick1));

        AbstractPlayableSprite restoredMain = groundedRightIntentPlayer(noSpindashDonorRules());
        AbstractPlayableSprite restoredSidekick0 = groundedRightIntentPlayer(noSpindashDonorRules());
        AbstractPlayableSprite restoredSidekick1 = groundedRightIntentPlayer(noSpindashDonorRules());
        CompactFieldCapturer.restore(trigger, blob,
                rewindContext(restoredMain, restoredSidekick0, restoredSidekick1));

        Map<AbstractPlayableSprite, Integer> restored = intentFrames(trigger);
        assertEquals(3, restored.size());
        assertEquals(2, restored.get(restoredMain));
        assertEquals(5, restored.get(restoredSidekick0));
        assertEquals(8, restored.get(restoredSidekick1));
        assertFalse(restored.containsKey(capturedMain));
        assertFalse(restored.containsKey(capturedSidekick0));
        assertFalse(restored.containsKey(capturedSidekick1));
    }

    private static MGZDashTriggerObjectInstance triggerAtTestPosition() {
        MGZDashTriggerObjectInstance trigger = new MGZDashTriggerObjectInstance(
                new ObjectSpawn(0x1200, 0x0340, Sonic3kObjectIds.MGZ_DASH_TRIGGER,
                        0, 0, false, 0));
        trigger.setServices(new TestObjectServices());
        return trigger;
    }

    private static AbstractPlayableSprite groundedRightIntentPlayer(GameRules rules) {
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        doReturn(rules).when(player).getGameRules();
        doReturn(false).when(player).getAir();
        doReturn(true).when(player).isRightPressed();
        doReturn(false).when(player).isLeftPressed();
        doReturn((short) (0x1200 - 36)).when(player).getCentreX();
        doReturn((short) 0x0340).when(player).getCentreY();
        doReturn((short) 9).when(player).getXRadius();
        doReturn((short) 19).when(player).getYRadius();
        doReturn(Sonic3kAnimationIds.WALK.id()).when(player).getAnimationId();
        doReturn(false).when(player).getSpindash();
        return player;
    }

    private static void advanceIntent(MGZDashTriggerObjectInstance trigger,
                                      AbstractPlayableSprite player,
                                      int frames) {
        for (int frame = 1; frame <= frames; frame++) {
            trigger.update(frame, player);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<AbstractPlayableSprite, Integer> intentFrames(
            MGZDashTriggerObjectInstance trigger) throws ReflectiveOperationException {
        Field field = MGZDashTriggerObjectInstance.class.getDeclaredField("noSpindashIntentFrames");
        field.setAccessible(true);
        return (Map<AbstractPlayableSprite, Integer>) field.get(trigger);
    }

    private static RewindCaptureContext rewindContext(AbstractPlayableSprite player) {
        return rewindContext(player, null, null);
    }

    private static RewindCaptureContext rewindContext(AbstractPlayableSprite main,
                                                       AbstractPlayableSprite firstSidekick,
                                                       AbstractPlayableSprite secondSidekick) {
        RewindIdentityTable table = new RewindIdentityTable();
        table.registerPlayer(main, PlayerRefId.mainPlayer());
        if (firstSidekick != null) {
            table.registerPlayer(firstSidekick, PlayerRefId.sidekick(0));
        }
        if (secondSidekick != null) {
            table.registerPlayer(secondSidekick, PlayerRefId.sidekick(1));
        }
        return RewindCaptureContext.withIdentityTable(table);
    }

    private static GameRules noSpindashDonorRules() {
        return CrossGameRuleComposer.compose(
                GameRules.SONIC_3K,
                GameRules.SONIC_1,
                new Sonic1GameModule().getDonorCapabilities());
    }

    private static final class TestDashTrigger extends MGZDashTriggerObjectInstance {
        private final PatternSpriteRenderer renderer;

        private TestDashTrigger(PatternSpriteRenderer renderer, ObjectSpawn spawn) {
            super(spawn);
            this.renderer = renderer;
        }

        @Override
        protected PatternSpriteRenderer getRenderer(String artKey) {
            return renderer;
        }
    }
}
