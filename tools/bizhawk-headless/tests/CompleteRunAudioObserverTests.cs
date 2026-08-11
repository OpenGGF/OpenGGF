using System;
using System.Collections.Generic;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class CompleteRunAudioObserverTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests expose one bounded native-frame collector",
                ExposesBoundedCollector,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests configure before one exact frame lifecycle",
                ConfiguresBeforeOneExactFrameLifecycle,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests drain only the exact native event count",
                DrainsOnlyExactNativeEventCount,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests stream the exact reusable native drain",
                StreamsExactReusableNativeDrain,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests abort without publication on overflow",
                AbortsWithoutPublicationOnOverflow,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests reject noncontiguous native ordinals",
                RejectsNoncontiguousNativeOrdinals,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests reject reserved and orphan native events",
                RejectsReservedAndOrphanEvents,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests validate nested token parent and depth",
                ValidatesNestedOwnership,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests checkpoint YM latches and proof-arm epoch",
                CheckpointsLatchesAndArmEpoch,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests reconstruct canonical nested services and writes",
                ReconstructsCanonicalServices,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests reject malformed snapshots and unexpected PCs",
                RejectsMalformedCanonicalEvents,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests publish projection and lifecycle atomically",
                PublishesAtomically,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests preserve global begin order across frames",
                PreservesCrossFrameBeginOrder,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests reject malformed tail and reset ordering",
                RejectsMalformedTailAndResetOrdering,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests discard bounded state at a capture cutoff",
                DiscardsBoundedCutoffState,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests restart published coordinates at a carried boundary",
                RestartsPublishedCoordinatesAtCarriedBoundary,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests report bounded native tail on end failure",
                ReportsBoundedNativeTailOnEndFailure,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests retain immutable begin ancestry across promotion",
                RetainsImmutableBeginAncestryAcrossPromotion,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests roll back pending ancestry after rejected promotion",
                RollsBackPendingAncestryAfterRejectedPromotion,
                serial: true));
        }

        private static void RollsBackPendingAncestryAfterRejectedPromotion()
        {
            var api = new FakeTraceApi { Events = PendingDescendantFrame() };
            CompleteRunAudioObserver observer = CreateCrossingWithPendingDescendant(api);
            observer.CaptureFrame(() => { });
            AssertEx.Equal((ushort)1, observer.PendingRootTokenForTesting(3));

            api.Events = new[]
            {
                Canonical(0,2,1,0,2,0,6,0x120,0),
                Canonical(1,11,2,0,3,0,6,0x120,0),
                new GpgxAudioTraceEvent {Ordinal=2,Kind=3,ServiceToken=2,
                    ParentToken=0,ServiceKindId=3,Depth=0,SourceCpu=1,
                    Pc=0x121,Subject=0,Value=0x2a,Reserved=1}
            };
            AssertEx.Throws<InvalidOperationException>(
                () => observer.CaptureFrame(() => { }), "reserved");
            AssertEx.Equal((ushort)1, observer.PendingRootTokenForTesting(3));

            var rejectedApi = new FakeTraceApi { Events = PendingDescendantFrame() };
            CompleteRunAudioObserver rejected =
                CreateCrossingWithPendingDescendant(rejectedApi);
            rejected.CaptureFrame(() => { });
            rejectedApi.Events = new[]
            {
                Canonical(0,2,1,0,2,0,6,0x120,0),
                Canonical(1,11,2,0,3,0,6,0x120,0)
            };
            AssertEx.Throws<InvalidOperationException>(() => rejected.CaptureFrame(
                () => { }, (events, count) =>
                {
                    AssertEx.Equal((ushort)1,
                        rejected.PendingRootTokenForTesting(3));
                    throw new InvalidOperationException("consumer rejected promotion");
                }), "consumer rejected promotion");
            AssertEx.Equal((ushort)1, rejected.PendingRootTokenForTesting(3));
        }

        private static GpgxAudioTraceEvent[] PendingDescendantFrame()
        {
            return new[]
            {
                Canonical(0,1,1,0,2,0,1,0x100,0),
                Canonical(1,1,2,1,3,1,3,0x110,0),
                Canonical(2,1,3,2,4,2,7,0x115,0),
                Canonical(3,2,3,2,4,2,8,0x118,0)
            };
        }

        private static void RetainsImmutableBeginAncestryAcrossPromotion()
        {
            var ordinaryEvents = new[]
            {
                Canonical(0,1,1,0,2,0,1,0x100,0),
                Canonical(1,5,1,0,2,0,7,0x120,0),
                Canonical(2,6,1,0,2,0,7,0x120,0,payloadLength:2,payload:0x2211),
                Canonical(3,7,1,0,2,0,7,0x120,2),
                Canonical(4,2,1,0,2,0,2,0x120,0)
            };
            var legacyApi = new FakeTraceApi { Events = ordinaryEvents };
            var promotionCapableApi = new FakeTraceApi { Events = ordinaryEvents };
            CompleteRunAudioObserver legacy = CreateCanonical(legacyApi);
            CompleteRunAudioObserver promotionCapable = CreateCanonical(
                promotionCapableApi, true);
            AssertEx.Equal(false, legacy.PromotionTransactionsEnabled);
            AssertEx.Equal(true, promotionCapable.PromotionTransactionsEnabled);
            CompleteRunAudioObserver.DriverService legacyService =
                legacy.CaptureCanonicalFrame(() => { }).Services[0];
            CompleteRunAudioObserver.DriverService promotionService =
                promotionCapable.CaptureCanonicalFrame(() => { }).Services[0];
            AssertEx.Equal(legacyService.Token, promotionService.Token);
            AssertEx.Equal(legacyService.ParentToken, promotionService.ParentToken);
            AssertEx.Equal(legacyService.CurrentParentToken,
                promotionService.CurrentParentToken);
            AssertEx.Equal(legacyService.BeginCoordinate,
                promotionService.BeginCoordinate);
            AssertEx.Equal(legacyService.EndCoordinate,
                promotionService.EndCoordinate);
            AssertEx.Equal(legacyService.Snapshots[0].Bytes[0],
                promotionService.Snapshots[0].Bytes[0]);
            int settledScratchCapacity = legacy.ProjectionScratchCapacity;
            legacyApi.Events = ordinaryEvents;
            legacy.CaptureCanonicalFrame(() => { });
            AssertEx.Equal(settledScratchCapacity, legacy.ProjectionScratchCapacity);

            var snapshotApi = new FakeTraceApi
            {
                Events = new[]
                {
                    Canonical(0,1,1,0,2,0,1,0x100,0),
                    Canonical(1,1,2,1,3,1,3,0x110,0),
                    Canonical(2,5,1,0,2,0,7,0x120,0),
                    Canonical(3,6,1,0,2,0,7,0x120,0,payloadLength:2,payload:0x2211),
                    Canonical(4,7,1,0,2,0,7,0x120,2),
                    Canonical(5,2,1,0,2,0,6,0x120,0),
                    Canonical(6,11,2,0,3,0,6,0x120,0),
                    Canonical(7,2,2,0,3,0,4,0x114,0)
                }
            };
            CompleteRunAudioObserver snapshotObserver = CreateCrossingWithSnapshots(snapshotApi);
            CompleteRunAudioObserver.FrameCapture snapshotCapture =
                snapshotObserver.CaptureCanonicalFrame(() => { });
            AssertEx.Equal(2, snapshotCapture.Services.Count);
            AssertEx.Equal(1, snapshotCapture.Services[0].Snapshots.Count);
            AssertEx.Equal((ushort)0, snapshotCapture.Services[1].CurrentParentToken);
            var interposedApi = new FakeTraceApi
            {
                Events = new[]
                {
                    Canonical(0,1,1,0,2,0,1,0x100,0),
                    Canonical(1,1,2,1,3,1,3,0x110,0),
                    Canonical(2,5,1,0,2,0,7,0x120,0),
                    Canonical(3,3,2,1,3,1,0,0x111,0,0x2a),
                    Canonical(4,6,1,0,2,0,7,0x120,0,payloadLength:2,payload:0x2211),
                    Canonical(5,7,1,0,2,0,7,0x120,2),
                    Canonical(6,2,1,0,2,0,6,0x120,0),
                    Canonical(7,11,2,0,3,0,6,0x120,0)
                }
            };
            AssertEx.Throws<InvalidOperationException>(() =>
                CreateCrossingWithSnapshots(interposedApi).CaptureCanonicalFrame(() => { }),
                "promotion snapshot adjacency");

            var api = new FakeTraceApi
            {
                Events = new[]
                {
                    Canonical(0,1,1,0,2,0,1,0x100,0),
                    Canonical(1,1,2,1,3,1,3,0x110,0),
                    Canonical(2,2,1,0,2,0,6,0x120,0),
                    Canonical(3,11,2,0,3,0,6,0x120,0),
                    Canonical(4,3,2,0,3,0,0,0x121,0,0x2a),
                    Canonical(5,2,2,0,3,0,4,0x114,0)
                }
            };
            CompleteRunAudioObserver observer = CreateCrossing(api);
            CompleteRunAudioObserver.FrameCapture capture =
                observer.CaptureCanonicalFrame(() => { });
            AssertEx.Equal(2, capture.Services.Count);
            CompleteRunAudioObserver.DriverService parent = capture.Services[0];
            CompleteRunAudioObserver.DriverService child = capture.Services[1];
            AssertEx.Equal((ushort)0, parent.ParentToken);
            AssertEx.Equal((byte)0, parent.Depth);
            AssertEx.Equal((ushort)1, child.ParentToken);
            AssertEx.Equal((byte)1, child.Depth);
            AssertEx.Equal((ushort)0, child.CurrentParentToken);
            AssertEx.Equal((byte)0, child.CurrentDepth);
            AssertEx.Equal(1, child.AncestryTransitions.Count);
            AssertEx.Equal((ushort)1,
                child.AncestryTransitions[0].PreviousParentToken);
            AssertEx.Equal((ushort)0,
                child.AncestryTransitions[0].CurrentParentToken);
            AssertEx.Equal(3L, child.AncestryTransitions[0].Coordinate);
            AssertEx.Equal(1, child.OwnedChipEvents.Count);

            var resetApi = new FakeTraceApi
            {
                Events = new[]
                {
                    Canonical(0,1,1,0,2,0,1,0x100,0),
                    Canonical(1,1,2,1,3,1,3,0x110,0),
                    Canonical(2,2,1,0,2,0,6,0x120,0),
                    Canonical(3,11,2,0,3,0,6,0x120,0)
                }
            };
            CompleteRunAudioObserver resetObserver = CreateCrossing(resetApi);
            CompleteRunAudioObserver.FrameCapture parentFrame =
                resetObserver.CaptureCanonicalFrame(() => { });
            AssertEx.Equal(1, parentFrame.Services.Count);
            resetApi.Events = new[]
            {
                new GpgxAudioTraceEvent { Ordinal=0,Kind=8,ServiceToken=9,
                    ServiceKindId=2,SourceCpu=3,Subject=1 },
                new GpgxAudioTraceEvent { Ordinal=1,Kind=2,ServiceToken=2,
                    ParentToken=0,ServiceKindId=3,Depth=0,SourceCpu=3,Flags=2 },
                new GpgxAudioTraceEvent { Ordinal=2,Kind=9,ServiceToken=9,
                    ServiceKindId=2,SourceCpu=3 }
            };
            CompleteRunAudioObserver.FrameCapture resetFrame =
                resetObserver.CaptureCanonicalFrame(() => { });
            AssertEx.Equal(2, resetFrame.Services.Count);
            CompleteRunAudioObserver.DriverService cancelled = resetFrame.Services[0];
            AssertEx.Equal(true, cancelled.Cancelled);
            AssertEx.Equal((ushort)1, cancelled.ParentToken);
            AssertEx.Equal((byte)1, cancelled.Depth);
            AssertEx.Equal((ushort)0, cancelled.CurrentParentToken);
            AssertEx.Equal((byte)0, cancelled.CurrentDepth);
            AssertEx.Equal(1, cancelled.AncestryTransitions.Count);
        }

        private static void ExposesBoundedCollector()
        {
            Type observer = typeof(GpgxHost).Assembly.GetType(
                "OpenGGF.BizHawk.Headless.CompleteRunAudioObserver", false);
            AssertEx.Equal(true, observer != null);
            AssertEx.Equal(2, Array.FindAll(observer.GetMethods(),
                method => method.Name == "CaptureFrame").Length);
            AssertEx.Equal(true, observer.GetMethod("ResetAfterLoad") != null);
            AssertEx.Equal(1, observer.GetConstructors().Length);
            AssertEx.Equal(6, observer.GetConstructors()[0].GetParameters().Length);
        }

        private static void ReportsBoundedNativeTailOnEndFailure()
        {
            var api = new FakeTraceApi
            {
                EndStatus = -3,
                FirstFault = new GpgxAudioObserverAdapter.FirstFault
                    { Reason=5, SourceCpu=1, Pc=0x9C, ActiveKind=2,
                      ActiveDepth=1, ContinuationCount=4, ContinuationLimit=4 },
                Events = new[]
                {
                    new GpgxAudioTraceEvent { Ordinal=0, Kind=1,
                        ServiceToken=1, ParentToken=0, ServiceKindId=1,
                        Depth=0, Pc=0x71B4C, Subject=100, SourceCpu=2 },
                    new GpgxAudioTraceEvent { Ordinal=1, Kind=1,
                        ServiceToken=2, ParentToken=1, ServiceKindId=2,
                        Depth=1, Pc=0x77, Subject=1, SourceCpu=1 }
                }
            };
            CompleteRunAudioObserver observer = Create(api);
            AssertEx.Throws<InvalidOperationException>(
                () => observer.CaptureFrame(() => { }, (events, count) => { }),
                "first_fault=5:1:9c:2:1:4:4 native_tail=0:1:1:0:1:0:71b4c:100:0:0|"
                    + "1:1:2:1:2:1:77:1:0:0");
            AssertEx.Equal(0, api.AbortCalls);
        }

        private static void ConfiguresBeforeOneExactFrameLifecycle()
        {
            var api = new FakeTraceApi();
            CompleteRunAudioObserver observer = Create(api);
            AssertEx.Equal("configure", string.Join(",", api.Calls));
            GpgxAudioTraceEvent[] events = observer.CaptureFrame(() => api.Calls.Add("advance"));
            AssertEx.Equal(0, events.Length);
            AssertEx.Equal("configure,begin,advance,end,count,drain:0", string.Join(",", api.Calls));
            AssertEx.Equal(0, api.AbortCalls);
        }

        private static void DrainsOnlyExactNativeEventCount()
        {
            var api = new FakeTraceApi
            {
                Events = new[]
                {
                    Event(0, 1, 1, 0, 1, 0),
                    Event(1, 2, 1, 0, 1, 0)
                }
            };
            CompleteRunAudioObserver observer = Create(api);
            GpgxAudioTraceEvent[] events = observer.CaptureFrame(() => { });
            AssertEx.Equal(2, events.Length);
            AssertEx.Equal(0u, events[0].Ordinal);
            AssertEx.Equal(1u, events[1].Ordinal);
            AssertEx.Equal(2u, api.LastDrainCapacity);
            AssertEx.Equal("configure,begin,end,count,drain:2", string.Join(",", api.Calls));
        }

        private static void StreamsExactReusableNativeDrain()
        {
            var api = new FakeTraceApi
            {
                Events = new[]
                {
                    Event(0, 1, 1, 0, 1, 0),
                    Event(1, 2, 1, 0, 1, 0)
                }
            };
            CompleteRunAudioObserver observer = Create(api);
            GpgxAudioTraceEvent[] firstBuffer = null;
            int firstCount = -1;
            observer.CaptureFrame(() => { }, (buffer, count) =>
            {
                firstBuffer = buffer;
                firstCount = count;
            });
            observer.CaptureFrame(() => { }, (buffer, count) =>
            {
                AssertEx.Equal(true, object.ReferenceEquals(firstBuffer, buffer));
                AssertEx.Equal(firstCount, count);
            });
            AssertEx.Equal(2, firstCount);
            AssertEx.Equal(2u, api.LastDrainCapacity);
            AssertEx.Equal(true,observer.LastCapture==null);
            observer.CaptureCanonicalFrame(()=>{});
            AssertEx.Equal(true,observer.LastCapture!=null);
            observer.CaptureFrame(()=>{},(buffer,count)=>{});
            AssertEx.Equal(true,observer.LastCapture==null);
            observer.CaptureCanonicalFrame(()=>{});
            CompleteRunAudioObserver.FrameCapture retained=observer.LastCapture;
            AssertEx.Throws<InvalidOperationException>(()=>observer.CaptureFrame(()=>{},(buffer,count)=>
                {throw new InvalidOperationException("consumer rejected");}),"consumer rejected");
            AssertEx.Equal(true,object.ReferenceEquals(retained,observer.LastCapture));
        }

        private static void AbortsWithoutPublicationOnOverflow()
        {
            var api = new FakeTraceApi { Overflow = 1 };
            CompleteRunAudioObserver observer = Create(api);
            AssertEx.Throws<InvalidOperationException>(
                () => observer.CaptureFrame(() => { }), "overflow");
            AssertEx.Equal(1, api.AbortCalls);
            AssertEx.Equal(false, api.Calls.Contains("drain:0"));
        }

        private static void RejectsNoncontiguousNativeOrdinals()
        {
            var api = new FakeTraceApi
            {
                Events = new[]
                {
                    Event(0, 1, 1, 0, 1, 0),
                    Event(2, 2, 1, 0, 1, 0)
                }
            };
            CompleteRunAudioObserver observer = Create(api);
            AssertEx.Throws<InvalidOperationException>(
                () => observer.CaptureFrame(() => { }), "ordinal");
        }

        private static void RejectsReservedAndOrphanEvents()
        {
            var reserved = new FakeTraceApi { Events = new[]
                { new GpgxAudioTraceEvent { Ordinal = 0, Kind = 3, Reserved = 1 } } };
            AssertEx.Throws<InvalidOperationException>(
                () => Create(reserved).CaptureFrame(() => { }), "reserved");
            var orphan = new FakeTraceApi { Events = new[]
                { new GpgxAudioTraceEvent { Ordinal = 0, Kind = 4, SourceCpu = 1 } } };
            AssertEx.Throws<InvalidOperationException>(
                () => Create(orphan).CaptureFrame(() => { }), "orphan");
        }

        private static void ValidatesNestedOwnership()
        {
            var api = new FakeTraceApi { Events = new[]
            {
                Event(0, 1, 1, 0, 1, 0),
                Event(1, 1, 2, 99, 2, 1)
            } };
            AssertEx.Throws<InvalidOperationException>(
                () => Create(api).CaptureFrame(() => { }), "parent");
        }

        private static void CheckpointsLatchesAndArmEpoch()
        {
            var api = new FakeTraceApi { Events = new[]
            {
                Event(0, 1, 1, 0, 1, 0),
                new GpgxAudioTraceEvent { Ordinal = 1, Kind = 3, ServiceToken = 1,
                    ServiceKindId = 1, SourceCpu = 2, Subject = 0, Value = 0x2A },
                new GpgxAudioTraceEvent { Ordinal = 2, Kind = 3, ServiceToken = 1,
                    ServiceKindId = 1, SourceCpu = 2, Subject = 2, Value = 0x30 },
                new GpgxAudioTraceEvent { Ordinal = 3, Kind = 2, ServiceToken = 1,
                    ServiceKindId = 1, SourceCpu = 2, Subject = 9, Pc = 0x120 }
            } };
            CompleteRunAudioObserver observer = Create(api, true);
            observer.CaptureFrame(() => { });
            AssertEx.Equal((byte)0x2A, observer.YmPort0Address);
            AssertEx.Equal((byte)0x30, observer.YmPort1Address);
            AssertEx.Equal(true, observer.IsArmed);
            AssertEx.Equal(1L, observer.ArmEpoch);
            CompleteRunAudioObserver.Checkpoint checkpoint = observer.CreateCheckpoint();

            api.Events = new[]
            {
                new GpgxAudioTraceEvent { Ordinal = 0, Kind = 8, ServiceToken = 2,
                    ServiceKindId = 1, SourceCpu = 3 },
                new GpgxAudioTraceEvent { Ordinal = 1, Kind = 9, ServiceToken = 2,
                    ServiceKindId = 1, SourceCpu = 3 }
            };
            observer.CaptureFrame(() => { });
            AssertEx.Equal(false, observer.IsArmed);
            AssertEx.Equal(2L, observer.ArmEpoch);
            AssertEx.Equal(1, observer.LastCapture.Services.Count);
            AssertEx.Equal(1, observer.LastCapture.Resets.Count);
            AssertEx.Equal(true, object.ReferenceEquals(observer.LastCapture.Services[0],
                observer.LastCapture.Resets[0].Service));
            AssertEx.Equal((ushort)2, observer.LastCapture.Resets[0].Token);
            AssertEx.Throws<InvalidOperationException>(
                () => observer.RestoreCheckpoint(checkpoint), "epoch");
            AssertEx.Equal(0, api.DisableCalls);
        }

        private static void ReconstructsCanonicalServices()
        {
            var api = new FakeTraceApi { Events = new[]
            {
                Canonical(0, 1, 1, 0, 2, 0, 1, 0x100, 0),
                Canonical(1, 3, 1, 0, 2, 0, 0, 0x102, 0, value: 0x2A),
                Canonical(2, 3, 1, 0, 2, 0, 1, 0x104, 0, value: 0x7F),
                Canonical(3, 1, 2, 1, 3, 1, 3, 0x110, 0),
                Canonical(4, 4, 2, 1, 3, 1, 0, 0x112, 0, value: 0x9F),
                Canonical(5, 5, 2, 1, 3, 1, 7, 0x114, 0),
                Canonical(6, 6, 2, 1, 3, 1, 7, 0x114, 0, payloadLength: 2, payload: 0xBBAA),
                Canonical(7, 7, 2, 1, 3, 1, 7, 0x114, 2),
                Canonical(8, 2, 2, 1, 3, 1, 4, 0x114, 0),
                Canonical(9, 5, 1, 0, 2, 0, 7, 0x120, 0),
                Canonical(10, 6, 1, 0, 2, 0, 7, 0x120, 0, payloadLength: 2, payload: 0xDDCC),
                Canonical(11, 7, 1, 0, 2, 0, 7, 0x120, 2),
                Canonical(12, 2, 1, 0, 2, 0, 2, 0x120, 0)
            } };
            CompleteRunAudioObserver observer = CreateCanonical(api);
            CompleteRunAudioObserver.FrameCapture capture = observer.CaptureCanonicalFrame(() => { });
            AssertEx.Equal(true, object.ReferenceEquals(capture,observer.LastCapture));
            AssertEx.Equal(true,capture.RawEventsRetained);
            AssertEx.Equal(13, capture.RawEvents.Count);
            AssertEx.Equal(2, capture.Services.Count);
            AssertEx.Equal((ushort)1, capture.Services[0].Token);
            AssertEx.Equal((ushort)2, capture.Services[1].Token);
            AssertEx.Equal(1, capture.Services[0].ChipWrites.Count);
            AssertEx.Equal(1, capture.Services[1].ChipWrites.Count);
            var ym = (CompleteRunAudioObserver.YmWrite)capture.Services[0].ChipWrites[0];
            AssertEx.Equal((byte)0, ym.Port); AssertEx.Equal((byte)0x2A, ym.Register);
            AssertEx.Equal((byte)0x7F, ym.Value);
            var psg = (CompleteRunAudioObserver.PsgWrite)capture.Services[1].ChipWrites[0];
            AssertEx.Equal((byte)0x9F, psg.Value);
            AssertEx.Equal((byte)0xAA, capture.Services[1].Snapshots[0].Bytes[0]);
            AssertEx.Equal((byte)0xDD, capture.Services[0].Snapshots[0].Bytes[1]);
            AssertEx.Equal(3, capture.FlattenedChipOrdinals.Count);
            AssertEx.Equal((uint)1,capture.Services[0].RawChipOrdinals[0]);
            AssertEx.Equal(false,capture.RawEvents is GpgxAudioTraceEvent[]);
            AssertEx.Equal(false,capture.Services is CompleteRunAudioObserver.DriverService[]);
            byte[] changed=capture.Services[0].Snapshots[0].Bytes;changed[0]=0;
            AssertEx.Equal((byte)0xCC,capture.Services[0].Snapshots[0].Bytes[0]);
        }

        private static void RejectsMalformedCanonicalEvents()
        {
            var badPc = new FakeTraceApi { Events = new[]
                { Canonical(0, 1, 1, 0, 2, 0, 1, 0x101, 0) } };
            AssertEx.Throws<InvalidOperationException>(
                () => CreateCanonical(badPc).CaptureFrame(() => { }), "PC");
            var gap = new FakeTraceApi { Events = new[]
            {
                Canonical(0, 1, 1, 0, 2, 0, 1, 0x100, 0),
                Canonical(1, 5, 1, 0, 2, 0, 7, 0x120, 0),
                Canonical(2, 6, 1, 0, 2, 0, 7, 0x120, 1, payloadLength: 1, payload: 0xAA)
            } };
            AssertEx.Throws<InvalidOperationException>(
                () => CreateCanonical(gap).CaptureFrame(() => { }), "snapshot");
        }

        private static void PublishesAtomically()
        {
            var api=new FakeTraceApi{Events=new[]{Event(0,1,1,0,1,0),
                new GpgxAudioTraceEvent{Ordinal=1,Kind=3,ServiceToken=1,ServiceKindId=1,
                    SourceCpu=1,Subject=0,Value=0x2A},Event(2,2,1,0,1,0)}};
            CompleteRunAudioObserver observer=Create(api);
            AssertEx.Throws<InvalidOperationException>(()=>observer.CaptureFrame(()=>{},(b,c)=>
                {throw new InvalidOperationException("consumer rejected");}),"consumer rejected");
            AssertEx.Equal((byte)0,observer.YmPort0Address);
            AssertEx.Equal(0L,observer.ArmEpoch);
            AssertEx.Equal(0,api.AbortCalls);
            AssertEx.Throws<InvalidOperationException>(()=>observer.CaptureFrame(()=>{}),"faulted");
        }

        private static void PreservesCrossFrameBeginOrder()
        {
            var api=new FakeTraceApi{Events=new[]{
                Canonical(0,1,1,0,2,0,1,0x100,0),Canonical(1,1,2,1,3,1,3,0x110,0),
                Canonical(2,5,2,1,3,1,7,0x114,0),Canonical(3,6,2,1,3,1,7,0x114,0,payloadLength:2,payload:0x2211),
                Canonical(4,7,2,1,3,1,7,0x114,2),Canonical(5,2,2,1,3,1,4,0x114,0)}};
            CompleteRunAudioObserver observer=CreateCanonical(api);
            AssertEx.Equal(0,observer.CaptureCanonicalFrame(()=>{}).Services.Count);
            api.Events=new[]{Canonical(0,5,1,0,2,0,7,0x120,0),
                Canonical(1,6,1,0,2,0,7,0x120,0,payloadLength:2,payload:0x4433),
                Canonical(2,7,1,0,2,0,7,0x120,2),Canonical(3,2,1,0,2,0,2,0x120,0)};
            CompleteRunAudioObserver.FrameCapture capture=observer.CaptureCanonicalFrame(()=>{});
            AssertEx.Equal(2,capture.Services.Count);AssertEx.Equal((ushort)1,capture.Services[0].Token);
            AssertEx.Equal((ushort)2,capture.Services[1].Token);
        }

        private static void RejectsMalformedTailAndResetOrdering()
        {
            var tailApi=new FakeTraceApi{Events=new[]{Canonical(0,1,1,0,2,0,1,0x100,0),
                Canonical(1,2,1,0,2,0,5,0x130,0),Canonical(2,4,1,0,2,0,0,0x132,0,value:0x9F)}};
            AssertEx.Throws<InvalidOperationException>(()=>CreateCanonical(tailApi).CaptureCanonicalFrame(()=>{}),"tail");
            var resetApi=new FakeTraceApi{Events=new[]{Canonical(0,1,1,0,2,0,1,0x100,0),
                new GpgxAudioTraceEvent{Ordinal=1,Kind=8,ServiceToken=9,ServiceKindId=2,SourceCpu=3,Subject=1},
                new GpgxAudioTraceEvent{Ordinal=2,Kind=4,ServiceToken=9,ServiceKindId=2,SourceCpu=3,Value=0x9F}}};
            AssertEx.Throws<InvalidOperationException>(()=>CreateCanonical(resetApi).CaptureCanonicalFrame(()=>{}),"reset");
            var badEnd=new FakeTraceApi{Events=new[]{Event(0,1,1,0,1,0),Event(1,2,1,0,1,0)}};
            badEnd.Events[1].Offset=1;
            AssertEx.Throws<InvalidOperationException>(()=>Create(badEnd).CaptureCanonicalFrame(()=>{}),"completion fields");
            var badReset=new FakeTraceApi{Events=new[]{new GpgxAudioTraceEvent{Ordinal=0,Kind=8,
                ServiceToken=9,ServiceKindId=2,SourceCpu=3,Offset=1}}};
            AssertEx.Throws<InvalidOperationException>(()=>CreateCanonical(badReset).CaptureCanonicalFrame(()=>{}),"reset begin fields");
            var badZ80Chip=new FakeTraceApi{Events=new[]{Event(0,1,1,0,1,0),
                new GpgxAudioTraceEvent{Ordinal=1,Kind=4,ServiceToken=1,ServiceKindId=1,
                    SourceCpu=1,Pc=0x10000,Value=0x9f}}};
            AssertEx.Throws<InvalidOperationException>(()=>Create(badZ80Chip).CaptureCanonicalFrame(()=>{}),"Z80 chip PC");
            var badM68kChip=new FakeTraceApi{Events=new[]{Event(0,1,1,0,1,0),
                new GpgxAudioTraceEvent{Ordinal=1,Kind=4,ServiceToken=1,ServiceKindId=1,
                    SourceCpu=2,Pc=0x1000000,Value=0x9f}}};
            AssertEx.Throws<InvalidOperationException>(()=>Create(badM68kChip).CaptureCanonicalFrame(()=>{}),"M68K chip PC");
            var badResetSourceOutside=new FakeTraceApi{Events=new[]{Event(0,1,1,0,1,0),
                new GpgxAudioTraceEvent{Ordinal=1,Kind=4,ServiceToken=1,ServiceKindId=1,
                    SourceCpu=3,Value=0x9f}}};
            AssertEx.Throws<InvalidOperationException>(()=>Create(badResetSourceOutside).CaptureCanonicalFrame(()=>{}),"chip source");
            var badResetChip=new FakeTraceApi{Events=new[]{new GpgxAudioTraceEvent{Ordinal=0,Kind=8,
                ServiceToken=9,ServiceKindId=2,SourceCpu=3},new GpgxAudioTraceEvent{Ordinal=1,Kind=4,
                ServiceToken=9,ServiceKindId=2,SourceCpu=3,Pc=1,Value=0x9f}}};
            AssertEx.Throws<InvalidOperationException>(()=>CreateCanonical(badResetChip).CaptureCanonicalFrame(()=>{}),"reset chip source/PC");
            var badZ80ResetChip=new FakeTraceApi{Events=new[]{new GpgxAudioTraceEvent{Ordinal=0,Kind=8,
                ServiceToken=9,ServiceKindId=2,SourceCpu=3},new GpgxAudioTraceEvent{Ordinal=1,Kind=4,
                ServiceToken=9,ServiceKindId=2,SourceCpu=1,Value=0x9f}}};
            AssertEx.Throws<InvalidOperationException>(()=>CreateCanonical(badZ80ResetChip).CaptureCanonicalFrame(()=>{}),"reset chip source/PC");
        }

        private static void DiscardsBoundedCutoffState()
        {
            var api=new FakeTraceApi{Events=new[]{
                Canonical(0,1,1,0,2,0,1,0x100,0),Canonical(1,1,2,1,3,1,3,0x110,0),
                Canonical(2,5,2,1,3,1,7,0x114,0),Canonical(3,6,2,1,3,1,7,0x114,0,payloadLength:2,payload:0x2211),
                Canonical(4,7,2,1,3,1,7,0x114,2),Canonical(5,2,2,1,3,1,4,0x114,0)}};
            CompleteRunAudioObserver observer=CreateCanonical(api);
            observer.CaptureCanonicalFrame(()=>{});
            AssertEx.Equal(1,observer.ActiveServiceDepth);AssertEx.Equal(1,observer.PendingServiceCount);
            CompleteRunAudioObserver.CutoffFrontier frontier=observer.CaptureCutoffFrontier();
            AssertEx.Equal(1,frontier.ActiveServices.Count);AssertEx.Equal((ushort)1,frontier.ActiveServices[0].Token);
            AssertEx.Equal(false,frontier.ActiveServices[0].IsComplete);
            AssertEx.Equal(1,frontier.PendingServices.Count);AssertEx.Equal((ushort)2,frontier.PendingServices[0].Token);
            AssertEx.Equal(true,frontier.PendingServices[0].IsComplete);
            observer.DiscardCutoffState();
            AssertEx.Equal(0,observer.ActiveServiceDepth);AssertEx.Equal(0,observer.PendingServiceCount);
            AssertEx.Equal(1,frontier.ActiveServices.Count);AssertEx.Equal(1,frontier.PendingServices.Count);
            AssertEx.Equal(1,api.DisableCalls);
            AssertEx.Throws<InvalidOperationException>(()=>observer.CaptureCanonicalFrame(()=>{}),"faulted");
        }

        private static void RestartsPublishedCoordinatesAtCarriedBoundary()
        {
            var api=new FakeTraceApi{Events=new[]{
                Canonical(0,1,1,0,2,0,1,0x100,0),
                Canonical(1,3,1,0,2,0,0,0x101,0,0x2a)}};
            CompleteRunAudioObserver observer=CreateCanonical(api);
            observer.CaptureCanonicalFrame(()=>{});
            CompleteRunAudioObserver.CutoffFrontier frontier=
                observer.CaptureBoundaryFrontierAndResetPublication();
            AssertEx.Equal(0L,frontier.ActiveServices[0].BeginCoordinate);

            api.Events=new[]{Canonical(0,3,1,0,2,0,1,0x102,0,0x55)};
            CompleteRunAudioObserver.FrameCapture capture=
                observer.CaptureCanonicalFrame(()=>{});
            AssertEx.Equal(0L,capture.FlattenedChipOrdinals[0]);
            AssertEx.Equal((byte)0x2a,observer.YmPort0Address);
        }

        private static GpgxAudioTraceEvent Event(uint ordinal, byte kind,
            ushort token, ushort parent, byte serviceKind, byte depth)
        {
            ushort subject = kind == 1 ? (ushort)(serviceKind == 2 ? 3 : 1)
                : kind == 2 ? (ushort)2 : (ushort)0;
            uint pc = kind == 1 ? (serviceKind == 2 ? 0x110u : 0x100u)
                : kind == 2 ? 0x120u : 0u;
            return new GpgxAudioTraceEvent { Ordinal = ordinal, Kind = kind,
                ServiceToken = token, ParentToken = parent,
                ServiceKindId = serviceKind, Depth = depth, SourceCpu = 1,
                Subject=subject,Pc=pc };
        }

        private static GpgxAudioTraceEvent Canonical(uint ordinal, byte kind,
            ushort token, ushort parent, byte serviceKind, byte depth, ushort subject,
            uint pc, ushort offset, byte value = 0, byte payloadLength = 0, ulong payload = 0)
        {
            return new GpgxAudioTraceEvent { Ordinal=ordinal, Kind=kind,
                ServiceToken=token, ParentToken=parent, ServiceKindId=serviceKind,
                Depth=depth, Subject=subject, Pc=pc, Offset=offset, Value=value,
                PayloadLength=payloadLength, Payload=payload, SourceCpu=1 };
        }

        private static CompleteRunAudioObserver CreateCanonical(FakeTraceApi api)
        {
            return CreateCanonical(api, false);
        }

        private static CompleteRunAudioObserver CreateCanonical(FakeTraceApi api,
            bool includeUnusedPromotion)
        {
            var config = new GpgxAudioObserverAdapter.Config
            {
                Magic=0x31544147, AbiVersion=1, StructSize=64, KindSize=16,
                HookSize=32, RangeSize=16, EventSize=32, MaxDepth=8,
                WatchMaskBytes=8192, HookCount=(uint)(includeUnusedPromotion?6:5), RangeCount=1,
                EventCapacity=65536, KindCount=2, ResetServiceKind=2
            };
            var kinds = new[]
            {
                new GpgxAudioObserverAdapter.ServiceKind { KindId=2, Flags=4 },
                new GpgxAudioObserverAdapter.ServiceKind { KindId=3 }
            };
            var hooks = new List<GpgxAudioObserverAdapter.ServiceHook>
            {
                new GpgxAudioObserverAdapter.ServiceHook { HookToken=1,Action=1,Cpu=1,Pc=0x100,ServiceKindId=2 },
                new GpgxAudioObserverAdapter.ServiceHook { HookToken=3,Action=1,Cpu=1,Pc=0x110,ServiceKindId=3,ExpectedActiveKind=2 },
                new GpgxAudioObserverAdapter.ServiceHook { HookToken=4,Action=2,Cpu=1,Pc=0x114,ExpectedActiveKind=3,RangeCount=1 },
                new GpgxAudioObserverAdapter.ServiceHook { HookToken=2,Action=2,Cpu=1,Pc=0x120,ExpectedActiveKind=2,RangeCount=1 },
                new GpgxAudioObserverAdapter.ServiceHook { HookToken=5,Action=4,Cpu=1,Pc=0x130,ServiceKindId=3,ExpectedActiveKind=2 }
            };
            if(includeUnusedPromotion)hooks.Add(new GpgxAudioObserverAdapter.ServiceHook
                {HookToken=6,Action=8,Cpu=1,Pc=0x124,ServiceKindId=2,
                    ExpectedActiveKind=3,RangeCount=1});
            var ranges = new[] { new GpgxAudioObserverAdapter.SnapshotRange
                { RangeId=7, Start=0, Length=2 } };
            return new CompleteRunAudioObserver(api,config,new byte[8192],kinds,hooks.ToArray(),ranges);
        }

        private static CompleteRunAudioObserver CreateCrossing(FakeTraceApi api)
        {
            var config = new GpgxAudioObserverAdapter.Config
            {
                Magic=0x31544147, AbiVersion=3, StructSize=64, KindSize=16,
                HookSize=32, RangeSize=16, EventSize=32, MaxDepth=8,
                WatchMaskBytes=8192, HookCount=5, RangeCount=0,
                EventCapacity=65536, KindCount=2, ResetServiceKind=2
            };
            var kinds = new[]
            {
                new GpgxAudioObserverAdapter.ServiceKind { KindId=2, Flags=4 },
                new GpgxAudioObserverAdapter.ServiceKind { KindId=3 }
            };
            var hooks = new[]
            {
                new GpgxAudioObserverAdapter.ServiceHook { HookToken=1,Action=1,Cpu=1,Pc=0x100,ServiceKindId=2 },
                new GpgxAudioObserverAdapter.ServiceHook { HookToken=3,Action=1,Cpu=1,Pc=0x110,ServiceKindId=3,ExpectedActiveKind=2 },
                new GpgxAudioObserverAdapter.ServiceHook { HookToken=4,Action=2,Cpu=1,Pc=0x114,ExpectedActiveKind=3 },
                new GpgxAudioObserverAdapter.ServiceHook { HookToken=2,Action=2,Cpu=1,Pc=0x120,ExpectedActiveKind=2 },
                new GpgxAudioObserverAdapter.ServiceHook { HookToken=6,Action=8,Cpu=1,Pc=0x120,ServiceKindId=2,ExpectedActiveKind=3 }
            };
            return new CompleteRunAudioObserver(api,config,new byte[8192],
                kinds,hooks,new GpgxAudioObserverAdapter.SnapshotRange[0]);
        }

        private static CompleteRunAudioObserver CreateCrossingWithSnapshots(FakeTraceApi api)
        {
            var config = new GpgxAudioObserverAdapter.Config
            {
                Magic=0x31544147, AbiVersion=3, StructSize=64, KindSize=16,
                HookSize=32, RangeSize=16, EventSize=32, MaxDepth=8,
                WatchMaskBytes=8192, HookCount=5, RangeCount=1,
                EventCapacity=65536, KindCount=2, ResetServiceKind=2
            };
            var kinds = new[]
            {
                new GpgxAudioObserverAdapter.ServiceKind { KindId=2, Flags=4 },
                new GpgxAudioObserverAdapter.ServiceKind { KindId=3 }
            };
            var hooks = new[]
            {
                new GpgxAudioObserverAdapter.ServiceHook { HookToken=1,Action=1,Cpu=1,Pc=0x100,ServiceKindId=2 },
                new GpgxAudioObserverAdapter.ServiceHook { HookToken=3,Action=1,Cpu=1,Pc=0x110,ServiceKindId=3,ExpectedActiveKind=2 },
                new GpgxAudioObserverAdapter.ServiceHook { HookToken=4,Action=2,Cpu=1,Pc=0x114,ExpectedActiveKind=3 },
                new GpgxAudioObserverAdapter.ServiceHook { HookToken=2,Action=2,Cpu=1,Pc=0x120,ExpectedActiveKind=2 },
                new GpgxAudioObserverAdapter.ServiceHook { HookToken=6,Action=8,Cpu=1,Pc=0x120,ServiceKindId=2,ExpectedActiveKind=3,RangeCount=1 }
            };
            var ranges = new[] { new GpgxAudioObserverAdapter.SnapshotRange
                { RangeId=7, Start=0, Length=2 } };
            return new CompleteRunAudioObserver(api,config,new byte[8192],kinds,hooks,ranges);
        }

        private static CompleteRunAudioObserver CreateCrossingWithPendingDescendant(
            FakeTraceApi api)
        {
            var config = new GpgxAudioObserverAdapter.Config
            {
                Magic=0x31544147,AbiVersion=3,StructSize=64,KindSize=16,
                HookSize=32,RangeSize=16,EventSize=32,MaxDepth=8,
                WatchMaskBytes=8192,HookCount=6,EventCapacity=65536,
                KindCount=3,ResetServiceKind=2
            };
            var kinds = new[]
            {
                new GpgxAudioObserverAdapter.ServiceKind {KindId=2,Flags=4},
                new GpgxAudioObserverAdapter.ServiceKind {KindId=3},
                new GpgxAudioObserverAdapter.ServiceKind {KindId=4}
            };
            var hooks = new[]
            {
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=1,Action=1,Cpu=1,Pc=0x100,ServiceKindId=2},
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=3,Action=1,Cpu=1,Pc=0x110,ServiceKindId=3,ExpectedActiveKind=2},
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=7,Action=1,Cpu=1,Pc=0x115,ServiceKindId=4,ExpectedActiveKind=3},
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=8,Action=2,Cpu=1,Pc=0x118,ExpectedActiveKind=4},
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=4,Action=2,Cpu=1,Pc=0x114,ExpectedActiveKind=3},
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=6,Action=8,Cpu=1,Pc=0x120,ServiceKindId=2,ExpectedActiveKind=3}
            };
            return new CompleteRunAudioObserver(api,config,new byte[8192],kinds,
                hooks,new GpgxAudioObserverAdapter.SnapshotRange[0]);
        }

        private static CompleteRunAudioObserver Create(FakeTraceApi api)
        {
            return Create(api, false);
        }

        private static CompleteRunAudioObserver Create(FakeTraceApi api, bool armOnCompletion)
        {
            var config = new GpgxAudioObserverAdapter.Config
            {
                Magic = 0x31544147, AbiVersion = 1, StructSize = 64,
                KindSize = 16, HookSize = 32, RangeSize = 16, EventSize = 32,
                WatchMaskBytes = 8192, EventCapacity = 65536
            };
            var hooks = new[] {
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=1,Action=1,Cpu=1,Pc=0x100,ServiceKindId=1},
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=3,Action=1,Cpu=1,Pc=0x110,ServiceKindId=2,ExpectedActiveKind=1},
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=2,Action=2,Cpu=1,Pc=0x120,ExpectedActiveKind=1},
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=9,Action=2,Cpu=2,Pc=0x120,ExpectedActiveKind=1,Flags=(byte)(armOnCompletion?1:0)} };
            var kinds = new[] {
                new GpgxAudioObserverAdapter.ServiceKind {KindId=1,Flags=4},
                new GpgxAudioObserverAdapter.ServiceKind {KindId=2} };
            config.HookCount=(uint)hooks.Length;config.KindCount=(ushort)kinds.Length;config.ResetServiceKind=1;
            return new CompleteRunAudioObserver(api, config, new byte[8192],
                kinds,
                hooks,
                new GpgxAudioObserverAdapter.SnapshotRange[0]);
        }

        private sealed class FakeTraceApi : IGpgxAudioTraceApi
        {
            public readonly List<string> Calls = new List<string>();
            public GpgxAudioTraceEvent[] Events = new GpgxAudioTraceEvent[0];
            public uint Overflow;
            public int EndStatus;
            public GpgxAudioObserverAdapter.FirstFault FirstFault;
            public int AbortCalls;
            public int DisableCalls;
            public uint LastDrainCapacity;
            public uint AbiVersion { get { return 1; } }
            public uint EventSize { get { return 32; } }
            public uint Capacity { get { return 65536; } }
            public int Configure(ref GpgxAudioObserverAdapter.Config config, byte[] mask,
                GpgxAudioObserverAdapter.ServiceKind[] kinds,
                GpgxAudioObserverAdapter.ServiceHook[] hooks,
                GpgxAudioObserverAdapter.SnapshotRange[] ranges)
            { Calls.Add("configure"); return 0; }
            public int BeginFrame() { Calls.Add("begin"); return 0; }
            public int EndFrame() { Calls.Add("end"); return EndStatus; }
            public int EventCount(out uint count, out uint overflow)
            { Calls.Add("count"); count = (uint)Events.Length; overflow = Overflow; return 0; }
            public int Drain(GpgxAudioTraceEvent[] events, uint capacity, out uint count)
            {
                Calls.Add("drain:" + capacity); LastDrainCapacity = capacity;
                count = (uint)Events.Length;
                if (events != null) Array.Copy(Events, events, Events.Length);
                return 0;
            }
            public int AbortFrame() { Calls.Add("abort"); AbortCalls++; return 0; }
            public int GetFirstFault(out GpgxAudioObserverAdapter.FirstFault fault)
            { Calls.Add("fault"); fault=FirstFault; return 0; }
            public int Disable() { Calls.Add("disable"); DisableCalls++; return 0; }
            public int BeginPublicationEpoch() { Calls.Add("publication"); return 0; }
        }
    }
}
