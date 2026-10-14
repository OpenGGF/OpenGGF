package com.openggf.tools;

import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.sprites.managers.SpriteManagerInternalAccess;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Actual capture boot must bind donor participants before ROM starts and native event parents. */
@RequiresRom(SonicGame.SONIC_3K)
class TestGameplayCaptureDonorEntry {
    private record Row(String main, String donor, String followers) { }

    static Stream<Row> configurations() {
        var rows = new LinkedHashSet<Row>();
        for (var donor : List.of("off", "s1", "s2")) {
            for (var main : List.of("sonic", "tails", "knuckles")) {
                if (donor.equals("s1") && !main.equals("sonic")
                        || donor.equals("s2") && main.equals("knuckles")) continue;
                var first = main.equals("tails") || donor.equals("s1") ? "sonic" : "tails";
                var second = donor.equals("off") && !main.equals("knuckles") ? "knuckles" : "sonic";
                for (var followers : List.of("", first, first + "," + second, first + "," + first,
                        first + "," + first + "," + second + ",sonic," + second + ",sonic," + first)) {
                    rows.add(new Row(main, donor, followers));
                }
            }
        }
        assertEquals(28, rows.size());
        return rows.stream();
    }

    @ParameterizedTest(name = "donor bound before native entry {0}")
    @MethodSource("configurations")
    void originalRosterOwnsRomStartCameraAndNativeIntroFromFirstAssembly(Row row) throws Exception {
        Path donorRom = row.donor().equals("off") ? null
                : (row.donor().equals("s1") ? RomTestUtils.ensureSonic1RomAvailable()
                        : RomTestUtils.ensureSonic2RomAvailable()).toPath();
        var settings = new GameplayCaptureSession.Settings(320, row.main(), row.followers(),
                row.donor(), donorRom, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 3, 0, settings);
            var player = session.player();
            var sprites = GameServices.sprites();
            assertSame(player, sprites.getMainPlayable());
            assertSame(player, GameServices.camera().getFocusedSprite());
            assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
            assertFalse(GameServices.level().hasPendingFreshLevelTransitionBoundary());
            assertEquals(!row.donor().equals("off"), CrossGameFeatureProvider.isActive());
            if (!row.donor().equals("off")) assertEquals(row.donor(),
                    GameServices.crossGameFeatures().getDonorGameId());
            int table = row.main().equals("knuckles") ? Sonic3kConstants.KNUX_START_LOCATIONS_ADDR
                    : Sonic3kConstants.SONIC_START_LOCATIONS_ADDR;
            int address = table + (3 * 2) * 4;
            var rom = GameServices.rom().getRom();
            assertEquals(rom.read16BitAddr(address), player.getCentreX() & 0xFFFF, "host ROM start X");
            assertEquals(rom.read16BitAddr(address + 2), player.getCentreY() & 0xFFFF, "host ROM start Y");
            assertRoster(row, player);
            int lives = GameServices.gameState().getLives();
            for (int frame = 0; frame < 4; frame++) {
                session.step(null);
                session.renderFrame();
                assertSame(player, sprites.getMainPlayable(), "late donor fallback must not replace P1");
                assertSame(player, GameServices.camera().getFocusedSprite());
                assertRoster(row, player);
                assertFalse(player.getDead());
                assertEquals(lives, GameServices.gameState().getLives());
            }
            if (row.main().equals("sonic") && row.followers().isEmpty()) {
                var carrier = sprites.getRegisteredSidekicks().getFirst();
                assertTrue(carrier.getTailsCarryController().isCarrying(player));
                assertTrue(player.isObjectControlled());
                assertEquals(0x91, player.getMappingFrame(), "native literal carried pose, not ordinary donor Animate");
                assertTrue(SpriteManagerInternalAccess.hasBorrowedNativePlayerArt(sprites, player));
            }
        }
    }

    private static void assertRoster(Row row, AbstractPlayableSprite player) {
        var sprites = GameServices.sprites();
        var followers = sprites.getRegisteredSidekicks();
        boolean npc = row.main().equals("sonic") && row.followers().isEmpty();
        String[] names = row.followers().isEmpty() ? new String[0] : row.followers().split(",");
        assertEquals(names.length + (npc ? 1 : 0), followers.size());
        var leader = player;
        for (int i = 0; i < followers.size(); i++) {
            var follower = followers.get(i);
            assertEquals(npc ? "tails" : names[i], follower.characterKey().persisted());
            assertTrue(follower.isCpuControlled());
            assertSame(leader, follower.getCpuController().getLeader(), "CPU references original current world roster");
            assertEquals(npc, SpriteManagerInternalAccess.usesNativeScriptedArt(sprites, follower));
            leader = follower;
        }
        var p2 = GameServices.level().getObjectManager().getObjectServices().playerQuery().nativeP2OrNull();
        if (followers.isEmpty()) assertNull(p2); else assertSame(followers.getFirst(), p2);
    }
}
