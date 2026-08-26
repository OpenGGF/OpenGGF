#!/usr/bin/env bash
set -euo pipefail

game=${1:?usage: build-representative-ledger.sh s1|s2 INPUT OUTPUT}
input=${2:?usage: build-representative-ledger.sh s1|s2 INPUT OUTPUT}
output=${3:?usage: build-representative-ledger.sh s1|s2 INPUT OUTPUT}
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
source_map="$script_dir/../../../../docs/architecture/research/audio/s1-s2-ym-write-source-map-v1.tsv"
[[ "$game" == s1 || "$game" == s2 ]] || { echo 'game must be s1 or s2' >&2; exit 2; }
[[ -f "$input" && ! -e "$output" ]] || { echo 'input must exist and output must not' >&2; exit 2; }

gawk -F '\t' -v OFS='\t' -v game="$game" '
function hex(value) { return strtonum(value) }
function z80_conditional(op) {
  return op == 0x10 || op == 0x20 || op == 0x28 || op == 0x30 || op == 0x38 \
    || op == 0xc0 || op == 0xc8 || op == 0xd0 || op == 0xd8 \
    || op == 0xe0 || op == 0xe8 || op == 0xf0 || op == 0xf8 \
    || op == 0xc2 || op == 0xca || op == 0xd2 || op == 0xda \
    || op == 0xe2 || op == 0xea || op == 0xf2 || op == 0xfa \
    || op == 0xc4 || op == 0xcc || op == 0xd4 || op == 0xdc \
    || op == 0xe4 || op == 0xec || op == 0xf4 || op == 0xfc
}
function z80_length(op) {
  if (op == 0x10 || op == 0x18 || op == 0x20 || op == 0x28 || op == 0x30 || op == 0x38) return 2
  if (op == 0xc2 || op == 0xca || op == 0xd2 || op == 0xda || op == 0xe2 || op == 0xea || op == 0xf2 || op == 0xfa || op == 0xc3 \
      || op == 0xc4 || op == 0xcc || op == 0xd4 || op == 0xdc || op == 0xe4 || op == 0xec || op == 0xf4 || op == 0xfc || op == 0xcd) return 3
  return 1
}
function flow_kind(op, pc, high) {
  if (game == "s2") {
    if (op == 0xcd || op == 0xc4 || op == 0xcc || op == 0xd4 || op == 0xdc || op == 0xe4 || op == 0xec || op == 0xf4 || op == 0xfc \
        || op == 0xc7 || op == 0xcf || op == 0xd7 || op == 0xdf || op == 0xe7 || op == 0xef || op == 0xf7 || op == 0xff) return "call"
    if (op == 0xc9 || op == 0xc0 || op == 0xc8 || op == 0xd0 || op == 0xd8 || op == 0xe0 || op == 0xe8 || op == 0xf0 || op == 0xf8) return "return"
    if (op == 0x10 || op == 0x18 || op == 0x20 || op == 0x28 || op == 0x30 || op == 0x38 \
        || op == 0xc2 || op == 0xca || op == 0xd2 || op == 0xda || op == 0xe2 || op == 0xea || op == 0xf2 || op == 0xfa || op == 0xc3) return "branch"
    return "linear"
  }
  high = and(rshift(op, 8), 0xff)
  if (high == 0x61 || and(op, 0xffc0) == 0x4e80) return "call"
  if (and(op, 0xffc0) == 0x4ec0) return "jump"
  if (op == 0x4e75 || op == 0x4e73 || op == 0x4e77) return "return"
  if ((high >= 0x60 && high <= 0x6f) || and(op, 0xf0f8) == 0x50c8) return "branch"
  return "linear"
}
function conditional(op, high) {
  if (game == "s2") return z80_conditional(op)
  high = and(rshift(op, 8), 0xff)
  return (high >= 0x62 && high <= 0x6f) || and(op, 0xf0f8) == 0x50c8
}
function sequential_pc(op, pc, low, high) {
  if (game == "s2") return pc + z80_length(op)
  high = and(rshift(op, 8), 0xff); low = and(op, 0xff)
  if (high >= 0x60 && high <= 0x6f) return pc + (low == 0 ? 4 : (low == 0xff ? 6 : 2))
  if (and(op, 0xf0f8) == 0x50c8) return pc + 4
  if (and(op, 0xffc0) == 0x4e80) return pc + 2
  return pc + 2
}
function roles(op, pc, flow, result) {
  result = flow == "linear" ? "ordinary" : "control_flow"
  if (game == "s1") {
    if (((pc == 0x7272e || pc == 0x72746 || pc == 0x72764 || pc == 0x7277c) && op == 0x1439) \
        || ((pc == 0x72734 || pc == 0x7274c || pc == 0x7276a || pc == 0x72782) && op == 0x0802) \
        || ((pc == 0x7273a || pc == 0x72750 || pc == 0x72770 || pc == 0x72786) && and(op,0xff00) == 0x6600)) result = result ",busy_poll"
    if ((pc == 0x72788 || pc == 0x72752) && op == 0x13c1) result = result ",ym_write"
  } else {
    if ((pc == 0x8 && op == 0x3a) || (pc == 0xb && op == 0x87) || (pc == 0xc && op == 0x38)) result = result ",busy_poll"
    if ((pc == 0xe34 && op == 0x7e) || ((pc == 0xe46 || pc == 0xe52 || pc == 0xe79) && op == 0x4e)) result = result ",bank_wait_3t"
    if ((pc == 0x31 || pc == 0x21) && op == 0x32) result = result ",ym_write"
  }
  return result
}
function source(pc, j) {
  for (j=1; j<=map_n; j++) if (map_game[j] == game && pc >= map_start[j] && pc <= map_end[j])
    return map_label[j] "@" map_file[j] ":" map_line_start[j] "-" map_line_end[j]
  return "UNKNOWN"
}
ARGIND == 1 && FNR == 1 { next }
ARGIND == 1 { map_n++; map_game[map_n]=$1; map_start[map_n]=hex($2); map_end[map_n]=hex($3); map_label[map_n]=$4; map_file[map_n]=$5; map_line_start[map_n]=$6; map_line_end[map_n]=$7; next }
ARGIND == 2 && FNR == 1 { next }
ARGIND == 2 && $2 == 0 { n++; frame[n]=$1; after[n]=$3; cpu[n]=$4; pc_text[n]=$5; op_text[n]=$6; cycle[n]=$7; refresh[n]=$8; pc[n]=hex($5); op[n]=hex($6) }
END {
  if (game == "s1")
    print "occurrence_ordinal","frame","after_source_ordinal","cpu","pc","opcode","start_master_cycle","refresh_delay_total_master_cycles","next_pc","delta_to_next_start","flow","branch_outcome","roles","source"
  else
    print "occurrence_ordinal","frame","after_source_ordinal","cpu","pc","opcode","start_master_cycle","next_pc","delta_to_next_start","flow","branch_outcome","roles","source"
  for (i=1; i<=n; i++) {
    flow = flow_kind(op[i], pc[i]); outcome = "n/a"; next_pc_value = i<n ? pc_text[i+1] : "key_on"
    delta = i<n ? cycle[i+1]-cycle[i] : "key_on"
    if (conditional(op[i])) outcome = (i<n && pc[i+1] == sequential_pc(op[i], pc[i])) ? "not_taken" : "taken"
    else if (flow != "linear") outcome = "target=" next_pc_value
    citation = source(pc[i]); if (citation == "UNKNOWN") { print "unmapped source PC " pc_text[i] > "/dev/stderr"; exit 3 }
    if (game == "s1")
      print i-1,frame[i],after[i],cpu[i],pc_text[i],op_text[i],cycle[i],refresh[i],next_pc_value,delta,flow,outcome,roles(op[i],pc[i],flow),citation
    else
      print i-1,frame[i],after[i],cpu[i],pc_text[i],op_text[i],cycle[i],next_pc_value,delta,flow,outcome,roles(op[i],pc[i],flow),citation
  }
}' "$source_map" "$input" > "$output"
