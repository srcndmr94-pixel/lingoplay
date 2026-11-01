package com.example.subtitles

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun ApiKeyScreen(
    store: ApiKeyStore,
    onDone: () -> Unit = {}
) {
    var apiKey by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(store.hasKey()) }
    var message by remember { mutableStateOf(if (saved) "API anahtarı cihazda kayıtlı." else "") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Opsiyonel API anahtarı", style = MaterialTheme.typography.headlineSmall)
        Text("Çevrimdışı ML Kit çevirisi anahtar gerektirmez. Bu alan yalnızca ileride kullanacağınız isteğe bağlı çevrimiçi sağlayıcı içindir.")

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API anahtarı") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = apiKey.isNotBlank(),
                onClick = {
                    store.save(apiKey)
                    apiKey = ""
                    saved = true
                    message = "API anahtarı şifrelenerek kaydedildi."
                    onDone()
                }
            ) { Text("Kaydet") }

            OutlinedButton(
                enabled = saved,
                onClick = {
                    store.clear()
                    apiKey = ""
                    saved = false
                    message = "Kayıtlı API anahtarı silindi."
                }
            ) { Text("Sil") }
        }

        if (message.isNotBlank()) Text(message)
    }
}
