package okhttp3
import java.io.Closeable
import java.util.concurrent.TimeUnit
class HttpUrl {
    companion object { fun String.toHttpUrl(): HttpUrl = HttpUrl() }
    fun newBuilder() = Builder()
    class Builder {
        fun addQueryParameter(name: String, value: String) = this
        fun build() = HttpUrl()
    }
}
class Request {
    class Builder { fun url(url: HttpUrl) = this; fun get() = this; fun build() = Request() }
}
object StubNetwork { var status = 429; var attempts = 0 }
class OkHttpClient {
    class Builder {
        fun connectTimeout(time: Long, unit: TimeUnit) = this
        fun readTimeout(time: Long, unit: TimeUnit) = this
        fun callTimeout(time: Long, unit: TimeUnit) = this
        fun build() = OkHttpClient()
    }
    fun newCall(request: Request) = Call()
}
class Call {
    fun execute(): Response { StubNetwork.attempts++; return Response(StubNetwork.status) }
}
class Response(val code: Int) : Closeable {
    val body: ResponseBody? = ResponseBody()
    val isSuccessful get() = code in 200..299
    fun header(name: String): String? = if (name == "Retry-After" && code == 429) "90" else null
    override fun close() {}
}
class ResponseBody { fun string() = "[]" }
