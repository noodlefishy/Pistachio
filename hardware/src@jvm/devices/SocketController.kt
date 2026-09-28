package devices

import io.cuttlefish.components.PhysicalMemory
import io.cuttlefish.devices.Device
import java.io.InputStream
import java.io.OutputStream
import java.net.*
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue

class SocketController(private val ram: PhysicalMemory) : Device {
    override val name: String = "Socket Controller"
    override val deviceId: UShort = 8u
    override val memoryUsed: UIntRange = 0xFF70u..0xFF78u

    // --- State Constants ---
    companion object {
        const val STAT_CLOSED = 0
        const val STAT_INIT = 1
        const val STAT_LISTEN = 2
        const val STAT_ESTABLISHED = 3
        const val STAT_CLOSE_WAIT = 4
        const val STAT_UDP_READY = 5
    }

    // --- Hardware Registers ---
    private var sockStat: Short = STAT_CLOSED.toShort()
    private var sockProto: Short = 0 // 0=TCP Byte, 1=UDP Byte, 2=TCP Word, 3=UDP Word
    private var localPort: Int = 9000
    private var dipHi: Int = 0       // IP Octets 0 & 1 (or 0 for Domain Pointer mode)
    private var dipLo: Int = 0       // IP Octets 2 & 3 (or RAM pointer to hostname string)
    private var destPort: Int = 80

    // Queues
    private val txBuffer = mutableListOf<Short>()
    private val rxQueue = ConcurrentLinkedQueue<Short>()

    // Network Handles
    private var tcpSocket: Socket? = null
    private var serverSocket: ServerSocket? = null
    private var udpSocket: DatagramSocket? = null
    private var tcpIn: InputStream? = null
    private var tcpOut: OutputStream? = null

    @Volatile
    private var isListening = false

    override suspend fun read(address: UShort): Short {
        return when (address.toInt()) {
            0xFF70 -> 0 // Write-only Command
            0xFF71 -> sockStat
            0xFF72 -> sockProto
            0xFF73 -> localPort.toShort()
            0xFF74 -> dipHi.toShort()
            0xFF75 -> dipLo.toShort()
            0xFF76 -> destPort.toShort()
            0xFF77 -> rxQueue.poll() ?: 0 // Pop from incoming FIFO
            0xFF78 -> rxQueue.size.coerceAtMost(65535).toShort()
            else -> 0
        }
    }

    override suspend fun write(address: UShort, value: Short) {
        val valInt = value.toInt() and 0xFFFF
        when (address.toInt()) {
            0xFF70 -> executeCommand(valInt)
            0xFF72 -> sockProto = value
            0xFF73 -> localPort = valInt
            0xFF74 -> dipHi = valInt
            0xFF75 -> dipLo = valInt
            0xFF76 -> destPort = valInt
            0xFF77 -> txBuffer.add(value) // Push to outgoing FIFO
        }
    }

    private fun executeCommand(cmd: Int) {
        when (cmd) {
            1 -> cmdOpen()
            2 -> cmdListen()
            3 -> cmdConnect()
            4 -> cmdSend()
            5 -> cmdDisconnect()
            6 -> cmdClose()
        }
    }

    private fun cmdOpen() {
        cmdClose() // Ensure clean state
        rxQueue.clear()
        txBuffer.clear()

        val isUdp = (sockProto.toInt() and 1) != 0

        if (isUdp) {
            try {
                udpSocket = DatagramSocket(localPort)
                sockStat = STAT_UDP_READY.toShort()
                startUdpReceiver()
            } catch (e: Exception) {
                sockStat = STAT_CLOSED.toShort()
            }
        } else {
            // TCP starts in INIT state, awaiting LISTEN or CONNECT
            sockStat = STAT_INIT.toShort()
        }
    }

    private fun cmdListen() {
        if (sockStat.toInt() != STAT_INIT) return
        try {
            serverSocket = ServerSocket(localPort)
            sockStat = STAT_LISTEN.toShort()
            isListening = true

            Thread {
                try {
                    val client = serverSocket!!.accept()
                    tcpSocket = client
                    tcpIn = client.getInputStream()
                    tcpOut = client.getOutputStream()
                    sockStat = STAT_ESTABLISHED.toShort()
                    startTcpReceiver()
                } catch (e: Exception) {
                    if (isListening) sockStat = STAT_CLOSED.toShort()
                }
            }.apply { isDaemon = true; start() }

        } catch (e: Exception) {
            sockStat = STAT_CLOSED.toShort()
        }
    }

