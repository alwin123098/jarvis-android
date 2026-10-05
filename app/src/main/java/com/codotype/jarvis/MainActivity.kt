package com.codotype.jarvis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codotype.jarvis.ui.ChatScreen
import com.codotype.jarvis.ui.JarvisViewModel
import com.codotype.jarvis.ui.SetupScreen
import com.codotype.jarvis.ui.theme.JarvisTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JarvisTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val vm: JarvisViewModel = viewModel(
                        factory = JarvisViewModel.factory(application)
                    )
                    JarvisRoot(vm)
                }
            }
        }
    }
}

@Composable
private fun JarvisRoot(vm: JarvisViewModel) {
    val ui by vm.ui.collectAsState()
    val amplitude by vm.amplitude.collectAsState()

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startListening()
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    val onMicToggle: () -> Unit = {
        when {
            ui.isListening -> vm.stopListeningAndTranscribe()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED -> vm.startListening()
            else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    if (ui.showSetup || !ui.ready) {
        SetupScreen(
            ui = ui,
            onDownload = vm::download,
            onDelete = vm::delete,
            onSelectLlm = vm::selectLlm,
            onContinue = vm::continueToChat
        )
    } else {
        ChatScreen(
            ui = ui,
            amplitude = amplitude,
            onSend = vm::send,
            onStop = vm::stopGeneration,
            onInputChange = vm::onInputChange,
            onMicToggle = onMicToggle,
            onToggleVoice = vm::toggleVoiceOutput,
            onToggleKnowledge = vm::toggleKnowledge,
            onClear = vm::clearConversation,
            onOpenSetup = vm::openSetup
        )
    }
}
