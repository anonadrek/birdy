# Artsidor fas 1: pipelinesteget `web` – implementationsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ett nytt kommando `uv run birdy-fetcher web` som för de 180 granskade arterna skriver webbtexter (SV och EN) ur hela Wikipediaartikeln, kontrollerar dem automatiskt, skalar ned fotona och sparar allt som färdiga datafiler i `website/`.

**Architecture:** Ett nytt underpaket `birdy_fetcher.web` i den befintliga Python-pipelinen (`tools/content-pipeline`). Varje modul har ett ansvar: slugs, grupper, licenser, källdata, Wikipedia, kontroller, modellanrop, bilder, utdata, rapport och körning. Modellen anropas med strukturerad utdata (Pydantic via `messages.parse`) och alla regler kontrolleras i Python innan något sparas. Utdata hamnar i `website/src/data/species/<QID>.json` och `website/src/assets/species/<QID>/`, som inget på sajten läser förrän fas 2.

**Tech Stack:** Python 3.12, uv, click, pydantic 2, anthropic 0.97 (`AsyncAnthropic().messages.parse`), aiohttp, Pillow, PyYAML, pytest + pytest-asyncio, ruff, mypy strict.

**Spec:** `docs/superpowers/specs/2026-09-25-artsidor-design.md` (avsnitt 7, 8 och bilaga B och C). Planen för fas 2 (sidorna) är `docs/superpowers/plans/2026-09-25-artsidor-fas2-sidor.md`.

---

## Avvikelser från specen (medvetna, små)

1. **Kommandot heter `birdy-fetcher web` utan `--approved`.** Steget kör alltid bara granskade arter. `--species` smalnar av, och en ogranskad art ger ett fel.
2. **Apostrofer tas bort i slugs** i stället för att bli `-` ("Montagu's Harrier" blir `montagus-harrier`, inte `montagu-s-harrier`).
3. **Första person:** kontrollen stoppar `jag`, `vi` och `oss` på svenska, men inte `vår`/`våra`. "Våra vanligaste fåglar" är idiomatisk svenska om svenska fåglar, inte en röst som talar om sig själv.
4. **Var och när för arter som inte finns i Sverige:** texten säger det först och nämner sedan den region där arten lever (till exempel Kanarieöarna). Specens regel "inga platsnamn utanför Sverige" hade gjort de texterna tomma.
5. **Modell:** standard är `claude-opus-5` (`--model opus`). Specen sa "Sonnet-klass". Uppskattad kostnad för alla 180 arter: cirka 30 USD med Opus 5 och cirka 13 USD med Sonnet 5 (`--model sonnet`). Albin väljer vid körningen i Task 14.
6. **Gruppernas namn och ingresser** ligger i `website/src/data/species-groups.json` tillsammans med slugs, fotoart och listan Vanliga arter, så att pipelinen och sajten läser samma fil.

## Förutsättningar (görs en gång, före Task 13)

- `uv` finns (`uv --version`).
- **API-nyckel:** Windows-maskinen saknar `tools/content-pipeline/.env`. Antingen kopierar Albin `.env.example` till `.env` och fyller i `ANTHROPIC_API_KEY`, eller så kör han `ant auth login` (den nya klienten använder `AsyncAnthropic()` utan argument och hittar då profilen själv). Kommandona i Task 13 och 14 körs med `uv run --env-file .env ...` när `.env` används.
- Alla kommandon i planen körs från `tools/content-pipeline` om inget annat står.

## Filstruktur

**Skapas:**

| Fil | Ansvar |
|---|---|
| `website/src/data/species-groups.json` | De 15 grupperna (nyckel, slugs, namn, fotoart, ingresser) och listan Vanliga arter. Delas av pipeline och sajt. |
| `tools/content-pipeline/src/birdy_fetcher/web/__init__.py` | Tomt paket. |
| `.../web/slugs.py` | `slugify(name, lang)`. |
| `.../web/groups.py` | `GroupTable`: familj till grupp (appens `family_groups.yaml`) och webbens grupptabell. |
| `.../web/licenses.py` | Licenslänkar, tvätt av fotografnamn, Commons-adresser. |
| `.../web/source.py` | Läser granskade arter ur `shared/content/species`. |
| `.../web/wiki_full.py` | Wikidata-sitelinks och hela Wikipediaartiklar i klartext, med cache. |
| `.../web/model.py` | Pydantic-modellerna för modellens svar. |
| `.../web/checks.py` | Alla kontroller (text, fakta, rimlighet) och strykning av fakta. |
| `.../web/writer.py` | Modellanropet med ett nytt försök, cache och kostnad. |
| `.../web/images.py` | Nedskalning till WebP och fotometadata. |
| `.../web/output.py` | Bygger och skriver artens JSON (spec bilaga C). |
| `.../web/report.py` | Rapporten i markdown. |
| `.../web/run.py` | Körningen för en lista arter. |
| `tools/content-pipeline/prompts/web-v1.md` | Prompten. |
| `tools/content-pipeline/prompts/web-banned-phrases.txt` | Förbjudna fraser (spec bilaga B). |
| `tools/content-pipeline/tests/web_fixtures.py` | Gemensamma testdata. |
| `tools/content-pipeline/tests/test_web_*.py` | Tester per modul. |

**Ändras:** `src/birdy_fetcher/cli.py` (nytt kommando `web`), `src/birdy_fetcher/cost.py` (priser för Opus 5 och Sonnet 5).

---

### Task 1: Slugs

**Files:**
- Create: `tools/content-pipeline/src/birdy_fetcher/web/__init__.py`
- Create: `tools/content-pipeline/src/birdy_fetcher/web/slugs.py`
- Test: `tools/content-pipeline/tests/test_web_slugs.py`

- [ ] **Step 1: Skriv testet**

```python
"""Tests for web/slugs.py (spec 2026-09-25 §4)."""

from __future__ import annotations

import pytest

from birdy_fetcher.web.slugs import slugify


@pytest.mark.parametrize(
    ("name", "lang", "expected"),
    [
        ("Talgoxe", "sv", "talgoxe"),
        ("Större hackspett", "sv", "storre-hackspett"),
        ("Änder & gäss", "sv", "ander-och-gass"),
        ("Rödstrupig piplärka", "sv", "rodstrupig-piplarka"),
        ("Eurasian Blue Tit", "en", "eurasian-blue-tit"),
        ("Ducks & geese", "en", "ducks-and-geese"),
        ("Eurasian Three-toed Woodpecker", "en", "eurasian-three-toed-woodpecker"),
        ("Montagu's Harrier", "en", "montagus-harrier"),
        ("Montagu’s Harrier", "en", "montagus-harrier"),
        ("  Gök  ", "sv", "gok"),
    ],
)
def test_slugify(name: str, lang: str, expected: str) -> None:
    assert slugify(name, lang) == expected


def test_slugify_rejects_empty_result() -> None:
    with pytest.raises(ValueError):
        slugify("  ", "sv")
```

- [ ] **Step 2: Kör testet och se det faila**

Run: `uv run pytest tests/test_web_slugs.py -v`
Expected: FAIL med `ModuleNotFoundError: No module named 'birdy_fetcher.web'`

- [ ] **Step 3: Skriv koden**

`src/birdy_fetcher/web/__init__.py`:

```python
"""Web pages on birdy.community: texts, photos and data for the species pages."""
```

`src/birdy_fetcher/web/slugs.py`:

```python
"""URL slugs for species and group pages (spec 2026-09-25 §4)."""

from __future__ import annotations

import re
import unicodedata

_REPLACE = {"å": "a", "ä": "a", "ö": "o", "æ": "ae", "ø": "o", "ß": "ss"}
_AMPERSAND = {"sv": " och ", "en": " and "}
_APOSTROPHES = "'’`´"


def slugify(name: str, lang: str) -> str:
    """Lowercase ASCII slug: å/ä to a, ö to o, & to och/and, apostrophes removed."""
    text = name.strip().lower()
    for ch in _APOSTROPHES:
        text = text.replace(ch, "")
    text = text.replace("&", _AMPERSAND[lang])
    text = "".join(_REPLACE.get(ch, ch) for ch in text)
    text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode("ascii")
    text = re.sub(r"[^a-z0-9]+", "-", text).strip("-")
    if not text:
        raise ValueError(f"Kan inte göra en slug av {name!r}")
    return text
```

- [ ] **Step 4: Kör testet igen**

Run: `uv run pytest tests/test_web_slugs.py -v`
Expected: PASS (11 tester)

- [ ] **Step 5: Lint och typer**

Run: `uv run ruff check src tests && uv run mypy`
Expected: inga fel

- [ ] **Step 6: Commit**

```bash
git add tools/content-pipeline/src/birdy_fetcher/web tools/content-pipeline/tests/test_web_slugs.py
git commit -m "feat(pipeline): slugs för artsidorna"
```

---

### Task 2: Grupptabellen

**Files:**
- Create: `website/src/data/species-groups.json`
- Create: `tools/content-pipeline/src/birdy_fetcher/web/groups.py`
- Test: `tools/content-pipeline/tests/test_web_groups.py`

- [ ] **Step 1: Skapa grupptabellen**

`website/src/data/species-groups.json`. Ordningen är appens (`family_groups.yaml`). Namnen är appens `archive_chip_*`, utom "Övriga fåglar"/"Other birds" som blir tydligare som sidrubrik. Fotoarterna är granskade arter med huvudfoto. `common` är raden Vanliga arter i sidfoten (spec avsnitt 6).

```json
{
  "groups": [
    {
      "key": "songbirds",
      "slug": { "sv": "tattingar", "en": "songbirds" },
      "name": { "sv": "Tättingar", "en": "Songbirds" },
      "photo": "Q25234",
      "intro": {
        "sv": "Tättingar är den största ordningen bland fåglarna, med mer än hälften av alla fågelarter. Hit hör de flesta småfåglar du ser i trädgården och skogen, och många känns lättast igen på sången.",
        "en": "Songbirds, or passerines, are the largest order of birds, with more than half of all bird species. Most of the small birds in gardens and woodland belong here, and many are easiest to recognise by their song."
      }
    },
    {
      "key": "waterfowl",
      "slug": { "sv": "ander-och-gass", "en": "ducks-and-geese" },
      "name": { "sv": "Änder & gäss", "en": "Ducks & geese" },
      "photo": "Q25348",
      "intro": {
        "sv": "Änder, gäss och svanar hör alla till familjen andfåglar. De lever vid sjöar, vikar och kuster, och hos många änder ser hanen och honan helt olika ut.",
        "en": "Ducks, geese and swans all belong to the same family. They live on lakes, bays and coasts, and in many ducks the male and female look completely different."
      }
    },
    {
      "key": "waders",
      "slug": { "sv": "vadare", "en": "waders" },
      "name": { "sv": "Vadare", "en": "Waders" },
      "photo": "Q25928",
      "intro": {
        "sv": "Vadare söker föda på stränder, i våtmarker och på grunda bottnar, ofta med lång näbb och långa ben. Många häckar i norr och syns i södra Sverige under flyttningen vår och höst.",
        "en": "Waders feed on shores, in wetlands and on mudflats, often with long bills and long legs. Many breed in the far north and pass through southern Sweden on migration in spring and autumn."
      }
    },
    {
      "key": "gulls_terns",
      "slug": { "sv": "masar-och-tarnor", "en": "gulls-and-terns" },
      "name": { "sv": "Måsar & tärnor", "en": "Gulls & terns" },
      "photo": "Q26427",
      "intro": {
        "sv": "Måsar och trutar är vanliga längs kusterna och allt oftare i städerna. Tärnorna är smäckrare, dyker efter småfisk och flyttar långt söderut över vintern.",
        "en": "Gulls are common along the coasts and more and more often in towns. Terns are slimmer, dive for small fish and migrate far south for the winter."
      }
    },
    {
      "key": "auks",
      "slug": { "sv": "alkor", "en": "auks" },
      "name": { "sv": "Alkor", "en": "Auks" },
      "photo": "Q27102",
      "intro": {
        "sv": "Alkor är havsfåglar som dyker och simmar under vattnet med hjälp av vingarna. De häckar i kolonier på klippor och öar och tillbringar resten av året ute till havs.",
        "en": "Auks are seabirds that dive and swim underwater using their wings. They breed in colonies on cliffs and islands and spend the rest of the year out at sea."
      }
    },
    {
      "key": "seabirds",
      "slug": { "sv": "havsfaglar", "en": "seabirds" },
      "name": { "sv": "Havsfåglar", "en": "Seabirds" },
      "photo": "Q25440",
      "intro": {
        "sv": "Här samlas fåglar som hör hemma på öppet hav eller längs kusten, som skarvar och sulor. Många ses bäst från en udde eller en färja.",
        "en": "This group gathers birds of the open sea and the coast, such as cormorants and gannets. Many are best seen from a headland or a ferry."
      }
    },
    {
      "key": "grebes_divers",
      "slug": { "sv": "doppingar-och-lommar", "en": "grebes-and-divers" },
      "name": { "sv": "Doppingar & lommar", "en": "Grebes & divers" },
      "photo": "Q25422",
      "intro": {
        "sv": "Doppingar och lommar är skickliga dykare med benen placerade långt bak på kroppen. På land rör de sig klumpigt, men i vattnet är de snabba och jagar fisk under ytan.",
        "en": "Grebes and divers are skilled divers with their legs set far back on the body. They are clumsy on land, but in the water they are fast and hunt fish below the surface."
      }
    },
    {
      "key": "herons_storks",
      "slug": { "sv": "hagrar-och-storkar", "en": "herons-and-storks" },
      "name": { "sv": "Hägrar & storkar", "en": "Herons & storks" },
      "photo": "Q25273",
      "intro": {
        "sv": "Hägrar och storkar är stora fåglar med långa ben, lång hals och kraftig näbb. De söker föda i grunt vatten och på fuktiga ängar och står ofta helt stilla medan de väntar på bytet.",
        "en": "Herons and storks are large birds with long legs, long necks and strong bills. They feed in shallow water and on wet meadows and often stand completely still while they wait for prey."
      }
    },
    {
      "key": "raptors",
      "slug": { "sv": "rovfaglar", "en": "birds-of-prey" },
      "name": { "sv": "Rovfåglar", "en": "Birds of prey" },
      "photo": "Q25385",
      "intro": {
        "sv": "Rovfåglar jagar med skarp syn, kraftiga klor och krökt näbb. De flesta känns lättast igen på silhuetten och sättet att flyga, eftersom de sällan ses på nära håll.",
        "en": "Birds of prey hunt with sharp eyesight, strong talons and a hooked bill. Most are easiest to recognise by their silhouette and the way they fly, since they are rarely seen up close."
      }
    },
    {
      "key": "owls",
      "slug": { "sv": "ugglor", "en": "owls" },
      "name": { "sv": "Ugglor", "en": "Owls" },
      "photo": "Q25756",
      "intro": {
        "sv": "Ugglor jagar mest i skymning och mörker och hittar bytet med hjälp av hörseln. Du hör dem oftare än du ser dem, och lätet är det säkraste sättet att veta vilken uggla det är.",
        "en": "Owls hunt mostly at dusk and in the dark and find their prey by hearing. You will hear them more often than you see them, and the call is the surest way to tell which owl it is."
      }
    },
    {
      "key": "gamebirds",
      "slug": { "sv": "honsfaglar", "en": "gamebirds" },
      "name": { "sv": "Hönsfåglar", "en": "Gamebirds" },
      "photo": "Q25432",
      "intro": {
        "sv": "Hönsfåglar lever mest på marken och har kraftig kropp och korta, rundade vingar. Hit hör skogshönsen som tjäder och orre, men också fasan och rapphöna.",
        "en": "Gamebirds live mostly on the ground and have a heavy body and short, rounded wings. The group includes forest grouse such as the capercaillie and black grouse, as well as the pheasant and grey partridge."
      }
    },
    {
      "key": "doves",
      "slug": { "sv": "duvor", "en": "doves-and-pigeons" },
      "name": { "sv": "Duvor", "en": "Doves & pigeons" },
      "photo": "Q26026",
      "intro": {
        "sv": "Duvor har litet huvud, rund kropp och ett mjukt, kuttrande läte. Ringduvan är den största och vanligaste i Sverige, och flera arter trivs nära människor.",
        "en": "Pigeons and doves have a small head, a plump body and a soft, cooing call. The woodpigeon is the largest and most common in Sweden, and several species thrive close to people."
      }
    },
    {
      "key": "woodpeckers",
      "slug": { "sv": "hackspettar", "en": "woodpeckers" },
      "name": { "sv": "Hackspettar", "en": "Woodpeckers" },
      "photo": "Q26209",
      "intro": {
        "sv": "Hackspettar klättrar på trädstammar och hackar i trä efter insekter och för att göra bohål. På våren trummar de med näbben mot torra grenar för att hävda revir.",
        "en": "Woodpeckers climb tree trunks and chisel into wood for insects and to make nest holes. In spring they drum on dry branches with their bills to claim a territory."
      }
    },
    {
      "key": "cranes_rails",
      "slug": { "sv": "tranor-och-rallar", "en": "cranes-and-rails" },
      "name": { "sv": "Tranor & rallar", "en": "Cranes & rails" },
      "photo": "Q4764",
      "intro": {
        "sv": "Tranan är en av Sveriges största fåglar och känns igen på sitt trumpetande läte. Rallarna lever i vass och våtmark, och medan sothönan simmar öppet hörs vattenrallen oftare än den syns.",
        "en": "The common crane is one of the largest birds in Sweden and is known for its trumpeting call. Rails live in reeds and wetlands, and while the coot swims in the open, the water rail is heard more often than it is seen."
      }
    },
    {
      "key": "other",
      "slug": { "sv": "ovriga-faglar", "en": "other-birds" },
      "name": { "sv": "Övriga fåglar", "en": "Other birds" },
      "photo": "Q18845",
      "intro": {
        "sv": "Här samlas fåglar från små familjer som inte passar i de andra grupperna. Hit hör göken, tornseglaren, nattskärran och kungsfiskaren.",
        "en": "This group gathers birds from small families that do not fit in the other groups. It includes the cuckoo, the swift, the nightjar and the kingfisher."
      }
    }
  ],
  "common": [
    "Q25485", "Q25404", "Q25234", "Q25334", "Q14683", "Q25307",
    "Q25345384", "Q25383", "Q25348", "Q26427", "Q25385", "Q4764"
  ]
}
```

- [ ] **Step 2: Skriv testet**

`tests/test_web_groups.py`:

```python
"""Tests for web/groups.py and the shared website/src/data/species-groups.json."""

