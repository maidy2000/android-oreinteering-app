package com.example.endotastic.repositories.user

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.endotastic.repositories.user.User.Companion.TABLE_NAME


@Entity(tableName = TABLE_NAME)
data class User(
    val firstName: String,
    val lastName: String,
    @PrimaryKey
    val email: String,
    val password: String,
    val token: String
) {
    companion object {
        const val TABLE_NAME: String = "user_table"
    }
}