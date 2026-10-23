package com.openggf.level.objects;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestObjectLifecycleCallAudit {
    @Test
    void movingACallToAnotherMemberCannotSpendRemovedDebtAtTheSameTotal() throws IOException {
        Map<String, Integer> before = scan("""
                class Sample {
                    void oldOwner() { setDestroyed(true); }
                    void newOwner() {}
                }
                """);
        Map<String, Integer> after = scan("""
                class Sample {
                    void oldOwner() {}
                    void newOwner() { setDestroyed(true); }
                }
                """);
        assertEquals(1, before.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, after.values().stream().mapToInt(Integer::intValue).sum());
        List<String> differences = ObjectLifecycleCallAudit.differences(after, before);
        assertTrue(differences.stream().anyMatch(line -> line.contains("newOwner()")), differences::toString);
        assertTrue(differences.stream().anyMatch(line -> line.contains("oldOwner()")), differences::toString);
    }

    @Test
    void removedCallInAnotherFileCannotMaskNewCaller() {
        assertFalse(ObjectLifecycleCallAudit.differences(
                Map.of("Other.java#Other#update()->setDestroyed(true)", 1),
                Map.of("Old.java#Old#update()->setDestroyed(true)", 1)).isEmpty());
    }

    @Test
    void countGrowthAndStaleCountsRequireAnExplicitBaselineUpdate() {
        String key = "Sample.java#Sample#update()->setDestroyed(true)";
        assertFalse(ObjectLifecycleCallAudit.differences(Map.of(key, 2), Map.of(key, 1)).isEmpty());
        assertFalse(ObjectLifecycleCallAudit.differences(Map.of(key, 1), Map.of(key, 2)).isEmpty());
        assertFalse(ObjectLifecycleCallAudit.differences(Map.of(), Map.of(key, 1)).isEmpty());
        assertEquals(List.of(), ObjectLifecycleCallAudit.differences(Map.of(key, 1), Map.of(key, 1)));
    }

    @Test
    void parserIgnoresCommentsAndLiteralsAndKeepsOverloadsAndNestedMembersSeparate() throws IOException {
        assertEquals(Map.of(
                "Sample.java#Sample#update(int)->setDestroyed(true)", 2,
                "Sample.java#Sample#update(String)->addDynamicObjectAfterSlot", 1,
                "Sample.java#Sample#Child#update()->addDynamicObjectNextFrame", 1), scan("""
                class Sample {
                    // setDestroyed(true); manager.addDynamicObject(ghost);
                    String text = "setDestroyed(true)";
                    String block = """ + "\"\"\"\nmanager.addDynamicObject(ghost);\n\"\"\";" + """

                    void update(int tick) {
                        setDestroyed(true);
                        child.setDestroyed((true));
                        child.setDestroyed(false);
                    }
                    void update(String text) { manager.addDynamicObjectAfterSlot(child, 3); }
                    class Child {
                        void update() { manager.addDynamicObjectNextFrame(child); }
                    }
                }
                """));
    }

    @Test
    void formattingAndLineMovementDoNotChangeCallerKeys() throws IOException {
        Map<String, Integer> original = scan("class Sample { void update() { setDestroyed(true); } }");
        assertEquals(Map.of("Sample.java#Sample#update()->setDestroyed(true)", 1), original);
        assertEquals(original, scan("""

                /* another header with { braces } */
                class Sample {
                    void update ( ) {
                        setDestroyed (
                            true
                        );
                    }
                }
                """));
    }

    @Test
    void parserAttributesConstructorsFieldInitializersAndInitializerBlocks() throws IOException {
        assertEquals(Map.of(
                "Sample.java#Sample#<init>()->setDestroyed(true)", 1,
                "Sample.java#Sample#field:effect->addDynamicObject", 1,
                "Sample.java#Sample#<clinit>->setDestroyed(true)", 1,
                "Sample.java#Sample#<init-block>->setDestroyed(true)", 1), scan("""
                class Sample {
                    Object effect = manager.addDynamicObject(child);
                    static { child.setDestroyed(true); }
                    { child.setDestroyed(true); }
                    Sample() { setDestroyed(true); }
                }
                """));
    }

    @Test
    void invalidJavaFailsClosedInsteadOfSilentlyShrinkingTheInventory() {
        assertThrows(IOException.class, () -> scan("class Sample { void update( { setDestroyed(true); } }"));
    }

    @Test
    void anonymousMembersAndLambdasKeepTheirEnclosingCallerIdentity() throws IOException {
        assertEquals(Map.of(
                "Sample.java#Sample#update()->setDestroyed(true)", 1,
                "Sample.java#Sample#update()#<anonymous:Child:1>#run()->setDestroyed(true)", 1), scan("""
                class Sample {
                    void update() {
                        Runnable expire = () -> setDestroyed(true);
                        Child child = new Child() {
                            void run() { setDestroyed(true); }
                        };
                    }
                }
                """));
    }

    @Test
    void sameTypeAnonymousDeclarationsCannotExchangeDebt() throws IOException {
        String template = """
                class Sample {
                    void update() {
                        Child first = new Child() { void run() { %s } };
                        Child second = new Child() { void run() { %s } };
                    }
                }
                """;
        assertDeclarationSwapIsDetected(template, "<anonymous:Child:1>", "<anonymous:Child:2>");
    }

    @Test
    void sameNamedLocalDeclarationsInSiblingBlocksCannotExchangeDebt() throws IOException {
        String template = """
                class Sample {
                    void update() {
                        { class Child { void run() { %s } } }
                        { class Child { void run() { %s } } }
                    }
                }
                """;
        assertDeclarationSwapIsDetected(template, "<local:Child:1>", "<local:Child:2>");
    }

    private static void assertDeclarationSwapIsDetected(String template, String first, String second)
            throws IOException {
        String original = template.formatted("setDestroyed(true);", "");
        Map<String, Integer> before = scan(original);
        Map<String, Integer> after = scan(template.formatted("", "setDestroyed(true);"));
        String prefix = "Sample.java#Sample#update()#";
        String suffix = "#run()->setDestroyed(true)";
        assertEquals(Map.of(prefix + first + suffix, 1), before);
        assertEquals(Map.of(prefix + second + suffix, 1), after);
        assertEquals(List.of(
                prefix + first + suffix + " observed=0 baseline=1 (resolved debt: tighten baseline)",
                prefix + second + suffix + " observed=1 baseline=0 (new/increased debt)"),
                ObjectLifecycleCallAudit.differences(after, before));
        assertEquals(before, scan("/* declaration order ignores comments */\n\n"
                + original.replace("{", "{\n // formatting only\n")));
    }

    @Test
    void baselineRejectsDuplicatesNonpositiveCountsAndMissingReasons() {
        String key = "Sample.java#Sample#update()->setDestroyed(true)";
        assertEquals(Map.of(key, 2), ObjectLifecycleCallAudit.readBaseline(
                List.of("# reviewed debt", "", key + "\t2\tlegacy deletion timing")));
        assertThrows(IllegalArgumentException.class, () -> ObjectLifecycleCallAudit.readBaseline(
                List.of(key + "\t1\tlegacy", key + "\t1\tlegacy")));
        assertThrows(IllegalArgumentException.class, () -> ObjectLifecycleCallAudit.readBaseline(
                List.of(key + "\t0\tlegacy")));
        assertThrows(IllegalArgumentException.class, () -> ObjectLifecycleCallAudit.readBaseline(
                List.of(key + "\t1\t")));
    }

    @Test
    void genericOverloadsWithDifferentBoundsRemainDifferentCallers() throws IOException {
        assertEquals(Map.of(
                "Sample.java#Sample#<TextendsNumber>expire(T)->setDestroyed(true)", 1,
                "Sample.java#Sample#<TextendsCharSequence>expire(T)->setDestroyed(true)", 1), scan("""
                class Sample {
                    <T extends Number> void expire(T value) { setDestroyed(true); }
                    <T extends CharSequence> void expire(T value) { setDestroyed(true); }
                }
                """));
    }

    private static Map<String, Integer> scan(String source) throws IOException {
        return ObjectLifecycleCallAudit.scan(Map.of("Sample.java", source));
    }
}
