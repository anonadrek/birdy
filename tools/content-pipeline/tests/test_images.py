"""Tests for images.py — selection algorithm + resize/EXIF strip."""

from __future__ import annotations

import io
from pathlib import Path

import pytest
from PIL import Image

from birdy_fetcher.cache import Cache
from birdy_fetcher.images import (
    ImageCandidate,
    ImageProcessor,
    ImageSelector,
    mentions_species,
    parse_imageinfo_response,
    rank_candidates,
)


def test_parse_imageinfo_extracts_candidates(fixtures_dir: Path) -> None:
    raw = (fixtures_dir / "commons_imageinfo_q25372.json").read_text()
    candidates = parse_imageinfo_response(raw)
    titles = [c.commons_filename for c in candidates]
    assert "Parus major - Mindelheim - 2012.jpg" in titles


def test_rank_rejects_illustrations() -> None:
    illust = ImageCandidate(
        commons_filename="Parus major illustration.jpg",
        url="x",
        width=4000,
        height=3000,
        license="Public domain",
        author="Anna Andersson",
        categories=["Bird illustrations"],
    )
    photo = ImageCandidate(
        commons_filename="Parus major - photo.jpg",
        url="y",
        width=4000,
        height=3000,
        license="CC BY-SA 4.0",
        author="Bo Berg",
        categories=["Photographs of Aves", "Birds in nature"],
    )
    ranked = rank_candidates([illust, photo])
    assert ranked[0].commons_filename == "Parus major - photo.jpg"
    assert all("illustration" not in c.commons_filename.lower() for c in ranked)


def test_rank_rejects_specimens() -> None:
    specimen = ImageCandidate(
        commons_filename="Parus major specimen.jpg",
        url="x",
        width=4000,
        height=3000,
        license="CC0",
        author="Anna Andersson",
        categories=["Bird specimens"],
    )
    photo = ImageCandidate(
        commons_filename="Parus major - garden.jpg",
        url="y",
        width=4000,
        height=3000,
        license="CC BY-SA 4.0",
        author="Bo Berg",
        categories=["Photographs of Aves"],
    )
    ranked = rank_candidates([specimen, photo])
    assert all("specimen" not in c.commons_filename.lower() for c in ranked)


def test_rank_rejects_plural_categories() -> None:
    illust_plural = ImageCandidate(
        commons_filename="Parus major - field.jpg",
        url="x",
        width=4000,
        height=3000,
        license="Public domain",
        author="Anna Andersson",
        categories=["Parus major (illustrations)"],
    )
    specimen_plural = ImageCandidate(
        commons_filename="Parus major - museum.jpg",
        url="y",
        width=4000,
        height=3000,
        license="CC0",
        author="Anna Andersson",
        categories=["Parus major (museum specimens)", "Taxidermied birds"],
    )
    photo = ImageCandidate(
        commons_filename="Parus major - garden.jpg",
        url="z",
        width=4000,
        height=3000,
        license="CC BY-SA 4.0",
        author="Bo Berg",
        categories=["Photographs of Aves"],
    )
    ranked = rank_candidates([illust_plural, specimen_plural, photo])
    assert len(ranked) == 1
    assert ranked[0].commons_filename == "Parus major - garden.jpg"


def test_rank_rejects_historical_print_filenames() -> None:
    print_file = ImageCandidate(
        commons_filename="Pernis apivorus - 1700-1880 - Print - Iconographia Zoologica.jpg",
        url="x",
        width=4000,
        height=3000,
        license="Public domain",
        author="Anna Andersson",
        categories=[],
    )
    chromolitho = ImageCandidate(
        # Wellcome upload truncated as "Chromolithograp" — regex must still match.
        commons_filename="Aquila adalberti Chromolithograp Wellcome V0022220.jpg",
        url="y",
        width=4000,
        height=3000,
        license="Public domain",
        author="Anna Andersson",
        categories=[],
    )
    hardwicke = ImageCandidate(
        commons_filename="Butastur teesa Hardwicke.jpg",
        url="z",
        width=4000,
        height=3000,
        license="Public domain",
        author="Anna Andersson",
        categories=[],
    )
    photo = ImageCandidate(
        commons_filename="Pernis apivorus - flying.jpg",
        url="w",
        width=4000,
        height=3000,
        license="CC BY-SA 4.0",
        author="Bo Berg",
        categories=["Photographs of Aves"],
    )
    ranked = rank_candidates([print_file, chromolitho, hardwicke, photo])
    assert len(ranked) == 1
    assert ranked[0].commons_filename == "Pernis apivorus - flying.jpg"


