// Bounded native R11A Mosqui contact reference, 2026-10-24 player-contact-reference-opus-r2.
// Derived from the frozen R2 player_reference_probe.cs (SHA256 58231f8f...a967) via the R1 contact probe
// (SHA256 458e903b...e545): the same official BizHawk 2.11 public APIs (FrameAdvance, GetSamplesSync,
// IMemoryDomains.PeekByte batched under the domains' public BizHawk.Common.IMonitor, GetCpuFlagsAndRegisters,
// GetVideoBuffer, IStatable) and the same post-frame phase label and row format. R2 changes, per review
// player-contact-reference-opus-review.md: both end states are always retained as independent files (not only
// on inequality), and the observer-free control reports its player/contact/dynamic categories as
// "not_observed" instead of comparing null placeholders. No guest writes, callbacks, hooks, cheats or core changes.
using System;
using System.IO;
using System.Linq;
using System.Collections.Generic;
using System.Security.Cryptography;
using BizHawk.Common;
using BizHawk.Emulation.Common;
using BizHawk.Emulation.Cores;
using BizHawk.Emulation.Cores.Consoles.Sega.gpgx;
using BizHawk.Emulation.DiscSystem;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

internal sealed class ContactPad : IController
{
    public ControllerDefinition Definition { get; set; }
    public HashSet<string> Buttons = new HashSet<string>();
    public bool IsPressed(string name) { return Buttons.Contains(name); }
    public int AxisValue(string name) { return 0; }
    public IReadOnlyCollection<(string Name, int Strength)> GetHapticsSnapshot() { return new (string Name, int Strength)[0]; }
    public void SetHapticChannelStrength(string name,int strength) { }
}

internal sealed class DomainHold : IDisposable
{
    readonly List<IMonitor> held = new List<IMonitor>();
    public DomainHold(params MemoryDomain[] domains)
    {
        foreach(var d in domains){var m=(IMonitor)d;m.Enter();held.Add(m);}
    }
    public void Dispose(){for(int i=held.Count-1;i>=0;i--)held[i].Exit();}
}

internal sealed class StreamResult
{
    public string Rows, Av, Core, Contact, Dynamic, Screens;
}

internal static class ContactReference
{
    static JObject layout, scenario, contact;
    static IMemoryDomains domains;
    static MemoryDomain ram, word, vram;
    static int calls;
    static bool observed;
    static int dynOffset, dynCount, slotBytes, firstWhole, idOffset, routineOffset;
    static HashSet<byte> contactIds;
    static string H(byte[] b) { return Probe.Hash(b,SHA256.Create()); }
    static string HS(string s) { return H(System.Text.Encoding.UTF8.GetBytes(s)); }
    static byte[] Read(MemoryDomain d,int at,int count)
    {
        if (at < 0 || count < 0 || at+count > d.Size) throw new InvalidOperationException("Out-of-domain read");
        var b=new byte[count];for(int i=0;i<count;i++)b[i]=d.PeekByte(at+i);return b;
    }
    static string Hex(byte[] b) { return BitConverter.ToString(b).Replace("-", "").ToLowerInvariant(); }
    static string Hex(byte[] b,int at,int n) { return BitConverter.ToString(b,at,n).Replace("-", "").ToLowerInvariant(); }
    static long U(byte[] b,int at,int n)
    { long x=0;for(int i=0;i<n;i++)x=(x<<8)|b[at+i];return x; }
    static int O(string name) { return (int)layout["object_fields"][name]; }
    static int SignedWord(long n) { return unchecked((short)n); }

