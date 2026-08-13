using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Runtime.CompilerServices;

namespace OpenGGF.BizHawk.Headless
{
    public sealed class CompleteRunAudioObserver
    {
        private static readonly GpgxAudioTraceEvent[] EmptyEvents = new GpgxAudioTraceEvent[0];
        private readonly IGpgxAudioTraceApi api;
        private GpgxAudioObserverAdapter.Config config;
        private readonly byte[] mask;
        private readonly GpgxAudioObserverAdapter.ServiceKind[] kinds;
        private readonly GpgxAudioObserverAdapter.ServiceHook[] hooks;
        private readonly GpgxAudioObserverAdapter.SnapshotRange[] ranges;
        private GpgxAudioTraceEvent[] drainBuffer;
        private List<ushort> activeTokens = new List<ushort>();
        private readonly Guid runtimeInstanceId = Guid.NewGuid();
        private readonly ushort armHookToken;
        private readonly bool hasPromotionHooks;
        private readonly GpgxAudioObserverAdapter.ServiceKind[] kindById =
            new GpgxAudioObserverAdapter.ServiceKind[256];
        private readonly bool[] hasKind = new bool[256];
        private readonly GpgxAudioObserverAdapter.ServiceHook[] hookByToken =
            new GpgxAudioObserverAdapter.ServiceHook[ushort.MaxValue+1];
        private readonly bool[] hasHook = new bool[ushort.MaxValue+1];
        private readonly GpgxAudioObserverAdapter.SnapshotRange[] rangeById =
            new GpgxAudioObserverAdapter.SnapshotRange[ushort.MaxValue+1];
        private readonly bool[] hasRange = new bool[ushort.MaxValue+1];
        private readonly List<ServiceBuilder> activeServices = new List<ServiceBuilder>();
        private readonly List<ServiceBuilder> pendingCompleted = new List<ServiceBuilder>();
        private readonly List<ServiceBuilder> projectionActive = new List<ServiceBuilder>(8);
        private readonly List<ServiceBuilder> projectionComplete = new List<ServiceBuilder>(128);
        private readonly List<ServiceBuilder> projectionPending = new List<ServiceBuilder>(128);
        private readonly List<ResetRecord> projectionResets = new List<ResetRecord>(8);
        private readonly List<DeferredBeginReservation> projectionDeferredBegins =
            new List<DeferredBeginReservation>(8);
        private DeferredBeginReservation pendingDeferredBegin;
        private long globalEventCoordinate;
        private byte ymPort0Address;
        private byte ymPort1Address;
        private long armEpoch;
        private bool armed;
        private bool capturing;
        private bool faulted;

        public byte YmPort0Address { get { return ymPort0Address; } }
        public byte YmPort1Address { get { return ymPort1Address; } }
        public long ArmEpoch { get { return armEpoch; } }
        public bool IsArmed { get { return armed; } }
        public FrameCapture LastCapture { get; private set; }
        internal int ActiveServiceDepth { get { return activeTokens.Count; } }
        internal int PendingServiceCount { get { return pendingCompleted.Count; } }
        internal bool PromotionTransactionsEnabled { get { return hasPromotionHooks; } }
        internal int ProjectionScratchCapacity { get { return projectionActive.Capacity
            +projectionComplete.Capacity+projectionPending.Capacity+projectionResets.Capacity
            +projectionDeferredBegins.Capacity; } }
        internal ushort PendingRootTokenForTesting(ushort token)
        {for(int i=0;i<pendingCompleted.Count;i++)if(pendingCompleted[i].Token==token)
            return pendingCompleted[i].RootToken;throw new InvalidOperationException("Pending token not found.");}
        internal int PendingDeferredObservationCountForTesting
        {get{return pendingDeferredBegin==null?0:pendingDeferredBegin.ObservationCount;}}

        public sealed class DeferredBeginEvidence
        {
            internal DeferredBeginEvidence(DeferredBeginReservation value)
            {
                BlockerToken=value.BlockerToken;BlockerParentToken=value.BlockerParentToken;
                BlockerKind=value.BlockerKind;BlockerDepth=value.BlockerDepth;
                CurrentOwnerToken=value.CurrentOwnerToken;
                CurrentOwnerParentToken=value.CurrentOwnerParentToken;
                CurrentOwnerKind=value.CurrentOwnerKind;CurrentOwnerDepth=value.CurrentOwnerDepth;
                TargetKind=value.TargetKind;HookToken=value.HookToken;SourceCpu=value.SourceCpu;
                Pc=value.Pc;FirstCoordinate=value.FirstCoordinate;LatestCoordinate=value.LatestCoordinate;
                FirstOrdinal=value.FirstOrdinal;LatestOrdinal=value.LatestOrdinal;
                ObservationCount=value.ObservationCount;Consumed=value.Consumed;
                ConsumedToken=value.ConsumedToken;ConsumeCoordinate=value.ConsumeCoordinate;
            }
            public ushort BlockerToken{get;private set;} public ushort BlockerParentToken{get;private set;}
            public byte BlockerKind{get;private set;} public byte BlockerDepth{get;private set;}
            internal ushort CurrentOwnerToken{get;private set;}
            internal ushort CurrentOwnerParentToken{get;private set;}
            internal byte CurrentOwnerKind{get;private set;}
            internal byte CurrentOwnerDepth{get;private set;}
            public byte TargetKind{get;private set;} public ushort HookToken{get;private set;}
            public byte SourceCpu{get;private set;} public uint Pc{get;private set;}
            public long FirstCoordinate{get;private set;} public long LatestCoordinate{get;private set;}
            public uint FirstOrdinal{get;private set;} public uint LatestOrdinal{get;private set;}
            public int ObservationCount{get;private set;} public bool Consumed{get;private set;}
            public ushort ConsumedToken{get;private set;} public long ConsumeCoordinate{get;private set;}
        }

        internal sealed class DeferredBeginReservation
        {
            internal ushort BlockerToken,BlockerParentToken,HookToken,ConsumedToken;
            internal byte BlockerKind,BlockerDepth,TargetKind,SourceCpu;
            internal ushort CurrentOwnerToken,CurrentOwnerParentToken;
            internal byte CurrentOwnerKind,CurrentOwnerDepth;
            internal uint Pc,FirstOrdinal,LatestOrdinal;
            internal long FirstCoordinate,LatestCoordinate,ConsumeCoordinate;
            internal int ObservationCount;internal bool Consumed;
        }

        public sealed class CutoffFrontier
        {
            internal CutoffFrontier(List<ServiceBuilder> active,List<ServiceBuilder> pending,
                byte port0,byte port1,long epoch,bool armed)
                :this(active,pending,null,port0,port1,epoch,armed){}
            internal CutoffFrontier(List<ServiceBuilder> active,List<ServiceBuilder> pending,
                DeferredBeginReservation deferred,byte port0,byte port1,long epoch,bool armed)
            {
                ValidateDeferredCurrentOwner(active,deferred);
                ActiveServices=Materialize(active,false,false);PendingServices=Materialize(pending,true,true);
                PendingDeferredBegin=deferred==null?null:new DeferredBeginEvidence(deferred);
                YmPort0Address=port0;YmPort1Address=port1;ArmEpoch=epoch;IsArmed=armed;
            }
            public IReadOnlyList<DriverService> ActiveServices{get;private set;}
            public IReadOnlyList<DriverService> PendingServices{get;private set;}
            public DeferredBeginEvidence PendingDeferredBegin{get;private set;}
            public byte YmPort0Address{get;private set;} public byte YmPort1Address{get;private set;}
            public long ArmEpoch{get;private set;} public bool IsArmed{get;private set;}
            private static IReadOnlyList<DriverService> Materialize(List<ServiceBuilder> source,bool complete,bool sort)
            {
                var copy=new ServiceBuilder[source.Count];for(int i=0;i<copy.Length;i++)copy[i]=Clone(source[i]);
                if(sort)Array.Sort(copy,(a,b)=>a.BeginCoordinate.CompareTo(b.BeginCoordinate));
                var result=new DriverService[copy.Length];for(int i=0;i<result.Length;i++)result[i]=new DriverService(copy[i],complete);
                return Array.AsReadOnly(result);
            }
            private static void ValidateDeferredCurrentOwner(List<ServiceBuilder> active,
                DeferredBeginReservation deferred)
            {
                if(deferred==null)return;
                if(active.Count==0)throw Invalid("deferred cutoff without current owner");
                ServiceBuilder current=active[active.Count-1];
                if(current.Token!=deferred.CurrentOwnerToken
                    ||current.CurrentParentToken!=deferred.CurrentOwnerParentToken
                    ||current.Kind!=deferred.CurrentOwnerKind
                    ||current.CurrentDepth!=deferred.CurrentOwnerDepth)
                    throw Invalid("deferred cutoff current owner");
            }
        }

        public CutoffFrontier CaptureCutoffFrontier()
        {
            if(capturing)throw new InvalidOperationException("Cannot capture a cutoff frontier during capture.");
            if(faulted)throw new InvalidOperationException("The audio observer is faulted after a failed publication.");
            return new CutoffFrontier(activeServices,pendingCompleted,pendingDeferredBegin,
                ymPort0Address,ymPort1Address,armEpoch,armed);
        }

        internal void ValidateCutoffFrontierBounds(int activeLimit,
            int pendingLimit,int totalLimit,int transitionLimit,
            long totalTransitionLimit)
        {
            if(capturing)throw new InvalidOperationException(
                "Cannot validate a cutoff frontier during capture.");
            if(faulted)throw new InvalidOperationException(
                "The audio observer is faulted after a failed publication.");
            if(activeServices.Count>activeLimit)throw Invalid(
                "baseline active service bound");
            if(pendingCompleted.Count>pendingLimit)throw Invalid(
                "baseline pending service bound");
            if(activeServices.Count>totalLimit-pendingCompleted.Count)
                throw Invalid("baseline total service bound");
            long transitions=0;
            for(int group=0;group<2;group++)
            {
                List<ServiceBuilder> values=group==0?activeServices:pendingCompleted;
                for(int i=0;i<values.Count;i++)
                {
                    int count=values[i].AncestryRecords==null
                        ?0:values[i].AncestryRecords.Length;
                    if(count>transitionLimit)throw Invalid(
                        "baseline per-service transition bound");
                    if(count>totalTransitionLimit-transitions)throw Invalid(
                        "baseline total transition bound");
                    transitions+=count;
                }
            }
        }

