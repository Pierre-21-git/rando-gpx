package com.tonapp.rando

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.tonapp.rando.ui.navigation.RandoNavGraph
import com.tonapp.rando.ui.theme.RandoGPXTheme

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
