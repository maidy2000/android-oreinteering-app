package com.example.endotastic

import android.app.AlertDialog
import android.app.Application
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.example.endotastic.databases.UserDatabase
import com.example.endotastic.repositories.user.User
import com.example.endotastic.repositories.user.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONTokener


class LoginDialog(application: Application, private val activity: MapActivity) : DialogFragment() {


    private lateinit var editTextEmail: EditText
    private lateinit var editTextPassword: EditText

    private val userRepository: UserRepository

    init {
        val userDao = UserDatabase.getDatabase(application).getUserDao()
        userRepository = UserRepository(userDao)
    }


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity.let {
            // Use the Builder class for convenient dialog construction
            val builder = AlertDialog.Builder(it)

            val view = layoutInflater.inflate(R.layout.layout_login, null)
            editTextEmail = view.findViewById(R.id.editTextEmail)
            editTextPassword = view.findViewById(R.id.editTextPassword)

            builder.setView(view).setTitle("Login")
//
//                .setNeutralButton(
//                "Register"
//            ) { dialog, id ->
//                //TODO: Register option
//            }
                .setPositiveButton(
                "Login"
            ) { dialog, id ->
                val email = editTextEmail.text.toString()
                val password = editTextPassword.text.toString()
                login(email, password)
            }
            // Create the AlertDialog object and return it
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    private fun login(email: String, password: String) {
        val url = C.API + "Account/Login"
        val handler = HttpSingletonHandler.getInstance(requireContext())

        val httpRequest = object : StringRequest(Request.Method.POST,
            url,
            Response.Listener { response ->
                 Log.d("Response.Listener", response.toString())
                val json = JSONTokener(response).nextValue() as JSONObject
                loginUser(json, email, password)
            },
            Response.ErrorListener { error ->
                 Log.d(
                    "Response.ErrorListener", "${error.message} ${error.networkResponse.statusCode}"
                )
            }) {
            override fun getBodyContentType(): String {
                return "application/json"
            }

            override fun getBody(): ByteArray {
                val params = HashMap<String, String>()
                params["email"] = email
                params["password"] = password

                val body = JSONObject(params as Map<*, *>).toString()
                 Log.d("getBody", body)

                return body.toByteArray()
            }

        }
        handler.addToRequestQueue(httpRequest)
    }

    private fun loginUser(json: JSONObject, email: String, password: String) {
        val firstName = json.getString("firstName")
        val lastName = json.getString("lastName")
        val token = json.getString("token")
        val user = User(firstName, lastName, email, password, token)
        saveUser(user)
        val loginScreen = activity.findViewById<TextView>(R.id.textViewLoginScreen)
        loginScreen.visibility = View.GONE
    }

    private fun saveUser(user: User) {
        lifecycleScope.launch(Dispatchers.IO) {
            userRepository.addUser(user)
             Log.d("saveUser", "user=$user")
        }
    }
}