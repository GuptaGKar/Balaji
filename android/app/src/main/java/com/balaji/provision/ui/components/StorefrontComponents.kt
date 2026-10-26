package com.balaji.provision.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.balaji.provision.domain.model.CartItem
import com.balaji.provision.domain.model.Category
import com.balaji.provision.domain.model.Product
import com.balaji.provision.domain.model.ProductVariantGroup
import com.balaji.provision.domain.model.QuantityType
import com.balaji.provision.domain.model.formatPaise
import com.balaji.provision.ui.theme.appColors
import coil.compose.SubcomposeAsyncImage

/**
 * Storefront building blocks modelled on the Balaji reference design:
 * pill-shaped search, orange action pills, compact product cards, category
 * tiles and the free-delivery progress block. All data comes from the live
 * catalogue; nothing here invents discounts or availability.
 */

/** Search pill used on Home (read-only) and on listing screens (editable). */
@Composable
fun StoreSearchBar(
    modifier: Modifier = Modifier,
    query: String? = null,
    onQueryChange: ((String) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    placeholder: String = "Search for atta, milk, snacks…",
    autoFocus: Boolean = false,
    trailing: @Composable (() -> Unit)? = null
) {
    val editable = query != null && onQueryChange != null
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(autoFocus) {
        if (autoFocus && editable) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (!editable && onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            if (editable) {
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isNullOrEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = query.orEmpty(),
                        onValueChange = { value -> onQueryChange?.invoke(value) },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                            fontFamily = MaterialTheme.typography.bodyMedium.fontFamily
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                    if (!query.isNullOrEmpty()) {
                        IconButton(
                            onClick = { onQueryChange?.invoke("") },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            } else {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            trailing?.invoke()
        }
    }
}

/** Compact orange ADD pill used on product cards. */
@Composable
fun AddPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = "ADD"
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(32.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

/** White stepper with a grey border and orange controls. */
@Composable
fun OutlinedQuantityStepper(
    value: Long,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseContentDescription: String,
    increaseContentDescription: String,
    modifier: Modifier = Modifier,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true
) {
    Surface(
        modifier = modifier.height(32.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = onDecrease,
                enabled = canDecrease,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = decreaseContentDescription,
                    tint = if (canDecrease) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    },
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                onClick = onIncrease,
                enabled = canIncrease,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = increaseContentDescription,
                    tint = if (canIncrease) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    },
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/** Two-column listing card matching the reference product grid. */
@Composable
fun ProductGridCard(
    group: ProductVariantGroup,
    product: Product,
    cartItem: CartItem?,
    onOpenDetails: () -> Unit,
    onAdd: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    modifier: Modifier = Modifier,
    onWeight: (() -> Unit)? = null
) {
    val weighted = product.quantityType == QuantityType.WEIGHTED
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp)
                    .clickable(onClick = onOpenDetails),
                contentAlignment = Alignment.Center
            ) {
                ProductThumb(
                    name = product.name,
                    imageUrl = product.imagePath,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                if (product.hasDiscount) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(2.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${product.discountPercent}% OFF",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = product.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clickable(onClick = onOpenDetails)
            )
            Text(
                text = product.packLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (group.hasMultipleVariants) {
                Text(
                    text = "${group.variants.size} pack sizes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = formatPaise(product.pricePaise),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (product.hasDiscount) {
                            Text(
                                text = formatPaise(product.mrpPaise!!),
                                style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (product.hasDiscount) {
                        Text(
                            text = "${product.discountPercent}% OFF",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (!product.isInStock) {
                        Text(
                            text = "Out of stock",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (product.isLowStock) {
                        Text(
                            text = "Only a few left",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.appColors.warning
                        )
                    }
                }

                when {
                    weighted -> AddPill(
                        onClick = { onWeight?.invoke() ?: onAdd() },
                        enabled = product.isInStock,
                        label = if (cartItem != null) "EDIT" else "WEIGHT"
                    )
                    cartItem != null -> OutlinedQuantityStepper(
                        value = cartItem.qtyBase,
                        onDecrease = onDecrease,
                        onIncrease = onIncrease,
                        decreaseContentDescription = "Decrease ${product.name}",
                        increaseContentDescription = "Increase ${product.name}",
                        canIncrease = cartItem.qtyBase < product.maxOrderQty
                    )
                    else -> AddPill(
                        onClick = onAdd,
                        enabled = product.isInStock
                    )
                }
            }
        }
    }
}

/** Compact horizontal card used in the Home carousels. */
@Composable
fun HorizontalProductCard(
    group: ProductVariantGroup,
    product: Product,
    cartItem: CartItem?,
    onOpenDetails: () -> Unit,
    onAdd: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    modifier: Modifier = Modifier,
    onWeight: (() -> Unit)? = null
) {
    val weighted = product.quantityType == QuantityType.WEIGHTED
    Card(
        modifier = modifier.width(148.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clickable(onClick = onOpenDetails),
                contentAlignment = Alignment.Center
            ) {
                ProductThumb(
                    name = product.name,
                    imageUrl = product.imagePath,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                if (product.hasDiscount) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(2.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${product.discountPercent}% OFF",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .heightIn(min = 34.dp)
                    .clickable(onClick = onOpenDetails)
            )
            Text(
                text = product.packLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            if (group.hasMultipleVariants) {
                Text(
                    text = "${group.variants.size} packs",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = formatPaise(product.pricePaise),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (product.hasDiscount) {
                            Text(
                                text = formatPaise(product.mrpPaise!!),
                                style = MaterialTheme.typography.labelSmall.copy(textDecoration = TextDecoration.LineThrough),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (product.hasDiscount) {
                        Text(
                            text = "${product.discountPercent}% OFF",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                when {
                    weighted -> AddPill(
                        onClick = { onWeight?.invoke() ?: onAdd() },
                        enabled = product.isInStock,
                        label = if (cartItem != null) "EDIT" else "WEIGHT"
                    )
                    cartItem != null -> OutlinedQuantityStepper(
                        value = cartItem.qtyBase,
                        onDecrease = onDecrease,
                        onIncrease = onIncrease,
                        decreaseContentDescription = "Decrease ${product.name}",
                        increaseContentDescription = "Increase ${product.name}",
                        canIncrease = cartItem.qtyBase < product.maxOrderQty
                    )
                    else -> AddPill(
                        onClick = onAdd,
                        enabled = product.isInStock
                    )
                }
            }
        }
    }
}

/** Circular category shortcut for the Home screen. */
@Composable
fun CategoryShortcut(
    category: Category,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CategoryArtwork(
            category = category,
            size = 56.dp,
            iconSize = 26.dp,
            containerColor = containerColor,
            contentColor = contentColor
        )
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Category grid tile used on the Categories tab. */
@Composable
fun CategoryTile(
    category: Category,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
            CategoryArtwork(
                category = category,
                size = 48.dp,
                iconSize = 24.dp,
                containerColor = containerColor,
                contentColor = contentColor
            )
            Text(
            text = category.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CategoryArtwork(
    category: Category,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    containerColor: Color,
    contentColor: Color
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(containerColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (category.imagePath != null) {
            SubcomposeAsyncImage(
                model = category.imagePath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = { CategoryFallbackIcon(category.name, iconSize, contentColor) },
                error = { CategoryFallbackIcon(category.name, iconSize, contentColor) },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            CategoryFallbackIcon(category.name, iconSize, contentColor)
        }
    }
}

@Composable
private fun CategoryFallbackIcon(name: String, iconSize: androidx.compose.ui.unit.Dp, tint: Color) {
    Icon(
        imageVector = categoryIcon(name),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(iconSize)
    )
}

/**
 * Free-delivery progress block shown in the cart and at checkout. [amountToFree]
 * comes from the server quote; the threshold is derived, never hardcoded.
 */
@Composable
fun FreeDeliveryProgress(
    subtotalPaise: Long,
    amountToFreePaise: Long,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val unlocked = amountToFreePaise <= 0L
    val threshold = if (unlocked) subtotalPaise else subtotalPaise + amountToFreePaise
    val progress = if (threshold <= 0L) 1f else {
        (subtotalPaise.toFloat() / threshold.toFloat()).coerceIn(0f, 1f)
    }
    val accent = if (unlocked) MaterialTheme.appColors.success else MaterialTheme.colorScheme.primary
    val container = if (unlocked) {
        MaterialTheme.appColors.successContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (unlocked) Icons.Default.Check else Icons.Default.LocalShipping,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (unlocked) {
                    "Free delivery unlocked"
                } else {
                    "Free delivery above ${formatPaise(threshold)}"
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (unlocked) {
                    MaterialTheme.appColors.onSuccessContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = if (unlocked) MaterialTheme.appColors.success else MaterialTheme.colorScheme.primary,
            trackColor = if (unlocked) {
                MaterialTheme.appColors.success.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
        if (!unlocked) {
            Text(
                text = "Add ${formatPaise(amountToFreePaise)} more for free delivery",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Address → Payment → Review step rail used at checkout. */
@Composable
fun CheckoutStepIndicator(
    currentStep: Int,
    modifier: Modifier = Modifier
) {
    val steps = listOf("Address", "Payment", "Review")
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, label ->
            val number = index + 1
            val active = number <= currentStep
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(64.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            color = if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (number < currentStep) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            text = number.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (active) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            if (index < steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(
                            if (number < currentStep) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                )
            }
        }
    }
}

/** Orange basket mark used on the splash screen and store cards. */
@Composable
fun BalajiBasketMark(
    modifier: Modifier = Modifier,
    basketColor: Color,
    leafColor: Color
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = (w * 0.055f).coerceAtLeast(2f)

        // Handle
        drawArc(
            color = basketColor,
            startAngle = 190f,
            sweepAngle = 160f,
            useCenter = false,
            topLeft = Offset(w * 0.28f, h * 0.02f),
            size = Size(w * 0.44f, h * 0.42f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        // Basket body
        val body = Path().apply {
            moveTo(w * 0.12f, h * 0.34f)
            lineTo(w * 0.88f, h * 0.34f)
            lineTo(w * 0.78f, h * 0.9f)
            lineTo(w * 0.22f, h * 0.9f)
            close()
        }
        drawPath(body, color = basketColor)
        drawLine(
            color = Color.White.copy(alpha = 0.9f),
            start = Offset(w * 0.24f, h * 0.46f),
            end = Offset(w * 0.27f, h * 0.78f),
            strokeWidth = stroke * 0.7f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White.copy(alpha = 0.9f),
            start = Offset(w * 0.5f, h * 0.46f),
            end = Offset(w * 0.5f, h * 0.78f),
            strokeWidth = stroke * 0.7f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White.copy(alpha = 0.9f),
            start = Offset(w * 0.76f, h * 0.46f),
            end = Offset(w * 0.73f, h * 0.78f),
            strokeWidth = stroke * 0.7f,
            cap = StrokeCap.Round
        )

        // Leaf
        val leaf = Path().apply {
            moveTo(w * 0.56f, h * 0.2f)
            quadraticBezierTo(w * 0.82f, h * 0.02f, w * 0.9f, h * 0.18f)
            quadraticBezierTo(w * 0.72f, h * 0.34f, w * 0.56f, h * 0.2f)
            close()
        }
        drawPath(leaf, color = leafColor)
    }
}

/** Soft container/content colour pair for category shortcuts and tiles. */
fun categoryColors(index: Int): Pair<Color, Color> {
    val palette = listOf(
        Color(0xFFFFE9D9) to Color(0xFFC2410C),
        Color(0xFFDCFCE7) to Color(0xFF166534),
        Color(0xFFFFF7DB) to Color(0xFF92400E),
        Color(0xFFDBEAFE) to Color(0xFF1D4ED8),
        Color(0xFFFCE7F3) to Color(0xFF9D174D),
        Color(0xFFEDE9FE) to Color(0xFF5B21B6),
        Color(0xFFE0F2FE) to Color(0xFF0369A1),
        Color(0xFFFEE2E2) to Color(0xFFB91C1C)
    )
    return palette[index.mod(palette.size)]
}

/** Best-effort icon by category name; falls back to a generic basket. */
fun categoryIcon(name: String): androidx.compose.ui.graphics.vector.ImageVector {
    val normalized = name.lowercase()
    return when {
        normalized.contains("fruit") || normalized.contains("vegetable") ->
            androidx.compose.material.icons.Icons.Default.Eco
        normalized.contains("dairy") || normalized.contains("bread") || normalized.contains("egg") ->
            androidx.compose.material.icons.Icons.Default.BakeryDining
        normalized.contains("snack") || normalized.contains("biscuit") ->
            androidx.compose.material.icons.Icons.Default.Fastfood
        normalized.contains("beverage") || normalized.contains("drink") ->
            androidx.compose.material.icons.Icons.Default.LocalDrink
        normalized.contains("tea") || normalized.contains("coffee") || normalized.contains("breakfast") ->
            androidx.compose.material.icons.Icons.Default.LocalCafe
        normalized.contains("cook") -> androidx.compose.material.icons.Icons.Default.Restaurant
        normalized.contains("staple") || normalized.contains("rice") || normalized.contains("atta") ->
            androidx.compose.material.icons.Icons.Default.Grain
        normalized.contains("personal") || normalized.contains("care") ->
            androidx.compose.material.icons.Icons.Default.Spa
        normalized.contains("clean") -> androidx.compose.material.icons.Icons.Default.CleaningServices
        normalized.contains("house") || normalized.contains("home") ->
            androidx.compose.material.icons.Icons.Default.Home
        normalized.contains("pooja") || normalized.contains("seasonal") ->
            androidx.compose.material.icons.Icons.Default.AutoAwesome
        else -> androidx.compose.material.icons.Icons.Default.ShoppingBasket
    }
}

/** Small centred progress indicator for inline loading sections. */
@Composable
fun InlineLoader(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 2.5.dp
        )
    }
}
