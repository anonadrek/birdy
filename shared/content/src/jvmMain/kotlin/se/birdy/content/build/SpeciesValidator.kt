package se.birdy.content.build

import java.nio.file.Files
import java.nio.file.Path

class SpeciesValidator(
    private val imageRoot: Path,
    private val expectedCount: Int,
    private val overrides: Map<String, OverrideEntry>,
    // The image folder must hold exactly the files the YAML points at: a file
    // nothing references still ships (iOS bundles the whole folder). Off by
    // default because the test fixtures share one image folder.
    private val checkOrphans: Boolean = false,
) {
    fun validate(items: List<Pair<Path, SpeciesYaml>>): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        val seenIds = mutableSetOf<String>()

        if (items.size < expectedCount) {
            errors +=
                ValidationError(
                    species = "(global)",
                    rule = "expected-count-mismatch",
                    message = "Expected $expectedCount species, got ${items.size}",
                )
        }

        for ((path, yaml) in items) {
            errors += validateOne(path, yaml)
            if (!seenIds.add(yaml.id)) {
                errors +=
                    ValidationError(yaml.id, "duplicate-id", "id appears in multiple files")
            }
        }
        if (checkOrphans) errors += orphanImages(items)

        return errors
    }

    private fun orphanImages(items: List<Pair<Path, SpeciesYaml>>): List<ValidationError> {
        if (!Files.isDirectory(imageRoot)) return emptyList()
        val referenced = items.flatMap { (_, yaml) -> yaml.image_refs.map { it.path } }.toSet()
        return Files
            .walk(imageRoot)
            .use { stream ->
                stream
                    .filter { Files.isRegularFile(it) }
                    .map { imageRoot.relativize(it).joinToString("/") }
                    .filter { it !in referenced }
                    .sorted()
                    .toList()
            }.map { ValidationError("(global)", "image-orphan", "$it is not referenced by any image_refs") }
    }

    private fun validateOne(
        path: Path,
        yaml: SpeciesYaml,
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        val expectedFilename = "${yaml.id}.yaml"
        if (path.fileName.toString() != expectedFilename) {
            errors += ValidationError(yaml.id, "filename-id-mismatch", "file=${path.fileName}, id=${yaml.id}")
        }

        for ((lang, text) in yaml.description) {
            val resolved =
                overrides[yaml.id]?.descriptionAcceptMissing?.contains(lang) == true
            if (resolved) continue
            val words = (text ?: "").split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.size < 80) {
                errors +=
                    ValidationError(
                        yaml.id,
                        "description-too-short",
                        "description.$lang has ${words.size} words (need ≥80)",
                    )
            }
        }

        if (yaml.abundance == "allmän" && yaml.review_status != "approved") {
            errors +=
                ValidationError(
                    yaml.id,
                    "common-needs-approval",
                    "abundance=allmän requires review_status=approved",
                )
        }

        for (region in yaml.regions) {
            if (region !in VALID_REGIONS) {
                errors += ValidationError(yaml.id, "invalid-region", "unknown ISO code '$region'")
            }
        }

        for (img in yaml.image_refs) {
            val full = imageRoot.resolve(img.path)
            if (!Files.exists(full)) {
                errors +=
                    ValidationError(
                        yaml.id,
                        "image-file-missing",
                        "${img.path} not found under $imageRoot",
                    )
            }
            if (img.role == "hero" && (img.width < 2048 && img.height < 2048)) {
                errors +=
                    ValidationError(
                        yaml.id,
                        "hero-too-small",
                        "${img.path} is ${img.width}x${img.height}, need ≥2048 on one side",
                    )
            }
            if (img.license.isBlank() || img.author.isBlank() || img.source_url.isBlank()) {
                errors +=
                    ValidationError(
                        yaml.id,
                        "image-missing-metadata",
                        "${img.path} missing license/author/source_url",
                    )
            }
            errors += validateCredit(yaml.id, img)
        }

        if (yaml.image_refs.isEmpty() && overrides[yaml.id]?.allowMissingImages != true) {
            errors +=
                ValidationError(
                    yaml.id,
                    "no-images",
                    "image_refs empty; add images or set allow_missing_images in overrides",
                )
        }

        return errors
    }

    private fun validateCredit(
        species: String,
        img: ImageRefYaml,
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        if (img.license !in ALLOWED_LICENSES) {
            errors +=
                ValidationError(
                    species,
                    "image-license-not-allowed",
                    "${img.path} has licence '${img.license}' (allowed: ${ALLOWED_LICENSES.joinToString()})",
                )
        }
        if ('<' in img.author || '>' in img.author) {
            errors += ValidationError(species, "image-author-html", "${img.path} author is HTML, not a name")
        }
        val needsName = img.license in ALLOWED_LICENSES && img.license !in PUBLIC_DOMAIN_LICENSES
        if (needsName && img.author.trim().lowercase() in UNNAMED_AUTHORS) {
            errors +=
                ValidationError(
                    species,
                    "image-author-missing",
                    "${img.path} is ${img.license} but credits '${img.author}'",
                )
        }
        return errors
    }

    companion object {
        // Release 1.3.0: the app may only ship photos under these licences
        // (mirrors tools/content-pipeline/src/birdy_fetcher/credits.py).
        val PUBLIC_DOMAIN_LICENSES = setOf("CC0", "Public domain")
        val ALLOWED_LICENSES =
            PUBLIC_DOMAIN_LICENSES +
                setOf("CC BY 2.0", "CC BY 3.0", "CC BY 4.0", "CC BY-SA 2.0", "CC BY-SA 3.0", "CC BY-SA 4.0")
        private val UNNAMED_AUTHORS = setOf("", "unknown", "anonymous", "no rights reserved")

        private val VALID_REGIONS =
            setOf(
                "SE",
                "NO",
                "FI",
                "DK",
                "DE",
                "NL",
                "BE",
                "FR",
                "GB",
                "IE",
                "PL",
                "AT",
                "CH",
                "IT",
                "ES",
                "PT",
                "GR",
                "IS",
            )
    }
}

data class OverrideEntry(
    val descriptionAcceptMissing: Set<String> = emptySet(),
    val allowMissingImages: Boolean = false,
)
