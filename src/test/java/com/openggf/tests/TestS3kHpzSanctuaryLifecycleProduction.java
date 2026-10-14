package com.openggf.tests;

import com.openggf.configuration.WidescreenAspect;
import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.sprites.managers.SpriteManagerInternalAccess;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.objects.HPZSanctuaryFallingCrystalObjectInstance;
import com.openggf.game.sonic3k.objects.HPZSuperEmeraldObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** $1701 ceremony is a separate subscene, not the linear $1601 Hidden Palace act. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kHpzSanctuaryLifecycleProduction {
    private final CanonicalEntryMatrixSupport support = new CanonicalEntryMatrixSupport() {
        @Override TestS3kCanonicalEntryLifecycle.Act binding() {
            return new TestS3kCanonicalEntryLifecycle.Act("HPZ-sanctuary", 0x17, 1);
        }
    };

    @BeforeEach void saveConfiguration() { support.saveConfiguration(); }
    @AfterEach void restoreConfiguration() { support.restoreConfiguration(); }

    static Stream<Arguments> finiteCeremonyProducts() {
        // Both actual controller branches apply the raw midpoint pose; one
        // converts seven Chaos Emeralds, the other releases without conversion.
        return CanonicalEntryMatrixSupport.configurations().flatMap(row ->
                Stream.of(Arguments.of(row, false), Arguments.of(row, true)));
    }

    @ParameterizedTest(name = "sanctuary ceremony / lifetime / resources {0} conversion={1}")
    @MethodSource("finiteCeremonyProducts")
    void ceremonyUsesItsLiteralNativeResourcesAndReplaysBeforeActiveAndReleasedWorlds(
            CanonicalEntryMatrixSupport.Row row, boolean conversion) throws Exception {
        verifyCeremony(row, conversion);
    }

    static Stream<CanonicalEntryMatrixSupport.Row> nativeP2CharacterIdentities() {
        return Stream.of(new String[]{"sonic", "tails"}, new String[]{"tails", "tails,tails"},
                new String[]{"knuckles", "knuckles,knuckles"})
                .map(value -> new CanonicalEntryMatrixSupport.Row(value[0], WidescreenAspect.NATIVE_4_3,
                        "off", value[1]));
    }

    @ParameterizedTest(name = "registered native P2 character / duplicate identity {0}")
    @MethodSource("nativeP2CharacterIdentities")
    void nativeP2UsesItsCharacterIdentityWithoutBorrowingTheAdditionalFollowers(
            CanonicalEntryMatrixSupport.Row row) throws Exception {
        verifyCeremony(row, true);
    }

    private void verifyCeremony(CanonicalEntryMatrixSupport.Row row, boolean conversion) throws Exception {
        var fixture = support.boot(row);
        var player = GameServices.sprites().getMainPlayable();
        var rules = player.getGameRules();
        var physics = player.getPhysicsProfile();
        var poweredController = player.getSuperStateController();
        var configured = row.donor().equals("off")
                ? (PlayerSpriteArtProvider) GameServices.level().getGame()
                : CrossGameFeatureProvider.getInstance();
        // Declared conversion/no-conversion progress before the actual
        // controller's first dispatch. No routine/timer/position is supplied.
        var initial = conversion ? List.of(1,1,1,1,1,1,1) : List.of(0,0,0,0,0,0,0);
        GameServices.gameState().restoreS3kEmeraldProgress(initial, false);
        // loc_1BF74's native $A0 snap generalizes to the supported free-camera
        // screen-center contract. Explicit choreography targets remain ROM words.
        assertEquals(row.aspect().pixelWidth() / 2,
                player.getCentreX() - GameServices.camera().getX(), "resolved free-camera snap center");
        if (row.aspect().pixelWidth() == 320) assertEquals(0x15A0, GameServices.camera().getX() & 0xFFFF);
        // Fresh Get_LevelSizeStart clamps the $1701 row to $320. The
        // separate SpecialStage_Results backdrop explicitly writes $240.
        assertEquals(0x320, GameServices.camera().getY() & 0xFFFF);
        assertFalse(GameServices.level().isTitleCardRequested(), "$1701 has no level title");
        var registry = fixture.gameplayMode().getRewindRegistry();
        var unbound = registry.capture();
        assertFalse(SpriteManagerInternalAccess.hasBorrowedNativePlayerArt(GameServices.sprites(), player));
        advance(fixture, row, 1);
        var intro = registry.capture();
        assertEquals(0x1640, player.getCentreX() & 0xFFFF, "sub_90A4C");
        assertEquals(0x3A3, player.getCentreY() & 0xFFFF, "sub_90A4C");
        assertTrue(player.isObjectControlled());
        assertTrue(player.isObjectMappingFrameControl());
        assertEquals(0, player.getMappingFrame());
        replayTwice(fixture, row, "intro countdown", 45);

        int literalPose = switch (row.main()) {
            case "tails" -> 0xAD;
            case "knuckles" -> 0x56;
            default -> 0xBA;
        };
        int elapsed = 0;
        while (player.getMappingFrame() != literalPose && elapsed++ < 200) advance(fixture, row, 1);
        assertEquals(literalPose, player.getMappingFrame(), "sub_90E9A literal native pose");
        var host = (PlayerSpriteArtProvider) GameServices.level().getGame();
        TestS3kCanonicalEntryLifecycle.assertRendererSource(player.getSpriteRenderer(),
                host.loadPlayerSpriteArt(row.main()));
        TestS3kCanonicalEntryLifecycle.assertPaletteSource(player.getSpriteRenderer(),
                host.loadCharacterPalette(row.main()));
        assertSame(rules, player.getGameRules(), "script borrows presentation, not movement rules");
        assertSame(physics, player.getPhysicsProfile());
        assertSame(poweredController, player.getSuperStateController());
        assertTrue(GameServices.level().getObjectManager()
                .activeObjectsOfType(HPZSanctuaryFallingCrystalObjectInstance.class).stream()
                .anyMatch(object -> object.getSpawn().subtype() == 7), "actual intro crystal is still live");
        for (var participant : nativeParticipants()) {
            int expected = switch (participant.characterKey().persisted()) {
                case "tails" -> 0xAD;
                case "knuckles" -> 0x56;
                default -> 0xBA;
            };
            assertEquals(expected, participant.getMappingFrame(),
                    "sub_90EAE reads character_id for " + participant.getCode());
            assertTrue(SpriteManagerInternalAccess.hasBorrowedNativePlayerArt(GameServices.sprites(), participant));
            TestS3kCanonicalEntryLifecycle.assertRendererSource(participant.getSpriteRenderer(),
                    host.loadPlayerSpriteArt(participant.characterKey().persisted()));
        }
        GameServices.sprites().getRegisteredSidekicks().stream().skip(1).forEach(participant -> {
            assertFalse(SpriteManagerInternalAccess.hasBorrowedNativePlayerArt(GameServices.sprites(), participant),
                    "native Player_1/Player_2 script does not borrow additional configured followers");
            assertFalse(participant.isObjectMappingFrameControl());
        });
        replayThroughNaturalIntroCrystalExpiry(fixture, row);

        elapsed = 0;
        while (player.isObjectControlled() && elapsed++ < 1100) advance(fixture, row, 1);
        assertFalse(player.isObjectControlled(), "the actual ceremony releases within its native sequence");
        assertFalse(player.isObjectMappingFrameControl());
        assertFalse(player.isHidden());
        assertEquals(conversion ? List.of(2,2,2,2,2,2,2) : initial,
                GameServices.gameState().getS3kEmeraldStates());
        assertEquals(conversion ? 7 : 0, GameServices.level().getObjectManager()
                .activeObjectsOfType(HPZSuperEmeraldObjectInstance.class).size());
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream()
                .noneMatch(object -> object instanceof com.openggf.game.sonic3k.objects.HPZSanctuarySmallEmeraldCeremonyObjectInstance),
                "the ceremony graph must naturally retire, and no-conversion never creates its orbit");
        advance(fixture, row, 2); // The ordinary animation publisher returns configured resources.
        for (var participant : nativeParticipants()) {
            assertFalse(SpriteManagerInternalAccess.hasBorrowedNativePlayerArt(GameServices.sprites(), participant),
                    "ordinary mapping publication returned configured resources for " + participant.getCode());
            TestS3kCanonicalEntryLifecycle.assertRendererSource(participant.getSpriteRenderer(),
                    configured.loadPlayerSpriteArt(participant.characterKey().persisted()));
        }
        replayTwice(fixture, row, "released sanctuary", 45);
        var released = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(unbound);
            fixture.runner().primeInputState(new Bk2FrameInput(0,0,0,false,""));
            TestS3kCanonicalEntryLifecycle.assertSameWorld(unbound, registry.capture(), "pre-owner reverse restore");
            assertFalse(SpriteManagerInternalAccess.hasBorrowedNativePlayerArt(GameServices.sprites(), player));
            advance(fixture, row, 1);
            TestS3kCanonicalEntryLifecycle.assertSameWorld(intro, registry.capture(), "fresh literal owner replay", 1);
            registry.restore(released);
            TestS3kCanonicalEntryLifecycle.assertSameWorld(released, registry.capture(), "released owner reverse restore");
            assertFalse(SpriteManagerInternalAccess.hasBorrowedNativePlayerArt(GameServices.sprites(), player));
        }
    }

    private static List<AbstractPlayableSprite> nativeParticipants() {
        return GameServices.level().getObjectManager().getObjectServices().playerQuery()
                .playersFor(com.openggf.level.objects.ObjectPlayerParticipationPolicy.NATIVE_P1_P2).stream()
                .map(AbstractPlayableSprite.class::cast).toList();
    }

    private static HPZSanctuaryFallingCrystalObjectInstance introCrystal() {
        return GameServices.level().getObjectManager()
                .activeObjectsOfType(HPZSanctuaryFallingCrystalObjectInstance.class).stream()
                .filter(object -> object.getSpawn().subtype() == 7).findFirst().orElse(null);
    }

    private void replayThroughNaturalIntroCrystalExpiry(HeadlessTestFixture fixture,
                                                       CanonicalEntryMatrixSupport.Row row) {
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        var previous = introCrystal();
        assertNotNull(previous);
        int elapsed = 0;
        while (introCrystal() != null && elapsed++ < 120) advance(fixture, row, 1);
        assertNull(introCrystal(), "the native intro crystal naturally leaves the actual manager");
        assertTrue(elapsed > 0 && elapsed <= 120);
        var after = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before);
            fixture.runner().primeInputState(new Bk2FrameInput(0,0,0,false,""));
            TestS3kCanonicalEntryLifecycle.assertSameWorld(before, registry.capture(), "crystal recreation restore");
            var restored = introCrystal();
            assertNotNull(restored);
            assertNotSame(previous, restored, "naturally expired graph child must be recreated");
            previous = restored;
            support.assertOwners(row);
            advance(fixture, row, elapsed);
            assertNull(introCrystal());
            TestS3kCanonicalEntryLifecycle.assertSameWorld(after, registry.capture(), "natural crystal expiry replay", elapsed);
        }
    }

    private void advance(HeadlessTestFixture fixture, CanonicalEntryMatrixSupport.Row row, int frames) {
        int lives = GameServices.gameState().getLives();
        for (int frame = 0; frame < frames; frame++) {
            fixture.stepIdleFrames(1);
            assertFalse(GameServices.sprites().getMainPlayable().getDead(), row.toString());
            assertEquals(lives, GameServices.gameState().getLives(), "no hidden P1 reload");
            support.assertOwners(row);
            for (var participant : nativeParticipants()) {
                if (!participant.isObjectMappingFrameControl()) continue;
                int pose = participant.getMappingFrame();
                boolean literal = switch (participant.characterKey().persisted()) {
                    case "tails" -> pose == 0 || pose == 0xAD || pose == 0xB0;
                    case "knuckles" -> pose == 0 || pose == 0x56 || pose == 0xD6;
                    default -> pose == 0 || pose == 0xBA || pose == 0xC4;
                };
                assertTrue(literal, () -> "native character_id pose for " + participant.getCode()
                        + "=" + Integer.toHexString(pose) + " " + row);
            }
        }
    }

    private void replayTwice(HeadlessTestFixture fixture, CanonicalEntryMatrixSupport.Row row,
                             String spot, int frames) {
        assertFalse(GameServices.level().hasPendingFreshLevelTransitionBoundary());
        assertFalse(GameServices.fade().hasPendingCompletion());
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        advance(fixture, row, frames);
        var after = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before);
            fixture.runner().primeInputState(new Bk2FrameInput(0,0,0,false,""));
            TestS3kCanonicalEntryLifecycle.assertSameWorld(before, registry.capture(),
                    row + "/" + spot + " restore=" + cycle, frames);
            support.assertOwners(row);
            advance(fixture, row, frames);
            TestS3kCanonicalEntryLifecycle.assertSameWorld(after, registry.capture(),
                    row + "/" + spot + " replay=" + cycle, frames);
        }
    }
}
