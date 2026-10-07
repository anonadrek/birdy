#!/usr/bin/env python3
"""Generate the licence list the app shows under Settings > About > Open-source licences.

Release 1.3.0, legal review 7i-fix B (docs/legal/2026-10-1.3.0-genomgang.md, section 6). Run it from
anywhere inside the repo whenever a release dependency, a font, a model or a native library changes:

    python tools/licenses/generate.py

and commit what it writes. `./gradlew :androidApp:verifyLicenseList` (run by CI and before every
release bundle or APK) fails until it has been run after a dependency change.

What it does:

1. Runs `./gradlew :androidApp:writeReleaseDependencies` (or reads --deps FILE) for the release
   build's external dependencies, `group:name:version`, and saves them as
   tools/licenses/release-dependencies.txt, the list verifyLicenseList compares against.
2. Reads each dependency's licence from its POM (Google Maven or Maven Central, parent POMs
   followed) and groups the libraries by Maven group, version and licence.
3. Extracts the notices Google's closed-source libraries carry for the open-source code inside them
   (third_party_licenses.json/.txt in the AARs, read from the Gradle cache). A notice that is just
   the Apache License 2.0 keeps only its copyright lines; the licence text is in apache-2.0.txt.
4. Fetches the licences of the native code inside the TensorFlow libraries (native-components.json
   lists every component with its version and source) and TensorFlow's own LICENSE (Apache 2.0 plus
   the Caffe notice).
5. Reads the copyright lines of every bundled font (composeApp's composeResources/font and the PDF
   fonts) from their name tables and pairs them with the SIL Open Font License 1.1.
6. Adds the models (notices/birdnet-lite.txt, notices/aiy-birds-v1.txt).
7. Writes composeApp/src/commonMain/composeResources/files/licenses/: index.json (what the screen
   lists) and one text file per licence or notice.

Needs Python 3.10+, fontTools (`pip install fonttools`) and network access; downloads are cached in
tools/licenses/.cache/ (git-ignored). Fails loudly on a licence it does not recognise: add it to
LICENSE_RULES below rather than guessing.
"""

from __future__ import annotations

import argparse
import glob
import json
import os
import re
import subprocess
import sys
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
OUT = ROOT / "composeApp/src/commonMain/composeResources/files/licenses"
SNAPSHOT = HERE / "release-dependencies.txt"
CACHE = HERE / ".cache"
GRADLE_DEPS = ROOT / "androidApp/build/licenses/release-dependencies.txt"

REPOSITORIES = (
    "https://dl.google.com/dl/android/maven2",
    "https://repo.maven.apache.org/maven2",
)
POM_NS = {"m": "http://maven.apache.org/POM/4.0.0"}

APACHE_URL = "https://www.apache.org/licenses/LICENSE-2.0.txt"
OFL_URL = "https://openfontlicense.org/documents/OFL.txt"
TF_COMMIT = "a95156b81d3899ed3fc471843aa82f3c33bc8c32"
TF_LICENSE_URL = f"https://raw.githubusercontent.com/tensorflow/tensorflow/{TF_COMMIT}/LICENSE"

FONT_DIRS = (
    ROOT / "composeApp/src/commonMain/composeResources/font",
    ROOT / "shared/pdf/src/androidMain/assets/fonts",
)

# POM licence name or URL -> (licence id, display name, text file). The first matching rule wins.
LICENSE_RULES: tuple[tuple[re.Pattern[str], str, str, str], ...] = (
    (
        re.compile(r"apache.*2\.0|apache-2\.0|apache.org/licenses/license-2\.0", re.I),
        "apache-2.0",
        "Apache License 2.0",
        "apache-2.0.txt",
    ),
    (
        re.compile(r"android software development kit license", re.I),
        "android-sdk",
        "Android Software Development Kit License",
        "google-sdk.txt",
    ),
    (
        re.compile(r"play core software development kit terms", re.I),
        "play-core",
        "Play Core Software Development Kit Terms of Service",
        "google-sdk.txt",
    ),
)

