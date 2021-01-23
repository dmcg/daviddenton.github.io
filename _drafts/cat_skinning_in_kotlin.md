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

Now, the pendulum has swung for me, and the line between functional and object-oriented thinking is firmly buried in the FP half of the dial. Far from simply creating application object trees with classes named in the "Havers-and-Doers" style, I now try to spend as much of my time as possible avoiding the creation of new classes and relying on composing programs from simple calls to functions instead.

Let's ask ourselves - what *is* the purpose of an object when we are now concentrated on minimising mutable state? For example, consider a typical interface and class which we might create and use:

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

Ah, but I hear the cry, our `FileSystem` class actually defines an entity or concept in our system. Well, this may appear true - but it's actually not the presence of the class that defines this entity - it's the interface! When we realise this, we extract the interface and make this change (which you admittedly may have actually started with anyway should you have had the foresight to anticipate this change):

```kotlin
interface FileSystem {
    fun directories(): Array<File>
}

class LocalFileSystem(private val dir: File) : FileSystem {
    override fun directories() = dir.listFiles(FileFilter { it.isDirectory })
}

val localDirs = LocalFileSystem(File(".")).directories()
```

There are downsides to this action. Firstly, introducing the `LocalFileSystem` later is a breaking change - our API users suddenly have to deal with the new class construction API. Additionally, that `LocalFileSystem` is even now visible muddies the waters in terms of type-inferfence. Kotlin will automatically assign the most accurate type that it can to a reference of `LocalFileSystem`.

the presence of the `LocalFileSystem` class provides zero benefit beyond the capturing of the `dir` parameter. In fact, it could be argued as a negative due to the fact that the `LocalFileSystem` is visible to the compiler and API user and combined with the lack of explicit repeated types due to Kotlin's type inference, if we attempt to extract a variable or a parameter we will end up with:

```kotlin
val localFs: LocalFileSystem = LocalFileSystem(File("."))
val localDirs = localFs.directories()
```

We could therefore, do this instead:

```kotlin
fun FileSystem(dir: File): FileSystem = object : FileSystem {
    override fun directories() = dir.listFiles(FileFilter { it.isDirectory })
}

val localDirs = FileSystem(File(".")).directories()
```

To the API client, there is no difference between the two approaches - they're constructing and using a FileSystem and the contract is in place. Additionally we have polluted our object namespace with one less useless API class which will 




swap out interface for object difference with java

don't give object identity when it doesn't have state minimise namespace pollution

extracting when refactoring

in the post OOP world, a typical object is just a partially applied function

Class Vs function.
A class is a collections of related functions. The "name" Denotes that we recognise we are initiating one of these collections.
Where is the state? And should the API client care? Or be able to tell the difference?
Example of capturing arguments by applying a constructor to create another function (or collection is functions)

Does the capital indicate that the return type is a function? Should it?
Value Vs function...




defining our contract
```kotlin
typealias Calculation = (Double) -> Double

fun interface Calculation : (Double) -> Double

fun interface Calculation {
    operator fun invoke(operand: Double): Double
}
```

```kotlin
typealias Calculation = (Double) -> Double

fun interface Calculation : (Double) -> Double

fun interface Calculation {
    operator fun invoke(operand: Double): Double
}

class Plus(private val that: Double) : Calculation {
    override fun invoke(operand: Double) = operand + that
}

fun Minus(that: Double) = Calculation { it - that }

class Power(private val that: Int) : Calculation {
    override fun invoke(a: Double) = a.pow(that)
}

fun Square() = Power(2)

object Cube : Power(3)

object SomeOperationWhichNeedsState {
    operator fun invoke(a: Double) = Calculation { a -> a.pow(2) }
}
```
