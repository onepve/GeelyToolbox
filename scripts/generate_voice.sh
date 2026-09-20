#!/usr/bin/env bash
# 生成车机语音 mp3，对齐 app/src/main/assets/audio/ 现有参数（24kHz / 单声道 / 48kbps，参考 epb_alarm.mp3）。
# 用法: VOICE=zh-CN-XiaoxiaoNeural ./generate_voice.sh "<台词>" <输出文件名.mp3>
set -euo pipefail

TEXT="${1:?用法: generate_voice.sh "<台词>" <输出.mp3>}"
OUT="${2:?缺输出文件名}"
VOICE="${VOICE:-zh-CN-XiaoxiaoNeural}"
RATE="${RATE:--5%}"
PITCH="${PITCH:--1Hz}"

EDGE_TTS="$(command -v edge-tts || echo /usr/local/lib/hermes-agent/venv/bin/edge-tts)"
TMP="$(mktemp --suffix=.mp3)"
trap 'rm -f "$TMP"' EXIT

"$EDGE_TTS" --voice "$VOICE" --rate "$RATE" --pitch "$PITCH" --text "$TEXT" --write-media "$TMP"
# 前置 280ms 绝对静音（车机功放建立通道会吃掉开头，零静音直录必吞首字 —— 门禁 24a/24c 锁定）
ffmpeg -y -loglevel error -i "$TMP" -af "adelay=280" -ar 24000 -ac 1 -b:a 48k "$OUT"
echo "已生成: $OUT"
ffprobe -v error -show_entries stream=codec_name,sample_rate,channels,bit_rate,duration -of default=noprint_wrappers=1 "$OUT"
