-- LRZ3 encounter/event observer, originating 2026-09-22 S&K completion task.
-- Inputs: original BK2, verified ROM, native fixture state and Lua frame-window plan.
-- Outputs: event/player state.csv, native object graph objects.csv, screenshots.
-- Observation only: no RAM writes; these files must never hydrate engine gameplay.
local out=assert(os.getenv('OGGF_NATIVE_OUTPUT'))
local plan=dofile(assert(os.getenv('OGGF_NATIVE_PLAN')))
assert(savestate.load(assert(os.getenv('OGGF_NATIVE_FIXTURE_STATE'))))
assert(movie.isloaded() and emu.framecount()==plan.state_frame)
client.speedmode(6400); client.invisibleemulation(true)
local state=assert(io.open(out..'/state.csv','w'))
local objects=assert(io.open(out..'/objects.csv','w'))
local function b(a) return mainmemory.read_u8(a) end
local function w(a) return mainmemory.read_u16_be(a) end
local function l(a) return mainmemory.read_u32_be(a) end
state:write('frame,zone,px,py,pvx,pvy,pgv,pstatus,tx,ty,tgv,tstatus,camx,camxf,camy,camyf,special,fg,bg,fg4,fg5,bg00,bg02,bg08,bg0a,bg0c,bg0e,fa84,fa88,faa2,faa4,faa8,faa9,facd,fab8\n')
objects:write('frame,slot,code,map,x,xf,y,yf,vx,vy,frameid,routine,subtype,status,hits,flash,parent,link44,flags38,timer2e,timer3a,angle\n')
while emu.framecount()<plan.last do
 if plan.screenshots[emu.framecount()+1] then client.invisibleemulation(false) end
 emu.frameadvance()
 local f=emu.framecount()
 if f>=plan.first then
  local row={f,w(0xFE10),w(0xB010),w(0xB014),w(0xB018),w(0xB01A),w(0xB01C),b(0xB02A),w(0xB05A),w(0xB05E),w(0xB066),b(0xB074),w(0xEE78),w(0xEE7A),w(0xEE7C),w(0xEE7E),w(0xEEB2),w(0xEEC0),w(0xEEC2),w(0xEEC4),w(0xEEC6),w(0xEED2),w(0xEED4),w(0xEEDA),w(0xEEDC),w(0xEEDE),w(0xEEE0),w(0xFA84),w(0xFA88),w(0xFAA2),w(0xFAA4),b(0xFAA8),b(0xFAA9),b(0xFACD),b(0xFAB8)}
  state:write(table.concat(row,',')..'\n')
  for slot=2,129 do
   local a=0xB000+slot*0x4A; local code=l(a)&0xFFFFFF
   if (code>=0x78F50 and code<0x7A14C) or (code>=0x59FC4 and code<0x5A106) or (code>=0x86400 and code<0x86B64) then
    local r={f,slot,code,l(a+0xC)&0xFFFFFF,w(a+0x10),w(a+0x12),w(a+0x14),w(a+0x16),w(a+0x18),w(a+0x1A),b(a+0x22),b(a+0x5),b(a+0x2C),b(a+0x2A),b(a+0x29),b(a+0x20),w(a+0x46),w(a+0x44),b(a+0x38),w(a+0x2E),w(a+0x3A),w(a+0x26)}
    objects:write(table.concat(r,',')..'\n')
   end
  end
 end
 if plan.screenshots[f] then client.screenshot(out..'/f'..f..'.png'); client.invisibleemulation(true) end
end
state:close();objects:close()
local done=assert(io.open(out..'/done.txt','w'));done:write('read-only movie observation completed\n');done:close();client.exit()
