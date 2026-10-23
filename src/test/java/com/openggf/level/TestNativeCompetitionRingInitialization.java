package com.openggf.level;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.competition.Sonic2CompetitionRingStreaming;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.rings.RingManagerInternalAccess;
import com.openggf.level.rings.RingSpawn;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SingletonResetExtension.class)
class TestNativeCompetitionRingInitialization {
    @Test void firstResetAlreadyOwnsNativeCursorsAndTheyRestoreIndependently() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        var roster = mock(NativeCompetitionRoster.class);
        var cameras = new Camera[]{new Camera(), new Camera()};
        when(roster.camera(0)).thenReturn(cameras[0]);
        when(roster.camera(1)).thenReturn(cameras[1]);
        var policy = mock(WorldSessionPolicyState.class);
        when(policy.getService(NativeCompetitionRoster.class)).thenReturn(roster);
        var module = new Sonic2GameModule() {
            @Override public <T> T getGameService(Class<T> type) {
                return type == WorldSessionPolicyProvider.class
                        ? type.cast((WorldSessionPolicyProvider) ignored -> policy) : super.getGameService(type);
            }
        };
        var world = new WorldSession(module);
        var level = mock(LevelManager.class);
        var terrain = mock(Level.class);
        var objects = mock(ObjectManager.class);
        when(level.getWorldSession()).thenReturn(world);
        when(level.getCurrentLevel()).thenReturn(terrain);
        when(level.getObjectManager()).thenReturn(objects);
        when(objects.getObjectServices()).thenReturn(new StubObjectServices() {
            @Override public WorldSession worldSession() { return world; }
        });
        var rings = List.of(new RingSpawn(0x80, 0x100), new RingSpawn(0x600, 0x100));
        when(terrain.getRings()).thenReturn(rings);
        var streaming = spy(new Sonic2CompetitionRingStreaming());
        var manager = LevelManagerInitializationSupport.initializeRings(level, null,
                mock(AudioManager.class), cameras[0], mock(GraphicsManager.class), null, streaming);
        verify(streaming).reset(rings, 0);
        var adapter = RingManagerInternalAccess.nativeCompetitionStreamingRewindAdapter(manager);
        var initial = adapter.capture();
        int firstEnd = streaming.end(0);
        cameras[1].setX((short) 0x600);
        manager.update(0, null, 1, false);
        assertEquals(firstEnd, streaming.end(0));
        assertTrue(streaming.end(1) > firstEnd);
        adapter.restore(initial);
        assertEquals(firstEnd, streaming.end(0));
        assertEquals(firstEnd, streaming.end(1));
        verify(streaming, times(1)).reset(anyList(), anyInt());
    }

    @Test void repeatedInitializationReplacesTheNativeCursorAdapterAlongsideOrdinaryRings() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        var roster = mock(NativeCompetitionRoster.class);
        when(roster.gameState(0)).thenReturn(new com.openggf.game.GameStateManager());
        var cameras = new Camera[]{new Camera(), new Camera()};
        when(roster.camera(0)).thenReturn(cameras[0]);
        when(roster.camera(1)).thenReturn(cameras[1]);
        var policy = mock(WorldSessionPolicyState.class);
        when(policy.getService(NativeCompetitionRoster.class)).thenReturn(roster);
        doReturn(new com.openggf.game.rewind.RewindSnapshottable<Integer>() {
            @Override public String key() { return "test-native-ring-policy"; }
            @Override public Integer capture() { return 0; }
            @Override public void restore(Integer state) { }
        }).when(policy).rewindAdapter();
        var module = new Sonic2GameModule() {
            @Override public <T> T getGameService(Class<T> type) {
                return type == WorldSessionPolicyProvider.class
                        ? type.cast((WorldSessionPolicyProvider) ignored -> policy) : super.getGameService(type);
            }
        };
        var context = com.openggf.game.session.SessionManager.openGameplaySession(module);
        try {
            com.openggf.game.session.GameplaySessionFactory.attachManagers(context,
                    com.openggf.game.session.EngineServices.current());
            var level = mock(LevelManager.class);
            var terrain = mock(Level.class);
            var objects = mock(ObjectManager.class);
            when(level.getWorldSession()).thenReturn(context.getWorldSession());
            when(level.getCurrentLevel()).thenReturn(terrain);
            when(level.getObjectManager()).thenReturn(objects);
            when(objects.getObjectServices()).thenReturn(new StubObjectServices() {
                @Override public WorldSession worldSession() { return context.getWorldSession(); }
            });
            when(terrain.getRings()).thenReturn(List.of(
                    new RingSpawn(0x80, 0x100), new RingSpawn(0x600, 0x100)));
            var oldStream = new Sonic2CompetitionRingStreaming();
            var oldRings = LevelManagerInitializationSupport.initializeRings(level, null,
                    mock(AudioManager.class), cameras[0], mock(GraphicsManager.class), null, oldStream);
            cameras[1].setX((short) 0x600);
            oldRings.update(0, null, 1, false);
            Object retiredState = oldStream.capture();
            var newStream = new Sonic2CompetitionRingStreaming();
            var newRings = LevelManagerInitializationSupport.initializeRings(level, null,
                    mock(AudioManager.class), cameras[0], mock(GraphicsManager.class), null, newStream);
            assertNotSame(oldRings, newRings);
            Object replacementState = newStream.capture();
            assertNotEquals(retiredState, replacementState);
            var adapter = RingManagerInternalAccess.nativeCompetitionStreamingRewindAdapter(newRings);
            var captured = context.getRewindRegistry().capture();
            assertEquals(replacementState, captured.get(adapter.key()));
            newRings.update(0, null, 2, false);
            assertNotEquals(replacementState, newStream.capture());
            context.getRewindRegistry().restore(captured);
            assertEquals(replacementState, newStream.capture());
            assertEquals(retiredState, oldStream.capture(), "restore must not target the retired act's cursors");
        } finally {
            com.openggf.game.session.SessionManager.clear();
        }
    }

    @Test void missingRosterFailsBeforeConstructionOrPresentationMutation() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        var level = mock(LevelManager.class);
        when(level.getWorldSession()).thenReturn(new WorldSession(new Sonic2GameModule()));
        var graphics = mock(GraphicsManager.class);
        var audio = mock(AudioManager.class);
        assertThrows(IllegalStateException.class, () -> LevelManagerInitializationSupport.initializeRings(
                level, null, audio, new Camera(), graphics, null, new Sonic2CompetitionRingStreaming()));
        verify(level, never()).getCurrentLevel();
        verifyNoInteractions(graphics, audio);
        assertThrows(NullPointerException.class,
                () -> LevelArtInternalAccess.initializeNativeCompetitionRings(level, null));
    }
}
