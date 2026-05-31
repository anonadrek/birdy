package se.birdy.app.ui.badges

import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_audio_scholar
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_birder_bronze
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_birder_gold
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_birder_legend
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_birder_silver
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_breadth_families_20
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_breadth_families_50
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_breadth_orders_20
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_family_accipitridae
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_family_anatidae
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_family_fringillidae
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_family_paridae
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_family_scolopacidae
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_family_songbirds
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_family_strigidae
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_monthly_streak_12
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_monthly_streak_3
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_novice
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_redlisted_1
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_redlisted_15
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_redlisted_5
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_season_all_year
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_season_faithful
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_weekly_streak_12
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_weekly_streak_4
import birdy_bird_scanner.composeapp.generated.resources.badge_desc_weekly_streak_52
import birdy_bird_scanner.composeapp.generated.resources.badge_name_audio_scholar
import birdy_bird_scanner.composeapp.generated.resources.badge_name_birder_bronze
import birdy_bird_scanner.composeapp.generated.resources.badge_name_birder_gold
import birdy_bird_scanner.composeapp.generated.resources.badge_name_birder_legend
import birdy_bird_scanner.composeapp.generated.resources.badge_name_birder_silver
import birdy_bird_scanner.composeapp.generated.resources.badge_name_breadth_families_20
import birdy_bird_scanner.composeapp.generated.resources.badge_name_breadth_families_50
import birdy_bird_scanner.composeapp.generated.resources.badge_name_breadth_orders_20
import birdy_bird_scanner.composeapp.generated.resources.badge_name_family_accipitridae
import birdy_bird_scanner.composeapp.generated.resources.badge_name_family_anatidae
import birdy_bird_scanner.composeapp.generated.resources.badge_name_family_fringillidae
import birdy_bird_scanner.composeapp.generated.resources.badge_name_family_paridae
import birdy_bird_scanner.composeapp.generated.resources.badge_name_family_scolopacidae
import birdy_bird_scanner.composeapp.generated.resources.badge_name_family_songbirds
import birdy_bird_scanner.composeapp.generated.resources.badge_name_family_strigidae
import birdy_bird_scanner.composeapp.generated.resources.badge_name_monthly_streak_12
import birdy_bird_scanner.composeapp.generated.resources.badge_name_monthly_streak_3
import birdy_bird_scanner.composeapp.generated.resources.badge_name_novice
import birdy_bird_scanner.composeapp.generated.resources.badge_name_redlisted_1
import birdy_bird_scanner.composeapp.generated.resources.badge_name_redlisted_15
import birdy_bird_scanner.composeapp.generated.resources.badge_name_redlisted_5
import birdy_bird_scanner.composeapp.generated.resources.badge_name_season_all_year
import birdy_bird_scanner.composeapp.generated.resources.badge_name_season_faithful
import birdy_bird_scanner.composeapp.generated.resources.badge_name_weekly_streak_12
import birdy_bird_scanner.composeapp.generated.resources.badge_name_weekly_streak_4
import birdy_bird_scanner.composeapp.generated.resources.badge_name_weekly_streak_52
import birdy_bird_scanner.composeapp.generated.resources.badge_premium_daily_bird_hunter_desc
import birdy_bird_scanner.composeapp.generated.resources.badge_premium_daily_bird_hunter_name
import birdy_bird_scanner.composeapp.generated.resources.badge_premium_dawn_chorus_desc
import birdy_bird_scanner.composeapp.generated.resources.badge_premium_dawn_chorus_name
import birdy_bird_scanner.composeapp.generated.resources.badge_premium_early_pilgrim_desc
import birdy_bird_scanner.composeapp.generated.resources.badge_premium_early_pilgrim_name
import birdy_bird_scanner.composeapp.generated.resources.badge_premium_field_journalist_desc
import birdy_bird_scanner.composeapp.generated.resources.badge_premium_field_journalist_name
import birdy_bird_scanner.composeapp.generated.resources.badge_premium_field_member_desc
import birdy_bird_scanner.composeapp.generated.resources.badge_premium_field_member_name
import birdy_bird_scanner.composeapp.generated.resources.badge_premium_sunday_birder_desc
import birdy_bird_scanner.composeapp.generated.resources.badge_premium_sunday_birder_name
import birdy_bird_scanner.composeapp.generated.resources.badge_premium_winter_wanderer_desc
import birdy_bird_scanner.composeapp.generated.resources.badge_premium_winter_wanderer_name
import org.jetbrains.compose.resources.StringResource

