package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.resources.CdNemesisPlcDecoder;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Modifier;
import java.nio.ReadOnlyBufferException;
import java.nio.file.*;
import java.util.*;
import static com.openggf.game.soniccd.level.CdR13SectionSources.ReaderKind.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

/** Actual original-USA PLC inputs/products, no service/current-bank/Frame authority. Tests first; Java UNRUN.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Graphics Lists/Data (Act3), Functions(General)401-579.
 * Native extent/cursor metadata is independent in-memory research, not an alternate production decoder.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdR13SectionSources {
    private static final String MANIFEST="""
R13C ART_LEVEL_TILES 39746 11158 8314 97 11158 11159 6183109387a85f9885fde847a95e543af57f3726913d6e4a7e2651a5ab7103cb 6183109387a85f9885fde847a95e543af57f3726913d6e4a7e2651a5ab7103cb 3d8c9e2ae434f5a2af49a67abfb44aa24d985868f3ce562ded7421919b4bfd4d 963d8b39d5941ead09033c6b5cb773a56bb848f02f11540b228a221a8db23438
R13C ART_TITLE_CARD 30da2 940 806d 73 939 940 8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8 8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8 8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8 68b70b1e8189073c24642b8939420ac4d3c3ba1d6a1c9c00d6538390d1764f7a
R13C ART_TITLE_CARD_TEXT 339f6 298 8034 49 298 298 39ec1617bda91d34562b315729f003e09c907ec95da5bffb31333b2c4dead3e5 39ec1617bda91d34562b315729f003e09c907ec95da5bffb31333b2c4dead3e5 39ec1617bda91d34562b315729f003e09c907ec95da5bffb31333b2c4dead3e5 66b74c71966af3b2bdf42828b3a0b5e5fcf73f05c8cae89fa2ff3f28eb01ee70
R13C ART_BOULDER 33ce4 270 8013 51 270 271 6335b858cc043dcce7de3f716a506c5d393342298729c04739673331f0e4e55f 6335b858cc043dcce7de3f716a506c5d393342298729c04739673331f0e4e55f bf9f0cf2200b515b5f84c8daf70b4d78dcbcd92d432e2f305aebdc7aa0fb2f48 abdbe20715a2c4784fae851622bb276dc1630abc339efb1e13b99a5ddc4d0c7d
R13C ART_SPRINGS 3213a 546 8024 82 547 547 19212b9e7b64237a58a6bfa64566338930fd82a71555dbfab61e9bc45e165831 52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847 52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847 8e3c6d91a395d24872b4ae5da293cd21d8e68230687ef9f07feb62ec2612f416
R13C ART_HUD 334bc 300 801b 51 300 301 d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb fb31ef2bca6df6e547b6e578c1455730a6665226befb550fd6c03e2a9a1a88b5 0a448ef2c11a1414886684523d551d65af43e4f0c02e08c73e869437b15b40fc
R13C ART_MONITOR_TIME_POSTS 3235c 1540 8052 107 1541 1541 ee98713d147c62128c7af02e250a6bf733c49c2c86a90332ecc4d77be29ed05a a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902 a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902 7575dcf197dbc2f246838cfedcb95af40100d449f0a0c5a48b7294c27c9a924f
R13C ART_EXPLOSIONS 32960 1256 46 91 1256 1257 1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1 1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1 c37077d4a4bf8aab8c50bd454137dba54a054c5a756b0f3a00515468d81fcdfe 434f2415b56d7623e57fd94533506804d7ee1fe3a0321f77bfe86f58d8ff6664
R13C ART_POINTS 2ede6 100 5 31 101 101 6923bd2ac955ac11b88bb75b036d2fbd24f02fe523cd401e30bb1520426ad363 9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176 9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176 2270d6453b2d2526412afab1ba19c3cf175b571a41766dc4fe0da422283d57db
R13C ART_FLOWER 33770 646 8027 79 645 646 baae7501cf372a57007db352ffcfe404cc8d2066a9cc639e64d1f2796cfe9af1 baae7501cf372a57007db352ffcfe404cc8d2066a9cc639e64d1f2796cfe9af1 baae7501cf372a57007db352ffcfe404cc8d2066a9cc639e64d1f2796cfe9af1 db06551b90412becaec9aa45ccfd14dca62c94e1f165718629553f8fdaac6793
R13C ART_RINGS 32e48 244 e 48 244 244 530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2 530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2 530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2 c1f35c87c98d27990926ad515264ab1e8a5c9385b087931d79ff24709afd6abb
R13C ART_TUNNEL_DOOR 34320 154 800c 49 153 154 cbbd577ac9af46007b2618fd0481753435aae38e0f68fe0ae7ed17df74b0cb7b cbbd577ac9af46007b2618fd0481753435aae38e0f68fe0ae7ed17df74b0cb7b cbbd577ac9af46007b2618fd0481753435aae38e0f68fe0ae7ed17df74b0cb7b dc7be7cdc86c642af5aafd7462269397a6d901678241fde33a35b9209eaf22a0
R13C ART_WATERFALL 3417c 420 8020 47 420 421 9f0fee55bedc64dbcd4c505906db1d120d292f9308dce4027e9bcd6f5703c340 9f0fee55bedc64dbcd4c505906db1d120d292f9308dce4027e9bcd6f5703c340 a1dff8aa5d05e61738cc3e6b2d1b5ca23baacdfced9480717bfe52186f3f9128 904e8950e10d01607b0a8c7bee6c3bfb0957827fac8e451b4025f85b920721c3
R13C ART_ANIMALS 359e2 446 14 89 445 446 63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab 63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab 63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab ac1edab54e2b767578c6b3c2b828dc9821ef02c7a3bed9079dc7dded4a26e8d1
R13C ART_TAMABBOH 35172 1142 803b 91 1142 1143 39b42a53ad2a2b1e50c73d2f0abdb918304fd8987499af1a4d8773b9239c309f 39b42a53ad2a2b1e50c73d2f0abdb918304fd8987499af1a4d8773b9239c309f c90255c16ce2a453a6dad66e1b9ff641e12b2fc87e5b639429e1ef1962978b0e aa544f679799481cc55fe2f329ef30be56ee2c8f8a655e5509f02d4035cbed95
R13C ART_FLOWER_CAPSULE 2ee4a 1704 8068 101 1703 1704 da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808 da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808 da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808 b61c6d944dce535ccb74d0a374ac873e95a5e0b3911b465f62bb025b03660236
R13C ART_BOSS_MACHINE 1d400 2822 80a4 97 2821 2822 3e68f355f2cea70a3f79c657ce661623641afb2d5b025d7ad1003ee52e10bc8b 3e68f355f2cea70a3f79c657ce661623641afb2d5b025d7ad1003ee52e10bc8b 3e68f355f2cea70a3f79c657ce661623641afb2d5b025d7ad1003ee52e10bc8b 9862e164be793c18e701abd1037d9b2619b9f0c9d34fbf6000ab9b515c9b582a
R13C ART_EGGMAN 1df06 1902 8070 103 1902 1903 36015b93b0a37feafb2a35729cefda33e2d8910ec8963e37220782de71f8d3bf 36015b93b0a37feafb2a35729cefda33e2d8910ec8963e37220782de71f8d3bf c6debab0076bb136feb19ac084801080b2e66a6d2f0fcc97841c3d0138006df5 8e15af728aa36095a2aaaf85302299798ee2e57a43538736f334c29e165efa02
R13C ART_RESULTS 30098 1354 8078 67 1354 1354 c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40 c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40 c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40 fdb13e161a5862fe4f8c550291bba36589028b8a7cc0394638c019a0ab35760d
R13D ART_LEVEL_TILES 39734 11686 832e 105 11685 11686 08c497311f7960a47b7d18f98123f5e74f353be899847bf3d53af9665a52a24e 08c497311f7960a47b7d18f98123f5e74f353be899847bf3d53af9665a52a24e 08c497311f7960a47b7d18f98123f5e74f353be899847bf3d53af9665a52a24e 11cd4655075a61e0a55b553f15e9f9717ee6f08bb6644836b5a5cef1206a9caf
R13D ART_TITLE_CARD 30da2 940 806d 73 939 940 8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8 8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8 8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8 68b70b1e8189073c24642b8939420ac4d3c3ba1d6a1c9c00d6538390d1764f7a
R13D ART_TITLE_CARD_TEXT 339f6 298 8034 49 298 298 39ec1617bda91d34562b315729f003e09c907ec95da5bffb31333b2c4dead3e5 39ec1617bda91d34562b315729f003e09c907ec95da5bffb31333b2c4dead3e5 39ec1617bda91d34562b315729f003e09c907ec95da5bffb31333b2c4dead3e5 66b74c71966af3b2bdf42828b3a0b5e5fcf73f05c8cae89fa2ff3f28eb01ee70
R13D ART_BOULDER 33ce4 270 8013 51 270 271 6335b858cc043dcce7de3f716a506c5d393342298729c04739673331f0e4e55f 6335b858cc043dcce7de3f716a506c5d393342298729c04739673331f0e4e55f bf9f0cf2200b515b5f84c8daf70b4d78dcbcd92d432e2f305aebdc7aa0fb2f48 abdbe20715a2c4784fae851622bb276dc1630abc339efb1e13b99a5ddc4d0c7d
R13D ART_SPRINGS 3213a 546 8024 82 547 547 19212b9e7b64237a58a6bfa64566338930fd82a71555dbfab61e9bc45e165831 52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847 52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847 8e3c6d91a395d24872b4ae5da293cd21d8e68230687ef9f07feb62ec2612f416
R13D ART_HUD 334bc 300 801b 51 300 301 d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb fb31ef2bca6df6e547b6e578c1455730a6665226befb550fd6c03e2a9a1a88b5 0a448ef2c11a1414886684523d551d65af43e4f0c02e08c73e869437b15b40fc
R13D ART_MONITOR_TIME_POSTS 3235c 1540 8052 107 1541 1541 ee98713d147c62128c7af02e250a6bf733c49c2c86a90332ecc4d77be29ed05a a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902 a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902 7575dcf197dbc2f246838cfedcb95af40100d449f0a0c5a48b7294c27c9a924f
R13D ART_EXPLOSIONS 32960 1256 46 91 1256 1257 1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1 1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1 c37077d4a4bf8aab8c50bd454137dba54a054c5a756b0f3a00515468d81fcdfe 434f2415b56d7623e57fd94533506804d7ee1fe3a0321f77bfe86f58d8ff6664
R13D ART_POINTS 2ede6 100 5 31 101 101 6923bd2ac955ac11b88bb75b036d2fbd24f02fe523cd401e30bb1520426ad363 9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176 9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176 2270d6453b2d2526412afab1ba19c3cf175b571a41766dc4fe0da422283d57db
R13D ART_FLOWER 33770 646 8027 79 645 646 baae7501cf372a57007db352ffcfe404cc8d2066a9cc639e64d1f2796cfe9af1 baae7501cf372a57007db352ffcfe404cc8d2066a9cc639e64d1f2796cfe9af1 baae7501cf372a57007db352ffcfe404cc8d2066a9cc639e64d1f2796cfe9af1 db06551b90412becaec9aa45ccfd14dca62c94e1f165718629553f8fdaac6793
R13D ART_RINGS 32e48 244 e 48 244 244 530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2 530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2 530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2 c1f35c87c98d27990926ad515264ab1e8a5c9385b087931d79ff24709afd6abb
R13D ART_TUNNEL_DOOR 34320 154 800c 49 153 154 cbbd577ac9af46007b2618fd0481753435aae38e0f68fe0ae7ed17df74b0cb7b cbbd577ac9af46007b2618fd0481753435aae38e0f68fe0ae7ed17df74b0cb7b cbbd577ac9af46007b2618fd0481753435aae38e0f68fe0ae7ed17df74b0cb7b dc7be7cdc86c642af5aafd7462269397a6d901678241fde33a35b9209eaf22a0
R13D ART_WATERFALL 3417c 420 8020 47 420 421 9f0fee55bedc64dbcd4c505906db1d120d292f9308dce4027e9bcd6f5703c340 9f0fee55bedc64dbcd4c505906db1d120d292f9308dce4027e9bcd6f5703c340 a1dff8aa5d05e61738cc3e6b2d1b5ca23baacdfced9480717bfe52186f3f9128 904e8950e10d01607b0a8c7bee6c3bfb0957827fac8e451b4025f85b920721c3
R13D ART_ANIMALS 359e2 446 14 89 445 446 63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab 63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab 63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab ac1edab54e2b767578c6b3c2b828dc9821ef02c7a3bed9079dc7dded4a26e8d1
R13D ART_TAMABBOH 35172 1142 803b 91 1142 1143 39b42a53ad2a2b1e50c73d2f0abdb918304fd8987499af1a4d8773b9239c309f 39b42a53ad2a2b1e50c73d2f0abdb918304fd8987499af1a4d8773b9239c309f c90255c16ce2a453a6dad66e1b9ff641e12b2fc87e5b639429e1ef1962978b0e aa544f679799481cc55fe2f329ef30be56ee2c8f8a655e5509f02d4035cbed95
R13D ART_FLOWER_CAPSULE 2ee4a 1704 8068 101 1703 1704 da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808 da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808 da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808 b61c6d944dce535ccb74d0a374ac873e95a5e0b3911b465f62bb025b03660236
R13D ART_BOSS_MACHINE 1d400 2822 80a4 97 2821 2822 3e68f355f2cea70a3f79c657ce661623641afb2d5b025d7ad1003ee52e10bc8b 3e68f355f2cea70a3f79c657ce661623641afb2d5b025d7ad1003ee52e10bc8b 3e68f355f2cea70a3f79c657ce661623641afb2d5b025d7ad1003ee52e10bc8b 9862e164be793c18e701abd1037d9b2619b9f0c9d34fbf6000ab9b515c9b582a
R13D ART_EGGMAN 1df06 1902 8070 103 1902 1903 36015b93b0a37feafb2a35729cefda33e2d8910ec8963e37220782de71f8d3bf 36015b93b0a37feafb2a35729cefda33e2d8910ec8963e37220782de71f8d3bf c6debab0076bb136feb19ac084801080b2e66a6d2f0fcc97841c3d0138006df5 8e15af728aa36095a2aaaf85302299798ee2e57a43538736f334c29e165efa02
R13D ART_RESULTS 30098 1354 8078 67 1354 1354 c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40 c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40 c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40 fdb13e161a5862fe4f8c550291bba36589028b8a7cc0394638c019a0ab35760d
""";
    @Test void all38VariantDescriptorsAndReaderViewsMatchActualOriginalImages()throws Exception {
        var f=actual();assertEquals(19,CdR13SectionSources.SourceId.values().length);assertEquals(38,MANIFEST.strip().lines().count());
        for(var line:MANIFEST.strip().lines().toList()) {
            var v=line.strip().split("\\s+");var a=v[0].equals("R13C")?f.good():f.bad();var image=v[0].equals("R13C")?f.c():f.d();var registry=CdR13SectionSources.fromAssets(a);var id=CdR13SectionSources.SourceId.valueOf(v[1]);var s=registry.source(id);
            int at=Integer.parseInt(v[2],16),blob=Integer.parseInt(v[3]),header=Integer.parseInt(v[4],16),prep=Integer.parseInt(v[5]),jc=Integer.parseInt(v[6]),nc=Integer.parseInt(v[7]);
            assertEquals(a.identity().file(),s.blobRange().file());assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM,s.blobRange().domain());assertEquals(at,s.blobRange().fileOffset());assertEquals(blob,s.blobRange().length());assertEquals(0x200000L+at,s.nativeAddress());assertEquals(header,s.headerWord());assertEquals((header&32767)*32,s.outputBytes());
            assertEquals(prep,s.expectedPrepareConsumedBytes());assertEquals(jc,s.expectedJavaConsumedBytes());assertEquals(nc,s.expectedNativeConsumedBytes());assertEquals(Math.max(blob,jc),s.javaReaderRange().length());assertEquals(Math.max(blob,nc),s.nativeReaderRange().length());
            for(var kind:CdR13SectionSources.ReaderKind.values()){var range=kind==CANONICAL?s.blobRange():kind==JAVA?s.javaReaderRange():s.nativeReaderRange();var bytes=registry.view(id,kind);assertArrayEquals(image.readFile(range.fileOffset(),range.length()),bytes.copy());assertEquals(v[kind==CANONICAL?8:kind==JAVA?9:10],hash(bytes.copy()));assertEquals(s.sha256(kind),hash(bytes.copy()));}
            assertEquals(v[11],s.decodedSha256());
        }
    }
    @Test void everyNativeListAliasDestinationAndDistinctPointerJoinsTheFixedSourceRegistry()throws Exception {
        for(var a:actual().all()){var registry=CdR13SectionSources.fromAssets(a);assertEquals(19,registry.sources().size());var order=new LinkedHashSet<Long>();var bodies=new HashSet<Integer>();
            for(var list:a.plcs()){bodies.add(list.bodyRange().fileOffset());for(var row:list.entries()){var s=registry.source(row.sourceNativeAddress());assertTrue(s.outputBytes()<=0x10000-row.vramAddress());assertEquals(a.identity().file(),s.blobRange().file());order.add(row.sourceNativeAddress());}}
            assertEquals(17,a.plcs().size());assertEquals(5,bodies.size());assertEquals(registry.sources().stream().map(CdR13SectionSources.Source::nativeAddress).toList(),List.copyOf(order));
            for(var s:registry.sources())for(var label:s.nativeLabels())assertSame(s,registry.sourceForNativeLabel(label));
        }
    }
    @Test void completeProductsUseOnlyExistingJavaDecoderAndReuseActualLevelPatterns()throws Exception {
        for(var a:actual().all()){var r=CdR13SectionSources.fromAssets(a);for(var s:r.sources()){var decoded=r.decoded(s.id());assertEquals(s.outputBytes(),decoded.bytes().length());assertEquals(s.expectedJavaConsumedBytes(),decoded.consumedInputBytes());assertEquals(s.decodedSha256(),hash(decoded.bytes().copy()));assertSame(decoded,r.decoded(s.id()));}
            assertSame(a.levelTilesDecode(),r.decoded(CdR13SectionSources.SourceId.ART_LEVEL_TILES));assertArrayEquals(a.terrain().patterns().copy(),r.decoded(CdR13SectionSources.SourceId.ART_LEVEL_TILES).bytes().copy());
        }
    }
    @Test void existingPurePlcDecoderReadsTheDocumentedFiniteNativeViews()throws Exception {
        // Explicit constructed incoming code-table bytes test holes/read bounds; not native initialization or a default.
        byte[] dirty=new byte[512];for(int i=0;i<512;i++)dirty[i]=(byte)(i*29+7);var seed=CdAssetBytes.copyOf(dirty);
        for(var a:actual().all()){var r=CdR13SectionSources.fromAssets(a);for(var s:r.sources()){
            var state=CdNemesisPlcDecoder.prepare(r.view(s.id(),NATIVE),s.nativeAddress(),seed);assertEquals(s.expectedPrepareConsumedBytes(),state.inputOffset());var output=new ByteArrayOutputStream();
            while(state.remainingTiles()!=0){var tile=CdNemesisPlcDecoder.decodeTile(state);output.writeBytes(tile.bytes().copy());state=tile.state();}
            assertEquals(s.expectedNativeConsumedBytes(),state.inputOffset());assertEquals(s.decodedSha256(),hash(output.toByteArray()));assertArrayEquals(dirty,seed.copy());
        }}
    }
    @Test void finiteReaderTruncationFailsRatherThanSynthesizingTheNeighborByte()throws Exception {
        var seed=CdAssetBytes.copyOf(new byte[512]); // Constructed pure-format input; never a gameplay clear.
        for(var a:actual().all()){var r=CdR13SectionSources.fromAssets(a);for(var s:r.sources()){
            var full=r.view(s.id(),NATIVE);var shortRead=full.slice(0,s.expectedNativeConsumedBytes()-1);
            var failure=assertThrows(CdNemesisPlcDecoder.DecodeException.class,()->decodeAll(shortRead,s.nativeAddress(),seed));assertEquals(CdNemesisPlcDecoder.Reason.SOURCE_EXHAUSTED,failure.reason());
            if(s.expectedJavaConsumedBytes()>s.blobRange().length())assertThrows(java.io.IOException.class,()->CdNemesisAsset.decode(r.view(s.id(),CANONICAL).copy(),s.outputBytes(),0x10000));
        }}
    }
    @Test void equalReaderRangesShareImmutableBytesAndActualUnreadTailsRemainPresent()throws Exception {
        for(var a:actual().all()){var r=CdR13SectionSources.fromAssets(a);for(var s:r.sources()){
            var canonical=r.view(s.id(),CANONICAL);var java=r.view(s.id(),JAVA);var nativeView=r.view(s.id(),NATIVE);
            if(s.blobRange().equals(s.javaReaderRange()))assertSame(canonical,java);if(s.javaReaderRange().equals(s.nativeReaderRange()))assertSame(java,nativeView);
            byte[] before=java.copy(),copy=java.copy();Arrays.fill(copy,(byte)99);assertArrayEquals(before,java.copy());assertThrows(ReadOnlyBufferException.class,()->java.buffer().put(0,(byte)1));assertThrows(UnsupportedOperationException.class,()->s.nativeLabels().clear());
        }
            var level=r.source(CdR13SectionSources.SourceId.ART_LEVEL_TILES);assertEquals(a.view(CdR13FutureAssets.Asset.ART_LEVEL_TILES).length(),level.blobRange().length());
            if(a.identity().file()==CdNativeFile.R13D)assertEquals(1,level.blobRange().length()-level.expectedJavaConsumedBytes());
        }
    }
    @Test void onlyActualOwnerIdentityAndItsOriginalImageCanBindTheRegistry()throws Exception {
        var f=actual();var r=CdR13SectionSources.fromAssets(f.good());assertTrue(r.ownsAssets(f.good()));assertFalse(r.ownsAssets(f.sameGood()));assertFalse(r.ownsAssets(f.bad()));assertFalse(r.ownsAssets(null));assertEquals(f.good().identity(),f.sameGood().identity());
        assertThrows(NullPointerException.class,()->CdR13SectionSources.fromAssets(null));var wrong=spy(f.good());doReturn(f.d()).when(wrong).originalImage();assertThrows(IllegalArgumentException.class,()->CdR13SectionSources.fromAssets(wrong));
        assertThrows(IllegalArgumentException.class,()->r.source(0x23FFFFL));assertThrows(IllegalArgumentException.class,()->r.sourceForNativeLabel("unknown"));assertThrows(NullPointerException.class,()->r.source((CdR13SectionSources.SourceId)null));assertThrows(NullPointerException.class,()->r.view(CdR13SectionSources.SourceId.ART_POINTS,null));
    }
    @Test void publicFactoriesNeverAcceptRawImagesAddressesRangesOrClaimedIdentities() {
        assertTrue(Modifier.isFinal(CdR13SectionSources.class.getModifiers()));for(var c:CdR13SectionSources.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(c.getModifiers()));
        var factories=Arrays.stream(CdR13SectionSources.class.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers())&&Modifier.isStatic(m.getModifiers())).toList();assertEquals(1,factories.size());assertEquals("fromAssets",factories.getFirst().getName());assertArrayEquals(new Class<?>[]{CdR13FutureAssets.class},factories.getFirst().getParameterTypes());
        for(var m:CdR13SectionSources.class.getDeclaredMethods())if(Modifier.isPublic(m.getModifiers()))assertNotEquals(CdMmdImage.class,m.getReturnType());
    }
    private static void decodeAll(CdAssetBytes bytes,long address,CdAssetBytes seed){var s=CdNemesisPlcDecoder.prepare(bytes,address,seed);while(s.remainingTiles()!=0)s=CdNemesisPlcDecoder.decodeTile(s).state();}
    record Fixture(CdR13FutureAssets good,CdR13FutureAssets bad,CdR13FutureAssets sameGood,CdMmdImage c,CdMmdImage d){List<CdR13FutureAssets> all(){return List.of(good,bad);}}
    private static Fixture retained;
    static synchronized Fixture actual()throws Exception{if(retained!=null)return retained;String root=System.getProperty("soniccd.media.root");assumeTrue(root!=null&&!root.isBlank(),"Optional original CD media is absent");Path cue;try(var paths=Files.list(Path.of(root).resolve("USA"))){var cues=paths.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}try(var p=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){retained=new Fixture(CdR13FutureAssets.fromPrograms(p,CdStagePayload.R13C.stage()),CdR13FutureAssets.fromPrograms(p,CdStagePayload.R13D.stage()),CdR13FutureAssets.fromPrograms(p,CdStagePayload.R13C.stage()),p.mmd(CdNativeFile.R13C),p.mmd(CdNativeFile.R13D));}return retained;}
    static String hash(byte[] b){return CdR11aProfile.sha256(b);}
}
