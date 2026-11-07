package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.resources.CdR11aPlcBuffer;
import java.util.*;
import java.io.IOException;

/** Authenticated original USA R31A load/source profile; no service, clear or gameplay authority.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Main.asm:156-165,245-275,599-614;
 * Palmtree Panic/Load Level Data.asm:11-51; Section Art Load.asm:7-46;
 * Collision Chaos/Section Data (Act 1 Present).asm:11-29,37-46.
 * Canonical include, Java product-reader and native eager-refill extents remain distinct.
 */
public final class CdR31aLoadPlan {
    public enum Delivery { IMMEDIATE, QUEUED }
    public enum Branch { LOAD_SECTION, APPEND_STANDARD, NO_PLC_TITLE_BIT_CLEAR }
    public record PlcOperation(Delivery delivery,int nativeId,CdR31aLoadMetadata.PlcList sourceList,
                               List<CdR11aPlcBuffer.Entry> entries) {
        public PlcOperation {
            Objects.requireNonNull(delivery);Objects.requireNonNull(sourceList);entries=List.copyOf(entries);
            if(nativeId!=sourceList.nativeId() || entries.size()!=sourceList.entries().size())
                throw new IllegalArgumentException("R31A original PLC descriptor count differs");
            for(int i=0;i<entries.size();i++) {
                var row=sourceList.entries().get(i);var source=new CdR31aLoadMetadata.Source(row.source());var entry=entries.get(i);
                if(entry.source()!=source.nativeAddress() || entry.destination()!=row.vramAddress())
                    throw new IllegalArgumentException("R31A operation row differs from its literal source list");
            }
        }
    }
    /** Explicit native absence is a reviewed branch, never an empty fallback list. */
    public record LevelDataSelection(Branch branch,Optional<PlcOperation> operation) {
        public LevelDataSelection {
            Objects.requireNonNull(branch);Objects.requireNonNull(operation);
            if((branch==Branch.NO_PLC_TITLE_BIT_CLEAR)==operation.isPresent())
                throw new IllegalArgumentException("R31A native branch/operation differs");
        }
    }
    public record NativeReader(CdR31aLoadMetadata.Source source,int prepareConsumedBytes,int consumedBytes,
                               int byteLength,String sha256) {
        public NativeReader {
            Objects.requireNonNull(source);Objects.requireNonNull(sha256);
            if(prepareConsumedBytes!=source.codeTableBytes()+2 || consumedBytes<prepareConsumedBytes
                    || byteLength!=Math.max(source.includeBytes(),consumedBytes)
                    || consumedBytes>source.includeBytes()+1 || !sha256.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("R31A finite native reader differs");
        }
    }
    private final CdR31aAssets assets;
    private final CdR31aLoadMetadata sources;
    private final List<NativeReader> readers;
    private final List<PlcOperation> start;
    private final CdAssetBytes[] nativeViews=new CdAssetBytes[37];
    private CdR31aLoadPlan(CdR31aAssets assets) {
        this.assets=Objects.requireNonNull(assets,"actual original USA R31A assets");assets.requireAuthenticated();
        sources=assets.loadMetadata();if(!sources.ownsAssets(assets) || sources.sources().size()!=37 || sources.plcs().size()!=19)
            throw new IllegalArgumentException("R31A retained source/PLC owner differs");
        if(assets.levelIndex().levelArt().plcId()!=3 || assets.levelIndex().blocks().plcId()!=2
                || assets.levelIndex().levelArt().nativeAddress()!=0x237BEE || assets.levelIndex().blocks().nativeAddress()!=0x236C76)
            throw new IllegalArgumentException("R31A literal tagged load operands differ");
        // Actual R31A instructions, not equal-looking PPZ/other-release offsets or native address-shaped labels.
        require(0x15E4,"700045f90020fac070001012670461000fca700161000fc4");
        require(0x3580,"45f90020fac02f0a588a221a0281003fffff204149f900ff20006100ed82");
        require(0x35B4,"4a3900ff152267064ef90020bef60839000100ff151c670a7000101267046100ee784e75");
        require(0x1AD6,"203c742000027400143900ff152e08820007ef4a2d40000443f900232f3c47f12000");
        require(0xBEF6,"43fa0050700072003038f700b0596504540160f813c100ff1574303b10464ef90020244c");
        require(0xBF1A,"43fa002c700072003038f700b0596504540160f8b23900ff157466024e7513c100ff1574303b10104ef90020247e");
        var rr=new ArrayList<NativeReader>();
        for(var line:NATIVE_READERS.strip().lines().toList()) {
            var w=line.split(" ");var id=CdR31aLoadMetadata.SourceId.valueOf(w[0]);
            if(id.ordinal()!=rr.size())throw new IllegalArgumentException("R31A native source order differs");
            rr.add(new NativeReader(sources.source(id),Integer.parseInt(w[1]),Integer.parseInt(w[2]),Integer.parseInt(w[3]),w[4]));
        }
        readers=List.copyOf(rr);if(readers.size()!=37)throw new IllegalArgumentException("Complete R31A native source profile required");
        start=List.of(operation(3,Delivery.IMMEDIATE),operation(1,Delivery.IMMEDIATE));
    }
    public static CdR31aLoadPlan fromAssets(CdR31aAssets assets){return new CdR31aLoadPlan(assets);}
    public boolean ownsAssets(CdR31aAssets assets){return this.assets==assets;}
    public CdR31aAssets.Identity identity(){return assets.identity();}
    public CdR31aLoadMetadata sources(){return sources;}
    public List<NativeReader> nativeReaders(){return readers;}
    public NativeReader nativeReader(CdR31aLoadMetadata.SourceId id){return readers.get(Objects.requireNonNull(id).ordinal());}
    public synchronized CdAssetBytes nativeView(CdR31aLoadMetadata.SourceId id) {
        var r=nativeReader(id);int index=id.ordinal();if(nativeViews[index]!=null)return nativeViews[index];
        CdAssetBytes bytes=r.byteLength()==r.source().includeBytes()?sources.canonicalView(id)
                :r.byteLength()==r.source().javaReaderBytes()?sources.javaView(id)
                :CdAssetBytes.copyOf(assets.originalImage().readFile(r.source().fileOffset(),r.byteLength()));
        if(bytes.unsignedWord(0)!=r.source().headerWord() || !r.sha256().equals(CdR11aProfile.sha256(bytes.copy())))
            throw new IllegalArgumentException("R31A retained native reader span differs");
        nativeViews[index]=bytes;return bytes;
    }
    public List<CdR31aLoadMetadata.PlcList> plcs(){return sources.plcs();}
    public List<CdR11bLoadPlan.SectionArt> sections(){return sources.sections();}
    public CdR11bLoadPlan.LevelIndex levelIndex(){return assets.levelIndex();}
    public CdR11bLoadPlan.Bounds bounds(){return assets.bounds();}
    public CdR11bLoadPlan.InitialCentre initialCentre(){return assets.initialCentre();}
    public List<PlcOperation> levelStartOperations(){return start;}
    /** Original TitleCard_SlideOutVert submits queued2 after its routine/scrollLock stores. */
    public PlcOperation titleSlideOutOperation(){return operation(2,Delivery.QUEUED);}
    /** Branch-order-aware DATA selection: no flags read after nonzero spawn, no camera read in standard/absent branches. */
    public LevelDataSelection levelDataSelection(int spawnModeByte,int plcLoadFlagsByte,int cameraXWord) {
        nativeByte(spawnModeByte);
        if(spawnModeByte!=0)return new LevelDataSelection(Branch.LOAD_SECTION,
                Optional.of(operation(sections().get(sectionIndex(cameraXWord)).initialPlcId(),Delivery.QUEUED)));
        nativeByte(plcLoadFlagsByte);
        if((plcLoadFlagsByte&2)==0)return new LevelDataSelection(Branch.NO_PLC_TITLE_BIT_CLEAR,Optional.empty());
        return new LevelDataSelection(Branch.APPEND_STANDARD,Optional.of(operation(levelIndex().blocks().plcId(),Delivery.QUEUED)));
    }
    public int sectionIndex(int cameraXWord) {
        word(cameraXWord);for(int i=0;i<sections().size();i++)if(cameraXWord<sections().get(i).rightThresholdWord())return i;
        throw new UnsupportedOperationException("R31A native next SectionRanges WORD is unowned");
    }
    public CdAssetBytes blocksReader(){return assets.view(CdR31aAssets.Asset.LEVEL_BLOCKS);}
    public long blocksNativeAddress(){return assets.range(CdR31aAssets.Asset.LEVEL_BLOCKS).nativeAddress();}
    public CdAssetBytes lifeIcon(){return assets.view(CdR31aAssets.Asset.ART_LIFE_ICONS);}
    public long lifeIconNativeAddress(){return assets.range(CdR31aAssets.Asset.ART_LIFE_ICONS).nativeAddress();}
    public CdNemesisAsset.Decoded blocksDecode()throws IOException{return assets.blocksDecode();}
    private PlcOperation operation(int id,Delivery delivery) {
        var list=plcs().get(id);var rows=list.entries().stream().map(e->new CdR11aPlcBuffer.Entry(sources.source(e.source()).nativeAddress(),e.vramAddress())).toList();
        return new PlcOperation(delivery,id,list,rows);
    }
    private void require(int at,String hex) {
        if(!HexFormat.of().formatHex(assets.originalImage().readFile(at,hex.length()/2)).equals(hex))
            throw new IllegalArgumentException("R31A selected native load operand differs at "+Integer.toHexString(at));
    }
    private static void nativeByte(int x){if((x&~255)!=0)throw new IllegalArgumentException("Native BYTE required");}
    private static void word(int x){if((x&~65535)!=0)throw new IllegalArgumentException("Native WORD required");}
    private static final String NATIVE_READERS="""
STAGE_GFX 99 10162 10162 f959c37d4a78c6fbe47ebf119e8db80fe2a5121093482559cc61675f5feb7b37
CHECKPOINT_GFX 53 212 212 e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf
GOAL_GFX 88 449 449 1ba1c7e9a34a071c8786cc2eb94eaf2adf0e58b480ef8d62f6501bb7f3006250
TITLE_CARD_GFX 73 940 940 8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8
TITLE_CARD_TEXT_GFX 61 270 270 3d5ca60e7c8f253ed0665fd0af32c7e428ac4afc2c6cb77fda80a2cb30dbc1f8
TENTOU_GFX 92 790 790 68b42bec569d58ea87f93374d02e2bcdde6e25f35286d533412e3ff6fbbc3097
SPIKE_BOMB_GFX 46 194 194 fd5d956e1afc51b7788258dece2b7048585bfac9a87718a5d1bcb6f05dfffc70
FLIPPER_GFX 84 614 614 a986f5b9a93e4cf0297c033a1955f1937ebb8280941e2b6d8ca0e42ca38272b6
BUMPER_GFX 74 533 533 0ef1d0fde20d3a729e08fdcd878d69b1804174aba1bc0b71d799fa2f10cc68a4
SPRING45_GFX 77 780 780 3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a
SPRING_GFX 82 547 547 52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847
HUD_GFX 51 301 301 fb31ef2bca6df6e547b6e578c1455730a6665226befb550fd6c03e2a9a1a88b5
MONITOR_TIME_GFX 107 1541 1541 a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902
EXPLOSION_GFX 91 1257 1257 c37077d4a4bf8aab8c50bd454137dba54a054c5a756b0f3a00515468d81fcdfe
POINTS_GFX 31 101 101 9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176
FLOWER_GFX 77 603 603 cb53ead8bf4ba0a230ce61ea6f67d77ff99b435f411e1a8c4f4c57df1d358069
RING_GFX 48 244 244 530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2
SPIKES_HV2_GFX 63 218 218 a22061edc90f38168d76f08fe3f544862313030d26bc192726ee0849ecebda80
BLOCK_GFX 57 228 228 578176f5fa319d634f48495c38065d2891aeffb3ae203f5229109eb84e13ec23
METAL_PLATFORM_GFX 65 294 294 6c0ed1ff94e4171caace147ecf427d936a940a16077fb2e628d284af0966a8d7
SPIKES_HV4_GFX 71 370 370 06349704636cb711fd4d4b598ef4d960435c47dde8627a293dfd635caeed3941
BOUNCE_PLATFORM_GFX_AB 64 212 212 f09700e63813b634c6d447dae1ce0a1adf0369bedfaad0f58051d011b1b69e2d
ONE_WAY_BARRIER_GFX 42 125 125 7afe047cfa59195eb46cce051fe096f0bbd9eb6700c2e2864ee542914312cd96
FIRE_SHOOT_GFX 65 385 385 07fb31e4617550dd44b728cf705423a478fd5b35eb7817456d2641e54b06a85f
RETRACT_BLOCK_GFX 44 187 187 1a993438d70b8c6bcbe842c0bde53879ee4e0d850fc1ae999d4bb6780cb70bdb
POCKET_GFX_A 52 179 179 16a912563b1b30bee11e12fa4fbd3608e627737be559d0875b7c05b0ffd5b6d0
SPIKE_CHAIN_GFX 63 217 217 618541e54d2d6679a14bf42340e9ad90ed9d4de222586ba721129e359d41e3f0
ANIMALS_GFX 68 432 432 6aa691aed9cef36d041f0773aadfa92befe9d326c50c3202b20deb431c142bdb
KAMA_KAMA_GFX 96 926 926 9860f3101159fb13e2439a5964d1d957eef669e911b46dd52695a6b2433b4437
GA_GFX 92 1281 1281 cf09b74a48b1b800d46e5e12483a8b0f2f16041f0762beb1d331f14a7e3efbd8
ROBOT_TRANSPORT_GFX_A 79 332 332 26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda
AMY_ROSE_GFX 112 1998 1998 9b3ec92dafac2c1b7baa8d21b25403f5736350b08862787dfd9bf599f21724ea
METAL_SONIC_GFX 99 2479 2479 89f9db5a9c5833c64cf47442c56d30b8a909955ce1b557040dfef3ff60e5d0df
RESULTS_GFX 67 1354 1354 c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40
SIGNPOST_GFX 117 1501 1501 c44aa3a5ef0fe775973ebd9f915c6b78b521a2107b4b69af6fa182de1a152c1d
BIG_RING_GFX 61 1034 1034 5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6
BIG_RING_FLASH 38 385 385 e59af60b6dd221ca0b6c993894db9e69567814126686e9f2270bba02e2adf25b
""";
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
