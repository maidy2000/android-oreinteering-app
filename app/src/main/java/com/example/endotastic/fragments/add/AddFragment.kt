package com.example.endotastic.fragments.add

import android.os.Bundle
import android.text.TextUtils
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.endotastic.HistoryViewModel
import com.example.endotastic.R
import com.example.endotastic.repositories.gpsSession.GpsSession


class AddFragment : Fragment() { // TODO: DELETE, made for learning and testing purposes

    private lateinit var mHistoryViewModel: HistoryViewModel
    private lateinit var editTextTextGpsSessionName: TextView
    private lateinit var editTextTextDescription: TextView
    private lateinit var editTextTextRecordedAt: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment

        val view = inflater.inflate(R.layout.fragment_add, container, false)
        editTextTextGpsSessionName = view.findViewById(R.id.editTextTextGpsSessionName)
        editTextTextDescription = view.findViewById(R.id.editTextTextDescription)
        editTextTextRecordedAt = view.findViewById(R.id.editTextTextRecordedAt)
        mHistoryViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]


        val button: Button = view.findViewById(R.id.button)
        button.setOnClickListener {
            insertDataToDatabase()
        }

        return view
    }

    private fun insertDataToDatabase() {
        val name = editTextTextGpsSessionName.text.toString()
        val description = editTextTextDescription.text.toString()
        val recordedAt = editTextTextRecordedAt.text.toString()

        if (isInputCorrect(name, description, recordedAt)) {
            val gpsSession = GpsSession(0, name, description, recordedAt)
            mHistoryViewModel.addGpsSession(gpsSession)
            Toast.makeText(requireContext(), "success", Toast.LENGTH_LONG).show()
            findNavController().navigate(R.id.action_addFragment_to_listFragment)
        } else {
            Toast.makeText(requireContext(), "Fill out all fields", Toast.LENGTH_LONG).show()
        }
    }

    private fun isInputCorrect(name: String, description: String, recordedAt: String): Boolean {
        //todo recodedAt peab olema korralik kuupaeva kontroll
        return !(TextUtils.isEmpty(name) && TextUtils.isEmpty(description) && TextUtils.isEmpty(recordedAt))
    }
}