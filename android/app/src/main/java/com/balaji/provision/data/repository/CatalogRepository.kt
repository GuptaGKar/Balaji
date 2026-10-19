package com.balaji.provision.data.repository

import com.balaji.provision.domain.model.Category
import com.balaji.provision.domain.model.Product
import com.balaji.provision.domain.model.QuantityType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogRepository @Inject constructor() {
    
    var liveProducts: List<Product> = emptyList()
    var liveCategories: List<Category> = emptyList()
    
    val sampleCategories = listOf(
        Category("cat-atta", "Atta & Flour", "atta-flour", 1, true),
        Category("cat-dal", "Dal & Pulses", "dal-pulses", 2, true),
        Category("cat-rice", "Rice & Grains", "rice-grains", 3, true),
        Category("cat-oil", "Oils & Ghee", "oils-ghee", 4, true),
        Category("cat-spices", "Spices & Salt", "spices-salt", 5, true),
        Category("cat-dairy", "Dairy & Essentials", "dairy-essentials", 6, true)
    )
    
    val sampleProducts = listOf(
        Product(
            id = "p-1",
            sku = "ATTA-01",
            name = "Aashirvaad Shudh Chakki Atta",
            description = "100% pure whole wheat grain flour, soft & tasty rotis.",
            categoryId = "cat-atta",
            imagePath = null,
            quantityType = QuantityType.PACKED,
            pricePaise = 24000,
            priceBasisQty = 1,
            unitLabel = "5kg pack",
            packSizeLabel = "5 kg",
            stepQty = 1,
            minQty = 1,
            maxQty = 5,
            stockQty = 50,
            lowStockThreshold = 5,
            active = true,
            available = true
        ),
        Product(
            id = "p-2",
            sku = "OIL-01",
            name = "Fortune Sunlite Sunflower Oil",
            description = "Refined sunflower oil, rich in vitamins & healthy nutrients.",
            categoryId = "cat-oil",
            imagePath = null,
            quantityType = QuantityType.PACKED,
            pricePaise = 14500,
            priceBasisQty = 1,
            unitLabel = "1L pouch",
            packSizeLabel = "1 L",
            stepQty = 1,
            minQty = 1,
            maxQty = 10,
            stockQty = 30,
            lowStockThreshold = 5,
            active = true,
            available = true
        )
    )
    
    fun findProductById(id: String): Product? {
        return liveProducts.find { it.id == id } ?: sampleProducts.find { it.id == id }
    }
}