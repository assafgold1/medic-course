#!/usr/bin/env python3
"""
Records the defibrillator simulator's voice prompts as WAV files, the way a real AED does it:
every prompt is a fixed recording in one voice, played through a small loudspeaker.

  python3 tools/aed_voice.py            # all prompts, macOS Hebrew voice (Carmit)
  python3 tools/aed_voice.py --voice X  # another installed voice (see: say -v '?')

Output: shared/src/commonMain/composeResources/files/sounds/aed/<id>.wav (16 kHz, mono, 16-bit).

To use real human recordings instead, put your own <id>.wav files (any WAV) in that folder with
the ids below, and run this script with --process-only to give them the same device sound.
Recordings from a commercial AED belong to its maker: only use them with written permission.
"""
import argparse
import array
import math
import os
import subprocess
import sys
import tempfile
import wave

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "shared/src/commonMain/composeResources/files/sounds/aed")
RATE = 16000

# id -> spoken text. The app shows its own (written) text on the screen; these are read aloud.
# Device prompts are short, calm and repeat the key word, like a real AED.
PROMPTS = {
    "pads": "חשפו את חזה המטופל. בחרו מדבקות, והדביקו אותן כמו בציור.",
    "analyze": "מנתח את קצב הלב. אל תגעו במטופל.",
    "advised": "מומלץ שוק. התרחקו מהמטופל.",
    "press": "לחצו על כפתור השוק המהבהב.",
    "cancelled": "השוק בוטל. התחילו החייאה.",
    "cpr": "התחילו החייאה. שלושים לחיצות, ושתי הנשמות.",
    "no_shock_check": "לא מומלץ שוק. בדקו נשימה ודופק.",
    "no_shock": "לא מומלץ שוק.",
    "motion": "זוהתה תנועה. הפסיקו כל מגע במטופל.",
    "shocked": "השוק ניתן.",
}
# Spoken by the rescuer, not the device: no loudspeaker colouring.
PLAIN = {
    "clear": "זהירות, אני נותן שוק. כולם להתרחק!",
}


def render(text, voice, rate_wpm, path_wav):
    with tempfile.TemporaryDirectory() as tmp:
        aiff = os.path.join(tmp, "v.aiff")
        subprocess.run(["say", "-v", voice, "-r", str(rate_wpm), "-o", aiff, text], check=True)
        subprocess.run(["afconvert", "-f", "WAVE", "-d", f"LEI16@{RATE}", "-c", "1", aiff, path_wav], check=True)


def read_wav(path):
    with wave.open(path, "rb") as w:
        if w.getsampwidth() != 2:
            sys.exit(f"{path}: need 16-bit WAV")
        ch, rate, n = w.getnchannels(), w.getframerate(), w.getnframes()
        data = array.array("h", w.readframes(n))
    if ch > 1:
        data = array.array("h", (int(sum(data[i:i + ch]) / ch) for i in range(0, len(data), ch)))
    x = [s / 32768.0 for s in data]
    if rate != RATE:  # linear resample
        step = rate / RATE
        x = [x[min(int(i * step), len(x) - 1)] for i in range(int(len(x) / step))]
    return x


def write_wav(path, x):
    with wave.open(path, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes(array.array("h", (max(-32767, min(32767, int(s * 32767))) for s in x)).tobytes())


def biquad(x, kind, f0, q=0.707, gain_db=0.0):
    """RBJ cookbook biquad."""
    a = 10 ** (gain_db / 40)
    w0 = 2 * math.pi * f0 / RATE
    c, s = math.cos(w0), math.sin(w0)
    al = s / (2 * q)
    if kind == "hp":
        b0, b1, b2, a0, a1, a2 = (1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al
    elif kind == "lp":
        b0, b1, b2, a0, a1, a2 = (1 - c) / 2, 1 - c, (1 - c) / 2, 1 + al, -2 * c, 1 - al
    else:  # peaking
        b0, b1, b2, a0, a1, a2 = 1 + al * a, -2 * c, 1 - al * a, 1 + al / a, -2 * c, 1 - al / a
    b0, b1, b2, a1, a2 = b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0
    y, x1, x2, y1, y2 = [], 0.0, 0.0, 0.0, 0.0
    for v in x:
        o = b0 * v + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1, y2, y1 = x1, v, y1, o
        y.append(o)
    return y


def normalize(x, peak=0.89):
    m = max((abs(v) for v in x), default=0) or 1
    return [v * peak / m for v in x]


def device_speaker(x):
    """A small AED loudspeaker: no deep bass, no air, a forward midrange, gently compressed."""
    x = biquad(x, "hp", 320, 0.8)
    x = biquad(x, "hp", 320, 0.8)
    x = biquad(x, "lp", 3800, 0.8)
    x = biquad(x, "peak", 1900, 1.1, 4.5)
    x = normalize(x, 1.0)
    x = [math.tanh(2.2 * v) / math.tanh(2.2) for v in x]  # soft compression
    return x


def finish(x):
    x = normalize(x)
    lead, tail = [0.0] * int(RATE * 0.06), [0.0] * int(RATE * 0.15)
    return lead + x + tail


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--voice", default="Carmit")
    ap.add_argument("--rate", type=int, default=158, help="words per minute (real AEDs speak slowly)")
    ap.add_argument("--process-only", action="store_true", help="only re-process WAVs already in the folder")
    a = ap.parse_args()
    os.makedirs(OUT, exist_ok=True)
    for pid, text in {**PROMPTS, **PLAIN}.items():
        path = os.path.join(OUT, f"{pid}.wav")
        if not a.process_only:
            render(text, a.voice, a.rate if pid in PROMPTS else a.rate + 25, path)
        elif not os.path.exists(path):
            print(f"skip {pid}: no file"); continue
        x = read_wav(path)
        x = device_speaker(x) if pid in PROMPTS else x
        write_wav(path, finish(x))
        print(f"{pid}.wav  {len(x) / RATE:.1f}s")


if __name__ == "__main__":
    main()
