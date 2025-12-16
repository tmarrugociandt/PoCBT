package dji.sampleV5.aircraft
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import com.pedro.rtplibrary.rtmp.RtmpCamera2
import com.pedro.rtplibrary.view.OpenGlView

class OpenCameraActivity : AppCompatActivity() {

    private lateinit var rtmpCamera: RtmpCamera2
    private lateinit var openGlView: OpenGlView
    private lateinit var startButton: Button
    private var isStreaming = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_open_camera)

        openGlView = findViewById(R.id.gl_view)
        startButton = findViewById(R.id.start_button)

        rtmpCamera = RtmpCamera2(openGlView, this)

        startButton.setOnClickListener {
            if (isStreaming) {
                stopStreaming()
            } else {
                startStreaming("rtmp://your.rtmp.server/live/stream") // Cambia esto por tu URL RTMP
            }
        }

        requestCameraPermission()
    }

    private fun startStreaming(url: String) {
        if (rtmpCamera.isStreaming) {
            Toast.makeText(this, "Ya está transmitiendo", Toast.LENGTH_SHORT).show()
            return
        }
        rtmpCamera.prepareAudio() // Prepara el audio
        rtmpCamera.prepareVideo(1280, 720, 30, 2000, 1) // Prepara el video
        if (rtmpCamera.connect(url)) {
            isStreaming = true
            startButton.text = "Detener Streaming"
        }
    }

    private fun stopStreaming() {
        rtmpCamera.stopStream()
        isStreaming = false
        startButton.text = "Iniciar Streaming"
    }

    private fun requestCameraPermission() {
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
            }
        }

        when {
            checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(openGlView.surfaceProvider)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview)
            } catch (exc: Exception) {
                Log.e("OpenCameraActivity", "Error al iniciar la cámara: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        rtmpCamera.stopStream()
    }
}