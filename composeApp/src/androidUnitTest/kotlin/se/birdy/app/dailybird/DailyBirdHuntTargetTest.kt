package se.birdy.app.dailybird

import org.junit.Test
import se.birdy.app.badges.BadgeCatalogLoader
import se.birdy.domain.badge.BadgeRule
import java.io.File
import kotlin.test.assertEquals

/**
 * The challenge row's "N av 3 dagar" and seals use [DAILY_BIRD_HUNT_TARGET]; the badge it counts
 * toward is premium_daily_bird_hunter in premium_badges.yaml. If either changes alone, the row
 * would promise a badge at the wrong day.
 */
class DailyBirdHuntTargetTest {
    @Test
    fun `the challenge target is the Dagens fagel-jagare badge target`() {
        val catalog = BadgeCatalogLoader.parsePremium(premiumBadgesYaml().readText())
        val hunter = catalog.badges.single { it.id == "premium_daily_bird_hunter" }
        val rule = hunter.rule as BadgeRule.DailyBirdMatches
        assertEquals(rule.target, DAILY_BIRD_HUNT_TARGET)
    }

    private fun premiumBadgesYaml(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            listOf("src/commonMain/composeResources/files", "composeApp/src/commonMain/composeResources/files")
                .map { File(File(dir, it), "premium_badges.yaml") }
                .firstOrNull { it.isFile }
                ?.let { return it }
            dir = dir.parentFile
        }
        error("premium_badges.yaml not found from ${System.getProperty("user.dir")}")
    }
}
