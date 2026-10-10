# Play Console: What's new för vC130 (1.3.1, gratis)

Klistras in vid uploaden av **vC130** (produktion). **1.3.1 släpps utan betalning** (Albin
2026-10-09): Premium är öppet för alla som i 1.2 (`PREMIUM_OPEN_FOR_LAUNCH=true`, brytpunkt 0 så
att ingen tackskärm för tidiga användare visas; beviset för installationen sparas ändå). Betalningen
slås på i en senare release när BirdNET har svarat, och då sätts brytpunkten till 1.3.1:s go-live
plus 48 timmar (webbens löfte: "använde du Birdy före version 1.3"). Köptestet med vC129 behövs inte
för den här uploaden. Köptestbyggena vC128 och vC129 (`1.3.0-koptest`) får aldrig befordras till
produktion.

Play tillåter högst 500 tecken per språk; räkna med skriptet längst ned. Inga tankstreck, inga
utropstecken.

Vid uploaden:

- Privacy policy-fältet pekar på `https://birdy.community/legal/privacy/`.
- Data safety-formuläret stämmer fortfarande med `docs/play-store/data-safety-form.md`.
- **Hela den långa beskrivningen klistras in på nytt** (båda språken) ur `store-listing-sv.md` och
  `store-listing-en.md`: ändringarna från 1.3.0 (se `console-paste-v1.3.0.md`) plus Premium-stycket,
  som nu säger "gratis tills vidare" / "free for now" i stället för engångsköp eller abonnemang.
- Stegvis utrullning: 20 % först, sedan 100 % efter ungefär ett dygn om krascher och ANR i Android
  vitals ser normala ut.

---

## 🇬🇧 English, en-US (max 500)
```
• A new look with calmer colours, bigger photos and cleaner pages.
• Change the language in the intro or in Settings, now on every Android version.
• Sound ID listens for up to 60 seconds, stops when it is sure and shows what it hears.
• Camera fixes: photo ID works on the first try, scanned photos are saved the right way up and the camera turns off when you leave the scanner.
• Android 16 support. Premium stays free for everyone for now.
```

## 🇸🇪 Svenska, sv-SE (max 500)
```
• Nytt utseende med lugnare färger, större foton och renare sidor.
• Byt språk i introt eller i Inställningar, nu på alla Android-versioner.
• Ljud-ID lyssnar i upp till 60 sekunder, slutar när det är säkert och visar vad det hör.
• Kamerafixar: foto-ID fungerar redan första gången, skannade foton sparas rättvända och kameran stängs av när du lämnar skannern.
• Stöd för Android 16. Premium är fortsatt gratis för alla tills vidare.
```

## Räkna tecknen

```bash
python - <<'EOF'
import re, pathlib
text = pathlib.Path("docs/play-store/console-paste-v1.3.1.md").read_text(encoding="utf-8")
for block in re.findall(r"## .*?\(max 500\)\n```\n(.*?)\n```", text, re.S):
    assert len(block) <= 500 and not re.search("[\\u2013\\u2014!]", block), block
    print(len(block))
EOF
```
