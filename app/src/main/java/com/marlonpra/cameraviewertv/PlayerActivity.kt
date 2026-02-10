package com.marlonpra.cameraviewertv

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import android.os.Build
import android.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

class PlayerActivity : FragmentActivity() {

    private var libVlc: LibVLC? = null
    private val players = ArrayList<MediaPlayer>()
    private val videoLayouts = ArrayList<VLCVideoLayout>()
    private var mode: Int = 1
    private var urls: List<String> = emptyList()
    private var currentIndex: Int = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var container: LinearLayout? = null
    private var rows: Int = 1
    private var cols: Int = 1
    private var cellCount: Int = 1
    private var selectedUrlIndices: IntArray = intArrayOf()
    private var rowLayouts: ArrayList<LinearLayout> = arrayListOf()
    private val cellWrappers = ArrayList<FrameLayout>()
    private val retryStates = HashMap<MediaPlayer, RetryState>()
    private val longPressTimeoutMs = 550L
    private var okLongPressFired = false
    private var okLongPressRunnable: Runnable? = null

    private val dpadLongPressTimeoutMs = 350L
    private var dpadLongPressFired = false
    private var dpadLongPressRunnable: Runnable? = null
    private var dpadActiveDelta: Int = 0

    private data class RetryState(
        val preferredUrl: String,
        val originalUrl: String,
        var attempt: Int = 0,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        container = findViewById(R.id.playerContainer)

        mode = intent.getIntExtra(EXTRA_MODE, 1)
        urls = intent.getStringArrayListExtra(EXTRA_URLS) ?: arrayListOf()
        currentIndex = intent.getIntExtra(EXTRA_INDEX, 0).coerceAtLeast(0)

        val (r, c, count) = when (mode) {
            2 -> Triple(1, 2, 2)
            4 -> {
                val n = urls.size.coerceIn(1, 4)
                when (n) {
                    1 -> Triple(1, 1, 1)
                    2 -> Triple(1, 2, 2)
                    3 -> Triple(2, 2, 3)
                    else -> Triple(2, 2, 4)
                }
            }
            else -> Triple(1, 1, 1)
        }
        rows = r
        cols = c
        cellCount = count

        val store = RtspStore(this)
        val saved = store.getSelectedIndicesForMode(mode)
        selectedUrlIndices = if (saved != null && saved.isNotEmpty()) {
            IntArray(cellCount) { i -> (saved.getOrNull(i) ?: i).coerceAtMost((urls.size - 1).coerceAtLeast(0)) }
        } else {
            IntArray(cellCount) { it.coerceAtMost((urls.size - 1).coerceAtLeast(0)) }
        }
    }

