@file:JvmName("UriExt")
package com.kickstarter.libs.utils.extensions

import android.net.Uri
import com.kickstarter.libs.utils.ObjectUtils
import com.kickstarter.libs.utils.Secrets
import java.util.regex.Pattern

fun Uri.host(): String {
    return this.host ?: ""
}

fun Uri.lastPathSegment(): String {
    return this.lastPathSegment ?: ""
}

fun Uri.path(): String {
    return this.path ?: ""
}

fun Uri.query(): String {
    return this.query ?: ""
}

const val SCHEME_KSR = "ksr"
const val SCHEME_HTTPS = "https"

/**
 * Get token from Uri query params
 * From "at={TOKEN}&ref=ksr_email_user_email_verification" to "{TOKEN}"
 */
fun Uri.getTokenFromQueryParams(): String {
    return this.getQueryParameter("at") ?: ""
}

fun Uri.isSettingsUrl(): Boolean {
    return this.toString().contains("/settings/notify_mobile_of_marketing_update/true")
}

/**
 * Identify valid SCHEMAS "https:" or "ksr:"
 */
fun Uri.isKSScheme(): Boolean {
    return (scheme == SCHEME_KSR || scheme == SCHEME_HTTPS)
}

/**
 * Identify the Reward Fulfilled Deeplink
 */
fun Uri.isRewardFulfilledDl(): Boolean {
    return isKSScheme() && isKickstarterUri(this.toString()) && PROJECT_REWARD_FULFILLMENT.matcher(path()).matches()
}

fun Uri.isVerificationEmailUrl(): Boolean {
    return this.toString().contains(VERIFICATION)
}

fun Uri.isApiUri(webEndpoint: String): Boolean {
    return isKickstarterUri(webEndpoint) && Secrets.RegExpPattern.API.matcher(host())
        .matches()
}

fun Uri.isCheckoutUri(webEndpoint: String): Boolean {
    return isKickstarterUri(webEndpoint) && NATIVE_CHECKOUT_PATTERN.matcher(path())
        .matches()
}

fun Uri.isDiscoverCategoriesPath(): Boolean {
    return DISCOVER_CATEGORIES_PATTERN.matcher(path()).matches()
}

fun Uri.isDiscoverScopePath(scope: String): Boolean {
    val matcher = DISCOVER_SCOPE_PATTERN.matcher(path())
    return matcher.matches() && scope == matcher.group(1)
}

fun Uri.isDiscoverPlacesPath(): Boolean {
    return DISCOVER_PLACES_PATTERN.matcher(path()).matches()
}

fun Uri.isHivequeenUri(webEndpoint: String): Boolean {
    return isKickstarterUri(webEndpoint) && Secrets.RegExpPattern.HIVEQUEEN.matcher(host()).matches()
}

fun Uri.isKickstarterUri(webEndpoint: String): Boolean {
    return host() == Uri.parse(webEndpoint).host()
}

fun Uri.isKSFavIcon(webEndpoint: String): Boolean {
    return isKickstarterUri(webEndpoint) && lastPathSegment() == "favicon.ico"
}

fun Uri.isWebViewUri(webEndpoint: String): Boolean {
    return isKickstarterUri(webEndpoint) && !isKSFavIcon(webEndpoint)
}

fun Uri.isNewGuestCheckoutUri(webEndpoint: String): Boolean {
    return isKickstarterUri(webEndpoint) && NEW_GUEST_CHECKOUT_PATTERN.matcher(path())
        .matches()
}

fun Uri.isProjectUri(webEndpoint: String): Boolean {
    return isKickstarterUri(webEndpoint) && PROJECT_PATTERN.matcher(path()).matches()
}

fun Uri.isProjectPreviewUri(webEndpoint: String): Boolean {
    return isProjectUri(
        webEndpoint
    ) && ObjectUtils.isNotNull(getQueryParameter("token"))
}

fun Uri.isSignupUri(webEndpoint: String): Boolean {
    return isKickstarterUri(webEndpoint) && path() == "/signup"
}

fun Uri.isStagingUri(webEndpoint: String): Boolean {
    return isKickstarterUri(webEndpoint) && Secrets.RegExpPattern.STAGING.matcher(host()).matches()
}

fun Uri.isCheckoutThanksUri(webEndpoint: String): Boolean {
    return isKickstarterUri(webEndpoint) && CHECKOUT_THANKS_PATTERN.matcher(path())
        .matches()
}

fun Uri.isModalUri(webEndpoint: String): Boolean {
    return isKickstarterUri(webEndpoint) && getQueryParameter("modal") != null && getQueryParameter("modal") == "true"
}

fun Uri.isProjectSurveyUri(webEndpoint: String): Boolean {
    return isKickstarterUri(webEndpoint) && PROJECT_SURVEY.matcher(path()).matches()
}

fun Uri.isProjectCommentUri(webEndpoint: String): Boolean {
    return isKickstarterUri(webEndpoint) && PROJECT_COMMENTS_PATTERN.matcher(path())
        .matches()
}

fun Uri.isProjectSaveUri(webEndpoint: String): Boolean {
    return isKickstarterUri(webEndpoint) &&
        PROJECT_PATTERN.matcher(path()).matches() &&
        PROJECT_SAVE_QUERY_PATTERN.matcher(query()).matches()
}

