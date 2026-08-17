# i3 (iOS ljud-ID) — slutreviewens triage-tabell + sessionens rulings

> **Proveniens:** i3-sessionens slutreview (fable, range `8a54c7bf..e2294dd1`) och slutrapport
> levererades 2026-08-16 enbart i sessionskontexten; återhämtade ur sessionstranskriptet
> 2026-08-17 och arkiverade här så att båda maskinerna kan läsa dem (CLAUDE.md:s placeringsregel).
> Fixvågen `a13c2cd3` åtgärdade allt märkt FIX FÖRE MERGE + de två REKOMMENDERADE (scoped
> re-review: alla 9 fynd ADDRESSED). "T<N> m<M>"-etiketterna refererar i3-ledgerns per-task-minors
> (ledgern är inte bevarad — beskrivningarna i tabellen är den kvarvarande definitionen).

## Status efter fixvågen + i4

- **Important 1** (AVAudioEngineConfigurationChangeNotification — mic-byte/BT mitt i inspelning gav frusen timer): ✅ fixad i slutvågen, device-verifieras i grind 2.
- **Minor 2** (`.ended`-interruption-guard), **3** (normalize-iosTest), **4** (NSLog-degrade-logg), **5** (@Volatile-publicering) + doc-passet (T7 m5, T4 m9–m10): ✅ fixade i slutvågen.
- **T3 m5** (NSDataReadingMappedIfSafe) + **T7 m2** (ioDispatcher-återbruk): ✅ togs som valfria i samma våg.
- **T4 m6** (NotifyOthersOnDeactivation — duckad musik återupptas): ✅ åtgärdad senare av **i4 T13** (`21592cf0`).
- Allt under "FÖRBLIR DEFERRED" nedan är fortsatt öppet (medvetet).

## Ledger-triage (slutreviewens beslut, ordagrant)

| Grupp | Beslut | Motiv |
|---|---|---|
| T7 m1 (degrade-logg println) | **FIX FÖRE MERGE** | Den utpekade triage-raden för båda grindarna; `t.message` kan vara null (Minor 4). |
| T4 m2–m3 (@Volatile + registreringsfönster) | **FIX FÖRE MERGE** | Ett nyckelord per fält; normal-finalize korsar tråd på fältet (Minor 5). |
| T7 m5 (doc-drift `AppGraph.kt:124/138/144` "non-Android targets" + `AudioClassifierBootstrap`-KDoc:s MainActivity-referens) | **FIX FÖRE MERGE** | Doc-only; tre KDoc-påståenden bevisligen falska sedan T7 wirade iOS. |
| T4 m9–m10 (AudioRecorderApi-KDoc "IO-tråd" vs main-queue-interruption; fel start-fel-mening) | **FIX FÖRE MERGE** (samma doc-pass) | Kontraktsdok som nästa plattformsimplementer läser ordagrant. |
| T3 m5 (54 MB dubbel-kopia; `NSDataReadingMappedIfSafe`) | **REKOMMENDERAD, ej blockerande** | 1-radsändring halverar transient peak per load-försök (inkl. varje sim-retry). |
| T7 m2 (ioDispatcher-återbruk) | **REKOMMENDERAD, ej blockerande** | Raderar det skakiga "Dispatchers.IO is internal on K/N"-versionspåståendet; noll beteendeskillnad. |
| T1 m1–m4, m6 (curl-timeout, rm-rf-ordning, markörfil, sim-fetch, ociterad `$(SRCROOT)`) | FÖRBLIR DEFERRED | SHA-gaten garanterar integritet; självläkande vid nästa körning; repo-sökvägen saknar space. |
| T1 m5 | REDAN SJÄLVLÄKT | project.yml-kommentaren stämmer sedan T8. |
| T1 m7 (`TensorFlowLiteSelectTfOps.bundle` utan Info.plist) | FÖRBLIR DEFERRED | Rätt grind är första TestFlight-upload (**i6**) — bokförd där. |
| T3 m2–m4, m6 + options-fönster + close-runCatching | FÖRBLIR DEFERRED | m4 (close-UAF) verifierat neutraliserad av T7-designen (ingen close-väg existerar på iOS); övriga kosmetik/onåbara/pre-existerande mönster. |
| T4 m1 (hör-chip ~10 Hz), m5 (FIFO-serialisering cap-vägen), m6 (NotifyOthersOnDeactivation) | FÖRBLIR DEFERRED → m6 ✅ i4 T13 | m1+m5 är exakt vad grind 2 finns för (ligger i checklistan); dubbel-`removeTapOnBus` verifierat no-op-säkert. |
| T4 m4 (TOCTOU), m7 (rms-testfall), m8 (sub-chunk-svans) | FÖRBLIR DEFERRED | Android-paritet/inert; m8 matematiskt onåbar (verifierat 1600×1800 = exakt cap-gräns). |
| T5 (colorSpace-null, pixeltest), T6 (dubbel mappningstabell, ternär), T7 m3/m4/m6, T8 m1, T9 (627-assert, lint) | FÖRBLIR DEFERRED | Kosmetik, brief-scopat eller plan-mandaterat (T7 m4:s jetsam-notis rätt bokförd). |

## Slutreviewens Important-fynd (för sammanhang)

**Important 1 — mic-förlust via engine-konfigurationsändring signalerades inte** (`IosAudioRecorder.kt`):
endast `AVAudioSessionInterruptionNotification` observerades, men när input-hårdvaran byts mitt i
inspelning (BT-öronsnäckor auto-ansluter) stoppar AVAudioEngine sig själv och postar
`AVAudioEngineConfigurationChangeNotification` — tap-callbacks upphör tyst → frusen timer/rms,
stopp-knappen död < 3 s. Spec §D listade "mic-förlust" som tredje felkällan; planens verbatim-kod
täckte bara två. **Plan-defekt, inte implementer-slarv.** Fix: observer med `object = engine` →
`fireError`. Grind 2-punkt: BT-anslutning mitt i inspelning ⇒ `RecordingFailed`, INTE frusen timer.

## Sessionens 12 rulings (ur slutrapporten, komprimerade)

1. Kört på `main` utan worktree — CLAUDE.md:s konvention.
2. T5-briefens döda testrad ströks (scaffolding).
3. **Storleksgaten: PASS på shippad storlek** (Release + strip = 88,1 MB ≤ 150 MB) trots röd Debug-mätning i plan-texten — spec:ens definition ("slutbinären efter strip/thinning") bindande. Väg B (selektiv bazel-build, ger även sim-slice) kvar som dokumenterad fallback.
4. T3:s init-läcke-fynd stod trots att planens kod manderade läckan — spegelprincipen i spec:en vann; fixrunda kördes.
5. T3:s close-inflight-UAF-minor parkerades — neutraliserad av att iOS-grafen aldrig stänger klassificeraren; blir verklig först om en close-väg införs utan atomisk getAndSet.
6. Explicit IO-dispatcher i T7: Dispatchers.IO otillgänglig på K/N → explicit Default → repots `ioDispatcher`-actual i slutvågen.
7. T4:s två concerns klassades som korrekthet → fixades före review.
8. T9 kördes parallellt med T8-reviewen (disjunkta filytor).
9. Slutreviewens triage accepterades i sin helhet (fix-före-merge + de två valfria; resten deferred).
10. Re-reviewerns closure-guard-förslag parkerades som redundant — `fireError` guardar redan `terminated` som första rad.
11. Negativ testpunkt (ren manuell stopp ⇒ aldrig spurious `RecordingFailed`) lades i grind 2.
12. finishing-a-development-branch-ceremonin ersattes av repo-konventionen (main + push).
