package com.nobetmaker.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nobetmaker.app.ui.NobetNavHost
import com.nobetmaker.app.ui.NobetTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    /** Uygulama yalnızca Türkçe: tarih seçici gibi hazır bileşenler de Türkçe ve haftası Pazartesi başlar. */
    override fun attachBaseContext(newBase: Context) {
        val tr = Locale.forLanguageTag("tr-TR")
        Locale.setDefault(tr)
        val config = Configuration(newBase.resources.configuration).apply { setLocale(tr) }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NobetTheme {
                NobetNavHost()
            }
        }
    }
}
