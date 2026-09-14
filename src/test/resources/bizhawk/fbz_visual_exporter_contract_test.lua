-- FBZ native exporter regression, originating task 2026-09-14 FBZ completion.
-- Inputs: exporter path and disposable output directory. No emulator or ROM.
-- Execute actual production observations/publication gates with BizHawk 2.11 stubs.
local source_path, output = assert(arg[1]), assert(arg[2])
local plan = output .. "/plan.lua"
local file = assert(io.open(plan, "w"))
file:write([[return {manifest_sha256="BAE29DD285FF8D43166589164E31E1163F4196FCC1EA8DE8E2A5B90817AF7FC8",bk2_frame_offset=237913,cadence_series={
["aniplc-cadence-200"]={237914},["aniplc-cadence-208"]={237914},["aniplc-cadence-210"]={237914},["aniplc-cadence-230"]={237914},["aniplc-cadence-238"]={237914}}}]])
file:close()
local variables = {OGGF_FBZ_VISUAL_PLAN=plan, OGGF_FBZ_VISUAL_OUTPUT=output,
 OGGF_FBZ_ROM_SHA1="CFBF98C36C776677290A872547AC47C53D2761D6", OGGF_FBZ_BK2_SHA256="stub", OGGF_FBZ_HOST_RECEIPT="stub"}
local getenv = os.getenv
os.getenv = function(name) return variables[name] or getenv(name) end
local config, captures = {}, 0
client = {getconfig=function() return config end, speedmode=function() end,
 invisibleemulation=function() end, screenshot=function() captures=captures+1 end}
event = {onexit=function() end}; gui = {clearGraphics=function() end}
emu = {limitframerate=function() end}
local words, bytes, addresses = {}, {}, {}
mainmemory = {read_u16_be=function(a) addresses[a]=true; return words[a] or 0 end,
 read_u8=function(a) return bytes[a] or 0 end}
local domain, hash_failure = "Main RAM", false
memory = {getmemorydomainlist=function() return {"Main RAM","VRAM"} end,
 getcurrentmemorydomain=function() return domain end,
 usememorydomain=function(name) domain=name end,
 hash_region=function(address,count,name)
  assert(name=="VRAM", "hash_region third argument must be domain, not algorithm")
  assert(address==0x4000 and count==256, "wrong tile byte range")
  if hash_failure then error("native hash failure") end
  return string.rep("a",64)
 end}
file=assert(io.open(source_path,"r")); local source=file:read("*a"); file:close()
-- Execute setup and real function definitions, stopping only before the movie loop.
local boundary=assert(source:find("local function run_export()",1,true))
local api=assert(load(source:sub(1,boundary-1)..[[
return {read=read_ram_snapshot,visibility=visibility_failures,hash=hash_vram,
start=capture_start,write=write_snapshot_json,config=install_capture_config}
]],"@"..source_path))()
api.config()
words[0xB010],words[0xB014],words[0xFE10],bytes[0xF600]=0x60,0x76C,0x400,0x0C
words[0xEED2],words[0xEED4],words[0xEED6]=0x18,0xFF00,0xFF00
for _,command in ipairs({0x8134,0x8174}) do
 words[0xF60E]=command
 local snapshot=api.read()
 assert(addresses[0xF60E] and not addresses[0xF60C], "wrong command-template address")
 assert(snapshot.vdp_reg1_command==command)
 assert(snapshot.display_enabled==nil, "RAM template became hardware state")
 assert(snapshot.display_verification=="unverified-no-live-vdp-register-observation")
 assert(#api.visibility(snapshot)==1, "otherwise-visible start must reject unknown display")
 assert(not api.start(snapshot,237948), "unverified display accepted a reference")
 local chunks={}; api.write({write=function(_,v) chunks[#chunks+1]=v end},snapshot)
 assert(table.concat(chunks):find('"display_enabled": null',1,true), "unknown must be JSON null")
end
assert(captures==0, "unverified frame reached screenshot publication")
words[0xB250],words[0xB252],words[0xB280]=0x0002,0xD690,4
local snapshot=api.read()
assert(snapshot.title_card_active and not snapshot.title_card_complete)
-- Native Obj_FBZFloatingPlatform update loc_3A5DA reuses this allocator slot.
words[0xB250],words[0xB252],words[0xB280]=0x0003,0xA5DA,0x100
snapshot=api.read()
assert(not snapshot.title_card_active and snapshot.title_card_complete, "reused slot is not title card")
assert(snapshot.title_card_child_count==0x100, "raw slot evidence lost")
local spec={destination_tile=0x200,tile_count=8}
assert(api.hash(spec)==string.rep("A",64)); assert(domain=="Main RAM", "hash changed domain")
hash_failure=true
assert(not pcall(api.hash,spec), "hash failure swallowed")
assert(domain=="Main RAM", "failed hash changed domain")
-- A valid host content observation is paired with this emulator frame, not
-- promoted to a fabricated live VDP bit. Same-frame reads share its PNG hash.
variables.OGGF_FBZ_FRAMEBUFFER_PROBE = "/stub/probe.py"
local frame, probes, reply = 237948, 0, "PASS " .. string.rep("B",64) .. " 172 7 223"
emu.framecount=function() return frame end
io.popen=function(command)
    probes=probes+1
    assert(command:find("--probe-framebuffer",1,true))
    return {read=function() return reply end, close=function() return true end}
end
snapshot=api.read()
assert(snapshot.display_enabled==nil, "framebuffer observation fabricated a hardware bit")
assert(snapshot.display_verification=="verified-framebuffer-content")
assert(snapshot.framebuffer_sha256==string.rep("B",64))
assert(#api.visibility(snapshot)==0)
assert(api.read().framebuffer_sha256==snapshot.framebuffer_sha256 and probes==1,
    "same-frame reread did not retain framebuffer binding")
frame=frame+1; reply="FAIL blank-native-crop"
snapshot=api.read()
assert(#api.visibility(snapshot)==1 and snapshot.framebuffer_sha256==nil,
    "blank-frame probe or stale hash accepted")
print("FBZ exporter observation and fail-closed publication checks passed")
