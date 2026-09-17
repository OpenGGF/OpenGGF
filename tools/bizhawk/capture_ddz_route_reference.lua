-- DDZ native route reference, 2026-09-17 (feature/ai-ddz-bring-up).
-- Purpose: answer named presentation/behaviour questions about The Doomsday Zone ($C00)
-- from ordinary complete-run movie playback, not a seeded position.
-- Inputs: common native capture host, verified locked-on ROM, a complete-run BK2 and a plan
-- returning {windows={{label=..., first=<movie frame>, last=<movie frame>, every=<n>}, ...}}.
-- Optional plan.save_states={<movie frame>,...} writes states/<frame>.State while the movie
-- plays; later probes pass one as --fixture-state (with plan.fixture_frame/fixture_zone checks).
-- Outputs: observations.csv (every frame inside any window), <label>/<frame>.png every
-- `every` frames, and done.txt once the last window or save has been reached.
-- RAM (sonic3k.lst): Camera_X/Y_pos $EE78/$EE7C (longwords), _unkEE98/_unkEE9C foreground
-- plane scroll, Events_routine_fg $EEC0, Events_bg $EED2 (+0 window base, +2/+4 boss x/y,
-- +6 background scroll), _unkFA82 speed, _unkFA86 locked camera, _unkFA8A accel,
-- _unkFA8E controller SST address, _unkFA90 camera delta, _unkFAA4 boss body SST address,
-- _unkFAAE wrap offset, _unkFAB8 phase bits, Ring_count $FE20, Super_Sonic_Knux_flag $FE19,
-- Super_frame_count $F670, Super_emerald_count $FFB1, V_int_run_count $FE0C,
-- Level_frame_counter $FE04, Current_zone_and_act $FE10, Player_1 SST $B000.
-- Object offsets (sonic3k.constants.asm): routine 5, x 10, y 14, x_vel 18, y_vel 1A, anim 20,
-- mapping_frame 22, angle 26, collision_flags 28, collision_property 29, status 2A,
-- object_control 2E, invulnerability_timer 34. Obj_DDZEndBoss code $817DA.
local out=assert(os.getenv('OGGF_NATIVE_OUTPUT'))
local plan=dofile(assert(os.getenv('OGGF_NATIVE_PLAN')))
local windows=plan.windows or {}
local lastFrame=0
for _,w in ipairs(windows) do
  assert(w.label and w.first and w.last and w.every,'window needs label/first/last/every')
  os.execute('mkdir -p "'..out..'/'..w.label..'"')
  if w.last>lastFrame then lastFrame=w.last end
end
local saves={}
for _,f in ipairs(plan.save_states or {}) do saves[f]=true if f>lastFrame then lastFrame=f end end
if next(saves) then os.execute('mkdir -p "'..out..'/states"') end
local fixture=os.getenv('OGGF_NATIVE_FIXTURE_STATE')
if fixture then
  assert(savestate.load(fixture),'fixture state failed to load: '..fixture)
  assert(plan.fixture_frame==nil or emu.framecount()==plan.fixture_frame,
    'fixture movie frame '..emu.framecount()..' expected '..tostring(plan.fixture_frame))
  assert(plan.fixture_zone==nil or mainmemory.read_u16_be(0xFE10)==plan.fixture_zone,
    string.format('fixture zone %04X', mainmemory.read_u16_be(0xFE10)))
end
local BOSS=0x817DA
local function findCode(code)
  for slot=0,129 do
    local a=0xB000+slot*0x4A
    if (mainmemory.read_u32_be(a) & 0xFFFFFF)==code then return a end
  end
  return nil
end
local function obj(a)
  if a==nil or a==0 then return '0,0,0,0,0,0,0,0' end
  return string.format('%06X,%d,%d,%d,%d,%d,%02X,%d',
    mainmemory.read_u32_be(a) & 0xFFFFFF, mainmemory.read_u8(a+5),
    mainmemory.read_u16_be(a+0x10), mainmemory.read_u16_be(a+0x14),
    mainmemory.read_s16_be(a+0x18), mainmemory.read_s16_be(a+0x1A),
    mainmemory.read_u8(a+0x28), mainmemory.read_u8(a+0x29))
end
local function word_addr(ptr)
  local v=mainmemory.read_u16_be(ptr)
  if v==0 then return nil end
  return v
