package com.localcut.editor.mobile

import android.content.ClipData
import android.content.Intent
import android.net.Uri

/** Ordered, read-only media handoff; does not upload or publish anything. */
internal object TikTokShareIntentFactory {
    fun create(uris: List<Uri>): Intent {
        require(uris.size in 2..TikTokCarouselPolicy.MAX_PAGES)
        require(uris.all { it.scheme == "content" })
        val clip = ClipData.newRawUri("LocalCut carousel", uris.first())
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
