---
layout: post 
title: "Outklassed: How Kotlin taught me to rethink application design"
tags: [kotlin, fp, design]
comments: false
thumbnail: assets/img/outklassed.jpg
---

In which I try to reason with myself about how five years of Kotlin has encouraged me to construct programs.

<a title="Image by Nicole Köhler from Pixabay"
href="https://pixabay.com/photos/power-lines-fields-sunset-twilight-532720"><img width="512" alt="Dog.in.sleep" src="
../../../assets/img/outklassed.jpg"></a>

<hr/>

### tl;dr
> **"Wherever possible, don't eagerly promote an created object instance to have class identity when it is only providing partial application of common parameters over an abstraction. Minimise your exposed API surface."**

<hr/>

### rta

I've been thinking recently about the transition in coding style I've made over the last few years. Like many others, I cut my teeth in this industry as a typical self-taught OO programmer coding Java for a living, and life seemed - if not easy - at least something that was tractable. We all knew when and how to create objects, and that objects held state. Spin forward a decade or so and my style of programming evolved as I finally met some talented folks who actually knew how to test and use an IDE properly, and to embrace concepts such as immutability and collections processing with higher order functions. This was even better - coding was simpler and safer but the fundamentals were the same.

And then I met Kotlin.

Now the pendulum has swung for me, and the needle on that functional/object-oriented dial is buried deep into the FP half. Top-level functions became available whereas previously in Java everything had to exist within the context of a class.

Far from simply creating application object trees with custom classes named in the "Hav-er/Doer/Service" style, I was now actively noticed the lines blurring and becoming frustrated with my traditional approach. I started to avoid the creation of new classes and relying instead on composing things from abstractions created by calling floating functions.

But why? To answer, let's ask ourselves - what *is* the purpose of an object with class identity when we are generally now so concentrated on minimising mutable state? For example, consider a typical interface and class which we might create and use:

```kotlin
class FileSystem(private val dir: File) {
    fun directories() = dir.listFiles(FileFilter { it.isDirectory })
}

val fileSystem: FileSystem = FileSystem(File("."))
val localDirs = fileSystem.directories()
```

Does this structure look familiar - just a constructor containing references to immutable vals and a bunch of functions using them? As an alternative, Kotlin gives us the facility to create APIs in a much more full-on functional style, eschewing the class instance entirely:

```kotlin
fun directories(dir: File) = dir.listFiles(FileFilter { it.isDirectory })
val otherLocalDirs = directories(File("."))
```

There is no mutable state here in either solution. The two examples are effectively identical, apart from the class instance is only used as a way of fixing the `dir` parameter. To put it another way, it's just a mechanism of partial application of the `directories()` function. I noticed that a lot of our class instances that we create in our Stateless Microservices Themepark™ can just be thought of a set of partially applied functions over a set of common parameters - be they directories, HTTP clients or database connections. So why even create it at all?

Aha, but I hear the cry, our `FileSystem` class actually defines an entity or concept in our system! It gives us a nicer thing to deal with than passing a method reference of the function. And what happens if we add a second function? Well, this may appear true - but it's actually not the presence of the class that defines this entity - it's actually the interface that's important. 

This is all good design thinking as per what we were taught back in OO-School - to design our systems mostly in terms of abstract roles/interfaces and avoid references to concrete classes, before it was the trend to create an interface for every "Hav-er/Doer/Service" because a DI container or mocking framework demanded that's what you should be doing. In the before-times, an interface represented an capability that an object had eg. `Iterable` or `Comparable`. Now, we just swam in a sea of `MyRepositoryService` (implemented by the inevitable `MyRepositoryServiceImpl`).

But I digress.

If we want to pursue the abstraction, we can decide to extract a `FileSystem` interface and make this change (which admittedly you may have actually started with anyway, should you have had the foresight to anticipate the change to this previously unremarkable class):

```kotlin
interface FileSystem {
    fun directories(): Array<File>
}

class LocalFileSystem(private val dir: File) : FileSystem {
    override fun directories() = dir.listFiles(FileFilter { it.isDirectory })
}

val localDirs = LocalFileSystem(File(".")).directories()
```

There are, however, downsides to this approach. Firstly, introducing the `LocalFileSystem` class is a breaking/invasive change - our API users suddenly have to deal with the new class construction API. Additionally, that `LocalFileSystem` is even now visible muddies the waters in terms of type-inference, and that Kotlin will automatically assign the most accurate type that it can to a reference of `LocalFileSystem` instance when we extract the object as a field, variable or parameter:

```kotlin
val fs = LocalFileSystem(File(".")) // to the IDE, fs is a LocalFileSystem
val localDirs = fs.directories()
```

This isn't a problem when you are manually changing two references in other code, but it gets more tedious and error prone as the number of changes grows. There are of course workarounds to these problems, but they feel extremely clunky. For instance, we could privatise the constructor then create an `invoke()` method on the `LocalFileSystem` companion object that mimics the constructor interface and returns the `FileSystem` interface:

```kotlin
class LocalFileSystem private constructor(private val dir: File) : FileSystem {
    override fun directories() = dir.listFiles(FileFilter { it.isDirectory })

    companion object {
        operator fun invoke(dir: File): FileSystem = LocalFileSystem(dir)
    }
}

val fs = LocalFileSystem(File(".")) // to the IDE, fs is now a FileSystem
```

Ewww - this not only seems like a lot of effort (especially in Kotlin), and we still have a `LocalFileSystem` class identity hanging around even if we can't construct one. Instead we could cut to the chase, dispose of the class entirely, and just use a top-level function `FileSystem` which mimics the constructor and creates an anonymous instance. It's the perfect crime - to the API client there is no difference between the two approaches - they're constructing and using a `FileSystem` and the contract is still in place. Additionally we have avoiding polluting our namespace with one extra class for the API user to wonder about:

```kotlin
fun FileSystem(dir: File): FileSystem = object : FileSystem {
    override fun directories() = dir.listFiles(FileFilter { it.isDirectory })
}

val fs = FileSystem(File(".")) // to the IDE, fs is a FileSystem
val localDirs = fs.directories()
```

It should be noted that the above approach may anger the "Kotlin-style gods" because of the capital at the start of the function name. But Kotlin, like Scala before it, empowers us to inhabit this woolly mixed class-based/functional world where there is no `new` keyword. This opens up different styles of API design to us, and also leads to more interesting philosophical questions such as:

> "What does the presence of a Capital letter even mean, and should our clients even care?"

If it's that we're allocating a state-gathering object to put on the heap, then we do call functions which do that all the time without thinking twice about it. And due to the existence of properties (and how Kotlin handles them as pseudo-fields - making them available on interfaces), this distinction becomes even less clear as the APIs betraying statefulness can exist anywhere. One way of making the distinction could be that a function beginning with a capital letter should only be present at the top level we are returning an abstraction representing one or more functions.

It's an exercise for the reader to cut their own path here. I'm only here for the rants. Sorry. :)

Regardless, in my day-to-day and Open Source work, I'm now applying the rules of...
- Generally try to only create a "Hav-er/Doer/Service" class as a last resort where there is state to be tracked or we are extending another abstraction. Help out your users by minimising the API surface.
- Use interfaces to represent collections of one or more functions closing over common parameters.
- Representing data is still almost always as value type or data classes, with maybe the odd sealed hierarchy thrown in.

### ps.
In some ways, I rationalise this approach as an extension to the rule of "Composition over Inheritance" - we are trying to simplify and minimise the number of top-level visible class identities in our system.

