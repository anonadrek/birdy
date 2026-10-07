# Image and media sources

Where every image, sound, font, model and data file in this repo comes from, and under which licence it is used.
Last checked 2026-10-07 (release 1.3.0). Not legal advice.

When you add a file of this kind, add a row here (or in the folder's own `SOURCES.md` or `LICENSE.md`).

## Sets with their own records

| What | Where | Licence and credit |
|---|---|---|
| Species photos in the app (2 066 files) | `shared/content/images/`, `asset-pack/src/main/assets/images/` | CC0, public domain, CC BY 2.0/3.0/4.0 or CC BY-SA 2.0/3.0/4.0 from Wikimedia Commons. Photographer, licence and Commons file per photo in `shared/content/species/**/<QID>.yaml` (`image_refs`); shown in the app under each photo and under Settings, About, Photo credits. Resized, converted to sRGB and WebP. |
| Species texts in the app | `shared/content/species/` | CC BY-SA 4.0, based on Wikipedia, see `shared/content/species/LICENSE.md`. |
| Species data and texts on the website | `website/src/data/species/`, `website/src/data/comparisons/` | See the `LICENSE.md` in each folder. |
| Photos on the website | `website/src/assets/photos/` | CC0 or public domain, see `SOURCES.md` there. |
| IOC World Bird List v14.1 | `tools/content-pipeline/sources/ioc-14.1.xlsx` | CC BY 3.0, see `tools/content-pipeline/sources/LICENSE.md`. |
| BirdLife Sverige's Western Palearctic list v11 | not in the repo | No stated licence, so it is downloaded at run time (`tools/content-pipeline/src/birdy_fetcher/vp11_source.py`). |
| Photo ID model AIY Birds V1 | `shared/ml/src/commonMain/composeResources/files/ml/aiy_birds_v1.tflite`, `aiy_labelmap.csv` | Google, Apache License 2.0, from TF Hub. Notice: `tools/licenses/notices/aiy-birds-v1.txt`. |
| Sound ID model BirdNET-Lite | `composeApp/src/androidMain/assets/models/birdnet_lite_v2.tflite` | K. Lisa Yang Center for Conservation Bioacoustics, Cornell Lab of Ornithology, CC BY-NC-SA 4.0, used unchanged. Notice: `tools/licenses/notices/birdnet-lite.txt`. This is why sound ID is always free in the app. |
| Model mappings | `shared/ml/src/commonMain/composeResources/files/ml/aiy_to_qid.json`, `birdnet_lite_to_qid.json` | Our own mapping from model labels to Wikidata IDs (Wikidata is CC0). |
| `species.db` | `composeApp/src/commonMain/composeResources/files/species.db` | Built from `shared/content/` (rows above). |
| Fonts in the app | `composeApp/src/commonMain/composeResources/font/`, `shared/pdf/src/androidMain/assets/fonts/` | Caveat and DM Serif Display, SIL Open Font License 1.1. Licence text and copyright lines in the app (Settings, About, Open-source licenses; `composeResources/files/licenses/ofl-1.1.txt`). |
| Fonts on the website | `website/public/fonts/` | Caveat, DM Serif Display and Inter, SIL Open Font License 1.1, see `website/public/fonts/OFL.txt`. |
| TensorFlow Lite C for iOS | `iosApp/Frameworks/TensorFlowLiteC.xcframework` | Google, Apache License 2.0, unmodified, see `iosApp/Frameworks/LICENSE-TensorFlowLiteC.txt`. |
| Gradle wrapper | `gradle/wrapper/gradle-wrapper.jar` | Gradle, Apache License 2.0. |

## Birdy's own artwork

| Files | Source |
|---|---|
| The launcher icon bird: `androidApp/src/main/res/mipmap-*/ic_launcher*.png`, `androidApp/src/main/res/drawable-nodpi/ic_launcher_monochrome.png`, `composeApp/src/androidMain/res/drawable-nodpi/ic_launcher_monochrome.png`, `docs/play-store/ic_launcher_512.png` | **Made with AI** (Albin Abrahamsson, 2026-10-07: "We did it using AI"). The tool is not recorded. Added in `428fd74b` (2026-05-30), edges sharpened in `f92a2bed`. Owned by AlbIT AB. |
| The flying bird silhouette: `composeApp/src/commonMain/composeResources/files/branding/hero_bird.png`, `androidApp/src/main/res/drawable-nodpi/splash_icon.png` (made from it in `22402a0d`), `website/public/coverage/seal-bird.png`, `website/public/brand/birdy-bird.png` (mask made from it), `docs/superpowers/icon-concepts/final/ic_launcher_512.png`, `docs/superpowers/specs/assets/2026-09-24-website-1-3-lyft/bird.png` | Added in `0c761131` (2026-05-22) as the source of the earlier launcher icon. Albin's design according to the commits; whether AI was used is not recorded (**open: Albin to confirm**). |
| Website favicons and brand files: `website/public/favicon*`, `apple-touch-icon.png`, `og.png`, `website/src/assets/wing-logo-*.svg`, `wordmark-2048.png` | Albin's brand folder (`caa33cb1`, `76f861a0`). Same open question as the row above. `og.png` is no longer used by the site. |
| Icon concepts and the final SVG set: `docs/superpowers/icon-concepts/**/*.svg` | Drawn as SVG in the repo (`d1a20800`, `a5c00960`). Own work. |
| Map pin seals, Premium icons, badge seals | Drawn in code. Own work. |

## AI-generated images

| Files | Source |
|---|---|
| `website/src/assets/hero-robin.webp` | **AI-generated robin.** The tool is not known (Albin, 2026-10-07). Added in `46b80fbb` (2026-09-24). |
| Made from it: `website/src/assets/hero/{robin-plate.webp,robin-layer.png,phone-robin.webp}`, `website/public/og-field-{sv,en}.png` (`website/tools/generate-og.mjs`), and in `docs/superpowers/specs/assets/2026-09-24-website-1-3-lyft/`: `robin-hero.jpg`, `robin-plate.jpg`, `robin-layer.webp`, `rodhake-utklipp/*` | Cut out and recoloured with the scripts in `rodhake-utklipp/` (`mask-isnet-general-use.png` is the cut-out mask). |
| The launcher icon bird | See "Birdy's own artwork". |

## Copies of species photos outside the photo set

These are copies (resized or cropped) of Wikimedia Commons photos that the app also uses or used. The CC BY and CC BY-SA ones are resized, which counts as a change; the CC BY-SA ones are shared under the same licence.

**App and tests**

| File | QID | Photographer | Licence | Commons file |
|---|---|---|---|---|
| `composeApp/src/androidDebug/assets/benchmark/talgoxe.jpg` (debug builds only) | Q25485 | Hobbyfotowiki | CC0 | [Great tit (Parus major), North Rhine-Westphalia.jpg](https://commons.wikimedia.org/wiki/File:Great_tit_(Parus_major),_North_Rhine-Westphalia.jpg) |
| `composeApp/src/androidDebug/assets/benchmark/blames.jpg` (debug builds only) | Q25404 | Jojovavasasa | CC0 | [Blue Tit (Cyanistes caeruleus) on bird feeder 06.jpg](https://commons.wikimedia.org/wiki/File:Blue_Tit_(Cyanistes_caeruleus)_on_bird_feeder_06.jpg) |
| `composeApp/src/androidDebug/assets/benchmark/koltrast.jpg` (debug builds only) | Q25234 | Musicaline | CC BY-SA 4.0 | [Turdus merula 1294.jpg](https://commons.wikimedia.org/wiki/File:Turdus_merula_1294.jpg) |
| `shared/ml/src/commonMain/composeResources/files/testdata/parity_Q180991.jpg` | Q180991 | Hobbyfotowiki | CC0 | [Goosander (Eurasian) (Mergus merganser).jpg](https://commons.wikimedia.org/wiki/File:Goosander_(Eurasian)_(Mergus_merganser).jpg) |
| `composeApp/src/commonMain/composeResources/files/premium/great-tit-hero.jpg` | Q25485 | Hobbyfotowiki | CC0 | Same as the first row; see `great-tit-hero-attribution.txt` next to it. |

**Photo ID test corpus** (`tools/ml-eval/corpus/`: the hero photos of 2026-05-08, copied by `tools/ml-eval/scripts/build_corpus.py`; matched by file hash against commit `c58363e2`)

| File | QID | Photographer | Licence | Commons file |
|---|---|---|---|---|
| `accipitridae/Q25380.jpg` | Q25380 | caroline legg | CC BY 4.0 | [Sparrowhawk - Accipiter nisus 2.jpg](https://commons.wikimedia.org/wiki/File:Sparrowhawk_-_Accipiter_nisus_2.jpg) |
| `accipitridae/Q25385.jpg` | Q25385 | Hobbyfotowiki | CC0 | [Buteo buteo insularum, Azores.jpg](https://commons.wikimedia.org/wiki/File:Buteo_buteo_insularum,_Azores.jpg) |
| `accipitridae/Q26407.jpg` | Q26407 | David A Mitchell from Calgary, Canada | CC BY 2.0 | [Buteo lagopus (45356696964).jpg](https://commons.wikimedia.org/wiki/File:Buteo_lagopus_(45356696964).jpg) |
| `accipitridae/Q26431.jpg` | Q26431 | Nikolam993 | CC BY-SA 4.0 | [Eja močvarica (Circus aeruginosus).jpg](https://commons.wikimedia.org/wiki/File:Eja_močvarica_(Circus_aeruginosus).jpg) |
| `aegithalidae/Q170831.jpg` | Q170831 | Membeth | CC0 | [Parks.Range.Aegithalos.caudatus.caudatus.jpg](https://commons.wikimedia.org/wiki/File:Parks.Range.Aegithalos.caudatus.caudatus.jpg) |
| `alaudidae/Q25961.jpg` | Q25961 | Alun Williams333 | CC BY-SA 4.0 | [Alauda arvensis Ehedydd.jpg](https://commons.wikimedia.org/wiki/File:Alauda_arvensis_Ehedydd.jpg) |
| `alcedinidae/Q79915.jpg` | Q79915 | Rison Thumboor from Thrissur, India | CC BY 2.0 | [Common Kingfisher (Alcedo atthis) നീലപ്പൊന്മാന്‍. (25114264257).jpg](https://commons.wikimedia.org/wiki/File:Common_Kingfisher_(Alcedo_atthis)_നീലപ്പൊന്മാന്‍._(25114264257).jpg) |
| `alcidae/Q21062.jpg` | Q21062 | Hobbyfotowiki | CC0 | [Common guillemot (Uria aalge).jpg](https://commons.wikimedia.org/wiki/File:Common_guillemot_(Uria_aalge).jpg) |
| `alcidae/Q212055.jpg` | Q212055 | Jacob Spinks from England | CC BY 2.0 | [Cepphus grylle, Oban Harbour 1.jpg](https://commons.wikimedia.org/wiki/File:Cepphus_grylle,_Oban_Harbour_1.jpg) |
| `alcidae/Q27102.jpg` | Q27102 | lwolfartist | CC BY 2.0 | [Alca torda DSC 5788.jpg](https://commons.wikimedia.org/wiki/File:Alca_torda_DSC_5788.jpg) |
| `anatidae/Q180991.jpg` | Q180991 | Hobbyfotowiki | CC0 | [Goosander (Eurasian) (Mergus merganser).jpg](https://commons.wikimedia.org/wiki/File:Goosander_(Eurasian)_(Mergus_merganser).jpg) |
| `anatidae/Q189609.jpg` | Q189609 | lwolfartist | CC BY 2.0 | [Mergus serrator DSC 5939.jpg](https://commons.wikimedia.org/wiki/File:Mergus_serrator_DSC_5939.jpg) |
| `anatidae/Q25348.jpg` | Q25348 | Alexis Lours | CC BY 4.0 | [Mallard (Anas platyrhynchos) male head.jpg](https://commons.wikimedia.org/wiki/File:Mallard_(Anas_platyrhynchos)_male_head.jpg) |
| `anatidae/Q25402.jpg` | Q25402 | Roger Culos | CC BY-SA 4.0 | [Cygnus olor MHNT.ZOO.2010.11.11.2.jpg](https://commons.wikimedia.org/wiki/File:Cygnus_olor_MHNT.ZOO.2010.11.11.2.jpg) |
| `anatidae/Q25450.jpg` | Q25450 | Hobbyfotowiki | CC0 | [Flock Of Northern Pintail Ducks (Anas acuta).jpg](https://commons.wikimedia.org/wiki/File:Flock_Of_Northern_Pintail_Ducks_(Anas_acuta).jpg) |
| `apodidae/Q25377.jpg` | Q25377 | TRinaud | CC BY 4.0 | [Common swift (Apus apus) 2024 3.jpg](https://commons.wikimedia.org/wiki/File:Common_swift_(Apus_apus)_2024_3.jpg) |
| `ardeidae/Q25273.jpg` | Q25273 | Jebulon | CC0 | [Héron cendré Ardea cinerea Tiergarten Schönbrunn.jpg](https://commons.wikimedia.org/wiki/File:Héron_cendré_Ardea_cinerea_Tiergarten_Schönbrunn.jpg) |
| `bombycillidae/Q26135.jpg` | Q26135 | Аимаина хикари | CC0 | [Bombycilla garrulus Kiev1.jpg](https://commons.wikimedia.org/wiki/File:Bombycilla_garrulus_Kiev1.jpg) |
| `calcariidae/Q208703.jpg` | Q208703 | U.S. Fish and Wildlife Service | Public domain | [Lapland Longspur (Calcarius lapponicus).jpg](https://commons.wikimedia.org/wiki/File:Lapland_Longspur_(Calcarius_lapponicus).jpg) |
| `calcariidae/Q26416.jpg` | Q26416 | Estormiz | CC0 | [Plectrophenax nivalis Oulu 20140406 03.JPG](https://commons.wikimedia.org/wiki/File:Plectrophenax_nivalis_Oulu_20140406_03.JPG) |
| `falconidae/Q26490.jpg` | Q26490 | Flocci Nivis | CC BY 4.0 | [20210930 Falco tinnunculus.jpg](https://commons.wikimedia.org/wiki/File:20210930_Falco_tinnunculus.jpg) |
| `paridae/Q191096.jpg` | Q191096 | Calandrella | CC0 | [Periparus ater ringed by Landsort Bird Observatory-10.JPG](https://commons.wikimedia.org/wiki/File:Periparus_ater_ringed_by_Landsort_Bird_Observatory-10.JPG) |
| `paridae/Q25404.jpg` | Q25404 | Jojovavasasa | CC0 | [Blue Tit (Cyanistes caeruleus) on bird feeder 06.jpg](https://commons.wikimedia.org/wiki/File:Blue_Tit_(Cyanistes_caeruleus)_on_bird_feeder_06.jpg) |
| `paridae/Q25485.jpg` | Q25485 | Hobbyfotowiki | CC0 | [Great tit (Parus major), North Rhine-Westphalia.jpg](https://commons.wikimedia.org/wiki/File:Great_tit_(Parus_major),_North_Rhine-Westphalia.jpg) |
| `turdidae/Q25234.jpg` | Q25234 | Musicaline | CC BY-SA 4.0 | [Turdus merula 1294.jpg](https://commons.wikimedia.org/wiki/File:Turdus_merula_1294.jpg) |

**Design mockups** (`docs/superpowers/specs/assets/`; matched against the species photos of commit `92ad5d62`)

| File | QID | Photographer | Licence | Commons file | Match |
|---|---|---|---|---|---|
| `2026-09-24-v1-3-release/bluetit.jpg` | Q25485 | Hobbyfotowiki | CC0 | [Great tit (Parus major), North Rhine-Westphalia.jpg](https://commons.wikimedia.org/wiki/File:Great_tit_(Parus_major),_North_Rhine-Westphalia.jpg) | same image |
| `2026-09-24-v1-3-release/robin.jpg` | Q25334 | Rob Hille | Public domain | [Robin - Erithacus rubecula 1.jpg](https://commons.wikimedia.org/wiki/File:Robin_-_Erithacus_rubecula_1.jpg) | same image |
| `2026-09-24-website-1-3-lyft/bullfinch.jpg` | Q25382 | Estormiz | CC0 | [Pyrrhula Pyrrhula Kittilä 20170408 01.jpg](https://commons.wikimedia.org/wiki/File:Pyrrhula_Pyrrhula_Kittilä_20170408_01.jpg) | same image |
| `2026-09-24-website-1-3-lyft/greattit.jpg` | Q25485 | Hobbyfotowiki | CC0 | [Great tit (Parus major), North Rhine-Westphalia.jpg](https://commons.wikimedia.org/wiki/File:Great_tit_(Parus_major),_North_Rhine-Westphalia.jpg) | same image |
| `2026-09-24-website-1-3-lyft/ltt.jpg` | Q170831 | Membeth | CC0 | [Parks.Range.Aegithalos.caudatus.caudatus.jpg](https://commons.wikimedia.org/wiki/File:Parks.Range.Aegithalos.caudatus.caudatus.jpg) | same image |
| `2026-09-24-website-1-3-lyft/reedling.jpg` | Q192817 | Hobbyfotowiki | CC0 | [Bearded reedling (Panurus biarmicus).jpg](https://commons.wikimedia.org/wiki/File:Bearded_reedling_(Panurus_biarmicus).jpg) | same image |
| `2026-09-24-website-1-3-lyft/robinpd.jpg` | Q25334 | Rob Hille | Public domain | [Robin - Erithacus rubecula 1.jpg](https://commons.wikimedia.org/wiki/File:Robin_-_Erithacus_rubecula_1.jpg) | same image |
| `2026-09-24-website-1-3-lyft/swallow.jpg` | Q25429 | Аимаина хикари | CC0 | [Hirundo rustica Zazymya3.JPG](https://commons.wikimedia.org/wiki/File:Hirundo_rustica_Zazymya3.JPG) | same image |
| `2026-09-24-website-1-3-lyft/waxwing.jpg` | Q26135 | Аимаина хикари | CC0 | [Bombycilla garrulus Kiev1.jpg](https://commons.wikimedia.org/wiki/File:Bombycilla_garrulus_Kiev1.jpg) | same image |
| `2026-09-25-artsidor/blames.jpg` | Q25404 | Jojovavasasa | CC0 | [Blue Tit (Cyanistes caeruleus) on bird feeder 06.jpg](https://commons.wikimedia.org/wiki/File:Blue_Tit_(Cyanistes_caeruleus)_on_bird_feeder_06.jpg) | same image |
| `2026-09-25-artsidor/entita.jpg` | Q207838 | blondinrikard | CC BY 2.0 | [Marsh tit, Poecile palustris, Entita.jpg](https://commons.wikimedia.org/wiki/File:Marsh_tit,_Poecile_palustris,_Entita.jpg) | same image |
| `2026-09-25-artsidor/g-auks.jpg` | Q27102 | lwolfartist | CC BY 2.0 | [Alca torda DSC 5788.jpg](https://commons.wikimedia.org/wiki/File:Alca_torda_DSC_5788.jpg) | same image |
| `2026-09-25-artsidor/g-cranes_rails.jpg` | Q4764 | DimiTalen | CC0 | [Flock of cranes (Grus grus) flying over the High Fens, Eupen, 2025.jpg](https://commons.wikimedia.org/wiki/File:Flock_of_cranes_(Grus_grus)_flying_over_the_High_Fens,_Eupen,_2025.jpg) | same image |
| `2026-09-25-artsidor/g-doves.jpg` | Q26026 | Klaaschwotzer | CC0 | [Ringeltaube Columba palumbus Hannover Germany.jpg](https://commons.wikimedia.org/wiki/File:Ringeltaube_Columba_palumbus_Hannover_Germany.jpg) | same image |
| `2026-09-25-artsidor/g-gamebirds.jpg` | Q25432 | Tlusťa | Public domain | [Phasianus colchicus Konopiste.jpg](https://commons.wikimedia.org/wiki/File:Phasianus_colchicus_Konopiste.jpg) | same image |
| `2026-09-25-artsidor/g-grebes_divers.jpg` | Q25422 | Mattivirtala | CC0 | [Podiceps cristatus Oulu Finland 2021-05-03.jpg](https://commons.wikimedia.org/wiki/File:Podiceps_cristatus_Oulu_Finland_2021-05-03.jpg) | same image |
| `2026-09-25-artsidor/g-gulls_terns.jpg` | Q26427 | Estormiz | CC0 | [Larus canus Oulu 20150712 03.JPG](https://commons.wikimedia.org/wiki/File:Larus_canus_Oulu_20150712_03.JPG) | same image |
| `2026-09-25-artsidor/g-herons_storks.jpg` | Q25273 | Jebulon | CC0 | [Héron cendré Ardea cinerea Tiergarten Schönbrunn.jpg](https://commons.wikimedia.org/wiki/File:Héron_cendré_Ardea_cinerea_Tiergarten_Schönbrunn.jpg) | same image |
| `2026-09-25-artsidor/g-other.jpg` | Q18845 | gailhampshire from Cradley, Malvern, U.K | CC BY 2.0 | [Cuckoo. Cuculus canorus - Flickr - gailhampshire.jpg](https://commons.wikimedia.org/wiki/File:Cuckoo._Cuculus_canorus_-_Flickr_-_gailhampshire.jpg) | same image |
| `2026-09-25-artsidor/g-owls.jpg` | Q25756 | Ypsilon from Finland | CC0 | [Strix aluco in Vantaa 1.jpg](https://commons.wikimedia.org/wiki/File:Strix_aluco_in_Vantaa_1.jpg) | same image |
| `2026-09-25-artsidor/g-raptors.jpg` | Q25385 | Hobbyfotowiki | CC0 | [Buteo buteo insularum, Azores.jpg](https://commons.wikimedia.org/wiki/File:Buteo_buteo_insularum,_Azores.jpg) | image comparison (cropped) |
| `2026-09-25-artsidor/g-seabirds.jpg` | Q26675 | Hobbyfotowiki | CC0 | [Northern ganet (Morus bassanus), Heligoland.jpg](https://commons.wikimedia.org/wiki/File:Northern_ganet_(Morus_bassanus),_Heligoland.jpg) | same image |
| `2026-09-25-artsidor/g-songbirds-2.jpg` | Q25334 | Rob Hille | Public domain | [Robin - Erithacus rubecula 1.jpg](https://commons.wikimedia.org/wiki/File:Robin_-_Erithacus_rubecula_1.jpg) | same image |
| `2026-09-25-artsidor/g-songbirds.jpg` | Q25234 | Musicaline | CC BY-SA 4.0 | [Turdus merula 1294.jpg](https://commons.wikimedia.org/wiki/File:Turdus_merula_1294.jpg) | same image |
| `2026-09-25-artsidor/g-waders.jpg` | Q25928 | Hobbyfotowiki | CC0 | [Eurasian oystercatcher (Haematopus ostralegus) at Amrum.jpg](https://commons.wikimedia.org/wiki/File:Eurasian_oystercatcher_(Haematopus_ostralegus)_at_Amrum.jpg) | same image |
| `2026-09-25-artsidor/g-waterfowl.jpg` | Q25348 | Alexis Lours | CC BY 4.0 | [Mallard (Anas platyrhynchos) male head.jpg](https://commons.wikimedia.org/wiki/File:Mallard_(Anas_platyrhynchos)_male_head.jpg) | same image |
| `2026-09-25-artsidor/g-woodpeckers.jpg` | Q26209 | Hobbyfotowiki | CC0 | [Dendrocopos major (feeding).jpg](https://commons.wikimedia.org/wiki/File:Dendrocopos_major_(feeding).jpg) | same image |
| `2026-09-25-artsidor/svartmes.jpg` | Q191096 | Calandrella | CC0 | [Periparus ater ringed by Landsort Bird Observatory-10.JPG](https://commons.wikimedia.org/wiki/File:Periparus_ater_ringed_by_Landsort_Bird_Observatory-10.JPG) | same image |
| `2026-09-25-artsidor/talgoxe-2.jpg` | Q25485 | Anton Whoa | CC BY 2.0 | [Parus major, Omsk, Russia.jpg](https://commons.wikimedia.org/wiki/File:Parus_major,_Omsk,_Russia.jpg) | same image |
| `2026-09-25-artsidor/talgoxe-hero.jpg` | Q25485 | Hobbyfotowiki | CC0 | [Great tit (Parus major), North Rhine-Westphalia.jpg](https://commons.wikimedia.org/wiki/File:Great_tit_(Parus_major),_North_Rhine-Westphalia.jpg) | same image |
| `2026-09-25-artsidor/tofsmes.jpg` | Q207831 | Gonçalo Vila Ferraz | CC BY 4.0 | [Lophophanes cristatus, Parque Biológico de Gaia, Porto.jpg](https://commons.wikimedia.org/wiki/File:Lophophanes_cristatus,_Parque_Biológico_de_Gaia,_Porto.jpg) | image comparison (cropped) |

**Design mockups with embedded media** (`docs/superpowers/specs/assets/`; images and sound stored as base64 inside the HTML file)

| File | Embedded media | Credits |
|---|---|---|
| `2026-10-06-1.3-val/birdy-posts.html`, `2026-10-06-1.3-val/birdy-posts-2.html` | Plate photos from Wikimedia Commons, cut-outs drawn from them, and in `birdy-posts-2.html` five bird recordings from Wikimedia Commons (MP3) | Inline in each page: photographer or recordist, licence, Commons file and how it was edited (`birdy-posts-2.html` has a full credit list at the end). |
| `2026-10-06-1.3-val/birdy-uppslag-marken.html` | Plate photos (Sidensvans, Talgoxe, Domherre, Rödhake, Lavskrika; all CC0 or public domain) and drawn app screens | Inline ("Foto: … · CC0 · Wikimedia Commons" and a source line at the end). |
| `2026-10-06-1.3-val/birdy-val.html` | Seven crops of the plate photos as they were before the photo cleanup in release 1.3.0 | The page says they are the app's plate photos. Sävsångare: caroline legg, CC BY 2.0, [Sedge Warbler - Acrocephalus schoenobaenus - Juvenile (51354422957).jpg](https://commons.wikimedia.org/wiki/File:Sedge_Warbler_-_Acrocephalus_schoenobaenus_-_Juvenile_(51354422957).jpg). Talgoxe: Hobbyfotowiki, CC0. Blåmes: Jojovavasasa, CC0. Koltrast: Musicaline, CC BY-SA 4.0. Rödhake: Rob Hille, public domain. Bofink: Hobbyfotowiki, CC0, [Chaffinch of the subspecies Fringilla coelebs canariensis.jpg](https://commons.wikimedia.org/wiki/File:Chaffinch_of_the_subspecies_Fringilla_coelebs_canariensis.jpg). The other files are linked in the tables above. |
| `2026-10-06-1.3-val/birdy-foto-fore-efter.html` | Twelve app screenshots, before and after | Screenshots, see the next section. |
| `2026-09-24-website-1-3-lyft/startsida-v5.html` | Four copies of the Birdy bird as a PNG mask | Own artwork (see "Birdy's own artwork"). The robin images the page loads are the AI-generated ones above. |

## Screenshots

| Files | Content |
|---|---|
| `docs/superpowers/screenshots/**`, `docs/superpowers/specs/assets/2026-09-24-v1-3-release/current_identify.png`, `website/src/assets/screens/`, `website/src/assets/slides/` | Screenshots of the Birdy app. The UI is ours. The species photos in them come from the photo set; their photographers and licences are in `shared/content/species/**/<QID>.yaml` and in the app under Settings, About, Photo credits. |
| `docs/superpowers/specs/assets/2026-09-28-webb-faltboksfarger/*.jpg` | Screenshots of birdy.community. |
| `docs/superpowers/specs/assets/2026-09-24-website-1-3-lyft/map-europe.jpg` | Screenshot of the earlier website map: map tiles © MapTiler © OpenStreetMap contributors, pins ours. |
| `docs/play-store/feature-graphic-1024x500.png` | Store graphic with app screens. The blurred photo in "This week's page" has no recorded source (legal review 2026-10, section 8); it is replaced by the new store images (release 1.3.0, Task 9). |

## Other files

| Files | Source |
|---|---|
| `website/public/play-badge-{sv,en}.png` | Official Google Play badges from Google, used under Google's badge guidelines. Google Play and the Google Play logo are trademarks of Google LLC. |
| `website/public/coverage/coverage-fallback.webp` | Rendered by `website/tools/render-coverage-fallback.mjs` from Natural Earth 1:10m (public domain). |
| `tools/ml-eval/flexref/fixtures/chirp_3s_48k.wav` | Synthetic chirp made by `tools/ml-eval/flexref/gen_fixture.py`. |
| `tools/content-pipeline/tests/fixtures/{vp11_sample.pdf,ioc_sample.xlsx}` | Synthetic, made by `tests/fixtures/_generate.py` (column layout only, no text from the real lists). |
| `tools/content-pipeline/tests/fixtures/sample_image.jpg` | A plain one-colour 256 x 256 image. |
| `shared/content/src/jvmTest/resources/fixtures/images/*/hero.jpg` | 11-byte placeholder files, not images. |
