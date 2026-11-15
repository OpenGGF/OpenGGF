package com.openggf.game.soniccd.media;

import com.openggf.data.cd.Iso9660FileSystem;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.release.CdRetailReleases;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Detached original-USA initial-save SOURCE DATA, never a fresh/retained profile or storage operation.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Backup RAM/Initial Data.asm:7-217;
 * Main CPU Functions.asm InitSaveData:223-244; Sub.asm WriteTempSaveData/ReadTempSaveData:521-551.
 * Actual new-profile creation still requires the original NotFound/InitSaveData/storage completion branch.
 * Found historical data, unknown historical progress, transfers, BIOS and live save state are not supplied here.
 */
public final class CdInitialSaveAssets {
    private static final String INIT_SHA="5895de24b19d0a362f9184ff049d4e5e857f32b0e591184027b0802342765fe6";
    private static final String SUB_SHA="a1585624a99082c7a41a4107848763e73f3a0e86a6fd74749c8868dcac114ab9";
    private static final String DATA_SHA="c8e9bcc03252fd3cd3df44cbc2810c6bcc9ac1b1d155f2039b19ff76222abee1";
    private static final CdMmdHeader HEADER=new CdMmdHeader(0,0,0xFF2000,0xBFF,0xFF2000,0,0xFF20AE);

    /** Descriptive source identity only; equal records cannot construct an asset owner or create a profile. */
    public record Identity(CdReleaseVariant variant,CdNativeFile initializationFile,CdNativeFile subCpuFile,
                           String initializationSha256,String subCpuSha256,String observedWholeDiscSha256) {
        public Identity {
            Objects.requireNonNull(variant);Objects.requireNonNull(initializationFile);Objects.requireNonNull(subCpuFile);
            Objects.requireNonNull(initializationSha256);Objects.requireNonNull(subCpuSha256);Objects.requireNonNull(observedWholeDiscSha256);
            if(variant!=CdReleaseVariant.USA||initializationFile!=CdNativeFile.BURAMINIT||subCpuFile!=CdNativeFile.BURAMSUB
                    ||!INIT_SHA.equals(initializationSha256)||!SUB_SHA.equals(subCpuSha256)
                    ||!CdRetailReleases.USA.normalizedSha256().equals(observedWholeDiscSha256))
                throw new IllegalArgumentException("Not the finite original-USA initial-save source pair");
        }
    }
    /** Complete source table spans. Initials/default have no separate native label but retain their original rows. */
    public enum Table {
        TIME_ATTACK_TIMES(0x14D8,336,"1b97fdc3294558df5ecfc4c9adb2d66ba451e429d5a45a449d9145766f74ed68"),
        TIME_ATTACK_INITIALS(0x1628,336,"cbe19222b55065336a6085ddef955df7e3fbcc6732138458e39121efb419395f"),
        DEFAULT_INITIALS(0x1778,4,"4966f3d5a53ffcc1dedc26f1122d537d06d0e18c532f9606af600227b46483a7"),
        MAIN(0x177C,30,"696e976d86b7c24dc5f731046a0090efce405f9696234ebc4752ea7d2c0124e6");
        private final int offset,length;private final String sha;
        Table(int offset,int length,String sha){this.offset=offset;this.length=length;this.sha=sha;}
    }
    /** A finite descriptive range; no reader accepts caller-created ranges or arbitrary native addresses. */
    public record Range(Table table,CdNativeFile file,CdMmdImage.Domain domain,int fileOffset,int length,int nativeAddress,String sha256) {
        public Range {
            Objects.requireNonNull(table);Objects.requireNonNull(file);Objects.requireNonNull(domain);Objects.requireNonNull(sha256);
            if(file!=CdNativeFile.BURAMINIT||domain!=CdMmdImage.Domain.RELOCATED_COPY||fileOffset!=table.offset
                    ||length!=table.length||nativeAddress!=0xFF2000+table.offset-CdMmdHeader.HEADER_BYTES||!table.sha.equals(sha256))
                throw new IllegalArgumentException("Not a finite original initial-save table range");
        }
        public int endExclusive(){return fileOffset+length;}
    }
    /**
     * Exact consuming copy sites, an inventory rather than a sequence submitted by construction.
     * Native integer division/DBF leaves the last two main/temporary bytes untouched; neither is padded.
     * SUB addresses name that programme's source operands, not MMD/Word-RAM execution authority.
     */
    public enum Transfer {
        INITIAL_TIME_ATTACK(CdNativeFile.BURAMINIT,0x17D6,0xFF36D6,0xFF33D8,0x200040,0xA8,676),
        INITIAL_MAIN(CdNativeFile.BURAMINIT,0x17EC,0xFF36EC,0xFF367C,0x2002E4,6,30),
        WRITE_TEMPORARY(CdNativeFile.BURAMSUB,0x398,0x10398,0x80040,0x7400,0xAF,706),
        READ_TEMPORARY(CdNativeFile.BURAMSUB,0x3B8,0x103B8,0x7400,0x80040,0xAF,706);
        private final CdNativeFile file;private final int offset,address,source,destination,counter,declared;
        Transfer(CdNativeFile file,int offset,int address,int source,int destination,int counter,int declared) {
            this.file=file;this.offset=offset;this.address=address;this.source=source;this.destination=destination;this.counter=counter;this.declared=declared;
        }
        public CdNativeFile file(){return file;}public int instructionFileOffset(){return offset;}
        public int instructionNativeAddress(){return address;}public int sourceNativeAddress(){return source;}
        public int destinationNativeAddress(){return destination;}public int counterWord(){return counter;}
        public int declaredDataBytes(){return declared;}public int copiedBytes(){return (counter+1)*4;}
    }
    /** Initial template bytes, NOT current/fresh profile knowledge. Construction has no storage/admission authority. */
    public record MainFields(int zoneByte,int timeAttackUnlockByte,int unknownByte,int goodFuturesByte,int titleFlagsByte,
                             int specialStageByte,int timeStonesByte) {
        public MainFields {
            for(int value:new int[]{zoneByte,timeAttackUnlockByte,unknownByte,goodFuturesByte,titleFlagsByte,specialStageByte,timeStonesByte})
                if((value&~0xFF)!=0)throw new IllegalArgumentException("Initial-save field must retain its raw unsigned byte");
        }
        /** IPX ReadSaveData reads the overlapping first WORD before separate byte fields; no stage inference. */
        public int savedLevelWord(){return (zoneByte<<8)|timeAttackUnlockByte;}
    }