        /// <summary>
        /// Captures the immutable state at a comparison-epoch boundary, then
        /// discards only ownership which belongs wholly to the preceding
        /// epoch. Open services remain live and retain their native identity,
        /// but begin a fresh bounded chip/snapshot inventory.
        /// </summary>
        public CutoffFrontier CaptureBoundaryFrontierAndResetPublication()
        {
            CutoffFrontier frontier=CaptureCutoffFrontier();
            RequireOk(api.BeginPublicationEpoch(),"begin publication epoch");
            globalEventCoordinate=0;
            pendingCompleted.Clear();
            for(int i=0;i<activeServices.Count;i++)ResetPublishedOwnership(activeServices[i]);
            LastCapture=null;
            return frontier;
        }

        private static void ResetPublishedOwnership(ServiceBuilder service)
        {
            service.EventCount=0;
            service.Chip0=default(WriteRecord);service.Chip1=default(WriteRecord);
            service.Chip2=default(WriteRecord);service.Chip3=default(WriteRecord);
            service.AdditionalChipRecords=null;service.ChipRecordCount=0;
            service.FirstSnapshot=default(SnapshotRecord);
            service.AdditionalSnapshots=null;service.SnapshotRecordCount=0;
        }

        internal void DiscardCutoffState()
        {
            if(capturing)throw new InvalidOperationException("Cannot discard during capture.");
            RequireOk(api.Disable(),"disable");
            activeTokens.Clear();activeServices.Clear();pendingCompleted.Clear();
            pendingDeferredBegin=null;
            armed=false;faulted=true;
        }

        public abstract class ChipWrite
        {
            protected ChipWrite(long coordinate, uint ordinal, ushort token)
            { Coordinate=coordinate; NativeOrdinal=ordinal; ServiceToken=token; }
            public long Coordinate { get; private set; }
            public uint NativeOrdinal { get; private set; }
            public ushort ServiceToken { get; private set; }
        }
        public sealed class OwnedChipEvent
        {
            internal OwnedChipEvent(WriteRecord r)
            {Coordinate=r.Coordinate;NativeOrdinal=r.Ordinal;EventKind=r.Kind;Subject=r.Subject;Value=r.Value;
                Pc=r.Pc;SourceCpu=r.SourceCpu;IsData=r.IsData;Port=r.Port;Register=r.Register;}
            public long Coordinate{get;private set;} public uint NativeOrdinal{get;private set;}
            public byte EventKind{get;private set;} public byte Subject{get;private set;} public byte Value{get;private set;}
            public uint Pc{get;private set;} public byte SourceCpu{get;private set;}
            public bool IsData{get;private set;} public byte Port{get;private set;} public byte Register{get;private set;}
        }
        public sealed class YmWrite : ChipWrite
        {
            internal YmWrite(long c,uint o,ushort t,byte p,byte r,byte v):base(c,o,t)
            { Port=p;Register=r;Value=v; }
            public byte Port { get; private set; }
            public byte Register { get; private set; }
            public byte Value { get; private set; }
        }
        public sealed class PsgWrite : ChipWrite
        {
            internal PsgWrite(long c,uint o,ushort t,byte v):base(c,o,t){Value=v;}
            public byte Value { get; private set; }
        }
        public sealed class SnapshotGroup
        {
            private readonly byte[] bytes;
            internal SnapshotGroup(ushort id,byte source,uint pc,byte[] bytes)
            {RangeId=id;SourceCpu=source;Pc=pc;this.bytes=bytes;}
            public ushort RangeId { get; private set; }
            public byte SourceCpu { get; private set; }
            public uint Pc { get; private set; }
            public byte[] Bytes { get { return (byte[])bytes.Clone(); } }
        }
        public sealed class AncestryTransition
        {
            internal AncestryTransition(AncestryRecord r)
            {Coordinate=r.Coordinate;NativeOrdinal=r.NativeOrdinal;PreviousParentToken=r.PreviousParentToken;
                PreviousDepth=r.PreviousDepth;CurrentParentToken=r.CurrentParentToken;CurrentDepth=r.CurrentDepth;
                HookToken=r.HookToken;SourceCpu=r.SourceCpu;Pc=r.Pc;}
            public long Coordinate{get;private set;} public uint NativeOrdinal{get;private set;}
            public ushort PreviousParentToken{get;private set;} public byte PreviousDepth{get;private set;}
            public ushort CurrentParentToken{get;private set;} public byte CurrentDepth{get;private set;}
            public ushort HookToken{get;private set;} public byte SourceCpu{get;private set;}
            public uint Pc{get;private set;}
        }
        public sealed class DriverService
        {
            private readonly WriteRecord chip0,chip1,chip2,chip3;
            private readonly WriteRecord[] additionalChipRecords;
            private readonly int chipRecordCount;
            private ChipWrite[] chipWrites;
            private ReadOnlyCollection<ChipWrite> chipWritesView;
            private uint[] rawChipOrdinals;
            private ReadOnlyCollection<uint> rawChipOrdinalsView;
            private OwnedChipEvent[] ownedChipEvents;
            private ReadOnlyCollection<OwnedChipEvent> ownedChipEventsView;
            private readonly SnapshotRecord firstSnapshot;
            private readonly SnapshotRecord[] additionalSnapshots;
            private readonly int snapshotRecordCount;
            private SnapshotGroup[] snapshots;
            private ReadOnlyCollection<SnapshotGroup> snapshotsView;
            private readonly AncestryRecord[] ancestryRecords;
            private AncestryTransition[] ancestryTransitions;
            private ReadOnlyCollection<AncestryTransition> ancestryTransitionsView;
            internal DriverService(ServiceBuilder b,bool complete=true)
            {
                Token=b.Token;ParentToken=b.ParentToken;Kind=b.Kind;Depth=b.Depth;
                CurrentParentToken=b.CurrentParentToken;CurrentDepth=b.CurrentDepth;
                BeginCoordinate=b.BeginCoordinate;EndCoordinate=b.EndCoordinate;
                BeginPc=b.BeginPc;EndPc=b.EndPc;Cancelled=b.Cancelled;
                BeginHookToken=b.BeginHookToken;BeginSourceCpu=b.BeginSourceCpu;
                EndHookToken=b.EndHookToken;
                IsComplete=complete;
                chip0=b.Chip0;chip1=b.Chip1;chip2=b.Chip2;chip3=b.Chip3;
                additionalChipRecords=b.AdditionalChipRecords;chipRecordCount=b.ChipRecordCount;
                firstSnapshot=b.FirstSnapshot;additionalSnapshots=b.AdditionalSnapshots;snapshotRecordCount=b.SnapshotRecordCount;
                ancestryRecords=b.AncestryRecords==null?new AncestryRecord[0]:(AncestryRecord[])b.AncestryRecords.Clone();
            }
            public ushort Token{get;private set;} public ushort ParentToken{get;private set;}
            public byte Kind{get;private set;} public byte Depth{get;private set;}
            public ushort CurrentParentToken{get;private set;} public byte CurrentDepth{get;private set;}
            public long BeginCoordinate{get;private set;} public long EndCoordinate{get;private set;}
            public uint BeginPc{get;private set;} public uint EndPc{get;private set;}
            public ushort BeginHookToken{get;private set;} public byte BeginSourceCpu{get;private set;}
            public bool Cancelled{get;private set;}
            public bool IsComplete{get;private set;}
            public ushort EndHookToken{get;private set;}
            public IReadOnlyList<ChipWrite> ChipWrites
            {
                get
                {
                    if(chipWrites==null)
                    {
                        int count=0;for(int i=0;i<chipRecordCount;i++)if(ChipAt(i).IsData)count++;
                        chipWrites=new ChipWrite[count];int at=0;
                        for(int i=0;i<chipRecordCount;i++){WriteRecord r=ChipAt(i);if(r.IsData)chipWrites[at++]=r.ToWrite();}
                        chipWritesView=Array.AsReadOnly(chipWrites);
                    }
                    return chipWritesView;
                }
            }
            public IReadOnlyList<SnapshotGroup> Snapshots
            {get{if(snapshots==null){snapshots=new SnapshotGroup[snapshotRecordCount];for(int i=0;i<snapshots.Length;i++)
                {SnapshotRecord r=i==0?firstSnapshot:additionalSnapshots[i-1];snapshots[i]=new SnapshotGroup(r.RangeId,r.SourceCpu,
                    r.Pc,r.Materialize());}snapshotsView=Array.AsReadOnly(snapshots);}return snapshotsView;}}
            public IReadOnlyList<uint> RawChipOrdinals
            {get{if(rawChipOrdinals==null){rawChipOrdinals=new uint[chipRecordCount];
                for(int i=0;i<chipRecordCount;i++)rawChipOrdinals[i]=ChipAt(i).Ordinal;
                rawChipOrdinalsView=Array.AsReadOnly(rawChipOrdinals);}return rawChipOrdinalsView;}}
            public IReadOnlyList<OwnedChipEvent> OwnedChipEvents
            {get{if(ownedChipEvents==null){ownedChipEvents=new OwnedChipEvent[chipRecordCount];
                for(int i=0;i<ownedChipEvents.Length;i++)ownedChipEvents[i]=new OwnedChipEvent(ChipAt(i));
                ownedChipEventsView=Array.AsReadOnly(ownedChipEvents);}return ownedChipEventsView;}}
            public IReadOnlyList<AncestryTransition> AncestryTransitions
            {get{if(ancestryTransitions==null){ancestryTransitions=new AncestryTransition[ancestryRecords.Length];
                for(int i=0;i<ancestryTransitions.Length;i++)ancestryTransitions[i]=new AncestryTransition(ancestryRecords[i]);
                ancestryTransitionsView=Array.AsReadOnly(ancestryTransitions);}return ancestryTransitionsView;}}
            private WriteRecord ChipAt(int index)
            {if(index==0)return chip0;if(index==1)return chip1;if(index==2)return chip2;if(index==3)return chip3;
                return additionalChipRecords[index-4];}
        }
        public sealed class ResetLifecycle
        {
            internal ResetLifecycle(ushort token,bool power,long begin,long end,DriverService service)
            {Token=token;Power=power;BeginCoordinate=begin;EndCoordinate=end;Service=service;}
            public ushort Token{get;private set;} public bool Power{get;private set;}
            public long BeginCoordinate{get;private set;} public long EndCoordinate{get;private set;}
            public DriverService Service{get;private set;}
        }
        public sealed class FrameCapture
        {
            internal readonly ServiceBuilder[] completed;
            private readonly ResetRecord[] resetRecords;
            private readonly long frameBase;
            private DriverService[] services;
            private ReadOnlyCollection<DriverService> servicesView;
            private ResetLifecycle[] resets;
            private ReadOnlyCollection<ResetLifecycle> resetsView;
            private long[] flattened;
            private ReadOnlyCollection<long> flattenedView;
            private readonly DeferredBeginEvidence[] deferredBegins;
            private readonly ReadOnlyCollection<DeferredBeginEvidence> deferredBeginsView;
            internal FrameCapture(GpgxAudioTraceEvent[] raw,List<ServiceBuilder> completed,
                List<ResetRecord> resets,long frameBase)
                :this(raw,completed,resets,frameBase,(DeferredBeginReservation)null){}
            internal FrameCapture(GpgxAudioTraceEvent[] raw,List<ServiceBuilder> completed,
                List<ResetRecord> resets,long frameBase,DeferredBeginReservation deferred)
            {RawEvents=Array.AsReadOnly(raw);this.completed=completed.ToArray();resetRecords=resets.ToArray();this.frameBase=frameBase;
                deferredBegins=deferred==null?new DeferredBeginEvidence[0]:new[]{new DeferredBeginEvidence(deferred)};
                deferredBeginsView=Array.AsReadOnly(deferredBegins);}
            internal FrameCapture(GpgxAudioTraceEvent[] raw,List<ServiceBuilder> completed,
                List<ResetRecord> resets,long frameBase,
                List<DeferredBeginReservation> deferred)
            {
                RawEvents=Array.AsReadOnly(raw);this.completed=completed.ToArray();
                resetRecords=resets.ToArray();this.frameBase=frameBase;
                deferredBegins=new DeferredBeginEvidence[deferred.Count];
                for(int i=0;i<deferredBegins.Length;i++)
                    deferredBegins[i]=new DeferredBeginEvidence(deferred[i]);
                deferredBeginsView=Array.AsReadOnly(deferredBegins);
            }
            public IReadOnlyList<GpgxAudioTraceEvent> RawEvents{get;private set;}
            public bool RawEventsRetained{get{return RawEvents.Count!=0;}}
            public IReadOnlyList<DeferredBeginEvidence> DeferredBegins{get{return deferredBeginsView;}}
            public IReadOnlyList<DriverService> Services
            {get{MaterializeServices();return servicesView;}}
            public IReadOnlyList<ResetLifecycle> Resets
            {get{MaterializeServices();if(resets==null){resets=new ResetLifecycle[resetRecords.Length];
                for(int i=0;i<resets.Length;i++)resets[i]=new ResetLifecycle(resetRecords[i].Builder.Token,
                    resetRecords[i].Power,resetRecords[i].Builder.BeginCoordinate,resetRecords[i].Builder.EndCoordinate,
                    services[resetRecords[i].ServiceIndex]);resetsView=Array.AsReadOnly(resets);}return resetsView;}}
            public IReadOnlyList<long> FlattenedChipOrdinals
            {get{if(flattened==null){int count=0;for(int i=0;i<RawEvents.Count;i++)if(RawEvents[i].Kind==3||RawEvents[i].Kind==4)count++;
                flattened=new long[count];int at=0;for(int i=0;i<RawEvents.Count;i++)if(RawEvents[i].Kind==3||RawEvents[i].Kind==4)
                    flattened[at++]=frameBase+i;flattenedView=Array.AsReadOnly(flattened);}return flattenedView;}}
            private void MaterializeServices()
            {if(services!=null)return;services=new DriverService[completed.Length];
                for(int i=0;i<services.Length;i++)services[i]=new DriverService(completed[i]);
                servicesView=Array.AsReadOnly(services);}
        }
        internal struct ResetRecord{internal ServiceBuilder Builder;internal bool Power;internal int ServiceIndex;}
        internal sealed class ServiceBuilder
        {
            internal ushort Token,ParentToken; internal byte Kind,Depth; internal long BeginCoordinate,EndCoordinate;
            internal ushort CurrentParentToken; internal byte CurrentDepth;
            internal ushort RootToken;
            internal uint BeginPc,EndPc; internal ushort BeginHookToken;internal byte BeginSourceCpu;
            internal bool Cancelled,IsReset,ResetPower;
            internal int EventCount;
            internal WriteRecord Chip0,Chip1,Chip2,Chip3;internal WriteRecord[] AdditionalChipRecords;
            internal int ChipRecordCount;
            internal SnapshotRecord FirstSnapshot;internal SnapshotRecord[] AdditionalSnapshots;
            internal int SnapshotRecordCount;
            internal ushort ActiveRange; internal byte[] ActiveBytes;internal int ActiveByteCount,ActiveByteLength;
            internal ulong ActivePayload;
            internal byte ActiveSnapshotSource; internal uint ActiveSnapshotPc;
            internal ushort EndHookToken;
            internal AncestryRecord[] AncestryRecords;
            internal void AddAncestry(AncestryRecord record)
            {int count=AncestryRecords==null?0:AncestryRecords.Length;if(count>=7)throw Invalid("ancestry transition bound");
                Array.Resize(ref AncestryRecords,count+1);AncestryRecords[count]=record;}
            internal void AddChip(WriteRecord record)
            {if(ChipRecordCount==0)Chip0=record;else if(ChipRecordCount==1)Chip1=record;
                else if(ChipRecordCount==2)Chip2=record;else if(ChipRecordCount==3)Chip3=record;else
                {int at=ChipRecordCount-4;if(AdditionalChipRecords==null)AdditionalChipRecords=new WriteRecord[8];
                    else if(at==AdditionalChipRecords.Length)Array.Resize(ref AdditionalChipRecords,at*2);
                    AdditionalChipRecords[at]=record;}ChipRecordCount++;}
            internal void AddSnapshot(SnapshotRecord record)
            {if(SnapshotRecordCount==0)FirstSnapshot=record;else{int at=SnapshotRecordCount-1;
                if(AdditionalSnapshots==null)AdditionalSnapshots=new SnapshotRecord[2];else if(at==AdditionalSnapshots.Length)
                    Array.Resize(ref AdditionalSnapshots,at*2);AdditionalSnapshots[at]=record;}SnapshotRecordCount++;}
            internal SnapshotRecord SnapshotAt(int index)
            {return index==0?FirstSnapshot:AdditionalSnapshots[index-1];}
        }
        internal struct SnapshotRecord
        {
            internal ushort RangeId;internal byte SourceCpu;internal uint Pc;internal byte[] Bytes;
            internal ulong Payload;internal int Length;
            internal byte[] Materialize(){if(Bytes!=null)return Bytes;var result=new byte[Length];
                for(int i=0;i<Length;i++)result[i]=(byte)(Payload>>(8*i));return result;}
        }
        internal struct WriteRecord
        {
            internal long Coordinate;internal uint Ordinal,Pc;internal ushort Token;
            internal byte Kind,Subject,Port,Register,Value,SourceCpu;
            internal bool IsData{get{return Kind==4||(Kind==3&&(Subject==1||Subject==3));}}
            internal ChipWrite ToWrite()
            {
                if(Kind==3)return new YmWrite(Coordinate,Ordinal,Token,Port,Register,Value);
                return new PsgWrite(Coordinate,Ordinal,Token,Value);
            }
        }
        internal struct AncestryRecord
        {
            internal long Coordinate;internal uint NativeOrdinal;internal ushort PreviousParentToken,CurrentParentToken;
            internal byte PreviousDepth,CurrentDepth;internal ushort HookToken;internal byte SourceCpu;internal uint Pc;
        }

