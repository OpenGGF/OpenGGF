package com.openggf.game.rules;

import com.openggf.game.DonorCapabilities;
import com.openggf.game.GameModule;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.kis2.Kis2GamePatch;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.soniccd.SonicCdGameModule;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Donation composes whole policies through the capability declaration only. Every
 * host/donor pair the engine can launch is exercised with the donor module's real
 * {@link DonorCapabilities}.
 */
class TestCrossGamePolicyComposer {

    /** Capability fields a donor may supply; every other field stays the host's. */
    private static final Set<String> DONATED_CAPABILITY_FIELDS = Set.of(
            "spindashEnabled", "spindashSpeedTable", "elementalShieldsEnabled",
            "instaShieldEnabled", "tailsFlightEnabled", "lightningShieldEnabled",
            "glideAttacksEnabled");

    private static List<GameModule> hosts() {
        return List.of(new Sonic1GameModule(), new Sonic2GameModule(), new Sonic3kGameModule(),
                new SonicCdGameModule(),
                new Kis2GamePatch().apply(new Sonic2GameModule(), mock(PatchContext.class)));
    }

    private static List<GameModule> donors() {
        return List.of(new Sonic1GameModule(), new Sonic2GameModule(), new Sonic3kGameModule(),
                new SonicCdGameModule());
    }

    @Test
    void everyHostDonorPairImportsOnlyDeclaredPlayerCapabilities() throws Exception {
        int donatingPairs = 0;
        for (GameModule hostModule : hosts()) {
            for (GameModule donorModule : donors()) {
                if (hostModule.getGameId() == donorModule.getGameId()) {
                    continue; // same-game donation is rejected before composition
                }
                String pair = hostModule.getIdentifier() + " <- " + donorModule.getIdentifier();
                GamePolicies host = hostModule.getPolicies();
                GamePolicies donor = donorModule.getPolicies();
                DonorCapabilities capabilities = donorModule.getDonorCapabilities();

                GamePolicies composed = CrossGamePolicyComposer.donatePlayerCapabilities(
                        host, donor, capabilities);

                if (capabilities == null) {
                    assertSame(host, composed, pair + ": a game that cannot donate leaves the host intact");
                    continue;
                }
                donatingPairs++;
                assertHostGroupsKept(pair, host.rules(), composed.rules());
                assertCapabilities(pair, host.rules().playerCapability(), donor.rules().playerCapability(),
                        capabilities, composed.rules().playerCapability());
            }
        }
        // S1, S2 and S3K donate to every host of another game: S1 and S3K reach four hosts
        // each; S2 reaches three, because the KiS2 lock-on host is a Sonic 2 game.
        assertEquals(11, donatingPairs);
    }

    @Test
    void absentCapabilitiesReturnTheHostAndMissingPoliciesFail() {
        GamePolicies host = new Sonic2GameModule().getPolicies();
        GamePolicies donor = new Sonic3kGameModule().getPolicies();
        assertSame(host, CrossGamePolicyComposer.donatePlayerCapabilities(host, donor, null));
        DonorCapabilities capabilities = new Sonic3kGameModule().getDonorCapabilities();
        assertThrows(IllegalArgumentException.class,
                () -> CrossGamePolicyComposer.donatePlayerCapabilities(null, donor, capabilities));
        assertThrows(IllegalArgumentException.class,
                () -> CrossGamePolicyComposer.donatePlayerCapabilities(host, null, capabilities));
    }

    /**
     * A caller cannot express "the donor's collision (or camera, ring, audio...) under
     * this host": the only public composer takes whole policies plus the capability
     * declaration, and the aggregate has no category-replacement method.
     */
    @Test
    void invalidCategoryMixturesAreNotRepresentableThroughThePublicComposer() {
        List<Method> publicMethods = Arrays.stream(CrossGamePolicyComposer.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toList();
        assertEquals(1, publicMethods.size(), "one public donation entry point: " + publicMethods);
        assertEquals(List.of(GamePolicies.class, GamePolicies.class, DonorCapabilities.class),
                List.of(publicMethods.get(0).getParameterTypes()));
        assertEquals(GamePolicies.class, publicMethods.get(0).getReturnType());
        assertEquals(0, CrossGamePolicyComposer.class.getConstructors().length, "no instances");

        Set<String> accessors = Arrays.stream(GamePolicies.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(java.util.stream.Collectors.toSet());
        for (Method method : GamePolicies.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) continue;
            String name = method.getName();
            assertTrue(accessors.contains(name) || Set.of("equals", "hashCode", "toString").contains(name),
                    "GamePolicies exposes no withers or replacement helpers: " + method);
            assertTrue(!Modifier.isStatic(method.getModifiers()), "no static composition on the record: " + method);
        }
        assertEquals(1, GamePolicies.class.getConstructors().length,
                "only the canonical constructor builds policies");
    }

    private static void assertHostGroupsKept(String pair, GameRules host, GameRules composed) throws Exception {
        for (RecordComponent component : GameRules.class.getRecordComponents()) {
            if (component.getName().equals("playerCapability")) continue;
            assertSame(component.getAccessor().invoke(host), component.getAccessor().invoke(composed),
                    pair + ": host keeps " + component.getName());
        }
    }

    private static void assertCapabilities(String pair, PlayerCapabilityRules host, PlayerCapabilityRules donor,
            DonorCapabilities capabilities, PlayerCapabilityRules composed) throws Exception {
        assertEquals(capabilities.hasSpindash(), composed.spindashEnabled(), pair);
        if (capabilities.hasSpindash()) {
            assertArrayEquals(donor.spindashSpeedTable(), composed.spindashSpeedTable(), pair);
        } else {
            assertNull(composed.spindashSpeedTable(), pair);
        }
        assertEquals(capabilities.hasElementalShields(), composed.elementalShieldsEnabled(), pair);
        assertEquals(capabilities.hasElementalShields(), composed.lightningShieldEnabled(), pair);
        assertEquals(capabilities.hasInstaShield(), composed.instaShieldEnabled(), pair);
        assertEquals(capabilities.hasTailsFlight(), composed.tailsFlightEnabled(), pair);
        assertEquals(host.glideAttacksEnabled() || (donor.glideAttacksEnabled()
                        && capabilities.getPlayableCharacters().contains(PlayerCharacter.KNUCKLES)),
                composed.glideAttacksEnabled(), pair);
        for (RecordComponent component : PlayerCapabilityRules.class.getRecordComponents()) {
            if (DONATED_CAPABILITY_FIELDS.contains(component.getName())) continue;
            Object hostValue = component.getAccessor().invoke(host);
            Object composedValue = component.getAccessor().invoke(composed);
            if (hostValue instanceof short[] table) {
                assertArrayEquals(table, (short[]) composedValue, pair + ": host keeps " + component.getName());
            } else {
                assertEquals(hostValue, composedValue, pair + ": host keeps " + component.getName());
            }
        }
    }
}
