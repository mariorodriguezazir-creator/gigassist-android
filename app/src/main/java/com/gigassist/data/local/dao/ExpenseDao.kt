package com.gigassist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.gigassist.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert
    suspend fun insert(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getExpenses(): Flow<List<ExpenseEntity>>
}
