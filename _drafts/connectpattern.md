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
the rest - in this case we don't need to provide a `Bearer` token to access our API calls if we have access to directly test `echo()` and `health()`.

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

This is all quite sensible, but unfortunately, as our usage of the API grows, so does the size of the `GitHubApi` class - it may gain many (10s or even 100s of individual functions), all of which generally provide singular access to a single API call.

As there is no interaction between these functions - it would be desirable to structure the code in a similar way to how we structured our incoming API - in a modular, easily testable fashion.

<hr/>

### ps.

If you'd like to see an example of the Connect Pattern in
