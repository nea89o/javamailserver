import java.net.InetAddress
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


@OptIn(ExperimentalContracts::class)
class SMTPReceiveProtocol(val localHost: String, val inetAddress: InetAddress) : Protocol() {

    class Commands(val line: String, private val io: IO) : IO by io {
        var matched = false


        suspend inline fun command(vararg name: String, block: suspend IO.(String) -> Unit) {
            contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
            for (n in name) commandOnce(n, block)
        }

        suspend inline fun commandOnce(name: String, block: suspend IO.(String) -> Unit) {
            contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
            if (matched) return
            if (!line.startsWith(name, ignoreCase = true)) return
            matched = true
            block(line.substring(name.length).trimStart())
        }

        suspend inline fun otherwise(block: suspend IO.(String) -> Unit) {
            contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
            if (matched) return
            matched = true
            block(line)
        }
    }

    suspend inline fun IO.commands(line: String, block: suspend Commands.() -> Unit) {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        Commands(line, this).block()
    }

    data class Mail(val sender: String, val recipient: List<String>, val text: String)

    class MailTransaction(
        var isHelod: Boolean = false,
        var isEhlod: Boolean = false,
        var recipients: MutableList<String> = mutableListOf(),
        var sender: String? = null,
    ) {
        fun reset() {
            recipients = mutableListOf()
            sender = null
        }
    }

    override suspend fun IO.execute() {
        send("220 $localHost\r\n")
        val messages = mutableListOf<Mail>()
        val trans = MailTransaction()
        while (isOpen()) {
            commands(readLine()) {
                println(line)
                command("EHLO") {
                    send("250 hello advanced $it\r\n")
                    trans.isHelod = true
                    trans.isEhlod = true
                }
                command("HELO") {
                    send("250 Hello $it, how are you on this fine day?\r\n")
                    trans.isHelod = true
                }
                command("MAIL FROM:") {
                    send("250 Sender ok\r\n")
                    trans.sender = it
                }
                command("RCPT TO:") {
                    send("250 Receipient ok\r\n")
                    trans.recipients.add(it)
                }
                command("DATA") {
                    send("354 Enter mail, end with \".\" on a line by itself\r\n")
                    var text = readUntil("\r\n.\r\n".encodeToByteArray())
                    messages.add(Mail(trans.sender!!, trans.recipients.toList(), text.decodeToString()))
                    trans.reset()
                    send("250 Message accepted for delivery\r\n")
                }
                command("QUIT") {
                    send("221 $localHost closing connection\r\n")
                    close()
                }
                otherwise {
                    send("500 ERROR UNKNOWN CODE $it\r\n")
                }
            }
        }
        println("Got ${messages.size} messages")
        messages.forEach {
            println("Message:")
            println("MAIL FROM: ${it.sender}")
            println("RCPTS TO: ${it.recipient}")
            println("CONTENT: ${it.text}")
        }
    }
}