    // Returns null core-only rows for unknown programs, exactly like R2; dynamic continuity resets there.
    static JObject Poll(GPGX core,int frame,ref byte[] prevDyn)
    {
        using(new DomainHold(word,ram,vram))
        {
            var hashes=new Dictionary<string,string>();
            bool valid=true;
            foreach(var w in layout["windows"])
            {
                string hash=H(Read(word,(int)w["offset"],(int)w["length"]));
                hashes[(string)w["name"]]=hash;
                if(hash!=(string)w["sha256"])valid=false;
            }
            if(!valid){prevDyn=null;return JObject.FromObject(new { frame, phase=(string)layout["phase"], program="unknown_or_not_R11A", accepted=false });}
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
            var row=JObject.FromObject(new { frame, phase=(string)layout["phase"], program="USA_R11A_source_and_loaded_windows_matched",
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
                cpu_registers=core.GetCpuFlagsAndRegisters().ToDictionary(p=>p.Key,p=>p.Value.Value) });
            var cg=new JObject();var cgRaw=new JObject();
            foreach(var p in ((JObject)contact["globals"]).Properties())
            {
                int width=(int)p.Value["width"];byte[] raw=Read(ram,(int)p.Value["offset"],width);
                cg[p.Name]=U(raw,0,width);cgRaw[p.Name]=Hex(raw);
            }
            var pf=new JObject();
            foreach(var p in ((JObject)contact["player_fields"]).Properties())
                pf[p.Name]=U(b,(int)p.Value["offset"],(int)p.Value["width"]);
            byte[] dyn=Read(ram,dynOffset,dynCount*slotBytes);
            var ids=new byte[dynCount];var routines=new byte[dynCount];
            var slots=new JArray();var transitions=new JArray();
            for(int i=0;i<dynCount;i++)
            {
                int at=i*slotBytes;ids[i]=dyn[at+idOffset];routines[i]=dyn[at+routineOffset];
                if(contactIds.Contains(ids[i]))slots.Add(new JObject{{"slot",firstWhole+i},{"raw_hex",Hex(dyn,at,slotBytes)}});
                if(prevDyn!=null && (prevDyn[at+idOffset]!=ids[i] || prevDyn[at+routineOffset]!=routines[i]))
                    transitions.Add(new JObject{{"slot",firstWhole+i},{"before_hex",Hex(prevDyn,at,slotBytes)},{"after_hex",Hex(dyn,at,slotBytes)}});
            }
            var dynamic=new JObject{{"first_whole",firstWhole},{"ids_hex",Hex(ids)},{"routines_hex",Hex(routines)},
                {"region_sha256",H(dyn)},{"previous",prevDyn!=null?"previous_admitted_poll_in_this_stream":"none"},
                {"contact_slots",slots},{"transitions",transitions}};
            prevDyn=dyn;
            row["contact_globals"]=cg;row["contact_globals_raw_hex"]=cgRaw;row["contact_player_fields"]=pf;row["dynamic"]=dynamic;
            return row;
        }
    }
    static string Category(JObject row,string kind)
    {
        if(row==null)return "null";
        var o=(JObject)row.DeepClone();
        var keys=new[]{"contact_globals","contact_globals_raw_hex","contact_player_fields","dynamic"};
        if(kind=="core"){foreach(var k in keys)o.Remove(k);return o.ToString(Formatting.None);}
        if(kind=="dynamic")return o["dynamic"]==null?"none":o["dynamic"].ToString(Formatting.None);
        var c=new JObject();foreach(var k in keys.Take(3))if(o[k]!=null)c[k]=o[k];return c.ToString(Formatting.None);
    }
    static string VideoHash(GPGX c)
    { byte[] b=new byte[c.GetVideoBuffer().Length*4];Buffer.BlockCopy(c.GetVideoBuffer(),0,b,0,b.Length);return H(b); }
    static object Advance(GPGX c,ContactPad pad,int f,JToken inputs)
    {
        if(++calls>6000)throw new InvalidOperationException("6000-call process bound exceeded");
        pad.Buttons.Clear();
        foreach(var x in inputs)
            if(f>=(int)x["first"] && f<=(int)x["last"])pad.Buttons.Add("P1 "+(string)x["button"]);
        c.FrameAdvance(pad,true,true);
        short[] audio;int count;c.GetSamplesSync(out audio,out count);
        byte[] bytes=new byte[count*4];Buffer.BlockCopy(audio,0,bytes,0,bytes.Length);
        return new { frame=c.Frame,input=pad.Buttons.OrderBy(x=>x).ToArray(),lag=c.IsLagFrame,
            audio_samples=count,audio_sha256=H(bytes),video_sha256=VideoHash(c),width=c.BufferWidth,height=c.BufferHeight };
    }
    static string Ppm(GPGX c,string file)
    {
        using(var s=File.Create(file))
        {
            byte[] h=System.Text.Encoding.ASCII.GetBytes("P6\n"+c.BufferWidth+" "+c.BufferHeight+"\n255\n");s.Write(h,0,h.Length);
            var pixels=c.GetVideoBuffer();
            for(int i=0;i<c.BufferWidth*c.BufferHeight;i++)
            {s.WriteByte((byte)(pixels[i]>>16));s.WriteByte((byte)(pixels[i]>>8));s.WriteByte((byte)pixels[i]);}
        }
        return H(File.ReadAllBytes(file));
    }
    static StreamResult Stream(GPGX c,ContactPad pad,int from,int count,JToken inputs,HashSet<int> shots,string output,string prefix,ref byte[] prev)
    {
        var r=new StreamResult();var av=new List<string>();var core=new List<string>();var con=new List<string>();var dyn=new List<string>();var screens=new List<string>();
        using(var stream=new StreamWriter(output))
            for(int i=from+1;i<=from+count;i++)
            {
                var a=Advance(c,pad,i,inputs);
                JObject row=observed?Poll(c,i,ref prev):null;
                stream.WriteLine(JsonConvert.SerializeObject(new { av=a,player=row }));
                av.Add(JsonConvert.SerializeObject(a));core.Add(Category(row,"core"));con.Add(Category(row,"contact"));dyn.Add(Category(row,"dynamic"));
                if(shots.Contains(i))screens.Add(i+":"+Ppm(c,Path.Combine(Path.GetDirectoryName(output),prefix+"-postframe-"+i.ToString("D5")+".ppm")));
            }
        r.Rows=H(File.ReadAllBytes(output));r.Av=HS(string.Join("\n",av));r.Core=HS(string.Join("\n",core));
        r.Contact=HS(string.Join("\n",con));r.Dynamic=HS(string.Join("\n",dyn));r.Screens=HS(string.Join("\n",screens));
        return r;
    }
    static void Bind()
    {
        contact=(JObject)layout["contact"];
        var d=(JObject)contact["dynamic"];
        dynOffset=(int)d["offset"];dynCount=(int)d["count"];slotBytes=(int)d["slot_bytes"];firstWhole=(int)d["first_whole"];
        idOffset=(int)contact["slot_fields"]["oID"]["offset"];routineOffset=(int)contact["slot_fields"]["oRoutine"]["offset"];
        if(slotBytes!=64 || dynCount!=96 || idOffset!=0 || (int)contact["slot_fields"]["oID"]["width"]!=1 || (int)contact["slot_fields"]["oRoutine"]["width"]!=1)
            throw new InvalidOperationException("Unexpected source-bound dynamic layout");
        contactIds=new HashSet<byte>(((JObject)contact["ids"]).Properties().Select(p=>(byte)(int)p.Value));
    }
    public static int Main(string[] args)
    {
        try{return Run(args);}
        catch(Exception ex){Console.Error.WriteLine(ex);return 1;}
    }
    public static int Run(string[] args)
    {
        if(args.Length!=7)throw new ArgumentException("cue firmware output layout scenario bios mode expected");
        layout=JObject.Parse(File.ReadAllText(args[3]));scenario=JObject.Parse(File.ReadAllText(args[4]));Bind();
        string mode=args[6];bool discovery=mode=="discovery";
        if(!discovery && mode!="observed-1" && mode!="observed-2" && mode!="control")throw new ArgumentException("Unknown mode");
        int frames=(int)scenario["frames"];
        int planned=discovery?frames+scenario["branches"].Sum(b=>(int)b["frames"]):frames+240;
        if(frames<1 || planned>6000)throw new ArgumentException("Bound exceeds 6000 calls");
        observed=mode!="control";
        string output=Path.GetFullPath(args[2]);if(Directory.Exists(output))throw new IOException("Output exists");
        if(typeof(GPGX).Assembly.GetName().Version!=new Version(2,11,0,0))throw new InvalidOperationException("BizHawk 2.11 required");
        Directory.CreateDirectory(output);
        var fw=new Firmware(args[1],args[5]);var disc=Disc.LoadAutomagic(args[0]);
        using(var c=new GPGX(new CoreLoadParameters<GPGX.GPGXSettings,GPGX.GPGXSyncSettings>{
            Comm=new CoreComm(m=>Console.WriteLine(m),(m,n)=>{},fw,CoreComm.CorePreferencesFlags.None,null),
            Game=new GameInfo{Name="Sonic CD bounded contact comparison",System="GEN"},
            Settings=new GPGX.GPGXSettings(),SyncSettings=new GPGX.GPGXSyncSettings(),
            Discs={new CdAsset{DiscData=disc,DiscName=Path.GetFileName(args[0])}},DeterministicEmulationRequested=true}))
        {
            domains=c.ServiceProvider.GetService<IMemoryDomains>();
            ram=domains["68K RAM"];word=domains["CD WORD RAM (2M)"];vram=domains["VRAM"];
            if(ram.Size!=65536 || word.Size!=262144 || ram.EndianType!=MemoryDomain.Endian.Big || word.EndianType!=MemoryDomain.Endian.Big)
                throw new InvalidOperationException("Unsupported memory domains/endian");
            var pad=new ContactPad{Definition=c.ControllerDefinition};
            var shots=new HashSet<int>(scenario["screenshots"].Select(t=>(int)t));
            byte[] prev=null;
            using(var av=new StreamWriter(Path.Combine(output,"av.jsonl")))
            using(var rows=observed?new StreamWriter(Path.Combine(output,"player.jsonl")):null)
                for(int f=1;f<=frames;f++)
                {
                    av.WriteLine(JsonConvert.SerializeObject(Advance(c,pad,f,scenario["inputs"])));
                    if(observed)rows.WriteLine(Poll(c,f,ref prev).ToString(Formatting.None));
                    if(shots.Contains(f))Ppm(c,Path.Combine(output,"postframe-"+f.ToString("D5")+".ppm"));
                }
            var checkpoint=((IStatable)c).CloneSavestate();
            byte[] checkpointDyn=prev;JObject checkpointPoll=null;
            if(observed)
            {
                byte[] t=checkpointDyn;checkpointPoll=Poll(c,frames,ref t);
                for(int i=0;i<10;i++){t=checkpointDyn;Poll(c,frames,ref t);}
            }
            var afterPoll=((IStatable)c).CloneSavestate();
            bool unchanged=checkpoint.SequenceEqual(afterPoll);
            if(!unchanged)throw new InvalidOperationException("Read-only polling changed raw state");
            File.WriteAllBytes(Path.Combine(output,"checkpoint.state"),checkpoint);
            if(discovery)
            {
                var branches=new List<object>();
                foreach(var br in scenario["branches"])
                {
                    string name=(string)br["name"];int n=(int)br["frames"];
                    ((IStatable)c).LoadStateBinary(checkpoint);c.DiscardSamples();
                    byte[] p=checkpointDyn;
                    var bshots=new HashSet<int>(br["screenshots"].Select(t=>(int)t));
                    var res=Stream(c,pad,frames,n,br["inputs"],bshots,Path.Combine(output,"branch-"+name+".jsonl"),name,ref p);
                    branches.Add(new { name, frames=n, first_call=frames+1, last_call=frames+n, rows_sha256=res.Rows,
                        end_state_sha256=H(((IStatable)c).CloneSavestate()), core_frame_after=c.Frame });
                }
                var dsummary=new {schema="soniccd-contact-discovery-run-v1",mode,frames,total_frameadvance_calls=calls,
                    branch_point=frames,branches,checkpoint_poll=checkpointPoll,observation_savestate_unchanged=unchanged,
                    checkpoint_bytes=checkpoint.Length,checkpoint_sha256=H(checkpoint),monitor_batched_peekbyte=true,
                    firmware=fw.Selected,deterministic=c.DeterministicEmulation,is_mega_cd=c.IsMegaCD,
                    refresh_numerator=c.VsyncNumerator,refresh_denominator=c.VsyncDenominator,
                    settings=c.GetSettings(),sync_settings=c.GetSyncSettings(),
                    domains=domains.Select(d=>new{name=d.Name,bytes=d.Size,endian=d.EndianType.ToString()}),
                    phase=(string)layout["phase"],exact_instruction_phase=(string)null,display=(string)null,audio_device=(string)null,
                    restore_source="this_process_checkpoint_only"};
                File.WriteAllText(Path.Combine(output,"summary.json"),JsonConvert.SerializeObject(dsummary,Formatting.Indented));
                return 0;
            }
            var cshots=new HashSet<int>(scenario["continuation_screenshots"].Select(t=>(int)t));
            byte[] fp=checkpointDyn;
            var forward=Stream(c,pad,frames,120,scenario["inputs"],cshots,Path.Combine(output,"forward.jsonl"),"forward",ref fp);
            byte[] forwardEnd=((IStatable)c).CloneSavestate();
            ((IStatable)c).LoadStateBinary(checkpoint);c.DiscardSamples();
            byte[] rp=checkpointDyn;
            var replay=Stream(c,pad,frames,120,scenario["inputs"],cshots,Path.Combine(output,"replay.jsonl"),"replay",ref rp);
            byte[] replayEnd=((IStatable)c).CloneSavestate();
            // The control installs no observer, so player/contact/dynamic payloads are absent there: not_observed, never a pass.
            object Cat(bool same,bool observable){return observable?(object)same:"not_observed";}
            var categories=new Dictionary<string,object>{{"av",forward.Av==replay.Av},{"player_core",Cat(forward.Core==replay.Core,observed)},
                {"contact_globals_and_player_fields",Cat(forward.Contact==replay.Contact,observed)},{"dynamic_slots",Cat(forward.Dynamic==replay.Dynamic,observed)},
                {"continuation_screenshots",forward.Screens==replay.Screens},{"complete_rows",forward.Rows==replay.Rows}};
            bool equal=categories.Values.Where(x=>x is bool).All(x=>(bool)x);
            bool rawEqual=forwardEnd.Length==replayEnd.Length && forwardEnd.SequenceEqual(replayEnd);
            var diffs=new List<int>();
            for(int i=0;i<Math.Min(forwardEnd.Length,replayEnd.Length);i++)if(forwardEnd[i]!=replayEnd[i])diffs.Add(i);
            File.WriteAllBytes(Path.Combine(output,"forward-end.state"),forwardEnd);File.WriteAllBytes(Path.Combine(output,"replay-end.state"),replayEnd);
            var summary=new {schema="soniccd-contact-postframe-run-v2",mode,frames,total_frameadvance_calls=calls,
                core_frame_at_end=c.Frame,checkpoint_poll=checkpointPoll,observation_savestate_unchanged=unchanged,
                checkpoint_bytes=checkpoint.Length,checkpoint_sha256=H(checkpoint),continuation_sha256=forward.Rows,
                restore_category_equal=categories,
                forward_category_sha256=new{av=forward.Av,player_core=forward.Core,contact=forward.Contact,dynamic=forward.Dynamic,screens=forward.Screens},
                replay_category_sha256=new{av=replay.Av,player_core=replay.Core,contact=replay.Contact,dynamic=replay.Dynamic,screens=replay.Screens},
                final_state_sha256=H(forwardEnd),replay_final_state_sha256=H(replayEnd),
                final_state_bytes=forwardEnd.Length,replay_final_state_bytes=replayEnd.Length,
                raw_final_states_equal=rawEqual,raw_final_different_bytes=diffs.Count+Math.Abs(forwardEnd.Length-replayEnd.Length),
                raw_final_different_offsets_first_64=diffs.Take(64),raw_final_different_offsets_complete=diffs,
                raw_state_comparison="complete_unmasked_serialized_bytes",end_states_retained=true,
                observed_categories=categories.Where(x=>x.Value is bool).Select(x=>x.Key).ToArray(),
                restore_120_equal=equal,monitor_batched_peekbyte=true,firmware=fw.Selected,
                deterministic=c.DeterministicEmulation,is_mega_cd=c.IsMegaCD,
                refresh_numerator=c.VsyncNumerator,refresh_denominator=c.VsyncDenominator,
                settings=c.GetSettings(),sync_settings=c.GetSyncSettings(),
                domains=domains.Select(d=>new{name=d.Name,bytes=d.Size,endian=d.EndianType.ToString()}),
                phase=(string)layout["phase"],exact_instruction_phase=(string)null,display=(string)null,audio_device=(string)null};
            File.WriteAllText(Path.Combine(output,"summary.json"),JsonConvert.SerializeObject(summary,Formatting.Indented));
            return equal?0:3;
        }
    }
}
