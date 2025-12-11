package dji.sampleV5.aircraft

import android.content.Context
import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Class Description
 *
 * @author Hoker
 * @date 2022/3/2
 *
 * Copyright (c) 2022, DJI All Rights Reserved.
 */
class DJIAircraftApplication : DJIApplication() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        // Try to install the helper and catch any exception to notify the user safely.
        try {
            com.cySdkyc.clx.Helper.install(this)
        } catch (t: Throwable) {
            // Log the full error locally for debugging.
            Log.e("DJIAircraftApplication", "Helper.install failed", t)

            // Use the provided base context if available, otherwise use application context.
            val ctx = base ?: this

            // We must show UI only from the main thread. Post to main looper if needed.
            val mainHandler = Handler(Looper.getMainLooper())
            mainHandler.post {
                if (ctx is Activity && !ctx.isFinishing) {
                    // If we have an Activity context, show a dialog with the error (English message).
                    try {
                        AlertDialog.Builder(ctx)
                            .setTitle("Initialization Error")
                            .setMessage(t.toString())
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    } catch (_: Throwable) {
                        // If showing a dialog fails for any reason, fall back to a Toast.
                        Toast.makeText(ctx.applicationContext, "Failed to initialize helper: ${t.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    // Not an Activity context: show a Toast using application context (English message).
                    try {
                        Toast.makeText(ctx.applicationContext, "Failed to initialize helper: ${t.message}", Toast.LENGTH_LONG).show()
                    } catch (_: Throwable) {
                        // last-resort: log
                        Log.e("DJIAircraftApplication", "Toast failed to show", t)
                    }
                }
            }
        }
    }
}