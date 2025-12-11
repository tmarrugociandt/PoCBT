package dji.sampleV5.aircraft

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class OpenStreamingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_open_streaming)

        // Insert LiveFragment into the container
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, dji.sampleV5.aircraft.pages.LiveFragmentCamera())
                .commit()
        }
    }
}