# Google's closed-source libraries: listed with their terms, plus the notices they carry.
GOOGLE_TERMS_IDS = {"android-sdk", "play-core"}

# Dependencies that are TensorFlow: shown in their own section with TensorFlow's LICENSE.
TENSORFLOW_GROUPS = {"com.google.ai.edge.litert", "org.tensorflow"}


@dataclass
class Library:
    group: str
    version: str
    license_id: str
    license_name: str
    file: str
    url: str | None = None
    artifacts: list[str] = field(default_factory=list)


def log(message: str) -> None:
    print(message, file=sys.stderr)


def fetch(url: str) -> bytes:
    """Download [url] once; later runs read the copy in tools/licenses/.cache/."""
    name = re.sub(r"[^A-Za-z0-9._-]+", "_", url.split("://", 1)[-1])
    cached = CACHE / name[-180:]
    if cached.exists():
        return cached.read_bytes()
    log(f"  fetching {url}")
    request = urllib.request.Request(url, headers={"User-Agent": "birdy-licenses/1.0"})
    with urllib.request.urlopen(request, timeout=60) as response:
        data = response.read()
    CACHE.mkdir(parents=True, exist_ok=True)
    cached.write_bytes(data)
    return data


def fetch_text(url: str) -> str:
    return fetch(url).decode("utf-8").replace("\r\n", "\n")


# ---- 1. dependencies -----------------------------------------------------------------------------


def release_dependencies(deps_file: Path | None) -> list[str]:
    if deps_file is None:
        gradlew = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
        log("Running :androidApp:writeReleaseDependencies ...")
        subprocess.run([str(gradlew), ":androidApp:writeReleaseDependencies", "-q"], cwd=ROOT, check=True)
        deps_file = GRADLE_DEPS
    lines = [line.strip() for line in deps_file.read_text(encoding="utf-8").splitlines()]
    return sorted(line for line in lines if line and not line.startswith("#"))


# ---- 2. POM licences -----------------------------------------------------------------------------


def pom(group: str, name: str, version: str) -> ET.Element | None:
    path = f"{group.replace('.', '/')}/{name}/{version}/{name}-{version}.pom"
    for repo in REPOSITORIES:
        if repo.startswith("https://dl.google.com") and not group.startswith(("androidx", "com.android", "com.google")):
            continue
        try:
            root = ET.fromstring(fetch(f"{repo}/{path}"))
        except (OSError, ET.ParseError):  # not in this repository (404) or unreadable: try the next
            continue
        # Old POMs (javax.inject 1) have no namespace; give every element the POM namespace.
        for element in root.iter():
            if not element.tag.startswith("{"):
                element.tag = f"{{{POM_NS['m']}}}{element.tag}"
        return root
    return None


def pom_licenses(group: str, name: str, version: str, depth: int = 0) -> tuple[list[tuple[str, str]], str | None]:
    """The POM's licences (name, url) and project URL, following parent POMs when it has none."""
    root = pom(group, name, version)
    if root is None:
        raise SystemExit(f"No POM found for {group}:{name}:{version}")
    licenses = [
        (
            (lic.findtext("m:name", default="", namespaces=POM_NS) or "").strip(),
            (lic.findtext("m:url", default="", namespaces=POM_NS) or "").strip(),
        )
        for lic in root.findall("m:licenses/m:license", POM_NS)
    ]
    url = (root.findtext("m:url", default="", namespaces=POM_NS) or "").strip() or None
    parent = root.find("m:parent", POM_NS)
    if not licenses and parent is not None and depth < 5:
        pg, pn, pv = (parent.findtext(f"m:{key}", namespaces=POM_NS) for key in ("groupId", "artifactId", "version"))
        parent_licenses, parent_url = pom_licenses(pg, pn, pv, depth + 1)
        return parent_licenses, url or parent_url
    return licenses, url


