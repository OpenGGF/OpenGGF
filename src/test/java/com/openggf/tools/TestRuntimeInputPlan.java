package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class TestRuntimeInputPlan {
    @TempDir Path temporary;

    @Test
    void controllerPredecessorAndStructuralPolicySurviveWithoutComparisonData() throws Exception {
        var trace = Path.of("src/test/resources/traces/s1/ghz1_fullrun");
        var original = new Bk2MovieLoader().load(trace.resolve("ghz1_fullrun.bk2"));
        var plan = RuntimeInputPlan.compile(trace, 0, 0, 0, 4);
        assertEquals(4, plan.rows().size());
        assertEquals(5, plan.inputs().size());
        for (int i = 0; i < plan.inputs().size(); i++) {
            var input = plan.inputs().get(i);
            var source = original.getFrame(input.frameIndex());
            assertEquals(source.p1InputMask(), input.p1InputMask());
            assertEquals(source.p1ActionMask(), input.p1ActionMask());
            assertEquals(source.p1StartPressed(), input.p1StartPressed());
            assertEquals(source.p2InputMask(), input.p2InputMask());
            assertEquals(source.p2ActionMask(), input.p2ActionMask());
            assertEquals(source.p2StartPressed(), input.p2StartPressed());
        }
        var path = temporary.resolve("plan.json");
        plan.write(path);
        assertEquals(plan, RuntimeInputPlan.read(path));
        String json = Files.readString(path);
        assertFalse(json.contains("player_x"));
        assertFalse(json.contains("start_x"));
        assertFalse(json.contains("rng_seed"));
        assertFalse(json.contains("aux_state"));
        assertThrows(UnsupportedOperationException.class, () -> plan.inputs().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.rows().clear());
    }

    @Test
    void measuredPlanRejectsUnknownGameplayFields() throws Exception {
        var plan = RuntimeInputPlan.compile(Path.of("src/test/resources/traces/s1/ghz1_fullrun"), 0, 0, 0, 1);
        var path = temporary.resolve("plan.json");
        plan.write(path);
        Files.writeString(path, Files.readString(path).replaceFirst("\\{", "{\"player_x\":80,"));
        assertThrows(java.io.IOException.class, () -> RuntimeInputPlan.read(path));
    }
}
