package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.TestFbzAct2TraversalPreboss;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import java.nio.file.Path;

/**
 * Prints one {@code EVIDENCE <row> RouteCompletionEvidence[...]} (or
 * {@code FAIL <row> <message>}) line per FBZ2 complete-route matrix row so a
 * refactor of the route controller or the shared route primitives can be
 * checked for byte-identical behaviour: run it before and after the change
 * and diff the eleven lines.
 *
 * <p>Inputs: the S3K ROM plus the S1/S2 donor ROMs for the donor rows; the
 * same team/width/donor configuration as {@code TestFbzCompatibilityMatrix}.
 * Each row is its own parameterized test: a red row fails that row and the
 * others still run. With {@code -Dopenggf.fbz.evidence.baseline=<file>} (the
 * {@code EVIDENCE} lines from a previous run) each row also asserts byte
 * identity against its baseline line.
 * Opt-in: {@code -Dmse=off -Dopenggf.fbz.evidence=true
 * -Dtest=FbzRouteEvidenceProbe}; about two minutes for all rows.
 *
 * <p>Origin: the byte-identity check used for the primitives extraction
 * (commit 610464952) and its review fixes (commit ad40500d7); it had been
 * re-derived as a scratch class twice before being kept.
 */
@RequiresRom(SonicGame.SONIC_3K)
@EnabledIfSystemProperty(named = "openggf.fbz.evidence", matches = "true")
class FbzRouteEvidenceProbe {

    static Stream<String> rows() {
        return Stream.of("team-solo", "team-tails", "team-tk", "team-tks", "team-sss",
                "width-400", "width-512", "width-640", "width-800", "donor-s1", "donor-s2");
    }

    @ParameterizedTest(name = "evidence: {0}")
    @MethodSource("rows")
    void printEvidence(String label) throws Exception {
        run(label, () -> {
            if (label.startsWith("team-")) {
                String sidekicks = switch (label) {
                    case "team-solo" -> ""; case "team-tails" -> "tails"; case "team-tk" -> "tails,knuckles";
                    case "team-tks" -> "tails,knuckles,sonic"; default -> "sonic,sonic,sonic"; };
                if (sidekicks.isEmpty()) configureCompatibility("", WidescreenAspect.NATIVE_4_3, "off", null);
                else configureNative(sidekicks, WidescreenAspect.NATIVE_4_3, 320);
                SessionManager.clear();
                TestEnvironment.activeGameplayMode();
                return TestFbzAct2TraversalPreboss
                        .runNativeStartFixedInputsReachSafeLateFrontierWithAllRouteMilestones(start -> {
                            if (sidekicks.equals("tails")) GameServices.graphics().setViewport(0, 0, 320, 224);
                        });
            }
            if (label.startsWith("width-")) {
                int width = Integer.parseInt(label.substring(6));
                WidescreenAspect aspect = width <= 512 ? WidescreenAspect.WIDE_16_9
                        : width == 640 ? WidescreenAspect.ULTRA_21_9 : WidescreenAspect.SUPER_32_9;
                configureNative("tails", aspect, width);
                SessionManager.clear();
                TestEnvironment.activeGameplayMode();
                return TestFbzAct2TraversalPreboss
                        .runNativeStartFixedInputsReachSafeLateFrontierWithAllRouteMilestones(start ->
                                GameServices.graphics().setViewport(0, 0, width, 224));
            }
            String donor = label.substring(6);
            java.io.File rom = donor.equals("s1") ? RomTestUtils.ensureSonic1RomAvailable()
                    : RomTestUtils.ensureSonic2RomAvailable();
            configureCompatibility("", WidescreenAspect.NATIVE_4_3, donor, rom == null ? null : rom.toPath());
            SessionManager.clear();
            TestEnvironment.activeGameplayMode();
            return TestFbzAct2TraversalPreboss
                    .runNativeStartFixedInputsReachSafeLateFrontierWithAllRouteMilestones(start -> { }, donor);
        });
    }

    private interface Row {
        Object call() throws Exception;
    }

    private static void run(String label, Row row) throws Exception {
        String line;
        try {
            line = "EVIDENCE " + label + " " + row.call();
            System.out.println(line);
        } catch (Throwable failure) {
            System.out.println("FAIL " + label + " " + failure.getMessage());
            throw new AssertionError("row " + label + " did not produce evidence", failure);
        } finally {
            CrossGameFeatureProvider.getInstance().resetState();
            SonicConfigurationService.getInstance().clearSessionOverrides();
        }
        String baseline = System.getProperty("openggf.fbz.evidence.baseline");
        if (baseline != null && !baseline.isBlank()) {
            String prefix = "EVIDENCE " + label + " ";
            String expected = java.nio.file.Files.readAllLines(java.nio.file.Path.of(baseline)).stream()
                    .filter(l -> l.startsWith(prefix)).findFirst()
                    .orElseThrow(() -> new AssertionError("baseline has no line for row " + label));
            org.junit.jupiter.api.Assertions.assertEquals(expected, line,
                    "row " + label + " evidence must be byte-identical to the baseline");
        } else {
            org.junit.jupiter.api.Assertions.assertTrue(line.startsWith("EVIDENCE " + label + " RouteCompletionEvidence["),
                    "row " + label + " must print a RouteCompletionEvidence record");
        }
    }

    private static void configureNative(String sidekicks, WidescreenAspect aspect, int pixelWidth) {
        CrossGameFeatureProvider.getInstance().resetState();
        SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        configuration.clearSessionOverrides();
        configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, sidekicks);
        configuration.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, aspect.name());
        configuration.resolveDisplayAspect();
        configuration.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, pixelWidth);
        configuration.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        configuration.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE, "off");
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    private static void configureCompatibility(
            String sidekicks, WidescreenAspect aspect, String donor, Path donorRom) throws Exception {
        CrossGameFeatureProvider provider = CrossGameFeatureProvider.getInstance();
        provider.resetState();
        SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        configuration.clearSessionOverrides();
        configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, sidekicks);
        configuration.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, aspect.name());
        configuration.resolveDisplayAspect();
        configuration.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, !donor.equals("off"));
        configuration.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE, donor);
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        if (!donor.equals("off")) {
            SonicConfiguration romKey = donor.equals("s1")
                    ? SonicConfiguration.SONIC_1_ROM : SonicConfiguration.SONIC_2_ROM;
            configuration.setSessionOverride(romKey, donorRom.toAbsolutePath().toString());
            provider.initialize(donor);
        }
    }
}
