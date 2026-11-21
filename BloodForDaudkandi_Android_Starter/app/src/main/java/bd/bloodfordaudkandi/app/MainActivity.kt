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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
        db.collection("donors").orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, e ->
                loading = false
                if (e != null) error = e.localizedMessage ?: "তথ্য লোড করা যায়নি"
                else donors = snap?.documents?.map { d ->
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
            }
    }

    LaunchedEffect(user?.uid) {
        if (user != null) loadDonors()
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFFB71C1C))) {
        if (user == null) {
            LoginScreen(
                onLogin = { email, password ->
                    scope.launch {
                        loading = true
                        try {
                            auth.signInWithEmailAndPassword(email.trim(), password).await()
                            user = auth.currentUser
                            error = ""
                        } catch (e: Exception) { error = e.localizedMessage ?: "লগইন ব্যর্থ হয়েছে" }
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
                onAdd = { editing = null; showForm = true },
                onEdit = { editing = it; showForm = true },
                onDelete = { donor ->
                    scope.launch {
                        try { db.collection("donors").document(donor.id).delete().await() }
                        catch (e: Exception) { error = e.localizedMessage ?: "মুছতে সমস্যা হয়েছে" }
                    }
                },
                onLogout = { auth.signOut(); user = null; donors = emptyList() }
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
                                if (donor.id.isBlank()) db.collection("donors").add(data).await()
                                else db.collection("donors").document(donor.id).set(data).await()
                                showForm = false
                            } catch (e: Exception) { error = e.localizedMessage ?: "সংরক্ষণ করা যায়নি" }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun LoginScreen(onLogin: (String, String) -> Unit, loading: Boolean, error: String) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("🩸 Blood for Daudkandi", style = MaterialTheme.typography.headlineMedium)
        Text("অ্যাডমিন লগইন", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(email, { email = it }, label = { Text("ইমেইল") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("পাসওয়ার্ড") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { onLogin(email, password) }, enabled = !loading && email.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(if (loading) "অপেক্ষা করুন..." else "লগইন")
        }
        Text("নতুন অ্যাডমিন অ্যাকাউন্ট অনুমোদিত সুপার অ্যাডমিন Firebase Console থেকে তৈরি করবেন।", style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonorScreen(
    donors: List<Donor>, error: String, loading: Boolean,
    onAdd: () -> Unit, onEdit: (Donor) -> Unit, onDelete: (Donor) -> Unit, onLogout: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("সব") }
    val groups = listOf("সব", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
    val filtered = donors.filter {
        (group == "সব" || it.bloodGroup == group) &&
        (search.isBlank() || it.name.contains(search, true) || it.area.contains(search, true) || it.phone.contains(search))
    }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blood for Daudkandi") },
                actions = { TextButton(onClick = onLogout) { Text("লগআউট") } }
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = onAdd, containerColor = MaterialTheme.colorScheme.primary) { Text("+") } }
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            Text("মোট ডোনার: ${donors.size}", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(search, { search = it }, label = { Text("নাম, এলাকা বা মোবাইল দিয়ে খুঁজুন") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                groups.take(5).forEach { g -> FilterChip(selected = group == g, onClick = { group = g }, label = { Text(g) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                groups.drop(5).forEach { g -> FilterChip(selected = group == g, onClick = { group = g }, label = { Text(g) }) }
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { donor ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(Modifier.weight(1f), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text(donor.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { copyToClipboard(context, "ডোনারের নাম", donor.name) }) { Text("নাম কপি") }
                                }
                                AssistChip(onClick = { group = donor.bloodGroup }, label = { Text(donor.bloodGroup) })
                            }
                            Text("এলাকা: ${donor.area}")
                            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text("মোবাইল: ${donor.phone}", modifier = Modifier.weight(1f))
                                TextButton(onClick = { copyToClipboard(context, "মোবাইল নম্বর", donor.phone) }) { Text("নম্বর কপি") }
                            }
                            Text("সর্বশেষ রক্তদান: ${donor.lastDonation.ifBlank { "তথ্য নেই" }}")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${donor.phone}"))
                                    context.startActivity(intent)
                                }) { Icon(Icons.Default.Call, contentDescription = null); Text("কল") }
                                IconButton(onClick = { onEdit(donor) }) { Icon(Icons.Default.Edit, contentDescription = "সম্পাদনা") }
                                IconButton(onClick = { onDelete(donor) }) { Icon(Icons.Default.Delete, contentDescription = "মুছুন") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DonorForm(initial: Donor?, onDismiss: () -> Unit, onSave: (Donor) -> Unit) {
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
        title = { Text(if (initial == null) "নতুন ডোনার" else "ডোনারের তথ্য সম্পাদনা") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("পূর্ণ নাম *") })
                OutlinedTextField(phone, { phone = it }, label = { Text("মোবাইল নম্বর *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text("রক্তের গ্রুপ: $group") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        groups.forEach { g -> DropdownMenuItem(text = { Text(g) }, onClick = { group = g; expanded = false }) }
                    }
                }
                OutlinedTextField(area, { area = it }, label = { Text("এলাকা / ঠিকানা") })
                OutlinedTextField(last, { last = it }, label = { Text("সর্বশেষ রক্তদানের তারিখ (যেমন 2026-10-15)") })
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(active, { active = it }); Text("সক্রিয় ডোনার")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && phone.isNotBlank(),
                onClick = { onSave(Donor(initial?.id ?: "", name, phone, group, area, last, active)) }
            ) { Text("সংরক্ষণ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}


private fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "$label কপি হয়েছে", Toast.LENGTH_SHORT).show()
}