from __future__ import annotations

import json
import re
from pathlib import Path

import pytest

from birdy_fetcher.web.groups import GroupTable

REPO = Path(__file__).resolve().parents[3]
FAMILY_GROUPS = REPO / "shared/content/src/jvmMain/resources/family_groups.yaml"
WEB_GROUPS = REPO / "website/src/data/species-groups.json"


def _small_tables(tmp_path: Path) -> tuple[Path, Path]:
    yaml_path = tmp_path / "family_groups.yaml"
    yaml_path.write_text(
        "order: [songbirds, owls, other]\n"
        "groups:\n"
        "  songbirds: {keyed_by: order, ioc_order: Passeriformes}\n"
        "  owls: {families: [Strigidae, Tytonidae]}\n"
        "  other: {families: [Cuculidae]}\n",
        encoding="utf-8",
    )
    group = {
        "slug": {"sv": "x", "en": "x"},
        "name": {"sv": "X", "en": "X"},
        "photo": "Q1",
        "intro": {"sv": "a", "en": "a"},
    }
    json_path = tmp_path / "species-groups.json"
    json_path.write_text(
        json.dumps(
            {
                "groups": [
                    {**group, "key": "songbirds", "slug": {"sv": "tattingar", "en": "songbirds"}},
                    {**group, "key": "owls", "slug": {"sv": "ugglor", "en": "owls"}},
                    {**group, "key": "other", "slug": {"sv": "ovriga", "en": "other"}},
                ],
                "common": ["Q1"],
            }
        ),
        encoding="utf-8",
    )
    return yaml_path, json_path


def test_group_for_uses_order_for_passerines(tmp_path: Path) -> None:
    table = GroupTable(*_small_tables(tmp_path))
    assert table.group_for(family="Paridae", ioc_order="Passeriformes") == "songbirds"


def test_group_for_uses_family_and_falls_back_to_other(tmp_path: Path) -> None:
    table = GroupTable(*_small_tables(tmp_path))
    assert table.group_for(family="Strigidae", ioc_order="Strigiformes") == "owls"
    assert table.group_for(family="Unknownidae", ioc_order="X") == "other"


def test_by_key_and_common(tmp_path: Path) -> None:
    table = GroupTable(*_small_tables(tmp_path))
    assert table.by_key("owls").slug_sv == "ugglor"
    assert table.common == ["Q1"]
    with pytest.raises(KeyError):
        table.by_key("nope")


def test_order_mismatch_is_an_error(tmp_path: Path) -> None:
    yaml_path, json_path = _small_tables(tmp_path)
    data = json.loads(json_path.read_text(encoding="utf-8"))
    data["groups"].reverse()
    json_path.write_text(json.dumps(data), encoding="utf-8")
    with pytest.raises(ValueError, match="ordning"):
        GroupTable(yaml_path, json_path)


def test_real_tables_load_and_match_the_app() -> None:
    table = GroupTable(FAMILY_GROUPS, WEB_GROUPS)
    assert len(table.groups) == 15
    assert len(table.common) == 12
    assert len(set(table.common)) == 12


def test_real_intros_follow_the_writing_rules() -> None:
    data = json.loads(WEB_GROUPS.read_text(encoding="utf-8"))
    for group in data["groups"]:
        for lang in ("sv", "en"):
            text = group["intro"][lang]
            where = f"{group['key']}.{lang}"
            assert "—" not in text and "–" not in text and "--" not in text, where
            assert "!" not in text, where
            sentences = [s for s in re.split(r"(?<=[.?!])\s+", text.strip()) if s]
            assert 2 <= len(sentences) <= 3, where
            assert len(text.split()) <= 60, where
```

- [ ] **Step 3: Kör testet och se det faila**

Run: `uv run pytest tests/test_web_groups.py -v`
Expected: FAIL med `ModuleNotFoundError: No module named 'birdy_fetcher.web.groups'`

- [ ] **Step 4: Skriv koden**

`src/birdy_fetcher/web/groups.py`:

```python
"""Family to group mapping (the app's family_groups.yaml) and the web's group table."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml


@dataclass(frozen=True)
class WebGroup:
    key: str
    slug_sv: str
    slug_en: str
    name_sv: str
    name_en: str
    photo: str


class GroupTable:
    """Same 15 groups as the app's field guide, plus the web's slugs and names."""

    def __init__(self, family_groups_yaml: Path, web_groups_json: Path) -> None:
        raw: dict[str, Any] = yaml.safe_load(family_groups_yaml.read_text(encoding="utf-8"))
        self.order: list[str] = list(raw["order"])
        self._order_key = ""
        self._order_group = ""
        self._family_to_group: dict[str, str] = {}
        for key, spec in raw["groups"].items():
            if spec.get("keyed_by") == "order":
                self._order_key = spec["ioc_order"]
                self._order_group = key
            for family in spec.get("families", []):
                self._family_to_group[family] = key

        web: dict[str, Any] = json.loads(web_groups_json.read_text(encoding="utf-8"))
        self.groups = [
            WebGroup(
                key=g["key"],
                slug_sv=g["slug"]["sv"],
                slug_en=g["slug"]["en"],
                name_sv=g["name"]["sv"],
                name_en=g["name"]["en"],
                photo=g["photo"],
            )
            for g in web["groups"]
        ]
        self.common: list[str] = list(web["common"])
        keys = [g.key for g in self.groups]
        if keys != self.order:
            raise ValueError(
                f"species-groups.json har inte samma grupper i samma ordning som appen: "
                f"{keys} mot {self.order}"
            )

    def group_for(self, *, family: str, ioc_order: str) -> str:
        if ioc_order == self._order_key:
            return self._order_group
        return self._family_to_group.get(family, "other")

    def by_key(self, key: str) -> WebGroup:
        for group in self.groups:
            if group.key == key:
                return group
        raise KeyError(key)
```

- [ ] **Step 5: Kör testet igen**

Run: `uv run pytest tests/test_web_groups.py -v`
Expected: PASS (6 tester)

- [ ] **Step 6: Lint, typer och commit**

Run: `uv run ruff check src tests && uv run mypy`
Expected: inga fel

```bash
git add website/src/data/species-groups.json tools/content-pipeline/src/birdy_fetcher/web/groups.py tools/content-pipeline/tests/test_web_groups.py
git commit -m "feat(pipeline): grupptabellen för artsidorna (delas med webben)"
```

---

### Task 3: Licenser och fotografnamn

**Files:**
- Create: `tools/content-pipeline/src/birdy_fetcher/web/licenses.py`
- Test: `tools/content-pipeline/tests/test_web_licenses.py`

- [ ] **Step 1: Skriv testet**

```python
"""Tests for web/licenses.py."""

from __future__ import annotations

import pytest

from birdy_fetcher.web.licenses import (
    LICENSE_URLS,
    UnknownLicenseError,
    clean_author,
    commons_url,
    license_url,
)


def test_all_eight_licenses_in_the_data_are_known() -> None:
    assert set(LICENSE_URLS) == {
        "CC0",
        "Public domain",
        "CC BY 2.0",
        "CC BY 3.0",
        "CC BY 4.0",
        "CC BY-SA 2.0",
        "CC BY-SA 3.0",
        "CC BY-SA 4.0",
    }


def test_license_url() -> None:
    assert license_url("CC BY-SA 4.0") == "https://creativecommons.org/licenses/by-sa/4.0/"
    assert license_url("CC BY 2.0") == "https://creativecommons.org/licenses/by/2.0/"
    assert license_url("CC0") is None
    assert license_url("Public domain") is None


def test_unknown_license_is_an_error() -> None:
    with pytest.raises(UnknownLicenseError):
        license_url("All rights reserved")


def test_clean_author_strips_commons_html() -> None:
    raw = (
        '<div class="fn value">\n<a href="//commons.wikimedia.org/wiki/User:Archaeodontosaurus" '
        'title="User:Archaeodontosaurus">Didier\n    Descouens</a></div>'
    )
    assert clean_author(raw) == "Didier Descouens"


def test_clean_author_decodes_entities_and_handles_empty() -> None:
    assert clean_author("J&ouml;rg &amp; Anna") == "Jörg & Anna"
    assert clean_author("") is None
    assert clean_author(None) is None
    assert clean_author("<span> </span>") is None


def test_commons_url_replaces_spaces() -> None:
    url = "https://commons.wikimedia.org/wiki/File:Great tit (Parus major), North Rhine-Westphalia.jpg"
    assert commons_url(url) == (
        "https://commons.wikimedia.org/wiki/File:Great_tit_(Parus_major),_North_Rhine-Westphalia.jpg"
    )
```

- [ ] **Step 2: Kör testet och se det faila**

Run: `uv run pytest tests/test_web_licenses.py -v`
Expected: FAIL med `ModuleNotFoundError`

- [ ] **Step 3: Skriv koden**

`src/birdy_fetcher/web/licenses.py`:

```python
"""Photo licences, photographer names and Commons links for the credits (spec §8)."""

from __future__ import annotations

from html.parser import HTMLParser

LICENSE_URLS: dict[str, str | None] = {
    "CC0": None,
    "Public domain": None,
    "CC BY 2.0": "https://creativecommons.org/licenses/by/2.0/",
    "CC BY 3.0": "https://creativecommons.org/licenses/by/3.0/",
    "CC BY 4.0": "https://creativecommons.org/licenses/by/4.0/",
    "CC BY-SA 2.0": "https://creativecommons.org/licenses/by-sa/2.0/",
    "CC BY-SA 3.0": "https://creativecommons.org/licenses/by-sa/3.0/",
    "CC BY-SA 4.0": "https://creativecommons.org/licenses/by-sa/4.0/",
}


class UnknownLicenseError(ValueError):
    pass


def license_url(license_id: str) -> str | None:
    if license_id not in LICENSE_URLS:
        raise UnknownLicenseError(f"Okänd bildlicens: {license_id!r}")
    return LICENSE_URLS[license_id]


class _TextCollector(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.parts: list[str] = []

    def handle_data(self, data: str) -> None:
        self.parts.append(data)


def clean_author(raw: str | None) -> str | None:
    """Commons author fields are HTML; the site shows plain text."""
    if not raw:
        return None
    parser = _TextCollector()
    parser.feed(raw)
    parser.close()
    text = " ".join("".join(parser.parts).split())
    return text or None


def commons_url(source_url: str) -> str:
    return source_url.strip().replace(" ", "_")
```

- [ ] **Step 4: Kör testet igen**

Run: `uv run pytest tests/test_web_licenses.py -v`
Expected: PASS (6 tester)

- [ ] **Step 5: Lint, typer och commit**

Run: `uv run ruff check src tests && uv run mypy`

```bash
git add tools/content-pipeline/src/birdy_fetcher/web/licenses.py tools/content-pipeline/tests/test_web_licenses.py
git commit -m "feat(pipeline): licenslänkar och tvättade fotografnamn för artsidorna"
```

---

### Task 4: Källdata (granskade arter)

**Files:**
- Create: `tools/content-pipeline/src/birdy_fetcher/web/source.py`
- Test: `tools/content-pipeline/tests/test_web_source.py`

- [ ] **Step 1: Skriv testet**

```python
"""Tests for web/source.py."""

from __future__ import annotations

from pathlib import Path

import pytest

from birdy_fetcher.web.source import NotApprovedError, load_approved

YAML_TEMPLATE = """id: {qid}
scientific_name: Parus major
taxonomy:
  family: Paridae
  family_sv: Mesar
  genus: Parus
  ioc_order: Passeriformes
