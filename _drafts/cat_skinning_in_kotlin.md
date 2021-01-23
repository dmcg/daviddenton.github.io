---
layout: post 
title: something something something objects
tags: [kotlin, functions, design]
comments: false
---
### tl;dr:
In which I try to reason with myself about the various ways in which construct programs as a mixture of object creation and calls to top-level functions.

### meat

I've been thinking recently about the transition I've made over the last few years. Like many others, I cut my teeth on this industry as a typical self-taught OO programmer coding Java for a living, and life seemed - if not easy - at least something that was tractable. Spin forward a decade or so and my style of programming evolved as I finally met some talented folks and started to really use an IDE properly, and to embrace concepts such as immutability and collections processing with higher order functions.

And then I met Kotlin.

Now, the pendulum has swung for me, and the line between functional and object-oriented thinking is firmly buried in the FP half of the dial. Far from simply creating application object trees with classes named in the "Hav-er/Do-er" style, I now try to spend as much of my time as possible avoiding the creation of new classes and relying on composing programs from simple calls to functions instead.

Let's ask ourselves - what *is* the purpose of an object with class identity when we are now concentrated on minimising mutable state? For example, consider a typical interface and class which we might create and use:

```kotlin
class FileSystem(private val dir: File) {
    fun directories() = dir.listFiles(FileFilter { it.isDirectory })
}

val fileSystem: FileSystem = FileSystem(File("."))
val localDirs = fileSystem.directories()
```

Does this structure look familiar? Alternatively, Kotlin gives us the facility to eschew the class instance entirely and just use top-level functions.

```kotlin
fun directories(dir: File) =  dir.listFiles(FileFilter { it.isDirectory })
val otherLocalDirs = directories(File("."))
```

There is no mutable state here. The two examples are effectively identical, apart from the class instance is only used as a way of fixing the `dir` parameter - ie. it's just an alternate means of partial application of the top level functions. In this way a lot of our class instances that we create in our now universal Stateless Microservices™ can just be thought of a set of partially applied functions over some common parameters - be they file directories or HTTP clients. 

Ah, but I hear the cry, "our `FileSystem` class actually defines an entity or concept in our system!". Well, this may appear true - but it's actually not the presence of the class that defines this entity - it's the interface. This is all good design thinking that we were taught back at OO School - to program mostly in terms of interfaces and avoid references to concrete classes. When we realise this, we extract the interface and make this change (which admittedly you may have actually started with anyway, should you have had the foresight to anticipate the change to this previously unremarkable class):

```kotlin
interface FileSystem {
    fun directories(): Array<File>
}

class LocalFileSystem(private val dir: File) : FileSystem {
    override fun directories() = dir.listFiles(FileFilter { it.isDirectory })
}

val localDirs = LocalFileSystem(File(".")).directories()
```

There are, however, downsides to this action. Firstly, introducing the `LocalFileSystem` class is a breaking change - our API users suddenly have to deal with the new class construction API. Additionally, that `LocalFileSystem` is even now visible muddies the waters in terms of type-inference means that Kotlin will automatically assign the most accurate type that it can to a reference of `LocalFileSystem` instance when we extract the object as a field, variable or parameter:

```kotlin
val fs = LocalFileSystem(File(".")) // to the IDE, fs is a LocalFileSystem
val localDirs = fs.directories()
```

There are of course workarounds to this, but in 2021 they feel clunky and don't really solve the breaking API issue - for instance, we can privatise the constructor then create an `invoke()` method on the `LocalFileSystem` companion object that mimics the constructor interface and returns the `FileSystem` interface.

Instead we could cut to the chase, dispose of the class entirely, and just use a top-level function `FileSystem` which mimics the constructor. It's the perfect crime - to the API client there is no difference between the two approaches - they're constructing and using a FileSystem and the contract is still in place. Additionally we have avoiding polluting our namespace with one extra class for the API reader to wonder about:

```kotlin
fun FileSystem(dir: File): FileSystem = object : FileSystem {
    override fun directories() = dir.listFiles(FileFilter { it.isDirectory })
}

val fs = FileSystem(File(".")) // to the IDE, fs is a FileSystem
val localDirs = fs.directories()
```

It should be noted that the above approach may anger the "Kotlin-style gods" because of the capital at the start of the function name. But this does also lead to an interesting question - "What does the presence of a Capital letter even mean?". If it's that we're allocating a state-gathering object to put on the heap, then we do that all the time without thinking twice about it. And due to the existence of properties (and how Kotlin handles them as pseudo-fields - making them available on Interfaces), this distinction becomes even less clear. One way of making the distinction is that the Capital letter should only be applied when we are returning an abstraction representing one or more functions.

It's an exercise for the reader to determine their own path here.. :) 

In my day-to-day work, I'm now pursing the rule of generally only creating a "Hav-er/Do-er" class as a last resort. Data is almost always represented as a data class (or in a sealed hierarchy). Try to use interfaces to represent collections of functions closing over common parameters.

David's rule: "Wherever possible, don't allocate an object special identity when it is only providing partial application".

Class Vs function.
A class is a collections of related functions. The "name" Denotes that we recognise we are initiating one of these collections.
Where is the state? And should the API client care? Or be able to tell the difference?
Example of capturing arguments by applying a constructor to create another function (or collection is functions)
