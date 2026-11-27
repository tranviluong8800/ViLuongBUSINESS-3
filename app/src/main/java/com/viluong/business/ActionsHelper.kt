package com.viluong.business

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ActionsHelper {

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun buildInvoiceText(order: OrderItem): String {
        return buildString {
            appendLine("VĨ LƯƠNG BUSINESS")
            appendLine("HÓA ĐƠN BÁN HÀNG")
            appendLine("-------------------------------")
            appendLine("Mã đơn: ${order.id}")
            appendLine("Ngày: ${formatDateTime(order.createdAt)}")
            appendLine("Khách hàng: ${order.customerName}")
            if (order.phone.isNotBlank()) appendLine("SĐT: ${order.phone}")
            appendLine("Mặt hàng: ${order.productName}")
            appendLine("SL: ${order.quantity} x ${formatVnd(order.unitPrice)}")
            appendLine("Thành tiền: ${formatVnd(order.totalAmount)}")
            appendLine("Đặt cọc: ${formatVnd(order.depositAmount)}")
            appendLine("Còn lại: ${formatVnd(order.remainingAmount)}")
            appendLine("Trạng thái: ${order.status.label}")
            if (order.note.isNotBlank()) appendLine("Ghi chú: ${order.note}")
            appendLine("-------------------------------")
            appendLine("Cảm ơn quý khách!")
        }
    }

    fun exportOrderPdfAndShare(
        context: Context,
        order: OrderItem,
        preferZalo: Boolean = true,
        onMessage: (String) -> Unit
    ) {
        try {
            val dir = File(context.getExternalFilesDir(null), "Invoices")
            if (!dir.exists()) dir.mkdirs()

            val safeId = order.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val pdfFile = File(dir, "HoaDon_${safeId}.pdf")

            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val titlePaint = Paint().apply {
                color = Color.rgb(0, 104, 255)
                textSize = 22f
                isFakeBoldText = true
                isAntiAlias = true
            }
            val textPaint = Paint().apply {
                color = Color.BLACK
                textSize = 15f
                isAntiAlias = true
            }
            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 1.5f
            }

            var y = 60f
            canvas.drawText("VĨ LƯƠNG BUSINESS - HÓA ĐƠN BÁN HÀNG", 45f, y, titlePaint)
            y += 20f
            canvas.drawLine(45f, y, 550f, y, linePaint)
            y += 35f

            val lines = listOf(
                "Mã đơn hàng: ${order.id}",
                "Thời gian: ${formatDateTime(order.createdAt)}",
                "Khách hàng: ${order.customerName}",
                "Số điện thoại: ${order.phone.ifBlank { "Không có" }}",
                "Sản phẩm / Thiết bị: ${order.productName}",
                "Số lượng: ${order.quantity}",
                "Đơn giá: ${formatVnd(order.unitPrice)}",
                "Tổng tiền: ${formatVnd(order.totalAmount)}",
                "Đã đặt cọc: ${formatVnd(order.depositAmount)}",
                "Còn phải thu: ${formatVnd(order.remainingAmount)}",
                "Trạng thái: ${order.status.label}",
                "Ghi chú: ${order.note.ifBlank { "Không có" }}"
            )

            for (line in lines) {
                canvas.drawText(line, 45f, y, textPaint)
                y += 32f
            }

            document.finishPage(page)
            FileOutputStream(pdfFile).use { out ->
                document.writeTo(out)
            }
            document.close()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Hóa đơn ${order.id} - ${order.customerName}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            if (preferZalo && isPackageInstalled(context, "com.zing.zalo")) {
                shareIntent.setPackage("com.zing.zalo")
                context.startActivity(shareIntent)
                onMessage("Đã tạo PDF và mở Zalo để gửi hóa đơn.")
            } else {
                val chooser = Intent.createChooser(shareIntent, "Chia sẻ hóa đơn PDF")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                onMessage("Đã tạo file PDF hóa đơn.")
            }
        } catch (e: Exception) {
            onMessage("Lỗi xuất PDF: ${e.message ?: "Không xác định"}")
        }
    }

    fun printOrderBluetooth(
        context: Context,
        order: OrderItem,
        onMessage: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnect = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasConnect) {
                onMessage("Cần cấp quyền Bluetooth để in hóa đơn.")
                return
            }
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter? = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            onMessage("Vui lòng bật Bluetooth trước khi in.")
            return
        }

        Thread {
            try {
                val pairedDevices = adapter.bondedDevices
                val targetDevice = pairedDevices?.firstOrNull()
                if (targetDevice == null) {
                    onMessage("Chưa có máy in Bluetooth đã ghép đôi.")
                    return@Thread
                }

                val socket = targetDevice.createRfcommSocketToServiceRecord(SPP_UUID)
                adapter.cancelDiscovery()
                socket.connect()
                socket.outputStream.use { out ->
                    val content = buildInvoiceText(order) + "\n\n\n"
                    out.write(content.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
                socket.close()
                onMessage("Đã gửi lệnh in tới ${targetDevice.name ?: "máy in"}." )
            } catch (e: Exception) {
                onMessage("Lỗi in Bluetooth: ${e.message ?: "Không kết nối được máy in"}")
            }
        }.start()
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }
}
