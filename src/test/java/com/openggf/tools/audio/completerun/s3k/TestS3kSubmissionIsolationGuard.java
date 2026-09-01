package com.openggf.tools.audio.completerun.s3k;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaMethodReference;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestS3kSubmissionIsolationGuard {
    private static final String RAW_ADAPTER =
            S3kCompleteRunReferenceRawAdapter.class.getName();
    private static final String PROJECTOR =
            S3kCompleteRunReferenceProjector.class.getName();

    @Test
    void unboundSubmissionEntriesRemainPackagePrivate() throws NoSuchMethodException {
        int adapterModifiers = S3kCompleteRunReferenceRawAdapter.class.getDeclaredMethod(
                "scanSubmissionV2PrefixForTesting", Path.class,
                S3kCompleteRunReferenceRawAdapter.Sink.class).getModifiers();
        int projectorModifiers = S3kCompleteRunReferenceProjector.class.getDeclaredMethod(
                "projectSubmissionV2PrefixForTesting", Path.class, Path.class).getModifiers();

        assertPackagePrivate(adapterModifiers);
        assertPackagePrivate(projectorModifiers);
    }

    @Test
    void compiledProductionHasOnlyTheExactUnboundSubmissionCaller() {
        JavaClasses production = new ClassFileImporter()
                .importPath(Path.of("target/classes"));

        assertEquals(List.of(), unauthorizedSubmissionCalls(production),
                "the UNBOUND_TEST_ONLY S3K raw-v2 path must remain unreachable from"
                        + " production entry points and bindings");
    }

    @Test
    void bytecodeGuardRejectsAnUnauthorizedBinding() {
        JavaClasses mutation = new ClassFileImporter().importClasses(
                UnauthorizedBinding.class);

        List<String> violations = unauthorizedSubmissionCalls(mutation);

        assertEquals(1, violations.size(), violations.toString());
        assertFalse(violations.getFirst().isBlank());
    }

    private static void assertPackagePrivate(int modifiers) {
        assertFalse(Modifier.isPublic(modifiers));
        assertFalse(Modifier.isProtected(modifiers));
        assertFalse(Modifier.isPrivate(modifiers));
    }

    private static List<String> unauthorizedSubmissionCalls(JavaClasses classes) {
        return classes.stream()
                .flatMap(owner -> owner.getAccessesFromSelf().stream())
                .filter(TestS3kSubmissionIsolationGuard::targetsUnboundSubmissionEntry)
                .filter(access -> !isApprovedProjectorAdapterCall(access))
                .map(JavaAccess::getDescription)
                .sorted()
                .distinct()
                .toList();
    }

    private static boolean targetsUnboundSubmissionEntry(JavaAccess<?> access) {
        if (!(access instanceof JavaMethodCall)
                && !(access instanceof JavaMethodReference)) {
            return false;
        }
        String owner = access.getTargetOwner().getName();
        String method = access.getTarget().getName();
        return RAW_ADAPTER.equals(owner)
                        && "scanSubmissionV2PrefixForTesting".equals(method)
                || PROJECTOR.equals(owner)
                        && "projectSubmissionV2PrefixForTesting".equals(method);
    }

    private static boolean isApprovedProjectorAdapterCall(JavaAccess<?> access) {
        return PROJECTOR.equals(access.getOriginOwner().getName())
                && RAW_ADAPTER.equals(access.getTargetOwner().getName())
                && "scanSubmissionV2PrefixForTesting".equals(
                        access.getTarget().getName());
    }

    private static final class UnauthorizedBinding {
        Object acquire(S3kCompleteRunReferenceProjector projector, Path raw, Path rom)
                throws IOException {
            return projector.projectSubmissionV2PrefixForTesting(raw, rom);
        }
    }
}
