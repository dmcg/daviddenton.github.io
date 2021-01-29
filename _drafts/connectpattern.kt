import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.filter.ClientFilters.SetBaseUriFrom
import org.http4k.filter.ServerFilters.BearerAuth
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Netty
import org.http4k.server.asServer

fun MySecureApp(): HttpHandler =
    BearerAuth("")
        .then(
            routes(
                echo(),
                reverse()
            )
        )

fun echo() = "/echo" bind POST to { req: Request -> Response(OK).body(req.bodyString()) }

fun reverse() = "/echo" bind POST to { req: Request -> Response(OK).body(req.bodyString().reversed()) }

val server = MySecureApp().asServer(Netty(8080)).start()

// interface
interface Action<R> {
    fun toRequest(): Request
    fun fromResponse(response: Response): R
}

// interface
interface GitHubApiAction<R> : Action<R>

interface GitHubApi {
    operator fun <R : Any> invoke(action: GitHubApiAction<R>): R

    companion object
}

// action/response
data class GetUser(val username: String) : GitHubApiAction<UserDetails> {
    override fun toRequest() = Request(GET, "/users/$username")
    override fun fromResponse(response: Response) = UserDetails(response.bodyString())
}

data class UserDetails(val userJson: String)

// adapter
fun GitHubApi.Companion.Http(client: HttpHandler) = object : GitHubApi {
    private val http = SetBaseUriFrom(Uri.of("https://api.github.com")).then(client)

    override fun <R : Any> invoke(action: GitHubApiAction<R>) = action.fromResponse(http(action.toRequest()))
}

// extension function - nicer API
fun GitHubApi.getUser(username: String) = GetUser(username)