        public sealed class Checkpoint
        {
            internal readonly Guid RuntimeInstanceId;
            internal readonly long ArmEpoch;
            internal readonly bool Armed;
            internal readonly byte YmPort0Address;
            internal readonly byte YmPort1Address;

            internal Checkpoint(Guid runtimeInstanceId, long armEpoch, bool armed,
                byte ymPort0Address, byte ymPort1Address)
            {
                RuntimeInstanceId = runtimeInstanceId;
                ArmEpoch = armEpoch;
                Armed = armed;
                YmPort0Address = ymPort0Address;
                YmPort1Address = ymPort1Address;
            }
        }

        public CompleteRunAudioObserver(IGpgxAudioTraceApi api,
            GpgxAudioObserverAdapter.Config config, byte[] mask,
            GpgxAudioObserverAdapter.ServiceKind[] kinds,
            GpgxAudioObserverAdapter.ServiceHook[] hooks,
            GpgxAudioObserverAdapter.SnapshotRange[] ranges)
        {
            if (api == null) throw new ArgumentNullException("api");
            this.api = api;
            this.config = config;
            this.mask = (byte[])mask.Clone();
            this.kinds = (GpgxAudioObserverAdapter.ServiceKind[])kinds.Clone();
            this.hooks = (GpgxAudioObserverAdapter.ServiceHook[])hooks.Clone();
            this.ranges = (GpgxAudioObserverAdapter.SnapshotRange[])ranges.Clone();
            for(int i=0;i<this.kinds.Length;i++){kindById[this.kinds[i].KindId]=this.kinds[i];hasKind[this.kinds[i].KindId]=true;}
            for(int i=0;i<this.ranges.Length;i++){rangeById[this.ranges[i].RangeId]=this.ranges[i];hasRange[this.ranges[i].RangeId]=true;}
            for(int i=0;i<this.hooks.Length;i++){hookByToken[this.hooks[i].HookToken]=this.hooks[i];hasHook[this.hooks[i].HookToken]=true;}
            ushort foundArmHook = 0;
            bool foundPromotionHook = false;
            for (int i = 0; i < this.hooks.Length; i++)
            {
                if (this.hooks[i].Action == 8 || this.hooks[i].Action == 9)
                    foundPromotionHook = true;
                if ((this.hooks[i].Flags & 1) == 0) continue;
                if (foundArmHook != 0)
                    throw new ArgumentException("Only one Z80 proof-arm completion is permitted.", "hooks");
                foundArmHook = this.hooks[i].HookToken;
            }
            armHookToken = foundArmHook;
            hasPromotionHooks = foundPromotionHook;
            armed = armHookToken == 0;
            RequireOk(api.Configure(ref this.config, this.mask, this.kinds, this.hooks, this.ranges), "configure");
        }

