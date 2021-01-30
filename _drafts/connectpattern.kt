import org.http4k.client.JavaHttpClient
import org.http4k.client.OkHttp
import org.http4k.core.Filter
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

object before {
    fun MySecureApp(): HttpHandler =
        BearerAuth("")
            .then(
                routes(
                    echo(),
                    health()
                )
            )

    fun echo() = "/echo" bind POST to { req: Request -> Response(OK).body(req.bodyString()) }

    fun health() = "/health" bind GET to { req: Request -> Response(OK).body("alive!") }

    val server = MySecureApp().asServer(Netty(8080)).start()

    class GitHubApi(client: HttpHandler) {
        private val http = SetBaseUriFrom(Uri.of("https://api.github.com"))
            .then(SetHeader("Accept", "application/vnd.github.v3+json"))
            .then(client)

        fun getUser(username: String) = UserDetails(http(Request(GET, "/users/$username")).bodyString())

        fun getRepoLatestCommit(owner: String, repo: String): Commit = Commit(
            http(
                Request(GET, "/repos/$owner/$repo/commits").query("per_page", "1")
            ).bodyString()
        )
    }

    val github: GitHubApi = GitHubApi(OkHttp())
}

// interface
interface GitHubApiAction<R> {
    fun toRequest(): Request
    fun fromResponse(response: Response): R
}

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

data class GetRepoLatestCommit(val owner: String, val repo: String) : GitHubApiAction<Commit> {
    override fun toRequest() = Request(GET, "/repos/$owner/$repo/commits").query("per_page", "1")
    override fun fromResponse(response: Response) = Commit(response.bodyString())
}

data class Commit(val commitJson: String)

// adapter
fun GitHubApi.Companion.Http(client: HttpHandler) = object : GitHubApi {
    private val http = SetBaseUriFrom(Uri.of("https://api.github.com"))
        .then(SetHeader("Accept", "application/vnd.github.v3+json"))
        .then(client)

    override fun <R : Any> invoke(action: GitHubApiAction<R>) = action.fromResponse(http(action.toRequest()))
}

val github: GitHubApi = GitHubApi.Http(OkHttp())

// extension function - nicer API
fun GitHubApi.getUser(username: String) = invoke(GetUser(username))
fun GitHubApi.getLatestRepoCommit(owner: String, repo: String): Commit = invoke(GetRepoLatestCommit(owner, repo))

//fun GitHubApi.getLatestUser(org: String, repo: String) {
//    val commit = getLatestRepoCommit(org, repo)
//    return getUser(commit.author)
//}


fun SetHeader(name: String, value: String): Filter = TODO()
