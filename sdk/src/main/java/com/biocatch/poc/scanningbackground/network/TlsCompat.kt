package com.biocatch.poc.scanningbackground.network

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import com.biocatch.poc.scanningbackground.sdk.R
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Makes HTTPS to Let's Encrypt hosts (webhook.site) work on old Android.
 *
 * - API < 25 (before 7.1.1) does not ship ISRG Root X1, so the chain does not validate.
 *   We trust the bundled public ISRG roots in addition to the system store.
 * - API < 22 may have TLS 1.2 off by default, see [Tls12SocketFactory].
 *
 * Returns null on API 25+, where the platform defaults already work and nothing is overridden.
 */
object TlsCompat {

    private val bundledRoots = intArrayOf(R.raw.isrg_root_x1, R.raw.isrg_root_x2)

    fun socketFactoryOrNull(context: Context): SSLSocketFactory? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) return null

        val trustManager = CompositeTrustManager(systemTrustManager(), bundledTrustManager(context))
        val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
        val factory = sslContext.socketFactory
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) Tls12SocketFactory(factory) else factory
    }

    private fun systemTrustManager(): X509TrustManager = trustManagerFor(null)

    private fun bundledTrustManager(context: Context): X509TrustManager {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
        bundledRoots.forEachIndexed { index, resId ->
            context.resources.openRawResource(resId).use { input ->
                keyStore.setCertificateEntry("bundled-root-$index", certificateFactory.generateCertificate(input))
            }
        }
        return trustManagerFor(keyStore)
    }

    private fun trustManagerFor(keyStore: KeyStore?): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    /** Accepts a chain the system store trusts, or failing that one rooted in a bundled ISRG root. */
    @SuppressLint("CustomX509TrustManager")
    private class CompositeTrustManager(
        private val primary: X509TrustManager,
        private val fallback: X509TrustManager,
    ) : X509TrustManager {

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
            primary.checkClientTrusted(chain, authType)

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            try {
                primary.checkServerTrusted(chain, authType)
            } catch (systemFailure: CertificateException) {
                fallback.checkServerTrusted(chain, authType)
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            primary.acceptedIssuers + fallback.acceptedIssuers
    }
}