names:
  sv: {sv}
  en: {en}
abundance: allmän
iucn_status: LC
description:
  sv: x
  en: x
migration:
  sv: x
  en: x
{marginalia}image_refs:
- role: hero
  path: {qid}/hero.webp
  width: 2400
  height: 1600
  license: CC0
  author: '<a href="x">Hobbyfotowiki</a>'
  source_url: https://commons.wikimedia.org/wiki/File:A b.jpg
- role: secondary
  path: {qid}/secondary-1.webp
  width: 1800
  height: 1200
  license: CC BY 2.0
  author: Anton Whoa
  source_url: https://commons.wikimedia.org/wiki/File:C.jpg
review_status: {status}
"""


def _write(root: Path, qid: str, sv: str, en: str, status: str, marginalia: str = "") -> None:
    path = root / "paridae" / f"{qid}.yaml"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        YAML_TEMPLATE.format(qid=qid, sv=sv, en=en, status=status, marginalia=marginalia),
        encoding="utf-8",
    )


def test_loads_only_approved(tmp_path: Path) -> None:
    _write(tmp_path, "Q1", "Talgoxe", "Great Tit", "approved")
    _write(tmp_path, "Q2", "Blåmes", "Blue Tit", "auto")
    sources = load_approved(tmp_path)
    assert [s.qid for s in sources] == ["Q1"]
    src = sources[0]
    assert src.name_sv == "Talgoxe"
    assert src.family == "Paridae"
    assert src.family_sv == "Mesar"
    assert src.ioc_order == "Passeriformes"
    assert src.iucn == "LC"
    assert src.marginalia_sv is None
    assert [i.role for i in src.images] == ["hero", "secondary"]
    assert src.images[1].author == "Anton Whoa"


def test_reads_marginalia(tmp_path: Path) -> None:
    _write(
        tmp_path,
        "Q1",
        "Talgoxe",
        "Great Tit",
        "approved",
        marginalia="marginalia:\n  sv: Söker frön.\n  en: Forages.\n",
    )
    src = load_approved(tmp_path)[0]
    assert src.marginalia_sv == "Söker frön."
    assert src.marginalia_en == "Forages."


def test_filter_by_qid_and_reject_unapproved(tmp_path: Path) -> None:
    _write(tmp_path, "Q1", "Talgoxe", "Great Tit", "approved")
    _write(tmp_path, "Q2", "Blåmes", "Blue Tit", "auto")
    _write(tmp_path, "Q3", "Svartmes", "Coal Tit", "approved")
    assert [s.qid for s in load_approved(tmp_path, qids=("Q3",))] == ["Q3"]
    with pytest.raises(NotApprovedError, match="Q2"):
        load_approved(tmp_path, qids=("Q2",))
    with pytest.raises(KeyError, match="Q9"):
        load_approved(tmp_path, qids=("Q9",))
```

- [ ] **Step 2: Kör testet och se det faila**

Run: `uv run pytest tests/test_web_source.py -v`
Expected: FAIL med `ModuleNotFoundError`

- [ ] **Step 3: Skriv koden**

`src/birdy_fetcher/web/source.py`:

```python
"""Reads the approved species (review_status: approved) from shared/content/species."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml


class NotApprovedError(ValueError):
    pass


@dataclass(frozen=True)
class SourceImage:
    role: str
    path: str
    license: str
    author: str | None
    source_url: str


@dataclass(frozen=True)
class SpeciesSource:
    qid: str
    scientific_name: str
    name_sv: str
    name_en: str
    family: str
    family_sv: str
    ioc_order: str
    iucn: str
    marginalia_sv: str | None
    marginalia_en: str | None
    images: tuple[SourceImage, ...]


def _parse(data: dict[str, Any]) -> SpeciesSource:
    taxonomy = data["taxonomy"]
    marginalia = data.get("marginalia") or {}
    return SpeciesSource(
        qid=data["id"],
        scientific_name=data["scientific_name"],
        name_sv=data["names"]["sv"],
        name_en=data["names"]["en"],
        family=taxonomy["family"],
        family_sv=taxonomy.get("family_sv") or taxonomy["family"],
        ioc_order=taxonomy["ioc_order"],
        iucn=data["iucn_status"],
        marginalia_sv=marginalia.get("sv"),
        marginalia_en=marginalia.get("en"),
        images=tuple(
            SourceImage(
                role=ref["role"],
                path=ref["path"],
                license=ref["license"],
                author=ref.get("author"),
                source_url=ref.get("source_url", ""),
            )
            for ref in data.get("image_refs") or []
        ),
    )


def load_approved(species_root: Path, qids: Sequence[str] = ()) -> list[SpeciesSource]:
    """All approved species, or only `qids`. A requested species that isn't approved is an error."""
    wanted = set(qids)
    found: list[SpeciesSource] = []
    seen: set[str] = set()
    for path in sorted(species_root.rglob("*.yaml")):
        data: dict[str, Any] = yaml.safe_load(path.read_text(encoding="utf-8"))
        qid = data["id"]
        if wanted and qid not in wanted:
            continue
        seen.add(qid)
        if data.get("review_status") != "approved":
            if wanted:
                raise NotApprovedError(f"{qid} är inte granskad (review_status: approved krävs)")
            continue
        found.append(_parse(data))
    missing = wanted - seen
    if missing:
        raise KeyError(f"Saknas i artfilerna: {sorted(missing)}")
    return found
```

- [ ] **Step 4: Kör testet igen**

Run: `uv run pytest tests/test_web_source.py -v`
Expected: PASS (3 tester)

- [ ] **Step 5: Lint, typer och commit**

Run: `uv run ruff check src tests && uv run mypy`

```bash
git add tools/content-pipeline/src/birdy_fetcher/web/source.py tools/content-pipeline/tests/test_web_source.py
git commit -m "feat(pipeline): läs granskade arter för artsidorna"
```

---

### Task 5: Hela Wikipediaartiklar

Den befintliga `wikipedia.py` hämtar bara ingressen och slår upp artikeln på artens namn, vilket träffar förgreningssidor ("Merlin", "Rook"). Det nya steget slår upp artikeltiteln via Wikidatas sitelinks på QID och hämtar hela artikeln som klartext med revisionsnummer.

**Files:**
- Create: `tools/content-pipeline/src/birdy_fetcher/web/wiki_full.py`
- Test: `tools/content-pipeline/tests/test_web_wiki_full.py`

- [ ] **Step 1: Skriv testet**

```python
"""Tests for web/wiki_full.py: sitelinks by QID and full plain-text articles."""

from __future__ import annotations

import json
from pathlib import Path

from birdy_fetcher.cache import Cache
from birdy_fetcher.web.wiki_full import FullWikiClient

SITELINKS = json.dumps(
    {
        "entities": {
            "Q25485": {
                "sitelinks": {
                    "svwiki": {"site": "svwiki", "title": "Talgoxe"},
                    "enwiki": {"site": "enwiki", "title": "Great tit"},
                }
            }
        }
    }
)


def _article(title: str, text: str, revid: int) -> str:
    return json.dumps(
        {"query": {"pages": [{"title": title, "extract": text, "revisions": [{"revid": revid}]}]}}
    )


class FakeHttp:
    def __init__(self) -> None:
        self.urls: list[str] = []

    async def __call__(self, url: str) -> str:
        self.urls.append(url)
        if "wikidata.org" in url:
            return SITELINKS
        if url.startswith("https://sv."):
            return _article("Talgoxe", "Talgoxen är cirka 14 centimeter lång.", 111)
        return _article("Great tit", "The great tit is about 14 centimetres long.", 222)


async def test_articles_uses_sitelinks_and_full_text(tmp_path: Path) -> None:
    http = FakeHttp()
    client = FullWikiClient(cache=Cache(tmp_path), http_get=http)
    articles = await client.articles("Q25485")
    assert set(articles) == {"sv", "en"}
    assert articles["sv"].title == "Talgoxe"
    assert articles["sv"].revision == "111"
    assert "14 centimeter" in articles["sv"].text
    assert articles["en"].revision == "222"
    assert any("sitefilter=svwiki%7Cenwiki" in u for u in http.urls)
    assert any("explaintext=1" in u and "titles=Great%20tit" in u for u in http.urls)


async def test_second_call_uses_cache(tmp_path: Path) -> None:
    http = FakeHttp()
    client = FullWikiClient(cache=Cache(tmp_path), http_get=http)
    await client.articles("Q25485")
    calls = len(http.urls)
    await client.articles("Q25485")
    assert len(http.urls) == calls
    await client.articles("Q25485", refresh=True)
    assert len(http.urls) == calls * 2


async def test_missing_sitelink_and_missing_page(tmp_path: Path) -> None:
    async def http(url: str) -> str:
        if "wikidata.org" in url:
            return json.dumps(
                {"entities": {"Q1": {"sitelinks": {"svwiki": {"site": "svwiki", "title": "X"}}}}}
            )
        return json.dumps({"query": {"pages": [{"title": "X", "missing": True}]}})

    client = FullWikiClient(cache=Cache(tmp_path), http_get=http)
    assert await client.articles("Q1") == {}
```

- [ ] **Step 2: Kör testet och se det faila**

Run: `uv run pytest tests/test_web_wiki_full.py -v`
Expected: FAIL med `ModuleNotFoundError`

- [ ] **Step 3: Skriv koden**

`src/birdy_fetcher/web/wiki_full.py`:

```python
"""Full Wikipedia articles as plain text, found through Wikidata sitelinks (spec §7)."""

from __future__ import annotations

import json
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from urllib.parse import quote

from ..cache import Cache
from ..wikipedia import _default_http_get

HttpGet = Callable[[str], Awaitable[str]]
LANGS = ("sv", "en")


@dataclass(frozen=True)
class WikiArticle:
    lang: str
    title: str
    revision: str
    text: str


class FullWikiClient:
    def __init__(self, *, cache: Cache, http_get: HttpGet | None = None) -> None:
        self.cache = cache
        self._http_get = http_get or _default_http_get

    async def _cached(self, qid: str, name: str, url: str, refresh: bool) -> str:
        raw = None if refresh else self.cache.get(qid, name)
        if raw is None:
            raw = await self._http_get(url)
            self.cache.put(qid, name, raw)
        return raw

    async def sitelinks(self, qid: str, *, refresh: bool = False) -> dict[str, str]:
        url = (
            "https://www.wikidata.org/w/api.php?action=wbgetentities&format=json"
            f"&props=sitelinks&sitefilter=svwiki%7Cenwiki&ids={qid}"
        )
        raw = await self._cached(qid, "web-sitelinks.json", url, refresh)
        links = json.loads(raw).get("entities", {}).get(qid, {}).get("sitelinks", {})
        return {
            site.removesuffix("wiki"): link["title"]
            for site, link in links.items()
            if site in ("svwiki", "enwiki")
        }

    async def article(
        self, qid: str, lang: str, title: str, *, refresh: bool = False
    ) -> WikiArticle | None:
        url = (
            f"https://{lang}.wikipedia.org/w/api.php?action=query&format=json&formatversion=2"
            "&prop=extracts%7Crevisions&explaintext=1&rvprop=ids&redirects=1"
            f"&titles={quote(title)}"
        )
        raw = await self._cached(qid, f"web-article-{lang}.json", url, refresh)
        pages = json.loads(raw).get("query", {}).get("pages", [])
        if not pages or pages[0].get("missing"):
            return None
        page = pages[0]
        text = str(page.get("extract", "")).strip()
        revisions = page.get("revisions") or []
        if not text or not revisions:
            return None
        return WikiArticle(
            lang=lang, title=page["title"], revision=str(revisions[0]["revid"]), text=text
        )

    async def articles(self, qid: str, *, refresh: bool = False) -> dict[str, WikiArticle]:
        titles = await self.sitelinks(qid, refresh=refresh)
        found: dict[str, WikiArticle] = {}
        for lang in LANGS:
            if lang not in titles:
                continue
            art = await self.article(qid, lang, titles[lang], refresh=refresh)
            if art is not None:
                found[lang] = art
        return found
```

- [ ] **Step 4: Kör testet igen**

Run: `uv run pytest tests/test_web_wiki_full.py -v`
Expected: PASS (3 tester)

- [ ] **Step 5: Lint, typer och commit**

Run: `uv run ruff check src tests && uv run mypy`

```bash
git add tools/content-pipeline/src/birdy_fetcher/web/wiki_full.py tools/content-pipeline/tests/test_web_wiki_full.py
git commit -m "feat(pipeline): hela Wikipediaartiklar via Wikidata för artsidorna"
```

---

### Task 6: Modellens svar och textkontrollerna

**Files:**
- Create: `tools/content-pipeline/src/birdy_fetcher/web/model.py`
- Create: `tools/content-pipeline/src/birdy_fetcher/web/checks.py`
- Create: `tools/content-pipeline/prompts/web-banned-phrases.txt`
- Create: `tools/content-pipeline/tests/web_fixtures.py`
- Test: `tools/content-pipeline/tests/test_web_checks_text.py`

- [ ] **Step 1: Skapa listan med förbjudna fraser**

`prompts/web-banned-phrases.txt` (spec bilaga B, en fras per rad, `#` är kommentar):

```text
# Förbjudna fraser i webbtexterna (spec 2026-09-25, bilaga B). En per rad, gemener.
# Får växa när Albins granskning hittar nya mönster.
anmärkningsvärd
anmärkningsvärt
fascinerande
spännande
magnifik
fantastisk
unik
en sann
en riktig pärla
inte bara
utan också
i hjärtat av
en symbol för
värd att upptäcka
kort sagt
sammanfattningsvis
det är värt att notera
ett nöje att
remarkable
fascinating
stunning
breathtaking
magnificent
boasts
nestled
a true
a testament to
not only
but also
in the heart of
it is worth noting
delve
tapestry
vibrant
iconic
truly
```

- [ ] **Step 2: Skapa testdata**

`tests/web_fixtures.py`:

