package com.subu1979.imagesender.share

sealed interface ShareResult {
    data object Success : ShareResult

    /** No supported WhatsApp package is installed. */
    data object NotInstalled : ShareResult

    /** The target is installed but refused or could not handle the intent. */
    data object LaunchFailed : ShareResult

    /** The image URI could not be handed over; a retry with an app-owned copy is worthwhile. */
    data object UriNotGrantable : ShareResult
}
