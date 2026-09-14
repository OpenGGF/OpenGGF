-- Fresh native FBZ entry via ordinary inputs; originating 2026-09-14 FBZ completion.
-- Inputs: verified host ROM and complete BK2 from reset; optional saved native
-- first-vine state. Outputs: observed input/state log, menu and LFC35 state/PNG.
-- All gameplay is native: no RAM writes, copied scroll state, or patched ROM.
-- Ordinary AIZ vine cheat -> pause+A -> title level select -> FBZ1/2.
-- No memory writes. Source: AIZRideVineHandle_CheckButtonSequence, Pause_Game,
-- Obj_TitleSelection_Main, LS_Level_Order (FBZ1 index14), LevelSelect_StartZone.
local out=assert(os.getenv('OGGF_FBZ_VISUAL_OUTPUT'))
local plan=dofile(assert(os.getenv('OGGF_FBZ_VISUAL_PLAN')))
local act=plan.fresh_entry_act or 1;assert(act==1 or act==2)
local selection=13+act
local log=assert(io.open(out..'/selection.jsonl','w'))
local function record(phase)
 log:write(string.format('{"phase":"%s","frame":%d,"mode":%d,"level_select":%d,"title_option":%d,"level_option":%d,"player_option":%d,"zone":%d,"lfc":%d,"a9fc":%u}\n',phase,emu.framecount(),mainmemory.read_u8(0xF600),mainmemory.read_u8(0xFFE0),mainmemory.read_u8(0xFF86),mainmemory.read_u16_be(0xFF82),mainmemory.read_u16_be(0xFF0A),mainmemory.read_u16_be(0xFE10),mainmemory.read_u16_be(0xFE04),mainmemory.read_u32_be(0xA9FC)));log:flush()
end
local function step(button)
 local keys={};for _,k in ipairs({'Up','Down','Left','Right','A','B','C','Start'}) do keys['P1 '..k]=k==button end
 joypad.set(keys);emu.frameadvance();record(button or 'neutral')
end
client.invisibleemulation(false);client.speedmode(6400)
local state=os.getenv('OGGF_FBZ_FIXTURE_STATE')
if state then assert(savestate.load(state)) else
 while emu.framecount()<15000 do
  emu.frameadvance()
  if mainmemory.read_u16_be(0xFE10)==0 and mainmemory.read_u8(0xB02E)==3
   and mainmemory.read_u8(0xB020)==0x14 then break end
 end
end
assert(mainmemory.read_u16_be(0xFE10)==0 and mainmemory.read_u8(0xB02E)==3
 and mainmemory.read_u8(0xB020)==0x14,'ordinary AIZ vine grab prerequisite unavailable')
movie.stop();record('vine-start');savestate.save(out..'/vine.State')
for _,key in ipairs({'Left','Left','Left','Right','Right','Right','Up','Up','Up'}) do step();step(key);step();end
if mainmemory.read_u8(0xFFE0)~=1 then record('FAIL-unlock');log:close();client.exit();return end
savestate.save(out..'/unlocked.State')
step('Start');step();step('A');step()
-- Obj_TitleSelection_Main ($4A88 in the verified ROM) accepts menu input.
-- loc_41D4 also requires Reserved_object_3 ($B094) after the banner
-- finishes; menu input alone can work before Start is admitted.
local menu_ready=false
for i=1,720 do
 step()
 for slot=0,109 do
  if mainmemory.read_u32_be(0xB000+slot*0x4A)==0x4A88 then menu_ready=true;break end
 end
 if menu_ready and mainmemory.read_u32_be(0xB094)~=0 then break end
 menu_ready=false
end
if not menu_ready then record('FAIL-title-owner');log:close();client.exit();return end
for i=1,12 do
 if mainmemory.read_u8(0xFF86)==2 then break end
 step('Up');for j=1,10 do step() end
end
record('title-selection');step('Start');step()
for i=1,120 do step() end
record('level-menu');client.screenshot(out..'/level-menu.png')
for i=1,34 do
 if mainmemory.read_u16_be(0xFF82)==selection then break end
 step('Down');step();step()
end
record('fbz-selected');client.screenshot(out..'/fbz-selected.png')
savestate.save(out..'/fbz-menu.State')
if mainmemory.read_u16_be(0xFF82)~=selection then record('FAIL-selection');log:close();client.exit();return end
step('Start');step()
for i=1,480 do
 step()
 if mainmemory.read_u16_be(0xFE10)==0x0400+act-1 and mainmemory.read_u16_be(0xFE04)==35 and mainmemory.read_u8(0xB005)==2 then
  savestate.save(out..'/fbz'..act..'-lfc35.State');client.screenshot(out..'/fbz'..act..'-lfc35.png');record('fbz-lfc35');break
 end
end
record('finished');log:close();client.exit()
