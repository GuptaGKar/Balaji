package com.balaji.provision.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.unit.dp

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = viewModel()
) {
    val user by remember { mutableStateOf<UserProfileView?>(null) }
    val isLoading by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Profile header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Background(
                        modifier = Modifier.fillMaxSize(),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
                    Text(
                        user?.displayName?.substring(0, 1).uppercase() ?? "?",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Column {
                    Text(user?.displayName ?? "Guest User", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(user?.phone ?? "", color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    Text(user?.role?.name ?? "Customer", fontSize = 12.sp)
                        .padding(8.dp, 4.dp)
                        .background(
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                }
            }
        }
        
        // Menu options
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column {
                ProfileMenuItem(
                    icon = androidx.compose.material.icons.outlined.Person,
                    title = "Edit Profile",
                    onClick = { }
                )
                Divider(modifier = Modifier.padding(start = 16.dp))
                ProfileMenuItem(
                    icon = androidx.compose.material.icons.outlined.LocalShipping,
                    title = "My Addresses",
                    onClick = { }
                )
                Divider(modifier = Modifier.padding(start = 16.dp))
                ProfileMenuItem(
                    icon = androidx.compose.material.icons.outlined.Settings,
                    title = "Settings",
                    onClick = { }
                )
                Divider(modifier = Modifier.padding(start = 16.dp))
                ProfileMenuItem(
                    icon = androidx.compose.material.icons.outlined.Logout,
                    title = "Sign Out",
                    onClick = { },
                    isDestructive = true
                )
            }
        }
        
        // App info
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("App Information", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                InfoRow("Version", "1.0.0")
                InfoRow("Store", "Balaji Provision Store")
                InfoRow("Contact", "9837803293")
            }
        }
    }
}

@Composable
fun ProfileMenuItem(
    icon: androidx.compose.ui.res.Painter,
    title: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    androidx.compose.material3.ListItem(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        headContent = {
            Icon(icon, contentDescription = null, tint = if (isDestructive) androidx.compose.material3.MaterialTheme.colorScheme.error else androidx.compose.material3.MaterialTheme.colorScheme.primary)
        },
        headlineContent = {
            Text(title, color = if (isDestructive) androidx.compose.material3.MaterialTheme.colorScheme.error else androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
        }
    )
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

data class UserProfileView(
    val displayName: String,
    val phone: String,
    val role: String
)

class ProfileViewModel : androidx.lifecycle.ViewModel() {
    // Implementation would go here
}