-- SSZ1 arrival/Death Egg observer, originating 2026-09-24 S&K completion task.
-- Inputs: original BK2, its native save and an explicit frame-window plan.
-- Outputs: state.csv, objects.csv, screenshots and optional restart save.
-- Read-only ROM observation. Never use these rows to hydrate engine gameplay.
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
state:write('frame,zone,vint,lfc,camx,camy,px,py,fab8,button,delta,oscillator,rng,tracking,mask,palette3\n')
objects:write('frame,slot,code,map,x,y,yacc,vx,vy,frameid,routine,subtype,priority,art,parent,timer,animation,animindex,delay,velocity40\n')
while emu.framecount()<plan.last do
 if plan.screenshots[emu.framecount()+1] then client.invisibleemulation(false) end
 emu.frameadvance()
 local f=emu.framecount()
 if plan.save_frame==f then savestate.save(out..'/arrival.State') end
 if f>=plan.first then
  local colors={};for i=0,15 do colors[#colors+1]=string.format('%04X',w(0xFC60+i*2)) end
  state:write(table.concat({f,w(0xFE10),l(0xFE0C),w(0xFE04),w(0xEE78),w(0xEE7C),w(0xB010),w(0xB014),b(0xFAB8),b(0xEEDA),w(0xFA84),w(0xEE9C),l(0xF636),b(0xFA9C),w(0xEF3A),table.concat(colors)},',')..'\n')
  for slot=2,129 do
   local a=0xB000+slot*0x4A;local code=l(a)&0xFFFFFF
   if code>=0x65600 and code<0x65BE0 then
    objects:write(table.concat({f,slot,code,l(a+0xC)&0xFFFFFF,w(a+0x10),w(a+0x14),l(a+0x1A),w(a+0x18),w(a+0x1A),b(a+0x22),b(a+5),b(a+0x2C),w(a+8),w(a+0xA),w(a+0x46),w(a+0x2E),l(a+0x30),b(a+0x23),b(a+0x24),w(a+0x40)},',')..'\n')
   end
  end
 end
 if plan.screenshots[f] then client.screenshot(out..'/f'..f..'.png');client.invisibleemulation(true) end
end
state:close();objects:close()
local done=assert(io.open(out..'/done.txt','w'));done:write('read-only movie observation completed\n');done:close();client.exit()
