package com.openggf.tools;

import com.openggf.audio.AudioRequestObserver;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.sonic2.credits.Sonic2EndingProvider;
import com.openggf.game.sonic2.objects.bosses.Sonic2DeathEggRobotInstance;
import com.openggf.graphics.FadeManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Real rendered native handoff, seeded at ending ingress rather than a cold boss clear. */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2NativeEndingEntry {
    @Test
    void robotWhiteCountdownEntersNativePhotosWithoutAnotherShellFadeOrMusicCommand() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.enableAudio(); // Production boot installs HeadlessSmpsAudioBackend/NoDeviceAudioSink.
            session.boot(RomTestUtils.ensureSonic2RomAvailable().toPath(), 10, 0, settings);
            var loop = session.loop();
            var level = GameServices.level();
            var objects = level.getObjectManager();
            var paletteRegistry = GameServices.paletteOwnershipRegistry();
            var fade = GameServices.fade();
            fade.cancel();
            level.consumeTitleCardRequest();
            NativePositionOps.writeXPosResetSubpixel(session.player(), 0xEC0);
            NativePositionOps.writeYPosResetSubpixel(session.player(), 0x14C);
            var boss = objects.createDynamicObjectWithReservedSlot(() -> new Sonic2DeathEggRobotInstance(
                    new ObjectSpawn(0xEA0, 0x14C, 0xC7, 0, 0, false, 0)));
            assertNotNull(boss);
            boss.getState().defeated = true;
            set(boss, "bodyRoutine", 0xE);
            set(boss, "defeatPhase", 6);
            set(boss, "robotFollowOffset", 0x20);
            var requests = new ArrayList<Integer>();
            var audio = GameServices.audio();
            long initialFades = audio.musicFadeOutCount();
            audio.setRequestObserver((kind, id) -> requests.add(id));
            try {
                // loc_3D9BE + first21 colour passes remain in the ordinary world.
                for (int pass = 0; pass <= 21; pass++) {
                    paletteRegistry.beginFrame();
                    boss.update(15 + pass, session.player());
                    level.getAnimatedPaletteManager().update();
                    assertFalse(fade.isActive(), "the object owns real palette writes, not a shell overlay");
                    assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
                    assertFalse(level.consumeCreditsRequest());
                    assertFalse(requests.contains(0xF9));
                }
                paletteRegistry.beginFrame();
                boss.update(37, session.player());
                level.getAnimatedPaletteManager().update();
                for (int line = 0; line < 4; line++) for (int color = 0; color < 16; color++) {
                    assertEquals(0xEEE, PaletteWriteSupport.segaWordFromColor(
                            level.getCurrentLevel().getPalette(line).getColor(color)));
                }
                assertEquals(0xF9, requests.getLast(), "FixBugs=0 queues fade through PlaySound at terminal pass");
                assertEquals(initialFades, audio.musicFadeOutCount(), "no direct fade command precedes the handoff");
                // Consume the actual request with the production shell, not a private-method shortcut.
                loop.step();
                assertEquals(GameMode.ENDING_CUTSCENE, loop.getCurrentGameMode());
                assertInstanceOf(Sonic2EndingProvider.class, get(get(loop, "endingSession"), "endingProvider"));
                assertFalse((Boolean) get(get(loop, "endingSession"), "endingTransitionPending"));
                assertEquals(FadeManager.FadeState.NONE, fade.getState(), "neither extra outgoing nor reveal fade");
                assertEquals(List.of(0xF9, 0x8A), requests.subList(requests.indexOf(0xF9), requests.size()),
                        "native ending music follows the boss mailbox command without a generic fade");
                assertEquals(initialFades, audio.musicFadeOutCount());
                var white = session.render();
                for (int pixel : white.pixels()) assertEquals(0xFFFFFF, pixel & 0xFFFFFF,
                        "the ending's own initial all-white palette must reach the framebuffer");
            } finally {
                audio.setRequestObserver(AudioRequestObserver.NONE);
            }
        }
    }

    private static Object get(Object target, String name) throws Exception {
        Field f = field(target.getClass(), name); return f.get(target);
    }
    private static void set(Object target, String name, int value) throws Exception {
        field(target.getClass(), name).setInt(target, value);
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name); f.setAccessible(true); return f;
    }
}