```python
"""Shared test data for the web step: a valid model answer and matching articles."""

from __future__ import annotations

from birdy_fetcher.web.model import Facts, LangText, SizeFact, StatusFact, WebTextOutput
from birdy_fetcher.web.wiki_full import WikiArticle

ARTICLES = {
    "sv": WikiArticle(
        lang="sv",
        title="Talgoxe",
        revision="111",
        text="Talgoxen är cirka 14 centimeter lång och väger omkring 18 gram. "
        "Den är stannfågel i hela Sverige och ses året runt.",
    ),
    "en": WikiArticle(
        lang="en",
        title="Great tit",
        revision="222",
        text="The great tit is about 14 centimetres long. It is a resident bird across Sweden.",
    ),
}


def valid_output() -> WebTextOutput:
    return WebTextOutput(
        sv=LangText(
            lead="Den största av mesarna och en vanlig gäst vid fågelbordet. "
            "Den finns i hela Sverige året runt.",
            field_marks=[
                "Svart huvud med vita kinder",
                "Gul buk med ett svart band längs mitten",
                "Olivgrön rygg och blågrå vingar med vitt vingband",
            ],
            voice="Sången är ett ringande ti ta, ti ta som hörs redan i februari.",
            where_when="Stannfågel i hela landet. Vanligast i lövskog, parker och trädgårdar.",
            meta_description=(
                "Talgoxe: så känner du igen den på gul buk och svart slips, hur sången låter "
                "och var och när du ser den i Sverige under året."
            ),
            facts=Facts(
                size=SizeFact(value="Cirka 14 cm", quote="cirka 14 centimeter lång och väger"),
                sweden_status=StatusFact(value="resident", quote="Den är stannfågel i hela Sverige"),
            ),
        ),
        en=LangText(
            lead="The largest of the tits and a regular visitor to bird feeders. "
            "It lives across Sweden all year.",
            field_marks=[
                "Black head with white cheeks",
                "Yellow belly with a black stripe down the middle",
                "Olive back and blue grey wings with a white wing bar",
            ],
            voice="The song is a ringing tee cha, tee cha that is heard as early as February.",
            where_when="Resident across the country. Most common in woodland, parks and gardens.",
            meta_description=(
                "Great tit: how to recognise it by its yellow belly and black stripe, what its "
                "song sounds like and where you see it in Sweden."
            ),
            facts=Facts(
                size=SizeFact(value="About 14 cm", quote="about 14 centimetres long"),
                sweden_status=StatusFact(
                    value="resident", quote="It is a resident bird across Sweden"
                ),
            ),
        ),
    )
```

- [ ] **Step 3: Skriv testet**

`tests/test_web_checks_text.py`:

```python
"""Tests for the text checks in web/checks.py (spec §7, checks 1 to 3)."""

from __future__ import annotations

import json
from pathlib import Path

from birdy_fetcher.web.checks import banned_hits, check_text, load_banned, sentence_count

from .web_fixtures import valid_output

PIPELINE = Path(__file__).resolve().parents[1]
BANNED = load_banned(PIPELINE / "prompts" / "web-banned-phrases.txt")


def _paths(issues: list[object]) -> set[str]:
    return {getattr(i, "path") for i in issues}


def test_valid_output_has_no_text_issues() -> None:
    assert check_text(valid_output(), BANNED) == []


def test_dashes_exclamation_and_first_person() -> None:
    out = valid_output()
    out.sv.lead = "Den största av mesarna — och vanlig! Jag ser den ofta."
    out.en.voice = "We hear it -- often."
    issues = check_text(out, BANNED)
    messages = " ".join(i.message for i in issues)
    assert "sv.lead" in _paths(issues)
    assert "en.voice" in _paths(issues)
    assert "tankstreck" in messages
    assert "utropstecken" in messages
    assert "första person" in messages


def test_vara_is_not_first_person() -> None:
    out = valid_output()
    out.sv.lead = "En av våra vanligaste mesar. Den finns i hela Sverige."
    assert check_text(out, BANNED) == []


def test_banned_phrases_are_whole_words() -> None:
    assert banned_hits("En fascinerande fågel", BANNED) == ["fascinerande"]
    assert banned_hits("Unika teckningar", BANNED) == []
    assert banned_hits("It is truly common", BANNED) == ["truly"]


def test_length_limits() -> None:
    out = valid_output()
    out.sv.lead = "Ett. Två. Tre."
    out.sv.field_marks = ["Svart huvud", "Gul buk"]
    out.en.field_marks = ["x " * 17, "b", "c"]
    out.en.voice = "word " * 61
    out.sv.where_when = "ord " * 71
    out.en.meta_description = "Too short."
    issues = check_text(out, BANNED)
    paths = _paths(issues)
    assert {"sv.lead", "sv.field_marks", "en.field_marks[0]", "en.voice"} <= paths
    assert {"sv.where_when", "en.meta_description"} <= paths


def test_empty_field() -> None:
    out = valid_output()
    out.en.lead = "  "
    assert "en.lead" in _paths(check_text(out, BANNED))


def test_size_value_with_dash_is_a_fact_issue() -> None:
    out = valid_output()
    assert out.sv.facts.size is not None
    out.sv.facts.size.value = "13–15 cm"
    issues = check_text(out, BANNED)
    assert [(i.lang, i.fact) for i in issues] == [("sv", "size")]


def test_sentence_count() -> None:
    assert sentence_count("Ett. Två? Tre!") == 3
    assert sentence_count("Ingen punkt") == 1


def test_group_intros_have_no_banned_phrases() -> None:
    groups = json.loads(
        (PIPELINE.parents[1] / "website/src/data/species-groups.json").read_text(encoding="utf-8")
    )
    for group in groups["groups"]:
        for lang in ("sv", "en"):
            assert banned_hits(group["intro"][lang], BANNED) == [], group["key"]
```

- [ ] **Step 4: Kör testet och se det faila**

Run: `uv run pytest tests/test_web_checks_text.py -v`
Expected: FAIL med `ModuleNotFoundError: No module named 'birdy_fetcher.web.model'`

- [ ] **Step 5: Skriv modellerna**

`src/birdy_fetcher/web/model.py`. Inga fältbegränsningar i Pydantic: modellen ska alltid kunna tolka svaret, och alla regler ligger i `checks.py` så att felen kan skickas tillbaka till modellen.

```python
"""The model's structured answer for one species (both languages in one call)."""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel

SwedenStatus = Literal[
    "resident", "breeding_migrant", "passage", "winter_visitor", "rare_visitor", "absent"
]


class SizeFact(BaseModel):
    value: str
    quote: str


class StatusFact(BaseModel):
    value: SwedenStatus
    quote: str


class Facts(BaseModel):
    size: SizeFact | None
    sweden_status: StatusFact | None


class LangText(BaseModel):
    lead: str
    field_marks: list[str]
    voice: str
    where_when: str
    meta_description: str
    facts: Facts


class WebTextOutput(BaseModel):
    sv: LangText
    en: LangText
```

- [ ] **Step 6: Skriv textkontrollerna**

`src/birdy_fetcher/web/checks.py`:

```python
"""Checks that every web text must pass before it is saved (spec 2026-09-25 §7)."""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

from .model import LangText, WebTextOutput

LANGS = ("sv", "en")
DASHES = ("—", "–", "--")
FIRST_PERSON = {
    # "vår/våra" is left out on purpose: "våra vanligaste fåglar" is idiomatic Swedish.
    "sv": re.compile(r"\b(jag|vi|oss)\b", re.IGNORECASE),
    "en": re.compile(r"\b(I|[Ww]e|[Uu]s|[Oo]ur|[Oo]urs|[Mm]y)\b"),
}
LEAD_MAX_WORDS = 45
LEAD_MAX_SENTENCES = 2
MARKS_MIN, MARKS_MAX, MARK_MAX_WORDS = 3, 4, 16
VOICE_MAX_WORDS = 60
WHERE_MAX_WORDS = 70
META_MIN, META_MAX = 120, 155


@dataclass(frozen=True)
class Issue:
    """One broken rule. `fact` is set when the problem only affects one fact (size or status),
    which can then be dropped instead of failing the whole species."""

    path: str
    message: str
    lang: str | None = None
    fact: str | None = None


def load_banned(path: Path) -> list[str]:
    lines = path.read_text(encoding="utf-8").splitlines()
    return [ln.strip().lower() for ln in lines if ln.strip() and not ln.startswith("#")]


def banned_hits(text: str, banned: list[str]) -> list[str]:
    lower = text.lower()
    return [p for p in banned if re.search(rf"(?<!\w){re.escape(p)}(?!\w)", lower)]


def sentence_count(text: str) -> int:
    return len([s for s in re.split(r"(?<=[.?!])\s+", text.strip()) if s])


def _words(text: str) -> int:
    return len(text.split())


def _style(path: str, lang: str, text: str, banned: list[str]) -> list[Issue]:
    issues: list[Issue] = []
    if not text.strip():
        issues.append(Issue(path, "är tom"))
        return issues
    if any(d in text for d in DASHES):
        issues.append(Issue(path, "innehåller tankstreck eller --"))
    if "!" in text:
        issues.append(Issue(path, "innehåller utropstecken"))
    if FIRST_PERSON[lang].search(text):
        issues.append(Issue(path, "är skriven i första person"))
    issues.extend(Issue(path, f"innehåller den förbjudna frasen '{h}'") for h in banned_hits(text, banned))
    return issues


def _check_lang(lang: str, t: LangText, banned: list[str]) -> list[Issue]:
    issues: list[Issue] = []
    fields = {
        "lead": t.lead,
        "voice": t.voice,
        "where_when": t.where_when,
        "meta_description": t.meta_description,
    }
    fields.update({f"field_marks[{i}]": mark for i, mark in enumerate(t.field_marks)})
    for name, text in fields.items():
        issues.extend(_style(f"{lang}.{name}", lang, text, banned))

    if _words(t.lead) > LEAD_MAX_WORDS or sentence_count(t.lead) > LEAD_MAX_SENTENCES:
        issues.append(Issue(f"{lang}.lead", "ska vara högst 2 meningar och 45 ord"))
    if not MARKS_MIN <= len(t.field_marks) <= MARKS_MAX:
        issues.append(Issue(f"{lang}.field_marks", "ska ha 3 eller 4 punkter"))
    for i, mark in enumerate(t.field_marks):
        if _words(mark) > MARK_MAX_WORDS:
            issues.append(Issue(f"{lang}.field_marks[{i}]", "ska vara högst 16 ord"))
    if _words(t.voice) > VOICE_MAX_WORDS:
        issues.append(Issue(f"{lang}.voice", "ska vara högst 60 ord"))
    if _words(t.where_when) > WHERE_MAX_WORDS:
        issues.append(Issue(f"{lang}.where_when", "ska vara högst 70 ord"))
    if not META_MIN <= len(t.meta_description) <= META_MAX:
        issues.append(
            Issue(
                f"{lang}.meta_description",
                f"ska vara 120 till 155 tecken (är {len(t.meta_description)})",
            )
        )
    size = t.facts.size
    if size is not None and any(d in size.value for d in DASHES):
        issues.append(
            Issue(f"{lang}.facts.size", "storleken innehåller tankstreck", lang=lang, fact="size")
        )
    return issues


def check_text(out: WebTextOutput, banned: list[str]) -> list[Issue]:
    issues: list[Issue] = []
    for lang in LANGS:
        issues.extend(_check_lang(lang, getattr(out, lang), banned))
    return issues
```

- [ ] **Step 7: Kör testet igen**

Run: `uv run pytest tests/test_web_checks_text.py -v`
Expected: PASS (9 tester)

- [ ] **Step 8: Lint, typer och commit**

Run: `uv run ruff check src tests && uv run mypy`
Expected: inga fel. Klagar ruff på radlängden i `_style` (över 100 tecken), bryt `issues.extend(...)` över flera rader.

```bash
git add tools/content-pipeline/src/birdy_fetcher/web/model.py tools/content-pipeline/src/birdy_fetcher/web/checks.py tools/content-pipeline/prompts/web-banned-phrases.txt tools/content-pipeline/tests/web_fixtures.py tools/content-pipeline/tests/test_web_checks_text.py
git commit -m "feat(pipeline): textkontroller och förbjudna fraser för webbtexterna"
```

---

### Task 7: Faktakontroller och strykning av fakta

**Files:**
- Modify: `tools/content-pipeline/src/birdy_fetcher/web/checks.py` (lägg till i slutet)
- Test: `tools/content-pipeline/tests/test_web_checks_facts.py`

- [ ] **Step 1: Skriv testet**

```python
"""Tests for fact checks, plausibility and dropping facts (spec §7, checks 4 and 5)."""

from __future__ import annotations

from birdy_fetcher.web.checks import Issue, check_facts, drop_facts, quote_in_sources

from .web_fixtures import ARTICLES, valid_output


def test_valid_output_has_no_fact_issues() -> None:
    assert check_facts(valid_output(), ARTICLES) == []


def test_quote_matching_ignores_case_spaces_and_quote_styles() -> None:
    sources = ["Den  är “stannfågel” i hela Sverige."]
    assert quote_in_sources('den är "stannfågel" i hela sverige', sources)
    assert not quote_in_sources("i hela", sources)  # shorter than 20 characters
    assert not quote_in_sources("den är flyttfågel i hela Sverige", sources)


def test_quote_not_in_source_is_a_fact_issue() -> None:
    out = valid_output()
    assert out.sv.facts.size is not None
    out.sv.facts.size.quote = "cirka 16 centimeter lång och väger"
    issues = check_facts(out, ARTICLES)
    assert [(i.lang, i.fact) for i in issues] == [("sv", "size")]


def test_size_digits_must_appear_in_quote() -> None:
    out = valid_output()
    assert out.en.facts.size is not None
    out.en.facts.size.value = "About 15 cm"
    issues = check_facts(out, ARTICLES)
    assert [(i.lang, i.fact) for i in issues] == [("en", "size")]


def test_status_mismatch_between_languages_drops_both() -> None:
    out = valid_output()
    assert out.en.facts.sweden_status is not None
    out.en.facts.sweden_status.value = "passage"
    issues = check_facts(out, ARTICLES)
    assert sorted((i.lang, i.fact) for i in issues) == [
        ("en", "sweden_status"),
        ("sv", "sweden_status"),
    ]


def test_absent_status_contradicting_text_is_a_hard_issue() -> None:
    out = valid_output()
    assert out.sv.facts.sweden_status is not None and out.en.facts.sweden_status is not None
    out.sv.facts.sweden_status.value = "absent"
    out.en.facts.sweden_status.value = "absent"
    out.sv.where_when = "Stannfågel i hela landet."
    issues = check_facts(out, ARTICLES)
    hard = [i for i in issues if i.fact is None]
    assert [i.path for i in hard] == ["sv.where_when"]


def test_drop_facts_sets_only_the_named_facts_to_none() -> None:
    out = valid_output()
    dropped = drop_facts(out, [Issue("sv.facts.size", "x", lang="sv", fact="size")])
    assert dropped.sv.facts.size is None
    assert dropped.sv.facts.sweden_status is not None
    assert dropped.en.facts.size is not None
    assert out.sv.facts.size is not None  # the original is not changed
```

- [ ] **Step 2: Kör testet och se det faila**

Run: `uv run pytest tests/test_web_checks_facts.py -v`
Expected: FAIL med `ImportError: cannot import name 'check_facts'`

- [ ] **Step 3: Skriv koden**

Lägg till i slutet av `src/birdy_fetcher/web/checks.py` (och lägg till `from .wiki_full import WikiArticle` bland importerna högst upp):