def test_rank_rejects_rijksmuseum_manuscripts() -> None:
    """Rijksmuseum 1600s watercolours surface for many `allmän` SE species
    (e.g. Sneeuwgors / Plectrophenax nivalis from "Historia Naturalis van
    Rudolf II"). The filename uses inventory codes (RP-T-BR-...) without
    the words "drawing" / "illustration", so we reject via category text
    ("Historia Naturalis") and via the author field ("Rijksmuseum")."""
    rudolf_ii_watercolour = ImageCandidate(
        commons_filename="Sneeuwgors (Plectrophenax nivalis), RP-T-BR-2017-1-4-18.jpg",
        url="x",
        width=4264,
        height=6410,
        license="CC0",
        author="Rijksmuseum",
        categories=["CC-Zero", "Historia Naturalis van Rudolf II", "T IV"],
    )
    photo = ImageCandidate(
        commons_filename="Plectrophenax nivalis Oulu 20140406 03.JPG",
        url="y",
        width=4000,
        height=3000,
        license="CC BY-SA 4.0",
        author="Estormiz",
        categories=["Photographs of Aves", "Birds in nature"],
    )
    ranked = rank_candidates([rudolf_ii_watercolour, photo])
    assert len(ranked) == 1
    assert ranked[0].commons_filename == "Plectrophenax nivalis Oulu 20140406 03.JPG"


def test_rank_rejects_non_image_extensions() -> None:
    video = ImageCandidate(
        commons_filename="Galerida cristata, South Hebron.webm",
        url="x",
        width=2276,
        height=1280,
        license="CC0",
        author="Anna Andersson",
        categories=[],
    )
    svg = ImageCandidate(
        commons_filename="Alauda arvensis distribution.svg",
        url="y",
        width=4000,
        height=3000,
        license="CC0",
        author="Anna Andersson",
        categories=[],
    )
    photo = ImageCandidate(
        commons_filename="Alauda arvensis Ehedydd.jpg",
        url="z",
        width=2339,
        height=2665,
        license="CC0",
        author="Anna Andersson",
        categories=["Photographs of Aves"],
    )
    ranked = rank_candidates([video, svg, photo])
    assert ranked == [photo]


def test_rank_rejects_below_min_resolution() -> None:
    too_small = ImageCandidate(
        commons_filename="Parus major small.jpg",
        url="x",
        width=1024,
        height=768,
        license="CC0",
        author="Anna Andersson",
        categories=[],
    )
    big = ImageCandidate(
        commons_filename="Parus major big.jpg",
        url="y",
        width=4000,
        height=3000,
        license="CC0",
        author="Anna Andersson",
        categories=["Photographs of Aves"],
    )
    ranked = rank_candidates([too_small, big])
    assert too_small not in ranked


def test_rank_prefers_pd_over_cc_by_sa() -> None:
    pd = ImageCandidate(
        commons_filename="Parus major - photo1.jpg",
        url="x",
        width=4000,
        height=3000,
        license="Public domain",
        author="Anna Andersson",
        categories=["Photographs of Aves"],
    )
    sa = ImageCandidate(
        commons_filename="Parus major - photo2.jpg",
        url="y",
        width=4000,
        height=3000,
        license="CC BY-SA 4.0",
        author="Bo Berg",
        categories=["Photographs of Aves"],
    )
    ranked = rank_candidates([sa, pd])
    assert ranked[0].license == "Public domain"


def test_processor_resizes_to_hero_dimensions(fixtures_dir: Path, tmp_path: Path) -> None:
    cache = Cache(tmp_path)
    cache.put_bytes(
        "Q25485",
        "images/raw-hero.jpg",
        (fixtures_dir / "sample_image.jpg").read_bytes(),
    )

    processor = ImageProcessor()
    out_path = tmp_path / "hero.jpg"
    metadata = processor.process(
        cache.get_bytes("Q25485", "images/raw-hero.jpg") or b"",
        out_path=out_path,
        role="hero",
    )
    assert out_path.exists()
    img = Image.open(out_path)
    assert max(img.size) <= 2400
    assert metadata.width == img.size[0]
    assert metadata.height == img.size[1]


