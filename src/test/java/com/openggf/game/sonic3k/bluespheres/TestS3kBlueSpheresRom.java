package com.openggf.game.sonic3k.bluespheres;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.competition.NativeMenuRaster;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageDataLoader;
import com.openggf.graphics.PaletteFadePresentation;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Blue Spheres stage generation and screens against the locked-on S3&K ROM. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kBlueSpheresRom {
    private RomByteReader rom;
    private S3kBlueSpheresAssets assets;

    @BeforeEach
    void open() throws Exception {
        rom = RomByteReader.fromRom(GameServices.rom().getRom());
        assets = new S3kBlueSpheresAssets(rom);
    }

    @AfterEach
    void clear() {
        TestEnvironment.resetAll();
    }

    @Test
    void quadrantTableMirrorsTheRightAndBottomPieces() {
        // word_87BA: (step, start, row step, offset) per quadrant.
        int[] expected = {1, 0, 0x10, 0, -1, 0x0F, 0x10, 0x10, 1, 0xF0, -0x10, 0x200, -1, 0xFF, -0x10, 0x210};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], rom.readS16BE((int) S3kBlueSpheresStage.QUADRANT_TABLE + i * 2), "word " + i);
        }
    }

    @Test
    void generatedStageTilesFourMirroredPiecesAndSumsTheirRings() throws Exception {
        var loader = new Sonic3kSpecialStageDataLoader(GameServices.rom().getRom());
        byte[] pieces = loader.getCompressedLayoutSet(1);
        assertEquals(0x100 + 128 * 0x100, pieces.length, "ring counts, difficulties and 128 pieces");
        int[] seed = S3kBlueSpheresLevels.stageSeed(1234);
        S3kBlueSpheresStage stage = S3kBlueSpheresStage.build(GameServices.rom().getRom(), loader, seed);
        byte[] layout = stage.layout();
        assertEquals(0x408, layout.length);
        for (int row = 0; row < 16; row++) {
            for (int col = 0; col < 16; col++) {
                assertEquals(piece(pieces, seed[0], row, col), layout[row * 32 + col]);
                assertEquals(piece(pieces, seed[1], row, 15 - col), layout[row * 32 + 16 + col]);
                assertEquals(piece(pieces, seed[2], 15 - row, col), layout[(16 + row) * 32 + col]);
                assertEquals(piece(pieces, seed[3], 15 - row, 15 - col), layout[(16 + row) * 32 + 16 + col]);
            }
        }
        int rings = 0;
        int difficulty = 0;
        for (int piece : seed) {
            rings += pieces[piece] & 0xFF;
            difficulty += pieces[0x80 + piece] & 0xFF;
        }
        assertEquals(rings, stage.ringsLeft());
        assertEquals(difficulty & 0xFF, stage.targetDifficulty());
        assertEquals(0x4000, word(layout, 0x400), "Special_stage_angle");
        assertEquals(0x1000, word(layout, 0x402), "Special_stage_X_pos");
        assertEquals(0x0300, word(layout, 0x404), "Special_stage_Y_pos");
        assertEquals(rings, word(layout, 0x406), "Special_stage_rings_left");
    }

    @Test
    void lockOnSerialTableAcceptsBothSonic1Revisions() {
        byte[] table = rom.slice((int) S3kBlueSpheresLockOn.SERIALS_TEXT, 13 * 4);
        assertTrue(S3kBlueSpheresLockOn.serialMatches(table, "GM 00004049-01".getBytes()));
        assertTrue(S3kBlueSpheresLockOn.serialMatches(table, "GM 00001009-00".getBytes()));
        assertFalse(S3kBlueSpheresLockOn.serialMatches(table, "GM 00001051-01".getBytes()));
    }

    @Test
    void titleOpensTheMenuOnAbcAndAcceptsItsOwnCode() throws Exception {
        var session = new S3kBlueSpheresSession();
        List<Integer> sounds = new ArrayList<>();
        boolean[] started = new boolean[1];
        var title = new S3kBlueSpheresTitle(assets, session, new S3kBlueSpheresTitle.Events() {
            @Override public void playSfx(int sfx) { sounds.add(sfx); }
            @Override public void startStage() { started[0] = true; }
        });
        var palette = new S3kBlueSpheresPalette();
        var vram = titleVram();
        title.setUp(palette);
        palette.copyTargetToNormal();
        dump("title-no-way", title.scene(vram, palette));
        assertEquals(0, session.menuFlag, "NO WAY! waits for a button");
        title.update(S3kBlueSpheresTitle.START, 0);
        assertEquals(0, session.menuFlag, "holding a button is not a new press");

        // Intentional discrepancy: any single press opens the menu (the ROM needs A+B+C).
        title.update(S3kBlueSpheresTitle.B, S3kBlueSpheresTitle.B);
        assertEquals(1, session.menuFlag);
        assertEquals(1, session.option, "the full game starts on START");
        assertTrue(sounds.contains(S3kBlueSpheresTitle.SFX_STARPOST));
        for (int i = 0; i < 20; i++) title.update(0, 0);
        dump("title-menu", title.scene(vram, palette));

        // Enter the code for level 5 digit by digit, then press Start.
        long code = S3kBlueSpheresLevels.encodeCode(4, false);
        int[] digits = S3kBlueSpheresLevels.codeDigits(code);
        title.update(0, S3kBlueSpheresTitle.DOWN);
        assertEquals(0x81, session.menuFlag, "the code row takes the cursor");
        for (int place = 0; place < 12; place++) {
            for (int n = 0; n < digits[place]; n++) title.update(0, S3kBlueSpheresTitle.DOWN);
            title.update(0, S3kBlueSpheresTitle.RIGHT);
        }
        dump("title-code", title.scene(vram, palette));
        title.update(0, S3kBlueSpheresTitle.START);
        assertEquals(4, session.level, "a valid code selects its level");
        assertEquals(1, session.menuFlag);
        assertFalse(started[0], "Start inside code entry checks the code, it does not start");
        title.update(0, S3kBlueSpheresTitle.START);
        assertTrue(started[0], "Start on START begins the stage");
    }

    @Test
    void resultsCountDownThenLeaveOnAPress() throws Exception {
        var session = new S3kBlueSpheresSession();
        session.menuFlag = 1;
        session.seed = new int[] {0, 1, 2, 3};
        session.ringsLeft = 5;
        boolean[] left = new boolean[1];
        var vram = S3kBlueSpheresAssets.emptyVram();
        S3kBlueSpheresAssets.load(vram, S3kBlueSpheresAssets.TILE_MISC, assets.kosinski(S3kBlueSpheresAssets.ART_KOS_NORMAL_TEXT));
        S3kBlueSpheresAssets.load(vram, S3kBlueSpheresAssets.TILE_TITLE_TEXT, assets.nemesis(S3kBlueSpheresAssets.ART_NEM_CREDITS_TEXT));
        S3kBlueSpheresAssets.load(vram, S3kBlueSpheresAssets.TILE_BG, assets.nemesis(S3kBlueSpheresAssets.ART_NEM_SK_LOGO));
        byte[] general = assets.kosinski(S3kBlueSpheresAssets.ART_KOS_RESULTS_GENERAL);
        S3kBlueSpheresAssets.load(vram, S3kBlueSpheresAssets.TILE_EMERALDS, general);
        var results = new S3kBlueSpheresResults(assets, session, new S3kBlueSpheresResults.Events() {
            @Override public void playSfx(int sfx) { }
            @Override public void leave() { left[0] = true; }
        }, vram);
        var palette = new S3kBlueSpheresPalette();
        results.setUp(palette);
        palette.copyTargetToNormal();
        for (int i = 0; i < 40; i++) results.update(0);
        dump("results", results.scene(palette));
        assertFalse(left[0]);
        results.update(S3kBlueSpheresTitle.C);
        assertTrue(left[0], "an imperfect clear leaves on any press");
    }

    private com.openggf.level.Pattern[] titleVram() throws Exception {
        var bank = S3kBlueSpheresAssets.emptyVram();
        S3kBlueSpheresAssets.load(bank, S3kBlueSpheresAssets.TILE_TITLE_TEXT, assets.nemesis(S3kBlueSpheresAssets.ART_NEM_CREDITS_TEXT));
        S3kBlueSpheresAssets.load(bank, S3kBlueSpheresAssets.TILE_BG, assets.nemesis(S3kBlueSpheresAssets.ART_NEM_SK_LOGO));
        byte[] a = assets.kosinski(S3kBlueSpheresAssets.ART_KOS_CHAR);
        byte[] b = assets.kosinski(S3kBlueSpheresAssets.ART_KOS_CHAR_SPRITE);
        byte[] joined = new byte[a.length + b.length];
        System.arraycopy(a, 0, joined, 0, a.length);
        System.arraycopy(b, 0, joined, a.length, b.length);
        S3kBlueSpheresAssets.load(bank, 0, joined);
        byte[] text = assets.kosinski(S3kBlueSpheresAssets.ART_KOS_NORMAL_TEXT);
        S3kBlueSpheresAssets.load(bank, S3kBlueSpheresAssets.TILE_MISC, java.util.Arrays.copyOf(text, Math.min(text.length, 0xDA0)));
        return bank;
    }

    private static void dump(String name, S3kBlueSpheresScene scene) throws Exception {
        NativeMenuRaster.Picture picture = scene.rasterize();
        int[] rgb = picture.rgb(scene.cram(), new PaletteFadePresentation(),
                DisplayColorProfile.RAW_RGB, DisplayColorExpansion.FULL);
        BufferedImage image = new BufferedImage(NativeMenuRaster.WIDTH, NativeMenuRaster.HEIGHT, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, NativeMenuRaster.WIDTH, NativeMenuRaster.HEIGHT, rgb, 0, NativeMenuRaster.WIDTH);
        File dir = new File("target/blue-spheres");
        dir.mkdirs();
        ImageIO.write(image, "png", new File(dir, name + ".png"));
    }

    private static byte piece(byte[] pieces, int piece, int row, int col) {
        return pieces[0x100 + (piece & 0x7F) * 0x100 + row * 16 + col];
    }

    private static int word(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }
}
