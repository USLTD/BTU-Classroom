package ge.usltd.btuclassroom.ui.screens

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // Check if login was successful
                            // This would need to be customized based on BTU Classroom's actual login flow
                            if (url?.contains("classroom.btu.edu.ge") == true &&
                                !url.contains("login") &&
                                CookieManager.getInstance().getCookie(url)?.isNotEmpty() == true
                            ) {
                                onLoginSuccess()
                            }
                        }
                    }

                    // Load the BTU Classroom login page
                    loadUrl("https://classroom.btu.edu.ge/")
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
