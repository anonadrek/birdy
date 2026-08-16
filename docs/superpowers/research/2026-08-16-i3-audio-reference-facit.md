# i3 audio — desktop-referensfacit (full TF, Flex inkluderad)

Modell: `composeApp/src/androidMain/assets/models/birdnet_lite_v2.tflite` · TF 2.19.0 · pipeline = normalize → flat_sigmoid(±15) → filter-före-ranking → top-3.

## chirp_3s_48k.wav
- Q143313: 0.008854
- Q25469: 0.000196
- Q726873: 0.000191

## tystnad (144000 nollor)
- Q20754771: nan
- Q25380: nan
- Q715819: nan
- ⚠️ NaN confidence — indata har exakt NOLL varians (perfekt tystnad/DC), vilket gör att modellens interna normalisering delar med noll (verifierat: inte ett fel i denna postprocess, samma NaN skulle uppstå i Kotlin-pipelinen för identisk indata). Ordningen ovan är INTE en meningsfull ranking (NaN sorteras odefinierat av Python — de råkar bara stå i mapping-dictets ursprungsordning). Läs detta som 'ingen giltig confidence', INTE som en hög/låg confidence. Ett par LSB brus (verkligt mikrofon-brusgolv) undviker NaN helt — se facit-dokumentets slutnot.

**Metodnot (NaN):** se `format_result`-docstringen i `reference.py` för den fulla förklaringen. Kort: exakt-noll-varians-indata (t.ex. tystnadstestet ovan) ger NaN genom hela grafen — verifierat inte en bugg i den här referensen, men INTE en meningsfull "låg confidence"-signal heller. Bekräftat ofarligt för riktig ljud-ID eftersom on-device-ljud (även tysta rum) alltid har ett icke-noll brusgolv.
