using System;
using System.Collections.Generic;
using System.IO;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using BizHawk.Headless.Gpgx;
using Newtonsoft.Json.Linq;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class S1CompleteRunAudioReferenceCaptureTests
    {
        private const string FixtureName = "s1-audio-service-manifest-v1.json";
        private const int ProofMaxLineCharacters = 256 * 1024;
        private const int ProofMaxRetainedRecords = 2 * 65536 + 2;
        private const int ProofMaxRetainedCharacters = 16 * 1024 * 1024;

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests pin every reviewed REV01 boundary",
                PinsReviewedRev01Boundaries));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests keep kind six queue observations bounded",
                KeepsKindSixQueueObservationsBounded));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests reject malformed and mismatched manifests",
                RejectsMalformedAndMismatchedManifest));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests bound manifest bytes strings and UTF8",
                BoundsManifestBytesStringsAndUtf8));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests keep retries in one managed service",
                KeepsRetryInOneManagedService));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests resolve adjusted-return exits",
                ResolvesAdjustedReturnExits));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests correlate conditional close topologies and identity",
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
                "S1CompleteRunAudioReferenceCaptureTests correlate deferred tail successor identity",
                CorrelatesDeferredTailSuccessorIdentity));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests carry deferred tail successor across cutoff",
                CarriesDeferredTailSuccessorAcrossCutoff));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests roll back deferred tail successor identity",
                RollsBackDeferredTailSuccessorIdentity));
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
                "S1CompleteRunAudioReferenceCaptureTests rotate publication after same blocker relay",
                RotatesPublicationAfterSameBlockerRelay));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests bound rotating blocker relays",
                BoundsRotatingBlockerRelays));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests enforce held frame and evidence caps",
                EnforcesHeldFrameAndEvidenceCaps));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests make output failures terminal",
                MakesOutputFailuresTerminal));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests preserve successful raw bytes",
                PreservesSuccessfulRawBytes));
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
                "S1CompleteRunAudioReferenceCaptureTests selectively retain bounded proof JSONL",
                SelectivelyRetainsBoundedProofJsonl));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests reject invalid selective proof JSONL",
                RejectsInvalidSelectiveProofJsonl));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests consume one deferred child begin during row 8775 wait service",
                MaterializesDeferredBeginAfterWaitService,
                game: "s1", serial: true, estimatedSeconds: 300.0));
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunAudioReferenceCaptureTests prove row 12525 action 9 keep and promotion",
                ProvesRow12525Action9KeepAndPromotion,
                game: "s1", serial: true, estimatedSeconds: 300.0));
        }

        private sealed class RealPrefixResult
        {
            internal S1CompleteRunAudioReferenceCapture.Manifest Manifest;
            internal IList<JObject> Records;
            internal long TotalRecordCount;
            internal int RetainedCharacterCount;
        }

        private sealed class SelectiveJsonlProofWriter : TextWriter
        {
            private static readonly HashSet<string> RowRecordTypes=
                new HashSet<string>(StringComparer.Ordinal)
                {
                    "baseline", "frame_begin", "input_reset",
                    "managed_reset_service_snapshot", "request", "decision",
                    "managed_service_snapshot", "native_event",
                    "managed_hook_evidence", "dispatch", "frame_end"
                };
            private static readonly HashSet<string> RecordTypes=
                new HashSet<string>(RowRecordTypes,StringComparer.Ordinal)
                {
                    "metadata", "terminal"
                };

            private readonly HashSet<int> retainedRows;
            private readonly int firstRow;
            private readonly int finalRow;
            private readonly bool expectTerminal;
            private readonly int maxLineCharacters;
            private readonly int maxRetainedRecords;
            private readonly int maxRetainedCharacters;
            private readonly System.Text.StringBuilder line=
                new System.Text.StringBuilder();
            private readonly List<JObject> retainedRecords=new List<JObject>();
            private bool metadataSeen;
            private bool baselineSeen;
            private bool terminalSeen;
            private bool faulted;
            private bool finished;
            private long totalRecordCount;
            private int retainedCharacterCount;

            internal SelectiveJsonlProofWriter(int firstRow,int finalRow,
                IEnumerable<int> retainedRows,bool expectTerminal,
                int maxLineCharacters,
                int maxRetainedRecords,int maxRetainedCharacters)
            {
                if(retainedRows==null)throw new ArgumentNullException("retainedRows");
                if(firstRow<0||finalRow<firstRow)
                    throw new ArgumentOutOfRangeException("finalRow");
                if(maxLineCharacters<=0)throw new ArgumentOutOfRangeException(
                    "maxLineCharacters");
                if(maxRetainedRecords<=0)throw new ArgumentOutOfRangeException(
                    "maxRetainedRecords");
                if(maxRetainedCharacters<=0)throw new ArgumentOutOfRangeException(
                    "maxRetainedCharacters");
                this.retainedRows=new HashSet<int>(retainedRows);
                foreach(int row in this.retainedRows)
                    if(row<firstRow||row>finalRow)
                        throw new ArgumentOutOfRangeException("retainedRows");
                this.firstRow=firstRow;
                this.finalRow=finalRow;
                this.expectTerminal=expectTerminal;
                this.maxLineCharacters=maxLineCharacters;
                this.maxRetainedRecords=maxRetainedRecords;
                this.maxRetainedCharacters=maxRetainedCharacters;
            }

            public override System.Text.Encoding Encoding
            {
                get { return new System.Text.UTF8Encoding(false); }
            }

            internal IList<JObject> RetainedRecords
            {
                get { return retainedRecords.AsReadOnly(); }
            }

            internal int RetainedRecordCount
            {
                get { return retainedRecords.Count; }
            }

            internal int RetainedCharacterCount
            {
                get { return retainedCharacterCount; }
            }

            internal long TotalRecordCount
            {
                get { return totalRecordCount; }
            }

            public override void Write(char value)
            {
                EnsureWritable();
                Accept(value);
            }

            public override void Write(char[] buffer,int index,int count)
            {
                if(buffer==null)throw new ArgumentNullException("buffer");
                if(index<0||count<0||buffer.Length-index<count)
                    throw new ArgumentOutOfRangeException();
                EnsureWritable();
                for(int i=0;i<count;i++)Accept(buffer[index+i]);
            }

            public override void Write(string value)
            {
                EnsureWritable();
                if(value==null)return;
                for(int i=0;i<value.Length;i++)Accept(value[i]);
            }

            internal void Finish()
            {
                EnsureWritable();
                if(line.Length!=0)Fail("The selective proof JSONL has a partial non-LF-terminated line.");
                if(!metadataSeen||!baselineSeen)
                    Fail("The selective proof JSONL is missing metadata or baseline.");
                if(terminalSeen!=expectTerminal)
                    Fail("The selective proof JSONL terminal presence is unexpected.");
                finished=true;
            }

            private void EnsureWritable()
            {
                if(faulted)throw new InvalidOperationException(
                    "A selective proof sink failure requires a fresh writer and session.");
                if(finished)throw new InvalidOperationException(
                    "The selective proof JSONL writer is already finished.");
            }

            private void Accept(char value)
            {
                if(value=='\r')
                    Fail("The selective proof JSONL must use LF without CR.");
                if(value=='\n')
                {
                    ProcessLine();
                    line.Length=0;
                    return;
                }
                if(line.Length>=maxLineCharacters)
                    Fail("The selective proof JSONL exceeded its line-character limit.");
                line.Append(value);
            }

            private void ProcessLine()
            {
                JObject record;
                try
                {
                    record=JObject.Parse(line.ToString());
                }
                catch(Exception error)
                {
                    Fail("The selective proof JSONL line is not valid JSON.",error);
                    return;
                }
                JToken typeToken=record["type"];
                if(typeToken==null||typeToken.Type!=JTokenType.String
                    ||!RecordTypes.Contains((string)typeToken))
                    Fail("The selective proof JSONL has an unexpected record type.");
                string type=(string)typeToken;
                if(type=="metadata")
                {
                    if(metadataSeen)Fail("The selective proof JSONL has duplicate metadata.");
                    metadataSeen=true;
                }
                else if(type=="baseline")
                {
                    if(baselineSeen)Fail("The selective proof JSONL has a duplicate baseline.");
                    baselineSeen=true;
                }
                else if(type=="terminal")
                {
                    if(terminalSeen)Fail("The selective proof JSONL has a duplicate terminal.");
                    if(!expectTerminal)
                        Fail("The selective proof JSONL has an unexpected terminal.");
                    long exclusiveEnd=RequireInteger(record["exclusive_end"],
                        "The selective proof JSONL terminal has no allowed integer exclusive_end.");
                    if(exclusiveEnd!=(long)finalRow+1)
                        Fail("The selective proof JSONL terminal exclusive_end is unexpected.");
                    terminalSeen=true;
                }
                if(terminalSeen&&type!="terminal")
                    Fail("The selective proof JSONL has data after terminal.");

                int row=-1;
                if(RowRecordTypes.Contains(type))
                {
                    long parsedRow=RequireInteger(record["row"],
                        "The selective proof JSONL row record has no allowed integer row.");
                    if(parsedRow<firstRow||parsedRow>finalRow)
                        Fail("The selective proof JSONL row is outside the allowed capture range.");
                    row=(int)parsedRow;
                    if(type=="baseline"&&row!=firstRow)
                        Fail("The selective proof JSONL baseline row is unexpected.");
                }
                if(totalRecordCount==long.MaxValue)
                    Fail("The selective proof JSONL exceeded its record-count limit.");
                totalRecordCount++;
                if(type=="baseline"||retainedRows.Contains(row)
                    ||type=="terminal")
                    Retain(record,line.Length+1);
            }

            private long RequireInteger(JToken token,string message)
            {
                if(token==null||token.Type!=JTokenType.Integer)Fail(message);
                try
                {
                    return token.Value<long>();
                }
                catch(Exception error)
                {
                    Fail(message,error);
                    return -1;
                }
            }

            private void Retain(JObject record,int characters)
            {
                if(retainedRecords.Count>=maxRetainedRecords)
                    Fail("The selective proof JSONL exceeded its retained-record limit.");
                if(characters>maxRetainedCharacters-retainedCharacterCount)
                    Fail("The selective proof JSONL exceeded its retained-character limit.");
                retainedRecords.Add(record);
                retainedCharacterCount+=characters;
            }

            private void Fail(string message)
            {
                Fail(message,null);
            }

            private void Fail(string message,Exception inner)
            {
                faulted=true;
                line.Length=0;
                if(inner==null)throw new InvalidDataException(message);
                throw new InvalidDataException(message,inner);
            }
        }

        private sealed class FailingWriter : StringWriter
        {
            internal int RemainingCharacters;

            internal FailingWriter(int remainingCharacters)
                :base(System.Globalization.CultureInfo.InvariantCulture)
            {
                RemainingCharacters=remainingCharacters;
            }

            public override void Write(string value)
            {
                if(value==null)return;
                int accepted=Math.Min(RemainingCharacters,value.Length);
                if(accepted!=0)base.Write(value.Substring(0,accepted));
                RemainingCharacters-=accepted;
                if(accepted!=value.Length)throw new IOException(
                    "injected output failure");
            }
        }

        private static void SelectivelyRetainsBoundedProofJsonl()
        {
            var writer=new SelectiveJsonlProofWriter(
                0,10000,new[]{7},true,256,3,192);
            string metadata="{\"type\":\"metadata\",\"schema\":\"test\"}\n";
            string baseline="{\"type\":\"baseline\",\"row\":0}\n";
            writer.Write(metadata.Substring(0,11));
            writer.Write(metadata.Substring(11)+baseline);
            for(int row=1;row<=10000;row++)
            {
                if(row==7)continue;
                writer.Write("{\"type\":\"native_event\",\"row\":"
                    +row.ToString(System.Globalization.CultureInfo.InvariantCulture)
                    +",\"kind\":10}\n");
            }
            string selected="{\"type\":\"native_event\",\"row\":7,\"kind\":10}\n";
            for(int i=0;i<selected.Length;i++)writer.Write(selected[i]);
            writer.Write("{\"type\":\"terminal\",\"exclusive_end\":10001}\n");
            writer.Finish();

            AssertEx.Equal(10003L,writer.TotalRecordCount);
            AssertEx.Equal(3,writer.RetainedRecordCount);
            AssertEx.Equal(true,writer.RetainedCharacterCount<192);
            AssertEx.Equal("baseline",(string)writer.RetainedRecords[0]["type"]);
            AssertEx.Equal(7,(int)writer.RetainedRecords[1]["row"]);
            AssertEx.Equal("terminal",(string)writer.RetainedRecords[2]["type"]);
        }

        private static void RejectsInvalidSelectiveProofJsonl()
        {
            AssertSelectiveWriterFails(
                writer=>writer.Write("{\"type\":\"native_event\",\"row\":1}\r\n"),
                "LF");
            AssertSelectiveWriterFails(
                writer=>writer.Write("{not-json}\n"),"valid JSON");
            AssertSelectiveWriterFails(
                writer=>writer.Write("{\"type\":\"surprise\",\"row\":1}\n"),
                "record type");
            AssertSelectiveWriterFails(
                writer=>writer.Write("{\"type\":\"native_event\"}\n"),"row");
            AssertSelectiveWriterFails(
                writer=>writer.Write("{\"type\":\"native_event\",\"row\":2}\n"),
                "allowed capture range");
            AssertSelectiveWriterFails(
                writer=>writer.Write(
                    "{\"type\":\"native_event\",\"row\":999999999999999999999999}\n"),
                "allowed integer row");
            AssertSelectiveWriterFails(writer=>
            {
                writer.Write("{\"type\":\"baseline\",\"row\":0}\n");
                writer.Write("{\"type\":\"baseline\",\"row\":0}\n");
            },"duplicate");
            AssertSelectiveWriterFails(writer=>
            {
                writer.Write("{\"type\":\"terminal\",\"exclusive_end\":2}\n");
                writer.Write("{\"type\":\"terminal\",\"exclusive_end\":2}\n");
            },"duplicate");
            AssertSelectiveWriterFails(
                writer=>writer.Write(
                    "{\"type\":\"terminal\",\"exclusive_end\":3}\n"),
                "exclusive_end");
            AssertSelectiveWriterFails(writer=>
            {
                writer.Write("{\"type\":\"terminal\",\"exclusive_end\":2}\n");
                writer.Write("{\"type\":\"native_event\",\"row\":1}\n");
            },"after terminal");
            AssertSelectiveWriterFails(writer=>
                writer.Write(new string('x',257)),"line-character");

            var partial=new SelectiveJsonlProofWriter(
                0,1,new[]{1},true,256,3,192);
            partial.Write("{\"type\":\"native_event\",\"row\":1}");
            AssertEx.Throws<InvalidDataException>(()=>partial.Finish(),"partial");
            AssertEx.Throws<InvalidOperationException>(
                ()=>partial.Write("\n"),"fresh writer and session");

            var recordOverflow=new SelectiveJsonlProofWriter(
                0,1,new[]{1},true,256,1,192);
            recordOverflow.Write("{\"type\":\"baseline\",\"row\":0}\n");
            AssertEx.Throws<InvalidDataException>(()=>recordOverflow.Write(
                "{\"type\":\"native_event\",\"row\":1}\n"),
                "retained-record");
            AssertEx.Throws<InvalidOperationException>(
                ()=>recordOverflow.Write("{\"type\":\"native_event\",\"row\":2}\n"),
                "fresh writer and session");

            var characterOverflow=new SelectiveJsonlProofWriter(
                0,1,new[]{1},true,256,3,27);
            AssertEx.Throws<InvalidDataException>(()=>characterOverflow.Write(
                "{\"type\":\"baseline\",\"row\":0}\n"),
                "retained-character");

            var unexpectedTerminal=new SelectiveJsonlProofWriter(
                0,1,new[]{1},false,256,3,192);
            AssertEx.Throws<InvalidDataException>(()=>unexpectedTerminal.Write(
                "{\"type\":\"terminal\",\"exclusive_end\":2}\n"),
                "unexpected terminal");

            var incomplete=new SelectiveJsonlProofWriter(
                0,1,new[]{1},false,256,2,192);
            incomplete.Write("{\"type\":\"metadata\",\"schema\":\"test\"}\n");
            incomplete.Write("{\"type\":\"baseline\",\"row\":0}\n");
            incomplete.Write("{\"type\":\"native_event\",\"row\":1}\n");
            incomplete.Finish();
            AssertEx.Equal(2,incomplete.RetainedRecordCount);
        }

        private static void AssertSelectiveWriterFails(
            Action<SelectiveJsonlProofWriter> action,string message)
        {
            var writer=new SelectiveJsonlProofWriter(
                0,1,new[]{1},true,256,3,192);
            AssertEx.Throws<InvalidDataException>(()=>action(writer),message);
            AssertEx.Throws<InvalidOperationException>(
                ()=>writer.Write("{\"type\":\"native_event\",\"row\":1}\n"),
                "fresh writer and session");
        }

        private static void MaterializesDeferredBeginAfterWaitService()
        {
            if (Environment.GetEnvironmentVariable("OPENGGF_S1_AUDIO_PREFIX") != "1")
                throw new TestMain.SkipTestException("OPENGGF_S1_AUDIO_PREFIX is not enabled.");
            bool terminalProbe=Environment.GetEnvironmentVariable(
                "OPENGGF_S1_AUDIO_TERMINAL_PROBE")=="1";
            RealPrefixResult result=CaptureRealPrefix(
                terminalProbe?-1:8775,true,new[]{1548,8775});
            S1CompleteRunAudioReferenceCapture.Manifest manifest=result.Manifest;
            JObject baseline = null;
            JObject directParentRetry = null;
            ushort retryToken=0;
            ushort deferredHookToken=0;
            ushort consumeHookToken=0;
            ushort waitBeginHookToken=0;
            ushort waitTailHookToken=0;
            ushort childObservationHookToken=0;
            ushort childEndHookToken=0;
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
                if(hook.Cpu==1&&hook.Pc==0x003A&&hook.Action==1
                    &&hook.ExpectedActiveKind==0&&hook.ServiceKindId==6)
                    waitBeginHookToken=hook.HookToken;
                if(hook.Cpu==1&&hook.Pc==0x0077&&hook.Action==4
                    &&hook.ExpectedActiveKind==6&&hook.ServiceKindId==2)
                    waitTailHookToken=hook.HookToken;
                if(hook.Cpu==2&&hook.Pc==0x071BB2&&hook.Action==7
                    &&hook.ExpectedActiveKind==4)
                    childObservationHookToken=hook.HookToken;
                if(hook.Cpu==2&&hook.Pc==0x071C4C&&hook.Action==2
                    &&hook.ExpectedActiveKind==4)
                    childEndHookToken=hook.HookToken;
            }
            uint waitBeginOrdinal=uint.MaxValue;
            uint waitEndOrdinal=uint.MaxValue;
            uint priorMusicEndOrdinal=uint.MaxValue;
            uint consumeBeginOrdinal=uint.MaxValue;
            uint childObservationOrdinal=uint.MaxValue;
            uint childEndOrdinal=uint.MaxValue;
            uint dpcmBeginOrdinal=uint.MaxValue;
            ushort waitServiceToken=0;
            ushort consumedServiceToken=0;
            ushort dpcmServiceToken=0;
            int deferredBegins=0;
            int childObservations=0;
            int childEnds=0;
            int waitEnds=0;
            var deferredEvidence=new List<JObject>();
            var row8775ManagedEvidence=new List<JObject>();
            var row8775DpcmBegins=new List<JObject>();
            foreach (JObject record in result.Records)
            {
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
                    AssertEx.Equal(1,(int)record["source_cpu"]);
                    AssertEx.Equal(waitBeginHookToken,
                        (ushort)record["subject"]);
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
                    waitEnds++;
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
                    childObservations++;
                    childObservationOrdinal=(uint)record["ordinal"];
                    AssertEx.Equal(consumedServiceToken,
                        (ushort)record["service_token"]);
                    AssertEx.Equal(waitServiceToken,
                        (ushort)record["parent_token"]);
                    AssertEx.Equal(4,(int)record["service_kind"]);
                    AssertEx.Equal(1,(int)record["depth"]);
                    AssertEx.Equal(2,(int)record["source_cpu"]);
                    AssertEx.Equal(childObservationHookToken,
                        (ushort)record["subject"]);
                }
                if((string)record["type"]=="native_event"
                    &&(int)record["row"]==8775&&(int)record["kind"]==2
                    &&(int)record["pc"]==0x071C4C
                    &&(int)record["service_kind"]==4
                    &&(int)record["depth"]==1
                    &&(ushort)record["parent_token"]==waitServiceToken)
                {
                    childEnds++;
                    childEndOrdinal=(uint)record["ordinal"];
                    AssertEx.Equal(consumedServiceToken,
                        (ushort)record["service_token"]);
                    AssertEx.Equal(1,(int)record["depth"]);
                    AssertEx.Equal(2,(int)record["source_cpu"]);
                    AssertEx.Equal(childEndHookToken,
                        (ushort)record["subject"]);
                }
                if((string)record["type"]=="native_event"
                    &&(int)record["row"]==8775&&(int)record["kind"]==1
                    &&(int)record["pc"]==0x0077
                    &&(int)record["service_kind"]==2)
                    row8775DpcmBegins.Add(record);
                if((string)record["type"]=="managed_hook_evidence"
                    &&(int)record["row"]==8775
                    &&(int)record["pc"]==0x071B4C)
                    deferredEvidence.Add(record);
                if((string)record["type"]=="managed_hook_evidence"
                    &&(int)record["row"]==8775)
                    row8775ManagedEvidence.Add(record);
            }
            AssertEx.Equal(true, baseline != null);
            AssertEx.Equal(860, (int)baseline["row"]);
            AssertEx.Equal(true, ((JArray)baseline["active_services"]).Count > 0);
            AssertEx.Equal(true,directParentRetry!=null);
            AssertEx.Equal(4,(int)directParentRetry["service_kind"]);
            AssertEx.Equal(0,(int)directParentRetry["depth"]);
            AssertEx.Equal((uint)12,priorMusicEndOrdinal);
            AssertEx.Equal((uint)13,waitBeginOrdinal);
            AssertEx.Equal(1,waitEnds);
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
            AssertEx.Equal(1,childObservations);
            AssertEx.Equal(1,childEnds);
            int tailDpcmBegins=0;
            foreach(JObject record in row8775DpcmBegins)
            {
                if((uint)record["ordinal"]!=waitEndOrdinal+1)continue;
                tailDpcmBegins++;
                dpcmBeginOrdinal=(uint)record["ordinal"];
                dpcmServiceToken=(ushort)record["service_token"];
                AssertEx.Equal(0,(int)record["parent_token"]);
                AssertEx.Equal(0,(int)record["depth"]);
                AssertEx.Equal(1,(int)record["source_cpu"]);
                AssertEx.Equal(waitTailHookToken,
                    (ushort)record["subject"]);
            }
            AssertEx.Equal(1,tailDpcmBegins);
            AssertEx.Equal(true,consumeBeginOrdinal<childObservationOrdinal
                &&childObservationOrdinal<childEndOrdinal
                &&childEndOrdinal<waitEndOrdinal
                &&waitEndOrdinal<dpcmBeginOrdinal);
            AssertEx.Equal(waitEndOrdinal+1,dpcmBeginOrdinal);
            AssertEx.Equal(true,consumedServiceToken!=0);
            AssertEx.Equal(false,consumedServiceToken==waitServiceToken);
            AssertEx.Equal(true,dpcmServiceToken!=0);
            AssertEx.Equal(false,dpcmServiceToken==waitServiceToken);
            AssertEx.Equal(false,dpcmServiceToken==consumedServiceToken);
            var postChildPreTailHooks=new List<string>();
            foreach(JObject evidence in row8775ManagedEvidence)
            {
                foreach(JObject correlation in
                    (JArray)evidence["native_correlation_events"])
                {
                    uint ordinal=(uint)correlation["ordinal"];
                    if(ordinal>childEndOrdinal&&ordinal<waitEndOrdinal)
                        postChildPreTailHooks.Add(string.Format(
                            "{0}:{1:x6}:{2}:{3}",ordinal,
                            (uint)evidence["pc"],(string)evidence["name"],
                            (string)evidence["action"]));
                }
            }
            AssertEx.Equal("",string.Join("|",postChildPreTailHooks));
            if(terminalProbe)
            {
                List<JObject> terminals=Records(result.Records,"terminal",
                    value=>true);
                AssertEx.Equal(1,terminals.Count);
                AssertEx.Equal(manifest.ExclusiveEnd,
                    (int)terminals[0]["exclusive_end"]);
                AssertEx.Equal(manifest.ExclusiveEnd-manifest.FirstRow,
                    (int)terminals[0]["rows"]);
                Console.WriteLine(
                    "S1_TERMINAL_PROOF total_records={0} retained_records={1} retained_chars={2}",
                    result.TotalRecordCount,result.Records.Count,
                    result.RetainedCharacterCount);
            }
        }

        private static RealPrefixResult CaptureRealPrefix(int finalRow,bool complete,
            IEnumerable<int> retainedRows)
        {
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
            if(finalRow<0)finalRow=manifest.ExclusiveEnd-1;
            var output=new SelectiveJsonlProofWriter(
                manifest.FirstRow,finalRow,retainedRows,complete,
                ProofMaxLineCharacters,ProofMaxRetainedRecords,
                ProofMaxRetainedCharacters);
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
                if(complete)session.Complete(finalRow+1);
            }
            output.Finish();
            return new RealPrefixResult
            {
                Manifest=manifest,
                Records=output.RetainedRecords,
                TotalRecordCount=output.TotalRecordCount,
                RetainedCharacterCount=output.RetainedCharacterCount
            };
        }

        private static void ProvesRow12525Action9KeepAndPromotion()
        {
            if(Environment.GetEnvironmentVariable("OPENGGF_S1_AUDIO_ROW12525")!="1")
                throw new TestMain.SkipTestException(
                    "OPENGGF_S1_AUDIO_ROW12525 is not enabled.");
            RealPrefixResult result=CaptureRealPrefix(
                12525,false,new[]{12525});
            List<JObject> row=NativeRecords(result.Records,12525);
            JObject rootBegin=SingleNative(row,value=>(byte)value["kind"]==1
                &&(uint)value["pc"]==0x071B4C
                &&(byte)value["service_kind"]==4
                &&(ushort)value["parent_token"]==0
                &&(byte)value["depth"]==0);
            ushort rootToken=(ushort)rootBegin["service_token"];
            JObject childBegin=SingleNative(row,value=>(byte)value["kind"]==1
                &&(uint)value["pc"]==0x000077
                &&(byte)value["service_kind"]==2
                &&(ushort)value["parent_token"]==rootToken
                &&(byte)value["depth"]==1);
            ushort childToken=(ushort)childBegin["service_token"];
            JObject keep=SingleNative(row,value=>(byte)value["kind"]==10
                &&(uint)value["pc"]==0x072C24
                &&(ushort)value["service_token"]==childToken
                &&(ushort)value["parent_token"]==rootToken);
            JObject rootEnd=SingleNative(row,value=>(byte)value["kind"]==2
                &&(uint)value["pc"]==0x071C4C
                &&(ushort)value["service_token"]==rootToken);
            JObject promotion=SingleNative(row,value=>(byte)value["kind"]==11
                &&(uint)value["pc"]==0x071C4C
                &&(ushort)value["service_token"]==childToken);
            JObject childEnd=SingleNative(row,value=>(byte)value["kind"]==2
                &&(uint)value["pc"]==0x0000AC
                &&(ushort)value["service_token"]==childToken);

            AssertServiceTopology(rootBegin,4,0,0);
            AssertServiceTopology(childBegin,2,rootToken,1);
            AssertServiceTopology(keep,2,rootToken,1);
            AssertEx.Equal((ushort)5,rootToken);
            AssertEx.Equal((ushort)6,childToken);
            AssertEx.Equal(0,(int)keep["value"]);
            AssertEx.Equal((byte)9,
                result.Manifest.NativeActionByToken[(ushort)keep["subject"]]);
            AssertServiceTopology(rootEnd,4,0,0);
            AssertServiceTopology(promotion,2,0,0);
            AssertEx.Equal((byte)8,
                result.Manifest.NativeActionByToken[(ushort)promotion["subject"]]);
            AssertServiceTopology(childEnd,2,0,0);
            AssertEx.Equal((uint)rootEnd["ordinal"]+1,
                (uint)promotion["ordinal"]);
            AssertEx.Equal(true,(uint)keep["ordinal"]<(uint)rootEnd["ordinal"]);
            AssertEx.Equal(true,(uint)promotion["ordinal"]<(uint)childEnd["ordinal"]);

            List<JObject> evidence=Records(result.Records,"managed_hook_evidence",
                value=>(int)value["row"]==12525
                    &&(uint)value["pc"]==0x072C24);
            AssertEx.Equal(1,evidence.Count);
            JObject callback=evidence[0];
            AssertEx.Equal("CLOSE_IF_RETURN_OUTSIDE",(string)callback["action"]);
            AssertEx.Equal((uint)0x00FFFDAE,
                (uint)callback["registers"]["A7"]);
            AssertEx.Equal((uint)0x00071C38,(uint)callback["return_pc"]);
            AssertEx.Equal(childToken,(ushort)callback["native_service_token"]);
            AssertEx.Equal(rootToken,(ushort)callback["native_parent_token"]);
            AssertEx.Equal((ushort)keep["subject"],
                (ushort)callback["native_hook_token"]);
            AssertEx.Equal(0,(int)callback["native_marker_value"]);
            JArray chain=(JArray)callback["native_correlation_events"];
            AssertEx.Equal(1,chain.Count);
            AssertEx.Equal((uint)keep["ordinal"],(uint)chain[0]["ordinal"]);
            AssertEx.Equal(childToken,(ushort)chain[0]["service_token"]);
            AssertEx.Equal(rootToken,(ushort)chain[0]["parent_token"]);
            AssertEx.Equal(2,(int)chain[0]["service_kind"]);
            AssertEx.Equal(1,(int)chain[0]["depth"]);
            AssertEx.Equal(10,(int)chain[0]["event_kind"]);
            AssertEx.Equal(0,(int)chain[0]["value"]);
            AssertEx.Equal(true,(bool)chain[0]["terminal"]);
            Console.WriteLine(
                "ROW12525_PROOF root={0} child={1} keep_ordinal={2} "
                +"callback_a7={3:X8} return_pc={4:X8} "
                +"root_end_ordinal={5} promotion_ordinal={6} child_end_ordinal={7}",
                rootToken,childToken,(uint)keep["ordinal"],
                (uint)callback["registers"]["A7"],(uint)callback["return_pc"],
                (uint)rootEnd["ordinal"],(uint)promotion["ordinal"],
                (uint)childEnd["ordinal"]);
        }

        private static JObject SingleNative(List<JObject> row,
            Func<JObject,bool> predicate)
        {
            var matches=new List<JObject>();
            foreach(JObject value in row)
                if(predicate(value))matches.Add(value);
            AssertEx.Equal(1,matches.Count);
            return matches[0];
        }

        private static void AssertServiceTopology(JObject value,byte kind,
            ushort parent,byte depth)
        {
            AssertEx.Equal(kind,(byte)value["service_kind"]);
            AssertEx.Equal(parent,(ushort)value["parent_token"]);
            AssertEx.Equal(depth,(byte)value["depth"]);
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
            AssertEx.Equal((uint)291,manifest.NativeConfig.HookCount);
            AssertEx.Equal((uint)16412,
                manifest.NativeConfig.SnapshotBytesTotal);
            AssertHook(manifest, 0x00138E, "11c0f00a", "QueueSound1", "REQUEST_QUEUE_0");
            AssertHook(manifest, 0x001394, "11c0f00b", "QueueSound2", "REQUEST_QUEUE_1");
            AssertHook(manifest, 0x00139A, "11c0f00c", "QueueSound3", "REQUEST_QUEUE_2");
            AssertQueueObservationKinds(manifest, 0x00138E, 0, 2, 3, 6);
            AssertQueueObservationKinds(manifest, 0x001394, 0, 2, 3, 6);
            AssertQueueObservationKinds(manifest, 0x00139A, 0, 2, 3, 6);
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
            AssertConditionalCloseAlternatives(manifest, 0x072C24);
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

        private static void KeepsKindSixQueueObservationsBounded()
        {
            AssertPublishedKindSixQueueObservations();
            AssertPreEpochKindSixQueueObservationsDoNotPublish();
            AssertKindSixQueueCorrelationRollsBack(false);
            AssertKindSixQueueCorrelationRollsBack(true);
            AssertQueueKindRemainsRejected(4);
            AssertQueueKindRemainsRejected(5);
        }

        private static void AssertPublishedKindSixQueueObservations()
        {
            var api=new FakeTraceApi();
            const uint stack=0x00FFFDF0;
            var host=new FakeS1Host((current,frame)=>
            {
                api.VisitZ80(0x003A,current);
                current.SetCpuRegister("D0",0x81);
                current.SetCpuRegister("A7",stack);
                Visit(current,api,0x00138E);
                current.SetCpuRegister("D0",0x82);
                current.SetCpuRegister("A7",stack+4);
                Visit(current,api,0x001394);
                current.SetCpuRegister("D0",0x83);
                current.SetCpuRegister("A7",stack+8);
                Visit(current,api,0x00139A);
                api.EmitChip(3,1,0x2A,0x0089,1);
                api.VisitZ80(0x0077,current);
                api.VisitZ80(0x00AC,current);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.CaptureFrame(860,host.Advance);
                AssertEx.Equal(0,session.PendingDeferredObservationCountForTesting);
                AssertEx.Equal(0L,session.PendingGenerationIdForTesting);
                session.Complete(861);
            }

            string raw=output.ToString();
            AssertEx.Equal(0,CountRecords(raw,"managed_service_snapshot"));
            List<JObject> begins=Records(raw,"native_event",value=>
                (int)value["kind"]==1);
            AssertEx.Equal(2,begins.Count);
            AssertEx.Equal(6,(int)begins[0]["service_kind"]);
            ushort root=(ushort)begins[0]["service_token"];
            List<JObject> markers=Records(raw,"native_event",value=>
                (int)value["kind"]==10&&(int)value["value"]==3);
            AssertEx.Equal(3,markers.Count);
            for(int i=0;i<markers.Count;i++)
            {
                AssertEx.Equal((uint)(0x00138E+i*6),(uint)markers[i]["pc"]);
                AssertEx.Equal(root,(ushort)markers[i]["service_token"]);
                AssertEx.Equal(0,(int)markers[i]["parent_token"]);
                AssertEx.Equal(6,(int)markers[i]["service_kind"]);
                AssertEx.Equal(0,(int)markers[i]["depth"]);
                AssertEx.Equal(2,(int)markers[i]["source_cpu"]);
            }
            List<JObject> chips=Records(raw,"native_event",value=>
                (int)value["kind"]==3);
            AssertEx.Equal(1,chips.Count);
            AssertEx.Equal(root,(ushort)chips[0]["service_token"]);
            AssertEx.Equal(6,(int)chips[0]["service_kind"]);

            JObject queue0=Record(raw,"managed_hook_evidence",0);
            JObject queue1=Record(raw,"managed_hook_evidence",1);
            JObject queue2=Record(raw,"managed_hook_evidence",2);
            AssertEx.Equal((uint)0x00138E,(uint)queue0["pc"]);
            AssertEx.Equal((uint)0x001394,(uint)queue1["pc"]);
            AssertEx.Equal((uint)0x00139A,(uint)queue2["pc"]);
            uint actual0=(uint)queue0["registers"]["A7"];
            uint actual1=(uint)queue1["registers"]["A7"];
            uint actual2=(uint)queue2["registers"]["A7"];
            AssertEx.Equal(stack,actual0);
            AssertEx.Equal(actual0+4,actual1);
            AssertEx.Equal(actual1+4,actual2);
            foreach(JObject queue in new[]{queue0,queue1,queue2})
            {
                AssertEx.Equal(root,(ushort)queue["native_service_token"]);
                AssertEx.Equal(0,(int)queue["native_parent_token"]);
                AssertEx.Equal(3,(int)queue["native_marker_value"]);
                AssertEx.Equal(1,((JArray)queue["native_correlation_events"]).Count);
            }
        }

        private static void AssertPreEpochKindSixQueueObservationsDoNotPublish()
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                if(frame==1)
                {
                    api.VisitZ80(0x003A,current);
                    current.SetCpuRegister("A7",0x00FFFDF0);
                    Visit(current,api,0x001394);
                    return;
                }
                api.VisitZ80(0x0077,current);
                api.VisitZ80(0x00AC,current);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.ObservePreEpochFrame(859,null,host.Advance);
                AssertEx.Equal(0,output.ToString().Length);
                AssertEx.Equal(2,api.LastDrainedEvents.Count);
                AssertEx.Equal(1,(int)api.LastDrainedEvents[0].Kind);
                AssertEx.Equal((uint)0x003A,api.LastDrainedEvents[0].Pc);
                AssertEx.Equal(10,(int)api.LastDrainedEvents[1].Kind);
                AssertEx.Equal((uint)0x001394,api.LastDrainedEvents[1].Pc);
                AssertEx.Equal(3,(int)api.LastDrainedEvents[1].Value);
                AssertEx.Equal(6,(int)api.LastDrainedEvents[1].ServiceKindId);
                session.BeginEpoch();
                AssertEx.Equal(1,((JArray)Record(output.ToString(),"baseline",0)
                    ["active_services"]).Count);
                AssertEx.Equal(0,CountRecords(output.ToString(),"request"));
                session.CaptureFrame(860,host.Advance);
                session.Complete(861);
            }
        }

        private static void AssertKindSixQueueCorrelationRollsBack(bool wrongOrder)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                api.VisitZ80(0x003A,current);
                current.SetCpuRegister("A7",0x00FFFDF0);
                if(wrongOrder)
                {
                    current.FireExecuteCallback(0x00138E);
                    api.VisitManaged(0x001394,current);
                }
                else
                {
                    Visit(current,api,0x001394);
                    api.MutateLast(value=>
                    {
                        value.Subject=checked((ushort)(value.Subject+1));
                        return value;
                    });
                }
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.BeginEpoch();
                int before=output.ToString().Length;
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.CaptureFrame(860,host.Advance),
                    wrongOrder?"order or PC":"invalid marker fields");
                AssertEx.Equal(before,output.ToString().Length);
                AssertEx.Equal(0,session.PendingDeferredObservationCountForTesting);
                AssertEx.Equal(0L,session.PendingGenerationIdForTesting);
            }
        }

        private static void AssertQueueKindRemainsRejected(byte kind)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDF0);
                if(kind==4)Visit(current,api,0x071B4C);
                else api.VisitManaged(0x00134A,current);
                Visit(current,api,0x001394);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.BeginEpoch();
                int before=output.ToString().Length;
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.CaptureFrame(860,host.Advance),
                    "no active-kind alternative");
                AssertEx.Equal(before,output.ToString().Length);
            }
        }

        private static void AssertDeferredBeginHooks(
            S1CompleteRunAudioReferenceCapture.Manifest manifest)
        {
            int reserve=0;var consume=new List<byte>();
            var consumeTokens=new HashSet<ushort>();
            var ordinary=new List<byte>();
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
                    consume.Add(hook.ExpectedActiveKind);
                    AssertEx.Equal(true,consumeTokens.Add(hook.HookToken));
                    AssertEx.Equal(true,object.ReferenceEquals(
                        manifest.FindManagedHook(0x071B82),
                        manifest.ManagedByNativeToken[hook.HookToken]));
                    AssertEx.Equal((byte)4,hook.ServiceKindId);
                    AssertEx.Equal((byte)2,hook.Cpu);
                    AssertEx.Equal((byte)6,hook.OpcodeLength);
                    AssertEx.Equal((ulong)0x00F0FF00F94D,hook.Opcode);
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
            consume.Sort();
            AssertEx.Equal(3,consume.Count);
            AssertEx.Equal((byte)2,consume[0]);
            AssertEx.Equal((byte)3,consume[1]);
            AssertEx.Equal((byte)6,consume[2]);
            AssertEx.Equal(3,consumeTokens.Count);
            ordinaryEntry.Sort();
            AssertEx.Equal(4,ordinaryEntry.Count);
            AssertEx.Equal((byte)2,ordinaryEntry[0]);
            AssertEx.Equal((byte)3,ordinaryEntry[1]);
            AssertEx.Equal((byte)4,ordinaryEntry[2]);
            AssertEx.Equal((byte)6,ordinaryEntry[3]);
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

        private static void CorrelatesDeferredTailSuccessorIdentity()
        {
            AssertDeferredTailSuccessorIdentity(0x0077,0x00AC,2,false);
            AssertDeferredTailSuccessorIdentity(0x00C1,0x00D0,3,true);
        }

        private static void AssertDeferredTailSuccessorIdentity(uint tailPc,
            uint successorEndPc,byte successorKind,bool crossFrame)
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
                    api.VisitZ80(tailPc,current);
                    if(crossFrame)return;
                }
                Visit(current,api,0x071B82);
                Visit(current,api,0x071C4C);
                api.VisitZ80(successorEndPc,current);
            });
            var output=new StringWriter();
            S1CompleteRunAudioReferenceCapture.Manifest manifest;
            using(var session=CreateSession(host,api,output))
            {
                manifest=S1CompleteRunAudioReferenceCapture.LoadManifest(
                    FixturePath(),RomForManifest(FixturePath()));
                session.BeginEpoch();
                int baselineLength=output.ToString().Length;
                session.CaptureFrame(860,host.Advance);
                if(crossFrame)
                {
                    AssertEx.Equal(2,
                        session.PendingDeferredObservationCountForTesting);
                    AssertEx.Equal(baselineLength,output.ToString().Length);
                    session.CaptureFrame(861,host.Advance);
                    session.Complete(862);
                }
                else session.Complete(861);
            }
            string raw=output.ToString();
            List<JObject> markers=Records(raw,"managed_hook_evidence",
                value=>value["native_marker_value"]!=null
                    &&value["native_marker_value"].Type==JTokenType.Integer
                    &&(int)value["native_marker_value"]==4);
            AssertEx.Equal(2,markers.Count);
            ushort origin=(ushort)markers[0]["native_service_token"];
            AssertEx.Equal((ushort)0,(ushort)markers[0]["native_parent_token"]);
            AssertEx.Equal(origin,(ushort)markers[1]["native_service_token"]);
            AssertEx.Equal((ushort)0,(ushort)markers[1]["native_parent_token"]);
            AssertEx.Equal((uint)0x00FFFDB2,(uint)markers[0]["deferred_a7"]);
            AssertEx.Equal((uint)0x00000B64,(uint)markers[0]["deferred_return_pc"]);

            List<JObject> native=NativeRecords(raw,crossFrame?861:860);
            int consume=FindNative(native,value=>(int)value["kind"]==1
                &&(int)value["service_kind"]==4
                &&(uint)value["pc"]==0x071B82);
            ushort successor=(ushort)native[consume]["parent_token"];
            AssertEx.Equal(true,successor!=origin);
            AssertEx.Equal(1,(int)native[consume]["depth"]);
            AssertEx.Equal(successorKind,FindServiceKind(raw,successor));
            ushort consumeHook=FindHookToken(manifest,0x071B82,12,
                successorKind);
            AssertEx.Equal(consumeHook,(ushort)native[consume]["subject"]);
            AssertEx.Equal(0,CountNative(raw,value=>(uint)value["pc"]==0x071B82
                &&(int)value["kind"]==10&&(int)value["value"]==3));
            for(int i=0;i<markers.Count;i++)
            {
                AssertEx.Equal(origin,(ushort)markers[i]["native_service_token"]);
                AssertEx.Equal(successor,(ushort)native[consume]["parent_token"]);
                AssertEx.Equal((ushort)native[consume]["service_token"],
                    (ushort)markers[i]["consumed_service_token"]);
            }
        }

        private static void CarriesDeferredTailSuccessorAcrossCutoff()
        {
            AssertDeferredTailSuccessorAcrossCutoff(0x0077,0x00AC,2);
            AssertDeferredTailSuccessorAcrossCutoff(0x00C1,0x00D0,3);
            AssertDeferredTailSuccessorWhollyBeforeEpoch(0x0077,0x00AC);
            AssertDeferredTailSuccessorWhollyBeforeEpoch(0x00C1,0x00D0);
        }

        private static void AssertDeferredTailSuccessorAcrossCutoff(uint tailPc,
            uint successorEndPc,byte successorKind)
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
                    api.VisitZ80(tailPc,current);
                    return;
                }
                Visit(current,api,0x071B82);
                Visit(current,api,0x071C4C);
                api.VisitZ80(successorEndPc,current);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.ObservePreEpochFrame(859,null,host.Advance);
                AssertEx.Equal(0,output.ToString().Length);
                AssertEx.Equal(0,session.BoundaryManagedServiceCountForTesting);
                session.BeginEpoch();
                JObject baseline=Record(output.ToString(),"baseline",0);
                JArray active=(JArray)baseline["active_services"];
                AssertEx.Equal(1,active.Count);
                AssertEx.Equal(successorKind,(byte)active[0]["kind"]);
                AssertEx.Equal((ushort)0,(ushort)active[0]["parent_token"]);
                AssertEx.Equal((byte)0,(byte)active[0]["depth"]);
                AssertEx.Equal(tailPc,(uint)active[0]["begin_pc"]);
                session.CaptureFrame(860,host.Advance);
                session.Complete(861);
            }
            AssertEx.Equal(0,CountNativeMarkerValue(output.ToString(),4));
            JObject consume=Records(output.ToString(),"managed_hook_evidence",
                value=>(uint)value["pc"]==0x071B82)[0];
            AssertEx.Equal((uint)0x00FFFDB2,(uint)consume["registers"]["A7"]);
        }

        private static void AssertDeferredTailSuccessorWhollyBeforeEpoch(
            uint tailPc,uint successorEndPc)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                api.VisitZ80(0x003A,current);
                Visit(current,api,0x071B4C);
                api.VisitZ80(tailPc,current);
                Visit(current,api,0x071B82);
                Visit(current,api,0x071C4C);
                api.VisitZ80(successorEndPc,current);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.ObservePreEpochFrame(859,null,host.Advance);
                session.BeginEpoch();
            }
            AssertEx.Equal(0,((JArray)Record(output.ToString(),"baseline",0)
                ["active_services"]).Count);
            AssertEx.Equal(0,CountRecords(output.ToString(),"native_event"));
            AssertEx.Equal(0,CountRecords(output.ToString(),
                "managed_hook_evidence"));
        }

        private static void RollsBackDeferredTailSuccessorIdentity()
        {
            AssertTransferredConsumeIdentityFails(current=>
                current.SetCpuRegister("A7",0x00FFFDB6));
            AssertTransferredConsumeIdentityFails(current=>
                current.SetU32(0xFDB2,0x00000B68));
            AssertTransferredPendingRejectsResetAndTerminal(false,true);
            AssertTransferredPendingRejectsResetAndTerminal(true,false);
        }

        private static void AssertTransferredConsumeIdentityFails(
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
                api.VisitZ80(0x0077,current);
                corrupt(current);
                Visit(current,api,0x071B82);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.BeginEpoch();
                int baselineLength=output.ToString().Length;
                session.CaptureFrame(860,host.Advance);
                AssertEx.Equal(1,session.PendingDeferredObservationCountForTesting);
                AssertEx.Equal(baselineLength,output.ToString().Length);
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.CaptureFrame(861,host.Advance),"identity");
                AssertEx.Equal(1,session.PendingDeferredObservationCountForTesting);
                AssertEx.Equal(baselineLength,output.ToString().Length);
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.CaptureFrame(861,()=>{}),"faulted");
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.Complete(861),"faulted");
            }
        }

        private static void AssertTransferredPendingRejectsResetAndTerminal(
            bool power,bool reset)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                api.VisitZ80(0x003A,current);
                Visit(current,api,0x071B4C);
                api.VisitZ80(0x0077,current);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.BeginEpoch();
                int baselineLength=output.ToString().Length;
                session.CaptureFrame(860,host.Advance);
                AssertEx.Equal(1,session.PendingDeferredObservationCountForTesting);
                AssertEx.Equal(baselineLength,output.ToString().Length);
                AssertEx.Throws<InvalidOperationException>(()=>session.CaptureFrame(
                    861,new Bk2Frame{Power=power,Reset=reset},()=>{}),"pending");
                AssertEx.Equal(1,session.PendingDeferredObservationCountForTesting);
                AssertEx.Equal(baselineLength,output.ToString().Length);
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.Complete(861),"pending");
            }
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

        private static void RotatesPublicationAfterSameBlockerRelay()
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
                Visit(current,api,0x071C4C);
                if(frame==2)
                {
                    Visit(current,api,0x071B4C);
                    return;
                }
                api.VisitZ80(0x0077,current);
                api.VisitZ80(0x00AC,current);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.CaptureFrame(860,host.Advance);
                long firstGeneration=session.PendingGenerationIdForTesting;
                AssertEx.Equal(0,CountRecords(output.ToString(),"frame_begin"));
                session.CaptureFrame(861,host.Advance);
                AssertEx.Equal(1,session.HeldFrameCountForTesting);
                AssertEx.Equal(true,session.PendingGenerationIdForTesting
                    >firstGeneration);
                AssertEx.Equal(1,CountRecords(output.ToString(),"frame_begin"));
                AssertEx.Equal(860,(int)Record(output.ToString(),"frame_begin",0)["row"]);
                session.CaptureFrame(862,host.Advance);
                session.Complete(863);
            }
            string raw=output.ToString();
            AssertEx.Equal(3,CountRecords(raw,"frame_begin"));
            AssertEx.Equal(3,CountRecords(raw,"frame_end"));
            AssertEx.Equal(2,Records(raw,"managed_hook_evidence",value=>(uint)value["pc"]
                ==0x071B4C&&(int)value["native_marker_value"]==4).Count);
        }

        private static void EnforcesHeldFrameAndEvidenceCaps()
        {
            int exactFrameCharacters=JsonLine(new JObject
                {{"type","frame_begin"},{"row",860}}).Length
                +JsonLine(new JObject{{"type","frame_end"},{"row",860}}).Length;
            var exactFrameApi=new FakeTraceApi();
            var exactFrameHost=new FakeS1Host(null);
            using(var session=CreateSession(exactFrameHost,exactFrameApi,
                new StringWriter(),new S1CompleteRunAudioReferenceCapture
                    .DeferredPublicationLimits(
                        frameCharacters:exactFrameCharacters)))
                session.CaptureFrame(860,exactFrameHost.Advance);
            var shortFrameApi=new FakeTraceApi();
            var shortFrameHost=new FakeS1Host(null);
            using(var session=CreateSession(shortFrameHost,shortFrameApi,
                new StringWriter(),new S1CompleteRunAudioReferenceCapture
                    .DeferredPublicationLimits(
                        frameCharacters:exactFrameCharacters-1)))
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.CaptureFrame(860,shortFrameHost.Advance),
                    "character limit");

            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                if(frame==1)
                {
                    api.VisitZ80(0x003A,current);
                    Visit(current,api,0x071B4C);
                }
            });
            var limits=new S1CompleteRunAudioReferenceCapture
                .DeferredPublicationLimits(newHeldFrames:2,evidenceRecords:1);
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output,limits))
            {
                session.CaptureFrame(860,host.Advance);
                long generation=session.PendingGenerationIdForTesting;
                long evidenceCharge=session.RetainedEvidenceCharactersForTesting;
                AssertEx.Equal(1,session.HeldFrameCountForTesting);
                AssertEx.Equal(1,session.RetainedEvidenceCountForTesting);
                session.CaptureFrame(861,host.Advance);
                AssertEx.Equal(2,session.HeldFrameCountForTesting);
                AssertEx.Equal(generation,session.PendingGenerationIdForTesting);
                long characters=session.HeldFrameCharactersForTesting;
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.CaptureFrame(862,host.Advance),"held-frame limit");
                AssertEx.Equal(2,session.HeldFrameCountForTesting);
                AssertEx.Equal(characters,session.HeldFrameCharactersForTesting);
                AssertEx.Equal(0,output.ToString().IndexOf(
                    "{\"type\":\"metadata\"",StringComparison.Ordinal));
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.CaptureFrame(862,()=>{}),"faulted");

                var exactEvidenceApi=new FakeTraceApi();
                var exactEvidenceHost=new FakeS1Host((current,frame)=>
                {
                    current.SetCpuRegister("A7",0x00FFFDB2);
                    current.SetU32(0xFDB2,0x00000B64);
                    exactEvidenceApi.VisitZ80(0x003A,current);
                    Visit(current,exactEvidenceApi,0x071B4C);
                });
                using(var exactSession=CreateSession(exactEvidenceHost,
                    exactEvidenceApi,new StringWriter(),
                    new S1CompleteRunAudioReferenceCapture
                        .DeferredPublicationLimits(
                            evidenceCharacters:evidenceCharge)))
                    exactSession.CaptureFrame(860,exactEvidenceHost.Advance);
                var shortEvidenceApi=new FakeTraceApi();
                var shortEvidenceHost=new FakeS1Host((current,frame)=>
                {
                    current.SetCpuRegister("A7",0x00FFFDB2);
                    current.SetU32(0xFDB2,0x00000B64);
                    shortEvidenceApi.VisitZ80(0x003A,current);
                    Visit(current,shortEvidenceApi,0x071B4C);
                });
                using(var shortSession=CreateSession(shortEvidenceHost,
                    shortEvidenceApi,new StringWriter(),
                    new S1CompleteRunAudioReferenceCapture
                        .DeferredPublicationLimits(
                            evidenceCharacters:evidenceCharge-1)))
                    AssertEx.Throws<InvalidOperationException>(()=>
                        shortSession.CaptureFrame(860,shortEvidenceHost.Advance),
                        "evidence character limit");
            }

            var overflowApi=new FakeTraceApi();
            var overflowHost=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                overflowApi.VisitZ80(0x003A,current);
                Visit(current,overflowApi,0x071B4C);
                Visit(current,overflowApi,0x071B4C);
            });
            using(var session=CreateSession(overflowHost,overflowApi,
                new StringWriter(),limits))
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.CaptureFrame(860,overflowHost.Advance),
                    "evidence record limit");

            var cutoffApi=new FakeTraceApi();
            var cutoffHost=new FakeS1Host((current,frame)=>
            {
                if(frame!=1)return;
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                cutoffApi.VisitZ80(0x003A,current);
                Visit(current,cutoffApi,0x071B4C);
            });
            using(var session=CreateSession(cutoffHost,cutoffApi,
                new StringWriter()))
            {
                session.ObservePreEpochFrame(859,null,cutoffHost.Advance);
                session.BeginEpoch();
                for(int row=860;row<=863;row++)
                    session.CaptureFrame(row,cutoffHost.Advance);
                AssertEx.Equal(4,session.HeldFrameCountForTesting);
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.CaptureFrame(864,cutoffHost.Advance),
                    "end frame failed");
                AssertEx.Equal(4,session.HeldFrameCountForTesting);
            }
        }

        private static void BoundsRotatingBlockerRelays()
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                if(frame!=1)
                {
                    Visit(current,api,0x071B82);
                    Visit(current,api,0x071C4C);
                    api.VisitZ80(0x0077,current);
                    api.VisitZ80(0x00AC,current);
                }
                if(frame<=10)
                {
                    api.VisitZ80(0x003A,current);
                    Visit(current,api,0x071B4C);
                }
            });
            var output=new StringWriter();
            long previousGeneration=0,peakCharacters=0;
            using(var session=CreateSession(host,api,output))
            {
                for(int row=860;row<=869;row++)
                {
                    session.CaptureFrame(row,host.Advance);
                    AssertEx.Equal(1,session.HeldFrameCountForTesting);
                    AssertEx.Equal(true,session.PendingGenerationIdForTesting
                        >previousGeneration);
                    previousGeneration=session.PendingGenerationIdForTesting;
                    peakCharacters=Math.Max(peakCharacters,
                        session.HeldFrameCharactersForTesting);
                }
                session.CaptureFrame(870,host.Advance);
                AssertEx.Equal(0,session.HeldFrameCountForTesting);
                AssertEx.Equal(0L,session.HeldFrameCharactersForTesting);
                session.Complete(871);
            }
            AssertEx.Equal(11,CountRecords(output.ToString(),"frame_begin"));
            AssertEx.Equal(true,peakCharacters>0);
        }

        private static void MakesOutputFailuresTerminal()
        {
            var metadataApi=new FakeTraceApi();
            var metadataHost=new FakeS1Host(null);
            var metadataOutput=new FailingWriter(0);
            using(var session=CreateSession(metadataHost,metadataApi,
                metadataOutput))
            {
                AssertEx.Throws<IOException>(()=>session.BeginEpoch(),
                    "injected output failure");
                int calls=metadataApi.Calls.Count;
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.CaptureFrame(860,metadataHost.Advance),"faulted");
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.Complete(860),"faulted");
                AssertEx.Equal(calls,metadataApi.Calls.Count);
            }

            var frameApi=new FakeTraceApi();
            var frameHost=new FakeS1Host(null);
            var frameOutput=new FailingWriter(int.MaxValue);
            using(var session=CreateSession(frameHost,frameApi,frameOutput))
            {
                session.BeginEpoch();
                frameOutput.RemainingCharacters=7;
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.CaptureFrame(860,frameHost.Advance),
                    "injected output failure");
                AssertEx.Equal(0,session.HeldFrameCountForTesting);
                int calls=frameApi.Calls.Count;
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.CaptureFrame(860,frameHost.Advance),"faulted");
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.Complete(861),"faulted");
                AssertEx.Equal(calls,frameApi.Calls.Count);
            }

            var capApi=new FakeTraceApi();
            var capHost=new FakeS1Host(null);
            using(var session=CreateSession(capHost,capApi,new StringWriter(),
                new S1CompleteRunAudioReferenceCapture.DeferredPublicationLimits(
                    frameCharacters:1)))
            {
                session.BeginEpoch();
                int calls=capApi.Calls.Count;
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.CaptureFrame(860,capHost.Advance),
                    "character limit");
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.CaptureFrame(860,capHost.Advance),"faulted");
                AssertEx.Equal(calls,capApi.Calls.Count);
            }

            var baselineApi=new FakeTraceApi();
            var baselineHost=new FakeS1Host(null);
            var baselineOutput=new StringWriter();
            using(var session=CreateSession(baselineHost,baselineApi,
                baselineOutput))session.BeginEpoch();
            string[] baselineLines=baselineOutput.ToString().Split('\n');
            long baselineCharacters=baselineLines[1].Length+1;
            using(var session=CreateSession(new FakeS1Host(null),
                new FakeTraceApi(),new StringWriter(),
                new S1CompleteRunAudioReferenceCapture.DeferredPublicationLimits(
                    aggregateLineCharacters:baselineCharacters)))
                session.BeginEpoch();
            var shortBaselineOutput=new StringWriter();
            using(var session=CreateSession(new FakeS1Host(null),
                new FakeTraceApi(),shortBaselineOutput,
                new S1CompleteRunAudioReferenceCapture.DeferredPublicationLimits(
                    aggregateLineCharacters:baselineCharacters-1)))
            {
                AssertEx.Throws<InvalidDataException>(()=>session.BeginEpoch(),
                    "character limit");
                AssertEx.Equal(0,shortBaselineOutput.ToString().Length);
            }

            var activeApi=new FakeTraceApi();
            var activeHost=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                Visit(current,activeApi,0x071B4C);
                activeApi.VisitZ80(0x003A,current);
            });
            var activeOutput=new StringWriter();
            using(var session=CreateSession(activeHost,activeApi,activeOutput,
                new S1CompleteRunAudioReferenceCapture.DeferredPublicationLimits(
                    baselineActiveServices:1)))
            {
                session.ObservePreEpochFrame(859,null,activeHost.Advance);
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.BeginEpoch(),"active service bound");
                AssertEx.Equal(0,activeOutput.ToString().Length);
            }
        }

        private static void PreservesSuccessfulRawBytes()
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host(null);
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                session.CaptureFrame(860,host.Advance);
                session.Complete(861);
            }
            string zeros=new string('0',1472*2);
            string expected=JsonLine(new JObject
                {
                    ["type"]="metadata",
                    ["schema"]="openggf.s1-complete-run-audio-raw.v1",
                    ["rom_sha1"]=RomIdentity.Sonic1Rev01Sha1,
                    ["first_row"]=860,["exclusive_end"]=225101,
                    ["native_abi"]=3,["native_event_size"]=32,
                    ["native_capacity"]=65536
                })+JsonLine(new JObject
                {
                    ["type"]="baseline",["row"]=860,
                    ["state_start"]=0xF000,["state_hex"]=zeros,
                    ["ym_port0_latch"]=0,["ym_port1_latch"]=0,
                    ["active_services"]=new JArray(),
                    ["pending_descendants"]=new JArray(),
                    ["native_arm_epoch"]=0,["native_armed"]=false
                })+JsonLine(new JObject{{"type","frame_begin"},{"row",860}})
                +JsonLine(new JObject{{"type","frame_end"},{"row",860}})
                +JsonLine(new JObject
                {
                    ["type"]="terminal",["exclusive_end"]=861,["rows"]=1,
                    ["orphan_closes"]=0,["opcode_mismatches"]=0,
                    ["overflows"]=0
                });
            AssertEx.Equal(expected,output.ToString());
            AssertEx.Equal(false,output.ToString().Contains("\r"));
            AssertEx.Equal(false,output.ToString().StartsWith("\ufeff",
                StringComparison.Ordinal));

            var deferredApi=new FakeTraceApi();
            var deferredHost=SameFrameDeferredHost(deferredApi);
            var deferredOutput=new StringWriter();
            using(var session=CreateSession(deferredHost,deferredApi,
                deferredOutput))
            {
                session.CaptureFrame(860,deferredHost.Advance);
                session.Complete(861);
            }
            string path=Path.Combine(Path.GetTempPath(),
                "openggf-s1-audio-bytes-"+Guid.NewGuid().ToString("N"));
            try
            {
                var stagedApi=new FakeTraceApi();
                var stagedHost=SameFrameDeferredHost(stagedApi);
                using(var stream=new FileStream(path,FileMode.CreateNew,
                    FileAccess.Write,FileShare.None))
                using(var writer=new StreamWriter(stream,
                    new System.Text.UTF8Encoding(false)))
                {
                    using(var session=CreateSession(stagedHost,stagedApi,writer))
                    {
                        session.CaptureFrame(860,stagedHost.Advance);
                        session.Complete(861);
                    }
                }
                byte[] staged=File.ReadAllBytes(path);
                byte[] expectedBytes=new System.Text.UTF8Encoding(false)
                    .GetBytes(deferredOutput.ToString());
                AssertEx.Equal(Convert.ToBase64String(expectedBytes),
                    Convert.ToBase64String(staged));
                AssertEx.Equal(false,staged.Length>=3&&staged[0]==0xEF
                    &&staged[1]==0xBB&&staged[2]==0xBF);
            }
            finally{if(File.Exists(path))File.Delete(path);}
        }

        private static FakeS1Host SameFrameDeferredHost(FakeTraceApi api)
        {
            return new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                api.VisitZ80(0x003A,current);
                Visit(current,api,0x071B4C);
                Visit(current,api,0x071B82);
                Visit(current,api,0x071C4C);
                api.VisitZ80(0x0077,current);
                api.VisitZ80(0x00AC,current);
            });
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
            AssertNestedKindSixDriverInputEntry(false);
            AssertNestedKindSixDriverInputEntry(true);
        }

        private static void AssertNestedKindSixDriverInputEntry(bool preEpoch)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                Visit(current,api,0x071B4C);
                api.VisitZ80(0x003A,current);
                Visit(current,api,0x071B82);
                api.EmitReset(false,current);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                if(preEpoch)
                {
                    session.ObservePreEpochFrame(859,null,host.Advance);
                    AssertEx.Equal(0,output.ToString().Length);
                    AssertEx.Equal(0,CountRecords(output.ToString(),
                        "managed_hook_evidence"));
                    return;
                }
                session.CaptureFrame(860,host.Advance);
            }
            JObject evidence=Records(output.ToString(),"managed_hook_evidence",
                value=>(uint)value["pc"]==0x071B82)[0];
            JArray correlation=(JArray)evidence["native_correlation_events"];
            AssertEx.Equal(1,correlation.Count);
            AssertEx.Equal(6,(byte)correlation[0]["service_kind"]);
            AssertEx.Equal(1,(int)correlation[0]["depth"]);
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
                api.VisitZ80(0x003A,current),"identity");
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
            Type entry=typeof(S1CompleteRunAudioReferenceCapture
                .ManagedServiceTracker).GetNestedType("Entry",
                    System.Reflection.BindingFlags.NonPublic);
            var entryFields=entry.GetFields(System.Reflection.BindingFlags.Instance
                |System.Reflection.BindingFlags.NonPublic);
            Array.Sort(entryFields,(left,right)=>string.CompareOrdinal(
                left.Name,right.Name));
            AssertEx.Equal(2,entryFields.Length);
            AssertEx.Equal("Stack",entryFields[0].Name);
            AssertEx.Equal(typeof(uint),entryFields[0].FieldType);
            AssertEx.Equal("Token",entryFields[1].Name);
            AssertEx.Equal(typeof(ushort),entryFields[1].FieldType);

            CompleteRunAudioObserver.DriverService promoted=ManagedBoundaryService(
                3,1,1,0,0);
            AssertEx.Equal(true,tracker.MatchesBoundary(
                new[]{promoted}));
            AssertEx.Equal(true,tracker.MatchesBoundary(new[]{
                ManagedBoundaryService(8,0,0,0,0,2),promoted}));
            AssertEx.Equal(true,tracker.MatchesBoundary(new[]{
                ManagedBoundaryService(9,0,0,0,0,3),promoted}));
            AssertEx.Equal(1,tracker.Count);
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
                ushort currentParent,byte currentDepth,byte kind=4)
        {
            return new CompleteRunAudioObserver.DriverService(
                new CompleteRunAudioObserver.ServiceBuilder
                {
                    Token=token,ParentToken=parent,Kind=kind,Depth=depth,
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
            int ordinary=0,total=0;var crossing=new List<byte>();
            foreach (GpgxAudioObserverAdapter.ServiceHook hook in manifest.NativeServiceHooks)
            {
                if (hook.Cpu!=2||hook.Pc!=pc) continue;
                total++;
                AssertEx.Equal((byte)2,hook.OpcodeLength);
                AssertEx.Equal((ulong)0x754e,hook.Opcode);
                AssertEx.Equal((ushort)2,hook.RangeFirst);
                AssertEx.Equal((ushort)1,hook.RangeCount);
                AssertEx.Equal((byte)0,hook.Flags);
                AssertEx.Equal((ulong)0x00070003,hook.Reserved);
                if (hook.Action==5&&hook.ServiceKindId==0
                    &&hook.ExpectedActiveKind==4) ordinary++;
                else if (hook.Action==9&&hook.ServiceKindId==4)
                    crossing.Add(hook.ExpectedActiveKind);
                else throw new InvalidOperationException(
                    "Unexpected conditional-close topology alternative.");
            }
            crossing.Sort();
            AssertEx.Equal(3,total);
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

            foreach(string queueKinds in new[]{
                "[0,2,3]", "[0,2,3,6,5]", "[0,2,3,6,6]", "[0,2,6,3]"})
            {
                root=JObject.Parse(original);
                root["native_observer"]["m68k_binding"]["queue_expected_kinds"]=
                    JArray.Parse(queueKinds);
                string mismatchedKinds=WriteScratch(root.ToString());
                AssertEx.Throws<InvalidDataException>(
                    ()=>S1CompleteRunAudioReferenceCapture.LoadManifest(
                        mismatchedKinds,RomForManifest(mismatchedKinds)),
                    "queue_expected_kinds");
            }
        }

        private static void BoundsManifestBytesStringsAndUtf8()
        {
            string exact=null,longString=null,invalid=null,fifo=null;
            try
            {
                JObject root=JObject.Parse(File.ReadAllText(FixturePath()));
                exact=WriteScratch(root.ToString(Newtonsoft.Json.Formatting.None));
                byte[] rom=RomForManifest(exact);
                long exactBytes=new FileInfo(exact).Length;
                var exactLimits=new S1CompleteRunAudioReferenceCapture
                    .DeferredPublicationLimits(manifestBytes:exactBytes);
                S1CompleteRunAudioReferenceCapture.LoadManifest(exact,rom,exactLimits);
                AssertEx.Throws<InvalidDataException>(()=>
                    S1CompleteRunAudioReferenceCapture.LoadManifest(exact,rom,
                        new S1CompleteRunAudioReferenceCapture.DeferredPublicationLimits(
                            manifestBytes:exactBytes-1)),"byte limit");

                root["m68k_hooks"][0]["name"]=new string('x',1025);
                longString=WriteScratch(
                    root.ToString(Newtonsoft.Json.Formatting.None));
                AssertEx.Throws<InvalidDataException>(()=>
                    S1CompleteRunAudioReferenceCapture.LoadManifest(
                        longString,RomForManifest(longString)),"string");

                invalid=Path.Combine(Path.GetTempPath(),
                "openggf-s1-audio-manifest-"+Guid.NewGuid().ToString("N")+".json");
                File.WriteAllBytes(invalid,new byte[]{(byte)'{',0xC3,(byte)'}'});
                AssertEx.Throws<InvalidDataException>(()=>
                    S1CompleteRunAudioReferenceCapture.LoadManifest(
                        invalid,rom),"UTF-8");

                fifo=Path.Combine(Path.GetTempPath(),
                    "openggf-s1-audio-manifest-"+Guid.NewGuid().ToString("N"));
                AssertEx.Equal(0,MkFifo(fifo,384));
                AssertEx.Throws<InvalidDataException>(()=>
                    S1CompleteRunAudioReferenceCapture.LoadManifest(
                        fifo,rom),"regular file");
            }
            finally
            {
                foreach(string path in new[]{exact,longString,invalid,fifo})
                    if(path!=null&&File.Exists(path))File.Delete(path);
            }
        }

        [DllImport("libc",EntryPoint="mkfifo",CharSet=CharSet.Ansi,
            SetLastError=true)]
        private static extern int MkFifo(string path,uint mode);

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
                current.SetCpuRegister("A7", 0x00FFF004);
                Visit(current, api, 0x071B4C);
                current.SetCpuRegister("A7", 0x00FFF000);
                current.SetU32(0xF000, 0x00071BD4);
                Visit(current, api, 0x072E04);
                current.SetCpuRegister("A7", 0x00FFF004);
                Visit(current, api, 0x071C4C);
                current.SetCpuRegister("A7", 0x00FFF014);
                Visit(current, api, 0x071B4C);
                current.SetU32(0xF014, 0x00010000);
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
            foreach(uint pc in new uint[]{0x072C24,0x072E04})
            {
                AssertConditionalDirectParent(pc,0x0077,0x00AC,2,true,false);
                AssertConditionalDirectParent(pc,0x0077,0x00AC,2,false,false);
                AssertConditionalDirectParent(pc,0x00C1,0x00D0,3,true,false);
                AssertConditionalDirectParent(pc,0x00C1,0x00D0,3,false,false);
                AssertConditionalDirectParent(pc,0x0077,0x00AC,2,true,true);
                AssertConditionalDirectParent(pc,0x0077,0x00AC,2,false,true);
                AssertConditionalDirectParent(pc,0x00C1,0x00D0,3,true,true);
                AssertConditionalDirectParent(pc,0x00C1,0x00D0,3,false,true);
            }
            foreach(bool preEpoch in new[]{false,true})
            {
                AssertConditionalTopKindFourAt72C24(true,preEpoch);
                AssertConditionalTopKindFourAt72C24(false,preEpoch);
                AssertConditionalCallbackStackRelationFails(
                    false,true,preEpoch);
                AssertConditionalCallbackStackRelationFails(
                    false,false,preEpoch);
                AssertConditionalCallbackStackRelationFails(
                    true,true,preEpoch);
                AssertConditionalCallbackStackRelationFails(
                    true,false,preEpoch);
                foreach(bool directParent in new[]{false,true})
                {
                    AssertConditionalStackBoundary(
                        directParent,true,0x00FFFFF8,true,preEpoch);
                    AssertConditionalStackBoundary(
                        directParent,true,0x00FFFFFA,false,preEpoch);
                    AssertConditionalStackBoundary(
                        directParent,true,0x00FFFFFC,false,preEpoch);
                    AssertConditionalStackBoundary(
                        directParent,false,0x00FFFFFC,true,preEpoch);
                    AssertConditionalDecisionShapeRollback(
                        directParent,preEpoch);
                }
            }
            AssertInvalidConditionalReturnProof(0x0000FDAE,0x00071C38);
            AssertInvalidConditionalReturnProof(0x00FFFDAF,0x00071C38);
            AssertInvalidConditionalReturnProof(0x00FFFFFE,0x00071C38);
            AssertInvalidConditionalReturnProof(0x00FFFDAE,0x00071C39);
            AssertInvalidConditionalReturnProof(0x00FFFDAE,0x01000000);
            foreach(bool preEpoch in new[]{false,true})
            {
                AssertConditionalPromotionReset(false,true,preEpoch);
                AssertConditionalPromotionReset(true,false,preEpoch);
                AssertConditionalPromotionReset(true,true,preEpoch);
                AssertConditionalPromotionRollback(false,preEpoch);
                AssertConditionalPromotionRollback(true,preEpoch);
            }
        }

        private static void AssertConditionalDirectParent(uint pc,
            uint childBegin,uint childEnd,byte childKind,bool keep,bool preEpoch)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame) =>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00000B64);
                Visit(current,api,0x071B4C);
                api.VisitZ80(childBegin,current);
                uint callbackStack=keep?0x00FFFDAEu:0x00FFFDB2u;
                current.SetCpuRegister("A7",callbackStack);
                current.SetU32((int)(callbackStack&0xFFFF),
                    keep?0x00071C38u:0x00010000u);
                Visit(current,api,pc);
                if(preEpoch)return;
                if(keep)
                {
                    current.SetCpuRegister("A7",0x00FFFDB2);
                    Visit(current,api,0x071C4C);
                }
                api.VisitZ80(childEnd,current);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                if(preEpoch)
                {
                    session.ObservePreEpochFrame(859,null,host.Advance);
                    AssertEx.Equal(0,output.ToString().Length);
                    AssertEx.Equal(keep?1:0,
                        session.BoundaryManagedServiceCountForTesting);
                    session.BeginEpoch();
                    JArray active=(JArray)Record(output.ToString(),
                        "baseline",0)["active_services"];
                    AssertEx.Equal(keep?2:1,active.Count);
                    if(keep)
                    {
                        AssertEx.Equal(4,(int)active[0]["kind"]);
                        AssertEx.Equal(childKind,(byte)active[1]["kind"]);
                        AssertEx.Equal((ushort)active[0]["token"],
                            (ushort)active[1]["parent_token"]);
                        AssertEx.Equal(1,(int)active[1]["depth"]);
                    }
                    else
                    {
                        AssertEx.Equal(childKind,(byte)active[0]["kind"]);
                        AssertEx.Equal(0,(int)active[0]["current_parent_token"]);
                        AssertEx.Equal(0,(int)active[0]["current_depth"]);
                    }
                    AssertEx.Equal(0,CountRecords(output.ToString(),
                        "native_event"));
                    AssertEx.Equal(0,CountRecords(output.ToString(),
                        "managed_hook_evidence"));
                    return;
                }
                session.CaptureFrame(860,host.Advance);
                session.Complete(861);
            }
            List<JObject> native=NativeRecords(output.ToString(),860);
            int begin=FindNative(native,value=>(int)value["kind"]==1
                &&(int)value["service_kind"]==4);
            int child=FindNative(native,value=>(int)value["kind"]==1
                &&(int)value["service_kind"]==childKind);
            ushort rootToken=(ushort)native[begin]["service_token"];
            ushort childToken=(ushort)native[child]["service_token"];
            if(keep)
            {
                List<JObject> markers=Records(output.ToString(),"native_event",
                    value=>(uint)value["pc"]==pc&&(int)value["kind"]==10);
                AssertEx.Equal(1,markers.Count);
                AssertEx.Equal(0,(int)markers[0]["value"]);
                AssertEx.Equal(childToken,(ushort)markers[0]["service_token"]);
                AssertEx.Equal(rootToken,(ushort)markers[0]["parent_token"]);
                AssertEx.Equal(childKind,(byte)markers[0]["service_kind"]);
                AssertEx.Equal(1,(int)markers[0]["depth"]);
                AssertEx.Equal(0,Records(output.ToString(),"native_event",
                    value=>(uint)value["pc"]==pc
                        &&((int)value["kind"]==2||(int)value["kind"]==11)).Count);
                JObject close=Records(output.ToString(),"native_event",
                    value=>(uint)value["pc"]==0x071C4C
                        &&(int)value["kind"]==2)[0];
                AssertEx.Equal(rootToken,(ushort)close["service_token"]);
            }
            else
            {
                AssertEx.Equal(0,CountNativeMarkerValue(output.ToString(),1));
                List<JObject> close=Records(output.ToString(),"native_event",
                    value=>(uint)value["pc"]==pc&&(int)value["kind"]==2);
                List<JObject> promotion=Records(output.ToString(),"native_event",
                    value=>(uint)value["pc"]==pc&&(int)value["kind"]==11);
                AssertEx.Equal(1,close.Count);
                AssertEx.Equal(1,promotion.Count);
                AssertEx.Equal(rootToken,(ushort)close[0]["service_token"]);
                AssertEx.Equal(childToken,(ushort)promotion[0]["service_token"]);
                AssertEx.Equal(0,(int)promotion[0]["parent_token"]);
                AssertEx.Equal(0,(int)promotion[0]["depth"]);
            }
        }

        private static void AssertConditionalTopKindFourAt72C24(
            bool keep,bool preEpoch)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                Visit(current,api,0x071B4C);
                uint callbackStack=keep?0x00FFFDAEu:0x00FFFDB2u;
                current.SetCpuRegister("A7",callbackStack);
                current.SetU32((int)(callbackStack&0xFFFF),
                    keep?0x00071C38u:0x00010000u);
                Visit(current,api,0x072C24);
                if(keep)
                {
                    current.SetCpuRegister("A7",0x00FFFDB2);
                    Visit(current,api,0x071C4C);
                }
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                if(preEpoch)
                {
                    session.ObservePreEpochFrame(859,null,host.Advance);
                    AssertEx.Equal(0,
                        session.BoundaryManagedServiceCountForTesting);
                    session.BeginEpoch();
                    AssertEx.Equal(0,((JArray)Record(
                        output.ToString(),"baseline",0)["active_services"]).Count);
                    AssertEx.Equal(0,CountRecords(output.ToString(),
                        "native_event"));
                    return;
                }
                session.CaptureFrame(860,host.Advance);
                session.Complete(861);
            }
            AssertEx.Equal(keep?0:1,CountNativeMarkerValue(output.ToString(),1));
            AssertEx.Equal(keep?1:0,CountNativeMarkerValue(output.ToString(),0));
            AssertEx.Equal(0,CountNativeKind(output.ToString(),11));
        }

        private static void AssertConditionalCallbackStackRelationFails(
            bool directParent,bool keep,bool preEpoch)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                Visit(current,api,0x071B4C);
                if(directParent)api.VisitZ80(0x0077,current);
                uint invalidStack=keep?0x00FFFDB2u:0x00FFFDAEu;
                current.SetCpuRegister("A7",invalidStack);
                current.SetU32((int)(invalidStack&0xFFFF),
                    keep?0x00071C38u:0x00010000u);
                Visit(current,api,0x072E04);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                if(preEpoch)
                {
                    AssertEx.Throws<InvalidOperationException>(()=>
                        session.ObservePreEpochFrame(859,null,host.Advance),
                        "conditional managed identity");
                    AssertEx.Equal(0,output.ToString().Length);
                    AssertEx.Equal(0,
                        session.BoundaryManagedServiceCountForTesting);
                    return;
                }
                session.BeginEpoch();
                int before=output.ToString().Length;
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.CaptureFrame(860,host.Advance),
                    "conditional managed identity");
                AssertEx.Equal(before,output.ToString().Length);
            }
        }

        private static void AssertConditionalStackBoundary(
            bool directParent,bool keep,uint callbackStack,
            bool accepted,bool preEpoch)
        {
            var api=new FakeTraceApi();
            uint rootStack=callbackStack+(keep?4u:0u);
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",rootStack);
                Visit(current,api,0x071B4C);
                if(directParent)api.VisitZ80(0x0077,current);
                current.SetCpuRegister("A7",callbackStack);
                current.SetU32((int)(callbackStack&0xFFFF),
                    keep?0x00071C38u:0x00010000u);
                Visit(current,api,0x072E04);
                if(keep)
                {
                    current.SetCpuRegister("A7",rootStack);
                    Visit(current,api,0x071C4C);
                }
                if(directParent)api.VisitZ80(0x00AC,current);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                if(preEpoch)
                {
                    if(!accepted)
                    {
                        AssertEx.Throws<InvalidOperationException>(()=>
                            session.ObservePreEpochFrame(859,null,host.Advance),
                            "conditional managed identity");
                        AssertEx.Equal(0,output.ToString().Length);
                        AssertEx.Equal(0,
                            session.BoundaryManagedServiceCountForTesting);
                        return;
                    }
                    session.ObservePreEpochFrame(859,null,host.Advance);
                    AssertEx.Equal(0,
                        session.BoundaryManagedServiceCountForTesting);
                    session.BeginEpoch();
                    AssertEx.Equal(0,((JArray)Record(output.ToString(),
                        "baseline",0)["active_services"]).Count);
                    return;
                }
                session.BeginEpoch();
                if(!accepted)
                {
                    int before=output.ToString().Length;
                    AssertEx.Throws<InvalidOperationException>(()=>
                        session.CaptureFrame(860,host.Advance),
                        "conditional managed identity");
                    AssertEx.Equal(before,output.ToString().Length);
                    return;
                }
                session.CaptureFrame(860,host.Advance);
                session.Complete(861);
            }
        }

        private static void AssertConditionalDecisionShapeRollback(
            bool directParent,bool preEpoch)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                Visit(current,api,0x071B4C);
                if(directParent)api.VisitZ80(0x0077,current);
                current.SetU32(0xFDB2,0x00010000);
                Visit(current,api,0x072C24);
                api.ForgeConditionalOutsideAsKeep(0x072C24,directParent);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                if(preEpoch)
                {
                    AssertEx.Throws<InvalidOperationException>(()=>
                        session.ObservePreEpochFrame(859,null,host.Advance),
                        "conditional managed identity");
                    AssertEx.Equal(0,output.ToString().Length);
                    AssertEx.Equal(0,
                        session.BoundaryManagedServiceCountForTesting);
                    return;
                }
                session.BeginEpoch();
                int before=output.ToString().Length;
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.CaptureFrame(860,host.Advance),
                    "conditional managed identity");
                AssertEx.Equal(before,output.ToString().Length);
            }
        }

        private static void AssertInvalidConditionalReturnProof(
            uint stack,uint returnPc)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                Visit(current,api,0x071B4C);
                current.SetCpuRegister("A7",stack);
                SetWrappedU32(current,(int)(stack&0xFFFF),returnPc);
                current.FireExecuteCallback(0x072C24);
            });
            using(var session=CreateSession(host,api,new StringWriter()))
                AssertEx.Throws<InvalidOperationException>(()=>
                    session.CaptureFrame(860,host.Advance),
                    "conditional return proof");
        }

        private static void AssertConditionalPromotionReset(
            bool power,bool reset,bool preEpoch)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                if(frame==1)
                {
                    current.SetCpuRegister("A7",0x00FFFDB2);
                    Visit(current,api,0x071B4C);
                    api.VisitZ80(0x0077,current);
                    current.SetCpuRegister("A7",0x00FFFDB2);
                    current.SetU32(0xFDB2,0x00010000);
                    Visit(current,api,0x072C24);
                    return;
                }
                if(power)api.EmitReset(true,current);
                if(reset)api.EmitReset(false,current);
            });
            var output=new StringWriter();
            using(var session=CreateSession(host,api,output))
            {
                if(preEpoch)
                {
                    session.ObservePreEpochFrame(858,null,host.Advance);
                    AssertEx.Equal(0,
                        session.BoundaryManagedServiceCountForTesting);
                    session.ObservePreEpochFrame(859,
                        new Bk2Frame{Power=power,Reset=reset},host.Advance);
                    AssertEx.Equal(0,
                        session.BoundaryManagedServiceCountForTesting);
                    AssertEx.Equal(0,output.ToString().Length);
                    session.BeginEpoch();
                    AssertEx.Equal(0,((JArray)Record(output.ToString(),
                        "baseline",0)["active_services"]).Count);
                    return;
                }
                session.BeginEpoch();
                session.CaptureFrame(860,host.Advance);
                ushort child=(ushort)Records(output.ToString(),"native_event",
                    value=>(uint)value["pc"]==0x072C24
                        &&(int)value["kind"]==11)[0]["service_token"];
                session.CaptureFrame(861,
                    new Bk2Frame{Power=power,Reset=reset},host.Advance);
                session.Complete(862);
                List<JObject> cancellations=Records(output.ToString(),
                    "native_event",value=>(int)value["kind"]==2
                        &&((int)value["flags"]&2)!=0
                        &&(ushort)value["service_token"]==child);
                AssertEx.Equal(1,cancellations.Count);
                AssertEx.Equal(0,(int)cancellations[0]["parent_token"]);
                AssertEx.Equal(0,(int)cancellations[0]["depth"]);
                AssertEx.Equal(0,CountRecords(output.ToString(),
                    "managed_reset_service_snapshot"));
            }
        }

        private static void AssertConditionalPromotionRollback(
            bool removePromotion,bool preEpoch)
        {
            var api=new FakeTraceApi();
            var host=new FakeS1Host((current,frame)=>
            {
                current.SetCpuRegister("A7",0x00FFFDB2);
                if(frame==1)
                {
                    Visit(current,api,0x071B4C);
                    api.VisitZ80(0x0077,current);
                    return;
                }
                current.SetCpuRegister("A7",0x00FFFDB2);
                current.SetU32(0xFDB2,0x00010000);
                Visit(current,api,0x072C24);
                api.RemoveFirst(value=>value.Pc==0x072C24
                    &&value.Kind==(removePromotion?(byte)11:(byte)2));
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
                AssertEx.Throws<InvalidOperationException>(
                    () => session.BeginEpoch(), "faulted");
            }
            AssertEx.Equal(1, api.PublicationCalls);
            AssertEx.Equal(0, CountRecords(output.ToString(), "baseline"));
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
                    NativeMarkerEvent(1, 104, 0x001394)
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
            return CreateSession(host,api,output,
                S1CompleteRunAudioReferenceCapture.DeferredPublicationLimits
                    .Production);
        }

        private static S1CompleteRunAudioReferenceCapture.Session CreateSession(
            FakeS1Host host,FakeTraceApi api,TextWriter output,
            S1CompleteRunAudioReferenceCapture.DeferredPublicationLimits limits)
        {
            return new S1CompleteRunAudioReferenceCapture.Session(
                host, new StrictM68kRegisterReader(host), api,
                S1CompleteRunAudioReferenceCapture.LoadManifest(
                    FixturePath(), RomForManifest(FixturePath())), output,limits);
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

        private static void SetWrappedU32(FakeS1Host host,int offset,uint value)
        {
            host.WriteMainRamByte(offset&0xFFFF,(byte)(value>>24));
            host.WriteMainRamByte((offset+1)&0xFFFF,(byte)(value>>16));
            host.WriteMainRamByte((offset+2)&0xFFFF,(byte)(value>>8));
            host.WriteMainRamByte((offset+3)&0xFFFF,(byte)value);
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

        private static void AssertQueueObservationKinds(
            S1CompleteRunAudioReferenceCapture.Manifest manifest,uint pc,
            params byte[] expectedKinds)
        {
            var actual=new List<byte>();
            foreach(GpgxAudioObserverAdapter.ServiceHook hook
                in manifest.NativeServiceHooks)
            {
                if(hook.Pc!=pc||hook.Action!=7)continue;
                actual.Add(hook.ExpectedActiveKind);
                AssertEx.Equal((byte)2,hook.Cpu);
                AssertEx.Equal((byte)0,hook.ServiceKindId);
                AssertEx.Equal((byte)0,hook.Flags);
                AssertEx.Equal((ushort)0,hook.RangeFirst);
                AssertEx.Equal((ushort)0,hook.RangeCount);
                AssertEx.Equal((ulong)0,hook.Reserved);
            }
            AssertEx.Equal(expectedKinds.Length,actual.Count);
            for(int i=0;i<expectedKinds.Length;i++)
                AssertEx.Equal(expectedKinds[i],actual[i]);
            AssertEx.Equal(false,actual.Contains(4));
            AssertEx.Equal(false,actual.Contains(5));
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

        private static byte FindServiceKind(string jsonl,ushort token)
        {
            List<JObject> begins=Records(jsonl,"native_event",value=>
                (int)value["kind"]==1&&(ushort)value["service_token"]==token);
            if(begins.Count!=1)throw new InvalidOperationException(
                "Expected exactly one native service begin for token.");
            return (byte)begins[0]["service_kind"];
        }

        private static ushort FindHookToken(
            S1CompleteRunAudioReferenceCapture.Manifest manifest,uint pc,
            byte action,byte expectedKind)
        {
            ushort token=0;
            foreach(GpgxAudioObserverAdapter.ServiceHook hook
                in manifest.NativeServiceHooks)
            {
                if(hook.Pc!=pc||hook.Action!=action
                    ||hook.ExpectedActiveKind!=expectedKind)continue;
                if(token!=0)throw new InvalidOperationException(
                    "Expected one native hook identity.");
                token=hook.HookToken;
            }
            if(token==0)throw new InvalidOperationException(
                "Missing native hook identity.");
            return token;
        }

        private static List<JObject> NativeRecords(
            IEnumerable<JObject> records,int row)
        {
            var result=new List<JObject>();
            foreach(JObject record in records)
                if((string)record["type"]=="native_event"
                    &&(int)record["row"]==row)result.Add(record);
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

        private static string JsonLine(JObject value)
        {
            return value.ToString(Newtonsoft.Json.Formatting.None)+"\n";
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

        private static List<JObject> Records(IEnumerable<JObject> records,
            string type,Func<JObject,bool> predicate)
        {
            var result=new List<JObject>();
            foreach(JObject value in records)
                if((string)value["type"]==type&&predicate(value))result.Add(value);
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
                internal byte CarriedFrames;
            }

            public readonly List<string> Calls = new List<string>();
            public GpgxAudioTraceEvent[] Events = new GpgxAudioTraceEvent[0];
            public int PublicationStatus;
            public int PublicationCalls;
            public readonly List<GpgxAudioTraceEvent> LastDrainedEvents=
                new List<GpgxAudioTraceEvent>();
            private readonly List<GpgxAudioTraceEvent> frameEvents =
                new List<GpgxAudioTraceEvent>();
            private readonly List<ActiveService> active =
                new List<ActiveService>();
            private GpgxAudioObserverAdapter.ServiceHook[] hooks;
            private GpgxAudioObserverAdapter.SnapshotRange[] ranges;
            private GpgxAudioObserverAdapter.ServiceKind[] kinds;
            private GpgxAudioObserverAdapter.ServiceHook deferredReserveHook;
            private readonly List<GpgxAudioObserverAdapter.ServiceHook>
                deferredConsumeHooks=
                    new List<GpgxAudioObserverAdapter.ServiceHook>();
            private bool hasDeferredPair;
            private bool prepublication;
            private bool deferredPending;
            private ushort deferredOriginToken,deferredOriginParentToken;
            private ushort deferredCurrentToken,deferredCurrentParentToken;
            private byte deferredOriginKind,deferredOriginDepth;
            private byte deferredCurrentKind,deferredCurrentDepth;
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
                        if(hook.Cpu!=2||hook.ServiceKindId==0
                            ||hook.ExpectedActiveKind==0
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
                int reserveCount=0;
                deferredConsumeHooks.Clear();
                for(int i=0;i<hooks.Length;i++)
                {
                    if(hooks[i].Action==11)
                    {deferredReserveHook=hooks[i];reserveCount++;}
                    else if(hooks[i].Action==12)
                        deferredConsumeHooks.Add(hooks[i]);
                }
                var expectedConsumeKinds=new HashSet<byte>();
                if(reserveCount==1)
                {
                    expectedConsumeKinds.Add(
                        deferredReserveHook.ExpectedActiveKind);
                    for(int i=0;i<hooks.Length;i++)
                        if(hooks[i].Action==4&&hooks[i].ExpectedActiveKind
                            ==deferredReserveHook.ExpectedActiveKind)
                            expectedConsumeKinds.Add(hooks[i].ServiceKindId);
                }
                var actualConsumeKinds=new HashSet<byte>();
                hasDeferredPair=reserveCount==1
                    &&deferredConsumeHooks.Count==expectedConsumeKinds.Count;
                for(int i=0;i<deferredConsumeHooks.Count;i++)
                {
                    GpgxAudioObserverAdapter.ServiceHook consume=
                        deferredConsumeHooks[i];
                    if(consume.ServiceKindId!=deferredReserveHook.ServiceKindId
                        ||!expectedConsumeKinds.Contains(
                            consume.ExpectedActiveKind)
                        ||!actualConsumeKinds.Add(consume.ExpectedActiveKind))
                        hasDeferredPair=false;
                }
                if((reserveCount!=0||deferredConsumeHooks.Count!=0)
                    &&!hasDeferredPair)return -2;
                deferredPending=false;
                deferredOriginToken=deferredOriginParentToken=0;
                deferredCurrentToken=deferredCurrentParentToken=0;
                deferredOriginKind=deferredOriginDepth=0;
                deferredCurrentKind=deferredCurrentDepth=0;
                prepublication=(config.Flags&1)!=0;
                return 0;
            }
            public int BeginFrame()
            {
                Calls.Add("begin");
                if(!prepublication&&active.Count!=0
                    &&active[active.Count-1].CarriedFrames<byte.MaxValue)
                    active[active.Count-1].CarriedFrames++;
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
            public int EndFrame()
            {
                Calls.Add("end");
                if(EndFrameStatus!=0)return EndFrameStatus;
                if(!prepublication)
                    for(int i=0;i<active.Count;i++)
                    {
                        GpgxAudioObserverAdapter.ServiceKind kind=Array.Find(
                            kinds,value=>value.KindId==active[i].Kind);
                        if(kind.KindId==0||(kind.Flags&2)==0
                            ||active[i].CarriedFrames
                                >kind.ContinuationFrameLimit)return -2;
                    }
                return 0;
            }
            public int EventCount(out uint count, out uint overflow)
            { Calls.Add("count"); count=(uint)frameEvents.Count; overflow=0;
                return EventCountStatus; }
            public int Drain(GpgxAudioTraceEvent[] events, uint capacity,
                out uint count)
            { Calls.Add("drain:"+capacity); count=(uint)frameEvents.Count;
                LastDrainedEvents.Clear();LastDrainedEvents.AddRange(frameEvents);
                if(events!=null)frameEvents.CopyTo(events);return 0; }
            public int AbortFrame() { Calls.Add("abort"); return 0; }
            public int Disable() { Calls.Add("disable"); return 0; }
            public int GetFirstFault(out GpgxAudioObserverAdapter.FirstFault fault)
            { Calls.Add("fault"); fault=default(GpgxAudioObserverAdapter.FirstFault); return 0; }
            public int BeginPublicationEpoch()
            {
                Calls.Add("publication");
                PublicationCalls++;
                if(PublicationStatus!=0)return PublicationStatus;
                for(int i=0;i<active.Count;i++)active[i].CarriedFrames=0;
                prepublication=false;
                return 0;
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
                        if(hooks[i].Action==12)
                        {
                            if(!deferredPending||!DeferredCurrentMatchesTop())
                                continue;
                        }
                        else if(hooks[i].Action==7&&deferredPending
                            &&HasDeferredConsumeHook(pc,activeKind))continue;
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
                {
                    if (!deferredPending && HasDeferredConsumeHook(pc, activeKind))
                    {
                        throw new InvalidOperationException(
                            "Fake native deferred consume had no exact reservation.");
                    }
                    throw new InvalidOperationException(
                        "Fake native M68K visit had no active-kind alternative.");
                }
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
                else if(hook.Action==8)
                {
                    SnapshotDirectParentAndPromote(hook,host);
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
                    if(active.Count==0)throw new InvalidOperationException(
                        "Fake native deferred reserve had no owner.");
                    ActiveService owner=active[active.Count-1];
                    if(!deferredPending)
                    {
                        deferredOriginToken=deferredCurrentToken=owner.Token;
                        deferredOriginParentToken=deferredCurrentParentToken=
                            owner.Parent;
                        deferredOriginKind=deferredCurrentKind=owner.Kind;
                        deferredOriginDepth=deferredCurrentDepth=owner.Depth;
                        deferredPending=true;
                    }
                    else if(owner.Token!=deferredOriginToken
                        ||owner.Parent!=deferredOriginParentToken
                        ||owner.Kind!=deferredOriginKind
                        ||owner.Depth!=deferredOriginDepth)
                        throw new InvalidOperationException(
                            "Fake native deferred retry changed origin.");
                    Add(Owned(hook,10,4));
                }
                else if(hook.Action==12)
                {
                    if(!deferredPending||active.Count!=1
                        ||!DeferredCurrentMatchesTop()
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

            internal void ForgeConditionalOutsideAsKeep(
                uint pc,bool directParent)
            {
                int first=-1;
                GpgxAudioTraceEvent end=default(GpgxAudioTraceEvent);
                GpgxAudioTraceEvent promotion=default(GpgxAudioTraceEvent);
                for(int i=0;i<frameEvents.Count;i++)
                {
                    GpgxAudioTraceEvent value=frameEvents[i];
                    if(value.Pc!=pc)continue;
                    if(first<0)first=i;
                    if(value.Kind==2&&value.ServiceKindId==4)end=value;
                    else if(value.Kind==11)promotion=value;
                }
                if(first<0||end.Kind!=2||(directParent&&promotion.Kind!=11))
                    throw new InvalidOperationException(
                        "Missing fake conditional outside events for forgery.");
                frameEvents.RemoveRange(first,frameEvents.Count-first);
                Add(new GpgxAudioTraceEvent
                {
                    ServiceToken=directParent
                        ?promotion.ServiceToken:end.ServiceToken,
                    ParentToken=directParent?end.ServiceToken:(ushort)0,
                    Pc=pc,Subject=end.Subject,Kind=10,
                    ServiceKindId=directParent?(byte)2:(byte)4,
                    Depth=directParent?(byte)1:(byte)0,
                    SourceCpu=2,Value=0
                });
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
                ActiveService owner=active[active.Count-1];
                GpgxAudioObserverAdapter.SnapshotRange range=ranges[hook.RangeFirst];
                Add(Owned(hook,5,0,range.RangeId));
                GpgxAudioTraceEvent chunk=Owned(hook,6,0,range.RangeId);
                chunk.PayloadLength=1;chunk.Payload=host.ReadMainRamByte(range.Start);Add(chunk);
                GpgxAudioTraceEvent snapshotEnd=Owned(hook,7,0,range.RangeId);
                snapshotEnd.Offset=range.Length;Add(snapshotEnd);
                Add(Owned(hook,2,0,hook.HookToken));
                active.RemoveAt(active.Count-1);
                Push(hook);
                if(deferredPending&&owner.Token==deferredCurrentToken
                    &&owner.Parent==deferredCurrentParentToken
                    &&owner.Kind==deferredCurrentKind
                    &&owner.Depth==deferredCurrentDepth
                    &&deferredCurrentToken==deferredOriginToken
                    &&deferredCurrentParentToken==deferredOriginParentToken
                    &&deferredCurrentKind==deferredOriginKind
                    &&deferredCurrentDepth==deferredOriginDepth)
                {
                    ActiveService successor=active[active.Count-1];
                    deferredCurrentToken=successor.Token;
                    deferredCurrentParentToken=successor.Parent;
                    deferredCurrentKind=successor.Kind;
                    deferredCurrentDepth=successor.Depth;
                }
            }

            private bool HasDeferredConsumeHook(uint pc,byte expectedKind)
            {
                for(int i=0;i<deferredConsumeHooks.Count;i++)
                    if(deferredConsumeHooks[i].Pc==pc
                        &&deferredConsumeHooks[i].ExpectedActiveKind
                            ==expectedKind)return true;
                return false;
            }

            private bool DeferredCurrentMatchesTop()
            {
                if(active.Count==0)return false;
                ActiveService owner=active[active.Count-1];
                return owner.Token==deferredCurrentToken
                    &&owner.Parent==deferredCurrentParentToken
                    &&owner.Kind==deferredCurrentKind
                    &&owner.Depth==deferredCurrentDepth;
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
