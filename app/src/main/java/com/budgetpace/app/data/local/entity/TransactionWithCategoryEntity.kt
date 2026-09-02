package com.budgetpace.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class TransactionWithCategoryEntity(
    @Embedded val transaction: TransactionEntity,
    @Relation(
        parentColumn = "categoryId",
        entityColumn = "id"
    )
    val category: CategoryEntity?
)
