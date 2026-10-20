package com.biocatch.poc.scanningbackground.network

import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Wraps a delegate factory and turns TLS 1.1/1.2 on for every socket it creates.
 *
 * Before API 22, TLS 1.2 is supported but not reliably enabled by default, so a plain
 * HttpURLConnection to a TLS-1.2-only host can fail the handshake. SSLv3 is left off.
 */
class Tls12SocketFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {

    private val enabledProtocols = arrayOf("TLSv1", "TLSv1.1", "TLSv1.2")

    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites

    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket =
        enableTls12(delegate.createSocket(s, host, port, autoClose))

    override fun createSocket(host: String?, port: Int): Socket =
        enableTls12(delegate.createSocket(host, port))

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
        enableTls12(delegate.createSocket(host, port, localHost, localPort))

    override fun createSocket(host: InetAddress?, port: Int): Socket =
        enableTls12(delegate.createSocket(host, port))

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        enableTls12(delegate.createSocket(address, port, localAddress, localPort))

    private fun enableTls12(socket: Socket): Socket {
        if (socket is SSLSocket) {
            val supported = socket.supportedProtocols.toSet()
            socket.enabledProtocols = enabledProtocols.filter { it in supported }.toTypedArray()
        }
        return socket
    }
}
