package com.jc.aura

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuraSystemDeepInfoModule(private val context: Context, private val memory: AuraMemory) {

    suspend fun handle(command: String): String {
        return when {
            command.contains("bateria") || command.contains("battery") -> getBatteryInfo()
            command.contains("memória") || command.contains("ram") || command.contains("memoria") -> getMemoryInfo()
            command.contains("armazenamento") || command.contains("storage") || command.contains("espaço") -> getStorageInfo()
            command.contains("internet") || command.contains("conexão") || command.contains("rede") || command.contains("wifi") -> getNetworkInfo()
            command.contains("sistema") || command.contains("android") || command.contains("telefone") || command.contains("dispositivo") -> getSystemInfo()
            command.contains("apps instaladas") || command.contains("aplicativos") -> getInstalledApps()
            command.contains("brilho") && (command.contains("aumentar") || command.contains("máximo")) -> setBrightness(255)
            command.contains("brilho") && (command.contains("diminuir") || command.contains("mínimo")) -> setBrightness(50)
            command.contains("tudo") || command.contains("status") || command.contains("relatório") -> getFullReport()
            else -> getFullReport()
        }
    }

    private fun getBatteryInfo(): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = bm.isCharging
        val voltage = bm.getIntProperty(1) / 1000f
        val temp = run {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = context.registerReceiver(null, filter)
            (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        }
        val status = if (isCharging) "A carregar ⚡" else if (level > 20) "Descarregando 🔋" else "Bateria baixa ⚠️"
        return "Senhor, estado da bateria:\n• Nível: **$level%** ($status)\n• Voltagem: ${voltage}V\n• Temperatura: ${temp}°C"
    }

    private fun getMemoryInfo(): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val total = memInfo.totalMem / (1024 * 1024)
        val available = memInfo.availMem / (1024 * 1024)
        val used = total - available
        val pct = (used.toFloat() / total * 100).toInt()
        return "Senhor, memória RAM:\n• Total: **${total}MB**\n• Em uso: **${used}MB** ($pct%)\n• Disponível: **${available}MB**"
    }

    private fun getStorageInfo(): String {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes / (1024 * 1024 * 1024)
        val available = stat.availableBytes / (1024 * 1024 * 1024)
        val used = total - available
        val statExt = StatFs(Environment.getExternalStorageDirectory().path)
        val extTotal = statExt.totalBytes / (1024 * 1024 * 1024)
        val extAvail = statExt.availableBytes / (1024 * 1024 * 1024)
        return "Senhor, armazenamento:\n• Interno: **${used}GB usado** de ${total}GB (${available}GB livre)\n• Cartão SD: ${extTotal}GB total, ${extAvail}GB livre"
    }

    private fun getNetworkInfo(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
        return if (caps != null) {
            val type = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi 📶"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Dados Móveis 📱"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet 🔌"
                else -> "Outro"
            }
            val speed = caps.linkDownstreamBandwidthKbps / 1000
            "Senhor, rede:\n• Tipo: **$type**\n• Velocidade estimada: **${speed}Mbps**\n• Internet: ✅ Conectado"
        } else {
            "Senhor, não está conectado à internet. ❌"
        }
    }

    private fun getSystemInfo(): String {
        return "Senhor, informações do sistema:\n" +
            "• Dispositivo: **${Build.MANUFACTURER} ${Build.MODEL}**\n" +
            "• Android: **${Build.VERSION.RELEASE}** (API ${Build.VERSION.SDK_INT})\n" +
            "• Build: ${Build.DISPLAY}\n" +
            "• CPU: ${Build.HARDWARE}\n" +
            "• Processadores: ${Runtime.getRuntime().availableProcessors()} núcleos"
    }

    private suspend fun getInstalledApps(): String = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(0)
            .filter { it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 }
            .mapNotNull { pm.getApplicationLabel(it)?.toString() }
            .sorted()
            .take(20)
        "Senhor, tem **${apps.size}** apps de utilizador instaladas (primeiras 20):\n${apps.joinToString(", ")}"
    }

    private fun setBrightness(value: Int): String {
        return try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
            val pct = (value / 255f * 100).toInt()
            "Senhor, brilho ajustado para $pct%."
        } catch (e: Exception) {
            "Senhor, preciso de permissão para alterar o brilho. Vá a Definições > Acessibilidade > Aura e ative 'Modificar definições do sistema'."
        }
    }

    private fun getFullReport(): String {
        return "${getBatteryInfo()}\n\n${getMemoryInfo()}\n\n${getNetworkInfo()}"
    }
}