    private final Identity identity;
    private final Iso9660FileSystem.Entry initializationEntry,subCpuEntry;
    private final CdMmdHeader initializationHeader;
    private final int fileBackedInitializationCopyBytes,unresolvedInitializationCopyBytes;
    private final CdAssetBytes initialData,timeAttack;
    private final CdAssetBytes[] views;
    private final List<Range> ranges;
    private final List<Transfer> transfers;
    private final MainFields initialMainFields;

    private CdInitialSaveAssets(Identity identity,CdMmdImage image,Iso9660FileSystem.Entry initializationEntry,
                                Iso9660FileSystem.Entry subCpuEntry) throws IOException {
        this.identity=identity;this.initializationEntry=initializationEntry;this.subCpuEntry=subCpuEntry;
        initializationHeader=image.header();fileBackedInitializationCopyBytes=image.fileBackedCopyBytes();unresolvedInitializationCopyBytes=image.unresolvedCopyBytes();
        initialData=CdAssetBytes.copyOf(image.readFile(0x14D8,706));timeAttack=initialData.slice(0,676);
        if(!DATA_SHA.equals(hash(initialData.copy())))throw new IOException("Original initial-save table aggregate differs");
        views=new CdAssetBytes[Table.values().length];var checked=new ArrayList<Range>();
        for(var table:Table.values()) {
            var range=new Range(table,CdNativeFile.BURAMINIT,CdMmdImage.Domain.RELOCATED_COPY,table.offset,table.length,
                    0xFF2000+table.offset-CdMmdHeader.HEADER_BYTES,table.sha);
            // The actual LEA consumes the relocated, FILE-BACKED image. No unresolved bytes become known.
            var original=image.nativeSlice(range.domain(),range.nativeAddress(),range.length());byte[] bytes=new byte[original.remaining()];original.get(bytes);
            var view=initialData.slice(range.fileOffset()-0x14D8,range.length());
            if(!range.sha256().equals(hash(bytes))||!Arrays.equals(bytes,view.copy()))throw new IOException("Original initial-save table source differs: "+table);
            views[table.ordinal()]=view;checked.add(range);
        }
        ranges=List.copyOf(checked);transfers=List.of(Transfer.values());var main=view(Table.MAIN);
        initialMainFields=new MainFields(main.unsignedByte(0),main.unsignedByte(1),main.unsignedByte(2),main.unsignedByte(3),main.unsignedByte(4),main.unsignedByte(8),main.unsignedByte(9));
    }
    /** Original complete-disc data only. This does NOT execute BURAMINIT, create a profile, or read/write platform saves. */
    public static CdInitialSaveAssets fromPrograms(CdProgramAssets programs) throws IOException {
        Objects.requireNonNull(programs,"actual complete-disc original initial-save source");
        if(programs.header().observedVariant().orElse(null)!=CdReleaseVariant.USA)throw new IOException("Initial-save data requires original USA source headers");
        var observed=programs.identity();var release=CdRetailReleases.find(observed).orElseThrow(()->new IOException("Initial-save data requires a known complete-disc content profile"));
        if(release!=CdRetailReleases.USA)throw new IOException("Initial-save data requires the original USA disc profile");
        var catalogue=programs.catalogue().orElseThrow(()->new IOException("Initial-save source catalogue unavailable"));
        if(catalogue.release()!=CdReleaseVariant.USA||catalogue.programForCommand(CdCommand.BURAMINIT).orElse(null)!=CdNativeProgram.BURAMINIT
                ||!catalogue.nativeLoadFiles(CdCommand.BURAMINIT).equals(List.of(CdNativeFile.BURAMINIT,CdNativeFile.BURAMSUB)))
            throw new IOException("Initial-save command/catalogue source pair differs");
        var initEntry=programs.entry(CdNativeFile.BURAMINIT);var subEntry=programs.entry(CdNativeFile.BURAMSUB);
        requireEntry(initEntry,CdNativeFile.BURAMINIT,10246,10998);requireEntry(subEntry,CdNativeFile.BURAMSUB,10252,2592);
        var image=programs.mmd(CdNativeFile.BURAMINIT);
        if(image.fileBytes()!=10998||!HEADER.equals(image.header())||image.fileBackedCopyBytes()!=10742||image.unresolvedCopyBytes()!=1546
                ||!INIT_SHA.equals(hash(image.readFile(0,image.fileBytes()))))throw new IOException("Original USA BRAMINIT image/header differs");
        byte[] init=image.readFile(0,image.fileBytes());byte[] sub=programs.read(CdNativeFile.BURAMSUB,0,2592);
        if(sub.length!=2592||!SUB_SHA.equals(hash(sub)))throw new IOException("Original USA BRAMSUB image differs");
        verifyOperands(init,sub);
        return new CdInitialSaveAssets(new Identity(CdReleaseVariant.USA,CdNativeFile.BURAMINIT,CdNativeFile.BURAMSUB,INIT_SHA,SUB_SHA,observed.normalizedSha256()),image,initEntry,subEntry);
    }
    public Identity identity(){return identity;}public Iso9660FileSystem.Entry initializationEntry(){return initializationEntry;}
    public Iso9660FileSystem.Entry subCpuEntry(){return subCpuEntry;}public CdMmdHeader initializationHeader(){return initializationHeader;}
    public int fileBackedInitializationCopyBytes(){return fileBackedInitializationCopyBytes;}
    public int unresolvedInitializationCopyBytes(){return unresolvedInitializationCopyBytes;}
    public CdAssetBytes initialData(){return initialData;}public CdAssetBytes timeAttack(){return timeAttack;}
    public List<Range> ranges(){return ranges;}public Range range(Table table){return ranges.get(Objects.requireNonNull(table).ordinal());}
    public CdAssetBytes view(Table table){return views[Objects.requireNonNull(table).ordinal()];}
    public MainFields initialMainFields(){return initialMainFields;}public List<Transfer> transfers(){return transfers;}

