---
layout: post
title: "Smash your Adapter Monolith with the Connect pattern"
tags: [kotlin, fp, design]
comments: false
thumbnail: assets/img/smash-your-adapter-monolith.jpg
---

In which I describe a pattern for writing third party adapters in a modular and extensible way, hoping that it is
original enough for me to christen it. 🙃

<hr/>

### TL;DR
> **"Breaking down monolithic remote API adapters into individual Actions sharing a common single-function Protocol interface allows them to not only be more decoupled at the code layer, but also simplifies both testing and extensibility."**

<a title="Image by Steve Buissinne from Pixabay"
href="https://pixabay.com/users/stevepb-282134"><img class="article" alt="smash egg" src="
../../../assets/img/smash-your-adapter-monolith.jpg"></a>

<hr/>

*Reader Note: whilst this post is about a pattern that is not Kotlin-specific, it does demonstrate the pattern using code written in Kotlin. This uses various language features such as [Data classes](https://kotlinlang.org/docs/reference/data-classes.html), [Companion Objects](https://kotlinlang.org/docs/reference/object-declarations.html#companion-objects), [Operator overloading](hhttps://kotlinlang.org/docs/reference/operator-overloading.html) and [Extension functions](https://kotlinlang.org/docs/reference/extensions.html). It also uses the basics of the [http4k](https://www.http4k.org/rationale/) HTTP toolkit which might be worth referring to if unfamiliar with them.*

The main bulk of non-operationally focussed application code in a modern Server-based HTTP microservice can be broken
down into a few broad areas:

1. Inbound Server-side APIs, routing and unmarshalling of incoming requests
2. Business logic functions
3. Data-access querying and mutations
4. Adapter code for outbound remote API communication

#### Structuring our inbound APIs 
For 1) - the Server-side - we tend to model the application as a set of separate HTTP entrypoint classes/functions which are composed into a whole to represent the incoming HTTP API, either explicitly or via some meta-programming such as annotations. So for example, using http4k, we might create and start our server with:

```kotlin
fun MySecureApp(): HttpHandler =
    BearerAuth("my-very-secure-and-secret-bearer-token")
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
the rest - e.g. we don't need to provide a Bearer token to access our API calls if we have access to directly test `echo()` and `health()`.

Additionally, because we have modularised the code in this way, it is also reusable in other contexts - we can put common endpoint code such as `health()` into a shared location and reuse them across our fleet of microservices.

#### Structuring our outbound APIs
When it comes to **4)** of the list above - adapter code for other remote APIs - we don't generally have a pattern in place to use the same structure. HTTP adapters to remote systems are usually constructed as monolithic classes with many methods, all built around a singularly configured HTTP adapter. Let's say we want to talk to the GitHub API, we would normally build an API adapter like so:

```kotlin
class GitHubApi(client: HttpHandler) {
    private val http = SetBaseUriFrom(Uri.of("https://api.github.com"))
        .then(SetHeader("Accept", "application/vnd.github.v3+json"))
        .then(client)

    fun getUser(username: String): UserDetails {
        val response = http(Request(GET, "/users/$username"))
        return UserDetails(userNameFrom(response), userOrgsFrom(response))
    }

    fun getRepoLatestCommit(owner: String, repo: String) = Commit(
        authorFrom(http(Request(GET, "/repos/$owner/$repo/commits").query("per_page", "1")))
    )
}

val gitHub: GitHubApi = GitHubApi(OkHttp())
val user: UserDetails = gitHub.getUser("octocat")
```

This is all quite sensible - there is a shared HTTP client which is configured to send requests to the API with the correct `Accept` header. Unfortunately though, as our usage of the API grows, so will the size of the `GitHubApi` class - it may gain many (10s or even 100s of individual) functions, all of which generally provide singular access to a single API call. We end up with a monolith object which can be thousands of lines long if left unchecked.

As there is generally no interaction between these functions - it would be desirable to structure the code in a similar way to how we structured our inbound API - in a modular, easily testable and reusable fashion. Even so, we also want to find a way to build functions which combine one or more calls to the API.

#### Introducing the Connect pattern
This is where the Connect pattern will help us. In essence, Connect allows the splitting of an adapter monolith into individual Actions and a shared Protocol object which centralises the communication with the API. 

The pattern itself has been created around the features available in the Kotlin language - most notably the use of interfaces and extension functions. Other languages may not have these exact same facilities, but Connect should be adaptable (to greater or lesser effect). Let's split it down and take a look by reimplementing the example above.

The following explanation is based upon a simplified version of the [http4k-connect](https://github.com/http4k/http4k-connect) library, which we're using as the canonical implementation of the pattern. As the name implies, http4k-connect is itself built upon the [http4k](https://http4k.org) HTTP toolkit for it's core HTTP abstractions, although there is nothing in the pattern to tie it to this library (or even to the HTTP protocol).

#### Action
The fundamental unit of work in the Connect pattern is the `Action` interface, which represents a single interaction with the remote system, generified by the type of the return object `R`. Each action contains the state of the data that needs to be transmitted, and also how to marshall the data within the action to and from the underlying HTTP API. 

For our `GitHubApi` adapter, we create the superinterface and an implementation of an Action to get a user from the API along with the result type. Note that the Action and result types are modelled as Kotlin data classes - this will give us advantages which we will cover later:
```kotlin
interface GitHubApiAction<R> {
    fun toRequest(): Request
    fun fromResponse(response: Response): R
}

