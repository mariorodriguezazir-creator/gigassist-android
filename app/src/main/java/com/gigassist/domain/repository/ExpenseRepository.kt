package com.gigassist.domain.repository

import com.gigassist.domain.model.Expense
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio para gastos del conductor.
 */
interface ExpenseRepository {
    suspend fun addExpense(expense: Expense)
    fun getExpenses(): Flow<List<Expense>>
}
