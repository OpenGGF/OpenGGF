using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// The one supported trace envelope emitted by native BizHawk-headless
    /// writers. Provenance identifies the producer only; replay behaviour
    /// is selected by trace content and semantic capabilities.
    /// </summary>
    public static class TraceContract
    {
        public const int Schema = 5;
        public const string NativeRecorder = "native-bizhawk-headless";
        public const string LuaDiagnosticRecorder = "lua-bizhawk-diagnostic";
        public const string RecorderVersion = "3.0";
        public const string DynamicArtTransferStatePerFrame =
            "dynamic_art_transfer_state_per_frame";
        public const string NativePreludeBootstrap =
            "native_prelude_bootstrap";

        public static void AppendNativeEnvelope(StringBuilder json)
        {
            json.Append("  \"recorder\": \"").Append(NativeRecorder)
                .Append("\",\n");
            json.Append("  \"recorder_version\": \"")
                .Append(RecorderVersion).Append("\",\n");
            json.Append("  \"trace_schema\": ").Append(Schema)
                .Append(",\n");
        }
    }
}
