package com.example.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * BigDecimal currency arithmetic helper using RoundingMode.HALF_UP (scale = 2)
 * while maintaining seamless Double compatibility with Room and Firestore models.
 */
object MoneyMath {

    private const val SCALE = 2
    private val ROUNDING = RoundingMode.HALF_UP

    fun toDecimal(value: Double): BigDecimal {
        if (value.isNaN() || value.isInfinite()) return BigDecimal.ZERO.setScale(SCALE, ROUNDING)
        return BigDecimal.valueOf(value).setScale(SCALE, ROUNDING)
    }

    fun round(value: Double): Double {
        return toDecimal(value).toDouble()
    }

    fun add(a: Double, b: Double): Double {
        return toDecimal(a).add(toDecimal(b)).setScale(SCALE, ROUNDING).toDouble()
    }

    fun subtract(a: Double, b: Double): Double {
        return toDecimal(a).subtract(toDecimal(b)).setScale(SCALE, ROUNDING).toDouble()
    }

    fun multiply(a: Double, b: Double): Double {
        return toDecimal(a).multiply(toDecimal(b)).setScale(SCALE, ROUNDING).toDouble()
    }

    fun divide(numerator: Double, denominator: Double): Double {
        val denom = toDecimal(denominator)
        if (denom.compareTo(BigDecimal.ZERO) == 0) return 0.0
        return toDecimal(numerator).divide(denom, SCALE, ROUNDING).toDouble()
    }

    /**
     * Computes the realized interest share of a payment installment strictly excluding upfront deductions.
     * Formula: paymentAmount * (interestAmount / (loanAmount + interestAmount))
     */
    fun realizedInterestShare(paymentAmount: Double, principalAmount: Double, interestAmount: Double): Double {
        val totalContract = add(principalAmount, interestAmount)
        if (totalContract <= 0.0 || paymentAmount <= 0.0 || interestAmount <= 0.0) return 0.0
        val paymentDec = toDecimal(paymentAmount)
        val interestDec = toDecimal(interestAmount)
        val totalDec = toDecimal(totalContract)
        return paymentDec.multiply(interestDec)
            .divide(totalDec, SCALE, ROUNDING)
            .toDouble()
    }

    /**
     * Splits an unrecovered remaining balance into (Principal Loss, Interest Loss, Total Loss)
     * proportionally based on the loan's principal and interest ratio.
     */
    fun calculateBadDebtLossBreakdown(
        principalAmount: Double,
        interestAmount: Double,
        paidAmount: Double
    ): Triple<Double, Double, Double> {
        val totalContract = add(principalAmount, interestAmount)
        val unpaidTotal = subtract(totalContract, paidAmount).coerceAtLeast(0.0)
        if (totalContract <= 0.0 || unpaidTotal <= 0.0) {
            return Triple(0.0, 0.0, 0.0)
        }
        val unpaidDec = toDecimal(unpaidTotal)
        val totalDec = toDecimal(totalContract)
        val principalLoss = unpaidDec.multiply(toDecimal(principalAmount))
            .divide(totalDec, SCALE, ROUNDING)
            .toDouble()
        val interestLoss = subtract(unpaidTotal, principalLoss).coerceAtLeast(0.0)
        return Triple(principalLoss, interestLoss, unpaidTotal)
    }
}
