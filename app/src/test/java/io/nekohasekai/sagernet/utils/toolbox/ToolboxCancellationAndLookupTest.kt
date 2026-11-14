package io.nekohasekai.sagernet.utils.toolbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class ToolboxCancellationAndLookupTest {

    @Test
    fun queryUdpDecodesLocalLoopbackDnsResponse() = runBlocking {
        DatagramSocket(0, InetAddress.getLoopbackAddress()).use { server ->
            val port = server.localPort
            val responder = thread(isDaemon = true) {
                val buf = ByteArray(1024)
                val dp = DatagramPacket(buf, buf.size)
                server.receive(dp)
                val questionEnd = dp.length
                val out = ByteArrayOutputStream()
                // Echo txId
                out.write(buf[0].toInt() and 0xFF)
                out.write(buf[1].toInt() and 0xFF)
                out.write(0x81); out.write(0x80) // standard response, NOERROR
                out.write(0x00); out.write(0x01) // QDCOUNT=1
                out.write(0x00); out.write(0x01) // ANCOUNT=1
                out.write(0x00); out.write(0x00) // NSCOUNT=0
                out.write(0x00); out.write(0x00) // ARCOUNT=0
                out.write(buf, 12, questionEnd - 12)
                // Compressed pointer answer: 93.184.216.34
                out.write(0xC0); out.write(0x0C)
                out.write(0x00); out.write(0x01)
                out.write(0x00); out.write(0x01)
                out.write(0x00); out.write(0x00); out.write(0x00); out.write(0x3C)
                out.write(0x00); out.write(0x04)
                out.write(93); out.write(184); out.write(216); out.write(34)
                val resp = out.toByteArray()
                server.send(DatagramPacket(resp, resp.size, dp.address, dp.port))
            }

            val result = DnsLookupEngine.queryUdp(
                label = "Loopback UDP",
                host = "127.0.0.1",
                port = port,
                domain = "example.com",
                recordType = DnsRecordType.A,
                viaProxy = true,
                connectivity = null,
            )
            responder.join(2_000)

            assertEquals("NOERROR", result.rcodeName)
            assertEquals("", result.error)
            assertEquals(1, result.answers.size)
            assertEquals("93.184.216.34", result.answers[0].value)
        }
    }

    @Test
    fun queryUdpCancelsImmediatelyOnJobCancel() = runBlocking {
        DatagramSocket(0, InetAddress.getLoopbackAddress()).use { silentServer ->
            val port = silentServer.localPort
            val job = launch(Dispatchers.IO) {
                DnsLookupEngine.queryUdp(
                    label = "Silent UDP",
                    host = "127.0.0.1",
                    port = port,
                    domain = "example.com",
                    recordType = DnsRecordType.A,
                    viaProxy = true,
                    connectivity = null,
                )
            }
            delay(100)
            val startMs = System.currentTimeMillis()
            job.cancelAndJoin()
            val cancelMs = System.currentTimeMillis() - startMs

            assertTrue("Expected cancel before 5s UDP timeout, took ${cancelMs}ms", cancelMs < 1_500L)
            assertTrue(job.isCancelled)
        }
    }

    @Test
    fun executeCancellableCancelsHttpCallImmediatelyOnJobCancel() = runBlocking {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val port = server.localPort
            val acceptThread = thread(isDaemon = true) {
                runCatching {
                    server.accept().use { sock ->
                        Thread.sleep(5_000)
                    }
                }
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            try {
                val job = launch(Dispatchers.IO) {
                    val req = Request.Builder().url("http://127.0.0.1:$port/slow").build()
                    client.newCall(req).executeCancellable { resp ->
                        resp.body?.string()
                    }
                }
                delay(100)
                val startMs = System.currentTimeMillis()
                job.cancelAndJoin()
                val cancelMs = System.currentTimeMillis() - startMs

                assertTrue("Expected cancel before 10s read timeout, took ${cancelMs}ms", cancelMs < 1_500L)
                assertTrue(job.isCancelled)
            } finally {
                acceptThread.interrupt()
                client.dispatcher.executorService.shutdownNow()
                client.connectionPool.evictAll()
            }
        }
    }

    @Test
    fun parseTraceLocExtractsCountryOrBlank() {
        assertEquals("DE", ConnectivityChecker.parseTraceLoc("fl=12f\nh=cloudflare.com\nloc=de\n").uppercase())
        assertEquals("", ConnectivityChecker.parseTraceLoc("fl=12f\nh=cloudflare.com\n"))
    }

    @Test
    fun withImmediateCancelPreservesNonCancellationException() = runBlocking {
        var cancelledCallbackRan = false
        var caught: IllegalStateException? = null
        try {
            withImmediateCancel(onCancel = { cancelledCallbackRan = true }) {
                throw IllegalStateException("boom")
            }
        } catch (e: IllegalStateException) {
            caught = e
        }
        assertEquals("boom", caught?.message)
        assertTrue(!cancelledCallbackRan)
    }
}
