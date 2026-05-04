package se.birdy.content.build

import java.nio.file.Files
import java.nio.file.Path

class SpeciesValidator(
    private val imageRoot: Path,
    private val expectedCount: Int,
    private val overrides: Map<String, OverrideEntry>,
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

        return errors
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

    companion object {
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
