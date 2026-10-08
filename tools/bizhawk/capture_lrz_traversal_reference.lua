-- LRZ turbine/chained-platform diagnostic, 2026-09-22 S&K completion campaign.
-- Inputs: verified ROM/BK2 and native movie save; plan fields state_frame, first,
-- last, screenshots (frame-to-boolean map). Outputs: objects.csv, selected PNGs, done.txt.
-- Read-only replay of original BK2, resumed at its own frame-415400 state.
-- No RAM writes. Observations are comparison-only, never engine state input.
local out=assert(os.getenv('OGGF_NATIVE_OUTPUT'))
local plan=dofile(assert(os.getenv('OGGF_NATIVE_PLAN')))
assert(savestate.load(assert(os.getenv('OGGF_NATIVE_FIXTURE_STATE'))))
assert(movie.isloaded() and emu.framecount()==plan.state_frame)
client.speedmode(6400); client.invisibleemulation(true)
local log=assert(io.open(out..'/objects.csv','w'))
log:write('movie_frame,lfc,slot,code,map,x,xsub,y,ysub,vx,vy,subtype,cursor,captured1,captured2,angle1,angle2,px,py,pvy,ctl\n')
while emu.framecount()<plan.last do
 if plan.screenshots[emu.framecount()+1] then client.invisibleemulation(false) end
 emu.frameadvance()
 local f=emu.framecount()
 if f>=plan.first then
  assert(mainmemory.read_u16_be(0xFE10)==0x901,'expected LRZ2')
  for slot=2,129 do
   local a=0xB000+slot*0x4A
   local map=mainmemory.read_u32_be(a+0xC)&0xFFFFFF
   if map==0x445A6 or map==0x445B0 or map==0x4A980 then
    log:write(string.format('%d,%d,%d,%X,%X,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d\n',
     f,mainmemory.read_u16_be(0xFE04),slot,mainmemory.read_u32_be(a)&0xFFFFFF,map,
     mainmemory.read_u16_be(a+0x10),mainmemory.read_u16_be(a+0x12),mainmemory.read_u16_be(a+0x14),mainmemory.read_u16_be(a+0x16),
     mainmemory.read_s16_be(a+0x18),mainmemory.read_s16_be(a+0x1A),mainmemory.read_u8(a+0x2C),mainmemory.read_u8(a+0x3C),
     mainmemory.read_u8(a+0x30),mainmemory.read_u8(a+0x31),mainmemory.read_u8(a+0x34),mainmemory.read_u8(a+0x35),
     mainmemory.read_u16_be(0xB010),mainmemory.read_u16_be(0xB014),mainmemory.read_s16_be(0xB01A),mainmemory.read_u8(0xB02E)))
   end
  end
 end
 if plan.screenshots[f] then client.screenshot(out..'/f'..f..'.png');client.invisibleemulation(true) end
end
log:close()
local done=assert(io.open(out..'/done.txt','w'));done:write('read-only original movie playback completed\n');done:close()
client.exit()
