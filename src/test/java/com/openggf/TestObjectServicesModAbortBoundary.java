package com.openggf;

import com.openggf.game.BonusStageProvider;
import com.openggf.game.BonusStageState;
import com.openggf.game.BonusStageType;
import com.openggf.game.ShieldType;
import com.openggf.game.session.EngineServices;
import com.openggf.level.objects.DefaultObjectServices;
import com.openggf.level.objects.ObjectServices;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.runtime.OwnerBoundCallbacks;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class TestObjectServicesModAbortBoundary {
    enum BonusStageAction {
        EXIT(ObjectServices::requestBonusStageExit),
        RINGS(services -> services.addBonusStageRings(7)),
        SHIELD(services -> services.setBonusStageShield(ShieldType.LIGHTNING));

        private final Consumer<ObjectServices> invoke;

        BonusStageAction(Consumer<ObjectServices> invoke) {
            this.invoke = invoke;
        }
    }

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
    }

    @ParameterizedTest
    @EnumSource(BonusStageAction.class)
    void failingOwnedBonusStageActionAbortsAndDiscardsTheFrame(BonusStageAction action) {
        var findings = new ModRuntimeFindingStore();
        var savedDisabledOwners = new AtomicReference<Set<String>>(Set.of());
        var boundary = new ModFaultBoundary(Map.of("dependent", Set.of("bonus-owner")), findings,
                owners -> {
                    savedDisabledOwners.set(owners);
                    return new ModStateSaveResult.Saved();
                }, owners -> {});
        var provider = new FailingBonusStageProvider();
        BonusStageProvider owned = new OwnerBoundCallbacks("bonus-owner", boundary, 8, Map.of())
                .bind(BonusStageProvider.class, provider);
        var gameplay = TestEnvironment.activeGameplayMode();
        var services = new DefaultObjectServices(gameplay, EngineServices.current());
        gameplay.setActiveBonusStageProvider(owned);
        var continuedFrame = new AtomicBoolean();
        var discardedFrames = new AtomicInteger();
        var titleReturns = new AtomicInteger();

        boolean completed = Engine.runFrameWithModAbort(() -> {
            action.invoke.accept(services);
            continuedFrame.set(true);
        }, discardedFrames::incrementAndGet, titleReturns::incrementAndGet);

        assertAll(
                () -> assertEquals(1, provider.failedActions),
                () -> assertEquals(Set.of("bonus-owner", "dependent"), savedDisabledOwners.get()),
                () -> assertTrue(findings.snapshot().containsKey("bonus-owner")),
                () -> assertFalse(boundary.isOwnerAvailable("bonus-owner")),
                () -> assertFalse(completed, "a failed creator callback cannot complete its frame"),
                () -> assertFalse(continuedFrame.get(), "native object work must stop after the abort"),
                () -> assertEquals(1, discardedFrames.get()),
                () -> assertEquals(1, titleReturns.get()));
    }

    public static final class FailingBonusStageProvider implements BonusStageProvider {
        private int failedActions;

        private void failAction() {
            failedActions++;
            throw new IllegalStateException("bonus action failed");
        }

        @Override public void requestExit() { failAction(); }
        @Override public void addRings(int count) { failAction(); }
        @Override public void setAwardedShield(ShieldType type) { failAction(); }
        @Override public boolean hasBonusStages() { return true; }
        @Override public BonusStageType selectBonusStage(int rings) { return BonusStageType.NONE; }
        @Override public void onEnter(BonusStageType type, BonusStageState state) {}
        @Override public void onExit() {}
        @Override public void onFrameUpdate() {}
        @Override public boolean isStageComplete() { return false; }
        @Override public BonusStageRewards getRewards() { return BonusStageRewards.none(); }
        @Override public int getZoneId(BonusStageType type) { return -1; }
        @Override public int getMusicId(BonusStageType type) { return -1; }
        @Override public BonusStageState getSavedState() { return null; }
    }
}