def classify(coordinate: str, licenses: list[tuple[str, str]]) -> tuple[str, str, str]:
    for lic_name, lic_url in licenses:
        for pattern, license_id, display, file in LICENSE_RULES:
            if pattern.search(lic_name) or pattern.search(lic_url):
                return license_id, display, file
    raise SystemExit(f"Unknown licence for {coordinate}: {licenses}. Add a rule to LICENSE_RULES.")


def libraries(coordinates: list[str]) -> list[Library]:
    grouped: dict[tuple[str, str, str], Library] = {}
    for coordinate in coordinates:
        group, name, version = coordinate.split(":")
        licenses, url = pom_licenses(group, name, version)
        license_id, display, file = classify(coordinate, licenses)
        key = (group, version, license_id)
        library = grouped.setdefault(key, Library(group, version, license_id, display, file, url))
        library.artifacts.append(name)
        library.url = library.url or url
    return sorted(grouped.values(), key=lambda lib: (lib.group, lib.version))


# ---- licence texts -------------------------------------------------------------------------------


def normalized(text: str) -> str:
    """Lower case, single spaces and http for https, so that copies of one licence compare equal."""
    return " ".join(text.split()).lower().replace("https://", "http://")


APACHE_TEXT = ""
APACHE_TERMS = ""


def load_apache() -> None:
    global APACHE_TEXT, APACHE_TERMS
    APACHE_TEXT = fetch_text(APACHE_URL).strip() + "\n"
    start = APACHE_TEXT.index("TERMS AND CONDITIONS FOR USE")
    end = APACHE_TEXT.index("END OF TERMS AND CONDITIONS") + len("END OF TERMS AND CONDITIONS")
    APACHE_TERMS = normalized(APACHE_TEXT[start:end])


APPENDIX_TEMPLATE = normalized(
    """APPENDIX: How to apply the Apache License to your work.

      To apply the Apache License to your work, attach the following
      boilerplate notice, with the fields enclosed by brackets "[]"
      replaced with your own identifying information. (Don't include
      the brackets!)  The text should be enclosed in the appropriate
      comment syntax for the file format. We also recommend that a
      file or class name and description of purpose be included on the
      same "printed page" as the copyright notice for easier
      identification within third-party archives.

   Copyright [yyyy] [name of copyright owner]

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License."""
)


# The appendix as it is, or in its older form with {} for [], with any copyright filled in.
APPENDIX_RE = (
    re.escape(APPENDIX_TEMPLATE)
    .replace(re.escape("[yyyy] [name of copyright owner]"), r".{0,200}?")
    .replace(re.escape('"[]"'), r'"(\[\]|\{\})"')
)
HEADER_RE = r"apache license,? version 2\.0,? january 2004 http://www\.apache\.org/licenses/?"
SHORT_NOTICE_RE = (
    r'licensed under the apache license, version 2\.0 \(the "license"\); you may not use this file except in '
    r"compliance with the license\.( you may obtain a copy of the license at https?://www\.apache\.org/licenses/"
    r"license-2\.0)? unless required by applicable law or agreed to in writing, software distributed under the "
    r'license is distributed on an "as is" basis, without warranties or conditions of any kind, either express or '
    r"implied\. see the license for the specific language governing permissions and limitations under the license\."
)
# "Copyright (c) 2005 ...", "Copyright 2008 ...", "© 2019 ...", not the licence's own "copyright notice".
COPYRIGHT_LINE = re.compile(r"^\s*(copyright\s*(\(c\)|©|\d{4})|\(c\)\s*\d{4}|©\s*\d{4})", re.I)