end
local log=assert(io.open(out..'/observations.csv','w'))
log:write('movie_frame,label,zone_act,lfc,vint,cam_x,cam_xsub,cam_y,p1_x,p1_y,p1_xvel,p1_yvel,p1_gvel,p1_anim,p1_mapping,p1_angle,p1_objctl,p1_invuln,p1_status,rings,super_flag,super_frames,super_emeralds,ctrl1,fa82,fa86,fa8a,fa90,faae,fab8,ee98,ee9c,fg_routine,bg0,bg2,bg4,bg6,cam_minx,cam_maxx,')
log:write('ctl_code,ctl_routine,ctl_x,ctl_y,ctl_xvel,ctl_yvel,ctl_cf,ctl_cp,boss_code,boss_routine,boss_x,boss_y,boss_xvel,boss_yvel,boss_cf,boss_cp,body_code,body_routine,body_x,body_y,body_xvel,body_yvel,body_cf,body_cp,palette\n')
client.speedmode(6400)
local function paletteHex()
  local t={}
  for i=0,63 do t[#t+1]=string.format('%04X',mainmemory.read_u16_be(0xFC00+i*2)) end
  return table.concat(t,'')
end
while true do
  local frame=emu.framecount()
  local w=nil
  for _,x in ipairs(windows) do
    if frame+1>=x.first-2 and frame+1<=x.last then w=x break end
  end
  client.invisibleemulation(w==nil)
  emu.frameadvance()
  frame=emu.framecount()
  if saves[frame] then savestate.save(string.format('%s/states/%07d.State',out,frame)) end
  local cw=nil
  for _,x in ipairs(windows) do
    if frame>=x.first and frame<=x.last then cw=x break end
  end
  if cw then
    local ctl=word_addr(0xFA8E)
    local body=word_addr(0xFAA4)
    log:write(string.format('%d,%s,%04X,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%02X,%d,%d,%02X,%d,%02X,%d,%d,%d,%d,%04X,%08X,%08X,%08X,%d,%d,%02X,%d,%d,%d,%d,%d,%d,%d,%d,%d,',
      frame,cw.label,mainmemory.read_u16_be(0xFE10),mainmemory.read_u16_be(0xFE04),mainmemory.read_u32_be(0xFE0C),
      mainmemory.read_u16_be(0xEE78),mainmemory.read_u16_be(0xEE7A),mainmemory.read_u16_be(0xEE7C),
      mainmemory.read_u16_be(0xB010),mainmemory.read_u16_be(0xB014),
      mainmemory.read_s16_be(0xB018),mainmemory.read_s16_be(0xB01A),mainmemory.read_s16_be(0xB01C),
      mainmemory.read_u8(0xB020),mainmemory.read_u8(0xB022),mainmemory.read_u8(0xB026),
      mainmemory.read_u8(0xB02E),mainmemory.read_u8(0xB034),mainmemory.read_u8(0xB02A),
      mainmemory.read_u16_be(0xFE20),mainmemory.read_s8(0xFE19),mainmemory.read_s16_be(0xF670),
      mainmemory.read_u8(0xFFB1),mainmemory.read_u16_be(0xF604),
      mainmemory.read_u32_be(0xFA82),mainmemory.read_u32_be(0xFA86),mainmemory.read_u32_be(0xFA8A),
      mainmemory.read_s16_be(0xFA90),mainmemory.read_s16_be(0xFAAE),mainmemory.read_u8(0xFAB8),
      mainmemory.read_s16_be(0xEE98),mainmemory.read_s16_be(0xEE9C),mainmemory.read_u16_be(0xEEC0),
      mainmemory.read_s16_be(0xEED2),mainmemory.read_u16_be(0xEED4),mainmemory.read_u16_be(0xEED6),
      mainmemory.read_s16_be(0xEED8),mainmemory.read_u16_be(0xEE14),mainmemory.read_u16_be(0xEE16)))
    log:write(obj(ctl and (0xFF0000|ctl)&0xFFFF or nil)..','..obj(findCode(BOSS))..','..obj(body and (0xFF0000|body)&0xFFFF or nil)..','..paletteHex()..'\n')
    if (frame-cw.first)%cw.every==0 then
      client.screenshot(string.format('%s/%s/%07d.png',out,cw.label,frame))
    end
  end
  if frame>=lastFrame then break end
end
log:close()
local done=assert(io.open(out..'/done.txt','w'));done:write('last_frame='..lastFrame..'\n');done:close()
client.exit()
