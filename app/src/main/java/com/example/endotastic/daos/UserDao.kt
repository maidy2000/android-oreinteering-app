package com.example.endotastic.daos

import androidx.room.*
import com.example.endotastic.repositories.user.User

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addUser(user: User)

    @Query("SELECT * FROM ${User.TABLE_NAME} WHERE email = :email")
    fun getUserByEmail(email: String): User

    @Update
    fun updateUser(user: User)

    @Query("SELECT EXISTS(SELECT * FROM ${User.TABLE_NAME})")
    fun getUserExists(): Boolean

    @Query("SELECT * FROM ${User.TABLE_NAME} LIMIT 1;")
    fun getUser(): User

}