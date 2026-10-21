package com.openggf.tools;

import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S3K {@code Render_Sprites} emits {@code Render_HUD} ahead of every object
 * priority bucket once {@code Level_started_flag} is set
 * (docs/skdisasm/sonic3k.asm:36366-36369), and earlier sprite-table entries
 * are drawn in front. The HUD therefore covers the title card while the card
 * holds over the running level: the native Knuckles HPZ entry dumps
 * (campaign-visual {@code hpz-entry} seq130-152) show the score, time and ring
 * digits over the red banner.
 *
 * <p>Inside the settled banner's HUD rows the frame shows something other than
 * banner red only if the HUD is drawn over the card.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kHudOverTitleCard {
    // The settled red banner spans x 64-127 (ObjArray_TtlCard target x 96);
    // the HUD's score, time and ring rows lie in y 8-55.
    private static final int BANNER_LEFT = 66;
    private static final int BANNER_RIGHT = 126;
    private static final int HUD_TOP = 8;
    private static final int HUD_BOTTOM = 56;

    @Test
    void levelHudCoversTheHoldingTitleCardBanner() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off",
                null, null, null, null, true, false);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 1, settings);
            var level = GameServices.level();
            level.requestZoneAndAct(22, 1, true);
            boolean holding = false;
            for (int frame = 0; frame < 600 && !holding; frame++) {
                session.step(null);
                holding = session.loop().getCurrentGameMode() == GameMode.LEVEL
                        && level.getCurrentZone() == 22 && level.getFrameCounter() == 10
                        && session.loop().getTitleCardProvider() instanceof Sonic3kTitleCardManager card
                        && card.getStateName().equals("DISPLAY") && !card.drawsOpaqueLevelBackdrop();
            }
            assertTrue(holding, "the fresh HPZ card holds over the running level at counter 10");

            RgbaImage frame = session.render();
            int bannerRed = frame.argb((BANNER_LEFT + BANNER_RIGHT) / 2, 130);
            assertTrue(isRed(bannerRed), "the sampled banner pixel is the red banner: "
                    + Integer.toHexString(bannerRed));
            int area = (BANNER_RIGHT - BANNER_LEFT) * (HUD_BOTTOM - HUD_TOP);
            int red = 0;
            for (int y = HUD_TOP; y < HUD_BOTTOM; y++) {
                for (int x = BANNER_LEFT; x < BANNER_RIGHT; x++) {
                    if (frame.argb(x, y) == bannerRed) {
                        red++;
                    }
                }
            }
            assertTrue(red > area / 2, "the banner covers the HUD rows: " + red + " of " + area);
            assertTrue(red < area, "the HUD digits are drawn over the card's red banner");
        }
    }

    private static boolean isRed(int argb) {
        int r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
        return r > 0x80 && g < 0x40 && b < 0x40;
    }

}
