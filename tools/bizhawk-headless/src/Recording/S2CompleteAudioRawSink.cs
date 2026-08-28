using System;
using System.Globalization;
using System.IO;
using Newtonsoft.Json.Linq;

namespace OpenGGF.BizHawk.Headless
{
    internal interface IS2CompleteAudioStateSource
    {
        bool IsLagged { get; }
        byte[] CaptureDriverState();
    }

    internal sealed class GpgxS2CompleteAudioStateSource : IS2CompleteAudioStateSource
    {
        private readonly GpgxHost host;

        internal GpgxS2CompleteAudioStateSource(GpgxHost value)
        { host = value ?? throw new ArgumentNullException("value"); }

        public bool IsLagged { get { return host.IsLagged; } }

        public byte[] CaptureDriverState()
        {
            int start = S2AudioObserverProfile.DriverStateStart;
            int end = S2AudioObserverProfile.DriverStateExclusiveEnd;
            var bytes = new byte[end - start];
            for (int index = 0; index < bytes.Length; index++)
                bytes[index] = host.ReadZ80RamByte(start + index);
            return bytes;
        }
    }

    /// <summary>
    /// Lossless bounded staging stream. It carries no canonical-store or
    /// publication authority; the Java game adapter validates it separately.
    /// </summary>
    internal sealed class S2CompleteAudioRawSink : IS2CompleteAudioCaptureSink
    {
        internal const string Schema = "openggf.s2-complete-run-audio-raw.v1";
        private readonly IS2CompleteAudioStateSource state;
        private readonly TextWriter output;
        private int lastRow = -1;
        private bool begun;
        private bool complete;

        internal S2CompleteAudioRawSink(IS2CompleteAudioStateSource stateSource,
            TextWriter writer)
        {
            state = stateSource ?? throw new ArgumentNullException("stateSource");
            output = writer ?? throw new ArgumentNullException("writer");
        }

        public void Begin(CompleteRunAudioObserver.CutoffFrontier boundary)
        {
            if (begun) throw new InvalidOperationException("The S2 raw epoch already began.");
            if (boundary == null) throw new ArgumentNullException("boundary");
            Write(new JObject
            {
                ["type"]="metadata", ["schema"]=Schema,
                ["rom_sha1"]=S2AudioObserverProfile.RomSha1,
                ["bk2_sha256"]=S2AudioObserverProfile.MovieSha256,
                ["service_manifest_sha256"]=S2AudioObserverProfile.ServiceManifestSha256,
                ["first_row"]=S2AudioObserverProfile.FirstRow,
                ["exclusive_end"]=S2AudioObserverProfile.ExclusiveEnd,
                ["state_start"]=S2AudioObserverProfile.DriverStateStart,
                ["state_exclusive_end"]=S2AudioObserverProfile.DriverStateExclusiveEnd
            });
            JObject baseline = Boundary("baseline", boundary);
            baseline["row"] = S2AudioObserverProfile.FirstRow;
            Write(baseline);
            begun = true;
        }

        public void Frame(int row, CompleteRunAudioObserver.FrameCapture frame)
        {
            RequireActive();
            if (frame == null) throw new ArgumentNullException("frame");
            int expected = lastRow < 0 ? S2AudioObserverProfile.FirstRow : lastRow + 1;
            if (row != expected || row >= S2AudioObserverProfile.ExclusiveEnd)
                throw new InvalidDataException("The S2 raw rows are not contiguous and in range.");
            var events = new JArray();
            foreach (GpgxAudioTraceEvent value in frame.RawEvents)
            {
                events.Add(new JObject
                {
                    ["ordinal"]=value.Ordinal, ["service_token"]=value.ServiceToken,
                    ["parent_token"]=value.ParentToken, ["pc"]=value.Pc,
                    ["subject"]=value.Subject, ["offset"]=value.Offset,
                    ["kind"]=value.Kind, ["service_kind"]=value.ServiceKindId,
                    ["depth"]=value.Depth, ["source_cpu"]=value.SourceCpu,
                    ["payload_length"]=value.PayloadLength, ["value"]=value.Value,
                    ["flags"]=value.Flags, ["reserved"]=value.Reserved,
                    ["payload"]=value.Payload.ToString(CultureInfo.InvariantCulture)
                });
            }
            Write(new JObject
            {
                ["type"]="frame", ["row"]=row, ["lag"]=state.IsLagged,
                ["state_hex"]=StateHex(), ["events"]=events
            });
            lastRow = row;
        }

        public void Complete(CompleteRunAudioObserver.CutoffFrontier cutoff)
        {
            RequireActive();
            if (cutoff == null) throw new ArgumentNullException("cutoff");
            JObject value = Boundary("cutoff", cutoff);
            value["exclusive_end"] = lastRow < 0
                ? S2AudioObserverProfile.FirstRow : lastRow + 1;
            Write(value);
            complete = true;
        }

