import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.filter.ClientFilters.SetBaseUriFrom

// interface
interface Action<R> {
    fun toRequest(): Request
    fun fromResponse(response: Response): R
}

// inteface
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