        public GpgxAudioTraceEvent[] CaptureFrame(Action frameAdvance)
        {
            FrameCapture capture=CaptureCanonicalFrame(frameAdvance);
            if(capture.RawEvents.Count==0)return EmptyEvents;
            var result=new GpgxAudioTraceEvent[capture.RawEvents.Count];
            for(int i=0;i<result.Length;i++)result[i]=capture.RawEvents[i];
            return result;
        }

        public FrameCapture CaptureCanonicalFrame(Action frameAdvance)
        {
            CaptureFrameCore(frameAdvance, (buffer, count) => { },true);
            return LastCapture;
        }

        public void CaptureFrame(Action frameAdvance,
            Action<GpgxAudioTraceEvent[], int> consume)
        {CaptureFrameCore(frameAdvance,consume,false);}

        private void CaptureFrameCore(Action frameAdvance,
            Action<GpgxAudioTraceEvent[], int> consume,bool retainRaw)
        {
            if (frameAdvance == null) throw new ArgumentNullException("frameAdvance");
            if (consume == null) throw new ArgumentNullException("consume");
            if (capturing) throw new InvalidOperationException("An audio observer frame is already active.");
            if (faulted) throw new InvalidOperationException("The audio observer is faulted after a failed publication.");
            capturing = true;
            RequireOk(api.BeginFrame(), "begin frame");
            bool drainedFrame = false;
            try
            {
                frameAdvance();
                int endStatus = api.EndFrame();
                if (endStatus != 0)
                {
                    GpgxAudioObserverAdapter.FirstFault firstFault;
                    int faultStatus = api.GetFirstFault(out firstFault);
                    string nativeFault = faultStatus == 0
                        ? firstFault.Reason + ":" + firstFault.SourceCpu + ":"
                            + firstFault.Pc.ToString("x") + ":" + firstFault.ActiveKind
                            + ":" + firstFault.ActiveDepth + ":"
                            + firstFault.ContinuationCount + ":" + firstFault.ContinuationLimit
                        : "unavailable:" + faultStatus;
                    string nativeTail = "";
                    string nativeM68k = "";
                    string nativeLifecycle = "";
                    uint diagnosticCount, diagnosticOverflow;
                    if (api.EventCount(out diagnosticCount, out diagnosticOverflow) == 0
                        && diagnosticOverflow == 0 && diagnosticCount <= api.Capacity)
                    {
                        if (drainBuffer == null || drainBuffer.Length < diagnosticCount)
                            drainBuffer = new GpgxAudioTraceEvent[checked((int)diagnosticCount)];
                        uint diagnosticCopied;
                        if (api.Drain(diagnosticCount == 0 ? null : drainBuffer,
                            diagnosticCount, out diagnosticCopied) == 0
                            && diagnosticCopied == diagnosticCount)
                        {
                            drainedFrame = true;
                            int first = Math.Max(0, checked((int)diagnosticCopied) - 16);
                            for (int i = first; i < diagnosticCopied; i++)
                            {
                                ref GpgxAudioTraceEvent value = ref drainBuffer[i];
                                if (nativeTail.Length != 0) nativeTail += "|";
                                nativeTail += value.Ordinal + ":" + value.Kind + ":"
                                    + value.ServiceToken + ":" + value.ParentToken + ":"
                                    + value.ServiceKindId + ":" + value.Depth + ":"
                                    + value.Pc.ToString("x") + ":" + value.Subject
                                    + ":" + value.Value + ":" + value.Flags;
                            }
                            int m68kFirst = 0;
                            int lifecycleCount = 0;
                            for (int i = 0; i < diagnosticCopied; i++)
                            {
                                ref GpgxAudioTraceEvent value = ref drainBuffer[i];
                                if ((value.Kind == 1 || value.Kind == 2)
                                    && lifecycleCount++ < 32)
                                {
                                    if (nativeLifecycle.Length != 0)
                                        nativeLifecycle += "|";
                                    nativeLifecycle += value.Ordinal + ":" + value.Kind
                                        + ":" + value.ServiceToken + ":"
                                        + value.ParentToken + ":" + value.ServiceKindId
                                        + ":" + value.Depth + ":" + value.Pc.ToString("x");
                                }
                                if (value.SourceCpu == 2
                                    && (value.Kind == 1 || value.Kind == 2
                                        || value.Kind == 10))
                                {
                                    if (m68kFirst++ >= 16) continue;
                                    if (nativeM68k.Length != 0) nativeM68k += "|";
                                    nativeM68k += value.Ordinal + ":" + value.Kind + ":"
                                        + value.ServiceToken + ":" + value.ParentToken + ":"
                                        + value.ServiceKindId + ":" + value.Depth + ":"
                                        + value.Pc.ToString("x") + ":" + value.Subject
                                        + ":" + value.Value;
                                }
                            }
                        }
                    }
                    throw new InvalidOperationException("GPGX audio observer end frame failed with status "
                        + endStatus + ". first_fault=" + nativeFault + " native_tail=" + nativeTail
                        + " native_m68k=" + nativeM68k
                        + " native_lifecycle=" + nativeLifecycle);
                }
                uint count, overflow;
                RequireOk(api.EventCount(out count, out overflow), "event count");
                if (overflow != 0 || count > api.Capacity)
                    throw new InvalidOperationException("The native audio observer overflowed its bounded frame buffer.");
                if (count == 0)
                {
                    uint drained;
                    RequireOk(api.Drain(null, 0, out drained), "empty drain");
                    drainedFrame = true;
                    if (drained != 0) throw new InvalidOperationException("An empty drain returned events.");
                    var emptyCapture = new FrameCapture(EmptyEvents,new List<ServiceBuilder>(),
                        new List<ResetRecord>(),globalEventCoordinate,pendingDeferredBegin);
                    consume(EmptyEvents, 0);
                    LastCapture=retainRaw?emptyCapture:null;
                    return;
                }
                if (drainBuffer == null || drainBuffer.Length < count)
                    drainBuffer = new GpgxAudioTraceEvent[checked((int)count)];
                uint copied;
                RequireOk(api.Drain(drainBuffer, count, out copied), "drain");
                drainedFrame = true;
                if (copied != count) throw new InvalidOperationException("The native audio observer returned a short drain.");
                ProjectionResult projected = Project(drainBuffer,checked((int)count),retainRaw);
                consume(drainBuffer, checked((int)count));
                CommitProjection(projected);
            }
            catch
            {
                if (!drainedFrame) api.AbortFrame();
                else faulted = true;
                throw;
            }
            finally { capturing = false; }
        }

        private struct ProjectionResult
        {
            internal FrameCapture Capture; internal List<ServiceBuilder> Active;
            internal List<ServiceBuilder> Completed;
            internal List<ServiceBuilder> Pending;
            internal byte Port0,Port1; internal long Epoch; internal bool Armed;
            internal DeferredBeginReservation Deferred;
            internal int EventCount;
        }

        private struct DeferredOwnerTransfer
        {
            internal bool Pending;
            internal int BeginIndex;
            internal ushort Token,ParentToken;
            internal byte Kind,Depth;
        }

