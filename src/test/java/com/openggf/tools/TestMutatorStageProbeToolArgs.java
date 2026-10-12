package com.openggf.tools;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestMutatorStageProbeToolArgs {
    @Test
    void rejectsUnsupportedUnboundedAndUnreachableCellsBeforeNativeStartup() {
        String[] valid = {"s2", "special", "25", "false", "-1", "32", "lab.jar", "fresh"};
        assertThrows(IllegalArgumentException.class, () -> MutatorStageProbeTool.Arguments.parse(new String[0]));
        for (String[] change : new String[][] {
                {"0", "s4"}, {"1", "GUMBALL"}, {"2", "26"}, {"2", "0"}, {"2", "425"},
                {"3", "yes"}, {"4", "-3"}, {"4", "32"}, {"5", "7"}, {"5", "601"}}) {
            String[] args = valid.clone();
            args[Integer.parseInt(change[0])] = change[1];
            assertThrows(IllegalArgumentException.class, () -> MutatorStageProbeTool.Arguments.parse(args),
                    java.util.Arrays.toString(change));
        }
    }

    @Test
    void preservesDeclaredEntryModeAudioChoiceAndPathsWithSpaces() {
        var args = MutatorStageProbeTool.Arguments.parse(new String[] {
                "s3k", "GLOWING_SPHERE", "150", "true", "9", "32",
                "/owned/creator package/lab.jar", "/owned/stage evidence"});
        assertEquals("GLOWING_SPHERE", args.stage());
        assertTrue(args.audioFollows());
        assertEquals(9, args.denyAt());
        assertEquals(Path.of("/owned/creator package/lab.jar"), args.mod());
        assertEquals(Path.of("/owned/stage evidence"), args.out());
        assertEquals(-2, MutatorStageProbeTool.Arguments.parse(new String[] {
                "s1", "special", "100", "false", "-2", "32", "lab.jar", "fresh"}).denyAt());
    }
}
