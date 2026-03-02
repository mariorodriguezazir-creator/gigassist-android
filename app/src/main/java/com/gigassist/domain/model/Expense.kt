package com.gigassist.domain.model

/**
 * Modelo de dominio para un gasto del conductor.
 */
data class Expense(
    val id: Long = 0,
    val category: ExpenseCategory,
    val amount: Double,
    val date: Long,
    val notes: String? = null
)

/**
 * Categorías de gastos soportadas según PRD.
 */
enum class ExpenseCategory {
    GASOLINA,
    MANTENIMIENTO,
    PEAJES,
    LAVADO,
    OTROS
}
