package com.subu1979.imagesender.share

sealed interface ShareResult {
    data object Success : ShareResult

    /** No supported WhatsApp package is installed. */
    data object NotInstalled : ShareResult

    /** The target is installed but refused or could not handle the intent. */
    data object LaunchFailed : ShareResult
}
