package com.example.data.remote

/**
 * Single source of truth for "does this published update concern this build?".
 *
 * The developer pushes an update once, for every installation at once — so the
 * popup decision has to be purely a function of versions and the reader's
 * role, never of when the check ran:
 *
 *  * a build **below** the published `versionCode` is offered the popup;
 *  * a build **on or past** the published `versionCode` (the latest release,
 *    or one installed from a later APK) never sees it — the published entry
 *    simply no longer concerns them;
 *  * the DEVELOPER account never sees it, so publishing can never lock the
 *    publisher out of the console that pushes updates;
 *  * an optional (`mandatory = false`) update that the user already dismissed
 *    stays dismissed until a *newer* versionCode is published; a mandatory one
 *    cannot be dismissed.
 *
 * Kept free of Android types so the rule is unit-testable on the JVM.
 */
object UpdateGate {

    /**
     * @param currentVersionCode `BuildConfig.VERSION_CODE` of the running app.
     * @param publishedVersionCode `versionCode` carried by the pushed update.
     * @param isDeveloper true for the DEVELOPER role (never shown).
     * @param skippedVersionCode versionCode the user pressed "Nanti Saja" on
     *        (`-1L` when nothing was ever skipped).
     * @param mandatory whether the pushed update is mandatory.
     */
    fun shouldShow(
        currentVersionCode: Long,
        publishedVersionCode: Long,
        isDeveloper: Boolean,
        skippedVersionCode: Long,
        mandatory: Boolean
    ): Boolean {
        if (isDeveloper) return false
        // The heart of the rule: never offer what this build already has.
        if (publishedVersionCode <= currentVersionCode) return false
        return mandatory || skippedVersionCode != publishedVersionCode
    }

    /** True when [publishedVersionCode] is strictly newer than this build. */
    fun isNewer(currentVersionCode: Long, publishedVersionCode: Long): Boolean =
        publishedVersionCode > currentVersionCode

    /**
     * Whether the dev console may publish [versionCode] over the update entry
     * already on the server.
     *
     * The baseline is the **published** entry, never `BuildConfig` — the
     * developer pushes the very build she is holding, so "must be higher than
     * what runs on this phone" would make publishing her own build impossible.
     *
     * * `publishedVersionCode == null` → anything positive passes (first push);
     * * `versionCode == published` → **allowed**: republishing the same build
     *   with a new URL / note / mandatory flag is a legitimate correction;
     * * `versionCode > published` → the normal new-version push;
     * * `versionCode < published` → refused: that would walk the update
     *   channel backwards for everyone below it.
     */
    fun publishAllowed(versionCode: Long, publishedVersionCode: Long?): Boolean =
        publishedVersionCode == null || versionCode >= publishedVersionCode
}