    override fun onStart() {
        super.onStart()
        if (mode == 1) {
            RtspStore(this).setLastSingleIndex(currentIndex)
        }
        ensurePlayers()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (mode == 1 && urls.isNotEmpty()) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    switchSingle(+1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    switchSingle(-1)
                    return true
                }
            }
        } else if (mode != 1 && urls.isNotEmpty()) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT -> {
                    val delta = if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) +1 else -1

                    if (event?.repeatCount == 0) {
                        dpadLongPressFired = false
                        dpadActiveDelta = delta
                        dpadLongPressRunnable?.let { mainHandler.removeCallbacks(it) }
                        val r = Runnable {
                            dpadLongPressFired = true
                            val focused = currentFocus
                            val cellIndex = (focused?.tag as? Int) ?: -1
                            if (cellIndex >= 0) {
                                mainHandler.post { changeCellCamera(cellIndex, delta) }
                            }
                        }
                        dpadLongPressRunnable = r
                        mainHandler.postDelayed(r, dpadLongPressTimeoutMs)

                        // No consumimos: así el toque corto mueve el foco horizontal.
                        return super.onKeyDown(keyCode, event)
                    }

                    // Si está en long-press, consumimos los repeats para evitar que el foco siga moviéndose.
                    if (dpadLongPressFired && dpadActiveDelta == delta) {
                        val focused = currentFocus
                        val cellIndex = (focused?.tag as? Int) ?: -1
                        if (cellIndex >= 0) {
                            mainHandler.post { changeCellCamera(cellIndex, delta) }
                            return true
                        }
                    }

                    return super.onKeyDown(keyCode, event)
                }

                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    val focused = currentFocus
                    val cellIndex = (focused?.tag as? Int) ?: -1
                    if (cellIndex >= 0) {
                        if (event?.repeatCount == 0) {
                            okLongPressFired = false
                            okLongPressRunnable?.let { mainHandler.removeCallbacks(it) }
                            val r = Runnable {
                                okLongPressFired = true
                                showPickCameraDialog(cellIndex)
                            }
                            okLongPressRunnable = r
                            mainHandler.postDelayed(r, longPressTimeoutMs)
                        }
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    val focused = currentFocus
                    val cellIndex = (focused?.tag as? Int) ?: -1
                    if (cellIndex >= 0) {
                        val newIndex = if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            cellIndex - cols
                        } else {
                            cellIndex + cols
                        }
                        if (newIndex in 0 until cellCount) {
                            val wrapper = cellWrappers[newIndex]
                            wrapper.requestFocus()
                            return true
                        }
                    }
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (mode != 1) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    okLongPressRunnable?.let { mainHandler.removeCallbacks(it) }
                    okLongPressRunnable = null
                    if (!okLongPressFired) {
                        val focused = currentFocus
                        val cellIndex = (focused?.tag as? Int) ?: -1
                        if (cellIndex >= 0) {
                            mainHandler.post { openSingleFromCell(cellIndex) }
                            return true
                        }
                    }
                    return true
                }

                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    dpadLongPressRunnable?.let { mainHandler.removeCallbacks(it) }
                    dpadLongPressRunnable = null
                    dpadLongPressFired = false
                    dpadActiveDelta = 0
                }
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun switchSingle(delta: Int) {
        if (players.isEmpty()) return
        currentIndex = (currentIndex + delta).floorMod(urls.size)
        RtspStore(this).setLastSingleIndex(currentIndex)

        val player = players[0]
        playIndex(player, urls[currentIndex])
        Toast.makeText(this, "${currentIndex + 1}/${urls.size}", Toast.LENGTH_SHORT).show()
    }

    private fun changeCellCamera(cellIndex: Int, delta: Int) {
        if (cellIndex !in 0 until cellCount) return
        if (urls.isEmpty()) return

        val used = HashSet<Int>()
        for (i in 0 until cellCount) {
            if (i != cellIndex) used.add(selectedUrlIndices.getOrNull(i) ?: 0)
        }

        var next = selectedUrlIndices.getOrNull(cellIndex) ?: 0
        var tries = 0
        do {
            next = (next + delta).floorMod(urls.size)
            tries++
        } while (used.contains(next) && tries < urls.size)

        selectedUrlIndices[cellIndex] = next
        RtspStore(this).setSelectedIndicesForMode(mode, selectedUrlIndices)
        val player = players.getOrNull(cellIndex) ?: return
        playIndex(player, pickUrlForMode(urls[next], mode))
        Toast.makeText(this, "${next + 1}/${urls.size}", Toast.LENGTH_SHORT).show()
    }

    private fun openSingleFromCell(cellIndex: Int) {
        val idx = selectedUrlIndices.getOrNull(cellIndex) ?: return
        val i = android.content.Intent(this, PlayerActivity::class.java)
        i.putExtra(EXTRA_MODE, 1)
        i.putExtra(EXTRA_INDEX, idx)
        i.putStringArrayListExtra(EXTRA_URLS, ArrayList(urls))
        startActivity(i)
    }

    private fun showPickCameraDialog(cellIndex: Int) {
        if (urls.isEmpty()) return
        val current = selectedUrlIndices.getOrNull(cellIndex) ?: 0
        AlertDialog.Builder(this)
            .setTitle("Selecciona cámara")
            .setSingleChoiceItems(urls.toTypedArray(), current) { dialog, which ->
                selectedUrlIndices[cellIndex] = which
                RtspStore(this).setSelectedIndicesForMode(mode, selectedUrlIndices)
                players.getOrNull(cellIndex)?.let { p ->
                    playIndex(p, pickUrlForMode(urls[which], mode))
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun playIndex(player: MediaPlayer, url: String) {
        playIndexInternal(player, url, forceTcp = true)
    }

    private fun playIndexInternal(player: MediaPlayer, url: String, forceTcp: Boolean) {
        val vlc = checkNotNull(libVlc)
        val cleaned = url.trim()
        val media = Media(vlc, Uri.parse(cleaned))
        val isEmulator = Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
            Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("Android SDK built for", ignoreCase = true) ||
            Build.PRODUCT.contains("sdk", ignoreCase = true) ||
            Build.PRODUCT.contains("emulator", ignoreCase = true) ||
            Build.BRAND.contains("generic", ignoreCase = true) ||
            Build.DEVICE.contains("generic", ignoreCase = true) ||
            Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
            Build.HARDWARE.contains("ranchu", ignoreCase = true)

        if (isEmulator) {
            media.setHWDecoderEnabled(false, false)
            media.addOption(":avcodec-hw=none")
            media.addOption(":codec=avcodec")
        } else {
            media.setHWDecoderEnabled(true, false)
        }

        if (forceTcp) {
            media.addOption(":rtsp-tcp")
        }
        media.addOption(":network-caching=500")
        media.addOption(":live-caching=500")
        media.addOption(":no-audio")
        media.addOption(":drop-late-frames")
        media.addOption(":skip-frames")
        player.media = media
        media.release()
        player.play()
    }

    override fun onStop() {
        if (mode == 1) {
            RtspStore(this).setLastSingleIndex(currentIndex)
        }
        releasePlayers()
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        releasePlayers()
    }

    private fun releasePlayers() {
        for (p in players) {
            try {
                p.stop()
            } catch (_: Exception) {
            }
            try {
                p.detachViews()
            } catch (_: Exception) {
            }
            try {
                p.release()
            } catch (_: Exception) {
            }
        }
        players.clear()
        videoLayouts.clear()
        cellWrappers.clear()
        retryStates.clear()

        try {
            libVlc?.release()
        } catch (_: Exception) {
        }
        libVlc = null
    }

    private fun ensurePlayers() {
        if (urls.isEmpty()) {
            Toast.makeText(this, "Ingresa o selecciona una URL", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (players.isNotEmpty()) return

        val host = container ?: return
        host.removeAllViews()

        libVlc = LibVLC(
            this,
            arrayListOf(
                "--rtsp-tcp",
                "--network-caching=500",
                "--live-caching=500",
                "--clock-jitter=0",
                "--clock-synchro=0"
            )
        )
        val vlc = checkNotNull(libVlc)

        rowLayouts = ArrayList(rows)
        for (i in 0 until rows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }
            rowLayouts.add(rowLayout)
            host.addView(rowLayout)
        }

        val totalCells = rows * cols
        for (cellIndex in 0 until totalCells) {
            val rowIndex = cellIndex / cols
            if (cellIndex < cellCount) {
                val wrapper = FrameLayout(this).apply {
                    tag = cellIndex
                    isFocusable = true
                    isFocusableInTouchMode = true
                    minimumWidth = 0
                    minimumHeight = 0
                    setPadding(6, 6, 6, 6)
                    setBackgroundColor(0xFF000000.toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1f
                    ).apply {
                        setMargins(2, 2, 2, 2)
                    }
                }

                wrapper.setOnFocusChangeListener { v, hasFocus ->
                    v.setBackgroundColor(if (hasFocus) 0xFF1E88E5.toInt() else 0xFF000000.toInt())
                }

                val cell = VLCVideoLayout(this).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                wrapper.addView(cell)

                val player = MediaPlayer(vlc)
                player.attachViews(cell, null, false, false)

                val urlIndex = selectedUrlIndices.getOrNull(cellIndex) ?: 0
                val originalUrl = urls.getOrNull(urlIndex) ?: urls.first()
                val preferredUrl = pickUrlForMode(originalUrl, mode)

                retryStates[player] = RetryState(
                    preferredUrl = preferredUrl,
                    originalUrl = originalUrl,
                    attempt = 0,
                )
                player.setEventListener { event ->
                    when (event.type) {
                        MediaPlayer.Event.EncounteredError,
                        MediaPlayer.Event.EndReached -> {
                            scheduleRetry(player)
                        }
                    }
                }

                playIndexInternal(player, preferredUrl, forceTcp = true)

                players.add(player)
                videoLayouts.add(cell)
                cellWrappers.add(wrapper)
                rowLayouts.getOrNull(rowIndex)?.addView(wrapper)
            } else {
                // Hueco invisible para mantener la grilla 2x2 sin mostrar un 4to "cuadro".
                val spacer = View(this).apply {
                    isFocusable = false
                    isClickable = false
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1f
                    ).apply {
                        setMargins(2, 2, 2, 2)
                    }
                }
                rowLayouts.getOrNull(rowIndex)?.addView(spacer)
            }
        }

        if (mode != 1) {
            cellWrappers.firstOrNull()?.requestFocus()
        }
    }

    private fun scheduleRetry(player: MediaPlayer) {
        val state = retryStates[player] ?: return

        val step = state.attempt % 4
        val (nextUrl, forceTcp) = when (step) {
            0 -> state.preferredUrl to true
            1 -> state.preferredUrl to false
            2 -> state.originalUrl to true
            else -> state.originalUrl to false
        }

        state.attempt++
        val delay = (750L * (state.attempt.coerceAtMost(6))).coerceAtMost(6000L)

        mainHandler.postDelayed({
            try {
                playIndexInternal(player, nextUrl, forceTcp = forceTcp)
            } catch (_: Exception) {
            }
        }, delay)
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_URLS = "urls"
        const val EXTRA_INDEX = "index"
    }

    private fun pickUrlForMode(url: String, mode: Int): String {
        if (mode == 1) return url
        return toSubStreamUrl(url) ?: url
    }

    private fun toSubStreamUrl(url: String): String? {
        val trimmed = url.trim()
        if (!trimmed.startsWith("rtsp://", ignoreCase = true)) return null

        if (trimmed.contains("subtype=0")) {
            return trimmed.replace("subtype=0", "subtype=1")
        }

        val channelMain = Regex("/Streaming/Channels/([0-9]{2})(1)")
        val m = channelMain.find(trimmed)
        if (m != null) {
            val prefix = m.groupValues[1]
            return trimmed.replace("/Streaming/Channels/${prefix}1", "/Streaming/Channels/${prefix}2")
        }

        if (trimmed.contains("/av0_0")) {
            return trimmed.replace("/av0_0", "/av0_1")
        }

        return null
    }
}

private fun Int.floorMod(mod: Int): Int {
    val r = this % mod
    return if (r < 0) r + mod else r
}
