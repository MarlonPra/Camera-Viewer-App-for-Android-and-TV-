package com.marlonpra.cameraviewertv

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : FragmentActivity() {

    private var rtspInput: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rtspInput = findViewById(R.id.rtspInput)
        val addButton = findViewById<Button>(R.id.addButton)
        val play1Button = findViewById<Button>(R.id.play1Button)
        val play2Button = findViewById<Button>(R.id.play2Button)
        val play4Button = findViewById<Button>(R.id.play4Button)
        val list = findViewById<RecyclerView>(R.id.rtspList)

        val store = RtspStore(this)
        lateinit var adapter: RtspUrlAdapter
        adapter = RtspUrlAdapter(
            onClick = { url -> rtspInput?.setText(url) },
            onDelete = { url ->
                store.removeUrl(url)
                adapter.submit(store.getUrls())
                Toast.makeText(this, "Eliminada", Toast.LENGTH_SHORT).show()
            }
        )

        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        adapter.submit(store.getUrls())

        if (savedInstanceState == null) {
            val lastMode = store.getLastMode()
            val savedUrls = store.getUrls()
            when (lastMode) {
                2 -> if (savedUrls.size >= 2) startPlayer(mode = 2, urls = savedUrls.take(2), index = 0)
                4 -> if (savedUrls.isNotEmpty()) startPlayer(mode = 4, urls = savedUrls.take(4), index = 0)
            }
        }

        addButton.setOnClickListener {
            val url = rtspInput?.text?.toString()?.trim().orEmpty()
            if (url.isEmpty()) {
                Toast.makeText(this, "Ingresa una URL RTSP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!url.startsWith("rtsp://", ignoreCase = true)) {
                Toast.makeText(this, "Debe iniciar con rtsp://", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            addButton.isEnabled = false
            Toast.makeText(this, "Probando...", Toast.LENGTH_SHORT).show()

            Thread {
                val result = RtspProbe.probe(url)
                runOnUiThread {
                    addButton.isEnabled = true
                    if (result.ok) {
                        store.addUrl(url)
                        adapter.submit(store.getUrls())
                        Toast.makeText(this, "Guardada", Toast.LENGTH_SHORT).show()
                    } else {
                        val msg = result.message
                        val allowSave = msg.contains("reset", ignoreCase = true) ||
                            msg.contains("timed out", ignoreCase = true) ||
                            msg.contains("timeout", ignoreCase = true)
                        if (allowSave) {
                            store.addUrl(url)
                            adapter.submit(store.getUrls())
                            Toast.makeText(this, "Guardada (sin verificar)", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "No responde: ${result.message}", Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                }
            }.start()
        }

        play1Button.setOnClickListener {
            val url = rtspInput?.text?.toString()?.trim().orEmpty()
            val all = store.getUrls()
            if (all.isEmpty()) {
                if (url.isEmpty()) {
                    Toast.makeText(this, "Guarda al menos 1 URL", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                startPlayer(mode = 1, urls = listOf(url), index = 0)
                return@setOnClickListener
            }

            val initialIndex = if (url.isNotEmpty()) {
                all.indexOf(url).let { if (it >= 0) it else 0 }
            } else {
                store.getLastSingleIndex().coerceIn(0, all.size - 1)
            }
            startPlayer(mode = 1, urls = all, index = initialIndex)
        }

        play2Button.setOnClickListener {
            val urls = store.getUrls()
            if (urls.size < 2) {
                Toast.makeText(this, "Guarda al menos 2 URLs", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startPlayer(mode = 2, urls = urls, index = 0)
        }

        play4Button.setOnClickListener {
            val urls = store.getUrls()
            if (urls.isEmpty()) {
                Toast.makeText(this, "Guarda al menos 1 URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startPlayer(mode = 4, urls = urls, index = 0)
        }
    }

    private fun startPlayer(mode: Int, urls: List<String>, index: Int) {
        RtspStore(this).setLastMode(mode)
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra(PlayerActivity.EXTRA_MODE, mode)
        intent.putExtra(PlayerActivity.EXTRA_INDEX, index)
        intent.putStringArrayListExtra(PlayerActivity.EXTRA_URLS, ArrayList(urls))
        startActivity(intent)
    }
}