fun Uri.isProjectUpdateCommentsUri(webEndpoint: String): Boolean {
    return isKickstarterUri(webEndpoint) && PROJECT_UPDATE_COMMENTS_PATTERN.matcher(path()).matches()
}

fun Uri.isProjectUpdateUri(webEndpoint: String): Boolean {
    return isKickstarterUri(webEndpoint) && PROJECT_UPDATE_PATTERN.matcher(path())
        .matches()
}

fun Uri.isProjectUpdatesUri(webEndpoint: String): Boolean {
    return isKickstarterUri(webEndpoint) && PROJECT_UPDATES_PATTERN.matcher(path())
        .matches()
}

fun Uri.isUserSurveyUri(webEndpoint: String): Boolean {
    return isKickstarterUri(webEndpoint) && USER_SURVEY.matcher(path()).matches()
}

fun Uri.isWebUri(webEndpoint: String): Boolean {
    return isKickstarterUri(webEndpoint) && !isApiUri(webEndpoint)
}

fun Uri.isDiscoverSortParam(): Boolean {
    return DISCOVER_SORT_PATTERN.matcher(path()).matches() &&
        ObjectUtils.isNotNull(getQueryParameter("sort"))
}

private const val VERIFICATION = "/profile/verify_email"

// /projects/:creator_param/:project_param/checkouts/1/thanks
private val CHECKOUT_THANKS_PATTERN = Pattern.compile(
    "\\A\\/projects(\\/[a-zA-Z0-9_-]+)?\\/[a-zA-Z0-9_-]+\\/checkouts\\/\\d+\\/thanks\\z"
)

// /discover/categories/param
private val DISCOVER_CATEGORIES_PATTERN = Pattern.compile("\\A\\/discover\\/categories\\/.*")

// /discover/param
private val DISCOVER_SCOPE_PATTERN = Pattern.compile("\\A\\/discover\\/([a-zA-Z0-9-_]+)\\z")

// /discover/places/param
private val DISCOVER_PLACES_PATTERN =
    Pattern.compile("\\A\\/discover\\/places\\/[a-zA-Z0-9-_]+\\z")

// /discover/advanced?sort=param
private val DISCOVER_SORT_PATTERN = Pattern.compile("\\A\\/discover\\/advanced.*")

//  /projects/:creator_param/:project_param/pledge
private val NATIVE_CHECKOUT_PATTERN = Pattern.compile(
    "\\A\\/projects(\\/[a-zA-Z0-9_-]+)?\\/[a-zA-Z0-9_-]+\\/pledge\\z"
)

// /checkouts/:checkout_id/guest/new
private val NEW_GUEST_CHECKOUT_PATTERN = Pattern.compile(
    "\\A\\/checkouts\\/[a-zA-Z0-9-_]+\\/guest\\/new\\z"
)

// /projects/:creator_param/:project_param
private val PROJECT_PATTERN = Pattern.compile(
    "\\A\\/projects(\\/[a-zA-Z0-9_-]+)?\\/[a-zA-Z0-9_-]+\\/?\\z"
)

//  /projects/:creator_param/:project_param/surveys/:survey_param
private val PROJECT_SURVEY = Pattern.compile(
    "\\A\\/projects(\\/[a-zA-Z0-9_-]+)?\\/[a-zA-Z0-9_-]+\\/surveys\\/[a-zA-Z0-9-_]+\\z"
)

//  /projects/:creator_param/:project_param/mark_reward_fulfilled/true
private val PROJECT_REWARD_FULFILLMENT = Pattern.compile(
    "\\A/projects(\\/[a-zA-Z0-9_-]+)?\\/[a-zA-Z0-9_-]+\\/mark_reward_fulfilled/true+\\z"
)

// /projects/:creator_param/:project_param/comments
private val PROJECT_COMMENTS_PATTERN = Pattern.compile(
    "\\A\\/projects(\\/[a-zA-Z0-9_-]+)?\\/[a-zA-Z0-9_-]+\\/comments\\z"
)

// save=true|false
private val PROJECT_SAVE_QUERY_PATTERN = Pattern.compile(
    "save(\\=[a-zA-Z]+)"
)

// /projects/:creator_param/:project_param/posts/:update_param/comments
private val PROJECT_UPDATE_COMMENTS_PATTERN = Pattern.compile(
    "\\A\\/projects(\\/[a-zA-Z0-9_-]+)?\\/[a-zA-Z0-9_-]+\\/posts\\/[a-zA-Z0-9-_]+\\/comments\\z"
)

// /projects/:creator_param/:project_param/posts/:update_param
private val PROJECT_UPDATE_PATTERN = Pattern.compile(
    "\\A\\/projects(\\/[a-zA-Z0-9_-]+)?\\/[a-zA-Z0-9_-]+\\/posts\\/[a-zA-Z0-9-_]+\\z"
)

// /projects/:creator_param/:project_param/posts
private val PROJECT_UPDATES_PATTERN = Pattern.compile(
    "\\A\\/projects(\\/[a-zA-Z0-9_-]+)?\\/[a-zA-Z0-9_-]+\\/posts\\z"
)

// /users/:user_param/surveys/:survey_response_id": userSurvey
private val USER_SURVEY = Pattern.compile(
    "\\A\\/users(\\/[a-zA-Z0-9_-]+)?\\/surveys\\/[a-zA-Z0-9-_]+\\z"
)