```python
MIN_QUOTE_CHARS = 20
PRESENCE = {
    "sv": ("häckar i sverige", "vanlig i sverige", "stannfågel", "flyttfågel", "ses i sverige",
           "finns i sverige"),
    "en": ("breeds in sweden", "common in sweden", "resident in sweden", "seen in sweden",
           "found in sweden"),
}
_QUOTE_CHARS = {"’": "'", "‘": "'", "“": '"', "”": '"', "«": '"',
                "»": '"', " ": " "}


def _normalize(text: str) -> str:
    for src, dst in _QUOTE_CHARS.items():
        text = text.replace(src, dst)
    return " ".join(text.lower().split())


def quote_in_sources(quote: str, sources: list[str]) -> bool:
    q = _normalize(quote)
    return len(q) >= MIN_QUOTE_CHARS and any(q in _normalize(s) for s in sources)


def check_facts(out: WebTextOutput, articles: dict[str, WikiArticle]) -> list[Issue]:
    sources = [a.text for a in articles.values()]
    issues: list[Issue] = []
    for lang in LANGS:
        facts = getattr(out, lang).facts
        if facts.size is not None:
            path = f"{lang}.facts.size"
            if not quote_in_sources(facts.size.quote, sources):
                issues.append(Issue(path, "citatet finns inte i Wikipediatexten", lang, "size"))
            elif not set(re.findall(r"\d+", facts.size.value)) <= set(
                re.findall(r"\d+", facts.size.quote)
            ):
                issues.append(Issue(path, "siffrorna i storleken finns inte i citatet", lang, "size"))
        if facts.sweden_status is not None and not quote_in_sources(
            facts.sweden_status.quote, sources
        ):
            issues.append(
                Issue(f"{lang}.facts.sweden_status", "citatet finns inte i Wikipediatexten",
                      lang, "sweden_status")
            )

    sv_status, en_status = out.sv.facts.sweden_status, out.en.facts.sweden_status
    if sv_status and en_status and sv_status.value != en_status.value:
        for lang in LANGS:
            issues.append(
                Issue(f"{lang}.facts.sweden_status", "statusen skiljer sig mellan språken",
                      lang, "sweden_status")
            )

    for lang in LANGS:
        t = getattr(out, lang)
        status = t.facts.sweden_status
        if status is not None and status.value == "absent":
            text = t.where_when.lower()
            if any(phrase in text for phrase in PRESENCE[lang]):
                issues.append(
                    Issue(f"{lang}.where_when",
                          "beskriver förekomst i Sverige fast statusen är 'absent'")
                )
    return issues


def drop_facts(out: WebTextOutput, fact_issues: list[Issue]) -> WebTextOutput:
    """A copy of `out` where every fact named by an issue is set to None."""
    data = out.model_dump()
    for issue in fact_issues:
        if issue.lang is not None and issue.fact is not None:
            data[issue.lang]["facts"][issue.fact] = None
    return WebTextOutput.model_validate(data)
```

Formatera med `uv run ruff format src/birdy_fetcher/web/checks.py` om raderna blir för långa.

- [ ] **Step 4: Kör testerna**

Run: `uv run pytest tests/test_web_checks_facts.py tests/test_web_checks_text.py -v`
Expected: PASS (16 tester)

- [ ] **Step 5: Lint, typer och commit**

Run: `uv run ruff check src tests && uv run mypy`

```bash
git add tools/content-pipeline/src/birdy_fetcher/web/checks.py tools/content-pipeline/tests/test_web_checks_facts.py
git commit -m "feat(pipeline): källcitat, rimlighetskontroll och strykning av fakta"
```

---

### Task 8: Prompten och modellanropet

**Files:**
- Create: `tools/content-pipeline/prompts/web-v1.md`
- Create: `tools/content-pipeline/src/birdy_fetcher/web/writer.py`
- Modify: `tools/content-pipeline/src/birdy_fetcher/cost.py` (två nya prisrader)
- Test: `tools/content-pipeline/tests/test_web_writer.py`

- [ ] **Step 1: Lägg till priserna**

I `src/birdy_fetcher/cost.py`, ersätt `_PRICING` med:

```python
# Anthropic published pricing — $/1M tokens. Update yearly.
_PRICING = {
    "haiku": {"input": 0.80, "output": 4.00},  # claude-haiku-4-5
    "sonnet": {"input": 3.00, "output": 15.00},  # claude-sonnet-4-6
    "opus5": {"input": 5.00, "output": 25.00},  # claude-opus-5 (web step)
    "sonnet5": {"input": 2.00, "output": 10.00},  # claude-sonnet-5 (web step)
}
```

- [ ] **Step 2: Skriv prompten**

`prompts/web-v1.md`. Formatet är samma som de befintliga prompterna: allt efter `System:` är systemprompten och allt efter `User:` är användarmeddelandet. `{namn}` byts ut av `_split_prompt`.

```markdown
# web prompt v1 (artsidor på birdy.community, spec 2026-09-25)

System: You write short species texts for the field guide pages on birdy.community, in Swedish and English.
The reader is often outdoors with a phone and wants to know what the bird is and how to recognise it.

Source rule: use ONLY facts that are stated in the Wikipedia articles in the user message. Never add facts from memory. If the articles do not say something, leave it out and write shorter.

Style rules for both languages:
- Plain, concrete sentences, like a knowledgeable friend, not a brochure.
- No dashes of any kind: no em dash, no en dash, no double hyphen. Use a comma, a full stop or a colon instead. Write ranges with "till" in Swedish and "to" in English, for example "13 till 15 cm" and "13 to 15 cm".
- No exclamation marks. No first person (no jag, vi, oss, I, we, us, our, my). No questions to the reader.
- Never use any of these words or phrases: {banned_phrases}
- The Swedish text must read as natural Swedish written by a Swede, not as a translation. Use Swedish bird names in the Swedish text.
- Do not mention Birdy, apps, photos or Wikipedia.

Write every field in both languages. The Swedish and the English text say the same things, but each is written natively in its own language.
- lead: 1 or 2 sentences, at most 45 words. What the bird is and where people usually meet it.
- field_marks: 3 or 4 short items, at most 16 words each, no full stop at the end. What to look at to recognise it: plumage, bill, size compared with a familiar bird, behaviour. Mention differences between male and female when the articles do.
- voice: at most 60 words. How the song and the calls sound, described so that people can recognise them.
- where_when: at most 70 words. Where and when the bird is seen in Sweden: habitat, time of year, resident or migrant. If the species does not occur in Sweden, say so in the first sentence and then name the broad region where it lives.
- meta_description: 120 to 155 characters including spaces. Start with the bird's name, then say what the page offers: how to recognise it, its call and when it is seen.
- facts.size: the body length as the articles state it, written like "Cirka 14 cm" or "13 till 15 cm" in Swedish and "About 14 cm" or "13 to 15 cm" in English. The quote must be a fragment of at least 20 characters copied character for character from one of the articles, containing the numbers you used. If no article states the length, set size to null.
- facts.sweden_status: exactly one of resident, breeding_migrant, passage, winter_visitor, rare_visitor, absent. Use the same value in both languages. The quote must be a fragment of at least 20 characters copied character for character from one of the articles that supports the status. If the articles do not support any status, set it to null.

User: Species: {name_sv} (Swedish) and {name_en} (English), scientific name {scientific_name}.
Family: {family_sv} ({family}). Group on the site: {group_sv} / {group_en}.
Global IUCN Red List category: {iucn}.

Swedish Wikipedia article:
<article lang="sv">
{article_sv}
</article>

English Wikipedia article:
<article lang="en">
{article_en}
</article>
```

- [ ] **Step 3: Skriv testet**

`tests/test_web_writer.py`:

```python
"""Tests for web/writer.py: prompt, retry with feedback, cache and cost."""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

from anthropic.types import MessageParam

from birdy_fetcher.cache import Cache
from birdy_fetcher.cost import CostTracker
import pytest

from birdy_fetcher.web.checks import load_banned
from birdy_fetcher.web.model import WebTextOutput
from birdy_fetcher.web.source import SourceImage, SpeciesSource
from birdy_fetcher.web.writer import StructuredReply, WebTextWriter, render_prompt

from .web_fixtures import ARTICLES, valid_output

PIPELINE = Path(__file__).resolve().parents[1]
PROMPT = PIPELINE / "prompts" / "web-v1.md"
BANNED = load_banned(PIPELINE / "prompts" / "web-banned-phrases.txt")

SOURCE = SpeciesSource(
    qid="Q25485",
    scientific_name="Parus major",
    name_sv="Talgoxe",
    name_en="Great Tit",
    family="Paridae",
    family_sv="Mesar",
    ioc_order="Passeriformes",
    iucn="LC",
    marginalia_sv=None,
    marginalia_en=None,
    images=(SourceImage("hero", "Q25485/hero.webp", "CC0", None, "https://x"),),
)


@dataclass
class FakeClient:
    replies: list[StructuredReply]
    calls: list[list[MessageParam]] = field(default_factory=list)
    systems: list[str] = field(default_factory=list)

    async def parse_web_text(
        self, *, model: str, system: str, messages: list[MessageParam], max_tokens: int
    ) -> StructuredReply:
        self.calls.append(list(messages))
        self.systems.append(system)
        return self.replies.pop(0)


def _reply(output: WebTextOutput | None) -> StructuredReply:
    return StructuredReply(
        output=output,
        raw_text="{}",
        input_tokens=10_000,
        output_tokens=2_000,
        stop_reason="end_turn",
    )


def _writer(tmp_path: Path, client: FakeClient, regenerate: bool = False) -> WebTextWriter:
    return WebTextWriter(
        cache=Cache(tmp_path),
        cost=CostTracker(max_usd=None),
        client=client,
        prompt_path=PROMPT,
        banned=BANNED,
        model_key="opus",
        regenerate=regenerate,
    )


def test_render_prompt_fills_every_placeholder() -> None:
    system, user = render_prompt(
        PROMPT.read_text(encoding="utf-8"), SOURCE, ARTICLES, "Tättingar", "Songbirds", BANNED
    )
    assert "{" not in system and "{" not in user
    assert "fascinerande" in system
    assert "Talgoxe" in user and "Great Tit" in user and "Tättingar" in user
    assert "14 centimeter" in user and "14 centimetres" in user


async def test_valid_first_answer_is_one_call_and_costs(tmp_path: Path) -> None:
    client = FakeClient([_reply(valid_output())])
    writer = _writer(tmp_path, client)
    result = await writer.write(SOURCE, ARTICLES, "Tättingar", "Songbirds")
    assert result.output is not None and result.issues == [] and result.dropped_facts == []
    assert result.attempts == 1 and len(client.calls) == 1
    assert writer.cost.total_usd == pytest.approx(0.10)  # 10k in at $5/M + 2k out at $25/M


async def test_bad_first_answer_gets_feedback_and_second_try(tmp_path: Path) -> None:
    bad = valid_output()
    bad.sv.lead = "En fascinerande fågel. Den finns i hela Sverige."
    client = FakeClient([_reply(bad), _reply(valid_output())])
    result = await _writer(tmp_path, client).write(SOURCE, ARTICLES, "Tättingar", "Songbirds")
    assert result.issues == [] and result.attempts == 2
    feedback = client.calls[1][-1]
    assert feedback["role"] == "user"
    assert "sv.lead" in str(feedback["content"]) and "fascinerande" in str(feedback["content"])


async def test_fact_issue_left_after_retry_drops_the_fact(tmp_path: Path) -> None:
    bad = valid_output()
    assert bad.sv.facts.size is not None
    bad.sv.facts.size.quote = "en påhittad mening om storleken"
    client = FakeClient([_reply(bad), _reply(bad)])
    result = await _writer(tmp_path, client).write(SOURCE, ARTICLES, "Tättingar", "Songbirds")
    assert result.issues == []
    assert result.output is not None and result.output.sv.facts.size is None
    assert [i.fact for i in result.dropped_facts] == ["size"]


async def test_hard_issue_left_after_retry_is_reported(tmp_path: Path) -> None:
    bad = valid_output()
    bad.en.voice = "We love it!"
    client = FakeClient([_reply(bad), _reply(bad)])
    result = await _writer(tmp_path, client).write(SOURCE, ARTICLES, "Tättingar", "Songbirds")
    assert {i.path for i in result.issues} == {"en.voice"}


async def test_no_output_twice_is_reported(tmp_path: Path) -> None:
    client = FakeClient([_reply(None), _reply(None)])
    result = await _writer(tmp_path, client).write(SOURCE, ARTICLES, "Tättingar", "Songbirds")
    assert result.output is None and result.issues


async def test_cached_answer_is_reused_without_a_call(tmp_path: Path) -> None:
    client = FakeClient([_reply(valid_output())])
    await _writer(tmp_path, client).write(SOURCE, ARTICLES, "Tättingar", "Songbirds")
    second = FakeClient([])
    result = await _writer(tmp_path, second).write(SOURCE, ARTICLES, "Tättingar", "Songbirds")
    assert result.from_cache and second.calls == [] and result.output is not None
    third = FakeClient([_reply(valid_output())])
    await _writer(tmp_path, third, regenerate=True).write(
        SOURCE, ARTICLES, "Tättingar", "Songbirds"
    )
    assert len(third.calls) == 1
```

- [ ] **Step 4: Kör testet och se det faila**

Run: `uv run pytest tests/test_web_writer.py -v`
Expected: FAIL med `ModuleNotFoundError: No module named 'birdy_fetcher.web.writer'`

- [ ] **Step 5: Skriv koden**

`src/birdy_fetcher/web/writer.py`:

