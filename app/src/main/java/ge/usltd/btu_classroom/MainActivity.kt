package ge.usltd.btu_classroom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import ge.usltd.btu_classroom.app.BtucmAppRoot
import ge.usltd.btu_classroom.app.MainViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { BtucmAppRoot(hiltViewModel<MainViewModel>()) }
  }
}