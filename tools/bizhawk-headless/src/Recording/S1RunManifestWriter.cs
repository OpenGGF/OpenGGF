using System.Collections.Generic;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Byte-exact port of the S1 complete-run recorder's run_manifest.json
    /// emitter (tools/bizhawk/s1_complete_run_recorder.lua
    /// write_run_manifest L785-849; spec s1-run-mode-behavior.md §6),
    /// delegating the structural layout to the shared
    /// <see cref="RunManifestWriter"/> core. S1 deltas versus S2: the game
    /// literal is "s1", rom_checksum is the inline "AFE05EEE" literal (S1
    /// World REV01 CRC32 — not computed at runtime), source_bk2 is rendered
    /// with Lua %q rather than json_escape, and the run_id line is emitted
    /// only when OGGF_TRACE_RUN_ID was set (the S1 detour machine is always
    /// on, so a manifest can exist without a run id whenever a detour
    /// occurred — pass null to omit the line).
    ///
    /// lua_script_version is a capture-session input rather than an inline
    /// constant: the current Lua stamps "3.17", but the canonical
    /// runs/s1-ghz-maze-roundtrip fixtures were captured by an interim
    /// script stamped "3.15" whose output is byte-identical apart from the
    /// version strings (verified against the actual version-bump commits,
    /// spec §10). Injecting the session's stamp keeps the differential gate
    /// an exact byte comparison with no version-line normalization.
    /// Production captures pass
    /// <see cref="S1CompleteRunMetadataWriter.LuaScriptVersion"/>.
    /// </summary>
    public static class S1RunManifestWriter
    {
        public const string RomChecksum = "AFE05EEE";

        public static string Format(
            string runId,
            string sourceBk2,
            string luaScriptVersion,
            IList<RunManifestSegment> segments,
            IList<RunManifestTransition> transitions)
        {
            return RunManifestWriter.Format(
                "s1",
                runId,
                sourceBk2,
                true,
                RomChecksum,
                luaScriptVersion,
                segments,
                transitions);
        }
    }
}
