# Birdy Bird Scanner — arbetsguide för Claude Code

> **Den här filen läses automatiskt av Claude Code i varje session.** Den ger sammanhang för vad projektet är, var du hittar saker, och hur vi arbetar tillsammans.

## Vad är detta?

AI-driven Android-app för fågelidentifiering. Realtidsskanning via kamera + foto-upload + uppslagsverk över ~700 europeiska arter. Kotlin Multiplatform + Compose Multiplatform. v1 = Android-only ("Skanna & lär"); senare faser lägger till dagbok, gamification, karta, push, community, iOS.

**Status (2026-05-01):** Plan 1 (Foundation) Tasks 1–11 ✅ committade och CI grönt på `main`. Task 12 (visuell on-device-verifiering + tag `v0.1.0-foundation`) återstår. Specs/plans-driven utveckling — vi diskuterar i brainstorming, skriver spec, skriver implementationsplan, sen exekverar.

## Var hittar du saker

| Vad | Var |
|---|---|
| Designspec för v1 | `docs/superpowers/specs/2026-04-30-birdy-bird-scanner-v1-design.md` |
| Implementationsplaner | `docs/superpowers/plans/YYYY-MM-DD-v1-NN-<phase>.md` |
| Skärmdumpar per milstolpe | `docs/superpowers/screenshots/` |
| Visuellt språk (Mossbädd-paletten) | I auto-memory: `visual_language_birdy_v1.md`, sammanfattat nedan |
| Den här guiden | `CLAUDE.md` (du läser den nu) |
| Auto-memory (lokalt, inte i repo) | `~/.claude/projects/C--Users-.../memory/` |

## Plan-of-plans (v1)

| # | Plan | Status |
|---|---|---|
| 1 | Foundation — KMP-bootstrap, Compose, CI, Mossbädd-tema | **Tasks 1–11 klara, Task 12 (verifiering+tag) återstår** |
| 2 | Content pipeline (species data → species.db) | Nästa |
| 3 | Encyclopedia (browse + species profile) | |
| 4 | ML & Camera (TFLite + CameraX) | |
| 5 | Diary & Gamification | |
| 6 | i18n, polish, Play Store-release | |

Varje plan ska lämna projektet i ett byggbart, testbart tillstånd. Plan 1-12 är alltid sant: `./gradlew build` ska gå grönt.

## Hur vi jobbar (Claude Code-flödet)

### När du börjar en ny session

1. Säg "Vi fortsätter med birdy-bird-scanner" eller liknande.
2. Claude läser denna fil + auto-memory automatiskt.
3. Be om en statusöversikt: "Var står vi?" → Claude kollar git log, senaste commit, vilken plan/task som är aktiv.
4. Bestäm nästa steg utifrån status.

### Behövs superpowers?

**Ja, för dessa moment:**
- **Brainstorming av nya features eller större ändringar** → invoke `superpowers:brainstorming`
- **Skriva ny implementationsplan från en spec** → invoke `superpowers:writing-plans`
- **Exekvera en plan task-by-task med subagents + review** → invoke `superpowers:subagent-driven-development` (rekommenderat)
- **Exekvera en plan inline (utan subagents)** → invoke `superpowers:executing-plans`

**Nej, för dessa moment:**
- Vanliga frågor om kod ("vad gör den här filen?")
- Snabba bugfixar eller justeringar
- Att läsa specs/planer
- Mindre refactoring där hela kontexten ryms i ett samtal

Tumregeln: om du startar något som tar mer än ett samtal eller kräver disciplin (TDD-cykel, plan-tracking), använd skills. Annars bara prata.

### Modell-strategi (vilken Claude för vad)

| Uppgift | Modell |
|---|---|
| Brainstorming, designbeslut, arkitektur | **Opus 4.7** (denna session är ett exempel) |
| Skriva implementationsplaner | Opus 4.7 |
| Code review mellan tasks | Opus 4.7 |
| Exekvera enskilda plan-tasks (subagents) | **Sonnet 4.6** (snabb + tillräckligt smart för TDD-cykler) |
| Snabba lookups, formattering | Haiku 4.5 |

`subagent-driven-development` skickar normalt subagents till Sonnet 4.6 för execution och låter huvud-tråden (Opus 4.7) granska resultatet mellan tasks. Det ger snabb iteration utan att tappa noggrannhet.

### Subagent-driven execution-flöde

När en plan körs med `subagent-driven-development`:

1. Huvudtråden (Opus 4.7) dispatchar en subagent (Sonnet 4.6) för **en task** ur planen.
2. Subagenten implementerar tasken: skriver test → kör test → implementerar → kör test igen → committar.
3. Huvudtråden granskar diffen och plan-progressionen.
4. Du (användaren) kan stoppa, ändra riktning, eller låta nästa task dispatchas.
5. Repetera tills planen är klar.

**Vid avbrott:** all progress är committad i git. Nästa session kan fortsätta från senaste commit utan tappad kontext.

## Visuellt språk (Mossbädd)

Färgpalett (locked 2026-04-30):

| Token | Hex | Roll |
|---|---|---|
| Background | `#E8E2D2` | Pale moss-creme |
| Hero top | `#5C6E48` | Mossgrön |
| Hero deep | `#3F4F30` | Djup moss |
| Hero shadow | `#2A3520` | Skuggmoss |
| Accent | `#8C5A3C` | Koppar (CTA, aktiv flik, stat-siffror) |
| Stat surface | `#D8D0BC` | Sand-creme |
| Text primary | `#2A3525` | Djup skog |
| Text on hero/accent | `#F0EAD8` | Varm offwhite |

