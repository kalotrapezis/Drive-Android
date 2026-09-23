package com.kalotrapezis.drive

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URL
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * The real thing on a real device: the keystore's own certificate serving TLS, the tiny HTTP parser, and a
 * pairing that hands over both halves. Only a device can run this — the certificate comes from AndroidKeyStore.
 */
@RunWith(AndroidJUnit4::class)
class SyncServerDeviceTest {
    private fun post(port: Int, fingerprint: String, body: JSONObject): Pair<Int, JSONObject> {
        val pinned = object : X509TrustManager {
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                val actual = SyncRules.hex(MessageDigest.getInstance("SHA-256").digest(chain.first().encoded))
                if (actual != fingerprint) throw CertificateException("not the device that showed the code")
            }
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(pinned), null) }
        val c = (URL("https://127.0.0.1:$port/pair").openConnection() as HttpsURLConnection).apply {
            sslSocketFactory = tls.socketFactory
            hostnameVerifier = HostnameVerifier { _, _ -> true }
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 4_000
            readTimeout = 10_000
            setRequestProperty("Content-Type", "application/json")
        }
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val text = (if (c.responseCode in 200..299) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
        return c.responseCode to JSONObject(text)
    }

    @Test
    fun aDeviceCanBeScannedAndRemembersWhoScannedIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SyncStore(context)
        val server = SyncServer(context, store)
        server.start()
        try {
            val qr = SyncRules.parseQr(server.pairingQr(server.startPairing()))
            assertNotNull("the code it shows is a code it could scan", qr)
            assertEquals(server.fingerprint, qr!!.fingerprint)

            val theirFingerprint = "b".repeat(64)
            store.forgetPeer(theirFingerprint)
            val ourToken = "c".repeat(64)
            val (status, answer) = post(server.port, server.fingerprint, JSONObject()
                .put("code", qr.code).put("name", "Tablet").put("fp", theirFingerprint)
                .put("port", 43180).put("token", ourToken))
            assertEquals(200, status)
            assertTrue("it hands back a token to call it with", answer.getString("token").length >= 32)
            assertEquals(server.fingerprint, answer.getString("fp"))

            val peer = store.peers().first { it.fingerprint == theirFingerprint }
            assertEquals("Tablet", peer.name)
            assertEquals("the token we send when we call them", ourToken, peer.token)
            assertEquals("the token they send when they call us", answer.getString("token"), peer.theirToken)

            val (again, _) = post(server.port, server.fingerprint, JSONObject().put("code", qr.code).put("name", "Tablet"))
            assertEquals("a code works once", 200, again) // answered, but with an error rather than a token
            store.forgetPeer(theirFingerprint)
        } finally {
            server.stop()
        }
    }
}
