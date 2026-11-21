
package bd.bloodfordaudkandi.app

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val BloodRed = Color(0xFFB71C35)
private val DeepRed = Color(0xFF850D25)
private val SoftPink = Color(0xFFFFEEF1)
private val PageBg = Color(0xFFF7F7FA)

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

    LaunchedEffect(user?.uid) {
        if (user != null) {
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
                    }
                }
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = BloodRed,
            onPrimary = Color.White,
            secondary = DeepRed,
            background = PageBg,
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
                                .document(donor.id).delete().await()
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
        Modifier.fillMaxSize()
            .background(PageBg).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(Color.White, RoundedCornerShape(28.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🩸", style = MaterialTheme.typography.displayMedium)
            Text(
                "Blood for Daudkandi",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = DeepRed
            )
            Text("রক্তদাতা ব্যবস্থাপনা", color = Color.Gray)

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                email,
                { email = it },
                label = { Text("ইমেইল") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                password,
                { password = it },
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

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { onLogin(email, password) },
                enabled = !loading &&
                    email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (loading) "অপেক্ষা করুন..." else "লগইন")
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "অ্যাডমিন অ্যাকাউন্ট Firebase Console থেকে পরিচালিত হবে।",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
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
    var activeFilter by remember { mutableStateOf("সব") }

    val groups = listOf(
        "সব", "A+", "A-", "B+", "B-",
        "AB+", "AB-", "O+", "O-"
    )

    val filtered = donors.filter {
        (group == "সব" || it.bloodGroup == group) &&
        (activeFilter == "সব" ||
            (activeFilter == "সক্রিয়" && it.active) ||
            (activeFilter == "নিষ্ক্রিয়" && !it.active)) &&
        (search.isBlank() ||
            it.name.contains(search, true) ||
            it.area.contains(search, true) ||
            it.phone.contains(search))
    }

    val context = LocalContext.current

    Scaffold(
        containerColor = PageBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Blood for Daudkandi",
                            fontWeight = FontWeight.Bold,
                            color = DeepRed
                        )
                        Text(
                            "রক্তদাতা ড্যাশবোর্ড",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(
                            Icons.Default.Logout,
                            contentDescription = "লগআউট",
                            tint = DeepRed
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = BloodRed,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("+ নতুন ডোনার", fontWeight = FontWeight.Bold)
            }
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 96.dp)
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(BloodRed, DeepRed)
                            ),
                            RoundedCornerShape(24.dp)
                        )
                        .padding(20.dp)
                ) {
                    Text(
                        "মানবতার সেবায় পাশে থাকুন",
                        color = Color.White.copy(alpha = .85f)
                    )
                    Text(
                        "রক্তদাতাদের তথ্য",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatBox("মোট ডোনার", donors.size.toString(), Modifier.weight(1f))
                        StatBox("সক্রিয়", donors.count { it.active }.toString(), Modifier.weight(1f))
                        StatBox("নিষ্ক্রিয়", donors.count { !it.active }.toString(), Modifier.weight(1f))
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("নাম, মোবাইল বা এলাকা খুঁজুন") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = Color.White,
                        focusedContainerColor = Color.White
                    )
                )
            }

            item {
                Text("রক্তের গ্রুপ", fontWeight = FontWeight.Bold, color = DeepRed)

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    groups.take(5).forEach { g ->
                        FilterChip(
                            selected = group == g,
                            onClick = { group = g },
                            label = { Text(g) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    groups.drop(5).forEach { g ->
                        FilterChip(
                            selected = group == g,
                            onClick = { group = g },
                            label = { Text(g) }
                        )
                    }
                }
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("সব", "সক্রিয়", "নিষ্ক্রিয়").forEach { f ->
                        FilterChip(
                            selected = activeFilter == f,
                            onClick = { activeFilter = f },
                            label = { Text(f) }
                        )
                    }

                    Spacer(Modifier.weight(1f))
                    Text("${filtered.size} জন", color = Color.Gray)
                }
            }

            if (error.isNotBlank()) {
                item { Text(error, color = MaterialTheme.colorScheme.error) }
            }

            if (loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }

            if (filtered.isEmpty() && !loading) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            "কোনো ডোনার পাওয়া যায়নি",
                            Modifier.padding(24.dp),
                            color = Color.Gray
                        )
                    }
                }
            }

            items(filtered, key = { it.id }) { donor ->
                DonorCard(donor, context, onEdit, onDelete)
            }
        }
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.background(
            Color.White.copy(alpha = .16f),
            RoundedCornerShape(14.dp)
        ).padding(10.dp)
    ) {
        Text(
            value,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            color = Color.White.copy(alpha = .9f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun DonorCard(
    donor: Donor,
    context: Context,
    onEdit: (Donor) -> Unit,
    onDelete: (Donor) -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        donor.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        donor.area.ifBlank { "এলাকা উল্লেখ নেই" },
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Surface(color = SoftPink, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        donor.bloodGroup,
                        Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                        color = BloodRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider(color = PageBg)

            Text("মোবাইল: ${donor.phone}")
            Text("সর্বশেষ রক্তদান: ${donor.lastDonation.ifBlank { "তথ্য নেই" }}")
            Text(
                "সময়: ${elapsedSince(donor.lastDonation)}",
                color = DeepRed,
                fontWeight = FontWeight.SemiBold
            )

            if (donor.notes.isNotBlank()) {
                Text("নোট: ${donor.notes}", color = Color.DarkGray)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (donor.active) Color(0xFFE6F4EA)
                    else Color(0xFFFFEBEE),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        if (donor.active) "সক্রিয়" else "নিষ্ক্রিয়",
                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = if (donor.active) Color(0xFF237A42) else BloodRed,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Spacer(Modifier.weight(1f))

                IconButton(onClick = {
                    val all = """
                        নাম: ${donor.name}
                        মোবাইল: ${donor.phone}
                        রক্তের গ্রুপ: ${donor.bloodGroup}
                        এলাকা: ${donor.area}
                        সর্বশেষ রক্তদান: ${donor.lastDonation}
                        সময়: ${elapsedSince(donor.lastDonation)}
                        অবস্থা: ${if (donor.active) "সক্রিয়" else "নিষ্ক্রিয়"}
                        নোট: ${donor.notes}
                    """.trimIndent()

                    copyToClipboard(context, "ডোনারের সম্পূর্ণ তথ্য", all)
                }) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "সব তথ্য কপি",
                        tint = DeepRed
                    )
                }

                IconButton(onClick = { onEdit(donor) }) {
                    Icon(Icons.Default.Edit, contentDescription = "সম্পাদনা", tint = DeepRed)
                }

                IconButton(onClick = { onDelete(donor) }) {
                    Icon(Icons.Default.Delete, contentDescription = "মুছুন", tint = BloodRed)
                }

                IconButton(onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:${donor.phone}"))
                    )
                }) {
                    Icon(Icons.Default.Call, contentDescription = "কল", tint = Color(0xFF237A42))
                }
            }
        }
    }
}