**Typografi:** Crimson Pro (serif) för rubriker/siffror; system sans för UI. UPPERCASE etiketter med spärr.

**Layout-principer:**
- Hero är en *zon*, inte ett kort — fade-out vertikal gradient mot bg.
- CTA i koppar plockar upp samma accent som ekar i siffror och aktiv flik.
- Bottom-bar 72dp med ikon + textetikett per flik.

Tema-tokens implementeras i `composeApp/.../ui/theme/Color.kt` och `Type.kt` (Plan 1 Task 6).

## Tekniska val (en rad var)

- **Stack:** KMP + Compose Multiplatform (Android första, iOS-skelett)
- **DB:** SQLDelight 2.x med Flow-baserade queries
- **ML:** TensorFlow Lite (on-device, MobileNetV3-Large eller EfficientNet-Lite0/1)
- **Kamera:** CameraX (Android), 3 fps streaming, confidence threshold 0.35
- **Språk:** SV + EN, Sverige först
- **Distribution:** Play Asset Delivery för stora bundles
- **CI:** GitHub Actions (ktlint, detekt, unit tests, assembleDebug, APK-artefakt)
- **Statisk analys:** ktlint 12.1.2 + detekt 1.23.7

## Lokal utvecklingsmiljö (Windows + Galaxy S23 Ultra)

Allt verktyg är installerat och konfigurerat:

| Vad | Var |
|---|---|
| JDK 21 (Temurin) | `C:\Java\OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10\jdk-21.0.11+10\` |
| Android SDK (platform-35, build-tools 34/36/37) | `C:\Users\abbea\AppData\Local\Android\Sdk` |
| ADB | `C:\Users\abbea\AppData\Local\Android\Sdk\platform-tools\adb.exe` |
| `local.properties` (gitignored) | redan satt med `sdk.dir` |
| Telefon | SM-S918B (Galaxy S23 Ultra), USB-felsökning på, RSA-auktoriserad |

**Standard-prefix för alla `./gradlew`-kommandon i bash:**

```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"
```

(Annars hittar Gradle inte Java på vägen.)

## Vanliga kommandon

```bash
# Bygga + installera på ansluten enhet
./gradlew :androidApp:installDebug

# Starta appen efter installation
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity

# Verifiera ADB ser enheten
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" devices

# Tester (snabba — bara delade moduler på JVM)
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest

# Lint + statisk analys
./gradlew ktlintCheck detekt
./gradlew ktlintFormat   # autofix
```

## Repo

GitHub: https://github.com/anonadrek/birdy

Branches: `main` är default. Plan-arbete sker på `main` med små commits per task; tagga milstolpar (`v0.1.0-foundation` osv).

## Beslut & ramar

- **Scope:** v1 = "Skanna & lär" + uppslagsverk. Inget mer (dagbok, mål, etc kommer i Plan 5+).
- **Geografi:** Norden/Europa, ~700 arter.
- **Användare:** Bred två-lager (nybörjare som vill lära sig + entusiaster som vill scanna i fält).
- **AI:** On-device, ingen backend för inference. Migrationsdata och sannolikhet är art-nivå statisk i v1.
- **Solo-utvecklare:** användaren bygger detta själv via Claude Code. Granskning sker av användaren mellan tasks.

## Frågor under arbetet

Om en plan-task är otydlig eller kräver beslut, **stoppa och fråga** istället för att gissa. Bättre att pausa fem minuter än att rulla med fel antagande i tre tasks.

Om du upptäcker en spec-motsägelse, lyft det direkt — vi uppdaterar specen tillsammans.

## Autonomi-direktiv (gäller löpande)

Användaren har sagt: **"Don't ask me for permission to run anything."** Det betyder:

- Kör operationer som planen specar utan att be om bekräftelse — commits, push, gradle-kommandon, file-edits enligt task.
- Vid scope-creep eller spec-avvikelse upptäckta i review: fixa autonomt (t.ex. soft-reset + re-commit för att splitta scope-creepiga commits).
- Det betyder INTE att ignorera blockerare som kräver fysisk åtkomst (telefon, emulator) eller tredjepartsbeslut (CI-resultat) — där rapporterar man status och väntar.
- Det betyder INTE heller att hoppa över granskning. Två-stegs-review (spec → kvalitet) körs alltid mellan tasks per `subagent-driven-development`.

## Återstående för Plan 1 (vid sessionsstart)

Plan 1 Task 12 är en manuell verifierings-grind. Om Task 12 inte är klar:

1. Säkerställ att appen är installerad (`./gradlew :androidApp:installDebug` med JAVA_HOME satt — telefon ansluten med USB-debug).
2. Användaren tittar på enheten och bekräftar Mossbädd-paletten + texterna.
3. Ta en skärmdump: `adb exec-out screencap -p > docs/superpowers/screenshots/2026-04-30-foundation-home.png`
4. Commit: `git add docs/superpowers/screenshots/ && git commit -m "docs: add foundation milestone screenshot"`
5. `git push`
6. Tagg: `git tag v0.1.0-foundation && git push --tags`

Sen är Plan 1 klar och vi går till Plan 2 (Content pipeline).
