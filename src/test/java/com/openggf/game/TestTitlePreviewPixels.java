package com.openggf.game;

import com.openggf.tests.RomTestUtils;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.PhysicalImage;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomImageCatalogue;
import com.openggf.game.preview.TitlePreviewProvider.Sequence;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Pixel/clock characterization from d7732f78e, before the title-preview ownership migration. */
class TestTitlePreviewPixels {
    @ParameterizedTest
    @EnumSource(MasterTitleScreen.GameEntry.class)
    void preservesEveryAnimatedPixelAndFrameToken(MasterTitleScreen.GameEntry game) throws Exception {
        File file = switch (game) {
            case SONIC_1 -> RomTestUtils.ensureSonic1RomAvailable();
            case SONIC_2 -> RomTestUtils.ensureSonic2RomAvailable();
            case SONIC_3K -> RomTestUtils.ensureSonic3kRomAvailable();
        };
        assumeTrue(file != null && file.isFile(), "Required ROM is unavailable: " + game);
        String romHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(file.toPath())));
        String expectedRom = switch (game) {
            case SONIC_1 -> "69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b";
            case SONIC_2 -> "8bca5dcef1af3e00098666fd892dc1c2a76333f9";
            case SONIC_3K -> "cfbf98c36c776677290a872547ac47c53d2761d6";
        };
        assumeTrue(expectedRom.equals(romHash), "Characterization requires the verified stock revision: " + game);
        var sequence = MasterTitleRomPreview.loadSequenceFor(game, file.toPath()).orElseThrow();
        String expected = switch (game) {
            case SONIC_1 -> "10fb88697df4c8aaa7c063c9e37689770fe3161901fdac55523fc68254900f74";
            case SONIC_2 -> "9387b37c7b9971712138b98b6c431af91d5901957264f92107fa7b35dd90dad6";
            case SONIC_3K -> "d718792c2bb3e7061d595d66985f0df5c6bc292bf18a0914dbf8e4468e755d6d";
        };
        assertEquals(expected, fingerprint(sequence), game + " pixel/token digest");
    }

    static String fingerprint(Sequence sequence) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(ByteBuffer.allocate(12).putInt(sequence.width()).putInt(sequence.height()).putInt(sequence.stillTick()).array());
        // Covers entry, all sprite frames, settled animation, two S3K loops and negative clamping.
        for (int tick = -1; tick <= 336; tick++) {
            digest.update(ByteBuffer.allocate(4).putInt(sequence.frameTokenAt(tick)).array());
            digest.update(sequence.imageAt(tick).rgba());
        }
        digest.update(ByteBuffer.allocate(4).putInt(sequence.frameTokenAt(10_000)).array());
        digest.update(sequence.imageAt(10_000).rgba());
        return HexFormat.of().formatHex(digest.digest());
    }

    @ParameterizedTest
    @EnumSource(MasterTitleScreen.GameEntry.class)
    void stockCardIgnoresPublishedSessionAndReadsDetachedLogicalViews(MasterTitleScreen.GameEntry game) throws Exception {
        File file = switch (game) {
            case SONIC_1 -> RomTestUtils.ensureSonic1RomAvailable();
            case SONIC_2 -> RomTestUtils.ensureSonic2RomAvailable();
            case SONIC_3K -> RomTestUtils.ensureSonic3kRomAvailable();
        };
        assumeTrue(file != null && file.isFile(), "Required ROM is unavailable: " + game);
        Sequence fileSequence = MasterTitleRomPreview.loadSequenceFor(game, file.toPath()).orElseThrow();
        byte[] bytes = Files.readAllBytes(file.toPath());
        byte[] padded = new byte[bytes.length + 32];
        System.arraycopy(bytes, 0, padded, 16, bytes.length);
        var window = RomByteReader.fromBytes(padded).window(16, bytes.length);
        int seam = bytes.length / 2 + 1;
        var composite = RomByteReader.concat(window.window(0, seam), window.window(seam, bytes.length - seam));
        GameModule foreignModule = mock(GameModule.class);
        when(foreignModule.getTitlePreviewProvider()).thenThrow(new AssertionError("Live module must not supply stock previews"));
        WorldSession world = mock(WorldSession.class);
        Sequence detached;
        try (var sessions = mockStatic(SessionManager.class);
             var liveServices = mockStatic(GameServices.class);
             Rom rom = Rom.fromReader(composite, "logical preview test")) {
            sessions.when(SessionManager::getCurrentWorldSession).thenReturn(world);
            sessions.when(SessionManager::requireCurrentGameModule).thenReturn(foreignModule);
            assertSame(foreignModule, GameModuleRegistry.getCurrent(), "fixture exposes a published foreign session");
            detached = MasterTitleRomPreview.loadSequenceFor(game, rom).orElseThrow();
            liveServices.verifyNoInteractions();
        }
        assertEquals(fingerprint(fileSequence), fingerprint(detached), "all frames remain usable after the ROM closes");
    }

    @Test
    void catalogueCompositeS3kPreservesEveryPreviewFrame() throws Exception {
        File file = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(file != null && file.isFile(), "S3K ROM is unavailable");
        var catalogue = new RomImageCatalogue(List.of(PhysicalImage.probe(file.toPath())), Map.of(), true);
        var resolution = catalogue.resolve(RomIdentity.S3K).orElseThrow();
        assertEquals(RomImageCatalogue.Source.COMPOSITE, resolution.source());
        assertTrue(resolution.wholeImagePath().isEmpty());
        Sequence logical;
        try (Rom rom = Rom.fromReader(catalogue.open(resolution.rom()), resolution.describe())) {
            logical = MasterTitleRomPreview.loadSequenceFor(MasterTitleScreen.GameEntry.SONIC_3K, rom).orElseThrow();
        }
        var direct = MasterTitleRomPreview.loadSequenceFor(MasterTitleScreen.GameEntry.SONIC_3K, file.toPath()).orElseThrow();
        assertEquals(fingerprint(direct), fingerprint(logical));
    }
}
