---
layout: post title: "Kotlin Companion Objects: Shiny new entries for our API toy-box"
tags: [kotlin, fp, design]
comments: false thumbnail: assets/img/outklassed.jpg
---

Exploring the different types of superpower which are afforded to us by Kotlin's objects, and how we can use them in
unusual ways to design our APIs.

<a title="Photo by PIXNIO @ https://pixnio.com/objects/toys/giant-robot-exhibition"><img width="512" alt="Dog.in.sleep" src="../../../assets/img/toys.jpg"></a>

<hr/>

### tl;dr

> **"Be aware that Kotlin's companion objects have all the "**

<hr/>

### extension points
```kotlin
fun interface Validation : (LocalDate) -> Boolean {
    companion object
}

val Validation.Companion.future get() = Validation { it.isAfter(LocalDate.now()) }
fun Validation.Companion.between(start: LocalDate, end: LocalDate) =
    Validation { it.isAfter(start) && it.isBefore(end) }

val isFalse = Validation.future(LocalDate.of(2021, 1, 1))
val thisCentury = Validation.between(LocalDate.of(2000, 1, 1), LocalDate.of(2099, 12, 31))
val isTrue = thisCentury(LocalDate.of(2021, 1, 1))
```

### parsers
```kotlin
class BirthDate(val value: LocalDate) {
    companion object {
        fun parse(unchecked: String) = BirthDate(LocalDate.parse(unchecked))
    }
}

val birth = BirthDate.parse("2000-01-01")
```

### validation
```kotlin
// validation (vary result type)
inline class BirthDate /* private constructor */(val value: LocalDate) {
    companion object {
        fun asResult(unchecked: LocalDate): Result<BirthDate, Exception>? =
            resultFrom { BirthDate(unchecked.takeIf { it.isBefore(LocalDate.now()) }!!) }
    }
}

val birth = BirthDate.asResult(LocalDate.of(1999, 12, 31))
```

### factories
```kotlin
abstract class DateValueFactory<T>(private val buildFn: (LocalDate) -> T) {
    fun parse(unchecked: String) = buildFn(LocalDate.parse(unchecked))
}

class OrderDate(val value: LocalDate) {
    companion object : DateValueFactory<OrderDate>(::OrderDate)
}

class DeliveryDate(val value: LocalDate) {
    companion object : DateValueFactory<DeliveryDate>(::DeliveryDate)
}

val order = OrderDate.parse("2000-01-01")
val delivery = DeliveryDate.parse("2099-12-31")
```

Companion.object uses.article

Can Extend things:interfaces

Invoke can hide details (or types!)

Factory functions / shared!

Top level objects give nice starting off point (as with static functions in java (builder)

