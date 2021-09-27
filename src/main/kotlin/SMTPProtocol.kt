import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class Invalidatable {
    var isInvalid: Boolean = false
    fun checkValid() {
        if (isInvalid)
            throw IllegalStateException("Accessed invalid object")
    }

    fun invalidate() {
        isInvalid = true
    }
}

abstract class Protocol {
    interface IO {
        fun isOpen(): Boolean
        suspend fun pushBack(data: ByteArray)
        suspend fun readBytes(into: ByteArray): Int
        suspend fun send(bytes: ByteArray)
        suspend fun close()
        class FromSocket(val socket: Socket) : FromStreams(socket.getInputStream(), socket.getOutputStream()) {
            override suspend fun close() {
                super.close()
                with(Dispatchers.IO) {
                    socket.close()
                }
            }
        }

        open class FromStreams(val inputStream: InputStream, val outputStream: OutputStream) : IO {
            private val i = Invalidatable()
            override fun isOpen(): Boolean =
                !i.isInvalid


            val readBuffer = mutableListOf<ByteArray>()
            override suspend fun pushBack(data: ByteArray) {
                i.checkValid()
                if (data.isEmpty()) return
                readBuffer.add(0, data)
            }

            override suspend fun send(bytes: ByteArray) {
                i.checkValid()
                with(Dispatchers.IO) {
                    outputStream.write(bytes)
                    outputStream.flush()
                }
            }

            override suspend fun close() {
                i.checkValid()
                i.invalidate()
                with(Dispatchers.IO) {
                    inputStream.close()
                    outputStream.close()
                }
            }

            override suspend fun readBytes(into: ByteArray): Int {
                i.checkValid()
                val rb = readBuffer.removeFirstOrNull()
                if (rb != null) {
                    val w = minOf(rb.size, into.size)
                    rb.copyInto(into, 0, 0, w)
                    return w
                }
                return with(Dispatchers.IO) {
                    inputStream.read(into)
                }
            }
        }
    }

    protected abstract suspend fun IO.execute()

    fun executeAsync(scope: CoroutineScope, io: Protocol.IO): Job {
        return scope.launch {
            io.execute()
        }
    }
}

suspend fun Protocol.IO.send(string: String) = send(string.encodeToByteArray())
suspend fun Protocol.IO.readLine(): String {
    val y = mutableListOf<String>()
    while (true) {
        val buffer = ByteArray(4096)
        val read = readBytes(buffer)
        val i = buffer.findCRLF()
        if (i in 0 until read) {
            y.add(buffer.copyOfRange(0, i).decodeToString())
            pushBack(buffer.copyOfRange(i + 2, read))
            break
        } else {
            y.add(buffer.copyOfRange(0, read).decodeToString())
        }
    }
    return y.joinToString("")
}

private fun ByteArray.findCRLF(): Int {
    return this.asSequence().zipWithNext().withIndex().firstOrNull { (idx, v) ->
        (v.first == '\r'.code.toByte()) and (v.second == '\n'.code.toByte())
    }?.index ?: -1
}

suspend fun Protocol.IO.pushBack(string: String) = pushBack(string.encodeToByteArray())
suspend fun Protocol.IO.lookahead(string: String): Boolean = lookahead(string.encodeToByteArray())
suspend fun Protocol.IO.lookahead(bytes: ByteArray): Boolean {
    val buffer = ByteArray(bytes.size)
    val read = readBytes(buffer)
    if (read != bytes.size || !buffer.contentEquals(bytes)) {
        pushBack(buffer.copyOf(read))
        return false
    }
    return true
}


@OptIn(ExperimentalContracts::class)
class SMTPReceiveProtocol(val localHost: String, val inetAddress: InetAddress) : Protocol() {

    class Commands(val line: String, private val io: IO) : IO by io {
        var matched = false


        suspend inline fun command(vararg name: String, block: IO.(String) -> Unit) {
            contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
            for (n in name) commandOnce(n, block)
        }

        suspend inline fun commandOnce(name: String, block: IO.(String) -> Unit) {
            contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
            if (matched) return
            if (!line.startsWith(name)) return
            matched = true
            block(line.substring(name.length).trimStart())
        }

        suspend inline fun otherwise(block: IO.(String) -> Unit) {
            contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
            if (matched) return
            matched = true
            block(line)
        }
    }

    suspend inline fun IO.commands(line: String, block: Commands.() -> Unit) {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        Commands(line, this).block()
    }

    override suspend fun IO.execute() {
        send("220 $localHost\r\n")
        var isHelod = false
        var receipient: String? = null
        var sender: String? = null
        var text: String? = null
        while (isOpen()) {
            commands(readLine()) {
                println(line)
                command("HELO", "EHLO") {
                    send("250 Hello $it, how are you on this fine day?\r\n")
                    isHelod = true
                }
                command("MAIL FROM:") {
                    send("250 Sender ok\r\n")
                    sender = it
                }
                command("RCPT TO:") {
                    send("250 Receipient ok\r\n")
                    receipient = it
                }
                command("DATA") {
                    send("354 Enter mail, end with \".\" on a line by itself\r\n")
                    text = ""
                    while (true) {
                        val tmp = readLine()
                        if (tmp == ".") break
                        text += tmp + "\r\n"
                    }
                    send("250 Message accepted for delivery\r\n")
                }
                command("QUIT") {
                    send("221 $localHost closing connection\r\n")
                    close()
                }
                otherwise {
                    send("XXX ERROR UNKNOWN CODE $it\r\n")
                }
            }
        }
        println("IsHelod: $isHelod")
        println("From: $sender")
        println("To: $receipient")
        println("\n$text")
    }
}
