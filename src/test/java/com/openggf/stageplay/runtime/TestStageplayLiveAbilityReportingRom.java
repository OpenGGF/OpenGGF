package com.openggf.stageplay.runtime;

import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Simulation reports the performer's native capabilities, independently of the scenery game. */
class TestStageplayLiveAbilityReportingRom {
    @BeforeEach
    void logicGraphics() {
        EngineServices.current().graphics().initHeadless();
    }

    @Test
    @RequiresRom(SonicGame.SONIC_1)
    void nativeSonic1DoesNotReportInstaShield() throws Exception {
        assertReportedAbilities("s1", List.of(actor("s1", "sonic", 96)),
                Map.of("s1-sonic", "NONE"));
    }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void nativeSonic2CharactersDoNotReportSonic3Abilities() throws Exception {
        assertReportedAbilities("s2", List.of(actor("s2", "sonic", 96), actor("s2", "tails", 128)),
                Map.of("s2-sonic", "NONE", "s2-tails", "NONE"));
    }

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void nativeSonic3CharactersReportTheirOwnSecondaryAbilities() throws Exception {
        assertReportedAbilities("s3k", sonic3Cast(),
                Map.of("s3k-sonic", "INSTA_SHIELD", "s3k-tails", "FLY", "s3k-knuckles", "GLIDE"));
    }

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void olderGameDonorsDoNotBorrowSonic3SetAbilities() throws Exception {
        assertReportedAbilities("s3k", List.of(actor("s1", "sonic", 96), actor("s2", "tails", 128)),
                Map.of("s1-sonic", "NONE", "s2-tails", "NONE"));
    }

    @Test
    @RequiresRom(SonicGame.SONIC_1)
    void sonic3DonorsKeepTheirAbilitiesInSonic1Scenery() throws Exception {
        assertReportedAbilities("s1", sonic3Cast(),
                Map.of("s3k-sonic", "INSTA_SHIELD", "s3k-tails", "FLY", "s3k-knuckles", "GLIDE"));
    }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void sonic3DonorsKeepTheirAbilitiesInSonic2Scenery() throws Exception {
        assertReportedAbilities("s2", sonic3Cast(),
                Map.of("s3k-sonic", "INSTA_SHIELD", "s3k-tails", "FLY", "s3k-knuckles", "GLIDE"));
    }

    private static void assertReportedAbilities(String sceneryGame, List<Actor> actors,
            Map<String, String> expected) throws Exception {
        var roms = new LinkedHashMap<String, Path>();
        addRom(roms, sceneryGame);
        for (var actor : actors) addRom(roms, actor.gameId());
        var tracks = actors.stream().map(actor -> new PuppetTrack(actor.id(), false, 0, List.of())).toList();
        var scene = StageplayDocument.empty(sceneryGame, 0, 0, 0, 500, 320, 224)
                .withDurationFrames(2).withActors(actors).withPuppets(tracks);
        try (var library = SceneRomLibrary.open(roms);
             var world = StageplayLiveWorld.adoptOffline(library, SessionManager.getCurrentGameplayMode())) {
            for (int frame = 0; frame < scene.durationFrames(); frame++) {
                var sample = world.resolve(scene, StageplayTimeline.sample(scene, frame));
                var actual = sample.world().actors().stream().collect(Collectors.toMap(
                        StageplayTimeline.LiveActor::actorId, StageplayTimeline.LiveActor::ability));
                assertEquals(expected, actual,
                        "Native simulation capabilities in " + sceneryGame + " scenery at frame " + frame);
            }
        }
    }

    private static void addRom(Map<String, Path> roms, String game) {
        if (roms.containsKey(game)) return;
        String property = switch (game) {
            case "s1" -> "sonic1.rom.path";
            case "s2" -> "sonic2.rom.path";
            case "s3k" -> "s3k.rom.path";
            default -> throw new IllegalArgumentException("Unknown test game: " + game);
        };
        String path = System.getProperty(property);
        assumeTrue(path != null && !path.isBlank(), "Supply explicit ROM property " + property);
        roms.put(game, Path.of(path));
    }

    private static List<Actor> sonic3Cast() {
        return List.of(actor("s3k", "sonic", 96), actor("s3k", "tails", 128),
                actor("s3k", "knuckles", 160));
    }

    private static Actor actor(String game, String character, int x) {
        String id = game + "-" + character;
        return new Actor(id, id, game, character, List.of(
                new ActorKey(0, x, 655, 0, -1, Facing.RIGHT, true, 1, Interpolation.HOLD)));
    }
}
