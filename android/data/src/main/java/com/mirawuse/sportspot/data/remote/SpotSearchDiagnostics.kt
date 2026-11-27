package com.mirawuse.sportspot.data.remote

import com.mirawuse.sportspot.data.remote.error.NetworkError
import com.mirawuse.sportspot.data.remote.error.NetworkException
import kotlinx.coroutines.CancellationException
import okhttp3.Call
import okhttp3.EventListener
import java.io.IOException
import java.util.concurrent.TimeUnit

class SpotSearchDiagnostics(
    private val logger: NetworkLogger? = null,
) {
    internal suspend fun <T> stage(
        name: String,
        block: suspend () -> T,
    ): T {
        val log = logger ?: return block()
        val start = System.nanoTime()
        log.log("$name.start")
        try {
            return block().also { log.log("$name.ok (${elapsedMillis(start)}ms)") }
        } catch (cancelled: CancellationException) {
            log.log("$name.cancelled (${elapsedMillis(start)}ms)")
            throw cancelled
        } catch (failure: Exception) {
            val error = (failure as? NetworkException)?.error
            val type = if (error is NetworkError.Http) "Http(${error.status})" else (error ?: failure).javaClass.simpleName
            val cause =
                generateSequence<Throwable>(failure) { it.cause }
                    .take(6)
                    .last()
                    .javaClass.simpleName
            log.log("$name.failed type=$type cause=$cause (${elapsedMillis(start)}ms)")
            throw failure
        }
    }
}

internal class SpotSearchBodyListener(
    private val path: String,
    private val logger: NetworkLogger,
) : EventListener() {
    private val start = System.nanoTime()

    override fun callStart(call: Call) {
        logger.log("wire.start $path")
    }

    override fun responseBodyStart(call: Call) {
        logger.log("wire.body.start $path (${elapsedMillis(start)}ms)")
    }

    override fun responseBodyEnd(
        call: Call,
        byteCount: Long,
    ) {
        logger.log("wire.body.end $path bytes=$byteCount (${elapsedMillis(start)}ms)")
    }

    override fun callFailed(
        call: Call,
        ioe: IOException,
    ) {
        logger.log("wire.failed $path cause=${ioe.javaClass.simpleName} (${elapsedMillis(start)}ms)")
    }
}

private fun elapsedMillis(start: Long): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
