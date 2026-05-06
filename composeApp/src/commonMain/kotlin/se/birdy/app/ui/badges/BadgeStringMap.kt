package se.birdy.app.ui.badges

import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_birder_bronze
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_birder_silver
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_family_accipitridae
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_family_anatidae
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_family_corvidae
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_family_fringillidae
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_family_paridae
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_family_picidae
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_family_sylviidae
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_family_turdidae
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_monthly_streak_12
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_monthly_streak_3
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_monthly_streak_6
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_novice
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_rare_10
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_rare_5
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_rare_first
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_season_autumn
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_season_spring
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_season_summer
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_season_winter
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_weekly_streak_12
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_weekly_streak_26
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_weekly_streak_4
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_weekly_streak_52
import birdy_bird_scanner.composeapp.generated.resources.badge_name_birder_bronze
import birdy_bird_scanner.composeapp.generated.resources.badge_name_birder_silver
import birdy_bird_scanner.composeapp.generated.resources.badge_name_family_accipitridae
import birdy_bird_scanner.composeapp.generated.resources.badge_name_family_anatidae
import birdy_bird_scanner.composeapp.generated.resources.badge_name_family_corvidae
import birdy_bird_scanner.composeapp.generated.resources.badge_name_family_fringillidae
import birdy_bird_scanner.composeapp.generated.resources.badge_name_family_paridae
import birdy_bird_scanner.composeapp.generated.resources.badge_name_family_picidae
import birdy_bird_scanner.composeapp.generated.resources.badge_name_family_sylviidae
import birdy_bird_scanner.composeapp.generated.resources.badge_name_family_turdidae
import birdy_bird_scanner.composeapp.generated.resources.badge_name_monthly_streak_12
import birdy_bird_scanner.composeapp.generated.resources.badge_name_monthly_streak_3
import birdy_bird_scanner.composeapp.generated.resources.badge_name_monthly_streak_6
import birdy_bird_scanner.composeapp.generated.resources.badge_name_novice
import birdy_bird_scanner.composeapp.generated.resources.badge_name_rare_10
import birdy_bird_scanner.composeapp.generated.resources.badge_name_rare_5
import birdy_bird_scanner.composeapp.generated.resources.badge_name_rare_first
import birdy_bird_scanner.composeapp.generated.resources.badge_name_season_autumn
import birdy_bird_scanner.composeapp.generated.resources.badge_name_season_spring
import birdy_bird_scanner.composeapp.generated.resources.badge_name_season_summer
import birdy_bird_scanner.composeapp.generated.resources.badge_name_season_winter
import birdy_bird_scanner.composeapp.generated.resources.badge_name_weekly_streak_12
import birdy_bird_scanner.composeapp.generated.resources.badge_name_weekly_streak_26
import birdy_bird_scanner.composeapp.generated.resources.badge_name_weekly_streak_4
import birdy_bird_scanner.composeapp.generated.resources.badge_name_weekly_streak_52
import org.jetbrains.compose.resources.StringResource

object BadgeStringMap {
    fun nameFor(badgeId: String): StringResource =
        when (badgeId) {
            "novice" -> Res.string.badge_name_novice
            "birder_bronze" -> Res.string.badge_name_birder_bronze
            "birder_silver" -> Res.string.badge_name_birder_silver
            "weekly_streak_4" -> Res.string.badge_name_weekly_streak_4
            "weekly_streak_12" -> Res.string.badge_name_weekly_streak_12
            "weekly_streak_26" -> Res.string.badge_name_weekly_streak_26
            "weekly_streak_52" -> Res.string.badge_name_weekly_streak_52
            "monthly_streak_3" -> Res.string.badge_name_monthly_streak_3
            "monthly_streak_6" -> Res.string.badge_name_monthly_streak_6
            "monthly_streak_12" -> Res.string.badge_name_monthly_streak_12
            "season_winter" -> Res.string.badge_name_season_winter
            "season_spring" -> Res.string.badge_name_season_spring
            "season_summer" -> Res.string.badge_name_season_summer
            "season_autumn" -> Res.string.badge_name_season_autumn
            "family_anatidae" -> Res.string.badge_name_family_anatidae
            "family_paridae" -> Res.string.badge_name_family_paridae
            "family_accipitridae" -> Res.string.badge_name_family_accipitridae
            "family_corvidae" -> Res.string.badge_name_family_corvidae
            "family_fringillidae" -> Res.string.badge_name_family_fringillidae
            "family_turdidae" -> Res.string.badge_name_family_turdidae
            "family_sylviidae" -> Res.string.badge_name_family_sylviidae
            "family_picidae" -> Res.string.badge_name_family_picidae
            "rare_first" -> Res.string.badge_name_rare_first
            "rare_5" -> Res.string.badge_name_rare_5
            "rare_10" -> Res.string.badge_name_rare_10
            else -> error("No name resource for badgeId=$badgeId")
        }

    fun descriptionFor(badgeId: String): StringResource =
        when (badgeId) {
            "novice" -> Res.string.badge_desc_novice
            "birder_bronze" -> Res.string.badge_desc_birder_bronze
            "birder_silver" -> Res.string.badge_desc_birder_silver
            "weekly_streak_4" -> Res.string.badge_desc_weekly_streak_4
            "weekly_streak_12" -> Res.string.badge_desc_weekly_streak_12
            "weekly_streak_26" -> Res.string.badge_desc_weekly_streak_26
            "weekly_streak_52" -> Res.string.badge_desc_weekly_streak_52
            "monthly_streak_3" -> Res.string.badge_desc_monthly_streak_3
            "monthly_streak_6" -> Res.string.badge_desc_monthly_streak_6
            "monthly_streak_12" -> Res.string.badge_desc_monthly_streak_12
            "season_winter" -> Res.string.badge_desc_season_winter
            "season_spring" -> Res.string.badge_desc_season_spring
            "season_summer" -> Res.string.badge_desc_season_summer
            "season_autumn" -> Res.string.badge_desc_season_autumn
            "family_anatidae" -> Res.string.badge_desc_family_anatidae
            "family_paridae" -> Res.string.badge_desc_family_paridae
            "family_accipitridae" -> Res.string.badge_desc_family_accipitridae
            "family_corvidae" -> Res.string.badge_desc_family_corvidae
            "family_fringillidae" -> Res.string.badge_desc_family_fringillidae
            "family_turdidae" -> Res.string.badge_desc_family_turdidae
            "family_sylviidae" -> Res.string.badge_desc_family_sylviidae
            "family_picidae" -> Res.string.badge_desc_family_picidae
            "rare_first" -> Res.string.badge_desc_rare_first
            "rare_5" -> Res.string.badge_desc_rare_5
            "rare_10" -> Res.string.badge_desc_rare_10
            else -> error("No description resource for badgeId=$badgeId")
        }
}
