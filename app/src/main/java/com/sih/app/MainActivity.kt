package com.sih.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.sih.relay.RelayManager
import com.sih.relay.api.RelayDataSource
import com.sih.relay.model.SOSRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Temporary Day 2 Activity scaffolding for testing Nearby Connections.
 *
 * Provides a minimal layout with buttons to start and stop the relay engine.
 * Component C will replace this with Jetpack Compose UI.
 */
class MainActivity : Activity() {

    private companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var relayManager: RelayManager

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainActivity onCreate starting...")

        // Temporary stub data source until Component C delivers RoomRelayDataSource in :data
        val stubDataSource = object : RelayDataSource {
            override suspend fun getAllSosUuids(): List<String> = emptyList()
            override suspend fun getMissingSos(knownUuids: List<String>): List<SOSRequest> = emptyList()
            override suspend fun saveSosMessages(messages: List<SOSRequest>) {}
            override fun observeAllSos(): Flow<List<SOSRequest>> = emptyFlow()
        }

        relayManager = RelayManager(this, stubDataSource)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(48, 48, 48, 48)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val tvTitle = TextView(this).apply {
            text = "Day 2 Relay Test Scaffold"
            setTextColor(Color.WHITE)
            textSize = 20f
            setPadding(0, 0, 0, 32)
        }

        val buttonParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 16, 0, 16)
        }

        val btnStart = Button(this).apply {
            text = "Start Relay (Day 2 Test)"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0D47A1"))
            textSize = 16f
            layoutParams = buttonParams
            setOnClickListener {
                Log.i(TAG, "Start Relay button clicked")
                relayManager.startRelay()
            }
        }

        val btnStop = Button(this).apply {
            text = "Stop Relay"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#B71C1C"))
            textSize = 16f
            layoutParams = buttonParams
            setOnClickListener {
                Log.i(TAG, "Stop Relay button clicked")
                relayManager.stopRelay()
            }
        }

        layout.addView(tvTitle)
        layout.addView(btnStart)
        layout.addView(btnStop)

        setContentView(layout)
        Log.i(TAG, "MainActivity onCreate completed successfully")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::relayManager.isInitialized) {
            relayManager.stopRelay()
        }
    }
}


