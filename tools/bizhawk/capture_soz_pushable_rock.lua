-- SOZ pushable-rock diagnostic, 2026-09-15. Seeded position, not cold-route evidence.
-- Inputs: common native capture host, verified locked-on ROM/BK2, SOZ1 LFC35
-- native save state, explicit empty plan. Outputs: intro/rock CSVs, PNGs and
-- terminal.json only after observing the authored stop. Complete the intro with
-- its jump, normalize P1 position/motion/control once, then use normal input.
-- ROM owners: Obj_LevelIntro_PlayerFallIntoGround; Obj_SOZPushableRock ($40546).
local out=assert(os.getenv('OGGF_NATIVE_OUTPUT'))
movie.stop() -- Saved after movie playback ended; detach before loading.
local loaded=savestate.load(assert(os.getenv('OGGF_NATIVE_FIXTURE_STATE')))
local zone=mainmemory.read_u16_be(0xFE10)
assert(loaded and zone==0x0800 and mainmemory.read_u16_be(0xFE04)==35,
 'SOZ1 first-visible LFC35 native state required')
client.invisibleemulation(false);client.speedmode(6400)
-- Obj_LevelIntro_PlayerFallIntoGround: loc_420A6 waits for a fresh jump;
-- loc_4213C releases control after the player emerges. Finish with ordinary input.
local intro=assert(io.open(out..'/intro.csv','w'))
intro:write('frame,y,lock,jump\n')
for frame=0,299 do
 local jump=false
 for slot=0,109 do
  if mainmemory.read_u32_be(0xB000+slot*0x4A)==0x420A6 then jump=true end
 end
 local keys={};for _,k in ipairs({'Up','Down','Left','Right','A','B','C','Start'}) do keys['P1 '..k]=false end
 keys['P1 C']=jump;joypad.set(keys);emu.frameadvance()
 intro:write(string.format('%d,%d,%d,%d\n',frame,mainmemory.read_u16_be(0xB014),mainmemory.read_u8(0xF7CA),jump and 1 or 0))
 if mainmemory.read_u8(0xF7CA)==0 then break end
end
intro:close()
assert(mainmemory.read_u8(0xF7CA)==0,'native intro jump must release control before positioned setup')
mainmemory.write_u32_be(0xB010,0x3B00000)
mainmemory.write_u32_be(0xB014,0x5EC0000)
mainmemory.write_u16_be(0xB018,0);mainmemory.write_u16_be(0xB01A,0)
mainmemory.write_u16_be(0xB01C,0);mainmemory.write_u8(0xB02A,0)
mainmemory.write_u8(0xB02E,0);mainmemory.write_u8(0xB020,0)
local log=assert(io.open(out..'/pushable.csv','w'))
log:write('frame,lfc,px,py,rock,x,y,vx,vy,phase,timer,target,cursor,status\n')
local falling=false
local terminalFrame=nil
for frame=0,799 do
 local keys={};for _,k in ipairs({'Up','Down','Left','Right','A','B','C','Start'}) do keys['P1 '..k]=false end
 keys['P1 Right']=frame>=30 and not falling
 joypad.set(keys);emu.frameadvance()
 for slot=0,109 do
  local a=0xB000+slot*0x4A
  if mainmemory.read_u32_be(a+0xC)==0x40776 and mainmemory.read_u8(a+0x2C)%32==9 then
   if mainmemory.read_u32_be(a)==0x405D8 then falling=true end
   if mainmemory.read_u32_be(a)==0x406AE and mainmemory.read_u16_be(a+0x10)==0x4F0
    and mainmemory.read_u16_be(a+0x14)==0x652 and not terminalFrame then terminalFrame=frame end
   log:write(string.format('%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d\n',frame,
    mainmemory.read_u16_be(0xFE04),mainmemory.read_u16_be(0xB010),mainmemory.read_u16_be(0xB014),a,
    mainmemory.read_u16_be(a+0x10),mainmemory.read_u16_be(a+0x14),mainmemory.read_s16_be(a+0x18),mainmemory.read_s16_be(a+0x1A),
    mainmemory.read_u32_be(a),mainmemory.read_s16_be(a+0x32),mainmemory.read_u16_be(a+0x3A),mainmemory.read_u32_be(a+0x36),mainmemory.read_u8(a+0x2A)))
  end
 end
 if frame==30 or frame==300 or frame==500 or frame==700 then client.screenshot(out..'/pushable-'..frame..'.png') end
end
log:close()
if terminalFrame then
 local result=assert(io.open(out..'/terminal.json','w'))
 result:write(string.format('{"terminal_frame":%d,"x":1264,"y":1618}\n',terminalFrame));result:close()
end
client.exit()