    private fun cmdConnect() {
        if (sockStat.toInt() != STAT_INIT) return

        Thread {
            try {
                val host = resolveDestinationHost()
                val socket = Socket(host, destPort)
                tcpSocket = socket
                tcpIn = socket.getInputStream()
                tcpOut = socket.getOutputStream()
                sockStat = STAT_ESTABLISHED.toShort()
                startTcpReceiver()
            } catch (e: Exception) {
                sockStat = STAT_CLOSED.toShort()
            }
        }.apply { isDaemon = true; start() }
    }

    private fun cmdSend() {
        if (txBuffer.isEmpty()) return
        val isUdp = (sockProto.toInt() and 1) != 0
        val isWordMode = (sockProto.toInt() and 2) != 0

        val payload = serializeTxBuffer(isWordMode)
        txBuffer.clear()

        if (isUdp) {
            val sock = udpSocket ?: return
            Thread {
                try {
                    val addr = InetAddress.getByName(resolveDestinationHost())
                    val packet = DatagramPacket(payload, payload.size, addr, destPort)
                    sock.send(packet)
                } catch (_: Exception) {}
            }.start()
        } else {
            val out = tcpOut ?: return
            Thread {
                try {
                    out.write(payload)
                    out.flush()
                } catch (e: Exception) {
                    sockStat = STAT_CLOSE_WAIT.toShort()
                }
            }.start()
        }
    }

    private fun cmdDisconnect() {
        sockStat = STAT_CLOSED.toShort()
        try {
            tcpOut?.flush()
            tcpSocket?.close()
        } catch (_: Exception) {}
    }

    private fun cmdClose() {
        isListening = false
        sockStat = STAT_CLOSED.toShort()
        try { tcpSocket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        try { udpSocket?.close() } catch (_: Exception) {}
        tcpSocket = null
        serverSocket = null
        udpSocket = null
    }

    // --- Helpers ---

    private fun resolveDestinationHost(): String {
        return if (dipHi == 0) {
            // POINTER MODE: Read null-terminated ASCII string directly from RAM!
            val sb = StringBuilder()
            var ptr = dipLo and 0xFFFF
            while (ptr < ram.internals.size) {
                val word = ram.internals[ptr].toInt() and 0xFFFF
                if (word == 0) break
                sb.append(word.toChar())
                ptr++
            }
            sb.toString()
        } else {
            // RAW IP MODE: Decode IPv4 octets
            val b0 = (dipHi shr 8) and 0xFF
            val b1 = dipHi and 0xFF
            val b2 = (dipLo shr 8) and 0xFF
            val b3 = dipLo and 0xFF
            "$b0.$b1.$b2.$b3"
        }
    }

    private fun serializeTxBuffer(isWordMode: Boolean): ByteArray {
        return if (isWordMode) {
            val bb = ByteBuffer.allocate(txBuffer.size * 2)
            for (w in txBuffer) bb.putShort(w)
            bb.array()
        } else {
            // Byte Mode: Stream lower 8 bits (ASCII)
            val bytes = ByteArray(txBuffer.size)
            for (i in txBuffer.indices) bytes[i] = (txBuffer[i].toInt() and 0xFF).toByte()
            bytes
        }
    }

    private fun startTcpReceiver() {
        Thread {
            val inStream = tcpIn ?: return@Thread
            val isWordMode = (sockProto.toInt() and 2) != 0
            val buf = ByteArray(1024)

            try {
                while (sockStat.toInt() == STAT_ESTABLISHED) {
                    val readBytes = inStream.read(buf)
                    if (readBytes == -1) {
                        sockStat = STAT_CLOSE_WAIT.toShort()
                        break
                    }
                    enqueueReceivedBytes(buf, readBytes, isWordMode)
                }
            } catch (e: Exception) {
                sockStat = STAT_CLOSED.toShort()
            }
        }.apply { isDaemon = true; start() }
    }

    private fun startUdpReceiver() {
        Thread {
            val sock = udpSocket ?: return@Thread
            val isWordMode = (sockProto.toInt() and 2) != 0
            val buf = ByteArray(2048)

            while (sockStat.toInt() == STAT_UDP_READY) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    sock.receive(packet)
                    enqueueReceivedBytes(packet.data, packet.length, isWordMode)
                } catch (e: Exception) {
                    break
                }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun enqueueReceivedBytes(data: ByteArray, length: Int, isWordMode: Boolean) {
        if (isWordMode) {
            val bb = ByteBuffer.wrap(data, 0, length)
            while (bb.remaining() >= 2) {
                rxQueue.add(bb.short)
            }
        } else {
            for (i in 0 until length) {
                rxQueue.add((data[i].toInt() and 0xFF).toShort())
            }
        }
    }
}