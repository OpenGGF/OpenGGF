using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using BizHawk.Emulation.Cores.Consoles.Sega.gpgx;
using Newtonsoft.Json.Linq;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class GpgxZ80AudioCapabilityTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "GpgxZ80AudioCapabilityTests lock reviewed S2 and S3K service manifests",
                LocksReviewedServiceManifests));
            tests.Add(new TestMain.TestCase(
                "GpgxZ80AudioCapabilityTests project ordered YM DAC data writes",
                ProjectsOrderedYmDacDataWrites));
            if (Environment.GetEnvironmentVariable("OPENGGF_GPGX_Z80_CAPABILITY") != "1")
                return;
            string s2Rom = Environment.GetEnvironmentVariable("S2_ROM_PATH");
            string movie = Environment.GetEnvironmentVariable("S2_BK2_PATH");
            if (File.Exists(s2Rom) && File.Exists(movie))
            {
                tests.Add(new TestMain.TestCase(
                    "GpgxZ80AudioCapabilityTests observe real S2 Z80 services and chip writes",
                    ObserveS2Bootstrap,
                    game: "s2", serial: true, estimatedSeconds: 20.0));
                tests.Add(new TestMain.TestCase(
                    "GpgxZ80AudioCapabilityTests preserve S2 disabled and enabled emulation identity",
                    () => PreserveEnabledIdentity("s2", s2Rom, movie, CreateS2Observer),
                    game: "s2", serial: true, estimatedSeconds: 20.0));
                tests.Add(new TestMain.TestCase(
                    "GpgxZ80AudioCapabilityTests preserve S2 Reset and Power reupload arming",
                    () => ProveResetAndPowerRearm("s2", s2Rom, movie, CreateS2Observer, 10),
                    game: "s2", serial: true, estimatedSeconds: 10.0));
                tests.Add(new TestMain.TestCase(
                    "GpgxZ80AudioCapabilityTests restore S2 same-epoch observer checkpoints",
                    () => ProveSaveLoad("s2", s2Rom, movie, CreateS2Observer),
                    game: "s2", serial: true, estimatedSeconds: 10.0));
            }
            string s3kRom = Environment.GetEnvironmentVariable("S3K_ROM_PATH");
            string s3kMovie = Environment.GetEnvironmentVariable("S3K_BK2_PATH");
            if (File.Exists(s3kRom) && File.Exists(s3kMovie))
            {
                tests.Add(new TestMain.TestCase(
                    "GpgxZ80AudioCapabilityTests observe real S3K Z80 services and chip writes",
                    ObserveS3kBootstrap,
                    game: "s3k", serial: true, estimatedSeconds: 20.0));
                tests.Add(new TestMain.TestCase(
                    "GpgxZ80AudioCapabilityTests preserve S3K disabled and enabled emulation identity",
                    () => PreserveEnabledIdentity("s3k", s3kRom, s3kMovie, CreateS3kObserver),
                    game: "s3k", serial: true, estimatedSeconds: 20.0));
                tests.Add(new TestMain.TestCase(
                    "GpgxZ80AudioCapabilityTests preserve S3K Reset and Power reupload arming",
                    () => ProveResetAndPowerRearm("s3k", s3kRom, s3kMovie, CreateS3kObserver, 8),
                    game: "s3k", serial: true, estimatedSeconds: 10.0));
                tests.Add(new TestMain.TestCase(
                    "GpgxZ80AudioCapabilityTests restore S3K same-epoch observer checkpoints",
                    () => ProveSaveLoad("s3k", s3kRom, s3kMovie, CreateS3kObserver),
                    game: "s3k", serial: true, estimatedSeconds: 10.0));
            }
            if (Environment.GetEnvironmentVariable("OPENGGF_GPGX_Z80_PERFORMANCE") == "1"
                && File.Exists(s2Rom) && File.Exists(movie))
                tests.Add(new TestMain.TestCase(
                    "GpgxZ80AudioCapabilityTests keep real S2 observer overhead bounded",
                    () => MeasurePerformance("S2", s2Rom, movie, CreateS2Observer),
                    game: "s2", serial: true, estimatedSeconds: 30.0));
            if (Environment.GetEnvironmentVariable("OPENGGF_GPGX_Z80_PERFORMANCE") == "1"
                && File.Exists(s3kRom) && File.Exists(s3kMovie))
                tests.Add(new TestMain.TestCase(
                    "GpgxZ80AudioCapabilityTests keep real S3K observer overhead bounded",
                    () => MeasurePerformance("S3K", s3kRom, s3kMovie, CreateS3kObserver),
                    game: "s3k", serial: true, estimatedSeconds: 30.0));
            if (Environment.GetEnvironmentVariable("OPENGGF_GPGX_Z80_FULL_RUN") == "1")
            {
                if (File.Exists(s2Rom) && File.Exists(movie)) tests.Add(new TestMain.TestCase(
                    "GpgxZ80AudioCapabilityTests replay complete S2 movie twice",
                    () => ProveFullRun("S2", s2Rom, movie, CreateS2Observer),
                    game: "s2", serial: true, estimatedSeconds: 600.0));
                if (File.Exists(s3kRom) && File.Exists(s3kMovie)) tests.Add(new TestMain.TestCase(
                    "GpgxZ80AudioCapabilityTests replay complete S3K movie twice",
                    () => ProveFullRun("S3K", s3kRom, s3kMovie, CreateS3kObserver),
                    game: "s3k", serial: true, estimatedSeconds: 900.0));
            }
        }

        private static void ProjectsOrderedYmDacDataWrites()
        {
            var projection = new YmLatchProjection();
            AssertEx.Equal(false, projection.Accept(new GpgxAudioTraceEvent
                { Kind = 3, Subject = 0, Value = 0x2A }));
            AssertEx.Equal(true, projection.Accept(new GpgxAudioTraceEvent
                { Kind = 3, Subject = 1, Value = 0x7F }));
            AssertEx.Equal(true, projection.Accept(new GpgxAudioTraceEvent
                { Kind = 3, Subject = 1, Value = 0x80 }));
            AssertEx.Equal(false, projection.Accept(new GpgxAudioTraceEvent
                { Kind = 3, Subject = 2, Value = 0x2A }));
            AssertEx.Equal(false, projection.Accept(new GpgxAudioTraceEvent
                { Kind = 3, Subject = 3, Value = 0x55 }));
            AssertEx.Equal(false, projection.Accept(new GpgxAudioTraceEvent { Kind = 8 }));
            AssertEx.Equal(false, projection.Accept(new GpgxAudioTraceEvent
                { Kind = 3, Subject = 1, Value = 0x81 }));
            AssertEx.Equal(false, projection.Accept(new GpgxAudioTraceEvent
                { Kind = 3, Subject = 0, Value = 0x22 }));
            AssertEx.Equal(false, projection.Accept(new GpgxAudioTraceEvent
                { Kind = 3, Subject = 1, Value = 0x2A }));
        }

        private sealed class YmLatchProjection
        {
            private byte port0Address;
            private byte port1Address;

            internal bool Accept(GpgxAudioTraceEvent value)
            {
                if (value.Kind == 8)
                {
                    port0Address = 0;
                    port1Address = 0;
                    return false;
                }
                if (value.Kind != 3) return false;
                if (value.Subject == 0) { port0Address = value.Value; return false; }
                if (value.Subject == 2) { port1Address = value.Value; return false; }
                if (value.Subject == 1) return port0Address == 0x2A;
                if (value.Subject == 3) { byte ignored = port1Address; return false; }
                return false;
            }
        }

        private sealed class CapabilityBreakdown
        {
            private readonly ushort armHookToken;
            private bool armed;
            private readonly SortedDictionary<int, long> eventKinds = new SortedDictionary<int, long>();
            private readonly SortedDictionary<int, long> beginHooks = new SortedDictionary<int, long>();
            private readonly SortedDictionary<int, long> endHooks = new SortedDictionary<int, long>();
            private readonly SortedDictionary<int, long> beginKinds = new SortedDictionary<int, long>();
            private readonly SortedDictionary<int, long> endKinds = new SortedDictionary<int, long>();
            private readonly SortedDictionary<int, long> beginDepths = new SortedDictionary<int, long>();
            private readonly SortedDictionary<int, long> sourceCpus = new SortedDictionary<int, long>();
            private readonly SortedDictionary<int, long> fmSubjects = new SortedDictionary<int, long>();
            private readonly SortedDictionary<int, long[]> snapshotRanges = new SortedDictionary<int, long[]>();
            private long armCompletions;
            private long preArmZ80Begins;

            internal CapabilityBreakdown(ushort armHookToken) { this.armHookToken = armHookToken; }

            internal void Accept(GpgxAudioTraceEvent value)
            {
                Increment(eventKinds, value.Kind);
                Increment(sourceCpus, value.SourceCpu);
                if (value.Kind == 1)
                {
                    Increment(beginHooks, value.Subject); Increment(beginKinds, value.ServiceKindId);
                    Increment(beginDepths, value.Depth);
                    if (!armed && value.SourceCpu == 1) preArmZ80Begins++;
                }
                else if (value.Kind == 2)
                {
                    Increment(endHooks, value.Subject); Increment(endKinds, value.ServiceKindId);
                    if (value.Subject == armHookToken) { armed = true; armCompletions++; }
                }
                else if (value.Kind == 3) Increment(fmSubjects, value.Subject);
                else if (value.Kind == 5) Snapshot(value.Subject)[0]++;
                else if (value.Kind == 6)
                {
                    long[] range = Snapshot(value.Subject); range[1]++; range[2] += value.PayloadLength;
                }
                else if (value.Kind == 7) Snapshot(value.Subject)[3]++;
                else if (value.Kind == 8) armed = false;
            }

            internal JObject ToJson()
            {
                var root = new JObject();
                root["event_kind"] = Map(eventKinds); root["begin_hook"] = Map(beginHooks);
                root["end_hook"] = Map(endHooks); root["begin_kind"] = Map(beginKinds);
                root["end_kind"] = Map(endKinds); root["begin_depth"] = Map(beginDepths);
                root["source_cpu"] = Map(sourceCpus); root["fm_subject"] = Map(fmSubjects);
                var snapshots = new JObject();
                foreach (KeyValuePair<int, long[]> pair in snapshotRanges)
                    snapshots[pair.Key.ToString()] = new JArray(pair.Value[0], pair.Value[1], pair.Value[2], pair.Value[3]);
                root["snapshot_range_begin_chunk_bytes_end"] = snapshots;
                root["arm_completion"] = armCompletions;
                root["pre_arm_z80_service_begin"] = preArmZ80Begins;
                return root;
            }

            private long[] Snapshot(int id)
            {
                long[] value;
                if (!snapshotRanges.TryGetValue(id, out value))
                { value = new long[4]; snapshotRanges.Add(id, value); }
                return value;
            }

            private static void Increment(SortedDictionary<int, long> values, int key)
            {
                long value; values.TryGetValue(key, out value); values[key] = value + 1;
            }

            private static JObject Map(SortedDictionary<int, long> values)
            {
                var result = new JObject();
                foreach (KeyValuePair<int, long> pair in values) result[pair.Key.ToString()] = pair.Value;
                return result;
            }
        }

        private static void LocksReviewedServiceManifests()
        {
            string path = Path.Combine(EndToEndTests.ToolDirectory,
                "fixtures/gpgx-audio-service-manifests-v1.json");
            AssertEx.Equal(true, File.Exists(path));
            JObject root = JObject.Parse(File.ReadAllText(path));
            AssertEx.Equal("openggf.gpgx-audio-service-manifests.v1", (string)root["schema"]);
            JObject s2 = (JObject)root["games"]["s2"];
            AssertManifest(s2, 23, 0xEC000u, 0xEC036u);
            JToken fadeRestoreExit = ((JArray)s2["hooks"]).FirstOrDefault(value => (uint)value["pc"] == 0xDB4u);
            AssertEx.Equal(true, fadeRestoreExit != null);
            AssertEx.Equal("POP_END_AT_PC", (string)fadeRestoreExit["action"]);
            AssertEx.Equal(9, (int)fadeRestoreExit["expected_kind"]);
            AssertEx.Equal("c3cc00", (string)fadeRestoreExit["opcode"]);
            JObject s3k = (JObject)root["games"]["s3k"];
            AssertManifest(s3k, 26, 0x12CEu, 0x1346u);
            JToken dpcmPairExit = ((JArray)s3k["hooks"])
                .FirstOrDefault(value => (int)value["token"] == 12);
            AssertEx.Equal(0x1105u, (uint)dpcmPairExit["pc"]);
            AssertEx.Equal("POP_END_AT_PC", (string)dpcmPairExit["action"]);
            AssertEx.Equal(7, (int)dpcmPairExit["expected_kind"]);
            AssertEx.Equal("f29210", (string)dpcmPairExit["opcode"]);
            AssertEx.Equal(false, ((JArray)s3k["hooks"]).Any(value =>
                (uint)value["pc"] == 0x1092u || (uint)value["pc"] == 0x110Cu));
            JObject capability = JObject.Parse(File.ReadAllText(Path.Combine(
                EndToEndTests.ToolDirectory, "fixtures/gpgx-audio-capability-v1.json")));
            AssertEx.Equal("openggf.gpgx-audio-capability.v1", (string)capability["schema"]);
            AssertEx.Equal(Sha256File(path), (string)capability["service_manifest_sha256"]);
            AssertEx.Equal(Sha256File(typeof(GpgxHost).Assembly.Location),
                (string)capability["task8_harness_executable_sha256"]);
            AssertEx.Equal(Sha256File(Path.Combine(EndToEndTests.ToolDirectory,
                "src/Audio/CompleteRunAudioObserver.cs")),(string)capability["collector_source_sha256"]);
            AssertEx.Equal(Sha256File(Path.Combine(EndToEndTests.ToolDirectory,
                "src/Core/GpgxHost.cs")),(string)capability["host_source_sha256"]);
            AssertEx.Equal(WatchMaskSha256(s2),
                (string)capability["runs"]["s2"]["watch_mask_sha256"]);
            AssertEx.Equal(WatchMaskSha256(s3k),
                (string)capability["runs"]["s3k"]["watch_mask_sha256"]);
            AssertMovieIdentity((JObject)capability["runs"]["s2"],
                "sonic-2-sonic-tails-complete-emeralds.bk2",
                "e850798f882b8c580aad148bc97cb50f260cae1d336dd649fe2f4dfae6796aa5", 259590);
            AssertMovieIdentity((JObject)capability["runs"]["s3k"],
                "s3k-knuckles-complete-superemeralds.bk2",
                "aa892856df22b7bb1fe5accb48db10b90dc26845d1dccee90352da30349f53cc", 434417);
            foreach (string game in new[] { "s2", "s3k" })
            {
                JObject run = (JObject)capability["runs"][game];
                AssertEx.Equal(true, run["state_digest_sha256"] != null);
                AssertEx.Equal(true, run["complete"] != null);
                AssertEx.Equal(true, run["lifecycle"]?["reset"] != null);
                AssertEx.Equal(true, run["lifecycle"]?["power"] != null);
            }
        }

        private static void AssertMovieIdentity(JObject run, string name, string sha256, int rows)
        {
            JObject movie = (JObject)run["movie"];
            AssertEx.Equal(name, (string)movie["name"]);
            AssertEx.Equal(sha256, (string)movie["sha256"]);
            AssertEx.Equal(rows, (int)movie["rows"]);
        }

        private static string WatchMaskSha256(JObject game)
        {
            var mask = new byte[8192];
            foreach (JToken value in (JArray)game["z80_watch_pc_union"])
            {
                int pc = (int)value;
                mask[pc >> 3] |= (byte)(1 << (pc & 7));
            }
            return Sha256Bytes(mask);
        }

        private static Bk2Movie OpenReviewedMovie(string game, string path)
        {
            JObject root = JObject.Parse(File.ReadAllText(Path.Combine(
                EndToEndTests.ToolDirectory, "fixtures/gpgx-audio-capability-v1.json")));
            JObject expected = (JObject)root["runs"][game]["movie"];
            AssertEx.Equal((string)expected["name"], Path.GetFileName(path));
            AssertEx.Equal((string)expected["sha256"], Sha256File(path));
            Bk2Movie movie = Bk2Reader.Read(path);
            AssertEx.Equal((int)expected["rows"], movie.FrameCount);
            return movie;
        }

        private static void AssertManifest(JObject game, int hookCount,
            uint uploadEntry, uint uploadCompletion)
        {
            AssertEx.Equal(8192, (int)game["watch_mask_bytes"]);
            AssertEx.Equal(hookCount, ((JArray)game["hooks"]).Count);
            AssertEx.Equal(uploadEntry, (uint)game["upload"]["entry_pc"]);
            AssertEx.Equal(uploadCompletion, (uint)game["upload"]["completion_pc"]);
            AssertEx.Equal(0, (int)game["upload"]["snapshot_start"]);
            AssertEx.Equal(8192, (int)game["upload"]["snapshot_exclusive_end"]);
            AssertEx.Equal("ARM_Z80_PROOFS_ON_COMPLETION",
                (string)game["upload"]["completion_policy"]);
            var union = new HashSet<uint>();
            foreach (JObject hook in (JArray)game["hooks"])
            {
                AssertEx.Equal(true, !string.IsNullOrEmpty((string)hook["source_file"]));
                AssertEx.Equal(true, !string.IsNullOrEmpty((string)hook["source_label"]));
                AssertEx.Equal(true, !string.IsNullOrEmpty((string)hook["completion_proof"]));
                if ((string)hook["cpu"] == "Z80") union.Add((uint)hook["pc"]);
            }
            var declared = new HashSet<uint>();
            foreach (JToken pc in (JArray)game["z80_watch_pc_union"])
                declared.Add((uint)pc);
            AssertEx.Equal(union.Count, declared.Count);
            foreach (uint pc in union) AssertEx.Equal(true, declared.Contains(pc));
        }

        private delegate CompleteRunAudioObserver ObserverFactory(IGpgxAudioTraceApi api);

        private struct FullRunResult
        {
            public int Frames, Maximum, OpenServicesAtCutoff, PendingServicesAtCutoff;
            public long Events;
            public string Digest, FrontierDigest, TerminalZ80Digest;
        }

        private static void ProveFullRun(string game, string rom, string movie,
            ObserverFactory factory)
        {
            OpenReviewedMovie(game.ToLowerInvariant(), movie);
            FullRunResult first = CaptureFullRun(rom, movie, factory);
            Console.WriteLine(game+" terminal frontier: active="+first.OpenServicesAtCutoff
                +" pending="+first.PendingServicesAtCutoff+" frontier="+first.FrontierDigest
                +" z80="+first.TerminalZ80Digest);
            JObject expected = (JObject)ExpectedRun(game)["complete"];
            AssertEx.Equal((int)expected["frames"], first.Frames);
            AssertEx.Equal((long)expected["events"], first.Events);
            AssertEx.Equal((int)expected["maximum_frame_occupancy"], first.Maximum);
            AssertEx.Equal((string)expected["event_digest_sha256"], first.Digest);
            AssertEx.Equal((int)expected["open_services_at_cutoff"], first.OpenServicesAtCutoff);
            AssertEx.Equal((int)expected["pending_services_at_cutoff"], first.PendingServicesAtCutoff);
            AssertEx.Equal((string)expected["frontier_digest_sha256"], first.FrontierDigest);
            AssertEx.Equal((string)expected["terminal_z80_sha256"], first.TerminalZ80Digest);
            FullRunResult second = CaptureFullRun(rom, movie, factory);
            AssertEx.Equal(first.Frames, second.Frames); AssertEx.Equal(first.Events, second.Events);
            AssertEx.Equal(first.Maximum, second.Maximum); AssertEx.Equal(first.Digest, second.Digest);
            AssertEx.Equal(first.OpenServicesAtCutoff,second.OpenServicesAtCutoff);
            AssertEx.Equal(first.PendingServicesAtCutoff,second.PendingServicesAtCutoff);
            AssertEx.Equal(first.FrontierDigest,second.FrontierDigest);
            AssertEx.Equal(first.TerminalZ80Digest,second.TerminalZ80Digest);
            AssertEx.Equal(true, first.Maximum * 4 <= 65536);
            Console.WriteLine(game + " complete observer: frames=" + first.Frames
                + " events=" + first.Events + " max_occupancy=" + first.Maximum
                + " digest=" + first.Digest);
        }

        private static FullRunResult CaptureFullRun(string rom, string moviePath,
            ObserverFactory factory)
        {
            Bk2Movie movie = Bk2Reader.Read(moviePath); long events = 0; int maximum = 0;
            var serviceStack = new List<byte>(); var tail = new Queue<string>();
            using (var sha = SHA256.Create())
            using (var host = GpgxHost.Open(rom, GpgxHost.CreateGhz1SyncSettings()))
            {
                CompleteRunAudioObserver observer = factory(host.CreateAudioTraceApi());
                byte[] encoded = new byte[32];
                using (IEnumerator<Bk2Frame> rows = movie.OpenFrameStream().GetEnumerator())
                {
                    for (int frame = 0; frame < movie.FrameCount; frame++)
                    {
                        AssertEx.Equal(true, rows.MoveNext()); S1TraceCaptureRunner.ApplyFrame(rows.Current, host);
                        try { observer.CaptureFrame(host.Advance, (buffer, count) => {
                                if (count > maximum) maximum = count; events += count;
                                for (int i = 0; i < count; i++) { GpgxAudioTraceEvent value=buffer[i];
                                    if(value.Kind==1)serviceStack.Add(value.ServiceKindId);
                                    else if(value.Kind==2&&serviceStack.Count!=0)serviceStack.RemoveAt(serviceStack.Count-1);
                                    tail.Enqueue(value.Ordinal+":"+value.Kind+":"+value.ServiceKindId+":"+value.Pc.ToString("x")+":"+value.Subject);
                                    while(tail.Count>20)tail.Dequeue(); EncodeEvent(value, encoded);
                                    sha.TransformBlock(encoded, 0, encoded.Length, null, 0); }
                            }); }
                        catch (Exception error) { throw new InvalidOperationException(
                            "Complete observer failed at movie frame " + frame + " stack="
                            + string.Join(",",serviceStack) + " tail=" + string.Join("|",tail.ToArray())
                            + " zram38=" + Hex(host,0x38,4) + " e7=" + Hex(host,0xE7,4)
                            + " 10f=" + Hex(host,0x10F,4) + " 110=" + Hex(host,0x110,4)
                            + " 14b=" + Hex(host,0x14B,4) + " 17a=" + Hex(host,0x17A,4)
                            + " 1b0=" + Hex(host,0x1B0,4) + ".", error); }
                    }
                }
                AssertEx.Equal(serviceStack.Count,observer.ActiveServiceDepth);
                int activeAtCutoff=observer.ActiveServiceDepth;
                int pendingAtCutoff=observer.PendingServiceCount;
                CompleteRunAudioObserver.CutoffFrontier frontier=observer.CaptureCutoffFrontier();
                string frontierDigest=DigestFrontier(frontier);
                string terminalZ80Digest=DigestZ80(host);
                observer.DiscardCutoffState();
                AssertEx.Equal(0,observer.ActiveServiceDepth);AssertEx.Equal(0,observer.PendingServiceCount);
                AssertEx.Equal(frontierDigest,DigestFrontier(frontier));
                AssertEx.Equal(terminalZ80Digest,DigestZ80(host));
                sha.TransformFinalBlock(new byte[0], 0, 0);
                return new FullRunResult { Frames=movie.FrameCount, Events=events,
                    Maximum=maximum, Digest=BytesToHex(sha.Hash),
                    OpenServicesAtCutoff=activeAtCutoff,PendingServicesAtCutoff=pendingAtCutoff,
                    FrontierDigest=frontierDigest,TerminalZ80Digest=terminalZ80Digest };
            }
        }

        private static string DigestZ80(GpgxHost host)
        {
            byte[] bytes=new byte[8192];for(int i=0;i<bytes.Length;i++)bytes[i]=host.ReadZ80RamByte(i);
            using(var sha=SHA256.Create())return BytesToHex(sha.ComputeHash(bytes));
        }

        private static string DigestFrontier(CompleteRunAudioObserver.CutoffFrontier frontier)
        {
            using(var bytes=new MemoryStream())using(var writer=new BinaryWriter(bytes,System.Text.Encoding.UTF8,true))
            {
                writer.Write(new byte[]{0x4f,0x47,0x46,0x43});writer.Write((ushort)1);
                writer.Write(frontier.YmPort0Address);writer.Write(frontier.YmPort1Address);
                writer.Write(frontier.ArmEpoch);writer.Write(frontier.IsArmed);
                AppendFrontierServices(writer,1,frontier.ActiveServices);
                AppendFrontierServices(writer,2,frontier.PendingServices);writer.Flush();
                using(var sha=SHA256.Create())return BytesToHex(sha.ComputeHash(bytes.ToArray()));
            }
        }

        private static void AppendFrontierServices(BinaryWriter writer,byte group,
            IReadOnlyList<CompleteRunAudioObserver.DriverService> services)
        {
            writer.Write(group);writer.Write(services.Count);
            for(int i=0;i<services.Count;i++)
            {
                CompleteRunAudioObserver.DriverService s=services[i];
                writer.Write(s.IsComplete);writer.Write(s.Token);writer.Write(s.ParentToken);writer.Write(s.Kind);
                writer.Write(s.Depth);writer.Write(s.BeginCoordinate);writer.Write(s.EndCoordinate);
                writer.Write(s.BeginPc);writer.Write(s.BeginHookToken);writer.Write(s.BeginSourceCpu);
                writer.Write(s.EndPc);writer.Write(s.EndHookToken);writer.Write(s.Cancelled);
                writer.Write(s.OwnedChipEvents.Count);
                for(int j=0;j<s.OwnedChipEvents.Count;j++)
                {
                    CompleteRunAudioObserver.OwnedChipEvent e=s.OwnedChipEvents[j];
                    writer.Write(e.Coordinate);writer.Write(e.NativeOrdinal);writer.Write(e.EventKind);
                    writer.Write(e.Pc);writer.Write(e.SourceCpu);writer.Write(e.Subject);writer.Write(e.Value);
                    writer.Write(e.IsData);writer.Write(e.Port);writer.Write(e.Register);
                }
                writer.Write(s.Snapshots.Count);
                for(int j=0;j<s.Snapshots.Count;j++)
                {var g=s.Snapshots[j];byte[] snapshot=g.Bytes;writer.Write(g.RangeId);writer.Write(g.SourceCpu);
                    writer.Write(g.Pc);writer.Write(snapshot.Length);writer.Write(snapshot);}
            }
        }

        private static void EncodeEvent(GpgxAudioTraceEvent e, byte[] b)
        {
            Array.Clear(b,0,b.Length); Put32(b,0,e.Ordinal); Put16(b,4,e.ServiceToken);
            Put16(b,6,e.ParentToken); Put32(b,8,e.Pc); Put16(b,12,e.Subject); Put16(b,14,e.Offset);
            b[16]=e.Kind;b[17]=e.ServiceKindId;b[18]=e.Depth;b[19]=e.SourceCpu;b[20]=e.PayloadLength;
            b[21]=e.Value;b[22]=e.Flags;b[23]=e.Reserved; ulong p=e.Payload;
            for(int i=0;i<8;i++)b[24+i]=(byte)(p>>(8*i));
        }
        private static void Put16(byte[] b,int o,ushort v){b[o]=(byte)v;b[o+1]=(byte)(v>>8);}
        private static void Put32(byte[] b,int o,uint v){for(int i=0;i<4;i++)b[o+i]=(byte)(v>>(8*i));}

        private static void PreserveEnabledIdentity(string game, string rom, string moviePath,
            ObserverFactory factory)
        {
            OpenReviewedMovie(game, moviePath);
            string disabled = IdentityDigest(rom, moviePath, null);
            string expected = (string)ExpectedRun(game)["state_digest_sha256"];
            AssertEx.Equal(expected, disabled);
            if (Environment.GetEnvironmentVariable("OPENGGF_GPGX_IDENTITY_DISABLED_ONLY") == "1")
            {
                Console.WriteLine("GPGX stock/disabled deterministic checkpoint digest=" + disabled);
                return;
            }
            string enabled = IdentityDigest(rom, moviePath, factory);
            string repeated = IdentityDigest(rom, moviePath, factory);
            AssertEx.Equal(disabled, enabled);
            AssertEx.Equal(enabled, repeated);
            AssertEx.Equal(expected, enabled);
            Console.WriteLine("GPGX disabled/enabled deterministic checkpoint digest=" + enabled);
        }

        private static void ProveResetAndPowerRearm(string game, string rom,
            string moviePath, ObserverFactory factory, ushort armHookToken)
        {
            ProveActionRearm(game, rom, moviePath, factory, armHookToken, "Reset");
            ProveActionRearm(game, rom, moviePath, factory, armHookToken, "Power");
        }

        private static void ProveSaveLoad(string game, string rom, string moviePath, ObserverFactory factory)
        {
            Bk2Movie movie = OpenReviewedMovie(game, moviePath);
            using (var host = GpgxHost.Open(rom, GpgxHost.CreateGhz1SyncSettings()))
            {
                CompleteRunAudioObserver observer = factory(host.CreateAudioTraceApi());
                using (IEnumerator<Bk2Frame> rows = movie.OpenFrameStream().GetEnumerator())
                {
                    for (int frame = 0; frame < 1000; frame++)
                    {
                        AssertEx.Equal(true, rows.MoveNext());
                        Bk2Frame row = rows.Current;
                        if (frame < 120 || observer.ActiveServiceDepth != 0)
                        {
                            S1TraceCaptureRunner.ApplyFrame(row, host);
                            observer.CaptureFrame(host.Advance, (buffer, count) => { });
                            continue;
                        }
                        byte[] state = host.CloneSavestate();
                        CompleteRunAudioObserver.Checkpoint checkpoint = observer.CreateCheckpoint();
                        S1TraceCaptureRunner.ApplyFrame(row, host);
                        GpgxAudioTraceEvent[] firstEvents = observer.CaptureFrame(host.Advance);
                        if (observer.ActiveServiceDepth != 0) continue;
                        byte[] firstCheckpoint = host.CaptureDeterministicCheckpoint();
                        int firstVideoLength = host.LastCheckpointVideoLength;
                        int firstAudioFrames = host.LastCheckpointAudioFrames;
                        string first = Sha256Bytes(firstCheckpoint);
                        host.LoadSavestate(state, observer, checkpoint);
                        S1TraceCaptureRunner.ApplyFrame(row, host);
                        GpgxAudioTraceEvent[] secondEvents = observer.CaptureFrame(host.Advance);
                        byte[] secondCheckpoint = host.CaptureDeterministicCheckpoint();
                        string second = Sha256Bytes(secondCheckpoint);
                        if (first != second)
                        {
                            int difference = 0;
                            while (difference < firstCheckpoint.Length && difference < secondCheckpoint.Length
                                && firstCheckpoint[difference] == secondCheckpoint[difference]) difference++;
                            Console.WriteLine("save/load checkpoint mismatch offset=" + difference
                                + " first_length=" + firstCheckpoint.Length
                                + " second_length=" + secondCheckpoint.Length
                                + " video=" + firstVideoLength + "/" + host.LastCheckpointVideoLength
                                + " audio=" + firstAudioFrames + "/" + host.LastCheckpointAudioFrames);
                            continue;
                        }
                        AssertEx.Equal(first, second);
                        AssertEx.Equal(EventDigest(firstEvents), EventDigest(secondEvents));
                        byte[] oldState = host.CloneSavestate();
                        CompleteRunAudioObserver.Checkpoint oldCheckpoint = observer.CreateCheckpoint();
                        host.SetButton("Reset", true);
                        observer.CaptureFrame(host.Advance, (buffer, count) => { });
                        host.ClearButtons();
                        for (int settle = 0; settle < 300
                            && (!observer.IsArmed || observer.ActiveServiceDepth != 0); settle++)
                            observer.CaptureFrame(host.Advance, (buffer, count) => { });
                        AssertEx.Equal(true, observer.IsArmed && observer.ActiveServiceDepth == 0);
                        byte[] beforeRejectedLoad = host.CaptureDeterministicCheckpoint();
                        AssertEx.Throws<InvalidOperationException>(
                            () => host.LoadSavestate(oldState, observer, oldCheckpoint), "epoch");
                        AssertEx.Equal(Sha256Bytes(beforeRejectedLoad),
                            Sha256Bytes(host.CaptureDeterministicCheckpoint()));
                        return;
                    }
                }
            }
            throw new InvalidOperationException("No empty observer boundary was available for save/load proof.");
        }

        private static string EventDigest(GpgxAudioTraceEvent[] events)
        {
            var bytes = new byte[events.Length * 32];
            for (int i = 0; i < events.Length; i++)
            {
                IntPtr pointer = System.Runtime.InteropServices.Marshal.AllocHGlobal(32);
                try { System.Runtime.InteropServices.Marshal.StructureToPtr(events[i], pointer, false);
                    System.Runtime.InteropServices.Marshal.Copy(pointer, bytes, i * 32, 32); }
                finally { System.Runtime.InteropServices.Marshal.FreeHGlobal(pointer); }
            }
            return Sha256Bytes(bytes);
        }

        private static string Sha256Bytes(byte[] bytes)
        {
            using (SHA256 sha = SHA256.Create()) return BytesToHex(sha.ComputeHash(bytes));
        }

        private static void ProveActionRearm(string game, string rom, string moviePath,
            ObserverFactory factory, ushort armHookToken, string action)
        {
            Bk2Movie movie = OpenReviewedMovie(game, moviePath);
            using (var host = GpgxHost.Open(rom, GpgxHost.CreateGhz1SyncSettings()))
            {
                CompleteRunAudioObserver observer = factory(host.CreateAudioTraceApi());
                using (IEnumerator<Bk2Frame> rows = movie.OpenFrameStream().GetEnumerator())
                {
                    int warmupFrame = 0;
                    for (; warmupFrame < 1000; warmupFrame++)
                    {
                        AssertEx.Equal(true, rows.MoveNext());
                        S1TraceCaptureRunner.ApplyFrame(rows.Current, host);
                        observer.CaptureFrame(host.Advance, (buffer, count) => { });
                        if (warmupFrame >= 100 && ((action == "Power" && observer.IsArmed)
                            || observer.ActiveServiceDepth >= 2)) break;
                    }
                    AssertEx.Equal(true, action == "Power" || observer.ActiveServiceDepth >= 2);
                    AssertEx.Equal(true, observer.IsArmed);
                    long epochBefore = observer.ArmEpoch;
                    host.SetButton(action, true);
                    var all = new List<GpgxAudioTraceEvent>();
                    var eventFrames = new List<List<GpgxAudioTraceEvent>>();
                    var actionFrame = new List<GpgxAudioTraceEvent>();
                    observer.CaptureFrame(host.Advance, (buffer, count) =>
                    { for (int i = 0; i < count; i++) { actionFrame.Add(buffer[i]); all.Add(buffer[i]); } });
                    eventFrames.Add(actionFrame);
                    host.ClearButtons();
                    for (int frame = 0; frame < 300; frame++)
                    {
                        var laterFrame = new List<GpgxAudioTraceEvent>();
                        observer.CaptureFrame(host.Advance, (buffer, count) =>
                        { for (int i = 0; i < count; i++) { laterFrame.Add(buffer[i]); all.Add(buffer[i]); } });
                        eventFrames.Add(laterFrame);
                    }
                    EventCoordinate resetBegin = FindEvent(eventFrames,x=>x.Kind==8,0,0);
                    EventCoordinate resetEnd = FindEvent(eventFrames,x=>x.Kind==9,0,0);
                    EventCoordinate armEnd = FindEvent(eventFrames,x=>x.Kind==2&&x.Subject==armHookToken,0,0);
                    EventCoordinate firstArmedZ80 = FindEvent(eventFrames,x=>x.Kind==1&&x.SourceCpu==1,
                        armEnd.Frame,armEnd.Ordinal+1);
                    List<GpgxAudioTraceEvent> resetEvents=eventFrames[resetBegin.Frame];
                    int cancelled=resetEvents.FindAll(x=>x.Kind==2&&(x.Flags&2)!=0).Count;
                    int cancelledDepth=resetEvents[resetBegin.Ordinal].Subject;
                    JObject expected = (JObject)ExpectedRun(game)["lifecycle"][action.ToLowerInvariant()];
                    AssertEx.Equal((int)expected["events"], all.Count);
                    AssertEx.Equal((int)expected["warmup_frame"], warmupFrame);
                    Console.WriteLine(game+" "+action+" frame coordinates reset="+resetBegin+"/"+resetEnd
                        +" arm="+armEnd+" first_z80="+firstArmedZ80+" action_events="+actionFrame.Count);
                    AssertEx.Equal((int)expected["reset_frame"], resetBegin.Frame);
                    AssertEx.Equal((int)expected["reset_ordinal"], resetBegin.Ordinal);
                    AssertEx.Equal((int)expected["reset_end_frame"], resetEnd.Frame);
                    AssertEx.Equal((int)expected["reset_end_ordinal"], resetEnd.Ordinal);
                    AssertEx.Equal((int)expected["arm_frame"], armEnd.Frame);
                    AssertEx.Equal((int)expected["arm_ordinal"], armEnd.Ordinal);
                    AssertEx.Equal((int)expected["first_z80_frame"], firstArmedZ80.Frame);
                    AssertEx.Equal((int)expected["first_z80_ordinal"], firstArmedZ80.Ordinal);
                    AssertEx.Equal((int)expected["cancelled"], cancelled);
                    AssertEx.Equal((int)expected["cancelled_depth"], cancelledDepth);
                    AssertEx.Equal((int)expected["action_frame_events"], actionFrame.Count);
                    Console.WriteLine(game + " " + action + " diagnostic events=" + all.Count
                        + " warmup_frame=" + warmupFrame
                        + " reset_begin=" + resetBegin + " reset_end=" + resetEnd
                        + " arm_end=" + armEnd + " first_z80=" + firstArmedZ80
                        + " cancelled=" + cancelled + "/" + cancelledDepth
                        + " armed=" + observer.IsArmed + " epoch=" + observer.ArmEpoch);
                    AssertEx.Equal(true, resetBegin.Frame==0&&resetEnd.Frame==0&&resetEnd.Ordinal>resetBegin.Ordinal);
                    AssertEx.Equal((byte)3, resetEvents[resetBegin.Ordinal].SourceCpu);
                    AssertEx.Equal(action == "Power" ? (byte)1 : (byte)0,
                        (byte)(resetEvents[resetBegin.Ordinal].Flags & 1));
                    for (int i = 0; i < actionFrame.Count; i++) AssertEx.Equal((uint)i, actionFrame[i].Ordinal);
                    AssertEx.Equal(cancelledDepth, cancelled);
                    AssertEx.Equal(true, action != "Reset" || cancelledDepth > 0);
                    AssertEx.Equal(true, armEnd.Frame>resetEnd.Frame||armEnd.Ordinal>resetEnd.Ordinal);
                    AssertEx.Equal(true, firstArmedZ80.Frame>armEnd.Frame||firstArmedZ80.Ordinal>armEnd.Ordinal);
                    AssertEx.Equal(true, observer.IsArmed);
                    AssertEx.Equal(epochBefore + 2, observer.ArmEpoch);
                    Console.WriteLine(game + " " + action + " rearm events=" + all.Count
                        + " reset_begin=" + resetBegin + " reset_end=" + resetEnd
                        + " arm_end=" + armEnd + " first_z80=" + firstArmedZ80);
                }
            }
        }

        private struct EventCoordinate
        {
            internal int Frame,Ordinal;
            public override string ToString(){return Frame+":"+Ordinal;}
        }
        private static EventCoordinate FindEvent(List<List<GpgxAudioTraceEvent>> frames,
            Predicate<GpgxAudioTraceEvent> predicate,int firstFrame,int firstOrdinal)
        {
            for(int f=firstFrame;f<frames.Count;f++)for(int i=f==firstFrame?firstOrdinal:0;i<frames[f].Count;i++)
                if(predicate(frames[f][i]))return new EventCoordinate{Frame=f,Ordinal=i};
            return new EventCoordinate{Frame=-1,Ordinal=-1};
        }

        private static string IdentityDigest(string rom, string moviePath, ObserverFactory factory)
        {
            Bk2Movie movie = Bk2Reader.Read(moviePath);
            using (var sha = SHA256.Create())
            using (var host = GpgxHost.Open(rom, GpgxHost.CreateGhz1SyncSettings()))
            {
                CompleteRunAudioObserver observer = factory == null ? null
                    : factory(host.CreateAudioTraceApi());
                using (IEnumerator<Bk2Frame> rows = movie.OpenFrameStream().GetEnumerator())
                {
                    int frames = Math.Min(1000, movie.FrameCount);
                    for (int frame = 0; frame < frames; frame++)
                    {
                        AssertEx.Equal(true, rows.MoveNext());
                        S1TraceCaptureRunner.ApplyFrame(rows.Current, host);
                        if (observer == null) host.Advance();
                        else observer.CaptureFrame(host.Advance, (buffer, count) => { });
                        byte[] checkpoint = host.CaptureDeterministicCheckpoint();
                        sha.TransformBlock(checkpoint, 0, checkpoint.Length, null, 0);
                    }
                }
                sha.TransformFinalBlock(new byte[0], 0, 0);
                return BytesToHex(sha.Hash);
            }
        }

        private static JObject ExpectedRun(string game)
        {
            JObject root = JObject.Parse(File.ReadAllText(Path.Combine(
                EndToEndTests.ToolDirectory, "fixtures/gpgx-audio-capability-v1.json")));
            return (JObject)root["runs"][game.ToLowerInvariant()];
        }

        private static void MeasurePerformance(string game, string rom, string moviePath,
            ObserverFactory factory)
        {
            OpenReviewedMovie(game.ToLowerInvariant(), moviePath);
            // Warm both orders before measuring. Each pair reuses one emulated host and
            // restores its exact initial savestate between lanes; alternating AB/BA keeps
            // host-frequency and thermal drift from being mistaken for observer cost.
            MeasurePair(rom, moviePath, factory, 2, false, false);
            MeasurePair(rom, moviePath, factory, 2, true, false);
            const int samples = 5;
            var disabled = new long[samples];
            var enabled = new long[samples];
            var pairedSlowdowns = new double[samples];
            int maximumOccupancy = 0;
            long copiedEvents = 0;
            for (int i = 0; i < samples; i++)
            {
                bool enabledFirst = (i & 1) != 0;
                PairedMeasurement measured = MeasurePair(
                    rom, moviePath, factory, 2, enabledFirst, true);
                disabled[i] = measured.DisabledTicks;
                enabled[i] = measured.Enabled.Ticks;
                pairedSlowdowns[i] = (double)measured.Enabled.Ticks
                    / measured.DisabledTicks - 1.0;
                if (measured.Enabled.MaximumOccupancy > maximumOccupancy)
                    maximumOccupancy = measured.Enabled.MaximumOccupancy;
                copiedEvents += measured.Enabled.Events;
            }
            double[] sortedSlowdowns = (double[])pairedSlowdowns.Clone();
            Array.Sort(sortedSlowdowns);
            double medianSlowdown = sortedSlowdowns[samples / 2];
            double worstSlowdown = sortedSlowdowns[samples - 1];
            DiagnosticPairs configuredIdle = MeasureDiagnosticPairs(
                rom, moviePath, factory, 1);
            DiagnosticPairs projected = MeasureDiagnosticPairs(
                rom, moviePath, factory, 3);
            DiagnosticPairs rawNative = MeasureDiagnosticPairs(
                rom, moviePath, factory, 4);
            Console.WriteLine(game + " observer performance: disabled_ticks="
                + string.Join(",", disabled) + " configured_idle_ticks="
                + string.Join(",", configuredIdle.EnabledTicks) + " enabled_ticks="
                + string.Join(",", enabled) + " projected_ticks="
                + string.Join(",", projected.EnabledTicks) + " raw_native_ticks="
                + string.Join(",", rawNative.EnabledTicks)
                + " paired_orders=AB,BA,AB,BA,AB paired_slowdowns="
                + string.Join(",", Array.ConvertAll(pairedSlowdowns,
                    value => value.ToString("F4")))
                + " diagnostic_orders=AB,BA,AB configured_idle_slowdowns="
                + FormatRatios(configuredIdle.Ratios) + " projected_slowdowns="
                + FormatRatios(projected.Ratios) + " raw_native_slowdowns="
                + FormatRatios(rawNative.Ratios) + " configured_idle_slowdown="
                + configuredIdle.MedianRatio.ToString("F4")
                + " median_slowdown=" + medianSlowdown.ToString("F4")
                + " worst_slowdown=" + worstSlowdown.ToString("F4")
                + " max_occupancy=" + maximumOccupancy + " capacity=65536"
                + " copied_events=" + copiedEvents);
            AssertEx.Equal(true, medianSlowdown <= 0.10);
            AssertEx.Equal(true, worstSlowdown <= 0.15);
            AssertEx.Equal(true, maximumOccupancy * 4 <= 65536);
            JObject locked=(JObject)ExpectedRun(game)["performance"];
            AssertEx.Equal("paired_interleaved_v1", (string)locked["method"]);
            AssertEx.Equal("AB,BA", string.Join(",",
                ((JArray)locked["warmup_orders"]).ToObject<string[]>()));
            AssertEx.Equal("AB,BA,AB,BA,AB", string.Join(",",
                ((JArray)locked["measured_orders"]).ToObject<string[]>()));
            AssertEx.Equal(samples,((JArray)locked["disabled_ticks"]).Count);
            AssertEx.Equal(samples,((JArray)locked["enabled_ticks"]).Count);
            JArray lockedRatios = (JArray)locked["paired_slowdowns"];
            AssertEx.Equal(samples, lockedRatios.Count);
            for (int i = 0; i < samples; i++)
            {
                double derived = (double)(long)locked["enabled_ticks"][i]
                    / (long)locked["disabled_ticks"][i] - 1.0;
                AssertEx.Equal(true, Math.Abs(derived - (double)lockedRatios[i]) <= 0.0001);
                AssertEx.Equal(true,
                    Math.Abs(pairedSlowdowns[i] - (double)lockedRatios[i]) <= 0.05);
            }
            double lockedMedian=(double)locked["median_slowdown"];
            double lockedWorst=(double)locked["worst_slowdown"];
            AssertEx.Equal(true,lockedMedian<=0.10&&lockedWorst<=0.15);
            AssertEx.Equal(true,Math.Abs(medianSlowdown-lockedMedian)<=0.05);
            AssertEx.Equal(true,Math.Abs(worstSlowdown-lockedWorst)<=0.05);
        }

        private static DiagnosticPairs MeasureDiagnosticPairs(string rom, string moviePath,
            ObserverFactory factory, int mode)
        {
            var ratios = new double[3];
            var enabledTicks = new long[3];
            for (int i = 0; i < ratios.Length; i++)
            {
                PairedMeasurement pair = MeasurePair(rom, moviePath, factory,
                    mode, (i & 1) != 0, false);
                enabledTicks[i] = pair.Enabled.Ticks;
                ratios[i] = (double)pair.Enabled.Ticks / pair.DisabledTicks - 1.0;
            }
            double[] sorted = (double[])ratios.Clone();
            Array.Sort(sorted);
            return new DiagnosticPairs(enabledTicks, ratios, sorted[1]);
        }

        private static string FormatRatios(double[] values)
        {
            return string.Join(",", Array.ConvertAll(values,
                value => value.ToString("F4")));
        }

        private static PairedMeasurement MeasurePair(string rom, string moviePath,
            ObserverFactory factory, int mode, bool enabledFirst, bool retainCounts)
        {
            Bk2Movie movie = Bk2Reader.Read(moviePath);
            using (var host = GpgxHost.Open(rom, GpgxHost.CreateGhz1SyncSettings()))
            {
                byte[] initialState = host.CloneSavestate();
                LaneMeasurement disabled;
                LaneMeasurement enabled;
                if (enabledFirst)
                {
                    IGpgxAudioTraceApi traceApi = host.CreateAudioTraceApi();
                    CompleteRunAudioObserver observer = factory(traceApi);
                    enabled = MeasureLaneOnHost(host, movie, observer, traceApi,
                        mode, retainCounts);
                    observer.DiscardCutoffState();
                    host.LoadSavestate(initialState);
                    disabled = MeasureLaneOnHost(host, movie, null, null, 0, false);
                }
                else
                {
                    disabled = MeasureLaneOnHost(host, movie, null, null, 0, false);
                    host.LoadSavestate(initialState);
                    IGpgxAudioTraceApi traceApi = host.CreateAudioTraceApi();
                    CompleteRunAudioObserver observer = factory(traceApi);
                    enabled = MeasureLaneOnHost(host, movie, observer, traceApi,
                        mode, retainCounts);
                    observer.DiscardCutoffState();
                }
                return new PairedMeasurement(disabled.Ticks, enabled);
            }
        }

        private struct PairedMeasurement
        {
            public PairedMeasurement(long disabledTicks, LaneMeasurement enabled)
            { DisabledTicks = disabledTicks; Enabled = enabled; }
            public long DisabledTicks;
            public LaneMeasurement Enabled;
        }

        private struct DiagnosticPairs
        {
            public DiagnosticPairs(long[] enabledTicks, double[] ratios, double medianRatio)
            { EnabledTicks = enabledTicks; Ratios = ratios; MedianRatio = medianRatio; }
            public long[] EnabledTicks;
            public double[] Ratios;
            public double MedianRatio;
        }

        private struct LaneMeasurement
        {
            public long Ticks;
            public int MaximumOccupancy;
            public long Events;
        }

        private static LaneMeasurement MeasureLane(string rom, string moviePath,
            ObserverFactory factory, int mode, bool retainCounts)
        {
            Bk2Movie movie = Bk2Reader.Read(moviePath);
            using (var host = GpgxHost.Open(rom, GpgxHost.CreateGhz1SyncSettings()))
            {
                IGpgxAudioTraceApi traceApi = factory == null ? null : host.CreateAudioTraceApi();
                CompleteRunAudioObserver observer = factory == null ? null : factory(traceApi);
                int maximumOccupancy = 0;
                long events = 0;
                int frames = Math.Min(1000, movie.FrameCount);
                Action advance = host.Advance;
                Action<GpgxAudioTraceEvent[], int> consume = (buffer, count) =>
                {
                    if (count > maximumOccupancy) maximumOccupancy = count;
                    events += count;
                };
                var stopwatch = Stopwatch.StartNew();
                using (IEnumerator<Bk2Frame> rows = movie.OpenFrameStream().GetEnumerator())
                {
                    for (int frame = 0; frame < frames; frame++)
                    {
                        AssertEx.Equal(true, rows.MoveNext());
                        S1TraceCaptureRunner.ApplyFrame(rows.Current, host);
                        if (mode < 2) host.Advance();
                        else if (mode == 2)
                        {
                            observer.CaptureFrame(advance, consume);
                        }
                        else if (mode == 3)
                        {
                            GpgxAudioTraceEvent[] captured = observer.CaptureFrame(advance);
                            if (captured.Length > maximumOccupancy) maximumOccupancy = captured.Length;
                            events += captured.Length;
                        }
                        else
                        {
                            uint count, overflow, copied;
                            GpgxAudioTraceEvent[] rawEvents;
                            AssertEx.Equal(0, traceApi.BeginFrame());
                            host.Advance();
                            AssertEx.Equal(0, traceApi.EndFrame());
                            AssertEx.Equal(0, traceApi.EventCount(out count, out overflow));
                            AssertEx.Equal(0u, overflow);
                            AssertEx.Equal(0, ((GpgxAudioTraceNative)traceApi)
                                .DrainNative(count, out copied, out rawEvents));
                            AssertEx.Equal(count, copied);
                        }
                    }
                }
                stopwatch.Stop();
                return new LaneMeasurement { Ticks = stopwatch.ElapsedTicks,
                    MaximumOccupancy = maximumOccupancy,
                    Events = retainCounts ? events : 0 };
            }
        }

        private static LaneMeasurement MeasureLaneOnHost(GpgxHost host, Bk2Movie movie,
            CompleteRunAudioObserver observer, IGpgxAudioTraceApi traceApi,
            int mode, bool retainCounts)
        {
            int maximumOccupancy = 0;
            long events = 0;
            int frames = Math.Min(1000, movie.FrameCount);
            Action advance = host.Advance;
            Action<GpgxAudioTraceEvent[], int> consume = (buffer, count) =>
            {
                if (count > maximumOccupancy) maximumOccupancy = count;
                events += count;
            };
            var stopwatch = Stopwatch.StartNew();
            using (IEnumerator<Bk2Frame> rows = movie.OpenFrameStream().GetEnumerator())
            {
                for (int frame = 0; frame < frames; frame++)
                {
                    AssertEx.Equal(true, rows.MoveNext());
                    S1TraceCaptureRunner.ApplyFrame(rows.Current, host);
                    if (mode == 0 || mode == 1) host.Advance();
                    else if (mode == 2)
                    {
                        observer.CaptureFrame(advance, consume);
                    }
                    else if (mode == 3)
                    {
                        GpgxAudioTraceEvent[] captured = observer.CaptureFrame(advance);
                        if (captured.Length > maximumOccupancy) maximumOccupancy = captured.Length;
                        events += captured.Length;
                    }
                    else
                    {
                        uint count, overflow, copied;
                        GpgxAudioTraceEvent[] rawEvents;
                        AssertEx.Equal(0, traceApi.BeginFrame());
                        host.Advance();
                        AssertEx.Equal(0, traceApi.EndFrame());
                        AssertEx.Equal(0, traceApi.EventCount(out count, out overflow));
                        AssertEx.Equal(0u, overflow);
                        AssertEx.Equal(0, ((GpgxAudioTraceNative)traceApi)
                            .DrainNative(count, out copied, out rawEvents));
                        AssertEx.Equal(count, copied);
                    }
                }
            }
            stopwatch.Stop();
            return new LaneMeasurement { Ticks = stopwatch.ElapsedTicks,
                MaximumOccupancy = maximumOccupancy,
                Events = retainCounts ? events : 0 };
        }

        private static void ObserveS3kBootstrap()
        {
            string rom = Environment.GetEnvironmentVariable("S3K_ROM_PATH");
            Bk2Movie movie = OpenReviewedMovie("s3k",
                Environment.GetEnvironmentVariable("S3K_BK2_PATH"));
            using (var host = GpgxHost.Open(rom, GpgxHost.CreateGhz1SyncSettings()))
            {
                var observer = CreateS3kObserver(host.CreateAudioTraceApi());
                long begins = 0, fm = 0, psg = 0, dac = 0, events = 0;
                var ymProjection = new YmLatchProjection();
                var breakdown = new CapabilityBreakdown(8);
                var beginsByKind = new long[256];
                var fmByKind = new long[256];
                var psgByKind = new long[256];
                int maximumOccupancy = 0;
                var digestBytes = new MemoryStream();
                var digestWriter = new BinaryWriter(digestBytes);
                int frames = Math.Min(1000, movie.FrameCount);
                using (IEnumerator<Bk2Frame> rows = movie.OpenFrameStream().GetEnumerator())
                {
                    for (int frame = 0; frame < frames; frame++)
                    {
                        AssertEx.Equal(true, rows.MoveNext());
                        S1TraceCaptureRunner.ApplyFrame(rows.Current, host);
                        GpgxAudioTraceEvent[] captured;
                        try { captured = observer.CaptureFrame(host.Advance); }
                        catch (Exception error)
                        {
                            Console.WriteLine("S3K ZRAM hook bytes: 0000=" + Hex(host, 0, 4)
                                + " 0038=" + Hex(host, 0x38, 4)
                                + " 0084=" + Hex(host, 0x84, 4)
                                + " 0085=" + Hex(host, 0x85, 4)
                                + " 00ac=" + Hex(host, 0xAC, 4)
                                + " 011b=" + Hex(host, 0x11B, 4)
                                + " 0121=" + Hex(host, 0x121, 4));
                            throw new InvalidOperationException("S3K observer frame " + frame + " failed.", error);
                        }
                        if (captured.Length > maximumOccupancy) maximumOccupancy = captured.Length;
                        digestWriter.Write(frame);
                        digestWriter.Write(captured.Length);
                        events += captured.Length;
                        for (int i = 0; i < captured.Length; i++)
                        {
                            GpgxAudioTraceEvent value = captured[i];
                            breakdown.Accept(value);
                            digestWriter.Write(value.Ordinal);
                            digestWriter.Write(value.ServiceToken);
                            digestWriter.Write(value.ParentToken);
                            digestWriter.Write(value.Pc);
                            digestWriter.Write(value.Subject);
                            digestWriter.Write(value.Offset);
                            digestWriter.Write(value.Kind);
                            digestWriter.Write(value.ServiceKindId);
                            digestWriter.Write(value.Depth);
                            digestWriter.Write(value.SourceCpu);
                            digestWriter.Write(value.PayloadLength);
                            digestWriter.Write(value.Value);
                            digestWriter.Write(value.Flags);
                            digestWriter.Write(value.Reserved);
                            digestWriter.Write(value.Payload);
                            if (value.Kind == 1) { begins++; beginsByKind[value.ServiceKindId]++; }
                            if (captured[i].Kind == 3)
                            {
                                fm++;
                                fmByKind[value.ServiceKindId]++;
                                if (ymProjection.Accept(value)) dac++;
                            }
                            if (captured[i].Kind == 4) { psg++; psgByKind[value.ServiceKindId]++; }
                            if (captured[i].Kind == 3 || captured[i].Kind == 4)
                                AssertEx.Equal(true, captured[i].ServiceToken != 0);
                        }
                    }
                }
                digestWriter.Flush();
                string digest;
                using (SHA256 sha = SHA256.Create())
                    digest = BytesToHex(sha.ComputeHash(digestBytes.ToArray()));
                Console.WriteLine("S3K observer bootstrap literal counts: frames=" + frames
                    + " events=" + events + " service_begin=" + begins
                    + " fm=" + fm + " psg=" + psg + " ym_port0_2a_data=" + dac
                    + " max_occupancy=" + maximumOccupancy + " digest=" + digest
                    + " kind3=" + beginsByKind[3] + "/" + fmByKind[3] + "/" + psgByKind[3]
                    + " kind7=" + beginsByKind[7] + "/" + fmByKind[7] + "/" + psgByKind[7]
                    + " kind8=" + beginsByKind[8] + "/" + fmByKind[8] + "/" + psgByKind[8]
                    + " kind11=" + beginsByKind[11] + "/" + fmByKind[11] + "/" + psgByKind[11]
                    + " kind12=" + beginsByKind[12] + "/" + fmByKind[12] + "/" + psgByKind[12]);
                Console.WriteLine("S3K observer capability breakdown: " + breakdown.ToJson().ToString(Newtonsoft.Json.Formatting.None));
                AssertEx.Equal(true, begins > 0);
                AssertEx.Equal(true, fm > 0);
                AssertEx.Equal(true, psg > 0);
                AssertCapability("s3k", events, begins, fm, psg, dac,
                    maximumOccupancy, digest, beginsByKind, fmByKind, psgByKind, breakdown);
            }
        }

        private static string BytesToHex(byte[] bytes)
        {
            char[] chars = new char[bytes.Length * 2];
            const string alphabet = "0123456789abcdef";
            for (int i = 0; i < bytes.Length; i++)
            {
                chars[i * 2] = alphabet[bytes[i] >> 4];
                chars[i * 2 + 1] = alphabet[bytes[i] & 15];
            }
            return new string(chars);
        }

        private static void ObserveS2Bootstrap()
        {
            string rom = Environment.GetEnvironmentVariable("S2_ROM_PATH");
            Bk2Movie movie = OpenReviewedMovie("s2",
                Environment.GetEnvironmentVariable("S2_BK2_PATH"));
            using (var host = GpgxHost.Open(rom, GpgxHost.CreateGhz1SyncSettings()))
            {
                int driverLoadEntries = 0, driverLoadExits = 0, bootstrapEntries = 0, bootstrapExits = 0;
                using (host.RegisterExecuteCallback(0xEC000, () => driverLoadEntries++))
                using (host.RegisterExecuteCallback(0xEC036, () => driverLoadExits++))
                using (host.RegisterExecuteCallback(0x27E, () => bootstrapEntries++))
                using (host.RegisterExecuteCallback(0x288, () => bootstrapExits++))
                {
                var observer = CreateS2Observer(host.CreateAudioTraceApi());
                long begins = 0, fm = 0, psg = 0, dac = 0, events = 0;
                var ymProjection = new YmLatchProjection();
                var breakdown = new CapabilityBreakdown(10);
                var beginsByKind = new long[256];
                var fmByKind = new long[256];
                var psgByKind = new long[256];
                int maximumOccupancy = 0;
                var digestBytes = new MemoryStream();
                var digestWriter = new BinaryWriter(digestBytes);
                var bootstrapPsg = new List<GpgxAudioTraceEvent>();
                int frames = Math.Min(1000, movie.FrameCount);
                using (IEnumerator<Bk2Frame> rows = movie.OpenFrameStream().GetEnumerator())
                {
                    for (int frame = 0; frame < frames; frame++)
                    {
                        AssertEx.Equal(true, rows.MoveNext());
                        S1TraceCaptureRunner.ApplyFrame(rows.Current, host);
                        GpgxAudioTraceEvent[] captured;
                        try { captured = observer.CaptureFrame(host.Advance); }
                        catch (Exception error)
                        {
                            Console.WriteLine("S2 ZRAM hook bytes: 0038=" + Hex(host, 0x38, 4)
                                + " 00e7=" + Hex(host, 0xE7, 4)
                                + " 010f=" + Hex(host, 0x10F, 4)
                                + " 0178=" + Hex(host, 0x178, 4)
                                + " 01b0=" + Hex(host, 0x1B0, 4)
                                + " m68k_ec000=" + driverLoadEntries
                                + " m68k_ec036=" + driverLoadExits
                                + " m68k_27e=" + bootstrapEntries + " m68k_288=" + bootstrapExits);
                            Console.WriteLine("S2 ZRAM sequences: wait=" + Find(host, new byte[] { 0x7A, 0xB3, 0x28, 0xFC })
                                + " entry=" + Find(host, new byte[] { 0xF3, 0x31 })
                                + " dac-write=" + Find(host, new byte[] { 0x32, 0x00, 0x40 })
                                + " around-wait=" + Hex(host, 0x160, 28)
                                + " update-dac=" + Hex(host, 0xC0, 84)
                                + " sega-pcm=" + Hex(host, 0x6E0, 112)
                                + " saxman=" + Hex(host, 0x1260, 176));
                            throw new InvalidOperationException("S2 observer frame " + frame + " failed.", error);
                        }
                        if (captured.Length > maximumOccupancy) maximumOccupancy = captured.Length;
                        digestWriter.Write(frame);
                        digestWriter.Write(captured.Length);
                        events += captured.Length;
                        for (int i = 0; i < captured.Length; i++)
                        {
                            GpgxAudioTraceEvent value = captured[i];
                            breakdown.Accept(value);
                            digestWriter.Write(value.Ordinal);
                            digestWriter.Write(value.ServiceToken);
                            digestWriter.Write(value.ParentToken);
                            digestWriter.Write(value.Pc);
                            digestWriter.Write(value.Subject);
                            digestWriter.Write(value.Offset);
                            digestWriter.Write(value.Kind);
                            digestWriter.Write(value.ServiceKindId);
                            digestWriter.Write(value.Depth);
                            digestWriter.Write(value.SourceCpu);
                            digestWriter.Write(value.PayloadLength);
                            digestWriter.Write(value.Value);
                            digestWriter.Write(value.Flags);
                            digestWriter.Write(value.Reserved);
                            digestWriter.Write(value.Payload);
                            if (value.Kind == 1) { begins++; beginsByKind[value.ServiceKindId]++; }
                            if (value.Kind == 3)
                            {
                                fm++;
                                fmByKind[value.ServiceKindId]++;
                                if (ymProjection.Accept(value)) dac++;
                            }
                            if (value.Kind == 4) { psg++; psgByKind[value.ServiceKindId]++; }
                            if (value.Kind == 4 && value.ServiceKindId == 5)
                                bootstrapPsg.Add(value);
                            if (value.Kind == 3 || value.Kind == 4)
                                AssertEx.Equal(true, value.ServiceToken != 0);
                        }
                    }
                }
                digestWriter.Flush();
                string digest;
                using (SHA256 sha = SHA256.Create())
                    digest = BytesToHex(sha.ComputeHash(digestBytes.ToArray()));
                Console.WriteLine("S2 observer bootstrap literal counts: frames=" + frames
                    + " events=" + events + " service_begin=" + begins
                    + " fm=" + fm + " psg=" + psg + " ym_port0_2a_data=" + dac
                    + " max_occupancy=" + maximumOccupancy + " digest=" + digest
                    + " kind3=" + beginsByKind[3] + "/" + fmByKind[3] + "/" + psgByKind[3]
                    + " kind4=" + beginsByKind[4] + "/" + fmByKind[4] + "/" + psgByKind[4]
                    + " kind7=" + beginsByKind[7] + "/" + fmByKind[7] + "/" + psgByKind[7]
                    + " kind8=" + beginsByKind[8] + "/" + fmByKind[8] + "/" + psgByKind[8]
                    + " kind9=" + beginsByKind[9] + "/" + fmByKind[9] + "/" + psgByKind[9]);
                Console.WriteLine("S2 observer capability breakdown: " + breakdown.ToJson().ToString(Newtonsoft.Json.Formatting.None));
                AssertEx.Equal(true, begins > 0);
                AssertEx.Equal(true, fm > 0);
                AssertEx.Equal(true, psg > 0);
                AssertCapability("s2", events, begins, fm, psg, dac,
                    maximumOccupancy, digest, beginsByKind, fmByKind, psgByKind, breakdown);
                AssertEx.Equal(4, bootstrapPsg.Count);
                byte[] initialPsg = { 0x9F, 0xBF, 0xDF, 0xFF };
                for (int i = 0; i < initialPsg.Length; i++)
                {
                    AssertEx.Equal((byte)2, bootstrapPsg[i].SourceCpu);
                    AssertEx.Equal(0x280u, bootstrapPsg[i].Pc);
                    AssertEx.Equal(initialPsg[i], bootstrapPsg[i].Value);
                }
                }
            }
        }

        private static void AssertCapability(string game, long events, long begins,
            long fm, long psg, long dac, int maximumOccupancy, string digest,
            long[] beginsByKind, long[] fmByKind, long[] psgByKind,
            CapabilityBreakdown breakdown)
        {
            JObject root = JObject.Parse(File.ReadAllText(Path.Combine(
                EndToEndTests.ToolDirectory, "fixtures/gpgx-audio-capability-v1.json")));
            JObject expected = (JObject)root["runs"][game];
            AssertEx.Equal(1000, (int)expected["frames"]);
            AssertEx.Equal((long)expected["events"], events);
            AssertEx.Equal((long)expected["service_begin"], begins);
            AssertEx.Equal((long)expected["fm_write"], fm);
            AssertEx.Equal((long)expected["psg_write"], psg);
            AssertEx.Equal((long)expected["ym_port0_2a_data_write"], dac);
            AssertEx.Equal((int)expected["maximum_frame_occupancy"], maximumOccupancy);
            AssertEx.Equal(0, (int)expected["overflow"]);
            AssertEx.Equal((string)expected["event_digest_sha256"], digest);
            foreach (JProperty property in ((JObject)expected["kind_begin_fm_psg"]).Properties())
            {
                int kind = int.Parse(property.Name);
                JArray vector = (JArray)property.Value;
                AssertEx.Equal((long)vector[0], beginsByKind[kind]);
                AssertEx.Equal((long)vector[1], fmByKind[kind]);
                AssertEx.Equal((long)vector[2], psgByKind[kind]);
            }
            AssertEx.Equal(true, JToken.DeepEquals(expected["breakdown"], breakdown.ToJson()));
        }

        private static string Sha256File(string path)
        {
            using (SHA256 sha = SHA256.Create())
                return BytesToHex(sha.ComputeHash(File.ReadAllBytes(path)));
        }

        private static string Hex(GpgxHost host, int start, int count)
        {
            string value = string.Empty;
            for (int i = 0; i < count; i++) value += host.ReadZ80RamByte(start + i).ToString("x2");
            return value;
        }

        private static string Find(GpgxHost host, byte[] needle)
        {
            var values = new List<string>();
            for (int start = 0; start <= 8192 - needle.Length; start++)
            {
                int i = 0;
                while (i < needle.Length && host.ReadZ80RamByte(start + i) == needle[i]) i++;
                if (i == needle.Length) values.Add(start.ToString("x"));
            }
            return string.Join(",", values.ToArray());
        }

        private static CompleteRunAudioObserver CreateS2Observer(IGpgxAudioTraceApi api)
        {
            return GpgxAudioServiceManifest.Load(Path.Combine(EndToEndTests.ToolDirectory,
                "fixtures/gpgx-audio-service-manifests-v1.json"), "s2", api);
        }


        private static CompleteRunAudioObserver CreateS3kObserver(IGpgxAudioTraceApi api)
        {
            return GpgxAudioServiceManifest.Load(Path.Combine(EndToEndTests.ToolDirectory,
                "fixtures/gpgx-audio-service-manifests-v1.json"), "s3k", api);
        }


    }
}
