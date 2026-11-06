package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import java.io.IOException;
import java.util.*;

/**
 * Finite original-USA C/D PLC sources and immutable data products, not service or current-art authority.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Graphics Lists (Act3), Data (Act3) and native source blobs;
 * Functions (General).asm:401-579,664-766. Whole MMD provenance remains with the actual aggregate.
 * Native reader extents are independent research evidence. Production product decode always delegates to
 * existing CdNemesisAsset with its bounded Java input; no native/hardware/alternate decoder lives here.
 * Canonical includes and reader lookahead are distinct. Any extra byte is retained real neighboring MMD data.
 */
public final class CdR13SectionSources {
    public enum ReaderKind { CANONICAL, JAVA, NATIVE }
    private record Extent(int offset,int blobBytes,int header,int prepareCursor,int javaCursor,int nativeCursor,
                          String blobSha,String javaSha,String nativeSha,String decodedSha) {
        private Extent {
            int maximum=Math.max(blobBytes,Math.max(javaCursor,nativeCursor));
            if(offset<0 || (offset&1)!=0 || blobBytes<3 || offset>0x40000-maximum
                    || (header&~0xFFFF)!=0 || (header&0x7FFF)==0 || (header&0x7FFF)>2048
                    || prepareCursor<4 || prepareCursor>javaCursor || prepareCursor>nativeCursor
                    || javaCursor>blobBytes+1 || nativeCursor>blobBytes+1) {
                throw new IllegalArgumentException("Invalid fixed R13 source/reader bounds");
            }
            for(var hash:List.of(blobSha,javaSha,nativeSha,decodedSha))if(!hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Expected source SHA256");
            if((javaCursor<=blobBytes && !blobSha.equals(javaSha)) || (nativeCursor<=blobBytes && !blobSha.equals(nativeSha))
                    || (Math.max(blobBytes,javaCursor)==Math.max(blobBytes,nativeCursor) && !javaSha.equals(nativeSha)))throw new IllegalArgumentException("R13 finite reader hashes differ");
        }
    }
    /** Exact first directory/body source encounter order. Values alone grant no source owner. */
    public enum SourceId {
        ART_LEVEL_TILES("StageGfx",
                new Extent(0x39746,11158,0x8314,97,11158,11159,"6183109387a85f9885fde847a95e543af57f3726913d6e4a7e2651a5ab7103cb","6183109387a85f9885fde847a95e543af57f3726913d6e4a7e2651a5ab7103cb","3d8c9e2ae434f5a2af49a67abfb44aa24d985868f3ce562ded7421919b4bfd4d","963d8b39d5941ead09033c6b5cb773a56bb848f02f11540b228a221a8db23438"),
                new Extent(0x39734,11686,0x832E,105,11685,11686,"08c497311f7960a47b7d18f98123f5e74f353be899847bf3d53af9665a52a24e","08c497311f7960a47b7d18f98123f5e74f353be899847bf3d53af9665a52a24e","08c497311f7960a47b7d18f98123f5e74f353be899847bf3d53af9665a52a24e","11cd4655075a61e0a55b553f15e9f9717ee6f08bb6644836b5a5cef1206a9caf"),"Art_LevelTiles","StageGfx"),
        ART_TITLE_CARD("Art_TitleCard",new Extent(0x30DA2,940,0x806D,73,939,940,"8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8","8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8","8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8","68b70b1e8189073c24642b8939420ac4d3c3ba1d6a1c9c00d6538390d1764f7a"),"TitleCardGfx","Art_TitleCard"),
        ART_TITLE_CARD_TEXT("Art_TitleCardText",new Extent(0x339F6,298,0x8034,49,298,298,"39ec1617bda91d34562b315729f003e09c907ec95da5bffb31333b2c4dead3e5","39ec1617bda91d34562b315729f003e09c907ec95da5bffb31333b2c4dead3e5","39ec1617bda91d34562b315729f003e09c907ec95da5bffb31333b2c4dead3e5","66b74c71966af3b2bdf42828b3a0b5e5fcf73f05c8cae89fa2ff3f28eb01ee70"),"TitleCardTextGfx","Art_TitleCardText"),
        ART_BOULDER("Art_Boulder",new Extent(0x33CE4,270,0x8013,51,270,271,"6335b858cc043dcce7de3f716a506c5d393342298729c04739673331f0e4e55f","6335b858cc043dcce7de3f716a506c5d393342298729c04739673331f0e4e55f","bf9f0cf2200b515b5f84c8daf70b4d78dcbcd92d432e2f305aebdc7aa0fb2f48","abdbe20715a2c4784fae851622bb276dc1630abc339efb1e13b99a5ddc4d0c7d"),"BoulderGfx","Art_Boulder"),
        ART_SPRINGS("Art_Springs",new Extent(0x3213A,546,0x8024,82,547,547,"19212b9e7b64237a58a6bfa64566338930fd82a71555dbfab61e9bc45e165831","52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847","52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847","8e3c6d91a395d24872b4ae5da293cd21d8e68230687ef9f07feb62ec2612f416"),"SpringGfx","Art_Springs"),
        ART_HUD("Art_HUD",new Extent(0x334BC,300,0x801B,51,300,301,"d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb","d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb","fb31ef2bca6df6e547b6e578c1455730a6665226befb550fd6c03e2a9a1a88b5","0a448ef2c11a1414886684523d551d65af43e4f0c02e08c73e869437b15b40fc"),"HudGfx","Art_HUD"),
        ART_MONITOR_TIME_POSTS("Art_MonitorTimePosts",new Extent(0x3235C,1540,0x8052,107,1541,1541,"ee98713d147c62128c7af02e250a6bf733c49c2c86a90332ecc4d77be29ed05a","a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902","a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902","7575dcf197dbc2f246838cfedcb95af40100d449f0a0c5a48b7294c27c9a924f"),"MonitorTimeGfx","Art_MonitorTimePosts"),
        ART_EXPLOSIONS("Art_Explosions",new Extent(0x32960,1256,0x46,91,1256,1257,"1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1","1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1","c37077d4a4bf8aab8c50bd454137dba54a054c5a756b0f3a00515468d81fcdfe","434f2415b56d7623e57fd94533506804d7ee1fe3a0321f77bfe86f58d8ff6664"),"ExplosionGfx","Art_Explosions"),
        ART_POINTS("Art_Points",new Extent(0x2EDE6,100,0x5,31,101,101,"6923bd2ac955ac11b88bb75b036d2fbd24f02fe523cd401e30bb1520426ad363","9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176","9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176","2270d6453b2d2526412afab1ba19c3cf175b571a41766dc4fe0da422283d57db"),"PointsGfx","Art_Points"),
        ART_FLOWER("Art_Flower",new Extent(0x33770,646,0x8027,79,645,646,"baae7501cf372a57007db352ffcfe404cc8d2066a9cc639e64d1f2796cfe9af1","baae7501cf372a57007db352ffcfe404cc8d2066a9cc639e64d1f2796cfe9af1","baae7501cf372a57007db352ffcfe404cc8d2066a9cc639e64d1f2796cfe9af1","db06551b90412becaec9aa45ccfd14dca62c94e1f165718629553f8fdaac6793"),"FlowerGfx","Art_Flower"),
        ART_RINGS("Art_Rings",new Extent(0x32E48,244,0xE,48,244,244,"530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2","530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2","530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2","c1f35c87c98d27990926ad515264ab1e8a5c9385b087931d79ff24709afd6abb"),"RingGfx","Art_Rings"),
        ART_TUNNEL_DOOR("DoorGfx",new Extent(0x34320,154,0x800C,49,153,154,"cbbd577ac9af46007b2618fd0481753435aae38e0f68fe0ae7ed17df74b0cb7b","cbbd577ac9af46007b2618fd0481753435aae38e0f68fe0ae7ed17df74b0cb7b","cbbd577ac9af46007b2618fd0481753435aae38e0f68fe0ae7ed17df74b0cb7b","dc7be7cdc86c642af5aafd7462269397a6d901678241fde33a35b9209eaf22a0"),"DoorGfx","Art_TunnelDoor"),
        ART_WATERFALL("WaterfallGfx",new Extent(0x3417C,420,0x8020,47,420,421,"9f0fee55bedc64dbcd4c505906db1d120d292f9308dce4027e9bcd6f5703c340","9f0fee55bedc64dbcd4c505906db1d120d292f9308dce4027e9bcd6f5703c340","a1dff8aa5d05e61738cc3e6b2d1b5ca23baacdfced9480717bfe52186f3f9128","904e8950e10d01607b0a8c7bee6c3bfb0957827fac8e451b4025f85b920721c3"),"WaterfallGfx","Art_Waterfall"),
        ART_ANIMALS("AnimalsGfx",new Extent(0x359E2,446,0x14,89,445,446,"63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab","63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab","63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab","ac1edab54e2b767578c6b3c2b828dc9821ef02c7a3bed9079dc7dded4a26e8d1"),"AnimalsGfx","Art_Animals"),
        ART_TAMABBOH("TamabbohGfx",new Extent(0x35172,1142,0x803B,91,1142,1143,"39b42a53ad2a2b1e50c73d2f0abdb918304fd8987499af1a4d8773b9239c309f","39b42a53ad2a2b1e50c73d2f0abdb918304fd8987499af1a4d8773b9239c309f","c90255c16ce2a453a6dad66e1b9ff641e12b2fc87e5b639429e1ef1962978b0e","aa544f679799481cc55fe2f329ef30be56ee2c8f8a655e5509f02d4035cbed95"),"TamabbohGfx","Art_Tamabboh"),
        ART_FLOWER_CAPSULE("Art_FlowerCapsule",new Extent(0x2EE4A,1704,0x8068,101,1703,1704,"da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808","da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808","da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808","b61c6d944dce535ccb74d0a374ac873e95a5e0b3911b465f62bb025b03660236"),"CapsuleGfx","Art_FlowerCapsule"),
        ART_BOSS_MACHINE("BossMachineGfx",new Extent(0x1D400,2822,0x80A4,97,2821,2822,"3e68f355f2cea70a3f79c657ce661623641afb2d5b025d7ad1003ee52e10bc8b","3e68f355f2cea70a3f79c657ce661623641afb2d5b025d7ad1003ee52e10bc8b","3e68f355f2cea70a3f79c657ce661623641afb2d5b025d7ad1003ee52e10bc8b","9862e164be793c18e701abd1037d9b2619b9f0c9d34fbf6000ab9b515c9b582a"),"BossMachineGfx"),
        ART_EGGMAN("EggmanGfx",new Extent(0x1DF06,1902,0x8070,103,1902,1903,"36015b93b0a37feafb2a35729cefda33e2d8910ec8963e37220782de71f8d3bf","36015b93b0a37feafb2a35729cefda33e2d8910ec8963e37220782de71f8d3bf","c6debab0076bb136feb19ac084801080b2e66a6d2f0fcc97841c3d0138006df5","8e15af728aa36095a2aaaf85302299798ee2e57a43538736f334c29e165efa02"),"EggmanGfx"),
        ART_RESULTS("Art_Results",new Extent(0x30098,1354,0x8078,67,1354,1354,"c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40","c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40","c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40","fdb13e161a5862fe4f8c550291bba36589028b8a7cc0394638c019a0ab35760d"),"ResultsGfx","Art_Results");
        private final String label;
        private final Extent good,bad;
        private final List<String> labels;
        SourceId(String label,Extent both,String... labels){this(label,both,both,labels);}
        SourceId(String label,Extent good,Extent bad,String... labels){
            this.label=Objects.requireNonNull(label);this.good=Objects.requireNonNull(good);this.bad=Objects.requireNonNull(bad);this.labels=List.of(labels);
            if(!this.labels.contains(label) || new HashSet<>(this.labels).size()!=this.labels.size())throw new IllegalArgumentException("R13 source label aliases differ");
        }
        private Extent extent(CdNativeFile file){if(file==CdNativeFile.R13C)return good;if(file==CdNativeFile.R13D)return bad;throw new IllegalArgumentException("Only original R13C/D source metadata");}
    }
    /** Descriptive source/format metadata only; this record cannot construct a registry or gameplay capability. */
    public record Source(SourceId id,CdNativeFile file) {
        public Source {Objects.requireNonNull(id);Objects.requireNonNull(file);id.extent(file);}
        public String nativeLabel(){return id.label;}
        public List<String> nativeLabels(){return id.labels;}
        public CdR13FutureAssets.FileRange blobRange(){return range(ReaderKind.CANONICAL);}
        public CdR13FutureAssets.FileRange javaReaderRange(){return range(ReaderKind.JAVA);}
        public CdR13FutureAssets.FileRange nativeReaderRange(){return range(ReaderKind.NATIVE);}
        private CdR13FutureAssets.FileRange range(ReaderKind kind){var e=id.extent(file);int length=switch(Objects.requireNonNull(kind)){case CANONICAL->e.blobBytes;case JAVA->Math.max(e.blobBytes,e.javaCursor);case NATIVE->Math.max(e.blobBytes,e.nativeCursor);};return new CdR13FutureAssets.FileRange(file,CdMmdImage.Domain.ORIGINAL_WORD_RAM,e.offset,length);}
        public long nativeAddress(){return Integer.toUnsignedLong(blobRange().nativeAddress());}
        public int headerWord(){return id.extent(file).header;}
        public int tileCount(){return headerWord()&0x7FFF;}
        public int outputBytes(){return tileCount()*32;}
        public int expectedPrepareConsumedBytes(){return id.extent(file).prepareCursor;}
        public int expectedJavaConsumedBytes(){return id.extent(file).javaCursor;}
        public int expectedNativeConsumedBytes(){return id.extent(file).nativeCursor;}
        public String sha256(ReaderKind kind){var e=id.extent(file);return switch(Objects.requireNonNull(kind)){case CANONICAL->e.blobSha;case JAVA->e.javaSha;case NATIVE->e.nativeSha;};}
        public String decodedSha256(){return id.extent(file).decodedSha;}
    }
    private final CdR13FutureAssets assets;
    private final CdR13FutureAssets.Identity identity;
    private final CdMmdImage image;
    private final List<Source> sources;
    private final Map<Long,Source> byAddress;
    private final Map<String,Source> byLabel;
    // Cached immutable values only: no queue, continuation, budget, residency or runtime lifecycle state.
    private final CdAssetBytes[][] views=new CdAssetBytes[19][ReaderKind.values().length];
    private final CdNemesisAsset.Decoded[] products=new CdNemesisAsset.Decoded[19];

    private CdR13SectionSources(CdR13FutureAssets assets){
        this.assets=Objects.requireNonNull(assets,"actual original R13 assets");assets.requireAuthenticatedLayout();
        identity=Objects.requireNonNull(assets.identity());image=Objects.requireNonNull(assets.originalImage());
        boolean good=identity.file()==CdNativeFile.R13C && identity.stage().equals(CdStagePayload.R13C.stage());
        boolean bad=identity.file()==CdNativeFile.R13D && identity.stage().equals(CdStagePayload.R13D.stage());
        String expected=good?"3bc8965dc5668a9d1223392f5f7762df41b930b797ebc76aaa5fcf334bc16b4f":"27e6c42268ea411d184b2b91b79ed626b39871b2823a5362573216ce29fd9356";
        if(identity.variant()!=CdReleaseVariant.USA || (!good&&!bad) || identity.domain()!=CdMmdImage.Domain.ORIGINAL_WORD_RAM
                || image.fileBytes()!=0x40000 || !expected.equals(identity.mmdSha256()) || !expected.equals(CdR11aProfile.sha256(image.readFile(0,image.fileBytes()))))throw new IllegalArgumentException("Original R13 source/image relation differs");
        var ordered=new ArrayList<Source>();var addresses=new HashMap<Long,Source>();var labels=new HashMap<String,Source>();
        for(var id:SourceId.values()){var source=new Source(id,identity.file());ordered.add(source);if(addresses.putIfAbsent(source.nativeAddress(),source)!=null)throw new IllegalArgumentException("Duplicate R13 source address");for(var label:source.nativeLabels())if(labels.putIfAbsent(label,source)!=null)throw new IllegalArgumentException("Ambiguous R13 source alias");}
        var encountered=new LinkedHashSet<Long>();var bodies=new HashSet<CdR13FutureAssets.FileRange>();
        if(assets.plcs().size()!=17)throw new IllegalArgumentException("R13 requires17 PLC IDs");
        for(var list:assets.plcs()){bodies.add(list.bodyRange());for(var row:list.entries()){var source=addresses.get(row.sourceNativeAddress());if(source==null || source.outputBytes()>0x10000-row.vramAddress())throw new IllegalArgumentException("R13 PLC/source/output join differs");encountered.add(row.sourceNativeAddress());}}
        if(bodies.size()!=5 || !List.copyOf(encountered).equals(ordered.stream().map(Source::nativeAddress).toList()))throw new IllegalArgumentException("R13 finite PLC source order differs");
        sources=List.copyOf(ordered);byAddress=Map.copyOf(addresses);byLabel=Map.copyOf(labels);
    }
    /** Actual aggregate ingress only. Metadata records/raw images do not construct a registry. */
    public static CdR13SectionSources fromAssets(CdR13FutureAssets assets){return new CdR13SectionSources(assets);}
    public boolean ownsAssets(CdR13FutureAssets assets){return this.assets==assets;}
    public CdR13FutureAssets.Identity identity(){return identity;}
    public List<Source> sources(){return sources;}
    public Source source(SourceId id){return sources.get(Objects.requireNonNull(id).ordinal());}
    public Source source(long nativeAddress){var value=byAddress.get(nativeAddress);if(value==null)throw new IllegalArgumentException("Unknown R13 source address");return value;}
    public Source sourceForNativeLabel(String label){var value=byLabel.get(Objects.requireNonNull(label));if(value==null)throw new IllegalArgumentException("Unknown R13 native source label");return value;}
    /** Full genuine includes plus only the separately bounded real reader bytes; no decode/padding. */
    public synchronized CdAssetBytes view(SourceId id,ReaderKind kind){
        var source=source(id);Objects.requireNonNull(kind);var cache=views[id.ordinal()];if(cache[kind.ordinal()]!=null)return cache[kind.ordinal()];var range=source.range(kind);
        for(var other:ReaderKind.values())if(cache[other.ordinal()]!=null && range.equals(source.range(other))){cache[kind.ordinal()]=cache[other.ordinal()];return cache[kind.ordinal()];}
        var bytes=range.read(image);if(bytes.unsignedWord(0)!=source.headerWord() || !source.sha256(kind).equals(CdR11aProfile.sha256(bytes.copy())))throw new IllegalArgumentException("Retained R13 source bytes differ");cache[kind.ordinal()]=bytes;return bytes;
    }
    /** Pure existing-Java product decode only. Source values are never submitted/adopted into gameplay. */
    public synchronized CdNemesisAsset.Decoded decoded(SourceId id)throws IOException {
        var source=source(id);if(products[id.ordinal()]!=null)return products[id.ordinal()];var bytes=view(id,ReaderKind.JAVA);
        var product=id==SourceId.ART_LEVEL_TILES?assets.levelTilesDecode():CdNemesisAsset.decode(bytes.copy(),source.outputBytes(),0x10000);
        if(product.bytes().length()!=source.outputBytes() || product.consumedInputBytes()!=source.expectedJavaConsumedBytes()
                || !source.decodedSha256().equals(CdR11aProfile.sha256(product.bytes().copy())))throw new IOException("R13 finite Java source product differs");
        if(id==SourceId.ART_LEVEL_TILES && !Arrays.equals(product.bytes().copy(),assets.terrain().patterns().copy()))throw new IOException("R13 original level pattern product differs");products[id.ordinal()]=product;return product;
    }
}
