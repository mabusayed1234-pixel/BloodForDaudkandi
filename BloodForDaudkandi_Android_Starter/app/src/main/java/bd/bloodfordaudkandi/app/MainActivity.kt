
package bd.bloodfordaudkandi.app

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

data class Donor(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val bloodGroup: String = "A+",
    val area: String = "",
    val lastDonation: String = "",
    val active: Boolean = true
)

private val BloodRed = Color(0xFFB71C35)
private val LightRed = Color(0xFFFFE8EC)
private val Green = Color(0xFF16834A)
private val Orange = Color(0xFFB66A00)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)

        // Firestore Android SDK-তে offline persistence
        // ডিফল্টভাবেই সক্রিয় থাকে।

        setContent {
            BloodApp()
        }
    }
}

@Composable
fun BloodApp() {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var user by remember { mutableStateOf(auth.currentUser) }
    var donors by remember { mutableStateOf<List<Donor>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(false) }
    var pendingSync by remember { mutableStateOf(false) }

    var showForm by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Donor?>(null) }

    val prefs = remember {
        context.getSharedPreferences("blood_settings", Context.MODE_PRIVATE)
    }

    var fontChoice by remember {
        mutableStateOf(prefs.getString("font", "Sans") ?: "Sans")
    }

    var fontScale by remember {
        mutableFloatStateOf(prefs.getFloat("fontScale", 0.90f))
    }

    val fontFamily = when (fontChoice) {
        "Serif" -> FontFamily.Serif
        "Mono" -> FontFamily.Monospace
        else -> FontFamily.SansSerif
    }

    val base = Typography()

    val compactTypography = base.copy(
        displayLarge = base.displayLarge.copy(
            fontFamily = fontFamily,
            fontSize = base.displayLarge.fontSize * fontScale
        ),
        headlineLarge = base.headlineLarge.copy(
            fontFamily = fontFamily,
            fontSize = base.headlineLarge.fontSize * fontScale
        ),
        headlineMedium = base.headlineMedium.copy(
            fontFamily = fontFamily,
            fontSize = base.headlineMedium.fontSize * fontScale
        ),
        headlineSmall = base.headlineSmall.copy(
            fontFamily = fontFamily,
            fontSize = base.headlineSmall.fontSize * fontScale
        ),
        titleLarge = base.titleLarge.copy(
            fontFamily = fontFamily,
            fontSize = base.titleLarge.fontSize * fontScale
        ),
        titleMedium = base.titleMedium.copy(
            fontFamily = fontFamily,
            fontSize = base.titleMedium.fontSize * fontScale
        ),
        titleSmall = base.titleSmall.copy(
            fontFamily = fontFamily,
            fontSize = base.titleSmall.fontSize * fontScale
        ),
        bodyLarge = base.bodyLarge.copy(
            fontFamily = fontFamily,
            fontSize = base.bodyLarge.fontSize * fontScale
        ),
        bodyMedium = base.bodyMedium.copy(
            fontFamily = fontFamily,
            fontSize = base.bodyMedium.fontSize * fontScale
        ),
        bodySmall = base.bodySmall.copy(
            fontFamily = fontFamily,
            fontSize = base.bodySmall.fontSize * fontScale
        ),
        labelLarge = base.labelLarge.copy(
            fontFamily = fontFamily,
            fontSize = base.labelLarge.fontSize * fontScale
        ),
        labelMedium = base.labelMedium.copy(
            fontFamily = fontFamily,
            fontSize = base.labelMedium.fontSize * fontScale
        ),
        labelSmall = base.labelSmall.copy(
            fontFamily = fontFamily,
            fontSize = base.labelSmall.fontSize * fontScale
        )
    )

    // Firestore realtime listener
    DisposableEffect(user?.uid) {
        var registration: com.google.firebase.firestore.ListenerRegistration? = null

        if (user != null) {
            loading = true

            registration = db.collection("donors")
                .orderBy("name", Query.Direction.ASCENDING)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, e ->

                    loading = false

                    if (e != null) {
                        error = e.localizedMessage ?: "তথ্য লোড করা যায়নি"
                    } else if (snapshot != null) {

                        offline = snapshot.metadata.isFromCache
                        pendingSync = snapshot.metadata.hasPendingWrites()

                        donors = snapshot.documents.map { d ->
                            Donor(
                                id = d.id,
                                name = d.getString("name") ?: "",
                                phone = d.getString("phone") ?: "",
                                bloodGroup = d.getString("bloodGroup") ?: "A+",
                                area = d.getString("area") ?: "",
                                lastDonation = d.getString("lastDonation") ?: "",
                                active = d.getBoolean("active") ?: true
                            )
                        }

                        error = ""
                    }
                }
        }

        onDispose {
            registration?.remove()
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = BloodRed,
            onPrimary = Color.White,
            secondary = Green,
            background = Color(0xFFF7F7F9),
            surface = Color.White
        ),
        typography = compactTypography
    ) {
        if (user == null) {

            LoginScreen(
                loading = loading,
                error = error,
                onLogin = { email, password ->
                    auth.signInWithEmailAndPassword(
                        email.trim(),
                        password
                    ).addOnCompleteListener { task ->

                        loading = false

                        if (task.isSuccessful) {
                            user = auth.currentUser
                            error = ""
                        } else {
                            error = task.exception?.localizedMessage
                                ?: "লগইন ব্যর্থ হয়েছে"
                        }
                    }

                    loading = true
                }
            )

        } else {

            DonorScreen(
                donors = donors,
                loading = loading,
                error = error,
                offline = offline,
                pendingSync = pendingSync,
                onAdd = {
                    editing = null
                    showForm = true
                },
                onEdit = {
                    editing = it
                    showForm = true
                },
                onDelete = { donor ->

                    db.collection("donors")
                        .document(donor.id)
                        .delete()
                        .addOnFailureListener {
                            error = it.localizedMessage ?: "মুছতে সমস্যা হয়েছে"
                        }

                    Toast.makeText(
                        context,
                        "মুছে ফেলার অনুরোধ করা হয়েছে",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onLogout = {
                    auth.signOut()
                    user = null
                    donors = emptyList()
                },
                onSettings = {
                    showSettings = true
                }
            )

            if (showForm) {
                DonorForm(
                    initial = editing,
                    onDismiss = {
                        showForm = false
                    },
                    onSave = { donor ->

                        val data = mapOf(
                            "name" to donor.name.trim(),
                            "phone" to donor.phone.trim(),
                            "bloodGroup" to donor.bloodGroup,
                            "area" to donor.area.trim(),
                            "lastDonation" to donor.lastDonation.trim(),
                            "active" to donor.active
                        )

                        val operation =
                            if (donor.id.isBlank()) {
                                db.collection("donors").add(data)
                            } else {
                                db.collection("donors")
                                    .document(donor.id)
                                    .set(data)
                            }

                        // Firestore offline হলে write queue-তে থাকবে।
                        operation.addOnFailureListener {
                            error = it.localizedMessage ?: "সংরক্ষণ করা যায়নি"
                        }

                        showForm = false

                        Toast.makeText(
                            context,
                            "সংরক্ষণের অনুরোধ পাঠানো হয়েছে",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }

            if (showSettings) {
                SettingsDialog(
                    fontChoice = fontChoice,
                    fontScale = fontScale,
                    onFontChange = {
                        fontChoice = it
                        prefs.edit().putString("font", it).apply()
                    },
                    onScaleChange = {
                        fontScale = it
                        prefs.edit().putFloat("fontScale", it).apply()
                    },
                    onDismiss = {
                        showSettings = false
                    }
                )
            }
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
            .background(Color(0xFFF7F7F9)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Column(
                Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = BloodRed,
                    modifier = Modifier.size(42.dp)
                )

                Text(
                    "Blood for Daudkandi",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "অ্যাডমিন লগইন",
                    color = Color.Gray
                )

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

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("পাসওয়ার্ড") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    )
                )

                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }

                Button(
                    onClick = { onLogin(email, password) },
                    enabled = !loading &&
                        email.isNotBlank() &&
                        password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (loading) "অপেক্ষা করুন..." else "লগইন")
                }

                Text(
                    "শুধুমাত্র অনুমোদিত অ্যাডমিন ব্যবহার করতে পারবেন।",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonorScreen(
    donors: List<Donor>,
    loading: Boolean,
    error: String,
    offline: Boolean,
    pendingSync: Boolean,
    onAdd: () -> Unit,
    onEdit: (Donor) -> Unit,
    onDelete: (Donor) -> Unit,
    onLogout: () -> Unit,
    onSettings: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("সব") }

    val context = LocalContext.current

    val groups = listOf(
        "সব", "A+", "A-", "B+", "B-",
        "AB+", "AB-", "O+", "O-"
    )

    val filtered = donors.filter {
        (group == "সব" || it.bloodGroup == group) &&
        (
            search.isBlank() ||
            it.name.contains(search, true) ||
            it.area.contains(search, true) ||
            it.phone.contains(search, true) ||
            it.bloodGroup.contains(search, true)
        )
    }

    Scaffold(
        containerColor = Color(0xFFF7F7F9),

        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Blood for Daudkandi",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "রক্তদানে বাঁচুক প্রাণ",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, "সেটিংস")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, "লগআউট")
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
                Icon(Icons.Default.Add, "নতুন ডোনার")
            }
        }
    ) { padding ->

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 13.dp)
        ) {

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = BloodRed
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(15.dp)) {
                    Text(
                        "রক্তের প্রয়োজনে পাশে থাকুন",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(Modifier.height(5.dp))

                    Text(
                        "মোট নিবন্ধিত ডোনার",
                        color = Color.White.copy(alpha = 0.8f)
                    )

                    Text(
                        donors.size.toString(),
                        color = Color.White,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    if (offline) Icons.Default.CloudOff
                    else Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = if (offline) Orange else Green,
                    modifier = Modifier.size(17.dp)
                )

                Text(
                    when {
                        offline -> "ক্যাশ করা তথ্য / অফলাইন"
                        pendingSync -> "তথ্য Sync হচ্ছে"
                        else -> "অনলাইন সংযুক্ত"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (offline) Orange else Green
                )
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("নাম, মোবাইল, এলাকা বা গ্রুপ খুঁজুন")
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, null)
                },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Default.Close, "মুছুন")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                groups.forEach { g ->
                    FilterChip(
                        selected = group == g,
                        onClick = { group = g },
                        label = { Text(g) }
                    )
                }
            }

            Spacer(Modifier.height(7.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "ডোনার তালিকা (${filtered.size})",
                    fontWeight = FontWeight.Bold
                )

                Text(
                    if (loading) "লোড হচ্ছে..." else "সর্বশেষ তথ্য",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            if (error.isNotBlank()) {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (loading) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth()
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 75.dp)
            ) {
                items(filtered, key = { it.id }) { donor ->

                    DonorCard(
                        donor = donor,
                        onEdit = { onEdit(donor) },
                        onDelete = { onDelete(donor) }
                    )
                }

                if (filtered.isEmpty() && !loading) {
                    item {
                        Text(
                            "কোনো ডোনার পাওয়া যায়নি।",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(25.dp),
                            color = Color.Gray
                        )
                    }
                }
            }
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
    val elapsed = donationElapsed(donor.lastDonation)

    val ready = elapsed.second != null &&
        elapsed.second!! >= 90

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(11.dp)) {

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        donor.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        donor.area.ifBlank { "ঠিকানা দেওয়া হয়নি" },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                Surface(
                    color = LightRed,
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Text(
                        donor.bloodGroup,
                        color = BloodRed,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 7.dp
                        )
                    )
                }
            }

            HorizontalDivider(
                Modifier.padding(vertical = 8.dp),
                color = Color(0xFFEEEEEE)
            )

            Text(
                "মোবাইল: ${donor.phone}",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(5.dp))

            Text(
                "সর্বশেষ রক্তদান: ${
                    donor.lastDonation.ifBlank { "তথ্য নেই" }
                }",
                style = MaterialTheme.typography.bodySmall
            )

            if (elapsed.first.isNotBlank()) {
                Text(
                    "সময় অতিবাহিত: ${elapsed.first}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (ready) Color(0xFFE2F5E9)
                    else Color(0xFFFFF1DA),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (!donor.active) "নিষ্ক্রিয়"
                        else if (elapsed.second == null) "তারিখ যাচাই করুন"
                        else if (ready) "● সম্ভাব্য সময় হয়েছে"
                        else "● অপেক্ষমাণ",
                        color = if (ready && donor.active) Green else Orange,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(
                            horizontal = 9.dp,
                            vertical = 6.dp
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        val text = buildDonorText(donor)
                        copyToClipboard(context, text)
                    }) {
                        Icon(
                            Icons.Default.ContentCopy,
                            "সম্পূর্ণ তথ্য কপি",
                            tint = Green
                        )
                    }

                    IconButton(onClick = {
                        val intent = Intent(
                            Intent.ACTION_DIAL,
                            Uri.parse("tel:${donor.phone}")
                        )
                        context.startActivity(intent)
                    }) {
                        Icon(
                            Icons.Default.Call,
                            "কল",
                            tint = Green
                        )
                    }

                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            "সম্পাদনা"
                        )
                    }

                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            "মুছুন",
                            tint = BloodRed
                        )
                    }
                }
            }
        }
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
    var expanded by remember { mutableStateOf(false) }

    val groups = listOf(
        "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) "নতুন ডোনার"
                else "ডোনারের তথ্য সম্পাদনা"
            )
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text("পূর্ণ নাম *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    phone,
                    { phone = it },
                    label = { Text("মোবাইল নম্বর *") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone
                    ),
                    singleLine = true
                )

                Box {
                    OutlinedButton(
                        onClick = { expanded = true }
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
                    area,
                    { area = it },
                    label = { Text("এলাকা / ঠিকানা") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    last,
                    { last = it },
                    label = {
                        Text("সর্বশেষ রক্তদান (YYYY-MM-DD)")
                    },
                    placeholder = { Text("2026-10-15") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
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
                            active = active
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

@Composable
fun SettingsDialog(
    fontChoice: String,
    fontScale: Float,
    onFontChange: (String) -> Unit,
    onScaleChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("অ্যাপ সেটিংস") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("বাংলা ও ইংরেজি ফন্ট")

                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    listOf(
                        "Sans" to "সাধারণ",
                        "Serif" to "Serif",
                        "Mono" to "Mono"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = fontChoice == value,
                            onClick = { onFontChange(value) },
                            label = { Text(label) }
                        )
                    }
                }

                Text("লেখার আকার")

                Text(
                    "${(fontScale * 100).toInt()}%",
                    color = BloodRed
                )

                Slider(
                    value = fontScale,
                    onValueChange = onScaleChange,
                    valueRange = 0.8f..1.0f
                )

                Text(
                    "ছোট লেখা নির্বাচন করলে অ্যাপে আরও বেশি তথ্য দেখা যাবে।",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                Text(
                    "নোট: ফন্ট পরিবর্তন Android-এর উপলব্ধ ফন্টের ওপর নির্ভর করে।"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("সম্পন্ন")
            }
        }
    )
}

// দিন ও মাস গণনা
private fun donationElapsed(dateText: String): Pair<String, Long?> {
    if (dateText.isBlank()) return "" to null

    val patterns = listOf("yyyy-MM-dd", "dd-MM-yyyy")
    var date: Date? = null

    for (pattern in patterns) {
        try {
            val formatter = SimpleDateFormat(pattern, Locale.US)
            formatter.isLenient = false
            date = formatter.parse(dateText)
            if (date != null) break
        } catch (_: Exception) {
        }
    }

    if (date == null) return "তারিখ সঠিক নয়" to null

    val days = ((Date().time - date.time) / 86400000L)

    if (days < 0) return "ভবিষ্যতের তারিখ" to null

    val months = days / 30
    val remainingDays = days % 30

    val result = if (months == 0L) {
        "$days দিন"
    } else {
        "$months মাস $remainingDays দিন"
    }

    return result to days
}

private fun buildDonorText(d: Donor): String {
    return """
        🩸 BLOOD FOR DAUDKANDI

        নাম: ${d.name}
        রক্তের গ্রুপ: ${d.bloodGroup}
        মোবাইল: ${d.phone}
        ঠিকানা: ${d.area}
        সর্বশেষ রক্তদান: ${d.lastDonation.ifBlank { "তথ্য নেই" }}
        সক্রিয়: ${if (d.active) "হ্যাঁ" else "না"}
    """.trimIndent()
}

private fun copyToClipboard(context: Context, value: String) {
    val clipboard = context.getSystemService(
        Context.CLIPBOARD_SERVICE
    ) as ClipboardManager

    clipboard.setPrimaryClip(
        ClipData.newPlainText("Donor Information", value)
    )

    Toast.makeText(
        context,
        "সম্পূর্ণ তথ্য কপি হয়েছে",
        Toast.LENGTH_SHORT
    ).show()
}
