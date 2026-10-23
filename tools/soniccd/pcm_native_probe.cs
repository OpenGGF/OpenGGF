// CD-06 2026-10-23: natural-input source-native PCM blocks and independent controls.
// Frozen oracle_probe.cs contributes only firmware/media/hash adapter classes.
using System;
using System.IO;
using System.Linq;
using System.Collections.Generic;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using BizHawk.BizInvoke;
using BizHawk.Emulation.Common;
using BizHawk.Emulation.Cores;
using BizHawk.Emulation.Cores.Consoles.Sega.gpgx;
using BizHawk.Emulation.Cores.Waterbox;
using Newtonsoft.Json;

public abstract class PcmNativeExports
{
    [BizImport(CallingConvention.Cdecl)] public abstract uint pcm_native_abi();
    [BizImport(CallingConvention.Cdecl)] public abstract uint pcm_native_summary_bytes();
    [BizImport(CallingConvention.Cdecl)] public abstract uint pcm_native_block_bytes();
    [BizImport(CallingConvention.Cdecl)] public abstract uint pcm_native_capacity();
    [BizImport(CallingConvention.Cdecl)] public abstract uint pcm_native_fault();
    [BizImport(CallingConvention.Cdecl)] public abstract uint pcm_native_dropped();
    [BizImport(CallingConvention.Cdecl)] public abstract uint pcm_native_count();
    [BizImport(CallingConvention.Cdecl)] public abstract uint pcm_native_ready();
    [BizImport(CallingConvention.Cdecl)] public abstract int pcm_native_configure(uint enabled, uint limit, IntPtr ordinals, uint count);
    [BizImport(CallingConvention.Cdecl)] public abstract int pcm_native_poll(IntPtr destination, uint bytes);
    [BizImport(CallingConvention.Cdecl)] public abstract int pcm_native_copy_block(uint index, IntPtr destination, uint bytes);
}

