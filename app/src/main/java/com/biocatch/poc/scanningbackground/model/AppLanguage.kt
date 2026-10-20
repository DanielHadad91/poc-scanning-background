package com.biocatch.poc.scanningbackground.model

/** UI language choice. SYSTEM follows the device; the other two force Hebrew or English. */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    HEBREW("iw"),
    ENGLISH("en"),
}
