package com.openggf.tools.modsdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import com.openggf.io.ModInputLimits;

import static org.junit.jupiter.api.Assertions.*;

class TestAudioConverter {
    @TempDir Path temp;

    @Test
    void validatesDecodedAudioAndLoopThenCopiesCanonicalManifestAndAsset() throws Exception {
        Path root=temp.resolve("source"); Files.createDirectories(root.resolve("audio"));
        Path manifest=root.resolve("custom-input.yaml");
        Files.writeString(manifest, "# author formatting\n"+manifest(0,2)); Files.write(root.resolve("audio/test.wav"),tinyWav());
        Path output=temp.resolve("out");

        AudioConverter.Result result=new AudioConverter().convert("example",
                Path.of("custom-input.yaml"),root,output);

        assertEquals(1,result.trackCount());
        assertArrayEquals(tinyWav(),Files.readAllBytes(output.resolve("audio/test.wav")));
        Path canonical=output.resolve("audio/audio-manifest.yaml");assertTrue(Files.isRegularFile(canonical));
        assertFalse(Files.exists(output.resolve("custom-input.yaml")));
        assertNotEquals(Files.readString(manifest),Files.readString(canonical));
        assertEquals(1,new com.openggf.mods.ModAudioManifestParser("example")
                .parse(Files.readAllBytes(canonical)).tracks().size());
    }

    @Test
    void rejectsMalformedAudioAndOutOfRangeLoopBeforePublishing() throws Exception {
        Path root=temp.resolve("source");Files.createDirectories(root.resolve("audio"));
        Path manifest=root.resolve("audio/audio-manifest.yaml");Path output=temp.resolve("out");
        Files.writeString(manifest,manifest(0,2));Files.write(root.resolve("audio/test.wav"),new byte[]{1});
        assertThrows(Exception.class,()->new AudioConverter().convert("example",manifest,root,output));
        assertFalse(Files.exists(output));
        Files.write(root.resolve("audio/test.wav"),tinyWav());Files.writeString(manifest,manifest(2,8));
        assertTrue(assertThrows(Exception.class,()->new AudioConverter().convert("example",manifest,root,output))
                .getMessage().contains("Loop metadata"));
        assertFalse(Files.exists(output));
    }

    @Test void rejectsNestedSymlinkEscapeAndBoundsOversizedGrowthWithoutPublishing() throws Exception {
        Path root=temp.resolve("secure");Files.createDirectories(root);Path outside=temp.resolve("outside");Files.createDirectories(outside);
        Files.write(outside.resolve("test.wav"),tinyWav());Path link=root.resolve("linked");
        try{Files.createSymbolicLink(link,outside);}catch(Exception unsupported){
            org.junit.jupiter.api.Assumptions.assumeTrue(false,"symlinks unavailable");}
        Path manifest=root.resolve("manifest.yaml");Files.writeString(manifest,manifest(0,2).replace("audio/test.wav","linked/test.wav"));
        Path output=temp.resolve("secure-out");assertThrows(Exception.class,
                ()->new AudioConverter().convert("example",manifest,root,output));assertFalse(Files.exists(output));

        Files.delete(link);Files.createDirectories(root.resolve("audio"));Files.write(root.resolve("audio/test.wav"),tinyWav());
        Files.writeString(manifest,manifest(0,2));
        ModInputLimits low=ModInputLimits.loweringBuilder().maxAssetBytes(32).build();
        assertThrows(Exception.class,()->new AudioConverter(low).convert("example",manifest,root,output));
        assertFalse(Files.exists(output));
    }

    private static String manifest(long start,long end){return """
            formatVersion: 1
            tracks:
              - id: test
                assetPath: audio/test.wav
                loop: true
                loopStartFrame: %d
                loopEndFrame: %d
                gain: 1.0
                tempoEffects: false
            sfx: []
            """.formatted(start,end);}
    private static byte[] tinyWav() throws Exception{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(bytes)){out.writeBytes("RIFF");le32(out,40);out.writeBytes("WAVEfmt ");le32(out,16);le16(out,1);le16(out,1);le32(out,8000);le32(out,16000);le16(out,2);le16(out,16);out.writeBytes("data");le32(out,4);out.write(new byte[4]);}return bytes.toByteArray();}
    private static void le16(DataOutputStream out,int v)throws Exception{out.writeByte(v);out.writeByte(v>>>8);}
    private static void le32(DataOutputStream out,int v)throws Exception{out.writeByte(v);out.writeByte(v>>>8);out.writeByte(v>>>16);out.writeByte(v>>>24);}
}
