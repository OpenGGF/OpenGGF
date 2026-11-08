package com.openggf.game.sonic2.competition.scroll;

import com.openggf.game.GameServices;
import org.junit.jupiter.api.BeforeEach;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.sonic2.competition.scroll.Sonic2CompetitionScroll.*;

abstract class Scroll2PFixture {
    Sonic2CompetitionScroll scroll;
    @BeforeEach void load() throws Exception {
        var rom = GameServices.rom().getRom();
        assertEquals("8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9", HexFormat.of().withUpperCase()
                .formatHex(MessageDigest.getInstance("SHA-1").digest(rom.readAllBytes())));
        scroll = new Sonic2CompetitionScroll(rom);
    }
    static Camera camera(int x, int y) { return new Camera(x, y); }
    static int word(int fg, int bg) { return (fg << 16) | (bg & 65535); }
    static void bands(View view, int fg, int... lengthValuePairs) {
        var expected = new ArrayList<Integer>();
        for (int i = 0; i < lengthValuePairs.length; i += 2)
            for (int j = 0; j < lengthValuePairs[i]; j++) expected.add(word(fg, lengthValuePairs[i + 1]));
        assertArrayEquals(expected.stream().mapToInt(Integer::intValue).toArray(), view.lines());
    }
}
