package bd.bloodfordaudkandi.app

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val BloodRed = Color(0xFFB71C35)
private val DeepRed = Color(0xFF850D25)
private val LightPink = Color(0xFFFFE8EC)
private val PageBg = Color(0xFFFFF7F8)
private val TextDark = Color(0xFF29232A)
private val Muted = Color(0xFF81757A)

data class Donor(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val bloodGroup: String = "A+",
    val area: String = "",
    val lastDonation: String = "",
    val active: Boolean = true
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
                            active = d.getBoolean("active") ?: true
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
            onPrimary = Color.White,
            secondary = DeepRed,
            background = PageBg,
            surface = Color.White,
            error = Color(0xFFB3261E)
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = PageBg
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
                                        "active" to donor.active
                                    )
                                    if (donor.id.isBlank()) {
                                        db.collection("donors").add(data).await()
                                    } else {
                                        db.collection("donors")
                                            .document(donor.id).set(data).await()
                                    }
                                    showForm = false
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
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(DeepRed, BloodRed, PageBg)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🩸", fontSize = 55.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Blood for Daudkandi",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = BloodRed,
                    textAlign = TextAlign.Center
                )
                Text(
                    "রক্তদানে বাঁচুক প্রাণ",
                    color = Muted,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(28.dp))
                Text(
                    "অ্যাডমিন লগইন",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("ইমেইল ঠিকানা") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email
                    )
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("পাসওয়ার্ড") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (error.isNotBlank()) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { onLogin(email, password) },
                    enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        if (loading) "অপেক্ষা করুন..." else "লগইন করুন",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "নতুন অ্যাডমিন অ্যাকাউন্ট সুপার অ্যাডমিন Firebase Console থেকে তৈরি করবেন।",
                    fontSize = 11.sp,
                    color = Muted,
                    textAlign = TextAlign.Center
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
    val context = LocalContext.current

    val groups = listOf("সব", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

    val filtered = donors.filter {
        (group == "সব" || it.bloodGroup == group) &&
        (search.isBlank() ||
            it.name.contains(search, true) ||
            it.area.contains(search, true) ||
            it.phone.contains(search))
    }

    Scaffold(
        containerColor = PageBg,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "BLOOD FOR",
                            fontSize = 10.sp,
                            letterSpacing = 3.sp,
                            color = BloodRed,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "DAUDKANDI",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextDark
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("লগআউট", color = BloodRed)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
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
                Text("+", fontSize = 24.sp)
                Spacer(Modifier.width(5.dp))
                Text("নতুন ডোনার", fontWeight = FontWeight.Bold)
            }
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(10.dp))

            // Dashboard banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = BloodRed)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(DeepRed, BloodRed, Color(0xFFE34D68))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Text(
                            "রক্তদানে এগিয়ে আসুন ❤️",
                            fontSize = 21.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "আপনার এক ব্যাগ রক্ত বাঁচাতে পারে একটি জীবন",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(18.dp))
                        Text(
                            "আমাদের রক্তদাতা পরিবার",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                        Text(
                            "${donors.size} জন",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Statistics
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    title = "মোট ডোনার",
                    value = "${donors.size}",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "সক্রিয় ডোনার",
                    value = "${donors.count { it.active }}",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "রক্তের গ্রুপ",
                    value = "${donors.map { it.bloodGroup }.distinct().size}",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                "রক্তদাতা খুঁজুন",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("নাম, এলাকা বা মোবাইল নম্বর") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BloodRed,
                    unfocusedBorderColor = Color(0xFFE8D9DD)
                )
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "রক্তের গ্রুপ নির্বাচন করুন",
                fontSize = 13.sp,
                color = Muted,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                groups.forEach { g ->
                    FilterChip(
                        selected = group == g,
                        onClick = { group = g },
                        label = { Text(g) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BloodRed,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "রক্তদাতার তালিকা",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Text(
                    "${filtered.size} জন পাওয়া গেছে",
                    fontSize = 11.sp,
                    color = Muted
                )
            }

            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = BloodRed
                )
            }

            if (filtered.isEmpty() && !loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "কোনো রক্তদাতা পাওয়া যায়নি 🩸",
                        color = Muted,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.id }) { donor ->
                        DonorCard(
                            donor = donor,
                            context = context,
                            onEdit = { onEdit(donor) },
                            onDelete = { onDelete(donor) },
                            onGroup = { group = donor.bloodGroup }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 15.dp, horizontal = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                fontSize = 23.sp,
                color = BloodRed,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                title,
                fontSize = 10.sp,
                color = Muted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun DonorCard(
    donor: Donor,
    context: Context,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onGroup: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(LightPink, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🩸", fontSize = 25.sp)
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        donor.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Text(
                        donor.area.ifBlank { "এলাকা উল্লেখ নেই" },
                        fontSize = 12.sp,
                        color = Muted
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = LightPink,
                        onClick = onGroup
                    ) {
                        Text(
                            donor.bloodGroup,
                            modifier = Modifier.padding(
                                horizontal = 12.dp,
                                vertical = 8.dp
                            ),
                            color = BloodRed,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        if (donor.active) "সক্রিয়" else "নিষ্ক্রিয়",
                        fontSize = 10.sp,
                        color = if (donor.active) Color(0xFF21844A) else Muted
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = Color(0xFFF1E7EA)
            )

            Text(
                "মোবাইল: ${donor.phone}",
                fontSize = 13.sp,
                color = TextDark
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "সর্বশেষ রক্তদান: ${donor.lastDonation.ifBlank { "তথ্য নেই" }}",
                fontSize = 12.sp,
                color = Muted
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text("কল করুন")
                }

                OutlinedButton(
                    onClick = {
                        copyToClipboard(context, "মোবাইল নম্বর", donor.phone)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("কপি")
                }

                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "সম্পাদনা",
                        tint = BloodRed
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "মুছুন",
                        tint = Color(0xFFB3261E)
                    )
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

    val groups = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        title = {
            Text(
                if (initial == null) "নতুন রক্তদাতা যোগ করুন" else "তথ্য সম্পাদনা",
                fontWeight = FontWeight.Bold,
                color = BloodRed
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text("পূর্ণ নাম *") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    phone,
                    { phone = it },
                    label = { Text("মোবাইল নম্বর *") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    last,
                    { last = it },
                    label = { Text("সর্বশেষ রক্তদানের তারিখ") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(active, { active = it })
                    Text("সক্রিয় ডোনার")
                }
            }
        },
        confirmButton = {
            Button(
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
                            active
                        )
                    )
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল", color = Muted)
            }
        }
    )
}

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
