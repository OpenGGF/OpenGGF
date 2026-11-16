package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.events.Sonic3kHCZEvents.ScreenPublication;
import com.openggf.game.sonic3k.scroll.SwScrlHcz;
import com.openggf.level.scroll.*;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Bounded deformation/matching contract, not route or rendered-pixel certification. */
@RequiresRom(SonicGame.SONIC_3K)
class TestHczMotionContinuity {
    private byte[] blend;
    @BeforeEach void setup() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        blend = GameServices.rom().getRom().readBytes(0x5077E, 4096);
    }
    @AfterEach void cleanup() { SessionManager.clear(); }

    private record Picture(LevelScrollPresentation scroll, ScrollProjectionPhase phase) {}
    private Picture picture(int act, int x, int y, ScreenPublication publication) throws Exception {
        return picture(act, x, y, new AtomicReference<>(publication));
    }
    private Picture picture(int act, int x, int y, AtomicReference<ScreenPublication> owner) throws Exception {
        var handler = new SwScrlHcz(blend, owner::get);
        handler.init(act, x, y);
        int[] words = new int[224];
        handler.update(words, x, y, 7, act);
        assertNull(handler.getPerLineVScrollBG());
        assertNull(handler.getPerColumnVScrollBG());
        short fg = handler.foregroundVerticalScrollExplicit() ? handler.getVscrollFactorFG() : (short) y;
        var nativeOutput = new ScrollProjection(words, null, null, null, fg,
                handler.getVscrollFactorBG(), handler.getBgCameraX(), handler.getBgPeriodWidth(),
                0, handler.getShakeOffsetY(), handler.getBgTilemapUpdateMode());
        var phase = handler.captureProjection(new ScrollProjectionContext(x, y, 7, act, 320, 224, nativeOutput));
        var scroll = new LevelScrollPresentation(new LevelScrollPresentation.Registers(x, y, x, fg,
                nativeOutput.backgroundY(), nativeOutput.backgroundX(), nativeOutput.backgroundPeriod(),
                false, false, false, false, (short) 0, false, (short) 0, null),
                words, null, null, null, null);
        // Install the real handler phase at the package-private presentation seam.
        var field = LevelScrollPresentation.class.getDeclaredField("phase");
        field.setAccessible(true);
        field.set(scroll, phase);
        return new Picture(scroll, phase);
    }
    private static ScreenPublication normal(int x, int y) {
        return new ScreenPublication(true, false, x, y, 0, y >> 2, 0);
    }

    @ParameterizedTest
    @CsvSource({"0,1400", "0,1544", "0,1552", "0,1672", "1,28", "1,572", "1,668", "1,1404"})
    void verticalPanAlignsBandsButStillMovesScalarY(int act, int y) throws Exception {
        int x = 0x400;
        var old = picture(act, x, y, normal(x, y));
        var now = picture(act, x, y + 8, normal(x, y + 8));
        var motion = now.scroll.backgroundMotionSince(old.scroll, 16, 320);
        assertNotNull(motion);
        assertArrayEquals(new short[224], motion.words(), "Vertical band reselection is not horizontal motion");
        assertNull(motion.lines());
        assertNull(motion.columns());
        assertEquals(-2, motion.y(), "Quarter-speed scalar vertical displacement remains native");
        var half = now.scroll.forMotion(0, -4, 320, motion, .5);
        assertEquals(now.scroll.registers().backgroundY() - 1, half.registers().backgroundY());
        assertNotEquals(now.scroll.registers().backgroundY(), half.registers().backgroundY(),
                "Frozen background negative control");
        assertArrayEquals(now.scroll.horizontal(), half.horizontal());
        assertEquals(y + 4, half.registers().foregroundY());
    }

    @Test void crossingHcz2BandHasAnUnalignedNegativeControlAndExactHorizontalAmount() throws Exception {
        var old = picture(1, 0x400, 28, normal(0x400, 28));
        var now = picture(1, 0x408, 36, normal(0x408, 36));
        // BG Y 7 -> 9 crosses the first eight-line deformation band.
        assertEquals(-384, (short) old.scroll.horizontal()[0]);
        assertEquals(-322, (short) now.scroll.horizontal()[0]);
        assertTrue(Math.abs((short)(old.scroll.horizontal()[0] - now.scroll.horizontal()[0])) > 16,
                "Comparing native rows would incorrectly reject this ordinary pan");
        var motion = now.scroll.backgroundMotionSince(old.scroll, 16, 320);
        assertNotNull(motion);
        assertEquals(2, motion.words()[0], "Aligned prior band is -320; current band is -322");
        var half = now.scroll.forMotion(-4, -4, 320, motion, .5);
        assertEquals(-321, (short) half.horizontal()[0], "Half of the two-pixel horizontal step");
        assertNotEquals((short) now.scroll.horizontal()[0], (short) half.horizontal()[0]);
    }

    @Test void heldPublicationIgnoresNewCameraAndFrozenPhaseSurvivesOwnerReplacement() throws Exception {
        var publication = normal(0x400, 572);
        var owner = new AtomicReference<>(publication);
        var first = picture(1, 0x400, 572, owner);
        var held = picture(1, 0x408, 580, owner);
        assertArrayEquals(first.scroll.horizontal(), held.scroll.horizontal());
        assertEquals(first.scroll.registers().backgroundY(), held.scroll.registers().backgroundY());
        var expected = first.phase.sample(new ScrollView(0x400, 572, 320, 224));
        owner.set(new ScreenPublication(true, true, 0x800, 900, 0x700, 800, 7));
        assertEquals(expected, first.phase.sample(new ScrollView(0x400, 572, 320, 224)));
        assertEquals(143, expected.backgroundY());
        assertEquals(572, expected.foregroundY());
    }

    @Test void wallPlaneTracksFullVerticalAmountAndRetainsCurrentDiscreteShake() throws Exception {
        var old = picture(1, 0x400, 500, new ScreenPublication(true, true, 0x400, 503, 800, 300, 3));
        var now = picture(1, 0x408, 508, new ScreenPublication(true, true, 0x408, 503, 808, 308, -5));
        var motion = now.scroll.backgroundMotionSince(old.scroll, 16, 320);
        assertNotNull(motion);
        assertEquals(-8, motion.y());
        assertEquals(-8, motion.x());
        short[] expectedWords = new short[224];
        Arrays.fill(expectedWords, (short) 8);
        assertArrayEquals(expectedWords, motion.words());
        var half = now.scroll.forMotion(-4, -4, 320, motion, .5);
        assertEquals(304, half.registers().backgroundY());
        assertEquals(804, half.registers().backgroundX());
        assertEquals(499, half.registers().foregroundY(), "Interpolated camera 504 retains current shake -5");
        assertEquals(-804, (short) half.horizontal()[0]);
    }

    @Test void completionUsesActOneDeformationAndWallToNormalIsAnExpectedSnap() throws Exception {
        int x = 0x400, y = 1544;
        var completion = picture(1, x, y, new ScreenPublication(true, false, x, y, 0, 0, 0, true));
        var actOne = picture(0, x, y, normal(x, y));
        assertArrayEquals(actOne.scroll.horizontal(), completion.scroll.horizontal());
        assertEquals(actOne.scroll.registers().backgroundY(), completion.scroll.registers().backgroundY());
        var next = picture(1, x, y + 8, new ScreenPublication(true, false, x, y + 8, 0, 0, 0, true));
        assertEquals(-2, next.scroll.backgroundMotionSince(completion.scroll, 16, 320).y());
        var wall = picture(1, x, y, new ScreenPublication(true, true, x, y, 800, 300, 0));
        var normal = picture(1, x, y, normal(x, y));
        assertNull(normal.scroll.backgroundMotionSince(wall.scroll, 16, 320),
                "Wall plane has an explicit BG X; normal deformation has no scalar BG X");
        var snapped = normal.scroll.forMotion(-4, 0, 320, null, .5);
        assertEquals(normal.scroll.registers().backgroundY(), snapped.registers().backgroundY());
        assertFalse(Arrays.equals(normal.scroll.horizontal(), snapped.horizontal()),
                "Foreground camera still reframes during a background snap");
        for (int row = 0; row < 224; row++)
            assertEquals((short) normal.scroll.horizontal()[row], (short) snapped.horizontal()[row]);
    }
}
