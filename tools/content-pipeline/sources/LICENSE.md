# Sources in this folder

## `ioc-14.1.xlsx`

**IOC World Bird List v14.1**, F. Gill, D. Donsker & P. Rasmussen (eds.), https://www.worldbirdnames.org/.

Licensed under the Creative Commons Attribution 3.0 Unported License (CC BY 3.0),
https://creativecommons.org/licenses/by/3.0/, as stated on worldbirdnames.org ("IOC World Bird List
by Frank Gill, David Donsker & Pamela Rasmussen (Eds) is licensed under a Creative Commons
Attribution 3.0 Unported License"). The file is the list as published; Birdy has not changed it.

Birdy uses it to cross-check its species list, status codes and English names (see
`src/birdy_fetcher/species_list.py`). The app credits the list under Settings, About.

## BirdLife Sverige's Western Palearctic list (not in this folder)

BirdLife Sverige's taxonomic committee's Western Palearctic list, version 11 (June 2025),
https://birdlife.se/tk/vastpalearktislistan/. It carries no licence, so the repo does not
contain it. `birdy-fetcher init` downloads `VP11.pdf` from BirdLife Sverige into the gitignored
`.cache/sources/` folder and checks a pinned SHA-256 (`src/birdy_fetcher/vp11_source.py`).
The species selection, status codes and English names in `species_list.yaml` were taken from
it; the app credits the list under Settings, About.