data class GetUser(val username: String) : GitHubApiAction<UserDetails> {
    override fun toRequest() = Request(GET, "/users/$username")
    override fun fromResponse(response: Response) = 
        UserDetails(userNameFrom(response), userOrgsFrom(response))
}

data class UserDetails(val name: String, val orgs: List<String>)
```

#### Adapter
The Adapter interface represents the common base protocol for interacting with the remote API - it will deal with server host location, authorisation and other headers, and perform the actual HTTP interactions. Each Adapter is modelled as a simple interface with a single generic method accepting the generic Action type.

Note here the presence of the Kotlin `companion object` - it is meant to be empty and is there precisely to give us a point to hook other code onto in a moment. This is to make life easier for the API user.

```kotlin
interface GitHubApi {
    operator fun <R : Any> invoke(action: GitHubApiAction<R>): R

    companion object
}
```

Our first usage of the companion object is to rewrite our previous version as an anonymous implementation of the `GitHubApi` and attach it to our Adapter, returned by the `Http()` factory function. All dependencies required by the Adapter are passed in here and closed over. Note that we explicitly pass in the HTTP client instead of constructing it inside the function  - access to this is critical if we want to be able to decorate the client Adapter with call logging or other operational concerns:

```kotlin
fun GitHubApi.Companion.Http(client: HttpHandler) = object : GitHubApi {
    private val http = SetBaseUriFrom(Uri.of("https://api.github.com"))
        .then(SetHeader("Accept", "application/vnd.github.v3+json"))
        .then(client)

    override fun <R : Any> invoke(action: GitHubApiAction<R>) = 
        action.fromResponse(http(action.toRequest()))
}
```

#### Using the adapter
Apart from the usage of the Companion Object as a hook, construction of our Adapter looks similar to the previous version - we have not exposed any more concrete types (there is still just `GitHubApi`). However, calling the API does look different - because of the operator function `invoke()`, we now treat the Server as a simple function which takes Action instances:

```kotlin
val gitHub: GitHubApi = GitHubApi.Http(OkHttp())

val user: UserDetails = gitHub(GetUser("octocat"))
```

This change may leave a slight bad taste in the mouth as the API is no longer as IDE discoverable. Luckily, Kotlin has another trick up it's sleeve here which will help us...

#### Extension Functions
We can get back our old API very simply by creating another extension function for each Action that mimics the signature of the Action itself and delegates to the `invoke()` call in the client:

```kotlin
fun GitHubApi.getUser(username: String) = invoke(GetUser(username))
fun GitHubApi.getRepoLatestCommit(GetRepoLatestCommit: String, repo: String): Commit = 
    invoke(GetRepoLatestCommit(owner, repo))

val user: UserDetails = gitHub.getUser("octocat")
```

Even better, for actions which consist more than one API call such as `getLatestUser()` below, we can just create more extension functions which delegate down to the individual actions. These functions can be added to `GitHubApi` instances at the global level, or just in the contexts or modules which make sense. The extension function effectively allow us to compose our own custom `GitHubApi` Adapter out of the individual Action parts that we are interested in:

```kotlin
fun GitHubApi.getLatestUser(owner: String, repo: String): UserDetails {
    val commit = getRepoLatestCommit(owner, repo)
    return getUser(commit.author)
}

val latestUser: UserDetails = gitHub.getLatestUser("http4k", "http4k-connect")
```

### Testing the Connect pattern
Both the Adapter and the modularisation of the various Action classes make it very easy to write unit tests for the action code created using the Connect pattern, but it's also important to consider how the API design will affect the testing of client code.

Fortunately, the simplicity of the single arity functional Adapter interface in concert with the Actions being implemented as Kotlin data classes (which are easily comparable) make testing as a client of Connect APIs trivial at multiple levels. Consider for instance if we are intending to mock a function which has seven parameters that we don't care about - in the previous implementation we would have to mock out each of those with a value (or an `any()` matcher), versus a single `any<Action>()` covering the Connect version:

```kotlin
@Test
fun `get user details`() {
    val githubApi = mockk<GitHubApi>()
    val userDetails = UserDetails("bob", listOf("http4k"))
    every { githubApi(any<GetUser>()) } returns userDetails

    assertThat(githubApi.getUser("bob"), equalTo(userDetails))
}
```

The Action object being a single parameter of the `invoke()` method also gives us the ability to easily decorate our Adapter instance for testing or other purposes, for instance we can record all of the incoming calls for any purpose we want:

```kotlin
class RecordingGitHubApi(private val delegate: GitHubApi) : GitHubApi {
    val recorded = mutableListOf<GitHubApiAction<*>>()
    override fun <R : Any> invoke(action: GitHubApiAction<R>): R {
        recorded += action
        return delegate(action)
    }
}

