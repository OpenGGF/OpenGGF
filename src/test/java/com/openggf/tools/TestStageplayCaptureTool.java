package com.openggf.tools;

import com.openggf.capture.*;
import com.openggf.stageplay.StageplayDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayCaptureTool {
    @TempDir Path temp;
    private final StageplayDocument scene=StageplayDocument.empty("s2",0,0,0,0,160,112).withDurationFrames(4);
    private CapturedFrame frame(int frame,int samples) {
        return new CapturedFrame(new byte[160*112*4],160,112,new short[samples*2],samples,frame);
    }
    @Test void exactFramesAndFractionalAudioClockAreFinalizedOnce() throws Exception {
        var encoder=new Encoder(); var requested=new ArrayList<Integer>();
        StageplayCaptureTool.renderFrames(scene,temp.resolve("film.mkv"),8000,encoder,index->{
            requested.add(index); return frame(index,(index+1)*8000/60-index*8000/60);
        });
        assertEquals(List.of(0,1,2,3),requested);assertEquals(List.of(0L,1L,2L,3L),encoder.frames);
        assertTrue(encoder.finished); assertFalse(encoder.aborted); assertEquals(533,encoder.samples);
    }
    @Test void sourceFailureAndSampleMismatchAbortInsteadOfPublishingPartialFilm() {
        var encoder=new Encoder();
        assertThrows(IOException.class,()->StageplayCaptureTool.renderFrames(scene,temp.resolve("bad.mkv"),48000,encoder,index->{
            if(index==2)throw new IOException("missing clip");return frame(index,800);
        }));
        assertTrue(encoder.aborted); assertFalse(encoder.finished);
        var drift=new Encoder();
        assertThrows(IOException.class,()->StageplayCaptureTool.renderFrames(scene,temp.resolve("drift.mkv"),48000,drift,index->frame(index,799)));
        assertTrue(drift.aborted);assertFalse(drift.finished);
    }
    @Test void cancellationDoesNotEvaluateAnotherFrame() {
        var encoder=new Encoder(); Thread.currentThread().interrupt();
        try {assertThrows(IOException.class,()->StageplayCaptureTool.renderFrames(scene,temp.resolve("cancel.mkv"),48000,encoder,index->{fail();return null;}));}
        finally {Thread.interrupted();}
        assertFalse(encoder.finished);assertTrue(encoder.aborted);
    }
    @Test void configuredLogicalRomIsDefaultAndAmbiguousArgumentsFail() {
        var args=StageplayCaptureTool.Arguments.parse(new String[]{"--scene","scene.ogfstage","--out","film.mkv","--s3k-rom","locked.gen"});
        assertNull(args.rom());assertNull(args.game());assertEquals(Path.of("locked.gen"),args.suppliedRoms().get("s3k"));
        assertThrows(IllegalArgumentException.class,()->StageplayCaptureTool.Arguments.parse(new String[]{"--scene","s","--out","x.mp4"}));
        assertThrows(IllegalArgumentException.class,()->StageplayCaptureTool.Arguments.parse(new String[]{"--scene","s","--out","x.mkv","--out","y.mkv"}));
    }
    @Test void stagingOwnershipRejectsExistingFilesAndSymlinkDirectories() throws Exception {
        Path owned=java.nio.file.Files.createDirectory(temp.resolve("owned"));
        assertEquals(owned,StageplayCaptureTool.prepareStagingDirectory(temp,owned));
        Path keep=java.nio.file.Files.writeString(owned.resolve("keep.txt"),"user data");
        assertThrows(IOException.class,()->StageplayCaptureTool.prepareStagingDirectory(temp,owned));
        assertEquals("user data",java.nio.file.Files.readString(keep));
        Path link=java.nio.file.Files.createSymbolicLink(temp.resolve("link"),owned);
        assertThrows(IOException.class,()->StageplayCaptureTool.prepareStagingDirectory(temp,link));
    }
    static class Encoder implements CaptureEncoder {
        final List<Long> frames=new ArrayList<>();boolean finished,aborted;long samples;Path path;
        public void open(Path output,int width,int height,int fps,int sampleRate) {path=output;assertEquals(60,fps);}
        public void encode(CapturedFrame frame) {frames.add(frame.frameIndex());samples+=frame.sampleCount();}
        public Path finish() {finished=true;return path;}
        public void abort() {aborted=true;}
    }
}
