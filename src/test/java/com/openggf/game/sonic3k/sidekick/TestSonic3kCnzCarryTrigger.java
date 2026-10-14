package com.openggf.game.sonic3k.sidekick;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import org.junit.jupiter.api.Test;
import com.openggf.level.BigRingReturnState;
import com.openggf.level.LevelManager;
import com.openggf.level.LevelTransitionCoordinator;
import com.openggf.game.RespawnState;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic3kCnzCarryTrigger {

    private final Sonic3kCnzCarryTrigger trigger = new Sonic3kCnzCarryTrigger();

    private static final int ZONE_AIZ = 0;
    private static final int ZONE_HCZ = 1;
    private static final int ZONE_MGZ = 2;
    private static final int ZONE_CNZ = 3;
    private static final int ZONE_MHZ = 7;

    @Test
    void cnzAct1SonicAndTailsFires() {
        assertTrue(trigger.shouldEnterCarry(ZONE_CNZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    void cnzAct1SonicAloneFires() {
        // ROM SpawnLevelMainSprites loc_68D8 spawns a throwaway carry-in Tails for
        // solo Sonic (Player_mode==1) at CNZ1; the trigger fires for it too.
        assertTrue(trigger.shouldEnterCarry(ZONE_CNZ, 0, PlayerCharacter.SONIC_ALONE));
    }

    @Test
    void mhzAct1SonicAndTailsFires() {
        // ROM Tails CPU loc_13A8E sends MHZ1 Sonic+Tails into the same carry
        // routine as CNZ1, but with the MHZ pickup coordinates.
        assertTrue(trigger.shouldEnterCarry(ZONE_MHZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    void mhzAct1SonicAloneFires() {
        assertTrue(trigger.shouldEnterCarry(ZONE_MHZ, 0, PlayerCharacter.SONIC_ALONE));
    }

    @Test
    void cnzAct1TailsAloneDoesNotFire() {
        assertFalse(trigger.shouldEnterCarry(ZONE_CNZ, 0, PlayerCharacter.TAILS_ALONE));
    }

    @Test
    void cnzAct1KnucklesDoesNotFire() {
        assertFalse(trigger.shouldEnterCarry(ZONE_CNZ, 0, PlayerCharacter.KNUCKLES));
    }

    @Test
    void cnzAct2DoesNotFire() {
        assertFalse(trigger.shouldEnterCarry(ZONE_CNZ, 1, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    void mhzAct2DoesNotFire() {
        assertFalse(trigger.shouldEnterCarry(ZONE_MHZ, 1, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    void aizAct1SonicAndTailsDoesNotFire() {
        // CRITICAL: protects AIZ from spurious carry-mode triggering
        assertFalse(trigger.shouldEnterCarry(ZONE_AIZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    void hczAct1SonicAndTailsDoesNotFire() {
        assertFalse(trigger.shouldEnterCarry(ZONE_HCZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    void mgzAct1SonicAndTailsDoesNotFire() {
        assertFalse(trigger.shouldEnterCarry(ZONE_MGZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    void savedOriginAtAFormerPickupRectangleCannotRestartTheNativeCarry() {
        var level = mock(LevelManager.class);
        var leader = mock(AbstractPlayableSprite.class);
        when(leader.currentLevelManagerIfAvailable()).thenReturn(level);
        when(leader.getCentreX()).thenReturn((short)448);
        when(leader.getCentreY()).thenReturn((short)1664);
        when(level.getCurrentZone()).thenReturn(ZONE_MHZ);
        when(level.getCurrentAct()).thenReturn(0);
        var transitions = mock(LevelTransitionCoordinator.class);
        when(level.getTransitions()).thenReturn(transitions);
        assertTrue(trigger.isLeaderAtIntroPosition(leader), "fresh inlets remain eligible");
        var saved = new BigRingReturnState(448,1664,192,1568,1,(byte)12,(byte)13,
                2048,0,0,ZONE_MHZ,0,-1,0,0,0,0,0);
        when(level.getBigRingReturn()).thenReturn(saved);
        assertTrue(trigger.isLeaderAtIntroPosition(leader), "unselected Saved2 bank is not a return");
        when(transitions.isLastStarPostHitSet()).thenReturn(true);
        assertFalse(trigger.isLeaderAtIntroPosition(leader), "native nonzero CPU star-post flag bypasses carry");
        when(level.getBigRingReturn()).thenReturn(null);
        assertFalse(trigger.isLeaderAtIntroPosition(leader), "native copied eligibility survives bank consumption");
        when(transitions.isLastStarPostHitSet()).thenReturn(false);
        assertTrue(trigger.isLeaderAtIntroPosition(leader), "next fresh assembly republishes zero");
    }

    @Test
    void aRealCheckpointBypassesCarryAndFreshPlacementStillUsesTheNativeInlet() {
        var level = mock(LevelManager.class);
        var leader = mock(AbstractPlayableSprite.class);
        var checkpoint = mock(RespawnState.class);
        when(leader.currentLevelManagerIfAvailable()).thenReturn(level);
        when(leader.getCentreX()).thenReturn((short)24);
        when(leader.getCentreY()).thenReturn((short)1536);
        when(level.getCheckpointState()).thenReturn(checkpoint);
        when(level.getTransitions()).thenReturn(mock(LevelTransitionCoordinator.class));
        assertTrue(trigger.isLeaderAtIntroPosition(leader));
        when(checkpoint.isActive()).thenReturn(true);
        assertFalse(trigger.isLeaderAtIntroPosition(leader));
        when(checkpoint.isActive()).thenReturn(false);
        assertTrue(trigger.isLeaderAtIntroPosition(leader));
    }

    @Test
    void romConstantsMatch() {
        assertEquals(Sonic3kConstants.CARRY_DESCEND_OFFSET_Y, trigger.carryDescendOffsetY());
        assertEquals(Sonic3kConstants.CARRY_INIT_TAILS_X_VEL, trigger.carryInitXVel());
        assertEquals(Sonic3kConstants.CARRY_INPUT_INJECT_MASK, trigger.carryInputInjectMask());
        assertEquals(Sonic3kConstants.CARRY_COOLDOWN_JUMP_RELEASE,
                trigger.carryJumpReleaseCooldownFrames());
        assertEquals(Sonic3kConstants.CARRY_COOLDOWN_LATCH_RELEASE,
                trigger.carryLatchReleaseCooldownFrames());
        assertEquals(Sonic3kConstants.CARRY_RELEASE_JUMP_Y_VEL, trigger.carryReleaseJumpYVel());
        assertEquals(Sonic3kConstants.CARRY_RELEASE_JUMP_X_VEL, trigger.carryReleaseJumpXVel());
    }
}
