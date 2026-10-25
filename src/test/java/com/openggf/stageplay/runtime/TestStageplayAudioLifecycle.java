package com.openggf.stageplay.runtime;

import com.openggf.audio.AudioManager;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.audio.rewind.AudioTimelineEntry;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestStageplayAudioLifecycle {
    @Test void laterStopCueAcquiresBeforeEntrySnapshotAndRetainsAdmissionAcrossSeekAndRefresh() throws Exception {
        var manager = spy(AudioManager.class);
        var first = mock(AudioManager.SceneAudioSamples.class);
        var second = mock(AudioManager.SceneAudioSamples.class);
        var entry = mock(AudioLogicalSnapshot.class);
        doReturn(first, second).when(manager).prepareSceneSamples(anyMap());
        doAnswer(call -> {
            assertFalse(com.openggf.audio.LevelMusicAccess.allowsAutomatic(manager),
                    "the whole document owns admission before its first entry snapshot");
            return entry;
        }).when(manager).captureLogicalSnapshot();
        doNothing().when(manager).restoreLogicalSnapshot(any());
        var document = StageplayDocument.empty("s2",0,0,0,0,320,224).withAudio(List.of(
                new AudioCue("later",50,AudioKind.MUSIC_STOP,"s2",-1,"",1)));
        try (var scene = new StageplayAudio(manager,mock(SceneRomLibrary.class),asset -> { throw new AssertionError(); })) {
            scene.prepare(document);
            assertTrue(com.openggf.audio.LevelMusicAccess.allowsAutomatic(manager), "prepare acquires no lease");
            scene.playFrame(document,0);
            verify(manager,never()).stopMusic();
            scene.seek(8);
            assertFalse(com.openggf.audio.LevelMusicAccess.allowsAutomatic(manager));
            scene.refreshAfterNativeBoundary(10);
            verify(first).close();
            assertFalse(com.openggf.audio.LevelMusicAccess.allowsAutomatic(manager), "refresh retires voices, not control");
            scene.restoreEntry(null);
            assertTrue(com.openggf.audio.LevelMusicAccess.allowsAutomatic(manager));
            scene.restoreEntry(entry);
            assertFalse(com.openggf.audio.LevelMusicAccess.allowsAutomatic(manager));
            scene.stop();
            assertTrue(com.openggf.audio.LevelMusicAccess.allowsAutomatic(manager));
        } finally { manager.destroy(); }
    }

    @Test void replacementPreparationPrecedesStoppingOldPlaybackAndFailureRetainsItsLease() throws Exception {
        var manager = spy(AudioManager.class);
        var first = mock(AudioManager.SceneAudioSamples.class);
        var replacement = mock(AudioManager.SceneAudioSamples.class);
        var entry = mock(AudioLogicalSnapshot.class);
        doReturn(first).when(manager).prepareSceneSamples(anyMap());
        doReturn(entry).when(manager).captureLogicalSnapshot();
        doNothing().when(manager).restoreLogicalSnapshot(any());
        var old = StageplayDocument.empty("s2",0,0,0,0,320,224).withAudio(List.of(
                new AudioCue("later",50,AudioKind.MUSIC_STOP,"s2",-1,"",1)));
        var next = StageplayDocument.empty("s2",0,0,0,0,320,224).withDurationFrames(90);
        try (var scene = new StageplayAudio(manager,mock(SceneRomLibrary.class),asset -> { throw new AssertionError(); })) {
            scene.prepare(old); scene.playFrame(old,0);
            var failure = new IllegalStateException("replacement preparation");
            doAnswer(call -> {
                assertFalse(com.openggf.audio.LevelMusicAccess.allowsAutomatic(manager));
                throw failure;
            }).when(manager).prepareSceneSamples(anyMap());
            assertSame(failure, assertThrows(IllegalStateException.class, () -> scene.prepare(next)));
            assertSame(entry, scene.captureEntry());
            assertFalse(com.openggf.audio.LevelMusicAccess.allowsAutomatic(manager));
            verify(manager,never()).restoreLogicalSnapshot(any());
            // prepare(old) calls stop even before playback, so clear only those earlier interactions.
            clearInvocations(first);
            var order = new java.util.ArrayList<String>();
            doAnswer(call -> {
                assertFalse(com.openggf.audio.LevelMusicAccess.allowsAutomatic(manager));
                order.add("prepare"); return replacement;
            }).when(manager).prepareSceneSamples(anyMap());
            doAnswer(call -> {
                assertFalse(com.openggf.audio.LevelMusicAccess.allowsAutomatic(manager));
                order.add("stop-old"); return null;
            }).when(first).stop();
            scene.prepare(next);
            assertEquals(List.of("prepare","stop-old"), order);
            assertTrue(com.openggf.audio.LevelMusicAccess.allowsAutomatic(manager));
            verify(first).close();
        } finally { manager.destroy(); }
    }

    @ParameterizedTest @ValueSource(booleans = {false,true})
    void closeAndAbandonReleaseAdmissionBeforeClosingSamplesEvenWhenStopFails(boolean abandon) throws Exception {
        var manager = spy(AudioManager.class);
        var samples = mock(AudioManager.SceneAudioSamples.class);
        var entry = mock(AudioLogicalSnapshot.class);
        doReturn(samples).when(manager).prepareSceneSamples(anyMap());
        doReturn(entry).when(manager).captureLogicalSnapshot();
        doNothing().when(manager).restoreLogicalSnapshot(any());
        var document = StageplayDocument.empty("s2",0,0,0,0,320,224).withAudio(List.of(
                new AudioCue("later",50,AudioKind.MUSIC_STOP,"s2",-1,"",1)));
        var scene = new StageplayAudio(manager,mock(SceneRomLibrary.class),asset -> { throw new AssertionError(); });
        try {
            scene.prepare(document); scene.playFrame(document,0);
            var failure = new IllegalStateException("sample stop");
            doAnswer(call -> {
                assertFalse(com.openggf.audio.LevelMusicAccess.allowsAutomatic(manager));
                throw failure;
            }).when(samples).stop();
            doAnswer(call -> {
                assertTrue(com.openggf.audio.LevelMusicAccess.allowsAutomatic(manager), "stop finally releases before sample close");
                return null;
            }).when(samples).close();
            assertSame(failure, assertThrows(IllegalStateException.class, () -> {
                if (abandon) scene.abandon(); else scene.close();
            }));
            assertTrue(com.openggf.audio.LevelMusicAccess.allowsAutomatic(manager));
            verify(manager,never()).restoreLogicalSnapshot(any());
            scene.close(); verify(samples,times(1)).close();
        } finally { scene.close(); manager.destroy(); }
    }

    @RequiresRom(SonicGame.SONIC_2)
    @ParameterizedTest @ValueSource(strings = {"NONE", "MUSIC_STOP", "ROM_MUSIC", "PREPARED"})
    void authoredMusicOwnsTheLateNativeLevelEntry(String control) throws Exception {
        EngineServices.current().graphics().initHeadless();
        try (var library = SceneRomLibrary.open(Map.of("s2", Path.of(System.getProperty("sonic2.rom.path"))));
             var world = SessionManager.getCurrentGameplayMode() == null ? StageplayLiveWorld.openOffline(library)
                     : StageplayLiveWorld.adoptOffline(library, SessionManager.getCurrentGameplayMode())) {
            var scene = StageplayDocument.empty("s2", 0, 0, 0, 500, 320, 224).withDurationFrames(100);
            scene = scene.withCamera(List.of(scene.camera().getFirst(), new CameraKey(50, 0, 500, 1, Interpolation.HOLD)))
                    .withSceneChanges(List.of(new SceneChange("cut", 50, scene.set(), Transition.CUT, 0)));
            var kind = control.equals("PREPARED") ? AudioKind.MUSIC_STOP : control.equals("NONE") ? null : AudioKind.valueOf(control);
            if (kind != null) scene = scene.withAudio(List.of(
                    new AudioCue("first", 0, kind, "s2", control.equals("ROM_MUSIC") ? 130 : -1, "", 1),
                    new AudioCue("second", 50, kind, "s2", control.equals("ROM_MUSIC") ? 130 : -1, "", 1)));
            var manager = EngineServices.current().audio();
            try (var audio = new StageplayAudio(manager, library, asset -> { throw new AssertionError(); })) {
                audio.prepare(scene);
                world.resolve(scene, StageplayTimeline.sample(scene, 0));
                int before = manager.commandTimeline().entryCount();
                for (int frame = 0; frame < 100; frame++) {
                    world.resolve(scene, StageplayTimeline.sample(scene, frame));
                    if (frame == 50) audio.refreshAfterNativeBoundary(frame);
                    if (frame == 24) audio.seek(frame - 1);
                    if (!control.equals("PREPARED")) audio.playFrame(scene, frame);
                    HeadlessGameBoot.presentHeadlessOuterAudioFrame();
                }
                var music = manager.commandTimeline().entries().stream().skip(before - manager.commandTimeline().firstRetainedEntryIndex())
                        .map(AudioTimelineEntry::command).filter(command -> command instanceof AudioCommand.PlayMusic)
                        .map(command -> ((AudioCommand.PlayMusic) command).musicId()).toList();
                assertEquals(control.equals("NONE") || control.equals("PREPARED") ? List.of(129, 129)
                                : control.equals("ROM_MUSIC") ? List.of(129, 129) : List.of(), music,
                        "Automatic level music must respect authored control through seek and shot replacement");
                audio.stop();
                SessionManager.getCurrentGameplayMode().getLevelManager().playCurrentLevelMusic();
                HeadlessGameBoot.presentHeadlessOuterAudioFrame();
                assertEquals(129, manager.captureLogicalSnapshot().presentation().activeMusic().musicId(),
                        "Stopping the director must release automatic playlist admission");
                audio.playFrame(scene, 0);
                audio.restoreEntry(null);
                SessionManager.getCurrentGameplayMode().getLevelManager().playCurrentLevelMusic();
                HeadlessGameBoot.presentHeadlessOuterAudioFrame();
                assertEquals(129, manager.captureLogicalSnapshot().presentation().activeMusic().musicId(),
                        "Restoring the pre-playback state must also release playlist admission");
            }
        }
    }

    @RequiresRom(SonicGame.SONIC_2)
    @Test void entryFrameMusicIsNotFollowedByTheNativeEntryFade() throws Exception {
        EngineServices.current().graphics().initHeadless();
        try (var library = SceneRomLibrary.open(Map.of("s2", Path.of(System.getProperty("sonic2.rom.path"))));
             var world = SessionManager.getCurrentGameplayMode() == null ? StageplayLiveWorld.openOffline(library)
                     : StageplayLiveWorld.adoptOffline(library, SessionManager.getCurrentGameplayMode())) {
            var scene = StageplayDocument.empty("s2",0,0,0,500,320,224).withDurationFrames(360)
                    .withAudio(List.of(new AudioCue("entry",0,AudioKind.ROM_MUSIC,"s2",130,"",1)));
            var manager = EngineServices.current().audio();
            world.resolve(scene, StageplayTimeline.sample(scene,0));
            try (var cues = new StageplayAudio(manager,library,path -> { throw new AssertionError(); })) {
                cues.prepare(scene);
                for (int frame=0;frame<360;frame++) {
                    world.resolve(scene,StageplayTimeline.sample(scene,frame));
                    cues.playFrame(scene,frame);
                    HeadlessGameBoot.presentHeadlessOuterAudioFrame();
                }
                var commands = manager.commandTimeline().entries().stream().map(AudioTimelineEntry::command).toList();
                assertTrue(commands.stream().anyMatch(c -> c instanceof AudioCommand.PlayMusic));
                assertFalse(commands.stream().anyMatch(c -> c instanceof AudioCommand.FadeOutMusic),
                        "An authored entry song must survive deferred native entry controls: " + commands);
            }
        }
    }

    @RequiresRom(SonicGame.SONIC_2)
    @Test void nativeEmeraldHillNumberSelectsEmeraldHillRatherThanMetropolis() throws Exception {
        EngineServices.current().graphics().initHeadless();
        try (var library = SceneRomLibrary.open(Map.of("s2", Path.of(System.getProperty("sonic2.rom.path"))));
             var world = SessionManager.getCurrentGameplayMode() == null ? StageplayLiveWorld.openOffline(library)
                     : StageplayLiveWorld.adoptOffline(library, SessionManager.getCurrentGameplayMode())) {
            var scene = StageplayDocument.empty("s2",0,0,0,500,320,224).withAudio(List.of(
                    new AudioCue("native-EHZ",8,AudioKind.ROM_MUSIC,"s2",0x82,"",1)));
            world.resolve(scene,StageplayTimeline.sample(scene,0));
            var manager = EngineServices.current().audio();
            try (var cues = new StageplayAudio(manager,library,path -> { throw new AssertionError(); })) {
                cues.prepare(scene);
                for (int frame=0;frame<40;frame++) {
                    world.resolve(scene,StageplayTimeline.sample(scene,frame)); cues.playFrame(scene,frame);
                    HeadlessGameBoot.presentHeadlessOuterAudioFrame();
                }
                assertEquals(0x81,manager.captureLogicalSnapshot().presentation().activeMusic().musicId(),
                        "ROM EHZ $82 must resolve to the engine's EHZ asset $81");
            }
        }
    }

    @RequiresRom(SonicGame.SONIC_2)
    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "s1,s1,s1,0", "s1,s2,s2,0", "s1,s2,s2,8", "s1,s2,s2,40",
            "s2,s2,s2,0", "s2,s3k,s3k,0", "s3k,s1,s1,0", "s1,s1,s2,0"})
    void authoredSongsSurviveNativeEntriesInOfflinePcm(String opening, String destination,
            String soundtrack, int localCue) throws Exception {
        var services = EngineServices.current(); services.graphics().initHeadless();
        var paths = Map.of("s1",Path.of(System.getProperty("sonic1.rom.path")),
                "s2",Path.of(System.getProperty("sonic2.rom.path")),
                "s3k",Path.of(System.getProperty("s3k.rom.path")));
        var nativeIds = Map.of("s1",129,"s2",130,"s3k",1);
        var engineIds = Map.of("s1",129,"s2",129,"s3k",1);
        try (var library = SceneRomLibrary.open(paths);
             var world = SessionManager.getCurrentGameplayMode() == null ? StageplayLiveWorld.openOffline(library)
                     : StageplayLiveWorld.adoptOffline(library,SessionManager.getCurrentGameplayMode())) {
            var manager = services.audio();
            services.configuration().setSessionOverride(com.openggf.configuration.SonicConfiguration.AUDIO_ENABLED,true);
            manager.setBackend(new com.openggf.audio.HeadlessSmpsAudioBackend(services.configuration(),services.profiler(),manager,manager));
            var scene = StageplayDocument.empty(opening,0,0,0,500,320,224).withDurationFrames(450)
                    .withCamera(List.of(new CameraKey(0,0,500,1,Interpolation.HOLD),new CameraKey(90,0,500,1,Interpolation.HOLD)))
                    .withSceneChanges(List.of(new SceneChange("next",90,new SetSource(destination,0,0,"",""),Transition.CUT,0)))
                    .withAudio(List.of(new AudioCue("opening",0,AudioKind.ROM_MUSIC,opening,nativeIds.get(opening),"",1),
                            new AudioCue("next-song",90+localCue,AudioKind.ROM_MUSIC,soundtrack,nativeIds.get(soundtrack),"",1)));
            world.resolve(scene,StageplayTimeline.sample(scene,0));
            try (var cues = new StageplayAudio(manager,library,path -> {throw new AssertionError();})) {
                cues.prepare(scene); manager.beginCaptureMode(48000,60);
                long tailEnergy = 0;
                for (int frame=0;frame<450;frame++) {
                    if (frame==90) manager.endCaptureMode();
                    world.resolve(scene,StageplayTimeline.sample(scene,frame));
                    if (frame==90) {
                        cues.refreshAfterNativeBoundary(frame); manager.beginCaptureMode(48000,60);
                    }
                    cues.playFrame(scene,frame);
                    if (frame==180) try (var entry = com.openggf.audio.LevelMusicAccess.suppressAutomaticEntry(manager)) {
                        manager.playSfx(com.openggf.audio.GameSound.JUMP);
                    }
                    HeadlessGameBoot.presentHeadlessOuterAudioFrame();
                    var pcm = new short[1600]; assertEquals(800,manager.drainCaptureFrame(pcm));
                    if (frame>=390) for (short value : pcm) tailEnergy += (long)value*value;
                    if (frame>=120+localCue && frame%30==0) {
                        var snapshot = manager.captureLogicalSnapshot().presentation();
                        assertNotNull(snapshot.activeMusic(), "Authored song disappeared at " + frame);
                        assertEquals(engineIds.get(soundtrack),snapshot.activeMusic().musicId());
                        assertEquals(0,snapshot.smpsLogical().fadeOutTimeout(),"Native driver fade at " + frame);
                        assertTrue(snapshot.smpsLogical().sequencers().stream().filter(e -> !e.sfx())
                                .noneMatch(e -> e.snapshot().fade().active() && e.snapshot().fade().fadeOut()),
                                "Native sequencer fade at " + frame);
                    }
                }
                assertTrue(tailEnergy>0,"The final second must contain actual offline music samples");
                assertInstanceOf(com.openggf.audio.HeadlessSmpsAudioBackend.class,manager.getBackend());
                int jump = library.module(destination).getAudioProfile().getSoundMap().get(com.openggf.audio.GameSound.JUMP);
                assertTrue(manager.commandTimeline().entries().stream().map(AudioTimelineEntry::command)
                        .anyMatch(c -> c instanceof AudioCommand.PlaySfx effect && effect.sfxId()==jump),
                        "Music entry ownership must preserve the requested native Jump effect");
            } finally {
                manager.endCaptureMode(); manager.setBackend(new com.openggf.audio.NullAudioBackend());
            }
        }
    }

    @RequiresRom(SonicGame.SONIC_2)
    @ParameterizedTest @ValueSource(strings = {"s2","s3k"})
    void nativeS3kCreditsRequestUsesTheCreditsAssetInBaseAndDonorProducers(String setGame) throws Exception {
        EngineServices.current().graphics().initHeadless();
        try (var library = SceneRomLibrary.open(Map.of("s2",Path.of(System.getProperty("sonic2.rom.path")),
                "s3k",Path.of(System.getProperty("s3k.rom.path"))));
             var world = SessionManager.getCurrentGameplayMode() == null ? StageplayLiveWorld.openOffline(library)
                     : StageplayLiveWorld.adoptOffline(library,SessionManager.getCurrentGameplayMode())) {
            var scene = StageplayDocument.empty(setGame,0,0,0,500,320,224).withAudio(List.of(
                    new AudioCue("credits",8,AudioKind.ROM_MUSIC,"s3k",0xDC,"",1)));
            world.resolve(scene,StageplayTimeline.sample(scene,0));
            var manager = EngineServices.current().audio();
            try (var cues = new StageplayAudio(manager,library,path -> {throw new AssertionError();})) {
                assertDoesNotThrow(() -> cues.prepare(scene));
                for (int frame=0;frame<40;frame++) {
                    world.resolve(scene,StageplayTimeline.sample(scene,frame)); cues.playFrame(scene,frame);
                    HeadlessGameBoot.presentHeadlessOuterAudioFrame();
                }
                assertEquals(0x33,manager.captureLogicalSnapshot().presentation().activeMusic().musicId());
            }
        }
    }

    @Test void closingCurrentSceneRestoresSoundtrackButAbandoningOldVisitDoesNot() throws Exception {
        var document=StageplayDocument.empty("s2",0,0,0,0,320,224);
        for(boolean abandon:new boolean[]{false,true}) {
            var manager=mock(AudioManager.class);when(manager.presentationFrameRate()).thenReturn(60);var samples=mock(AudioManager.SceneAudioSamples.class);
            var entry=mock(AudioLogicalSnapshot.class);
            when(manager.prepareSceneSamples(anyMap())).thenReturn(samples);
            when(manager.captureLogicalSnapshot()).thenReturn(entry);
            var scene=new StageplayAudio(manager,mock(SceneRomLibrary.class),asset->{throw new AssertionError("No assets");});
            scene.prepare(document);scene.playFrame(document,0);
            if(abandon)scene.abandon();else scene.close();
            verify(manager,abandon?never():times(1)).restoreLogicalSnapshot(entry);
            verify(samples).close();scene.close();verify(samples,times(1)).close();
        }
    }
    @Test void nativeShotBoundaryRenewsVoiceScopeWithoutReplayingEarlierCuesOrRestoringOldEntry() throws Exception {
        var manager=mock(AudioManager.class); when(manager.presentationFrameRate()).thenReturn(60);
        var first=mock(AudioManager.SceneAudioSamples.class); var second=mock(AudioManager.SceneAudioSamples.class);
        when(manager.prepareSceneSamples(anyMap())).thenReturn(first,second);
        var entryA=mock(AudioLogicalSnapshot.class); var entryB=mock(AudioLogicalSnapshot.class);
        when(manager.captureLogicalSnapshot()).thenReturn(entryA,entryB);
        var document=StageplayDocument.empty("s2",0,0,0,0,320,224).withAudio(java.util.List.of(
                new StageplayDocument.AudioCue("before",0,StageplayDocument.AudioKind.VOICE,"s2",-1,"voice.wav",1),
                new StageplayDocument.AudioCue("after",120,StageplayDocument.AudioKind.VOICE,"s2",-1,"voice.wav",1)));
        var wav=java.nio.ByteBuffer.allocate(44+1600).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        wav.putInt(0x46464952).putInt(36+1600).putInt(0x45564157).putInt(0x20746D66).putInt(16)
                .putShort((short)1).putShort((short)1).putInt(48000).putInt(96000).putShort((short)2).putShort((short)16)
                .putInt(0x61746164).putInt(1600);
        for(int i=0;i<800;i++) wav.putShort((short)1000);
        try(var scene=new StageplayAudio(manager,mock(SceneRomLibrary.class),asset->wav.array())) {
            scene.prepare(document); scene.playFrame(document,0); verify(first).play("voice.wav",1);
            scene.refreshAfterNativeBoundary(100); verify(first).close();
            verify(manager,never()).restoreLogicalSnapshot(any());
            scene.playFrame(document,100); verify(second,never()).play(anyString(),anyFloat());
            scene.playFrame(document,120); verify(second).play("voice.wav",1);
        }
        verify(manager).restoreLogicalSnapshot(entryB); verify(manager,never()).restoreLogicalSnapshot(entryA);
        verify(second).close();
    }

    @Test void nativePacketsAreForwardOnlyBoundedAndSilencedBySeekAndStop() throws Exception {
        var manager=mock(AudioManager.class);when(manager.presentationFrameRate()).thenReturn(60);
        var authored=mock(AudioManager.SceneAudioSamples.class);
        var a=mock(AudioManager.SceneAudioSamples.class);var b=mock(AudioManager.SceneAudioSamples.class);
        var c=mock(AudioManager.SceneAudioSamples.class);var afterSeek=mock(AudioManager.SceneAudioSamples.class);
        when(manager.prepareSceneSamples(anyMap())).thenReturn(authored,a,b,c,afterSeek);
        var packet=new com.openggf.audio.StreamedMusicPort.SfxPcm(48000,2,new short[]{1200,-1200},1);
        try(var scene=new StageplayAudio(manager,mock(SceneRomLibrary.class),asset->{throw new AssertionError();})) {
            scene.prepare(StageplayDocument.empty("s2",0,0,0,0,320,224));
            scene.playNativeFrame(10,packet);scene.playNativeFrame(10,packet);scene.playNativeFrame(9,packet);
            verify(a,times(1)).play("native",1);verify(manager,times(2)).prepareSceneSamples(anyMap());
            scene.playNativeFrame(11,packet);scene.playNativeFrame(12,packet);verify(a).close();
            scene.seek(4);verify(b).close();verify(c).close();
            scene.playNativeFrame(5,packet);verify(afterSeek).play("native",1);
            scene.stop();verify(afterSeek).close();
        }
        verify(authored).close();verify(a,times(1)).close();verify(afterSeek,times(1)).close();
    }

    @Test void nonSixtyHertzAudioFailsBeforeAssetsOrPlaybackAreAdmitted() {
        var manager=mock(AudioManager.class);when(manager.presentationFrameRate()).thenReturn(50);
        try(var scene=new StageplayAudio(manager,mock(SceneRomLibrary.class),asset->{throw new AssertionError();})) {
            org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class,()->scene.prepare(
                    StageplayDocument.empty("s2",0,0,0,0,320,224)));
            verify(manager,never()).prepareSceneSamples(anyMap());verify(manager,never()).captureLogicalSnapshot();
        }
    }
    @Test void silentSeekRestoresNoSoundtrackAndRewindEntrySurvivesCompletion() throws Exception {
        var manager=mock(AudioManager.class);when(manager.presentationFrameRate()).thenReturn(60);var samples=mock(AudioManager.SceneAudioSamples.class);
        var entry=mock(AudioLogicalSnapshot.class);when(manager.prepareSceneSamples(anyMap())).thenReturn(samples);
        when(manager.captureLogicalSnapshot()).thenReturn(entry);
        var document=StageplayDocument.empty("s2",0,0,0,0,320,224);
        try(var scene=new StageplayAudio(manager,mock(SceneRomLibrary.class),asset->{throw new AssertionError();})) {
            scene.prepare(document);scene.playFrame(document,0);var saved=scene.captureEntry();
            scene.seek(5);verify(manager,never()).restoreLogicalSnapshot(any());
            scene.stop();verify(manager).restoreLogicalSnapshot(entry);
            scene.restoreEntry(saved);scene.seek(0);scene.playFrame(document,1);scene.stop();
            verify(manager,times(2)).restoreLogicalSnapshot(entry);
            verify(manager,times(1)).captureLogicalSnapshot();
        }
    }
}
