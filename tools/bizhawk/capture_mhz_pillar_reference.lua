-- MHZ2 native pillar arithmetic/collision reference, September 27 2026 campaign.
-- Inputs: capture_native_references.py, complete-run BK2, a matching MHZ2
-- movie save, and plan {state_frame=<save frame>, last_frame=<final frame>}.
-- Outputs: observations.csv, sampled native PNGs and done.txt. No RAM writes.
-- ROM loc_55552..loc_555FC: camera/copy EE78/EE80, Events_bg EED2,
-- H_scroll_buffer E000, helper pointers HScroll_table+8=A808 (sonic3k.lst).
-- Captures are diagnostic native evidence, not engine replay input.
local out=assert(os.getenv('OGGF_NATIVE_OUTPUT'))
local plan=dofile(assert(os.getenv('OGGF_NATIVE_PLAN')))
assert(savestate.load(assert(os.getenv('OGGF_NATIVE_FIXTURE_STATE'))))
assert(movie.isloaded() and emu.framecount()==plan.state_frame)
assert(mainmemory.read_u16_be(0xFE10)==0x701)
client.speedmode(6400);client.invisibleemulation(false)
local log=assert(io.open(out..'/observations.csv','w'))
log:write('frame,camera,copy,bg0,bg1,bg_line0,bg_line17,bg_line30,bg_line40,bg_line47,tall,spike0,spike1,spike2,spike3,spike4,spike5\n')
local count=0
while emu.framecount()<plan.last_frame do
 emu.frameadvance()
 local camera=mainmemory.read_u16_be(0xEE78)
 if mainmemory.read_u16_be(0xFE10)==0x701 and camera>=0x3F00 and camera<0x4300 then
  local row={emu.framecount(),camera,mainmemory.read_u16_be(0xEE80),mainmemory.read_u8(0xEED2),mainmemory.read_u8(0xEED3)}
  for _,line in ipairs({0,17,30,40,47}) do row[#row+1]=mainmemory.read_s16_be(0xE002+4*line) end
  for i=0,6 do local ptr=mainmemory.read_u16_be(0xA808+2*i);row[#row+1]=ptr>=0xB000 and ptr<=0xDFEF and mainmemory.read_u16_be(ptr+0x10) or -1 end
  log:write(table.concat(row,',')..'\n')
  if count%240==0 then client.screenshot(out..'/f'..emu.framecount()..'.png') end
  count=count+1
 end
end
log:close();assert(count>0,'no chase observations')
local done=assert(io.open(out..'/done.txt','w'));done:write(count..' observations\n');done:close();client.exit()
