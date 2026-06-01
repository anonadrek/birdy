package se.birdy.content.build

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.Serializable

@Serializable
private data class FamilyGroupsYaml(
    val order: List<String>,
    val groups: Map<String, GroupDefYaml>,
)

@Serializable
private data class GroupDefYaml(
    val keyed_by: String? = null,
    val ioc_order: String? = null,
    val families: List<String> = emptyList(),
)

/**
 * DP E — kurerad ekologisk grupp-axel. Enda sanningskällan: family_groups.yaml
 * (jvmMain-resurs). `songbirds` keyas på ioc_order==Passeriformes; varje annan
 * grupp keyas på latinsk familj. Okänd familj → "other".
 */
class FamilyGroups internal constructor(
    val groupIds: List<String>,
    private val familyToGroup: Map<String, String>,
    private val orderKeyedGroupId: String?,
    private val orderKeyedIocOrder: String?,
) {
    /** Returnerar grupp-id för en art. Kastar aldrig; okänd familj → "other". */
    fun groupFor(
        family: String,
        iocOrder: String,
    ): String {
        if (orderKeyedGroupId != null && iocOrder == orderKeyedIocOrder) return orderKeyedGroupId
        return familyToGroup[family] ?: OTHER
    }

    /** True om arten matchas av en uttrycklig regel (order-key eller family-set), inte fallback. */
    fun isExplicitlyMapped(
        family: String,
        iocOrder: String,
    ): Boolean {
        if (orderKeyedGroupId != null && iocOrder == orderKeyedIocOrder) return true
        return familyToGroup.containsKey(family)
    }

    companion object {
        const val OTHER = "other"
        private const val RESOURCE = "/family_groups.yaml"
        private val yaml = Yaml(configuration = YamlConfiguration(strictMode = false))

        fun loadDefault(): FamilyGroups {
            val text =
                FamilyGroups::class.java
                    .getResourceAsStream(RESOURCE)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: error("Missing classpath resource $RESOURCE")
            return parse(text)
        }

        fun parse(yamlText: String): FamilyGroups {
            val model = yaml.decodeFromString(FamilyGroupsYaml.serializer(), yamlText)
            val familyToGroup = LinkedHashMap<String, String>()
            var orderKeyedId: String? = null
            var orderKeyedIoc: String? = null
            for ((groupId, def) in model.groups) {
                if (def.keyed_by == "order") {
                    requireNotNull(def.ioc_order) { "Group '$groupId' keyed_by order saknar ioc_order" }
                    orderKeyedId = groupId
                    orderKeyedIoc = def.ioc_order
                    continue
                }
                for (family in def.families) {
                    val prev = familyToGroup.put(family, groupId)
                    require(prev == null) { "Familjen '$family' mappad till både '$prev' och '$groupId'" }
                }
            }
            require(model.order.toSet() == model.groups.keys) {
                "order-listan måste matcha grupp-id:na: ${model.order} vs ${model.groups.keys}"
            }
            return FamilyGroups(model.order, familyToGroup, orderKeyedId, orderKeyedIoc)
        }
    }
}
