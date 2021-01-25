import dev.forkhandles.result4k.Result
import dev.forkhandles.result4k.resultFrom
import extensionPoint.Validation
import java.time.LocalDate

object extensionPoint {
    fun interface Validation : (LocalDate) -> Boolean {
        companion object
    }

    val Validation.Companion.future get() = Validation { it.isAfter(LocalDate.now()) }
    fun Validation.Companion.between(start: LocalDate, end: LocalDate) =
        Validation { it.isAfter(start) && it.isBefore(end) }

    val isFalse = Validation.future(LocalDate.of(2021, 1, 1))
    val thisCentury = Validation.between(LocalDate.of(2000, 1, 1), LocalDate.of(2099, 12, 31))
    val isTrue = thisCentury(LocalDate.of(2021, 1, 1))
}

// validation (vary result type)
inline class BirthDate /* private constructor */(val value: LocalDate) {
    companion object {
        fun asResult(unchecked: LocalDate): Result<BirthDate, Exception>? =
            resultFrom { BirthDate(unchecked.takeIf { it.isBefore(LocalDate.now()) }!!) }
    }
}

val birth = BirthDate.asResult(LocalDate.of(1999, 12, 31))

object factories {
    class BirthDate(val value: LocalDate) {
        companion object {
            fun parse(unchecked: String) = BirthDate(LocalDate.parse(unchecked))
        }
    }

    val birth = BirthDate.parse("2000-01-01")
}

object extending {
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
}