```python
"""One model call per species (both languages), checked, with one retry (spec §7)."""

from __future__ import annotations

import hashlib
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

from anthropic.types import MessageParam
from pydantic import ValidationError

from ..cache import Cache
from ..claude_summarizer import _split_prompt
from ..cost import CostTracker
from .checks import Issue, check_facts, check_text, drop_facts
from .model import WebTextOutput
from .source import SpeciesSource
from .wiki_full import WikiArticle

WEB_MODELS = {"opus": "claude-opus-5", "sonnet": "claude-sonnet-5"}
COST_KEYS = {"opus": "opus5", "sonnet": "sonnet5"}
PROMPT_VERSION = "web-v1"
MAX_TOKENS = 16_000
ATTEMPTS = 2


@dataclass
class StructuredReply:
    output: WebTextOutput | None
    raw_text: str
    input_tokens: int
    output_tokens: int
    stop_reason: str | None


class StructuredClient(Protocol):
    async def parse_web_text(
        self, *, model: str, system: str, messages: list[MessageParam], max_tokens: int
    ) -> StructuredReply: ...


class AnthropicStructuredClient:
    """The real client. `AsyncAnthropic()` finds ANTHROPIC_API_KEY or an `ant auth login` profile."""

    def __init__(self) -> None:
        from anthropic import AsyncAnthropic

        self._client = AsyncAnthropic()

    async def parse_web_text(
        self, *, model: str, system: str, messages: list[MessageParam], max_tokens: int
    ) -> StructuredReply:
        try:
            msg = await self._client.messages.parse(
                model=model,
                max_tokens=max_tokens,
                system=system,
                messages=messages,
                output_format=WebTextOutput,
            )
        except ValidationError as exc:
            return StructuredReply(None, str(exc), 0, 0, "invalid_output")
        text = "".join(block.text for block in msg.content if block.type == "text")
        return StructuredReply(
            output=msg.parsed_output,
            raw_text=text,
            input_tokens=msg.usage.input_tokens,
            output_tokens=msg.usage.output_tokens,
            stop_reason=msg.stop_reason,
        )


@dataclass
class WriteResult:
    output: WebTextOutput | None
    issues: list[Issue]
    dropped_facts: list[Issue]
    attempts: int
    from_cache: bool


def render_prompt(
    template: str,
    source: SpeciesSource,
    articles: dict[str, WikiArticle],
    group_sv: str,
    group_en: str,
    banned: list[str],
) -> tuple[str, str]:
    return _split_prompt(
        template,
        name_sv=source.name_sv,
        name_en=source.name_en,
        scientific_name=source.scientific_name,
        family=source.family,
        family_sv=source.family_sv,
        group_sv=group_sv,
        group_en=group_en,
        iucn=source.iucn,
        banned_phrases=", ".join(banned),
        article_sv=articles["sv"].text if "sv" in articles else "(no Swedish article)",
        article_en=articles["en"].text if "en" in articles else "(no English article)",
    )


def feedback_message(issues: list[Issue]) -> str:
    lines = "\n".join(f"- {i.path}: {i.message}" for i in issues)
    return (
        "Your answer broke these rules. Write the whole answer again with the same structure "
        "and fix only these points:\n" + lines
    )


@dataclass
class WebTextWriter:
    cache: Cache
    cost: CostTracker
    client: StructuredClient
    prompt_path: Path
    banned: list[str]
    model_key: str
    regenerate: bool = False

    @property
    def model_id(self) -> str:
        return WEB_MODELS[self.model_key]

    def _cache_name(self, template: str, articles: dict[str, WikiArticle]) -> str:
        prompt_hash = hashlib.sha256(template.encode("utf-8")).hexdigest()[:8]
        revs = "-".join(f"{lang}{articles[lang].revision}" for lang in sorted(articles))
        return f"web-text-{self.model_key}-{prompt_hash}-{revs}.json"

    def _finish(
        self, output: WebTextOutput, articles: dict[str, WikiArticle], attempts: int, cached: bool
    ) -> WriteResult:
        issues = check_text(output, self.banned) + check_facts(output, articles)
        hard = [i for i in issues if i.fact is None]
        facts = [i for i in issues if i.fact is not None]
        return WriteResult(drop_facts(output, facts), hard, facts, attempts, cached)

    async def write(
        self,
        source: SpeciesSource,
        articles: dict[str, WikiArticle],
        group_sv: str,
        group_en: str,
    ) -> WriteResult:
        template = self.prompt_path.read_text(encoding="utf-8")
        cache_name = self._cache_name(template, articles)
        cached = None if self.regenerate else self.cache.get(source.qid, cache_name)
        if cached is not None:
            return self._finish(
                WebTextOutput.model_validate_json(cached), articles, attempts=0, cached=True
            )

        system, user = render_prompt(template, source, articles, group_sv, group_en, self.banned)
        messages: list[MessageParam] = [{"role": "user", "content": user}]
        output: WebTextOutput | None = None
        issues: list[Issue] = []
        attempts = 0
        for attempt in range(1, ATTEMPTS + 1):
            attempts = attempt
            reply = await self.client.parse_web_text(
                model=self.model_id, system=system, messages=messages, max_tokens=MAX_TOKENS
            )
            self.cost.record(
                model=COST_KEYS[self.model_key],
                input_tokens=reply.input_tokens,
                output_tokens=reply.output_tokens,
            )
            if reply.output is None:
                issues = [
                    Issue("svar", f"modellen gav inget giltigt svar (stop_reason={reply.stop_reason})")
                ]
                continue
            output = reply.output
            issues = check_text(output, self.banned) + check_facts(output, articles)
            if not issues:
                break
            if attempt < ATTEMPTS:
                messages = [
                    *messages,
                    {"role": "assistant", "content": reply.raw_text or output.model_dump_json()},
                    {"role": "user", "content": feedback_message(issues)},
                ]

        if output is None:
            return WriteResult(None, issues, [], attempts, False)
        self.cache.put(source.qid, cache_name, output.model_dump_json(indent=2))
        return self._finish(output, articles, attempts=attempts, cached=False)
```

- [ ] **Step 6: Kör testerna**

Run: `uv run pytest tests/test_web_writer.py -v`
Expected: PASS (7 tester)

- [ ] **Step 7: Kör hela sviten, lint och typer**

Run: `uv run pytest && uv run ruff check src tests && uv run mypy`
Expected: alla tester gröna (även de gamla), inga lint- eller typfel. Klagar mypy på typen för `messages` i `messages.parse`, behåll `list[MessageParam]` och läs felet: SDK:ns parameter heter `messages: Iterable[MessageParam]`, så en lista ska godtas.

- [ ] **Step 8: Commit**

```bash
git add tools/content-pipeline/prompts/web-v1.md tools/content-pipeline/src/birdy_fetcher/web/writer.py tools/content-pipeline/src/birdy_fetcher/cost.py tools/content-pipeline/tests/test_web_writer.py
git commit -m "feat(pipeline): prompt och modellanrop för webbtexterna (nytt försök med återkoppling)"
```

---

### Task 9: Bilderna

**Files:**
- Create: `tools/content-pipeline/src/birdy_fetcher/web/images.py`
- Test: `tools/content-pipeline/tests/test_web_images.py`

- [ ] **Step 1: Skriv testet**

```python
"""Tests for web/images.py: downscaled WebP and photo metadata (spec §8)."""

from __future__ import annotations

from pathlib import Path

import pytest
from PIL import Image

from birdy_fetcher.web.images import MissingImageError, prepare_images
from birdy_fetcher.web.source import SourceImage, SpeciesSource


def _source(images: tuple[SourceImage, ...]) -> SpeciesSource:
    return SpeciesSource("Q1", "Parus major", "Talgoxe", "Great Tit", "Paridae", "Mesar",
                         "Passeriformes", "LC", None, None, images)


def _webp(path: Path, size: tuple[int, int]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.new("RGB", size, (120, 140, 90)).save(path, "WEBP")


HERO = SourceImage("hero", "Q1/hero.webp", "CC BY-SA 4.0", "<a>Ann</a>",
                   "https://commons.wikimedia.org/wiki/File:A b.jpg")
EXTRA = SourceImage("secondary", "Q1/secondary-1.webp", "CC0", None, "https://c/File:C.jpg")


def test_hero_and_extra_are_downscaled(tmp_path: Path) -> None:
    assets, out = tmp_path / "assets", tmp_path / "out"
    _webp(assets / "Q1/hero.webp", (2400, 1600))
    _webp(assets / "Q1/secondary-1.webp", (1800, 1200))
    images = prepare_images(_source((HERO, EXTRA)), asset_images=assets, out_root=out)
    assert [(i.role, i.file, i.width, i.height) for i in images] == [
        ("hero", "Q1/hero.webp", 1600, 1067),
        ("extra", "Q1/extra.webp", 1200, 800),
    ]
    assert images[0].author == "Ann"
    assert images[0].license_url == "https://creativecommons.org/licenses/by-sa/4.0/"
    assert images[0].source_url.endswith("File:A_b.jpg")
    assert images[1].license_url is None
    with Image.open(out / "Q1/hero.webp") as im:
        assert im.format == "WEBP" and im.size == (1600, 1067)


def test_small_images_are_not_upscaled(tmp_path: Path) -> None:
    assets = tmp_path / "assets"
    _webp(assets / "Q1/hero.webp", (900, 600))
    images = prepare_images(_source((HERO,)), asset_images=assets, out_root=tmp_path / "out")
    assert (images[0].width, images[0].height) == (900, 600)


def test_missing_extra_is_skipped_but_missing_hero_is_an_error(tmp_path: Path) -> None:
    assets = tmp_path / "assets"
    _webp(assets / "Q1/hero.webp", (2000, 1000))
    images = prepare_images(_source((HERO, EXTRA)), asset_images=assets, out_root=tmp_path / "o")
    assert [i.role for i in images] == ["hero"]
    with pytest.raises(MissingImageError):
        prepare_images(_source((EXTRA,)), asset_images=assets, out_root=tmp_path / "o")
```

- [ ] **Step 2: Kör testet och se det faila**

Run: `uv run pytest tests/test_web_images.py -v`
Expected: FAIL med `ModuleNotFoundError`

- [ ] **Step 3: Skriv koden**

`src/birdy_fetcher/web/images.py`:

```python
"""Hero plus one extra photo per species, downscaled to WebP for the website (spec §8)."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from PIL import Image

from .licenses import clean_author, commons_url, license_url
from .source import SourceImage, SpeciesSource

MAX_WIDTH = {"hero": 1600, "extra": 1200}
QUALITY = 78


class MissingImageError(FileNotFoundError):
    pass


@dataclass(frozen=True)
class ImageOut:
    role: str
    file: str
    width: int
    height: int
    author: str | None
    license: str
    license_url: str | None
    source_url: str


def _pick(source: SpeciesSource) -> list[tuple[str, SourceImage]]:
    hero = next((i for i in source.images if i.role == "hero"), None)
    if hero is None:
        raise MissingImageError(f"{source.qid} saknar huvudfoto i artfilen")
    extra = next((i for i in source.images if i.role == "secondary"), None)
    return [("hero", hero)] + ([("extra", extra)] if extra is not None else [])


def prepare_images(source: SpeciesSource, *, asset_images: Path, out_root: Path) -> list[ImageOut]:
    result: list[ImageOut] = []
    for role, img in _pick(source):
        src = asset_images / img.path
        if not src.exists():
            if role == "hero":
                raise MissingImageError(f"{source.qid}: huvudfotot finns inte: {src}")
            continue
        rel = f"{source.qid}/{role}.webp"
        dst = out_root / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        with Image.open(src) as loaded:
            im = loaded.convert("RGB")
            limit = MAX_WIDTH[role]
            if im.width > limit:
                im = im.resize((limit, round(im.height * limit / im.width)), Image.Resampling.LANCZOS)
            im.save(dst, "WEBP", quality=QUALITY, method=6)
            width, height = im.size
        result.append(
            ImageOut(
                role=role,
                file=rel,
                width=width,
                height=height,
                author=clean_author(img.author),
                license=img.license,
                license_url=license_url(img.license),
                source_url=commons_url(img.source_url),
            )
        )
    return result
```

- [ ] **Step 4: Kör testet igen**

Run: `uv run pytest tests/test_web_images.py -v`
Expected: PASS (3 tester)

- [ ] **Step 5: Lint, typer och commit**

Run: `uv run ruff check src tests && uv run mypy`

```bash
git add tools/content-pipeline/src/birdy_fetcher/web/images.py tools/content-pipeline/tests/test_web_images.py
git commit -m "feat(pipeline): nedskalade foton med licensdata för artsidorna"
```

---

### Task 10: Artens JSON-fil

**Files:**
- Create: `tools/content-pipeline/src/birdy_fetcher/web/output.py`
- Test: `tools/content-pipeline/tests/test_web_output.py`

- [ ] **Step 1: Skriv testet**

```python
"""Tests for web/output.py: the record in spec appendix C, and approved files are kept."""

from __future__ import annotations

import json
from datetime import UTC, datetime
from pathlib import Path

from birdy_fetcher.web.images import ImageOut
from birdy_fetcher.web.output import build_record, is_approved, write_record
from birdy_fetcher.web.source import SpeciesSource

from .web_fixtures import ARTICLES, valid_output

SOURCE = SpeciesSource("Q25485", "Parus major", "Talgoxe", "Great Tit", "Paridae", "Mesar",
                       "Passeriformes", "LC", "Söker frön.", "Forages.", ())
IMAGE = ImageOut("hero", "Q25485/hero.webp", 1600, 1067, "Hobbyfotowiki", "CC0", None,
                 "https://commons.wikimedia.org/wiki/File:A.jpg")
NOW = datetime(2026, 10, 1, 12, 0, tzinfo=UTC)


def _record(errors: list[str] | None = None) -> dict[str, object]:
    return build_record(
        source=SOURCE,
        group="songbirds",
        text=valid_output(),
        articles=ARTICLES,
        images=[IMAGE],
        errors=errors or [],
        model_id="claude-opus-5",
        generated_at=NOW,
    )


def test_record_matches_appendix_c() -> None:
    rec = _record()
    assert rec["qid"] == "Q25485" and rec["status"] == "ok" and rec["review"] == "unreviewed"
    assert rec["slug"] == {"sv": "talgoxe", "en": "great-tit"}
    assert rec["names"] == {"sv": "Talgoxe", "en": "Great Tit", "scientific": "Parus major"}
    assert rec["family"] == {"latin": "Paridae", "sv": "Mesar"}
    assert rec["group"] == "songbirds" and rec["iucn"] == "LC"
    assert rec["marginalia"] == {"sv": "Söker frön.", "en": "Forages."}
    assert rec["wikipedia"] == {
        "sv": {"title": "Talgoxe", "revision": "111"},
        "en": {"title": "Great tit", "revision": "222"},
    }
    assert rec["generated"] == {
        "model": "claude-opus-5", "prompt": "web-v1", "at": "2026-10-01T12:00:00+00:00"
    }
    text = rec["text"]
    assert isinstance(text, dict)
    sv = text["sv"]
    assert set(sv) == {"lead", "fieldMarks", "voice", "whereWhen", "metaDescription", "facts"}
    assert sv["facts"]["swedenStatus"]["value"] == "resident"
    images = rec["images"]
    assert isinstance(images, list)
    assert images[0] == {
        "role": "hero", "file": "Q25485/hero.webp", "width": 1600, "height": 1067,
        "author": "Hobbyfotowiki", "license": "CC0", "licenseUrl": None,
        "sourceUrl": "https://commons.wikimedia.org/wiki/File:A.jpg",
    }


def test_errors_make_the_record_failed() -> None:
    rec = _record(["en.voice: är skriven i första person"])
    assert rec["status"] == "failed" and rec["errors"] == ["en.voice: är skriven i första person"]


def test_write_keeps_approved_files_unless_forced(tmp_path: Path) -> None:
    rec = _record()
    assert write_record(rec, tmp_path, force=False) == "written"
    path = tmp_path / "Q25485.json"
    saved = json.loads(path.read_text(encoding="utf-8"))
    saved["review"] = "approved"
    path.write_text(json.dumps(saved), encoding="utf-8")
    assert is_approved(path)
    assert write_record(rec, tmp_path, force=False) == "skipped"
    assert write_record(rec, tmp_path, force=True) == "written"
    assert json.loads(path.read_text(encoding="utf-8"))["review"] == "unreviewed"
```

- [ ] **Step 2: Kör testet och se det faila**

Run: `uv run pytest tests/test_web_output.py -v`
Expected: FAIL med `ModuleNotFoundError`

- [ ] **Step 3: Skriv koden**

`src/birdy_fetcher/web/output.py`:

