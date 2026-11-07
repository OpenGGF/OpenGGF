-- Purpose: read-only two-player S2 RAM and native framebuffer observer.
-- Inputs: OGGF_NATIVE_PLAN/OUTPUT/FIXTURE_STATE and matching power-on BK2.
-- Origin: S2 Competition reference lane, 2026-11-07; no RAM writes.
local out=assert(os.getenv('OGGF_NATIVE_OUTPUT'))
local p=dofile(assert(os.getenv('OGGF_NATIVE_PLAN')))
local state=os.getenv('OGGF_NATIVE_FIXTURE_STATE')
client.speedmode(6400)
client.invisibleemulation(false)
if state then assert(savestate.load(state)); assert(emu.framecount()==p.state_frame) end
local fields={{'mode',0xf600,1},{'zoneact',0xfe10,2},{'two_player',0xffd8,2},{'title_option',0xff86,1},{'selection',0xff88,1},{'lfc',0xfe04,2},{'vint',0xfe0c,4},{'results',0xff10,2}}
for i,base in ipairs({0xb000,0xb040}) do
 for _,v in ipairs({{'x',8,2},{'y',12,2},{'vx',16,-2},{'vy',18,-2},{'status',34,1},{'anim',28,1},{'routine',36,1}}) do fields[#fields+1]={'p'..i..'_'..v[1],base+v[2],v[3]} end
end
for i,base in ipairs({0xee00,0xee20}) do
 for j,n in ipairs({'camx','camy','bgx','bgy'}) do fields[#fields+1]={'p'..i..'_'..n,base+(j-1)*4,2} end
end
for i,base in ipairs({0xfe12,0xfec6}) do fields[#fields+1]={'p'..i..'_lives',base,1} end
for i,base in ipairs({0xfe20,0xfed0}) do
 for _,v in ipairs({{'rings',0,2},{'time',2,4},{'score',6,4}}) do fields[#fields+1]={'p'..i..'_'..v[1],base+v[2],v[3]} end
end
for i=0,5 do fields[#fields+1]={'card'..i..'_id',0xb080+i*64,1};fields[#fields+1]={'card'..i..'_x',0xb088+i*64,2};fields[#fields+1]={'card'..i..'_routine',0xb0a4+i*64,1} end
for i=0,15 do fields[#fields+1]={'anim_counter'..i,0xf7f0+i,1} end
for _,v in ipairs({{'slot_inuse',0xff4c,2},{'slot_routine',0xff4e,1},{'slot1',0xff54,2},{'slot2',0xff58,2},{'slot3',0xff5c,2}}) do fields[#fields+1]=v end
local function read(a,n)
 if n==1 then return mainmemory.read_u8(a) elseif n==2 then return mainmemory.read_u16_be(a) elseif n==-2 then return mainmemory.read_s16_be(a) else return mainmemory.read_u32_be(a) end
end
local csv=assert(io.open(out..'/state.csv','w')); csv:write('frame')
for _,v in ipairs(fields) do csv:write(','..v[1]) end; csv:write('\n')
local want={}; for _,f in ipairs(p.frames or {}) do want[f]=true end
local captured={};local prev={}
local progress=assert(io.open(out..'/progress.txt','w'))
while emu.framecount()<p.last do
 emu.frameadvance(); local f=emu.framecount()
 csv:write(f); for _,v in ipairs(fields) do csv:write(','..read(v[2],v[3])) end; csv:write('\n')
 if f%100==0 then csv:flush();progress:write(f..'\n');progress:flush() end
 local event=false
 if p.events then
  for _,v in ipairs(fields) do
   local k=v[1];local now=read(v[2],v[3])
   if (k=='p1_rings' or k=='p2_rings' or k=='p1_lives' or k=='p2_lives') and prev[k] and now~=prev[k] and not captured[k] and f>1400 then event=true;captured[k]=f end
   if k=='mode' and prev[k] and now~=prev[k] and f>1400 then event=true end
   prev[k]=now
  end
 end
 if event or want[f] or (p.every and f%p.every==0) then client.screenshot(out..'/f'..f..'.png'); savestate.save(out..'/f'..f..'.State') end
end
csv:close();progress:close();local d=assert(io.open(out..'/done.txt','w'));d:write('ok');d:close();client.exit()
