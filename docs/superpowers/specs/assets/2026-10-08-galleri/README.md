# Skärmarna i galleriet (2026-10-08)

Albin 2026-10-08 kväll: "låt mig välja bilderna till galleriet på Birdy-startsidan". Galleriet är appturen (`website/src/components/AppTour.astro`, skärmarna i `website/src/assets/screens/1.3.0/{sv,en}/`). I dag hänger sex skärmar: Identifiera, Lyssna, Träff, Mina arter, Uppslagsverket, Artprofil (butiksbilderna ur `docs/play-store/screenshots/1.3.0/` på `release/1.3.0`).

- `galleri.html`: väljaren, publicerad privat som https://claude.ai/artifact/EeuvdW3TCeoPeqVNxV1EeF. 17 skärmar (1 till 6 = galleriet i dag), Albin trycker i den ordning de ska hänga och kopierar svaret ("Galleriet: 1, 12, 3 ...").
- `img/s01.jpg` till `s17.jpg`: miniatyrer. Källorna: 1 till 6 butiksbilderna ovan; 7 till 17 enhetsbilderna i `docs/superpowers/screenshots/v1.3/device/` på `release/1.3.0` (`p1-identifiera-dagens-fagel-sv`, `p4-lyssna-inspelning-sv`, `rc-beskarning-talgoxe-sv`, `p5-fynd-talgoxe-anteckning-sv`, `rc-fynddetalj-scrollad-sv`, `rc-veckans-uppslag-sv`, `rc-marken-sv`, `rc-troferum-sv`, `p7-statistik-sv`, `p6-grupp-vadare-sv`, `p1-tack-skarm-sv`).

**Efter valet:** skärmar utanför butiksserien tas om på emulatorn (`pg-api36`, demoläge med ren statusrad, 1080 × 1920, SV och EN) och läggs i `website/src/assets/screens/1.3.0/`; bildtexterna i `copy.{sv,en}.json` (`tour.slides`). Kartan står utanför tills den nya kartstilen finns i appen.
