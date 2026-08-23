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
    "$count ${pluralOf(count, singular, plural)}"

/**
 * Just the noun, agreeing with [count]: "Cat" / "Cats".
 *
 * For layouts that render the number and its noun as separate elements, where [countLabel]'s single
 * string doesn't fit — Home's stat pills draw the figure above its label, which is exactly how the
 * "Cats" pill came to read **"1 Cats"** while every inline count in the app had been fixed. Same
 * rule, one definition: [countLabel] is built on this, so neither can drift from the other.
 */
fun pluralOf(count: Int, singular: String, plural: String = "${singular}s"): String =
    if (count == 1) singular else plural
