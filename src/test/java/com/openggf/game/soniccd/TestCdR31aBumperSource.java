package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.Path;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

/** UNRUN. Genuine selected Boot/source ingress; no body, terrain, art or live-admission claim. */
@Isolated class TestCdR31aBumperSource {
    @TempDir Path directory;
    private final TestSonicCdR31aSelectedProfileLoading fixture = new TestSonicCdR31aSelectedProfileLoading();
    @BeforeEach void setup() { fixture.directory = directory; fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void originalSourceRetainsLiteralMappingAndFdAnimationIncludes() throws Exception {
        try (var b = fixture.start(1, 0x80)) {
            var assets = assets(b);
            var source = CdR31aBumperSource.fromAssets(assets);
            assertTrue(source.owns(assets));
            assertFalse(source.owns(null));
            assertSame(source.data(), source.data());
            assertSame(source,CdR31aBumperSource.fromAssets(assets));
            assertSame(source.assets(),CdR31aBumperSource.fromAssets(assets).assets());
            assertEquals(0x20AD9EL, assets.view(CdR31aAssets.Asset.OBJECT_INDEX).unsignedLong((0x1D - 1) * 4));
            assertEquals(0x20B0AA, source.data().mappingAddress());
            // Independent literal dc.w/dc.b oracle, N sprites/r3/bumper.asm:5-27.
            assertEquals("0006000c001201f00f0000f001f40a0010f406ec040019f0f40a001be80c041019f0ec04081900f40a081b000c04181900",
                    HexFormat.of().formatHex(source.data().visual().mappings().copy()));
            var table = source.data().animation().orElseThrow();
            // N anims/r3/bumper.asm:5-18; real EVEN byte and FD operand retained.
            assertEquals("000400080f00ff000401020102fd00", HexFormat.of().formatHex(table.bytes().copy()));
            assertEquals(2, table.scriptCount());
            assertEquals(0, table.tableOffset());
        }
    }

    @Test void sharedMapperHasOnlyThreeOriginalBumperFramesAndNoPointerAlias() throws Exception {
        try (var b = fixture.start(1, 0x80)) {
            var assets = assets(b);
            var mapper = CdObjectMappings.fromR31aAssets(assets, CdR31aObjectAssets.fromAssets(assets));
            assertEquals(1, mapper.resolve(0x20B0AA, false, 0).pieces().size());
            assertEquals(1, mapper.resolve(0x20B0AA, false, 1).pieces().size());
            assertEquals(6, mapper.resolve(0x20B0AA, false, 2).pieces().size());
            assertThrows(UnsupportedOperationException.class, () -> mapper.resolve(0x20B0AA, false, 3));
            assertThrows(UnsupportedOperationException.class, () -> mapper.resolve(0xFF20B0AA, false, 0));
            assertThrows(UnsupportedOperationException.class, () -> mapper.resolve(0x20B0EA, false, 0)); // Flipper not registered.
        }
    }

    @Test void originalFdOperandUsesExistingAnimatorWithoutConvenienceDecoderExtension() throws Exception {
        try (var b = fixture.start(1, 0x80)) {
            var table = CdR31aBumperSource.fromAssets(assets(b)).data().animation().orElseThrow();
            // Constructed cursor at the actual FD terminator, not a live producer.
            var before = new CdObjectAnimation.State(1, 1, 4, 0, 2, 4, 0, 2, 0);
            var after = CdObjectAnimation.step(before, table);
            assertEquals(0, after.animationId());
            assertEquals(2, after.mapFrame());
            assertEquals(2, after.routine());
        }
    }

    @Test void allRequiredP3DataMatchesIndependentNativeLiteralHashes() throws Exception {
        try(var b=fixture.start(1,0x80)){
            var a=CdR31aBumperSource.fromAssets(assets(b)).assets();
            // Expected hashes were independently evaluated from pinned native dc declarations,
            // not copied from the Java profile and never supplied as runtime fallback bytes.
            assertEquals("1457505865e8ace2f1ddd839b79de427dadb0e81f1fd6f6681463a56287ab0fc", sha(a.bumper().visual().mappings().copy()), "BUMPER_MAPPINGS");
            assertEquals("76f66835672139075de3eda4fbfb6fd0a23eed4f3acf34ee7c99365ae70bc5a2", sha(a.flipper().visual().mappings().copy()), "FLIPPER_MAPPINGS");
            assertEquals("a00c6e3c1cda44f0d81c30948f442893df31a13aa304db508740f8e0ce9f6992", sha(a.glass().visual().mappings().copy()), "GLASS_BREAK_MAPPINGS");
            assertEquals("9f3285bf8f25646ee95178fb2b4d832affec6a41a1d6f0b4bffb842d58c9e51c", sha(a.platform().visual().mappings().copy()), "BUMPER_PLATFORM_MAPPINGS");
            assertEquals("7c0e87ca5ef5fb7457c7d4305789b38c45968abbebb8978a028e86d7979bb0d4", sha(a.pocketParent().visual().mappings().copy()), "POCKET_1_MAPPINGS");
            assertEquals("724f36d2c178f081557e68d20cab6a65c33a948d97ebacba2a02d75348c14534", sha(a.pocketChild().visual().mappings().copy()), "POCKET_2_MAPPINGS");
            assertEquals("da522ac3e6ecd46d6f1a334bfa8f3a6c8ea2a1f9db88cc829a8bf89a69bc0739", sha(a.bumper().animation().orElseThrow().bytes().copy()), "BUMPER_ANIMATIONS");
            assertEquals("6f9574db4542aee9a5e545fbbc958d1c2c37c1e1a994784f84c66f0cd096d408", sha(a.flipper().animation().orElseThrow().bytes().copy()), "FLIPPER_ANIMATIONS");
            assertEquals("0475253553c44e33ad81495d235e83fe632c3a7b74c54e284c4d0ca9c44b962b", sha(a.glass().animation().orElseThrow().bytes().copy()), "GLASS_BREAK_ANIMATIONS");
            assertEquals("afeed3ae14c968c5a47925c5f2c76941f6034534aba04af7e65091fa44039f8a", sha(a.pocketChild().animation().orElseThrow().bytes().copy()), "POCKET_ANIMATIONS");
            assertEquals("1523ac8274d5ddd7908400056722ae9121ac77d7d58fce268e3686c90a2f34dd", sha(a.flipperSlopeReadHull().copy()), "FLIPPER_SLOPE_READ_HULL");
        }
    }
    private static String sha(byte[] bytes) throws Exception {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}

    private static CdR31aAssets assets(TestSonicCdR31aSelectedProfileLoading.Booted b) {
        return ((CdGameDataSource) b.mode.getWorldSession().getDataSource()).r31aAssets().orElseThrow();
    }
}
