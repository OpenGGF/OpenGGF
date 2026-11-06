package com.openggf.tools.modsdk;

import com.fasterxml.jackson.annotation.JsonInclude;

/** CLI presentation guidance only; does not change runtime findings or validation policy. */
final class DiagnosticGuidance {
    private static final String CATALOG = "docs/modding/troubleshooting.md";
    private DiagnosticGuidance() { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Guidance(String documentation, String nextAction, String supportedReplacement) { }

    static Guidance forCode(String code) {
        return switch (code) {
            case "MANIFEST_MISSING", "MANIFEST_INVALID" -> guide("#jar-manifest-dependency-and-budget-findings",
                    "Include META-INF/openggf-mod.yaml with canonical fields; correct the reported field and rebuild.");
            case "ENGINE_API_INCOMPATIBLE" -> guide("#jar-manifest-dependency-and-budget-findings",
                    "Use engine and SDK artifacts from the same supported build; recompile and align engineApiRange with the reported contracts.");
            case "MOD_JAR_INVALID", "JAR_SIZE_LIMIT", "JAR_READ_FAILED", "MALFORMED_JAR" ->
                    guide("#jar-manifest-dependency-and-budget-findings",
                            "Check the input path and rebuild a readable, bounded, regular non-symlink jar with safe unique entries.");
            case "ASSET_MISSING", "ASSET_FORMAT_INVALID", "LEVEL_FORMAT_INVALID", "AUDIO_ASSET_MISSING", "AUDIO_ASSET_INVALID" ->
                    guide("#asset-level-and-audio-findings",
                            "Include the declared asset at the reported path; regenerate invalid bytes with the matching SDK converter and repackage.");
            case "UNKNOWN_ART_OVERRIDE_KEY" -> guide("#asset-level-and-audio-findings",
                    "Run ggfmod art-keys --game <baseGame> and use an exact case-sensitive key requested by that game's consumer.");
            case "STATIC_STATE_UNSUPPORTED" -> guide("#compiled-code-and-rewind-findings",
                    "Move mutable gameplay state to instances or session services; retain only supported constants and verified simple immutable enums.");
            case "OBJECT_RECREATE_PATH_MISSING", "FINAL_SCALAR_REWIND_GAP", "OBJECT_REFERENCE_REWIND_ID_MISSING" ->
                    guide("#compiled-code-and-rewind-findings",
                            "Implement the supported object recreation and capture/restore contract; store object references by rewind identity and verify replay.");
            case "ENTRYPOINT_MISSING", "ENTRYPOINT_CONTRACT", "ENTRYPOINT_CONSTRUCTOR" ->
                    new Guidance(CATALOG + "#compiled-code-and-rewind-findings",
                            "Point entrypoint at a packaged public concrete GgfMod implementation with a public no-arg constructor; rebuild.",
                            "com.openggf.mods.code.GgfMod");
            case "CONSTRUCTOR_SERVICES_ACCESS" -> guide("#compiled-code-and-rewind-findings",
                    "Move service access from constructors to supported lifecycle callbacks and validate again.");
            case "NON_API_ENGINE_REFERENCE" -> guide("#compiled-code-and-rewind-findings",
                    "Review the reported internal symbol against the public Mod API. No general supported replacement is known; retain it only with explicit compatibility risk and matching-build testing.");
            case "CLI_INPUT_INVALID" -> new Guidance("docs/modding/ggfmod.md",
                    "Correct the path or command arguments, then retry the same report command.", null);
            case "COMMAND_FAILED", "VALIDATION_FAILED" -> new Guidance("docs/modding/ggfmod.md",
                    "Check the reported input, flags and filesystem access; correct the cause and rerun. An operational failure does not certify the artifact.", null);
            default -> new Guidance(CATALOG,
                    "Inspect the original code, message and location in the matching build's finding catalog; no code-specific remedy is known. Correct the cause and validate again.", null);
        };
    }

    private static Guidance guide(String anchor, String action) {
        return new Guidance(CATALOG + anchor, action, null);
    }
}