def apache_notice(text: str) -> str | None:
    """The copyright lines to keep when [text] is the Apache License 2.0 and nothing else, or None.

    Allowed around the licence: its title, the standard appendix (with or without its copyright
    filled in, a boilerplate the licence asks owners to put in their files), the short per-file
    notice, and copyright lines, which are returned so that the notice keeps them.
    """
    flat = normalized(text)
    if APACHE_TERMS not in flat:
        return None
    rest = flat.replace(APACHE_TERMS, " ")
    rest = re.sub(APPENDIX_RE, " ", rest)
    rest = re.sub(HEADER_RE, " ", rest)
    rest = re.sub(SHORT_NOTICE_RE, " ", rest)
    copyright_lines = [
        line.strip() for line in text.splitlines() if COPYRIGHT_LINE.match(line) and "[yyyy]" not in line
    ]
    for line in copyright_lines:
        rest = rest.replace(normalized(line), " ")
    if len(rest.strip()) > 80:
        return None
    return "\n".join(dict.fromkeys(copyright_lines))


def apache_or_text(text: str) -> str:
    """[text] itself or, when it is just the Apache License 2.0, its copyright lines and a reference."""
    notice = apache_notice(text)
    if notice is None:
        return text.strip()
    return (
        notice + "\n\n" if notice else ""
    ) + "Apache License 2.0 (its text is under Apache License 2.0 in this list)"


def write(name: str, text: str) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / name).write_text(text.rstrip() + "\n", encoding="utf-8", newline="\n")


def section(title: str, body: str) -> str:
    rule = "=" * min(len(title), 72)
    return f"{title}\n{rule}\n\n{body.strip()}\n"


# ---- 3. Google's libraries -----------------------------------------------------------------------


def gradle_cache() -> Path:
    home = os.environ.get("GRADLE_USER_HOME") or os.path.expanduser("~/.gradle")
    return Path(home) / "caches/modules-2/files-2.1"


def google_notices(google_libs: list[Library]) -> str:
    """The third-party notices inside Google's AARs, one section per component, deduplicated."""
    bodies: dict[str, list[str]] = {}
    for lib in google_libs:
        for artifact in lib.artifacts:
            hits = glob.glob(
                str(gradle_cache() / lib.group / artifact / lib.version / "*" / f"{artifact}-{lib.version}.aar")
            )
            if not hits:
                continue
            with zipfile.ZipFile(hits[0]) as aar:
                if "third_party_licenses.json" not in aar.namelist():
                    continue
                index = json.loads(aar.read("third_party_licenses.json"))
                raw = aar.read("third_party_licenses.txt")
                for component, span in index.items():
                    body = raw[span["start"] : span["start"] + span["length"]].decode("utf-8", "replace")
                    body = body.replace("\r\n", "\n").strip()
                    known = bodies.setdefault(component, [])
                    if all(normalized(body) != normalized(other) for other in known):
                        known.append(body)
    parts = [
        "Google's libraries in Birdy (Google Play Billing, Google Play services and Play In-App Review) "
        "are not open source; Google provides them under the terms named in the list. They contain "
        "open-source code, whose notices follow, as Google ships them in each library "
        "(third_party_licenses.txt). For a notice that is the Apache License 2.0, only its copyright "
        "lines are repeated here; the licence text is under Apache License 2.0 in this list.\n"
    ]
    for component in sorted(bodies, key=str.lower):
        variants = bodies[component]
        # One body that contains another (a longer edition of the same notice) replaces it.
        kept = [b for b in variants if not any(b is not o and normalized(b) in normalized(o) for o in variants)]
        parts.append(section(component, "\n\n".join(dict.fromkeys(apache_or_text(body) for body in kept))))
    return "\n\n".join(parts)


# ---- 4. TensorFlow and its native code -----------------------------------------------------------


