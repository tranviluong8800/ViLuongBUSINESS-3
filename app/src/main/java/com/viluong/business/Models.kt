package com.viluong.business

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class OrderStatus(val label: String) {
    PENDING("Chờ xử lý"),
    SHIPPING("Đang giao"),
    COMPLETED("Hoàn tất"),
    CANCELLED("Đã hủy")
}

data class OrderItem(
    val id: String,
    val customerName: String,
    val phone: String = "",
    val productName: String,
    val quantity: Int = 1,
    val unitPrice: Double = 0.0,
    val depositAmount: Double = 0.0,
    val note: String = "",
    val status: OrderStatus = OrderStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
) {
    val totalAmount: Double
        get() = quantity * unitPrice

    val remainingAmount: Double
        get() = (totalAmount - depositAmount).coerceAtLeast(0.0)
}

data class ExpenseItem(
    val id: String,
    val title: String,
    val category: String = "Chi phí vận hành",
    val amount: Double,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class AppStateData(
    val orders: List<OrderItem> = emptyList(),
    val expenses: List<ExpenseItem> = emptyList()
)

object LocalStore {
    private const val FILE_NAME = "viluong_business_data.json"
    private val gson = Gson()

    fun load(context: Context): AppStateData {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return AppStateData()
            val json = file.readText(Charsets.UTF_8)
            val type = object : TypeToken<AppStateData>() {}.type
            gson.fromJson<AppStateData>(json, type) ?: AppStateData()
        } catch (_: Exception) {
            AppStateData()
        }
    }

    fun save(context: Context, data: AppStateData) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(gson.toJson(data), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }
}

fun formatVnd(amount: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale("vi", "VN"))
    return "${formatter.format(amount.toLong())} đ"
}

fun formatDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN"))
    return sdf.format(Date(timestamp))
}
