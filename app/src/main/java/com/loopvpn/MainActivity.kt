package com.loopvpn

import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LoopVpnScreen() }
    }
}

private data class ServerProfile(
    val name: String,
    val protocol: String,
    val address: String,
    // Kept only in memory in this MVP. Do not log or display the credential/config.
    val source: String
)

@Composable
private fun LoopVpnScreen() {
    val profiles = remember { mutableStateListOf<ServerProfile>() }
    var input by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Импортируйте ��сылку или конфиг сервера") }
    var selected by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val contents = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                if (contents.isNullOrBlank()) {
                    message = "Не удалось прочитать файл"
                } else {
                    input = contents
                    message = "Файл загружен — нажмите «Добавить серверы»"
                }
            }
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("LoopVpn", style = MaterialTheme.typography.headlineLarge)
                Text("Ваши конфиги и серверы — в одном месте", style = MaterialTheme.typography.bodyLarge)

                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Подключение", style = MaterialTheme.typography.titleMedium)
                        Text("${selected ?: "Сервер не выбран"} · VPN-движок ещё не подключён")
                        Button(onClick = { message = "Импорт и выбор серверов доступны. Подключение появится после добавления VPN-движка." }, enabled = selected != null, modifier = Modifier.fillMaxWidth()) {
                            Text("Подключить")
                        }
                    }
                }

                Text("Добавить сервер", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    placeholder = { Text("Вставьте ссылку или конфиг") },
                    supportingText = { Text("Ссылки: VLESS, VMess, Trojan, Shadowsocks. Также конфиги WireGuard/OpenVPN.") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }) { Text("Выбрать файл") }
                    Button(onClick = {
                        val parsed = parseProfiles(input)
                        if (parsed.isEmpty()) {
                            message = "Формат не распознан. Проверьте ссылку или файл конфигурации."
                        } else {
                            parsed.forEach { profile ->
                                if (profiles.none { it.source == profile.source }) profiles.add(profile)
                            }
                            message = "Добавлено серверов: ${parsed.size}"
                            input = ""
                        }
                    }) { Text("Добавить серверы") }
                }
                Text(message, style = MaterialTheme.typography.bodySmall)
                Text("Мои серверы (${profiles.size})", style = MaterialTheme.typography.titleMedium)
                if (profiles.isEmpty()) {
                    Text("Пока пусто. Вставьте конфиг, полученный у вашего провайдера.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(profiles) { profile ->
                            Card(onClick = { selected = profile.name }, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text(profile.name, style = MaterialTheme.typography.titleSmall)
                                        Text("${profile.protocol} · ${profile.address}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (selected == profile.name) Text("Выбран")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text("Конфиги хранятся только в памяти приложения и удаляются при его закрытии. Не вставляйте ключи в недоверенные приложения.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun parseProfiles(raw: String): List<ServerProfile> {
    val lines = raw.lines().map(String::trim).filter(String::isNotEmpty)
    if (lines.isEmpty()) return emptyList()

    // Accept a single WG/OpenVPN configuration file as well as URI-per-line subscriptions.
    if (lines.any { it.startsWith("[Interface]", ignoreCase = true) } && lines.any { it.startsWith("[Peer]", ignoreCase = true) }) {
        return listOf(ServerProfile("WireGuard", "WireGuard", "Конфигурация", raw))
    }
    if (lines.any { it.startsWith("client", ignoreCase = true) } && lines.any { it.startsWith("remote ", ignoreCase = true) }) {
        val host = lines.firstOrNull { it.startsWith("remote ", ignoreCase = true) }?.split(Regex("\\s+"))?.getOrNull(1) ?: "Конфигурация"
        return listOf(ServerProfile("OpenVPN · $host", "OpenVPN", host, raw))
    }

    return lines.mapNotNull(::parseUriProfile)
}

private fun parseUriProfile(value: String): ServerProfile? {
    val scheme = value.substringBefore(":", "").lowercase()
    val protocol = when (scheme) {
        "vless" -> "VLESS"
        "vmess" -> "VMess"
        "trojan" -> "Trojan"
        "ss" -> "Shadowsocks"
        else -> return null
    }

    return runCatching {
        if (scheme == "vmess") {
            val encoded = value.substringAfter("://").substringBefore('#').trim()
            val jsonText = String(Base64.decode(padBase64(encoded), Base64.DEFAULT), Charsets.UTF_8)
            val json = JSONObject(jsonText)
            val host = json.optString("add", "Сервер")
            val name = json.optString("ps", protocol).ifBlank { protocol }
            ServerProfile(name, protocol, host, value)
        } else {
            val uri = Uri.parse(value)
            val host = uri.host ?: value.substringAfter("@", "").substringBefore(':').ifBlank { "Сервер" }
            val label = uri.fragment?.takeIf { it.isNotBlank() }?.let { Uri.decode(it) } ?: "$protocol · $host"
            ServerProfile(label, protocol, host, value)
        }
    }.getOrNull()
}

private fun padBase64(value: String): String = value + "=".repeat((4 - value.length % 4) % 4)
