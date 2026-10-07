#!/usr/bin/env python3
"""Generate the licence list the app shows under Settings > About > Open-source licences.

Release 1.3.0, legal review 7i-fix B (docs/legal/2026-10-1.3.0-genomgang.md, section 6). Run it from
anywhere inside the repo whenever a release dependency, a font, a model or a native library changes:

    uv run --with fonttools python tools/licenses/generate.py

and commit what it writes. `./gradlew :androidApp:verifyLicenseList` (run by CI and before every
release bundle or APK) fails until it has been run after a dependency change.

What it does:

1. Runs `./gradlew :androidApp:writeReleaseDependencies :androidApp:writeReleaseArtifacts` (or
   reads --deps and --artifacts FILE) for the release build's external dependencies,
   `group:name:version`, from the runtime classpath and the core library desugaring library, and
   the files Gradle resolved for them. Saves the dependencies as
   tools/licenses/release-dependencies.txt, the list verifyLicenseList compares against.
2. Reads each dependency's licence from its POM (Google Maven or Maven Central, parent POMs
   followed) and groups the libraries by Maven group, version and licence.
3. Extracts the third-party notices every dependency's file carries (third_party_licenses.json/.txt,
   in Google's AARs among others). A notice that is just the Apache License 2.0 keeps only its
   copyright lines; the licence text is in apache-2.0.txt. Fails when one of Google's closed-source
   libraries has no file.
4. Fetches the licences of the native code inside the TensorFlow libraries (native-components.json
   lists every component with its version and source) and TensorFlow's own LICENSE (Apache 2.0 plus
   the Caffe notice). Stops first when the release build's LiteRT version or androidApp's flex
   library tag is not the one native-components.json was researched for: a new library can contain
   other components, so look at it (strings in the .so, TensorFlow's workspace files) and update
   the JSON before running again. LicenseListTest checks the same in CI.
5. Reads the copyright lines of every bundled font (composeApp's composeResources/font and the PDF
   fonts) from their name tables and pairs them with the SIL Open Font License 1.1.
6. Adds the models (notices/birdnet-lite.txt, notices/aiy-birds-v1.txt).
7. Writes composeApp/src/commonMain/composeResources/files/licenses/: index.json (what the screen
   lists) and one text file per licence or notice.

Needs Python 3.10+, fontTools and network access; downloads are cached in tools/licenses/.cache/
(git-ignored, a 404 is cached too). Fails loudly on a licence it does not recognise, on a library
whose licence needs its own text (LIBRARY_TEXTS) and on any network error other than a 404.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import urllib.error
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
GRADLE_ARTIFACTS = ROOT / "androidApp/build/licenses/release-artifacts.txt"

REPOSITORIES = (
    "https://dl.google.com/dl/android/maven2",
    "https://repo.maven.apache.org/maven2",
)
POM_NS = {"m": "http://maven.apache.org/POM/4.0.0"}

APACHE_URL = "https://www.apache.org/licenses/LICENSE-2.0.txt"
OFL_URL = "https://openfontlicense.org/documents/OFL.txt"
# What the native notices were researched for: the TensorFlow commit the flex library is built from,
# its release tag, LiteRT's version and the dates of both. A bump of either library must be looked at
# by hand (new components, new pins), so check_native_pins() stops the generator until it is.
NATIVE = json.loads((HERE / "native-components.json").read_text(encoding="utf-8"))
TF_COMMIT: str = NATIVE["tensorflowCommit"]
TF_LICENSE_URL = f"https://raw.githubusercontent.com/tensorflow/tensorflow/{TF_COMMIT}/LICENSE"
FLEX_TAG_IN_BUILD = re.compile(r'val flexReleaseTag = "([^"]+)"')

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
    # These need the library's own text (LIBRARY_TEXTS): the copyright lines, or a source offer.
    (
        re.compile(r"general public license.*version 2.*classpath exception", re.I),
        "gpl-2.0-classpath",
        "GPL 2.0 with the Classpath Exception",
        "",
    ),
    (re.compile(r"bsd-3-clause|bsd 3-clause", re.I), "bsd-3-clause", "BSD 3-Clause License", ""),
)

# Licence ids whose text is shared by every library under them.
SHARED_TEXT_IDS = {"apache-2.0", "android-sdk", "play-core"}

DESUGAR_COMMIT = "73170c345e6a762fc6a1f0301bb15218850023ef"

# The texts for libraries under a licence that is not one shared text, by `group:name`. "version"
# pins what the text was checked for: a new version stops the generator until it is looked at.
LIBRARY_TEXTS: dict[str, dict[str, str]] = {
    "com.android.tools:desugar_jdk_libs": {
        "version": "2.1.5",
        "file": "desugar-jdk-libs.txt",
        "url": f"https://raw.githubusercontent.com/google/desugar_jdk_libs/{DESUGAR_COMMIT}/LICENSE",
        "header": (
            "desugar_jdk_libs 2.1.5: Java library code from OpenJDK that the Android build compiles into the "
            "app, so that java.time and other Java APIs work on older Android versions. It is licensed under the "
            "GNU General Public License, version 2, with the Classpath Exception.\n"
            f"Source code: https://github.com/google/desugar_jdk_libs/tree/{DESUGAR_COMMIT} (the commit that "
            "prepared version 2.1.5, 2025-02-14)"
        ),
    },
    "com.android.tools:desugar_jdk_libs_configuration": {
        "version": "2.1.5",
        "file": "desugar-jdk-libs-configuration.txt",
        "jarEntry": "LICENSE",
        "header": "desugar_jdk_libs_configuration 2.1.5 (the R8 project), the LICENSE in its JAR:",
    },
}

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

    @property
    def coordinates(self) -> list[str]:
        return [f"{self.group}:{name}:{self.version}" for name in self.artifacts]


class NotFoundError(Exception):
    """The server answered 404 (cached, so the next run does not ask again)."""


def log(message: str) -> None:
    print(message, file=sys.stderr)


def fetch(url: str) -> bytes:
    """Download [url] once; later runs read the copy in tools/licenses/.cache/.

    Raises [NotFoundError] on a 404 (remembered in the cache too). Any other failure (no network, a
    timeout, a server error) is raised as it is: the generator must not quietly leave a text out.
    """
    name = re.sub(r"[^A-Za-z0-9._-]+", "_", url.split("://", 1)[-1])[-180:]
    cached = CACHE / name
    missing = CACHE / (name + ".404")
    if cached.exists():
        return cached.read_bytes()
    if missing.exists():
        raise NotFoundError(url)
    log(f"  fetching {url}")
    CACHE.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(url, headers={"User-Agent": "birdy-licenses/1.0"})
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            data = response.read()
    except urllib.error.HTTPError as error:
        if error.code == 404:
            missing.touch()
            raise NotFoundError(url) from error
        raise
    # Written next to its place and moved there, so an interrupted run never leaves half a file.
    partial = CACHE / (name + ".partial")
    partial.write_bytes(data)
    os.replace(partial, cached)
    return data


def fetch_text(url: str) -> str:
    return fetch(url).decode("utf-8").replace("\r\n", "\n")


# ---- 1. dependencies -----------------------------------------------------------------------------


def run_gradle() -> None:
    gradlew = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    tasks = [":androidApp:writeReleaseDependencies", ":androidApp:writeReleaseArtifacts"]
    log("Running " + " ".join(tasks) + " ...")
    subprocess.run([str(gradlew), *tasks, "-q"], cwd=ROOT, check=True)


def release_dependencies(deps_file: Path) -> list[str]:
    lines = [line.strip() for line in deps_file.read_text(encoding="utf-8").splitlines()]
    return sorted(line for line in lines if line and not line.startswith("#"))


def release_artifacts(artifacts_file: Path, coordinates: list[str]) -> dict[str, list[Path]]:
    """The files Gradle resolved for each dependency (a module with only metadata has none)."""
    files: dict[str, list[Path]] = {}
    for line in artifacts_file.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        coordinate, path = line.split("\t", 1)
        if coordinate not in coordinates:
            raise SystemExit(f"{artifacts_file} lists {coordinate}, not a release dependency; run Gradle again")
        if not Path(path).is_file():
            raise SystemExit(f"The file of {coordinate} is gone: {path}; run Gradle again")
        files.setdefault(coordinate, []).append(Path(path))
    return files


# ---- 2. POM licences -----------------------------------------------------------------------------


def pom(group: str, name: str, version: str) -> ET.Element | None:
    path = f"{group.replace('.', '/')}/{name}/{version}/{name}-{version}.pom"
    for repo in REPOSITORIES:
        if repo.startswith("https://dl.google.com") and not group.startswith(("androidx", "com.android", "com.google")):
            continue
        url = f"{repo}/{path}"
        try:
            data = fetch(url)
        except NotFoundError:  # not in this repository: try the next
            continue
        try:
            root = ET.fromstring(data)
        except ET.ParseError as error:
            raise SystemExit(f"Unreadable POM {url}: {error}") from error
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


def libraries(coordinates: list[str], artifacts: dict[str, list[Path]]) -> list[Library]:
    grouped: dict[tuple[str, str, str, str], Library] = {}
    for coordinate in coordinates:
        group, name, version = coordinate.split(":")
        licenses, url = pom_licenses(group, name, version)
        license_id, display, file = classify(coordinate, licenses)
        if license_id not in SHARED_TEXT_IDS:
            file = library_text(coordinate, artifacts)
        key = (group, version, license_id, file)
        library = grouped.setdefault(key, Library(group, version, license_id, display, file, url))
        library.artifacts.append(name)
        library.url = library.url or url
    return sorted(grouped.values(), key=lambda lib: (lib.group, lib.version, lib.file))


def library_text(coordinate: str, artifacts: dict[str, list[Path]]) -> str:
    """Writes the licence text of a library that needs its own (LIBRARY_TEXTS) and returns its file."""
    group, name, version = coordinate.split(":")
    spec = LIBRARY_TEXTS.get(f"{group}:{name}")
    if spec is None:
        raise SystemExit(f"{coordinate} needs its own licence text (copyright lines): add it to LIBRARY_TEXTS")
    if spec["version"] != version:
        raise SystemExit(
            f"LIBRARY_TEXTS has the text of {group}:{name} {spec['version']}, the release has {version}: "
            "check the new version's licence and source, then update LIBRARY_TEXTS"
        )
    if "url" in spec:
        body = fetch_text(spec["url"])
        source = f"Source of this text: {spec['url']}"
    else:
        jar = next((p for p in artifacts.get(coordinate, []) if p.suffix in (".jar", ".aar")), None)
        if jar is None:
            raise SystemExit(f"No file for {coordinate}, whose licence is inside it")
        with zipfile.ZipFile(jar) as archive:
            body = archive.read(spec["jarEntry"]).decode("utf-8").replace("\r\n", "\n")
        source = f"Source of this text: {spec['jarEntry']} in {name}-{version}"
    write(spec["file"], spec["header"] + "\n" + source + "\n\n" + body.strip())
    return spec["file"]


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


def third_party_notices(libs: list[Library], artifacts: dict[str, list[Path]]) -> str:
    """The third-party notices inside every dependency's file, one section per component, deduplicated.

    Fails when one of Google's closed-source libraries has no file: its notices would go missing.
    """
    for lib in libs:
        if lib.license_id not in SHARED_TEXT_IDS - {"apache-2.0"}:
            continue
        missing = [c for c in lib.coordinates if c not in artifacts]
        if missing:
            raise SystemExit(f"No file for {', '.join(missing)}, so its third-party notices cannot be read")
    bodies: dict[str, list[str]] = {}
    for path in sorted({p for paths in artifacts.values() for p in paths}):
        if path.suffix not in (".aar", ".jar"):
            continue
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            if "third_party_licenses.json" not in names or "third_party_licenses.txt" not in names:
                continue
            index = json.loads(archive.read("third_party_licenses.json"))
            raw = archive.read("third_party_licenses.txt")
        for component, span in index.items():
            body = raw[span["start"] : span["start"] + span["length"]].decode("utf-8", "replace")
            body = body.replace("\r\n", "\n").strip()
            known = bodies.setdefault(component, [])
            if body and all(normalized(body) != normalized(other) for other in known):
                known.append(body)
    parts = [
        "Google's libraries in Birdy (Google Play Billing, Google Play services and Play In-App Review) "
        "are not open source; Google provides them under the terms named in the list. They contain "
        "open-source code, whose notices follow, as Google ships them in each library "
        "(third_party_licenses.txt). For a notice that is the Apache License 2.0, only its copyright "
        "lines are repeated here; the licence text is under Apache License 2.0 in this list. Some "
        "notices contain GPL or LGPL texts (J2ObjC's, for code under the GPL with the Classpath "
        "exception, and Kotlin's); they are reproduced as Google ships them.\n"
    ]
    for component in sorted(bodies, key=str.lower):
        variants = bodies[component]
        if not variants:
            continue
        # One body that contains another (a longer edition of the same notice) replaces it.
        kept = [b for b in variants if not any(b is not o and normalized(b) in normalized(o) for o in variants)]
        parts.append(section(component, "\n\n".join(dict.fromkeys(apache_or_text(body) for body in kept))))
    return "\n\n".join(parts)


# ---- 4. TensorFlow and its native code -----------------------------------------------------------


def native_version_lines(component: dict) -> list[str]:
    """Where the component is built in, and which version the text is from, without claiming LiteRT's."""
    pin = f"{component['version']}, pinned by TensorFlow {TF_COMMIT[:12]}"
    in_flex = "flex" in component["in"]
    in_litert = "litert" in component["in"]
    lines = []
    if in_flex:
        lines.append(f"In the flex library: version {pin}")
    if in_litert:
        lines.append(f"In LiteRT {NATIVE['litertVersion']}: version not published")
    if in_litert and not in_flex:
        lines.append(f"This text is from version {pin}")
    return lines