    private static void requireEntry(Iso9660FileSystem.Entry entry,CdNativeFile file,long lba,int size) throws IOException {
        if(entry==null||entry.directory()||!file.isoName().equals(entry.name())||entry.version()!=1||entry.extentBlock()!=lba||entry.size()!=size)
            throw new IOException("Original USA initial-save ISO source extent differs: "+file);
    }
    private static void verifyOperands(byte[] init,byte[] sub) throws IOException {
        for(var transfer:Transfer.values()) {
            byte[] bytes=transfer.file()==CdNativeFile.BURAMINIT?init:sub;int at=transfer.instructionFileOffset();boolean main=transfer.file()==CdNativeFile.BURAMINIT;
            requireWord(bytes,at,main?0x41F9:0x207C);requireLong(bytes,at+2,transfer.sourceNativeAddress());
            requireWord(bytes,at+6,main?0x43F9:0x227C);requireLong(bytes,at+8,transfer.destinationNativeAddress());
            requireWord(bytes,at+12,0x303C);requireWord(bytes,at+14,transfer.counterWord());requireWord(bytes,at+16,0x22D8);
            requireWord(bytes,at+18,0x51C8);requireWord(bytes,at+20,0xFFFC);
        }
        // Actual InitSaveData wait and suffix. Values are operands authenticated in original bytes, not stores performed here.
        requireWord(init,0x17D0,0x4EB9);requireLong(init,0x17D2,0xFF2232);
        requireWord(init,0x1802,0x13FC);requireWord(init,0x1804,0);requireLong(init,0x1806,0x200029);
        requireWord(init,0x180A,0x33FC);requireWord(init,0x180C,0xB);requireLong(init,0x180E,0x20002A);requireWord(init,0x1812,0x4E75);
    }
    private static void requireWord(byte[] bytes,int at,int expected) throws IOException {
        if(at<0||at>bytes.length-2||((bytes[at]&255)<<8|(bytes[at+1]&255))!=expected)
            throw new IOException("Initial-save original WORD operand differs@"+Integer.toHexString(at));
    }
    private static void requireLong(byte[] bytes,int at,int expected) throws IOException {
        requireWord(bytes,at,(expected>>>16)&0xFFFF);requireWord(bytes,at+2,expected&0xFFFF);
    }
    private static String hash(byte[] bytes) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("Required SHA-256 unavailable",impossible);}
    }
}
