package com.openggf.route;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The route core's boundaries. Front-ends and tests submit tasks and replay tapes; only trusted
 * host composition drives the host SPI. The core reads no trace data, no front-end document and
 * no game module.
 */
class TestRouteSurfaceGuard {
    /** The trusted composition that implements hosts and may drive them. */
    private static final Set<String> HOST_OWNERS = Set.of(
            "com.openggf.tools.EngineRouteHosts", "com.openggf.tools.EngineRouteWorld",
            // The engine host's own contract tests.
            "com.openggf.tools.TestEngineRouteHost");

    /** One import, held only for this method: the guards fork shares its heap with other whole-graph guards. */
    @Test
    void routeBoundariesHold() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.openggf");
        var host = hostSpiViolations(classes);
        var core = coreDependencyViolations(classes);
        assertTrue(host.isEmpty(), "front-ends and tests must use RoutePlanner/RouteReplay/RouteFiles, "
                + "not the host SPI:\n  " + String.join("\n  ", host));
        assertTrue(core.isEmpty(), "the route core must stay game-, trace- and front-end-neutral:\n  "
                + String.join("\n  ", core));
    }

    private static TreeSet<String> hostSpiViolations(JavaClasses classes) {
        var violations = new TreeSet<String>();
        for (JavaClass origin : classes) {
            if (origin.getPackageName().equals("com.openggf.route") || HOST_OWNERS.contains(outermost(origin))) continue;
            for (JavaMethodCall call : origin.getMethodCallsFromSelf()) {
                String owner = call.getTargetOwner().getName();
                String name = call.getName();
                boolean hostSpi = (owner.equals(ReplayWorld.class.getName()) || owner.equals(RouteWorld.class.getName()))
                        && Set.of("step", "capture", "restore").contains(name);
                boolean factory = owner.equals(RouteWorldFactory.class.getName())
                        && Set.of("openSearch", "openColdReplay").contains(name);
                if (hostSpi || factory) violations.add(origin.getName() + " calls " + owner + "." + name);
            }
        }
        return violations;
    }

    private static TreeSet<String> coreDependencyViolations(JavaClasses classes) {
        var violations = new TreeSet<String>();
        for (JavaClass origin : classes) {
            if (!origin.getPackageName().equals("com.openggf.route")) continue;
            origin.getDirectDependenciesFromSelf().forEach(dependency -> {
                String target = dependency.getTargetClass().getPackageName();
                if (target.startsWith("com.openggf.trace") || target.startsWith("com.openggf.stageplay")
                        || target.startsWith("com.openggf.tools") || target.startsWith("com.openggf.editor")
                        || target.matches("com\\.openggf\\.game\\.sonic(1|2|3k)(\\..*)?")) {
                    violations.add(origin.getName() + " -> " + dependency.getTargetClass().getName());
                }
            });
        }
        return violations;
    }

    private static String outermost(JavaClass javaClass) {
        String name = javaClass.getName();
        int nested = name.indexOf('$');
        return nested < 0 ? name : name.substring(0, nested);
    }
}
