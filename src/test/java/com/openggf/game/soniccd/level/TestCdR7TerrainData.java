package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import java.nio.file.Path;
import java.util.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static com.openggf.game.soniccd.level.CdProgrammeReader.Role.*;

/** Authenticated canonical INITIAL terrain DATA; no legacy projection/actor/scroll/current-art admission. */
class TestCdR7TerrainData {
    private static CdProgramAssets source;
    private static synchronized CdProgramAssets media()throws Exception {
        if(source==null)source=CdProgramAssets.open(Path.of(System.getProperty("soniccd.media.root")).resolve("USA/Sonic CD (US).cue"),Optional.of(CdReleaseVariant.USA),Optional.empty());return source;
    }
    @AfterAll static void close()throws Exception {if(source!=null)source.close();source=null;}
    // Independent literal N1ecc/original-media oracles. A missing/wrong source binding must fail.
    private static int[] counts(String id) {return switch(id) {
            case "R71A" -> new int[]{924,735,117,48,6,18,3,1024};
            case "R71B" -> new int[]{728,752,114,48,6,24,3,900};
            case "R71C" -> new int[]{680,754,113,48,6,2,3,800};
            case "R71D" -> new int[]{968,754,107,48,6,18,3,1024};
            case "R72A" -> new int[]{924,735,117,48,8,18,3,1024};
            case "R72B" -> new int[]{728,752,114,48,8,24,3,900};
            case "R72C" -> new int[]{684,754,113,48,8,2,3,800};
            case "R72D" -> new int[]{968,753,107,48,8,24,3,1024};
            case "R73C" -> new int[]{680,754,113,64,3,2,3,800};
            case "R73D" -> new int[]{968,753,107,64,3,60,3,1024};
            default -> throw new IllegalArgumentException("No authenticated initial R7 oracle");
    };}
    @ParameterizedTest @ValueSource(strings={"R71A","R71B","R71C","R71D","R72A","R72B","R72C","R72D","R73C","R73D"})
    void authenticatedInitialTerrainUsesSharedIngress(String id)throws Exception {
        var expected=counts(id);var t=CdStageTerrain.fromProgramme(media(),CdStageKey.parse(id));
        assertEquals(expected[0],t.blockCount());assertEquals(expected[1],t.patternCount());assertEquals(expected[2],t.chunkCount());assertEquals(expected[7],t.collision().ids().length());
        assertEquals(expected[3],t.layout().columns(CdStageTerrain.Plane.FOREGROUND));assertEquals(expected[4],t.layout().rows(CdStageTerrain.Plane.FOREGROUND));
        assertEquals(expected[5],t.layout().columns(CdStageTerrain.Plane.BACKGROUND));assertEquals(expected[6],t.layout().rows(CdStageTerrain.Plane.BACKGROUND));
        assertThrows(UnsupportedOperationException.class,t::shape);
    }
    // Literal independent N1ecc/media hashes and operand witnesses, never expectations computed from profiles.
    private static List<String> hashes(String id) {return switch(id) {
            case "R71A" -> List.of("493c6adae1fb2b4b12706b1af377bc7ba86f89b580be19741e559ba0ebe4150f","574bfca2091b45675fe1b06061ae4acf2459b66e803d0ff9bb900d417dac9de1","14e7e09831b429d264072950f61b57633562142513cc94ea0c858a6858d64c86","6057c0cb3a1989882a3d3042c43faa9c4a994aff9f2cd3bada351a79c2c2e6de","2b03daf681eb9e63b2fe22dee8a2323062d158de18a77be8e3fa6dd86d5ee54f","0f6f13d9cc185a1c898b53f3354f850825f7b0d103b5f3dd988b3881c0071626","c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd","103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d","90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4");
            case "R71B" -> List.of("71eb59a8f73a3143b29b7da30fdf242105b87630c60d481927d6841df9c44ea8","5fa3622e5a650fca408918e60cb800b471673b278b996c52e92f6d1c01508c4f","f64a70afb794c08d10bf52a895903d0d7c3ca6076807dd01fc216f1bbb6f55a9","aa2251d5f6a1710e3393db37f0906870794b1ba6709ef4824e9ad07d99311bc2","dcf055290788fae0403f837b4cfa244cdba09e8b36f3d6ac4d76bc5d29d9d039","1c7576565a12f62cd681c00479d28dbf62999508e10eae3901636c7303384c86","c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd","103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d","90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4");
            case "R71C" -> List.of("b6102510affe5be9dc39c0f07fa01d044fcb44c5206577633dac6862be746374","af790c0808c4a0bb125ee29978e7004ce723328d0d01c73f2ecfe9ad47184ac0","6d7a0076311553dbf6d5cb051031e4048076ab390b02392a26894c77011e6c39","cc9711cc432a2205f2fd8e79039a99893b4f41ef9f401301d25cc9c8fe5bbdeb","5a03f216547f84d181f79330414e287339c1944115387f9c5826dda358ae68a3","4f7331a41ffae00a3cb1b878a8e470cf0039bef8bd168262f17c77c3e81de80e","c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd","103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d","90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4");
            case "R71D" -> List.of("69eb4c82ead17aefb0c11472e8ce47d165723572d7ee2c51da29a5c7b5525460","f16f18589a1d6a3417c470d48994aae4dc869c429e7e0376421a832670ef8390","5143aaadc67c7514eece0187201e9a48a7229da185cb42470a4a23841eed9041","793571d7cbf75caade3cda4a2bd63fa86ca42e2fb93a68740a4930911d992e0e","f07573148d8c3155277f3ba9b21180127905807c5b51830d687115cd07eb028b","cd1fa0926edcedddd45193177ba4e8dd155ca60c087c3f181328965c17b623e3","c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd","103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d","90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4");
            case "R72A" -> List.of("8c836095ac6c89979cf2cec70f1ba0513a715739942865108093dd8424f54b22","574bfca2091b45675fe1b06061ae4acf2459b66e803d0ff9bb900d417dac9de1","02387165982aad6bee5ca6d2dd4106999e355c98c06c124053634330072d6b86","79f29e436066e209fa3847dfaef81fef9c7c7697dfa634a14c94f8a1a2c66484","d9e2b68acd0f407e42af835a0fff1df509c5d118aa0724151654932b71734010","26d3693f2a6d2e74568f203e65ea47dfd7ffb394c5e01a74c76b333d0ce6abae","c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd","103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d","90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4");
            case "R72B" -> List.of("52294e74089afe7e88119a75b866756991e8260f8e971b854fcd8e30743e61ea","5fa3622e5a650fca408918e60cb800b471673b278b996c52e92f6d1c01508c4f","35852068d0984b59b0a224719c5ae2022878b583f570cce523f9497adf469f8d","6adb066098a3868636cb8a8b8b25337889f4e0239b6ce74eeee6b9e1a5ec60a6","dcf055290788fae0403f837b4cfa244cdba09e8b36f3d6ac4d76bc5d29d9d039","7ccedc5e18c370c384ec47d2ec13236da1867bd01d28d015130d113ac40625c3","c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd","103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d","90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4");
            case "R72C" -> List.of("54de0dd7b7302f5b87caf169098e20c6a57d4e15a8542c261a53a53db4178c3d","af790c0808c4a0bb125ee29978e7004ce723328d0d01c73f2ecfe9ad47184ac0","3f3009662bcc68945aeb5ecd7c1332d64c4a58311549be7e99bb9b1756cfec7e","05679b491f37cae1adc5d23ddb92037ce2b326616da0ff51d1b1ef6efff65cb1","5a03f216547f84d181f79330414e287339c1944115387f9c5826dda358ae68a3","80cd5068305a430b98277fbe16d2bb2a4e5ace2d8b7875dbc6b305617b8679cb","c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd","103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d","90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4");
            case "R72D" -> List.of("cb47d8b4f47f8d5414b3484ea89839d1d69381ca3655b2accd66009dc0628ba8","3e67dff7377a19106314142cf95768cfce3a8b945507603183bcc773bd79a6c2","22e1c827f482d5fb27cd2b156f6667d4034cb9e171a9a46c913ea1f9ffa99b68","398abf1a4da91a8bad05ec5ba3d637a348dbbf6125f3eb37eb31c88ba77e4257","6e34c1df51737309613e0023534f9dffe90588fa83a22c89ee6272e1612fca1d","e75fd683690acd0572b309813c17c6970325e25a7c37503888335785fa068201","c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd","103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d","90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4");
            case "R73C" -> List.of("388a59fb538689db9f12ec92facbf323a6b1ad8cb8200b3f82d706a315c63887","af790c0808c4a0bb125ee29978e7004ce723328d0d01c73f2ecfe9ad47184ac0","bfa98685a55315f566093ab66670e6c0022004c15cda9625a5133f58480ca57e","d4f71a8194d8b09abe649ba58b0ec4c49bd6ca065d1926f974fa46ab85ed438e","5a03f216547f84d181f79330414e287339c1944115387f9c5826dda358ae68a3","0152eae690f96a47d214aff2ec3d9992fed9da0c8775e4798be4c9b7a97211a9","c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd","103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d","90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4");
            case "R73D" -> List.of("d727bbefb73c3d0154c853b6a8de986eb94b9cee90e8b7283aa21ffa13f773a5","3e67dff7377a19106314142cf95768cfce3a8b945507603183bcc773bd79a6c2","089c046f2ecdc137aa787a4d1581a2523024296cd3fa45ce046c7ed24fab95bb","41a4116119f6a829b8541e161056a851729b416c73ecc160797e4f0b861096a8","e656ee9b866ae38abe01cafdb77896810253232c5ce324bd2e74d42bdec9ca3b","8b8bd91a9c3c4666a89fcdf60a0ba01002250bb31768ce1c4dbae009283d922e","c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd","103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d","90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4");
            default -> throw new IllegalArgumentException("No independent R7 source oracle");
    };}
    private static int[] geometry(String id) {return switch(id) {
            case "R71A" -> new int[]{921,921,79};
            case "R71B" -> new int[]{723,723,77};
            case "R71C" -> new int[]{655,655,72};
            case "R71D" -> new int[]{966,854,72};
            case "R72A" -> new int[]{921,921,56};
            case "R72B" -> new int[]{723,723,55};
            case "R72C" -> new int[]{673,673,54};
            case "R72D" -> new int[]{966,841,56};
            case "R73C" -> new int[]{652,652,133};
            case "R73D" -> new int[]{966,966,15};
            default -> throw new IllegalArgumentException("No independent R7 source oracle");
    };}
    private static int[] firstSolid(String id) {return switch(id) {
            case "R71A" -> new int[]{24,0,26,26,0,10,24801,81};
            case "R71B" -> new int[]{24,0,26,26,0,10,24577,81};
            case "R71C" -> new int[]{42,0,26,26,0,10,24624,81};
            case "R71D" -> new int[]{42,0,26,26,0,10,24621,81};
            case "R72A" -> new int[]{0,0,3,3,0,10,25345,81};
            case "R72B" -> new int[]{0,0,3,3,0,10,24837,81};
            case "R72C" -> new int[]{0,0,3,3,0,10,24624,81};
            case "R72D" -> new int[]{0,0,3,3,0,10,28926,81};
            case "R73C" -> new int[]{0,1,2,2,0,10,25014,81};
            case "R73D" -> new int[]{0,1,2,2,0,10,24777,81};
            default -> throw new IllegalArgumentException("No independent R7 source oracle");
    };}
    private static long[][] initialPlcs(String id) {return switch(id) {
            case "R71A" -> new long[][]{{2327032,0,23520},{2307560,55648,384}};
            case "R71B" -> new long[][]{{2325638,0,24064}};
            case "R71C" -> new long[][]{{2325320,0,24128}};
            case "R71D" -> new long[][]{{2327370,0,24128}};
            case "R72A" -> new long[][]{{2327128,0,23520},{2307560,55648,384}};
            case "R72B" -> new long[][]{{2325734,0,24064}};
            case "R72C" -> new long[][]{{2325442,0,24128}};
            case "R72D" -> new long[][]{{2327486,0,24096}};
            case "R73C" -> new long[][]{{2325226,0,24128}};
            case "R73D" -> new long[][]{{2327400,0,24096}};
            default -> throw new IllegalArgumentException("No independent R7 source oracle");
    };}
    @ParameterizedTest @ValueSource(strings={"R71A","R71B","R71C","R71D","R72A","R72B","R72C","R72D","R73C","R73D"})
    void productsAndRawQueriesMatchIndependentNativeMediaOracles(String id)throws Exception {
        var r=CdProgrammeReader.open(media(),CdStageKey.parse(id));var t=r.terrain();var expected=hashes(id);
        var fields=List.of(t.blocks(),t.patterns(),t.chunks(),r.role(FG),r.role(BG),t.collision().ids(),t.collision().angles(),t.collision().heights(),t.collision().widths());
        for(int i=0;i<fields.size();i++)assertEquals(expected.get(i),CdR11aProfile.sha256(fields.get(i).copy()),"original source role "+i);
        for(int block=0;block<t.blockCount();block++)for(int y=0;y<2;y++)for(int x=0;x<2;x++) {
            int raw=t.blocks().unsignedWord(block*8+(y*2+x)*2);var word=t.blockWord(block,x,y);
            assertEquals(raw,word.rawWord());assertEquals(raw&2047,word.patternIndex());assertEquals((raw>>>13)&3,word.paletteLine());assertTrue(word.patternIndex()<counts(id)[1]);
        }
        for(int chunk=1;chunk<=t.chunkCount();chunk++)for(int y=0;y<16;y++)for(int x=0;x<16;x++) {
            int raw=t.chunks().unsignedWord((chunk-1)*512+(y*16+x)*2);var word=t.chunkWord(chunk,x,y);
            assertEquals(raw,word.rawWord());assertEquals(raw&1023,word.renderBlockIndex());assertEquals(raw&2047,word.collisionBlockIndex());assertEquals((raw>>>13)&3,word.solidity());
        }
        for(int pattern=0;pattern<t.patternCount();pattern++)for(int y=0;y<8;y++)for(int x=0;x<8;x++) {
            int packed=t.patterns().unsignedByte(pattern*32+y*4+x/2);assertEquals((x&1)==0?packed>>>4:packed&15,t.pixel(pattern,x,y));
        }
    }
    @ParameterizedTest @ValueSource(strings={"R71A","R71B","R71C","R71D","R72A","R72B","R72C","R72D","R73C","R73D"})
    void exactSignedDirectoryViewsClearCopyAndRawLayerFlagsStaySeparate(String id)throws Exception {
        var r=CdProgrammeReader.open(media(),CdStageKey.parse(id));var t=r.terrain();int flags=0;
        for(var plane:CdStageTerrain.Plane.values()) {
            var source=r.role(plane==CdStageTerrain.Plane.FOREGROUND?FG:BG);int w=source.unsignedByte(0)+1,h=source.unsignedByte(1)+1;assertEquals(2+w*h,source.length());
            for(int y=0;y<16;y++)for(int x=0;x<64;x++) {
                int expected=x<w&&y<h?source.unsignedByte(2+y*w+x):0; // Only the source-proven $800 clear supplies these zeros.
                var cell=t.layout().nativeCell(plane,x,y);assertEquals(expected,cell.rawByte());assertEquals(expected&127,cell.chunkId());
                if(x<w&&y<h)assertEquals(cell,t.layout().cell(plane,x,y));
                if(plane==CdStageTerrain.Plane.FOREGROUND && (expected&128)!=0)flags++;
            }
        }
        assertEquals(geometry(id)[2],flags); // Raw R7 flags do not invoke GetBlockZone5AnimatedTiles or player priority/layer writes.
        assertEquals(CdStageTerrain.LayoutEncoding.EXACT_READ_VIEW,t.profile().layoutEncoding());assertEquals(CdStageTerrain.ReferencePolicy.SELECTED_KNOWN_SOURCE,t.profile().referencePolicy());
        assertThrows(IndexOutOfBoundsException.class,()->t.layout().cell(CdStageTerrain.Plane.BACKGROUND,t.profile().backgroundColumns(),0));
    }
    @ParameterizedTest @ValueSource(strings={"R71A","R71B","R71C","R71D","R72A","R72B","R72C","R72D","R73C","R73D"})
    void fullSignedCollisionTablesAndDirectEncodedCandidatesRemainFinite(String id)throws Exception {
        var t=CdStageTerrain.fromProgramme(media(),CdStageKey.parse(id));int maxRaw=0,maxSolid=0;
        for(int y=0;y<t.profile().foregroundRows();y++)for(int x=0;x<t.profile().foregroundColumns();x++) {
            int chunk=t.layout().cell(CdStageTerrain.Plane.FOREGROUND,x,y).chunkId();if(chunk==0)continue;
            for(int cy=0;cy<16;cy++)for(int cx=0;cx<16;cx++) {
                var word=t.chunkWord(chunk,cx,cy);maxRaw=Math.max(maxRaw,word.collisionBlockIndex());
                if(word.solidity()!=0 && word.collisionBlockIndex()!=0) {
                    maxSolid=Math.max(maxSolid,word.collisionBlockIndex());assertEquals(t.collision().ids().unsignedByte(word.collisionBlockIndex()),t.collision().idForBlock(word.collisionBlockIndex()));
                }
            }
        }
        assertEquals(geometry(id)[0],maxRaw);assertEquals(geometry(id)[1],maxSolid);
        int[] witness=firstSolid(id);assertEquals(witness[2],t.layout().cell(CdStageTerrain.Plane.FOREGROUND,witness[0],witness[1]).rawByte());assertTrue(witness[2]<128);
        var word=t.chunkWord(witness[3],witness[4],witness[5]);assertEquals(witness[6],word.rawWord());assertEquals(witness[7],t.collision().idForBlock(word.collisionBlockIndex()));
        assertEquals(255,t.collision().angle(81));assertEquals(16,t.collision().height(81,0));assertEquals(16,t.collision().width(81,0));
        for(int cid=0;cid<256;cid++)for(int at=0;at<16;at++) {
            assertEquals((byte)t.collision().heights().unsignedByte(cid*16+at),t.collision().height(cid,at));assertEquals((byte)t.collision().widths().unsignedByte(cid*16+at),t.collision().width(cid,at));assertEquals(t.collision().angles().unsignedByte(cid),t.collision().angle(cid));
        }
    }
    private static void unavailable(CdStageTerrain.ReadOwner owner,int index,int count,org.junit.jupiter.api.function.Executable query) {
        var failure=assertThrows(CdStageTerrain.UnavailableRead.class,query);assertEquals(owner,failure.owner());assertEquals(index,failure.index());assertEquals(count,failure.availableCount());
    }
    @ParameterizedTest @ValueSource(strings={"R71A","R71B","R71C","R71D","R72A","R72B","R72C","R72D","R73C","R73D"})
    void authenticatedPrefixLimitsRejectSelectedMissingDataWithoutPadding(String id)throws Exception {
        var t=CdStageTerrain.fromProgramme(media(),CdStageKey.parse(id));int[] n=counts(id);
        unavailable(CdStageTerrain.ReadOwner.BLOCK_STAGING,n[0],n[0],()->t.blockWord(n[0],0,0));unavailable(CdStageTerrain.ReadOwner.PATTERN_SOURCE,n[1],n[1],()->t.pixel(n[1],0,0));
        unavailable(CdStageTerrain.ReadOwner.CHUNK_SOURCE,n[2]+1,n[2],()->t.chunkWord(n[2]+1,0,0));unavailable(CdStageTerrain.ReadOwner.COLLISION_SOURCE,n[7],n[7],()->t.collision().idForBlock(n[7]));
        assertThrows(IndexOutOfBoundsException.class,()->t.blockWord(-1,0,0));assertThrows(IndexOutOfBoundsException.class,()->t.pixel(0,8,0));assertThrows(IndexOutOfBoundsException.class,()->t.collision().height(256,0));assertThrows(IndexOutOfBoundsException.class,()->t.collision().width(0,16));
    }
    @ParameterizedTest @ValueSource(strings={"R71A","R71B","R71C","R71D","R72A","R72B","R72C","R72D","R73C","R73D"})
    void selectedInitialPlcIndexKeepsAliasOrderAndNoncontiguousDestinations(String id)throws Exception {
        var r=CdProgrammeReader.open(media(),CdStageKey.parse(id));var table=r.loadTables();var selected=table.plcs().get(3);long[][] expected=initialPlcs(id);
        assertEquals(19,table.plcs().size());assertEquals(3,selected.nativeId());assertSame(table.plcs().getFirst().body(),selected.body());assertEquals(expected.length,selected.body().entries().size());
        for(int i=0;i<expected.length;i++) {var entry=selected.body().entries().get(i);assertEquals(expected[i][0],entry.sourceNativeAddress());assertEquals(expected[i][1],entry.vramAddress());assertEquals(expected[i][2],entry.outputBytes());}
        assertEquals(r.terrain().patterns().length(),expected[0][2]);for(int i=1;i<expected.length;i++)assertTrue(expected[i][1]>=r.terrain().patterns().length());
    }
    @ParameterizedTest @ValueSource(strings={"R71A","R71B","R71C","R71D","R72A","R72B","R72C","R72D","R73C","R73D"})
    void failedAndMalformedReadsRetryWithoutPublishingOrBypassingAuthentication(String id)throws Exception {
        var p=CdR7DataLayout.Programme.valueOf(id);var r=CdProgrammeReader.open(media(),p.payload().stage());var binding=r.definition().terrain().orElseThrow();var observed=spy(r);var first=new IOException("original R7 graphics unavailable");
        doThrow(first).when(observed).decoded(binding.graphics());clearInvocations(observed);assertSame(first,assertThrows(IOException.class,observed::terrain));verify(observed,never()).role(CHUNKS);
        var blockProduct=r.decoded(binding.blocks());doReturn(r.decoded(binding.graphics())).when(observed).decoded(binding.graphics());doReturn(CdAssetBytes.copyOf(new byte[]{0,0})).when(observed).role(BG);
        assertThrows(IllegalArgumentException.class,observed::terrain);assertSame(blockProduct,r.decoded(binding.blocks()));
        var late=new AssertionError("original R7 BG unavailable before collision");doThrow(late).when(observed).role(BG);clearInvocations(observed);assertSame(late,assertThrows(AssertionError.class,observed::terrain));verify(observed,never()).role(COLLISION);
        doReturn(r.role(BG)).when(observed).role(BG);var terrain=observed.terrain();clearInvocations(observed);assertSame(terrain,observed.terrain());verify(observed,never()).role(any());verify(observed,never()).decoded(any());assertNotSame(terrain,CdStageTerrain.fromProgramme(media(),p.payload().stage()));
        var wrong=mock(CdProgramAssets.class,delegatesTo(media()));doReturn(media().mmd(p==CdR7DataLayout.Programme.R71A?CdNativeFile.R71B:CdNativeFile.R71A)).when(wrong).mmd(p.file());assertThrows(IOException.class,()->CdStageTerrain.fromProgramme(wrong,p.payload().stage()));
    }
    @ParameterizedTest @ValueSource(strings={"R71A","R71B","R71C","R71D","R72A","R72B","R72C","R72D","R73C","R73D"})
    void immutableTerrainDoesNotAdmitLegacyShapeSourceFamilyOrAbsentLoadReads(String id)throws Exception {
        var r=spy(CdProgrammeReader.open(media(),CdStageKey.parse(id)));clearInvocations(r);assertTrue(r.definition().dataOnly());
        assertThrows(UnsupportedOperationException.class,r::ordinaryZoneActWord);assertThrows(UnsupportedOperationException.class,r::terrainShape);assertThrows(IllegalArgumentException.class,()->CdProgrammeData.fromReader(r));verify(r,never()).role(any());verify(r,never()).decoded(any());
        doThrow(new AssertionError("Terrain DATA must not select load/palette/section operations")).when(r).loadTables();var terrain=r.terrain();assertThrows(UnsupportedOperationException.class,terrain::shape);verify(r,never()).loadTables();
        assertEquals(CdStageTerrain.ReferencePolicy.SELECTED_KNOWN_SOURCE,terrain.profile().referencePolicy());
    }
    @Test void originalR73cBackgroundWord680RemainsRawMissingBlockStaging()throws Exception {
        var r=CdProgrammeReader.open(media(),CdStagePayload.R73C.stage());var t=r.terrain();assertEquals(112,t.layout().cell(CdStageTerrain.Plane.BACKGROUND,0,2).chunkId());
        var word=t.chunkWord(112,5,6);assertEquals(680,word.rawWord());assertEquals(0,word.solidity());unavailable(CdStageTerrain.ReadOwner.BLOCK_STAGING,680,680,()->t.blockWord(word.renderBlockIndex(),0,0));
        assertEquals(800,t.collision().ids().length());assertEquals(t.collision().ids().unsignedByte(680),t.collision().idForBlock(680)); // Known DATA only; solidity0 grants no native collision read.
        var p=t.profile();var eager=new CdStageTerrain.Profile(p.blockBytes(),p.patternBytes(),p.chunkBytes(),p.foregroundColumns(),p.foregroundRows(),p.backgroundColumns(),p.backgroundRows(),p.collisionIndexBytes(),p.layoutEncoding(),CdStageTerrain.ReferencePolicy.EAGER_VALIDATED);
        assertThrows(IllegalArgumentException.class,()->CdStageTerrain.decodeData(eager,Optional.empty(),t.blocks().copy(),t.patterns().copy(),t.chunks().copy(),r.role(FG).copy(),r.role(BG).copy(),t.collision().ids().copy(),t.collision().angles().copy(),t.collision().heights().copy(),t.collision().widths().copy()));
    }
    @Test void terrainDataLeavesZeroPaletteSlotAndTrueRtsSectionsUnavailable()throws Exception {
        for(var stage:List.of(CdStagePayload.R71D,CdStagePayload.R72D,CdStagePayload.R73D)) {
            var r=CdProgrammeReader.open(media(),stage.stage());var terrain=r.terrain();var palettes=r.loadTables().palettes();var missing=palettes.get(5);
            assertEquals(5,missing.nativeId());assertEquals(0,missing.sourceNativeAddress());assertEquals(0,missing.destinationWord());assertEquals(65535,missing.longwordCounter());assertTrue(missing.sourceBytes().isEmpty());assertThrows(UnsupportedOperationException.class,missing::bytes);
            assertEquals(6,palettes.get(6).nativeId());assertTrue(palettes.get(6).sourceBytes().isPresent());assertSame(terrain,r.terrain());
        }
        for(var stage:List.of(CdStagePayload.R73C,CdStagePayload.R73D)) {
            var r=CdProgrammeReader.open(media(),stage.stage());assertNotNull(r.terrain());assertEquals(CdProgrammeDefinition.NoSections.INSTANCE,r.loadTables().sectionProgramme());assertThrows(UnsupportedOperationException.class,r.loadTables()::sections);
        }
    }
}
