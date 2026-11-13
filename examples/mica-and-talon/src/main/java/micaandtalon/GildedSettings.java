package micaandtalon;

import com.openggf.audio.GameMusic;
import com.openggf.game.*;
import com.openggf.game.patch.*;
import com.openggf.mods.mutators.*;

import java.util.List;
import java.util.Set;

/** MutatorDefinition and GamePatch for the optional load-scoped Gilded settings screen. */
final class GildedSettings {
    static MutatorDefinition definition() {
        var scope = MutatorScope.LOAD;
        return new MutatorDefinition(
                "gilded",
                "Gilded form settings",
                "Override MICA's powered-form rules on the next full load.",
                scope,
                scope,
                List.of(
                        new MutatorOption.Checkbox(
                                "s1",
                                "Sonic 1 Gilded",
                                "Mod-defined form; no donor ROM needed.",
                                scope,
                                true),
                        new MutatorOption.Checkbox(
                                "s2", "Sonic 2 Gilded", "Native seven-emerald gate.", scope, true),
                        new MutatorOption.Checkbox(
                                "s3k",
                                "Sonic 3K Gilded",
                                "Native Chaos/Super Emerald gates.",
                                scope,
                                true),
                        new MutatorOption.IntegerSlider(
                                "emeralds",
                                "S1 emerald requirement",
                                "Only changes the mod-defined S1 rule.",
                                scope,
                                6,
                                0,
                                6,
                                1,
                                ""),
                        new MutatorOption.IntegerSlider(
                                "rings",
                                "Ring requirement",
                                "Minimum rings to transform.",
                                scope,
                                50,
                                1,
                                999,
                                1,
                                ""),
                        new MutatorOption.Choice(
                                "music",
                                "S1 form music",
                                "S1 has no native Super cue.",
                                scope,
                                "invincibility",
                                List.of("invincibility", "special-stage"))),
                Set.of(MutatorCapability.CHARACTER_SUPER_FORM),
                options ->
                        List.of(
                                new CharacterSuperFormPolicy(
                                        "mica",
                                        options.checkbox("s1"),
                                        options.checkbox("s2"),
                                        options.checkbox("s3k"),
                                        options.integer("emeralds"),
                                        options.integer("rings"),
                                        options.choice("music").equals("invincibility")
                                                ? GameMusic.INVINCIBILITY
                                                : GameMusic.SPECIAL_STAGE)));
    }

    static final class Patch implements GamePatch {
        private final String game;

        Patch(String game) {
            this.game = game;
        }

        public String id() {
            return "mica-and-talon:gilded-" + game;
        }

        public String displayName() {
            return "MICA Gilded settings";
        }

        public String baseGameId() {
            return game;
        }

        public boolean activatesFor(GameplayLaunchRequest request) {
            return request.mainCharacter().equals("mica-and-talon:mica");
        }

        public List<String> providedMainCharacters() {
            return List.of();
        }

        public Set<LogicalRom> romPrerequisites() {
            return Set.of();
        }

        public GameModule apply(GameModule base, PatchContext context) {
            return new Module(base, id());
        }
    }

    private static final class Module extends DelegatingGameModule {
        @Override
        public boolean supportsSidekick() {
            return true;
        }

        private MutatorConfigurationScreen screen;

        Module(GameModule base, String id) {
            super(base, id);
        }

        private MutatorConfigurationScreen screen() {
            if (screen == null) {
                boolean s3k = getGameId() == GameId.S3K;
                screen =
                        MutatorConfigurationScreen.forCurrentWorld(
                                "MICA SETTINGS",
                                super.getTitleScreenProvider(),
                                s3k ? 0x5B : 0xCD,
                                s3k ? 0x33 : 0xB5,
                                s3k ? 0xB6 : getGameId() == GameId.S1 ? 0xCC : 0xBC,
                                s3k ? 0xB2 : getGameId() == GameId.S1 ? 0xC1 : 0xED);
            }
            return screen;
        }

        @Override
        public <T> T getGameService(Class<T> type) {
            if (type == MutatorSupportProfile.class)
                return type.cast(
                        new MutatorSupportProfile() {
                            public Set<MutatorCapability> capabilities(int zone, int act) {
                                return Set.of(MutatorCapability.CHARACTER_SUPER_FORM);
                            }

                            public boolean supportsPlayer(String key, boolean leader) {
                                return key.equals("mica-and-talon:mica");
                            }
                        });
            if (type == LevelInputOverlay.class) return type.cast(screen());
            return super.getGameService(type);
        }
    }
}