        private ProjectionResult Project(GpgxAudioTraceEvent[] events,int count,bool retainRaw)
        {
            var active=projectionActive;active.Clear();
            for(int i=0;i<activeServices.Count;i++)active.Add(Clone(activeServices[i]));
            var complete=projectionComplete;complete.Clear();
            var pending=projectionPending;pending.Clear();pending.AddRange(pendingCompleted);
            var resets=projectionResets;resets.Clear();
            var deferredBegins=projectionDeferredBegins;deferredBegins.Clear();
            ServiceBuilder reset=null;int rawChipCount=0,ownedChipCount=0;
            byte port0=ymPort0Address,port1=ymPort1Address; long epoch=armEpoch; bool nowArmed=armed;
            bool promotionHooks=hasPromotionHooks;
            DeferredBeginReservation deferred=Clone(pendingDeferredBegin);
            DeferredOwnerTransfer deferredTransfer=default(DeferredOwnerTransfer);
            for(int i=0;i<count;i++)
            {
                ref GpgxAudioTraceEvent e=ref events[i]; long coordinate=globalEventCoordinate+i;
                if(e.Ordinal!=(uint)i)throw Invalid("noncontiguous event ordinal");
                ValidateCommon(ref e);
                switch(e.Kind)
                {
                case 1:
                {
                    if(reset!=null)throw Invalid("service begin during reset cancellation");
                    GpgxAudioObserverAdapter.ServiceHook hook=RequireHook(e.Subject,"begin");
                    bool deferredConsume=hook.Action==12;
                    if(hook.Action!=1&&hook.Action!=4&&!deferredConsume)throw Invalid("begin hook action");
                    if(deferredConsume)
                    {
                        if(deferred==null||deferred.Consumed||active.Count!=1)
                            throw Invalid("orphan deferred consume");
                        ServiceBuilder blocker=active[0];
                        if(blocker.Token!=deferred.CurrentOwnerToken
                            ||blocker.CurrentParentToken!=deferred.CurrentOwnerParentToken
                            ||blocker.Kind!=deferred.CurrentOwnerKind
                            ||blocker.CurrentDepth!=deferred.CurrentOwnerDepth
                            ||hook.ServiceKindId!=deferred.TargetKind
                            ||hook.ExpectedActiveKind!=deferred.CurrentOwnerKind
                            ||e.ParentToken!=deferred.CurrentOwnerToken
                            ||e.Depth!=deferred.CurrentOwnerDepth+1)
                            throw Invalid("deferred consume ownership");
                    }
                    if(hook.Action==4)
                        ValidateTailBegin(events,i,ref e,hook);
                    if(e.Pc!=hook.Pc)throw Invalid("unexpected service begin PC");
                    if(e.SourceCpu!=hook.Cpu||e.ServiceKindId!=hook.ServiceKindId)throw Invalid("begin hook kind/source");
                    if(e.Offset!=0||e.PayloadLength!=0||e.Flags!=0
                        ||(hook.Flags&~2)!=0)throw Invalid("begin fields");
                    ushort parent=active.Count==0?(ushort)0:active[active.Count-1].Token;
                    if(e.ServiceToken==0||ContainsToken(active,e.ServiceToken)||e.ParentToken!=parent||e.Depth!=active.Count)
                        throw Invalid("begin token/parent/depth");
                    if(!hasKind[e.ServiceKindId])throw Invalid("unknown service kind");
                    var b=new ServiceBuilder{Token=e.ServiceToken,ParentToken=e.ParentToken,Kind=e.ServiceKindId,
                        Depth=e.Depth,CurrentParentToken=e.ParentToken,CurrentDepth=e.Depth,
                        BeginCoordinate=coordinate,BeginPc=e.Pc,EventCount=1,
                        BeginHookToken=e.Subject,BeginSourceCpu=e.SourceCpu,
                        RootToken=active.Count==0?e.ServiceToken:active[0].RootToken};
                    active.Add(b);
                    if(hook.Action==4&&deferredTransfer.Pending)
                    {
                        if(i!=deferredTransfer.BeginIndex
                            ||b.Token!=deferredTransfer.Token
                            ||b.CurrentParentToken!=deferredTransfer.ParentToken
                            ||b.Kind!=deferredTransfer.Kind
                            ||b.CurrentDepth!=deferredTransfer.Depth
                            ||deferred==null||deferred.Consumed)
                            throw Invalid("deferred tail transfer begin");
                        deferred.CurrentOwnerToken=b.Token;
                        deferred.CurrentOwnerParentToken=b.CurrentParentToken;
                        deferred.CurrentOwnerKind=b.Kind;
                        deferred.CurrentOwnerDepth=b.CurrentDepth;
                        deferredTransfer=default(DeferredOwnerTransfer);
                    }
                    if(deferredConsume)
                    {
                        deferred.Consumed=true;deferred.ConsumedToken=e.ServiceToken;
                        deferred.ConsumeCoordinate=coordinate;
                        deferredBegins.Add(Clone(deferred));
                        deferred=null;
                    }
                    break;
                }
                case 2:
                {
                    if(active.Count==0)throw Invalid("completion without active service");
                    bool cancelled=(e.Flags&2)!=0;
                    GpgxAudioObserverAdapter.ServiceHook completionHook=cancelled
                        ?default(GpgxAudioObserverAdapter.ServiceHook):RequireHook(e.Subject,"completion");
                    bool promotes=!cancelled&&promotionHooks
                        &&(completionHook.Action==8||completionHook.Action==9);
                    if(promotes&&active.Count<2)throw Invalid("promotion completion without direct parent");
                    ServiceBuilder b=active[promotes?active.Count-2:active.Count-1]; ValidateOwnership(ref e,b);
                    bool deferredBlocker=deferred!=null&&!deferred.Consumed;
                    if(deferredBlocker)
                    {
                        if(cancelled||promotes||completionHook.Action!=4
                            ||!MatchesCurrentOwner(deferred,b)
                            ||!CurrentOwnerIsOrigin(deferred)
                            ||completionHook.ExpectedActiveKind!=deferred.CurrentOwnerKind
                            ||!HasDeferredConsumeRoute(completionHook.ServiceKindId,
                                deferred.TargetKind))
                            throw Invalid("deferred blocker completion before consume");
                        ValidateTailEnd(events,i,ref e,completionHook,b);
                        ref GpgxAudioTraceEvent successor=ref events[i+1];
                        deferredTransfer=new DeferredOwnerTransfer
                        {Pending=true,BeginIndex=i+1,Token=successor.ServiceToken,
                            ParentToken=successor.ParentToken,Kind=successor.ServiceKindId,
                            Depth=successor.Depth};
                    }
                    b.EventCount++;
                    if(e.Offset!=0||e.PayloadLength!=0||e.Payload!=0||e.Value!=0)
                        throw Invalid("completion fields");
                    if(reset!=null&&!cancelled)throw Invalid("non-cancelled service end during reset cancellation");
                    if(cancelled)
                    {
                        if(e.Flags!=2||e.Subject!=0||e.Pc!=0||e.SourceCpu!=3)throw Invalid("reset cancellation fields");
                        GpgxAudioObserverAdapter.ServiceKind kind=RequireKind(b.Kind);
                        ValidateSnapshots(b,kind.CancellationRangeFirst,kind.CancellationRangeCount,e.SourceCpu,e.Pc); b.Cancelled=true;
                    }
                    else
                    {
                        if(e.Flags!=0)throw Invalid("completion flags");
                        GpgxAudioObserverAdapter.ServiceHook hook=completionHook;
                        if((hook.Action<2||hook.Action>5)
                            &&hook.Action!=8&&hook.Action!=9)
                            throw Invalid("completion hook action");
                        if(hook.Action==4)
                            ValidateTailEnd(events,i,ref e,hook,b);
                        if(e.Pc!=hook.Pc||e.SourceCpu!=hook.Cpu
                            ||(hook.Action==8||hook.Action==9?hook.ServiceKindId!=b.Kind
                                ||hook.ExpectedActiveKind!=active[active.Count-1].Kind
                                :hook.ExpectedActiveKind!=b.Kind))
                            throw Invalid("unexpected completion PC/kind/source");
                        ValidateSnapshots(b,hook.RangeFirst,hook.RangeCount,e.SourceCpu,e.Pc);
                        if(hook.Action==5)
                            ValidateConditionalCompletion(events,i,ref e,hook);
                    }
                    b.EndCoordinate=coordinate;b.EndPc=e.Pc;b.EndHookToken=cancelled?(ushort)0:e.Subject;
                    active.RemoveAt(promotes?active.Count-2:active.Count-1);
                    pending.Add(b);
                    if(cancelled&&reset==null)throw Invalid("cancellation outside reset");
                    if(promotes)
                    {
                        if(cancelled||i+1>=count||events[i+1].Kind!=11)throw Invalid("promotion adjacency");
                        ServiceBuilder child=active[active.Count-1];
                        ref GpgxAudioTraceEvent promote=ref events[i+1];
                        if(promote.Ordinal!=e.Ordinal+1||promote.ServiceToken!=child.Token
                            ||promote.ParentToken!=b.CurrentParentToken||promote.Depth!=b.CurrentDepth
                            ||promote.ServiceKindId!=child.Kind||promote.Subject!=e.Subject
                            ||promote.Pc!=e.Pc||promote.SourceCpu!=e.SourceCpu||promote.Offset!=0
                            ||promote.PayloadLength!=0||promote.Payload!=0||promote.Value!=0||promote.Flags!=0)
                            throw Invalid("promotion fields");
                    }
                    break;
                }
                case 3:
                case 4:
                {
                    if(deferred!=null&&!deferred.Consumed&&e.SourceCpu==2)
                        throw Invalid("interposed M68K chip event during deferred begin");
                    rawChipCount++;
                    if(reset!=null&&active.Count!=0)throw Invalid("reset chip event before service cancellations");
                    ServiceBuilder b=OwnedBuilder(ref e,active,reset);
                    if(reset!=null)
                    {if(e.SourceCpu!=3||e.Pc!=0)throw Invalid("reset chip source/PC");}
                    else if(e.SourceCpu==1)
                    {if(e.Pc>0xffff)throw Invalid("Z80 chip PC");}
                    else if(e.SourceCpu==2)
                    {if(e.Pc>0xffffff)throw Invalid("M68K chip PC");}
                    else throw Invalid("chip source");
                    if(e.Offset!=0||e.PayloadLength!=0||e.Payload!=0||e.Flags!=0)
                        throw Invalid("chip event fields");
                    long rawCoordinate=coordinate;
                    if(e.Kind==3)
                    {
                        if(e.Subject>3)throw Invalid("FM subject");
                        byte port=e.Subject<2?(byte)0:(byte)1;byte register=port==0?port0:port1;
                        b.AddChip(new WriteRecord{Coordinate=coordinate,Ordinal=e.Ordinal,
                            Pc=e.Pc,SourceCpu=e.SourceCpu,Token=e.ServiceToken,Kind=3,Subject=(byte)e.Subject,
                            Port=port,Register=register,Value=e.Value});
                        if(e.Subject==0)port0=e.Value; else if(e.Subject==2)port1=e.Value;
                    }
                    else
                    { if(e.Subject!=0)throw Invalid("PSG subject"); b.AddChip(new WriteRecord{Coordinate=coordinate,
                        Ordinal=e.Ordinal,Pc=e.Pc,SourceCpu=e.SourceCpu,Token=e.ServiceToken,
                        Kind=4,Subject=0,Value=e.Value}); }
                    ownedChipCount++;
                    break;
                }
                case 5:
                case 6:
                case 7:
                {
                    ServiceBuilder b=promotionHooks
                        ?OwnedSnapshotBuilder(events,i,count,ref e,active,reset)
                        :OwnedBuilder(ref e,active,reset);
                    Snapshot(ref e,b);
                    break;
                }
                case 8:
                {
                    if(deferred!=null&&!deferred.Consumed)
                        throw Invalid("reset during deferred begin");
                    if(reset!=null||e.SourceCpu!=3||e.Pc!=0||e.ServiceToken==0||e.ParentToken!=0||e.Depth!=0
                        ||e.ServiceKindId!=config.ResetServiceKind||e.Subject!=active.Count||(e.Flags&~1)!=0
                        ||e.Offset!=0||e.PayloadLength!=0||e.Payload!=0||e.Value!=0)
                        throw Invalid("reset begin fields");
                    reset=new ServiceBuilder{Token=e.ServiceToken,Kind=e.ServiceKindId,Depth=0,CurrentDepth=0,
                        BeginCoordinate=coordinate,BeginPc=0,IsReset=true,ResetPower=(e.Flags&1)!=0,EventCount=1,
                        RootToken=e.ServiceToken};
                    port0=port1=0;nowArmed=armHookToken==0;epoch++;
                    break;
                }
                case 9:
                {
                    if(reset==null||active.Count!=0)throw Invalid("reset end shape");
                    ValidateOwnership(ref e,reset);
                    reset.EventCount++;
                    if(e.SourceCpu!=3||e.Pc!=0||e.Subject!=0||e.Offset!=0||e.PayloadLength!=0||e.Payload!=0
                        ||e.Flags!=(reset.ResetPower?1:0))
                        throw Invalid("reset end fields");
                    GpgxAudioObserverAdapter.ServiceKind kind=RequireKind(reset.Kind);
                    ValidateSnapshots(reset,kind.CancellationRangeFirst,kind.CancellationRangeCount,e.SourceCpu,e.Pc);reset.EndCoordinate=coordinate;
                    pending.Add(reset);
                    resets.Add(new ResetRecord{Builder=reset,Power=(e.Flags&1)!=0});reset=null;
                    break;
                }
                case 10:
                {
                    GpgxAudioObserverAdapter.ServiceHook hook=RequireHook(e.Subject,"marker");
                    if(e.Pc!=hook.Pc||e.SourceCpu!=2||hook.Cpu!=2||e.Offset!=0
                        ||e.Flags!=0)
                        throw Invalid("marker fields");
                    bool carriesA7=config.AbiVersion==4&&hook.Action==7
                        &&e.Value==3;
                    if(carriesA7
                        ?e.PayloadLength!=4||(e.Payload>>32)!=0
                        :e.PayloadLength!=0||e.Payload!=0)
                        throw Invalid("marker fields");
                    if(e.Value==4)
                    {
                        if(hook.Action!=11||active.Count!=1
                            ||hook.ExpectedActiveKind!=active[0].Kind
                            ||hook.ServiceKindId==0)
                            throw Invalid("deferred marker action/ownership");
                        ServiceBuilder blocker=OwnedBuilder(ref e,active,reset);
                        if(deferred==null)
                        {
                            deferred=new DeferredBeginReservation
                            {BlockerToken=blocker.Token,BlockerParentToken=blocker.CurrentParentToken,
                                BlockerKind=blocker.Kind,BlockerDepth=blocker.CurrentDepth,
                                CurrentOwnerToken=blocker.Token,
                                CurrentOwnerParentToken=blocker.CurrentParentToken,
                                CurrentOwnerKind=blocker.Kind,
                                CurrentOwnerDepth=blocker.CurrentDepth,
                                TargetKind=hook.ServiceKindId,HookToken=e.Subject,SourceCpu=e.SourceCpu,
                                Pc=e.Pc,FirstCoordinate=coordinate,LatestCoordinate=coordinate,
                                FirstOrdinal=e.Ordinal,LatestOrdinal=e.Ordinal,ObservationCount=1};
                        }
                        else
                        {
                            if(deferred.Consumed||deferred.BlockerToken!=blocker.Token
                                ||deferred.BlockerParentToken!=blocker.CurrentParentToken
                                ||deferred.BlockerKind!=blocker.Kind
                                ||deferred.BlockerDepth!=blocker.CurrentDepth
                                ||deferred.TargetKind!=hook.ServiceKindId
                                ||deferred.HookToken!=e.Subject||deferred.SourceCpu!=e.SourceCpu
                                ||deferred.Pc!=e.Pc)
                                throw Invalid("deferred marker reservation ownership");
                            deferred.LatestCoordinate=coordinate;deferred.LatestOrdinal=e.Ordinal;
                            deferred.ObservationCount++;
                        }
                    }
                    else if(e.Value==0||e.Value==1)
                    {
                        if((e.Value==0?(hook.Action!=5&&hook.Action!=9):hook.Action!=5)
                            ||active.Count==0
                            ||hook.ExpectedActiveKind!=active[active.Count-1].Kind)
                            throw Invalid("conditional marker action/ownership");
                        if(hook.Action==9&&(active.Count<2
                            ||hook.ServiceKindId!=active[active.Count-2].Kind))
                            throw Invalid("conditional marker direct parent");
                        ServiceBuilder b=OwnedBuilder(ref e,active,reset);
                        if(e.Value==1&&(i+1>=count||events[i+1].Kind!=5
                            ||events[i+1].ServiceToken!=b.Token))
                            throw Invalid("conditional POP marker adjacency");
                    }
                    else if(e.Value==2)
                    {
                        if((hook.Action!=6&&hook.Action!=10)||active.Count==0
                            ||hook.ExpectedActiveKind!=active[active.Count-1].Kind)
                            throw Invalid("retry marker action/ownership");
                        if(hook.Action==6) OwnedBuilder(ref e,active,reset);
                        else
                        {
                            if(active.Count<2
                                ||hook.ServiceKindId!=active[active.Count-2].Kind)
                                throw Invalid("retry marker direct parent");
                            ServiceBuilder parent=active[active.Count-2];
                            if(e.ServiceToken!=parent.Token
                                ||e.ParentToken!=parent.CurrentParentToken
                                ||e.ServiceKindId!=parent.Kind
                                ||e.Depth!=parent.CurrentDepth)
                                throw Invalid("retry marker parent ownership");
                            parent.EventCount++;
                        }
                    }
                    else if(e.Value==3)
                    {
                        if(hook.Action!=7)throw Invalid("observation marker action");
                        if(hook.ExpectedActiveKind==0)
                        {
                            if(active.Count!=0||e.ServiceToken!=0||e.ParentToken!=0
                                ||e.ServiceKindId!=0||e.Depth!=0)
                                throw Invalid("root observation marker ownership");
                        }
                        else
                        {
                            if(active.Count==0||hook.ExpectedActiveKind!=active[active.Count-1].Kind)
                                throw Invalid("owned observation marker kind");
                            OwnedBuilder(ref e,active,reset);
                        }
                    }
                    else throw Invalid("marker value");
                    break;
                }
                case 11:
                {
                    if(i==0||events[i-1].Kind!=2||active.Count==0)throw Invalid("orphan promotion");
                    ref GpgxAudioTraceEvent ended=ref events[i-1];
                    GpgxAudioObserverAdapter.ServiceHook hook=RequireHook(e.Subject,"promotion");
                    if((hook.Action!=8&&hook.Action!=9)
                        ||ended.Subject!=e.Subject||ended.Pc!=e.Pc
                        ||ended.SourceCpu!=e.SourceCpu)throw Invalid("promotion hook adjacency");
                    ServiceBuilder child=active[active.Count-1];
                    if(e.ServiceToken!=child.Token||e.ServiceKindId!=child.Kind
                        ||e.ParentToken!=child.CurrentParentToken&&e.ParentToken!=ended.ParentToken
                        ||e.Depth+1!=child.CurrentDepth||e.ParentToken!=ended.ParentToken
                        ||e.Offset!=0||e.PayloadLength!=0||e.Payload!=0||e.Value!=0||e.Flags!=0)
                        throw Invalid("promotion ownership");
                    ushort oldRoot=child.RootToken;
                    child.AddAncestry(new AncestryRecord{Coordinate=coordinate,NativeOrdinal=e.Ordinal,
                        PreviousParentToken=child.CurrentParentToken,PreviousDepth=child.CurrentDepth,
                        CurrentParentToken=e.ParentToken,CurrentDepth=e.Depth,HookToken=e.Subject,
                        SourceCpu=e.SourceCpu,Pc=e.Pc});
                    child.CurrentParentToken=e.ParentToken;child.CurrentDepth=e.Depth;
                    if(e.ParentToken==0)
                    {
                        child.RootToken=child.Token;
                        ReassignDescendantRoots(pending,child.Token,oldRoot);
                    }
                    break;
                }
                }
                if(deferred!=null&&!deferred.Consumed&&e.SourceCpu==2
                    &&!(e.Kind==10&&e.Value==4))
                    throw Invalid("interposed M68K event during deferred begin");
            }
            if(deferredTransfer.Pending)throw Invalid("partial deferred tail transfer");
            if(reset!=null)throw Invalid("partial snapshot/reset state");
            for(int i=0;i<active.Count;i++)if(active[i].ActiveByteLength!=0)throw Invalid("partial snapshot/reset state");
            if((uint)pending.Count>config.EventCapacity)throw Invalid("pending service bound");
            ulong pendingEvents=0;for(int i=0;i<pending.Count;i++)pendingEvents+=(uint)pending[i].EventCount;
            ulong continuation=(ulong)(config.MaxContinuationFrames==0?1:config.MaxContinuationFrames+1);
            ulong depth=(ulong)(config.MaxDepth==0?1:config.MaxDepth);
            if(pendingEvents>(ulong)config.EventCapacity*continuation*depth)throw Invalid("pending event bound");
            int heldCount=0;
            for(int i=0;i<pending.Count;i++)
            {
                ServiceBuilder candidate=pending[i];
                if(ContainsToken(active,candidate.RootToken))pending[heldCount++]=candidate;
                else complete.Add(candidate);
            }
            if(heldCount<pending.Count)pending.RemoveRange(heldCount,pending.Count-heldCount);
            if(retainRaw)
            {
                SortByBegin(complete);
                for(int i=0;i<resets.Count;i++)for(int j=0;j<complete.Count;j++)
                    if(object.ReferenceEquals(resets[i].Builder,complete[j])){ResetRecord rr=resets[i];rr.ServiceIndex=j;resets[i]=rr;break;}
            }
            if(ownedChipCount!=rawChipCount)throw Invalid("chip flatten coverage");
            GpgxAudioTraceEvent[] raw=EmptyEvents;
            if(retainRaw){raw=new GpgxAudioTraceEvent[count];Array.Copy(events,raw,count);}
            if(deferred!=null)deferredBegins.Add(Clone(deferred));
            return new ProjectionResult{Capture=retainRaw?new FrameCapture(raw,complete,resets,
                    globalEventCoordinate,deferredBegins):null,
                Active=active,Completed=complete,Pending=pending,
                Port0=port0,Port1=port1,Epoch=epoch,Armed=nowArmed,
                Deferred=deferred,EventCount=count};
        }