@pytest.mark.asyncio
async def test_selector_url_quotes_scientific_name_and_caches(
    fixtures_dir: Path, tmp_path: Path
) -> None:
    """ImageSelector quotes the scientific_name into the search URL and
    caches the raw response on first call (no second HTTP call)."""
    captured_urls: list[str] = []
    fixture = (fixtures_dir / "commons_imageinfo_q25372.json").read_text()

    async def fake_get(url: str) -> str:
        captured_urls.append(url)
        return fixture

    cache = Cache(tmp_path)
    selector = ImageSelector(cache=cache, http_get=fake_get)

    first = await selector.fetch_candidates("Q25485", "Parus major minor")
    assert len(captured_urls) == 1
    assert "intitle:%22Parus+major+minor%22" in captured_urls[0]
    assert any(c.commons_filename.startswith("Parus major") for c in first)

    # Second call hits cache, no new HTTP call.
    second = await selector.fetch_candidates("Q25485", "Parus major minor")
    assert len(captured_urls) == 1
    assert [c.commons_filename for c in second] == [c.commons_filename for c in first]

    # force=True bypasses cache.
    await selector.fetch_candidates("Q25485", "Parus major minor", force=True)
    assert len(captured_urls) == 2


def _cand(
    name: str,
    *,
    license_name: str = "CC BY-SA 4.0",
    author: str = "Bo Berg",
    categories: list[str] | None = None,
    size: tuple[int, int] = (4000, 3000),
) -> ImageCandidate:
    return ImageCandidate(
        commons_filename=name,
        url=f"https://upload.example/{name}",
        width=size[0],
        height=size[1],
        license=license_name,
        author=author,
        categories=categories or ["Photographs of Aves"],
    )


@pytest.mark.parametrize(
    "license_name",
    [
        "CC BY-NC 2.0",
        "CC BY-NC-SA 4.0",
        "CC BY-ND 4.0",
        "GFDL",
        "CC BY-SA 2.5",
        "CC BY-SA 3.0 de",
        "",
    ],
)
def test_rank_rejects_licences_outside_the_allow_list(license_name: str) -> None:
    assert rank_candidates([_cand("Parus major 1.jpg", license_name=license_name)]) == []


def test_rank_requires_a_usable_author_for_attribution_licences() -> None:
    nsid = _cand(
        "Parus major nsid.jpg",
        license_name="CC BY-SA 2.0",
        author='<a href="https://www.flickr.com/photos/28092414@N03/">'
        "https://www.flickr.com/photos/28092414@N03/</a>",
    )
    no_author = _cand("Parus major blank.jpg", license_name="CC BY 4.0", author="")
    junk = _cand(
        "Parus major junk.jpg",
        license_name="CC BY 4.0",
        author="No machine-readable author provided.",
    )
    named = _cand("Parus major named.jpg", license_name="CC BY 4.0", author="Charles J. Sharp")
    assert rank_candidates([nsid, no_author, junk, named]) == [named]


def test_rank_keeps_public_domain_without_a_named_author() -> None:
    pd = _cand("Parus major pd.jpg", license_name="Public domain", author="unknown")
    cc0 = _cand("Parus major cc0.jpg", license_name="CC0", author="no rights reserved")
    assert rank_candidates([pd, cc0]) == [pd, cc0]


@pytest.mark.parametrize(
    ("name", "categories"),
    [
        ("Vanellus gregarius range map.png", None),
        ("Emberiza rustica european distribution 2010-2011.png", None),
        ("Map illustrating the irruption of Syrrhaptes paradoxus in 1863.jpg", None),
        ("Cygnus olor MHNT.ZOO.2010.11.11.2.jpg", None),
        ("Feather Buteo rufinus.jpg", None),
        ("Surnia ulula - Finnish Museum of Natural History - DSC04619.JPG", None),
        ("Nest Remiz pendulinus.JPG", None),
        ("Falco peregrinus nest USFWS.jpg", None),
        ("Ural owl (Strix uralensis) ringing.jpg", None),
        ("Starr 061017-1146 Ardenna pacifica (banding).jpg", None),
        ("Dendrocopos major with dead pig.jpg", None),
        ("Emberiza pusilla (10.3897-BDJ.12.e133721) Figure 5.jpg", None),
        ("Parus major 7.jpg", ["Eggs of Parus major"]),
        ("Parus major 8.jpg", ["Distribution maps of birds"]),
    ],
)
def test_rank_rejects_what_the_release_audit_found(name: str, categories: list[str] | None) -> None:
    assert rank_candidates([_cand(name, categories=categories)]) == []


