package com.example.unpawse.ui.about

/** One heading-plus-body block of the policy. Kept as data so the screen stays pure layout. */
data class PolicySection(val heading: String, val body: String)

/** Shown under the title. Bump this whenever [privacyPolicySections] changes materially. */
const val PRIVACY_POLICY_UPDATED = "Last updated 4 October 2026"

/**
 * The privacy policy, as data rather than markup.
 *
 * Every claim here is checked against what the code actually does. The manifest strips the
 * `INTERNET` permission ML Kit pulls in and unregisters its own telemetry backend, but the labeller
 * also logs anonymous call counts through Google Play services, which the app can't switch off, so
 * the policy discloses that. If a change adds a network call, an analytics SDK or a new stored
 * field, this text is part of the change.
 */
val privacyPolicySections: List<PolicySection> = listOf(
    PolicySection(
        heading = "The short version",
        body = "unPawse has no account, no server and no ads, and it does not ask for internet " +
            "access, so it cannot send your screen time, limits or photos anywhere. The one " +
            "exception is anonymous performance counts from the cat detector, described below.",
    ),
    PolicySection(
        heading = "What unPawse stores",
        body = "Screen time — for each app you chose to limit, how long you spent in it each day, " +
            "how many minutes you earned back, and how many times it was blocked. Also how many " +
            "times a day you unlocked your phone while monitoring was running.\n\n" +
            "Your limits — which apps you picked, their names and categories, each one's daily " +
            "and weekend budget, and any blocking schedules you set up.\n\n" +
            "Cat photos — the cat photos you take, saved in the app's private " +
            "storage along with when each was taken, its dimensions, how confident the detector " +
            "was, how much time it earned, and whether it is a favorite.\n\n" +
            "Your preferences — display name, theme, detection sensitivity, the time one cat " +
            "earns back, your notification choices and how long photos are kept.\n\n" +
            "Plus a few internal timers and caches, such as when a focus session ends and the " +
            "detector's own performance notes.",
    ),
    PolicySection(
        heading = "Cat detection runs on your phone",
        body = "Photos are checked by an on-device image labeller that ships inside the app. " +
            "Nothing is uploaded for analysis, and no photo is sent anywhere to be verified.\n\n" +
            "On phones with Google Play services, the labeller reports anonymous performance " +
            "counts to Play services — whether a check ran and how long it took, never the photo " +
            "or its result — which may send them to Google. unPawse switches off the labeller's " +
            "own reporting, but cannot switch off this part.",
    ),
    PolicySection(
        heading = "What leaves your device",
        body = "Apart from the detector's performance counts above, nothing on its own. The " +
            "only ways your data goes anywhere are ones you start yourself: " +
            "sharing a cat photo from the Gallery hands that single photo to whichever app you " +
            "pick, and Settings › Export data saves your history, limits, settings and photos to " +
            "a file wherever you choose to put it.\n\n" +
            "Separately, if you have Android's backup turned on for your Google account, the " +
            "system may include unPawse's data — photos included — in your device backup, and " +
            "moving to a new phone with Android's transfer tool copies it across too. That is " +
            "Android's backup, not ours, and you can turn it off in your device settings.",
    ),
    PolicySection(
        heading = "Which permissions, and why",
        body = "Camera — to photograph a cat. Only while the camera screen is open.\n\n" +
            "Usage access — to see which app is in front, so time can be counted against the " +
            "limits you set, and to read Android's own screen-time totals for the app picker and " +
            "the All apps view in Stats. unPawse cannot see anything inside those apps.\n\n" +
            "Display over other apps — to draw the block screen over an app whose limit you have " +
            "reached, and to open the camera from it.\n\n" +
            "Notifications — for the ongoing badge Android shows while monitoring runs, and for " +
            "the warning before an app locks, plus the reminders and daily summary you " +
            "switch on.\n\n" +
            "Background running — so monitoring keeps going while you use other apps, and photo " +
            "cleanup runs on schedule.\n\n" +
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
            "Settings › Manage photos. Settings › Delete all data erases everything else as " +
            "well.\n\n" +
            "Uninstalling unPawse removes everything it stored — photos, screen-time history and " +
            "preferences — from your device.",
    ),
)