        private void CommitProjection(ProjectionResult p)
        {
            activeServices.Clear();activeServices.AddRange(p.Active);activeTokens.Clear();
            for(int i=0;i<p.Active.Count;i++)activeTokens.Add(p.Active[i].Token);
            pendingCompleted.Clear();pendingCompleted.AddRange(p.Pending);
            pendingDeferredBegin=p.Deferred;
            ymPort0Address=p.Port0;ymPort1Address=p.Port1;armEpoch=p.Epoch;armed=p.Armed;
            LastCapture=p.Capture;globalEventCoordinate+=p.EventCount;
            for(int i=0;i<p.Completed.Count;i++)
            { ServiceBuilder s=p.Completed[i]; if(!s.Cancelled&&armHookToken!=0&&HookArms(s)) {armed=true;armEpoch++;} }
            projectionActive.Clear();projectionComplete.Clear();projectionPending.Clear();projectionResets.Clear();
            projectionDeferredBegins.Clear();
        }

        private bool HookArms(ServiceBuilder s)
        { return hasHook[s.EndHookToken]&&(hookByToken[s.EndHookToken].Flags&1)!=0; }

        private static bool ContainsToken(List<ServiceBuilder> active,ushort token)
        {for(int i=0;i<active.Count;i++)if(active[i].Token==token)return true;return false;}

