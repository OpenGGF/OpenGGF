package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.game.PhysicsProfile;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.internal.PhysicsProfileOverrides;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kPhysicsProvider;
import com.openggf.io.ModAssetRoot;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class TestModContextPhysicsTransform {
    @Test void transformsComposeAndPreserveNativePhysicsInputs() {
        var context = new ModContext("movement", "any", ModAssetRoot.forTests("movement"));
        context.transformPhysics("sonic"::equals, profile -> profile.withMax(0x800));
        context.transformPhysics("sonic"::equals, profile -> profile.withMax(profile.max() + 0x100));
        var nativeProvider = new Sonic3kPhysicsProvider() {
            @Override public PhysicsProfile getInitProfile(String character) {
                return character.equals("sonic") ? PhysicsProfile.SONIC_3K_SONIC_INIT : null;
            }
        };
        var stock = new Sonic3kGameModule() {
            @Override public PhysicsProvider getPhysicsProvider() { return nativeProvider; }
        };
        var plan = context.freeze().stockScenePlans().stream()
                .filter(candidate -> candidate.baseGameId().equals("s3k")).findFirst().orElseThrow();
        GameModule module = new ModBackedGamePatch(plan, boundary(new AtomicReference<>())).apply(stock, null);
        PhysicsProvider provider = module.getPhysicsProvider();
        assertSame(provider, module.getPhysicsProvider());
        assertEquals(0x900, provider.getProfile("sonic").max());
        assertEquals(0x900, provider.getInitProfile("sonic").max());
        assertEquals(stock.getPhysicsProvider().getProfile("tails"), provider.getProfile("tails"));
        assertEquals(stock.getPhysicsProvider().getInitProfile("tails"), provider.getInitProfile("tails"));
        assertEquals(stock.getPhysicsProvider().getModifiers(), provider.getModifiers());
        assertSame(nativeProvider, PhysicsProfileOverrides.nativeSource(provider));
        assertSame(stock.getPolicies(), module.getPolicies());

        TestEnvironment.configureGameModuleFixture(module);
        try {
            Sonic player = new Sonic("sonic", (short) 0, (short) 0);
            assertEquals(0x900, player.getPhysicsProfile().max());
        } finally {
            TestEnvironment.resetAll();
        }
    }

    @Test void invalidRegistrationPoisonsTransactionEvenIfCreatorCatchesIt() {
        var context = new ModContext("movement", "s3k", ModAssetRoot.forTests("movement"));
        context.transformPhysics("sonic"::equals, profile -> profile.withMax(0x800));
        assertThrows(ModRegistrationException.class, () -> context.transformPhysics(null, profile -> profile));
        assertThrows(ModRegistrationException.class, context::freeze);
    }

    @Test void failingEditAbortAndQuarantineBelongToTheRegisteringOwner() {
        var context = new ModContext("movement", "s3k", ModAssetRoot.forTests("movement"));
        context.transformPhysics("sonic"::equals, profile -> null);
        var disabled = new AtomicReference<Set<String>>();
        var stock = new Sonic3kGameModule();
        var provider = new ModBackedGamePatch(context.freeze(), boundary(disabled))
                .apply(stock, null).getPhysicsProvider();
        var failure = assertThrows(ModFaultBoundary.CallbackAborted.class, () -> provider.getProfile("sonic"));
        assertEquals("movement", failure.owner());
        assertEquals(Set.of("movement", "dependent"), disabled.get());
        assertEquals(stock.getPhysicsProvider().getProfile("sonic"), provider.getProfile("sonic"));
    }

    private static ModFaultBoundary boundary(AtomicReference<Set<String>> disabled) {
        return new ModFaultBoundary(Map.of("dependent", Set.of("movement")), new ModRuntimeFindingStore(),
                ignored -> new ModStateSaveResult.Saved(), disabled::set);
    }
}
