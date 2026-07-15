package com.openggf.mods.code;

import com.openggf.game.CharacterKey;
import com.openggf.game.GameModule;
import com.openggf.game.GameplayInputFilter;
import com.openggf.game.GameplayLaunchTeam;
import com.openggf.game.GameplayPolicyProvider;
import com.openggf.game.ZoneKey;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.io.ModAssetRoot;
import com.openggf.level.objects.HudLabel;
import com.openggf.level.objects.HudMetric;
import com.openggf.level.objects.HudProfile;
import com.openggf.level.objects.HudRow;
import com.openggf.level.objects.HudWarningPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestModGameplayPolicyRegistration {
    @Test
    void transactionFreezesAllPoliciesByTaggedDestinationInInsertionOrder() {
        ZoneKey alphaSky = ZoneKey.mod("alpha", "sky");
        ZoneKey alphaClouds = ZoneKey.mod("alpha", "clouds");
        ModContext context = context("alpha");
        context.registerLaunchTeam(new ModLaunchTeamContribution(
                alphaSky, CharacterKey.TAILS, List.of()));
        context.registerLaunchTeam(new ModLaunchTeamContribution(
                alphaClouds, CharacterKey.SONIC, List.of(CharacterKey.TAILS)));
        context.registerInputFilter(new ModInputFilterContribution(
                alphaSky, GameplayInputFilter.IDENTITY));
        context.registerHudProfile(new ModHudProfileContribution(alphaSky, HudProfile.stock()));

        ModRegistrationPlan plan = context.freeze();

        assertEquals(List.of(alphaSky, alphaClouds), new ArrayList<>(plan.launchTeams().keySet()));
        assertEquals(new GameplayLaunchTeam(CharacterKey.TAILS, List.of()),
                plan.launchTeams().get(alphaSky));
        assertSame(GameplayInputFilter.IDENTITY, plan.inputFilters().get(alphaSky));
        assertEquals(HudProfile.stock(), plan.hudProfiles().get(alphaSky));
        assertThrows(UnsupportedOperationException.class,
                () -> plan.launchTeams().put((ZoneKey.Mod) ZoneKey.mod("alpha", "later"),
                        new GameplayLaunchTeam(CharacterKey.SONIC, List.of())));
        assertTrue(plan.hasContent());
    }

    @Test
    void immutablePolicyValuesDefensivelyCopyCreatorLists() {
        List<CharacterKey> sidekicks = new ArrayList<>(List.of(CharacterKey.TAILS));
        GameplayLaunchTeam team = new GameplayLaunchTeam(CharacterKey.SONIC, sidekicks);
        sidekicks.clear();

        List<HudRow> rows = new ArrayList<>(HudProfile.stock().rows());
        HudProfile profile = new HudProfile(rows);
        rows.clear();

        assertEquals(List.of(CharacterKey.TAILS), team.sidekicks());
        assertThrows(UnsupportedOperationException.class,
                () -> team.sidekicks().add(CharacterKey.KNUCKLES));
        assertEquals(4, profile.rows().size());
        assertThrows(UnsupportedOperationException.class, () -> profile.rows().clear());
    }

    @Test
    void stockHudProfileMatchesCurrentFourRows() {
        assertEquals(List.of(
                new HudRow(true, HudLabel.SCORE, HudMetric.SCORE,
                        16, 8, 64, 8, 6, HudWarningPolicy.NONE),
                new HudRow(true, HudLabel.TIME, HudMetric.TIME,
                        16, 24, 56, 24, 4, HudWarningPolicy.TIMER_FLASH),
                new HudRow(true, HudLabel.RINGS, HudMetric.RINGS,
                        16, 40, 64, 40, 3, HudWarningPolicy.ZERO_FLASH),
                new HudRow(true, HudLabel.LIVES, HudMetric.LIVES,
                        16, 200, 56, 208, 2, HudWarningPolicy.NONE)),
                HudProfile.stock().rows());
    }

    @Test
    void foreignZoneAndDuplicatePolicyPoisonTheWholeTransaction() {
        assertPoisoned(context("alpha"), ctx -> ctx.registerLaunchTeam(
                new ModLaunchTeamContribution(ZoneKey.mod("other", "sky"),
                        CharacterKey.TAILS, List.of())));

        ModContext duplicate = context("alpha");
        ModHudProfileContribution hud = new ModHudProfileContribution(
                ZoneKey.mod("alpha", "sky"), HudProfile.stock());
        duplicate.registerHudProfile(hud);
        assertPoisoned(duplicate, ctx -> ctx.registerHudProfile(hud));

        assertPoisoned(context("alpha"), ctx -> ctx.registerInputFilter(
                new ModInputFilterContribution(ZoneKey.stock(0), GameplayInputFilter.IDENTITY)));
    }

    @Test
    void moduleDefaultIsEmptyAndDelegatingModuleForwardsProvider() {
        GameModule stock = mock(GameModule.class, CALLS_REAL_METHODS);
        assertSame(GameplayPolicyProvider.EMPTY, stock.getGameplayPolicyProvider());
        assertTrue(stock.getGameplayPolicyProvider().launchTeam(ZoneKey.stock(0)).isEmpty());

        GameplayPolicyProvider provider = new GameplayPolicyProvider() {
            @Override public java.util.Optional<GameplayLaunchTeam> launchTeam(ZoneKey destination) {
                return java.util.Optional.of(new GameplayLaunchTeam(CharacterKey.TAILS, List.of()));
            }
            @Override public java.util.Optional<GameplayInputFilter> inputFilter(ZoneKey destination) {
                return java.util.Optional.of(GameplayInputFilter.IDENTITY);
            }
            @Override public java.util.Optional<HudProfile> hudProfile(ZoneKey destination) {
                return java.util.Optional.of(HudProfile.stock());
            }
        };
        GameModule base = mock(GameModule.class);
        when(base.getGameplayPolicyProvider()).thenReturn(provider);

        assertSame(provider, new DelegatingGameModule(base, "test") { }
                .getGameplayPolicyProvider());
    }

    @Test
    void providerMethodsDefaultToEmptyForSourceCompatiblePartialProviders() {
        GameplayPolicyProvider launchOnly = new GameplayPolicyProvider() {
            @Override public java.util.Optional<GameplayLaunchTeam> launchTeam(ZoneKey destination) {
                return java.util.Optional.of(new GameplayLaunchTeam(CharacterKey.TAILS, List.of()));
            }
        };

        assertTrue(launchOnly.inputFilter(ZoneKey.stock(0)).isEmpty());
        assertTrue(launchOnly.hudProfile(ZoneKey.stock(0)).isEmpty());
    }

    @Test
    void prePolicyRegistrationPlanConstructorDefaultsMapsToEmpty() {
        ModRegistrationPlan compatible = new ModRegistrationPlan(
                "alpha", "s3k", Map.of(), Map.of(), Map.of(), List.of(),
                List.of(), List.of(), Map.of(), Map.of(), null, Map.of());

        assertTrue(compatible.launchTeams().isEmpty());
        assertTrue(compatible.inputFilters().isEmpty());
        assertTrue(compatible.hudProfiles().isEmpty());
        assertFalse(compatible.hasContent());
    }

    @Test
    void canonicalPlanRejectsForeignPolicyKeysAndNullValues() {
        ZoneKey.Mod owned = (ZoneKey.Mod) ZoneKey.mod("alpha", "sky");
        ZoneKey.Mod foreign = (ZoneKey.Mod) ZoneKey.mod("other", "sky");
        GameplayLaunchTeam team = new GameplayLaunchTeam(CharacterKey.TAILS, List.of());

        assertThrows(IllegalArgumentException.class, () -> canonicalPlan(
                Map.of(foreign, team), Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> canonicalPlan(
                Map.of(), Map.of(foreign, GameplayInputFilter.IDENTITY), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> canonicalPlan(
                Map.of(), Map.of(), Map.of(foreign, HudProfile.stock())));

        Map<ZoneKey.Mod, GameplayLaunchTeam> nullTeams = new java.util.LinkedHashMap<>();
        nullTeams.put(owned, null);
        Map<ZoneKey.Mod, GameplayInputFilter> nullFilters = new java.util.LinkedHashMap<>();
        nullFilters.put(owned, null);
        Map<ZoneKey.Mod, HudProfile> nullProfiles = new java.util.LinkedHashMap<>();
        nullProfiles.put(owned, null);
        assertThrows(IllegalArgumentException.class, () -> canonicalPlan(
                nullTeams, Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> canonicalPlan(
                Map.of(), nullFilters, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> canonicalPlan(
                Map.of(), Map.of(), nullProfiles));
    }

    private static ModRegistrationPlan canonicalPlan(
            Map<ZoneKey.Mod, GameplayLaunchTeam> teams,
            Map<ZoneKey.Mod, GameplayInputFilter> filters,
            Map<ZoneKey.Mod, HudProfile> profiles) {
        return new ModRegistrationPlan("alpha", "s3k", Map.of(), Map.of(), Map.of(),
                List.of(), List.of(), List.of(), Map.of(), Map.of(), null, Map.of(),
                teams, filters, profiles);
    }

    private static ModContext context(String owner) {
        return new ModContext(owner, "s3k", ModAssetRoot.forTests(owner));
    }

    private static void assertPoisoned(ModContext context,
                                       java.util.function.Consumer<ModContext> invalidMutation) {
        ModRegistrationException first = assertThrows(ModRegistrationException.class,
                () -> invalidMutation.accept(context));
        assertSame(first, assertThrows(ModRegistrationException.class, context::freeze));
    }
}