        private static bool MatchesCurrentOwner(DeferredBeginReservation deferred,
            ServiceBuilder service)
        {
            return service.Token==deferred.CurrentOwnerToken
                &&service.CurrentParentToken==deferred.CurrentOwnerParentToken
                &&service.Kind==deferred.CurrentOwnerKind
                &&service.CurrentDepth==deferred.CurrentOwnerDepth;
        }

        private static bool CurrentOwnerIsOrigin(DeferredBeginReservation deferred)
        {
            return deferred.CurrentOwnerToken==deferred.BlockerToken
                &&deferred.CurrentOwnerParentToken==deferred.BlockerParentToken
                &&deferred.CurrentOwnerKind==deferred.BlockerKind
                &&deferred.CurrentOwnerDepth==deferred.BlockerDepth;
        }

        private bool HasDeferredConsumeRoute(byte currentKind,byte targetKind)
        {
            int matches=0;
            for(int i=0;i<hooks.Length;i++)
                if(hooks[i].Action==12&&hooks[i].ExpectedActiveKind==currentKind
                    &&hooks[i].ServiceKindId==targetKind)matches++;
            return matches==1;
        }

        private static void ReassignDescendantRoots(List<ServiceBuilder> pending,ushort childToken,ushort oldRoot)
        {
            for(int i=0;i<pending.Count;i++)
            {
                ServiceBuilder candidate=pending[i];
                if(candidate.RootToken!=oldRoot)continue;
                ushort parent=candidate.ParentToken;
                for(int steps=0;parent!=0&&steps<8;steps++)
                {
                    if(parent==childToken)
                    {
                        candidate=Clone(candidate);candidate.RootToken=childToken;
                        pending[i]=candidate;break;
                    }
                    ServiceBuilder next=null;
                    for(int j=0;j<pending.Count;j++)if(pending[j].Token==parent){next=pending[j];break;}
                    if(next==null)break;parent=next.ParentToken;
                }
            }
        }

        private static void ValidateTailEnd(GpgxAudioTraceEvent[] events,int index,
            ref GpgxAudioTraceEvent end,GpgxAudioObserverAdapter.ServiceHook hook,ServiceBuilder oldService)
        {
            if(index+1>=events.Length)throw Invalid("tail completion without adjacent begin");
            ref GpgxAudioTraceEvent begin=ref events[index+1];
            if(begin.Kind!=1||begin.Ordinal!=end.Ordinal+1||begin.Subject!=end.Subject||begin.Pc!=end.Pc
                ||begin.SourceCpu!=end.SourceCpu||begin.ServiceToken==0||begin.ServiceToken==oldService.Token
                ||begin.ParentToken!=oldService.CurrentParentToken||begin.Depth!=oldService.CurrentDepth
                ||begin.ServiceKindId!=hook.ServiceKindId||oldService.Kind!=hook.ExpectedActiveKind)
                throw Invalid("tail completion/begin pair");
        }

        private void ValidateConditionalCompletion(GpgxAudioTraceEvent[] events,int index,
            ref GpgxAudioTraceEvent end,GpgxAudioObserverAdapter.ServiceHook hook)
        {
            int at=index-1;
            for(int rangeIndex=hook.RangeCount-1;rangeIndex>=0;rangeIndex--)
            {
                GpgxAudioObserverAdapter.SnapshotRange range=ranges[hook.RangeFirst+rangeIndex];
                if(at<0||events[at].Kind!=7||events[at].Subject!=range.RangeId
                    ||events[at].ServiceToken!=end.ServiceToken)throw Invalid("conditional completion snapshot adjacency");
                at--;
                while(at>=0&&events[at].Kind==6&&events[at].Subject==range.RangeId
                    &&events[at].ServiceToken==end.ServiceToken)at--;
                if(at<0||events[at].Kind!=5||events[at].Subject!=range.RangeId
                    ||events[at].ServiceToken!=end.ServiceToken)throw Invalid("conditional completion snapshot adjacency");
                at--;
            }
            if(at<0||events[at].Kind!=10||events[at].Value!=1
                ||events[at].Subject!=hook.HookToken||events[at].Pc!=hook.Pc
                ||events[at].ServiceToken!=end.ServiceToken)
                throw Invalid("conditional completion without POP marker");
        }

        private static void ValidateTailBegin(GpgxAudioTraceEvent[] events,int index,
            ref GpgxAudioTraceEvent begin,GpgxAudioObserverAdapter.ServiceHook hook)
        {
            if(index==0)throw Invalid("tail begin without adjacent completion");
            ref GpgxAudioTraceEvent end=ref events[index-1];
            if(end.Kind!=2||end.Ordinal+1!=begin.Ordinal||end.Subject!=begin.Subject||end.Pc!=begin.Pc
                ||end.SourceCpu!=begin.SourceCpu||end.ServiceToken==begin.ServiceToken
                ||end.ParentToken!=begin.ParentToken||end.Depth!=begin.Depth
                ||end.ServiceKindId!=hook.ExpectedActiveKind||begin.ServiceKindId!=hook.ServiceKindId)
                throw Invalid("tail completion/begin pair");
        }

        private static void SortByBegin(List<ServiceBuilder> services)
        {
            for(int i=1;i<services.Count;i++)
            {
                ServiceBuilder value=services[i];int j=i-1;
                while(j>=0&&services[j].BeginCoordinate>value.BeginCoordinate)
                {services[j+1]=services[j];j--;}
                services[j+1]=value;
            }
        }

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        private ServiceBuilder OwnedBuilder(ref GpgxAudioTraceEvent e,List<ServiceBuilder> active,ServiceBuilder reset)
        {
            ServiceBuilder b=null;
            if(reset!=null&&e.ServiceToken==reset.Token)
            {if(active.Count!=0)throw Invalid("reset root event before service cancellations");b=reset;}
            else if(active.Count!=0&&e.ServiceToken==active[active.Count-1].Token)b=active[active.Count-1];
            if(b==null)throw Invalid("orphan event is not owned by the innermost service");
            if(e.ParentToken!=b.CurrentParentToken||e.ServiceKindId!=b.Kind||e.Depth!=b.CurrentDepth)
                throw Invalid("token/parent/depth/kind ownership");
            b.EventCount++;
            return b;
        }

