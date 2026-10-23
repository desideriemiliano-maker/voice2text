package com.desideri.familybalance

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle

/**
 * Riceve i file condivisi da altre app (Condividi › FamilyBalance) senza mostrare nulla e li passa
 * alla MainActivity già aperta, nel suo task: così non si apre una seconda istanza dell'app dentro
 * l'app che condivide.
 */
class CondivisioneActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        if (uri != null) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(Intent.ACTION_SEND)
                    .setType(intent.type)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    // Il permesso di lettura passa alla MainActivity insieme all'uri.
                    .also { it.clipData = ClipData.newRawUri("", uri) }
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
            )
        }
        finish()
    }
}
