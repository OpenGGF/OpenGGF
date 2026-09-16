package com.openggf.level.objects;

import com.openggf.graphics.RenderPriority;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every object that draws itself must state its sprite bucket.
 * <p>
 * {@link ObjectInstance#getPriorityBucket()} defaults to bucket 0, the front-most
 * display list. In the ROM a freshly allocated SST slot is also zero, but nearly
 * every displayed object writes {@code priority} in its init; the engine has no
 * equivalent pressure, so a port that forgets the field silently draws in front
 * of the player. The 2026-09-16 audit found 32 S3K, 10 S1 and 15 S2 rendering
 * classes in that state, several against ROM {@code $280}/{@code $300}. This guard
 * requires any concrete {@link ObjectInstance} whose own {@code appendRenderCommands}
 * body does something to declare {@code getPriorityBucket()} in its class hierarchy.
 * An object the ROM really leaves at priority 0 opts in by overriding and returning
 * {@link RenderPriority#bucket(int) bucket(0)} with the ROM citation, which is what
 * separates "verified 0" from "forgotten".
 * <p>
 * Runs under {@code -Pguards} in a fresh JVM because it imports the whole production
 * class graph.
 */
class TestObjectPriorityBucketGuard {

    private static final String RENDER_METHOD = "appendRenderCommands";
    private static final String BUCKET_METHOD = "getPriorityBucket";

    @Test
    void everyRenderingObjectDeclaresItsPriorityBucket() {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.openggf");
        JavaClass contract = production.get(ObjectInstance.class);

        List<String> offenders = new ArrayList<>();
        for (JavaClass candidate : production) {
            if (candidate.isInterface() || candidate.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.ABSTRACT)) {
                continue;
            }
            if (!candidate.isAssignableTo(contract.reflect())) {
                continue;
            }
            Optional<JavaMethod> render = declaredMethod(candidate, RENDER_METHOD);
            if (render.isEmpty() || render.get().getCallsFromSelf().isEmpty()) {
                // No own render body, or an empty one: rendering (and its bucket) is inherited or absent.
                continue;
            }
            if (!declaresBucketSomewhere(candidate)) {
                offenders.add(candidate.getName());
            }
        }
        offenders.sort(String::compareTo);
        assertEquals(List.of(), offenders,
                "objects that draw themselves but never override getPriorityBucket(); transcribe the ROM"
                        + " priority (S1/S2 byte via RenderPriority.bucket, S3K word via RenderPriority.fromS3kWord)"
                        + " or return bucket(0) with the ROM citation when the ROM leaves priority at 0");
    }

    private static boolean declaresBucketSomewhere(JavaClass type) {
        JavaClass current = type;
        while (current != null && !current.getName().equals(Object.class.getName())) {
            if (declaredMethod(current, BUCKET_METHOD).isPresent()) {
                return true;
            }
            current = current.getRawSuperclass().orElse(null);
        }
        return false;
    }

    private static Optional<JavaMethod> declaredMethod(JavaClass type, String name) {
        return type.getMethods().stream()
                .filter(m -> m.getName().equals(name))
                .filter(m -> !m.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.ABSTRACT))
                .findFirst();
    }
}
