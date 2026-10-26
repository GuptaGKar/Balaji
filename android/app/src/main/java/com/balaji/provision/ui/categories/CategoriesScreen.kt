package com.balaji.provision.ui.categories

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import com.balaji.provision.domain.model.Category
import com.balaji.provision.ui.catalog.ProductViewModel
import com.balaji.provision.ui.components.categoryColors
import com.balaji.provision.ui.theme.appColors
import kotlin.math.roundToInt

private const val SEARCH_PLACEHOLDER = "Search for atta, milk, snacks…"

// The reference tiles are almost square. A wider ratio looked flat once real
// category images were dropped in.
private const val TILE_ASPECT_RATIO = 1.02f

/**
 * Grid geometry for the current window width.
 *
 * The catalogue is a single flat grid of categories: there are no group
 * headings. Phones always get four columns (three only on very narrow
 * screens), while tablets widen the grid instead of stretching four tiles
 * across the screen. Padding and label size grow with the available width so
 * the page keeps the same rhythm on a 5" phone and a 10" tablet.
 */
internal data class CatalogueGridMetrics(
    val columns: Int,
    val horizontalPaddingDp: Int,
    val gapDp: Int,
    val rowGapDp: Int,
    val labelFontSizeSp: Int,
    val labelLineHeightSp: Int
)

internal fun catalogueGridMetrics(widthDp: Int): CatalogueGridMetrics = when {
    widthDp < 300 -> CatalogueGridMetrics(
        columns = 3,
        horizontalPaddingDp = 12,
        gapDp = 10,
        rowGapDp = 14,
        labelFontSizeSp = 12,
        labelLineHeightSp = 15
    )

    widthDp < 600 -> CatalogueGridMetrics(
        columns = 4,
        horizontalPaddingDp = 16,
        gapDp = 12,
        rowGapDp = 16,
        labelFontSizeSp = 13,
        labelLineHeightSp = 17
    )

    widthDp < 840 -> CatalogueGridMetrics(
        columns = 6,
        horizontalPaddingDp = 24,
        gapDp = 16,
        rowGapDp = 18,
        labelFontSizeSp = 14,
        labelLineHeightSp = 18
    )

    else -> CatalogueGridMetrics(
        columns = 8,
        horizontalPaddingDp = 32,
        gapDp = 20,
        rowGapDp = 20,
        labelFontSizeSp = 14,
        labelLineHeightSp = 18
    )
}

/**
 * Catalogue / Categories tab.
 *
 * A pinned search header sits above one continuous, flat grid of every active
 * category. The header, tiles and states use the same orange Material theme as
 * Home, the listing and the rest of the customer app. Categories come straight
 * from the live catalogue in the store's configured sort order; the screen adds
 * no invented sections and no placeholder categories. A category added from the
 * web dashboard appears on the next visit without an app update.
 */
