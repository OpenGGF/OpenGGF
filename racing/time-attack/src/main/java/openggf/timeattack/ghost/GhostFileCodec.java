package openggf.timeattack.ghost;

import com.openggf.net.ghost.GhostFrameCodec;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reader/writer for .ggfghost files: header + 7-byte frame stream (main spec §3/§7). */
public final class GhostFileCodec {
    public static final int FORMAT_VERSION = 1;
    /** Ten minutes at 60fps — the ROM act time-over cap; also bounds hostile-file allocations. */
    public static final int MAX_FRAMES = 36_000;
    public static final int MAX_SPLITS = 255;
    public static final int MAX_TEXT_BYTES = 576;
    public static final int MAX_FILE_BYTES = MAX_FRAMES * GhostFrameCodec.BYTES + MAX_SPLITS * 4 + 3 * MAX_TEXT_BYTES + 64;
    private static final int HASH_LENGTH = 32;
    private static final String MAGIC_TEXT = "GGFGHOST";

    private GhostFileCodec() {
    }

    public static void write(GhostRecording r, Path path) throws IOException {
        if (r.frameCount() > MAX_FRAMES) {
            throw new IOException("recording has " + r.frameCount()
                    + " frames, exceeding MAX_FRAMES " + MAX_FRAMES);
        }
        validateHeader(r.header());
        if (path.getParent()!=null) Files.createDirectories(path.getParent());
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
            out.write(magic());
            GhostHeader h = r.header();
            out.writeShort(h.formatVersion());
            out.writeUTF(h.gameId());
            out.writeByte(h.zone());
            out.writeByte(h.act());
            out.writeUTF(h.character());
            out.writeUTF(h.displayName());
            out.writeInt(h.firstInputFrame());
            out.writeInt(h.finishFrame());
            out.writeByte(h.splitFrames().length);
            for (int split : h.splitFrames()) out.writeInt(split);
            out.writeByte(h.inputRecordingHash().length);
            out.write(h.inputRecordingHash());
            out.writeInt(r.frameCount());
            out.write(r.frameData());
        }
    }

    public static GhostRecording read(Path path) throws IOException {
        if (Files.size(path)>MAX_FILE_BYTES) throw new IOException(path+" exceeds ghost file byte limit");
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            byte[] magic = new byte[MAGIC_TEXT.length()];
            in.readFully(magic);
            if (!java.util.Arrays.equals(magic, magic())) {
                throw new IOException(path + " is not a .ggfghost file");
            }
            int version = in.readUnsignedShort();
            if (version != FORMAT_VERSION) {
                throw new IOException(path + " has unsupported .ggfghost format version " + version);
            }
            String gameId = readText(in);
            int zone = in.readUnsignedByte();
            int act = in.readUnsignedByte();
            String character = readText(in);
            String displayName = readText(in);
            int firstInput = in.readInt();
            int finish = in.readInt();
            int[] splits = new int[in.readUnsignedByte()];
            for (int i = 0; i < splits.length; i++) splits[i] = in.readInt();
            int hashLength = in.readUnsignedByte();
            if (hashLength != HASH_LENGTH) {
                throw new IOException(path + " has invalid input-recording hash length " + hashLength);
            }
            byte[] hash = new byte[HASH_LENGTH];
            in.readFully(hash);
            int frameCount = in.readInt();
            if (frameCount < 1 || frameCount > MAX_FRAMES) {
                throw new IOException(path + " has invalid frame count " + frameCount);
            }
            byte[] frames = new byte[frameCount * GhostFrameCodec.BYTES];
            in.readFully(frames);
            if (in.read()!=-1) throw new IOException(path+" has trailing ghost bytes");
            GhostHeader header=new GhostHeader(version, gameId, zone, act, character,
                    displayName, firstInput, finish, splits, hash);
            validateHeader(header);
            return new GhostRecording(header, frames);
        }
    }

    public static void validateRoute(String gameId,int zone,int act,String character) throws IOException {
        if (gameId==null || !java.util.List.of("s1","s2","s3k").contains(gameId) || zone<0 || zone>255 || act<0 || act>255)
            throw new IOException("Invalid ghost game/zone/act");
        try { com.openggf.game.CharacterKey.parsePersisted(character); }
        catch (IllegalArgumentException | NullPointerException invalid) { throw new IOException("Invalid ghost character",invalid); }
    }

    private static void validateHeader(GhostHeader h) throws IOException {
        if (h==null || h.formatVersion()!=FORMAT_VERSION) throw new IOException("Unsupported ghost format version");
        validateRoute(h.gameId(),h.zone(),h.act(),h.character());
        for(String text:new String[]{h.gameId(),h.character(),h.displayName()}) {
            if (text==null || modifiedUtfLength(text)>MAX_TEXT_BYTES) throw new IOException("Ghost text exceeds byte limit");
        }
        if (h.firstInputFrame()<0 || h.finishFrame()<h.firstInputFrame() || h.finishFrame()>MAX_FRAMES)
            throw new IOException("Invalid ghost first/finish frame bounds");
        int[] splits=h.splitFrames();
        if (splits.length>MAX_SPLITS) throw new IOException("Ghost split count exceeds byte limit");
        int previous=h.firstInputFrame();
        for(int index=0;index<splits.length;index++) {
            int split=splits[index];
            if (split<previous || index>0 && split==previous || split>h.finishFrame())
                throw new IOException("Invalid ghost split frame bounds/order");
            previous=split;
        }
        if (h.inputRecordingHash().length!=HASH_LENGTH) throw new IOException("Invalid ghost input-recording hash length");
    }

    private static int modifiedUtfLength(String text) {
        long bytes=0;
        for(int index=0;index<text.length();index++) {
            int ch=text.charAt(index);bytes+=ch>=1 && ch<=127 ? 1 : ch<=2047 ? 2 : 3;
            if(bytes>MAX_TEXT_BYTES)return MAX_TEXT_BYTES+1;
        }
        return (int)bytes;
    }

    private static String readText(DataInputStream input) throws IOException {
        int length=input.readUnsignedShort();
        if(length>MAX_TEXT_BYTES)throw new IOException("Ghost text exceeds byte limit");
        byte[] encoded=new byte[length+2];encoded[0]=(byte)(length>>>8);encoded[1]=(byte)length;
        input.readFully(encoded,2,length);
        try(var text=new DataInputStream(new java.io.ByteArrayInputStream(encoded))) { return text.readUTF(); }
    }

    private static byte[] magic() {
        return MAGIC_TEXT.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }
}
