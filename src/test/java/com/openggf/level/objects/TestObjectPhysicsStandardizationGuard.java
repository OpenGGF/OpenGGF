package com.openggf.level.objects;

import com.openggf.tests.ObjectGuardSourceScanner;
import com.openggf.tests.ObjectGuardSourceScanner.SourceText;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestObjectPhysicsStandardizationGuard {
    @Test
    void objectManagerUsesNativePositionOpsForPlayablePreserveSubpixelWrites() throws IOException {
        SourceText source = source("com/openggf/level/objects/ObjectManager.java");

        assertEquals(List.of(), forbiddenLines(source,
                "setCentreXPreserveSubpixel(",
                "setCentreYPreserveSubpixel("));
    }

    @Test
    void badnikDestructionUsesLifetimeOpsForSlotTransferAndDestroyedFlag() throws IOException {
        SourceText source = source("com/openggf/level/objects/AbstractBadnikInstance.java");

        assertEquals(List.of(), forbiddenLines(source,
                "setSlotIndex(-1)",
                "setDestroyed(true)"));
    }

    @Test
    void destructionEffectsUsesLifetimeOpsForSpawnAndReplacementLifecycle() throws IOException {
        SourceText source = source("com/openggf/level/objects/DestructionEffects.java");

        assertEquals(List.of(), forbiddenLines(source,
                ".markRemembered(",
                ".removeFromActiveSpawns(",
                ".addDynamicObjectAtSlot("));
    }

    private static SourceText source(String relativePath) throws IOException {
        Path srcMain = ObjectGuardSourceScanner.findSourceRoot();
        if (srcMain == null) {
            throw new IOException("Could not locate src/main/java");
        }
        return ObjectGuardSourceScanner.sourceWithoutCommentOnlyLines(
                Files.readAllLines(srcMain.resolve(relativePath)));
    }

    private static List<String> forbiddenLines(SourceText source, String... fragments) {
        return source.lines().stream()
                .map(String::trim)
                .filter(line -> containsAny(line, fragments))
                .toList();
    }

    private static boolean containsAny(String line, String[] fragments) {
        for (String fragment : fragments) {
            if (line.contains(fragment)) {
                return true;
            }
        }
        return false;
    }
}
