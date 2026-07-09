package com.openggf.game.sonic3k.specialstage;

import com.openggf.level.Palette;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class TestSonic3kSpecialStageVisualSnapshot {
    @Test
    void perspectiveBackgroundHudAndBannerRestoreState() {
        Sonic3kSpecialStagePerspective perspective = new Sonic3kSpecialStagePerspective();
        set(perspective, "animFrame", 12);
        set(perspective, "paletteFrame", 8);
        Sonic3kSpecialStageSnapshot.PerspectiveSnapshot perspectiveSnapshot =
                perspective.captureRewindSnapshot();
        set(perspective, "animFrame", 1);
        set(perspective, "paletteFrame", 2);
        perspective.restoreRewindSnapshot(perspectiveSnapshot);
        assertEquals(12, perspective.getAnimFrame());
        assertEquals(8, perspective.getPaletteFrame());

        Sonic3kSpecialStageBackground background = new Sonic3kSpecialStageBackground();
        set(background, "vScroll", 40);
        set(background, "hScroll", 80);
        set(background, "prevXPos", 0x1111);
        set(background, "prevYPos", 0x2222);
        Sonic3kSpecialStageSnapshot.BackgroundSnapshot backgroundSnapshot =
                background.captureRewindSnapshot();
        background.reset();
        background.restoreRewindSnapshot(backgroundSnapshot);
        assertEquals(40, background.getVScroll());
        assertEquals(80, background.getHScroll());
        assertEquals(0x1111, get(background, "prevXPos"));
        assertEquals(0x2222, get(background, "prevYPos"));

        Sonic3kSpecialStageHud hud = new Sonic3kSpecialStageHud();
        hud.initialize();
        hud.update(17, 42);
        hud.clearSphereDirty();
        Sonic3kSpecialStageSnapshot.HudSnapshot hudSnapshot = hud.captureRewindSnapshot();
        hud.update(1, 2);
        hud.restoreRewindSnapshot(hudSnapshot);
        assertEquals(17, hud.getDisplayedSphereCount());
        assertEquals(42, hud.getDisplayedRingCount());
        assertEquals(false, hud.isSphereDirty());
        assertEquals(true, hud.isRingDirty());

        Sonic3kSpecialStageBanner banner = new Sonic3kSpecialStageBanner();
        banner.initialize();
        set(banner, "phase", Sonic3kSpecialStageBanner.Phase.SLIDING_IN);
        set(banner, "slideOffset", 33);
        set(banner, "displayTimer", 44);
        set(banner, "triggeredAdvance", true);
        set(banner, "showPerfect", true);
        Sonic3kSpecialStageSnapshot.BannerSnapshot bannerSnapshot = banner.captureRewindSnapshot();
        banner.initialize();
        banner.restoreRewindSnapshot(bannerSnapshot);
        assertEquals(Sonic3kSpecialStageBanner.Phase.SLIDING_IN, banner.getPhase());
        assertEquals(33, banner.getSlideOffset());
        assertEquals(44, get(banner, "displayTimer"));
        assertEquals(true, get(banner, "triggeredAdvance"));
        assertEquals(true, banner.isShowPerfect());
    }

    @Test
    void paletteSnapshotDeepCopiesPalettesAndStageData() {
        Sonic3kSpecialStagePalette palette = new Sonic3kSpecialStagePalette();
        Palette[] livePalettes = (Palette[]) get(palette, "palettes");
        for (int i = 0; i < livePalettes.length; i++) {
            livePalettes[i] = new Palette();
            Palette.Color color = livePalettes[i].getColor(i);
            color.r = (byte) (10 + i);
            color.g = (byte) (20 + i);
            color.b = (byte) (30 + i);
        }
        byte[] stageData = new byte[]{1, 2, 3, 4};
        set(palette, "stagePaletteData", stageData);
        set(palette, "fadeActive", true);

        Sonic3kSpecialStageSnapshot.PaletteSnapshot snapshot = palette.captureRewindSnapshot();
        stageData[0] = 99;
        for (int i = 0; i < livePalettes.length; i++) {
            Palette.Color color = livePalettes[i].getColor(i);
            color.r = (byte) (100 + i);
            color.g = (byte) (110 + i);
            color.b = (byte) (120 + i);
        }
        set(palette, "fadeActive", false);
        palette.restoreRewindSnapshot(snapshot);

        assertEquals(true, get(palette, "fadeActive"));
        assertArrayEquals(new byte[]{1, 2, 3, 4}, (byte[]) get(palette, "stagePaletteData"));
        assertNotSame(snapshot.palettes(), palette.getPalettes());
        assertNotSame(snapshot.palettes()[0], palette.getPalette(0));
        for (int i = 0; i < livePalettes.length; i++) {
            Palette.Color color = palette.getPalette(i).getColor(i);
            assertEquals((byte) (10 + i), color.r);
            assertEquals((byte) (20 + i), color.g);
            assertEquals((byte) (30 + i), color.b);
        }
    }

    @Test
    void ringConverterSnapshotRestoresSeedField() {
        Sonic3kSpecialStageRingConverter converter = new Sonic3kSpecialStageRingConverter();
        set(converter, "seedBlueConverted", 5);
        Sonic3kSpecialStageSnapshot.RingConverterSnapshot snapshot = converter.captureRewindSnapshot();
        set(converter, "seedBlueConverted", 0);
        converter.restoreRewindSnapshot(snapshot);
        assertEquals(5, get(converter, "seedBlueConverted"));
    }

    private static Object get(Object target, String field) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
