package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.objects.SongFadeTransitionInstance;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSszAct2MechaEntry {
    @Test void wideEmeraldPreviewHandsOverWithoutAllocatingOrAdvancingTheNativeObject() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 1).build();
        var state = (SszZoneRuntimeState) GameServices.zoneRuntimeState();
        assertFalse(com.openggf.game.sonic3k.render.SszMasterEmeraldPreview.shouldDraw(320, state, false));
        assertTrue(com.openggf.game.sonic3k.render.SszMasterEmeraldPreview.shouldDraw(800, state, false));
        var camera = GameServices.camera(); camera.setMaxX((short) 0x240); camera.setY((short) 0x400);
        var manager = GameServices.level().getObjectManager();
        var emerald = new SszMasterEmerald(new ObjectSpawn(0, 0, 0, 0, 0, false, 0));
        emerald.setServices(TestEnvironment.objectServices()); manager.addDynamicObject(emerald);
        emerald.update(0, fixture.sprite());
        assertEquals(0x340, emerald.getX()); assertEquals(0x4A8, emerald.getY());
        assertTrue(com.openggf.game.sonic3k.render.SszMasterEmeraldPreview.shouldDraw(800, state, emerald.isDrawing()),
                "native setup does not draw; keep the preview until its first drawing pass");
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) {
                registry.restore(saved);
                emerald = manager.activeObjectsOfType(SszMasterEmerald.class).getFirst();
                state = (SszZoneRuntimeState) GameServices.zoneRuntimeState();
            }
            emerald.update(1, fixture.sprite());
            assertFalse(com.openggf.game.sonic3k.render.SszMasterEmeraldPreview.shouldDraw(800, state, emerald.isDrawing()));
            state.setAct2EndingActive(true); emerald.update(2, fixture.sprite());
            assertFalse(com.openggf.game.sonic3k.render.SszMasterEmeraldPreview.shouldDraw(800, state, emerald.isDrawing()),
                    "the preview must not reappear after ending retirement");
        }
    }

    @Test void bodyPreservesRomTilePriorityIndependentlyOfSpriteOrdering() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(10, 1).build();
        var boss = new SszMechaSonicObjectInstance(new ObjectSpawn(0, 0, 0, 0, 0, false, 0));
        // ObjSlot_MechaSonic: count word, then art_tile. Read the shipped ROM,
        // not the renderer sheet (which does not own object tile priority).
        int artTile = TestEnvironment.objectServices().rom().read16BitAddr(0x7D3EC);
        assertEquals((artTile & 0x8000) != 0, boss.isHighPriority());
        assertEquals(0, boss.getTileOcclusionPaletteMask(),
                "high Plane B island must not mask Mecha's body in any palette phase");
        assertEquals(com.openggf.graphics.RenderPriority.fromS3kWord(0x280), boss.getPriorityBucket());
    }

    @Test void superPaletteChangesHeaderAndRunsCustomCallbackAtNativeExpiry() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(10, 1).build();
        var services = TestEnvironment.objectServices();
        var palette = new SszMechaPalette();
        palette.install(services, 0x7DA60);
        Runnable unexpected = () -> fail("loop command must not call the custom routine");
        for (int i = 0; i < 42; i++) palette.tick(services, unexpected);
        assertEquals(0x7DA6A, palette.headerAddress());
        var saved = palette.captureRewindStateValue();
        palette.tick(services, unexpected);
        assertEquals(0x7DAD8, palette.headerAddress());
        int expected = services.rom().read16BitAddr(0x7DADC + 18);
        assertEquals(expected, mechaPaletteWord(9), "transition publishes new header's first row immediately");
        palette.restoreRewindStateValue(saved); palette.tick(services, unexpected);
        assertEquals(0x7DAD8, palette.headerAddress());
        assertEquals(expected, mechaPaletteWord(9));
        palette.install(services, 0x7DC06);
        for (int i = 0; i < 24; i++) palette.tick(services, unexpected);
        assertEquals(0x7DC10, palette.headerAddress());
        palette.tick(services, unexpected);
        assertEquals(0x7DAD8, palette.headerAddress(), "signed negative relative header jump");
        palette.install(services, 0x7DC7E);
        int[] callbacks = {0};
        for (int i = 0; i < 18; i++) palette.tick(services, () -> callbacks[0]++);
        assertEquals(0, callbacks[0]);
        int before = mechaPaletteWord(9);
        palette.tick(services, () -> callbacks[0]++);
        assertEquals(1, callbacks[0]);
        assertEquals(before, mechaPaletteWord(9), "custom command skips colour publication");
    }

    @Test void transformationFlashKeepsNativeStepCadenceAndRestoresItsTargetAfterRewind() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 1).build();
        var services = TestEnvironment.objectServices();
        var manager = GameServices.level().getObjectManager();
        // A zero line makes all six per-channel steps independently observable.
        com.openggf.game.sonic3k.S3kPaletteWriteSupport.applyLine(
                services.paletteOwnershipRegistryOrNull(), services.currentLevel(), services.graphicsManager(),
                "ssz.flash.test", 1000, 1, new byte[32], true);
        SszMechaScreenFlash.saveTarget(services);
        var flash = new SszMechaScreenFlash(new ObjectSpawn(0, 0, 0, 0, 0, false, 0));
        flash.setServices(services); manager.addDynamicObject(flash);
        for (int i = 0; i <= 12; i++) {
            services.paletteOwnershipRegistryOrNull().beginFrame();
            flash.update(i, fixture.sprite());
            assertEquals(i < 6 ? 0x222 : i < 12 ? 0x444 : 0x666, mechaPaletteWord(9));
        }
        assertFalse(services.paletteOwnershipRegistryOrNull().isPaletteRotationDisabled());
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) {
                registry.restore(saved);
                flash = manager.activeObjectsOfType(SszMechaScreenFlash.class).getFirst();
            }
            for (int i = 13; i <= 50; i++) {
                services.paletteOwnershipRegistryOrNull().beginFrame();
                flash.update(i, fixture.sprite());
                assertEquals(i < 42 ? 0x666 : i < 46 ? 0x444 : i < 50 ? 0x222 : 0, mechaPaletteWord(9));
                assertFalse(flash.isDestroyed(), "Go_Delete_Sprite takes the next object pass");
            }
            flash.update(51, fixture.sprite());
            assertTrue(flash.isDestroyed());
        }
    }

    private int mechaPaletteWord(int color) {
        return com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(
                TestEnvironment.objectServices().currentLevel().getPalette(1).getColor(color));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void emeraldUsesNativePaletteGateAndEndingFlagWithRewind(boolean allSuperEmeralds) {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 1).build();
        var game = GameServices.gameState();
        game.configureSpecialStageProgress(7, 7);
        if (allSuperEmeralds) for (int i = 0; i < 7; i++) {
            game.markEmeraldCollected(i); game.markSuperEmeraldCollected(i);
        }
        var manager = GameServices.level().getObjectManager();
        var runtime = (SszZoneRuntimeState) GameServices.zoneRuntimeState();
        var camera = GameServices.camera();
        camera.setMaxX((short) 0x240); camera.setY((short) 0x400);
        var emerald = new SszMasterEmerald(new ObjectSpawn(0, 0, 0, 0, 0, false, 0));
        emerald.setServices(TestEnvironment.objectServices()); manager.addDynamicObject(emerald);
        int initial = paletteWord(14);
        updateEmerald(emerald, 0, fixture.sprite());
        assertEquals(0x340, emerald.getX()); assertEquals(0x4A8, emerald.getY());
        assertEquals(initial, paletteWord(14), "init does not run rotation");
        updateEmerald(emerald, 1, fixture.sprite());
        assertEquals(allSuperEmeralds ? 0x680 : initial, paletteWord(14));
        if (allSuperEmeralds) assertEquals(0x8A0, paletteWord(15));
        runtime.setCutsceneFlag(6); updateEmerald(emerald, 2, fixture.sprite());
        assertEquals(1, emerald.frameForTest());
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        for (int i = 3; i <= 11; i++) updateEmerald(emerald, i, fixture.sprite());
        int expected = paletteWord(15);
        if (allSuperEmeralds) assertEquals(0x8C0, expected, "second pair advances after ten updates");
        runtime.setAct2EndingActive(true); updateEmerald(emerald, 12, fixture.sprite());
        assertTrue(emerald.isDestroyed());
        registry.restore(saved);
        emerald = manager.activeObjectsOfType(SszMasterEmerald.class).getFirst();
        assertFalse(((SszZoneRuntimeState) GameServices.zoneRuntimeState()).act2EndingActive());
        for (int i = 3; i <= 11; i++) updateEmerald(emerald, i, fixture.sprite());
        assertEquals(expected, paletteWord(15));
        assertFalse(emerald.isDestroyed());
    }

    private void updateEmerald(SszMasterEmerald emerald, int clock, com.openggf.game.PlayableEntity player) {
        var services = TestEnvironment.objectServices();
        var registry = services.paletteOwnershipRegistryOrNull();
        registry.beginFrame();
        emerald.update(clock, player);
        var level = services.currentLevel();
        var palettes = new com.openggf.level.Palette[] {level.getPalette(0), level.getPalette(1),
                level.getPalette(2), level.getPalette(3)};
        registry.resolveInto(palettes, null, null, palettes[0]);
    }

    private int paletteWord(int color) {
        return com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(
                TestEnvironment.objectServices().currentLevel().getPalette(2).getColor(color));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(com.openggf.configuration.WidescreenAspect.class)
    void transformationPanProjectsNativeProgressAtEachWidthAndReplays(com.openggf.configuration.WidescreenAspect aspect) {
        var config = com.openggf.configuration.SonicConfigurationService.getInstance();
        int width = aspect.pixelWidth();
        config.clearSessionOverrides();
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.DISPLAY_ASPECT, aspect.name());
        config.resolveDisplayAspect();
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        com.openggf.game.CrossGameFeatureProvider.getInstance().resetState();
        com.openggf.game.session.SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        try {
            HeadlessTestFixture.builder().withZoneAndAct(10, 1).build();
            var camera = GameServices.camera();
            var state = (SszZoneRuntimeState) GameServices.zoneRuntimeState();
            assertEquals(width, camera.getWidth());
            state.setCenterNativeArenaCamera(true);
            int inset = (width - 320) / 2;
            camera.setX((short) (0x100 - inset));
            camera.setMinX((short) 0x100); camera.setMaxX((short) 0x240);
            var manager = GameServices.level().getObjectManager();
            var pan = new SszMechaArenaPan(new ObjectSpawn(0, 0, 0, 0, 0, false, 0));
            pan.setServices(TestEnvironment.objectServices()); manager.addDynamicObject(pan);
            pan.update(0, null);
            for (int frame = 1; frame <= 53; frame++) {
                pan.update(frame, null);
                assertEquals(0x100 + frame * 6 - inset, camera.getX());
                assertEquals(0x100, camera.getMinX()); assertEquals(0x240, camera.getMaxX());
            }
            var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
            var saved = registry.capture();
            for (int replay = 0; replay < 2; replay++) {
                if (replay != 0) {
                    registry.restore(saved);
                    pan = manager.activeObjectsOfType(SszMechaArenaPan.class).getFirst();
                }
                pan.update(54, null);
                assertTrue(pan.isDestroyed());
                assertEquals(0x240 - inset, camera.getX());
                assertTrue(camera.getFrozen());
                assertTrue(camera.getX() >= 0);
                assertTrue(camera.getX() + width <= 9 * 128,
                        "SSZ2's nine-block foreground must contain the displayed pan");
            }
        } finally {
            config.clearSessionOverrides();
            com.openggf.game.session.SessionManager.clear();
        }
    }

    @Test void transformationPanStopsAtNewLimitWithoutReleasingTheCamera() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 1).build();
        var manager = GameServices.level().getObjectManager();
        var camera = GameServices.camera();
        camera.setX((short) 0x100); camera.setMinX((short) 0x100);
        camera.setMaxX((short) 0x240); camera.setY((short) 0x400);
        camera.setHorizScrollDelay(7);
        var pan = new SszMechaArenaPan(new ObjectSpawn(0, 0, 0, 0, 0, false, 0));
        pan.setServices(TestEnvironment.objectServices()); manager.addDynamicObject(pan);
        pan.update(0, null);
        assertEquals(0x100, camera.getX(), "init locks but does not move");
        for (int i = 0; i < 53; i++) {
            fixture.sprite().setXSpeed((short) 0x100);
            fixture.sprite().setYSpeed((short) 0x200);
            fixture.sprite().setGSpeed((short) 0x300);
            pan.update(i, null);
            assertEquals(0, fixture.sprite().getXSpeed());
            assertEquals(0, fixture.sprite().getYSpeed());
            assertEquals(0, fixture.sprite().getGSpeed());
        }
        assertEquals(0x23E, camera.getX());
        assertFalse(pan.isDestroyed());
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        pan.update(54, null);
        assertTrue(pan.isDestroyed());
        assertEquals(0x240, camera.getX(), "overshooting six-pixel step clamps");
        assertTrue(camera.getFrozen());
        assertEquals(7, camera.getHorizScrollDelay());
        assertEquals(0x400, camera.getY());
        assertEquals(0x100, camera.getMinX());
        registry.restore(saved);
        pan = manager.activeObjectsOfType(SszMechaArenaPan.class).getFirst();
        assertEquals(0x23E, camera.getX());
        pan.update(54, null);
        assertTrue(pan.isDestroyed());
        assertEquals(0x240, camera.getX());
    }

    @Test void firstDefeatWaitsForChargeChildAndReplaysItsCallbackAfterRestore() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 1).build();
        var arenaCamera = GameServices.camera();
        arenaCamera.setX((short) 0x100); arenaCamera.setMinX((short) 0x100);
        arenaCamera.setMaxX((short) 0x100); arenaCamera.setY((short) 0x400);
        var manager = GameServices.level().getObjectManager();
        var boss = new SszMechaSonicObjectInstance(new ObjectSpawn(0, 0, 0, 0, 0, false, 0));
        boss.setServices(TestEnvironment.objectServices());
        manager.addDynamicObject(boss);
        boss.update(0, fixture.sprite());
        boss.onDefeatStarted();
        assertTrue(TestEnvironment.objectServices().levelGamestate().isTimerPaused());
        for (int i = 0; i < 128; i++) boss.update(i, fixture.sprite());
        assertEquals(2, boss.routineForTest());
        for (int i = 0; i < 60 && boss.routineForTest() == 2; i++) boss.update(i, fixture.sprite());
        assertEquals(8, boss.routineForTest(), "act2 takes loc_7B8E6, not act1 results");
        assertEquals(0xBF, boss.timerForTest());
        assertTrue(boss.stopsDefeatExplosions());
        assertFalse(((SszZoneRuntimeState) GameServices.zoneRuntimeState()).mechaSonicBeaten());
        assertTrue(manager.activeObjectsOfType(SszMechaSonicActEndObjectInstance.class).isEmpty());
        for (int i = 0; i < 191; i++) boss.update(i, fixture.sprite());
        assertEquals(8, boss.routineForTest());
        boss.update(191, fixture.sprite());
        assertEquals(0xA, boss.routineForTest());
        assertFalse(TestEnvironment.objectServices().levelGamestate().isTimerPaused());
        var charge = manager.activeObjectsOfType(SszMechaSonicChargeChild.class).getFirst();
        assertTrue(charge.getSlotIndex() > boss.getSlotIndex());
        for (int i = 0; i < 50; i++) charge.update(i, fixture.sprite());
        assertEquals(0x13, charge.mappingFrameForTest());
        boss.update(192, fixture.sprite());
        assertEquals(0xA, boss.routineForTest(), "child still owns bit6");
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        for (int i = 50; i < 118; i++) charge.update(i, fixture.sprite());
        assertFalse(charge.followingForTest(), "ROM pair delays hold through update117");
        charge.update(118, fixture.sprite());
        assertTrue(charge.followingForTest());
        boss.update(193, fixture.sprite());
        assertEquals(0xC, boss.routineForTest());
        registry.restore(saved);
        boss = manager.activeObjectsOfType(SszMechaSonicObjectInstance.class).getFirst();
        charge = manager.activeObjectsOfType(SszMechaSonicChargeChild.class).getFirst();
        assertEquals(0xA, boss.routineForTest());
        assertFalse(charge.followingForTest());
        for (int i = 50; i <= 118; i++) charge.update(i, fixture.sprite());
        boss.update(193, fixture.sprite());
        assertEquals(0xC, boss.routineForTest());
        assertEquals(0xE, boss.mappingFrameForTest());
        charge.update(119, fixture.sprite());
        assertFalse(charge.isDestroyed());
        assertEquals(boss.getX() - 7, charge.getX());
        for (int i = 0; i < 40 && boss.routineForTest() == 0xC; i++) boss.update(i, fixture.sprite());
        assertEquals(0xE, boss.routineForTest());
        assertEquals(0x3B, boss.timerForTest());
        assertEquals(2, boss.trailForTest().size());
        charge.update(120, fixture.sprite());
        assertTrue(charge.isDestroyed(), "standing glow expires when Mecha changes frame");
        for (int i = 0; i < 59; i++) boss.update(i, fixture.sprite());
        assertEquals(0xE, boss.routineForTest());
        boss.update(59, fixture.sprite());
        assertEquals(0x10, boss.routineForTest());
        assertEquals(0x240, arenaCamera.getMaxX());
        assertEquals(0x340, ((SszZoneRuntimeState) GameServices.zoneRuntimeState()).bossRightX());
        assertEquals(0x600, boss.xVelForTest());
        var pan = manager.activeObjectsOfType(SszMechaArenaPan.class).getFirst();
        var emerald = manager.activeObjectsOfType(SszMasterEmerald.class).getFirst();
        pan.update(0, fixture.sprite()); emerald.update(0, fixture.sprite());
        assertEquals(0x340, emerald.getX());
        for (int i = 0; i < 180 && boss.routineForTest() != 0x16; i++) {
            boss.update(i, fixture.sprite());
            if (!pan.isDestroyed()) pan.update(i, fixture.sprite());
        }
        assertEquals(0x16, boss.routineForTest(), "rush, skid and jump reach the native emerald platform");
        // $220 + 20*6 + sum($600-$30*n, n=1..24)/256 + 52*1.5 = $33D.C000.
        assertEquals(0x33D, boss.getX()); assertEquals(0x474, boss.getY());
        assertEquals(0x240, arenaCamera.getX()); assertEquals(0x1A0, arenaCamera.getMinX());
        var runtime = (SszZoneRuntimeState) GameServices.zoneRuntimeState();
        assertEquals(0x1B0, runtime.act2AttackPosition(0));
        assertEquals(0x2D0, runtime.act2AttackPosition(4));
        assertEquals(0x370, runtime.act2AttackPosition(6));
        assertTrue(arenaCamera.getFrozen(), "later forced-player run owns camera release");
        for (int i = 0; i < 60 && boss.routineForTest() != 0x18; i++) boss.update(i, fixture.sprite());
        assertEquals(0x18, boss.routineForTest());
        fixture.sprite().setCentreX((short) 0x2DF);
        boss.update(0, fixture.sprite());
        assertEquals(0x18, boss.routineForTest(), "one pixel short retains scripted input");
        assertTrue(fixture.sprite().isControlLocked());
        assertTrue(fixture.sprite().isRightPressed());
        var releaseSave = registry.capture();
        fixture.sprite().setCentreX((short) 0x2E0);
        boss.update(1, fixture.sprite());
        assertEquals(2, boss.routineForTest());
        assertFalse(arenaCamera.getFrozen());
        assertFalse(fixture.sprite().isControlLocked());
        assertEquals(8, boss.getCollisionProperty());
        registry.restore(releaseSave);
        boss = manager.activeObjectsOfType(SszMechaSonicObjectInstance.class).getFirst();
        assertEquals(0x18, boss.routineForTest());
        fixture.sprite().setCentreX((short) 0x2E0); boss.update(1, fixture.sprite());
        assertEquals(2, boss.routineForTest(), "restored code pointer takes the same Super entry");
        runtime.setBossCeilingY(0x430);
        for (int i = 0; i < 160 && boss.routineForTest() != 8; i++) boss.update(i, fixture.sprite());
        assertEquals(8, boss.routineForTest(), "charge, rise and animation reach the screen flash");
        assertEquals(0x430, boss.getY());
        assertEquals(0x3F, boss.timerForTest());
        assertEquals(1, manager.activeObjectsOfType(SszMechaScreenFlash.class).size());
        for (int i = 0; i < 64; i++) boss.update(i, fixture.sprite());
        assertEquals(0xA, boss.routineForTest());
        var dashSave = registry.capture();
        int dashStartX = boss.getX();
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) {
                registry.restore(dashSave);
                boss = manager.activeObjectsOfType(SszMechaSonicObjectInstance.class).getFirst();
            }
            for (int i = 0; i < 15; i++) boss.update(i, fixture.sprite());
            assertEquals(0xA, boss.routineForTest());
            assertEquals(dashStartX - 60, boss.getX(), "sum of fifteen half-pixel acceleration steps");
            boss.update(15, fixture.sprite());
            assertEquals(0xC, boss.routineForTest());
            assertEquals(-0x800, boss.xVelForTest());
            assertEquals(dashStartX - 60, boss.getX(), "speed cap switches routine without movement");
        }
        var glow = manager.activeObjectsOfType(SszMechaSuperGlow.class).getFirst();
        assertEquals(0x12, boss.mappingFrameForTest());
        glow.update(0, fixture.sprite()); glow.update(1, fixture.sprite());
        assertEquals(2, manager.activeObjectsOfType(SszMechaSuperParticle.class).size(),
                "native BTST has no branch: both V-int parities allocate");
        assertEquals(boss.getX() + 0x14, glow.getX());
        var sparkleSave = registry.capture();
        var sparkle = manager.activeObjectsOfType(SszMechaSuperParticle.class).getFirst();
        sparkle.update(0, fixture.sprite());
        int sparkleX = sparkle.getX(), sparkleY = sparkle.getY();
        registry.restore(sparkleSave);
        boss = manager.activeObjectsOfType(SszMechaSonicObjectInstance.class).getFirst();
        glow = manager.activeObjectsOfType(SszMechaSuperGlow.class).getFirst();
        sparkle = manager.activeObjectsOfType(SszMechaSuperParticle.class).getFirst();
        sparkle.update(0, fixture.sprite());
        assertEquals(sparkleX, sparkle.getX()); assertEquals(sparkleY, sparkle.getY());
        for (int i = 1; i < 20; i++) sparkle.update(i, fixture.sprite());
        assertTrue(sparkle.isDestroyed());
        for (int i = 0; i < 70 && boss.routineForTest() != 0x10; i++) boss.update(i, fixture.sprite());
        assertEquals(0x10, boss.routineForTest());
        assertEquals(7, boss.timerForTest());
        glow.update(2, fixture.sprite());
        assertTrue(glow.isDestroyed(), "dash completion clears parent bit6");
        // Seed once at the graph boundary; Random_Number(1) returns41, selecting the
        // projectile family after the native sector check rejects a direct dive.
        TestEnvironment.objectServices().rng().setSeed(1);
        var seen = new java.util.TreeSet<Integer>();
        int peakProjectiles = 0;
        for (int i = 0; i < 900 && boss.routineForTest() != 0; i++) {
            TestEnvironment.objectServices().paletteOwnershipRegistryOrNull().beginFrame();
            boss.update(i, fixture.sprite());
            seen.add(boss.routineForTest());
            var shots = java.util.List.copyOf(manager.activeObjectsOfType(SszMechaProjectile.class));
            peakProjectiles = Math.max(peakProjectiles, shots.size());
            for (var shot : shots) if (!shot.isDestroyed()) shot.update(i, fixture.sprite());
        }
        assertEquals(0, boss.routineForTest(), "projectile attack must return to emerald: " + seen);
        assertTrue(seen.containsAll(java.util.List.of(0x28, 0x2A, 0x2C, 0x2E, 0x30, 0x32, 0x34, 0x36, 0x38, 0x3A)),
                "all recharge stages execute: " + seen);
        assertTrue(peakProjectiles >= 1, "connected hover allocates its charge children");
        boss.onDefeatStarted(); // component boundary: killing-hit authority is covered separately.
        assertEquals(arenaCamera.getX(), arenaCamera.getMinX());
        assertEquals(arenaCamera.getX(), arenaCamera.getMaxX());
        for (int i = 0; i < 200 && boss.finalDefeatStageForTest() == 0; i++) boss.update(i, fixture.sprite());
        assertEquals(1, boss.finalDefeatStageForTest(), "second defeat bypasses the first transformation");
        assertEquals(3, manager.activeObjectsOfType(SszMechaDefeatRunner.class).size());
        assertEquals(0xBF, boss.timerForTest());
        for (int i = 0; i < 192; i++) boss.update(i, fixture.sprite());
        assertEquals(2, boss.finalDefeatStageForTest()); assertEquals(0x1F, boss.timerForTest());
        assertEquals(16, manager.activeObjectsOfType(SszMechaDebris.class).size());
        for (int i = 0; i < 32; i++) boss.update(i, fixture.sprite());
        assertEquals(3, boss.finalDefeatStageForTest());
        var finalFade = manager.activeObjectsOfType(SszMechaScreenFlash.class).stream()
                .filter(f -> f.getSpawn().subtype() == 1).findFirst().orElseThrow();
        for (int i = 0; i < 30; i++) {
            TestEnvironment.objectServices().paletteOwnershipRegistryOrNull().beginFrame();
            boss.update(i, fixture.sprite()); finalFade.update(i, fixture.sprite());
        }
        assertEquals(3, boss.finalDefeatStageForTest());
        var endingSave = registry.capture();
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) {
                registry.restore(endingSave);
                boss = manager.activeObjectsOfType(SszMechaSonicObjectInstance.class).getFirst();
                finalFade = manager.activeObjectsOfType(SszMechaScreenFlash.class).stream()
                        .filter(f -> f.getSpawn().subtype() == 1).findFirst().orElseThrow();
            }
            for (int i = 30; i <= 56; i++) {
                TestEnvironment.objectServices().paletteOwnershipRegistryOrNull().beginFrame();
                boss.update(i, fixture.sprite()); finalFade.update(i, fixture.sprite());
            }
            assertTrue(finalFade.nativeFadeCompleted());
            assertEquals(3, boss.finalDefeatStageForTest(), "earlier boss slot observes completion on its next pass");
            boss.update(57, fixture.sprite());
            assertEquals(4, boss.finalDefeatStageForTest()); assertEquals(119, boss.timerForTest());
            runtime = (SszZoneRuntimeState) GameServices.zoneRuntimeState();
            assertTrue(runtime.act2EndingActive()); assertEquals(0xFF, runtime.eventsFg4Low());
            assertTrue(fixture.sprite().isObjectControlled());
            assertEquals(0, fixture.sprite().getMappingFrame());
            for (int i = 0; i < 119; i++) boss.update(i, fixture.sprite());
            assertEquals(0, boss.timerForTest(), "accepted cold stop before ending allocation");
        }





    }

    @Test void firstDefeatCreatesNativeRepeatingExplosionWorker() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 1).build();
        var manager = GameServices.level().getObjectManager();
        var boss = new SszMechaSonicObjectInstance(new ObjectSpawn(0, 0, 0, 0, 0, false, 0));
        boss.setServices(TestEnvironment.objectServices());
        manager.addDynamicObject(boss);
        boss.update(0, fixture.sprite());
        assertFalse(boss.stopsDefeatExplosions());
        boss.onDefeatStarted();
        assertTrue(TestEnvironment.objectServices().levelGamestate().isTimerPaused());
        assertEquals(1, manager.activeObjectsOfType(SszBossExplosionController.class).size());
        var worker = manager.activeObjectsOfType(SszBossExplosionController.class).getFirst();
        assertTrue(worker.getSlotIndex() > boss.getSlotIndex(), "CreateChild1_Normal searches forward");
        assertFalse(boss.stopsDefeatExplosions(), "explosions continue until the defeated body lands");
    }

    @Test void waitsForCranePanBeforeChoosingArenaBoundsAndAttackCycle() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 1).build();
        var manager = GameServices.level().getObjectManager();
        var runtime = (SszZoneRuntimeState) GameServices.zoneRuntimeState();
        var camera = GameServices.camera();
        camera.setX((short) 0);
        camera.setY((short) 0x400);
        var boss = new SszMechaSonicObjectInstance(new ObjectSpawn(0, 0, 0, 0, 0, false, 0));
        boss.setServices(TestEnvironment.objectServices());
        manager.addDynamicObject(boss);
        boss.update(0, fixture.sprite());
        assertEquals(2, boss.routineForTest());
        assertEquals(0x220, boss.getX());
        assertEquals(0x4A0, boss.getY());
        assertEquals(0, boss.xVelForTest());
        assertEquals(0, boss.mappingFrameForTest());
        assertEquals(8, boss.getCollisionProperty());
        assertTrue(boss.trailForTest().isEmpty());
        assertTrue(manager.activeObjectsOfType(SongFadeTransitionInstance.class).isEmpty(),
                "the crane owns act-2 music");
        for (int update = 0; update < 16; update++) boss.update(update, fixture.sprite());
        assertEquals(2, boss.routineForTest());
        assertEquals(0x220, boss.getX());
        assertEquals(0, runtime.bossRightX(), "the final camera position is not known yet");
        camera.setX((short) 0x100);
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        runtime.setCutsceneFlag(4);
        boss.update(17, fixture.sprite());
        assertEquals(0x14, boss.routineForTest());
        assertEquals(2, boss.attackCounterForTest());
        assertEquals(0x120, runtime.bossLeftX());
        assertEquals(0x220, runtime.bossRightX());
        assertEquals(0x430, runtime.bossCeilingY());
        registry.restore(saved);
        var restored = manager.activeObjectsOfType(SszMechaSonicObjectInstance.class).getFirst();
        assertEquals(2, restored.routineForTest());
        ((SszZoneRuntimeState) GameServices.zoneRuntimeState()).setCutsceneFlag(4);
        restored.update(17, fixture.sprite());
        assertEquals(0x14, restored.routineForTest());
        assertEquals(2, restored.attackCounterForTest());
        assertEquals(0x220, restored.getX());
    }
}
