package com.openggf.game.soniccd.media;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.level.CdAssetBytes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/** One finite original PCM file validator. Descriptors are private-package source facts,
 * never a media source or invocation. Runtime bytes enter only through CdProgramAssets.
 * Whole files and headers/templates independently measured through existing RawIso reader
 * on original USA media, 20261109; see private task report. No driver execution. */
final class CdPcmFileSource {
    record Descriptor(CdNativeFile file, String path, int bytes, String sha256, String label,
                      long entry, long secondEntry, String templateSha256) { }
    static final Descriptor R1 = new Descriptor(CdNativeFile.R1PCM,"/SNCBNK1B.BIN;1",217866,
            "fd7ab10c908bd26545afc5bf5da0765e7178fe8fe2929b15957acf9bd7d6e558","SNCBNK25.S28    ",
            0x4EFA0906L,0x4EFA0F90L,"cfc335996cfae29fdf14fd2db7ea853366d350a5d387aa3c322616e81dacd56b");
    static final Descriptor R3 = new Descriptor(CdNativeFile.R3PCM,"/SNCBNK3B.BIN;1",242597,
            "82b9ac37eddbf379155ab76407efc1596e61c7707c592eb47e41cfc468012340","SNCBNK27.S28    ",
            0x4EFA0906L,0x4EFA0F90L,"cfc335996cfae29fdf14fd2db7ea853366d350a5d387aa3c322616e81dacd56b");
    static final Descriptor R4 = new Descriptor(CdNativeFile.R4PCM,"/SNCBNK4B.BIN;1",201562,
            "7746a04d60551aeeae724a2c6d77b48d7ff52bb050707495010b514e10b41296","SNCBNK28.S28    ",
            0x4EFA0906L,0x4EFA0F90L,"cfc335996cfae29fdf14fd2db7ea853366d350a5d387aa3c322616e81dacd56b");
    private CdPcmFileSource() { }
    /** Caller keeps its original release/null/programme rejection order before this finite read. */
    static CdAssetBytes read(CdProgramAssets programs, Descriptor descriptor) throws IOException {
        Objects.requireNonNull(programs);requireDescriptor(descriptor);
        var entry=programs.entry(descriptor.file());
        if(descriptor.file().kind()!=CdNativeFile.Kind.PCM_DRIVER
                || !descriptor.path().equals(entry.path()) || entry.size()!=descriptor.bytes())
            throw new IOException("Original USA "+descriptor.file()+" file path/extent differs");
        return validate(descriptor,programs.read(descriptor.file(),0,descriptor.bytes()));
    }
    /** Internal validation returns inert immutable bytes, never constructs a source owner. */
    static CdAssetBytes validate(Descriptor descriptor, byte[] raw) throws IOException {
        requireDescriptor(descriptor);Objects.requireNonNull(raw);
        if(raw.length!=descriptor.bytes() || !descriptor.sha256().equals(sha(raw)))
            throw new IOException("Original USA "+descriptor.file()+" content differs");
        var data=CdAssetBytes.copyOf(raw);
        if(!Arrays.equals(descriptor.label().getBytes(StandardCharsets.US_ASCII),data.slice(0,16).copy())
                || data.unsignedLong(0x10)!=descriptor.entry() || data.unsignedLong(0x14)!=descriptor.secondEntry()
                || !descriptor.templateSha256().equals(sha(data.slice(0x18,0x900).copy())))
            throw new IOException("Original USA "+descriptor.file()+" header/template differs");
        return data;
    }
    private static void requireDescriptor(Descriptor descriptor) {
        if(descriptor!=R1 && descriptor!=R3 && descriptor!=R4)throw new IllegalArgumentException("Only finite original PCM descriptors");
    }
    private static String sha(byte[] raw) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}
        catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
}
