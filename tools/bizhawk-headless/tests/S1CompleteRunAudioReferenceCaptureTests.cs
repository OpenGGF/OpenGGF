using System;
using System.Collections.Generic;
using System.IO;
using System.Security.Cryptography;
using BizHawk.Headless.Gpgx;
using Newtonsoft.Json.Linq;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class S1CompleteRunAudioReferenceCaptureTests
    {
        private const string FixtureName = "s1-audio-service-manifest-v1.json";

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests pin every reviewed REV01 boundary",
                PinsReviewedRev01Boundaries));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests reject malformed and mismatched manifests",
                RejectsMalformedAndMismatchedManifest));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests keep retries in one managed service",
                KeepsRetryInOneManagedService));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests resolve adjusted-return exits",
                ResolvesAdjustedReturnExits));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests correlate conditional direct-parent promotion",
                CorrelatesConditionalDirectParentPromotion));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests require direct-parent retry under async PCM",
                RequiresDirectParentRetryUnderAsyncPcm));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests correlate direct-parent retry to managed service",
                CorrelatesDirectParentRetryToManagedService));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests reject orphan close and frame overflow",
                RejectsOrphanCloseAndFrameOverflow));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests stream native DAC in the same frame pass",
                StreamsNativeDacInSameFramePass));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests sample baseline before row 860 and retain empty rows",
                SamplesBaselineBeforeInputAndRetainsEmptyRows));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests carry an open pre-epoch DPCM iteration into row 860",
                CarriesOpenPreEpochDpcmIntoFirstPublishedRow));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests carry a deferred reservation across the epoch boundary",
                CarriesDeferredReservationAcrossEpochBoundary));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests reject corrupt deferred boundary identity",
                RejectsCorruptDeferredBoundaryIdentity));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests publish the epoch exactly once after a drained boundary",
                PublishesEpochExactlyOnceAfterDrainedBoundary));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests correlate requests to later decisions",
                CorrelatesRequestsToLaterDecisions));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests close with full state and native-only chip writes",
                ClosesWithFullStateAndNativeOnlyChipWrites));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests refuse row gaps contamination and open terminal",
                RefusesRowGapsContaminationAndOpenTerminal));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests expose one fixed no-replace CLI mode",
                ExposesFixedNoReplaceCliMode));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests require native markers for cross CPU order",
                RequiresNativeMarkersForCrossCpuOrder));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests reject malformed native correlations",
                RejectsMalformedNativeCorrelations));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests preserve LIFO cross CPU nesting",
                PreservesLifoCrossCpuNesting));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests bind reset evidence to native groups",
                BindsResetEvidenceToNativeGroups));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests use pinned M68K debugger register names",
                UsesPinnedM68kDebuggerRegisterNames));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests report native failure row",
                ReportsNativeFailureRow));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests correlate deferred begin callbacks to one consume",
                CorrelatesDeferredBeginCallbacksToOneConsume));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests reject corrupt deferred begin identity and consume",
                RejectsCorruptDeferredBeginIdentityAndConsume));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests roll back deferred begin publication",
                RollsBackDeferredBeginPublication));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests preserve frame order across deferred consume",
                PreservesFrameOrderAcrossDeferredConsume));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests accept variable deferred observation counts",
                AcceptsVariableDeferredObservationCounts));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests reserve again after deferred child end",
                ReservesAgainAfterDeferredChildEnd));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests observe ordinary driverinput entry without consuming",
                ObservesOrdinaryDriverInputEntryWithoutConsuming));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests reject invalid driverinput ownership",
                RejectsInvalidDriverInputOwnership));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests correlate promoted managed identity in both epochs",
                CorrelatesPromotedManagedIdentityInBothEpochs));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests enforce managed token A7 and cutoff set identity",
                EnforcesManagedTokenA7AndCutoffSetIdentity));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests carry promoted managed identity across epoch",
                CarriesPromotedManagedIdentityAcrossEpoch));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests cancel and roll back promoted managed identity",
                CancelsAndRollsBackPromotedManagedIdentity));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests reject boundary retry token A7 changes",
                RejectsBoundaryRetryTokenA7Changes));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests reject native-valid cross-lifetime observations",
                RejectsNativeValidCrossLifetimeObservations));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests track a deferred child wholly before the epoch",
                TracksDeferredChildWhollyBeforeEpoch));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests carry a consumed deferred child across the epoch",
                CarriesConsumedDeferredChildAcrossEpoch));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests cancel boundary deferred child on reset",
                CancelsBoundaryDeferredChildOnReset));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests consume one deferred child begin during row 8775 wait service",
                MaterializesDeferredBeginAfterWaitService,
                game: "s1", serial: true, estimatedSeconds: 300.0));
        }

        private static void MaterializesDeferredBeginAfterWaitService()
        {
            if (Environment.GetEnvironmentVariable("OPENGGF_S1_AUDIO_PREFIX") != "1")
                throw new TestMain.SkipTestException("OPENGGF_S1_AUDIO_PREFIX is not enabled.");
            bool terminalProbe=Environment.GetEnvironmentVariable(
                "OPENGGF_S1_AUDIO_TERMINAL_PROBE")=="1";
            string romPath = Environment.GetEnvironmentVariable("S1_ROM_PATH");
            string moviePath = Environment.GetEnvironmentVariable("S1_AUDIO_BK2_PATH");
            if (string.IsNullOrEmpty(romPath) || !File.Exists(romPath))
                throw new InvalidOperationException("S1_ROM_PATH is required for the real audio prefix.");
            if (string.IsNullOrEmpty(moviePath) || !File.Exists(moviePath))
                throw new InvalidOperationException("S1_AUDIO_BK2_PATH is required for the real audio prefix.");
            AssertEx.Equal("f2e817936d07b2b1f2b80d61451f174189509a2817da2b2349ce0e19b8a5567b",
                Sha256(moviePath));
            Bk2Movie movie = Bk2Reader.Read(moviePath);
            AssertEx.Equal(225101, movie.FrameCount);
            byte[] rom = File.ReadAllBytes(romPath);
            string manifestPath = Path.Combine(EndToEndTests.ToolDirectory, "fixtures", FixtureName);
            S1CompleteRunAudioReferenceCapture.Manifest manifest =
                S1CompleteRunAudioReferenceCapture.LoadManifest(manifestPath, rom);
            var output = new StringWriter();
            using (var host = GpgxHost.Open(romPath, movie.SyncSettings))
            using (IEnumerator<Bk2Frame> frames = movie.OpenFrameStream().GetEnumerator())
            using (var session = new S1CompleteRunAudioReferenceCapture.Session(
                host, host, host.CreateAudioTraceApi(), manifest, output))
            {
                for (int row=0;row<manifest.FirstRow;row++)
                {
                    AssertEx.Equal(true, frames.MoveNext());
                    Bk2Frame frame = frames.Current;
                    int observed = row;
                    session.ObservePreEpochFrame(observed, frame, () =>
                    {
                        S1TraceCaptureRunner.ApplyFrame(frame, host);
                        host.Advance();
                    });
                }
                session.BeginEpoch();
                int finalRow=terminalProbe?manifest.ExclusiveEnd-1:8775;
                for (int row=manifest.FirstRow;row<=finalRow;row++)
                {
                    AssertEx.Equal(true, frames.MoveNext());
                    Bk2Frame frame=frames.Current;
                    int captured=row;
                    session.CaptureFrame(captured,frame,() =>
                    {
                        S1TraceCaptureRunner.ApplyFrame(frame,host);
                        host.Advance();
                    });
                }
                session.Complete(finalRow+1);
            }
            JObject baseline = null;
            JObject directParentRetry = null;
            ushort retryToken=0;
            ushort deferredHookToken=0;
            ushort consumeHookToken=0;
            foreach (GpgxAudioObserverAdapter.ServiceHook hook
                in manifest.NativeServiceHooks)
            {
                if (hook.Cpu==2&&hook.Pc==0x071B4C&&hook.Action==10
                    &&hook.ExpectedActiveKind==2) retryToken=hook.HookToken;
                if(hook.Cpu==2&&hook.Pc==0x071B4C&&hook.Action==11
                    &&hook.ExpectedActiveKind==6)
                    deferredHookToken=hook.HookToken;
                if(hook.Cpu==2&&hook.Pc==0x071B82&&hook.Action==12
                    &&hook.ExpectedActiveKind==6)
                    consumeHookToken=hook.HookToken;
            }
            uint waitBeginOrdinal=uint.MaxValue;
            uint waitEndOrdinal=uint.MaxValue;
            uint priorMusicEndOrdinal=uint.MaxValue;
            uint consumeBeginOrdinal=uint.MaxValue;
            uint childObservationOrdinal=uint.MaxValue;
            uint childEndOrdinal=uint.MaxValue;
            ushort waitServiceToken=0;
            ushort consumedServiceToken=0;
            int deferredBegins=0;
            var deferredEvidence=new List<JObject>();
            foreach (string line in output.ToString().Split(new[]{'\n'},
                StringSplitOptions.RemoveEmptyEntries))
            {
                JObject record = JObject.Parse(line);
                if ((string)record["type"] == "baseline") baseline = record;
                if ((string)record["type"]=="native_event"
                    &&(int)record["row"]==8775&&(int)record["kind"]==2
                    &&(int)record["pc"]==0x071C4C
                    &&(int)record["service_kind"]==4
                    &&priorMusicEndOrdinal==uint.MaxValue)
                    priorMusicEndOrdinal=(uint)record["ordinal"];
                if ((string)record["type"]=="native_event"
                    &&(int)record["row"]==8775&&(int)record["kind"]==1
                    &&(int)record["pc"]==0x003A
                    &&(int)record["service_kind"]==6)
                {
                    waitBeginOrdinal=(uint)record["ordinal"];
                    waitServiceToken=(ushort)record["service_token"];
                    AssertEx.Equal(0,(int)record["parent_token"]);
                    AssertEx.Equal(0,(int)record["depth"]);
                }
                if ((string)record["type"]=="native_event"
                    &&(int)record["row"]==1548&&(int)record["kind"]==10
                    &&(int)record["subject"]==retryToken
                    &&(int)record["value"]==2) directParentRetry=record;
                if ((string)record["type"]=="native_event"
                    &&(int)record["row"]==8775&&(int)record["kind"]==2
                    &&(int)record["service_kind"]==6)
                {
                    waitEndOrdinal=(uint)record["ordinal"];
                    AssertEx.Equal(waitServiceToken,
                        (ushort)record["service_token"]);
                    AssertEx.Equal(0x0077,(int)record["pc"]);
                    AssertEx.Equal(1,(int)record["source_cpu"]);
                    AssertEx.Equal(0,(int)record["parent_token"]);
                    AssertEx.Equal(0,(int)record["depth"]);
                }
                if ((string)record["type"]=="native_event"
                    &&(int)record["row"]==8775&&(int)record["kind"]==1
                    &&(int)record["pc"]==0x071B82
                    &&(int)record["service_kind"]==4)
                {
                    deferredBegins++;
                    consumeBeginOrdinal=(uint)record["ordinal"];
                    consumedServiceToken=(ushort)record["service_token"];
                    AssertEx.Equal(waitServiceToken,(ushort)record["parent_token"]);
                    AssertEx.Equal(1,(int)record["depth"]);
                    AssertEx.Equal(2,(int)record["source_cpu"]);
                    AssertEx.Equal(consumeHookToken,
                        (ushort)record["subject"]);
                }
                if((string)record["type"]=="native_event"
                    &&(int)record["row"]==8775&&(int)record["kind"]==10
                    &&(int)record["pc"]==0x071BB2)
                {
                    childObservationOrdinal=(uint)record["ordinal"];
                    AssertEx.Equal(consumedServiceToken,
                        (ushort)record["service_token"]);
                    AssertEx.Equal(waitServiceToken,
                        (ushort)record["parent_token"]);
                    AssertEx.Equal(4,(int)record["service_kind"]);
                    AssertEx.Equal(1,(int)record["depth"]);
                }
                if((string)record["type"]=="native_event"
                    &&(int)record["row"]==8775&&(int)record["kind"]==2
                    &&(int)record["pc"]==0x071C4C
                    &&(int)record["service_kind"]==4
                    &&(ushort)record["parent_token"]==waitServiceToken)
                    childEndOrdinal=(uint)record["ordinal"];
                if((string)record["type"]=="managed_hook_evidence"
                    &&(int)record["row"]==8775
                    &&(int)record["pc"]==0x071B4C)
                    deferredEvidence.Add(record);
            }
            AssertEx.Equal(true, baseline != null);
            AssertEx.Equal(860, (int)baseline["row"]);
            AssertEx.Equal(true, ((JArray)baseline["active_services"]).Count > 0);
            AssertEx.Equal(true,directParentRetry!=null);
            AssertEx.Equal(4,(int)directParentRetry["service_kind"]);
            AssertEx.Equal(0,(int)directParentRetry["depth"]);
            AssertEx.Equal((uint)12,priorMusicEndOrdinal);
            AssertEx.Equal((uint)13,waitBeginOrdinal);
            AssertEx.Equal(3,deferredEvidence.Count);
            var markerOrdinals=new List<uint>();
            for(int i=0;i<deferredEvidence.Count;i++)
            {
                JArray chain=(JArray)deferredEvidence[i]["native_correlation_events"];
                AssertEx.Equal(1,chain.Count);
                uint markerOrdinal=(uint)chain[0]["ordinal"];
                AssertEx.Equal(true,markerOrdinal>waitBeginOrdinal
                    &&markerOrdinal<waitEndOrdinal);
                AssertEx.Equal(false,markerOrdinals.Contains(markerOrdinal));
                if(markerOrdinals.Count!=0)
                    AssertEx.Equal(true,
                        markerOrdinals[markerOrdinals.Count-1]<markerOrdinal);
                markerOrdinals.Add(markerOrdinal);
                AssertEx.Equal(10,(int)chain[0]["event_kind"]);
                AssertEx.Equal(4,(int)chain[0]["value"]);
                AssertEx.Equal(4,(int)deferredEvidence[i]["native_marker_value"]);
                AssertEx.Equal("update_begin",(string)deferredEvidence[i]["name"]);
                AssertEx.Equal("SERVICE_BEGIN",(string)deferredEvidence[i]["action"]);
                AssertEx.Equal(deferredHookToken,
                    (ushort)deferredEvidence[i]["native_hook_token"]);
                AssertEx.Equal(waitServiceToken,
                    (ushort)deferredEvidence[i]["native_service_token"]);
                AssertEx.Equal((ushort)0,
                    (ushort)deferredEvidence[i]["native_parent_token"]);
                AssertEx.Equal(waitServiceToken,(ushort)chain[0]["service_token"]);
                AssertEx.Equal((ushort)0,(ushort)chain[0]["parent_token"]);
                AssertEx.Equal(deferredHookToken,(ushort)chain[0]["hook_token"]);
                AssertEx.Equal(6,(int)chain[0]["service_kind"]);
                AssertEx.Equal(0,(int)chain[0]["depth"]);
                AssertEx.Equal(2,(int)chain[0]["source_cpu"]);
                AssertEx.Equal((uint)0x00FFFDB2,
                    (uint)deferredEvidence[i]["deferred_a7"]);
                AssertEx.Equal((uint)0x00000B64,
                    (uint)deferredEvidence[i]["deferred_return_pc"]);
                AssertEx.Equal(consumeHookToken,
                    (ushort)deferredEvidence[i]["consume_hook_token"]);
                AssertEx.Equal((uint)0x071B82,
                    (uint)deferredEvidence[i]["consume_pc"]);
                AssertEx.Equal(consumedServiceToken,
                    (ushort)deferredEvidence[i]["consumed_service_token"]);
                AssertEx.Equal(consumeBeginOrdinal,
                    (uint)deferredEvidence[i]["consume_begin_ordinal"]);
            }
            AssertEx.Equal(1,deferredBegins);
            AssertEx.Equal(true,consumeBeginOrdinal<childObservationOrdinal
                &&childObservationOrdinal<childEndOrdinal
                &&childEndOrdinal<waitEndOrdinal);
            AssertEx.Equal(true,consumedServiceToken!=0);
            AssertEx.Equal(false,consumedServiceToken==waitServiceToken);
        }

        private static string Sha256(string path)
        {
            using (SHA256 value = SHA256.Create())
            using (FileStream stream = File.OpenRead(path))
                return BitConverter.ToString(value.ComputeHash(stream)).Replace("-", "").ToLowerInvariant();
        }

        private static void PinsReviewedRev01Boundaries()
        {
            byte[] rom = RomForManifest(FixturePath());
            S1CompleteRunAudioReferenceCapture.Manifest manifest =
                S1CompleteRunAudioReferenceCapture.LoadManifest(
                    FixturePath(), rom);

            AssertEx.Equal(860, manifest.FirstRow);
            AssertEx.Equal(225101, manifest.ExclusiveEnd);
            AssertEx.Equal(RomIdentity.Sonic1Rev01Sha1, manifest.RomSha1);
            AssertHook(manifest, 0x00138E, "11c0f00a", "QueueSound1", "REQUEST_QUEUE_0");
            AssertHook(manifest, 0x001394, "11c0f00b", "QueueSound2", "REQUEST_QUEUE_1");
            AssertHook(manifest, 0x00139A, "11c0f00c", "QueueSound3", "REQUEST_QUEUE_2");
            AssertHook(manifest, 0x071B4C, "33fc010000a11100", "UpdateMusic", "SERVICE_BEGIN");
            AssertHook(manifest, 0x071B82, "4df900fff000", ".driverinput",
                "DEFERRED_SERVICE_CONSUME");
            AssertObservationKinds(manifest, 0x071BB2, 2, 3, 4);
            AssertDeferredBeginHooks(manifest);
            AssertHook(manifest, 0x071FD2, "0c070088", "PlaySoundID", "BGM_CANDIDATE");
            AssertHook(manifest, 0x072098, "08d10007", ".bgm_fmloadloop", "LOAD_FM_DAC");
            AssertHook(manifest, 0x072126, "08d10007", ".bgm_psgloadloop", "LOAD_PSG");
            AssertHook(manifest, 0x071CA4, "13c000a01fff", "DACUpdateTrack", "DAC_COMMAND");
            AssertHook(manifest, 0x0720C4, "0c2b00070002", ".silencefm6", "FM_DAC_MODE_TEST");
            AssertHook(manifest, 0x0720CC, "702b7200", ".silencefm6", "DAC_DISABLE" );
            AssertHook(manifest, 0x0720D8, "70287206", ".silencefm6", "FM6_SILENCE" );
            AssertHook(manifest, 0x0721C6, "4a2e0027", "Sound_ChkValue", "NORMAL_CANDIDATE");
            AssertHook(manifest, 0x0721F4, "0c0700a7", "Sound_ChkValue", "NORMAL_REWRITTEN");
            AssertHook(manifest, 0x07222E, "1803", ".sfx_loadloop", "NORMAL_ROLE");
            AssertHook(manifest, 0x07227C, "3a99", ".clearsfxtrackram", "NORMAL_INIT");
            AssertHook(manifest, 0x07230C, "4a2e0027", "Sound_ChkValue", "SPECIAL_CANDIDATE");
            AssertHook(manifest, 0x07234C, "6b0c", ".sfxloadloop", "SPECIAL_ROLE");
            AssertHook(manifest, 0x07236E, "3a99", ".clearsfxtrackram", "SPECIAL_INIT");
            AssertHook(manifest, 0x0721CA, "660000fa", "Sound_PlaySFX", "NORMAL_BLOCK_ONEUP");
            AssertHook(manifest, 0x0721CE, "4a2e0004", "Sound_PlaySFX", "NORMAL_BLOCK_FADEOUT_TEST");
            AssertHook(manifest, 0x0721D2, "660000f2", "Sound_PlaySFX", "NORMAL_BLOCK_FADEOUT");
            AssertHook(manifest, 0x0721D6, "4a2e0024", "Sound_PlaySFX", "NORMAL_BLOCK_FADEIN_TEST");
            AssertHook(manifest, 0x0721DA, "660000ea", "Sound_PlaySFX", "NORMAL_BLOCK_FADEIN");
            AssertHook(manifest, 0x0722C6, "422e0000", "Sound_PlaySFX", "NORMAL_BLOCK_EXIT");
            AssertHook(manifest, 0x072310, "660000b4", "Sound_PlaySpecial", "SPECIAL_BLOCK_ONEUP");
            AssertHook(manifest, 0x072314, "4a2e0004", "Sound_PlaySpecial", "SPECIAL_BLOCK_FADEOUT_TEST");
            AssertHook(manifest, 0x072318, "660000ac", "Sound_PlaySpecial", "SPECIAL_BLOCK_FADEOUT");
            AssertHook(manifest, 0x07231C, "4a2e0024", "Sound_PlaySpecial", "SPECIAL_BLOCK_FADEIN_TEST");
            AssertHook(manifest, 0x072320, "660000a4", "Sound_PlaySpecial", "SPECIAL_BLOCK_FADEIN");
            AssertHook(manifest, 0x0723C6, "4e75", "Sound_PlaySpecial", "SPECIAL_BLOCK_EXIT");
            AssertHook(manifest, 0x071C4C, "4e75", "UpdateMusic", "SERVICE_CLOSE");
            AssertCloseAlternatives(manifest, 0x071C4C);
            AssertHook(manifest, 0x071FD0, "4e75", "PlaySegaSound", "SERVICE_CLOSE");
            AssertHook(manifest, 0x0721B8, "4e75", "Sound_PlayBGM", "SERVICE_CLOSE");
            AssertHook(manifest, 0x072B9C, "4e75", "cfFadeInToPrevious", "SERVICE_CLOSE");
            AssertHook(manifest, 0x072C24, "4e75", "cfStopSpecialFM4", "CLOSE_IF_RETURN_OUTSIDE");
            AssertHook(manifest, 0x072E04, "4e75", "cfStopTrack", "CLOSE_IF_RETURN_OUTSIDE");
            AssertConditionalCloseAlternatives(manifest, 0x072E04);
            AssertNative(manifest, 0x003A, "d681", "zCheckForSamples", "PUSH_BEGIN");
            AssertNative(manifest, 0x0077, "1a", "zPlayPCMLoop", "PUSH_BEGIN");
            AssertNative(manifest, 0x0077, "1a", "zPlayPCMLoop", "TAIL_POP_PUSH");
            AssertNative(manifest, 0x00AC, "c23200", "zPlayPCMLoop", "POP_END_AT_PC");
            AssertNative(manifest, 0x00C1, "1a", "zPlaySEGAPCMLoop", "PUSH_BEGIN");
            AssertNative(manifest, 0x00D0, "c2c100", "zPlaySEGAPCMLoop", "POP_END_AT_PC");
            AssertNative(manifest, 0x00134A, "4e71", "DACDriverLoad", "PUSH_BEGIN");
            AssertNative(manifest, 0x00138C, "4e75", "DACDriverLoad", "POP_END_AT_PC");
        }

        private static void AssertDeferredBeginHooks(
            S1CompleteRunAudioReferenceCapture.Manifest manifest)
        {
            int reserve=0,consume=0;var ordinary=new List<byte>();
            var ordinaryEntry=new List<byte>();
            foreach(GpgxAudioObserverAdapter.ServiceHook hook in manifest.NativeServiceHooks)
            {
                if(hook.Cpu!=2)continue;
                if(hook.Pc==0x071B4C&&hook.Action==11)
                {
                    reserve++;
                    AssertEx.Equal((byte)4,hook.ServiceKindId);
                    AssertEx.Equal((byte)6,hook.ExpectedActiveKind);
                    AssertEx.Equal((ushort)0,hook.RangeCount);
                    AssertEx.Equal((ulong)0,hook.Reserved);
                }
                else if(hook.Pc==0x071B82&&hook.Action==12)
                {
                    consume++;
                    AssertEx.Equal(true,object.ReferenceEquals(
                        manifest.FindManagedHook(0x071B82),
                        manifest.ManagedByNativeToken[hook.HookToken]));
                    AssertEx.Equal((byte)4,hook.ServiceKindId);
                    AssertEx.Equal((byte)6,hook.ExpectedActiveKind);
                    AssertEx.Equal((ushort)0,hook.RangeCount);
                    AssertEx.Equal((ulong)0,hook.Reserved);
                }
                else if(hook.Pc==0x071B82&&hook.Action==7)
                {
                    ordinaryEntry.Add(hook.ExpectedActiveKind);
                    AssertEx.Equal(true,object.ReferenceEquals(
                        manifest.FindManagedHook(0x071B82),
                        manifest.ManagedByNativeToken[hook.HookToken]));
                    AssertEx.Equal((byte)0,hook.ServiceKindId);
                    AssertEx.Equal((ushort)0,hook.RangeCount);
                    AssertEx.Equal((ulong)0,hook.Reserved);
                }
                else if(hook.Pc==0x071B4C&&hook.Action==1)
                    ordinary.Add(hook.ExpectedActiveKind);
            }
            ordinary.Sort();
            AssertEx.Equal(1,reserve);
            AssertEx.Equal(1,consume);
            ordinaryEntry.Sort();
            AssertEx.Equal(3,ordinaryEntry.Count);
            AssertEx.Equal((byte)2,ordinaryEntry[0]);
            AssertEx.Equal((byte)3,ordinaryEntry[1]);
            AssertEx.Equal((byte)4,ordinaryEntry[2]);
            AssertEx.Equal(3,ordinary.Count);
            AssertEx.Equal((byte)0,ordinary[0]);
            AssertEx.Equal((byte)2,ordinary[1]);
            AssertEx.Equal((byte)3,ordinary[2]);
            GpgxAudioObserverAdapter.ServiceKind blocker=Array.Find(
                manifest.NativeKinds,value=>value.KindId==6);
            AssertEx.Equal((byte)3,blocker.Flags);
        }

        private static void CorrelatesDeferredBeginCallbacksToOneConsume()
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                Visit(current,api,0x071B4C);
                Visit(current,api,0x071C4C);
                api.VisitZ80(0x003A,current);
                Visit(current,api,0x071B4C);
                Visit(current,api,0x071B4C);
                Visit(current,api,0x071B4C);
                Visit(current,api,0x071B82);
                Visit(current,api,0x071BB2);
                api.EmitChip(3,0,0x2A,0x071BB6);
                Visit(current,api,0x071C4C);
                api.VisitZ80(0x0077,current);
                api.VisitZ80(0x00AC,current);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.CaptureFrame(860,host.Advance);
                session.Complete(861);
            }
            string raw=output.ToString();
            AssertEx.Equal(3,CountNativeMarkerValue(raw,4));
            List<JObject> deferredEvidence=Records(raw,"managed_hook_evidence",
                value=>value["native_marker_value"]!=null
                    &&value["native_marker_value"].Type==JTokenType.Integer
                    &&(int)value["native_marker_value"]==4);
            AssertEx.Equal(3,deferredEvidence.Count);
            for(int i=0;i<3;i++)
            {
                JObject evidence=deferredEvidence[i];
                JArray chain=(JArray)evidence["native_correlation_events"];
                AssertEx.Equal(1,chain.Count);
                AssertEx.Equal(4,(int)chain[0]["value"]);
                AssertEx.Equal(true,(bool)chain[0]["terminal"]);
                AssertEx.Equal((uint)0xFFFDB2,(uint)evidence["deferred_a7"]);
                AssertEx.Equal((uint)0x00000B64,(uint)evidence["deferred_return_pc"]);
                AssertEx.Equal((uint)0x071B82,(uint)evidence["consume_pc"]);
                AssertEx.Equal(true,(ushort)evidence["consumed_service_token"]!=0);
            }
            List<JObject> native=NativeRecords(raw,860);
            int consume=FindNative(native,value=>(int)value["kind"]==1
                &&(int)value["service_kind"]==4&&(uint)value["pc"]==0x071B82);
            ushort blocker=(ushort)native[consume]["parent_token"];
            ushort child=(ushort)native[consume]["service_token"];
            AssertEx.Equal((ushort)2,blocker);
            AssertEx.Equal(1,(int)native[consume]["depth"]);
            int observation=FindNative(native,value=>(int)value["kind"]==10
                &&(uint)value["pc"]==0x071BB2);
            int chip=FindNative(native,value=>(int)value["kind"]==3
                &&(uint)value["pc"]==0x071BB6);
            int childEnd=FindNative(native,value=>(int)value["kind"]==2
                &&(int)value["service_kind"]==4&&(uint)value["pc"]==0x071C4C
                &&(ushort)value["parent_token"]==blocker);
            int blockerEnd=FindNative(native,value=>(int)value["kind"]==2
                &&(int)value["service_kind"]==6);
            AssertEx.Equal(true,consume<observation&&observation<chip
                &&chip<childEnd&&childEnd<blockerEnd);
            AssertEx.Equal(child,(ushort)native[observation]["service_token"]);
            AssertEx.Equal(child,(ushort)native[chip]["service_token"]);
            AssertEx.Equal(blocker,(ushort)native[observation]["parent_token"]);
            AssertEx.Equal(blocker,(ushort)native[chip]["parent_token"]);
        }

        private static void RejectsCorruptDeferredBeginIdentityAndConsume()
        {
            AssertDeferredCaptureFails((current,api,index)=>
            {
                if(index==1)current.SetCpuRegister("A7",0x00FFFDB6);
            },"identity");
            AssertDeferredCaptureFails((current,api,index)=>
            {
                if(index==1)current.SetU32(0xFDB2,0x00000B68);
            },"identity");
            AssertDeferredConsumeFails((current,api)=>
                current.SetCpuRegister("A7",0x00FFFDB6),"identity");
            AssertDeferredConsumeFails((current,api)=>
                current.SetU32(0xFDB2,0x00000B68),"identity");
            AssertDeferredConsumeEventFails(api=>api.RemoveFirst(value=>
                value.Kind==1&&value.Pc==0x071B82),"managed");
            AssertDeferredConsumeEventFails(api=>api.DuplicateLast(),"invalid");
        }

        private static void AssertDeferredConsumeEventFails(
            Action<FakeTraceApi> corrupt,string message)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                api.VisitZ80(0x003A,current);
                Visit(current,api,0x071B4C);
                Visit(current,api,0x071B82);
                corrupt(api);
            });
            using(var session=CreateSession(host,api,new StringWriter()))
                AssertEx.Throws<InvalidOperationException>(
                    ()=>session.CaptureFrame(860,host.Advance),message);
        }

        private static void AssertDeferredConsumeFails(
            Action<FakeS1Host,FakeTraceApi> beforeConsume,string message)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                api.VisitZ80(0x003A,current);
                Visit(current,api,0x071B4C);
                beforeConsume(current,api);
                Visit(current,api,0x071B82);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.BeginEpoch();
                int baselineLength=output.ToString().Length;
                AssertEx.Throws<InvalidOperationException>(
                    ()=>session.CaptureFrame(860,host.Advance),message);
                AssertEx.Equal(baselineLength,output.ToString().Length);
            }
        }

        private static void AssertDeferredCaptureFails(
            Action<FakeS1Host,FakeTraceApi,int> beforeCallback,string message)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                api.VisitZ80(0x003A,current);
                for(int i=0;i<3;i++)
                {
                    beforeCallback(current,api,i);
                    Visit(current,api,0x071B4C);
                }
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.BeginEpoch();
                int baselineLength=output.ToString().Length;
                AssertEx.Throws<InvalidOperationException>(
                    ()=>session.CaptureFrame(860,host.Advance),message);
                AssertEx.Equal(baselineLength,output.ToString().Length);
            }
        }

        private static void RollsBackDeferredBeginPublication()
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                if(frame==1)
                {
                    api.VisitZ80(0x003A,current);
                    Visit(current,api,0x071B4C);
                    Visit(current,api,0x071B4C);
                    Visit(current,api,0x071B4C);
                    return;
                }
                Visit(current,api,0x071B82);
                api.MutateLast(value=>{value.ParentToken=0;return value;});
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.CaptureFrame(860,host.Advance);
                AssertEx.Equal(3,session.PendingDeferredObservationCountForTesting);
                int published=output.ToString().Length;
                AssertEx.Throws<InvalidOperationException>(
                    ()=>session.CaptureFrame(861,host.Advance),"deferred");
                AssertEx.Equal(published,output.ToString().Length);
                AssertEx.Equal(3,session.PendingDeferredObservationCountForTesting);
            }
        }

        private static void PreservesFrameOrderAcrossDeferredConsume()
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                if(frame==1)
                {
                    api.VisitZ80(0x003A,current);
                    Visit(current,api,0x071B4C);
                    return;
                }
                Visit(current,api,0x071B4C);
                Visit(current,api,0x071B82);
                Visit(current,api,0x071C4C);
                api.VisitZ80(0x0077,current);
                api.VisitZ80(0x00AC,current);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.CaptureFrame(860,host.Advance);
                AssertEx.Equal(0,CountRecords(output.ToString(),"frame_begin"));
                session.CaptureFrame(861,host.Advance);
                session.Complete(862);
            }
            string raw=output.ToString();
            int begin860=raw.IndexOf("\"type\":\"frame_begin\",\"row\":860",
                StringComparison.Ordinal);
            int evidence860=raw.IndexOf("\"type\":\"managed_hook_evidence\",\"row\":860",
                StringComparison.Ordinal);
            int end860=raw.IndexOf("\"type\":\"frame_end\",\"row\":860",
                StringComparison.Ordinal);
            int begin861=raw.IndexOf("\"type\":\"frame_begin\",\"row\":861",
                StringComparison.Ordinal);
            int evidence861=raw.IndexOf("\"type\":\"managed_hook_evidence\",\"row\":861",
                StringComparison.Ordinal);
            int end861=raw.IndexOf("\"type\":\"frame_end\",\"row\":861",
                StringComparison.Ordinal);
            AssertEx.Equal(true,begin860>=0&&begin860<evidence860
                &&evidence860<end860&&end860<begin861
                &&begin861<evidence861&&evidence861<end861);
            AssertEx.Equal(2,Records(raw,"managed_hook_evidence",
                value=>value["native_marker_value"]!=null
                    &&value["native_marker_value"].Type==JTokenType.Integer
                    &&(int)value["native_marker_value"]==4).Count);
        }

        private static void AcceptsVariableDeferredObservationCounts()
        {
            AssertDeferredObservationCount(1);
            AssertDeferredObservationCount(4);
        }

        private static void ReservesAgainAfterDeferredChildEnd()
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                if(frame==1)
                {
                    api.VisitZ80(0x003A,current);
                    Visit(current,api,0x071B4C);
                    Visit(current,api,0x071B82);
                    Visit(current,api,0x071C4C);
                    Visit(current,api,0x071B4C);
                    return;
                }
                Visit(current,api,0x071B82);
                Visit(current,api,0x071C4C);
                api.VisitZ80(0x0077,current);
                api.VisitZ80(0x00AC,current);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.CaptureFrame(860,host.Advance);
                AssertEx.Equal(1,session.PendingDeferredObservationCountForTesting);
                session.CaptureFrame(861,host.Advance);
                session.Complete(862);
            }
            AssertEx.Equal(2,CountNativeMarkerValue(output.ToString(),4));
            AssertEx.Equal(2,Records(output.ToString(),"managed_hook_evidence",
                value=>value["native_marker_value"]!=null
                    &&value["native_marker_value"].Type==JTokenType.Integer
                    &&(int)value["native_marker_value"]==4).Count);
        }

        private static void ObservesOrdinaryDriverInputEntryWithoutConsuming()
        {
            AssertOrdinaryDriverInputEntry(0,0,4,false);
            AssertOrdinaryDriverInputEntry(0x0077,0x00AC,2,false);
            AssertOrdinaryDriverInputEntry(0x00C1,0x00D0,3,false);
            AssertOrdinaryDriverInputEntry(0x0077,0x00AC,2,true);
        }

        private static void AssertOrdinaryDriverInputEntry(uint childBegin,
            uint childEnd,byte observedKind,bool preEpoch)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                Visit(current,api,0x071B4C);
                if(childBegin!=0)api.VisitZ80(childBegin,current);
                Visit(current,api,0x071B82);
                if(childEnd!=0)api.VisitZ80(childEnd,current);
                Visit(current,api,0x071C4C);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                if(preEpoch)
                {
                    session.ObservePreEpochFrame(859,null,host.Advance);
                    AssertEx.Equal(0,output.ToString().Length);
                    session.BeginEpoch();
                    AssertEx.Equal(0,CountRecords(output.ToString(),"native_event"));
                    AssertEx.Equal(0,CountRecords(output.ToString(),
                        "managed_hook_evidence"));
                    return;
                }
                session.CaptureFrame(860,host.Advance);
                session.Complete(861);
            }
            string raw=output.ToString();
            AssertEx.Equal(1,CountNativeMarkerValue(raw,3));
            AssertEx.Equal(childBegin==0?1:2,CountNativeKind(raw,1));
            AssertEx.Equal(childEnd==0?1:2,CountNativeKind(raw,2));
            AssertEx.Equal(0,CountNativeMarkerValue(raw,4));
            JObject evidence=Records(raw,"managed_hook_evidence",
                value=>(uint)value["pc"]==0x071B82)[0];
            JArray correlation=(JArray)evidence["native_correlation_events"];
            AssertEx.Equal(1,correlation.Count);
            AssertEx.Equal(10,(int)correlation[0]["event_kind"]);
            AssertEx.Equal(3,(int)correlation[0]["value"]);
            AssertEx.Equal(observedKind,(byte)correlation[0]["service_kind"]);
            AssertEx.Equal(observedKind==4?0:1,
                (int)correlation[0]["depth"]);
        }

        private static void RejectsInvalidDriverInputOwnership()
        {
            AssertDriverInputVisitFails((current,api)=>
                api.VisitZ80(0x0077,current),"identity");
            AssertDriverInputVisitFails((current,api)=>
                api.VisitZ80(0x003A,current),"reservation");
            AssertDriverInputManagedIdentityFails();
        }

        private static void AssertDriverInputManagedIdentityFails()
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                Visit(current,api,0x071B4C);
                api.VisitZ80(0x0077,current);
                current.SetCpuRegister("A7",0x00FFFDB6);
                Visit(current,api,0x071B82);
            });
            using(var session=CreateSession(host,api,new StringWriter()))
                AssertEx.Throws<InvalidOperationException>(
                    ()=>session.CaptureFrame(860,host.Advance),"identity");
        }

        private static void AssertDriverInputVisitFails(
            Action<FakeS1Host,FakeTraceApi> begin,string message)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                begin(current,api);
                Visit(current,api,0x071B82);
            });
            using(var session=CreateSession(host,api,new StringWriter()))
                AssertEx.Throws<InvalidOperationException>(
                    ()=>session.CaptureFrame(860,host.Advance),message);
        }

        private static void CorrelatesPromotedManagedIdentityInBothEpochs()
        {
            AssertPromotedManagedIdentity(true);
            AssertPromotedManagedIdentity(false);
            AssertPromotedDirectChildIdentity(0x0077,0x00AC,2,true);
            AssertPromotedDirectChildIdentity(0x0077,0x00AC,2,false);
            AssertPromotedDirectChildIdentity(0x00C1,0x00D0,3,true);
            AssertPromotedDirectChildIdentity(0x00C1,0x00D0,3,false);
        }

        private static void AssertPromotedManagedIdentity(bool preEpoch)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDAE);
                current.SetU32(0xFDAE,0x00000B64);
                api.VisitZ80(0x0077,current);
                Visit(current,api,0x071B4C);
                api.VisitZ80(0x00AC,current);
                Visit(current,api,0x071B82);
                Visit(current,api,0x071C4C);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                if(preEpoch)
                {
                    session.ObservePreEpochFrame(859,null,host.Advance);
                    AssertEx.Equal(0,output.ToString().Length);
                    AssertEx.Equal(0,session.BoundaryManagedServiceCountForTesting);
                    session.BeginEpoch();
                    AssertEx.Equal(0,CountRecords(output.ToString(),"native_event"));
                    AssertEx.Equal(0,CountRecords(output.ToString(),
                        "managed_hook_evidence"));
                    return;
                }
                session.CaptureFrame(860,host.Advance);
                session.Complete(861);
            }
            string raw=output.ToString();
            AssertEx.Equal(1,CountNativeKind(raw,11));
            JObject observation=Records(raw,"managed_hook_evidence",
                value=>(uint)value["pc"]==0x071B82)[0];
            JArray correlation=(JArray)observation["native_correlation_events"];
            AssertEx.Equal(1,correlation.Count);
            AssertEx.Equal(4,(int)correlation[0]["service_kind"]);
            AssertEx.Equal(0,(int)correlation[0]["parent_token"]);
            AssertEx.Equal(0,(int)correlation[0]["depth"]);
        }

        private static void AssertPromotedDirectChildIdentity(
            uint childBegin,uint childEnd,byte childKind,bool preEpoch)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDAE);
                current.SetU32(0xFDAE,0x00000B64);
                api.VisitZ80(0x0077,current);
                Visit(current,api,0x071B4C);
                api.VisitZ80(0x00AC,current);
                api.VisitZ80(childBegin,current);
                Visit(current,api,0x071B82);
                api.VisitZ80(childEnd,current);
                Visit(current,api,0x071C4C);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                if(preEpoch)
                {
                    session.ObservePreEpochFrame(859,null,host.Advance);
                    AssertEx.Equal(0,output.ToString().Length);
                    session.BeginEpoch();
                    AssertEx.Equal(0,CountRecords(output.ToString(),
                        "managed_hook_evidence"));
                    return;
                }
                session.CaptureFrame(860,host.Advance);
                session.Complete(861);
            }
            JObject observation=Records(output.ToString(),
                "managed_hook_evidence",value=>(uint)value["pc"]==0x071B82)[0];
            JObject correlation=(JObject)observation[
                "native_correlation_events"][0];
            AssertEx.Equal(childKind,(byte)correlation["service_kind"]);
            AssertEx.Equal(1,(int)correlation["depth"]);
            AssertEx.Equal((ushort)2,(ushort)correlation["parent_token"]);
        }

        private static void EnforcesManagedTokenA7AndCutoffSetIdentity()
        {
            AssertPromotedObservationA7Fails(0,0);
            AssertPromotedObservationA7Fails(0x0077,0x00AC);
            AssertPromotedObservationA7Fails(0x00C1,0x00D0);

            var tracker=new S1CompleteRunAudioReferenceCapture.ManagedServiceTracker();
            tracker.Begin(3,0x00FFFDAE);
            AssertEx.Equal(false,tracker.Matches(4,0x00FFFDAE));
            AssertEx.Equal(false,tracker.Matches(3,0x00FFFDB2));
            AssertEx.Throws<InvalidOperationException>(
                ()=>tracker.Begin(3,0x00FFFDAE),"reused");
            AssertEx.Throws<InvalidOperationException>(
                ()=>tracker.End(4),"no open");
            AssertEx.Equal(1,tracker.Count);

            CompleteRunAudioObserver.DriverService promoted=ManagedBoundaryService(
                3,1,1,0,0);
            AssertEx.Equal(true,tracker.MatchesBoundary(
                new[]{promoted}));
            AssertEx.Equal(false,tracker.MatchesBoundary(
                new CompleteRunAudioObserver.DriverService[0]));
            AssertEx.Equal(false,tracker.MatchesBoundary(new[]{promoted,
                ManagedBoundaryService(4,0,0,0,0)}));
            AssertEx.Equal(false,tracker.MatchesBoundary(new[]{
                ManagedBoundaryService(4,0,0,0,0)}));
            AssertEx.Equal(false,tracker.MatchesBoundary(new[]{promoted,
                promoted}));
        }

        private static void AssertPromotedObservationA7Fails(
            uint childBegin,uint childEnd)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDAE);
                api.VisitZ80(0x0077,current);
                Visit(current,api,0x071B4C);
                api.VisitZ80(0x00AC,current);
                if(childBegin!=0)api.VisitZ80(childBegin,current);
                current.SetCpuRegister("A7",0x00FFFDB2);
                Visit(current,api,0x071B82);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.BeginEpoch();
                int before=output.ToString().Length;
                AssertEx.Throws<InvalidOperationException>(
                    ()=>session.CaptureFrame(860,host.Advance),"identity");
                AssertEx.Equal(before,output.ToString().Length);
            }
        }

        private static void RejectsBoundaryRetryTokenA7Changes()
        {
            AssertBoundaryRetryIdentityFails(0,false);
            AssertBoundaryRetryIdentityFails(0x0077,false);
            AssertBoundaryRetryIdentityFails(0x00C1,false);
            AssertBoundaryRetryIdentityFails(0,true);
            AssertBoundaryRetryIdentityFails(0x0077,true);
            AssertBoundaryRetryIdentityFails(0x00C1,true);
        }

        private static void AssertBoundaryRetryIdentityFails(
            uint childBegin,bool priorLifetime)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                if(frame==1)
                {
                    current.SetCpuRegister("A7",0x00FFFDAE);
                    if(priorLifetime)
                    {
                        Visit(current,api,0x071B4C);
                        Visit(current,api,0x071C4C);
                        current.SetCpuRegister("A7",0x00FFFDB2);
                    }
                    Visit(current,api,0x071B4C);
                    if(childBegin!=0)api.VisitZ80(childBegin,current);
                    return;
                }
                current.SetCpuRegister("A7",(uint)(priorLifetime
                    ?0x00FFFDAE:0x00FFFDB2));
                Visit(current,api,0x071B4C);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.ObservePreEpochFrame(858,null,host.Advance);
                AssertEx.Equal(1,session.BoundaryManagedServiceCountForTesting);
                AssertEx.Throws<InvalidOperationException>(
                    ()=>session.ObservePreEpochFrame(859,null,host.Advance),
                    "retry changed its native service identity");
                AssertEx.Equal(1,session.BoundaryManagedServiceCountForTesting);
                AssertEx.Equal(0,output.ToString().Length);
            }
        }

        private static void RejectsNativeValidCrossLifetimeObservations()
        {
            AssertNativeValidCrossLifetimeObservationFails(0,false);
            AssertNativeValidCrossLifetimeObservationFails(0x0077,false);
            AssertNativeValidCrossLifetimeObservationFails(0x00C1,false);
            AssertNativeValidCrossLifetimeObservationFails(0,true);
            AssertNativeValidCrossLifetimeObservationFails(0x0077,true);
            AssertNativeValidCrossLifetimeObservationFails(0x00C1,true);
        }

        private static void AssertNativeValidCrossLifetimeObservationFails(
            uint childBegin,bool preEpoch)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                if(frame==1)
                {
                    current.SetCpuRegister("A7",0x00FFFDAE);
                    Visit(current,api,0x071B4C);
                    Visit(current,api,0x071C4C);
                    api.VisitZ80(0x0077,current);
                    current.SetCpuRegister("A7",0x00FFFDB2);
                    Visit(current,api,0x071B4C);
                    api.VisitZ80(0x00AC,current);
                    if(childBegin!=0)api.VisitZ80(childBegin,current);
                    return;
                }
                current.SetCpuRegister("A7",0x00FFFDAE);
                Visit(current,api,0x071B82);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                if(preEpoch)
                {
                    session.ObservePreEpochFrame(858,null,host.Advance);
                    AssertEx.Equal(1,
                        session.BoundaryManagedServiceCountForTesting);
                    AssertEx.Throws<InvalidOperationException>(()=>
                        session.ObservePreEpochFrame(859,null,host.Advance),
                        "managed identity differs");
                    AssertEx.Equal(1,
                        session.BoundaryManagedServiceCountForTesting);
                    AssertEx.Equal(0,output.ToString().Length);
                    return;
                }
                session.BeginEpoch();
                session.CaptureFrame(860,host.Advance);
                int before=output.ToString().Length;
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.CaptureFrame(861,host.Advance),
                    "managed identity differs");
                AssertEx.Equal(before,output.ToString().Length);
            }
        }

        private static CompleteRunAudioObserver.DriverService
            ManagedBoundaryService(ushort token,ushort parent,byte depth,
                ushort currentParent,byte currentDepth)
        {
            return new CompleteRunAudioObserver.DriverService(
                new CompleteRunAudioObserver.ServiceBuilder
                {
                    Token=token,ParentToken=parent,Kind=4,Depth=depth,
                    CurrentParentToken=currentParent,CurrentDepth=currentDepth
                },false);
        }

        private static void CarriesPromotedManagedIdentityAcrossEpoch()
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDAE);
                if(frame==1)
                {
                    api.VisitZ80(0x0077,current);
                    Visit(current,api,0x071B4C);
                    api.VisitZ80(0x00AC,current);
                    return;
                }
                Visit(current,api,0x071B82);
                Visit(current,api,0x071C4C);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.ObservePreEpochFrame(859,null,host.Advance);
                AssertEx.Equal(0,output.ToString().Length);
                AssertEx.Equal(1,session.BoundaryManagedServiceCountForTesting);
                session.BeginEpoch();
                JObject baseline=Record(output.ToString(),"baseline",0);
                JArray active=(JArray)baseline["active_services"];
                AssertEx.Equal(1,active.Count);
                AssertEx.Equal(4,(int)active[0]["kind"]);
                AssertEx.Equal(1,(int)active[0]["parent_token"]);
                AssertEx.Equal(1,(int)active[0]["depth"]);
                AssertEx.Equal(0,(int)active[0]["current_parent_token"]);
                AssertEx.Equal(0,(int)active[0]["current_depth"]);
                AssertEx.Equal(1,
                    ((JArray)active[0]["ancestry_transitions"]).Count);
                AssertEx.Equal(0,CountRecords(output.ToString(),"native_event"));
                AssertEx.Equal(0,CountRecords(output.ToString(),
                    "managed_hook_evidence"));
                session.CaptureFrame(860,host.Advance);
                session.Complete(861);
            }
            AssertEx.Equal(1,Records(output.ToString(),"managed_hook_evidence",
                value=>(uint)value["pc"]==0x071B82).Count);
        }

        private static void CancelsAndRollsBackPromotedManagedIdentity()
        {
            AssertPromotedBoundaryReset(false,true,false);
            AssertPromotedBoundaryReset(true,false,false);
            AssertPromotedBoundaryReset(true,true,false);
            AssertPromotedBoundaryReset(false,true,true);
            RollsBackPromotedObservationAfterMalformedTail(false);
            RollsBackPromotedObservationAfterMalformedTail(true);
        }

        private static void AssertPromotedBoundaryReset(
            bool power,bool reset,bool malformed)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDAE);
                if(frame==1)
                {
                    api.VisitZ80(0x0077,current);
                    Visit(current,api,0x071B4C);
                    api.VisitZ80(0x00AC,current);
                    return;
                }
                if(power)api.EmitReset(true,current);
                if(reset)api.EmitReset(false,current);
                if(malformed)api.RemoveFirst(value=>value.Kind==2
                    &&(value.Flags&2)!=0&&value.ServiceKindId==4);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.ObservePreEpochFrame(858,null,host.Advance);
                AssertEx.Equal(1,session.BoundaryManagedServiceCountForTesting);
                if(malformed)
                {
                    AssertEx.Throws<InvalidOperationException>(()=>
                        session.ObservePreEpochFrame(859,
                            new Bk2Frame{Power=power,Reset=reset},host.Advance),
                        "native audio observer returned invalid");
                    AssertEx.Equal(1,
                        session.BoundaryManagedServiceCountForTesting);
                    AssertEx.Equal(true,session.ResetScratchClearForTesting);
                }
                else
                {
                    session.ObservePreEpochFrame(859,
                        new Bk2Frame{Power=power,Reset=reset},host.Advance);
                    AssertEx.Equal(0,
                        session.BoundaryManagedServiceCountForTesting);
                    session.BeginEpoch();
                    AssertEx.Equal(0,((JArray)Record(output.ToString(),
                        "baseline",0)["active_services"]).Count);
                }
            }
            if(malformed)AssertEx.Equal(0,output.ToString().Length);
        }

        private static void RollsBackPromotedObservationAfterMalformedTail(
            bool preEpoch)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDAE);
                if(frame==1)
                {
                    api.VisitZ80(0x0077,current);
                    Visit(current,api,0x071B4C);
                    api.VisitZ80(0x00AC,current);
                    return;
                }
                Visit(current,api,0x071B82);
                Visit(current,api,0x071C4C);
                api.MutateLast(value=>{value.ServiceToken++;return value;});
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                if(preEpoch)
                {
                    session.ObservePreEpochFrame(858,null,host.Advance);
                    AssertEx.Throws<InvalidOperationException>(()=>
                        session.ObservePreEpochFrame(859,null,host.Advance),
                        "native audio observer returned invalid");
                    AssertEx.Equal(1,
                        session.BoundaryManagedServiceCountForTesting);
                    AssertEx.Equal(0,output.ToString().Length);
                    return;
                }
                session.BeginEpoch();
                session.CaptureFrame(860,host.Advance);
                int before=output.ToString().Length;
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.CaptureFrame(861,host.Advance),
                    "native audio observer returned invalid");
                AssertEx.Equal(before,output.ToString().Length);
            }
        }

        private static void TracksDeferredChildWhollyBeforeEpoch()
        {
            AssertDeferredChildWhollyBeforeEpoch(0x0077,0x00AC);
            AssertDeferredChildWhollyBeforeEpoch(0x00C1,0x00D0);
            RollsBackCorruptPreEpochDeferredChildObservation();
        }

        private static void AssertDeferredChildWhollyBeforeEpoch(
            uint asyncBegin,uint asyncEnd)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                api.VisitZ80(0x003A,current);
                Visit(current,api,0x071B4C);
                Visit(current,api,0x071B82);
                api.VisitZ80(asyncBegin,current);
                Visit(current,api,0x071B82);
                api.VisitZ80(asyncEnd,current);
                Visit(current,api,0x071C4C);
                api.VisitZ80(0x0077,current);
                api.VisitZ80(0x00AC,current);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.ObservePreEpochFrame(859,null,host.Advance);
                AssertEx.Equal(0,output.ToString().Length);
                AssertEx.Equal(0,session.BoundaryManagedServiceCountForTesting);
                session.BeginEpoch();
            }
            JObject baseline=Record(output.ToString(),"baseline",0);
            AssertEx.Equal(0,((JArray)baseline["active_services"]).Count);
            AssertEx.Equal(0,CountRecords(output.ToString(),"native_event"));
            AssertEx.Equal(0,CountRecords(output.ToString(),
                "managed_hook_evidence"));
        }

        private static void RollsBackCorruptPreEpochDeferredChildObservation()
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                if(frame==1)
                {
                    api.VisitZ80(0x003A,current);
                    Visit(current,api,0x071B4C);
                    Visit(current,api,0x071B82);
                    return;
                }
                current.SetCpuRegister("A7",0x00FFFDB6);
                Visit(current,api,0x071B82);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.ObservePreEpochFrame(858,null,host.Advance);
                AssertEx.Equal(1,session.BoundaryManagedServiceCountForTesting);
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.ObservePreEpochFrame(859,null,host.Advance),"identity");
                AssertEx.Equal(1,session.BoundaryManagedServiceCountForTesting);
                AssertEx.Equal(0,output.ToString().Length);
            }
        }

        private static void CarriesConsumedDeferredChildAcrossEpoch()
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                if(frame==1)
                {
                    api.VisitZ80(0x003A,current);
                    Visit(current,api,0x071B4C);
                    Visit(current,api,0x071B82);
                    return;
                }
                Visit(current,api,0x071B82);
                Visit(current,api,0x071C4C);
                api.VisitZ80(0x0077,current);
                api.VisitZ80(0x00AC,current);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.ObservePreEpochFrame(859,null,host.Advance);
                AssertEx.Equal(0,output.ToString().Length);
                AssertEx.Equal(1,session.BoundaryManagedServiceCountForTesting);
                session.BeginEpoch();
                JObject baseline=Record(output.ToString(),"baseline",0);
                JArray active=(JArray)baseline["active_services"];
                AssertEx.Equal(2,active.Count);
                AssertEx.Equal(6,(int)active[0]["kind"]);
                AssertEx.Equal(4,(int)active[1]["kind"]);
                AssertEx.Equal((ushort)active[0]["token"],
                    (ushort)active[1]["parent_token"]);
                AssertEx.Equal(1,(int)active[1]["depth"]);
                AssertEx.Equal(0,CountRecords(output.ToString(),"native_event"));
                AssertEx.Equal(0,CountRecords(output.ToString(),
                    "managed_hook_evidence"));
                session.CaptureFrame(860,host.Advance);
                session.Complete(861);
            }
            AssertEx.Equal(1,Records(output.ToString(),"managed_hook_evidence",
                value=>(uint)value["pc"]==0x071B82).Count);
        }

        private static void CancelsBoundaryDeferredChildOnReset()
        {
            AssertBoundaryDeferredReset(false,true);
            AssertBoundaryDeferredReset(true,false);
            AssertBoundaryDeferredReset(true,true);
            RollsBackMalformedBoundaryDeferredReset();
            RollsBackAbortedBoundaryDeferredPower();
        }

        private static void AssertBoundaryDeferredReset(bool power,bool reset)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                if(frame==1)
                {
                    api.VisitZ80(0x003A,current);
                    Visit(current,api,0x071B4C);
                    Visit(current,api,0x071B82);
                    return;
                }
                if(power)api.EmitReset(true,current);
                if(reset)api.EmitReset(false,current);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.ObservePreEpochFrame(858,null,host.Advance);
                AssertEx.Equal(1,session.BoundaryManagedServiceCountForTesting);
                session.ObservePreEpochFrame(859,
                    new Bk2Frame{Power=power,Reset=reset},host.Advance);
                AssertEx.Equal(0,session.BoundaryManagedServiceCountForTesting);
                AssertEx.Equal(0,output.ToString().Length);
                session.BeginEpoch();
            }
            JObject baseline=Record(output.ToString(),"baseline",0);
            AssertEx.Equal(0,((JArray)baseline["active_services"]).Count);
            AssertEx.Equal(0,CountRecords(output.ToString(),"native_event"));
            AssertEx.Equal(0,CountRecords(output.ToString(),"input_reset"));
        }

        private static void RollsBackMalformedBoundaryDeferredReset()
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                if(frame==1)
                {
                    api.VisitZ80(0x003A,current);
                    Visit(current,api,0x071B4C);
                    Visit(current,api,0x071B82);
                    return;
                }
                api.EmitReset(false,current);
                api.RemoveFirst(value=>value.Kind==2&&(value.Flags&2)!=0
                    &&value.ServiceKindId==4);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.ObservePreEpochFrame(858,null,host.Advance);
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.ObservePreEpochFrame(859,
                        new Bk2Frame{Reset=true},host.Advance),
                    "innermost service");
                AssertEx.Equal(1,session.BoundaryManagedServiceCountForTesting);
                AssertEx.Equal(0,output.ToString().Length);
            }
        }

        private static void RollsBackAbortedBoundaryDeferredPower()
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                if(frame==1)
                {
                    api.VisitZ80(0x003A,current);
                    Visit(current,api,0x071B4C);
                    Visit(current,api,0x071B82);
                    return;
                }
                api.EmitReset(true,current);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.ObservePreEpochFrame(858,null,host.Advance);
                api.EventCountStatus=-3;
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.ObservePreEpochFrame(859,
                        new Bk2Frame{Power=true},host.Advance),"event count");
                AssertEx.Equal(1,session.BoundaryManagedServiceCountForTesting);
                AssertEx.Equal(true,session.ResetScratchClearForTesting);
                AssertEx.Equal(true,api.Calls.Contains("abort"));
                AssertEx.Equal(0,output.ToString().Length);
            }
        }

        private static void AssertDeferredObservationCount(int count)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                api.VisitZ80(0x003A,current);
                for(int i=0;i<count;i++)Visit(current,api,0x071B4C);
                Visit(current,api,0x071B82);
                Visit(current,api,0x071C4C);
                api.VisitZ80(0x0077,current);
                api.VisitZ80(0x00AC,current);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.CaptureFrame(860,host.Advance);
                session.Complete(861);
            }
            AssertEx.Equal(count,CountNativeMarkerValue(output.ToString(),4));
            AssertEx.Equal(count,Records(output.ToString(),"managed_hook_evidence",
                value=>value["native_marker_value"]!=null
                    &&value["native_marker_value"].Type==JTokenType.Integer
                    &&(int)value["native_marker_value"]==4).Count);
        }

        private static void AssertObservationKinds(
            S1CompleteRunAudioReferenceCapture.Manifest manifest,
            uint pc, params byte[] expectedKinds)
        {
            var actual = new List<byte>();
            foreach (GpgxAudioObserverAdapter.ServiceHook hook in manifest.NativeServiceHooks)
                if (hook.Cpu == 2 && hook.Pc == pc && hook.Action == 7)
                    actual.Add(hook.ExpectedActiveKind);
            actual.Sort();
            Array.Sort(expectedKinds);
            AssertEx.Equal(expectedKinds.Length, actual.Count);
            for (int i=0;i<expectedKinds.Length;i++)
                AssertEx.Equal(expectedKinds[i], actual[i]);
        }

        private static void AssertCloseAlternatives(
            S1CompleteRunAudioReferenceCapture.Manifest manifest, uint pc)
        {
            int ordinary=0;var crossing=new List<byte>();
            foreach (GpgxAudioObserverAdapter.ServiceHook hook in manifest.NativeServiceHooks)
            {
                if (hook.Cpu!=2||hook.Pc!=pc) continue;
                if (hook.Action==2&&hook.ServiceKindId==0&&hook.ExpectedActiveKind==4)
                    ordinary++;
                else if (hook.Action==8&&hook.ServiceKindId==4)
                    crossing.Add(hook.ExpectedActiveKind);
            }
            crossing.Sort();
            AssertEx.Equal(1,ordinary);
            AssertEx.Equal(2,crossing.Count);
            AssertEx.Equal((byte)2,crossing[0]);
            AssertEx.Equal((byte)3,crossing[1]);
        }

        private static void AssertConditionalCloseAlternatives(
            S1CompleteRunAudioReferenceCapture.Manifest manifest, uint pc)
        {
            int ordinary=0;var crossing=new List<byte>();
            foreach (GpgxAudioObserverAdapter.ServiceHook hook in manifest.NativeServiceHooks)
            {
                if (hook.Cpu!=2||hook.Pc!=pc) continue;
                if (hook.Action==5&&hook.ServiceKindId==0
                    &&hook.ExpectedActiveKind==4) ordinary++;
                else if (hook.Action==9&&hook.ServiceKindId==4)
                    crossing.Add(hook.ExpectedActiveKind);
            }
            crossing.Sort();
            AssertEx.Equal(1,ordinary);
            AssertEx.Equal(2,crossing.Count);
            AssertEx.Equal((byte)2,crossing[0]);
            AssertEx.Equal((byte)3,crossing[1]);
        }

        private static void RequiresDirectParentRetryUnderAsyncPcm()
        {
            S1CompleteRunAudioReferenceCapture.Manifest manifest =
                S1CompleteRunAudioReferenceCapture.LoadManifest(
                    FixturePath(), RomForManifest(FixturePath()));
            var asyncKinds = new List<byte>();
            foreach (GpgxAudioObserverAdapter.ServiceHook hook
                in manifest.NativeServiceHooks)
            {
                if (hook.Cpu == 2 && hook.Pc == 0x071B4C
                    && hook.Action == 10 && hook.ServiceKindId == 4
                    && hook.RangeCount == 0 && hook.Reserved == 0)
                {
                    asyncKinds.Add(hook.ExpectedActiveKind);
                }
            }
            asyncKinds.Sort();
            AssertEx.Equal(2, asyncKinds.Count);
            AssertEx.Equal((byte)2, asyncKinds[0]);
            AssertEx.Equal((byte)3, asyncKinds[1]);
        }

        private static void CorrelatesDirectParentRetryToManagedService()
        {
            CorrelatesDirectParentRetryToManagedService(0x0077, 0x00AC, 2);
            CorrelatesDirectParentRetryToManagedService(0x00C1, 0x00D0, 3);
            RejectsCorruptDirectParentRetryOwnership(false);
            RejectsCorruptDirectParentRetryOwnership(true);
        }

        private static void CorrelatesDirectParentRetryToManagedService(
            uint beginPc, uint closePc, byte asyncKind)
        {
            var api = new FakeTraceApi();
            var host = new FakeS1Host((current, frame) =>
            {
                current.SetCpuRegister("A7", 0x00FFFDB2);
                Visit(current, api, 0x071B4C);
                api.VisitZ80(beginPc, current);
                Visit(current, api, 0x071B4C);
                api.VisitZ80(closePc, current);
                Visit(current, api, 0x071C4C);
            });
            var output = new StringWriter();
            using (var session = CreateSession(host, api, output))
            {
                session.CaptureFrame(860, host.Advance);
                session.Complete(861);
            }
            string raw = output.ToString();
            AssertEx.Equal(2, CountNativeKind(raw, 1));
            AssertEx.Equal(2, CountNativeKind(raw, 2));
            AssertEx.Equal(1, CountNativeMarkerValue(raw, 2));
            JObject retry = Record(raw, "managed_hook_evidence", 1);
            JArray correlation = (JArray)retry["native_correlation_events"];
            AssertEx.Equal(1, correlation.Count);
            AssertEx.Equal(10, (int)correlation[0]["event_kind"]);
            AssertEx.Equal(2, (int)correlation[0]["value"]);
            AssertEx.Equal(4, (int)correlation[0]["service_kind"]);
            AssertEx.Equal(0, (int)correlation[0]["depth"]);
            JObject asyncBegin = NativeRecords(raw, 860).Find(value =>
                (int)value["kind"] == 1 && (uint)value["pc"] == beginPc);
            AssertEx.Equal(true, asyncBegin != null);
            AssertEx.Equal(asyncKind, (byte)asyncBegin["service_kind"]);
        }

        private static void RejectsCorruptDirectParentRetryOwnership(
            bool corruptDepth)
        {
            var api = new FakeTraceApi();
            var host = new FakeS1Host((current, frame) =>
            {
                current.SetCpuRegister("A7", 0x00FFFDB2);
                Visit(current, api, 0x071B4C);
                api.VisitZ80(0x0077, current);
                Visit(current, api, 0x071B4C);
                api.MutateLast(value =>
                {
                    if (corruptDepth) value.Depth++;
                    else value.ParentToken++;
                    return value;
                });
            });
            using (var session = CreateSession(host, api, new StringWriter()))
            {
                AssertEx.Throws<InvalidOperationException>(
                    () => session.CaptureFrame(860, host.Advance),
                    "retry marker parent ownership");
            }
        }

        private static void RejectsMalformedAndMismatchedManifest()
        {
            string original = File.ReadAllText(FixturePath());
            JObject root = JObject.Parse(original);
            root["unexpected"] = true;
            string malformed = WriteScratch(root.ToString());
            AssertEx.Throws<InvalidDataException>(
                () => S1CompleteRunAudioReferenceCapture.LoadManifest(
                    malformed, RomForManifest(FixturePath())), "property");

            byte[] rom = RomForManifest(FixturePath());
            rom[0x071B4C] ^= 0x01;
            AssertEx.Throws<InvalidDataException>(
                () => S1CompleteRunAudioReferenceCapture.LoadManifest(
                    FixturePath(), rom), "opcode");

            rom = RomForManifest(FixturePath());
            rom[0x071B82] ^= 0x01;
            AssertEx.Throws<InvalidDataException>(
                () => S1CompleteRunAudioReferenceCapture.LoadManifest(
                    FixturePath(), rom), "opcode");

            root = JObject.Parse(original);
            ((JArray)root["m68k_hooks"]).Add(
                ((JArray)root["m68k_hooks"])[0].DeepClone());
            string duplicate = WriteScratch(root.ToString());
            AssertEx.Throws<InvalidDataException>(
                () => S1CompleteRunAudioReferenceCapture.LoadManifest(
                    duplicate, RomForManifest(duplicate)), "duplicate");
        }

        private static void KeepsRetryInOneManagedService()
        {
            var api = new FakeTraceApi();
            var host = new FakeS1Host((current, frame) =>
            {
                current.SetCpuRegister("A7", 0x00FFFF00);
                Visit(current, api, 0x071B4C);
                Visit(current, api, 0x071B4C);
                current.SetCpuRegister("D7", 0x81);
                Visit(current, api, 0x071FD2);
                Visit(current, api, 0x071C4C);
            });
            var output = new StringWriter();
            using (var session = CreateSession(host, api, output))
            {
                session.CaptureFrame(860, host.Advance);
                session.Complete(861);
            }
            AssertEx.Equal(1, CountNativeKind(output.ToString(), 1));
            AssertEx.Equal(1, CountNativeKind(output.ToString(), 2));
            AssertEx.Equal(1, CountNativeMarkerValue(output.ToString(), 2));
            AssertEx.Equal(2, CountRecords(output.ToString(),
                "managed_hook_evidence", 0x071B4C));
            JObject begin = Record(output.ToString(), "managed_hook_evidence", 0);
            JObject retry = Record(output.ToString(), "managed_hook_evidence", 1);
            JObject close = Record(output.ToString(), "managed_hook_evidence", 2);
            AssertEx.Equal(0L, (long)begin["managed_correlation_ordinal"]);
            AssertEx.Equal(1L, (long)retry["managed_correlation_ordinal"]);
            AssertEx.Equal(2L, (long)close["managed_correlation_ordinal"]);
            AssertEx.Equal(1, ((JArray)retry["native_correlation_events"]).Count);
            AssertEx.Equal(10,
                (int)retry["native_correlation_events"][0]["event_kind"]);
            AssertEx.Equal(2,
                (int)retry["native_correlation_events"][0]["value"]);
        }

        private static void ResolvesAdjustedReturnExits()
        {
            var api = new FakeTraceApi();
            var host = new FakeS1Host((current, frame) =>
            {
                current.SetCpuRegister("A7", 0x00FFF000);
                Visit(current, api, 0x071B4C);
                current.SetU32(0xF000, 0x00071BD4);
                Visit(current, api, 0x072E04);
                Visit(current, api, 0x071C4C);
                current.SetCpuRegister("A7", 0x00FFF010);
                Visit(current, api, 0x071B4C);
                current.SetU32(0xF010, 0x00010000);
                Visit(current, api, 0x072C24);
            });
            var output = new StringWriter();
            using (var session = CreateSession(host, api, output))
            {
                session.CaptureFrame(860, host.Advance);
                session.Complete(861);
            }
            AssertEx.Equal(2, CountNativeKind(output.ToString(), 1));
            AssertEx.Equal(2, CountNativeKind(output.ToString(), 2));
            AssertEx.Equal(1, CountNativeMarkerValue(output.ToString(), 0));
            AssertEx.Equal(1, CountNativeMarkerValue(output.ToString(), 1));
            AssertEx.Equal(true, output.ToString().IndexOf(
                "\"return_pc\":465876", StringComparison.Ordinal) >= 0);
            AssertEx.Equal(true, output.ToString().IndexOf(
                "\"return_pc\":65536", StringComparison.Ordinal) >= 0);
            JObject conditional = Record(
                output.ToString(), "managed_service_snapshot", 1);
            JArray correlation = (JArray)conditional["native_correlation_events"];
            AssertEx.Equal(2, correlation.Count);
            AssertEx.Equal(10, (int)correlation[0]["event_kind"]);
            AssertEx.Equal(1, (int)correlation[0]["value"]);
            AssertEx.Equal(false, (bool)correlation[0]["terminal"]);
            AssertEx.Equal(2, (int)correlation[1]["event_kind"]);
            AssertEx.Equal(0, (int)correlation[1]["value"]);
            AssertEx.Equal(true, (bool)correlation[1]["terminal"]);
            AssertEx.Equal(true,
                (uint)correlation[0]["ordinal"] < (uint)correlation[1]["ordinal"]);
        }

        private static void CorrelatesConditionalDirectParentPromotion()
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame) =>
            {
                current.SetCpuRegister("A7",0x00FFF000);
                Visit(current,api,0x071B4C);
                api.VisitZ80(0x0077,current);
                current.SetU32(0xF000,0x00010000);
                Visit(current,api,0x072E04);
                api.VisitZ80(0x00AC,current);
            });
            var output=new StringWriter();
            using (var session=CreateSession(host,api,output))
            {
                session.CaptureFrame(860,host.Advance);
                session.Complete(861);
            }
            AssertEx.Equal(1,CountNativeKind(output.ToString(),11));
            AssertEx.Equal(0,CountNativeMarkerValue(output.ToString(),1));
            JObject snapshot=Record(output.ToString(),"managed_service_snapshot",0);
            JArray correlation=(JArray)snapshot["native_correlation_events"];
            AssertEx.Equal(1,correlation.Count);
            AssertEx.Equal(2,(int)correlation[0]["event_kind"]);
            AssertEx.Equal(true,(bool)correlation[0]["terminal"]);
        }

        private static void RejectsOrphanCloseAndFrameOverflow()
        {
            var orphan = new FakeS1Host((current, frame) =>
                current.FireExecuteCallback(0x071C4C));
            using (var session = CreateSession(
                orphan, new FakeTraceApi(), new StringWriter()))
            {
                AssertEx.Throws<InvalidOperationException>(
                    () => session.CaptureFrame(860, orphan.Advance), "orphan");
            }

            string limitedPath = WithMaximumRecords(2);
            var api = new FakeTraceApi();
            var noisy = new FakeS1Host((current, frame) =>
            {
                Visit(current, api, 0x00138E);
                Visit(current, api, 0x001394);
                Visit(current, api, 0x00139A);
            });
            using (var session = new S1CompleteRunAudioReferenceCapture.Session(
                noisy, noisy, api,
                S1CompleteRunAudioReferenceCapture.LoadManifest(
                    limitedPath, RomForManifest(limitedPath)),
                new StringWriter()))
            {
                AssertEx.Throws<InvalidOperationException>(
                    () => session.CaptureFrame(860, noisy.Advance), "overflow");
            }
        }

        private static void StreamsNativeDacInSameFramePass()
        {
            var api = new FakeTraceApi
            {
                Events = new[]
                {
                    NativeEvent(0, 1, 1, 2, 0x0077),
                    NativeEvent(1, 3, 1, 2, 0x0089, 0x2A),
                    SnapshotEvent(2, 5, 0, 0, 0),
                    SnapshotEvent(3, 6, 0, 1, 0x55),
                    SnapshotEvent(4, 7, 1, 0, 0),
                    NativeEvent(5, 2, 1, 2, 0x00AC)
                }
            };
            var host = new FakeS1Host((current, frame) =>
                Visit(current, api, 0x00138E));
            var output = new StringWriter();
            using (var session = CreateSession(host, api, output))
            {
                session.CaptureFrame(860, host.Advance);
                session.Complete(861);
            }
            AssertEx.Equal("configure,publication,begin,end,count,drain:7,disable",
                string.Join(",", api.Calls));
            AssertEx.Equal(7, CountRecords(output.ToString(), "native_event"));
            AssertEx.Equal(1, host.CompletedFrame);
        }

        private static void SamplesBaselineBeforeInputAndRetainsEmptyRows()
        {
            var api = new FakeTraceApi();
            var host = new FakeS1Host((current, frame) =>
                current.WriteMainRamByte(0xF000, 0x22));
            host.WriteMainRamByte(0xF000, 0x11);
            var output = new StringWriter();
            using (var session = CreateSession(host, api, output))
            {
                session.CaptureFrame(860, host.Advance);
                session.Complete(861);
            }
            JObject baseline = Record(output.ToString(), "baseline", 0);
            AssertEx.Equal(true,
                ((string)baseline["state_hex"]).StartsWith("11",
                    StringComparison.Ordinal));
            AssertEx.Equal(1, CountRecords(output.ToString(), "frame_begin"));
            AssertEx.Equal(1, CountRecords(output.ToString(), "frame_end"));
            AssertEx.Equal(0, CountNativeKind(output.ToString(), 1));
        }

        private static void CarriesOpenPreEpochDpcmIntoFirstPublishedRow()
        {
            var api = new FakeTraceApi();
            var host = new FakeS1Host(null);
            var output = new StringWriter();
            using (var session = CreateSession(host, api, output))
            {
                api.Events = new[]
                {
                    NativeEvent(0, 1, 1, 2, 0x0077),
                    new GpgxAudioTraceEvent
                    {
                        Ordinal=1,Kind=3,ServiceToken=1,ServiceKindId=2,
                        SourceCpu=1,Pc=0x009C,Subject=0,Value=0x2A
                    }
                };
                session.ObservePreEpochFrame(859, null, host.Advance);
                AssertEx.Equal(0, output.ToString().Length);
                session.BeginEpoch();

                api.Events = new[]
                {
                    new GpgxAudioTraceEvent
                    {
                        Ordinal=0,Kind=3,ServiceToken=1,ServiceKindId=2,
                        SourceCpu=1,Pc=0x009F,Subject=1,Value=0x55
                    },
                    SnapshotEvent(1, 5, 0, 0, 0),
                    SnapshotEvent(2, 6, 0, 1, 0x55),
                    SnapshotEvent(3, 7, 1, 0, 0),
                    NativeEvent(4, 2, 1, 2, 0x00AC)
                };
                session.CaptureFrame(860, host.Advance);
                session.Complete(861);
            }
            JObject baseline = Record(output.ToString(), "baseline", 0);
            AssertEx.Equal(0x2A, (int)baseline["ym_port0_latch"]);
            JArray active = (JArray)baseline["active_services"];
            AssertEx.Equal(1, active.Count);
            AssertEx.Equal("CARRIED_IN_OPEN", (string)active[0]["state"]);
            AssertEx.Equal(1, (int)active[0]["token"]);
            AssertEx.Equal(0, CountNative(output.ToString(), value =>
                (uint)value["pc"] == 0x009C));
            AssertEx.Equal(1, CountNative(output.ToString(), value =>
                (uint)value["pc"] == 0x009F));
            AssertEx.Equal(1, CountNativeKind(output.ToString(), 2));
            AssertEx.Equal("configure,begin,end,count,drain:2,publication,begin,end,count,drain:5,disable",
                string.Join(",", api.Calls));
        }

        private static void CarriesDeferredReservationAcrossEpochBoundary()
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                if(frame==1)
                {
                    api.VisitZ80(0x003A,current);
                    Visit(current,api,0x071B4C);
                    return;
                }
                Visit(current,api,0x071B82);
                Visit(current,api,0x071BB2);
                Visit(current,api,0x071C4C);
                api.VisitZ80(0x0077,current);
                api.VisitZ80(0x00AC,current);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.ObservePreEpochFrame(859,null,host.Advance);
                AssertEx.Equal(0,output.ToString().Length);
                session.BeginEpoch();
                string boundary=output.ToString();
                AssertEx.Equal(0,CountRecords(boundary,"native_event"));
                AssertEx.Equal(0,CountRecords(boundary,"managed_hook_evidence"));
                session.CaptureFrame(860,host.Advance);
                session.Complete(861);
            }
            string raw=output.ToString();
            AssertEx.Equal(0,CountNativeMarkerValue(raw,4));
            AssertEx.Equal(0,Records(raw,"managed_hook_evidence",
                value=>(uint)value["pc"]==0x071B4C).Count);
            List<JObject> native=NativeRecords(raw,860);
            int consume=FindNative(native,value=>(int)value["kind"]==1
                &&(int)value["service_kind"]==4
                &&(uint)value["pc"]==0x071B82);
            ushort blocker=(ushort)native[consume]["parent_token"];
            ushort child=(ushort)native[consume]["service_token"];
            AssertEx.Equal((ushort)1,blocker);
            AssertEx.Equal(1,(int)native[consume]["depth"]);
            int observation=FindNative(native,value=>(int)value["kind"]==10
                &&(uint)value["pc"]==0x071BB2);
            int childEnd=FindNative(native,value=>(int)value["kind"]==2
                &&(ushort)value["service_token"]==child);
            int blockerEnd=FindNative(native,value=>(int)value["kind"]==2
                &&(ushort)value["service_token"]==blocker);
            AssertEx.Equal(true,consume<observation&&observation<childEnd
                &&childEnd<blockerEnd);
            AssertEx.Equal(child,(ushort)native[observation]["service_token"]);
            JObject consumeEvidence=Records(raw,"managed_hook_evidence",
                value=>(uint)value["pc"]==0x071B82)[0];
            AssertEx.Equal((uint)0x00FFFDB2,
                (uint)consumeEvidence["registers"]["A7"]);
            AssertEx.Equal(child,(ushort)consumeEvidence["native_service_token"]);
            AssertEx.Equal(blocker,(ushort)consumeEvidence["native_parent_token"]);
        }

        private static void RejectsCorruptDeferredBoundaryIdentity()
        {
            var missingApi=new FakeTraceApi();
            var missingHost=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                missingApi.VisitZ80(0x003A,current);
                missingApi.VisitManaged(0x071B4C,current);
            });
            var missingOutput=new StringWriter();
            using(var session=CreateSession(missingHost,missingApi,missingOutput))
            {
                session.ObservePreEpochFrame(859,null,missingHost.Advance);
                AssertEx.Throws<InvalidOperationException>(
                    ()=>session.BeginEpoch(),"managed identity");
                AssertEx.Equal(0,missingOutput.ToString().Length);
            }

            var mismatchApi=new FakeTraceApi();
            var mismatchHost=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                mismatchApi.VisitZ80(0x003A,current);
                Visit(current,mismatchApi,0x071B4C);
                current.SetCpuRegister("A7",0x00FFFDB6);
                Visit(current,mismatchApi,0x071B4C);
            });
            var mismatchOutput=new StringWriter();
            using(var session=CreateSession(mismatchHost,mismatchApi,mismatchOutput))
            {
                AssertEx.Throws<InvalidOperationException>(
                    ()=>session.ObservePreEpochFrame(859,null,mismatchHost.Advance),
                    "identity");
                AssertEx.Equal(0,mismatchOutput.ToString().Length);
            }

            AssertDeferredBoundaryConsumeFails(current=>
                current.SetCpuRegister("A7",0x00FFFDB6));
            AssertDeferredBoundaryConsumeFails(current=>
                current.SetU32(0xFDB2,0x00000B68));
        }

        private static void AssertDeferredBoundaryConsumeFails(
            Action<FakeS1Host> corrupt)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                if(frame==1)
                {
                    api.VisitZ80(0x003A,current);
                    Visit(current,api,0x071B4C);
                    return;
                }
                corrupt(current);
                Visit(current,api,0x071B82);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.ObservePreEpochFrame(859,null,host.Advance);
                AssertEx.Equal(0,output.ToString().Length);
                session.BeginEpoch();
                int baselineLength=output.ToString().Length;
                AssertEx.Throws<InvalidOperationException>(
                    ()=>session.CaptureFrame(860,host.Advance),"identity");
                AssertEx.Equal(baselineLength,output.ToString().Length);
            }
        }

        private static void PublishesEpochExactlyOnceAfterDrainedBoundary()
        {
            var api = new FakeTraceApi { PublicationStatus = -3 };
            var host = new FakeS1Host(null);
            var output = new StringWriter();
            using (var session = CreateSession(host, api, output))
            {
                session.ObservePreEpochFrame(859, null, host.Advance);
                AssertEx.Throws<InvalidOperationException>(
                    () => session.BeginEpoch(), "begin publication epoch failed with status -3");
                AssertEx.Equal(0, output.ToString().Length);

                api.PublicationStatus = 0;
                session.BeginEpoch();
                AssertEx.Throws<InvalidOperationException>(
                    () => session.BeginEpoch(), "already began");
            }
            AssertEx.Equal(2, api.PublicationCalls);
            AssertEx.Equal(1, CountRecords(output.ToString(), "baseline"));
        }

        private static void CorrelatesRequestsToLaterDecisions()
        {
            var api = new FakeTraceApi();
            var host = new FakeS1Host((current, frame) =>
            {
                if (frame == 1)
                {
                    current.SetCpuRegister("D0", 0x81);
                    Visit(current, api, 0x00138E);
                    current.WriteMainRamByte(0xF00A, 0x81);
                    return;
                }
                current.SetCpuRegister("A7", 0x00FFF000);
                Visit(current, api, 0x071B4C);
                current.SetCpuRegister("D4", 2);
                current.SetCpuRegister("A1", 0x00FFF00A);
                current.WriteMainRamByte(0xF009, 0x80);
                Visit(current, api, 0x071F12);
                current.SetCpuRegister("D1", 0x81);
                current.SetCpuRegister("D2", 0x30);
                current.WriteMainRamByte(0xF00A, 0);
                current.WriteMainRamByte(0xF009, 0x81);
                Visit(current, api, 0x071F3E);
                current.SetCpuRegister("D7", 0x81);
                Visit(current, api, 0x071F52);
                Visit(current, api, 0x071FD2);
                Visit(current, api, 0x071C4C);
            });
            var output = new StringWriter();
            using (var session = CreateSession(host, api, output))
            {
                session.CaptureFrame(860, host.Advance);
                session.CaptureFrame(861, host.Advance);
                session.Complete(862);
            }
            JObject request = Record(output.ToString(), "request", 0);
            JObject decision = Record(output.ToString(), "decision", 0);
            JObject dispatch = Record(output.ToString(), "dispatch", 0);
            AssertEx.Equal(860, (int)request["row"]);
            AssertEx.Equal(861, (int)decision["row"]);
            AssertEx.Equal((long)request["request_id"],
                (long)decision["request_id"]);
            AssertEx.Equal((long)request["request_id"],
                (long)dispatch["request_id"]);
            AssertEx.Equal("accepted", (string)decision["outcome"]);
            AssertEx.Equal(129, (int)dispatch["sound_id"]);
        }

        private static void ClosesWithFullStateAndNativeOnlyChipWrites()
        {
            var api = new FakeTraceApi();
            var host = new FakeS1Host((current, frame) =>
            {
                current.SetCpuRegister("A7", 0x00FFF000);
                Visit(current, api, 0x071B4C);
                current.SetCpuRegister("D0", 0x2A);
                Visit(current, api, 0x07273A);
                api.EmitChip(3, 0, 0x2A, 0x07273A);
                current.SetCpuRegister("D1", 0x55);
                Visit(current, api, 0x072752);
                api.EmitChip(3, 1, 0x55, 0x072752);
                current.WriteMainRamByte(0xF000, 0x12);
                current.WriteMainRamByte(0xF3A0, 0x34);
                current.WriteMainRamByte(0xF5BF, 0x56);
                Visit(current, api, 0x071C4C);
            });
            var output = new StringWriter();
            using (var session = CreateSession(host, api, output))
            {
                session.CaptureFrame(860, host.Advance);
                session.Complete(861);
            }
            JObject state = Record(output.ToString(), "managed_service_snapshot", 0);
            string bytes = (string)state["state_hex"];
            AssertEx.Equal(1472 * 2, bytes.Length);
            AssertEx.Equal("12", bytes.Substring(0, 2));
            AssertEx.Equal("34", bytes.Substring((0x3A0) * 2, 2));
            AssertEx.Equal("56", bytes.Substring((0x5BF) * 2, 2));
            AssertEx.Equal(null, state["chip_writes"]);
            AssertEx.Equal(2, CountNativeKind(output.ToString(), 3));
            AssertEx.Equal(0, CountRecords(output.ToString(), "managed_chip_write"));
        }

        private static void RefusesRowGapsContaminationAndOpenTerminal()
        {
            var host = new FakeS1Host(null);
            using (var session = CreateSession(
                host, new FakeTraceApi(), new StringWriter()))
            {
                session.CaptureFrame(860, host.Advance);
                AssertEx.Throws<InvalidOperationException>(
                    () => session.CaptureFrame(862, host.Advance), "row");
                AssertEx.Throws<InvalidOperationException>(
                    () => host.FireExecuteCallback(0x00138E), "contamination");
                AssertEx.Throws<InvalidOperationException>(
                    () => session.Complete(225101), "final row");
            }

            var openApi = new FakeTraceApi();
            var open = new FakeS1Host((current, frame) =>
            {
                current.SetCpuRegister("A7", 0x00FFF000);
                Visit(current, openApi, 0x071B4C);
            });
            using (var session = CreateSession(
                open, openApi, new StringWriter()))
            {
                session.CaptureFrame(860, open.Advance);
                AssertEx.Throws<InvalidOperationException>(
                    () => session.Complete(861), "open");
            }
        }

        private static void ExposesFixedNoReplaceCliMode()
        {
            string scratch = TestScratch.CreateRootPath("s1-audio-cli");
            Directory.CreateDirectory(scratch);
            try
            {
                File.WriteAllText(Path.Combine(scratch, "physics.csv"),
                    "unrelated legacy output");
                string[] args =
                {
                    "--mode", "trace", "--rom", "s1.gen",
                    "--movie", "complete.bk2", "--output", scratch,
                    "--trace-profile",
                    S1CompleteRunAudioReferenceCapture.TraceProfile
                };
                CommandLineOptions options = CommandLineOptions.Parse(args);
                AssertEx.Equal(
                    S1CompleteRunAudioReferenceCapture.TraceProfile,
                    options.TraceProfile);
                File.WriteAllText(Path.Combine(scratch,
                    S1CompleteRunAudioReferenceCapture.RawFileName), "occupied");
                AssertEx.Throws<IOException>(
                    () => CommandLineOptions.Parse(args),
                    S1CompleteRunAudioReferenceCapture.RawFileName);
            }
            finally
            {
                if (Directory.Exists(scratch))
                    Directory.Delete(scratch, true);
            }
        }

        private static void RequiresNativeMarkersForCrossCpuOrder()
        {
            AssertEx.Equal(true,
                S1CompleteRunAudioReferenceCapture.IsManagedCorrelationEventKind(1));
            AssertEx.Equal(true,
                S1CompleteRunAudioReferenceCapture.IsManagedCorrelationEventKind(2));
            AssertEx.Equal(true,
                S1CompleteRunAudioReferenceCapture.IsManagedCorrelationEventKind(10));
            AssertEx.Equal(false,
                S1CompleteRunAudioReferenceCapture.IsManagedCorrelationEventKind(11));
            var tracker = new S1CompleteRunAudioReferenceCapture.ManagedServiceTracker();
            tracker.Begin(3, 0xFF1000);
            tracker.Begin(5, 0xFF1000);
            AssertEx.Equal(2, tracker.Count);
            AssertEx.Equal(true, tracker.Matches(3, 0xFF1000));
            AssertEx.Equal(true, tracker.Matches(5, 0xFF1000));
            AssertEx.Equal(false, tracker.Matches(5, 0xFF1004));
            tracker.End(5);
            AssertEx.Equal(1, tracker.Count);
            AssertEx.Equal((ushort)3, tracker.SingleToken);
            tracker.End(3);
            AssertEx.Equal(0, tracker.Count);
            AssertEx.Throws<InvalidOperationException>(
                () => tracker.End(3), "no open managed M68K service token");
            for (ushort token=1;token<=8;token++)
                tracker.Begin(token, (uint)(0xFF1000+token*4));
            AssertEx.Throws<InvalidOperationException>(
                () => tracker.Begin(9, 0xFF2000), "overflowed");
            AssertEx.Throws<InvalidOperationException>(
                () => tracker.Begin(8, 0xFF2000), "reused");
            tracker.Clear();
            var api = new FakeTraceApi
            {
                Events = new[]
                {
                    NativeEvent(0, 1, 1, 2, 0x0077),
                    NativeEvent(1, 3, 1, 2, 0x0089, 0x2A),
                    SnapshotEvent(2, 5, 0, 0, 0),
                    SnapshotEvent(3, 6, 0, 1, 0x55),
                    SnapshotEvent(4, 7, 1, 0, 0),
                    NativeEvent(5, 2, 1, 2, 0x00AC)
                }
            };
            var host = new FakeS1Host((current, frame) =>
            {
                current.SetCpuRegister("D0", 0x81);
                Visit(current, api, 0x00138E);
            });
            var output = new StringWriter();
            using (var session = CreateSession(host, api, output))
            {
                session.CaptureFrame(860, host.Advance);
                session.Complete(861);
            }
            string raw = output.ToString();
            int native = raw.IndexOf("\"type\":\"native_event\"",
                StringComparison.Ordinal);
            int managed = raw.IndexOf("\"type\":\"managed_hook_evidence\"",
                StringComparison.Ordinal);
            AssertEx.Equal(true, native >= 0 && native < managed);
            AssertEx.Equal(1, CountRecords(raw, "managed_hook_evidence"));
            JObject evidence = Record(raw, "managed_hook_evidence", 0);
            AssertEx.Equal(0L, (long)evidence["managed_correlation_ordinal"]);
            AssertEx.Equal(1,
                ((JArray)evidence["native_correlation_events"]).Count);
            AssertEx.Equal((uint)evidence["native_ordinal"],
                (uint)evidence["native_correlation_events"][0]["ordinal"]);
        }

        private static void RejectsMalformedNativeCorrelations()
        {
            var missingApi = new FakeTraceApi();
            var missing = new FakeS1Host((current, frame) =>
                current.FireExecuteCallback(0x00138E));
            using (var session = CreateSession(
                missing, missingApi, new StringWriter()))
            {
                AssertEx.Throws<InvalidOperationException>(
                    () => session.CaptureFrame(860, missing.Advance),
                    "no native ordered marker");
            }

            var extraApi = new FakeTraceApi
            {
                Events = new[] { NativeMarkerEvent(0, 100, 0x00138E) }
            };
            var idle = new FakeS1Host(null);
            using (var session = CreateSession(
                idle, extraApi, new StringWriter()))
            {
                AssertEx.Throws<InvalidOperationException>(
                    () => session.CaptureFrame(860, idle.Advance),
                    "no managed callback");
            }

            GpgxAudioTraceEvent wrong = NativeMarkerEvent(0, 100, 0x00138E);
            wrong.Value = 2;
            var wrongApi = new FakeTraceApi { Events = new[] { wrong } };
            var wrongHost = new FakeS1Host((current, frame) =>
                current.FireExecuteCallback(0x00138E));
            using (var session = CreateSession(
                wrongHost, wrongApi, new StringWriter()))
            {
                AssertEx.Throws<InvalidOperationException>(
                    () => session.CaptureFrame(860, wrongHost.Advance),
                    "retry marker");
            }

            var orderApi = new FakeTraceApi
            {
                Events = new[]
                {
                    NativeMarkerEvent(0, 100, 0x00138E),
                    NativeMarkerEvent(1, 103, 0x001394)
                }
            };
            var reversed = new FakeS1Host((current, frame) =>
            {
                current.FireExecuteCallback(0x001394);
                current.FireExecuteCallback(0x00138E);
            });
            using (var session = CreateSession(
                reversed, orderApi, new StringWriter()))
            {
                AssertEx.Throws<InvalidOperationException>(
                    () => session.CaptureFrame(860, reversed.Advance),
                    "order or PC");
            }


            var conditionalApi = new FakeTraceApi();
            var conditional = new FakeS1Host((current, frame) =>
            {
                current.SetCpuRegister("A7", 0x00FFF000);
                Visit(current, conditionalApi, 0x071B4C);
                current.SetU32(0xF000, 0x00010000);
                Visit(current, conditionalApi, 0x072C24);
                conditionalApi.RemoveFirst(value => value.Kind == 10
                    && value.Value == 1);
            });
            using (var session = CreateSession(
                conditional, conditionalApi, new StringWriter()))
            {
                AssertEx.Throws<InvalidOperationException>(
                    () => session.CaptureFrame(860, conditional.Advance),
                    "conditional completion");
            }
        }

        private static void PreservesLifoCrossCpuNesting()
        {
            var api = new FakeTraceApi();
            var host = new FakeS1Host((current, frame) =>
            {
                current.SetCpuRegister("A7", 0x00FFF000);
                if (frame == 1)
                {
                    Visit(current, api, 0x071B4C);
                    api.VisitZ80(0x0077, current);
                    api.EmitChip(3, 1, 0x2A, 0x0089, 1);
                    api.VisitZ80(0x00AC, current);
                    Visit(current, api, 0x071FD2);
                    Visit(current, api, 0x071C4C);
                    return;
                }
                api.VisitZ80(0x0077, current);
                current.SetCpuRegister("D0", 0x81);
                Visit(current, api, 0x00138E);
                api.EmitChip(3, 1, 0x33, 0x0089, 1);
                Visit(current, api, 0x071B4C);
                Visit(current, api, 0x071FD2);
                Visit(current, api, 0x071C4C);
                api.EmitChip(3, 1, 0x44, 0x0089, 1);
                api.VisitZ80(0x00AC, current);
            });
            var output = new StringWriter();
            using (var session = CreateSession(host, api, output))
            {
                session.CaptureFrame(860, host.Advance);
                session.CaptureFrame(861, host.Advance);
                session.Complete(862);
            }

            List<JObject> first = NativeRecords(output.ToString(), 860);
            int mBegin = FindNative(first, value => (int)value["kind"] == 1
                && (uint)value["pc"] == 0x071B4C);
            int zChildBegin = FindNative(first, value => (int)value["kind"] == 1
                && (uint)value["pc"] == 0x0077);
            int zChildEnd = FindNative(first, value => (int)value["kind"] == 2
                && (uint)value["pc"] == 0x00AC);
            int internalMarker = FindNative(first, value => (int)value["kind"] == 10
                && (uint)value["pc"] == 0x071FD2);
            int mEnd = FindNative(first, value => (int)value["kind"] == 2
                && (uint)value["pc"] == 0x071C4C);
            ushort mToken = (ushort)first[mBegin]["service_token"];
            AssertEx.Equal(true, mBegin < zChildBegin && zChildBegin < zChildEnd
                && zChildEnd < internalMarker && internalMarker < mEnd);
            AssertEx.Equal(mToken, (ushort)first[zChildBegin]["parent_token"]);
            AssertEx.Equal(mToken, (ushort)first[internalMarker]["service_token"]);

            List<JObject> second = NativeRecords(output.ToString(), 861);
            int zParentBegin = FindNative(second, value => (int)value["kind"] == 1
                && (uint)value["pc"] == 0x0077);
            int queueMarker = FindNative(second, value => (int)value["kind"] == 10
                && (uint)value["pc"] == 0x00138E);
            int mChildBegin = FindNative(second, value => (int)value["kind"] == 1
                && (uint)value["pc"] == 0x071B4C);
            int mChildEnd = FindNative(second, value => (int)value["kind"] == 2
                && (uint)value["pc"] == 0x071C4C);
            int resumedChip = FindNative(second, value => (int)value["kind"] == 3
                && (int)value["value"] == 0x44);
            int zParentEnd = FindNative(second, value => (int)value["kind"] == 2
                && (uint)value["pc"] == 0x00AC);
            ushort zToken = (ushort)second[zParentBegin]["service_token"];
            AssertEx.Equal(true, zParentBegin < queueMarker
                && queueMarker < mChildBegin && mChildBegin < mChildEnd
                && mChildEnd < resumedChip && resumedChip < zParentEnd);
            AssertEx.Equal(zToken, (ushort)second[queueMarker]["service_token"]);
            AssertEx.Equal(zToken, (ushort)second[mChildBegin]["parent_token"]);
            AssertEx.Equal(zToken, (ushort)second[resumedChip]["service_token"]);
        }

        private static void BindsResetEvidenceToNativeGroups()
        {
            RunResetCase(false, true, true, 1);
            RunResetCase(true, false, true, 1);
            RunResetCase(true, true, true, 2);
            RunResetCase(false, true, false, 1);

            var mismatchApi = new FakeTraceApi();
            var mismatch = new FakeS1Host((current, frame) =>
                mismatchApi.EmitReset(false, current));
            using (var session = CreateSession(
                mismatch, mismatchApi, new StringWriter()))
            {
                AssertEx.Throws<InvalidOperationException>(() => session.CaptureFrame(
                    860, new Bk2Frame { Power=true }, mismatch.Advance),
                    "power kind");
            }

            var missingApi = new FakeTraceApi();
            var idle = new FakeS1Host(null);
            using (var session = CreateSession(
                idle, missingApi, new StringWriter()))
            {
                AssertEx.Throws<InvalidOperationException>(() => session.CaptureFrame(
                    860, new Bk2Frame { Reset=true }, idle.Advance),
                    "no native ordered reset lifecycle");
            }
        }

        private static void RunResetCase(
            bool power, bool reset, bool openService, int expectedGroups)
        {
            var api = new FakeTraceApi();
            var host = new FakeS1Host((current, frame) =>
            {
                current.SetCpuRegister("A7", 0x00FFF000);
                if (frame == 1)
                {
                    if (openService) Visit(current, api, 0x071B4C);
                    return;
                }
                if (power) api.EmitReset(true, current);
                if (reset) api.EmitReset(false, current);
                Visit(current, api, 0x071B4C);
                Visit(current, api, 0x071C4C);
            });
            var output = new StringWriter();
            using (var session = CreateSession(host, api, output))
            {
                session.CaptureFrame(860, host.Advance);
                session.CaptureFrame(861,
                    new Bk2Frame { Power=power, Reset=reset }, host.Advance);
                session.Complete(862);
            }
            string raw = output.ToString();
            AssertEx.Equal(expectedGroups, CountRecords(raw, "input_reset"));
            AssertEx.Equal(openService ? 1 : 0,
                CountRecords(raw, "managed_reset_service_snapshot"));
            List<JObject> events = NativeRecords(raw, 861);
            int group = 0;
            int at = 0;
            while (at < events.Count)
            {
                if ((int)events[at]["kind"] != 8) { at++; continue; }
                int begin = at++;
                int cancellationCount = 0;
                while (at < events.Count && (int)events[at]["kind"] != 9)
                {
                    if ((int)events[at]["kind"] == 2
                        && (((int)events[at]["flags"] & 2) != 0))
                        cancellationCount++;
                    at++;
                }
                AssertEx.Equal(true, at < events.Count);
                AssertEx.Equal((ushort)events[begin]["service_token"],
                    (ushort)events[at]["service_token"]);
                if (openService && group == 0)
                    AssertEx.Equal(true, cancellationCount >= 1);
                if (group > 0) AssertEx.Equal(0, cancellationCount);
                group++;
                at++;
            }
            AssertEx.Equal(expectedGroups, group);
        }

        private static S1CompleteRunAudioReferenceCapture.Session CreateSession(
            FakeS1Host host, FakeTraceApi api, TextWriter output)
        {
            return new S1CompleteRunAudioReferenceCapture.Session(
                host, new StrictM68kRegisterReader(host), api,
                S1CompleteRunAudioReferenceCapture.LoadManifest(
                    FixturePath(), RomForManifest(FixturePath())), output);
        }

        private static void UsesPinnedM68kDebuggerRegisterNames()
        {
            var api = new FakeTraceApi();
            var host = new FakeS1Host((current, frame) =>
            {
                current.SetCpuRegister("D0", 0x81);
                Visit(current, api, 0x00138E);
            });
            var output = new StringWriter();
            using (var session = new S1CompleteRunAudioReferenceCapture.Session(
                host, new StrictM68kRegisterReader(host), api,
                S1CompleteRunAudioReferenceCapture.LoadManifest(
                    FixturePath(), RomForManifest(FixturePath())), output))
            {
                session.CaptureFrame(860, host.Advance);
                session.Complete(861);
            }
            AssertEx.Equal(0x81, (int)Record(output.ToString(), "request", 0)["sound_id"]);
        }

        private static void ReportsNativeFailureRow()
        {
            var api = new FakeTraceApi { EndFrameStatus = -3 };
            var host = new FakeS1Host((current, frame) => { });
            using (var session = CreateSession(host, api, new StringWriter()))
            {
                AssertEx.Throws<InvalidOperationException>(
                    () => session.CaptureFrame(860, host.Advance),
                    "row 860: GPGX audio observer end frame failed with status -3");
            }
        }

        private sealed class StrictM68kRegisterReader : ICpuRegisterReader
        {
            private readonly FakeS1Host host;

            internal StrictM68kRegisterReader(FakeS1Host host)
            {
                this.host = host;
            }

            public uint ReadCpuRegister(string name)
            {
                const string prefix = "M68K ";
                if (!name.StartsWith(prefix, StringComparison.Ordinal))
                    throw new InvalidOperationException(
                        "GPGX did not expose CPU register '" + name + "'.");
                return host.ReadCpuRegister(name.Substring(prefix.Length));
            }
        }

        private static void Visit(FakeS1Host host, FakeTraceApi api, uint pc)
        {
            host.FireExecuteCallback(pc);
            api.VisitManaged(pc, host);
        }

        private static void AssertHook(
            S1CompleteRunAudioReferenceCapture.Manifest manifest,
            uint pc, string opcode, string label, string action)
        {
            S1CompleteRunAudioReferenceCapture.ManagedHook hook =
                manifest.FindManagedHook(pc);
            AssertEx.Equal(opcode, hook.OpcodeHex);
            AssertEx.Equal(label, hook.SourceLabel);
            AssertEx.Equal(action, hook.Action);
        }

        private static void AssertNative(
            S1CompleteRunAudioReferenceCapture.Manifest manifest,
            uint pc, string opcode, string label, string action)
        {
            foreach (S1CompleteRunAudioReferenceCapture.NativeHook hook
                in manifest.NativeByPc[pc])
            {
                if (hook.Action != action) continue;
                AssertEx.Equal(opcode, hook.OpcodeHex);
                AssertEx.Equal(label, hook.SourceLabel);
                return;
            }
            throw new InvalidOperationException("Missing native action " + action + ".");
        }

        private static string FixturePath()
        {
            return Path.Combine(AppDomain.CurrentDomain.BaseDirectory,
                "fixtures", FixtureName);
        }

        private static string WithMaximumRecords(int maximum)
        {
            JObject root = JObject.Parse(File.ReadAllText(FixturePath()));
            root["raw_stream"]["max_records_per_frame"] = maximum;
            return WriteScratch(root.ToString());
        }

        private static string WriteScratch(string contents)
        {
            string path = Path.Combine(Path.GetTempPath(),
                "openggf-s1-audio-manifest-" + Guid.NewGuid().ToString("N")
                + ".json");
            File.WriteAllText(path, contents);
            return path;
        }

        private static byte[] RomForManifest(string path)
        {
            JObject root = JObject.Parse(File.ReadAllText(path));
            var rom = new byte[0x80000];
            foreach (JToken token in (JArray)root["m68k_hooks"])
            {
                int pc = (int)token["pc"];
                string opcode = (string)token["opcode"];
                for (int i = 0; i < opcode.Length / 2; i++)
                {
                    rom[pc + i] = Convert.ToByte(
                        opcode.Substring(i * 2, 2), 16);
                }
            }
            JObject arm = (JObject)root["native_observer"]["arm_service"];
            foreach (string name in new[] { "begin", "completion" })
            {
                JToken token = arm[name];
                int pc = (int)token["pc"];
                string opcode = (string)token["opcode"];
                for (int i = 0; i < opcode.Length / 2; i++)
                    rom[pc+i] = Convert.ToByte(opcode.Substring(i*2,2),16);
            }
            return rom;
        }

        private static int CountRecords(string jsonl, string type)
        {
            return CountRecords(jsonl, type, null);
        }

        private static int CountRecords(string jsonl, string type, uint? pc)
        {
            int count = 0;
            using (var reader = new StringReader(jsonl))
            {
                string line;
                while ((line = reader.ReadLine()) != null)
                {
                    JObject record = JObject.Parse(line);
                    if ((string)record["type"] == type
                        && (!pc.HasValue || (uint)record["pc"] == pc.Value))
                    {
                        count++;
                    }
                }
            }
            return count;
        }

        private static int CountNativeKind(string jsonl, int kind)
        {
            return CountNative(jsonl, record => (int)record["kind"] == kind);
        }

        private static int CountNativeMarkerValue(string jsonl, int value)
        {
            return CountNative(jsonl, record => (int)record["kind"] == 10
                && (int)record["value"] == value);
        }

        private static int CountNative(string jsonl, Func<JObject, bool> predicate)
        {
            int count = 0;
            using (var reader = new StringReader(jsonl))
            {
                string line;
                while ((line = reader.ReadLine()) != null)
                {
                    JObject record = JObject.Parse(line);
                    if ((string)record["type"] == "native_event"
                        && predicate(record)) count++;
                }
            }
            return count;
        }

        private static List<JObject> NativeRecords(string jsonl, int row)
        {
            var result = new List<JObject>();
            using (var reader = new StringReader(jsonl))
            {
                string line;
                while ((line = reader.ReadLine()) != null)
                {
                    JObject record = JObject.Parse(line);
                    if ((string)record["type"] == "native_event"
                        && (int)record["row"] == row) result.Add(record);
                }
            }
            return result;
        }

        private static int FindNative(
            List<JObject> records, Func<JObject, bool> predicate)
        {
            for (int i = 0; i < records.Count; i++)
                if (predicate(records[i])) return i;
            throw new InvalidOperationException("Missing expected native event.");
        }

        private static JObject Record(string jsonl, string type, int ordinal)
        {
            int found = 0;
            using (var reader = new StringReader(jsonl))
            {
                string line;
                while ((line = reader.ReadLine()) != null)
                {
                    JObject value = JObject.Parse(line);
                    if ((string)value["type"] != type) continue;
                    if (found++ == ordinal) return value;
                }
            }
            throw new InvalidOperationException("Missing raw record type " + type + ".");
        }

        private static List<JObject> Records(string jsonl,string type,
            Func<JObject,bool> predicate)
        {
            var result=new List<JObject>();
            using(var reader=new StringReader(jsonl))
            {
                string line;
                while((line=reader.ReadLine())!=null)
                {
                    JObject value=JObject.Parse(line);
                    if((string)value["type"]==type&&predicate(value))result.Add(value);
                }
            }
            return result;
        }

        private static GpgxAudioTraceEvent NativeEvent(
            uint ordinal, byte kind, ushort token, byte serviceKind,
            uint pc, byte value = 0)
        {
            ushort subject = pc == 0x0077 ? (ushort)1
                : pc == 0x00AC ? (ushort)2
                : pc == 0x00C1 ? (ushort)3 : (ushort)4;
            return new GpgxAudioTraceEvent
            {
                Ordinal = ordinal,
                Kind = kind,
                ServiceToken = token,
                ServiceKindId = serviceKind,
                SourceCpu = 1,
                Pc = pc,
                Subject = kind == 4 ? (ushort)0
                    : kind == 3 ? (ushort)1 : subject,
                Value = value
            };
        }

        private static GpgxAudioTraceEvent SnapshotEvent(
            uint ordinal, byte kind, ushort offset,
            byte payloadLength, ulong payload)
        {
            return new GpgxAudioTraceEvent
            {
                Ordinal=ordinal, Kind=kind, ServiceToken=1,
                ServiceKindId=2, SourceCpu=1, Pc=0x00AC,
                Subject=2, Offset=offset,
                PayloadLength=payloadLength, Payload=payload
            };
        }

        private static GpgxAudioTraceEvent NativeMarkerEvent(
            uint ordinal, ushort markerToken, uint pc)
        {
            return new GpgxAudioTraceEvent
            {
                Ordinal=ordinal, Kind=10, SourceCpu=2,
                Subject=markerToken, Pc=pc, Value=3
            };
        }

        private sealed class FakeTraceApi : IGpgxAudioTraceApi
        {
            internal int EndFrameStatus;
            internal int EventCountStatus;
            private sealed class ActiveService
            {
                internal ushort Token;
                internal ushort Parent;
                internal byte Kind;
                internal byte Depth;
            }

            public readonly List<string> Calls = new List<string>();
            public GpgxAudioTraceEvent[] Events = new GpgxAudioTraceEvent[0];
            public int PublicationStatus;
            public int PublicationCalls;
            private readonly List<GpgxAudioTraceEvent> frameEvents =
                new List<GpgxAudioTraceEvent>();
            private readonly List<ActiveService> active =
                new List<ActiveService>();
            private GpgxAudioObserverAdapter.ServiceHook[] hooks;
            private GpgxAudioObserverAdapter.SnapshotRange[] ranges;
            private GpgxAudioObserverAdapter.ServiceKind[] kinds;
            private GpgxAudioObserverAdapter.ServiceHook deferredReserveHook;
            private GpgxAudioObserverAdapter.ServiceHook deferredConsumeHook;
            private bool hasDeferredPair;
            private bool deferredPending;
            private ushort nextServiceToken = 1;
            public uint AbiVersion { get { return 3; } }
            public uint EventSize { get { return 32; } }
            public uint Capacity { get { return 65536; } }
            public int Configure(ref GpgxAudioObserverAdapter.Config config,
                byte[] mask, GpgxAudioObserverAdapter.ServiceKind[] kinds,
                GpgxAudioObserverAdapter.ServiceHook[] hooks,
                GpgxAudioObserverAdapter.SnapshotRange[] ranges)
            {
                Calls.Add("configure");
                if (config.AbiVersion != 3 || config.StructSize != 64
                    || config.HookSize != 32 || config.RangeSize != 16
                    || config.EventSize != 32 || config.KindSize != 16
                    || config.Flags != 1 || config.MaxContinuationFrames != 255
                    || config.RangeCount == 0 || ranges == null
                    || ranges.Length != config.RangeCount) return -2;
                for (int i = 0; i < kinds.Length; i++)
                    if (kinds[i].CancellationRangeCount == 0) return -2;
                for (int i = 0; i < hooks.Length; i++)
                {
                    GpgxAudioObserverAdapter.ServiceHook hook = hooks[i];
                    bool expectedKnown = hook.ExpectedActiveKind == 0
                        || Array.Exists(kinds, value => value.KindId
                            == hook.ExpectedActiveKind);
                    bool serviceKnown = hook.ServiceKindId == 0
                        || Array.Exists(kinds, value => value.KindId
                            == hook.ServiceKindId);
                    bool armBegin = hook.Flags == 2 && hook.Action == 1
                        && hook.Cpu == 2 && hook.ServiceKindId == 5
                        && hook.ExpectedActiveKind == 0;
                    bool armEnd = hook.Flags == 3 && hook.Action == 2
                        && hook.Cpu == 2 && hook.ServiceKindId == 0
                        && hook.ExpectedActiveKind == 5;
                    if (!expectedKnown || !serviceKnown
                        || hook.Flags != 0 && !armBegin && !armEnd)
                        return -2;
                    if (hook.Action == 1)
                    {
                        if (hook.ServiceKindId == 0 || hook.RangeCount != 0
                            || hook.Reserved != 0) return -2;
                    }
                    else if (hook.Action == 4)
                    {
                        if (hook.ServiceKindId == 0 || hook.ExpectedActiveKind == 0
                            || hook.RangeCount == 0 || hook.Reserved != 0)
                            return -2;
                    }
                    else if (hook.Action == 2 || hook.Action == 5)
                    {
                        if (hook.ServiceKindId != 0 || hook.RangeCount == 0)
                            return -2;
                        GpgxAudioObserverAdapter.ServiceKind ended =
                            Array.Find(kinds, value => value.KindId
                                == hook.ExpectedActiveKind);
                        if (ended.KindId == 0
                            || ended.CancellationRangeFirst
                                != hook.RangeFirst
                            || ended.CancellationRangeCount
                                != hook.RangeCount) return -2;
                        if (hook.Action == 2 && hook.Reserved != 0) return -2;
                        if (hook.Action == 5)
                        {
                            ushort first = (ushort)(hook.Reserved & 0xFFFF);
                            ushort count = (ushort)((hook.Reserved >> 16) & 0xFFFF);
                            if (hook.Cpu != 2 || count == 0
                                || (hook.Reserved >> 32) != 0
                                || first + count > ranges.Length) return -2;
                            for (int j = 0; j < count; j++)
                                if (ranges[first+j].Flags != 1) return -2;
                        }
                    }
                    else if (hook.Action == 6)
                    {
                        if (hook.Cpu != 2 || hook.ExpectedActiveKind == 0
                            || hook.ServiceKindId != 0 || hook.RangeCount != 0
                            || hook.Reserved != 0) return -2;
                    }
                    else if (hook.Action == 10)
                    {
                        if (hook.Cpu != 2 || hook.ExpectedActiveKind == 0
                            || hook.ServiceKindId == 0
                            || hook.ServiceKindId == hook.ExpectedActiveKind
                            || hook.RangeCount != 0 || hook.Flags != 0
                            || hook.Reserved != 0) return -2;
                    }
                    else if(hook.Action==11)
                    {
                        GpgxAudioObserverAdapter.ServiceKind blocker=Array.Find(
                            kinds,value=>value.KindId==hook.ExpectedActiveKind);
                        if(hook.Cpu!=2||hook.ServiceKindId==0
                            ||hook.ExpectedActiveKind==0||(blocker.Flags&4)!=0
                            ||hook.RangeCount!=0||hook.Flags!=0||hook.Reserved!=0)
                            return -2;
                    }
                    else if(hook.Action==12)
                    {
                        GpgxAudioObserverAdapter.ServiceKind blocker=Array.Find(
                            kinds,value=>value.KindId==hook.ExpectedActiveKind);
                        if(hook.Cpu!=2||hook.ServiceKindId==0
                            ||hook.ExpectedActiveKind==0||(blocker.Flags&4)!=0
                            ||hook.RangeCount!=0||hook.Flags!=0||hook.Reserved!=0)
                            return -2;
                    }
                    else if (hook.Action == 7)
                    {
                        if (hook.Cpu != 2 || hook.ServiceKindId != 0
                            || hook.RangeCount != 0 || hook.Reserved != 0)
                            return -2;
                    }
                    else if (hook.Action == 8 || hook.Action == 9)
                    {
                        GpgxAudioObserverAdapter.ServiceKind ended =
                            Array.Find(kinds, value => value.KindId == hook.ServiceKindId);
                        if ((hook.Cpu != 1 && hook.Cpu != 2) || hook.ServiceKindId == 0
                            || hook.ExpectedActiveKind == 0
                            || hook.ServiceKindId == hook.ExpectedActiveKind
                            || ended.KindId == 0 || hook.RangeCount == 0
                            || ended.CancellationRangeFirst != hook.RangeFirst
                            || ended.CancellationRangeCount != hook.RangeCount
                            || (hook.Action == 8 && hook.Reserved != 0)) return -2;
                        if (hook.Action == 9)
                        {
                            ushort first=(ushort)(hook.Reserved&0xFFFF);
                            ushort count=(ushort)((hook.Reserved>>16)&0xFFFF);
                            if (hook.Cpu!=2||count==0||(hook.Reserved>>32)!=0
                                ||first+count>ranges.Length) return -2;
                            for (int j=0;j<count;j++)
                                if (ranges[first+j].Flags!=1) return -2;
                        }
                    }
                    else return -2;
                }
                this.hooks = (GpgxAudioObserverAdapter.ServiceHook[])hooks.Clone();
                this.ranges = (GpgxAudioObserverAdapter.SnapshotRange[])ranges.Clone();
                this.kinds = (GpgxAudioObserverAdapter.ServiceKind[])kinds.Clone();
                int reserveCount=0,consumeCount=0;
                for(int i=0;i<hooks.Length;i++)
                {
                    if(hooks[i].Action==11)
                    {deferredReserveHook=hooks[i];reserveCount++;}
                    else if(hooks[i].Action==12)
                    {deferredConsumeHook=hooks[i];consumeCount++;}
                }
                hasDeferredPair=reserveCount==1&&consumeCount==1
                    &&deferredReserveHook.ServiceKindId==deferredConsumeHook.ServiceKindId
                    &&deferredReserveHook.ExpectedActiveKind
                        ==deferredConsumeHook.ExpectedActiveKind;
                if((reserveCount!=0||consumeCount!=0)&&!hasDeferredPair)return -2;
                deferredPending=false;
                return 0;
            }
            public int BeginFrame()
            {
                Calls.Add("begin");
                frameEvents.Clear();
                for (int i = 0; i < Events.Length; i++)
                {
                    frameEvents.Add(Events[i]);
                    ReplaySeed(Events[i]);
                    if (Events[i].ServiceToken >= nextServiceToken)
                        nextServiceToken = checked((ushort)(Events[i].ServiceToken + 1));
                }
                Events = new GpgxAudioTraceEvent[0];
                return 0;
            }
            public int EndFrame() { Calls.Add("end"); return EndFrameStatus; }
            public int EventCount(out uint count, out uint overflow)
            { Calls.Add("count"); count=(uint)frameEvents.Count; overflow=0;
                return EventCountStatus; }
            public int Drain(GpgxAudioTraceEvent[] events, uint capacity,
                out uint count)
            { Calls.Add("drain:"+capacity); count=(uint)frameEvents.Count;
                if(events!=null)frameEvents.CopyTo(events);return 0; }
            public int AbortFrame() { Calls.Add("abort"); return 0; }
            public int Disable() { Calls.Add("disable"); return 0; }
            public int GetFirstFault(out GpgxAudioObserverAdapter.FirstFault fault)
            { Calls.Add("fault"); fault=default(GpgxAudioObserverAdapter.FirstFault); return 0; }
            public int BeginPublicationEpoch()
            {
                Calls.Add("publication");
                PublicationCalls++;
                return PublicationStatus;
            }

            internal void VisitManaged(uint pc, FakeS1Host host)
            {
                byte activeKind = active.Count == 0
                    ? (byte)0 : active[active.Count-1].Kind;
                GpgxAudioObserverAdapter.ServiceHook hook = default(
                    GpgxAudioObserverAdapter.ServiceHook);
                bool found = false;
                bool directParentOverride = false;
                for (int i = 0; i < hooks.Length; i++)
                {
                    if (hooks[i].Cpu == 2 && hooks[i].Pc == pc
                        && hooks[i].ExpectedActiveKind == activeKind)
                    {
                        bool directParent = hooks[i].Action == 10
                            && active.Count >= 2
                            && active[active.Count-2].Kind
                                == hooks[i].ServiceKindId;
                        if (directParent)
                        {
                            hook = hooks[i];
                            found = true;
                            directParentOverride = true;
                            continue;
                        }
                        if (directParentOverride) continue;
                        if (hooks[i].Action == 10) continue;
                        if (found)
                            throw new InvalidOperationException(
                                "Fake native M68K visit was ambiguous.");
                        hook = hooks[i];
                        found = true;
                    }
                }
                if (!found)
                    throw new InvalidOperationException(
                        "Fake native M68K visit had no active-kind alternative.");
                if (hook.Action == 1)
                {
                    Push(hook);
                }
                else if (hook.Action == 2)
                {
                    SnapshotAndPop(hook, host);
                }
                else if (hook.Action == 5)
                {
                    uint returnPc = ReadReturnPc(host);
                    bool keep = returnPc == 0x71BD4 || returnPc == 0x71BE6
                        || returnPc == 0x71BF8 || returnPc == 0x71C10
                        || returnPc == 0x71C22 || returnPc == 0x71C38
                        || returnPc == 0x71C44;
                    Add(Owned(hook, 10, keep ? (byte)0 : (byte)1));
                    if (!keep) SnapshotAndPop(hook, host);
                }
                else if (hook.Action == 9)
                {
                    uint returnPc=ReadReturnPc(host);
                    bool keep=returnPc==0x71BD4||returnPc==0x71BE6
                        ||returnPc==0x71BF8||returnPc==0x71C10
                        ||returnPc==0x71C22||returnPc==0x71C38
                        ||returnPc==0x71C44;
                    if (keep) Add(Owned(hook,10,0));
                    else SnapshotDirectParentAndPromote(hook,host);
                }
                else if (hook.Action == 6)
                {
                    Add(Owned(hook, 10, 2));
                }
                else if (hook.Action == 10)
                {
                    if (active.Count < 2
                        || active[active.Count-2].Kind != hook.ServiceKindId)
                        throw new InvalidOperationException(
                            "Fake native direct-parent retry had no exact parent.");
                    Add(OwnedBy(active[active.Count-2], hook, 10,
                        hook.HookToken, 2));
                }
                else if(hook.Action==11)
                {
                    if(!hasDeferredPair)
                        throw new InvalidOperationException(
                            "Fake native deferred reserve had no consume pair.");
                    Add(Owned(hook,10,4));
                    deferredPending=true;
                }
                else if(hook.Action==12)
                {
                    if(!deferredPending||active.Count!=1
                        ||active[0].Kind!=hook.ExpectedActiveKind)
                        throw new InvalidOperationException(
                            "Fake native deferred consume had no exact reservation.");
                    Push(hook);
                    deferredPending=false;
                }
                else if (hook.Action == 7)
                {
                    Add(Owned(hook, 10, 3));
                }
                else throw new InvalidOperationException(
                    "Fake native M68K visit used an unsupported action.");
            }

            internal void EmitChip(byte kind, ushort subject, byte value, uint pc)
            {
                EmitChip(kind, subject, value, pc, 2);
            }

            internal void EmitChip(
                byte kind, ushort subject, byte value, uint pc, byte sourceCpu)
            {
                if (active.Count == 0 || (kind != 3 && kind != 4))
                    throw new InvalidOperationException(
                        "Fake native chip write had no active service.");
                ActiveService owner = active[active.Count-1];
                Add(new GpgxAudioTraceEvent
                {
                    ServiceToken=owner.Token, ParentToken=owner.Parent,
                    Pc=pc, Subject=subject, Kind=kind,
                    ServiceKindId=owner.Kind, Depth=owner.Depth,
                    SourceCpu=sourceCpu, Value=value
                });
            }

            internal void VisitZ80(uint pc, FakeS1Host host)
            {
                byte activeKind = active.Count == 0
                    ? (byte)0 : active[active.Count-1].Kind;
                GpgxAudioObserverAdapter.ServiceHook hook = default(
                    GpgxAudioObserverAdapter.ServiceHook);
                bool found = false;
                for (int i = 0; i < hooks.Length; i++)
                {
                    if (hooks[i].Cpu == 1 && hooks[i].Pc == pc
                        && hooks[i].ExpectedActiveKind == activeKind)
                    {
                        hook = hooks[i];
                        found = true;
                        break;
                    }
                }
                if (!found)
                    throw new InvalidOperationException(
                        "Fake native Z80 visit had no active-kind alternative.");
                if (hook.Action == 1) Push(hook);
                else if (hook.Action == 2) SnapshotAndPop(hook, host);
                else if(hook.Action==4)SnapshotTailAndPush(hook,host);
                else if(hook.Action==8)
                    SnapshotDirectParentAndPromote(hook,host);
                else throw new InvalidOperationException(
                    "Fake native Z80 visit used an unsupported action.");
            }

            internal void EmitReset(bool power, FakeS1Host host)
            {
                var reset = new ActiveService
                {
                    Token=nextServiceToken++, Kind=1, Depth=0
                };
                Add(new GpgxAudioTraceEvent
                {
                    ServiceToken=reset.Token, Subject=(ushort)active.Count,
                    Kind=8, ServiceKindId=1, SourceCpu=3,
                    Flags=(byte)(power?1:0)
                });
                while (active.Count != 0)
                {
                    ActiveService owner = active[active.Count-1];
                    GpgxAudioObserverAdapter.ServiceKind kind = Array.Find(
                        kinds, value => value.KindId == owner.Kind);
                    EmitResetSnapshot(owner, kind.CancellationRangeFirst,
                        kind.CancellationRangeCount, host);
                    Add(new GpgxAudioTraceEvent
                    {
                        ServiceToken=owner.Token, ParentToken=owner.Parent,
                        Kind=2, ServiceKindId=owner.Kind, Depth=owner.Depth,
                        SourceCpu=3, Flags=2
                    });
                    active.RemoveAt(active.Count-1);
                }
                GpgxAudioObserverAdapter.ServiceKind resetKind = Array.Find(
                    kinds, value => value.KindId == 1);
                EmitResetSnapshot(reset, resetKind.CancellationRangeFirst,
                    resetKind.CancellationRangeCount, host);
                Add(new GpgxAudioTraceEvent
                {
                    ServiceToken=reset.Token, Kind=9, ServiceKindId=1,
                    SourceCpu=3, Flags=(byte)(power?1:0)
                });
            }

            internal void MutateLast(Func<GpgxAudioTraceEvent,
                GpgxAudioTraceEvent> mutate)
            {
                int last = frameEvents.Count-1;
                frameEvents[last] = mutate(frameEvents[last]);
            }

            internal void DuplicateLast()
            {
                GpgxAudioTraceEvent value=frameEvents[frameEvents.Count-1];
                value.Ordinal=(uint)frameEvents.Count;
                frameEvents.Add(value);
            }

            internal void RemoveFirst(
                Func<GpgxAudioTraceEvent, bool> predicate)
            {
                for (int i = 0; i < frameEvents.Count; i++)
                {
                    if (!predicate(frameEvents[i])) continue;
                    frameEvents.RemoveAt(i);
                    for (int j = i; j < frameEvents.Count; j++)
                    {
                        GpgxAudioTraceEvent value = frameEvents[j];
                        value.Ordinal = (uint)j;
                        frameEvents[j] = value;
                    }
                    return;
                }
                throw new InvalidOperationException(
                    "Missing fake native event selected for removal.");
            }

            private void Push(GpgxAudioObserverAdapter.ServiceHook hook)
            {
                ushort parent = active.Count == 0
                    ? (ushort)0 : active[active.Count-1].Token;
                var service = new ActiveService
                {
                    Token=nextServiceToken++, Parent=parent,
                    Kind=hook.ServiceKindId, Depth=(byte)active.Count
                };
                Add(new GpgxAudioTraceEvent
                {
                    ServiceToken=service.Token, ParentToken=service.Parent,
                    Pc=hook.Pc, Subject=hook.HookToken, Kind=1,
                    ServiceKindId=service.Kind, Depth=service.Depth,
                    SourceCpu=hook.Cpu
                });
                active.Add(service);
            }

            private void SnapshotAndPop(
                GpgxAudioObserverAdapter.ServiceHook hook, FakeS1Host host)
            {
                if (active.Count == 0)
                    throw new InvalidOperationException(
                        "Fake native completion had no active service.");
                ActiveService owner = active[active.Count-1];
                GpgxAudioObserverAdapter.SnapshotRange range = ranges[hook.RangeFirst];
                Add(Owned(hook, 5, 0, range.RangeId));
                GpgxAudioTraceEvent chunk = Owned(hook, 6, 0, range.RangeId);
                chunk.PayloadLength = 1;
                chunk.Payload = host.ReadMainRamByte(range.Start);
                Add(chunk);
                GpgxAudioTraceEvent end = Owned(hook, 7, 0, range.RangeId);
                end.Offset = range.Length;
                Add(end);
                Add(Owned(hook, 2, 0, hook.HookToken));
                active.RemoveAt(active.Count-1);
            }

            private void SnapshotTailAndPush(
                GpgxAudioObserverAdapter.ServiceHook hook,FakeS1Host host)
            {
                if(active.Count==0)throw new InvalidOperationException(
                    "Fake native tail had no active service.");
                GpgxAudioObserverAdapter.SnapshotRange range=ranges[hook.RangeFirst];
                Add(Owned(hook,5,0,range.RangeId));
                GpgxAudioTraceEvent chunk=Owned(hook,6,0,range.RangeId);
                chunk.PayloadLength=1;chunk.Payload=host.ReadMainRamByte(range.Start);Add(chunk);
                GpgxAudioTraceEvent snapshotEnd=Owned(hook,7,0,range.RangeId);
                snapshotEnd.Offset=range.Length;Add(snapshotEnd);
                Add(Owned(hook,2,0,hook.HookToken));
                active.RemoveAt(active.Count-1);
                Push(hook);
            }

            private void SnapshotDirectParentAndPromote(
                GpgxAudioObserverAdapter.ServiceHook hook,FakeS1Host host)
            {
                if (active.Count<2)
                    throw new InvalidOperationException(
                        "Fake conditional promotion had no direct parent.");
                ActiveService parent=active[active.Count-2];
                ActiveService child=active[active.Count-1];
                if (parent.Kind!=hook.ServiceKindId
                    ||child.Kind!=hook.ExpectedActiveKind)
                    throw new InvalidOperationException(
                        "Fake conditional promotion stack differs.");
                GpgxAudioObserverAdapter.SnapshotRange range=ranges[hook.RangeFirst];
                Add(OwnedBy(parent,hook,5,range.RangeId));
                GpgxAudioTraceEvent chunk=OwnedBy(parent,hook,6,range.RangeId);
                chunk.PayloadLength=1;
                chunk.Payload=host.ReadMainRamByte(range.Start);
                Add(chunk);
                GpgxAudioTraceEvent snapshotEnd=OwnedBy(parent,hook,7,range.RangeId);
                snapshotEnd.Offset=range.Length;
                Add(snapshotEnd);
                Add(OwnedBy(parent,hook,2,hook.HookToken));
                child.Parent=parent.Parent;
                child.Depth=parent.Depth;
                Add(OwnedBy(child,hook,11,hook.HookToken));
                active.RemoveAt(active.Count-2);
            }

            private static GpgxAudioTraceEvent OwnedBy(ActiveService owner,
                GpgxAudioObserverAdapter.ServiceHook hook,byte kind,ushort subject)
            {
                return OwnedBy(owner, hook, kind, subject, 0);
            }

            private static GpgxAudioTraceEvent OwnedBy(ActiveService owner,
                GpgxAudioObserverAdapter.ServiceHook hook, byte kind,
                ushort subject, byte value)
            {
                return new GpgxAudioTraceEvent
                {
                    ServiceToken=owner.Token,ParentToken=owner.Parent,
                    Pc=hook.Pc,Subject=subject,Kind=kind,
                    ServiceKindId=owner.Kind,Depth=owner.Depth,
                    SourceCpu=hook.Cpu,Value=value
                };
            }

            private void EmitResetSnapshot(ActiveService owner, ushort first,
                ushort count, FakeS1Host host)
            {
                for (int i = 0; i < count; i++)
                {
                    GpgxAudioObserverAdapter.SnapshotRange range = ranges[first+i];
                    Add(new GpgxAudioTraceEvent
                    {
                        ServiceToken=owner.Token, ParentToken=owner.Parent,
                        Subject=range.RangeId, Kind=5, ServiceKindId=owner.Kind,
                        Depth=owner.Depth, SourceCpu=3
                    });
                    var chunk = new GpgxAudioTraceEvent
                    {
                        ServiceToken=owner.Token, ParentToken=owner.Parent,
                        Subject=range.RangeId, Kind=6, ServiceKindId=owner.Kind,
                        Depth=owner.Depth, SourceCpu=3, PayloadLength=1,
                        Payload=host.ReadMainRamByte(range.Start)
                    };
                    Add(chunk);
                    Add(new GpgxAudioTraceEvent
                    {
                        ServiceToken=owner.Token, ParentToken=owner.Parent,
                        Subject=range.RangeId, Offset=range.Length, Kind=7,
                        ServiceKindId=owner.Kind, Depth=owner.Depth, SourceCpu=3
                    });
                }
            }

            private GpgxAudioTraceEvent Owned(
                GpgxAudioObserverAdapter.ServiceHook hook, byte kind, byte value,
                ushort subject = 0)
            {
                if (active.Count == 0)
                {
                    return new GpgxAudioTraceEvent
                    {
                        Pc=hook.Pc, Subject=subject == 0 ? hook.HookToken : subject,
                        Kind=kind, SourceCpu=hook.Cpu, Value=value
                    };
                }
                ActiveService owner = active[active.Count-1];
                return new GpgxAudioTraceEvent
                {
                    ServiceToken=owner.Token, ParentToken=owner.Parent,
                    Pc=hook.Pc, Subject=subject == 0 ? hook.HookToken : subject,
                    Kind=kind, ServiceKindId=owner.Kind, Depth=owner.Depth,
                    SourceCpu=hook.Cpu, Value=value
                };
            }

            private void Add(GpgxAudioTraceEvent value)
            {
                value.Ordinal = (uint)frameEvents.Count;
                frameEvents.Add(value);
            }

            private void ReplaySeed(GpgxAudioTraceEvent value)
            {
                if (value.Kind == 1)
                {
                    active.Add(new ActiveService
                    {
                        Token=value.ServiceToken, Parent=value.ParentToken,
                        Kind=value.ServiceKindId, Depth=value.Depth
                    });
                }
                else if (value.Kind == 2 && active.Count != 0)
                {
                    active.RemoveAt(active.Count-1);
                }
            }

            private static uint ReadReturnPc(FakeS1Host host)
            {
                int offset = (int)(host.ReadCpuRegister("A7") & 0xFFFF);
                return ((uint)host.ReadMainRamByte(offset) << 24)
                    | ((uint)host.ReadMainRamByte((offset+1)&0xFFFF) << 16)
                    | ((uint)host.ReadMainRamByte((offset+2)&0xFFFF) << 8)
                    | host.ReadMainRamByte((offset+3)&0xFFFF);
            }
        }
    }
}
