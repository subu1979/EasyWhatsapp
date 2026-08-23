package com.subu1979.imagesender.share

/** The two supported share targets (FR-06). Only public package names are used. */
enum class WhatsAppApp(val packageName: String, val label: String) {
    STANDARD("com.whatsapp", "WhatsApp"),
    BUSINESS("com.whatsapp.w4b", "WhatsApp Business")
}
