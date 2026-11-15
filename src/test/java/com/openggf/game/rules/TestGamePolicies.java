package com.openggf.game.rules;

import com.openggf.game.GameModule;
import com.openggf.game.PhysicsModifiers;
import com.openggf.game.PhysicsProfile;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.StandaloneGameSpec;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.kis2.Kis2GamePatch;
import com.openggf.game.sonic2.kis2.Kis2Rules;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.soniccd.SonicCdGameModule;
import com.openggf.game.soniccd.player.SonicCdRules;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Module-owned policy shape: every stock module states the same typed subtree. */
class TestGamePolicies {

    @Test
    void policiesRequireRules() {
        assertThrows(NullPointerException.class, () -> new GamePolicies(null));
    }

    @Test
    void everyStockModuleExposesStablePoliciesWithItsOwnRules() {
        Map<GameModule, GameRules> expected = new LinkedHashMap<>();
        expected.put(new Sonic1GameModule(), GameRules.SONIC_1);
        expected.put(new Sonic2GameModule(), GameRules.SONIC_2);
        expected.put(new Sonic3kGameModule(), GameRules.SONIC_3K);
        expected.put(new SonicCdGameModule(), SonicCdRules.RULES);
        expected.put(new Kis2GamePatch().apply(new Sonic2GameModule(), mock(PatchContext.class)),
                Kis2Rules.RULES);
        expected.forEach((module, rules) -> {
            GamePolicies policies = module.getPolicies();
            assertSame(GamePolicies.class, policies.getClass(), module.getIdentifier());
            assertSame(rules, policies.rules(), module.getIdentifier());
            assertSame(policies, module.getPolicies(), module.getIdentifier() + " policies are stable");
        });
    }

    @Test
    void stockModulesOfTheSameGameShareOneImmutableValue() {
        assertSame(new Sonic3kGameModule().getPolicies(), new Sonic3kGameModule().getPolicies());
        assertSame(new SonicCdGameModule().getPolicies(), new SonicCdGameModule().getPolicies());
    }

    @Test
    void delegatingModulesForwardTheBasePoliciesUnlessTheyShipARule() {
        GameModule base = new Sonic3kGameModule();
        GameModule forwarding = new DelegatingGameModule(base, "fixture:forward") { };
        assertSame(base.getPolicies(), forwarding.getPolicies());

        GamePolicies shipped = new GamePolicies(GameRules.SONIC_1);
        GameModule patch = new DelegatingGameModule(base, "fixture:patch") {
            @Override public GamePolicies getPolicies() { return shipped; }
        };
        assertSame(shipped, patch.getPolicies());
        assertSame(base.getPhysicsProvider(), patch.getPhysicsProvider(),
                "a rule patch does not have to wrap the physics provider");
    }

    @Test
    void standaloneSpecificationsOwnPoliciesSeparatelyFromPhysics() {
        StandaloneGameSpec.Builder builder = StandaloneGameSpec.builder("policyprobe")
                .zone("Z", new StandaloneGameSpec.Act(0x400, mock(com.openggf.level.Level.class), 0, 0,
                        com.openggf.game.MusicReference.stock(-1)));
        assertSame(GameRules.SONIC_2, builder.build().policies().rules(),
                "the default keeps the previous fixed-physics default rules");

        GamePolicies authored = new GamePolicies(GameRules.SONIC_1);
        StandaloneGameSpec spec = builder
                .physics(PhysicsProvider.fixed(PhysicsProfile.SONIC_2_SONIC, PhysicsModifiers.STANDARD))
                .policies(authored)
                .build();
        assertSame(authored, spec.policies());
        assertSame(authored, spec.module().getPolicies());
        assertThrows(NullPointerException.class, () -> builder.policies(null));
    }

    @Test
    void physicsProvidersCarryNoGameWideRules() {
        for (Method method : PhysicsProvider.class.getMethods()) {
            assertTrue(!GameRules.class.isAssignableFrom(method.getReturnType())
                            && !GamePolicies.class.isAssignableFrom(method.getReturnType()),
                    "PhysicsProvider must not return policy types: " + method);
            assertTrue(Arrays.stream(method.getParameterTypes())
                            .noneMatch(type -> type == GameRules.class || type == GamePolicies.class),
                    "PhysicsProvider must not accept policy types: " + method);
        }
    }

    @Test
    void policyComponentsArePinned() {
        List<String> components = Arrays.stream(GamePolicies.class.getRecordComponents())
                .map(component -> component.getName() + ":" + component.getType().getName())
                .toList();
        assertEquals(List.of("rules:" + GameRules.class.getName()), components,
                "adding a policy category is an architecture change; update the design record first");
        for (RecordComponent component : GamePolicies.class.getRecordComponents()) {
            assertTrue(component.getType().isRecord(), "policy components are immutable records");
        }
    }
}
