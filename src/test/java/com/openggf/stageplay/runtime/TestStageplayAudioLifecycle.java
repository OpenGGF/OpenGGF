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
