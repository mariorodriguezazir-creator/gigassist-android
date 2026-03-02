package com.gigassist.data.repository

import com.gigassist.data.local.dao.ExpenseDao
import com.gigassist.data.local.mapper.toDomain
import com.gigassist.data.local.mapper.toEntity
import com.gigassist.domain.model.Expense
import com.gigassist.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ExpenseRepositoryImpl @Inject constructor(
    private val dao: ExpenseDao
) : ExpenseRepository {

    override suspend fun addExpense(expense: Expense) {
        dao.insert(expense.toEntity())
    }

    override fun getExpenses(): Flow<List<Expense>> {
        return dao.getExpenses().map { entities ->
            entities.map { it.toDomain() }
        }
    }
}
