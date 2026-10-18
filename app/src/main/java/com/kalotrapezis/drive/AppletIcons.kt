package com.kalotrapezis.drive

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle

/**
 * Tetra's parts with launcher icons of their own (asked 17 Oct: Settings › App icons — on the home screen and in the
 * app list, each opening straight into its part). Each is an activity-alias of [AppletLaunch], off until switched on.
 */
internal enum class Applet(val label: String, val art: Int) {
    // Home's art for each; the launcher icons are made from the same (res/mipmap-anydpi-v26/applet_*)
    Photos("Photos", R.drawable.gallery_art),
    Files("Files", R.drawable.files_art),
    Notes("Notes", R.drawable.notes_art),
    Scanner("Scanner", R.drawable.scanner_art),
    Codes("Codes", R.drawable.codes_art),
    Office("Office", R.drawable.office_art),
    TextEditor("Text editor", R.drawable.text_editor_art),
    Calendar("Calendar", R.drawable.calendar_art);

    private fun component(context: Context) = ComponentName(context.packageName, "${Applet::class.java.packageName}.App$name")

    fun shown(context: Context) = context.packageManager.getComponentEnabledSetting(component(context)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    /** The icon put in or taken out of the launcher (Tetra keeps running: DONT_KILL_APP). */
    fun show(context: Context, on: Boolean) {
        context.packageManager.setComponentEnabledSetting(component(context),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
        WriterLog.i("App icon ${if (on) "shown" else "hidden"}: $name")
    }

    companion object {
        const val EXTRA = "com.kalotrapezis.drive.APPLET"
        fun of(name: String?) = entries.firstOrNull { it.name == name }
    }
}

/**
 * An applet icon's tap: hands its part to Tetra's one window and goes. A launcher alias of MainActivity itself would
 * only bring a running Tetra to the front where it was; from here MainActivity is asked again, and turns to the part.
 */
class AppletLaunch : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val applet = intent.component?.className?.substringAfterLast(".App")
        startActivity(Intent(this, MainActivity::class.java).putExtra(Applet.EXTRA, applet)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}
