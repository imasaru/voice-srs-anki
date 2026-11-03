package com.evan.voicesrs

import android.content.Intent
import android.net.Uri

/**
 * Deep link to open a specific card in the AnkiDroid browser.
 *
 * AnkiDroid exports the `anki://x-callback-url/browser?search=cid:<cardId>` URI, which opens the
 * card browser filtered to that card. We never ship AnkiDroid as a dependency; we just launch it
 * with [intentFor] and let the system resolve it (falling back when AnkiDroid is absent).
 */
object AnkiDroidLink {

    const val PACKAGE = "com.ichi2.anki"

    fun uriFor(cardId: Long): Uri = Uri.Builder()
        .scheme("anki")
        .authority("x-callback-url")
        .path("browser")
        .appendQueryParameter("search", "cid:$cardId")
        .build()

    fun intentFor(cardId: Long): Intent = Intent(Intent.ACTION_VIEW, uriFor(cardId))
        .setPackage(PACKAGE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
