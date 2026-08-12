using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Fixed Sonic 1 REV01 complete-run audio reference recorder. Managed
    /// M68K callbacks retain source-level request/dispatch snapshots correlated
    /// 1:1 to native markers. The Task 7 native buffer is the sole owner of the
    /// globally ordered service lifecycle and YM/PSG write stream across both
    /// CPUs. The output is deliberately raw; Java validates and canonicalizes
    /// it into the shared capture store.
    /// </summary>
    internal static class S1CompleteRunAudioReferenceCapture
    {
        internal const string TraceProfile = "complete_run_audio_reference";
        internal const string ManifestFileName =
            "s1-audio-service-manifest-v1.json";
        internal const string RawFileName = "audio_reference_raw.jsonl";
        private const string ManifestSchema =
            "openggf.s1-audio-service-manifest.v1";
        private const string RawSchema =
            "openggf.s1-complete-run-audio-raw.v1";

        internal sealed class ManagedHook
        {
            internal string Name;
            internal uint Pc;
            internal byte[] Opcode;
            internal string Source;
            internal string SourceLabel;
            internal string Action;

            internal string OpcodeHex
            {
                get { return ToHex(Opcode); }
            }
        }

        internal sealed class NativeHook
        {
            internal ushort Token;
            internal byte Cpu;
            internal uint Pc;
            internal byte ExpectedKind;
            internal byte[] Opcode;
            internal string Source;
            internal string SourceLabel;
            internal string Action;

            internal string OpcodeHex
            {
                get { return ToHex(Opcode); }
            }
        }

        internal sealed class Manifest
        {
            internal string RomSha1;
            internal int FirstRow;
            internal int ExclusiveEnd;
            internal int MaximumRecordsPerFrame;
            internal int DriverStateStart;
            internal int DriverStateExclusiveEnd;
            internal Dictionary<uint, ManagedHook> ManagedByPc;
            internal Dictionary<uint, List<NativeHook>> NativeByPc;
            internal Dictionary<ushort, ManagedHook> ManagedByNativeToken;
            internal Dictionary<ushort, byte> NativeActionByToken;
            internal HashSet<uint> LegalContinuations;
            internal GpgxAudioObserverAdapter.Config NativeConfig;
            internal byte[] NativeMask;
            internal GpgxAudioObserverAdapter.ServiceKind[] NativeKinds;
            internal GpgxAudioObserverAdapter.ServiceHook[] NativeServiceHooks;
            internal GpgxAudioObserverAdapter.SnapshotRange[] NativeRanges;

            internal ManagedHook FindManagedHook(uint pc)
            {
                ManagedHook hook;
                if (!ManagedByPc.TryGetValue(pc, out hook))
                {
                    throw new KeyNotFoundException(
                        "No managed S1 audio hook at 0x"
                        + pc.ToString("X", CultureInfo.InvariantCulture) + ".");
                }
                return hook;
            }

            internal NativeHook FindNativeHook(uint pc)
            {
                List<NativeHook> hooks;
                if (!NativeByPc.TryGetValue(pc, out hooks) || hooks.Count == 0)
                {
                    throw new KeyNotFoundException(
                        "No native S1 audio hook at 0x"
                        + pc.ToString("X", CultureInfo.InvariantCulture) + ".");
                }
                return hooks[0];
            }

            internal CompleteRunAudioObserver CreateObserver(
                IGpgxAudioTraceApi api)
            {
                return new CompleteRunAudioObserver(
                    api, NativeConfig, NativeMask, NativeKinds,
                    NativeServiceHooks, NativeRanges);
            }
        }

        internal sealed class CaptureResult
        {
            internal CaptureResult(int rows, int frames)
            {
                RowCount = rows;
                CompletedFrames = frames;
            }

            internal int RowCount { get; private set; }
            internal int CompletedFrames { get; private set; }
        }

        internal static bool IsManagedCorrelationEventKind(byte kind)
        {
            return kind == 1 || kind == 2 || kind == 10;
        }

        internal sealed class ManagedServiceTracker
        {
            private sealed class Entry
            {
                internal ushort Token;
                internal uint Stack;
            }
            private readonly List<Entry> entries = new List<Entry>();
            internal int Count { get { return entries.Count; } }
            internal ushort SingleToken
            {
                get
                {
                    if (entries.Count != 1)
                        throw new InvalidOperationException(
                            "Exactly one managed M68K service is required.");
                    return entries[0].Token;
                }
            }
            internal void Begin(ushort token, uint stack)
            {
                if (MatchesToken(token) || entries.Count >= 8)
                    throw new InvalidOperationException(
                        "Managed service begin reused or overflowed its native token.");
                entries.Add(new Entry { Token=token, Stack=stack });
            }
            internal bool Matches(ushort token, uint stack)
            {
                for (int i=entries.Count-1;i>=0;i--)
                    if (entries[i].Token==token)
                        return entries[i].Stack==stack;
                return false;
            }
            internal void End(ushort token)
            {
                for (int i=entries.Count-1;i>=0;i--)
                    if (entries[i].Token==token)
                    { entries.RemoveAt(i); return; }
                throw new InvalidOperationException(
                    "Native completion had no open managed M68K service token.");
            }
            internal void Clear(){entries.Clear();}
            internal ManagedServiceTracker Clone()
            {
                var copy=new ManagedServiceTracker();
                for(int i=0;i<entries.Count;i++)copy.entries.Add(new Entry
                    {Token=entries[i].Token,Stack=entries[i].Stack});
                return copy;
            }
            internal void Restore(ManagedServiceTracker source)
            {
                entries.Clear();
                for(int i=0;i<source.entries.Count;i++)entries.Add(new Entry
                    {Token=source.entries[i].Token,Stack=source.entries[i].Stack});
            }
            private bool MatchesToken(ushort token)
            {
                for(int i=0;i<entries.Count;i++)
                    if(entries[i].Token==token)return true;
                return false;
            }
        }

        internal static Manifest LoadManifest(string path, byte[] rom)
        {
            if (string.IsNullOrEmpty(path) || !Path.IsPathRooted(path))
            {
                throw new ArgumentException(
                    "The S1 audio manifest path must be absolute.", "path");
            }
            if (rom == null) throw new ArgumentNullException("rom");
            JObject root;
            try
            {
                root = JObject.Parse(File.ReadAllText(path));
            }
            catch (JsonException exception)
            {
                throw new InvalidDataException(
                    "The S1 audio manifest is not strict JSON.", exception);
            }
            ExactProperties(root, "root", "schema", "game", "rom_sha1",
                "interval", "raw_stream", "driver_state",
                "legal_continuations", "m68k_hooks", "native_observer");
            RequireString(root, "schema", ManifestSchema);
            RequireString(root, "game", "s1");
            string sha1 = RequiredString(root, "rom_sha1");
            if (sha1 != RomIdentity.Sonic1Rev01Sha1)
                throw Invalid("ROM SHA-1 is not Sonic 1 World REV01");

            JObject interval = RequiredObject(root, "interval");
            ExactProperties(interval, "interval", "first_row", "exclusive_end");
            int firstRow = RequiredInt(interval, "first_row");
            int exclusiveEnd = RequiredInt(interval, "exclusive_end");
            if (firstRow != 860 || exclusiveEnd != 225101)
                throw Invalid("capture interval is not the reviewed 860..225100 range");

            JObject raw = RequiredObject(root, "raw_stream");
            ExactProperties(raw, "raw_stream", "schema", "max_records_per_frame");
            RequireString(raw, "schema", RawSchema);
            int maximum = RequiredInt(raw, "max_records_per_frame");
            if (maximum < 1 || maximum > 65536)
                throw Invalid("raw per-frame record bound is outside 1..65536");

            JObject driver = RequiredObject(root, "driver_state");
            ExactProperties(driver, "driver_state", "start", "exclusive_end");
            int driverStart = RequiredInt(driver, "start");
            int driverEnd = RequiredInt(driver, "exclusive_end");
            if (driverStart != 0xF000 || driverEnd != 0xF5C0)
                throw Invalid("driver-state range is not exact $FFF000..$FFF5BF");

            var continuations = new HashSet<uint>();
            JArray continuationJson = RequiredArray(root, "legal_continuations");
            uint[] exactContinuations =
            {
                0x71BD4, 0x71BE6, 0x71BF8, 0x71C10,
                0x71C22, 0x71C38, 0x71C44
            };
            foreach (JToken token in continuationJson)
            {
                uint pc = StrictUInt(token, "legal continuation");
                if (!continuations.Add(pc))
                    throw Invalid("duplicate legal continuation");
            }
            if (continuations.Count != exactContinuations.Length)
                throw Invalid("legal continuation set is incomplete");
            foreach (uint pc in exactContinuations)
                if (!continuations.Contains(pc))
                    throw Invalid("legal continuation set is not source-exact");

            var managed = new Dictionary<uint, ManagedHook>();
            foreach (JToken token in RequiredArray(root, "m68k_hooks"))
            {
                JObject value = token as JObject;
                if (value == null) throw Invalid("M68K hook is not an object");
                ExactProperties(value, "M68K hook", "name", "pc", "opcode",
                    "source_label", "source", "action");
                var hook = new ManagedHook
                {
                    Name = RequiredString(value, "name"),
                    Pc = StrictUInt(value["pc"], "M68K hook PC"),
                    Opcode = Hex(RequiredString(value, "opcode")),
                    SourceLabel = RequiredString(value, "source_label"),
                    Source = RequiredString(value, "source"),
                    Action = RequiredString(value, "action")
                };
                if (hook.Pc > 0xFFFFFF)
                    throw Invalid("M68K hook PC is out of range");
                if (!KnownManagedAction(hook.Action))
                    throw Invalid("unknown M68K hook action " + hook.Action);
                if (hook.Pc + hook.Opcode.Length > rom.Length)
                    throw Invalid("M68K hook opcode exceeds the ROM");
                for (int i = 0; i < hook.Opcode.Length; i++)
                {
                    if (rom[hook.Pc + i] != hook.Opcode[i])
                    {
                        throw Invalid("M68K opcode mismatch at 0x"
                            + hook.Pc.ToString("X", CultureInfo.InvariantCulture));
                    }
                }
                if (managed.ContainsKey(hook.Pc))
                    throw Invalid("duplicate M68K hook PC");
                managed.Add(hook.Pc, hook);
            }
            RequireManagedBoundary(managed, 0x00138E, "REQUEST_QUEUE_0");
            RequireManagedBoundary(managed, 0x001394, "REQUEST_QUEUE_1");
            RequireManagedBoundary(managed, 0x00139A, "REQUEST_QUEUE_2");
            RequireManagedBoundary(managed, 0x071B4C, "SERVICE_BEGIN");
            RequireManagedBoundary(managed, 0x071B82, "DEFERRED_SERVICE_CONSUME");
            RequireManagedBoundary(managed, 0x071C4C, "SERVICE_CLOSE");
            RequireManagedBoundary(managed, 0x071FD0, "SERVICE_CLOSE");
            RequireManagedBoundary(managed, 0x0721B8, "SERVICE_CLOSE");
            RequireManagedBoundary(managed, 0x072B9C, "SERVICE_CLOSE");
            RequireManagedBoundary(managed, 0x072C24, "CLOSE_IF_RETURN_OUTSIDE");
            RequireManagedBoundary(managed, 0x072E04, "CLOSE_IF_RETURN_OUTSIDE");

            Manifest manifest = ParseNative(root, maximum, managed, continuations);
            manifest.RomSha1 = sha1;
            manifest.FirstRow = firstRow;
            manifest.ExclusiveEnd = exclusiveEnd;
            manifest.MaximumRecordsPerFrame = maximum;
            manifest.DriverStateStart = driverStart;
            manifest.DriverStateExclusiveEnd = driverEnd;
            manifest.ManagedByPc = managed;
            manifest.LegalContinuations = continuations;
            return manifest;
        }

        private static Manifest ParseNative(JObject root, int maximum,
            Dictionary<uint, ManagedHook> managed,
            HashSet<uint> legalContinuations)
        {
            JObject native = RequiredObject(root, "native_observer");
            ExactProperties(native, "native_observer", "ranges", "kinds",
                "z80_hooks", "z80_watch_pc_union", "arm_service", "m68k_binding");
            JArray rangeJson = RequiredArray(native, "ranges");
            var ranges = new GpgxAudioObserverAdapter.SnapshotRange[rangeJson.Count];
            var rangeIndices = new Dictionary<ushort, ushort>();
            for (int i = 0; i < rangeJson.Count; i++)
            {
                JObject value = rangeJson[i] as JObject;
                if (value == null) throw Invalid("native range is not an object");
                ushort id = StrictUShort(value["id"], "native range id");
                string source = RequiredString(value, "source");
                if (id == 0 || rangeIndices.ContainsKey(id))
                    throw Invalid("invalid or duplicate native range");
                rangeIndices.Add(id, (ushort)i);
                if (source == "M68K_RETURN_PC")
                {
                    ExactProperties(value, "native predicate range", "id", "source", "pc");
                    uint pc = StrictUInt(value["pc"], "native predicate PC");
                    if (pc == 0 || pc > 0xFFFFFF)
                        throw Invalid("native predicate PC is out of range");
                    ranges[i] = new GpgxAudioObserverAdapter.SnapshotRange
                    { RangeId=id, Flags=1, Reserved0=pc };
                }
                else
                {
                    ExactProperties(value, "native snapshot range", "id", "source",
                        "start", "exclusive_end");
                    ushort start = StrictUShort(value["start"], "native range start");
                    int end = RequiredInt(value, "exclusive_end");
                    int limit = source == "Z80_RAM" ? 8192
                        : source == "M68K_RAM" ? 65536 : -1;
                    if (limit < 0 || end <= start || end > limit)
                        throw Invalid("invalid native snapshot range");
                    ranges[i] = new GpgxAudioObserverAdapter.SnapshotRange
                    {
                        RangeId=id, Start=start, Length=(ushort)(end-start),
                        Flags=(ushort)(source=="M68K_RAM"?2:0)
                    };
                }
            }

            JArray kindJson = RequiredArray(native, "kinds");
            var kinds = new GpgxAudioObserverAdapter.ServiceKind[kindJson.Count];
            var kindIds = new HashSet<byte>();
            uint snapshotBytes = 0;
            byte maximumContinuation = 0;
            for (int i = 0; i < kindJson.Count; i++)
            {
                JObject value = kindJson[i] as JObject;
                if (value == null) throw Invalid("native kind is not an object");
                ExactProperties(value, "native kind", "id", "flags", "continuation_frames", "canonical_ranges");
                byte id = StrictByte(value["id"], "native kind id");
                if (id == 0 || !kindIds.Add(id))
                    throw Invalid("invalid or duplicate native kind");
                byte flags = KindFlags(RequiredArray(value, "flags"));
                byte continuation = StrictByte(value["continuation_frames"],
                    "native continuation-frame limit");
                if (((flags&2)!=0) != (continuation!=0))
                    throw Invalid("native continuation-frame limit disagrees with kind flags");
                if (continuation > maximumContinuation) maximumContinuation=continuation;
                ushort first, count;
                Slice(RequiredArray(value, "canonical_ranges"), rangeIndices,
                    out first, out count);
                for (int j = 0; j < count; j++)
                    if (ranges[first+j].Flags == 1)
                        throw Invalid("predicate range used as a service snapshot");
                kinds[i] = new GpgxAudioObserverAdapter.ServiceKind
                {
                    KindId=id, Flags=flags, CancellationRangeFirst=first,
                    CancellationRangeCount=count,
                    ContinuationFrameLimit=continuation
                };
                snapshotBytes = checked(snapshotBytes + RangeLength(ranges, first, count));
            }
            for (byte id = 1; id <= 6; id++)
                if (!kindIds.Contains(id)) throw Invalid("native service-kind set is incomplete");

            var hookList = new List<GpgxAudioObserverAdapter.ServiceHook>();
            var publicHooks = new Dictionary<uint, List<NativeHook>>();
            var managedByToken = new Dictionary<ushort, ManagedHook>();
            var hookTokens = new HashSet<ushort>();
            var mask = new byte[8192];
            var union = new HashSet<uint>();
            foreach (JToken item in RequiredArray(native, "z80_hooks"))
            {
                JObject value = item as JObject;
                if (value == null) throw Invalid("Z80 native hook is not an object");
                ExactProperties(value, "Z80 native hook", "token", "action", "pc",
                    "kind", "expected_kind", "opcode", "source_label", "source",
                    "ranges", "flags");
                ushort token = StrictUShort(value["token"], "Z80 hook token");
                uint pc = StrictUInt(value["pc"], "Z80 hook PC");
                byte[] opcode = Hex(RequiredString(value, "opcode"));
                string actionName = RequiredString(value, "action");
                byte action = NativeAction(actionName);
                byte kind = StrictByte(value["kind"], "Z80 hook kind");
                byte expected = StrictByte(value["expected_kind"], "Z80 expected kind");
                if (token == 0 || !hookTokens.Add(token) || pc > 0xFFFF)
                    throw Invalid("invalid or duplicate Z80 native hook");
                ushort first, count;
                Slice(RequiredArray(value, "ranges"), rangeIndices, out first, out count);
                if (RequiredArray(value, "flags").Count != 0
                    || (action == 1 ? count != 0 : count == 0))
                    throw Invalid("Z80 native hook shape is invalid");
                var nativeHook = new NativeHook
                {
                    Token=token, Cpu=1, Pc=pc, ExpectedKind=expected, Opcode=opcode,
                    SourceLabel=RequiredString(value, "source_label"),
                    Source=RequiredString(value, "source"), Action=actionName
                };
                AddPublicHook(publicHooks, nativeHook);
                hookList.Add(new GpgxAudioObserverAdapter.ServiceHook
                {
                    HookToken=token, Action=action, Cpu=1, Pc=pc,
                    ServiceKindId=kind, ExpectedActiveKind=expected,
                    OpcodeLength=(byte)opcode.Length, Opcode=Pack(opcode),
                    RangeFirst=first, RangeCount=count
                });
                snapshotBytes = checked(snapshotBytes + RangeLength(ranges, first, count));
                mask[pc>>3] |= (byte)(1 << ((int)pc&7)); union.Add(pc);
            }

            JObject arm = RequiredObject(native, "arm_service");
            ExactProperties(arm, "native arm service", "kind", "range", "begin", "completion");
            byte armKind = StrictByte(arm["kind"], "native arm kind");
            ushort armRangeId = StrictUShort(arm["range"], "native arm range");
            ushort armRange;
            if (armKind != 5 || !rangeIndices.TryGetValue(armRangeId, out armRange)
                || ranges[armRange].Start != 0 || ranges[armRange].Length != 8192
                || ranges[armRange].Flags != 0)
                throw Invalid("native arm service does not prove the full Z80 RAM");
            AddArmHook(RequiredObject(arm, "begin"), 1, armKind, 0,
                0, 0, 2, hookList, publicHooks, hookTokens);
            AddArmHook(RequiredObject(arm, "completion"), 2, 0, armKind,
                armRange, 1, 3, hookList, publicHooks, hookTokens);
            snapshotBytes = checked(snapshotBytes + ranges[armRange].Length);

            JObject binding = RequiredObject(native, "m68k_binding");
            ExactProperties(binding, "m68k_binding", "first_token", "service_kind",
                "proof_range", "predicate_ranges", "queue_expected_kinds",
                "begin_expected_kinds", "direct_parent_retry_async_kinds",
                "deferred_begin_blocker_kind",
                "deferred_consume_observation_kinds",
                "retry_expected_kind", "internal_expected_kind");
            ushort nextToken = StrictUShort(binding["first_token"], "M68K first token");
            byte serviceKind = StrictByte(binding["service_kind"], "M68K service kind");
            if (nextToken != 100 || serviceKind != 4
                || StrictByte(binding["retry_expected_kind"], "retry kind") != 4
                || StrictByte(binding["internal_expected_kind"], "internal kind") != 4)
                throw Invalid("M68K native binding identity differs");
            ushort proofFirst, proofCount;
            Slice(new JArray(binding["proof_range"]), rangeIndices,
                out proofFirst, out proofCount);
            if (proofCount != 1 || ranges[proofFirst].Flags != 2)
                throw Invalid("M68K proof range differs");
            ushort predicateFirst, predicateCount;
            Slice(RequiredArray(binding, "predicate_ranges"), rangeIndices,
                out predicateFirst, out predicateCount);
            if (predicateCount != legalContinuations.Count)
                throw Invalid("M68K predicate range set differs");
            for (int i = 0; i < predicateCount; i++)
                if (ranges[predicateFirst+i].Flags != 1
                    || !legalContinuations.Contains(ranges[predicateFirst+i].Reserved0))
                    throw Invalid("M68K predicate range is not source-exact");
            byte[] queueKinds = ExactKindList(binding, "queue_expected_kinds", 0, 2, 3);
            byte[] beginKinds = ExactKindList(binding, "begin_expected_kinds", 0, 2, 3);
            byte[] directParentRetryKinds = ExactKindList(binding,
                "direct_parent_retry_async_kinds", 2, 3);
            byte deferredBeginBlockerKind=StrictByte(
                binding["deferred_begin_blocker_kind"],"deferred begin blocker kind");
            if(deferredBeginBlockerKind!=6)
                throw Invalid("deferred begin blocker kind differs");
            byte[] deferredConsumeObservationKinds=ExactKindList(binding,
                "deferred_consume_observation_kinds",2,3,4);

            var managedHooks = new List<ManagedHook>(managed.Values);
            managedHooks.Sort((left, right) => left.Pc.CompareTo(right.Pc));
            foreach (ManagedHook managedHook in managedHooks)
            {
                if (managedHook.Action.StartsWith("REQUEST_QUEUE_", StringComparison.Ordinal))
                {
                    foreach (byte expected in queueKinds)
                        AddManagedNativeHook(hookList, publicHooks, managedByToken,
                            hookTokens, managedHook, ref nextToken, 7, 0, expected, 0, 0, 0);
                }
                else if (managedHook.Action == "SERVICE_BEGIN")
                {
                    foreach (byte expected in beginKinds)
                        AddManagedNativeHook(hookList, publicHooks, managedByToken,
                            hookTokens, managedHook, ref nextToken, 1, serviceKind, expected, 0, 0, 0);
                    AddManagedNativeHook(hookList, publicHooks, managedByToken,
                        hookTokens, managedHook, ref nextToken, 6, 0, serviceKind, 0, 0, 0);
                    if (managedHook.Pc == 0x071B4C)
                    {
                        foreach (byte expected in directParentRetryKinds)
                            AddManagedNativeHook(hookList, publicHooks,
                                managedByToken, hookTokens, managedHook,
                                ref nextToken, 10, serviceKind, expected,
                                0, 0, 0);
                        AddManagedNativeHook(hookList,publicHooks,managedByToken,
                            hookTokens,managedHook,ref nextToken,11,serviceKind,
                            deferredBeginBlockerKind,0,0,0);
                    }
                }
                else if(managedHook.Action=="DEFERRED_SERVICE_CONSUME")
                {
                    foreach(byte expected in deferredConsumeObservationKinds)
                        AddManagedNativeHook(hookList,publicHooks,managedByToken,
                            hookTokens,managedHook,ref nextToken,7,0,
                            expected,0,0,0);
                    AddManagedNativeHook(hookList,publicHooks,managedByToken,
                        hookTokens,managedHook,ref nextToken,12,serviceKind,
                        deferredBeginBlockerKind,0,0,0);
                }
                else if (managedHook.Action == "SERVICE_CLOSE")
                {
                    AddManagedNativeHook(hookList, publicHooks, managedByToken,
                        hookTokens, managedHook, ref nextToken, 2, 0, serviceKind,
                        proofFirst, proofCount, 0);
                    snapshotBytes = checked(snapshotBytes + RangeLength(ranges, proofFirst, proofCount));
                    for (int i=0;i<kinds.Length;i++)
                    {
                        if ((kinds[i].Flags&5)!=5) continue;
                        AddManagedNativeHook(hookList, publicHooks, managedByToken,
                            hookTokens, managedHook, ref nextToken, 8, serviceKind,
                            kinds[i].KindId, proofFirst, proofCount, 0);
                        snapshotBytes=checked(snapshotBytes
                            +RangeLength(ranges,proofFirst,proofCount));
                    }
                }
                else if (managedHook.Action == "CLOSE_IF_RETURN_OUTSIDE")
                {
                    ulong predicate = predicateFirst | ((ulong)predicateCount << 16);
                    AddManagedNativeHook(hookList, publicHooks, managedByToken,
                        hookTokens, managedHook, ref nextToken, 5, 0, serviceKind,
                        proofFirst, proofCount, predicate);
                    snapshotBytes = checked(snapshotBytes + RangeLength(ranges, proofFirst, proofCount));
                    if (managedHook.Pc == 0x072E04)
                    {
                        for (int i=0;i<kinds.Length;i++)
                        {
                            if ((kinds[i].Flags&5)!=5) continue;
                            AddManagedNativeHook(hookList,publicHooks,managedByToken,
                                hookTokens,managedHook,ref nextToken,9,serviceKind,
                                kinds[i].KindId,proofFirst,proofCount,predicate);
                            snapshotBytes=checked(snapshotBytes
                                +RangeLength(ranges,proofFirst,proofCount));
                        }
                    }
                }
                else
                {
                    for (int i=0;i<kinds.Length;i++)
                        if ((kinds[i].Flags&5)==5)
                            AddManagedNativeHook(hookList, publicHooks, managedByToken,
                                hookTokens, managedHook, ref nextToken, 7, 0,
                                kinds[i].KindId, 0, 0, 0);
                    AddManagedNativeHook(hookList, publicHooks, managedByToken,
                        hookTokens, managedHook, ref nextToken, 7, 0, serviceKind, 0, 0, 0);
                }
            }
            hookList.Sort((left, right) => left.Cpu != right.Cpu
                ? left.Cpu.CompareTo(right.Cpu) : left.Pc != right.Pc
                    ? left.Pc.CompareTo(right.Pc) : left.HookToken.CompareTo(right.HookToken));

            RequireNativeBoundary(publicHooks, 0x003A, "PUSH_BEGIN", "d681");
            RequireNativeBoundary(publicHooks, 0x0077, "PUSH_BEGIN", "1a");
            RequireNativeBoundary(publicHooks, 0x0077, "TAIL_POP_PUSH", "1a");
            RequireNativeBoundary(publicHooks, 0x00AC, "POP_END_AT_PC", "c23200");
            RequireNativeBoundary(publicHooks, 0x00C1, "PUSH_BEGIN", "1a");
            RequireNativeBoundary(publicHooks, 0x00D0, "POP_END_AT_PC", "c2c100");
            var declaredUnion = new HashSet<uint>();
            foreach (JToken token in RequiredArray(native, "z80_watch_pc_union"))
                if (!declaredUnion.Add(StrictUInt(token, "Z80 watch PC")))
                    throw Invalid("duplicate Z80 watch PC");
            if (!union.SetEquals(declaredUnion))
                throw Invalid("Z80 watch mask is not the exact hook union");

            GpgxAudioObserverAdapter.ServiceHook[] hooks = hookList.ToArray();
            var nativeActionByToken = new Dictionary<ushort, byte>();
            foreach (GpgxAudioObserverAdapter.ServiceHook hook in hooks)
                nativeActionByToken.Add(hook.HookToken,hook.Action);
            var config = new GpgxAudioObserverAdapter.Config
            {
                Magic=0x31544147, AbiVersion=3, StructSize=64,
                HookSize=32, RangeSize=16, EventSize=32,
                MaxDepth=8, MaxOpcodeBytes=8, ResetServiceKind=1,
                MaxContinuationFrames=maximumContinuation, Flags=1, WatchMaskBytes=8192,
                HookCount=(uint)hooks.Length, RangeCount=(uint)ranges.Length,
                SnapshotBytesTotal=snapshotBytes, EventCapacity=65536,
                MaxServiceTokensPerFrame=65535, KindSize=16,
                KindCount=(ushort)kinds.Length
            };
            return new Manifest
            {
                NativeByPc=publicHooks, ManagedByNativeToken=managedByToken,
                NativeActionByToken=nativeActionByToken,
                NativeConfig=config, NativeMask=mask, NativeKinds=kinds,
                NativeServiceHooks=hooks, NativeRanges=ranges,
                MaximumRecordsPerFrame=maximum
            };
        }

        private static void AddArmHook(JObject value, byte action,
            byte serviceKind, byte expectedKind, ushort rangeFirst,
            ushort rangeCount, byte flags,
            List<GpgxAudioObserverAdapter.ServiceHook> hooks,
            Dictionary<uint, List<NativeHook>> publicHooks,
            HashSet<ushort> hookTokens)
        {
            ExactProperties(value, "native arm hook", "token", "pc", "opcode",
                "source_label", "source");
            ushort token = StrictUShort(value["token"], "native arm hook token");
            uint pc = StrictUInt(value["pc"], "native arm hook PC");
            byte[] opcode = Hex(RequiredString(value, "opcode"));
            if (token == 0 || !hookTokens.Add(token) || pc > 0xffffff)
                throw Invalid("invalid or duplicate native arm hook");
            hooks.Add(new GpgxAudioObserverAdapter.ServiceHook
            {
                HookToken=token,Action=action,Cpu=2,Pc=pc,
                ServiceKindId=serviceKind,ExpectedActiveKind=expectedKind,
                OpcodeLength=(byte)opcode.Length,Opcode=Pack(opcode),
                RangeFirst=rangeFirst,RangeCount=rangeCount,Flags=flags
            });
            var native = new NativeHook
            {
                Token=token,Cpu=2,Pc=pc,ExpectedKind=expectedKind,
                Opcode=opcode,SourceLabel=RequiredString(value,"source_label"),
                Source=RequiredString(value,"source"),
                Action=action==1?"PUSH_BEGIN":"POP_END_AT_PC"
            };
            AddPublicHook(publicHooks,native);
        }

        internal static CaptureResult Capture(Bk2Movie movie, IGpgxHost host,
            ICpuRegisterReader registers, IGpgxAudioTraceApi api,
            Manifest manifest, TextWriter output)
        {
            if (movie == null) throw new ArgumentNullException("movie");
            if (movie.FrameCount < manifest.ExclusiveEnd)
                throw new InvalidDataException("The BK2 ends before S1 audio row 225100.");
            int row = 0;
            using (IEnumerator<Bk2Frame> frames =
                movie.OpenFrameStream().GetEnumerator())
            using (var session = new Session(
                host, registers, api, manifest, output))
            {
                while (row < manifest.FirstRow)
                {
                    if (!frames.MoveNext())
                        throw new InvalidDataException("The BK2 is missing the S1 audio baseline row.");
                    Bk2Frame frame = frames.Current;
                    int observedRow = row;
                    session.ObservePreEpochFrame(observedRow, frame, () =>
                    {
                        S1TraceCaptureRunner.ApplyFrame(frame, host);
                        host.Advance();
                    });
                    row++;
                }
                session.BeginEpoch();
                while (row < manifest.ExclusiveEnd)
                {
                    if (!frames.MoveNext())
                        throw new InvalidDataException("The BK2 ended inside the S1 audio interval.");
                    Bk2Frame frame = frames.Current;
                    int capturedRow = row;
                    session.CaptureFrame(capturedRow, frame, () =>
                    {
                        S1TraceCaptureRunner.ApplyFrame(frame, host);
                        host.Advance();
                    });
                    row++;
                }
                session.Complete(manifest.ExclusiveEnd);
            }
            return new CaptureResult(
                manifest.ExclusiveEnd - manifest.FirstRow,
                host.CompletedFrame);
        }

        internal sealed class Session : IDisposable
        {
            private sealed class PendingManagedOccurrence
            {
                internal ManagedHook Hook;
                internal uint Stack;
                internal uint ReturnPc;
                internal long ManagedCorrelationOrdinal;
                internal bool ClosesService;
                internal bool ConditionalPopMarkerSeen;
                internal readonly List<JObject> Records = new List<JObject>();
                internal readonly List<JObject> NativeCorrelationEvents =
                    new List<JObject>();
            }

            private sealed class DeferredManagedBegin
            {
                internal ushort BlockerToken,BlockerParentToken,HookToken;
                internal byte BlockerKind,BlockerDepth,TargetKind,SourceCpu;
                internal uint Pc,Stack,ReturnPc;
                internal int CorrelatedObservationCount;
                internal readonly List<PendingManagedOccurrence> Observations=
                    new List<PendingManagedOccurrence>();
            }

            private static readonly string[] RegisterNames =
            {
                "D0", "D1", "D2", "D3", "D4", "D5", "D6", "D7",
                "A0", "A1", "A2", "A3", "A4", "A5", "A6", "A7"
            };
            private readonly IGpgxHost host;
            private readonly ICpuRegisterReader registers;
            private readonly IGpgxAudioTraceApi api;
            private readonly Manifest manifest;
            private readonly TextWriter output;
            private readonly CompleteRunAudioObserver observer;
            private readonly List<IDisposable> callbacks = new List<IDisposable>();
            private readonly Queue<PendingManagedOccurrence> pendingManaged =
                new Queue<PendingManagedOccurrence>();
            private readonly Queue<PendingManagedOccurrence> pendingBoundaryManaged =
                new Queue<PendingManagedOccurrence>();
            private PendingManagedOccurrence collectingManaged;
            private DeferredManagedBegin pendingDeferredBegin;
            private DeferredManagedBegin boundaryDeferredBegin;
            private StringWriter frameTransaction;
            private StringWriter deferredPublication;
            private readonly List<JObject> deferredEvidencePublication=
                new List<JObject>();
            private JObject pendingResetEvidence;
            private JObject pendingResetServiceSnapshot;
            private ushort pendingResetCancelledServiceToken;
            private ushort activeResetToken;
            private int expectedResetGroups;
            private int completedResetGroups;
            private bool expectedResetCancellationSeen;
            private uint expectedResetCancellationOrdinal;
            private bool frameServiceOpenBeforeAdvance;
            private bool resetInputAssertedThisFrame;
            private int currentRow = -1;
            private int lastRow = -1;
            private int frameRecords;
            private long nextManagedCorrelationOrdinal;
            private readonly ManagedServiceTracker managedServices =
                new ManagedServiceTracker();
            private readonly ManagedServiceTracker boundaryManagedServices =
                new ManagedServiceTracker();
            private long nextRequestId = 1;
            private readonly long?[] pendingRequestIds = new long?[3];
            private readonly byte[] pendingRequestSounds = new byte[3];
            private int cycleQueue = -1;
            private byte cycleSound;
            private byte cyclePreexistingSound;
            private long? cycleRequestId;
            private long? selectedRequestId;
            private bool capturing;
            private bool publishing;
            private bool epochStarted;
            private bool complete;
            private bool disposed;
            internal int PendingDeferredObservationCountForTesting
            {get{return pendingDeferredBegin==null
                ?0:pendingDeferredBegin.CorrelatedObservationCount;}}

            internal Session(IGpgxHost host, ICpuRegisterReader registers,
                IGpgxAudioTraceApi api, Manifest manifest, TextWriter output)
            {
                this.host = host ?? throw new ArgumentNullException("host");
                this.registers = registers
                    ?? throw new ArgumentNullException("registers");
                this.api = api ?? throw new ArgumentNullException("api");
                this.manifest = manifest
                    ?? throw new ArgumentNullException("manifest");
                this.output = output ?? throw new ArgumentNullException("output");
                observer = manifest.CreateObserver(api);
                try
                {
                    foreach (ManagedHook hook in manifest.ManagedByPc.Values)
                    {
                        ManagedHook captured = hook;
                        callbacks.Add(host.RegisterExecuteCallback(
                            captured.Pc, () => OnHook(captured)));
                    }
                }
                catch
                {
                    Dispose();
                    throw;
                }
            }

            internal void ObservePreEpochFrame(int row, Bk2Frame input, Action advance)
            {
                if (epochStarted) throw new InvalidOperationException("The comparison epoch has begun.");
                ProcessFrame(row, input, advance, false);
            }

            internal void BeginEpoch()
            {
                if (epochStarted) throw new InvalidOperationException("The comparison epoch already began.");
                if (capturing || pendingManaged.Count != 0
                    || pendingBoundaryManaged.Count != 0)
                    throw new InvalidOperationException("Cannot begin the epoch with an incomplete native drain.");
                CompleteRunAudioObserver.CutoffFrontier frontier =
                    observer.CaptureCutoffFrontier();
                DeferredManagedBegin carriedDeferred=ValidateBoundaryDeferredBegin(
                    frontier.PendingDeferredBegin);
                frontier=observer.CaptureBoundaryFrontierAndResetPublication();
                pendingDeferredBegin=carriedDeferred;
                managedServices.Restore(boundaryManagedServices);
                boundaryManagedServices.Clear();
                boundaryDeferredBegin=null;
                Write(new JObject
                {
                    ["type"]="metadata", ["schema"]=RawSchema,
                    ["rom_sha1"]=manifest.RomSha1,
                    ["first_row"]=manifest.FirstRow,
                    ["exclusive_end"]=manifest.ExclusiveEnd,
                    ["native_abi"]=api.AbiVersion,
                    ["native_event_size"]=api.EventSize,
                    ["native_capacity"]=api.Capacity
                });
                var active = new JArray();
                foreach (CompleteRunAudioObserver.DriverService service in frontier.ActiveServices)
                    active.Add(BoundaryService(service, true));
                var pending = new JArray();
                foreach (CompleteRunAudioObserver.DriverService service in frontier.PendingServices)
                    pending.Add(BoundaryService(service, false));
                Write(new JObject
                {
                    ["type"]="baseline", ["row"]=manifest.FirstRow,
                    ["state_start"]=manifest.DriverStateStart,
                    ["state_hex"]=CaptureDriverState(),
                    ["ym_port0_latch"]=frontier.YmPort0Address,
                    ["ym_port1_latch"]=frontier.YmPort1Address,
                    ["active_services"]=active,
                    ["pending_descendants"]=pending,
                    ["native_arm_epoch"]=frontier.ArmEpoch,
                    ["native_armed"]=frontier.IsArmed
                });
                for (int i=0;i<pendingRequestIds.Length;i++) pendingRequestIds[i]=null;
                cycleQueue=-1;cycleRequestId=null;selectedRequestId=null;
                nextRequestId=1;nextManagedCorrelationOrdinal=0;lastRow=-1;
                epochStarted=true;
            }

            private JObject BoundaryService(
                CompleteRunAudioObserver.DriverService service, bool carriedIn)
            {
                var transitions = new JArray();
                foreach (CompleteRunAudioObserver.AncestryTransition transition
                    in service.AncestryTransitions)
                {
                    transitions.Add(new JObject
                    {
                        ["coordinate"]=transition.Coordinate,
                        ["native_ordinal"]=transition.NativeOrdinal,
                        ["previous_parent_token"]=transition.PreviousParentToken,
                        ["previous_depth"]=transition.PreviousDepth,
                        ["current_parent_token"]=transition.CurrentParentToken,
                        ["current_depth"]=transition.CurrentDepth,
                        ["hook_token"]=transition.HookToken,
                        ["source_cpu"]=transition.SourceCpu,
                        ["pc"]=transition.Pc
                    });
                }
                return new JObject
                {
                    ["token"]=service.Token,["parent_token"]=service.ParentToken,
                    ["kind"]=service.Kind,["depth"]=service.Depth,
                    ["current_parent_token"]=service.CurrentParentToken,
                    ["current_depth"]=service.CurrentDepth,
                    ["ancestry_transitions"]=transitions,
                    ["state"]=carriedIn ? "CARRIED_IN_OPEN" : "COMPLETED",
                    ["begin_coordinate"]=service.BeginCoordinate,
                    ["begin_pc"]=service.BeginPc,
                    ["begin_hook_token"]=service.BeginHookToken,
                    ["begin_source_cpu"]=service.BeginSourceCpu,
                    ["state_start"]=manifest.DriverStateStart,
                    ["state_hex"]=CaptureDriverState()
                };
            }

            internal void CaptureFrame(int row, Action advance)
            {
                CaptureFrame(row, null, advance);
            }

            internal void CaptureFrame(int row, Bk2Frame input, Action advance)
            {
                if (!epochStarted) BeginEpoch();
                ProcessFrame(row, input, advance, true);
            }

            private void ProcessFrame(int row, Bk2Frame input, Action advance, bool publish)
            {
                if (disposed) throw new ObjectDisposedException("Session");
                if (complete) throw new InvalidOperationException("The S1 audio capture is complete.");
                if (advance == null) throw new ArgumentNullException("advance");
                int expected = publish ? (lastRow < 0 ? manifest.FirstRow : lastRow + 1) : row;
                if (row != expected || publish && row >= manifest.ExclusiveEnd)
                    throw new InvalidOperationException("Missing or out-of-order S1 audio row.");
                ManagedServiceTracker servicesBefore=managedServices.Clone();
                ManagedServiceTracker boundaryServicesBefore=
                    boundaryManagedServices.Clone();
                DeferredManagedBegin deferredBefore=CloneDeferred(pendingDeferredBegin);
                DeferredManagedBegin boundaryDeferredBefore=
                    CloneDeferred(boundaryDeferredBegin);
                PendingManagedOccurrence[] managedBefore=pendingManaged.ToArray();
                PendingManagedOccurrence[] boundaryManagedBefore=
                    pendingBoundaryManaged.ToArray();
                int deferredEvidenceBefore=deferredEvidencePublication.Count;
                var transaction=new StringWriter(CultureInfo.InvariantCulture);
                frameTransaction=transaction;
                    currentRow = row;
                    frameRecords = 0;
                    nextManagedCorrelationOrdinal = 0;
                capturing = true;
                publishing = publish;
                try
                {
                    WriteFrame(new JObject { ["type"]="frame_begin", ["row"]=row });
                    expectedResetGroups = 0;
                    completedResetGroups = 0;
                    activeResetToken = 0;
                    expectedResetCancellationSeen = false;
                    expectedResetCancellationOrdinal = 0;
                    resetInputAssertedThisFrame = false;
                    frameServiceOpenBeforeAdvance = managedServices.Count != 0;
                    if (input != null && (input.Power || input.Reset))
                    {
                        if(pendingDeferredBegin!=null)
                            throw new InvalidOperationException(
                                "Reset while a deferred M68K service begin is pending.");
                        resetInputAssertedThisFrame = true;
                        expectedResetGroups = (input.Power ? 1 : 0)
                            + (input.Reset ? 1 : 0);
                        pendingResetEvidence = new JObject
                        {
                            ["type"]="input_reset", ["row"]=row,
                            ["power"]=input.Power, ["reset"]=input.Reset
                        };
                        if (managedServices.Count != 0)
                        {
                            if (managedServices.Count != 1)
                                throw new InvalidOperationException(
                                    "Reset with multiple open M68K services requires"
                                    + " distinct managed snapshots.");
                            pendingResetCancelledServiceToken =
                                managedServices.SingleToken;
                            pendingResetServiceSnapshot = new JObject
                            {
                                ["type"]="managed_reset_service_snapshot",
                                ["row"]=row,
                                ["state_start"]=manifest.DriverStateStart,
                                ["state_hex"]=CaptureDriverState(),
                                ["cancelled_service_token"]=
                                    pendingResetCancelledServiceToken
                            };
                        }
                        managedServices.Clear();
                    }
                    observer.CaptureFrame(advance, (events, count) =>
                    {
                        for (int i = 0; i < count; i++)
                        {
                            WriteNative(events[i]);
                            ApplyNativeLifecycle(events[i]);
                            if (publish) CorrelateManaged(events[i]);
                            else CorrelateBoundaryManaged(events[i]);
                        }
                    });
                    if (pendingResetEvidence != null)
                        throw new InvalidOperationException(
                            "A reset input had no native ordered reset lifecycle.");
                    if (pendingManaged.Count != 0
                        || pendingBoundaryManaged.Count != 0)
                        throw new InvalidOperationException(
                            "A managed S1 callback had no native ordered marker.");
                    WriteFrame(new JObject { ["type"]="frame_end", ["row"]=row });
                    if (publish) lastRow = row;
                    if(pendingDeferredBegin!=null)
                    {
                        if(deferredPublication==null)
                            deferredPublication=new StringWriter(
                                CultureInfo.InvariantCulture);
                        deferredPublication.Write(transaction.ToString());
                    }
                    else
                    {
                        if(deferredPublication!=null)
                        {
                            FlushDeferredPublication();
                            deferredPublication=null;
                        }
                        output.Write(transaction.ToString());
                    }
                }
                catch (Exception error)
                {
                    managedServices.Restore(servicesBefore);
                    boundaryManagedServices.Restore(boundaryServicesBefore);
                    pendingDeferredBegin=deferredBefore;
                    boundaryDeferredBegin=boundaryDeferredBefore;
                    pendingManaged.Clear();
                    for(int i=0;i<managedBefore.Length;i++)pendingManaged.Enqueue(managedBefore[i]);
                    pendingBoundaryManaged.Clear();
                    for(int i=0;i<boundaryManagedBefore.Length;i++)
                        pendingBoundaryManaged.Enqueue(boundaryManagedBefore[i]);
                    if(deferredEvidencePublication.Count>deferredEvidenceBefore)
                        deferredEvidencePublication.RemoveRange(deferredEvidenceBefore,
                            deferredEvidencePublication.Count-deferredEvidenceBefore);
                    var pendingNames = new List<string>();
                    foreach (PendingManagedOccurrence occurrence in pendingManaged)
                        pendingNames.Add(occurrence.Hook.Name);
                    throw new InvalidOperationException(
                        "S1 audio capture failed at row "
                        + row.ToString(CultureInfo.InvariantCulture) + ": "
                        + error.Message + " pending_managed="
                        + string.Join(",", pendingNames.ToArray()),
                        error);
                }
                finally
                {
                    capturing = false;
                    publishing = false;
                    currentRow = -1;
                    frameTransaction=null;
                }
            }

            internal void Complete(int exclusiveEnd)
            {
                if (disposed) throw new ObjectDisposedException("Session");
                if (complete) throw new InvalidOperationException("The S1 audio terminal already exists.");
                if (capturing) throw new InvalidOperationException("Cannot terminate inside a frame.");
                if (lastRow < manifest.FirstRow || exclusiveEnd != lastRow + 1)
                    throw new InvalidOperationException("The S1 audio terminal does not follow the final row.");
                if (managedServices.Count != 0)
                    throw new InvalidOperationException("An M68K audio service is open at the terminal.");
                if(pendingDeferredBegin!=null)
                    throw new InvalidOperationException(
                        "A deferred M68K audio service begin is pending at the terminal.");
                Write(new JObject
                {
                    ["type"]="terminal", ["exclusive_end"]=exclusiveEnd,
                    ["rows"]=exclusiveEnd-manifest.FirstRow,
                    ["orphan_closes"]=0, ["opcode_mismatches"]=0,
                    ["overflows"]=0
                });
                complete = true;
            }

            private void OnHook(ManagedHook hook)
            {
                if (!capturing)
                    throw new InvalidOperationException(
                        "S1 audio callback contamination outside an active row.");
                // Before publication retain only the bounded A7/return identity
                // needed to carry an action-11 reservation across the cutoff.
                // No managed evidence from the preceding epoch is published.
                if (!publishing)
                {
                    if(hook.Action!="SERVICE_BEGIN"
                        &&hook.Action!="DEFERRED_SERVICE_CONSUME"
                        &&hook.Action!="SERVICE_CLOSE")return;
                    if(pendingBoundaryManaged.Count>=manifest.MaximumRecordsPerFrame)
                        throw new InvalidOperationException(
                            "The bounded S1 boundary-correlation stream overflowed.");
                    var boundaryOccurrence=new PendingManagedOccurrence
                    {
                        Hook=hook,
                        Stack=ReadM68kRegister("A7"),
                        ReturnPc=ReadReturnPc()
                    };
                    pendingBoundaryManaged.Enqueue(boundaryOccurrence);
                    return;
                }
                var occurrence = new PendingManagedOccurrence { Hook=hook };
                collectingManaged = occurrence;
                bool captured = false;
                try
                {
                    uint? returnPc = null;
                    if (hook.Action == "CLOSE_IF_RETURN_OUTSIDE")
                        returnPc = ReadReturnPc();
                    JObject registerJson = new JObject();
                    foreach (string name in RegisterNames)
                        registerJson[name] = ReadM68kRegister(name);
                    var record = new JObject
                    {
                        ["type"]="managed_hook_evidence", ["row"]=currentRow,
                        ["name"]=hook.Name, ["pc"]=hook.Pc,
                        ["action"]=hook.Action, ["registers"]=registerJson
                    };
                    if (returnPc.HasValue) record["return_pc"] = returnPc.Value;
                    WriteFrame(record);

                    if (hook.Action == "SERVICE_BEGIN"
                        ||hook.Action=="DEFERRED_SERVICE_CONSUME")
                    {
                        occurrence.Stack = ReadM68kRegister("A7");
                        occurrence.ReturnPc = ReadReturnPc();
                    }
                    else if (hook.Action == "SERVICE_CLOSE")
                    {
                        if (!HasManagedServiceCandidate())
                            throw new InvalidOperationException(
                                "An orphan M68K audio service close was observed.");
                        occurrence.ClosesService = true;
                        CaptureServiceState(hook.Name, null, false);
                    }
                    else if (hook.Action == "CLOSE_IF_RETURN_OUTSIDE")
                    {
                        if (!HasManagedServiceCandidate())
                            throw new InvalidOperationException(
                                "An orphan adjusted-return audio close was observed.");
                        if (!manifest.LegalContinuations.Contains(returnPc.Value))
                        {
                            occurrence.ClosesService = true;
                            CaptureServiceState(hook.Name, returnPc, false);
                        }
                    }
                    else if (hook.Action.StartsWith(
                        "REQUEST_QUEUE_", StringComparison.Ordinal))
                    {
                        CaptureRequest(hook.Action[hook.Action.Length - 1] - '0');
                    }
                    else if (hook.Action == "CYCLE_ITERATION_BEGIN")
                    {
                        BeginCycleIteration();
                    }
                    else if (hook.Action == "CYCLE_ITERATION_END")
                    {
                        EndCycleIteration();
                    }
                    else if (hook.Action == "ACTUAL_DISPATCH")
                    {
                        byte sound = (byte)ReadM68kRegister("D7");
                        var dispatch = new JObject
                        {
                            ["type"]="dispatch", ["row"]=currentRow,
                            ["sound_id"]=sound,
                            ["request_id"]=selectedRequestId.HasValue
                                ? new JValue(selectedRequestId.Value)
                                : JValue.CreateNull()
                        };
                        WriteFrame(dispatch);
                    }

                    captured = true;
                }
                finally
                {
                    collectingManaged = null;
                    if (captured)
                        pendingManaged.Enqueue(occurrence);
                }
            }

            private DeferredManagedBegin ValidateBoundaryDeferredBegin(
                CompleteRunAudioObserver.DeferredBeginEvidence native)
            {
                if(native==null)
                {
                    if(boundaryDeferredBegin!=null)
                        throw new InvalidOperationException(
                            "Native/managed deferred boundary identity differs.");
                    return null;
                }
                if(boundaryDeferredBegin==null)
                    throw new InvalidOperationException(
                        "Native deferred boundary reservation had no managed identity.");
                if(native.Consumed
                    ||native.BlockerToken!=boundaryDeferredBegin.BlockerToken
                    ||native.BlockerParentToken
                        !=boundaryDeferredBegin.BlockerParentToken
                    ||native.BlockerKind!=boundaryDeferredBegin.BlockerKind
                    ||native.BlockerDepth!=boundaryDeferredBegin.BlockerDepth
                    ||native.TargetKind!=boundaryDeferredBegin.TargetKind
                    ||native.HookToken!=boundaryDeferredBegin.HookToken
                    ||native.SourceCpu!=boundaryDeferredBegin.SourceCpu
                    ||native.Pc!=boundaryDeferredBegin.Pc
                    ||native.ObservationCount
                        !=boundaryDeferredBegin.CorrelatedObservationCount)
                    throw new InvalidOperationException(
                        "Native/managed deferred boundary identity differs.");
                return CloneDeferred(boundaryDeferredBegin);
            }

            private void CorrelateBoundaryManaged(GpgxAudioTraceEvent value)
            {
                if(!IsManagedCorrelationEventKind(value.Kind))return;
                ManagedHook expected;
                if(!manifest.ManagedByNativeToken.TryGetValue(value.Subject,out expected)
                    ||expected.Action!="SERVICE_BEGIN"
                        &&expected.Action!="DEFERRED_SERVICE_CONSUME"
                        &&expected.Action!="SERVICE_CLOSE")return;
                if(pendingBoundaryManaged.Count==0)return;
                PendingManagedOccurrence occurrence=pendingBoundaryManaged.Peek();
                if(!object.ReferenceEquals(occurrence.Hook,expected)
                    ||occurrence.Hook.Pc!=value.Pc)
                    throw new InvalidOperationException(
                        "Managed/native S1 boundary marker order or PC differs.");
                byte nativeAction;
                if(!manifest.NativeActionByToken.TryGetValue(value.Subject,
                    out nativeAction))
                    throw new InvalidOperationException(
                        "Managed boundary marker has no native action identity.");
                if(expected.Action=="SERVICE_BEGIN")
                {
                    if(value.Kind==10&&value.Value==4)
                    {
                        if(nativeAction!=11)
                            throw new InvalidOperationException(
                                "Deferred S1 boundary marker has no action-11 identity.");
                        if(boundaryDeferredBegin==null)
                        {
                            boundaryDeferredBegin=new DeferredManagedBegin
                            {
                                BlockerToken=value.ServiceToken,
                                BlockerParentToken=value.ParentToken,
                                BlockerKind=value.ServiceKindId,
                                BlockerDepth=value.Depth,
                                TargetKind=4,
                                HookToken=value.Subject,
                                SourceCpu=value.SourceCpu,
                                Pc=value.Pc,
                                Stack=occurrence.Stack,
                                ReturnPc=occurrence.ReturnPc,
                                CorrelatedObservationCount=1
                            };
                        }
                        else
                        {
                            if(boundaryDeferredBegin.BlockerToken!=value.ServiceToken
                                ||boundaryDeferredBegin.BlockerParentToken
                                    !=value.ParentToken
                                ||boundaryDeferredBegin.BlockerKind
                                    !=value.ServiceKindId
                                ||boundaryDeferredBegin.BlockerDepth!=value.Depth
                                ||boundaryDeferredBegin.HookToken!=value.Subject
                                ||boundaryDeferredBegin.SourceCpu!=value.SourceCpu
                                ||boundaryDeferredBegin.Pc!=value.Pc
                                ||boundaryDeferredBegin.Stack!=occurrence.Stack
                                ||boundaryDeferredBegin.ReturnPc
                                    !=occurrence.ReturnPc)
                                throw new InvalidOperationException(
                                    "Deferred S1 boundary callback A7/return identity changed.");
                            boundaryDeferredBegin.CorrelatedObservationCount++;
                        }
                        pendingBoundaryManaged.Dequeue();
                        return;
                    }
                    if(value.Kind==1&&nativeAction==1
                        ||value.Kind==10&&value.Value==2
                            &&(nativeAction==6||nativeAction==10))
                    {
                        if(value.Kind==1)
                            boundaryManagedServices.Begin(value.ServiceToken,
                                occurrence.Stack);
                        pendingBoundaryManaged.Dequeue();
                        return;
                    }
                    return;
                }
                if(nativeAction==7&&value.Kind==10&&value.Value==3)
                {
                    if(!MatchesManagedObservationOwner(boundaryManagedServices,
                        value,occurrence.Stack))
                        throw new InvalidOperationException(
                            "Ordinary S1 driverinput managed ancestor differs.");
                    pendingBoundaryManaged.Dequeue();
                    return;
                }
                if(expected.Action=="SERVICE_CLOSE")
                {
                    if(value.Kind!=2)
                        throw new InvalidOperationException(
                            "Managed boundary close did not match a native completion.");
                    boundaryManagedServices.End(value.ServiceToken);
                    pendingBoundaryManaged.Dequeue();
                    return;
                }
                if(nativeAction!=12||value.Kind!=1
                    ||boundaryDeferredBegin==null
                    ||occurrence.Stack!=boundaryDeferredBegin.Stack
                    ||occurrence.ReturnPc!=boundaryDeferredBegin.ReturnPc
                    ||value.ParentToken!=boundaryDeferredBegin.BlockerToken
                    ||value.ServiceKindId!=boundaryDeferredBegin.TargetKind
                    ||value.Depth!=boundaryDeferredBegin.BlockerDepth+1
                    ||value.SourceCpu!=boundaryDeferredBegin.SourceCpu)
                    throw new InvalidOperationException(
                        "Deferred S1 boundary consume identity or nested begin differs.");
                boundaryDeferredBegin=null;
                pendingBoundaryManaged.Dequeue();
            }

            private void CorrelateManaged(GpgxAudioTraceEvent value)
            {
                // SERVICE_PROMOTE uses Subject for the promoted service token,
                // not a managed-hook token. Service tokens and manifest hook
                // tokens occupy independent bounded spaces and may coincide.
                if (!IsManagedCorrelationEventKind(value.Kind))
                    return;
                ManagedHook expected;
                if (!manifest.ManagedByNativeToken.TryGetValue(value.Subject, out expected))
                    return;
                if (pendingManaged.Count == 0)
                    throw new InvalidOperationException(
                        "A native S1 marker had no managed callback snapshot.");
                PendingManagedOccurrence occurrence = pendingManaged.Peek();
                if (!object.ReferenceEquals(occurrence.Hook, expected)
                    || occurrence.Hook.Pc != value.Pc)
                    throw new InvalidOperationException(
                        "Managed/native S1 marker order or PC differs.");

                bool completeOccurrence = false;
                if (expected.Action == "CLOSE_IF_RETURN_OUTSIDE")
                {
                    byte nativeAction;
                    if (!manifest.NativeActionByToken.TryGetValue(
                        value.Subject,out nativeAction))
                        throw new InvalidOperationException(
                            "Managed conditional marker has no native action identity.");
                    if (value.Kind == 10 && value.Value == 0)
                    {
                        if ((nativeAction != 5 && nativeAction != 9)
                            || occurrence.ClosesService)
                            throw new InvalidOperationException(
                                "Native conditional keep disagreed with the managed return snapshot.");
                        completeOccurrence = true;
                    }
                    else if (value.Kind == 10 && value.Value == 1)
                    {
                        if (nativeAction != 5 || !occurrence.ClosesService
                            || occurrence.ConditionalPopMarkerSeen)
                            throw new InvalidOperationException(
                                "Native conditional POP disagreed with the managed return snapshot.");
                        occurrence.ConditionalPopMarkerSeen = true;
                        occurrence.NativeCorrelationEvents.Add(
                            NativeCorrelationEvent(value, false));
                        return;
                    }
                    else if (value.Kind == 2)
                    {
                        if (!occurrence.ClosesService
                            || (nativeAction == 5
                                ? !occurrence.ConditionalPopMarkerSeen
                                : nativeAction != 9
                                    || occurrence.ConditionalPopMarkerSeen))
                            throw new InvalidOperationException(
                                "Native conditional completion lacked its POP marker.");
                        completeOccurrence = true;
                        managedServices.End(value.ServiceToken);
                    }
                    else return;
                }
                else if(expected.Action=="DEFERRED_SERVICE_CONSUME")
                {
                    byte nativeAction;
                    if(!manifest.NativeActionByToken.TryGetValue(value.Subject,
                            out nativeAction))
                        throw new InvalidOperationException(
                            "Deferred S1 consume has no native action identity.");
                    if(nativeAction==7)
                    {
                        if(value.Kind!=10||value.Value!=3)
                            throw new InvalidOperationException(
                                "Ordinary S1 driverinput observation differs.");
                        if(!MatchesManagedObservationOwner(managedServices,value,
                                occurrence.Stack))
                            throw new InvalidOperationException(
                                "Ordinary S1 driverinput managed ancestor differs.");
                        completeOccurrence=true;
                    }
                    else if(nativeAction!=12
                        ||value.Kind!=1||pendingDeferredBegin==null
                        ||occurrence.Stack!=pendingDeferredBegin.Stack
                        ||occurrence.ReturnPc!=pendingDeferredBegin.ReturnPc
                        ||value.ParentToken!=pendingDeferredBegin.BlockerToken
                        ||value.ServiceKindId!=pendingDeferredBegin.TargetKind
                        ||value.Depth!=pendingDeferredBegin.BlockerDepth+1
                        ||value.SourceCpu!=pendingDeferredBegin.SourceCpu)
                        throw new InvalidOperationException(
                            "Deferred S1 consume identity or nested begin differs.");
                    else
                    {
                        EmitDeferredManaged(value);
                        managedServices.Begin(value.ServiceToken,occurrence.Stack);
                        pendingDeferredBegin=null;
                        completeOccurrence=true;
                    }
                }
                else if (expected.Action == "SERVICE_BEGIN")
                {
                    if(value.Kind==10&&value.Value==4)
                    {
                        byte nativeAction;
                        if(!manifest.NativeActionByToken.TryGetValue(value.Subject,out nativeAction)
                            ||nativeAction!=11)
                            throw new InvalidOperationException(
                                "Deferred S1 marker has no action-11 identity.");
                        if(pendingDeferredBegin==null)
                        {
                            pendingDeferredBegin=new DeferredManagedBegin
                            {BlockerToken=value.ServiceToken,BlockerParentToken=value.ParentToken,
                                BlockerKind=value.ServiceKindId,BlockerDepth=value.Depth,
                                TargetKind=4,HookToken=value.Subject,SourceCpu=value.SourceCpu,
                                Pc=value.Pc,Stack=occurrence.Stack,ReturnPc=occurrence.ReturnPc};
                        }
                        else if(pendingDeferredBegin.BlockerToken!=value.ServiceToken
                            ||pendingDeferredBegin.BlockerParentToken!=value.ParentToken
                            ||pendingDeferredBegin.BlockerKind!=value.ServiceKindId
                            ||pendingDeferredBegin.BlockerDepth!=value.Depth
                            ||pendingDeferredBegin.HookToken!=value.Subject
                            ||pendingDeferredBegin.SourceCpu!=value.SourceCpu
                            ||pendingDeferredBegin.Pc!=value.Pc
                            ||pendingDeferredBegin.Stack!=occurrence.Stack
                            ||pendingDeferredBegin.ReturnPc!=occurrence.ReturnPc)
                            throw new InvalidOperationException(
                                "Deferred S1 callback A7/return identity changed.");
                        occurrence.NativeCorrelationEvents.Add(
                            NativeCorrelationEvent(value,true));
                        if(nextManagedCorrelationOrdinal>=manifest.MaximumRecordsPerFrame
                            ||frameRecords+occurrence.Records.Count
                                >manifest.MaximumRecordsPerFrame)
                            throw new InvalidOperationException(
                                "The bounded S1 managed-correlation stream overflowed.");
                        occurrence.ManagedCorrelationOrdinal=
                            nextManagedCorrelationOrdinal++;
                        frameRecords+=occurrence.Records.Count;
                        pendingDeferredBegin.CorrelatedObservationCount++;
                        pendingDeferredBegin.Observations.Add(occurrence);
                        pendingManaged.Dequeue();
                        return;
                    }
                    if (value.Kind == 10 && value.Value == 2)
                    {
                        if (!managedServices.Matches(
                            value.ServiceToken, occurrence.Stack))
                            throw new InvalidOperationException(
                                "Managed retry changed its native service identity.");
                    }
                    else if (value.Kind == 1)
                    {
                        managedServices.Begin(value.ServiceToken,
                            occurrence.Stack);
                    }
                    else throw new InvalidOperationException(
                        "Managed service begin did not match a native begin.");
                    completeOccurrence = true;
                }
                else if (expected.Action == "SERVICE_CLOSE")
                {
                    if (value.Kind != 2 || !occurrence.ClosesService)
                        throw new InvalidOperationException(
                            "Managed service close did not match a native completion.");
                    completeOccurrence = true;
                    managedServices.End(value.ServiceToken);
                }
                else
                {
                    if (value.Kind != 10 || value.Value != 3)
                        throw new InvalidOperationException(
                            "Managed observation did not match a native observation marker.");
                    completeOccurrence = true;
                }
                if (!completeOccurrence) return;
                occurrence.NativeCorrelationEvents.Add(
                    NativeCorrelationEvent(value, true));
                pendingManaged.Dequeue();
                if (nextManagedCorrelationOrdinal >= manifest.MaximumRecordsPerFrame)
                    throw new InvalidOperationException(
                        "The bounded S1 managed-correlation stream overflowed.");
                long managedCorrelationOrdinal = nextManagedCorrelationOrdinal++;
                var nativeCorrelationEvents = new JArray(
                    occurrence.NativeCorrelationEvents);
                foreach (JObject record in occurrence.Records)
                {
                    record["managed_correlation_ordinal"] =
                        managedCorrelationOrdinal;
                    record["native_correlation_events"] =
                        nativeCorrelationEvents.DeepClone();
                    record["native_ordinal"] = value.Ordinal;
                    record["native_hook_token"] = value.Subject;
                    record["native_service_token"] = value.ServiceToken;
                    record["native_parent_token"] = value.ParentToken;
                    record["native_marker_value"] = value.Kind == 10
                        ? new JValue(value.Value) : JValue.CreateNull();
                    EmitFrame(record);
                }
            }

            private void EmitDeferredManaged(GpgxAudioTraceEvent consumed)
            {
                for(int i=0;i<pendingDeferredBegin.Observations.Count;i++)
                {
                    PendingManagedOccurrence occurrence=
                        pendingDeferredBegin.Observations[i];
                    var chain=new JArray(occurrence.NativeCorrelationEvents);
                    foreach(JObject record in occurrence.Records)
                    {
                        record["managed_correlation_ordinal"]=
                            occurrence.ManagedCorrelationOrdinal;
                        record["native_correlation_events"]=chain.DeepClone();
                        JObject marker=occurrence.NativeCorrelationEvents[0];
                        record["native_ordinal"]=(uint)marker["ordinal"];
                        record["native_hook_token"]=pendingDeferredBegin.HookToken;
                        record["native_service_token"]=pendingDeferredBegin.BlockerToken;
                        record["native_parent_token"]=pendingDeferredBegin.BlockerParentToken;
                        record["native_marker_value"]=4;
                        record["deferred_a7"]=pendingDeferredBegin.Stack;
                        record["deferred_return_pc"]=pendingDeferredBegin.ReturnPc;
                        record["consume_hook_token"]=consumed.Subject;
                        record["consume_pc"]=consumed.Pc;
                        record["consumed_service_token"]=consumed.ServiceToken;
                        record["consume_begin_ordinal"]=consumed.Ordinal;
                        if((int)record["row"]==currentRow)
                        {
                            frameTransaction.Write(record.ToString(Formatting.None));
                            frameTransaction.Write('\n');
                        }
                        else deferredEvidencePublication.Add(
                            (JObject)record.DeepClone());
                    }
                }
            }

            private static bool MatchesManagedObservationOwner(
                ManagedServiceTracker services,GpgxAudioTraceEvent value,
                uint stack)
            {
                if(value.ServiceKindId==4)
                    return value.Depth==0
                        &&services.Matches(value.ServiceToken,stack);
                return (value.ServiceKindId==2||value.ServiceKindId==3)
                    &&value.Depth==1
                    &&services.Matches(value.ParentToken,stack);
            }

            private bool HasManagedServiceCandidate()
            {
                int count = managedServices.Count+(pendingDeferredBegin==null?0:1);
                foreach (PendingManagedOccurrence pending in pendingManaged)
                {
                    if (pending.Hook.Action == "SERVICE_BEGIN") count++;
                    if (pending.ClosesService) count--;
                }
                return count > 0;
            }

            private static JObject NativeCorrelationEvent(
                GpgxAudioTraceEvent value, bool terminal)
            {
                return new JObject
                {
                    ["ordinal"]=value.Ordinal,
                    ["service_token"]=value.ServiceToken,
                    ["parent_token"]=value.ParentToken,
                    ["pc"]=value.Pc,
                    ["hook_token"]=value.Subject,
                    ["event_kind"]=value.Kind,
                    ["service_kind"]=value.ServiceKindId,
                    ["depth"]=value.Depth,
                    ["source_cpu"]=value.SourceCpu,
                    ["value"]=value.Value,
                    ["flags"]=value.Flags,
                    ["terminal"]=terminal
                };
            }

            private static DeferredManagedBegin CloneDeferred(
                DeferredManagedBegin source)
            {
                if(source==null)return null;
                var copy=new DeferredManagedBegin
                {BlockerToken=source.BlockerToken,BlockerParentToken=source.BlockerParentToken,
                    HookToken=source.HookToken,BlockerKind=source.BlockerKind,
                    BlockerDepth=source.BlockerDepth,TargetKind=source.TargetKind,
                    SourceCpu=source.SourceCpu,Pc=source.Pc,Stack=source.Stack,
                    ReturnPc=source.ReturnPc,
                    CorrelatedObservationCount=source.CorrelatedObservationCount};
                for(int i=0;i<source.Observations.Count;i++)
                    copy.Observations.Add(CloneOccurrence(source.Observations[i]));
                return copy;
            }

            private static PendingManagedOccurrence CloneOccurrence(
                PendingManagedOccurrence source)
            {
                var copy=new PendingManagedOccurrence
                {Hook=source.Hook,Stack=source.Stack,ReturnPc=source.ReturnPc,
                    ManagedCorrelationOrdinal=source.ManagedCorrelationOrdinal,
                    ClosesService=source.ClosesService,
                    ConditionalPopMarkerSeen=source.ConditionalPopMarkerSeen};
                for(int i=0;i<source.Records.Count;i++)
                    copy.Records.Add((JObject)source.Records[i].DeepClone());
                for(int i=0;i<source.NativeCorrelationEvents.Count;i++)
                    copy.NativeCorrelationEvents.Add((JObject)
                        source.NativeCorrelationEvents[i].DeepClone());
                return copy;
            }

            private void FlushDeferredPublication()
            {
                using(var reader=new StringReader(deferredPublication.ToString()))
                {
                    string line;
                    while((line=reader.ReadLine())!=null)
                    {
                        JObject value=JObject.Parse(line);
                        if((string)value["type"]=="frame_end")
                        {
                            int row=(int)value["row"];
                            for(int i=0;i<deferredEvidencePublication.Count;i++)
                                if((int)deferredEvidencePublication[i]["row"]==row)
                                {
                                    output.Write(deferredEvidencePublication[i]
                                        .ToString(Formatting.None));
                                    output.Write('\n');
                                }
                        }
                        output.Write(line);output.Write('\n');
                    }
                }
                deferredEvidencePublication.Clear();
            }

            private void ApplyNativeLifecycle(GpgxAudioTraceEvent value)
            {
                if (value.Kind == 8)
                {
                    if (activeResetToken != 0)
                        throw new InvalidOperationException(
                            "Nested native reset lifecycle.");
                    if (resetInputAssertedThisFrame
                        && completedResetGroups >= expectedResetGroups)
                        throw new InvalidOperationException(
                            "Native reset produced more groups than the asserted input.");
                    if (pendingResetEvidence == null
                        && frameServiceOpenBeforeAdvance)
                        throw new InvalidOperationException(
                            "An unexpected native reset lacked the required open-service snapshot.");
                    if (pendingResetEvidence != null)
                    {
                        bool powerInput = (bool)pendingResetEvidence["power"];
                        bool expectedPower = powerInput && completedResetGroups == 0;
                        if (((value.Flags & 1) != 0) != expectedPower)
                            throw new InvalidOperationException(
                                "Native reset power kind disagreed with the asserted input.");
                    }
                    activeResetToken = value.ServiceToken;
                    expectedResetCancellationSeen = false;
                    expectedResetCancellationOrdinal = 0;
                    return;
                }
                if (value.Kind == 2 && (value.Flags & 2) != 0
                    && activeResetToken != 0)
                {
                    if (completedResetGroups > 0
                        && value.ServiceToken == pendingResetCancelledServiceToken)
                        throw new InvalidOperationException(
                            "A later reset group repeated a stale M68K cancellation.");
                    if (completedResetGroups == 0
                        && value.ServiceToken == pendingResetCancelledServiceToken)
                    {
                        if (expectedResetCancellationSeen)
                            throw new InvalidOperationException(
                                "The snapshotted M68K service was cancelled more than once.");
                        expectedResetCancellationSeen = true;
                        expectedResetCancellationOrdinal = value.Ordinal;
                    }
                    return;
                }
                if (value.Kind != 9) return;
                if (activeResetToken == 0 || value.ServiceToken != activeResetToken)
                    throw new InvalidOperationException(
                        "Native reset end did not match its begin.");
                if (pendingResetEvidence != null)
                {
                    if (completedResetGroups >= expectedResetGroups)
                        throw new InvalidOperationException(
                            "Native reset produced more groups than the asserted input.");
                    if (completedResetGroups == 0
                        && pendingResetCancelledServiceToken != 0
                        && !expectedResetCancellationSeen)
                        throw new InvalidOperationException(
                            "The first native reset group did not cancel the snapshotted M68K service.");
                    JObject evidence = (JObject)pendingResetEvidence.DeepClone();
                    evidence["group_index"] = completedResetGroups;
                    evidence["native_end_ordinal"] = value.Ordinal;
                    evidence["native_service_token"] = value.ServiceToken;
                    evidence["native_power"] = (value.Flags & 1) != 0;
                    EmitFrame(evidence);
                    if (completedResetGroups == 0
                        && pendingResetServiceSnapshot != null)
                    {
                        pendingResetServiceSnapshot["native_reset_end_ordinal"] =
                            value.Ordinal;
                        pendingResetServiceSnapshot["native_reset_token"] =
                            value.ServiceToken;
                        pendingResetServiceSnapshot["native_cancellation_ordinal"] =
                            expectedResetCancellationOrdinal;
                        EmitFrame(pendingResetServiceSnapshot);
                    }
                    completedResetGroups++;
                    if (completedResetGroups == expectedResetGroups)
                    {
                        pendingResetEvidence = null;
                        pendingResetServiceSnapshot = null;
                        pendingResetCancelledServiceToken = 0;
                        managedServices.Clear();
                    }
                }
                activeResetToken = 0;
            }

            private void CaptureRequest(int queue)
            {
                if (queue < 0 || queue >= pendingRequestIds.Length)
                    throw new InvalidOperationException("Invalid S1 sound queue callback.");
                if (pendingRequestIds[queue].HasValue)
                {
                    WriteDecision(pendingRequestIds[queue], queue,
                        pendingRequestSounds[queue], "overwritten");
                }
                long requestId = nextRequestId++;
                byte sound = (byte)ReadM68kRegister("D0");
                pendingRequestIds[queue] = requestId;
                pendingRequestSounds[queue] = sound;
                WriteFrame(new JObject
                {
                    ["type"]="request", ["row"]=currentRow,
                    ["request_id"]=requestId, ["queue"]=queue,
                    ["sound_id"]=sound, ["origin"]="callback"
                });
            }

            private void BeginCycleIteration()
            {
                int queue = 2 - (int)(ReadM68kRegister("D4") & 0xFFFF);
                if (queue < 0 || queue > 2 || cycleQueue != -1)
                    throw new InvalidOperationException(
                        "Invalid or overlapping S1 queue iteration.");
                cycleQueue = queue;
                cycleSound = host.ReadMainRamByte(0xF00A + queue);
                cyclePreexistingSound = host.ReadMainRamByte(0xF009);
                cycleRequestId = pendingRequestIds[queue].HasValue
                    && pendingRequestSounds[queue] == cycleSound
                        ? pendingRequestIds[queue] : null;
            }

            private void EndCycleIteration()
            {
                if (cycleQueue < 0)
                    throw new InvalidOperationException(
                        "S1 queue iteration ended without a begin.");
                string outcome;
                if (cycleSound <= 0x80)
                {
                    outcome = "ignored";
                }
                else if (cyclePreexistingSound != 0x80)
                {
                    outcome = "deferred";
                }
                else if (host.ReadMainRamByte(0xF009) == cycleSound)
                {
                    outcome = "accepted";
                    selectedRequestId = cycleRequestId;
                }
                else
                {
                    outcome = "priority_rejected";
                }
                WriteDecision(cycleRequestId, cycleQueue, cycleSound, outcome);
                if (outcome == "deferred")
                {
                    pendingRequestIds[0] = cycleRequestId;
                    pendingRequestSounds[0] = cycleSound;
                    if (cycleQueue != 0)
                        pendingRequestIds[cycleQueue] = null;
                }
                else
                {
                    pendingRequestIds[cycleQueue] = null;
                }
                cycleQueue = -1;
                cycleRequestId = null;
            }

            private void WriteDecision(
                long? requestId, int queue, byte sound, string outcome)
            {
                WriteFrame(new JObject
                {
                    ["type"]="decision", ["row"]=currentRow,
                    ["request_id"]=requestId.HasValue
                        ? new JValue(requestId.Value) : JValue.CreateNull(),
                    ["queue"]=queue, ["sound_id"]=sound,
                    ["outcome"]=outcome
                });
            }

            private void CaptureServiceState(
                string reason, uint? returnPc, bool cancelled)
            {
                var snapshot = new JObject
                {
                    ["type"]="managed_service_snapshot", ["row"]=currentRow,
                    ["state_start"]=manifest.DriverStateStart,
                    ["state_hex"]=CaptureDriverState(),
                    ["close_reason"]=reason,
                    ["cancelled"]=cancelled
                };
                if (returnPc.HasValue) snapshot["return_pc"] = returnPc.Value;
                WriteFrame(snapshot);
            }

            private uint ReadReturnPc()
            {
                int offset = (int)(ReadM68kRegister("A7") & 0xFFFF);
                return ((uint)host.ReadMainRamByte(offset) << 24)
                    | ((uint)host.ReadMainRamByte((offset + 1) & 0xFFFF) << 16)
                    | ((uint)host.ReadMainRamByte((offset + 2) & 0xFFFF) << 8)
                    | host.ReadMainRamByte((offset + 3) & 0xFFFF);
            }

            private uint ReadM68kRegister(string name)
            {
                return registers.ReadCpuRegister("M68K " + name);
            }

            private string CaptureDriverState()
            {
                char[] hex = new char[
                    (manifest.DriverStateExclusiveEnd
                        - manifest.DriverStateStart) * 2];
                const string alphabet = "0123456789abcdef";
                int at = 0;
                for (int i = manifest.DriverStateStart;
                    i < manifest.DriverStateExclusiveEnd; i++)
                {
                    byte value = host.ReadMainRamByte(i);
                    hex[at++] = alphabet[value >> 4];
                    hex[at++] = alphabet[value & 15];
                }
                return new string(hex);
            }

            private void WriteNative(GpgxAudioTraceEvent value)
            {
                WriteFrame(new JObject
                {
                    ["type"]="native_event", ["row"]=currentRow,
                    ["ordinal"]=value.Ordinal,
                    ["service_token"]=value.ServiceToken,
                    ["parent_token"]=value.ParentToken,
                    ["pc"]=value.Pc, ["subject"]=value.Subject,
                    ["offset"]=value.Offset, ["kind"]=value.Kind,
                    ["service_kind"]=value.ServiceKindId,
                    ["depth"]=value.Depth, ["source_cpu"]=value.SourceCpu,
                    ["payload_length"]=value.PayloadLength,
                    ["value"]=value.Value, ["flags"]=value.Flags,
                    ["payload"]=value.Payload
                });
            }

            private void WriteFrame(JObject record)
            {
                if (collectingManaged != null)
                {
                    collectingManaged.Records.Add(record);
                    return;
                }
                EmitFrame(record);
            }

            private void EmitFrame(JObject record)
            {
                if (!publishing) return;
                if (frameRecords >= manifest.MaximumRecordsPerFrame)
                    throw new InvalidOperationException(
                        "The bounded S1 raw audio frame overflowed.");
                frameRecords++;
                Write(record);
            }

            private void Write(JObject record)
            {
                TextWriter target=frameTransaction==null?output:frameTransaction;
                target.Write(record.ToString(Formatting.None));
                target.Write('\n');
            }

            public void Dispose()
            {
                if (disposed) return;
                for (int i = callbacks.Count - 1; i >= 0; i--)
                    callbacks[i].Dispose();
                callbacks.Clear();
                api.Disable();
                disposed = true;
            }
        }

        private static void RequireManagedBoundary(
            Dictionary<uint, ManagedHook> hooks, uint pc, string action)
        {
            ManagedHook hook;
            if (!hooks.TryGetValue(pc, out hook) || hook.Action != action)
                throw Invalid("missing reviewed M68K boundary at 0x"
                    + pc.ToString("X", CultureInfo.InvariantCulture));
        }

        private static void RequireNativeBoundary(
            Dictionary<uint, List<NativeHook>> hooks, uint pc,
            string action, string opcode)
        {
            List<NativeHook> matches;
            if (!hooks.TryGetValue(pc, out matches))
                throw Invalid("missing reviewed native boundary at 0x"
                    + pc.ToString("X", CultureInfo.InvariantCulture));
            foreach (NativeHook hook in matches)
                if (hook.Action == action && hook.OpcodeHex == opcode) return;
            throw Invalid("missing reviewed native boundary at 0x"
                + pc.ToString("X", CultureInfo.InvariantCulture));
        }

        private static void AddPublicHook(
            Dictionary<uint, List<NativeHook>> hooks, NativeHook hook)
        {
            List<NativeHook> matches;
            if (!hooks.TryGetValue(hook.Pc, out matches))
            {
                matches = new List<NativeHook>();
                hooks.Add(hook.Pc, matches);
            }
            matches.Add(hook);
        }

        private static byte[] ExactKindList(
            JObject owner, string name, params byte[] expected)
        {
            JArray values = RequiredArray(owner, name);
            if (values.Count != expected.Length)
                throw Invalid(name + " differs from the reviewed kind set");
            var result = new byte[values.Count];
            for (int i = 0; i < result.Length; i++)
            {
                result[i] = StrictByte(values[i], name + " kind");
                if (result[i] != expected[i])
                    throw Invalid(name + " differs from the reviewed kind set");
            }
            return result;
        }

        private static void AddManagedNativeHook(
            List<GpgxAudioObserverAdapter.ServiceHook> hooks,
            Dictionary<uint, List<NativeHook>> publicHooks,
            Dictionary<ushort, ManagedHook> managedByToken,
            HashSet<ushort> hookTokens, ManagedHook managed,
            ref ushort nextToken, byte action, byte serviceKind,
            byte expectedKind, ushort rangeFirst, ushort rangeCount,
            ulong reserved)
        {
            ushort token = nextToken++;
            if (token == 0 || !hookTokens.Add(token))
                throw Invalid("M68K native hook token overflow or duplicate");
            hooks.Add(new GpgxAudioObserverAdapter.ServiceHook
            {
                HookToken=token, Action=action, Cpu=2, Pc=managed.Pc,
                ServiceKindId=serviceKind, ExpectedActiveKind=expectedKind,
                OpcodeLength=(byte)managed.Opcode.Length,
                Opcode=Pack(managed.Opcode), RangeFirst=rangeFirst,
                RangeCount=rangeCount, Reserved=reserved
            });
            var native = new NativeHook
            {
                Token=token, Cpu=2, Pc=managed.Pc,
                ExpectedKind=expectedKind, Opcode=managed.Opcode,
                Source=managed.Source, SourceLabel=managed.SourceLabel,
                Action=action==1?"PUSH_BEGIN":action==2?"POP_END_AT_PC":
                    action==5?"POP_END_IF_RETURN_OUTSIDE":
                    action==6?"RETRY_MARKER":
                    action==8?"POP_DIRECT_PARENT_PROMOTE_TOP":
                    action==9?"POP_DIRECT_PARENT_PROMOTE_TOP_IF_RETURN_OUTSIDE":
                    action==10?"DIRECT_PARENT_RETRY_MARKER":
                    action==11?"RESERVE_DEFERRED_BEGIN":
                    action==12?"CONSUME_DEFERRED_BEGIN":
                    "OBSERVATION_MARKER"
            };
            AddPublicHook(publicHooks, native);
            managedByToken.Add(token, managed);
        }

        private static bool KnownManagedAction(string action)
        {
            switch (action)
            {
                case "REQUEST_QUEUE_0": case "REQUEST_QUEUE_1":
                case "REQUEST_QUEUE_2": case "SERVICE_BEGIN":
                case "DEFERRED_SERVICE_CONSUME":
                case "SERVICE_CLOSE": case "CLOSE_IF_RETURN_OUTSIDE":
                case "QUEUE_TRIGGER": case "QUEUE_CYCLE":
                case "CYCLE_ITERATION_BEGIN": case "CYCLE_ITERATION_END":
                case "SELECTED_DISPATCH": case "ACTUAL_DISPATCH":
                case "BGM_CANDIDATE":
                case "BGM_ACCEPTED": case "LOAD_FM_DAC": case "LOAD_PSG":
                case "NORMAL_CANDIDATE": case "NORMAL_REWRITTEN":
                case "NORMAL_ROLE": case "NORMAL_INIT":
                case "NORMAL_BLOCK_ONEUP": case "NORMAL_BLOCK_FADEOUT_TEST":
                case "NORMAL_BLOCK_FADEOUT": case "NORMAL_BLOCK_FADEIN_TEST":
                case "NORMAL_BLOCK_FADEIN": case "NORMAL_BLOCK_EXIT":
                case "SPECIAL_CANDIDATE": case "SPECIAL_ROLE":
                case "SPECIAL_INIT": case "SPECIAL_OVERRIDE_TEST":
                case "SPECIAL_OVERRIDE_APPLY": case "ONEUP_CLEAR_MUSIC":
                case "SPECIAL_BLOCK_ONEUP": case "SPECIAL_BLOCK_FADEOUT_TEST":
                case "SPECIAL_BLOCK_FADEOUT": case "SPECIAL_BLOCK_FADEIN_TEST":
                case "SPECIAL_BLOCK_FADEIN": case "SPECIAL_BLOCK_EXIT":
                case "ONEUP_KILL_NORMAL": case "ONEUP_SAVE_COPY":
                case "ONEUP_SET": case "STOP_ALL_BEGIN":
                case "STOP_ALL_CLEARED": case "FADE_DELAY":
                case "FADE_COUNTER": case "FADE_STEP":
                case "FADE_CLEAR_DAC_OVERRIDE": case "FADE_CLEAR":
                case "FADE_RETURN": case "RESTORE_BEGIN":
                case "RESTORE_COPY": case "RESTORE_DAC_OVERRIDE":
                case "RESTORE_FM": case "RESTORE_PSG":
                case "RESTORE_PSG_NOTE_OFF": case "RESTORE_SET_FADE":
                case "RESTORE_SET_COUNTER": case "RESTORE_CLEAR_ONEUP":
                case "DAC_COMMAND": case "FM_DAC_MODE_TEST":
                case "DAC_DISABLE": case "FM6_SILENCE":
                case "YM_WRITE": case "PSG_WRITE": return true;
                default: return false;
            }
        }

        private static byte NativeAction(string value)
        {
            if (value == "PUSH_BEGIN") return 1;
            if (value == "POP_END_AT_PC") return 2;
            if (value == "TAIL_POP_PUSH") return 4;
            if (value == "POP_DIRECT_PARENT_PROMOTE_TOP") return 8;
            if (value == "POP_DIRECT_PARENT_PROMOTE_TOP_IF_RETURN_OUTSIDE") return 9;
            throw Invalid("unknown native action " + value);
        }

        private static byte KindFlags(JArray values)
        {
            byte flags = 0;
            foreach (JToken token in values)
            {
                string value = token.Type == JTokenType.String
                    ? (string)token : null;
                if (value == "TYPED_ASYNC") flags |= 1;
                else if (value == "ALLOW_CONTINUATION") flags |= 2;
                else if (value == "ALLOW_CHILDREN") flags |= 4;
                else throw Invalid("unknown native kind flag");
            }
            return flags;
        }

        private static void Slice(JArray ids,
            Dictionary<ushort, ushort> indices,
            out ushort first, out ushort count)
        {
            if (ids.Count == 0)
            {
                first = 0;
                count = 0;
                return;
            }
            ushort id = StrictUShort(ids[0], "native range slice id");
            if (!indices.TryGetValue(id, out first))
                throw Invalid("unknown native range slice id");
            count = (ushort)ids.Count;
            for (int i = 1; i < ids.Count; i++)
            {
                ushort next;
                if (!indices.TryGetValue(
                    StrictUShort(ids[i], "native range slice id"), out next)
                    || next != first + i)
                    throw Invalid("noncontiguous native range slice");
            }
        }

        private static uint RangeLength(
            GpgxAudioObserverAdapter.SnapshotRange[] ranges,
            ushort first, ushort count)
        {
            uint length = 0;
            for (int i = 0; i < count; i++)
                length = checked(length + ranges[first + i].Length);
            return length;
        }

        private static byte[] Hex(string value)
        {
            if (string.IsNullOrEmpty(value) || (value.Length & 1) != 0
                || value.Length > 16 || value != value.ToLowerInvariant())
                throw Invalid("invalid lowercase opcode literal");
            var result = new byte[value.Length / 2];
            try
            {
                for (int i = 0; i < result.Length; i++)
                    result[i] = Convert.ToByte(value.Substring(i * 2, 2), 16);
            }
            catch (FormatException exception)
            {
                throw new InvalidDataException("Invalid opcode literal.", exception);
            }
            return result;
        }

        private static ulong Pack(byte[] value)
        {
            ulong packed = 0;
            for (int i = 0; i < value.Length; i++)
                packed |= (ulong)value[i] << (i * 8);
            return packed;
        }

        private static string ToHex(byte[] value)
        {
            char[] result = new char[value.Length * 2];
            const string alphabet = "0123456789abcdef";
            for (int i = 0; i < value.Length; i++)
            {
                result[i*2] = alphabet[value[i] >> 4];
                result[i*2+1] = alphabet[value[i] & 15];
            }
            return new string(result);
        }

        private static void ExactProperties(
            JObject value, string where, params string[] names)
        {
            var expected = new HashSet<string>(names, StringComparer.Ordinal);
            int count = 0;
            foreach (JProperty property in value.Properties())
            {
                count++;
                if (!expected.Contains(property.Name))
                    throw Invalid("unexpected property " + where + "." + property.Name);
            }
            if (count != expected.Count)
                throw Invalid("missing property in " + where);
        }

        private static JObject RequiredObject(JObject value, string name)
        {
            JObject result = value[name] as JObject;
            if (result == null) throw Invalid(name + " is not an object");
            return result;
        }

        private static JArray RequiredArray(JObject value, string name)
        {
            JArray result = value[name] as JArray;
            if (result == null) throw Invalid(name + " is not an array");
            return result;
        }

        private static string RequiredString(JObject value, string name)
        {
            JToken token = value[name];
            if (token == null || token.Type != JTokenType.String
                || string.IsNullOrEmpty((string)token))
                throw Invalid(name + " is not a nonempty string");
            return (string)token;
        }

        private static void RequireString(
            JObject value, string name, string expected)
        {
            if (RequiredString(value, name) != expected)
                throw Invalid(name + " has an unsupported value");
        }

        private static int RequiredInt(JObject value, string name)
        {
            JToken token = value[name];
            if (token == null || token.Type != JTokenType.Integer)
                throw Invalid(name + " is not an integer");
            long number = (long)token;
            if (number < int.MinValue || number > int.MaxValue)
                throw Invalid(name + " is out of range");
            return (int)number;
        }

        private static uint StrictUInt(JToken token, string where)
        {
            if (token == null || token.Type != JTokenType.Integer)
                throw Invalid(where + " is not an integer");
            long value = (long)token;
            if (value < 0 || value > uint.MaxValue)
                throw Invalid(where + " is out of range");
            return (uint)value;
        }

        private static ushort StrictUShort(JToken token, string where)
        {
            uint value = StrictUInt(token, where);
            if (value > ushort.MaxValue) throw Invalid(where + " is out of range");
            return (ushort)value;
        }

        private static byte StrictByte(JToken token, string where)
        {
            uint value = StrictUInt(token, where);
            if (value > byte.MaxValue) throw Invalid(where + " is out of range");
            return (byte)value;
        }

        private static InvalidDataException Invalid(string message)
        {
            return new InvalidDataException("Invalid S1 audio manifest: " + message + ".");
        }
    }
}
