
package bd.bloodfordaudkandi.app

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

private val BloodRed = Color(0xFFB71C1C)
private val LightRed = Color(0xFFFFEBEE)
private val DarkText = Color(0xFF292929)

data class Donor(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val bloodGroup: String = "A+",
    val area: String = "",
    val lastDonation: String = "",
    val active: Boolean = true,
    val notes: String = ""
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContent { BloodApp() }
    }
}

@Composable
fun BloodApp() {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var user by remember { mutableStateOf(auth.currentUser) }
    var donors by remember { mutableStateOf<List<Donor>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Donor?>(null) }

    val scope = rememberCoroutineScope()

    fun loadDonors() {
        loading = true
        db.collection("donors")
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, e ->
                loading = false
                if (e != null) {
                    error = e.localizedMessage ?: "তথ্য লোড করা যায়নি"
                } else {
                    donors = snap?.documents?.map { d ->
                        Donor(
                            id = d.id,
                            name = d.getString("name") ?: "",
                            phone = d.getString("phone") ?: "",
                            bloodGroup = d.getString("bloodGroup") ?: "A+",
                            area = d.getString("area") ?: "",
                            lastDonation = d.getString("lastDonation") ?: "",
                            active = d.getBoolean("active") ?: true,
                            notes = d.getString("notes") ?: ""
                        )
                    } ?: emptyList()
                    error = ""
                }
            }
    }

    LaunchedEffect(user?.uid) {
        if (user != null) loadDonors()
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = BloodRed,
            secondary = Color(0xFFD32F2F),
            background = Color(0xFFF7F7F7),
            surface = Color.White
        )
    ) {
        if (user == null) {
            LoginScreen(
                onLogin = { email, password ->
                    scope.launch {
                        loading = true
                        try {
                            auth.signInWithEmailAndPassword(
                                email.trim(), password
                            ).await()
                            user = auth.currentUser
                            error = ""
                        } catch (e: Exception) {
                            error = e.localizedMessage ?: "লগইন ব্যর্থ হয়েছে"
                        }
                        loading = false
                    }
                },
                loading = loading,
                error = error
            )
        } else {
            DonorScreen(
                donors = donors,
                error = error,
                loading = loading,
                onAdd = {
                    editing = null
                    showForm = true
                },
                onEdit = {
                    editing = it
                    showForm = true
                },
                onDelete = { donor ->
                    scope.launch {
                        try {
                            db.collection("donors")
                                .document(donor.id)
                                .delete().await()
                        } catch (e: Exception) {
                            error = e.localizedMessage ?: "মুছতে সমস্যা হয়েছে"
                        }
                    }
                },
                onLogout = {
                    auth.signOut()
                    user = null
                    donors = emptyList()
                }
            )

            if (showForm) {
                DonorForm(
                    initial = editing,
                    onDismiss = { showForm = false },
                    onSave = { donor ->
                        scope.launch {
                            try {
                                val data = mapOf(
                                    "name" to donor.name.trim(),
                                    "phone" to donor.phone.trim(),
                                    "bloodGroup" to donor.bloodGroup,
                                    "area" to donor.area.trim(),
                                    "lastDonation" to donor.lastDonation.trim(),
                                    "active" to donor.active,
                                    "notes" to donor.notes.trim()
                                )

                                if (donor.id.isBlank()) {
                                    db.collection("donors").add(data).await()
                                } else {
                                    db.collection("donors")
                                        .document(donor.id)
                                        .set(data).await()
                                }

                                showForm = false
                                error = ""
                            } catch (e: Exception) {
                                error = e.localizedMessage ?: "সংরক্ষণ করা যায়নি"
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun LoginScreen(
    onLogin: (String, String) -> Unit,
    loading: Boolean,
    error: String
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        Modifier.fillMaxSize().background(LightRed).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(6.dp)
        ) {
            Column(
                Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("🩸", style = MaterialTheme.typography.displayLarge)
                Text(
                    "Blood for Daudkandi",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = BloodRed
                )
                Text("অ্যাডমিন লগইন")

                OutlinedTextField(
                    email, { email = it },
                    label = { Text("ইমেইল") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    password, { password = it },
                    label = { Text("পাসওয়ার্ড") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    singleLine = true
                )

                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }

                Button(
                    onClick = { onLogin(email, password) },
                    enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (loading) "অপেক্ষা করুন..." else "লগইন করুন")
                }

                Text(
                    "নতুন অ্যাডমিন অ্যাকাউন্ট সুপার অ্যাডমিন Firebase Console থেকে তৈরি করবেন।",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonorScreen(
    donors: List<Donor>,
    error: String,
    loading: Boolean,
    onAdd: () -> Unit,
    onEdit: (Donor) -> Unit,
    onDelete: (Donor) -> Unit,
    onLogout: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("সব") }
    var status by remember { mutableStateOf("সব") }

    val context = LocalContext.current

    val groups = listOf(
        "সব", "A+", "A-", "B+", "B-",
        "AB+", "AB-", "O+", "O-"
    )

    val filtered = donors.filter {
        (group == "সব" || it.bloodGroup == group) &&
        (status == "সব" ||
            (status == "সক্রিয়" && it.active) ||
            (status == "নিষ্ক্রিয়" && !it.active)) &&
        (
            search.isBlank() ||
            it.name.contains(search, true) ||
            it.area.contains(search, true) ||
            it.phone.contains(search) ||
            it.notes.contains(search, true)
        )
    }

    Scaffold(
        containerColor = Color(0xFFF7F7F7),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "🩸 Blood for Daudkandi",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "ডোনার ম্যানেজমেন্ট",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("লগআউট")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = BloodRed,
                contentColor = Color.White
            ) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        }
    ) { pad ->

        Column(
            Modifier.fillMaxSize()
                .padding(pad)
                .padding(horizontal = 14.dp)
        ) {

            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = BloodRed
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "সর্বমোট ডোনার",
                            color = Color.White
                        )
                        Text(
                            "${donors.size}",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "সক্রিয়: ${donors.count { it.active }}  |  নিষ্ক্রিয়: ${donors.count { !it.active }}",
                            color = Color.White
                        )
                    }

                    Text("🩸", style = MaterialTheme.typography.displayMedium)
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    val allText = donors.joinToString(
                        separator = "\n\n--------------------\n\n"
                    ) { d ->
                        """
                        নাম: ${d.name}
                        মোবাইল: ${d.phone}
                        রক্তের গ্রুপ: ${d.bloodGroup}
                        এলাকা: ${d.area}
                        সর্বশেষ রক্তদান: ${d.lastDonation.ifBlank { "তথ্য নেই" }}
                        রক্তদানের সময়কাল: ${donationDuration(d.lastDonation)}
                        অবস্থা: ${if (d.active) "সক্রিয়" else "নিষ্ক্রিয়"}
                        নোট: ${d.notes.ifBlank { "নেই" }}
                        """.trimIndent()
                    }

                    copyToClipboard(
                        context,
                        "সব ডোনারের তথ্য",
                        if (donors.isEmpty()) "কোনো ডোনার নেই" else allText
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("📋  সব ডোনারের তথ্য একসঙ্গে কপি (${donors.size})")
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("নাম, এলাকা, মোবাইল বা নোট দিয়ে খুঁজুন") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(Modifier.height(8.dp))

            Text("রক্তের গ্রুপ", fontWeight = FontWeight.Bold)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                groups.take(5).forEach { g ->
                    FilterChip(
                        selected = group == g,
                        onClick = { group = g },
                        label = { Text(g) }
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                groups.drop(5).forEach { g ->
                    FilterChip(
                        selected = group == g,
                        onClick = { group = g },
                        label = { Text(g) }
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("সব", "সক্রিয়", "নিষ্ক্রিয়").forEach { s ->
                    FilterChip(
                        selected = status == s,
                        onClick = { status = s },
                        label = { Text(s) }
                    )
                }
            }

            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            Text(
                "দেখানো হচ্ছে: ${filtered.size} জন",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(filtered, key = { it.id }) { donor ->

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        donor.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        donor.area.ifBlank { "এলাকা দেওয়া হয়নি" },
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                AssistChip(
                                    onClick = { group = donor.bloodGroup },
                                    label = {
                                        Text(
                                            donor.bloodGroup,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        labelColor = BloodRed
                                    )
                                )
                            }

                            HorizontalDivider(color = Color.LightGray)

                            Text("📞 মোবাইল: ${donor.phone}")

                            Text(
                                "🩸 সর্বশেষ রক্তদান: ${
                                    donor.lastDonation.ifBlank { "তথ্য নেই" }
                                }"
                            )

                            Text(
                                "⏳ রক্তদানের পর: ${
                                    donationDuration(donor.lastDonation)
                                }",
                                color = BloodRed,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                "অবস্থা: ${if (donor.active) "সক্রিয়" else "নিষ্ক্রিয়"}",
                                color = if (donor.active) Color(0xFF16834A) else Color.Gray
                            )

                            if (donor.notes.isNotBlank()) {
                                Surface(
                                    color = Color(0xFFFFF4E5),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        "📝 নোট: ${donor.notes}",
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        val intent = Intent(
                                            Intent.ACTION_DIAL,
                                            Uri.parse("tel:${donor.phone}")
                                        )
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = null)
                                    Text("কল")
                                }

                                OutlinedButton(
                                    onClick = {
                                        copyToClipboard(
                                            context,
                                            "ডোনারের সব তথ্য",
                                            donorText(donor)
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("কপি")
                                }

                                IconButton(onClick = { onEdit(donor) }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "সম্পাদনা",
                                        tint = BloodRed
                                    )
                                }

                                IconButton(onClick = { onDelete(donor) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "মুছুন",
                                        tint = BloodRed
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun donorText(d: Donor): String {
    return """
        নাম: ${d.name}
        মোবাইল: ${d.phone}
        রক্তের গ্রুপ: ${d.bloodGroup}
        এলাকা: ${d.area}
        সর্বশেষ রক্তদান: ${d.lastDonation.ifBlank { "তথ্য নেই" }}
        রক্তদানের সময়কাল: ${donationDuration(d.lastDonation)}
        অবস্থা: ${if (d.active) "সক্রিয়" else "নিষ্ক্রিয়"}
        নোট: ${d.notes.ifBlank { "নেই" }}
    """.trimIndent()
}

private fun donationDuration(date: String): String {
    if (date.isBlank()) return "তারিখ দেওয়া হয়নি"

    return try {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        formatter.isLenient = false

        val parsed = formatter.parse(date) ?: return "সঠিক তারিখ দিন"

        val last = Calendar.getInstance().apply {
            time = parsed
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (last.after(today)) return "ভবিষ্যতের তারিখ"

        val totalDays = TimeUnit.MILLISECONDS.toDays(
            today.timeInMillis - last.timeInMillis
        )

        var months =
            (today.get(Calendar.YEAR) - last.get(Calendar.YEAR)) * 12 +
            today.get(Calendar.MONTH) - last.get(Calendar.MONTH)

        val anchor = last.clone() as Calendar
        anchor.add(Calendar.MONTH, months)

        if (anchor.after(today)) {
            months--
            anchor.time = last.time
            anchor.add(Calendar.MONTH, months)
        }

        val remainingDays = TimeUnit.MILLISECONDS.toDays(
            today.timeInMillis - anchor.timeInMillis
        )

        val years = months / 12
        val extraMonths = months % 12

        when {
            years > 0 ->
                "$years বছর $extraMonths মাস $remainingDays দিন (মোট $totalDays দিন)"

            months > 0 ->
                "$months মাস $remainingDays দিন (মোট $totalDays দিন)"

            else ->
                "$totalDays দিন"
        }
    } catch (e: Exception) {
        "তারিখ YYYY-MM-DD ফরম্যাটে দিন"
    }
}

@Composable
fun DonorForm(
    initial: Donor?,
    onDismiss: () -> Unit,
    onSave: (Donor) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var group by remember { mutableStateOf(initial?.bloodGroup ?: "A+") }
    var area by remember { mutableStateOf(initial?.area ?: "") }
    var last by remember { mutableStateOf(initial?.lastDonation ?: "") }
    var active by remember { mutableStateOf(initial?.active ?: true) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var expanded by remember { mutableStateOf(false) }

    val groups = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) "নতুন ডোনার যুক্ত করুন"
                else "ডোনারের তথ্য সম্পাদনা"
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    name, { name = it },
                    label = { Text("পূর্ণ নাম *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    phone, { phone = it },
                    label = { Text("মোবাইল নম্বর *") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone
                    ),
                    singleLine = true
                )

                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("রক্তের গ্রুপ: $group")
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        groups.forEach { g ->
                            DropdownMenuItem(
                                text = { Text(g) },
                                onClick = {
                                    group = g
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    area, { area = it },
                    label = { Text("এলাকা / ঠিকানা") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    last, { last = it },
                    label = { Text("সর্বশেষ রক্তদানের তারিখ") },
                    placeholder = { Text("2026-10-15") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    "তারিখের ফরম্যাট: বছর-মাস-দিন (YYYY-MM-DD)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                Text(
                    "রক্তদানের পর সময়: ${donationDuration(last)}",
                    color = BloodRed,
                    fontWeight = FontWeight.Bold
                )

                HorizontalDivider()

                Text(
                    "অন্যান্য নোট",
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("অতিরিক্ত তথ্য / নোট লিখুন") },
                    placeholder = {
                        Text("যেমন: কখন রক্ত দিতে পারবেন, বিশেষ তথ্য ইত্যাদি")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = active,
                        onCheckedChange = { active = it }
                    )
                    Text("সক্রিয় ডোনার")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && phone.isNotBlank(),
                onClick = {
                    onSave(
                        Donor(
                            id = initial?.id ?: "",
                            name = name,
                            phone = phone,
                            bloodGroup = group,
                            area = area,
                            lastDonation = last,
                            active = active,
                            notes = notes
                        )
                    )
                }
            ) {
                Text("সংরক্ষণ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}

private fun copyToClipboard(
    context: Context,
    label: String,
    value: String
) {
    val clipboard =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    clipboard.setPrimaryClip(
        ClipData.newPlainText(label, value)
    )

    Toast.makeText(
        context,
        "$label কপি হয়েছে",
        Toast.LENGTH_SHORT
    ).show()
}
