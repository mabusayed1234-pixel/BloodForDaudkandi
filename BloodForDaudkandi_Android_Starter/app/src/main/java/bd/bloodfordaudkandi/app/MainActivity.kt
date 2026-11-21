
package bd.bloodfordaudkandi.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.DisposableEffect
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val Red = Color(0xFFB71C1C)
private val DarkRed = Color(0xFF7F0000)
private val LightRed = Color(0xFFFFEBEE)
private val BackgroundColor = Color(0xFFF7F7F9)
private val Green = Color(0xFF16864A)
private val Blue = Color(0xFF1769AA)
private val TextDark = Color(0xFF252525)

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

        setContent {
            BloodTheme {
                BloodApp()
            }
        }
    }
}

@Composable
fun BloodTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Red,
            onPrimary = Color.White,
            secondary = Green,
            background = BackgroundColor,
            surface = Color.White,
            error = DarkRed
        ),
        content = content
    )
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

    DisposableEffect(user?.uid) {
        val currentUser = user
        var registration: com.google.firebase.firestore.ListenerRegistration? = null

        if (currentUser != null) {
            loading = true

            registration = db.collection("donors")
                .orderBy("name", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, exception ->

                    loading = false

                    if (exception != null) {
                        error = exception.localizedMessage
                            ?: "ডোনার লোড করা যায়নি"
                    } else {
                        error = ""

                        donors = snapshot?.documents?.map { document ->
                            Donor(
                                id = document.id,
                                name = document.getString("name") ?: "",
                                phone = document.getString("phone") ?: "",
                                bloodGroup = document.getString("bloodGroup") ?: "A+",
                                area = document.getString("area") ?: "",
                                lastDonation = document.getString("lastDonation") ?: "",
                                active = document.getBoolean("active") ?: true,
                                notes = document.getString("notes") ?: ""
                            )
                        } ?: emptyList()
                    }
                }
        }

        onDispose {
            registration?.remove()
        }
    }

    if (user == null) {
        LoginScreen(
            loading = loading,
            error = error,
            onLogin = { email, password ->
                scope.launch {
                    loading = true
                    error = ""

                    try {
                        auth.signInWithEmailAndPassword(
                            email.trim(),
                            password
                        ).await()

                        user = auth.currentUser

                    } catch (e: Exception) {
                        error = e.localizedMessage ?: "লগইন ব্যর্থ হয়েছে"
                    }

                    loading = false
                }
            }
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
                            .delete()
                            .await()

                        error = ""

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
                onDismiss = {
                    showForm = false
                },
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
                                    .set(data)
                                    .await()
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

@Composable
fun LoginScreen(
    loading: Boolean,
    error: String,
    onLogin: (String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        Modifier
            .fillMaxSize()
            .background(BackgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .shadow(8.dp, RoundedCornerShape(24.dp))
                .background(Color.White, RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🩸", fontSize = 55.sp)

            Text(
                "Blood for Daudkandi",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Red
            )

            Text("অ্যাডমিন লগইন", color = Color.Gray)

            Spacer(Modifier.height(25.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("ইমেইল") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email
                )
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("পাসওয়ার্ড") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                )
            )

            if (error.isNotBlank()) {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = { onLogin(email, password) },
                enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (loading) "অপেক্ষা করুন..." else "লগইন করুন")
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "অনুমোদিত অ্যাডমিন অ্যাকাউন্ট দিয়ে প্রবেশ করুন।",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

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
    var deleteTarget by remember { mutableStateOf<Donor?>(null) }

    val context = LocalContext.current

    val groups = listOf(
        "সব", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"
    )

    val filtered = donors.filter { donor ->
        val groupMatch = group == "সব" || donor.bloodGroup == group

        val activeMatch = when (activeFilter) {
            "সক্রিয়" -> donor.active
            "নিষ্ক্রিয়" -> !donor.active
            else -> true
        }

        val searchMatch = search.isBlank() ||
                donor.name.contains(search, true) ||
                donor.area.contains(search, true) ||
                donor.phone.contains(search, true) ||
                donor.notes.contains(search, true)

        groupMatch && activeMatch && searchMatch
    }

    Scaffold(
        containerColor = BackgroundColor,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Blood for Daudkandi",
                            fontWeight = FontWeight.Bold,
                            color = Red
                        )
                        Text(
                            "রক্তদাতাদের ব্যবস্থাপনা",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("লগআউট", color = Red)
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
                containerColor = Red,
                contentColor = Color.White
            ) {
                Text("+ নতুন ডোনার", fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp)
        ) {

            Spacer(Modifier.height(12.dp))

            // Dashboard banner

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Red)
                    .padding(18.dp)
            ) {
                Column {
                    Text(
                        "এক ব্যাগ রক্ত, একটি জীবন ❤️",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "দাউদকান্দির রক্তদাতাদের ডিজিটাল তালিকা",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Statistics

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    "মোট ডোনার",
                    donors.size.toString(),
                    Red,
                    Modifier.weight(1f)
                )

                StatCard(
                    "সক্রিয়",
                    donors.count { it.active }.toString(),
                    Green,
                    Modifier.weight(1f)
                )

                StatCard(
                    "নিষ্ক্রিয়",
                    donors.count { !it.active }.toString(),
                    Blue,
                    Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(14.dp))

            // Copy all donors

            OutlinedButton(
                onClick = {
                    if (donors.isEmpty()) {
                        Toast.makeText(
                            context,
                            "কোনো ডোনার নেই",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val allData = donors.joinToString(
                            separator = "\n\n--------------------\n\n"
                        ) { donorDetails(it) }

                        copyToClipboard(
                            context,
                            "সকল ডোনারের তথ্য",
                            allData
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("📋 সকল ডোনারের তথ্য একসঙ্গে কপি")
            }

            Spacer(Modifier.height(12.dp))

            // Search

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("নাম, এলাকা বা মোবাইল দিয়ে খুঁজুন") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(Modifier.height(10.dp))

            Text(
                "রক্তের গ্রুপ",
                fontWeight = FontWeight.Bold,
                color = TextDark
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(groups) { g ->
                    FilterChip(
                        selected = group == g,
                        onClick = { group = g },
                        label = { Text(g) }
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("সব", "সক্রিয়", "নিষ্ক্রিয়").forEach { filter ->
                    FilterChip(
                        selected = activeFilter == filter,
                        onClick = { activeFilter = filter },
                        label = { Text(filter) }
                    )
                }
            }

            if (error.isNotBlank()) {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }

            if (loading) {
                LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                )
            }

            Text(
                "ডোনার তালিকা (${filtered.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextDark,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Donor list

            if (filtered.isEmpty() && !loading) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "🩸\nকোনো ডোনার পাওয়া যায়নি",
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(filtered, key = { it.id }) { donor ->

                        DonorCard(
                            donor = donor,
                            onEdit = { onEdit(donor) },
                            onDelete = { deleteTarget = donor }
                        )
                    }
                }
            }
        }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("ডোনার মুছে ফেলবেন?") },
            text = {
                Text("${deleteTarget?.name} -এর তথ্য স্থায়ীভাবে মুছে যাবে।")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget?.let { onDelete(it) }
                        deleteTarget = null
                    }
                ) {
                    Text("মুছে ফেলুন", color = Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("বাতিল")
                }
            }
        )
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(
            Modifier.padding(vertical = 15.dp, horizontal = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )

            Text(
                title,
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun DonorCard(
    donor: Donor,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(Modifier.padding(14.dp)) {

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        donor.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )

                    Text(
                        donor.area.ifBlank { "এলাকা উল্লেখ নেই" },
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = LightRed
                    ) {
                        Text(
                            donor.bloodGroup,
                            color = Red,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(
                                horizontal = 13.dp,
                                vertical = 7.dp
                            )
                        )
                    }

                    Text(
                        if (donor.active) "● সক্রিয়" else "● নিষ্ক্রিয়",
                        color = if (donor.active) Green else Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }

            HorizontalDivider(
                Modifier.padding(vertical = 10.dp),
                color = Color(0xFFEEEEEE)
            )

            Text("📞 মোবাইল: ${donor.phone}")

            Text(
                "🩸 সর্বশেষ রক্তদান: ${
                    donor.lastDonation.ifBlank { "তথ্য নেই" }
                }",
                fontSize = 13.sp
            )

            Text(
                "📅 ${donationDuration(donor.lastDonation)}",
                fontSize = 13.sp,
                color = Green,
                fontWeight = FontWeight.SemiBold
            )

            if (donor.notes.isNotBlank()) {
                Text(
                    "📝 নোট: ${donor.notes}",
                    fontSize = 13.sp,
                    color = Color.DarkGray,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_DIAL,
                            Uri.parse("tel:${Uri.encode(donor.phone)}")
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null)
                    Text("কল")
                }

                OutlinedButton(
                    onClick = {
                        copyToClipboard(
                            context,
                            "ডোনারের তথ্য",
                            donorDetails(donor)
                        )
                    },
                    modifier = Modifier.weight(1.4f),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text("📋 সব কপি", fontSize = 12.sp)
                }

                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "সম্পাদনা",
                        tint = Blue
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "মুছুন",
                        tint = Red
                    )
                }
            }
        }
    }
}

// Donation duration calculation

fun donationDuration(date: String): String {
    if (date.isBlank()) return "শেষ রক্তদানের তারিখ দেওয়া হয়নি"

    val parsedDate = try {
        val formats = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
        )

        formats.firstNotNullOfOrNull { format ->
            runCatching {
                LocalDate.parse(date.trim(), format)
            }.getOrNull()
        }
    } catch (e: Exception) {
        null
    } ?: return "তারিখের ফরম্যাট সঠিক নয়"

    val today = LocalDate.now()

    if (parsedDate.isAfter(today)) {
        return "তারিখটি ভবিষ্যতের"
    }

    val period = Period.between(parsedDate, today)
    val totalDays = ChronoUnit.DAYS.between(parsedDate, today)

    val duration = buildString {
        if (period.years > 0) append("${period.years} বছর ")
        if (period.months > 0) append("${period.months} মাস ")
        if (period.days > 0) append("${period.days} দিন")

        if (isEmpty()) append("আজই রক্তদান করেছেন")
    }

    return "$duration (মোট ${totalDays} দিন)"
}

fun donorDetails(donor: Donor): String {
    return """
        🩸 BLOOD FOR DAUDKANDI
        ----------------------------

        নাম: ${donor.name}
        মোবাইল: ${donor.phone}
        রক্তের গ্রুপ: ${donor.bloodGroup}
        এলাকা: ${donor.area}
        সর্বশেষ রক্তদান: ${donor.lastDonation.ifBlank { "তথ্য নেই" }}
        রক্তদানের পর সময়: ${donationDuration(donor.lastDonation)}
        অবস্থা: ${if (donor.active) "সক্রিয়" else "নিষ্ক্রিয়"}
        নোট: ${donor.notes.ifBlank { "নেই" }}

        ----------------------------
        Blood for Daudkandi
    """.trimIndent()
}

// Donor form

@Composable
fun DonorForm(
    initial: Donor?,
    onDismiss: () -> Unit,
    onSave: (Donor) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var phone by remember(initial) { mutableStateOf(initial?.phone ?: "") }
    var group by remember(initial) { mutableStateOf(initial?.bloodGroup ?: "A+") }
    var area by remember(initial) { mutableStateOf(initial?.area ?: "") }
    var last by remember(initial) { mutableStateOf(initial?.lastDonation ?: "") }
    var active by remember(initial) { mutableStateOf(initial?.active ?: true) }
    var notes by remember(initial) { mutableStateOf(initial?.notes ?: "") }
    var expanded by remember { mutableStateOf(false) }

    val groups = listOf(
        "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 650.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                Text(
                    if (initial == null) "🩸 নতুন ডোনার যোগ করুন"
                    else "ডোনারের তথ্য সম্পাদনা",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Red
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("পূর্ণ নাম *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
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
                    value = area,
                    onValueChange = { area = it },
                    label = { Text("এলাকা / ঠিকানা") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = last,
                    onValueChange = { last = it },
                    label = { Text("সর্বশেষ রক্তদানের তারিখ") },
                    placeholder = { Text("2026-10-15") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        Text("তারিখ: YYYY-MM-DD, DD-MM-YYYY অথবা DD/MM/YYYY")
                    }
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("অতিরিক্ত নোট") },
                    placeholder = {
                        Text("প্রয়োজনীয় অন্যান্য তথ্য লিখুন...")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = active,
                        onCheckedChange = { active = it }
                    )

                    Text("সক্রিয় ডোনার")
                }

                Button(
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
                    },
                    enabled = name.isNotBlank() && phone.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("সংরক্ষণ করুন")
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("বাতিল")
                }
            }
        }
    }
}

// Clipboard

private fun copyToClipboard(
    context: Context,
    label: String,
    value: String
) {
    val clipboard = context.getSystemService(
        Context.CLIPBOARD_SERVICE
    ) as ClipboardManager

    clipboard.setPrimaryClip(
        ClipData.newPlainText(label, value)
    )

    Toast.makeText(
        context,
        "$label কপি হয়েছে",
        Toast.LENGTH_SHORT
    ).show()
}
