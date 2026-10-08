"""Swedish family names, one per Latin family (re-review 2026-10-07).

The species files' `family_sv` was not one name per family: Paridae was "Mesar" for
Talgoxe and Blåmes but "Mesfåglar" for the other tits, Bombycillidae, Locustellidae,
Panuridae, Regulidae and Recurvirostridae had their Latin name as the Swedish one, and
"Fältsparvar" stood for both Calcariidae and Emberizidae. The pages take `family.sv` from
here instead, never from the app's YAML (which is left as it is).

Source: BirdLife Sverige's official Swedish names of the world's birds, version 2025
(NL20.xlsx, https://birdlife.se/tk/svenska-namn-pa-varldens-faglar/), its "familj" rows,
with the first letter capitalised. Every family among Birdy's 839 species (97) is here, so
a species approved later finds its family too; tests/test_web_families.py fails when one
is missing."""

from __future__ import annotations

FAMILY_SV: dict[str, str] = {
    "Accipitridae": "Hökar",
    "Acrocephalidae": "Rörsångare",
    "Aegithalidae": "Stjärtmesar",
    "Alaudidae": "Lärkor",
    "Alcedinidae": "Kungsfiskare",
    "Alcidae": "Alkor",
    "Anatidae": "Änder",
    "Anhingidae": "Ormhalsfåglar",
    "Apodidae": "Seglare",
    "Ardeidae": "Hägrar",
    "Bombycillidae": "Sidensvansar",
    "Bucerotidae": "Näshornsfåglar",
    "Burhinidae": "Tjockfotar",
    "Calcariidae": "Sporrsparvar",
    "Caprimulgidae": "Nattskärror",
    "Certhiidae": "Trädkrypare",
    "Cettiidae": "Cettior",
    "Charadriidae": "Pipare",
    "Ciconiidae": "Storkar",
    "Cinclidae": "Strömstarar",
    "Cisticolidae": "Cistikolor",
    "Columbidae": "Duvor",
    "Coraciidae": "Blåkråkor",
    "Corvidae": "Kråkfåglar",
    "Cuculidae": "Gökar",
    "Dromadidae": "Hägerpipare",
    "Emberizidae": "Fältsparvar",
    "Estrildidae": "Astrilder",
    "Falconidae": "Falkar",
    "Fregatidae": "Fregattfåglar",
    "Fringillidae": "Finkar",
    "Gaviidae": "Lommar",
    "Glareolidae": "Vadarsvalor",
    "Gruidae": "Tranor",
    "Haematopodidae": "Strandskator",
    "Hirundinidae": "Svalor",
    "Hydrobatidae": "Stormsvalor",
    "Hypocoliidae": "Hypokolier",
    "Jacanidae": "Jassanor",
    "Laniidae": "Törnskator",
    "Laridae": "Måsfåglar",
    "Leiothrichidae": "Fnittertrastar",
    "Locustellidae": "Gräsfåglar",
    "Malaconotidae": "Busktörnskator",
    "Meropidae": "Biätare",
    "Monarchidae": "Monarker",
    "Motacillidae": "Ärlor",
    "Muscicapidae": "Flugsnappare",
    "Nectariniidae": "Solfåglar",
    "Numididae": "Pärlhöns",
    "Oceanitidae": "Havslöpare",
    "Odontophoridae": "Tofsvaktlar",
    "Oriolidae": "Gyllingar",
    "Otididae": "Trappar",
    "Pandionidae": "Fiskgjusar",
    "Panuridae": "Skäggmesar",
    "Paradoxornithidae": "Papegojnäbbar",
    "Paridae": "Mesar",
    "Passeridae": "Sparvfinkar",
    "Pelecanidae": "Pelikaner",
    "Phaethontidae": "Tropikfåglar",
    "Phalacrocoracidae": "Skarvar",
    "Phasianidae": "Fasanfåglar",
    "Phoenicopteridae": "Flamingor",
    "Phylloscopidae": "Lövsångare",
    "Picidae": "Hackspettar",
    "Ploceidae": "Vävare",
    "Podicipedidae": "Doppingar",
    "Procellariidae": "Liror",
    "Prunellidae": "Järnsparvar",
    "Psittacidae": "Västpapegojor",
    "Psittaculidae": "Östpapegojor",
    "Pteroclidae": "Flyghöns",
    "Pycnonotidae": "Bulbyler",
    "Rallidae": "Rallar",
    "Recurvirostridae": "Skärfläckor",
    "Regulidae": "Kungsfåglar",
    "Remizidae": "Pungmesar",
    "Rostratulidae": "Rallbeckasiner",
    "Scolopacidae": "Snäppor",
    "Scopidae": "Skuggstorkar",
    "Sittidae": "Nötväckor",
    "Stercorariidae": "Labbar",
    "Strigidae": "Ugglor",
    "Struthionidae": "Strutsar",
    "Sturnidae": "Starar",
    "Sulidae": "Sulor",
    "Sylviidae": "Sylvior",
    "Threskiornithidae": "Ibisar",
    "Tichodromidae": "Murkrypare",
    "Troglodytidae": "Gärdsmygar",
    "Turdidae": "Trastar",
    "Turnicidae": "Springhöns",
    "Tytonidae": "Tornugglor",
    "Upupidae": "Härfåglar",
    "Viduidae": "Änkor",
    "Zosteropidae": "Glasögonfåglar",
}


class UnknownFamilyError(KeyError):
    pass


def family_sv(latin: str) -> str:
    """The Swedish name of the Latin family; a family not in the table stops the run."""
    try:
        return FAMILY_SV[latin]
    except KeyError:
        raise UnknownFamilyError(
            f"Familjen {latin} saknar svenskt namn i web/families.py "
            "(se BirdLife Sveriges lista NL20, raderna med nivå familj)"
        ) from None