def check_native_pins(coordinates: list[str]) -> None:
    """Stops when the app's LiteRT or flex library is not the one native-components.json describes."""
    litert = [c.rsplit(":", 1)[1] for c in coordinates if c.startswith("com.google.ai.edge.litert:litert:")]
    match = FLEX_TAG_IN_BUILD.search((ROOT / "androidApp/build.gradle.kts").read_text(encoding="utf-8"))
    flex_tag = match.group(1) if match else None
    problems = []
    if litert != [NATIVE["litertVersion"]]:
        problems.append(f"the release build has LiteRT {litert}, native-components.json {NATIVE['litertVersion']}")
    if flex_tag != NATIVE["flexTag"] or not TF_COMMIT.startswith(NATIVE["flexTag"].removeprefix("tf-")):
        problems.append(f"androidApp's flexReleaseTag is {flex_tag}, native-components.json {NATIVE['flexTag']}")
    if problems:
        raise SystemExit(
            "The native libraries changed: "
            + "; ".join(problems)
            + ". Check which components and versions the new libraries contain, update"
            " tools/licenses/native-components.json, then run the generator again."
        )


# The order the native code's licences are named in, in the list's row.
NATIVE_LABEL_ORDER = ["Apache 2.0", "BSD", "MIT", "MPL 2.0", "MINPACK", "zlib", "IJG", "libpng", "Ooura"]


