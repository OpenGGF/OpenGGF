package com.openggf.tools;

import com.openggf.io.ModInputLimits;
import com.openggf.mods.DefaultModRepositoryScanner;
import com.openggf.mods.EffectiveModCatalog;
import com.openggf.mods.ModCatalogValidator;
import com.openggf.mods.ModDescriptor;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModClassLoaderFactory;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.code.ModRuntime;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Real package/validator/snapshot/registration probe, originating in the
 * 2026-10-08 GraalVM feasibility task. Inputs: a folder of trusted experiment
 * JARs and an optional manifest owner ID. Each package is tested separately.
 * This explicitly opts into code loading only in the diagnostic: production
 * native rejection is also checked. It neither launches gameplay nor certifies
 * a mod. Do not use it as a launcher for untrusted downloaded code.
 */
public final class NativeModRegistrationProbe {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--audit")) {
            if (args.length != 2) throw new IllegalArgumentException("Expected: --audit <members.tsv>");
            NativeModMemberAudit.verify(Path.of(args[1]));
            return;
        }
        if (args.length > 0 && args[0].equals("--members")) {
            if (args.length < 3) throw new IllegalArgumentException("Expected: --members <members.tsv> <mods-directory> [owner-id]");
            NativeModMemberAudit.verify(Path.of(args[1]));
            args = java.util.Arrays.copyOfRange(args, 2, args.length);
        }
        boolean listOnly = args.length == 2 && args[0].equals("--list");
        if (listOnly) args = new String[] {args[1]};
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Expected: <mods-directory> [owner-id]");
        }
        Path mods = Path.of(args[0]).toAbsolutePath();
        var entries = new ModCatalogValidator(mods, ModInputLimits.production(), (game, id) -> true)
                .validate(new DefaultModRepositoryScanner().scan(mods)).entries();
        int passed = 0;
        for (var entry : entries) {
            if (!(entry instanceof ModDescriptor descriptor) || descriptor.hasErrors()) {
                throw new AssertionError(entry);
            }
            String owner = descriptor.manifest().id();
            if (listOnly) {
                System.out.println("OWNER\t" + owner);
                passed++;
                continue;
            }
            if (args.length == 2 && !owner.equals(args[1])) {
                continue;
            }
            var catalog = new EffectiveModCatalog(List.of(descriptor));
            var factory = new ModClassLoaderFactory(NativeModRegistrationProbe.class.getClassLoader());
            try (var policy = factory.create(catalog, Set.of(owner), false)) {
                if (descriptor.containsCode()
                        && (policy.rejectedOwners().get(owner) == null
                        || policy.rejectedOwners().get(owner).reason()
                        != ModRuntime.RejectionReason.NATIVE_UNSUPPORTED)) {
                    throw new AssertionError("Native code-mod policy not enforced");
                }
                if (!descriptor.containsCode() && !policy.rejectedOwners().isEmpty()) {
                    throw new AssertionError(policy.rejectedOwners());
                }
            }
            try (var runtime = factory.create(catalog, Set.of(owner), true)) {
                runtime.installFaultBoundary(new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                        ignored -> new ModStateSaveResult.Saved(), runtime::disableOwnersForProcess));
                if (!runtime.rejectedOwners().isEmpty()) {
                    throw new AssertionError(runtime.rejectedOwners());
                }
                runtime.newRegistrationPlan();
                if (!runtime.registrationFailures().isEmpty()) {
                    runtime.registrationFailures().values().forEach(Throwable::printStackTrace);
                    throw new AssertionError(runtime.registrationFailures());
                }
                System.out.println("PASS registration: " + owner + "; code=" + descriptor.containsCode());
                passed++;
            }
        }
        if (passed == 0) {
            throw new AssertionError("No mods selected");
        }
        System.out.println("PASS selected registrations: " + passed);
    }
}
