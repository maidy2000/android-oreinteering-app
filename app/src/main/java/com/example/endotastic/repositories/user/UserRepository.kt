package com.example.endotastic.repositories.user

import android.util.Log
import com.example.endotastic.daos.UserDao

class UserRepository(private val userDao: UserDao) {

    suspend fun addUser(user: User) {
        userDao.addUser(user)
    }

    suspend fun getUserByEmail(email: String): User {
        return userDao.getUserByEmail(email)
    }

    suspend fun getUserExists(): Boolean {
        return userDao.getUserExists()
    }

    suspend fun updateUser(user: User) {
        userDao.updateUser(user)
    }

    suspend fun getUser(): User {
        val result = userDao.getUser()
         Log.d("Login", "Repo getUser() user=$result")
        return result
    }
}