def native_label() -> str:
    """The licences in the native notices, from native-components.json's "labels"."""
    labels = {label for component in NATIVE["components"] for label in component["labels"]}
    unknown = labels - set(NATIVE_LABEL_ORDER)
    if unknown:
        raise SystemExit(f"Add {sorted(unknown)} to NATIVE_LABEL_ORDER")
    return " · ".join(label for label in NATIVE_LABEL_ORDER if label in labels)


def native_notices() -> tuple[str, list[dict]]:
    litert = f"LiteRT {NATIVE['litertVersion']}"
    parts = [
        "Native code built into the TensorFlow Lite libraries in Birdy: libtensorflowlite_jni.so "
        f"({litert}, photo and sound ID) and libtensorflowlite_flex_jni.so (TensorFlow, commit "
        f"{TF_COMMIT[:12]}, the 16 KB build from github.com/arxdeus/tflite_flex_16kb_android, tag "
        f"{NATIVE['flexTag']}; BirdNET's sound ID needs it). Versions are the ones TensorFlow pinned at "
        f"commit {TF_COMMIT[:12]} ({NATIVE['tensorflowCommitDate']}), which the flex library is built from. "
        f"{litert} ({NATIVE['litertDate']}) does not publish its versions; for LiteRT the texts come from "
        "the same versions, which may differ from the ones built into it.\n"
    ]
    for component in NATIVE["components"]:
        text = fetch_text(component["source"])
        if "lines" in component:
            first, last = component["lines"]
            text = "\n".join(text.splitlines()[first - 1 : last])
        header = [f"Licence: {component['license']}", *native_version_lines(component)]
        if "sourceCode" in component:
            header.append(f"Source code: {component['sourceCode']}")
        header.append(f"Source of this text: {component['source']}")
        if "sourceNote" in component:
            header.append(component["sourceNote"])
        if "evidence" in component:
            header.append(f"Why it is listed: {component['evidence']}")
        if "notice" in component:
            header.insert(0, component["notice"])
        body = apache_or_text(text)
        parts.append(section(component["name"], "\n".join(header) + "\n\n" + body))
    return "\n\n".join(parts), NATIVE["components"]


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


