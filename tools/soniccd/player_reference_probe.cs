// Bounded native R11A post-frame player reference, 2026-10-24 player-reference-r1.
// Official BizHawk 2.11 public APIs; source-derived external layout and controller schedule.
using System;
using System.IO;
using System.Linq;
using System.Collections.Generic;
using System.Security.Cryptography;
using BizHawk.Emulation.Common;
using BizHawk.Emulation.Cores;
using BizHawk.Emulation.Cores.Consoles.Sega.gpgx;
using BizHawk.Emulation.DiscSystem;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

internal sealed class ReferencePad : IController
{
    public ControllerDefinition Definition { get; set; }
    public HashSet<string> Buttons = new HashSet<string>();
    public bool IsPressed(string name) { return Buttons.Contains(name); }
    public int AxisValue(string name) { return 0; }
    public IReadOnlyCollection<(string Name, int Strength)> GetHapticsSnapshot() { return new (string Name, int Strength)[0]; }
    public void SetHapticChannelStrength(string name,int strength) { }
}
internal static class PlayerReference
{
    static JObject layout, scenario;
    static IMemoryDomains domains;
    static MemoryDomain ram, word, vram;
    static int calls;
    static bool observed;
    static string H(byte[] b) { return Probe.Hash(b,SHA256.Create()); }
    static byte[] Read(MemoryDomain d,int at,int count)
    {
        if (at < 0 || count < 0 || at+count > d.Size) throw new InvalidOperationException("Out-of-domain read");
        var b=new byte[count];for(int i=0;i<count;i++)b[i]=d.PeekByte(at+i);return b;
    }
    static string Hex(byte[] b) { return BitConverter.ToString(b).Replace("-", "").ToLowerInvariant(); }
    static long U(byte[] b,int at,int n)
    { long x=0;for(int i=0;i<n;i++)x=(x<<8)|b[at+i];return x; }
    static int O(string name) { return (int)layout["object_fields"][name]; }
    static int SignedWord(long n) { return unchecked((short)n); }
    static object Poll(GPGX core,int frame)
    {
        var hashes=new Dictionary<string,string>();
        bool valid=true;
        foreach(var w in layout["windows"])
        {
            string hash=H(Read(word,(int)w["offset"],(int)w["length"]));
            hashes[(string)w["name"]]=hash;
            if(hash!=(string)w["sha256"])valid=false;
        }
        if(!valid)return new { frame, phase=(string)layout["phase"], program="unknown_or_not_R11A", accepted=false };
        var globals=new Dictionary<string,long>();
        foreach(var p in ((JObject)layout["globals"]).Properties())
            globals[p.Name]=U(Read(ram,(int)p.Value["offset"],(int)p.Value["width"]),0,(int)p.Value["width"]);
        byte[] b=Read(ram,(int)layout["player_offset"],64);
        bool actor=b[O("oID")]==1 && U(b,O("oMap"),4)==(long)layout["map_pointer"];
        var fields=new Dictionary<string,long>();
        foreach(var p in ((JObject)layout["object_fields"]).Properties())
        {
            int n=(new[]{"oTile","oX","oXSub","oY","oYSub","oXVel","oYVel","oPlayerGVel","oPlayerMoveLock"}).Contains(p.Name)?2:p.Name=="oMap"?4:1;
            fields[p.Name]=U(b,(int)p.Value,n);
        }
        bool active=actor && fields["oRoutine"]==2 && globals["paused"]==0 && globals["demoMode"]==0 && globals["debugMode"]==0 && globals["ctrlLocked"]==0;
        byte[] cpu=Read(ram,(int)layout["art_buffer_offset"],(int)layout["art_buffer_bytes"]);
        byte[] resident=Read(vram,(int)layout["resident_vram_offset"],(int)layout["resident_vram_bytes"]);
        return new { frame, phase=(string)layout["phase"], program="USA_R11A_source_and_loaded_windows_matched",
            accepted=actor, active_player=active, identity_window_sha256=hashes, globals,
            actor=actor?"objPlayerSlot_ID1_source_ObjectIndex_ObjSonic_mapping_matched":"unverified_slot",
            player_raw_hex=Hex(b), fields,
            centre_x=SignedWord(fields["oX"]), centre_y=SignedWord(fields["oY"]),
            x_16_16_unsigned=U(b,O("oX"),4),y_16_16_unsigned=U(b,O("oY"),4),
            x_velocity_s16=SignedWord(fields["oXVel"]),y_velocity_s16=SignedWord(fields["oYVel"]),
            inertia_s16=SignedWord(fields["oPlayerGVel"]),animation_timer_s8=unchecked((sbyte)fields["oAnimTime"]),
            art=new { cpu_buffer_sha256=H(cpu),resident_vram_sha256=H(resident),
                cpu_prefix_hex=Hex(cpu.Take(32).ToArray()),resident_prefix_hex=Hex(resident.Take(32).ToArray()),
                relation="postframe_residency_only; last_frame_and_dirty_are_native_globals; DMA_instruction_phase_unknown" },
            cpu_registers=core.GetCpuFlagsAndRegisters().ToDictionary(p=>p.Key,p=>p.Value.Value) };
    }
    static string VideoHash(GPGX c)
    { byte[] b=new byte[c.GetVideoBuffer().Length*4];Buffer.BlockCopy(c.GetVideoBuffer(),0,b,0,b.Length);return H(b); }
    static object Advance(GPGX c,ReferencePad pad,int f)
    {
        if(++calls>6000)throw new InvalidOperationException("6000-call process bound exceeded");
        pad.Buttons.Clear();
        foreach(var x in scenario["inputs"])
            if(f>=(int)x["first"] && f<=(int)x["last"])pad.Buttons.Add("P1 "+(string)x["button"]);
        c.FrameAdvance(pad,true,true);
        short[] audio;int count;c.GetSamplesSync(out audio,out count);
        byte[] bytes=new byte[count*4];Buffer.BlockCopy(audio,0,bytes,0,bytes.Length);
        return new { frame=c.Frame,input=pad.Buttons.OrderBy(x=>x).ToArray(),lag=c.IsLagFrame,
            audio_samples=count,audio_sha256=H(bytes),video_sha256=VideoHash(c),width=c.BufferWidth,height=c.BufferHeight };
    }
    static void Ppm(GPGX c,string file)
    {
        using(var s=File.Create(file))
        {
            byte[] h=System.Text.Encoding.ASCII.GetBytes("P6\n"+c.BufferWidth+" "+c.BufferHeight+"\n255\n");s.Write(h,0,h.Length);
            var pixels=c.GetVideoBuffer();
            for(int i=0;i<c.BufferWidth*c.BufferHeight;i++)
            {s.WriteByte((byte)(pixels[i]>>16));s.WriteByte((byte)(pixels[i]>>8));s.WriteByte((byte)pixels[i]);}
        }
    }
    static string Continuation(GPGX c,ReferencePad pad,int from,string output)
    {
        using(var stream=new StreamWriter(output))
            for(int i=from+1;i<=from+120;i++)
            {
                var av=Advance(c,pad,i);
                stream.WriteLine(JsonConvert.SerializeObject(new { av,player=observed?Poll(c,i):null }));
            }
        return H(File.ReadAllBytes(output));
    }
    public static int Main(string[] args)
    {
        try
        {
            return Run(args);
        }
        catch(Exception ex){Console.Error.WriteLine(ex);return 1;}
    }
    public static int Run(string[] args)
    {
        if(args.Length!=7)throw new ArgumentException("cue firmware output layout scenario bios mode expected");
        layout=JObject.Parse(File.ReadAllText(args[3]));scenario=JObject.Parse(File.ReadAllText(args[4]));
        int frames=(int)scenario["frames"];if(frames<1 || frames+240>6000)throw new ArgumentException("Bound exceeds 6000 calls");
        observed=args[6]!="control";
        string output=Path.GetFullPath(args[2]);if(Directory.Exists(output))throw new IOException("Output exists");
        if(typeof(GPGX).Assembly.GetName().Version!=new Version(2,11,0,0))throw new InvalidOperationException("BizHawk 2.11 required");
        Directory.CreateDirectory(output);
        var fw=new Firmware(args[1],args[5]);var disc=Disc.LoadAutomagic(args[0]);
        using(var c=new GPGX(new CoreLoadParameters<GPGX.GPGXSettings,GPGX.GPGXSyncSettings>{
            Comm=new CoreComm(m=>Console.WriteLine(m),(m,n)=>{},fw,CoreComm.CorePreferencesFlags.None,null),
            Game=new GameInfo{Name="Sonic CD bounded player comparison",System="GEN"},
            Settings=new GPGX.GPGXSettings(),SyncSettings=new GPGX.GPGXSyncSettings(),
            Discs={new CdAsset{DiscData=disc,DiscName=Path.GetFileName(args[0])}},DeterministicEmulationRequested=true}))
        {
            domains=c.ServiceProvider.GetService<IMemoryDomains>();
            ram=domains["68K RAM"];word=domains["CD WORD RAM (2M)"];vram=domains["VRAM"];
            if(ram.Size!=65536 || word.Size!=262144 || ram.EndianType!=MemoryDomain.Endian.Big || word.EndianType!=MemoryDomain.Endian.Big)
                throw new InvalidOperationException("Unsupported memory domains/endian");
            var pad=new ReferencePad{Definition=c.ControllerDefinition};
            var shots=new HashSet<int>(scenario["screenshots"].Select(t=>(int)t));
            using(var av=new StreamWriter(Path.Combine(output,"av.jsonl")))
            using(var rows=observed?new StreamWriter(Path.Combine(output,"player.jsonl")):null)
                for(int f=1;f<=frames;f++)
                {
                    av.WriteLine(JsonConvert.SerializeObject(Advance(c,pad,f)));
                    if(observed)rows.WriteLine(JsonConvert.SerializeObject(Poll(c,f)));
                    if(shots.Contains(f))Ppm(c,Path.Combine(output,"postframe-"+f.ToString("D5")+".ppm"));
                }
            var checkpoint=((IStatable)c).CloneSavestate();
            object checkpointPoll=observed?Poll(c,frames):null;
            if(observed)for(int i=0;i<10;i++)Poll(c,frames);
            var afterPoll=((IStatable)c).CloneSavestate();
            bool unchanged=checkpoint.SequenceEqual(afterPoll);
            if(!unchanged)throw new InvalidOperationException("Read-only polling changed raw state");
            File.WriteAllBytes(Path.Combine(output,"checkpoint.state"),checkpoint);
            string forward=Continuation(c,pad,frames,Path.Combine(output,"forward.jsonl"));
            byte[] forwardEnd=((IStatable)c).CloneSavestate();
            ((IStatable)c).LoadStateBinary(checkpoint);c.DiscardSamples();
            string replay=Continuation(c,pad,frames,Path.Combine(output,"replay.jsonl"));
            byte[] replayEnd=((IStatable)c).CloneSavestate();
            bool equal=forward==replay;
            if(!equal)throw new InvalidOperationException("120-call AV/player restore mismatch");
            bool rawEqual=forwardEnd.SequenceEqual(replayEnd);
            var diffs=new List<int>();
            for(int i=0;i<Math.Min(forwardEnd.Length,replayEnd.Length);i++)if(forwardEnd[i]!=replayEnd[i])diffs.Add(i);
            if(!rawEqual){File.WriteAllBytes(Path.Combine(output,"forward-end.state"),forwardEnd);File.WriteAllBytes(Path.Combine(output,"replay-end.state"),replayEnd);}
            var summary=new {schema="soniccd-player-postframe-run-v1",mode=args[6],frames,total_frameadvance_calls=calls,
                core_frame_at_end=c.Frame,checkpoint_poll=checkpointPoll,observation_savestate_unchanged=unchanged,
                checkpoint_bytes=checkpoint.Length,checkpoint_sha256=H(checkpoint),continuation_sha256=forward,
                final_state_sha256=H(forwardEnd),replay_final_state_sha256=H(replayEnd),
                raw_final_states_equal=rawEqual,raw_final_different_bytes=diffs.Count,
                raw_final_different_offsets_first_64=diffs.Take(64),
                restore_120_equal=equal,firmware=fw.Selected,
                deterministic=c.DeterministicEmulation,is_mega_cd=c.IsMegaCD,
                refresh_numerator=c.VsyncNumerator,refresh_denominator=c.VsyncDenominator,
                settings=c.GetSettings(),sync_settings=c.GetSyncSettings(),
                domains=domains.Select(d=>new{name=d.Name,bytes=d.Size,endian=d.EndianType.ToString()}),
                phase=(string)layout["phase"],exact_instruction_phase=(string)null,display=(string)null,audio_device=(string)null};
            File.WriteAllText(Path.Combine(output,"summary.json"),JsonConvert.SerializeObject(summary,Formatting.Indented));
        }
        return 0;
    }
}