@Composable
fun CategoriesScreen(
    productViewModel: ProductViewModel = hiltViewModel(),
    onNavigateToSearch: (String) -> Unit = {},
    onOpenCategory: (String) -> Unit = {}
) {
    val categories by productViewModel.categories.collectAsState()
    val isLoading by productViewModel.isLoading.collectAsState()
    val error by productViewModel.error.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }

    // Categories are managed from the web dashboard. Refresh whenever this tab is
    // opened so a category created there appears without an app update; the view
    // model collapses overlapping requests into one fetch.
    LaunchedEffect(Unit) {
        productViewModel.loadData()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val metrics = remember(maxWidth) {
            catalogueGridMetrics(maxWidth.value.roundToInt())
        }

        Column(modifier = Modifier.fillMaxSize()) {
            CatalogueSearchHeader(
                query = query,
                horizontalPadding = metrics.horizontalPaddingDp.dp,
                onQueryChange = { query = it },
                onSearch = { onNavigateToSearch(it.trim()) }
            )

            Divider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp
            )

            Box(modifier = Modifier.weight(1f)) {
                when {
                    isLoading && categories.isEmpty() -> CatalogueLoadingState(metrics)

                    error != null && categories.isEmpty() -> CatalogueMessageState(
                        icon = Icons.Outlined.Warning,
                        title = "Could not load the catalogue",
                        message = error.orEmpty(),
                        actionLabel = "Try again",
                        onAction = { productViewModel.loadData() }
                    )

                    categories.isEmpty() -> CatalogueMessageState(
                        icon = Icons.Outlined.ShoppingBasket,
                        title = "No categories yet",
                        message = "Categories added from the store dashboard will appear here automatically.",
                        actionLabel = "Refresh",
                        onAction = { productViewModel.loadData() }
                    )

                    else -> CatalogueContent(
                        categories = categories,
                        metrics = metrics,
                        refreshError = error,
                        onRetry = { productViewModel.loadData() },
                        onOpenCategory = onOpenCategory
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogueSearchHeader(
    query: String,
    horizontalPadding: Dp,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit
) {
    val context = LocalContext.current
    // The reference header has a microphone beside the field. It opens the
    // system recogniser (no new dependency or permission); devices without a
    // speech activity get a clear message instead of a dead button.
    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spoken.isNullOrEmpty()) {
                onQueryChange(spoken)
                onSearch(spoken)
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = 10.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 14.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = SEARCH_PLACEHOLDER,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                fontFamily = MaterialTheme.typography.bodyMedium.fontFamily
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Search products" }
                        )
                    }

                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { onSearch(query) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowForward,
                                contentDescription = "Search products",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(
                                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                    )
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, SEARCH_PLACEHOLDER)
                                }
                                try {
                                    voiceSearchLauncher.launch(intent)
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(
                                        context,
                                        "Voice search is not available on this device",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = "Search by voice",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogueContent(
    categories: List<Category>,
    metrics: CatalogueGridMetrics,
    refreshError: String?,
    onRetry: () -> Unit,
    onOpenCategory: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(metrics.columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = metrics.horizontalPaddingDp.dp,
            end = metrics.horizontalPaddingDp.dp,
            top = 16.dp,
            bottom = 24.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(metrics.gapDp.dp),
        verticalArrangement = Arrangement.spacedBy(metrics.rowGapDp.dp)
    ) {
        if (refreshError != null) {
            item(
                key = "refresh-error",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                CatalogueRefreshBanner(
                    message = refreshError,
                    onRetry = onRetry
                )
            }
        }

        itemsIndexed(categories, key = { _, category -> category.id }) { index, category ->
            val (containerColor, contentColor) = categoryColors(index)
            CatalogueCategoryTile(
                category = category,
                containerColor = containerColor,
                contentColor = contentColor,
                labelFontSize = metrics.labelFontSizeSp.sp,
                labelLineHeight = metrics.labelLineHeightSp.sp,
                onClick = { onOpenCategory(category.id) }
            )
        }
    }
}

@Composable
private fun CatalogueCategoryTile(
    category: Category,
    containerColor: Color,
    contentColor: Color,
    labelFontSize: TextUnit,
    labelLineHeight: TextUnit,
    onClick: () -> Unit
) {
    val imageUrl = category.imagePath?.takeIf {
        it.startsWith("http://") || it.startsWith("https://")
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                onClickLabel = "Open ${category.name}",
                role = Role.Button,
                onClick = onClick
            )
            .padding(bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(TILE_ASPECT_RATIO)
                .clip(RoundedCornerShape(18.dp))
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl != null) {
                SubcomposeAsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    loading = { CatalogueCategoryFallback(category.slug, contentColor) },
                    error = { CatalogueCategoryFallback(category.slug, contentColor) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(7.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                CatalogueCategoryFallback(category.slug, contentColor)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = category.name,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 34.dp)
                .padding(horizontal = 2.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = labelFontSize,
            lineHeight = labelLineHeight,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CatalogueCategoryFallback(slug: String, contentColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.60f)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = catalogueCategoryIcon(slug),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.fillMaxSize(0.5f)
        )
    }
}

private fun catalogueCategoryIcon(slug: String): ImageVector {
    return when (slug.lowercase()) {
        "dvxbxd", "daal", "grains-staples" -> Icons.Outlined.Grain
        "atta", "flour", "bakery-biscuits" -> Icons.Outlined.BakeryDining
        "spices-masalas", "oils-ghee" -> Icons.Outlined.Restaurant
        "snacks-namkeen", "chips-namkeen", "fastfood" -> Icons.Outlined.Fastfood
        "personal-care", "bath-body", "beauty", "grooming-hygiene" -> Icons.Outlined.Spa
        "household", "cleaning-essentials" -> Icons.Outlined.CleaningServices
        else -> Icons.Outlined.ShoppingBasket
    }
}

@Composable
private fun CatalogueRefreshBanner(
    message: String,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.appColors.warning,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            TextButton(onClick = onRetry) {
                Text(
                    text = "Retry",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CatalogueLoadingState(metrics: CatalogueGridMetrics) {
    // Same geometry as the real grid so the skeleton does not jump when data
    // arrives.
    LazyVerticalGrid(
        columns = GridCells.Fixed(metrics.columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = metrics.horizontalPaddingDp.dp,
            end = metrics.horizontalPaddingDp.dp,
            top = 16.dp,
            bottom = 24.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(metrics.gapDp.dp),
        verticalArrangement = Arrangement.spacedBy(metrics.rowGapDp.dp),
        userScrollEnabled = false
    ) {
        items(count = metrics.columns * 2) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(TILE_ASPECT_RATIO)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .height(13.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
    }
}

@Composable
private fun CatalogueMessageState(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(onClick = onAction) {
            Text(actionLabel, fontWeight = FontWeight.SemiBold)
        }
    }
}
