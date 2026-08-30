package fr.pierre.randogpx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import fr.pierre.randogpx.ui.navigation.RandoNavGraph
import fr.pierre.randogpx.ui.theme.RandoGPXTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RandoGPXTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RandoNavGraph()
                }
            }
        }
    }
}
