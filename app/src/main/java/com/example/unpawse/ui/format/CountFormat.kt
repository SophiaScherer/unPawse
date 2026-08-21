package com.example.unpawse.ui.format

/**
 * A count and the noun it counts, agreeing: "1 photo", "5 photos", "1 Day", "3 Days".
 *
 * This rule had been solved ten independent times — `dayCountLabel`, the App Picker header, the
 * Settings summary, two byte-identical copies in Photo storage — and the one site that hadn't
 * solved it rendered "1 Photos" on Stats. Two hand-copies of a rule are one edit away from
 * disagreeing, the same reason `SearchField` and `EmptyStateCard` are shared.
 *
 * Plain Kotlin rather than a `<plurals>` resource: `strings.xml` holds only `app_name` and nothing
 * in the app calls `stringResource`, so a single localized entry would be the odd one out. This is
 * the seam a localization pass would replace.
 */
fun countLabel(count: Int, singular: String, plural: String = "${singular}s"): String =
    "$count ${if (count == 1) singular else plural}"