```python
"""The species record in website/src/data/species/<QID>.json (spec appendix C)."""

from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path
from typing import Any

from .images import ImageOut
from .model import LangText, WebTextOutput
from .slugs import slugify
from .source import SpeciesSource
from .wiki_full import WikiArticle
from .writer import PROMPT_VERSION


def _lang(t: LangText) -> dict[str, Any]:
    size, status = t.facts.size, t.facts.sweden_status
    return {
        "lead": t.lead,
        "fieldMarks": list(t.field_marks),
        "voice": t.voice,
        "whereWhen": t.where_when,
        "metaDescription": t.meta_description,
        "facts": {
            "size": size.model_dump() if size else None,
            "swedenStatus": status.model_dump() if status else None,
        },
    }


def build_record(
    *,
    source: SpeciesSource,
    group: str,
    text: WebTextOutput | None,
    articles: dict[str, WikiArticle],
    images: list[ImageOut],
    errors: list[str],
    model_id: str,
    generated_at: datetime,
) -> dict[str, Any]:
    has_marginalia = source.marginalia_sv or source.marginalia_en
    return {
        "qid": source.qid,
        "status": "failed" if errors or text is None else "ok",
        "review": "unreviewed",
        "slug": {"sv": slugify(source.name_sv, "sv"), "en": slugify(source.name_en, "en")},
        "names": {"sv": source.name_sv, "en": source.name_en, "scientific": source.scientific_name},
        "family": {"latin": source.family, "sv": source.family_sv},
        "group": group,
        "iucn": source.iucn,
        "marginalia": (
            {"sv": source.marginalia_sv, "en": source.marginalia_en} if has_marginalia else None
        ),
        "images": [
            {
                "role": i.role,
                "file": i.file,
                "width": i.width,
                "height": i.height,
                "author": i.author,
                "license": i.license,
                "licenseUrl": i.license_url,
                "sourceUrl": i.source_url,
            }
            for i in images
        ],
        "wikipedia": {
            lang: (
                {"title": articles[lang].title, "revision": articles[lang].revision}
                if lang in articles
                else None
            )
            for lang in ("sv", "en")
        },
        "text": {"sv": _lang(text.sv), "en": _lang(text.en)} if text is not None else None,
        "generated": {
            "model": model_id,
            "prompt": PROMPT_VERSION,
            "at": generated_at.isoformat(),
        },
        "errors": errors,
    }


def is_approved(path: Path) -> bool:
    if not path.exists():
        return False
    data: dict[str, Any] = json.loads(path.read_text(encoding="utf-8"))
    return data.get("review") == "approved"


def write_record(record: dict[str, Any], out_dir: Path, *, force: bool) -> str:
    """Writes the record. A file already marked review: approved is kept unless `force`."""
    path = out_dir / f"{record['qid']}.json"
    if not force and is_approved(path):
        return "skipped"
    out_dir.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(record, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return "written"
```

- [ ] **Step 4: Kör testet igen**

Run: `uv run pytest tests/test_web_output.py -v`
Expected: PASS (3 tester)

- [ ] **Step 5: Lint, typer och commit**

Run: `uv run ruff check src tests && uv run mypy`

```bash
git add tools/content-pipeline/src/birdy_fetcher/web/output.py tools/content-pipeline/tests/test_web_output.py
git commit -m "feat(pipeline): artens JSON för webben, granskade filer skrivs inte över"
```

---

### Task 11: Rapporten

**Files:**
- Create: `tools/content-pipeline/src/birdy_fetcher/web/report.py`
- Test: `tools/content-pipeline/tests/test_web_report.py`

- [ ] **Step 1: Skriv testet**

```python
"""Tests for web/report.py."""

from __future__ import annotations

from birdy_fetcher.web.report import SpeciesOutcome, render_report


def test_report_lists_counts_failures_and_dropped_facts() -> None:
    outcomes = [
        SpeciesOutcome("Q1", "Talgoxe", "ok", [], [], attempts=1, from_cache=False),
        SpeciesOutcome("Q2", "Blåmes", "ok", [], ["sv.facts.size: citatet finns inte"], 2, False),
        SpeciesOutcome("Q3", "Svartmes", "failed", ["en.voice: första person"], [], 2, False),
        SpeciesOutcome("Q4", "Tofsmes", "skipped", ["redan granskad"], [], 0, False),
    ]
    text = render_report(outcomes, cost_usd=1.234, model_id="claude-opus-5", date="2026-10-01")
    assert "# Webbtexter 2026-10-01" in text
    assert "`claude-opus-5`" in text and "1.23" in text
    assert "| ok | 2 |" in text and "| failed | 1 |" in text and "| skipped | 1 |" in text
    assert "Svartmes (Q3)" in text and "en.voice: första person" in text
    assert "Blåmes (Q2)" in text and "sv.facts.size" in text
    assert "Två försök: 2" in text
```

- [ ] **Step 2: Kör testet och se det faila**

Run: `uv run pytest tests/test_web_report.py -v`
Expected: FAIL med `ModuleNotFoundError`

- [ ] **Step 3: Skriv koden**

`src/birdy_fetcher/web/report.py`:

```python
"""Markdown report for a web run: tools/content-pipeline/reports/web-<date>.md."""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass


@dataclass(frozen=True)
class SpeciesOutcome:
    qid: str
    name_sv: str
    status: str  # ok | failed | skipped | dry-run
    errors: list[str]
    dropped_facts: list[str]
    attempts: int
    from_cache: bool


def render_report(
    outcomes: list[SpeciesOutcome], *, cost_usd: float, model_id: str, date: str
) -> str:
    counts = Counter(o.status for o in outcomes)
    lines = [
        f"# Webbtexter {date}",
        "",
        f"Modell: `{model_id}`. Kostnad för körningen: ${cost_usd:.2f}.",
        "",
        "| Utfall | Antal |",
        "|---|---|",
        *(f"| {status} | {counts[status]} |" for status in ("ok", "failed", "skipped", "dry-run")),
        "",
        f"Två försök: {sum(1 for o in outcomes if o.attempts == 2)}. "
        f"Från cache: {sum(1 for o in outcomes if o.from_cache)}.",
        "",
        "## Arter som inte fick någon sida",
        "",
    ]
    failed = [o for o in outcomes if o.status == "failed"]
    lines += [f"- **{o.name_sv} ({o.qid})**: {'; '.join(o.errors)}" for o in failed] or ["Inga."]
    lines += ["", "## Faktauppgifter som ströks", ""]
    dropped = [o for o in outcomes if o.dropped_facts]
    lines += [f"- **{o.name_sv} ({o.qid})**: {'; '.join(o.dropped_facts)}" for o in dropped] or [
        "Inga."
    ]
    skipped = [o for o in outcomes if o.status == "skipped"]
    lines += ["", "## Hoppades över", ""]
    lines += [f"- {o.name_sv} ({o.qid}): {'; '.join(o.errors)}" for o in skipped] or ["Inga."]
    return "\n".join(lines) + "\n"
```

- [ ] **Step 4: Kör testet igen, lint, typer och commit**

Run: `uv run pytest tests/test_web_report.py -v && uv run ruff check src tests && uv run mypy`
Expected: PASS, inga fel

```bash
git add tools/content-pipeline/src/birdy_fetcher/web/report.py tools/content-pipeline/tests/test_web_report.py
git commit -m "feat(pipeline): rapport för webbtexterna"
```

---

### Task 12: Körningen och kommandot `web`

**Files:**
- Create: `tools/content-pipeline/src/birdy_fetcher/web/run.py`
- Modify: `tools/content-pipeline/src/birdy_fetcher/cli.py` (nytt kommando före `if __name__ == "__main__":`)
- Test: `tools/content-pipeline/tests/test_web_run.py`

- [ ] **Step 1: Skriv testet**

`tests/test_web_run.py` bygger ett litet repo i `tmp_path` med två arter, testbilder, en liten grupptabell och falska klienter:

```python
"""End-to-end test for web/run.py with fake Wikipedia and a fake model."""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from datetime import UTC, datetime
from pathlib import Path

import pytest
from anthropic.types import MessageParam
from PIL import Image

from birdy_fetcher.web.run import SlugCollisionError, WebPaths, WebRunOptions, run_web
from birdy_fetcher.web.wiki_full import WikiArticle
from birdy_fetcher.web.writer import StructuredReply

from .test_web_source import _write
from .web_fixtures import ARTICLES, valid_output

PIPELINE = Path(__file__).resolve().parents[1]


def _repo(tmp_path: Path, species: list[tuple[str, str, str]]) -> WebPaths:
    paths = WebPaths(repo_root=tmp_path)
    for qid, sv, en in species:
        _write(paths.species_root, qid, sv, en, "approved")
        img = paths.asset_images / qid / "hero.webp"
        img.parent.mkdir(parents=True, exist_ok=True)
        Image.new("RGB", (2000, 1000), (90, 110, 70)).save(img, "WEBP")
    paths.family_groups.parent.mkdir(parents=True, exist_ok=True)
    paths.family_groups.write_text(
        "order: [songbirds, other]\ngroups:\n"
        "  songbirds: {keyed_by: order, ioc_order: Passeriformes}\n"
        "  other: {families: [Cuculidae]}\n",
        encoding="utf-8",
    )
    base = {"name": {"sv": "X", "en": "X"}, "photo": species[0][0], "intro": {"sv": "a", "en": "a"}}
    paths.web_groups.parent.mkdir(parents=True, exist_ok=True)
    paths.web_groups.write_text(
        json.dumps({
            "groups": [
                {**base, "key": "songbirds", "slug": {"sv": "tattingar", "en": "songbirds"}},
                {**base, "key": "other", "slug": {"sv": "ovriga-faglar", "en": "other-birds"}},
            ],
            "common": [species[0][0]],
        }),
        encoding="utf-8",
    )
    paths.prompt.parent.mkdir(parents=True, exist_ok=True)
    paths.prompt.write_text((PIPELINE / "prompts/web-v1.md").read_text(encoding="utf-8"),
                            encoding="utf-8")
    paths.banned.write_text("fascinerande\n", encoding="utf-8")
    return paths


@dataclass
class FakeWiki:
    async def articles(self, qid: str, *, refresh: bool = False) -> dict[str, WikiArticle]:
        return {} if qid == "Q9" else ARTICLES


@dataclass
class FakeClient:
    calls: int = 0
    seen: list[str] = field(default_factory=list)

    async def parse_web_text(
        self, *, model: str, system: str, messages: list[MessageParam], max_tokens: int
    ) -> StructuredReply:
        self.calls += 1
        return StructuredReply(valid_output(), "{}", 1000, 500, "end_turn")


def _options(**kw: object) -> WebRunOptions:
    base: dict[str, object] = dict(qids=(), model_key="opus", max_cost=None, force=False,
                                   refresh_sources=False, regenerate=False, workers=2,
                                   dry_run=False)
    base.update(kw)
    return WebRunOptions(**base)  # type: ignore[arg-type]


NOW = datetime(2026, 10, 1, tzinfo=UTC)


async def test_run_writes_records_images_and_report(tmp_path: Path) -> None:
    paths = _repo(tmp_path, [("Q1", "Talgoxe", "Great Tit"), ("Q9", "Blåmes", "Blue Tit")])
    client = FakeClient()
    outcomes = await run_web(paths, _options(), client=client, wiki=FakeWiki(), now=NOW)
    by_qid = {o.qid: o for o in outcomes}
    assert by_qid["Q1"].status == "ok"
    assert by_qid["Q9"].status == "failed"
    assert "Wikipedia" in by_qid["Q9"].errors[0]
    record = json.loads((paths.data_out / "Q1.json").read_text(encoding="utf-8"))
    assert record["status"] == "ok" and record["group"] == "songbirds"
    assert (paths.images_out / "Q1/hero.webp").exists()
    failed = json.loads((paths.data_out / "Q9.json").read_text(encoding="utf-8"))
    assert failed["status"] == "failed" and failed["text"] is None
    assert (paths.reports / "web-2026-10-01.md").exists()
    assert client.calls == 1


async def test_approved_species_are_skipped_without_a_call(tmp_path: Path) -> None:
    paths = _repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    await run_web(paths, _options(), client=FakeClient(), wiki=FakeWiki(), now=NOW)
    record_path = paths.data_out / "Q1.json"
    record = json.loads(record_path.read_text(encoding="utf-8"))
    record["review"] = "approved"
    record_path.write_text(json.dumps(record), encoding="utf-8")
    client = FakeClient()
    outcomes = await run_web(paths, _options(regenerate=True), client=client, wiki=FakeWiki(),
                             now=NOW)
    assert outcomes[0].status == "skipped" and client.calls == 0


async def test_dry_run_writes_nothing(tmp_path: Path) -> None:
    paths = _repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    outcomes = await run_web(paths, _options(dry_run=True), client=None, wiki=FakeWiki(), now=NOW)
    assert outcomes[0].status == "dry-run"
    assert not paths.data_out.exists() and not paths.reports.exists()


async def test_slug_collision_stops_the_run(tmp_path: Path) -> None:
    paths = _repo(tmp_path, [("Q1", "Tättingar", "Great Tit")])
    with pytest.raises(SlugCollisionError, match="tattingar"):
        await run_web(paths, _options(), client=FakeClient(), wiki=FakeWiki(), now=NOW)
```

- [ ] **Step 2: Kör testet och se det faila**

Run: `uv run pytest tests/test_web_run.py -v`
Expected: FAIL med `ModuleNotFoundError: No module named 'birdy_fetcher.web.run'`

- [ ] **Step 3: Skriv körningen**

`src/birdy_fetcher/web/run.py`:

