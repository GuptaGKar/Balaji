package com.balaji.provision.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.balaji.provision.domain.model.Product
import com.balaji.provision.domain.model.formatPaise

enum class WeightUnit {
    GRAMS, KILOGRAMS
}

@Composable
fun WeightedQuantityDialog(
    product: Product,
    currentQtyBaseGrams: Long,
    onDismiss: () -> Unit,
    onConfirmQtyGrams: (Long) -> Unit
) {
    var selectedUnit by remember {
        mutableStateOf(
            if (currentQtyBaseGrams >= 1000 && currentQtyBaseGrams % 500 == 0L) WeightUnit.KILOGRAMS else WeightUnit.GRAMS
        )
    }
    
    var inputText by remember(selectedUnit) {
        mutableStateOf(
            if (selectedUnit == WeightUnit.KILOGRAMS) {
                val kg = currentQtyBaseGrams / 1000.0
                if (kg == kg.toLong().toDouble()) kg.toLong().toString() else kg.toString()
            } else {
                currentQtyBaseGrams.toString()
            }
        )
    }
    
    val calculatedGrams by remember(inputText, selectedUnit) {
        derivedStateOf {
            val num = inputText.toDoubleOrNull() ?: 0.0
            if (selectedUnit == WeightUnit.KILOGRAMS) {
                (num * 1000).toLong()
            } else {
                num.toLong()
            }
        }
    }
    
    val calculatedPricePaise by remember(calculatedGrams) {
        derivedStateOf {
            if (product.priceBasisQty > 0) {
                (product.pricePaise.toDouble() * (calculatedGrams.toDouble() / product.priceBasisQty.toDouble())).toLong()
            } else {
                product.pricePaise
            }
        }
    }
    
    val isValid by remember(calculatedGrams) {
        derivedStateOf {
            calculatedGrams >= product.minQty && calculatedGrams <= product.maxOrderQty
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Scale,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column {
                            Text("Custom Weight", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(product.name, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                // Unit Selection Segmented Control (Grams vs Kilograms)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        UnitToggleButton(
                            label = "Grams (g)",
                            isSelected = selectedUnit == WeightUnit.GRAMS,
                            onClick = { selectedUnit = WeightUnit.GRAMS },
                            modifier = Modifier.weight(1f)
                        )
                        UnitToggleButton(
                            label = "Kilograms (kg)",
                            isSelected = selectedUnit == WeightUnit.KILOGRAMS,
                            onClick = { selectedUnit = WeightUnit.KILOGRAMS },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // Quick Weight Preset Chips
                Text("Quick Selection", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = if (selectedUnit == WeightUnit.GRAMS) {
                        listOf("100", "250", "500", "750")
                    } else {
                        listOf("0.5", "1", "2", "5")
                    }
                    
                    presets.forEach { preset ->
                        FilterChip(
                            selected = inputText == preset,
                            onClick = { inputText = preset },
                            label = { Text(if (selectedUnit == WeightUnit.GRAMS) "${preset}g" else "${preset}kg", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                
                // Manual Number Input Field
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { newValue ->
                        // Allow numbers and single decimal point
                        if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                            inputText = newValue
                        }
                    },
                    label = { Text("Enter quantity in ${if (selectedUnit == WeightUnit.GRAMS) "grams" else "kg"}") },
                    suffix = { Text(if (selectedUnit == WeightUnit.GRAMS) "g" else "kg", fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
                
                // Price Preview Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Weight", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (calculatedGrams >= 1000) "${calculatedGrams / 1000.0} kg" else "${calculatedGrams} g",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Calculated Price", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                formatPaise(calculatedPricePaise),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                if (!isValid && calculatedGrams > 0) {
                    Text(
                        "Please select between ${product.minQty}g and ${product.maxOrderQty}g",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Confirm Action Button
                Button(
                    onClick = {
                        if (isValid && calculatedGrams > 0) {
                            onConfirmQtyGrams(calculatedGrams)
                            onDismiss()
                        }
                    },
                    enabled = isValid && calculatedGrams > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Confirm & Add to Basket", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
fun UnitToggleButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        modifier = modifier
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(vertical = 10.dp),
            textAlign = TextAlign.Center,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}