internal sealed class PcmLedger : IDisposable
{
    public const int Capacity=4096, SummaryBytes=64, BlockBytes=262656;
    public readonly PcmNativeExports Api;
    readonly IntPtr summaries, block, ordinals;
    readonly int[] selected;
    readonly bool enabled, overflow;
    uint seen;
    public bool InvalidApiRejected;
    public PcmLedger(GPGX core, bool enabled, bool overflow, int[] selected, bool badLayout)
    {
        this.enabled=enabled; this.overflow=overflow; this.selected=selected;
        if (IntPtr.Size != 8 || !BitConverter.IsLittleEndian) throw new InvalidOperationException("Require x86-64 little-endian ABI");
        var host=(WaterboxHost)typeof(GPGX).GetField("_elf", BindingFlags.Instance|BindingFlags.NonPublic).GetValue(core);
        var adapter=CallingConventionAdapters.MakeWaterbox(new Delegate[0], host);
        host.Enter();
        try { Api=BizInvoker.GetInvoker<PcmNativeExports>(host, host, adapter); }
        finally { host.Exit(); }
        if (Api.pcm_native_abi()!=1 || Api.pcm_native_capacity()!=Capacity
                || Api.pcm_native_summary_bytes()!=SummaryBytes
                || Api.pcm_native_block_bytes()!=(badLayout ? BlockBytes-4 : BlockBytes))
            throw new InvalidOperationException("PCM native ABI/layout mismatch before polling");
        summaries=Marshal.AllocHGlobal(Capacity*SummaryBytes);
        block=Marshal.AllocHGlobal(BlockBytes); ordinals=Marshal.AllocHGlobal(8*4);
        Marshal.Copy(selected, 0, ordinals, selected.Length);
        Configure();
        uint before=Api.pcm_native_count();
        InvalidApiRejected=Api.pcm_native_configure(2,Capacity,ordinals,0)==-1
            && Api.pcm_native_configure(1,0,ordinals,0)==-1
            && Api.pcm_native_configure(1,Capacity+1,ordinals,0)==-1
            && Api.pcm_native_configure(1,Capacity,ordinals,9)==-1
            && Api.pcm_native_configure(1,Capacity,IntPtr.Zero,1)==-1
            && Api.pcm_native_poll(summaries,Capacity*SummaryBytes-4)==-1
            && Api.pcm_native_copy_block(8,block,BlockBytes)==-1
            && Api.pcm_native_copy_block(0,block,BlockBytes-4)==-1
            && Api.pcm_native_count()==before;
        // Invalid ordinal lists are rejected before clearing existing state.
        Marshal.WriteInt32(ordinals,0,0);
        InvalidApiRejected &= Api.pcm_native_configure(1,Capacity,ordinals,1)==-1;
        Marshal.WriteInt32(ordinals,0,2); Marshal.WriteInt32(ordinals,4,1);
        InvalidApiRejected &= Api.pcm_native_configure(1,Capacity,ordinals,2)==-1;
        Marshal.Copy(selected,0,ordinals,selected.Length);
        if (!InvalidApiRejected) throw new InvalidOperationException("Invalid PCM observation API accepted");
    }
    public void Configure()
    {
        if (Api.pcm_native_configure(enabled ? 1U : 0U,overflow ? 1U : Capacity,ordinals,(uint)selected.Length)!=0)
            throw new InvalidOperationException("PCM configure rejected");
        seen=0;
    }
    public byte[] Drain(string directory, string prefix)
    {
        uint count=Api.pcm_native_count();
        int actual=Api.pcm_native_poll(summaries,Capacity*SummaryBytes);
        if (actual<0 || actual!=count || actual>Capacity) throw new InvalidOperationException("PCM poll count mismatch");
        if (!overflow && (Api.pcm_native_fault()!=0 || Api.pcm_native_dropped()!=0))
            throw new InvalidOperationException("PCM observer overflow/fault: evidence incomplete");
        if (!enabled && actual!=0) throw new InvalidOperationException("Disabled PCM observer emitted data");
        byte[] data=new byte[actual*SummaryBytes]; Marshal.Copy(summaries,data,0,data.Length);
        uint ready=Api.pcm_native_ready();
        if ((ready >> selected.Length)!=0) throw new InvalidOperationException("Unexpected PCM fixture slot");
        for (int i=0;i<selected.Length;i++) if ((ready & ~seen & (1U<<i))!=0) {
            if (Api.pcm_native_copy_block((uint)i,block,BlockBytes)!=1) throw new InvalidOperationException("PCM fixture missing");
            byte[] bytes=new byte[BlockBytes]; Marshal.Copy(block,bytes,0,bytes.Length);
            File.WriteAllBytes(Path.Combine(directory,prefix+"block-"+selected[i].ToString("D8")+".bin"),bytes);
        }
        seen=ready;
        return data;
    }
    public void Dispose()
    {
        Api.pcm_native_configure(0,Capacity,IntPtr.Zero,0);
        Marshal.FreeHGlobal(summaries); Marshal.FreeHGlobal(block); Marshal.FreeHGlobal(ordinals);
    }
}
internal sealed class PcmInput { public int first, last; public string button; }
internal sealed class PcmPad : IController
{
    public ControllerDefinition Definition { get; set; }
    public HashSet<string> Buttons=new HashSet<string>();
    public bool IsPressed(string name) { return name.StartsWith("P1 ",StringComparison.Ordinal) && Buttons.Contains(name.Substring(3)); }
    public int AxisValue(string name) { return 0; }
    public IReadOnlyCollection<(string Name,int Strength)> GetHapticsSnapshot() { return new (string Name,int Strength)[0]; }
    public void SetHapticChannelStrength(string name,int strength) {}
}
internal static class PcmNative
{
    static string Hash(byte[] bytes) { return Probe.Hash(bytes,SHA256.Create()); }
    static object Advance(GPGX core,PcmPad pad,int frame,List<PcmInput> inputs)
    {
        pad.Buttons.Clear();
        foreach (var p in inputs.Where(p=>p.first<=frame && frame<=p.last)) pad.Buttons.Add(p.button);
        core.FrameAdvance(pad,true,true);
        short[] audio; int samples; core.GetSamplesSync(out audio,out samples);
        byte[] sound=new byte[samples*4]; Buffer.BlockCopy(audio,0,sound,0,sound.Length);
        byte[] pixels=new byte[core.GetVideoBuffer().Length*4]; Buffer.BlockCopy(core.GetVideoBuffer(),0,pixels,0,pixels.Length);
        return new {frame=core.Frame,buttons=pad.Buttons.OrderBy(n=>n,StringComparer.Ordinal),lag=core.IsLagFrame,
            audio_samples=samples,audio_sha256=Hash(sound),video_sha256=Hash(pixels)};
    }
    static object View(GPGX core,IMemoryDomains domains)
    {
        var hashes=new SortedDictionary<string,string>(StringComparer.Ordinal);
        foreach (var d in domains.Where(d=>!d.Name.EndsWith(" BUS",StringComparison.Ordinal) && !d.Name.StartsWith("Waterbox ",StringComparison.Ordinal))) {
            byte[] bytes=new byte[checked((int)d.Size)]; for(int i=0;i<bytes.Length;i++) bytes[i]=d.PeekByte(i);
            hashes.Add(d.Name,Hash(bytes));
        }
        return new {frame=core.Frame,lag_count=core.LagCount,domains=hashes,
            registers=core.GetCpuFlagsAndRegisters().OrderBy(p=>p.Key,StringComparer.Ordinal).ToDictionary(p=>p.Key,p=>p.Value.Value)};
    }
    static List<string> Continue(GPGX core,PcmPad pad,int from,List<PcmInput> inputs,PcmLedger ledger,string directory,string prefix)
    {
        if(ledger!=null) ledger.Configure();
        var rows=new List<string>();
        using(var stream=File.Create(Path.Combine(directory,prefix+"calls.bin")))
        for(int f=from+1;f<=from+120;f++) {
            rows.Add(JsonConvert.SerializeObject(Advance(core,pad,f,inputs)));
            if(ledger!=null) { byte[] b=ledger.Drain(directory,prefix); stream.Write(b,0,b.Length); }
        }
        return rows;
    }
    static void Picture(GPGX core,string file)
    {
        using(var f=File.Create(file)) {
            byte[] h=System.Text.Encoding.ASCII.GetBytes("P6\n"+core.BufferWidth+" "+core.BufferHeight+"\n255\n"); f.Write(h,0,h.Length);
            int[] pixels=core.GetVideoBuffer();
            for(int n=0;n<core.BufferWidth*core.BufferHeight;n++) { f.WriteByte((byte)(pixels[n]>>16)); f.WriteByte((byte)(pixels[n]>>8)); f.WriteByte((byte)pixels[n]); }
        }
    }
    public static int Main(string[] args)
    {
        try {
            if(args.Length!=10) throw new ArgumentException("cue bios-dir output frames input-json bios-sha1 core-sha256 mode selection-csv runtime-pins-json");
            string dll=Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"dll");
            var pins=JsonConvert.DeserializeObject<Dictionary<string,string>>(File.ReadAllText(args[9]));
            if(pins.Count!=8) throw new InvalidOperationException("Incomplete runtime pins");
            pins["gpgx.wbx.zst"]=args[6];
            foreach(var p in pins) if(Hash(File.ReadAllBytes(Path.Combine(dll,p.Key)))!=p.Value) throw new InvalidOperationException("PCM runtime identity mismatch: "+p.Key);
            if(typeof(GPGX).Assembly.GetName().Version!=new Version(2,11,0,0)) throw new InvalidOperationException("Require BizHawk 2.11");
            int count=int.Parse(args[3]); string mode=args[7];
            int[] selected=args[8].Split(new[]{','},StringSplitOptions.RemoveEmptyEntries).Select(int.Parse).ToArray();
            var inputs=JsonConvert.DeserializeObject<List<PcmInput>>(File.ReadAllText(args[4]));
            var allowed=new HashSet<string>(new[]{"Start","A","B","C","Up","Down","Left","Right"});
            if(count<1 || count+240>3600 || selected.Length>8 || selected.Any(n=>n<1 || n>10000000)
                || !selected.SequenceEqual(selected.OrderBy(n=>n).Distinct()) || inputs==null
                || inputs.Any(p=>p.first<1 || p.last<p.first || p.last>count || !allowed.Contains(p.button))
                || !new[]{"control","disabled","enabled","overflow","bad-layout"}.Contains(mode)) throw new ArgumentException("Invalid bounded PCM scenario");
            if(Directory.Exists(args[2])) throw new IOException("Output already exists");
            var fw=new Firmware(args[1],args[5]); var disc=BizHawk.Emulation.DiscSystem.Disc.LoadAutomagic(args[0]);
            using(var core=new GPGX(new CoreLoadParameters<GPGX.GPGXSettings,GPGX.GPGXSyncSettings>{
                Comm=new CoreComm(m=>Console.WriteLine(m),(m,n)=>{},fw,CoreComm.CorePreferencesFlags.None,null),
                Game=new GameInfo{Name="Sonic CD private PCM fixture",System="GEN"},Settings=new GPGX.GPGXSettings(),SyncSettings=new GPGX.GPGXSyncSettings(),
                Discs={new CdAsset{DiscData=disc,DiscName=Path.GetFileName(args[0])}},DeterministicEmulationRequested=true}))
            using(var ledger=mode=="control" ? null : new PcmLedger(core,mode!="disabled",mode=="overflow",selected,mode=="bad-layout")) {
                Directory.CreateDirectory(args[2]); var domains=core.ServiceProvider.GetService<IMemoryDomains>(); var pad=new PcmPad{Definition=core.ControllerDefinition};
                using(var av=new StreamWriter(Path.Combine(args[2],"av.jsonl")))
                using(var views=new StreamWriter(Path.Combine(args[2],"views.jsonl")))
                using(var calls=File.Create(Path.Combine(args[2],"calls.bin")))
                using(var counts=new StreamWriter(Path.Combine(args[2],"counts.jsonl")))
                for(int frame=1;frame<=count;frame++) {
                    av.WriteLine(JsonConvert.SerializeObject(Advance(core,pad,frame,inputs)));
                    if(ledger!=null) {byte[] b=ledger.Drain(args[2],""); calls.Write(b,0,b.Length);
                        counts.WriteLine(JsonConvert.SerializeObject(new{frame,calls=b.Length/PcmLedger.SummaryBytes,fault=ledger.Api.pcm_native_fault(),dropped=ledger.Api.pcm_native_dropped()}));}
                    if(frame%120==0 || frame==count) views.WriteLine(JsonConvert.SerializeObject(View(core,domains)));
                    if(inputs.Any(p=>p.button=="A" && frame==p.last+12)) Picture(core,Path.Combine(args[2],"after-A-"+frame.ToString("D5")+".ppm"));
                }
                Picture(core,Path.Combine(args[2],"final.ppm"));
                byte[] checkpoint=((IStatable)core).CloneSavestate(); File.WriteAllBytes(Path.Combine(args[2],"checkpoint.state"),checkpoint);
                uint fault=ledger==null?0:ledger.Api.pcm_native_fault(), dropped=ledger==null?0:ledger.Api.pcm_native_dropped();
                var checkpointView=View(core,domains);
                for(int i=0;ledger!=null && i<20;i++) {ledger.Drain(args[2],"audit-"); ledger.Configure();}
                bool unchanged=checkpoint.SequenceEqual(((IStatable)core).CloneSavestate());
                var forward=Continue(core,pad,count,inputs,ledger,args[2],"forward-"); string forwardView=JsonConvert.SerializeObject(View(core,domains));
                ((IStatable)core).LoadStateBinary(checkpoint); core.DiscardSamples(); GC.Collect(); GC.WaitForPendingFinalizers(); GC.Collect();
                var replay=Continue(core,pad,count,inputs,ledger,args[2],"replay-"); string replayView=JsonConvert.SerializeObject(View(core,domains));
                bool eventsEqual=File.ReadAllBytes(Path.Combine(args[2],"forward-calls.bin")).SequenceEqual(File.ReadAllBytes(Path.Combine(args[2],"replay-calls.bin")));
                string[] forwardBlocks=Directory.GetFiles(args[2],"forward-block-*.bin");
                foreach(string file in forwardBlocks) eventsEqual &= File.ReadAllBytes(file).SequenceEqual(File.ReadAllBytes(file.Replace("forward-block-","replay-block-")));
                bool restored=forward.SequenceEqual(replay) && forwardView==replayView && eventsEqual;
                File.WriteAllLines(Path.Combine(args[2],"continuation.jsonl"),replay);
                File.WriteAllText(Path.Combine(args[2],"summary.json"),JsonConvert.SerializeObject(new{
                    frames=count,mode,core_sha256=args[6],is_mega_cd=core.IsMegaCD,firmware=fw.Selected,inputs,selected,
                    fps_num=core.VsyncNumerator,fps_den=core.VsyncDenominator,settings=core.GetSettings(),sync_settings=core.GetSyncSettings(),
                    checkpoint_bytes=checkpoint.Length,checkpoint_sha256=Hash(checkpoint),checkpoint_view=checkpointView,final_view=JsonConvert.DeserializeObject(replayView),
                    restore_120_calls_equal=restored,observation_savestate_unchanged=unchanged,event_restore_equal=eventsEqual,
                    invalid_abi_calls_rejected=ledger==null?(bool?)null:ledger.InvalidApiRejected,overflow_fault=fault,overflow_dropped=dropped,
                    total_frame_advance_calls=count+240,display=(string)null,audio_device=(string)null},Formatting.Indented));
                if(!unchanged || !restored) throw new InvalidOperationException("PCM control/restore failed");
                if(mode=="overflow" && (fault!=1 || dropped==0)) throw new InvalidOperationException("PCM overflow not exercised");
            }
            return 0;
        } catch(Exception e) {Console.Error.WriteLine(e); return 1;}
    }
}
