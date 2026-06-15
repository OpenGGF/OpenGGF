package com.openggf.graphics.shaderlib;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RetroArchGlslCompat {
    private static final int ENGINE_GLSL_VERSION = 410;
    private static final Pattern VERSION_LINE = Pattern.compile("(?m)^\\s*#\\s*version\\s+(\\d+)\\b[^\\r\\n]*(?:\\R|$)");
    private static final Pattern TEXTURE2D_CALL = Pattern.compile("\\btexture2D\\s*\\(");
    private static final Pattern ATTRIBUTE_TOKEN = Pattern.compile("\\battribute\\b");
    private static final Pattern VARYING_TOKEN = Pattern.compile("\\bvarying\\b");
    private static final Pattern FRAGMENT_OUTPUT = Pattern.compile(
            "(?m)^\\s*(?:layout\\s*\\([^\\r\\n]*\\)\\s*)?out\\s+\\w+\\s+\\w+\\s*(?:\\[[^\\]]+\\])?\\s*;");

    private RetroArchGlslCompat() {
    }

    public static String stageSource(String source, String stage) throws DisplayShaderLoadException {
        if (source == null) {
            throw new DisplayShaderLoadException("Shader source is required");
        }

        String normalizedStage = normalizeStage(stage);
        String body = stripVersionLines(source);
        StringBuilder staged = new StringBuilder(source.length() + 128);

        staged.append("#version 410 core\n");
        staged.append("#define ").append(normalizedStage).append('\n');
        staged.append("#define COMPAT_").append(normalizedStage).append('\n');
        boolean needsFragColorOutput = needsFragColorOutput(body, normalizedStage);
        appendLegacyPrelude(staged, body, normalizedStage, needsFragColorOutput);
        appendBody(staged, body, needsFragColorOutput);

        return staged.toString();
    }

    private static String normalizeStage(String stage) throws DisplayShaderLoadException {
        if (stage == null) {
            throw new DisplayShaderLoadException("Shader stage is required");
        }

        String normalized = stage.trim().toUpperCase(Locale.ROOT);
        if (!"VERTEX".equals(normalized) && !"FRAGMENT".equals(normalized)) {
            throw new DisplayShaderLoadException("Unsupported shader stage: " + stage);
        }
        return normalized;
    }

    private static String stripVersionLines(String source) throws DisplayShaderLoadException {
        Matcher matcher = VERSION_LINE.matcher(source);
        while (matcher.find()) {
            int version = parseVersion(matcher.group(1));
            if (version > ENGINE_GLSL_VERSION) {
                throw new UnsupportedShaderException("GLSL version " + version
                        + " is newer than engine context version " + ENGINE_GLSL_VERSION);
            }
        }
        return matcher.replaceAll("");
    }

    private static int parseVersion(String rawVersion) throws DisplayShaderLoadException {
        try {
            return Integer.parseInt(rawVersion);
        } catch (NumberFormatException e) {
            throw new DisplayShaderLoadException("Invalid GLSL version: " + rawVersion, e);
        }
    }

    private static void appendLegacyPrelude(StringBuilder staged, String body, String stage,
                                            boolean needsFragColorOutput) {
        if (TEXTURE2D_CALL.matcher(body).find()) {
            staged.append("#define texture2D texture\n");
        }

        if ("VERTEX".equals(stage)) {
            if (ATTRIBUTE_TOKEN.matcher(body).find()) {
                staged.append("#define attribute in\n");
            }
            if (VARYING_TOKEN.matcher(body).find()) {
                staged.append("#define varying out\n");
            }
            return;
        }

        if (VARYING_TOKEN.matcher(body).find()) {
            staged.append("#define varying in\n");
        }
        if (needsFragColorOutput) {
            staged.append("#define gl_FragColor FragColor\n");
        }
    }

    private static boolean needsFragColorOutput(String body, String stage) {
        return "FRAGMENT".equals(stage)
                && body.contains("gl_FragColor")
                && !FRAGMENT_OUTPUT.matcher(body).find();
    }

    private static void appendBody(StringBuilder staged, String body, boolean needsFragColorOutput) {
        if (!needsFragColorOutput) {
            staged.append(body);
            return;
        }

        int insertionPoint = leadingExtensionBlockEnd(body);
        staged.append(body, 0, insertionPoint);
        staged.append("out vec4 FragColor;\n");
        staged.append(body.substring(insertionPoint));
    }

    private static int leadingExtensionBlockEnd(String body) {
        int index = 0;
        while (index < body.length()) {
            int lineEnd = body.indexOf('\n', index);
            int nextIndex = lineEnd < 0 ? body.length() : lineEnd + 1;
            int contentEnd = lineEnd < 0 ? body.length() : lineEnd;
            if (contentEnd > index && body.charAt(contentEnd - 1) == '\r') {
                contentEnd--;
            }

            String line = body.substring(index, contentEnd).trim();
            if (line.isEmpty() || line.startsWith("#extension")) {
                index = nextIndex;
                continue;
            }
            break;
        }
        return index;
    }
}
