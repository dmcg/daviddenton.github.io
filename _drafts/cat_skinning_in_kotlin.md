---
layout: post 
title: Cat skinning in Kotlin subtitle: Object initialisation patterns compared
tags: [kotlin, functions, design]
comments: false
---

defining our contract
```kotlin
typealias Calculation = (Double) -> Double

fun interface Calculation : (Double) -> Double

fun interface Calculation {
    operator fun invoke(operand: Double): Double
}
```

```kotlin

class Plus(private val that: Double) : Calculation {
    override fun invoke(operand: Double) = operand + that
}

fun Minus(that: Double) = Calculation { it - that }

class Power(private val that: Int) : Calculation {
    override fun invoke(a: Double) = a.pow(that)
}

fun Square() = Power(2)
fun Cube() = Power(3)

object SomeOperationWhichNeedsState {
    operator fun invoke(a: Double, b: Double) = Calculation { a -> a.pow(2) }
}
```

swap out interface for object difference with java

don't give object identity when it doesn't have state minimise namespace pollution

extracting when refactoring

in the post OOP world, a typical object is just a partially applied function

