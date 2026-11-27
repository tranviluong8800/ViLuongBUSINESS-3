package com.viluong.business

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                ViLuongBusinessApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViLuongBusinessApp() {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    var stateData by remember { mutableStateOf(LocalStore.load(context)) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddOrderDialog by remember { mutableStateOf(false) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var voiceTargetField by remember { mutableStateOf<String?>(null) }

    // Form trạng thái thêm đơn hàng
    var customerName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var productName by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("1") }
    var unitPriceText by remember { mutableStateOf("") }
    var depositText by remember { mutableStateOf("0") }
    var note by remember { mutableStateOf("") }

    // Form trạng thái thêm chi phí
    var expenseTitle by remember { mutableStateOf("") }
    var expenseAmountText by remember { mutableStateOf("") }
    var expenseNote by remember { mutableStateOf("") }

    fun notify(msg: String) {
        (context as? Activity)?.runOnUiThread {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) {
                when (voiceTargetField) {
                    "customer" -> customerName = spoken
                    "product" -> productName = spoken
                    "note" -> note = spoken
                    "expense_title" -> expenseTitle = spoken
                    "expense_amount" -> {
                        val digits = spoken.replace(Regex("[^0-9]"), "")
                        if (digits.isNotEmpty()) expenseAmountText = digits
                    }
                    "unit_price" -> {
                        val digits = spoken.replace(Regex("[^0-9]"), "")
                        if (digits.isNotEmpty()) unitPriceText = digits
                    }
                }
                notify("Đã nhận giọng nói: $spoken")
            }
        }
        voiceTargetField = null
    }

    fun startVoiceInput(target: String) {
        voiceTargetField = target
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("vi", "VN"))
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói nội dung cần nhập...")
        }
        try {
            voiceLauncher.launch(intent)
        } catch (_: Exception) {
            notify("Thiết bị không hỗ trợ nhận diện giọng nói.")
        }
    }

    fun updateData(newData: AppStateData) {
        stateData = newData
        LocalStore.save(context, newData)
    }

    val totalRevenue = stateData.orders
        .filter { it.status != OrderStatus.CANCELLED }
        .sumOf { it.totalAmount }
    val totalExpense = stateData.expenses.sumOf { it.amount }
    val netProfit = totalRevenue - totalExpense

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Vĩ Lương Business", fontWeight = FontWeight.Bold)
                        Text(
                            "Doanh thu: ${formatVnd(totalRevenue)} • Lãi ròng: ${formatVnd(netProfit)}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.ReceiptLong, contentDescription = null) },
                    label = { Text("Đơn hàng") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Savings, contentDescription = null) },
                    label = { Text("Thu chi & Lãi") }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTab == 0) {
                        customerName = ""
                        phone = ""
                        productName = ""
                        quantityText = "1"
                        unitPriceText = ""
                        depositText = "0"
                        note = ""
                        showAddOrderDialog = true
                    } else {
                        expenseTitle = ""
                        expenseAmountText = ""
                        expenseNote = ""
                        showAddExpenseDialog = true
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Thêm mới")
            }
        }
    ) { innerPadding ->
        if (selectedTab == 0) {
            if (stateData.orders.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Chưa có đơn hàng nào.", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Bấm nút + ở góc dưới để tạo đơn hoặc đọc giọng nói.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(stateData.orders, key = { it.id }) { order ->
                        OrderCard(
                            order = order,
                            onStatusChange = { newStatus ->
                                val updated = stateData.orders.map {
                                    if (it.id == order.id) it.copy(status = newStatus) else it
                                }
                                updateData(stateData.copy(orders = updated))
                            },
                            onPdfZalo = {
                                ActionsHelper.exportOrderPdfAndShare(
                                    context = context,
                                    order = order,
                                    preferZalo = true,
                                    onMessage = { notify(it) }
                                )
                            },
                            onPrintBt = {
                                ActionsHelper.printOrderBluetooth(
                                    context = context,
                                    order = order,
                                    onMessage = { notify(it) }
                                )
                            },
                            onDelete = {
                                val updated = stateData.orders.filterNot { it.id == order.id }
                                updateData(stateData.copy(orders = updated))
                                notify("Đã xóa đơn ${order.id}")
                            }
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Tổng kết tài chính", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Tổng doanh thu: ${formatVnd(totalRevenue)}")
                            Text("Tổng chi phí: ${formatVnd(totalExpense)}")
                            Text(
                                "Lợi nhuận ròng: ${formatVnd(netProfit)}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }

                items(stateData.expenses, key = { it.id }) { exp ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(exp.title, fontWeight = FontWeight.Bold)
                                Text(formatDateTime(exp.createdAt), style = MaterialTheme.typography.bodySmall)
                                if (exp.note.isNotBlank()) {
                                    Text(exp.note, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Text(
                                "-${formatVnd(exp.amount)}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            IconButton(onClick = {
                                val updated = stateData.expenses.filterNot { it.id == exp.id }
                                updateData(stateData.copy(expenses = updated))
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Xóa chi phí")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddOrderDialog) {
        AlertDialog(
            onDismissRequest = { showAddOrderDialog = false },
            title = { Text("Tạo đơn hàng mới") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text("Tên khách hàng") },
                        trailingIcon = {
                            IconButton(onClick = { startVoiceInput("customer") }) {
                                Icon(Icons.Default.Mic, contentDescription = "Nói tên khách")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Số điện thoại") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = productName,
                        onValueChange = { productName = it },
                        label = { Text("Máy móc / Sản phẩm") },
                        trailingIcon = {
                            IconButton(onClick = { startVoiceInput("product") }) {
                                Icon(Icons.Default.Mic, contentDescription = "Nói tên hàng")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = { quantityText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("SL") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.35f)
                        )
                        OutlinedTextField(
                            value = unitPriceText,
                            onValueChange = { unitPriceText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Đơn giá (đ)") },
                            trailingIcon = {
                                IconButton(onClick = { startVoiceInput("unit_price") }) {
                                    Icon(Icons.Default.Mic, contentDescription = "Nói giá")
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.65f)
                        )
                    }
                    OutlinedTextField(
                        value = depositText,
                        onValueChange = { depositText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Đặt cọc (đ)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Ghi chú") },
                        trailingIcon = {
                            IconButton(onClick = { startVoiceInput("note") }) {
                                Icon(Icons.Default.Mic, contentDescription = "Nói ghi chú")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val nameClean = customerName.trim().ifBlank { "Khách lẻ" }
                        val prodClean = productName.trim().ifBlank { "Thiết bị / Máy móc" }
                        val qty = quantityText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        val price = unitPriceText.toDoubleOrNull() ?: 0.0
                        val deposit = depositText.toDoubleOrNull() ?: 0.0
                        val shortCode = "VL-${UUID.randomUUID().toString().take(5).uppercase()}"

                        val newOrder = OrderItem(
                            id = shortCode,
                            customerName = nameClean,
                            phone = phone.trim(),
                            productName = prodClean,
                            quantity = qty,
                            unitPrice = price,
                            depositAmount = deposit,
                            note = note.trim()
                        )
                        updateData(stateData.copy(orders = listOf(newOrder) + stateData.orders))
                        showAddOrderDialog = false
                        notify("Đã lưu đơn $shortCode")
                    }
                ) {
                    Text("Lưu đơn")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddOrderDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    if (showAddExpenseDialog) {
        AlertDialog(
            onDismissRequest = { showAddExpenseDialog = false },
            title = { Text("Thêm khoản chi phí") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = expenseTitle,
                        onValueChange = { expenseTitle = it },
                        label = { Text("Khoản chi") },
                        trailingIcon = {
                            IconButton(onClick = { startVoiceInput("expense_title") }) {
                                Icon(Icons.Default.Mic, contentDescription = "Nói khoản chi")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = expenseAmountText,
                        onValueChange = { expenseAmountText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Số tiền (đ)") },
                        trailingIcon = {
                            IconButton(onClick = { startVoiceInput("expense_amount") }) {
                                Icon(Icons.Default.Mic, contentDescription = "Nói số tiền")
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = expenseNote,
                        onValueChange = { expenseNote = it },
                        label = { Text("Ghi chú") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = expenseAmountText.toDoubleOrNull() ?: 0.0
                        if (amount > 0.0) {
                            val item = ExpenseItem(
                                id = UUID.randomUUID().toString().take(8),
                                title = expenseTitle.trim().ifBlank { "Chi phí phát sinh" },
                                amount = amount,
                                note = expenseNote.trim()
                            )
                            updateData(stateData.copy(expenses = listOf(item) + stateData.expenses))
                            showAddExpenseDialog = false
                            notify("Đã ghi nhận chi phí")
                        } else {
                            notify("Vui lòng nhập số tiền lớn hơn 0.")
                        }
                    }
                ) {
                    Text("Lưu")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddExpenseDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
fun OrderCard(
    order: OrderItem,
    onStatusChange: (OrderStatus) -> Unit,
    onPdfZalo: () -> Unit,
    onPrintBt: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${order.id} • ${order.customerName}", fontWeight = FontWeight.Bold)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Xóa đơn")
                }
            }
            Text("Mặt hàng: ${order.productName} (SL: ${order.quantity})")
            Text("Tổng tiền: ${formatVnd(order.totalAmount)} • Cọc: ${formatVnd(order.depositAmount)}")
            Text("Còn lại: ${formatVnd(order.remainingAmount)}", fontWeight = FontWeight.SemiBold)
            Text(formatDateTime(order.createdAt), style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OrderStatus.entries.forEach { st ->
                    FilterChip(
                        selected = order.status == st,
                        onClick = { onStatusChange(st) },
                        label = { Text(st.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onPdfZalo, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PDF / Zalo")
                }
                OutlinedButton(onClick = onPrintBt, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Print, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("In BT")
                }
            }
        }
    }
}
