package com.openggf.mods;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure computation of which enabled mods cannot run on a GraalVM native-image
 * build. Code-bearing mods require runtime classloading, which closed-world AOT
 * cannot do. Data-only mods (music/reskin) are unaffected.
 *
 * <p>The source of truth is the user's <em>startup</em> enabled intent
 * ({@link ModState}) intersected with {@code containsCode()} -- not the eligibility
 * freeze and not runtime rejections.
 */
public final class NativeUnsupportedMods {

    public static final String NOTICE_HEADER =
            "The following enabled mods are not supported on OpenGGF native builds and have been disabled:";

    private NativeUnsupportedMods() {
    }

    public static List<ModDescriptor> compute(List<ModCatalogEntry> scanned,
                                              ModState startupEnabledIntent,
                                              boolean compiledModsSupported) {
        Objects.requireNonNull(scanned, "scanned");
        Objects.requireNonNull(startupEnabledIntent, "startupEnabledIntent");
        if (compiledModsSupported) {
            return List.of();
        }
        Set<String> enabled = new HashSet<>();
        for (ModState.Entry entry : startupEnabledIntent.entries()) {
            if (entry.enabled()) {
                enabled.add(entry.id());
            }
        }
        List<ModDescriptor> result = new ArrayList<>();
        for (ModCatalogEntry catalogEntry : scanned) {
            if (catalogEntry instanceof ModDescriptor descriptor
                    && descriptor.containsCode()
                    && enabled.contains(descriptor.manifest().id())) {
                result.add(descriptor);
            }
        }
        return List.copyOf(result);
    }

    public static boolean blocksStandalone(ModDescriptor descriptor, boolean compiledModsSupported) {
        return descriptor.containsCode() && !compiledModsSupported;
    }

    /**
     * Header plus one line per mod name, truncated to {@code maxLines} body lines.
     * When {@code modNames.size() > maxLines}, the last body line reports the
     * number of omitted names.
     */
    public static List<String> noticeLines(List<String> modNames, int maxLines) {
        Objects.requireNonNull(modNames, "modNames");
        if (maxLines < 1) {
            throw new IllegalArgumentException("maxLines must be >= 1");
        }
        List<String> lines = new ArrayList<>();
        lines.add(NOTICE_HEADER);
        if (modNames.size() <= maxLines) {
            lines.addAll(modNames);
        } else {
            int shown = maxLines - 1;
            lines.addAll(modNames.subList(0, shown));
            lines.add("…and " + (modNames.size() - shown) + " more");
        }
        return List.copyOf(lines);
    }
}