val recorder = RecordingGitHubApi(GitHubApi.Http(JavaHttpClient()))
recorder.getUser("bob")

println(recorder.recorded)
```

Writing stub implementations of an Adapter is also very simple, and the Connect pattern also encourages the same type of decomposed structure as with the real adapter - by creating extension functions which act on our in-memory state to create their responses. Once again, this helps to keep a grip on the size of the Adapter code.
```kotlin
class StubGitHubApi(private val users: Map<String, UserDetails>) : GitHubApi {
    override fun <R : Any> invoke(action: GitHubApiAction<R>): R = when (action) {
        is GetUser -> getUser(action, users) as R
        is GetRepoLatestCommit -> getRepoLatestCommit(action) as R
        else -> throw UnsupportedOperationException()
    }
}

private fun getUser(action: GetUser, users: Map<String, UserDetails>) = users[action.username]
private fun getRepoLatestCommit(action: GetRepoLatestCommit) = Commit(action.owner)
```

#### Varying the programming model
Depending on the style of team, there are several different popular programming models which may be commonly found out in the wild, and this will affect the value of the `R` type implemented for the Action classes. 

As in our example above, traditional OO-style teams using languages which embrace the throwing of Exceptions will represent `R` as the straight result type returned by the method, but teams that adopt a more Functional Programming approach will tend towards using a more monadic return type such as [Result4k](https://github.com/fork-handles/forkhandles/tree/trunk/result4k)'s `Result`, [Arrow](https://arrow-kt.io/docs/apidocs/arrow-core-data/arrow.core/-either/)'s `Either`, or (when it is generally available) Kotlin's built in [Result](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-result/) type.

The good news is that due to the decoupling of the Connect abstractions, any of these models can be supported simply by writing Actions in the relevant style. Here is an alternative example for the `GetUser` action using the Result4k monad:

```kotlin
interface GitHubApiAction<R> {
    fun toRequest(): Request
    fun fromResponse(response: Response): Result<R, Exception>
}

data class GetUser(val username: String) : GitHubApiAction<UserDetails> {
    override fun toRequest() = Request(GET, "/users/$username")
    override fun fromResponse(response: Response) = when {
        response.status.successful -> 
            Success(UserDetails(userNameFrom(response), userOrgsFrom(response)))
        else -> Failure(RuntimeException("API returned: " + response.status))
    }
}
```

### Summary 
The Connect pattern combines simple abstractions to provide a model that allows us to break down the common problem of the monolithic outbound API adapter into easily digestible parts. This modularity provides a mirror image of the composability that we expect when building inbound Server-side interfaces, and this further leads to a more testable and extensible codebase. With a small example such as this there is the potential for the approach seeming like overkill, but experience tells us that it is generally much harder to retrofit a compositional design than it is to promote it from the outset.

Initially designed around HTTP, the pattern will fit any request/response protocol and can easily be adapted to different programming models including Result monads and Future types.

Finally, although not crucial to the implementation of the Connect pattern, more advanced programming languages with features such as extension functions (such as Kotlin) provide an ideal platform for working with Connect. In statically typed languages, sufficiently advanced generic capabilities are the only required language feature.

Note: The code shown in this post is available in [GitHub](https://github.com/http4k/http4k-connect-examples/tree/master/connect-pattern).

### Footnote on the http4k-connect implementation of the pattern
<a title="http4k connect"
href="https://github.com/http4k/http4k-connect"><img class="article" alt="http4k connect" src="
../../../assets/img/connect.png"></a>

The Open Source [http4k-connect](https://github.com/http4k/http4k-connect) Kotlin libraries provide both the basic framework for implementing Connect pattern adapters, but also a set of pre-built API adapters for communicating with popular cloud services such as AWS. Further, http4k-connect provides a set of protocol-compatible In-Memory/Runnable Fake Servers which can be used as test-doubles for the various services, and a set of Storage backends (such as In-Memory, S3 and Redis) for test-data to be housed. 

The libraries are designed to be as lightweight as possible, meaning they are a perfect use-case for Serverless deployments, They use compile-time code-generation to automatically write extension functions for each of the implemented Actions using Kapt, and ships without the need for reflection in JSON message parsing by also generating message adapters for the [Moshi](https://github.com/square/moshi) JSON framework with the [Kotshi](https://github.com/ansman/kotshi) plugin.

