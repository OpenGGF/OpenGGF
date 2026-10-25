package com.openggf.game.sonic3k.competition;

import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
import com.openggf.game.GameModule;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.competition.S3kCompetitionPlayer.Character;
import com.openggf.level.objects.NativeSpriteSubmissionAccess;
import com.openggf.level.objects.NativeSpriteSubmissionSource;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Literal native branch/cadence cases; every animation and mapping byte comes from the locked-on ROM. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kMiniatureTail {
    private RomByteReader reader;
    private S3kMiniatureTail.Bank bank;

    @BeforeEach void loadNativeTailBank() throws Exception {
        reader=RomByteReader.fromRom(RomManager.getInstance().getRom());
        bank=S3kMiniatureTail.Bank.fromReader(reader);
    }

    @Test void nativeSelectionScriptsMappingsAndDplcPointersAreTheVerifiedTailBank() {
        assertArrayEquals(new byte[]{0,0,3,3,0,1,1,1,1,0,1,1,1,0,0,0,0},reader.slice(0x162A0,17));
        assertArrayEquals(new byte[]{0,8,0,11,0,11,0,16},reader.slice(0x162C2,8));
        assertArrayEquals(new byte[]{0x20,0,(byte)0xFF},Arrays.copyOf(bank.scriptBytes(0),3));
        assertArrayEquals(new byte[]{7,1,2,3,(byte)0xFF},Arrays.copyOf(bank.scriptBytes(1),5));
        assertArrayEquals(bank.scriptBytes(1),bank.scriptBytes(2));
        assertArrayEquals(new byte[]{(byte)0xFC,4,5,6,(byte)0xFF},Arrays.copyOf(bank.scriptBytes(3),5));
        assertEquals(0x2A00A4,reader.readU32BE(0x161FC));
        assertEquals(0x2A024C,reader.readU32BE(0x1602A));
        assertEquals(0x33D5E0,reader.readU32BE(0x1603C));
        assertEquals(16,S3kSpriteDataLoader.loadMappingFrames(reader,0x2A00A4,16).size());
        assertEquals(16,S3kSpriteDataLoader.loadDplcFrames(reader,0x2A024C,16).size());
    }

    @Test void eachFixedTailCopiesOnlyTheNativePoseStatusAndArtPriorityFromItsOwnParent() {
        for (int slot=0;slot<2;slot++) {
            var parent=parent(slot); parent.setCentre(0x641,0x230); parent.setFractions(0x1234,0x5678);
            parent.setAngle(0xA1); parent.setStatus(0x61); parent.setHighPriority(true);
            parent.setNativeRenderFlags(0x9F);
            var tail=tail(slot);
            assertFalse(tail.nativeDrawPublished()); assertNotEquals(0,tail.getLiveObjectId());
            tail.refreshNativeVisibility(true);
            assertEquals(0x80,tail.nativeRenderFlags(),"the admitted raw SST pointer owns visibility before its own draw");
            tail.updateNative(parent,bank);
            assertEquals(96+slot,tail.getSlotIndex()); assertEquals(96+slot,tail.nativeFixedSlot());
            assertEquals(slot,tail.parentSlot()); assertEquals(0x641,tail.getX()); assertEquals(0x230,tail.getY());
            assertEquals(0xA1,tail.nativeAngle()); assertEquals(0x41,tail.nativeStatus(),
                    "animation change clears the copied tail Push bit, never the body's bit");
            assertEquals(0x61,parent.status()); assertEquals(5,tail.nativeRenderFlags());
            assertEquals((slot==0?0x690:0x6B0)|0x8000,tail.nativeArtWord());
            assertTrue(tail.isHighPriority()); assertEquals(2,tail.getPriorityBucket());
            assertEquals(0x18,tail.getOnScreenHalfWidth()); assertEquals(0x18,tail.getOnScreenHalfHeight());
            assertTrue(tail.nativeDrawPublished()); assertTrue(tail.isPersistent());
            tail.updateNative(parent,bank); assertEquals(0x61,tail.nativeStatus());
            parent.setHighPriority(false); tail.updateNative(parent,bank);
            assertFalse(tail.isHighPriority()); assertEquals(slot==0?0x690:0x6B0,tail.nativeArtWord());
        }
    }

    @Test void pushingSelectsBlankExceptWaitingAndDoesNotModifyTheParentStatus() {
        var parent=parent(0); var tail=tail(0);
        parent.setAnimation(2); parent.setPushing(true); tail.updateNative(parent,bank);
        assertEquals(0,tail.animation()); assertEquals(0,tail.mappingFrame()); assertTrue(parent.pushing());
        parent.setAnimation(5); tail.updateNative(parent,bank);
        assertEquals(1,tail.animation()); assertEquals(1,tail.mappingFrame()); assertTrue(parent.pushing());
        parent.setPushing(false); parent.setAnimation(0); tail.updateNative(parent,bank);
        assertEquals(0,tail.animation()); assertEquals(0,tail.mappingFrame());
    }

    @Test void swishRunsTheRomSevenTimerForExactlyEightDispatchesPerFrame() {
        var parent=parent(1); var tail=tail(1);
        for (int tick=1;tick<=49;tick++) {
            tail.updateNative(parent,bank);
            assertEquals(1+((tick-1)/8)%3,tail.mappingFrame(),"native tail dispatch "+tick);
            assertEquals(7-(tick-1)%8,tail.animationTimer());
        }
    }

    @Test void rollingTailUsesParentVelocityForAllEightDirectionsAndEitherFacing() {
        int[][] velocities={{0x400,0},{0x400,0x400},{0,0x400},{-0x400,0x400},
                {-0x400,0},{-0x400,-0x400},{0,-0x400},{0x400,-0x400}};
        int[][] frames={{4,13,10,7,4,13,10,7},{4,7,10,13,4,7,10,13}};
        int[][] flips={{0,3,3,3,3,0,0,0},{2,2,2,2,1,1,1,1}};
        for (int facing=0;facing<2;facing++) for (int direction=0;direction<8;direction++) {
            var parent=parent(facing); var tail=tail(facing);
            parent.setAnimation(2); parent.setFacingLeft(facing!=0);
            parent.setXVelocity(velocities[direction][0]); parent.setYVelocity(velocities[direction][1]);
            parent.setAngle(0x55); tail.updateNative(parent,bank);
            assertEquals(3,tail.animation()); assertEquals(frames[facing][direction],tail.mappingFrame());
            assertEquals(4|flips[facing][direction],tail.nativeRenderFlags());
            assertEquals(3,tail.animationTimer());
        }
    }

    @Test void directionalPoseAndFlipStayCarriedUntilTheFourthTimerIntervalExpires() {
        var parent=parent(0); var tail=tail(0);
        parent.setAnimation(2); parent.setXVelocity(0x400); tail.updateNative(parent,bank);
        assertEquals(4,tail.mappingFrame()); assertEquals(4,tail.nativeRenderFlags());
        parent.setXVelocity(0); parent.setYVelocity(0x400);
        for (int tick=0;tick<3;tick++) {
            tail.updateNative(parent,bank); assertEquals(4,tail.mappingFrame()); assertEquals(4,tail.nativeRenderFlags());
        }
        tail.updateNative(parent,bank);
        assertEquals(11,tail.mappingFrame()); assertEquals(7,tail.nativeRenderFlags());
    }

    @Test void bodyInitializationDuplicateViewFlagsAndVisibilitySurviveCapturedForwardReplay() throws Exception {
        var animator=S3kMiniatureAnimation.fromRom(RomManager.getInstance().getRom());
        for (Character character:Character.values()) for (int slot=0;slot<2;slot++) {
            var body=new S3kCompetitionPlayer(slot,character,0x600,0x200);
            assertFalse(body.nativeInitialized());
            assertTrue(body.beginNativeActorPass(character.ordinal()));
            assertEquals(character==Character.TAILS?0x84:4,body.nativeRenderFlags(),
                    "native init overwrites even the first duplicate-character toggle");
            body.setAnimator(animator); body.setObjectControl(1); body.setNativeRenderFlags(0xA7);
            body.tick(S3kCompetitionPlayer.Input.NONE,noTerrain(),bounds(),0);
            assertEquals(0xA4,body.nativeRenderFlags(),"body animation changes only render_flags bits0/1");
            assertFalse(body.beginNativeActorPass(character.ordinal()));
            assertEquals(0xA4|(slot==0?8:0x10),body.nativeRenderFlags());
            var before=body.capture();
            assertFalse(body.beginNativeActorPass((character.ordinal()+1)%3));
            assertEquals(before.nativeRenderFlags(),body.nativeRenderFlags(),"unmatched partner leaves the carried skip bit");
            body.refreshNativeVisibility(false); body.beginNativeActorPass(character.ordinal());
            var forward=body.capture();
            var restored=S3kCompetitionPlayer.recreate(before,animator);
            restored.refreshNativeVisibility(false); assertFalse(restored.beginNativeActorPass(character.ordinal()));
            assertEquals(forward,restored.capture());
        }
        assertTrue(NativeSpriteSubmissionSource.class.isAssignableFrom(S3kMiniatureSprite.class));
    }

    @Test void tailDrawDoesNotInheritBodyBlinkOrDuplicateViewSkipBits() {
        var parent=parent(0); parent.beginNativeActorPass(Character.TAILS.ordinal());
        parent.setInvulnerabilityTimer(120); parent.setObjectControl(3);
        parent.tick(S3kCompetitionPlayer.Input.NONE,noTerrain(),bounds(),0);
        assertFalse(parent.bodyVisible()); parent.setNativeRenderFlags(0x9C);
        var tail=tail(0); tail.updateNative(parent,bank);
        assertTrue(tail.nativeDrawPublished()); assertEquals(4,tail.nativeRenderFlags());
        tail.refreshNativeVisibility(true); assertEquals(0x84,tail.nativeRenderFlags());
        assertEquals(0x9C,parent.nativeRenderFlags());
        tail.refreshNativeVisibility(false); assertEquals(4,tail.nativeRenderFlags());
    }

    @Test void canonicalTailUpdatePublishesItsFixedSlotAndRelinksTheRecreatedSessionParent() {
        var fixture=new CompetitionTestWorld(S3kCompetitionSession.Mode.MATCH_RACE);
        var module=mock(GameModule.class);
        when(module.getIdentifier()).thenReturn("native-tail-test");
        when(module.getGameService(WorldSessionPolicyProvider.class)).thenReturn(ignored->fixture.session);
        var world=new WorldSession(module);
        var services=new StubObjectServices() {
            @Override public WorldSession worldSession() { return world; }
            @Override public RomByteReader romReader() { return reader; }
        }.withIsolatedObjectManager();
        var manager=services.objectManager(); var tail=new S3kMiniatureTail(1);
        manager.addDynamicObjectAtSlot(tail,97);
        var body=fixture.session.player(1); body.setCentre(0x650,0x230); body.setAnimation(2);
        body.setXVelocity(0x400); tail.update(0,null);
        assertEquals(List.of(new NativeSpriteSubmissionAccess.Admission(97,2)),NativeSpriteSubmissionAccess.admissions(manager));
        var adapter=manager.rewindSnapshottable(); var before=adapter.capture();
        var sessionBefore=fixture.session.capture(); var id=manager.objectQuery().identityOf(tail).orElseThrow();
        for (int tick=0;tick<19;tick++) { body.setCentreNative(body.centreX()+3,body.centreY()+1); tail.update(tick,null); }
        var forward=tail.captureRewindState();
        fixture.session.restore(sessionBefore); adapter.restore(before);
        var freshP1=S3kCompetitionPlayer.recreate(sessionBefore.players().get(0).body());
        var freshP2=S3kCompetitionPlayer.recreate(sessionBefore.players().get(1).body());
        var p1=mock(S3kMiniatureSprite.class); when(p1.nativeSlot()).thenReturn(0); when(p1.nativePlayer()).thenReturn(freshP1);
        var p2=mock(S3kMiniatureSprite.class); when(p2.nativeSlot()).thenReturn(1); when(p2.nativePlayer()).thenReturn(freshP2);
        var sprites=mock(SpriteManager.class); when(sprites.getAllSprites()).thenReturn(List.of(p2,p1));
        fixture.session.bindRoster(sprites);
        var restored=(S3kMiniatureTail)manager.objectQuery().resolve(id).orElseThrow();
        assertNotSame(tail,restored); assertNotEquals(0,restored.getLiveObjectId());
        assertNotNull(restored.captureRewindState().compactGenericState());
        body.setCentre(0x777,0x333); // Retired actor must never be the tail's attachment after roster recreation.
        for (int tick=0;tick<19;tick++) { freshP2.setCentreNative(freshP2.centreX()+3,freshP2.centreY()+1); restored.update(tick,null); }
        assertEquals(freshP2.centreX(),restored.getX()); assertNotEquals(body.centreX(),restored.getX());
        assertEquals(forward,restored.captureRewindState());
    }

    private static S3kCompetitionPlayer parent(int slot) {
        return new S3kCompetitionPlayer(slot,Character.TAILS,0x600,0x200);
    }
    private static S3kMiniatureTail tail(int slot) {
        var tail=new S3kMiniatureTail(slot); tail.setSlotIndex(96+slot); return tail;
    }
    private static S3kCompetitionPlayer.Bounds bounds() { return new S3kCompetitionPlayer.Bounds(0x3FF,0x7FF,false,0); }
    private static S3kCompetitionPlayer.Terrain noTerrain() {
        return new S3kCompetitionPlayer.Terrain() {
            @Override public boolean canJump(S3kCompetitionPlayer p,int clearance) { throw new AssertionError("Object-controlled body"); }
            @Override public void resolveGroundWall(S3kCompetitionPlayer p) { throw new AssertionError("Object-controlled body"); }
            @Override public void followGround(S3kCompetitionPlayer p) { throw new AssertionError("Object-controlled body"); }
            @Override public void resolveAir(S3kCompetitionPlayer p) { throw new AssertionError("Object-controlled body"); }
            @Override public void resolveDeath(S3kCompetitionPlayer p) { throw new AssertionError("Object-controlled body"); }
        };
    }
}
