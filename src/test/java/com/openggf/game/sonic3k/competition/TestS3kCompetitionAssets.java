package com.openggf.game.sonic3k.competition;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.level.Palette;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCompetitionAssets {
    private Rom rom;
    private RomByteReader reader;
    private S3kCompetitionAssets assets;

    @BeforeEach void loadRealRomAssets() throws IOException {
        rom=GameServices.rom().getRom(); reader=RomByteReader.fromRom(rom);
        assets=new S3kCompetitionAssets(rom);
    }

    @Test void fullCompressedSourceSpansMatchTheVerifiedShippedAssets() throws IOException, NoSuchAlgorithmException {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        for (var source:S3kCompetitionAssets.sourceSpans()) {
            assertEquals(source.sha256(),HexFormat.of().formatHex(digest.digest(
                    rom.readBytes(source.address(),source.byteCount()))),source.name());
        }
        assertEquals(30,S3kCompetitionAssets.sourceSpans().size());
    }

    @Test void actualLockedOnRoutineOperandsAndPlcDestinationsOwnTheseAssets() {
        // Immediate operands, not a search for a matching duplicate in the other ROM half.
        int[][] instructions={{0xA944,0x39F46E},{0xA930,0x39F87E},{0xB854,0x3A16BE},
                {0xACDE,0x3A1B30},{0xA95E,0x209818},{0xAD0A,0x20A55E},
                {0xB89C,0x20AD84},{0xBDD0,0x20B26C},{0xA970,0x39D282},
                {0x130A6,0x337780},{0x16068,0x33A840},{0x1603A,0x33D5E0},
                {0x130B8,0x33DB60},{0x10698,0x29FC3A},{0x130A0,0x29FDE4},
                {0x13182,0x29FEE6},{0x16056,0x2A013E},{0x16028,0x2A024C},
                {0x106A6,0x2A02AA},{0x130B2,0x2A0498}};
        for (int[] pair:instructions) assertEquals(pair[1],longWord(pair[0]+2),"owning instruction "+Integer.toHexString(pair[0]));
        // PLC_42 common records, then BPZ/DPZ/CGZ/EMZ first records. Destinations are bytes.
        int[][] plc={{0x92758,0x3973F8,0x6BC},{0x9275E,0x397BCA,0x700},
                {0x92764,0x397570,0x600},{0x9276A,0x3980C4,0x75E},
                {0x92770,0x396D10,0x391},{0x92776,0x396B9C,0x3AD},
                {0x9277C,0x396F02,0x3C6},{0x92784,0x39815E,0x300},
                {0x927BC,0x3989A8,0x280},{0x927F4,0x3996BE,0x300},{0x9282C,0x399A32,0x300}};
        for (int[] entry:plc) {
            assertEquals(entry[1],longWord(entry[0]));
            assertEquals(entry[2]*32,reader.readU16BE(entry[0]+4));
        }
    }

    @Test void allArenaAndHudMappingsHaveExactNativeTableSizes() {
        Map<String,Integer> counts=Map.ofEntries(
                Map.entry("competition_item",32),Map.entry("competition_goal",4),
                Map.entry("competition_lap",10),Map.entry("competition_item_icon",4),
                Map.entry("competition_neon",19),Map.entry("competition_position",5),
                Map.entry("competition_timer",1),Map.entry("competition_timer_digit",10),
                Map.entry("competition_blade",4),Map.entry("competition_elephant",2),
                Map.entry("competition_balloon",10),Map.entry("competition_bumper",2),
                Map.entry("competition_fan",3),Map.entry("competition_sand_bar",30),
                Map.entry("competition_sand_bar_wide",29),Map.entry("competition_button",2),
                Map.entry("competition_floor_button",3),Map.entry("competition_dripper",4),
                Map.entry("competition_rock",8),Map.entry("competition_retracting_spring_base",4),
                Map.entry("competition_retracting_spring_base_emz",4),Map.entry("competition_spring",7),
                Map.entry("competition_spring_yellow",7),Map.entry("competition_sonic",44),
                Map.entry("competition_tails",46),Map.entry("competition_knuckles",50),
                Map.entry("competition_tails_tail",16),Map.entry("competition_menu_mode",7),
                Map.entry("competition_menu_select",16),Map.entry("competition_menu_player",3));
        counts.forEach((key,count)->assertEquals(count.intValue(),assets.sheet(key).getFrameCount(),key));
        for (var entry:assets.sheets().entrySet()) {
            ObjectSpriteSheet sheet=entry.getValue();
            for (int frame=0;frame<sheet.getFrameCount();frame++) for (SpriteMappingPiece piece:sheet.getFrame(frame).pieces()) {
                assertTrue(piece.tileIndex()>=0 && piece.tileIndex()+piece.widthTiles()*piece.heightTiles()
                        <=sheet.getPatterns().length,"tile bounds: "+entry.getKey()+" frame "+frame);
            }
        }
    }

    @Test void fullNativeTileWordAdditionPreservesCrossBankSpringIconsAndPaletteCarry() {
        SpriteMappingPiece emzBase=assets.sheet("competition_retracting_spring_base_emz").getFrame(1).pieces().getFirst();
        assertEquals(0x305,emzBase.tileIndex()); assertEquals(2,emzBase.paletteIndex()); assertTrue(emzBase.hFlip());
        SpriteMappingPiece blade=assets.sheet("competition_blade").getFrame(1).pieces().getFirst();
        assertEquals(0x328,blade.tileIndex()); assertEquals(2,blade.paletteIndex()); assertFalse(blade.priority());
        SpriteMappingPiece icon=assets.sheet("competition_item_icon").getFrame(1).pieces().getLast();
        assertEquals(0x3C6,icon.tileIndex()); assertEquals(0,icon.paletteIndex()); assertTrue(icon.priority());
        SpriteMappingPiece shard=assets.sheet("competition_rock").getFrame(7).pieces().getFirst();
        assertEquals(0,shard.tileIndex()); assertEquals(3,shard.paletteIndex()); assertTrue(shard.priority());
        SpriteMappingPiece horizontal=assets.sheet("competition_spring_horizontal").getFrame(3).pieces().getFirst();
        assertEquals(0x3AD,horizontal.tileIndex()); assertTrue(horizontal.hFlip());
        SpriteMappingPiece vertical=assets.sheet("competition_spring").getFrame(0).pieces().getFirst();
        assertEquals(0x391,vertical.tileIndex());
    }

    @Test void miniaturesUseTheirOwnArtAndDplcSourcesForEveryMappingFrame() {
        int[][] cases={{0x337780,0x29FC3A,0x29FDE4,44,390},
                {0x33A840,0x29FEE6,0x2A013E,46,365},{0x33DB60,0x2A02AA,0x2A0498,50,420}};
        for (int c=0;c<cases.length;c++) {
            int[] source=cases[c];
            var character=S3kCompetitionPlayer.Character.values()[c];
            ObjectSpriteSheet sheet=assets.miniature(character);
            assertEquals(source[4],sheet.getPatterns().length);
            for (int frame=1;frame<source[3];frame++) {
                int dplc=source[2]+reader.readU16BE(source[2]+frame*2);
                int expectedSource=reader.readU16BE(dplc+2)&0xFFF;
                SpriteMappingPiece piece=sheet.getFrame(frame).pieces().getFirst();
                assertEquals(expectedSource,piece.tileIndex(),character+" frame "+frame);
                assertEquals(c==2?1:0,piece.paletteIndex());
            }
            for (int y=0;y<8;y++) for (int x=0;x<8;x++) {
                int packed=reader.readU8(source[0]+y*4+x/2);
                int pixel=(x&1)==0?packed>>4:packed&15;
                assertEquals(pixel,sheet.getPatterns()[0].getPixel(x,y)&15,character+" raw first tile");
            }
        }
        assertEquals(44,assets.tailsTail().getPatterns().length);
        assertTrue(assets.miniature(S3kCompetitionPlayer.Character.SONIC).getFrame(0).pieces().isEmpty());
    }

    @Test void timerDigitsAndNativeCountdownScriptsRetainTheirRomShape() {
        var zero=assets.sheet("competition_timer_digit").getFrame(0).pieces().getFirst();
        var one=assets.sheet("competition_timer_digit").getFrame(1).pieces().getFirst();
        assertEquals(0x608,zero.tileIndex()); assertEquals(2,zero.widthTiles()); assertEquals(2,zero.heightTiles());
        assertEquals(0x60C,one.tileIndex()); assertEquals(1,one.widthTiles()); assertTrue(one.priority());
        assertEquals(-8,one.yOffset()); assertEquals(0,one.xOffset());
        var neon=assets.animations("competition_neon");
        assertEquals(8,neon.getScriptCount()); assertEquals(59,neon.getScript(0).delay());
        assertEquals(40,neon.getScript(1).frames().size());
        assertEquals(List.of(0,9,0,9),neon.getScript(1).frames().subList(0,4));
        assertEquals(3,neon.getScript(1).frames().get(30));
        // Both mini tables use a negative relative pointer to the same ordinary script.
        assertEquals(assets.animations("competition_sonic").getScript(31),
                assets.animations("competition_knuckles").getScript(31));
        assertEquals(0x12CA4,0x12F84+(short)reader.readU16BE(0x12F84+62));
        assertEquals(4,assets.animations("competition_sand_bar").getScriptCount());
        assertEquals(4,assets.animations("competition_balloon").getScriptCount());
    }

    @Test void menuPlanesAndPaletteLinesUseNativeArtWordsAndFiveArenaOrder() throws IOException {
        assertEquals(1120,assets.menuBackground(S3kCompetitionAssets.MenuPhase.MODE).length);
        assertEquals(243,assets.arenaBorders().length); assertEquals(240,assets.arenaImages().length);
        assertEquals(375,assets.arenaNames().length); assertEquals(90,assets.resultsLed().length);
        for (S3kCompetitionAssets.MenuPhase phase:S3kCompetitionAssets.MenuPhase.values()) {
            assertEquals(0x800,assets.menuPatterns(phase).length);
            Palette[] palette=assets.menuPalette(phase,S3kCompetitionSession.Arena.AZURE_LAKE);
            int source=phase==S3kCompetitionAssets.MenuPhase.MODE?0x209818:0x39D282;
            assertPaletteLine(palette[0],source);
            if (phase==S3kCompetitionAssets.MenuPhase.RESULTS) assertPaletteLine(palette[3],0x20AD84);
            if (phase==S3kCompetitionAssets.MenuPhase.TIME_ATTACK_RESULTS) assertPaletteLine(palette[3],0x20B26C);
        }
        for (int i=0;i<5;i++) {
            var arena=S3kCompetitionSession.GRAND_PRIX_ORDER.get(i);
            assertPaletteLine(assets.menuPalette(S3kCompetitionAssets.MenuPhase.ARENA_SELECT,arena)[3],0x20A5BE+i*32);
        }
        assertEquals(108,assets.rawMenuMaps().get("time_border").length);
        assertEquals(26,assets.rawMenuMaps().get("chrome_gadget").length);
    }

    @Test void rockDebrisReadsNativeCountOffsetsAndSignedVelocities() {
        assertEquals(6,assets.rockDebris().size());
        for (int i=0;i<6;i++) {
            var row=assets.rockDebris().get(i);
            assertEquals((byte)reader.readU8(0x202A8+i*2),row.dx());
            assertEquals((byte)reader.readU8(0x202A9+i*2),row.dy());
            assertEquals((short)reader.readU16BE(0x20358+i*4),row.xVelocity());
            assertEquals((short)reader.readU16BE(0x2035A+i*4),row.yVelocity());
        }
        assertTrue(assets.rockDebris().stream().anyMatch(row->row.xVelocity()<0));
    }

    private int longWord(int address) { return reader.readU16BE(address)<<16 | reader.readU16BE(address+2); }
    private void assertPaletteLine(Palette palette,int source) {
        for (int color=0;color<16;color++) {
            int word=reader.readU16BE(source+color*2);
            assertEquals((((word>>1)&7)*255+3)/7,palette.getColor(color).r&255);
            assertEquals((((word>>5)&7)*255+3)/7,palette.getColor(color).g&255);
            assertEquals((((word>>9)&7)*255+3)/7,palette.getColor(color).b&255);
        }
    }
}