```python
"""Runs the web step for the approved species (spec 2026-09-25 §7 and §8)."""

from __future__ import annotations

import asyncio
from collections import Counter
from collections.abc import Sequence
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Protocol

from ..cache import Cache
from ..cost import CostTracker, MaxCostExceeded
from .checks import load_banned
from .groups import GroupTable
from .images import prepare_images
from .output import build_record, is_approved, write_record
from .report import SpeciesOutcome, render_report
from .slugs import slugify
from .source import SpeciesSource, load_approved
from .wiki_full import FullWikiClient, WikiArticle
from .writer import WEB_MODELS, AnthropicStructuredClient, StructuredClient, WebTextWriter


class SlugCollisionError(ValueError):
    pass


class ArticleSource(Protocol):
    async def articles(self, qid: str, *, refresh: bool = False) -> dict[str, WikiArticle]: ...


@dataclass(frozen=True)
class WebPaths:
    repo_root: Path

    @property
    def pipeline_root(self) -> Path:
        return self.repo_root / "tools" / "content-pipeline"

    @property
    def species_root(self) -> Path:
        return self.repo_root / "shared" / "content" / "species"

    @property
    def asset_images(self) -> Path:
        return self.repo_root / "asset-pack" / "src" / "main" / "assets" / "images"

    @property
    def family_groups(self) -> Path:
        return (self.repo_root / "shared" / "content" / "src" / "jvmMain" / "resources"
                / "family_groups.yaml")

    @property
    def web_groups(self) -> Path:
        return self.repo_root / "website" / "src" / "data" / "species-groups.json"

    @property
    def data_out(self) -> Path:
        return self.repo_root / "website" / "src" / "data" / "species"

    @property
    def images_out(self) -> Path:
        return self.repo_root / "website" / "src" / "assets" / "species"

    @property
    def reports(self) -> Path:
        return self.pipeline_root / "reports"

    @property
    def prompt(self) -> Path:
        return self.pipeline_root / "prompts" / "web-v1.md"

    @property
    def banned(self) -> Path:
        return self.pipeline_root / "prompts" / "web-banned-phrases.txt"


@dataclass(frozen=True)
class WebRunOptions:
    qids: tuple[str, ...]
    model_key: str
    max_cost: float | None
    force: bool
    refresh_sources: bool
    regenerate: bool
    workers: int
    dry_run: bool


def check_slug_collisions(sources: Sequence[SpeciesSource], groups: GroupTable) -> None:
    for lang in ("sv", "en"):
        slugs = [slugify(s.name_sv if lang == "sv" else s.name_en, lang) for s in sources]
        slugs += [g.slug_sv if lang == "sv" else g.slug_en for g in groups.groups]
        dupes = sorted(slug for slug, n in Counter(slugs).items() if n > 1)
        if dupes:
            raise SlugCollisionError(f"Samma adress används två gånger ({lang}): {dupes}")


async def run_web(
    paths: WebPaths,
    options: WebRunOptions,
    *,
    client: StructuredClient | None,
    wiki: ArticleSource | None = None,
    now: datetime | None = None,
) -> list[SpeciesOutcome]:
    now = now or datetime.now(UTC)
    cache = Cache(paths.pipeline_root / ".cache")
    groups = GroupTable(paths.family_groups, paths.web_groups)
    all_approved = load_approved(paths.species_root)
    sources = load_approved(paths.species_root, options.qids) if options.qids else all_approved
    check_slug_collisions(all_approved, groups)
    approved = {s.qid for s in all_approved}
    missing = [q for q in groups.common + [g.photo for g in groups.groups] if q not in approved]
    if missing:
        raise ValueError(f"species-groups.json pekar på arter som inte är granskade: {missing}")

    wiki = wiki or FullWikiClient(cache=cache)
    cost = CostTracker(max_usd=options.max_cost)
    writer: WebTextWriter | None = None
    if not options.dry_run:
        writer = WebTextWriter(
            cache=cache,
            cost=cost,
            client=client or AnthropicStructuredClient(),
            prompt_path=paths.prompt,
            banned=load_banned(paths.banned),
            model_key=options.model_key,
            regenerate=options.regenerate,
        )
    stop = asyncio.Event()
    semaphore = asyncio.Semaphore(options.workers)

    async def one(source: SpeciesSource) -> SpeciesOutcome:
        async with semaphore:
            return await _process(source, paths, options, groups, wiki, writer, stop, now)

    outcomes = list(await asyncio.gather(*(one(s) for s in sources)))
    if not options.dry_run:
        paths.reports.mkdir(parents=True, exist_ok=True)
        report = render_report(outcomes, cost_usd=cost.total_usd,
                               model_id=WEB_MODELS[options.model_key],
                               date=now.date().isoformat())
        (paths.reports / f"web-{now.date().isoformat()}.md").write_text(report, encoding="utf-8")
    return outcomes


async def _process(
    source: SpeciesSource,
    paths: WebPaths,
    options: WebRunOptions,
    groups: GroupTable,
    wiki: ArticleSource,
    writer: WebTextWriter | None,
    stop: asyncio.Event,
    now: datetime,
) -> SpeciesOutcome:
    def outcome(status: str, errors: list[str], dropped: list[str] | None = None,
                attempts: int = 0, cached: bool = False) -> SpeciesOutcome:
        return SpeciesOutcome(source.qid, source.name_sv, status, errors, dropped or [],
                              attempts, cached)

    if not options.force and is_approved(paths.data_out / f"{source.qid}.json"):
        return outcome("skipped", ["redan granskad (review: approved)"])
    articles = await wiki.articles(source.qid, refresh=options.refresh_sources)
    group = groups.group_for(family=source.family, ioc_order=source.ioc_order)
    model_id = WEB_MODELS[options.model_key]

    if options.dry_run or writer is None:
        sizes = ", ".join(f"{lang} {len(a.text)} tecken" for lang, a in articles.items())
        return outcome("dry-run", [sizes or "ingen artikel"])

    if not articles:
        errors = ["ingen Wikipediaartikel på svenska eller engelska"]
        images = prepare_images(source, asset_images=paths.asset_images, out_root=paths.images_out)
        record = build_record(source=source, group=group, text=None, articles=articles,
                              images=images, errors=errors, model_id=model_id, generated_at=now)
        write_record(record, paths.data_out, force=options.force)
        return outcome("failed", errors)

    if stop.is_set():
        return outcome("skipped", ["kostnadstaket nåddes, körs vid nästa körning"])
    web_group = groups.by_key(group)
    try:
        result = await writer.write(source, articles, web_group.name_sv, web_group.name_en)
    except MaxCostExceeded as exc:
        stop.set()
        return outcome("skipped", [f"kostnadstaket nåddes: {exc}"])

    errors = [f"{i.path}: {i.message}" for i in result.issues]
    dropped = [f"{i.path}: {i.message}" for i in result.dropped_facts]
    images = prepare_images(source, asset_images=paths.asset_images, out_root=paths.images_out)
    record = build_record(source=source, group=group, text=result.output, articles=articles,
                          images=images, errors=errors, model_id=model_id, generated_at=now)
    write_record(record, paths.data_out, force=options.force)
    status = "ok" if record["status"] == "ok" else "failed"
    return outcome(status, errors, dropped, result.attempts, result.from_cache)
```

- [ ] **Step 4: Kör testet igen**

Run: `uv run pytest tests/test_web_run.py -v`
Expected: PASS (4 tester)

- [ ] **Step 5: Lägg till kommandot**

I `src/birdy_fetcher/cli.py`, lägg till före `if __name__ == "__main__":`:

```python
@main.command()
@click.option("--species", multiple=True, help="Q-ID(s). Utan flaggan körs alla granskade arter.")
@click.option("--model", "model_key", type=click.Choice(["opus", "sonnet"]), default="opus")
@click.option("--max-cost", type=float, default=None, help="Kostnadstak i USD för körningen.")
@click.option("--force", is_flag=True, help="Skriv över arter som redan har review: approved.")
@click.option("--refresh-sources", is_flag=True, help="Hämta Wikidata och Wikipedia på nytt.")
@click.option("--regenerate", is_flag=True, help="Fråga modellen igen trots cachat svar.")
@click.option("--workers", type=int, default=4)
@click.option("--dry-run", is_flag=True, help="Hämta källor och visa artikelstorlek, inget anrop.")
def web(
    species: tuple[str, ...],
    model_key: str,
    max_cost: float | None,
    force: bool,
    refresh_sources: bool,
    regenerate: bool,
    workers: int,
    dry_run: bool,
) -> None:
    """Webbtexter, foton och licensdata för artsidorna på birdy.community."""
    from collections import Counter

    from rich.console import Console

    from .web.run import WebPaths, WebRunOptions, run_web

    pipeline_root = Path(__file__).resolve().parent.parent.parent
    paths = WebPaths(repo_root=pipeline_root.parent.parent)
    options = WebRunOptions(
        qids=species,
        model_key=model_key,
        max_cost=max_cost,
        force=force,
        refresh_sources=refresh_sources,
        regenerate=regenerate,
        workers=workers,
        dry_run=dry_run,
    )
    outcomes = asyncio.run(run_web(paths, options, client=None))
    console = Console()
    for o in outcomes:
        if o.status != "ok":
            console.print(f"{o.status:8} {o.name_sv} ({o.qid}): {'; '.join(o.errors)}")
    counts = Counter(o.status for o in outcomes)
    console.print(f"Klart: {dict(counts)}. Rapport i {paths.reports}.")
    if counts["failed"]:
        console.print("[yellow]Några arter fick ingen sida. Se rapporten.[/yellow]")
```

- [ ] **Step 6: Röktest av kommandot**

Run: `uv run birdy-fetcher web --help`
Expected: hjälptexten med alla flaggor ovan

- [ ] **Step 7: Hela sviten, lint, typer och commit**

Run: `uv run pytest && uv run ruff check src tests && uv run mypy`
Expected: alla gröna

```bash
git add tools/content-pipeline/src/birdy_fetcher/web/run.py tools/content-pipeline/src/birdy_fetcher/cli.py tools/content-pipeline/tests/test_web_run.py
git commit -m "feat(pipeline): kommandot birdy-fetcher web"
```

---

### Task 13: Provkörning på talgoxen

Första riktiga körningen. Den kostar några kronor och visar om prompten ger rätt ton.

- [ ] **Step 1: Torrkörning för alla 180 (inget modellanrop)**

Run: `uv run birdy-fetcher web --dry-run`
Expected: 180 rader `dry-run` med artikelstorlek per språk. Notera arter med "ingen artikel" (de kommer att faila i skarp körning). Grupptabellens `common` och `photo` godkänns (annars stannar körningen med ett tydligt fel).

- [ ] **Step 2: Skarp körning för talgoxen**

Run: `uv run --env-file .env birdy-fetcher web --species Q25485 --max-cost 2`
(Utan `.env`, efter `ant auth login`: `uv run birdy-fetcher web --species Q25485 --max-cost 2`.)
Expected: `Klart: {'ok': 1}` och filerna `website/src/data/species/Q25485.json`, `website/src/assets/species/Q25485/hero.webp` och `extra.webp`.

- [ ] **Step 3: Läs resultatet**

Öppna `website/src/data/species/Q25485.json` och läs de svenska och engelska texterna. Kontrollera:
- Att kännetecknen nämner gul buk och svart band (det Wikipedia säger).
- Att tonen är vardaglig svenska utan AI-fraser.
- Att `facts.size` och `facts.swedenStatus` har citat som finns i artikeln.
- Att `meta_description` börjar med artnamnet.

Om tonen är fel: ändra `prompts/web-v1.md` och kör om med `--regenerate`. Promptens hash ingår i cachenyckeln, så ändringar ger ett nytt anrop även utan flaggan.

- [ ] **Step 4: Kör tre till som stickprov**

Run: `uv run --env-file .env birdy-fetcher web --species Q25383 --species Q25386 --species Q10546857 --max-cost 5`
Det är Bofink (tom beskrivning i appen), Råka (engelsk förgreningssida med gamla metoden) och Koboltmes (finns inte i Sverige). Kontrollera att Råka får rätt engelsk artikel ("Rook (bird)") och att Koboltmes status blir `absent` och att texten säger att den inte finns i Sverige.

- [ ] **Step 5: Commit**

```bash
git add website/src/data/species website/src/assets/species tools/content-pipeline/reports tools/content-pipeline/prompts/web-v1.md
git commit -m "feat(website): provkörning av artsidornas texter (talgoxe, bofink, råka, koboltmes)"
```

---

### Task 14: Körning för alla 180 arter

- [ ] **Step 1: Albin väljer modell**

Fråga Albin: Opus 5 (standard, cirka 30 USD) eller Sonnet 5 (`--model sonnet`, cirka 13 USD). Uppskattningen bygger på Task 13:s faktiska kostnad gånger 180. Räkna om den med rapportens siffra innan frågan ställs.

- [ ] **Step 2: Kör**

Run: `uv run --env-file .env birdy-fetcher web --max-cost 60`
Expected: cirka 180 `ok`. Arter som failar listas i rapporten `tools/content-pipeline/reports/web-<datum>.md`.

- [ ] **Step 3: Följ upp de som failade**

För varje art under "Arter som inte fick någon sida" i rapporten:
- Saknas artikel: kontrollera Wikidata-sitelinken. Ingen åtgärd om arten saknar artikel på båda språken, då får den ingen sida.
- Regelbrott två gånger: kör om arten med `--species <QID> --regenerate`. Upprepas samma brott för flera arter, skärp prompten och kör om de berörda.

Målet är minst 170 arter med `status: "ok"`. Stannar det under, stoppa och rapportera till Albin med rapporten.

Kontrollera också att de tolv arterna i `common` och de 15 fotoarterna (`photo`) i `website/src/data/species-groups.json` alla fick `ok`. Sajten stoppar bygget om en art i `common` saknar sida (fas 2). Byt en art som failade mot en annan granskad art i samma grupp, kör `uv run pytest tests/test_web_groups.py` och committa.

- [ ] **Step 4: Kontrollera storleken**

Run: `du -sh ../../website/src/assets/species`
Expected: cirka 25 MB (spec avsnitt 8). Är det över 40 MB, sänk `QUALITY` i `web/images.py` till 72 och kör om med `--species` för alla (texten kommer från cache, bara bilderna görs om).

- [ ] **Step 5: Commit**

```bash
git add website/src/data/species website/src/assets/species tools/content-pipeline/reports
git commit -m "feat(website): webbtexter och foton för de 180 granskade arterna"
```

---

### Task 15: Albins granskning (manuell grind)

Den här tasken görs av Albin. Agenten förbereder och tar hand om rättelserna.

- [ ] **Step 1: Förbered granskningslistan**

Skriv `docs/superpowers/research/<datum>-artsidor-granskning.md` med de svenska texterna för de tolv arterna i `common` plus åtta till (en per grupp som inte redan är med, i gruppordning), och de engelska texterna för tio av dem. Samla varje arts ingress, kännetecken, läte, var och när samt fakta i läsbar form. Lägg också in alla 15 gruppingresser från `species-groups.json`.

- [ ] **Step 2: Albin läser och kommenterar**

Albin markerar fel i fakta, ton och ord som låter som AI.

- [ ] **Step 3: Rätta**

- Enstaka fel: rätta direkt i artens JSON och sätt `"review": "approved"`.
- Systematiska fel (samma fras eller samma typ av fel i flera texter): lägg till frasen i `web-banned-phrases.txt` eller skärp prompten, och kör om alla som inte är `approved` med `--regenerate`.
- Kör `uv run pytest` igen om prompten eller listan ändrats.

- [ ] **Step 4: Commit**

```bash
git add website/src/data tools/content-pipeline/prompts docs/superpowers/research
git commit -m "docs: Albins granskning av artsidornas texter och rättelser"
```

---

### Task 16: Synka status

- [ ] **Step 1: Uppdatera CLAUDE.md**

Uppdatera 🔎-posten om artsidorna under Status: fas 1 klar, antal arter med `ok`, kostnad, modell, rapportens sökväg och granskningens utfall. Nästa steg: fas 2 (`docs/superpowers/plans/2026-09-25-artsidor-fas2-sidor.md`).

- [ ] **Step 2: Commit och push**

```bash
git add CLAUDE.md
git commit -m "docs: CLAUDE.md, artsidornas fas 1 klar"
git push
```
