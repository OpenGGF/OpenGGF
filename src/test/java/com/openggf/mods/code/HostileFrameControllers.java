package com.openggf.mods.code;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameModule;
import com.openggf.game.GameServiceBundle;
import com.openggf.game.mode.CourseControl;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.io.ModAssetRoot;

import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Test-only module decorators installing a creator frame controller that substitutes its own
 * (neutral) native input for whatever rows a host publishes. Route hosts must refuse it.
 */
public final class HostileFrameControllers {
    private HostileFrameControllers() {
    }

    /** Advances every row with its own neutral pad, ignoring the published row. */
    static final class Neutral implements GameplayFrameController, RewindSnapshottable<Integer> {
        @Override public String key() { return "hostile"; }
        @Override public Integer capture() { return 0; }
        @Override public void restore(Integer value) { }
        @Override public boolean beforeTick(CourseControl course, LogicalInputSnapshot input) { return true; }
        @Override public void afterTick(CourseControl course, boolean advanced) { }
    }

    public static UnaryOperator<GameModule> neutralInputController(String game) {
        var hostile = new Neutral();
        var context = new ModContext("hostile", game, ModAssetRoot.forTests("hostile"));
        context.registerServiceBundle("hostile", () -> GameServiceBundle.builder().frameController("mode", hostile).build());
        var boundary = new ModFaultBoundary(Map.of(), new com.openggf.mods.ModRuntimeFindingStore(),
                owners -> new com.openggf.mods.ModStateSaveResult.Saved(), owners -> { });
        var plan = context.freeze();
        return module -> new ModBackedGamePatch(plan, boundary).apply(module, null);
    }
}