        private ServiceBuilder OwnedSnapshotBuilder(GpgxAudioTraceEvent[] events,int index,int count,
            ref GpgxAudioTraceEvent e,List<ServiceBuilder> active,ServiceBuilder reset)
        {
            if(reset!=null||active.Count==0||e.ServiceToken==active[active.Count-1].Token)
                return OwnedBuilder(ref e,active,reset);
            if(active.Count<2)return OwnedBuilder(ref e,active,reset);
            ServiceBuilder parent=active[active.Count-2];
            ServiceBuilder child=active[active.Count-1];
            if(e.ServiceToken!=parent.Token||e.ParentToken!=parent.CurrentParentToken
                ||e.ServiceKindId!=parent.Kind||e.Depth!=parent.CurrentDepth)
                return OwnedBuilder(ref e,active,reset);
            int at=index;
            while(at<count&&events[at].Kind>=5&&events[at].Kind<=7)
            {
                ref GpgxAudioTraceEvent snapshot=ref events[at];
                if(snapshot.ServiceToken!=parent.Token||snapshot.ParentToken!=parent.CurrentParentToken
                    ||snapshot.ServiceKindId!=parent.Kind||snapshot.Depth!=parent.CurrentDepth)
                    throw Invalid("promotion snapshot ownership");
                at++;
            }
            if(at+1>=count||events[at].Kind!=2||events[at+1].Kind!=11)
                throw Invalid("promotion snapshot adjacency");
            ref GpgxAudioTraceEvent end=ref events[at];
            ref GpgxAudioTraceEvent promote=ref events[at+1];
            GpgxAudioObserverAdapter.ServiceHook hook=RequireHook(end.Subject,"promotion completion");
            if((hook.Action!=8&&hook.Action!=9)||end.ServiceToken!=parent.Token
                ||end.ParentToken!=parent.CurrentParentToken||end.ServiceKindId!=parent.Kind
                ||end.Depth!=parent.CurrentDepth||end.Pc!=hook.Pc||end.SourceCpu!=hook.Cpu
                ||hook.ServiceKindId!=parent.Kind||hook.ExpectedActiveKind!=child.Kind
                ||promote.Ordinal!=end.Ordinal+1||promote.ServiceToken!=child.Token
                ||promote.Subject!=end.Subject||promote.Pc!=end.Pc||promote.SourceCpu!=end.SourceCpu)
                throw Invalid("promotion snapshot adjacency");
            parent.EventCount++;
            return parent;
        }
        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        private static void ValidateOwnership(ref GpgxAudioTraceEvent e,ServiceBuilder b)
        {
            if(e.ServiceToken!=b.Token||e.ParentToken!=b.CurrentParentToken
                ||e.ServiceKindId!=b.Kind||e.Depth!=b.CurrentDepth)
                throw Invalid("token/parent/depth/kind ownership");
        }
        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        private void ValidateCommon(ref GpgxAudioTraceEvent e)
        {
            if(e.Kind<1||e.Kind>11)throw Invalid("unknown event kind");
            if(e.Reserved!=0)throw Invalid("reserved field");
            if(e.Kind!=6&&e.Kind!=10&&e.Payload!=0)
                throw Invalid("unexpected payload");
            if(e.Kind!=3&&e.Kind!=4&&e.Kind!=10&&e.Value!=0)throw Invalid("unexpected value");
        }
        private GpgxAudioObserverAdapter.ServiceHook RequireHook(ushort token,string where)
        {
            if(token==0||!hasHook[token])throw Invalid("unknown "+where+" hook");
            return hookByToken[token];
        }
        private GpgxAudioObserverAdapter.ServiceKind RequireKind(byte kind)
        {
            if(!hasKind[kind])throw Invalid("unknown service kind");return kindById[kind];
        }
        private void ValidateSnapshots(ServiceBuilder b,ushort first,ushort count,byte source,uint pc)
        {
            int actual=b.SnapshotRecordCount;
            if((uint)first+count>ranges.Length||b.ActiveByteLength!=0||actual!=count)throw Invalid("snapshot group count");
            for(int i=0;i<count;i++){SnapshotRecord r=b.SnapshotAt(i);if(r.RangeId!=ranges[first+i].RangeId
                ||r.SourceCpu!=source||r.Pc!=pc)throw Invalid("snapshot range order/source/PC");}
        }
        private void Snapshot(ref GpgxAudioTraceEvent e,ServiceBuilder b)
        {
            if(!hasRange[e.Subject])throw Invalid("unknown snapshot range");
            GpgxAudioObserverAdapter.SnapshotRange r=rangeById[e.Subject];
            if(e.SourceCpu<1||e.SourceCpu>3||e.Flags!=0||e.Value!=0)throw Invalid("snapshot source/flags");
            if(e.Kind==5)
            {
                if(b.ActiveByteLength!=0||e.Offset!=0||e.PayloadLength!=0||e.Payload!=0)throw Invalid("snapshot begin");
                b.ActiveRange=e.Subject;b.ActiveSnapshotSource=e.SourceCpu;b.ActiveSnapshotPc=e.Pc;
                b.ActiveByteLength=r.Length;b.ActiveByteCount=0;b.ActivePayload=0;
                b.ActiveBytes=r.Length>8?new byte[r.Length]:null;
            }
            else if(e.Kind==6)
            {
                if(b.ActiveByteLength==0||b.ActiveRange!=e.Subject||e.Offset!=b.ActiveByteCount
                    ||e.PayloadLength==0||e.PayloadLength>8||b.ActiveByteCount+e.PayloadLength>r.Length)
                    throw Invalid("snapshot chunk gap/overlap");
                for(int i=0;i<e.PayloadLength;i++)
                {byte value=(byte)(e.Payload>>(8*i));if(b.ActiveBytes==null)b.ActivePayload|=(ulong)value<<(8*b.ActiveByteCount);
                    else b.ActiveBytes[b.ActiveByteCount]=value;b.ActiveByteCount++;}
                if(e.PayloadLength<8&&(e.Payload>>(8*e.PayloadLength))!=0)throw Invalid("snapshot chunk tail");
            }
            else
            {
                if(b.ActiveByteLength==0||b.ActiveRange!=e.Subject||e.Offset!=r.Length
                    ||b.ActiveByteCount!=r.Length||e.PayloadLength!=0||e.Payload!=0)throw Invalid("snapshot end");
                if(e.SourceCpu!=b.ActiveSnapshotSource||e.Pc!=b.ActiveSnapshotPc)throw Invalid("snapshot source/PC continuity");
                b.AddSnapshot(new SnapshotRecord{RangeId=e.Subject,SourceCpu=e.SourceCpu,Pc=e.Pc,Bytes=b.ActiveBytes,
                    Payload=b.ActivePayload,Length=b.ActiveByteLength});
                b.ActiveBytes=null;b.ActiveByteCount=0;b.ActiveByteLength=0;b.ActivePayload=0;b.ActiveRange=0;
            }
        }
        private static ServiceBuilder Clone(ServiceBuilder b)
        {
            var n=new ServiceBuilder{Token=b.Token,ParentToken=b.ParentToken,Kind=b.Kind,Depth=b.Depth,
                CurrentParentToken=b.CurrentParentToken,CurrentDepth=b.CurrentDepth,
                RootToken=b.RootToken,
                BeginCoordinate=b.BeginCoordinate,EndCoordinate=b.EndCoordinate,BeginPc=b.BeginPc,EndPc=b.EndPc,
                BeginHookToken=b.BeginHookToken,BeginSourceCpu=b.BeginSourceCpu,
                Cancelled=b.Cancelled,IsReset=b.IsReset,ResetPower=b.ResetPower,ActiveRange=b.ActiveRange,
                EventCount=b.EventCount,
                EndHookToken=b.EndHookToken,
                ActiveSnapshotSource=b.ActiveSnapshotSource,ActiveSnapshotPc=b.ActiveSnapshotPc,
                ActiveByteCount=b.ActiveByteCount,ActiveByteLength=b.ActiveByteLength,ActivePayload=b.ActivePayload,
                ActiveBytes=b.ActiveBytes==null?null:(byte[])b.ActiveBytes.Clone(),
                AncestryRecords=b.AncestryRecords==null?null:(AncestryRecord[])b.AncestryRecords.Clone()};
            if(b.ChipRecordCount!=0){n.Chip0=b.Chip0;n.Chip1=b.Chip1;n.Chip2=b.Chip2;n.Chip3=b.Chip3;
                n.ChipRecordCount=b.ChipRecordCount;if(b.AdditionalChipRecords!=null)
                {n.AdditionalChipRecords=new WriteRecord[b.AdditionalChipRecords.Length];
                    Array.Copy(b.AdditionalChipRecords,n.AdditionalChipRecords,b.ChipRecordCount-4);}}
            if(b.SnapshotRecordCount!=0){n.FirstSnapshot=b.FirstSnapshot;n.SnapshotRecordCount=b.SnapshotRecordCount;
                if(b.AdditionalSnapshots!=null){n.AdditionalSnapshots=new SnapshotRecord[b.AdditionalSnapshots.Length];
                    Array.Copy(b.AdditionalSnapshots,n.AdditionalSnapshots,b.SnapshotRecordCount-1);}}return n;
        }
        private static DeferredBeginReservation Clone(DeferredBeginReservation b)
        {
            if(b==null)return null;
            return new DeferredBeginReservation
            {BlockerToken=b.BlockerToken,BlockerParentToken=b.BlockerParentToken,
                BlockerKind=b.BlockerKind,BlockerDepth=b.BlockerDepth,
                CurrentOwnerToken=b.CurrentOwnerToken,
                CurrentOwnerParentToken=b.CurrentOwnerParentToken,
                CurrentOwnerKind=b.CurrentOwnerKind,
                CurrentOwnerDepth=b.CurrentOwnerDepth,
                TargetKind=b.TargetKind,HookToken=b.HookToken,SourceCpu=b.SourceCpu,
                Pc=b.Pc,FirstOrdinal=b.FirstOrdinal,LatestOrdinal=b.LatestOrdinal,
                FirstCoordinate=b.FirstCoordinate,LatestCoordinate=b.LatestCoordinate,
                ObservationCount=b.ObservationCount,Consumed=b.Consumed,
                ConsumedToken=b.ConsumedToken,ConsumeCoordinate=b.ConsumeCoordinate};
        }
        private static InvalidOperationException Invalid(string what)
        {return new InvalidOperationException("The native audio observer returned invalid "+what+".");}

        public Checkpoint CreateCheckpoint()
        {
            if (capturing) throw new InvalidOperationException("Cannot save during an audio observer frame.");
            if (activeTokens.Count != 0)
                throw new InvalidOperationException("Cannot save with a continued audio service open.");
            return new Checkpoint(runtimeInstanceId, armEpoch, armed,
                ymPort0Address, ymPort1Address);
        }

        public void RestoreCheckpoint(Checkpoint checkpoint)
        {
            ValidateCheckpoint(checkpoint);
            ApplyCheckpoint(checkpoint);
        }

        internal void ValidateCheckpoint(Checkpoint checkpoint)
        {
            if (checkpoint == null) throw new ArgumentNullException("checkpoint");
            if (capturing) throw new InvalidOperationException("Cannot load during an audio observer frame.");
            if (activeTokens.Count != 0)
                throw new InvalidOperationException("Cannot load with a continued audio service open.");
            if (checkpoint.RuntimeInstanceId != runtimeInstanceId)
                throw new InvalidOperationException("The checkpoint belongs to a different observer/core instance.");
            if (checkpoint.ArmEpoch != armEpoch)
                throw new InvalidOperationException("The checkpoint arm epoch does not match the current native observer epoch.");
        }

        internal void ApplyCheckpoint(Checkpoint checkpoint)
        {
            armed = checkpoint.Armed;
            ymPort0Address = checkpoint.YmPort0Address;
            ymPort1Address = checkpoint.YmPort1Address;
            drainBuffer = null;
        }

        public void ResetAfterLoad()
        {
            if (capturing) throw new InvalidOperationException("Cannot load state during an audio observer frame.");
            if (activeTokens.Count != 0)
                throw new InvalidOperationException("Cannot load state with a continued audio service open.");
            drainBuffer = null;
        }

        private static void RequireOk(int result, string operation)
        {
            if (result != 0) throw new InvalidOperationException(
                "GPGX audio observer " + operation + " failed with status " + result + ".");
        }
    }
}