private fun elapsedSince(dateText: String): String {
    if (dateText.isBlank()) return "তারিখ দেওয়া নেই"

    return try {
        val date = LocalDate.parse(dateText.trim())
        val days = ChronoUnit.DAYS.between(date, LocalDate.now())

        if (days < 0) {
            "ভবিষ্যতের তারিখ"
        } else {
            val months = days / 30
            val remainingDays = days % 30

            if (months > 0) {
                "$months মাস $remainingDays দিন"
            } else {
                "$days দিন"
            }
        }
    } catch (_: Exception) {
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
            Text(if (initial == null) "নতুন ডোনার" else "ডোনারের তথ্য সম্পাদনা")
        },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    name, { name = it },
                    label = { Text("পূর্ণ নাম *") },
                    singleLine = true
                )

                OutlinedTextField(
                    phone, { phone = it },
                    label = { Text("মোবাইল নম্বর *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )

                Box {
                    OutlinedButton(onClick = { expanded = true }) {
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
                    singleLine = true
                )

                OutlinedTextField(
                    last, { last = it },
                    label = { Text("সর্বশেষ রক্তদানের তারিখ (YYYY-MM-DD)") },
                    singleLine = true
                )

                OutlinedTextField(
                    notes, { notes = it },
                    label = { Text("অতিরিক্ত নোট") },
                    minLines = 2
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(active, { active = it })
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
                            initial?.id ?: "",
                            name,
                            phone,
                            group,
                            area,
                            last,
                            active,
                            notes
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

    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))

    Toast.makeText(
        context,
        "$label কপি হয়েছে",
        Toast.LENGTH_SHORT
    ).show()
}
