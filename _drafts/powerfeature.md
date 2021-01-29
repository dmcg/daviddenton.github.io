---
layout: post 
title: "Companion Objects: Kotlin's most unassuming power feature"
tags: [kotlin, design]
comments: false
thumbnail: assets/img/powerfeature.jpg
---

In which I argue the case for Kotlin's Companion Objects being the Clark Kent of language features, in being deceptively
powerful, and how we can use them in creating ways to supercharge our API design.

<a title="Image by Nicole Köhler from Pixabay"
href="https://pixabay.com/photos/power-lines-fields-sunset-twilight-532720"><img width="512" alt="Dog.in.sleep" src="
../../../assets/img/powerfeature.jpg"></a>

<hr/>

### tl;dr

> **"Companion Objects are not just placeholders for constants and other static state; they allow for new strategies to help organise our code or to provide reusable factory and validation features."**

<hr/>

### rta
Of all the features that developers enthuse about in the Kotlin world, one of the ones that you hear about least is the
humble `Companion Object`. On the face of it, they are merely a convenient stand-in for where you could put static state.
But to dismiss them as such is prematurely writing them off - there are several very interesting use-cases that we have
discovered for them.

Don't believe me? I'd be disappointed if you did 😉. Let's dive in with some simple examples, all based around the Java's
trusty `LocalDate` class.

#### API extension points

A basic (and arguably the most boring) ability that Companion Objects give us is as extension points for growing
collections of similarly themed functions or values. If you define a simple concept or abstraction that will be used and
reused in your system I'd encourage the addition of a companion object onto the class/interface definition. This
provides both a place to attach extensions which are not relevant to the core concept in all scenarios and avoids
muddying the waters... imagine how intimidating it is for a Developer (or yourself having written it!) to come across an
interface with twenty functions instead of two!

This also allows developers in other parts of your own or another code module to define new implementations of said
abstraction to have a place to collect/organise them - the IDE will pick up all of these extensions and offer them to
you through autocompletion, instead of them being strewn all over the code.

We can see this with the (somewhat contrived) example below - we define a simple `Validation` predicate and attach
various extension functions/implementations/properties to:

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

#### Parsers/Validators
We also can utilise the Companion Objects to provide a place to ensure correct construction of micro-types from other
formats (these can also, as above, be external to the a base class as appropriate). In the example below we have a
simple value wrapper for `LocalDate`. But we also want to be able to parse, validate and show the value correctly from our
strange ISO compliant format (YYYY-DDD):

```kotlin
data class BirthDate(val value: LocalDate) {
    init {
        require(value.isBefore(LocalDate.now()))
    }

    companion object {
        private val format = ISO_ORDINAL_DATE
        fun parse(unchecked: String): BirthDate = BirthDate(LocalDate.parse(unchecked, format))
        fun show(date: BirthDate): String = format.format(date.value)
    }
}

val birth = BirthDate.parse("1976-244")
val string = BirthDate.show(birth) // prints "1976-244"
```

#### Vary your programming model!
One problem with the above approach is that traditional parse/require pattern blows up with an exception when an error
occurs - standard object construction techniques don't give us a chance to apply more functional programming models such
as Result/Either monads to our domain.

In these models, we actively try to avoid Exceptions - to help us here we can privatise the `BirthDate` constructor and write a  function to capture the error into a [Result4k](https://github.com/fork-handles/forkhandles/tree/trunk/result4k) type which we can then `map/flatMap()` over:

```kotlin
data class BirthDate private constructor(val value: LocalDate) {
    companion object {
        fun asResult(unchecked: LocalDate) = when {
            unchecked.isBefore(LocalDate.now()) -> Success(BirthDate(unchecked))
            else -> Failure("illegal date!")
        }
    }
}

val birth: Result<BirthDate, String> = BirthDate.asResult(LocalDate.of(1999, 12, 31))
```

If we want to plug in a different result monad (say Arrow's `Either`) or to return `null` on failure, it is trivial to add extension functions to cover these types. The companion object is giving us options...

#### Extract and reuse!
What a lot of developers don't appreciate is that Companion Objects have exactly the same capabilities as any other Kotlin object - and this includes inheritance. Rewinding back to a simpler Exception-based parse example, we can extract commonality to superclasses/interfaces/delegates which can be also mixed into our Companion Object in the standard fashion. Taking this one step further, we realise that any extension functions added to the superclass/interfaces will then automatically be added to our Companion Objects as well!

Here we have extracted out a common superclass `DateValueFactory` for all "Date wrapper" classes - and each of the functions on this class are now inherited by both `OrderDate` and `DeliveryDate` (via their Companions). As mentioned before, we have also added an extension function to all `DateValueFactory` instances for the Result4k construction case:

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

fun <T> DateValueFactory<T>.asResult(unchecked: String) = resultFrom { parse(unchecked) }

val order = OrderDate.parse("2000-01-01")
val delivery = DeliveryDate.asResult("2099-12-31")
```

### Summary
Although most of the patterns above are concentrated around value object construction, these are only the ones we have thought of - there are bound to be a bunch more just waiting to be discovered. This process has reinforced in my development work that we should be scratching beneath the surface in Kotlin features to see what it possible. As with a lot of inventions, it's possible that even JetBrains didn't really appreciate the depth which would be unlocked by adding such a humble feature - I'd be fascinated to learn of any of these types of uses in the Kotlin standard libraries.

### ps.
For a practical example of how the these type-creation techniques are used the real world, you can take a look at the foundational [Values4k](https://github.com/fork-handles/forkhandles/tree/trunk/values4k) library, which uses them to provide instantiation, validation, parsing and printing of value types.