def native_notices() -> tuple[str, list[dict]]:
    manifest = json.loads((HERE / "native-components.json").read_text(encoding="utf-8"))
    parts = [
        "Native code built into the TensorFlow Lite libraries in Birdy: libtensorflowlite_jni.so "
        "(LiteRT 1.4.1, photo and sound ID) and libtensorflowlite_flex_jni.so (TensorFlow, commit "
        f"{TF_COMMIT[:12]}, the 16 KB build from github.com/arxdeus/tflite_flex_16kb_android; "
        "BirdNET's sound ID needs it). Versions are the ones TensorFlow pinned at that commit.\n"
    ]
    for component in manifest["components"]:
        text = fetch_text(component["source"])
        if "lines" in component:
            first, last = component["lines"]
            text = "\n".join(text.splitlines()[first - 1 : last])
        used_in = " and ".join({"flex": "the flex library", "litert": "LiteRT"}[x] for x in component["in"])
        header = [
            f"Version: {component['version']}",
            f"Licence: {component['license']}",
            f"Used in: {used_in}",
            f"Source of this text: {component['source']}",
        ]
        if "notice" in component:
            header.insert(0, component["notice"])
        body = apache_or_text(text)
        parts.append(section(component["name"], "\n".join(header) + "\n\n" + body))
    return "\n\n".join(parts), manifest["components"]


# ---- 5. fonts ------------------------------------------------------------------------------------


def fonts() -> list[dict]:
    from fontTools.ttLib import TTFont  # imported here so --help works without fontTools

    families: dict[str, dict] = {}
    for directory in FONT_DIRS:
        for path in sorted(directory.glob("*.ttf")) + sorted(directory.glob("*.otf")):
            names = TTFont(path)["name"]
            family = names.getDebugName(1) or path.stem
            license_text = names.getDebugName(13) or ""
            if "Open Font License" not in license_text:
                raise SystemExit(f"{path} is not under the SIL Open Font License: {license_text!r}")
            entry = families.setdefault(family, {"copyright": [], "files": [], "version": names.getDebugName(5) or ""})
            copyright_line = (names.getDebugName(0) or "").strip()
            if copyright_line and copyright_line not in entry["copyright"]:
                entry["copyright"].append(copyright_line)
            entry["files"].append(path.name)
    return [{"family": family, **data} for family, data in sorted(families.items())]


def ofl_text() -> str:
    text = fetch_text(OFL_URL)
    start = text.index("-----------------------------------------------------------\nSIL OPEN FONT LICENSE")
    return text[start:].strip() + "\n"


# ---- index ---------------------------------------------------------------------------------------


