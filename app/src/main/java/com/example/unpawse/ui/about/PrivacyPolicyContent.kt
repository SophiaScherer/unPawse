package com.example.unpawse.ui.about

/** One heading-plus-body block of the policy. Kept as data so the screen stays pure layout. */
data class PolicySection(val heading: String, val body: String)

/** Shown under the title. Bump this whenever [privacyPolicySections] changes materially. */
const val PRIVACY_POLICY_UPDATED = "Last updated 27 July 2026"

/**
 * The privacy policy, as data rather than markup.
 *
 * Every claim here is checked against what the code actually does — the app declares no `INTERNET`
 * permission, ML Kit's labeller is the bundled on-device one, and captures are written to
 * `filesDir` by `PhotoStorage`. Keep it that way: if a future change adds a network call, an
 * analytics SDK, or a new stored field, this text is part of the change, not a follow-up.
 */
val privacyPolicySections: List<PolicySection> = listOf(
    PolicySection(
        heading = "The short version",
        body = "unPawse has no account, no server, no analytics and no ads. It cannot send your " +
            "data anywhere, because it does not ask for internet access at all.",
    ),
    PolicySection(
        heading = "What unPawse stores",
        body = "Screen time — for each app you chose to limit, how long you spent in it each day " +
            "and how many minutes you earned back.\n\n" +
            "Your limits — which apps you picked, their names, and each one's daily budget.\n\n" +
            "Cat photos — the pictures you take to earn time back, saved in the app's private " +
            "storage along with when each was taken and how confident the detector was.\n\n" +
            "Your preferences — display name, theme, detection sensitivity, and the time one cat " +
            "earns back.",
    ),
    PolicySection(
        heading = "Cat detection runs on your phone",
        body = "Photos are checked by an on-device image labeller that ships inside the app. " +
            "Nothing is uploaded for analysis, and no photo is sent anywhere to be verified.",
    ),
    PolicySection(
        heading = "What leaves your device",
        body = "Nothing on its own. The only ways data goes anywhere are ones you start yourself: " +
            "sharing a cat photo from the Gallery hands that single photo to whichever app you " +
            "pick.\n\n" +
            "Separately, if you have Android's backup turned on for your Google account, the " +
            "system may include unPawse's data — photos included — in your device backup. That is " +
            "Android's backup, not ours, and you can turn it off in your device settings.",
    ),
    PolicySection(
        heading = "Which permissions, and why",
        body = "Camera — to photograph a cat. Only while the camera screen is open.\n\n" +
            "Usage access — to see which app is in front, so time can be counted against the " +
            "limits you set. unPawse cannot see anything inside those apps.\n\n" +
            "Display over other apps — to draw the block screen over an app whose limit you have " +
            "reached.\n\n" +
            "Notifications — for the ongoing badge Android requires while monitoring runs.\n\n" +
            "Start at boot — so monitoring resumes after a restart instead of silently stopping.",
    ),
    PolicySection(
        heading = "How long photos are kept",
        body = "Cat photos are removed automatically once they pass the window you choose under " +
            "Settings › Manage photos — 30 days by default, or never if you prefer. Anything you " +
            "have marked as a favourite is kept until you delete it yourself.",
    ),
    PolicySection(
        heading = "Deleting your data",
        body = "You can delete any single photo from the Gallery, or all of them at once under " +
            "Settings › Manage photos.\n\n" +
            "Uninstalling unPawse removes everything it stored — photos, screen-time history and " +
            "preferences — from your device.",
    ),
)
