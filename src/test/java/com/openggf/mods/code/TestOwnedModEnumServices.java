package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.game.GameServiceBundle;
import com.openggf.game.sonic3k.difficulty.AizMinibossAttackPolicy;
import com.openggf.io.ModAssetRoot;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestOwnedModEnumServices {
    enum Recipe { STOCK, CUSTOM }
    public interface Counter { int increment(); }

    @Test
    void fixedEnumFallsBackToInheritedOwnerAfterFault() {
        var boundary = boundary(false);
        GameModule inherited = publish(base(), "inherited", boundary, false, new AtomicInteger());
        GameModule module = publish(inherited, "owner", boundary, false, new AtomicInteger());
        assertSame(Recipe.CUSTOM, module.getGameService(Recipe.class));
        fault(boundary, "owner");
        assertSame(Recipe.STOCK, module.getGameService(Recipe.class));
        fault(boundary, "inherited");
        assertNull(module.getGameService(Recipe.class));
    }

    @Test
    void unrelatedFaultPreservesEnumButDependencyFaultWithdrawsIt() {
        var boundary = boundary(false);
        var module = publish(base(), "dependent", boundary, false, new AtomicInteger());
        fault(boundary, "unrelated");
        assertSame(Recipe.CUSTOM, module.getGameService(Recipe.class));
        fault(boundary, "owner");
        assertNull(module.getGameService(Recipe.class));
    }

    @Test
    void dynamicEnumStopsCallingProviderAfterQuarantineEvenWhenDisableSaveFails() {
        var boundary = boundary(true);
        var calls = new AtomicInteger();
        var module = publish(base(), "owner", boundary, true, calls);
        assertSame(Recipe.CUSTOM, module.getGameService(Recipe.class));
        assertEquals(1, calls.get());
        fault(boundary, "owner");
        assertFalse(boundary.isOwnerAvailable("owner"));
        assertNull(module.getGameService(Recipe.class));
        assertEquals(1, calls.get());
    }

    @Test
    void nonEnumFreshAndCapturedCallbacksRetainExistingInvocationSemantics() {
        var boundary = boundary(false);
        var calls = new AtomicInteger();
        var context = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        context.registerService("counter", Counter.class, calls::incrementAndGet);
        var module = OwnedModServices.decorate(base(), context.freeze(), boundary);
        var captured = module.getGameService(Counter.class);
        assertEquals(1, captured.increment());
        fault(boundary, "owner");
        assertSame(captured, module.getGameService(Counter.class));
        assertEquals(2, module.getGameService(Counter.class).increment());
        assertEquals(3, captured.increment());
    }

    @Test
    void freshAttackPolicyWithdrawsWhileCapturedAndDirectCallbacksKeepTheirSemantics() {
        var boundary = boundary(false);
        var calls = new AtomicInteger();
        AizMinibossAttackPolicy direct = hits -> {
            calls.incrementAndGet();
            return 16;
        };
        var context = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        context.registerService("attack", AizMinibossAttackPolicy.class, direct);
        var module = OwnedModServices.decorate(base(), context.freeze(), boundary);
        var captured = module.getGameService(AizMinibossAttackPolicy.class);
        assertEquals(16, captured.followupDelayTicks(4));
        fault(boundary, "owner");
        assertEquals(16, captured.followupDelayTicks(4));
        assertEquals(16, boundary.call("owner", () -> direct.followupDelayTicks(4)));
        var fatal = new OutOfMemoryError("fatal callback");
        assertSame(fatal, assertThrows(OutOfMemoryError.class,
                () -> boundary.run("owner", () -> { throw fatal; })));
        assertEquals(3, calls.get());
        assertNull(module.getGameService(AizMinibossAttackPolicy.class));
    }

    @Test
    void dynamicAttackPolicyPreservesUnrelatedOwnerAndFallsBackAfterDependencyFault() {
        var boundary = boundary(false);
        var inherited = new ModContext("inherited", "s2", ModAssetRoot.forTests("inherited"));
        inherited.registerService("attack", AizMinibossAttackPolicy.class, hits -> 24);
        var nativeFallback = OwnedModServices.decorate(base(), inherited.freeze(), boundary);
        var fallback = nativeFallback.getGameService(AizMinibossAttackPolicy.class);
        var calls = new AtomicInteger();
        var dependent = new ModContext("dependent", "s2", ModAssetRoot.forTests("dependent"));
        dependent.registerServiceBundle("attack", () -> GameServiceBundle.builder()
                .dynamicService(AizMinibossAttackPolicy.class, () -> {
                    calls.incrementAndGet();
                    return hits -> 16;
                }).build());
        var module = OwnedModServices.decorate(nativeFallback, dependent.freeze(), boundary);
        assertEquals(16, module.getGameService(AizMinibossAttackPolicy.class).followupDelayTicks(4));
        fault(boundary, "unrelated");
        assertEquals(16, module.getGameService(AizMinibossAttackPolicy.class).followupDelayTicks(4));
        assertEquals(2, calls.get());
        fault(boundary, "owner");
        assertSame(fallback, module.getGameService(AizMinibossAttackPolicy.class));
        assertEquals(2, calls.get());
        fault(boundary, "inherited");
        assertNull(module.getGameService(AizMinibossAttackPolicy.class));
    }

    private static GameModule publish(GameModule base, String owner, ModFaultBoundary boundary,
                                      boolean dynamic, AtomicInteger calls) {
        var context = new ModContext(owner, "s2", ModAssetRoot.forTests(owner));
        Recipe value = owner.equals("inherited") ? Recipe.STOCK : Recipe.CUSTOM;
        context.registerServiceBundle("recipe", () -> dynamic
                ? GameServiceBundle.builder().dynamicService(Recipe.class, () -> {
                    calls.incrementAndGet();
                    return value;
                }).build()
                : GameServiceBundle.builder().service(Recipe.class, value).build());
        return OwnedModServices.decorate(base, context.freeze(), boundary);
    }

    private static GameModule base() {
        return mock(GameModule.class, CALLS_REAL_METHODS);
    }

    private static ModFaultBoundary boundary(boolean saveFails) {
        return new ModFaultBoundary(Map.of("dependent", Set.of("owner")), new ModRuntimeFindingStore(),
                ignored -> saveFails ? new ModStateSaveResult.Failed("disk full") : new ModStateSaveResult.Saved(),
                ignored -> { });
    }

    private static void fault(ModFaultBoundary boundary, String owner) {
        assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> boundary.run(owner, () -> { throw new IllegalStateException("callback failed"); }));
    }
}