        private JObject Boundary(string type, CompleteRunAudioObserver.CutoffFrontier frontier)
        {
            var active = new JArray();
            foreach (CompleteRunAudioObserver.DriverService service in frontier.ActiveServices)
                active.Add(Service(service));
            var pending = new JArray();
            foreach (CompleteRunAudioObserver.DriverService service in frontier.PendingServices)
                pending.Add(Service(service));
            return new JObject
            {
                ["type"]=type, ["state_hex"]=StateHex(),
                ["ym_port0_latch"]=frontier.YmPort0Address,
                ["ym_port1_latch"]=frontier.YmPort1Address,
                ["native_arm_epoch"]=frontier.ArmEpoch,
                ["native_armed"]=frontier.IsArmed,
                ["active_services"]=active, ["pending_descendants"]=pending
            };
        }

        private static JObject Service(CompleteRunAudioObserver.DriverService service)
        {
            var chips = new JArray();
            foreach (CompleteRunAudioObserver.OwnedChipEvent chip in service.OwnedChipEvents)
            {
                chips.Add(new JObject
                {
                    ["coordinate"]=chip.Coordinate, ["native_ordinal"]=chip.NativeOrdinal,
                    ["event_kind"]=chip.EventKind, ["subject"]=chip.Subject,
                    ["value"]=chip.Value, ["pc"]=chip.Pc,
                    ["source_cpu"]=chip.SourceCpu, ["data"]=chip.IsData,
                    ["port"]=chip.Port, ["register"]=chip.Register
                });
            }
            var snapshots = new JArray();
            foreach (CompleteRunAudioObserver.SnapshotGroup snapshot in service.Snapshots)
            {
                snapshots.Add(new JObject
                {
                    ["range_id"]=snapshot.RangeId, ["source_cpu"]=snapshot.SourceCpu,
                    ["pc"]=snapshot.Pc, ["bytes_hex"]=Hex(snapshot.Bytes)
                });
            }
            var ancestry = new JArray();
            foreach (CompleteRunAudioObserver.AncestryTransition transition in service.AncestryTransitions)
            {
                ancestry.Add(new JObject
                {
                    ["coordinate"]=transition.Coordinate,
                    ["native_ordinal"]=transition.NativeOrdinal,
                    ["previous_parent_token"]=transition.PreviousParentToken,
                    ["previous_depth"]=transition.PreviousDepth,
                    ["current_parent_token"]=transition.CurrentParentToken,
                    ["current_depth"]=transition.CurrentDepth,
                    ["hook_token"]=transition.HookToken,
                    ["source_cpu"]=transition.SourceCpu, ["pc"]=transition.Pc
                });
            }
            return new JObject
            {
                ["token"]=service.Token, ["parent_token"]=service.ParentToken,
                ["kind"]=service.Kind, ["depth"]=service.Depth,
                ["current_parent_token"]=service.CurrentParentToken,
                ["current_depth"]=service.CurrentDepth,
                ["begin_coordinate"]=service.BeginCoordinate,
                ["end_coordinate"]=service.EndCoordinate,
                ["begin_pc"]=service.BeginPc, ["end_pc"]=service.EndPc,
                ["begin_hook_token"]=service.BeginHookToken,
                ["end_hook_token"]=service.EndHookToken,
                ["begin_source_cpu"]=service.BeginSourceCpu,
                ["cancelled"]=service.Cancelled, ["complete"]=service.IsComplete,
                ["chips"]=chips, ["snapshots"]=snapshots,
                ["ancestry_transitions"]=ancestry
            };
        }

        private string StateHex()
        {
            byte[] bytes = state.CaptureDriverState();
            int expected = S2AudioObserverProfile.DriverStateExclusiveEnd
                - S2AudioObserverProfile.DriverStateStart;
            if (bytes == null || bytes.Length != expected)
                throw new InvalidDataException("The S2 raw state snapshot is not exactly $0000..$1FFF.");
            return Hex(bytes);
        }

        private static string Hex(byte[] bytes)
        {
            char[] value = new char[bytes.Length * 2];
            const string alphabet = "0123456789abcdef";
            for (int index = 0; index < bytes.Length; index++)
            {
                value[index * 2] = alphabet[bytes[index] >> 4];
                value[index * 2 + 1] = alphabet[bytes[index] & 15];
            }
            return new string(value);
        }

        private void Write(JObject value)
        {
            output.Write(value.ToString(Newtonsoft.Json.Formatting.None));
            output.Write('\n');
        }

        private void RequireActive()
        {
            if (!begun || complete)
                throw new InvalidOperationException("The S2 raw epoch is not active.");
        }
    }
}
