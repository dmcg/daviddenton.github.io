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

Now, the pendulum has swung, and the line between functional and object-oriented thinking is firmly buried in the FP half of the dial. Far from simply creating application object trees with classes named in the "havers-and-doers" style, I now try to spend as much of my time as possible avoiding the creation of new classes and relying on composing programs from simple calls to functions instead.

Let's ask ourselves - what *is* the purpose of an object when we are now concentrated on minimising mutable state? For example, consider a 

```kotlin
class FileSystem(private val dir: Directory) {
    fun listCsvFiles    (): List<File> = dir.
}
```

Most classes can just be thought of a set of partially applied functions



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
