# Play Console — What's new för vC126 (1.2.1) & vC127 (1.2.2) + uppladdnings-beslutsträd

Plain-text, paste-ready. Console enforcar "What's new" ≤500 tecken per språk —
blocken nedan ligger på ~270–460.

## En eller två uppladdningar? (beslut 2026-08-11)

Play kräver bara **stigande** versionCode — hopp är tillåtna (125 → 127 direkt är
giltigt), och vC127 innehåller allt i vC126 (byggd ovanpå samma `main`). Två
omgångar är alltså **inte nödvändigt**; vC126-first är en fallback/försäkring,
inte ett krav.

**Kör så här i Windows-sittningen:**

1. **`git pull` FÖRST** — Windows-klonen står kvar på i2a-läget (2026-07-18);
   hela recensions-batchen (`e43e5302..c342e8e3`) finns bara på `origin/main`.
2. **Galaxy-verify av vC127** (checklistan i CLAUDE.md Status: auto-stopp utan
   evig Analyzing, Back släcker mic, live-chip, Disambig top-3, felstates,
   språkbyte på API 35, ljud-init hard-gate, 60 s ambient-brus ⇒ inget Match).
3. **Grönt?** → bygg vC127-release (`:androidApp:bundleRelease`), kör
   `python tools/check_16kb_alignment.py` på AAB:n + `zipalign -c -P 16`,
   ladda upp **endast vC127** med 1.2.2-texterna nedan. Klart — en omgång.
4. **Rött (verifyn hittar något)?** → ladda upp den **färdiga vC126-AAB:n**
   samma sittning (`androidApp/build/outputs/bundle/release/androidApp-release.aab`,
   byggd 2026-07-18, redan ELF- + device-verifierad) med 1.2.1-texterna nedan,
   så når 16 KB-fixen + classifier-first-run-fixen användarna medan vC127 åtgärdas.

**Obs vid upload (båda vägarna):**

- 16 KB-varningen i Console ska vara **borta** (vC125 tog en per-version-skip;
  126/127 är själva fixen). Flaggar Play ändå 16 KB: stoppa och undersök — ta
  inte en skip till.
- Bekräfta att Privacy/Terms-fälten pekar på `birdy.community/legal/`
  (pending follow-up #1 i CLAUDE.md).
- Utrullning: full är rimlig (device-verifierade fixar, liten användarbas);
  staged 50 % → 100 % om du vill vara försiktig.

---

# What's new — 1.2.2 (vC127, huvudvägen)

## 🇬🇧 English (max 500)
```
• Pick your language (English/Swedish) right in the intro — and switching language now works on every Android version.
• Sound ID reworked: no more getting stuck on "Analyzing", a live chip shows what Birdy hears while recording, clear error states with retry, smarter matching.
• Photo ID could fail on the very first try after a fresh install — fixed.
• Support for the newest Android devices + upgraded on-device AI engine.
```

## 🇸🇪 Svenska (max 500)
```
• Välj språk (svenska/engelska) direkt i introduktionen — och språkbyte fungerar nu på alla Android-versioner.
• Ljud-ID omarbetat: fastnar inte längre på "Analyserar", ett live-chip visar vad Birdy hör under inspelningen, tydliga fellägen med försök igen, smartare matchning.
• Foto-ID kunde fallera vid allra första försöket efter ny installation — åtgärdat.
• Stöd för de nyaste Android-enheterna + uppgraderad AI-motor på enheten.
```

---

# What's new — 1.2.1 (vC126, ENDAST fallback-vägen)

## 🇬🇧 English (max 500)
```
Stability & compatibility update:
• Photo ID could fail ("Analyzer failed") on the very first try after a fresh install — fixed.
• Full support for the newest Android devices (16 KB memory pages).
• Upgraded on-device AI engine (LiteRT) — same models, faster and future-proof.
```

## 🇸🇪 Svenska (max 500)
```
Stabilitet & kompatibilitet:
• Foto-ID kunde fallera vid allra första försöket efter ny installation — åtgärdat.
• Fullt stöd för de nyaste Android-enheterna (16 KB-minnessidor).
• Uppgraderad AI-motor på enheten (LiteRT) — samma modeller, snabbare och framtidssäkrad.
```
