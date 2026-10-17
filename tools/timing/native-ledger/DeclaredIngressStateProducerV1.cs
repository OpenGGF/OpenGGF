// ---------------------------------------------------------------------------------------------------------------------
// DeclaredIngressStateProducerV1 - promoted diagnostic probe source (OpenGGF tools/timing/native-ledger).
// Origin: kos46 S3K ending Kos CPU-budget research, native-stall-counters lane (Claude Opus 5.5), 2026-10-17; research
//   source sha256 68199f78b4fb3a98b36e74e56cd0f39536875057d38978abd5600c6b9c73cfea. Promoted 2026-10-17 at OpenGGF base
//   00f8ab78a; promotion changed only this header and the disassembly line citations in the lineage notes below
//   (converted from the local skdisasm checkout 1a454a0 to the superproject pin 044fa46; ROM addresses are identical).
// Purpose: produce the declared-setup ingress savestate (ingress.bin + ingress-provenance.json) that the TraceChaser
//   `--m68k-ledger-window` runner replays, and optionally the v8 read-only observation files. Output is comparison
//   evidence for ledger_closure.py only; it is not a trace fixture and never runtime input.
// Inputs (10 args): ROM, full-sync BK2 movie, new output directory (must not exist), Player_mode, progress (-1/0/1),
//   ntsc|pal, observation window start level frame, stall-on|stall-off|stall-absent, the literal `ingress`, ingress LF.
// Build target: NOT the TraceChaser pinned by OpenGGF 00f8ab78a (33c05c16). It compiles against a BizHawk.Headless.Gpgx.exe
//   built from the TraceChaser native stall-counter branch (feature/ai-s3k-m68k-stall-counters; it needs the stall ABI v1
//   binding `GpgxM68kStallNative`, native patch 0004; the research build used the ABI v2 host of that branch), plus BizHawk 2.11 BizHawk.Emulation.Cores.dll / .Common.dll and netstandard, e.g.
//   `mono csc.exe /out:Producer.exe /reference:BizHawk.Headless.Gpgx.exe /reference:<BIZHAWK_HOME>/dll/BizHawk.Emulation.Cores.dll
//   /reference:<BIZHAWK_HOME>/dll/BizHawk.Emulation.Common.dll /reference:<netstandard.dll> DeclaredIngressStateProducerV1.cs`.
// Why it lives here and not in TraceChaser: it writes RAM for the declared ending setup and reflects into private
//   GpgxHost members (AdvanceDiagnosticAudio, DrainDiagnosticAudio, CloneSavestate, memoryCallbacks for an all-address
//   execute histogram). TraceChaser's observer policy admits only fixed, address-filtered observers, and the producer
//   promotion proposal keeps ingress production consumer-side. Placement is an open decision for the user/lead.
// ---------------------------------------------------------------------------------------------------------------------
// DeclaredIngressStateProducer v1 (native-stall-counters lane 2026-10-17): consumer-side, NOT TraceChaser. v8 unchanged
// (declared S3K ending setup: Title selection writes, fixed-zero fence) plus a ninth/tenth argument `ingress LF`: at the
// first frame boundary after the fence where Level_frame_counter >= LF it saves the savestate (ingress.bin) and
// ingress-provenance.json (ingress_kind "declared_setup", ROM/movie/core/state SHA-256, mode, progress, region, frame)
// and exits. The TraceChaser --m68k-ledger-window runner consumes that state; it never performs these writes itself.
// v8 (Opus native-stall-counters lane 2026-10-16): v7c unchanged plus (a) a required eighth argument stall-on|stall-off|stall-absent
// (stall-absent skips binding, for cores without 0004),
// (b) GpgxM68kStallNative (stall ABI v1, native patch 0004) bound in BOTH modes and enabled only for stall-on, with
// read-only stamps at VInt $584 (nop, first ROM instruction after the V_int_jump RAM jmp), Process_Kos_Queue $1C1A and the
// LevelLoop Level_frame_counter increment $6520 (addq.w #1,$FE04.w, before it executes) for level frames
// windowStart-4..windowStart+20 written to stall-stamps.csv (stall-on only; the same callbacks are registered in both modes),
// (c) every frame advanced with sound rendering (GpgxHost.AdvanceDiagnosticAudio) and the drained 44.1 kHz stereo output
// hashed over the whole run and dumped raw for frames whose level frame lies in the stamp range (audio-window.s16),
// (d) savestates cloned between frames at the first frame boundary after windowStart+20 and at the end. Hook opcodes are
// byte-checked against the ROM before registration. No reads or writes beyond v7c and the counters.
// v7c (Opus KOS-BUDGET-MEASUREMENT lane 2026-10-15): v7p with the observation window start as a seventh argument (default 4400),
// because the non-Hyper Sonic products submit the same reunion Kos jobs on other level frames (Sonic/0 at LF 3713).
// Kos window = start..start+16, histogram/DMA window = start..start+6. Observation-window selection only; no behaviour change.
// v7p (Opus KOS-BUDGET-MEASUREMENT lane PAL 2026-10-15): v7 with only the terminal prerequisite split and made region-aware.
// v7 PAL Tails/+2 reached polls=6144 but failed the combined check because Graphics_flags bit 6 (PAL system) is set by design.
// v7 (Opus KOS-BUDGET-MEASUREMENT lane M3/PAL 2026-10-15): v6 unchanged plus (a) optional sixth argument `pal` that sets only
// the in-memory SyncSettings.Region to Europe (the movie file is unchanged; `ntsc` keeps the movie's Autodetect region), and
// (b) read-only attribution for level frames 4400..4406: an all-address M68K execute histogram (PC -> count) keyed by the level
// frame whose LevelLoop body/Kos tail/VInt it belongs to (window k runs from the $6520 addq of frame k-1 to that of frame k),
// written to frame-pc-histogram.csv, and the DMA queue (DMA_queue $FB00, 7 words/entry, sonic3k.asm 1770-1790) at each
// Process_DMA_Queue $1588 entry written to frame-dma.csv. No writes beyond the unchanged v5 declared setup and fence.
// v6 (Opus KOS-BUDGET-MEASUREMENT lane M1 2026-10-15): v5 unchanged (branches.csv must reproduce v5 byte-for-byte as a
// non-perturbation control) plus read-only Kos service observations in level frames 4400..4416 written to kos-service.csv
// and kos-pc-counts.csv. Hooks (sonic3k.lst, opcode-verified by the runner and below): Process_Kos_Queue $1C1A (sonic3k.asm 2872),
// Set_Kos_Bookmark $1BF0 (sonic3k.asm 2851; reads the stacked rte PC at $42(sp) exactly as the ROM does), Process_Kos_Queue_EndReached
// $1CD2 (sonic3k.asm 2970; live A0/A1 final cursors), Backup_Kos_Registers rts $1D16 (sonic3k.asm 2999; stored registers just written),
// LevelLoop return $651C (sonic3k.asm 7920) and post-Wait_VSync $6520 (sonic3k.asm 7921). Per-instruction execute counts over
// $1C1A..$1D22 (Process_Kos_Queue through Wait_VSync) are attached once at LF>=4398 and counted per service window.
// RAM read: Kos_decomp_queue_count $FF0E, Kos_decomp_stored_registers $FF10-$FF37 (d0-d6,a0 $FF2C,a1 $FF30,a2 $FF34),
// Kos_decomp_stored_SR $FF38, Kos_decomp_bookmark $FF3A, Kos_description_field $FF3E, Kos_decomp_queue $FF40-$FF5F
// (sonic3k.constants.asm 918-925). No writes beyond the unchanged v5 declared setup and fence.
// v5 (Opus ISLANDLIFT lane 2026-10-15): v4 shadowed `mode` with the F600 game-mode byte inside the title callback,
// so Player_option received $4C and the fence failed; renamed. Adds Kos_decomp_queue_count ($FF0E), Kos_module_source ($FF64),
// Queue_Kos_Module ($1AD2) A1 rows and every-frame rows while fg is 32..52. Read-only after the fence.
// v4 (Opus ENDING lane 2026-10-15; v3 fence diagnostics; v2 hard-coded Player_mode2): parameterized mode/progress like EndingSceneSpotProbe; adds
// Camera_Y_pos/_copy, credits _unkFA86/_unkF660 and Events_bg+8 fade timer. Read-only after the fence.
// Temporary CPU-owner diagnostic. Frozen native-cloud-transition-observation-spec-v1.json.
// Uses the prior natural Title Start/fixed-zero setup; no reference reads or writes after setup.
using System;
using System.IO;
using System.Collections.Generic;
using OpenGGF.BizHawk.Headless;
class KosFlush:IDisposable{readonly StreamWriter a,b;public KosFlush(StreamWriter a,StreamWriter b){this.a=a;this.b=b;}public void Dispose(){a.Dispose();b.Dispose();}}
class HistScope:IDisposable{readonly Action a;public HistScope(Action a){this.a=a;}public void Dispose(){a();}}
class DeclaredIngressStateProducerV1 {
 static int W(GpgxHost h,int a){return h.ReadMainRamByte(a)<<8|h.ReadMainRamByte(a+1);}
 static uint L(GpgxHost h,int a){return (uint)W(h,a)<<16|(uint)W(h,a+2);}
 static void Word(IMainRamWriter h,int a,int v){h.WriteMainRamByte(a,(byte)(v>>8));h.WriteMainRamByte(a+1,(byte)v);}
 static void Long(IMainRamWriter h,int a,uint v){Word(h,a,(int)(v>>16));Word(h,a+2,(int)v);}
 static void Main(string[] args){
  if(args.Length!=10||args[8]!="ingress"||(args[5]!="ntsc"&&args[5]!="pal")||(args[7]!="stall-on"&&args[7]!="stall-off"&&args[7]!="stall-absent"))throw new ArgumentException("ROM full-sync-movie new-output mode progress ntsc|pal window-start-level-frame stall-on|stall-off|stall-absent required");
  int windowStart=int.Parse(args[6]);bool stallOn=args[7]=="stall-on";int ingressLf=int.Parse(args[9]);
  Func<string,string> sha=p=>{using(var s=System.Security.Cryptography.SHA256.Create())using(var f=File.OpenRead(p))return BitConverter.ToString(s.ComputeHash(f)).Replace("-","").ToLowerInvariant();};
  {byte[] rom=File.ReadAllBytes(args[0]);
   foreach(var pin in new[]{new object[]{0x584,"4e7148e7fffe"},new object[]{0x1C1A,"4a78ff0e670000da6b0000d8"},new object[]{0x6520,"5278fe04"}}){
    int at=(int)pin[0];string hex=(string)pin[1];var sb=new System.Text.StringBuilder();for(int i=0;i<hex.Length/2;i++)sb.Append(rom[at+i].ToString("x2"));
    if(sb.ToString()!=hex)throw new InvalidDataException("hook opcode mismatch at "+at.ToString("X"));}}
  int mode=int.Parse(args[3]),progress=int.Parse(args[4]);
  if(Directory.Exists(args[2]))throw new IOException("Refuse existing output");Directory.CreateDirectory(args[2]);
  var movie=Bk2Reader.Read(args[1]);
  if(args[5]=="pal")movie.SyncSettings.Region=BizHawk.Emulation.Cores.Consoles.Sega.gpgx.LibGPGX.Region.Europe;
  using(var h=GpgxHost.Open(args[0],movie.SyncSettings))using(var log=new StreamWriter(Path.Combine(args[2],"branches.csv"))){
   var stall=args[7]=="stall-absent"?null:GpgxM68kStallNative.Bind(h);
   var hostType=typeof(GpgxHost);var bf=System.Reflection.BindingFlags.NonPublic|System.Reflection.BindingFlags.Instance;
   var advanceAudio=hostType.GetMethod("AdvanceDiagnosticAudio",bf);var drainAudio=hostType.GetMethod("DrainDiagnosticAudio",bf);var cloneState=hostType.GetMethod("CloneSavestate",bf);
   if(advanceAudio==null||drainAudio==null||cloneState==null)throw new InvalidOperationException("pinned GpgxHost diagnostic members absent");
   var writer=(IMainRamWriter)h;bool selected=false,fenced=false,titleDispatched=false,titleReady=false,startSent=false,display=false;
   int process=0,polls=0,cloudCalls=0,afterAizCalls=0,priorFg=-1,priorBg=-1;bool aizSeen=false;uint extra=0;
   var registrations=new List<IDisposable>();
   Action<string> row=kind=>log.WriteLine("{0},{1},{2},{3},{4},{5},{6},{7},{8},{9:X8},{10:X8},{11:X8},{12},{13:X8},{14:X8},{15},{16},{17},{18:X4},{19:X8},{20:X8}",
    kind,h.CompletedFrame,polls-1,W(h,0xFE04),L(h,0xFE0C),process,W(h,0xEEC0),W(h,0xEEC2),h.ReadMainRamByte(0xFF60),L(h,0xA800),L(h,0xEE98),L(h,0xEE9C),afterAizCalls,L(h,0xEE7C),L(h,0xEE84),W(h,0xFA86),(short)W(h,0xF660),(short)W(h,0xEEDA),W(h,0xFF0E),L(h,0xFF64),extra);
   log.WriteLine("event,completed_frame,controller_ordinal,level_frame,vint,process,fg,bg,modules_left,hscroll_first_long,scroll_x,scroll_y,post_aiz_cloud_calls,camera_y,camera_y_copy,credits_fa86,credits_f660,fade_eeda,kos_decomp_queue_count,kos_module_source,a1");
   registrations.Add(h.RegisterExecuteCallback(0x4B6,()=>{
    if(selected)return;int gameMode=h.ReadMainRamByte(0xF600);if(gameMode==4){titleDispatched=true;return;}
    if(!titleDispatched||gameMode!=0x4C)return;
    if(!display||!startSent||W(h,0xFFD0)!=0||W(h,0xFFAE)!=0)throw new InvalidOperationException("Natural locked-on title prerequisite absent");
    Word(writer,0xFF0A,mode);writer.WriteMainRamByte(0xFFB0,(byte)(progress<0?0:7));writer.WriteMainRamByte(0xFFB1,(byte)(progress>0?7:0));
    for(int i=0;i<7;i++)writer.WriteMainRamByte(0xFFB2+i,(byte)(progress<0?0:progress==0?1:3));
    Word(writer,0xFE10,0xD01);Word(writer,0xEE4E,0xD01);writer.WriteMainRamByte(0xF600,0xC);selected=true;row("declared_selection");
   }));
   registrations.Add(h.RegisterExecuteCallback(0x3FB2,()=>{display=true;}));
   registrations.Add(h.RegisterExecuteCallback(0x41D4,()=>{if(!selected&&L(h,0xB094)!=0)titleReady=true;}));
   registrations.Add(h.RegisterExecuteCallback(0x1AADA,()=>{
    if(!selected)return;
    if(!fenced){int stack=(int)(h.ReadCpuRegister("M68K A7")&0xFFFF);if(L(h,stack)!=0x6484)return;
     if(W(h,0xFE10)!=0xD01||W(h,0xFE04)!=0||W(h,0xFF08)!=mode||W(h,0xFFD0)!=0||(h.ReadMainRamByte(0xF600)&0x7F)!=0xC)throw new InvalidOperationException("Specified initial fence absent FE10="+W(h,0xFE10).ToString("X")+" FE04="+W(h,0xFE04)+" FF08="+W(h,0xFF08)+" FFD0="+W(h,0xFFD0)+" F600="+h.ReadMainRamByte(0xF600).ToString("X")+" mode="+mode);
     Long(writer,0xFE0C,0);Long(writer,0xF636,0);fenced=true;row("fixed_zero_before_initial");}
    process++;
   }));
   registrations.Add(h.RegisterExecuteCallback(0x1AD2,()=>{if(!fenced)return;extra=(uint)h.ReadCpuRegister("M68K A1");row("queue_kos_module");extra=0;}));
   registrations.Add(h.RegisterExecuteCallback(0x10DE,()=>{if(fenced)polls++;}));
   foreach(var address in new[]{0x5AE0A,0x5AE76,0x5AFC4,0x5AFF0,0x5B03A,0x5B072}){
    uint owned=(uint)address;registrations.Add(h.RegisterExecuteCallback(owned,()=>{
     if(!fenced)return;
     if(owned==0x5AFC4)aizSeen=true;
     if(owned==0x5B072&&afterAizCalls>20)return;
     row("before_"+owned.ToString("X"));
    }));
   }
   registrations.Add(h.RegisterExecuteCallback(0x5B0A8,()=>{
    if(!fenced)return;cloudCalls++;if(aizSeen){afterAizCalls++;if(afterAizCalls<=20)row("cloud_before");}
   }));
   registrations.Add(h.RegisterExecuteCallback(0x658A,()=>{
    if(!fenced)return;int fg=W(h,0xEEC0),bg=W(h,0xEEC2);
    int lf=W(h,0xFE04);if(fg!=priorFg||bg!=priorBg||(fg>=32&&fg<=52)||(fg>=40&&lf%1==0&&W(h,0xFA86)<=4&&W(h,0xEE7C)<40)||polls==5120||polls==6144){row("prepared_after_events");priorFg=fg;priorBg=bg;}
   }));
   // ---- v6 read-only Kos service observer ----
   var kos=new StreamWriter(Path.Combine(args[2],"kos-service.csv"));var pcs=new StreamWriter(Path.Combine(args[2],"kos-pc-counts.csv"));
   kos.WriteLine("event,completed_frame,controller_ordinal,level_frame,vint,count,kos_source,kos_destination,st_d0,st_d1,st_d2,st_d3,st_d4,st_d5,st_d6,st_a0,st_a1,st_a2,st_sr,bookmark,desc_field,q1_src,q1_dst,q2_src,q2_dst,q3_src,q3_dst,x1,x2,x3");
   pcs.WriteLine("window_level_frame,window_vint,pc,count");
   int LfLo=windowStart,LfHi=windowStart+16;bool counting=false,countersAttached=false;int windowLf=-1;uint windowVint=0;
   var pcCount=new SortedDictionary<int,long>();
   Func<bool> inWindow=()=>{int lf=W(h,0xFE04);return fenced&&lf>=LfLo&&lf<=LfHi;};
   Action<string,uint,uint,uint> krow=(kind,x1,x2,x3)=>{
    var sb=new System.Text.StringBuilder();
    sb.AppendFormat("{0},{1},{2},{3},{4},{5:X4},{6:X8},{7:X8}",kind,h.CompletedFrame,polls-1,W(h,0xFE04),L(h,0xFE0C),W(h,0xFF0E),L(h,0xFF40),L(h,0xFF44));
    for(int r=0;r<10;r++)sb.AppendFormat(",{0:X8}",L(h,0xFF10+4*r));
    sb.AppendFormat(",{0:X4},{1:X8},{2:X4}",W(h,0xFF38),L(h,0xFF3A),W(h,0xFF3E));
    for(int q=1;q<4;q++)sb.AppendFormat(",{0:X8},{1:X8}",L(h,0xFF40+8*q),L(h,0xFF44+8*q));
    sb.AppendFormat(",{0:X8},{1:X8},{2:X8}",x1,x2,x3);kos.WriteLine(sb.ToString());};
   Func<uint> sp=()=>h.ReadCpuRegister("M68K A7");
   Func<uint,uint> stackLong=off=>{uint s=sp();if((s&0xFFFF0000)!=0xFFFF0000&&(s&0xFF000000)!=0xFF000000)throw new InvalidDataException("stack outside main RAM "+s.ToString("X"));return L(h,(int)((s+off)&0xFFFF));};
   // Process_Kos_Queue entry: x1=return address (caller), x2/x3 unused.
   registrations.Add(h.RegisterExecuteCallback(0x1C1A,()=>{if(!inWindow())return;counting=true;windowLf=W(h,0xFE04);windowVint=L(h,0xFE0C);pcCount.Clear();pcCount[0x1C1A]=1;krow("svc_entry",stackLong(0),0,0);}));
   // Set_Kos_Bookmark entry, only while the decompression-in-progress sign bit is set: x1=stacked rte PC $42(sp), x2=1 if inside
   // [Process_Kos_Queue_Main $1C26, Process_Kos_Queue_Done $1CFA) (the ROM's own bookmark range), x3=stacked SR word $40(sp).
   registrations.Add(h.RegisterExecuteCallback(0x1BF0,()=>{if(!inWindow())return;if((W(h,0xFF0E)&0x8000)==0)return;uint pc=stackLong(0x42);uint s=sp();
    krow("vint_busy",pc,(uint)(pc>=0x1C26&&pc<0x1CFA?1:0),(uint)W(h,(int)((s+0x40)&0xFFFF)));}));
   // EndReached: x1=live A0 (consumed source end), x2=live A1 (destination end).
   registrations.Add(h.RegisterExecuteCallback(0x1CD2,()=>{if(!inWindow())return;krow("end_reached",h.ReadCpuRegister("M68K A0"),h.ReadCpuRegister("M68K A1"),0);}));
   // Backup_Kos_Registers rts: stored registers now hold the interrupted state. x1=return address.
   registrations.Add(h.RegisterExecuteCallback(0x1D16,()=>{if(!inWindow())return;krow("backup_done",stackLong(0),0,0);}));
   // LevelLoop after jsr Process_Kos_Queue.
   registrations.Add(h.RegisterExecuteCallback(0x651C,()=>{if(!inWindow()||!counting)return;
    krow("svc_return",0,0,0);}));
   // After Wait_VSync returns (before Level_frame_counter increment): x1=Wait_VSync .loop ($1D1C) executions, x2=bne.s $1D20 executions,
   // x3=Hyper_Sonic_flash_timer $F666 (VInt_8 CPU CRAM-write gate, sonic3k.asm 725-746).
   registrations.Add(h.RegisterExecuteCallback(0x6520,()=>{
    if(fenced&&!countersAttached&&W(h,0xFE04)>=LfLo-2){countersAttached=true;
     foreach(int pc in new[]{0x1C1E,0x1C22,0x1C26,0x1C2C,0x1C30,0x1C34,0x1C38,0x1C3C,0x1C3E,0x1C40,0x1C42,0x1C44,0x1C46,0x1C4A,0x1C4E,0x1C50,0x1C52,0x1C54,0x1C56,0x1C58,0x1C5A,0x1C5C,0x1C5E,0x1C60,0x1C62,0x1C66,0x1C6A,0x1C6C,0x1C6E,0x1C70,0x1C72,0x1C74,0x1C76,0x1C7A,0x1C7E,0x1C80,0x1C82,0x1C84,0x1C86,0x1C88,0x1C8C,0x1C90,0x1C92,0x1C94,0x1C96,0x1C98,0x1C9A,0x1C9C,0x1C9E,0x1CA0,0x1CA2,0x1CA4,0x1CA6,0x1CA8,0x1CAA,0x1CAC,0x1CB0,0x1CB2,0x1CB4,0x1CB6,0x1CBA,0x1CBC,0x1CC0,0x1CC2,0x1CC4,0x1CC6,0x1CCA,0x1CCE,0x1CD0,0x1CD6,0x1CDA,0x1CE0,0x1CE4,0x1CE6,0x1CEA,0x1CEE,0x1CF0,0x1CF2,0x1CF4,0x1CF6,0x1CF8,0x1CFA,0x1CFC,0x1D02,0x1D06,0x1D0A,0x1D0C,0x1D10,0x1D18,0x1D1C,0x1D20,0x1D22}){
      int owned=pc;registrations.Add(h.RegisterExecuteCallback((uint)owned,()=>{if(!counting)return;long c;pcCount.TryGetValue(owned,out c);pcCount[owned]=c+1;}));}
    }
    if(!counting)return;counting=false;
    long loop,bne;pcCount.TryGetValue(0x1D1C,out loop);pcCount.TryGetValue(0x1D20,out bne);krow("vsync_released",(uint)loop,(uint)bne,h.ReadMainRamByte(0xF666));
    foreach(var kv in pcCount)pcs.WriteLine("{0},{1},{2:X4},{3}",windowLf,windowVint,kv.Key,kv.Value);
   }));
   registrations.Add(new KosFlush(kos,pcs));
   // ---- v7 attribution ----
   var histOut=new StreamWriter(Path.Combine(args[2],"frame-pc-histogram.csv"));var dmaOut=new StreamWriter(Path.Combine(args[2],"frame-dma.csv"));
   histOut.WriteLine("level_frame,pc,count");dmaOut.WriteLine("level_frame,vint,entries,total_words,entries_hex");
   int HLo=windowStart,HHi=windowStart+6;int histLf=-1;bool histAttached=false;var hist=new Dictionary<uint,long>();
   Action flushHist=()=>{if(histLf>=HLo&&histLf<=HHi){var keys=new List<uint>(hist.Keys);keys.Sort();foreach(var k in keys)histOut.WriteLine("{0},{1:X6},{2}",histLf,k,hist[k]);}hist.Clear();};
   BizHawk.Emulation.Common.MemoryCallbackDelegate allPc=(addr,val,flags)=>{if(histLf>=HLo&&histLf<=HHi){long c;hist.TryGetValue(addr,out c);hist[addr]=c+1;}return null;};
   var mcsField=typeof(GpgxHost).GetField("memoryCallbacks",System.Reflection.BindingFlags.NonPublic|System.Reflection.BindingFlags.Instance);
   var mcs=(BizHawk.Emulation.Common.IMemoryCallbackSystem)mcsField.GetValue(h);
   BizHawk.Emulation.Common.MemoryCallback allCb=null;
   registrations.Add(h.RegisterExecuteCallback(0x6520,()=>{
    if(!fenced)return;int next=W(h,0xFE04)+1;
    if(!histAttached&&next>=HLo-1){histAttached=true;allCb=new BizHawk.Emulation.Common.MemoryCallback("M68K BUS",BizHawk.Emulation.Common.MemoryCallbackType.Execute,"OpenGGF v7 read-only PC histogram",allPc,null,null);mcs.Add(allCb);}
    flushHist();histLf=next;
   }));
   registrations.Add(h.RegisterExecuteCallback(0x1588,()=>{
    int lf=W(h,0xFE04);if(!fenced||lf<HLo||lf>HHi)return;int n=0;long words=0;var sb=new System.Text.StringBuilder();
    for(int a=0xFB00;a<0xFBFC;a+=14){int w0=W(h,a);if(w0==0)break;n++;int len=(W(h,a+2)&0xFF)<<8|(W(h,a)&0xFF); /* $93lo then $94hi (Add_To_DMA_Queue sonic3k.asm 1723-1730) */words+=len;
     for(int k=0;k<14;k+=2)sb.AppendFormat("{0:X4}",W(h,a+k));sb.Append(' ');}
    dmaOut.WriteLine("{0},{1},{2},{3},{4}",lf,L(h,0xFE0C),n,words,sb.ToString().Trim());
   }));
   registrations.Add(new HistScope(()=>{flushHist();if(allCb!=null)mcs.Remove(allCb.Callback);histOut.Dispose();dmaOut.Dispose();}));
   // ---- v8 stall stamps ----
   int SLo=windowStart-4,SHi=windowStart+20;
   var stampOut=stallOn?new StreamWriter(Path.Combine(args[2],"stall-stamps.csv")):null;
   if(stampOut!=null)stampOut.WriteLine("event,level_frame,frame_ordinal,frame_base_mclk,m68k_cycles,z80_bus_mclk,fifo_wait_mclk,refresh_events,dma_freeze_mclk,z80_bus_access_count,fifo_wait_count,dma_freeze_count,completed_frame,vint");
   Action<string> stamp=kind=>{
    if(!fenced)return;int lf=W(h,0xFE04);if(lf<SLo||lf>SHi||!stallOn)return;var s=stall.Read();
    stampOut.WriteLine("{0},{1},{2},{3},{4},{5},{6},{7},{8},{9},{10},{11},{12},{13}",kind,lf,s.FrameOrdinal,s.FrameBaseMclk,s.M68kCycles,s.Z80BusMclk,s.FifoWaitMclk,s.RefreshEvents,s.DmaFreezeMclk,s.Z80BusAccessCount,s.FifoWaitCount,s.DmaFreezeCount,h.CompletedFrame,L(h,0xFE0C));};
   registrations.Add(h.RegisterExecuteCallback(0x584,()=>stamp("vint")));
   registrations.Add(h.RegisterExecuteCallback(0x1C1A,()=>stamp("kos")));
   registrations.Add(h.RegisterExecuteCallback(0x6520,()=>stamp("increment")));
   if(stampOut!=null)registrations.Add(new HistScope(()=>stampOut.Dispose()));
   var audioHash=System.Security.Cryptography.SHA256.Create();var audioWin=new FileStream(Path.Combine(args[2],"audio-window.s16"),FileMode.CreateNew);
   registrations.Add(new HistScope(()=>audioWin.Dispose()));long audioFrames=0;bool midSaved=false;
   Action<string> saveState=name=>{var bytes=(byte[])cloneState.Invoke(h,null);File.WriteAllBytes(Path.Combine(args[2],name),bytes);};
   if(stallOn)stall.Enable();
   try{
    for(int hardware=0;hardware<15000&&polls<6144;hardware++){
     h.ClearButtons();if(titleReady&&!startSent&&!selected){h.SetButton("P1 Start",true);startSent=true;row("ordinary_title_start");}
     advanceAudio.Invoke(h,null);
     var drainArgs=new object[]{0};var samples=(short[])drainAudio.Invoke(h,drainArgs);int stereo=(int)drainArgs[0];audioFrames+=stereo;
     var pcm=new byte[samples.Length*2];Buffer.BlockCopy(samples,0,pcm,0,pcm.Length);audioHash.TransformBlock(pcm,0,pcm.Length,null,0);
     int lfNow=W(h,0xFE04);if(fenced&&lfNow>=SLo&&lfNow<=SHi)audioWin.Write(pcm,0,pcm.Length);
     if(fenced&&!midSaved&&lfNow>SHi){midSaved=true;saveState("state-after-window.bin");}
     if(fenced&&lfNow>=ingressLf){
      saveState("ingress.bin");string home=Environment.GetEnvironmentVariable("BIZHAWK_HOME");
      string core=Path.Combine(home,"dll","gpgx.wbx.zst");if(!File.Exists(core))throw new FileNotFoundException(core);
      byte[] romB=File.ReadAllBytes(args[0]);string romSha1;using(var s1=System.Security.Cryptography.SHA1.Create())romSha1=BitConverter.ToString(s1.ComputeHash(romB)).Replace("-","").ToLowerInvariant();
      File.WriteAllText(Path.Combine(args[2],"ingress-provenance.json"),"{\n  \"schema\": \"openggf.declared-ingress-state.v1\",\n  \"ingress_kind\": \"declared_setup\",\n"
       +"  \"declared\": \"S3K ending research setup: Title-time writes $FF0A mode, $FFB0-$FFB8 progress, $FE10/$EE4E zone $0D01, $F600 game mode; fixed-zero fence $FE0C/$F636 at the first $1AADA\",\n"
       +"  \"producer\": \"DeclaredIngressStateProducer-v1.cs\",\n  \"mode\": "+mode+",\n  \"progress\": "+progress+",\n  \"region\": \""+args[5]+"\",\n"
       +"  \"frame\": "+h.CompletedFrame+",\n  \"level_frame\": "+lfNow+",\n  \"rom_sha1\": \""+romSha1+"\",\n  \"movie_sha256\": \""+sha(args[1])+"\",\n"
       +"  \"core_sha256\": \""+sha(core)+"\",\n  \"state_sha256\": \""+sha(Path.Combine(args[2],"ingress.bin"))+"\"\n}\n");
      log.WriteLine("INGRESS frame="+h.CompletedFrame+" level_frame="+lfNow);return;}
    }
    audioHash.TransformFinalBlock(new byte[0],0,0);
    File.WriteAllText(Path.Combine(args[2],"audio-run.sha256"),BitConverter.ToString(audioHash.Hash).Replace("-","").ToLowerInvariant()+" stereo_frames="+audioFrames+"\n");
    saveState("state-end.bin");
    if(!fenced||polls!=6144)throw new InvalidOperationException("Finite actual declared-progress owner prerequisite absent (fence/polls)");
    if((sbyte)h.ReadMainRamByte(0xFA88)!=progress)throw new InvalidOperationException("Finite actual declared-progress owner prerequisite absent (_unkFA88 progress)");
    // Graphics_flags $FFD8 bit 6 = PAL system (sonic3k.constants.asm 995): must match the requested region.
    if(((h.ReadMainRamByte(0xFFD8)&0x40)!=0)!=(args[5]=="pal"))throw new InvalidOperationException("Finite actual declared-progress owner prerequisite absent (Graphics_flags region)");
    if(!midSaved)throw new InvalidOperationException("stamp window never closed");
    log.WriteLine("DONE polls="+polls+" process="+process+" cloudCalls="+cloudCalls+" afterAizCalls="+afterAizCalls);
   }finally{foreach(var r in registrations)r.Dispose();}
  }
 }
}
