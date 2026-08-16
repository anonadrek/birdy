"""Genererar en deterministisk 3s/48kHz mono-chirp som paritetsfixtur.

Syntetisk (ingen licensfråga, till skillnad från xeno-canto-material). Facit är
inte "rätt art" utan EXAKT vilka top-3 den delade pipelinen ger — samma bytes in
ska ge samma svar på desktop-referensen och (via mic-luft-gapet approximativt)
på device. Determinism: ren matte, ingen slump.
"""
import math
import struct
import wave
from pathlib import Path

SAMPLE_RATE = 48_000
SECONDS = 3
OUT = Path(__file__).parent / "fixtures" / "chirp_3s_48k.wav"


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    n = SAMPLE_RATE * SECONDS
    samples = []
    for i in range(n):
        t = i / SAMPLE_RATE
        f = 2000.0 + (6000.0 * t / SECONDS)  # svep 2->8 kHz (fågelsångs-registret)
        v = 0.5 * math.sin(2 * math.pi * f * t) + 0.2 * math.sin(2 * math.pi * 2 * f * t)
        env = math.sin(math.pi * t / SECONDS)  # fade in/ut
        samples.append(int(max(-1.0, min(1.0, v * env)) * 32767))
    with wave.open(str(OUT), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        w.writeframes(struct.pack(f"<{n}h", *samples))
    print(f"Skrev {OUT} ({OUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
