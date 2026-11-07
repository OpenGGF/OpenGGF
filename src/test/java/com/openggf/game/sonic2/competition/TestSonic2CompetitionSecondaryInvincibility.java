package com.openggf.game.sonic2.competition;

import com.openggf.game.sonic2.Sonic2SuperStateController;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.*;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.*;

/** Sonic_CheckGoSuper, teleport_swap_table, Touch_Enemy/Touch_ChkHurt and Obj35/Obj38 in one ROM-backed world. */
@Isolated @RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionSecondaryInvincibility {
    @Test void teleportWhileSuperMovesRawProtectionAndStarsWithoutMovingTheGlobalFlag() throws Exception {
        NativeTeleportTestWorld.run(w -> {
            var p1=w.session.player(0);var p2=w.session.player(1);
            var progress=p1.currentGameState();progress.configureSpecialStageProgress(7,7);
            for(int i=0;i<7;i++)progress.markEmeraldCollected(i);
            p1.giveShield();p2.giveShield();p1.giveInvincibility();
            var objects=w.host.context().getObjectManager();
            assertSame(p1.getShieldObject(),NativeSstSlotAccess.currentOwner(objects,0x86));
            assertSame(p2.getShieldObject(),NativeSstSlotAccess.currentOwner(objects,0x87));
            assertSame(p1.getInvincibilityObject(),NativeSstSlotAccess.currentOwner(objects,0x88));
            var repeatedShield=p2.getShieldObject();p2.giveShield();assertSame(repeatedShield,p2.getShieldObject());
            p1.setRingCount(100);p1.setAir(true);p1.setJumping(true);p1.setYSpeed((short)0x28);
            p1.currentLevelState().resumeTimer();
            var superOwner=assertInstanceOf(Sonic2SuperStateController.class,p1.getSuperStateController());
            superOwner.checkTransformationBeforeMove();
            assertTrue(p1.isSuperSonic());assertEquals(0,p1.getInvincibleFrames());
            assertEquals(Boolean.TRUE,PlayableSpriteInternalAccess.nativeSecondaryInvincibility(p1));
            assertEquals(Boolean.FALSE,PlayableSpriteInternalAccess.nativeSecondaryInvincibility(p2));
            // A timer WORD is not a producer for status_secondary.invincible.
            p2.setInvincibleFrames(23);
            w.qualifyTeleport();
            assertEquals(Boolean.FALSE,PlayableSpriteInternalAccess.nativeSecondaryInvincibility(p1));
            assertEquals(Boolean.TRUE,PlayableSpriteInternalAccess.nativeSecondaryInvincibility(p2));
            assertEquals(23,p1.getInvincibleFrames());assertEquals(0,p2.getInvincibleFrames());
            assertTrue(p1.isSuperSonic());assertFalse(p2.isSuperSonic());
            assertFalse(p1.getInvulnerable());assertTrue(p2.getInvulnerable());
            var registry=w.host.context().getRewindRegistry();var snapshot=registry.capture();
            PlayableSpriteInternalAccess.writeNativeSecondaryInvincibility(p1,true);
            PlayableSpriteInternalAccess.writeNativeSecondaryInvincibility(p2,false);
            assertThrows(AssertionError.class,()->assertFalse(p1.getInvulnerable()));
            assertThrows(AssertionError.class,()->assertTrue(p2.getInvulnerable()));
            registry.restore(snapshot);
            assertEquals(Boolean.FALSE,PlayableSpriteInternalAccess.nativeSecondaryInvincibility(p1));
            assertEquals(Boolean.TRUE,PlayableSpriteInternalAccess.nativeSecondaryInvincibility(p2));
            PlayableSpriteInternalAccess.tickNativeDisplayInvincibility(p1);
            PlayableSpriteInternalAccess.tickNativeDisplayInvincibility(p2);
            assertEquals(23,p1.getInvincibleFrames(),"clear bit freezes the exchanged nonzero timer");
            assertEquals(0,p2.getInvincibleFrames(),"zero time never clears the transferred Super bit");
            var stars1=p1.getInvincibilityObject();var stars2=p2.getInvincibilityObject();
            ((ObjectInstance)stars1).update(1,p1);((ObjectInstance)stars2).update(1,p2);
            assertTrue(stars1.isDestroyed());assertFalse(stars2.isDestroyed(),"fixBugs=0 keeps P2's zero-time stars");
            var shield1=p1.getShieldObject();var shield2=p2.getShieldObject();
            ((ObjectInstance)shield1).update(1,p1);((ObjectInstance)shield2).update(1,p2);
            assertTrue(visible(shield1));assertFalse(visible(shield2));
            // Remove transform animation as an independent attack source; the native bit must decide.
            p1.setAnimationId(0);p2.setAnimationId(0);p1.setRolling(false);p2.setRolling(false);
            p1.setSpindash(false);p2.setSpindash(false);p1.setInvulnerableFrames(0);p2.setInvulnerableFrames(0);
            assertFalse(NativeInvincibilityTouchProbe.touch(p1));assertTrue(p1.isHurt());
            assertTrue(NativeInvincibilityTouchProbe.touch(p2));assertFalse(p2.isHurt());
            assertFalse(p2.applyHurt(p2.getCentreX()),"damage rejection reads the raw bit even with timer zero");
            // Sonic_RevertToNormal writes time=1 but does not re-set the exchanged-away bit.
            p1.setInvincibleFrames(1);PlayableSpriteInternalAccess.tickNativeDisplayInvincibility(p1);
            assertEquals(1,p1.getInvincibleFrames());
            PlayableSpriteInternalAccess.writeNativeSecondaryInvincibility(p2,true);
            p2.setInvincibleFrames(1);PlayableSpriteInternalAccess.tickNativeDisplayInvincibility(p2);
            assertEquals(0,p2.getInvincibleFrames());
            assertEquals(Boolean.FALSE,PlayableSpriteInternalAccess.nativeSecondaryInvincibility(p2));
        });
    }
    @Test void aMonitorRewritesTheDeletedStarOwnerAndExpiryAndDeathClearOnlyTheirNativeStatus() throws Exception {
        NativeTeleportTestWorld.run(w -> {
            var player=w.session.player(0);
            w.qualifyTeleport();
            var deleted=player.getInvincibilityObject();
            ((ObjectInstance)deleted).update(1,player);
            assertTrue(deleted.isDestroyed());
            player.giveInvincibility(5);
            assertNotSame(deleted,player.getInvincibilityObject());
            assertFalse(player.getInvincibilityObject().isDestroyed());
            assertEquals(Boolean.TRUE,PlayableSpriteInternalAccess.nativeSecondaryInvincibility(player));
            assertEquals(5,player.getInvincibleFrames());
            player.giveInvincibility(2);
            assertEquals(2,player.getInvincibleFrames(),"native monitor writes its literal WORD rather than maxing time");
            PlayableSpriteInternalAccess.tickNativeDisplayInvincibility(player);
            PlayableSpriteInternalAccess.tickNativeDisplayInvincibility(player);
            assertEquals(Boolean.FALSE,PlayableSpriteInternalAccess.nativeSecondaryInvincibility(player));
            assertEquals(0,player.getInvincibleFrames());
            player.giveInvincibility(7);
            assertTrue(player.applyCrushDeath());
            assertEquals(Boolean.FALSE,PlayableSpriteInternalAccess.nativeSecondaryInvincibility(player));
            assertEquals(7,player.getInvincibleFrames(),"KillCharacter clears the status byte, not the timer WORD");
            var other=w.session.player(1);other.giveInvincibility(19);
            assertSame(other.getInvincibilityObject(),NativeSstSlotAccess.currentOwner(w.host.context().getObjectManager(),0x8C));
            assertTrue(other.applyHurtOrDeath(0,com.openggf.game.DamageCause.TIME_OVER,false),
                    "TimeOver2 calls KillCharacter directly even with raw protection");
            assertEquals(Boolean.FALSE,PlayableSpriteInternalAccess.nativeSecondaryInvincibility(other));
            assertEquals(19,other.getInvincibleFrames());
        });
    }
    @Test void rewindAfterFreezeKeepsTheHiddenShieldWhoseStatusBitIsClear() throws Exception {
        NativeTeleportTestWorld.run(w -> {
            var p1=w.session.player(0);var p2=w.session.player(1);
            var progress=p1.currentGameState();progress.configureSpecialStageProgress(7,7);
            for(int i=0;i<7;i++)progress.markEmeraldCollected(i);
            p1.giveInvincibility(); // No shield monitor was collected by either actor.
            p1.setRingCount(100);p1.setAir(true);p1.setJumping(true);p1.setYSpeed((short)0x28);
            p1.currentLevelState().resumeTimer();
            assertInstanceOf(Sonic2SuperStateController.class,p1.getSuperStateController()).checkTransformationBeforeMove();
            var registry=w.host.context().getRewindRegistry();
            var beforeTeleport=registry.capture();registry.restore(beforeTeleport);
            assertNull(p1.getShieldObject(),"a set invincibility bit must not synthesize an absent shield ID");
            assertNull(NativeSstSlotAccess.currentOwner(w.host.context().getObjectManager(),0x86));
            w.qualifyTeleport();
            for(int tick=0;tick<64;tick++)w.tick();
            assertEquals(0,w.session.teleportTimer());
            assertEquals(Boolean.TRUE,PlayableSpriteInternalAccess.nativeSecondaryInvincibility(p2));
            assertEquals(0,p2.getInvincibleFrames());assertFalse(p2.hasShield());
            var hidden=p2.getShieldObject();assertNotNull(hidden);assertFalse(hidden.isDestroyed());assertFalse(visible(hidden));
            var snapshot=registry.capture();
            PlayableSpriteInternalAccess.writeNativeSecondaryInvincibility(p2,false);
            ((ObjectInstance)hidden).update(100,p2);assertTrue(hidden.isDestroyed());
            registry.restore(snapshot);
            assertCapturedHiddenShield(w,p2);
            // Break the liveness comparison deliberately, then restore the same captured graph again.
            p2.getShieldObject().destroy();
            assertThrows(AssertionError.class,()->assertCapturedHiddenShield(w,p2));
            registry.restore(snapshot);assertCapturedHiddenShield(w,p2);
        });
    }
    private static void assertCapturedHiddenShield(NativeTeleportTestWorld w,AbstractPlayableSprite player) throws Exception {
        assertEquals(0,w.session.teleportTimer());
        assertEquals(Boolean.TRUE,PlayableSpriteInternalAccess.nativeSecondaryInvincibility(player));
        assertFalse(player.hasShield());
        var shield=player.getShieldObject();assertNotNull(shield);assertFalse(shield.isDestroyed());
        assertSame(shield,NativeSstSlotAccess.currentOwner(w.host.context().getObjectManager(),0x87));
        assertTrue(shield.isShieldFor(player,com.openggf.game.ShieldType.BASIC));assertFalse(visible(shield));
        assertNotNull(player.getInvincibilityObject());assertFalse(player.getInvincibilityObject().isDestroyed());
    }
    private static boolean visible(Object shield) throws Exception {
        var field=ShieldObjectInstance.class.getDeclaredField("visible");field.setAccessible(true);return field.getBoolean(shield);
    }
}
