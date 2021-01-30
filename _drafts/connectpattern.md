---
layout: post title: "Smash your Adapter Monolith with the Connect Pattern"
tags: [kotlin, fp, design]
comments: false thumbnail: assets/img/connectpattern.jpg
---

In which I describe a pattern for writing third party adapters in a modular and extensible way, hoping that it is
original enough for me to christen it.

<a title="Image by Steve Buissinne from Pixabay"
href="https://pixabay.com/users/stevepb-282134"><img width="512" alt="Dog.in.sleep" src="
../../../assets/img/connectpattern.jpg"></a>

> **"Breaking down remote adapters into individual Actions sharing a common single-function Protocol interface allows them to not only be manageable at the code layer, but also simplifies both testing and extensibility. Testing your system through the edges of the remote boundary gives you super powers."**

<hr/>

### rta

The main bulk of non-operationally focussed application code in a modern Server-based HTTP microservice can be broken
down into a few broad areas:

1. Inbound Server-side APIs, routing and unmarshalling of incoming requests
2. Business logic functions
3. Data-access querying and mutations
4. Adapter code for outbound remote API communication

#### Structuring our incoming APIs 
For 1 above - the Server-side, we tend to model the application as a set of separate HTTP entrypoint classes/functions which are composed into a whole to represent the incoming HTTP API, either explicitly or via some meta-programming such as annotations. So for example, using [http4k](https://http4k/org), we might create and start our server with:

```kotlin
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
```

In this case, the splitting up of the server-side API into separate functions allows us to maintain a decent grip on our
application as a whole and also to be able to easily test the various endpoints in the application independently of
the rest - ie. we don't need to provide a `Bearer` token to access our API calls if we have access to directly test `echo()` and `health()`.

Additionally, because we have modularised the code in this way, it is also reusable in other contexts - we can put common endpoint code such as `health()` into a shared location and use them across our fleet of microservices.

#### Structuring our outgoing APIs
When it comes to part 4 of the list above - adapter code for other remote APIs - we don't generally have a pattern in place to use the same structure. HTTP adapters to remote systems are usually constructed as monolithic interfaces with many methods, all built around a singularly configured HTTP adapter. Let's say we want to talk to the GitHub API, we would normally build an API adapter like so:

```kotlin
class GitHubApi(client: HttpHandler) {
    private val http = SetBaseUriFrom(Uri.of("https://api.github.com"))
        .then(SetHeader("Accept", "application/vnd.github.v3+json"))
        .then(client)

    fun getUser(username: String) = UserDetails(http(Request(GET, "/users/$username")).bodyString())

    fun getRepo(owner: String, repo: String): Repo = Repo(http(Request(GET, "/repos/$owner/$repo\"")).bodyString())
}
```

This is all quite sensible - there is a shared HTTP client which is configured to send requests to the API with the correct `Accept` header. Unfortunately though, as our usage of the API grows, so will the size of the `GitHubApi` class - it may gain many (10s or even 100s of individual) functions, all of which generally provide singular access to a single API call. We end up with a monolith object which can be thousands of lines long if left unchecked.

As there is generally no interaction between these functions - it would be desirable to structure the code in a similar way to how we structured our incoming API - in a modular, easily testable and reusable fashion. Even so, we also want to find a way to build functions which combine one or more calls to the API.

#### Introducing the Connect pattern
This is where the Connect pattern will help us. In essence, the pattern allows the splitting of an adapter monolith into individual Actions and a shared Protocol object which centralises the communication with the API. That's quite a lot to take in, so let's split it down and take a look.

The pattern itself has been created around the facilities available in the Kotlin language - most notably the use of interfaces and extension functions. Other languages may not have these exact same facilities, but the pattern should be adaptable (to greater or lesser effect).

The following explanation is based upon a simplified version of the [http4k-connect](https://github.com/http4k/http4k-connect) library, which we're using as the canonical implementation of the pattern. As the name implies, http4k-connect is itself built upon the (http4k)[https://http4k.org] HTTP toolkit, although there is nothing in the pattern to tie it to this 

#### Action
The fundamental unit of work in the Connect pattern is the `Action` interface, which represents a single interaction with the remote system, generified by the type of the return object `R`. Each action contains the state of the data that needs to be transmitted, and also how to marshall the data within the action to and from the underlying HTTP API. 

For our GitHubApi adapter, we create the superinterface and an implementation of an action to get a user from the API. Note that the Action and result `R` types are modelled as Kotlin data classes. This will give us advantages which we will cover later:
```kotlin
interface GitHubApiAction<R> {
    fun toRequest(): Request
    fun fromResponse(response: Response): R
}

data class GetUser(val username: String) : GitHubApiAction<UserDetails> {
    override fun toRequest() = Request(GET, "/users/$username")
    override fun fromResponse(response: Response) = UserDetails(response.bodyString())
}

data class UserDetails(val userJson: String)
```

#### Adapter
```kotlin
interface GitHubApi {
    operator fun <R : Any> invoke(action: GitHubApiAction<R>): R

    companion object
}

fun GitHubApi.Companion.Http(client: HttpHandler) = object : GitHubApi {
    private val http = SetBaseUriFrom(Uri.of("https://api.github.com"))
        .then(SetHeader("Accept", "application/vnd.github.v3+json"))
        .then(client)

    override fun <R : Any> invoke(action: GitHubApiAction<R>) = action.fromResponse(http(action.toRequest()))
}
```

#### Extension Methods
```kotlin
fun GitHubApi.getUser(username: String) = invoke(GetUser(username))
```

#### Composite Actions
```kotlin
fun GitHubApi.getLatestRepoCommit(owner: String, repo: String): Commit = invoke(GetRepoLatestCommit(owner, repo))

fun GitHubApi.getLatestUser(org: String, repo: String) {
    val commit = getLatestRepoCommit(org, repo)
    return getUser(commit.author)
}
```

<hr/>

### ps.

If you'd like to see an example of the Connect Pattern in