def test_rank_keeps_place_names_that_look_like_reject_words() -> None:
    keep = [
        _cand("Namaqua dove, Oena capensis, at Mapungubwe National Park.jpg"),
        _cand("Tristram's starling, Dead Sea, Israel.jpg"),
        _cand("Milvus milvus, Deadmans Hill, Herts 1.jpg"),
        _cand("Common ringed plover (Charadrius hiaticula), Iceland.jpg"),
    ]
    assert len(rank_candidates(keep)) == len(keep)


@pytest.mark.parametrize(
    ("title", "name", "expected"),
    [
        ("An Alle, Alle! Heft 1, 1919.jpg", "Alle alle", False),
        ("Syntypistis perdix perdix (32413832364).jpg", "Perdix perdix", False),
        ("Little Auk (Alle alle) at Qagssissalik, Greenland.jpg", "Alle alle", True),
        ("Griffon vulture (gyps fulvus) in flight.jpg", "Gyps fulvus", True),
        ("Parus_major_-_Mindelheim.jpg", "Parus major", True),
        ("Parus majorana.jpg", "Parus major", False),
        ("Grey partridge (Perdix perdix) 2022.jpg", "Perdix perdix", True),
    ],
)
def test_mentions_species(title: str, name: str, expected: bool) -> None:
    assert mentions_species(title, name) is expected


def test_rank_with_scientific_name_drops_look_alike_titles() -> None:
    pamphlet = _cand("An Alle, Alle! Heft 1, 1919.jpg", license_name="Public domain")
    photo = _cand("Little Auk (Alle alle) on a rock.jpg")
    assert rank_candidates([pamphlet, photo], scientific_name="Alle alle") == [photo]


def test_rank_puts_captive_and_young_birds_last() -> None:
    zoo = _cand("Parus major in Tierpark Berlin.jpg", license_name="CC0")
    chick = _cand("Parus major chick.jpg", license_name="CC0")
    wild = _cand("Parus major in a forest.jpg", license_name="CC BY-SA 4.0")
    assert rank_candidates([zoo, chick, wild])[0] == wild


def test_rank_prefers_commons_reviewed_pictures() -> None:
    plain = _cand("Parus major plain.jpg", license_name="CC0")
    featured = _cand(
        "Parus major featured.jpg",
        license_name="CC BY-SA 4.0",
        categories=["Featured pictures of Parus major", "Photographs of Aves"],
    )
    assert rank_candidates([plain, featured])[0] == featured


def test_processor_writes_webp_and_applies_exif_orientation(tmp_path: Path) -> None:
    # 300x100 landscape pixels with EXIF orientation 6 (rotate 90° CW to view).
    src = Image.new("RGB", (300, 100), "red")
    exif = Image.Exif()
    exif[0x0112] = 6
    buf = io.BytesIO()
    src.save(buf, format="JPEG", exif=exif.tobytes())

    out = tmp_path / "secondary-1.webp"
    meta = ImageProcessor().process(buf.getvalue(), out_path=out, role="secondary")

    with Image.open(out) as written:
        assert written.format == "WEBP"
        assert written.size == (100, 300)
        assert not written.getexif()
    assert (meta.width, meta.height) == (100, 300)


@pytest.mark.asyncio
async def test_selector_fetches_category_members(fixtures_dir: Path, tmp_path: Path) -> None:
    captured: list[str] = []
    fixture = (fixtures_dir / "commons_imageinfo_q25372.json").read_text()

    async def fake_get(url: str) -> str:
        captured.append(url)
        return fixture

    selector = ImageSelector(cache=Cache(tmp_path), http_get=fake_get)
    found = await selector.fetch_category_candidates("Q25485", "Parus major")
    assert "generator=categorymembers" in captured[0]
    assert "gcmtitle=Category:Parus+major" in captured[0]
    assert found
    await selector.fetch_category_candidates("Q25485", "Parus major")
    assert len(captured) == 1
