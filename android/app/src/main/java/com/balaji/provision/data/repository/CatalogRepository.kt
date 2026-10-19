package com.balaji.provision.data.repository

import com.balaji.provision.domain.model.Category
import com.balaji.provision.domain.model.Product
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogRepository @Inject constructor() {

    var liveProducts: List<Product> = emptyList()
    var liveCategories: List<Category> = emptyList()

    fun findProductById(id: String): Product? = liveProducts.find { it.id == id }
}
