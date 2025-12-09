package dji.sampleV5.aircraft

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dji.sampleV5.aircraft.models.LiveStreamVM

class OpenCameraActivity : AppCompatActivity() {

    private lateinit var liveStreamVM: LiveStreamVM

    private lateinit var backgroundHandler: Handler
    private lateinit var backgroundThread: HandlerThread
    private var surfaceHolder: SurfaceHolder? = null

    private lateinit var btnPlay: Button
    private lateinit var btnPause: Button
    private lateinit var btnStop: Button

    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_open_camera)

        liveStreamVM = LiveStreamVM()

        val surfaceView = findViewById<SurfaceView>(R.id.surfaceView)
        surfaceHolder = surfaceView.holder
        btnPlay = findViewById(R.id.btnPlay)
        btnPause = findViewById(R.id.btnPause)
        btnStop = findViewById(R.id.btnStop)

        surfaceHolder?.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                checkAndRequestCameraPermission()
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                stopBackgroundThread()
                closeCamera()
            }
        })

        btnPlay.setOnClickListener {
            val rtmpUrl = "rtmp://18.130.36.142:1935/demo/live" // Replace with actual RTMP URL
            liveStreamVM.setRTMPConfig(rtmpUrl)
            liveStreamVM.startStream(null)
            Toast.makeText(this, "Streaming started", Toast.LENGTH_SHORT).show()
        }

        btnPause.setOnClickListener {
            Toast.makeText(this, "Pause not supported in RTMP", Toast.LENGTH_SHORT).show()
        }

        btnStop.setOnClickListener {
            liveStreamVM.stopStream(null)
            Toast.makeText(this, "Streaming stopped", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        } else {
            openCamera()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Toast.makeText(this, "Camera permission is required to use this feature", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openCamera() {
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: throw IllegalStateException("No cameras available")
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        startCameraPreview()
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        cameraDevice = null
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        cameraDevice = null
                        Toast.makeText(this@OpenCameraActivity, "Error opening camera: $error", Toast.LENGTH_SHORT).show()
                    }
                }, backgroundHandler)
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to access camera", Toast.LENGTH_SHORT).show()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCameraPreview() {
        try {
            val surface = surfaceHolder?.surface
            if (surface != null && cameraDevice != null) {
                val captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequestBuilder?.addTarget(surface)

                cameraDevice?.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraCaptureSession = session
                        try {
                            captureRequestBuilder?.build()?.let {
                                cameraCaptureSession?.setRepeatingRequest(it, null, backgroundHandler)
                            }
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                            Toast.makeText(this@OpenCameraActivity, "Failed to start camera preview", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Toast.makeText(this@OpenCameraActivity, "Failed to configure camera preview", Toast.LENGTH_SHORT).show()
                    }
                }, backgroundHandler)
            } else {
                Toast.makeText(this, "Surface or CameraDevice is null", Toast.LENGTH_SHORT).show()
            }
        } catch (e: CameraAccessException) {
        e.printStackTrace()
        Toast.makeText(this, "Error starting camera preview", Toast.LENGTH_SHORT).show()
    }
}

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread.looper)
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (surfaceHolder?.surface != null) {
            checkAndRequestCameraPermission()
        }
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    private fun closeCamera() {
        cameraCaptureSession?.close()
        cameraCaptureSession = null
        cameraDevice?.close()
        cameraDevice = null
    }

    override fun onDestroy() {
        super.onDestroy()
        liveStreamVM.stopStream(null)
        closeCamera()
        stopBackgroundThread()
    }

    private fun stopBackgroundThread() {
        backgroundThread.quitSafely()
        try {
            backgroundThread.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }
}
