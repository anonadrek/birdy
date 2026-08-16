"""Kör BirdNET-Lite med full TF (Flex ingår) och skriver facit-top-3.

Pipeline-spegel av appens delade postprocess: normalize (/32768) ->
flat_sigmoid (klipp +-15) -> filtrera till mappade EU-klasser FÖRE ranking ->
top-3. Se shared/ml BirdNetPostprocess.kt + AudioScanViewModel.

Användning:
    uv run python reference.py                # chirp-fixturen + nolltest
    uv run python reference.py path/to.wav    # godtyckligt 48k mono 16-bit wav
"""
import json
import struct
import sys
import wave
from pathlib import Path

import numpy as np
import tensorflow as tf

REPO = Path(__file__).resolve().parents[3]
MODEL = REPO / "composeApp/src/androidMain/assets/models/birdnet_lite_v2.tflite"
MAPPING = REPO / "shared/ml/src/commonMain/composeResources/files/ml/birdnet_lite_to_qid.json"
FACIT = REPO / "docs/superpowers/research/2026-08-16-i3-audio-reference-facit.md"
FIXTURE = Path(__file__).parent / "fixtures" / "chirp_3s_48k.wav"


def load_wav(path: Path) -> np.ndarray:
    with wave.open(str(path), "rb") as w:
        assert w.getframerate() == 48_000 and w.getnchannels() == 1 and w.getsampwidth() == 2, (
            "kräver 48 kHz mono 16-bit"
        )
        raw = w.readframes(w.getnframes())
    pcm = np.array(struct.unpack(f"<{len(raw) // 2}h", raw), dtype=np.float32)
    return pcm / 32768.0


def top3(waveform: np.ndarray, interp, lookup) -> list[tuple[str, float]]:
    n = 144_000
    x = np.zeros(n, dtype=np.float32)
    x[: min(n, len(waveform))] = waveform[:n]
    inputs = interp.get_input_details()
    interp.set_tensor(inputs[0]["index"], x.reshape(1, n))
    if len(inputs) > 1:
        interp.set_tensor(inputs[1]["index"], np.zeros((1, 6), dtype=np.float32))
    interp.invoke()
    logits = interp.get_tensor(interp.get_output_details()[0]["index"])[0]
    scores = 1.0 / (1.0 + np.exp(-np.clip(logits, -15.0, 15.0)))
    mapped = [(lookup[i], float(s)) for i, s in enumerate(scores) if lookup.get(i)]
    return sorted(mapped, key=lambda p: -p[1])[:3]


def format_result(result: list[tuple[str, float]]) -> list[str]:
    """Formaterar en top-3-lista till facit-doc-rader, med en explicit NaN-flagga.

    Upptäckt 2026-08-16: ett vågformsindata med EXAKT NOLL varians (perfekt tystnad
    eller perfekt DC — t.ex. np.zeros) ger NaN på ALLA 6362 utgångs-logits, inte bara
    de mappade. Verifierat att detta sitter i själva TFLite-grafen (troligen en
    per-klipp varians/RMS-normalisering som delar med noll), inte i den här skriptets
    postprocess — samma `logit.coerceIn(-15f,15f)` + sigmoid i Kotlin (BirdNetPostprocess.kt)
    skulle ge samma NaN för identisk indata. Verifierat ofarligt i praktiken: redan ett par
    LSB brus (mikrofonens eget brusgolv, ~3/32768 amplitud) undviker NaN helt och ger
    normala låga logits (~-7 till -10) — riktig on-device-tystnad/ambient-brus är aldrig
    matematiskt exakt konstant, så detta triggas bara av syntetiska exakt-noll-vektorer.
    """
    lines = [f"- {qid}: {conf:.6f}" for qid, conf in result]
    if any(np.isnan(conf) for _, conf in result):
        lines.append(
            "- ⚠️ NaN confidence — indata har exakt NOLL varians (perfekt tystnad/DC), vilket "
            "gör att modellens interna normalisering delar med noll (verifierat: inte ett fel i "
            "denna postprocess, samma NaN skulle uppstå i Kotlin-pipelinen för identisk indata). "
            "Ordningen ovan är INTE en meningsfull ranking (NaN sorteras odefinierat av Python — "
            "de råkar bara stå i mapping-dictets ursprungsordning). Läs detta som 'ingen giltig "
            "confidence', INTE som en hög/låg confidence. Ett par LSB brus (verkligt mikrofon-"
            "brusgolv) undviker NaN helt — se facit-dokumentets slutnot."
        )
    return lines


def main() -> None:
    mapping = json.loads(MAPPING.read_text())
    # birdnet_lite_to_qid.json:s struktur (verifierad 2026-08-16): topnivå-objekt med
    # "_meta" (coverage-stats) + "mapping" (Map<index-som-sträng, qid-sträng>, SPARSE —
    # bara de 627 mappade indexen finns med som nycklar). Detta är EXAKT samma data
    # BirdNetLabelMapper.parse läser (dto.mapping.mapKeys { it.toInt() }) och samma
    # kontrakt: indexToQid[classIndex] returnerar null (Kotlin) / lookup.get(i) None
    # (Python) för alla omappade index — de finns aldrig som nycklar i dicten.
    lookup = {int(k): v for k, v in mapping["mapping"].items()}
    interp = tf.lite.Interpreter(model_path=str(MODEL))
    interp.allocate_tensors()

    targets = [Path(sys.argv[1])] if len(sys.argv) > 1 else [FIXTURE]
    lines = [
        "# i3 audio — desktop-referensfacit (full TF, Flex inkluderad)",
        "",
        f"Modell: `{MODEL.relative_to(REPO)}` · TF {tf.__version__} · pipeline = normalize → flat_sigmoid(±15) → filter-före-ranking → top-3.",
        "",
    ]
    any_nan = False
    for wav in targets:
        result = top3(load_wav(wav), interp, lookup)
        any_nan = any_nan or any(np.isnan(conf) for _, conf in result)
        lines.append(f"## {wav.name}")
        lines += format_result(result) or ["- (tomt)"]
        lines.append("")
    zeros = top3(np.zeros(144_000, dtype=np.float32), interp, lookup)
    any_nan = any_nan or any(np.isnan(conf) for _, conf in zeros)
    lines.append("## tystnad (144000 nollor)")
    lines += format_result(zeros)
    lines.append("")
    if any_nan:
        lines.append(
            "**Metodnot (NaN):** se `format_result`-docstringen i `reference.py` för den fulla "
            "förklaringen. Kort: exakt-noll-varians-indata (t.ex. tystnadstestet ovan) ger NaN "
            "genom hela grafen — verifierat inte en bugg i den här referensen, men INTE en "
            "meningsfull \"låg confidence\"-signal heller. Bekräftat ofarligt för riktig ljud-ID "
            "eftersom on-device-ljud (även tysta rum) alltid har ett icke-noll brusgolv.",
        )
        lines.append("")
    FACIT.write_text("\n".join(lines))
    print(FACIT)
    print("\n".join(lines[4:]))


if __name__ == "__main__":
    main()
