using System;
using System.Collections.Generic;
using System.Reflection;

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
                "CompleteRunAudioObserverTests validate ABI four observation A7 payloads atomically",
                ValidatesAbiFourObservationA7PayloadsAtomically,
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
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests compose conditional keep with direct-parent promotion",
                ComposesConditionalKeepWithDirectParentPromotion,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests keep projection result allocation-free",
                KeepsProjectionResultAllocationFree,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests consume one deferred nested begin transaction",
                ConsumesDeferredNestedBeginTransaction,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests project one deferred tail owner transfer",
                ProjectsDeferredTailOwnerTransfer,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests project a promoted deferred tail owner transfer",
                ProjectsPromotedDeferredTailOwnerTransfer,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests reject malformed deferred tail owner transfers",
                RejectsMalformedDeferredTailOwnerTransfers,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests preserve deferred shared PC selection outcomes",
                PreservesDeferredSharedPcSelectionOutcomes,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests roll back deferred tail owner transfers",
                RollsBackDeferredTailOwnerTransfers,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests carry deferred current owner through cutoffs",
                CarriesDeferredCurrentOwnerThroughCutoffs,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests reject corrupt deferred begin transactions",
                RejectsCorruptDeferredBeginTransactions,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests roll back deferred begin reservations",
                RollsBackDeferredBeginReservations,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "CompleteRunAudioObserverTests reserve again after deferred child end",
                ReservesAgainAfterDeferredChildEnd,
                serial: true));
        }

        private static void ProjectsDeferredTailOwnerTransfer()
        {
            AssertProjectedDeferredTailOwnerTransfer(2,10);
            AssertProjectedDeferredTailOwnerTransfer(3,12);

            Type evidence=typeof(CompleteRunAudioObserver.DeferredBeginEvidence);
            AssertEx.Equal(true,evidence.GetProperty("CurrentOwnerToken")==null);
            AssertEx.Equal(true,evidence.GetProperty("CurrentOwnerParentToken")==null);
            AssertEx.Equal(true,evidence.GetProperty("CurrentOwnerKind")==null);
            AssertEx.Equal(true,evidence.GetProperty("CurrentOwnerDepth")==null);
        }

        private static void ProjectsPromotedDeferredTailOwnerTransfer()
        {
            var api=new FakeTraceApi{Events=DeferredPromotedOriginTransferFrame()};
            CompleteRunAudioObserver observer=CreateDeferred(api);
            CompleteRunAudioObserver.FrameCapture capture=
                observer.CaptureCanonicalFrame(()=>{});
            AssertEx.Equal(1,capture.DeferredBegins.Count);
            CompleteRunAudioObserver.DeferredBeginEvidence reservation=
                capture.DeferredBegins[0];
            AssertEx.Equal((ushort)2,reservation.BlockerToken);
            AssertEx.Equal((ushort)0,reservation.BlockerParentToken);
            AssertEx.Equal((byte)0,reservation.BlockerDepth);
            AssertEx.Equal((ushort)3,reservation.CurrentOwnerToken);
            AssertEx.Equal((ushort)0,reservation.CurrentOwnerParentToken);
            AssertEx.Equal((byte)2,reservation.CurrentOwnerKind);
            AssertEx.Equal((byte)0,reservation.CurrentOwnerDepth);
            AssertEx.Equal((ushort)1,capture.Services[1].ParentToken);
            AssertEx.Equal((ushort)0,capture.Services[1].CurrentParentToken);
            AssertEx.Equal((byte)1,capture.Services[1].Depth);
            AssertEx.Equal((byte)0,capture.Services[1].CurrentDepth);
            AssertCutoffCurrent(observer,3,2);
        }

        private static void AssertProjectedDeferredTailOwnerTransfer(byte successorKind,
            ushort consumeHook)
        {
            var api=new FakeTraceApi{Events=DeferredTransferFrame(successorKind)};
            CompleteRunAudioObserver observer=CreateDeferred(api);
            CompleteRunAudioObserver.FrameCapture transfer=
                observer.CaptureCanonicalFrame(()=>{});
            AssertEx.Equal(1,transfer.DeferredBegins.Count);
            AssertEx.Equal((ushort)2,transfer.DeferredBegins[0].BlockerToken);
            AssertEx.Equal((byte)6,transfer.DeferredBegins[0].BlockerKind);
            AssertEx.Equal(1L,transfer.DeferredBegins[0].FirstCoordinate);
            AssertEx.Equal(1L,transfer.DeferredBegins[0].LatestCoordinate);
            AssertEx.Equal(false,transfer.DeferredBegins[0].Consumed);
            AssertEx.Equal((ushort)3,
                transfer.DeferredBegins[0].CurrentOwnerToken);
            AssertEx.Equal(successorKind,
                transfer.DeferredBegins[0].CurrentOwnerKind);
            AssertDeferredCurrent(observer,3,0,successorKind,0);
            AssertCutoffCurrent(observer,3,successorKind);

            api.Events=DeferredTransferredConsumeFrame(successorKind,consumeHook,3);
            CompleteRunAudioObserver.FrameCapture consumed=
                observer.CaptureCanonicalFrame(()=>{});
            AssertEx.Equal(1,consumed.DeferredBegins.Count);
            AssertEx.Equal((ushort)2,consumed.DeferredBegins[0].BlockerToken);
            AssertEx.Equal((byte)6,consumed.DeferredBegins[0].BlockerKind);
            AssertEx.Equal(1L,consumed.DeferredBegins[0].FirstCoordinate);
            AssertEx.Equal(1L,consumed.DeferredBegins[0].LatestCoordinate);
            AssertEx.Equal(true,consumed.DeferredBegins[0].Consumed);
            AssertEx.Equal((ushort)4,consumed.DeferredBegins[0].ConsumedToken);
            AssertEx.Equal((ushort)3,
                consumed.DeferredBegins[0].CurrentOwnerToken);
            AssertEx.Equal(successorKind,
                consumed.DeferredBegins[0].CurrentOwnerKind);
            AssertEx.Equal(0,observer.PendingDeferredObservationCountForTesting);
            CompleteRunAudioObserver.CutoffFrontier cutoff=
                observer.CaptureCutoffFrontier();
            AssertEx.Equal(1,cutoff.ActiveServices.Count);
            AssertEx.Equal((ushort)3,cutoff.ActiveServices[0].Token);
            AssertEx.Equal(successorKind,cutoff.ActiveServices[0].Kind);
            AssertEx.Equal(1,cutoff.PendingServices.Count);
            AssertEx.Equal((ushort)4,cutoff.PendingServices[0].Token);
            AssertEx.Equal((ushort)3,cutoff.PendingServices[0].ParentToken);
        }

        private static void RejectsMalformedDeferredTailOwnerTransfers()
        {
            AssertDeferredTransferInvalid(events=>events[2].Ordinal=5,"ordinal");
            AssertDeferredTransferReplacementInvalid(events=>InsertEvent(events,3,
                Canonical(3,4,2,0,6,0,0,0x78,0,0x9f)),"tail");
            AssertDeferredTransferInvalid(events=>events[3].Subject=8,"tail");
            AssertDeferredTransferInvalid(events=>events[3].Pc=0x78,"tail");
            AssertDeferredTransferInvalid(events=>events[3].SourceCpu=2,"tail");
            AssertDeferredTransferInvalid(events=>events[2].ServiceToken=9,"ownership");
            AssertDeferredTransferInvalid(events=>events[3].ServiceToken=2,"tail");
            AssertDeferredTransferInvalid(events=>events[3].ParentToken=9,"tail");
            AssertDeferredTransferInvalid(events=>events[3].Depth=1,"tail");
            AssertDeferredTransferInvalid(events=>events[3].ServiceKindId=3,"tail");
            AssertDeferredTransferReplacementInvalid(events=>Truncate(events,3),"tail");
            AssertDeferredTransferReplacementInvalid(events=>RemoveEvent(events,2),"tail");

            GpgxAudioTraceEvent[] duplicate=DeferredTransferFrame(2);
            duplicate=Append(duplicate,
                Canonical(4,2,3,0,2,0,8,0x00C1,0),
                Canonical(5,1,4,0,3,0,8,0x00C1,0));
            AssertDeferredTransferReplacementInvalid(events=>duplicate,"deferred");

            var api=new FakeTraceApi{Events=DeferredTransferFrame(2)};
            CompleteRunAudioObserver observer=CreateDeferred(api);
            observer.CaptureCanonicalFrame(()=>{});
            api.Events=DeferredTransferredConsumeFrame(2,10,2);
            AssertEx.Throws<InvalidOperationException>(
                ()=>observer.CaptureCanonicalFrame(()=>{}),"deferred consume ownership");

            var wrongHookApi=new FakeTraceApi{Events=DeferredTransferFrame(2)};
            CompleteRunAudioObserver wrongHook=CreateDeferred(wrongHookApi);
            wrongHook.CaptureCanonicalFrame(()=>{});
            wrongHookApi.Events=DeferredTransferredConsumeFrame(2,5,3);
            AssertEx.Throws<InvalidOperationException>(
                ()=>wrongHook.CaptureCanonicalFrame(()=>{}),
                "deferred consume ownership");
        }

        private static void PreservesDeferredSharedPcSelectionOutcomes()
        {
            var observationApi=new FakeTraceApi
                {Events=UnreservedTailObservationFrame()};
            CompleteRunAudioObserver observation=CreateDeferred(observationApi);
            CompleteRunAudioObserver.FrameCapture observed=
                observation.CaptureCanonicalFrame(()=>{});
            AssertEx.Equal(5,observed.RawEvents.Count);
            AssertEx.Equal((byte)10,observed.RawEvents[4].Kind);
            AssertEx.Equal((ushort)9,observed.RawEvents[4].Subject);
            AssertEx.Equal((byte)3,observed.RawEvents[4].Value);
            AssertEx.Equal(0,observed.DeferredBegins.Count);

            var mismatchApi=new FakeTraceApi{Events=DeferredTransferFrame(2)};
            CompleteRunAudioObserver mismatch=CreateDeferred(mismatchApi);
            mismatch.CaptureCanonicalFrame(()=>{});
            CompleteRunAudioObserver.FrameCapture before=mismatch.LastCapture;
            mismatchApi.Events=new GpgxAudioTraceEvent[0];
            mismatchApi.EndStatus=-3;
            mismatchApi.FirstFault=new GpgxAudioObserverAdapter.FirstFault
                {Reason=4,SourceCpu=2,Pc=0x71B82,ActiveKind=2};
            AssertEx.Throws<InvalidOperationException>(
                ()=>mismatch.CaptureCanonicalFrame(()=>{}),
                "first_fault=4:2:71b82:2:0:0:0 native_tail=");
            AssertEx.Equal(true,object.ReferenceEquals(before,mismatch.LastCapture));
            AssertDeferredCurrentStorage(mismatch,3,0,2,0);
            AssertActiveCurrentStorage(mismatch,3,0,2,0);
        }

        private static void RollsBackDeferredTailOwnerTransfers()
        {
            AssertDeferredTransferRollback(false);
            AssertDeferredTransferRollback(true);
        }

        private static void AssertDeferredTransferRollback(bool consumerFailure)
        {
            var api=new FakeTraceApi{Events=DeferredFrame(1,false)};
            CompleteRunAudioObserver observer=CreateDeferred(api);
            observer.CaptureCanonicalFrame(()=>{});
            CompleteRunAudioObserver.FrameCapture before=observer.LastCapture;
            long coordinate=ObserverCoordinate(observer);
            AssertDeferredCurrent(observer,2,0,6,0);

            api.Events=DeferredTransferOnlyFrame(2);
            if(consumerFailure)
            {
                AssertEx.Throws<InvalidOperationException>(()=>observer.CaptureFrame(
                    ()=>{},(events,count)=>
                    {throw new InvalidOperationException("consumer rejected owner transfer");}),
                    "consumer rejected owner transfer");
            }
            else
            {
                api.Events=Append(api.Events,
                    Canonical(2,3,3,0,2,0,0,0x78,0,0x2a),
                    Canonical(3,3,3,0,2,0,2,0x79,0,0x30),
                    new GpgxAudioTraceEvent{Ordinal=4,Kind=4,ServiceToken=3,
                        ServiceKindId=2,SourceCpu=1,Pc=0x79,Value=0x9f,Reserved=1});
                AssertEx.Throws<InvalidOperationException>(
                    ()=>observer.CaptureCanonicalFrame(()=>{}),"reserved");
            }
            AssertEx.Equal(true,object.ReferenceEquals(before,observer.LastCapture));
            AssertEx.Equal((byte)0,observer.YmPort0Address);
            AssertEx.Equal((byte)0,observer.YmPort1Address);
            AssertEx.Equal(1,observer.ActiveServiceDepth);
            AssertEx.Equal(0,observer.PendingServiceCount);
            AssertEx.Equal(coordinate,ObserverCoordinate(observer));
            AssertDeferredCurrentStorage(observer,2,0,6,0);
            AssertActiveCurrentStorage(observer,2,0,6,0);
        }

        private static void CarriesDeferredCurrentOwnerThroughCutoffs()
        {
            var api=new FakeTraceApi{Events=DeferredTransferFrame(3)};
            CompleteRunAudioObserver observer=CreateDeferred(api);
            observer.CaptureCanonicalFrame(()=>{});
            CompleteRunAudioObserver.CutoffFrontier cutoff=
                observer.CaptureCutoffFrontier();
            AssertCutoffCurrent(cutoff,3,3);
            AssertEx.Equal((ushort)2,cutoff.PendingDeferredBegin.BlockerToken);
            AssertEx.Equal((byte)6,cutoff.PendingDeferredBegin.BlockerKind);

            CompleteRunAudioObserver.CutoffFrontier boundary=
                observer.CaptureBoundaryFrontierAndResetPublication();
            AssertCutoffCurrent(boundary,3,3);
            AssertEx.Equal((ushort)2,boundary.PendingDeferredBegin.BlockerToken);
            AssertEx.Equal(true,observer.LastCapture==null);
            AssertCutoffCurrent(observer.CaptureCutoffFrontier(),3,3);

            var corruptApi=new FakeTraceApi{Events=DeferredTransferFrame(2)};
            CompleteRunAudioObserver corrupt=CreateDeferred(corruptApi);
            corrupt.CaptureCanonicalFrame(()=>{});
            SetDeferredCurrentToken(corrupt,9);
            AssertEx.Throws<InvalidOperationException>(
                ()=>corrupt.CaptureCutoffFrontier(),"cutoff current owner");
        }

        private static void ReservesAgainAfterDeferredChildEnd()
        {
            var events=new List<GpgxAudioTraceEvent>(DeferredFrame(1,true));
            events.RemoveRange(events.Count-2,2);
            events.Add(DeferredMarker((uint)events.Count,2));
            var api=new FakeTraceApi{Events=events.ToArray()};
            CompleteRunAudioObserver observer=CreateDeferred(api);
            CompleteRunAudioObserver.FrameCapture capture=
                observer.CaptureCanonicalFrame(()=>{});
            AssertEx.Equal(2,capture.DeferredBegins.Count);
            AssertEx.Equal(true,capture.DeferredBegins[0].Consumed);
            AssertEx.Equal(false,capture.DeferredBegins[1].Consumed);
            AssertEx.Equal(1,capture.DeferredBegins[1].ObservationCount);
            AssertEx.Equal(1,observer.PendingDeferredObservationCountForTesting);
            AssertEx.Equal((byte)6,
                observer.CaptureCutoffFrontier().ActiveServices[0].Kind);
        }

        private static void ConsumesDeferredNestedBeginTransaction()
        {
            var api = new FakeTraceApi { Events = DeferredFrame(4,true) };
            CompleteRunAudioObserver observer = CreateDeferred(api);
            CompleteRunAudioObserver.FrameCapture capture =
                observer.CaptureCanonicalFrame(() => { });
            AssertEx.Equal(1, capture.DeferredBegins.Count);
            CompleteRunAudioObserver.DeferredBeginEvidence consumed =
                capture.DeferredBegins[0];
            AssertEx.Equal((ushort)2, consumed.BlockerToken);
            AssertEx.Equal((byte)6, consumed.BlockerKind);
            AssertEx.Equal((ushort)2,consumed.CurrentOwnerToken);
            AssertEx.Equal((ushort)0,consumed.CurrentOwnerParentToken);
            AssertEx.Equal((byte)6,consumed.CurrentOwnerKind);
            AssertEx.Equal((byte)0,consumed.CurrentOwnerDepth);
            AssertEx.Equal((byte)4, consumed.TargetKind);
            AssertEx.Equal((ushort)3, consumed.HookToken);
            AssertEx.Equal(4, consumed.ObservationCount);
            AssertEx.Equal(true, consumed.Consumed);
            AssertEx.Equal((ushort)3, consumed.ConsumedToken);
            AssertEx.Equal(7L, consumed.ConsumeCoordinate);
            AssertEx.Equal(3, capture.Services.Count);
            CompleteRunAudioObserver.DriverService child = null;
            for(int i=0;i<capture.Services.Count;i++)
                if(capture.Services[i].BeginPc==0x71B82)child=capture.Services[i];
            AssertEx.Equal(true,child!=null);
            AssertEx.Equal((byte)4, child.Kind);
            AssertEx.Equal((ushort)2, child.ParentToken);
            AssertEx.Equal((byte)1, child.Depth);
            AssertEx.Equal((ushort)5, child.BeginHookToken);
            AssertEx.Equal(1,child.OwnedChipEvents.Count);
            AssertEx.Equal((ushort)3,child.Token);
            AssertEx.Equal((uint)0x71BB6,child.OwnedChipEvents[0].Pc);
            CompleteRunAudioObserver.DriverService dpcm = observer
                .CaptureCutoffFrontier().ActiveServices[0];
            AssertEx.Equal((byte)2, dpcm.Kind);
            AssertEx.Equal((ushort)0, dpcm.ParentToken);
            AssertEx.Equal((byte)0, dpcm.Depth);
            AssertEx.Equal(0,observer.PendingDeferredObservationCountForTesting);
        }

        private static void RejectsCorruptDeferredBeginTransactions()
        {
            AssertDeferredInvalid(events => events[4].ServiceToken=99, "invalid");
            AssertDeferredInvalid(events => events[4].ServiceKindId=5, "invalid");
            AssertDeferredInvalid(events => events[4].Depth=1, "invalid");
            AssertDeferredInvalid(events => events[6].Subject=3, "invalid");
            AssertDeferredInvalid(events => events[6].Pc=0x71B84, "invalid");
            AssertDeferredInvalid(events => events[6].ParentToken=0, "invalid");
            AssertDeferredInvalid(events => events[6].Depth=0, "invalid");
            AssertDeferredInvalid(events => events[6].ServiceKindId=5, "invalid");
            AssertDeferredInvalid(events => events[6].ServiceToken=2, "invalid");
            AssertDeferredInvalid(events => RemoveDeferredEvent(events,6),"invalid");
            AssertDeferredInvalid(events => InsertDeferredEvent(events,6,events[6]),"invalid");
            AssertDeferredInvalid(events => InsertDeferredEvent(events,6,
                new GpgxAudioTraceEvent{Kind=10,ServiceToken=2,ServiceKindId=6,
                    Subject=6,Pc=0x71BB2,SourceCpu=2,Value=3}),"invalid");
            AssertDeferredInvalid(events => InsertDeferredEvent(events,6,
                new GpgxAudioTraceEvent{Kind=3,ServiceToken=2,ServiceKindId=6,
                    Pc=0x71BB6,SourceCpu=2,Value=0x2A}),"invalid");
            AssertDeferredInvalid(events => InsertDeferredEvent(events,6,
                new GpgxAudioTraceEvent{Kind=8,ServiceToken=9,ServiceKindId=1,
                    SourceCpu=3,Subject=1}),"reset");
        }

        private static void RollsBackDeferredBeginReservations()
        {
            var invalidApi = new FakeTraceApi { Events = DeferredFrame(3,false) };
            CompleteRunAudioObserver invalid = CreateDeferred(invalidApi);
            invalid.CaptureCanonicalFrame(() => { });
            AssertEx.Equal(3, invalid.PendingDeferredObservationCountForTesting);
            GpgxAudioTraceEvent[] later = DeferredConsumeFrame();
            later[0].ParentToken = 0;
            invalidApi.Events = later;
            AssertEx.Throws<InvalidOperationException>(
                () => invalid.CaptureCanonicalFrame(() => { }), "deferred consume");
            AssertEx.Equal(3, invalid.PendingDeferredObservationCountForTesting);

            var rejectedApi = new FakeTraceApi { Events = DeferredFrame(3,false) };
            CompleteRunAudioObserver rejected = CreateDeferred(rejectedApi);
            rejected.CaptureCanonicalFrame(() => { });
            rejectedApi.Events=DeferredConsumeFrame();
            AssertEx.Throws<InvalidOperationException>(() => rejected.CaptureFrame(
                () => { }, (events, count) =>
                { throw new InvalidOperationException("consumer rejected deferred consume"); }),
                "consumer rejected deferred consume");
            AssertEx.Equal(3, rejected.PendingDeferredObservationCountForTesting);
        }

        private static void AssertDeferredInvalid(
            Action<GpgxAudioTraceEvent[]> corrupt, string message)
        {
            GpgxAudioTraceEvent[] events = DeferredFrame(3,true);
            corrupt(events);
            var api = new FakeTraceApi { Events=events };
            AssertEx.Throws<InvalidOperationException>(
                () => CreateDeferred(api).CaptureCanonicalFrame(() => { }), message);
        }

        private static void InsertDeferredEvent(GpgxAudioTraceEvent[] events,
            int index,GpgxAudioTraceEvent value)
        {
            for(int i=events.Length-1;i>index;i--)events[i]=events[i-1];
            events[index]=value;
            for(int i=0;i<events.Length;i++)events[i].Ordinal=(uint)i;
        }

        private static void RemoveDeferredEvent(GpgxAudioTraceEvent[] events,int index)
        {
            for(int i=index;i<events.Length-1;i++)events[i]=events[i+1];
            events[events.Length-1]=new GpgxAudioTraceEvent
            {Ordinal=(uint)(events.Length-1),Kind=4,ServiceToken=4,ParentToken=3,
                ServiceKindId=2,Depth=1,SourceCpu=1,Pc=0x80,Value=0x20};
            for(int i=0;i<events.Length;i++)events[i].Ordinal=(uint)i;
        }

        private static GpgxAudioTraceEvent[] DeferredFrame(int markerCount,bool consume)
        {
            var values = new List<GpgxAudioTraceEvent>
            {
                Canonical(0,1,1,0,4,0,1,0x71B4C,0),
                Canonical(1,2,1,0,4,0,2,0x71C4C,0),
                Canonical(2,1,2,0,6,0,4,0x003A,0)
            };
            for(int i=0;i<markerCount;i++)
                values.Add(DeferredMarker((uint)values.Count,2));
            if (consume)
            {
                values.Add(Canonical((uint)values.Count,1,3,2,4,1,5,0x71B82,0));
                values.Add(Canonical((uint)values.Count,10,3,2,4,1,6,0x71BB2,0,3));
                values.Add(Canonical((uint)values.Count,3,3,2,4,1,0,0x71BB6,0,0x2A));
                values.Add(Canonical((uint)values.Count,2,3,2,4,1,2,0x71C4C,0));
                values.Add(Canonical((uint)values.Count,2,2,0,6,0,7,0x0077,0));
                values.Add(Canonical((uint)values.Count,1,4,0,2,0,7,0x0077,0));
            }
            GpgxAudioTraceEvent mBegin=values[0];mBegin.SourceCpu=2;values[0]=mBegin;
            GpgxAudioTraceEvent mEnd=values[1];mEnd.SourceCpu=2;values[1]=mEnd;
            if(consume)
            {
                int first=3+markerCount;
                for(int i=first;i<first+4;i++)
                {GpgxAudioTraceEvent m68k=values[i];m68k.SourceCpu=2;values[i]=m68k;}
            }
            return values.ToArray();
        }

        private static GpgxAudioTraceEvent[] DeferredConsumeFrame()
        {
            GpgxAudioTraceEvent[] values = new[]
            {
                Canonical(0,1,3,2,4,1,5,0x71B82,0),
                Canonical(1,10,3,2,4,1,6,0x71BB2,0,3),
                Canonical(2,2,3,2,4,1,2,0x71C4C,0)
            };
            for(int i=0;i<values.Length;i++)
            {GpgxAudioTraceEvent m68k=values[i];m68k.SourceCpu=2;values[i]=m68k;}
            return values;
        }

        private static GpgxAudioTraceEvent[] DeferredTransferFrame(byte successorKind)
        {
            ushort hook=successorKind==2?(ushort)7:(ushort)8;
            uint pc=successorKind==2?0x0077u:0x00C1u;
            GpgxAudioTraceEvent root=Canonical(0,1,2,0,6,0,4,0x003A,0);
            GpgxAudioTraceEvent marker=DeferredMarker(1,2);
            GpgxAudioTraceEvent end=Canonical(2,2,2,0,6,0,hook,pc,0);
            GpgxAudioTraceEvent begin=Canonical(3,1,3,0,successorKind,0,hook,pc,0);
            return new[]{root,marker,end,begin};
        }

        private static GpgxAudioTraceEvent[] DeferredPromotedOriginTransferFrame()
        {
            GpgxAudioTraceEvent marker=DeferredMarker(4,2);
            marker.ParentToken=0;marker.Depth=0;
            return new[]
            {
                Canonical(0,1,1,0,2,0,13,0x0020,0),
                Canonical(1,1,2,1,6,1,4,0x003A,0),
                Canonical(2,2,1,0,2,0,14,0x0022,0),
                Canonical(3,11,2,0,6,0,14,0x0022,0),
                marker,
                Canonical(5,2,2,0,6,0,7,0x0077,0),
                Canonical(6,1,3,0,2,0,7,0x0077,0)
            };
        }

        private static GpgxAudioTraceEvent[] DeferredTransferOnlyFrame(byte successorKind)
        {
            ushort hook=successorKind==2?(ushort)7:(ushort)8;
            uint pc=successorKind==2?0x0077u:0x00C1u;
            return new[]
            {
                Canonical(0,2,2,0,6,0,hook,pc,0),
                Canonical(1,1,3,0,successorKind,0,hook,pc,0)
            };
        }

        private static GpgxAudioTraceEvent[] DeferredTransferredConsumeFrame(
            byte successorKind,ushort consumeHook,ushort parentToken)
        {
            GpgxAudioTraceEvent begin=Canonical(0,1,4,parentToken,4,1,
                consumeHook,0x71B82,0);
            GpgxAudioTraceEvent end=Canonical(1,2,4,parentToken,4,1,
                2,0x71C4C,0);
            begin.SourceCpu=2;end.SourceCpu=2;
            return new[]{begin,end};
        }

        private static GpgxAudioTraceEvent[] UnreservedTailObservationFrame()
        {
            GpgxAudioTraceEvent marker=Canonical(4,10,3,0,2,0,9,
                0x71B82,0,3);
            marker.SourceCpu=2;
            return new[]
            {
                Canonical(0,1,2,0,6,0,4,0x003A,0),
                Canonical(1,2,2,0,6,0,7,0x0077,0),
                Canonical(2,1,3,0,2,0,7,0x0077,0),
                Canonical(3,4,3,0,2,0,0,0x78,0,0x9f),
                marker
            };
        }

        private static void AssertDeferredTransferInvalid(
            Action<GpgxAudioTraceEvent[]> corrupt,string message)
        {
            GpgxAudioTraceEvent[] events=DeferredTransferFrame(2);
            corrupt(events);
            AssertDeferredTransferEventsInvalid(events,message);
        }

        private static void AssertDeferredTransferReplacementInvalid(
            Func<GpgxAudioTraceEvent[],GpgxAudioTraceEvent[]> corrupt,string message)
        {
            AssertDeferredTransferEventsInvalid(
                corrupt(DeferredTransferFrame(2)),message);
        }

        private static void AssertDeferredTransferEventsInvalid(
            GpgxAudioTraceEvent[] events,string message)
        {
            var api=new FakeTraceApi{Events=events};
            AssertEx.Throws<InvalidOperationException>(
                ()=>CreateDeferred(api).CaptureCanonicalFrame(()=>{}),message);
        }

        private static GpgxAudioTraceEvent[] InsertEvent(
            GpgxAudioTraceEvent[] source,int index,GpgxAudioTraceEvent value)
        {
            var result=new GpgxAudioTraceEvent[source.Length+1];
            Array.Copy(source,0,result,0,index);result[index]=value;
            Array.Copy(source,index,result,index+1,source.Length-index);
            Reordinal(result);return result;
        }

        private static GpgxAudioTraceEvent[] RemoveEvent(
            GpgxAudioTraceEvent[] source,int index)
        {
            var result=new GpgxAudioTraceEvent[source.Length-1];
            Array.Copy(source,0,result,0,index);
            Array.Copy(source,index+1,result,index,source.Length-index-1);
            Reordinal(result);return result;
        }

        private static GpgxAudioTraceEvent[] Truncate(
            GpgxAudioTraceEvent[] source,int length)
        {
            var result=new GpgxAudioTraceEvent[length];
            Array.Copy(source,result,length);Reordinal(result);return result;
        }

        private static GpgxAudioTraceEvent[] Append(
            GpgxAudioTraceEvent[] source,params GpgxAudioTraceEvent[] tail)
        {
            var result=new GpgxAudioTraceEvent[source.Length+tail.Length];
            Array.Copy(source,result,source.Length);
            Array.Copy(tail,0,result,source.Length,tail.Length);
            Reordinal(result);return result;
        }

        private static void Reordinal(GpgxAudioTraceEvent[] events)
        {for(int i=0;i<events.Length;i++)events[i].Ordinal=(uint)i;}

        private static void AssertCutoffCurrent(CompleteRunAudioObserver observer,
            ushort token,byte kind)
        {AssertCutoffCurrent(observer.CaptureCutoffFrontier(),token,kind);}

        private static void AssertCutoffCurrent(
            CompleteRunAudioObserver.CutoffFrontier cutoff,ushort token,byte kind)
        {
            AssertEx.Equal(1,cutoff.ActiveServices.Count);
            CompleteRunAudioObserver.DriverService current=
                cutoff.ActiveServices[cutoff.ActiveServices.Count-1];
            AssertEx.Equal(token,current.Token);AssertEx.Equal(kind,current.Kind);
        }

        private static void AssertDeferredCurrent(CompleteRunAudioObserver observer,
            ushort token,ushort parent,byte kind,byte depth)
        {
            CompleteRunAudioObserver.DeferredBeginEvidence reservation=
                observer.CaptureCutoffFrontier().PendingDeferredBegin;
            AssertEx.Equal(true,reservation!=null);
            AssertEx.Equal(token,reservation.CurrentOwnerToken);
            AssertEx.Equal(parent,reservation.CurrentOwnerParentToken);
            AssertEx.Equal(kind,reservation.CurrentOwnerKind);
            AssertEx.Equal(depth,reservation.CurrentOwnerDepth);
        }

        private static void AssertDeferredCurrentStorage(
            CompleteRunAudioObserver observer,ushort token,ushort parent,
            byte kind,byte depth)
        {
            object reservation=typeof(CompleteRunAudioObserver).GetField(
                "pendingDeferredBegin",BindingFlags.Instance|BindingFlags.NonPublic)
                .GetValue(observer);
            AssertEx.Equal(true,reservation!=null);
            Type type=reservation.GetType();
            AssertEx.Equal(token,(ushort)type.GetField("CurrentOwnerToken",
                BindingFlags.Instance|BindingFlags.NonPublic).GetValue(reservation));
            AssertEx.Equal(parent,(ushort)type.GetField("CurrentOwnerParentToken",
                BindingFlags.Instance|BindingFlags.NonPublic).GetValue(reservation));
            AssertEx.Equal(kind,(byte)type.GetField("CurrentOwnerKind",
                BindingFlags.Instance|BindingFlags.NonPublic).GetValue(reservation));
            AssertEx.Equal(depth,(byte)type.GetField("CurrentOwnerDepth",
                BindingFlags.Instance|BindingFlags.NonPublic).GetValue(reservation));
        }

        private static void SetDeferredCurrentToken(
            CompleteRunAudioObserver observer,ushort token)
        {
            object reservation=typeof(CompleteRunAudioObserver).GetField(
                "pendingDeferredBegin",BindingFlags.Instance|BindingFlags.NonPublic)
                .GetValue(observer);
            reservation.GetType().GetField("CurrentOwnerToken",
                BindingFlags.Instance|BindingFlags.NonPublic).SetValue(reservation,token);
        }

        private static void AssertActiveCurrentStorage(
            CompleteRunAudioObserver observer,ushort token,ushort parent,
            byte kind,byte depth)
        {
            var active=(System.Collections.IList)typeof(CompleteRunAudioObserver)
                .GetField("activeServices",BindingFlags.Instance|BindingFlags.NonPublic)
                .GetValue(observer);
            AssertEx.Equal(1,active.Count);
            object current=active[0];Type type=current.GetType();
            AssertEx.Equal(token,(ushort)type.GetField("Token",
                BindingFlags.Instance|BindingFlags.NonPublic).GetValue(current));
            AssertEx.Equal(parent,(ushort)type.GetField("CurrentParentToken",
                BindingFlags.Instance|BindingFlags.NonPublic).GetValue(current));
            AssertEx.Equal(kind,(byte)type.GetField("Kind",
                BindingFlags.Instance|BindingFlags.NonPublic).GetValue(current));
            AssertEx.Equal(depth,(byte)type.GetField("CurrentDepth",
                BindingFlags.Instance|BindingFlags.NonPublic).GetValue(current));
        }

        private static long ObserverCoordinate(CompleteRunAudioObserver observer)
        {
            return (long)typeof(CompleteRunAudioObserver).GetField(
                "globalEventCoordinate",BindingFlags.Instance|BindingFlags.NonPublic)
                .GetValue(observer);
        }

        private static GpgxAudioTraceEvent DeferredMarker(uint ordinal, ushort blocker)
        {
            GpgxAudioTraceEvent value = Canonical(ordinal,10,blocker,0,6,0,3,0x71B4C,0,4);
            value.SourceCpu=2;
            return value;
        }

        private static void KeepsProjectionResultAllocationFree()
        {
            Type projection = typeof(CompleteRunAudioObserver).GetNestedType(
                "ProjectionResult", BindingFlags.NonPublic);
            AssertEx.Equal(true, projection != null && projection.IsValueType);
        }

        private static void ComposesConditionalKeepWithDirectParentPromotion()
        {
            var forgedRootKeepApi = new FakeTraceApi
            {
                Events = new[]
                {
                    Canonical(0,1,1,0,3,0,3,0x110,0),
                    new GpgxAudioTraceEvent {Ordinal=1,Kind=10,
                        ServiceToken=1,ParentToken=0,ServiceKindId=3,Depth=0,
                        Subject=6,Pc=0x120,SourceCpu=2,Value=0}
                }
            };
            AssertEx.Throws<InvalidOperationException>(() =>
                CreateConditionalCrossing(forgedRootKeepApi)
                    .CaptureCanonicalFrame(() => { }), "direct parent");

            var keepApi = new FakeTraceApi
            {
                Events = new[]
                {
                    Canonical(0,1,1,0,2,0,1,0x100,0),
                    Canonical(1,1,2,1,3,1,3,0x110,0),
                    new GpgxAudioTraceEvent {Ordinal=2,Kind=10,
                        ServiceToken=2,ParentToken=1,ServiceKindId=3,Depth=1,
                        Subject=6,Pc=0x120,SourceCpu=2,Value=0}
                }
            };
            CompleteRunAudioObserver keep = CreateConditionalCrossing(keepApi);
            AssertEx.Equal(true, keep.PromotionTransactionsEnabled);
            keep.CaptureCanonicalFrame(() => { });

            var promoteApi = new FakeTraceApi
            {
                Events = new[]
                {
                    Canonical(0,1,1,0,2,0,1,0x100,0),
                    Canonical(1,1,2,1,3,1,3,0x110,0),
                    new GpgxAudioTraceEvent {Ordinal=2,Kind=2,
                        ServiceToken=1,ParentToken=0,ServiceKindId=2,Depth=0,
                        Subject=6,Pc=0x120,SourceCpu=2},
                    new GpgxAudioTraceEvent {Ordinal=3,Kind=11,
                        ServiceToken=2,ParentToken=0,ServiceKindId=3,Depth=0,
                        Subject=6,Pc=0x120,SourceCpu=2}
                }
            };
            CompleteRunAudioObserver promoted = CreateConditionalCrossing(promoteApi);
            CompleteRunAudioObserver.FrameCapture capture =
                promoted.CaptureCanonicalFrame(() => { });
            AssertEx.Equal(1,capture.Services.Count);
            CompleteRunAudioObserver.DriverService child = promoted
                .CaptureBoundaryFrontierAndResetPublication().ActiveServices[0];
            AssertEx.Equal(1, child.AncestryTransitions.Count);
            AssertEx.Equal((ushort)0,
                child.AncestryTransitions[0].CurrentParentToken);
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

        private static void ValidatesAbiFourObservationA7PayloadsAtomically()
        {
            const ulong stack=0x89ABCDEFu;
            var acceptedApi=new FakeTraceApi{Events=MarkerFrame(4,stack,3)};
            CompleteRunAudioObserver accepted=CreateMarkerObserver(
                acceptedApi,4,7);
            CompleteRunAudioObserver.FrameCapture capture=
                accepted.CaptureCanonicalFrame(()=>{});
            AssertEx.Equal(2,capture.RawEvents.Count);
            AssertEx.Equal((byte)4,capture.RawEvents[1].PayloadLength);
            AssertEx.Equal(stack,capture.RawEvents[1].Payload);

            byte[] invalidLengths={0,1,3,5,8};
            for(int i=0;i<invalidLengths.Length;i++)
                AssertMarkerPayloadRejected(4,7,3,invalidLengths[i],stack);
            AssertMarkerPayloadRejected(4,7,3,4,0x100000000ul|stack);
            AssertMarkerPayloadRejected(3,7,3,4,stack);
            AssertMarkerPayloadRejected(4,5,0,4,stack);
            AssertMarkerPayloadRejected(4,5,1,4,stack);
            AssertMarkerPayloadRejected(4,6,2,4,stack);
            AssertMarkerPayloadRejected(4,11,4,4,stack);
        }

        private static void AssertMarkerPayloadRejected(ushort abi,byte action,
            byte value,byte payloadLength,ulong payload)
        {
            var api=new FakeTraceApi{Events=MarkerFrame(payloadLength,payload,value)};
            CompleteRunAudioObserver observer=CreateMarkerObserver(api,abi,action);
            int consumed=0;
            AssertEx.Throws<InvalidOperationException>(()=>observer.CaptureFrame(
                ()=>{},(events,count)=>consumed++),"marker fields");
            AssertEx.Equal(0,consumed);
            AssertEx.Equal(0,observer.ActiveServiceDepth);
            AssertEx.Equal(0,observer.PendingServiceCount);
            AssertEx.Equal(true,observer.LastCapture==null);
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

        private static GpgxAudioTraceEvent[] MarkerFrame(
            byte payloadLength,ulong payload,byte value)
        {
            return new[]
            {
                new GpgxAudioTraceEvent{Ordinal=0,Kind=1,ServiceToken=1,
                    ServiceKindId=4,SourceCpu=2,Pc=0x100,Subject=1},
                new GpgxAudioTraceEvent{Ordinal=1,Kind=10,ServiceToken=1,
                    ServiceKindId=4,SourceCpu=2,Pc=0x102,Subject=2,Value=value,
                    PayloadLength=payloadLength,Payload=payload}
            };
        }

        private static CompleteRunAudioObserver CreateMarkerObserver(
            FakeTraceApi api,ushort abi,byte markerAction)
        {
            var config=new GpgxAudioObserverAdapter.Config
            {
                Magic=0x31544147,AbiVersion=abi,StructSize=64,KindSize=16,
                HookSize=32,RangeSize=16,EventSize=32,MaxDepth=8,
                WatchMaskBytes=8192,HookCount=2,EventCapacity=65536,
                KindCount=2,ResetServiceKind=1
            };
            var kinds=new[]
            {
                new GpgxAudioObserverAdapter.ServiceKind{KindId=1},
                new GpgxAudioObserverAdapter.ServiceKind{KindId=4}
            };
            var hooks=new[]
            {
                new GpgxAudioObserverAdapter.ServiceHook{HookToken=1,Action=1,
                    Cpu=2,Pc=0x100,ServiceKindId=4},
                new GpgxAudioObserverAdapter.ServiceHook{HookToken=2,
                    Action=markerAction,Cpu=2,Pc=0x102,
                    ServiceKindId=markerAction==11?(byte)4:(byte)0,
                    ExpectedActiveKind=4}
            };
            return new CompleteRunAudioObserver(api,config,new byte[8192],
                kinds,hooks,new GpgxAudioObserverAdapter.SnapshotRange[0]);
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

        private static CompleteRunAudioObserver CreateDeferred(FakeTraceApi api)
        {
            var config = new GpgxAudioObserverAdapter.Config
            {
                Magic=0x31544147,AbiVersion=3,StructSize=64,KindSize=16,
                HookSize=32,RangeSize=16,EventSize=32,MaxDepth=8,
                WatchMaskBytes=8192,HookCount=14,RangeCount=1,
                EventCapacity=65536,KindCount=5,ResetServiceKind=1
            };
            var kinds = new[]
            {
                new GpgxAudioObserverAdapter.ServiceKind {KindId=1},
                new GpgxAudioObserverAdapter.ServiceKind {KindId=2,Flags=4},
                new GpgxAudioObserverAdapter.ServiceKind {KindId=3,Flags=4},
                new GpgxAudioObserverAdapter.ServiceKind {KindId=4,Flags=4},
                new GpgxAudioObserverAdapter.ServiceKind {KindId=6}
            };
            var hooks = new[]
            {
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=1,Action=1,Cpu=2,Pc=0x71B4C,ServiceKindId=4},
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=2,Action=2,Cpu=2,Pc=0x71C4C,ExpectedActiveKind=4},
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=3,Action=11,Cpu=2,Pc=0x71B4C,ServiceKindId=4,ExpectedActiveKind=6},
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=4,Action=1,Cpu=1,Pc=0x003A,ServiceKindId=6},
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=5,Action=12,Cpu=2,Pc=0x71B82,ServiceKindId=4,ExpectedActiveKind=6},
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=6,Action=7,Cpu=2,Pc=0x71BB2,ExpectedActiveKind=4},
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=7,Action=4,Cpu=1,Pc=0x0077,ServiceKindId=2,ExpectedActiveKind=6},
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=8,Action=4,Cpu=1,Pc=0x00C1,ServiceKindId=3,ExpectedActiveKind=6},
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=9,Action=7,Cpu=2,Pc=0x71B82,ExpectedActiveKind=2},
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=10,Action=12,Cpu=2,Pc=0x71B82,ServiceKindId=4,ExpectedActiveKind=2},
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=11,Action=7,Cpu=2,Pc=0x71B82,ExpectedActiveKind=3},
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=12,Action=12,Cpu=2,Pc=0x71B82,ServiceKindId=4,ExpectedActiveKind=3},
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=13,Action=1,Cpu=1,Pc=0x0020,ServiceKindId=2},
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=14,Action=8,Cpu=1,Pc=0x0022,ServiceKindId=2,ExpectedActiveKind=6}
            };
            var ranges = new[] { new GpgxAudioObserverAdapter.SnapshotRange
                {RangeId=7,Start=0,Length=0} };
            return new CompleteRunAudioObserver(api,config,new byte[8192],
                kinds,hooks,ranges);
        }

        private static CompleteRunAudioObserver CreateConditionalCrossing(
            FakeTraceApi api)
        {
            var config = new GpgxAudioObserverAdapter.Config
            {
                Magic=0x31544147,AbiVersion=3,StructSize=64,KindSize=16,
                HookSize=32,RangeSize=16,EventSize=32,MaxDepth=8,
                WatchMaskBytes=8192,HookCount=5,EventCapacity=65536,
                KindCount=2,ResetServiceKind=2
            };
            var kinds = new[]
            {
                new GpgxAudioObserverAdapter.ServiceKind {KindId=2,Flags=4},
                new GpgxAudioObserverAdapter.ServiceKind {KindId=3}
            };
            var hooks = new[]
            {
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=1,Action=1,Cpu=1,Pc=0x100,ServiceKindId=2},
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=3,Action=1,Cpu=1,Pc=0x110,ServiceKindId=3,ExpectedActiveKind=2},
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=4,Action=2,Cpu=1,Pc=0x114,ExpectedActiveKind=3},
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=2,Action=2,Cpu=1,Pc=0x122,ExpectedActiveKind=2},
                new GpgxAudioObserverAdapter.ServiceHook {HookToken=6,Action=9,Cpu=2,Pc=0x120,ServiceKindId=2,ExpectedActiveKind=3}
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
