# Play Console: What's new för vC130 (1.3.0)

Klistras in vid uploaden av **vC130** (produktion). Play tillåter högst 500 tecken per språk;
blocken nedan är räknade med skriptet längst ned (EN 485, SV 490). Inga tankstreck, inga
utropstecken.

Uppladdningen följer runbookens checklista för vC130:
`docs/superpowers/runbooks/2026-05-26-billing-verify-and-go-live.md` (uppdateringen överst).
Köptestbyggena vC128 och vC129 (`1.3.0-koptest`) får aldrig befordras till produktion. vC128 kraschar
dessutom vid start på Android 12 och senare (temat, rättat i `dbdd0148`).

Vid uploaden: bekräfta att Privacy policy-fältet pekar på `https://birdy.community/legal/privacy/`,
och att Data safety-formuläret fortfarande stämmer med `docs/play-store/data-safety-form.md`
(inga svar ändrade i 1.3.0).

---

## 🇬🇧 English, en-US (max 500)
```
• A new look with calmer colours, bigger photos and cleaner pages.
• Premium can now be bought in Google Play, yearly or once for life: the finds map, your journal as a PDF, season statistics and 7 extra stamps.
• Installed Birdy before this update? You keep Premium for free as long as you have the app.
• Sound ID listens for up to 60 seconds, stops when it is sure and shows what it hears.
• Language choice in the intro, Android 16 support, and photo ID now works on the first try.
```

## 🇸🇪 Svenska, sv-SE (max 500)
```
• Nytt utseende med lugnare färger, större foton och renare sidor.
• Premium kan nu köpas i Google Play, per år eller en gång för alltid: Fynd-kartan, fältdagboken som PDF, säsongsstatistik och 7 extra märken.
• Installerade du Birdy före den här uppdateringen? Då behåller du Premium gratis så länge du har appen.
• Ljud-ID lyssnar i upp till 60 sekunder, slutar när det är säkert och visar vad det hör.
• Språkval i introt, stöd för Android 16 och foto-ID fungerar nu redan första gången.
```

---

## Butikstexten (lång beskrivning)

Den långa beskrivningen har ändrats på flera ställen i `store-listing-sv.md` och
`store-listing-en.md`, så **hela den långa beskrivningen ska klistras in på nytt** (båda
språken) i samma upload:

- Stycket SKANNA/SCAN: "3-sekunders klipp" / "3-second clip" stämmer inte i 1.3.0.
- Stycket LÄR/LEARN (juridikgenomgången 2026-10-07, 7i-fix D): marginalanteckningarna är borta
  (bara 4 av 839 arter har en) och texterna "bygger på Wikipedia" / "are based on Wikipedia" i
  stället för "text från Wikipedia".
- Alla tankstreck i den långa beskrivningen är ersatta med kolon, kommatecken eller punkt.
  Apptiteln får kolon i stället för tankstreck (Albin 2026-10-08): "Birdy: Fågel-ID & Guide" och
  "Birdy: Bird Identify & Guide". Orden är desamma, så sökningen i Play påverkas inte.

Längd: SV 2 412 tecken, EN 2 451 (gränsen är 4 000). Den korta beskrivningen är oförändrad.

## Räkna tecknen

```bash
python - <<'EOF'
import re, pathlib
text = pathlib.Path("docs/play-store/console-paste-v1.3.0.md").read_text(encoding="utf-8")
for block in re.findall(r"## .*?\(max 500\)\n```\n(.*?)\n```", text, re.S):
    assert len(block) <= 500 and not re.search("[\\u2013\\u2014!]", block), block
    print(len(block))
EOF
```
