package com.openggf.stageplay.route;

import com.openggf.stageplay.StageplayDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayRouteAssets {
    @TempDir Path root;
    private StageplayDocument scene() {
        return StageplayDocument.empty("s2",0,0,0,0,320,224)
                .withDialogueSkins(List.of(new DialogueSkin("skin",-1,-1,-1,-1,6,"panel.png",0,true)))
                .withDialogue(List.of(new DialogueCue("line",0,90,"","Narrator","Hi","skin","",0,0,160,48,"portrait.png",List.of())))
                .withAudio(List.of(new AudioCue("voice",0,AudioKind.VOICE,"s2",0,"voice.wav",1)));
    }
    private String assets() throws Exception {
        Files.write(root.resolve("panel.png"),new byte[]{1,2,3});Files.write(root.resolve("portrait.png"),new byte[]{4,5,6});Files.write(root.resolve("voice.wav"),new byte[]{7,8,9});
        return StageplayRouteAssets.capture(scene(),root);
    }
    @Test void workerOwnsEveryDeclaredImageAndVoiceByteAndEditsChangeTheBinding() throws Exception {
        String before=assets();var reader=StageplayRouteAssets.decode(before,scene());
        assertArrayEquals(new byte[]{1,2,3},reader.read("panel.png"));
        assertArrayEquals(new byte[]{4,5,6},reader.read("portrait.png"));
        assertArrayEquals(new byte[]{7,8,9},reader.read("voice.wav"));
        reader.read("panel.png")[0]=99;assertEquals(1,reader.read("panel.png")[0]);
        assertThrows(IOException.class,()->reader.read("not-declared.png"));
        var request=new StageplayRouteRequest("hero",0,100,200,30);
        String original=StageplayRouteInputs.fingerprint(scene(),request,root,"{}",before);
        Files.write(root.resolve("portrait.png"),new byte[]{4,5,7});
        String imageChanged=StageplayRouteAssets.capture(scene(),root);
        assertNotEquals(original,StageplayRouteInputs.fingerprint(scene(),request,root,"{}",imageChanged));
        Files.write(root.resolve("voice.wav"),new byte[]{7,8,10});
        assertNotEquals(imageChanged,StageplayRouteAssets.capture(scene(),root));
        assertArrayEquals(new byte[]{7,8,9},reader.read("voice.wav"),"The worker sees an immutable submitted snapshot");
    }
    @Test void strictDescriptorRejectsUnknownDuplicateTrailingPathsAndCombinedBudget() throws Exception {
        String valid=assets();
        for(String malformed:List.of(valid+"{}",valid.replaceFirst("\\{","{\"version\":1,"),
                valid.replaceFirst("\\{","{\"unknown\":1,"),valid.replace("panel.png","../panel.png")))
            assertThrows(IOException.class,()->StageplayRouteAssets.decode(malformed,scene()));
        assertThrows(IOException.class,()->StageplayRouteAssets.decode(valid,scene(),8),"Three individually valid3-byte files exceed combined8-byte budget");
        Path oversized=root.resolve("voice.wav");
        try(var channel=java.nio.channels.FileChannel.open(oversized,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)) {
            channel.position(32L*1024*1024);channel.write(java.nio.ByteBuffer.wrap(new byte[]{1}));
        }
        assertThrows(IOException.class,()->StageplayRouteAssets.capture(scene(),root));
    }
    @Test void confinedSnapshotCannotFollowAnAssetSymlinkOutsideTheScene(@TempDir Path outside) throws Exception {
        assets();Path external=Files.write(outside.resolve("outside.png"),new byte[]{1});
        Files.delete(root.resolve("panel.png"));
        try {Files.createSymbolicLink(root.resolve("panel.png"),external);}
        catch(UnsupportedOperationException|FileSystemException unavailable) {org.junit.jupiter.api.Assumptions.assumeTrue(false,"Symlinks unavailable");}
        assertThrows(IOException.class,()->StageplayRouteAssets.capture(scene(),root));
    }
}
