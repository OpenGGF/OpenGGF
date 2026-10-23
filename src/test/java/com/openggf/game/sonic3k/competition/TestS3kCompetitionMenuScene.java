package com.openggf.game.sonic3k.competition;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.level.render.SpriteMappingPieces;
import com.openggf.physics.TrigLookupTable;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static com.openggf.game.sonic3k.competition.S3kCompetitionSession.Arena;
import static com.openggf.game.sonic3k.competition.S3kCompetitionSession.Mode;
import static com.openggf.game.sonic3k.competition.S3kCompetitionScreen.Phase;
import static org.junit.jupiter.api.Assertions.*;

/** Native ROM table assertions and state-driven composition; no GL or font fallback. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCompetitionMenuScene {
    private Rom rom;
    private RomByteReader reader;
    private S3kCompetitionAssets assets;
    private S3kCompetitionMenuScene composer;
    private S3kCompetitionRecords records;

    @BeforeEach void loadActualRom() throws IOException {
        rom = GameServices.rom().getRom(); reader = RomByteReader.fromRom(rom);
        assets = new S3kCompetitionAssets(rom); composer = new S3kCompetitionMenuScene(rom, assets);
        // Deliberately distinct, caller-supplied records in physical zone order.
        byte[] block = new byte[80];
        for (int zone = 0; zone < 5; zone++) {
            for (int rank = 0; rank < 3; rank++) {
                long packed = S3kCompetitionRecords.packTime(zone + 1, 12 + rank, 34 + rank);
                for (int i = 0; i < 4; i++) block[zone * 16 + rank * 4 + i] = (byte) (packed >>> (24 - i * 8));
                block[zone * 16 + 12 + rank] = (byte) rank;
            }
            block[zone * 16 + 15] = (byte) (0xA0 + zone);
        }
        records = new S3kCompetitionRecords(block);
    }

    @Test void owningRoutineOperandsAndEntireNativeCopyAndObjectRecordsAreVerified() throws Exception {
        int[][] lea = {{0xA982,0xAB48},{0xAC86,0xB0F4},{0xAD1C,0xB1F0},{0xAD26,0xB282},
                {0xB400,0xB6C8},{0xB80C,0xBBAC},{0xB8AE,0xBB4A},{0xBCAE,0xBF96},
                {0xBCC2,0xBFF2},{0xBDE2,0xBF58}};
        for (int[] pair : lea) assertEquals(pair[1], pair[0] + 2 + reader.readS16BE(pair[0] + 2));
        int[][] spans = {{0xAB48,72},{0xB0F4,252},{0xB1F0,146},{0xB282,74},{0xB6C8,134},
                {0xBB4A,98},{0xBBAC,92},{0xBF58,62},{0xBF96,92},{0xBFF2,20}};
        String[] hashes = {
                "bb356b41ec454150ad35f8a8a80bacf82239f79c3efca674f35c070542031ce9",
                "26b3d3e51d3dae3d8aace7e7b24893d1a8eb2665aa69140cbf8f892e6e7128c6",
                "b3daf9bbece9c5936131a14454da0696dca451667674f21842e736cd4c55add0",
                "f7f9dcb0c191108d00295d12b12035b05087e30bf8c0c6affb68978e2edd7737",
                "f1d75ac3c794e3440b0d7a63102c1f456fe80bd939cd58ea132a5d76f4b4c1f4",
                "5d5d2bdf2458f9df9957d93da8c6598fc45c7f5a47a1a028922d2ca1162deb08",
                "a653d99b6ac4c130b63aa7f2ab3636e1a67eb390d0cfadce7674190efee76a30",
                "18f8bbb89b9a08382f2a7860cab9f90d97f6e92f847d456940cc0183fb27aa4d",
                "a46f1133c008ed2883782d8db0760411fc7eb9b8a63fa76a4817af68de4b06af",
                "3de01cb061c7c8ba73f18f0cb70dc6b201cdf4b22dfddfd05cae7d1420468fea"};
        for (int i = 0; i < spans.length; i++) assertEquals(hashes[i], HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(rom.readBytes(spans[i][0],spans[i][1]))));
        // GetSineCosine's two PC-indexed operands resolve to the locked-on table, not its S3 duplicate.
        assertEquals(0x323B,reader.readU16BE(0x1D56));
        assertEquals(0x1D64,0x1D58 + (byte) reader.readU8(0x1D59));
    }

    @Test void modeMenuUsesNativePaletteSelectionAndBlinkingItemsArrow() throws IOException {
        var on = composer.compose(view(Phase.MODE,8,0,true,Mode.GRAND_PRIX,0,1,0,0,null),records);
        assertEquals(7,on.sprites().size());
        assertEquals(0x434A,group(on,0).nativeArtWord());
        assertEquals(0x234A,group(on,1).nativeArtWord());
        assertEquals(0x34A,group(on,4).nativeArtWord());
        assertEquals(0x94,group(on,0).x()); assertEquals(0x20,group(on,0).y());
        assertEquals(0xDC,group(on,6).x()); assertEquals(0x28,group(on,6).y());
        assertFalse(on.shadowHighlight()); assertEquals(0x30,on.backdropColorIndex());
        var off = composer.compose(view(Phase.MODE,8,1,false,Mode.MATCH_RACE,0,1,0,0,null),records);
        assertEquals(0xFC,group(off,6).x()); assertEquals(0x434A,group(off,1).nativeArtWord());
        assertEquals(6,composer.compose(view(Phase.MODE,0,0,true,Mode.GRAND_PRIX,0,1,0,0,null),records).sprites().size());
        assertEquals(6,composer.compose(view(Phase.MODE,8,2,true,Mode.TIME_ATTACK,0,1,0,0,null),records).sprites().size());
        assertEquals(6,composer.compose(view(Phase.MODE,8,3,true,Mode.GRAND_PRIX,0,1,0,0,null),records).sprites().size());
    }

    @Test void planesUseNativeStrideFillBackgroundAndPatternBankWithoutGraphics() throws IOException {
        var scene = composer.compose(view(Phase.MODE,0,0,true,Mode.GRAND_PRIX,0,1,0,0,null),records);
        int[] background = assets.menuBackground(S3kCompetitionAssets.MenuPhase.MODE);
        for (int y = 0; y < 28; y++) for (int x = 0; x < 40; x++)
            assertEquals(background[y * 40 + x],scene.planeBTile(x,y));
        assertEquals(0,scene.planeBTile(40,0)); assertEquals(0,scene.planeBTile(0,28));
        assertEquals(0x8000,scene.planeATile(0,0));
        assertEquals(4096,scene.planeA().length); assertEquals(4096,scene.planeB().length);
        assertEquals(2048,scene.patterns().length); assertEquals(4,scene.palettes().length);
        assertEquals(0,scene.horizontalScrollB()); assertEquals(0,scene.verticalScrollB());
    }

    @Test void characterWheelUsesRomSineArithmeticShiftsNativeSlotsAndDepth() throws IOException {
        var scene = composer.compose(view(Phase.PLAYER_SELECT,8,0,true,Mode.GRAND_PRIX,0,2,0,0,null),records);
        var sonic = group(scene,3); // ObjDat_B6C8 begins at Player_2, not Object_RAM.
        assertEquals(0xA5,sonic.x()); assertEquals(0x5D,sonic.y()); assertEquals(0,sonic.mappingFrame());
        assertEquals(0x80,sonic.drawPriority()); assertEquals(0xA5C9,sonic.nativeArtWord());
        assertEquals(0x80,sonic.nativePriorityWord()); assertEquals(1,sonic.priorityBucket());
        assertTrue(sonic.pieces().stream().allMatch(p -> p.priority() && p.paletteIndex() == 1));
        var knuckles = group(scene,4); assertEquals(2,knuckles.mappingFrame());
        assertEquals(0x97,knuckles.x()); assertEquals(0x51,knuckles.y());
        assertEquals(0x100,knuckles.drawPriority()); assertEquals(0x25C9,knuckles.nativeArtWord());
        var tails = group(scene,5); assertEquals(1,tails.mappingFrame()); assertEquals(0x180,tails.drawPriority());
        assertTrue(scene.shadowHighlight()); assertEquals(0,scene.backdropColorIndex());
        for (int i = 1; i < scene.sprites().size(); i++) assertTrue(
                scene.sprites().get(i-1).drawPriority() <= scene.sprites().get(i).drawPriority());
        var turning = new S3kCompetitionScreen.MenuView(Phase.PLAYER_SELECT,9,0,true,Mode.GRAND_PRIX,0,2,
                List.of(1,1),List.of(0x45,0x95),List.of(false,false),0,0,null);
        assertEquals(0xA3,group(composer.compose(turning,records),3).x());
        assertEquals(0x5C,group(composer.compose(turning,records),3).y());
    }

    @Test void screenDepthUsesTheSameNativeSineWordsForEveryAngle() {
        for (int angle = 0; angle < 256; angle++) assertEquals(reader.readS16BE(0x1D64+angle*2),
                TrigLookupTable.sinHex(angle),"native wheel sine angle "+angle);
    }

    @Test void nativeQueueWordsAndCurrentArtDepthAreIndependentAndCompositionHasNoHistory() throws IOException {
        var queued = new S3kCompetitionScreen.MenuView(Phase.PLAYER_SELECT,17,0,true,Mode.GRAND_PRIX,0,2,
                List.of(1,1),List.of(0x95,0x95),List.of(false,false),0,0,null,
                List.of(0x80,0x100,0x180,0x100,0x180,0x80));
        var scene = composer.compose(queued,records);
        assertEquals(0x80,group(scene,3).nativePriorityWord()); assertEquals(0x25C9,group(scene,3).nativeArtWord());
        assertEquals(0x180,group(scene,5).nativePriorityWord()); assertEquals(0xA5C9,group(scene,5).nativeArtWord());
        var initial = new S3kCompetitionScreen.MenuView(Phase.PLAYER_SELECT,0,0,true,Mode.GRAND_PRIX,0,2,
                List.of(0,1),List.of(0x40,0x95),List.of(false,false),0,0,null,List.of(0,0,0,0,0,0));
        var initialScene = composer.compose(initial,records);
        assertTrue(initialScene.sprites().stream().allMatch(g -> g.nativePriorityWord() == 0));
        var forward = composer.compose(queued,records);
        assertEquals(scene.sprites(),forward.sprites()); assertArrayEquals(scene.planeA(),forward.planeA());
    }

    @Test void soloPlayerSelectRetainsGhostLabelAndNativePressStartBlink() throws IOException {
        var hidden = composer.compose(view(Phase.PLAYER_SELECT,0,0,true,Mode.GRAND_PRIX,0,1,0,0,null),records);
        assertEquals(0,group(hidden,7).mappingFrame());
        assertTrue(hidden.sprites().stream().noneMatch(g -> g.nativeSlot() >= 8));
        var blink = composer.compose(view(Phase.PLAYER_SELECT,32,0,true,Mode.GRAND_PRIX,0,1,0,0,null),records);
        assertEquals(5,group(blink,11).mappingFrame()); assertEquals(0xA1,group(blink,11).x());
        var joined = composer.compose(view(Phase.PLAYER_SELECT,32,0,true,Mode.GRAND_PRIX,0,2,0,0,null),records);
        assertTrue(joined.sprites().stream().anyMatch(g -> g.nativeSlot() == 8));
        assertTrue(joined.sprites().stream().noneMatch(g -> g.nativeSlot() == 11));
    }

    @Test void allFiveArenaCardsUseTheExactRomCopyListReordering() throws IOException {
        var scene = composer.compose(view(Phase.ARENA_SELECT,0,1,true,Mode.MATCH_RACE,0,2,0,0,null),records);
        int[] imageOffsets = {192,144,0,48,96}, nameOffsets = {0,300,75,225,150};
        for (int row = 0; row < 5; row++) {
            assertBlock(scene.planeA(),0x98 + row * 0x480,assets.arenaBorders(),0,27,1);
            assertBlock(scene.planeA(),0x11A + row * 0x480,assets.arenaImages(),imageOffsets[row],8,6);
            assertBlock(scene.planeA(),0x1AC + row * 0x480,assets.arenaNames(),nameOffsets[row],15,5);
            assertBlock(scene.planeA(),0xCE + row * 0x480,assets.rawMenuMaps().get("time_border"),0,12,1);
            assertBlock(scene.planeA(),0x4CE + row * 0x480,assets.rawMenuMaps().get("time_border"),96,12,1);
            assertBlock(scene.planeA(),0x1D0 + row * 0x480,assets.rawMenuMaps().get("best_time"),0,9,2);
        }
    }

    @Test void arenaBestTimesReadPhysicalSaveOrderWhileMenuOrderIsAlzBpzCgzDpzEmz() throws IOException {
        var scene = composer.compose(view(Phase.ARENA_SELECT,8,1,true,Mode.MATCH_RACE,0,2,0,0,null),records);
        int[] minuteUnits = {1,2,4,3,5};
        for (int row = 0; row < 5; row++) {
            assertEquals(0x8000,word(scene,0x2D2 + row * 0x480)); // first minute zero is suppressed.
            assertEquals(reader.readU16BE(0x20A130 + minuteUnits[row]*4),word(scene,0x2D4 + row * 0x480));
            assertEquals(0xA50D,word(scene,0x2D6 + row * 0x480)); // colon.
            assertEquals(0xA50E,word(scene,0x2DC + row * 0x480)); // decimal separator.
        }
    }

    @Test void arenaCursorAndPaletteWaitForBothScrollAxesAndCursorBlinksIndependently() throws IOException {
        for (int row = 0; row < 5; row++) {
            int vertical = Math.clamp(row - 1,0,2)*0x48;
            var scene = composer.compose(view(Phase.ARENA_SELECT,8,1,true,Mode.MATCH_RACE,row,2,-0x58,vertical,null),records);
            assertEquals(row + 6,group(scene,12).mappingFrame()); assertEquals(0xC29F,group(scene,12).nativeArtWord());
            assertEquals(0x30,group(scene,12).x()); assertEquals(row*0x48 + 0x28 - vertical,group(scene,12).y());
            assertEquals(0x74,group(scene,0).x()); assertEquals(row*0x48 + 0x2C - vertical,group(scene,0).y());
            var expected = assets.menuPalette(S3kCompetitionAssets.MenuPhase.ARENA_SELECT,S3kCompetitionSession.GRAND_PRIX_ORDER.get(row));
            for (int i = 0; i < 16; i++) assertEquals(expected[3].colors[i].r,scene.palettes()[3].colors[i].r);
            var blinkOff = composer.compose(view(Phase.ARENA_SELECT,0,1,true,Mode.MATCH_RACE,row,2,-0x58,vertical,null),records);
            assertTrue(blinkOff.sprites().stream().anyMatch(g -> g.nativeSlot() == 12));
            assertTrue(blinkOff.sprites().stream().noneMatch(g -> g.nativeSlot() == 0));
        }
        var sliding = composer.compose(view(Phase.ARENA_SELECT,8,1,true,Mode.MATCH_RACE,2,2,-0x50,0,null),records);
        assertTrue(sliding.sprites().stream().noneMatch(g -> g.nativeSlot() == 12 || g.nativeSlot() == 0));
        var vertical = composer.compose(view(Phase.ARENA_SELECT,8,1,true,Mode.MATCH_RACE,2,2,-0x58,0,null),records);
        assertTrue(vertical.sprites().stream().noneMatch(g -> g.nativeSlot() == 12));
        assertEquals(-0x50,sliding.horizontalScrollA());
    }

    @Test void timeAttackSelectHasOneCenteredWheelAndNoGhostObjects() throws IOException {
        var scene = composer.compose(view(Phase.ARENA_SELECT,8,2,false,Mode.TIME_ATTACK,3,1,-0x58,0x90,null),records);
        assertEquals(7,scene.sprites().size());
        assertEquals(0xF1 - 0x80,group(scene,2).y());
        assertTrue(scene.sprites().stream().noneMatch(g -> g.nativeSlot() >= 6 && g.nativeSlot() != 12));
    }

    @Test void grandPrixComposesFiveTimesPerPlayerWinTilesTotalAndSeparateWinVersusFastestLabels() throws IOException {
        var result = result(Mode.GRAND_PRIX,Arena.ENDLESS_MINE,
                List.of(100,100,100,50000,50000),List.of(200,200,200,200,200));
        var dark = composer.compose(view(Phase.RESULTS,0,0,true,Mode.GRAND_PRIX,0,2,0,0,result),records);
        var lit = composer.compose(view(Phase.RESULTS,8,0,true,Mode.GRAND_PRIX,0,2,-0x88,0,result),records);
        assertBlock(lit.planeA(),0x204,assets.resultsLed(),0,15,6);
        assertBlock(lit.planeA(),0x884,assets.resultsLed(),0,15,6);
        assertBlock(lit.planeA(),0x142,assets.rawMenuMaps().get("results_letters"),0,2,10);
        for (int row = 0; row < 5; row++) {
            String p1 = row < 3 ? "win" : "lose", p2 = row < 3 ? "lose" : "win";
            assertBlock(lit.planeA(),0x148+row*0x100,assets.rawMenuMaps().get(p1),0,2,2);
            assertBlock(lit.planeA(),0x7C8+row*0x100,assets.rawMenuMaps().get(p2),0,2,2);
        }
        assertTrue(dark.sprites().stream().noneMatch(g -> g.nativeSlot() == 3));
        assertEquals(0xB,group(lit,3).mappingFrame()); assertEquals(0xC,group(lit,7).mappingFrame());
        assertEquals(0xA34A,group(lit,3).nativeArtWord());
        assertEquals(3,word(dark,0xCDC) >>> 13 & 3); assertEquals(1,word(lit,0xCDC) >>> 13 & 3);
        assertEquals(1,word(dark,0x65C) >>> 13 & 3); // P1 wins arenas; P2 has the faster total.
        assertEquals(-0x88,lit.horizontalScrollA()); assertEquals(-0x3C,group(lit,3).x());
    }

    @Test void grandPrixTiesRemainStaticAndMinuteOverflowKeepsNativeSecondCentisecondBytes() throws IOException {
        var times = List.of(599999,599999,599999,599999,599999);
        var result = result(Mode.GRAND_PRIX,Arena.ENDLESS_MINE,times,times);
        var scene = composer.compose(view(Phase.RESULTS,0,0,true,Mode.GRAND_PRIX,0,2,0,0,result),records);
        assertEquals(0xD,group(scene,3).mappingFrame()); assertEquals(0xD,group(scene,7).mappingFrame());
        assertBlock(scene.planeA(),0x148,assets.rawMenuMaps().get("tie"),0,2,2);
        // Native sub_BB04 clamps the minute byte, not the entire time: 99:59.95.
        assertEquals(0xA50C,word(scene,0x65A)); assertEquals(0xA50C,word(scene,0x65C));
        assertEquals(0xA50C,word(scene,0x666)); assertEquals(0xA508,word(scene,0x668));
        assertEquals(1,word(scene,0x65A) >>> 13 & 3);
    }

    @Test void grandPrixInitialGlyphPresentationDiffersFromMainLoopClockWrap() throws IOException {
        var result = result(Mode.GRAND_PRIX,Arena.ENDLESS_MINE,
                List.of(100,100,100,100,100),List.of(200,200,200,200,200));
        var initial = new S3kCompetitionScreen.MenuView(Phase.RESULTS,0,0,true,Mode.GRAND_PRIX,0,2,
                List.of(0,1),List.of(0x40,0x95),List.of(false,false),0,0,result,List.of(),false);
        var wrapped = new S3kCompetitionScreen.MenuView(Phase.RESULTS,0,0,true,Mode.GRAND_PRIX,0,2,
                List.of(0,1),List.of(0x40,0x95),List.of(false,false),0,0,result,List.of(),true);
        var beforeWrap = new S3kCompetitionScreen.MenuView(Phase.RESULTS,0xFFFF,0,true,Mode.GRAND_PRIX,0,2,
                List.of(0,1),List.of(0x40,0x95),List.of(false,false),0,0,result,List.of(),true);
        assertEquals(1,word(composer.compose(initial,records),0x65C) >>> 13 & 3);
        assertEquals(1,word(composer.compose(beforeWrap,records),0x65C) >>> 13 & 3);
        assertEquals(3,word(composer.compose(wrapped,records),0x65C) >>> 13 & 3);
        // Compatibility synthetic views represent an admitted loop presentation.
        assertTrue(view(Phase.RESULTS,0,0,true,Mode.GRAND_PRIX,0,2,0,0,result).mainLoopStarted());
    }

    @Test void allTimeAttackResultNamesAndThreeRecordRowsUseNativeZonePointers() throws IOException {
        int[] names = {0x20B3EA,0x20B41E,0x20B486,0x20B452,0x20B4BA};
        for (int zone = 0; zone < 5; zone++) {
            var arena = Arena.forZone(0xE + zone);
            var result = result(Mode.TIME_ATTACK,arena,List.of(101,202,303,404,505),List.of(0,0,0,0,0));
            var scene = composer.compose(view(Phase.TIME_ATTACK_RESULTS,8,2,false,Mode.TIME_ATTACK,0,1,0,0,result),records);
            for (int i = 0; i < 26; i++) assertEquals(reader.readU16BE(names[zone]+i*2),
                    scene.planeA()[(0x826/2)+(i/13)*64+i%13]);
            assertBlock(scene.planeA(),0x144,assets.rawMenuMaps().get("lap"),0,5,10);
            assertBlock(scene.planeA(),0x92C,assets.rawMenuMaps().get("records"),0,7,2);
            assertBlock(scene.planeA(),0x944,assets.rawMenuMaps().get("ranks"),0,3,8);
            for (int rank = 0; rank < 3; rank++) assertEquals(reader.readU16BE(0x20A130+(zone+1)*4),
                    word(scene,0x95C+rank*0x180));
            assertEquals(-0x88,scene.horizontalScrollA()); assertEquals(0,scene.verticalScrollA());
        }
    }

    @Test void newTimeAttackRecordBlinksOnlyItsCharacterAndKnucklesUsesTheNativeExtraPaletteLine() throws IOException {
        var result = result(Mode.TIME_ATTACK,Arena.AZURE_LAKE,List.of(101,202,303,404,505),List.of(0,0,0,0,0));
        var view = view(Phase.TIME_ATTACK_RESULTS,0,2,false,Mode.TIME_ATTACK,0,1,0,0,result);
        var blink = composer.compose(view,records,1);
        var steady = composer.compose(view,records,-1);
        assertEquals(4,blink.sprites().size()); assertEquals(5,steady.sprites().size());
        assertTrue(blink.sprites().stream().noneMatch(g -> g.nativeSlot() == 3));
        assertArrayEquals(steady.planeA(),blink.planeA());
        assertEquals(0x29,group(steady,2).mappingFrame()); assertEquals(0x2A,group(steady,3).mappingFrame());
        var knuckles = group(steady,4); assertEquals(0x2B,knuckles.mappingFrame());
        assertEquals(0xBFD5,knuckles.nativeArtWord()); assertEquals(0xB0,knuckles.x()); assertEquals(0xD0,knuckles.y());
        // The raw $85AF mapping word plus $BFD5 wraps to $4584: the carry
        // clears priority and selects palette 2. Art-word flags alone are insufficient.
        assertTrue(knuckles.pieces().stream().allMatch(p -> p.paletteIndex() == 2 && !p.priority()));
        var raw = assets.menuSheet(S3kCompetitionAssets.MenuPhase.TIME_ATTACK_RESULTS,"competition_results_character",0);
        assertEquals((SpriteMappingPieces.toTileWord(raw.getFrame(0x2B).pieces().getFirst())+0xBFD5)&0xFFFF,
                SpriteMappingPieces.toTileWord(knuckles.pieces().getFirst()));
    }

    @Test void sceneDefensivelyCopiesPlanesPatternsPaletteAndSpriteLists() throws IOException {
        var scene = composer.compose(view(Phase.MODE,8,0,true,Mode.GRAND_PRIX,0,1,0,0,null),records);
        int originalTile = scene.planeBTile(0,0); scene.planeB()[0] = originalTile ^ 0xFFFF;
        assertEquals(originalTile,scene.planeBTile(0,0));
        var first = scene.patterns(); byte pixel = first[1].getPixel(0,0);
        first[1].setPixel(0,0,(byte) (pixel ^ 15)); assertEquals(pixel,scene.patterns()[1].getPixel(0,0));
        var palette = scene.palettes(); byte red = palette[0].colors[1].r;
        palette[0].colors[1].r ^= 0x7F; assertEquals(red,scene.palettes()[0].colors[1].r);
        assertThrows(UnsupportedOperationException.class,() -> scene.sprites().clear());
        assertThrows(UnsupportedOperationException.class,() -> scene.sprites().getFirst().pieces().clear());
        assertThrows(IllegalArgumentException.class,() -> composer.compose(
                view(Phase.RACE,0,0,true,Mode.GRAND_PRIX,0,1,0,0,null),records));
    }

    private S3kCompetitionScreen.MenuView view(Phase phase,int frame,int modeRow,boolean items,Mode mode,
            int arena,int participants,int hScroll,int vScroll,S3kCompetitionSession.Result result) {
        return new S3kCompetitionScreen.MenuView(phase,frame,modeRow,items,mode,arena,participants,
                List.of(0,1),List.of(0x40,0x95),List.of(false,false),hScroll,vScroll,result);
    }
    private S3kCompetitionSession.Result result(Mode mode,Arena arena,List<Integer> first,List<Integer> second) {
        List<S3kCompetitionSession.PlayerResult> players = new ArrayList<>();
        players.add(new S3kCompetitionSession.PlayerResult(0,S3kCompetitionPlayer.Character.SONIC,5,
                first,first.stream().mapToInt(Integer::intValue).sum(),true,0));
        if (mode != Mode.TIME_ATTACK) players.add(new S3kCompetitionSession.PlayerResult(1,S3kCompetitionPlayer.Character.TAILS,
                5,second,second.stream().mapToInt(Integer::intValue).sum(),true,0));
        return new S3kCompetitionSession.Result(mode,arena,players,List.of(first,second));
    }
    private static S3kCompetitionMenuScene.SpriteGroup group(S3kCompetitionMenuScene.Scene scene,int slot) {
        return scene.sprites().stream().filter(g -> g.nativeSlot() == slot).findFirst().orElseThrow();
    }
    private static int word(S3kCompetitionMenuScene.Scene scene,int byteOffset) { return scene.planeA()[byteOffset/2]; }
    private static void assertBlock(int[] plane,int byteOffset,int[] source,int sourceOffset,int width,int height) {
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) assertEquals(
                source[sourceOffset+y*width+x],plane[byteOffset/2+y*64+x],"map byte "+Integer.toHexString(byteOffset)+" at "+x+","+y);
    }
}
