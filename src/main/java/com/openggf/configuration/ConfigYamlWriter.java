package com.openggf.configuration;

import java.util.Map;
import java.util.OptionalInt;

/**
 * Renders the flat config map to grouped, commented, deterministically-ordered
 * YAML. Walks {@link ConfigCatalog#emitOrder()} (persisted keys only), opening
 * nested mapping blocks as section paths deepen, emitting a {@code # ── Title ──}
 * banner per top-level normal section and a single fence banner when the
 * {@code debug.*} block begins.
 */
public final class ConfigYamlWriter {

    public String write(Map<String, Object> flat) {
        StringBuilder sb = new StringBuilder();
        sb.append("# OpenGGF configuration — grouped and documented.\n");
        sb.append("# Indentation is significant (YAML). This file is rewritten cleanly on save.\n");

        String[] prev = new String[0];
        boolean debugOpened = false;

        for (SonicConfiguration key : ConfigCatalog.emitOrder()) {
            ConfigKeyMeta m = ConfigCatalog.meta(key);
            String[] segs = m.section().split("\\.");
            boolean isDebug = segs[0].equals("debug");

            if (isDebug && !debugOpened) {
                sb.append('\n')
                  .append("# ════════════════════════════════════════════\n")
                  .append("#  DEBUG  (developer tooling — safe to ignore for normal play)\n")
                  .append("# ════════════════════════════════════════════\n");
                debugOpened = true;
                prev = new String[0]; // force re-open of debug: and its subsections
            } else if (!isDebug && (prev.length == 0 || !prev[0].equals(segs[0]))) {
                sb.append('\n').append("# ── ").append(ConfigCatalog.title(segs[0])).append(" ──\n");
            }

            int common = commonPrefix(prev, segs);
            for (int d = common; d < segs.length; d++) {
                indent(sb, d);
                sb.append(segs[d]).append(":\n");
            }
            indent(sb, segs.length);
            sb.append(m.leaf()).append(": ").append(format(m, flat.get(key.name())));
            if (m.description() != null && !m.description().isBlank()) {
                sb.append("   # ").append(m.description());
            }
            sb.append('\n');
            prev = segs;
        }
        return sb.toString();
    }

    private static int commonPrefix(String[] a, String[] b) {
        int n = Math.min(a.length, b.length);
        int i = 0;
        while (i < n && a[i].equals(b[i])) {
            i++;
        }
        return i;
    }

    private static void indent(StringBuilder sb, int depth) {
        sb.append("  ".repeat(depth));
    }

    private static String format(ConfigKeyMeta m, Object value) {
        return switch (m.type()) {
            case BOOL -> String.valueOf(toBool(value));
            case INT -> String.valueOf(toInt(value));
            case DOUBLE -> formatDouble(value);
            case KEY -> formatKey(value);
            case STRING, ENUM -> quote(value == null ? "" : value.toString());
        };
    }

    private static boolean toBool(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(v));
    }

    private static long toInt(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String formatDouble(Object v) {
        double d = (v instanceof Number n) ? n.doubleValue() : parseDoubleOrZero(v);
        if (d == Math.rint(d) && !Double.isInfinite(d)) {
            return (long) d + ".0";
        }
        return Double.toString(d);
    }

    private static double parseDoubleOrZero(Object v) {
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /** Renders a KEY value as its GLFW key name (handles legacy integer codes). */
    private static String formatKey(Object v) {
        if (v instanceof Number n) {
            return GlfwKeyNameResolver.nameOf(n.intValue());
        }
        String s = String.valueOf(v).trim();
        try {
            return GlfwKeyNameResolver.nameOf(Integer.parseInt(s));
        } catch (NumberFormatException ignored) {
            // already a key name (or empty)
        }
        OptionalInt resolved = GlfwKeyNameResolver.resolve(s);
        return resolved.isPresent() ? GlfwKeyNameResolver.nameOf(resolved.getAsInt()) : s;
    }

    /** Always double-quote string/enum scalars so special characters (spaces, [], !, :) are safe. */
    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
