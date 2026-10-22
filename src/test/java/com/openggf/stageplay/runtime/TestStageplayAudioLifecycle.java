package com.openggf.stageplay.runtime;

import com.openggf.audio.AudioManager;
import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.StageplayDocument;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class TestStageplayAudioLifecycle {
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