def library_entry(lib: Library, libs: list[Library], license_name: str, file: str) -> dict:
    shared = sum(1 for other in libs if other.group == lib.group and other.version == lib.version) > 1
    entry_id = f"lib:{lib.group}:{lib.version}" + (f":{lib.artifacts[0]}" if shared else "")
    return entry(
        entry_id,
        lib.group,
        lib.version,
        license_name,
        file,
        artifacts=lib.artifacts,
        coordinates=lib.coordinates,
        url=lib.url,
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--deps", type=Path, help="the dependencies file, instead of running Gradle")
    parser.add_argument("--artifacts", type=Path, help="the artifacts file, instead of running Gradle")
    args = parser.parse_args()

    if args.deps is None or args.artifacts is None:
        run_gradle()
    coordinates = release_dependencies(args.deps or GRADLE_DEPS)
    artifacts = release_artifacts(args.artifacts or GRADLE_ARTIFACTS, coordinates)
    log(f"{len(coordinates)} release dependencies, {sum(map(len, artifacts.values()))} files")
    check_native_pins(coordinates)
    load_apache()

    libs = libraries(coordinates, artifacts)
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
    write("google-third-party.txt", third_party_notices(libs, artifacts))
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
                    "6K global model",
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
                *(library_entry(lib, libs, "Apache License 2.0", "tensorflow.txt") for lib in tensorflow_libs),
                entry(
                    # The app shows its own localized name and no version for the two notice entries.
                    "tensorflow-native",
                    "Native code in TensorFlow Lite",
                    "",
                    native_label(),
                    "tensorflow-third-party.txt",
                    artifacts=[c["name"] for c in native_components],
                ),
            ],
        },
        {
            "id": "google",
            "entries": [
                *(library_entry(lib, libs, lib.license_name, "google-sdk.txt") for lib in google_libs),
                entry(
                    "google-third-party",
                    "Open-source code in Google's libraries",
                    "",
                    "Apache 2.0 · BSD · MIT · zlib · ICU · PCRE · GPL · LGPL",
                    "google-third-party.txt",
                ),
            ],
        },
        {
            "id": "libraries",
            "entries": [library_entry(lib, libs, lib.license_name, lib.file) for lib in other_libs],
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
