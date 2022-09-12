import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

object Main {

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size != 1) {
            System.err.println("Use ./javamailteste run")
            exitProcess(1)
        }
        when (args[0]) {
            "run" -> runServer(args.getOrElse(1) { "2500" }.toInt())
        }
    }

    fun runServer(port: Int) {
        val mailServer = MailServer("nea89.moe")
        runBlocking { mailServer.createServer(port) }
    }
}
