package com.openggf.game.sonic2;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.openggf.game.sonic2.Sonic2RomAddresses.address;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offset tables whose first word is not their own length must be read with their
 * disassembly label extent. A first-word count reads the neighbouring table and
 * frame bodies as offsets; the bounded parse must keep exactly the frames the
 * first-word parse started with and drop only that garbage tail.
 */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2BoundedMappingTables {
    private static final int MAX_SANE_FRAME_PIECES = 80;

    @Test
    void sheetsReadOnlyTheirOwnOffsetTables() throws Exception {
        Rom rom = TestEnvironment.currentRom();
        RomByteReader reader = RomByteReader.fromRom(rom);
        Sonic2ObjectArt art = new Sonic2ObjectArt(rom, reader);

        assertBounded(reader, Sonic2Constants.MAP_UNC_SPRING_ADDR,
                Sonic2ObjectArt.SPRING_MAPPING_FRAME_COUNT, art, Sonic2ObjectArt::loadSpringVerticalSheet);
        assertBounded(reader, Sonic2Constants.MAP_UNC_SPRING_ADDR,
                Sonic2ObjectArt.SPRING_MAPPING_FRAME_COUNT, art, Sonic2ObjectArt::loadSpringHorizontalSheet);
        assertBounded(reader, Sonic2Constants.MAP_UNC_SPRING_ADDR,
                Sonic2ObjectArt.SPRING_MAPPING_FRAME_COUNT, art, Sonic2ObjectArt::loadSpringDiagonalSheet);
        assertBounded(reader, Sonic2Constants.MAP_UNC_GRABBER_STRING_ADDR,
                Sonic2ObjectArt.GRABBER_STRING_MAPPING_FRAME_COUNT, art, Sonic2ObjectArt::loadGrabberStringSheet);
        assertBounded(reader, Sonic2Constants.MAP_UNC_GROUNDER_ADDR,
                Sonic2ObjectArt.GROUNDER_MAPPING_FRAME_COUNT, art, Sonic2ObjectArt::loadGrounderSheet);
        assertBounded(reader, Sonic2Constants.MAP_UNC_GROUNDER_ROCK_ADDR,
                Sonic2ObjectArt.GROUNDER_ROCK_MAPPING_FRAME_COUNT, art, Sonic2ObjectArt::loadGrounderRockSheet);

        // The bubble sheet rewrites the countdown frames 8-$D after parsing, so compare
        // its frame count and the untouched frames.
        int bubbles = Sonic2ObjectArt.SMALL_BUBBLES_MAPPING_FRAME_COUNT;
        List<SpriteMappingFrame> unbounded = S2SpriteDataLoader.loadMappingFrames(
                reader, address(reader, Sonic2Constants.MAP_UNC_SMALL_BUBBLES_ADDR));
        ObjectSpriteSheet bubbleSheet = art.loadBubblesSheet();
        assertTrue(unbounded.size() > bubbles, "first-word count overruns Obj24_MapUnc_1FBF6");
        assertEquals(bubbles, bubbleSheet.getFrameCount());
        for (int frame = 0; frame < bubbles; frame++) {
            if (frame >= 8 && frame <= 0xD) {
                continue;
            }
            assertEquals(unbounded.get(frame), bubbleSheet.getFrame(frame), "bubble frame " + frame);
        }
        assertSane("bubbles", frames(bubbleSheet));
    }

    private static void assertBounded(RomByteReader reader, int rev01MappingAddr, int realCount,
            Sonic2ObjectArt art, Function<Sonic2ObjectArt, ObjectSpriteSheet> loader) {
        String context = String.format("table 0x%X", rev01MappingAddr);
        List<SpriteMappingFrame> unbounded = S2SpriteDataLoader.loadMappingFrames(
                reader, address(reader, rev01MappingAddr));
        assertTrue(unbounded.size() > realCount, context + " first-word count overruns the table");
        List<SpriteMappingFrame> bounded = frames(loader.apply(art));
        assertEquals(realCount, bounded.size(), context);
        assertEquals(unbounded.subList(0, realCount), bounded, context);
        assertSane(context, bounded);
    }

    private static List<SpriteMappingFrame> frames(ObjectSpriteSheet sheet) {
        List<SpriteMappingFrame> frames = new ArrayList<>(sheet.getFrameCount());
        for (int frame = 0; frame < sheet.getFrameCount(); frame++) {
            frames.add(sheet.getFrame(frame));
        }
        return frames;
    }

    private static void assertSane(String context, List<SpriteMappingFrame> frames) {
        for (int frame = 0; frame < frames.size(); frame++) {
            assertTrue(frames.get(frame).pieces().size() <= MAX_SANE_FRAME_PIECES,
                    context + " frame " + frame + " has " + frames.get(frame).pieces().size() + " pieces");
        }
    }
}