def entry(entry_id: str, name: str, version: str, license_name: str, file: str, **extra: object) -> dict:
    data: dict[str, object] = {"id": entry_id, "name": name, "version": version, "license": license_name, "file": file}
    data.update({k: v for k, v in extra.items() if v})
    return data


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--deps", type=Path, help="read the dependencies from this file instead of running Gradle")
    args = parser.parse_args()

    coordinates = release_dependencies(args.deps)
    log(f"{len(coordinates)} release dependencies")
    load_apache()

    libs = libraries(coordinates)
    google_libs = [lib for lib in libs if lib.license_id in GOOGLE_TERMS_IDS]
    tensorflow_libs = [lib for lib in libs if lib.group in TENSORFLOW_GROUPS]
    other_libs = [lib for lib in libs if lib not in google_libs and lib not in tensorflow_libs]

    # Licence and notice texts.
    write("apache-2.0.txt", APACHE_TEXT)
    write("ofl-1.1.txt", ofl_text())
    tf_license = fetch_text(TF_LICENSE_URL)
    if "derived from Caffe" not in tf_license:
        raise SystemExit("TensorFlow's LICENSE no longer carries the Caffe notice; check it by hand.")
    write("tensorflow.txt", tf_license)
    native_text, native_components = native_notices()
    write("tensorflow-third-party.txt", native_text)
    google_text = google_notices(google_libs)
    write("google-third-party.txt", google_text)
    write(
        "google-sdk.txt",
        section(
            "Google's terms",
            "Android Software Development Kit License: https://developer.android.com/studio/terms.html\n"
            "Play Core Software Development Kit Terms of Service: https://developer.android.com/guide/playcore/license\n\n"
            "These libraries are not open source. The notices for the open-source code inside them are under "
            '"Open-source code in Google\'s libraries" in this list.',
        ),
    )
    for notice in ("birdnet-lite.txt", "aiy-birds-v1.txt"):
        write(notice, (HERE / "notices" / notice).read_text(encoding="utf-8"))

    sections = [
        {
            "id": "models",
            "entries": [
                entry(
                    "birdnet-lite",
                    "BirdNET-Lite",
                    "6K global model (v2)",
                    "CC BY-NC-SA 4.0",
                    "birdnet-lite.txt",
                    url="https://github.com/birdnet-team/BirdNET-Lite",
                ),
                entry(
                    "aiy-birds-v1",
                    "AIY Birds V1",
                    "birds_V1/3",
                    "Apache License 2.0",
                    "apache-2.0.txt",
                    notice="aiy-birds-v1.txt",
                    url="https://tfhub.dev/google/lite-model/aiy/vision/classifier/birds_V1/3",
                ),
            ],
        },
        {
            "id": "fonts",
            "entries": [
                entry(
                    "font:" + re.sub(r"\W+", "-", font["family"].lower()),
                    font["family"],
                    font["version"].split(";")[0].replace("Version ", ""),
                    "SIL Open Font License 1.1",
                    "ofl-1.1.txt",
                    copyright=font["copyright"],
                    artifacts=font["files"],
                )
                for font in fonts()
            ],
        },
        {
            "id": "tensorflow",
            "entries": [
                *(
                    entry(
                        f"lib:{lib.group}:{lib.version}",
                        lib.group,
                        lib.version,
                        "Apache License 2.0",
                        "tensorflow.txt",
                        artifacts=lib.artifacts,
                        url=lib.url,
                    )
                    for lib in tensorflow_libs
                ),
                entry(
                    # The app shows its own localized name and no version for the two notice entries.
                    "tensorflow-native",
                    "Native code in TensorFlow Lite",
                    "",
                    "Apache 2.0 · BSD · MIT · MPL 2.0 · zlib · IJG · libpng",
                    "tensorflow-third-party.txt",
                    artifacts=[c["name"] for c in native_components],
                ),
            ],
        },
        {
            "id": "google",
            "entries": [
                *(
                    entry(
                        f"lib:{lib.group}:{lib.version}",
                        lib.group,
                        lib.version,
                        lib.license_name,
                        "google-sdk.txt",
                        artifacts=lib.artifacts,
                        url=lib.url,
                    )
                    for lib in google_libs
                ),
                entry(
                    "google-third-party",
                    "Open-source code in Google's libraries",
                    "",
                    "Apache 2.0 · BSD · MIT · ICU · PCRE",
                    "google-third-party.txt",
                ),
            ],
        },
        {
            "id": "libraries",
            "entries": [
                entry(
                    f"lib:{lib.group}:{lib.version}",
                    lib.group,
                    lib.version,
                    lib.license_name,
                    lib.file,
                    artifacts=lib.artifacts,
                    url=lib.url,
                )
                for lib in other_libs
            ],
        },
    ]
    index = {
        "about": "Generated by tools/licenses/generate.py from the release build's dependencies "
        "(tools/licenses/release-dependencies.txt). Do not edit by hand.",
        "sections": sections,
    }
    write("index.json", json.dumps(index, ensure_ascii=False, indent=1))
    SNAPSHOT.write_text(
        "# The release build's external dependencies when the app's licence list was generated\n"
        "# (tools/licenses/generate.py). :androidApp:verifyLicenseList compares the build against this list.\n"
        + "\n".join(coordinates)
        + "\n",
        encoding="utf-8",
        newline="\n",
    )
    sizes = sorted(((p.stat().st_size, p.name) for p in OUT.iterdir()), reverse=True)
    log("Wrote " + ", ".join(f"{name} ({size // 1024} KB)" for size, name in sizes))


if __name__ == "__main__":
    main()
