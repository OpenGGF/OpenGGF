-- Purpose: read-only S2 competition goal evidence: slot in view and resident non-flat flip cells.
-- Inputs: OGGF_NATIVE_PLAN/OUTPUT/FIXTURE_STATE and matching power-on BK2.
-- Origin: lane P native evidence gate, 2026-11-08. No RAM/controller writes or input authoring.
local out=assert(os.getenv('OGGF_NATIVE_OUTPUT'))
local p=dofile(assert(os.getenv('OGGF_NATIVE_PLAN')))
local state=os.getenv('OGGF_NATIVE_FIXTURE_STATE')
client.speedmode(6400)
client.invisibleemulation(false)
if state then assert(savestate.load(state)); assert(emu.framecount()==p.state_frame) end
-- SpecialStage_Started: same REV01 RAM owner as s2_ss_trace_recorder.lua; not merely Game_Mode.
local fields={{'ss_started',0xdb23,1},{'p1_id',0xb000,1},{'p2_id',0xb040,1},{'ss_stage',0xfe16,1},{'zone_2p',0xff88,1},{'act_2p',0xff89,1},{'act1_result',0xff20,2},{'act2_result',0xff22,2},{'act_wins',0xff38,2},{'mode',0xf600,1},{'zoneact',0xfe10,2},{'two_player',0xffd8,2},{'title_option',0xff86,1},{'selection',0xff88,1},{'lfc',0xfe04,2},{'vint',0xfe0c,4},{'results',0xff10,2},{'game_paused',0xf63a,2},{'p1_pad',0xf604,2},{'p2_pad',0xf606,2}}
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

-- Native goal evidence reads resident VDP memory and actual foreground projections, not counters.
local templates=assert(p.flip_templates,'ROM-backed flip_templates required');local goal=assert(io.open(out..'/goals.csv','w'))
goal:write('frame,slot_inuse,flip_visible,pose,player,x,y,word,art_address,byte_swap,pixel_x,pixel_y,slot_visible,slot_player,slot_object_slot,slot_screen_x,slot_screen_y\n')
for _,t in ipairs(templates) do
 t.bytes={};for _,hex in ipairs(t.frames) do local a={};for h in hex:gmatch('..') do a[#a+1]=tonumber(h,16) end;t.bytes[#t.bytes+1]=a end
end
local function matches(a,b,swap)
 if #a~=#b then return false end
 for i=1,#a do local j=i;if swap then j=i%2==1 and i+1 or i-1 end;if a[i]~=b[j] then return false end end
 return true
end
local function word(domain,address,swap)
 local a=memory.read_u8(address,domain);local b=memory.read_u8(address+1,domain)
 return swap and b*256+a or a*256+b
end
local function flipProof()
 if mainmemory.read_u8(0xf600)~=12 or mainmemory.read_u16_be(0xffd8)~=1 or mainmemory.read_u8(0xf622)~=0 then return nil end
 for _,t in ipairs(templates) do
  local bytes=memory.read_bytes_as_array(t.address,#t.bytes[1],'VRAM');local pose,swap=nil,false
  for i,b in ipairs(t.bytes) do
   if matches(bytes,b,false) then pose=i-1;swap=false;break end
   if matches(bytes,b,true) then pose=i-1;swap=true;break end
  end
  if pose and pose%4~=0 then
   local corrupt={};for i,v in ipairs(t.bytes[pose+1]) do corrupt[i]=v end;corrupt[1]=(corrupt[1]+1)%256
   assert(not matches(bytes,corrupt,swap),'negative control: changed ROM art must not match')
   for player=1,2 do
    local top=player==1 and 0 or 216;local bottom=player==1 and 216 or 448
    local vs=mainmemory.read_s16_be(player==1 and 0xf616 or 0xf61e)
    local plane=player==1 and 0xc000 or 0xa000
    local first=top+(-(top+vs))%16
    for y=first,bottom-16,16 do
     local hs=word('VRAM',0xfc00+math.floor(y/2)*4,swap);if hs>=32768 then hs=hs-65536 end
     for x=hs%8,312,8 do
      local cell=word('VRAM',plane+((math.floor((y+vs)/16)%32)*64+(math.floor((x-hs)/8)%64))*2,swap)
      local tile=cell%1024
      if tile>=t.address/64 and tile<t.address/64+#bytes/64 then
       local art=memory.read_bytes_as_array(tile*64,64,'VRAM')
       -- Any nonblack opaque source pixel in this complete, projected resident-art cell.
       for i=1,64 do
        local byte=art[i];if swap then byte=art[i%2==1 and i+1 or i-1] end
        for nibble=0,1 do
         local colour=nibble==0 and math.floor(byte/16) or byte%16
         if colour~=0 and word('CRAM',(math.floor(cell/8192)%4*16+colour)*2,swap)~=0 then
          local px=(i-1)%4*2+nibble;local py=math.floor((i-1)/4)
          if math.floor(cell/2048)%2==1 then px=7-px end
          if math.floor(cell/4096)%2==1 then py=15-py end
          return {pose,player,x,y,cell,t.address,swap and 1 or 0,x+px,y+py}
         end
        end
       end
      end
     end
    end
   end
  end
 end
 return nil
end
local function slotProof(active)
 if active==0 or mainmemory.read_u8(0xf600)~=12 or mainmemory.read_u8(0xfe10)~=12 or mainmemory.read_u16_be(0xffd8)~=1 then return nil end
 for slot=2,127 do
  local base=0xb000+slot*64
  if mainmemory.read_u8(base)==0xd6 and mainmemory.read_u8(base+40)==1 then
   local x=mainmemory.read_u16_be(base+8);local y=mainmemory.read_u16_be(base+12)
   for player=1,2 do
    local camera=player==1 and 0xee00 or 0xee20
    local sx=x-mainmemory.read_u16_be(camera);local sy=y-mainmemory.read_u16_be(camera+4)
    if sx>=0 and sx<320 and sy>=0 and sy<(player==1 and 216 or 232) then
     return {1,player,slot,sx,sy}
    end
   end
  end
 end
 return nil
end
local slotSaved,flipSaved=false,false

local progress=assert(io.open(out..'/progress.txt','w'))
while emu.framecount()<p.last do
 emu.frameadvance(); local f=emu.framecount()
 csv:write(f); for _,v in ipairs(fields) do csv:write(','..read(v[2],v[3])) end; csv:write('\n')

 local slot=mainmemory.read_u16_be(0xff4c);local proof=nil
 if not flipSaved and f>p.anchor and mainmemory.read_u8(0xfe10)==12 then proof=flipProof() end
 local row={f,slot,proof and 1 or 0};for i=1,9 do row[#row+1]=proof and proof[i] or -1 end;local cage=slotProof(slot);for i=1,5 do row[#row+1]=cage and cage[i] or 0 end;goal:write(table.concat(row,',')..'\n')
 if cage and not slotSaved then client.screenshot(out..'/slot-'..f..'.png');savestate.save(out..'/slot-'..f..'.State');slotSaved=true end
 if proof then client.screenshot(out..'/flip-'..f..'.png');savestate.save(out..'/flip-'..f..'.State');flipSaved=true end
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
goal:close();csv:close();progress:close();local d=assert(io.open(out..'/done.txt','w'));d:write('ok');d:close();client.exit()
