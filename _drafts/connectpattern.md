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

1. Serverside routing and unmarshalling of incoming requests
2. Business logic functions
3. Data-access querying and mutations
4. Adapter code for remote API communication

For 1, we tend to model the application as a set of separate HTTP entrypoint classes/functions which are composed into a
whole to represent the incoming HTTP API, either explicitly or via some meta-programming such as annotations. So for
example, using http4k, we might create and start our server with:

```kotlin
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
```

In this case, the splitting up of the serverside API into separate functions allows us to maintain a decent grip on our
application as a whole and also to be able to trivially test the various endpoints in the application independently of
the rest - in this case we don't need to provide a `Bearer` token to access our API calls if we have access to directly test `echo()` and `reverser()`.



<hr/>

### ps.

If you'd like to see an example of the Connect Pattern in
