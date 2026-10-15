#!/usr/bin/env bash
# Status line: shows how much of Claude's context window ("memory") is left.
# Claude Code pipes session JSON on stdin; we print one line.

input=$(cat)

model=$(jq -r '.model.display_name // "Claude"' <<<"$input")
size=$(jq -r '.context_window.context_window_size // 200000' <<<"$input")
left=$(jq -r '.context_window.remaining_percentage // empty' <<<"$input")

# Older Claude Code builds have no remaining_percentage: derive it from token counts.
if [ -z "$left" ]; then
  used=$(jq -r '.context_window.current_usage
    | if . then (.input_tokens // 0) + (.cache_creation_input_tokens // 0) + (.cache_read_input_tokens // 0) else empty end' <<<"$input")
  if [ -z "$used" ]; then
    transcript=$(jq -r '.transcript_path // empty' <<<"$input")
    if [ -f "$transcript" ]; then
      used=$(tac "$transcript" | jq -r 'select(.message.usage) | .message.usage
        | (.input_tokens // 0) + (.cache_creation_input_tokens // 0) + (.cache_read_input_tokens // 0)' 2>/dev/null | head -n1)
    fi
  fi
  left=$(( 100 - ${used:-0} * 100 / size ))
fi

left=${left%.*}
[ "$left" -lt 0 ] && left=0
[ "$left" -gt 100 ] && left=100

width=20
filled=$(( left * width / 100 ))
bar=''
for ((i = 0; i < width; i++)); do
  if [ "$i" -lt "$filled" ]; then bar+='█'; else bar+='░'; fi
done

if   [ "$left" -gt 50 ]; then color='\033[32m'   # green
elif [ "$left" -gt 20 ]; then color='\033[33m'   # yellow
else                          color='\033[31m'   # red
fi

printf "%s  🧠 ${color}%s\033[0m %s%% памяти осталось\n" "$model" "$bar" "$left"