object BadgeStringMap {
    fun nameFor(badgeId: String): StringResource =
        when (badgeId) {
            "novice" -> Res.string.badge_name_novice
            "birder_bronze" -> Res.string.badge_name_birder_bronze
            "birder_silver" -> Res.string.badge_name_birder_silver
            "birder_gold" -> Res.string.badge_name_birder_gold
            "birder_legend" -> Res.string.badge_name_birder_legend
            "family_anatidae" -> Res.string.badge_name_family_anatidae
            "family_scolopacidae" -> Res.string.badge_name_family_scolopacidae
            "family_accipitridae" -> Res.string.badge_name_family_accipitridae
            "family_fringillidae" -> Res.string.badge_name_family_fringillidae
            "family_paridae" -> Res.string.badge_name_family_paridae
            "family_strigidae" -> Res.string.badge_name_family_strigidae
            "family_songbirds" -> Res.string.badge_name_family_songbirds
            "breadth_families_20" -> Res.string.badge_name_breadth_families_20
            "breadth_families_50" -> Res.string.badge_name_breadth_families_50
            "breadth_orders_20" -> Res.string.badge_name_breadth_orders_20
            "redlisted_1" -> Res.string.badge_name_redlisted_1
            "redlisted_5" -> Res.string.badge_name_redlisted_5
            "redlisted_15" -> Res.string.badge_name_redlisted_15
            "season_all_year" -> Res.string.badge_name_season_all_year
            "season_faithful" -> Res.string.badge_name_season_faithful
            "audio_scholar" -> Res.string.badge_name_audio_scholar
            "weekly_streak_4" -> Res.string.badge_name_weekly_streak_4
            "weekly_streak_12" -> Res.string.badge_name_weekly_streak_12
            "weekly_streak_52" -> Res.string.badge_name_weekly_streak_52
            "monthly_streak_3" -> Res.string.badge_name_monthly_streak_3
            "monthly_streak_12" -> Res.string.badge_name_monthly_streak_12
            "premium_field_member" -> Res.string.badge_premium_field_member_name
            "premium_dawn_chorus" -> Res.string.badge_premium_dawn_chorus_name
            "premium_early_pilgrim" -> Res.string.badge_premium_early_pilgrim_name
            "premium_field_journalist" -> Res.string.badge_premium_field_journalist_name
            "premium_winter_wanderer" -> Res.string.badge_premium_winter_wanderer_name
            "premium_sunday_birder" -> Res.string.badge_premium_sunday_birder_name
            "premium_daily_bird_hunter" -> Res.string.badge_premium_daily_bird_hunter_name
            else -> error("No name resource for badgeId=$badgeId")
        }

    fun descriptionFor(badgeId: String): StringResource =
        when (badgeId) {
            "novice" -> Res.string.badge_desc_novice
            "birder_bronze" -> Res.string.badge_desc_birder_bronze
            "birder_silver" -> Res.string.badge_desc_birder_silver
            "birder_gold" -> Res.string.badge_desc_birder_gold
            "birder_legend" -> Res.string.badge_desc_birder_legend
            "family_anatidae" -> Res.string.badge_desc_family_anatidae
            "family_scolopacidae" -> Res.string.badge_desc_family_scolopacidae
            "family_accipitridae" -> Res.string.badge_desc_family_accipitridae
            "family_fringillidae" -> Res.string.badge_desc_family_fringillidae
            "family_paridae" -> Res.string.badge_desc_family_paridae
            "family_strigidae" -> Res.string.badge_desc_family_strigidae
            "family_songbirds" -> Res.string.badge_desc_family_songbirds
            "breadth_families_20" -> Res.string.badge_desc_breadth_families_20
            "breadth_families_50" -> Res.string.badge_desc_breadth_families_50
            "breadth_orders_20" -> Res.string.badge_desc_breadth_orders_20
            "redlisted_1" -> Res.string.badge_desc_redlisted_1
            "redlisted_5" -> Res.string.badge_desc_redlisted_5
            "redlisted_15" -> Res.string.badge_desc_redlisted_15
            "season_all_year" -> Res.string.badge_desc_season_all_year
            "season_faithful" -> Res.string.badge_desc_season_faithful
            "audio_scholar" -> Res.string.badge_desc_audio_scholar
            "weekly_streak_4" -> Res.string.badge_desc_weekly_streak_4
            "weekly_streak_12" -> Res.string.badge_desc_weekly_streak_12
            "weekly_streak_52" -> Res.string.badge_desc_weekly_streak_52
            "monthly_streak_3" -> Res.string.badge_desc_monthly_streak_3
            "monthly_streak_12" -> Res.string.badge_desc_monthly_streak_12
            "premium_field_member" -> Res.string.badge_premium_field_member_desc
            "premium_dawn_chorus" -> Res.string.badge_premium_dawn_chorus_desc
            "premium_early_pilgrim" -> Res.string.badge_premium_early_pilgrim_desc
            "premium_field_journalist" -> Res.string.badge_premium_field_journalist_desc
            "premium_winter_wanderer" -> Res.string.badge_premium_winter_wanderer_desc
            "premium_sunday_birder" -> Res.string.badge_premium_sunday_birder_desc
            "premium_daily_bird_hunter" -> Res.string.badge_premium_daily_bird_hunter_desc
            else -> error("No description resource for badgeId=$badgeId")
        }
}
