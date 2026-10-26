#!/usr/bin/env node
/**
 * Product Image Finder & Uploader for Balaji Provision Store
 * 
 * This script:
 * 1. Fetches all products from Supabase database
 * 2. Searches for product images using DuckDuckGo HTML scraping (no API key needed)
 * 3. Downloads and validates images (max 2MB, JPEG/PNG/WebP)
 * 4. Uploads to Supabase storage (product-images bucket)
 * 5. Updates product records with image paths
 * 
 * Usage: node scripts/find-and-upload-product-images.js
 * Requires: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY in .env.local
 */

require('dotenv').config({ path: '.env.local' });
const { createClient } = require('@supabase/supabase-js');
const https = require('https');
const http = require('http');
const { URL } = require('url');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

// Configuration
const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL;
const SUPABASE_SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY;
const PRODUCT_IMAGES_BUCKET = 'product-images';
const CATEGORY_IMAGES_BUCKET = 'category-images';
const MAX_IMAGE_SIZE = 2 * 1024 * 1024; // 2MB
const ALLOWED_MIME_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const SEARCH_DELAY_MS = 1000; // Be respectful to search engines
const DOWNLOAD_TIMEOUT_MS = 10000;

// Create Supabase admin client
const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
  auth: { autoRefreshToken: false, persistSession: false }
});

// Helper: Sleep utility
const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));

// Helper: Generate search queries for a product
function generateSearchQueries(product) {
  const queries = [];
  const name = product.name;
  const category = product.category?.name || '';
  const sku = product.sku || '';
  
  // Primary search: product name + brand-like terms
  queries.push(`${name} product packaging`);
  queries.push(`${name} grocery item`);
  queries.push(`${name} pack`);
  
  // If we have category, use it
  if (category) {
    queries.push(`${name} ${category} packaging`);
  }
  
  // SKU-based search (sometimes reveals brand)
  if (sku) {
    queries.push(`${sku} product`);
  }
  
  return queries;
}

// Helper: Search DuckDuckGo for images (HTML scraping - no API key needed)
async function searchDuckDuckGoImages(query) {
  const searchUrl = `https://duckduckgo.com/html/?q=${encodeURIComponent(query + ' product image')}&iax=images&ia=images`;
  
  return new Promise((resolve, reject) => {
    const req = https.get(searchUrl, { timeout: 10000 }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          // Parse image URLs from HTML
          const imageUrls = [];
          const imgRegex = /<img[^>]+class="tile--img[^>]*data-src="([^"]+)"/g;
          let match;
          while ((match = imgRegex.exec(data)) !== null) {
            if (match[1].startsWith('http')) {
              imageUrls.push(match[1]);
            }
          }
          
          // Also try alternative pattern
          const altRegex = /<img[^>]+src="([^"]+\.(?:jpg|jpeg|png|webp)[^"]*)"/gi;
          while ((match = altRegex.exec(data)) !== null) {
            if (match[1].startsWith('http') && !match[1].includes('duckduckgo.com')) {
              imageUrls.push(match[1]);
            }
          }
          
          resolve([...new Set(imageUrls)].slice(0, 5)); // Deduplicate, max 5
        } catch (e) {
          resolve([]);
        }
      });
    });
    
    req.on('error', reject);
    req.on('timeout', () => { req.destroy(); reject(new Error('Search timeout')); });
  });
}

// Helper: Download image with validation
async function downloadImage(imageUrl) {
  return new Promise((resolve, reject) => {
    const url = new URL(imageUrl);
    const client = url.protocol === 'https:' ? https : http;
    
    const req = client.get(imageUrl, { 
      timeout: DOWNLOAD_TIMEOUT_MS,
      headers: { 'User-Agent': 'Mozilla/5.0 (compatible; BalajiBot/1.0)' }
    }, (res) => {
      // Check content type
      const contentType = res.headers['content-type'];
      if (!contentType || !ALLOWED_MIME_TYPES.includes(contentType.toLowerCase())) {
        req.destroy();
        return reject(new Error(`Invalid content type: ${contentType}`));
      }
      
      // Check content length
      const contentLength = parseInt(res.headers['content-length'] || '0');
      if (contentLength > MAX_IMAGE_SIZE) {
        req.destroy();
        return reject(new Error(`Image too large: ${contentLength} bytes`));
      }
      
      const chunks = [];
      let totalSize = 0;
      
      res.on('data', (chunk) => {
        totalSize += chunk.length;
        if (totalSize > MAX_IMAGE_SIZE) {
          req.destroy();
          return reject(new Error('Image exceeds 2MB during download'));
        }
        chunks.push(chunk);
      });
      
      res.on('end', () => {
        const buffer = Buffer.concat(chunks);
        // Double-check magic bytes
        const mimeType = detectMimeType(buffer);
        if (!ALLOWED_MIME_TYPES.includes(mimeType)) {
          return reject(new Error(`Invalid file type detected: ${mimeType}`));
        }
        resolve({ buffer, mimeType, extension: mimeTypeToExtension(mimeType) });
      });
    });
    
    req.on('error', reject);
    req.on('timeout', () => { req.destroy(); reject(new Error('Download timeout')); });
  });
}

// Helper: Detect MIME type from buffer magic bytes
function detectMimeType(buffer) {
  if (buffer.length < 12) return 'unknown';
  
  // JPEG: FF D8 FF
  if (buffer[0] === 0xFF && buffer[1] === 0xD8 && buffer[2] === 0xFF) {
    return 'image/jpeg';
  }
  // PNG: 89 50 4E 47 0D 0A 1A 0A
  if (buffer[0] === 0x89 && buffer[1] === 0x50 && buffer[2] === 0x4E && buffer[3] === 0x47) {
    return 'image/png';
  }
  // WebP: RIFF....WEBP
  if (buffer[0] === 0x52 && buffer[1] === 0x49 && buffer[2] === 0x46 && buffer[3] === 0x46 &&
      buffer[8] === 0x57 && buffer[9] === 0x45 && buffer[10] === 0x42 && buffer[11] === 0x50) {
    return 'image/webp';
  }
  
  return 'unknown';
}

function mimeTypeToExtension(mimeType) {
  switch (mimeType) {
    case 'image/jpeg': return 'jpg';
    case 'image/png': return 'png';
    case 'image/webp': return 'webp';
    default: return 'jpg';
  }
}

// Helper: Upload to Supabase storage
async function uploadToSupabase(buffer, fileName, bucket = PRODUCT_IMAGES_BUCKET) {
  const { data, error } = await supabase.storage
    .from(bucket)
    .upload(fileName, buffer, {
      contentType: mimeTypeToContentType(path.extname(fileName)),
      cacheControl: '31536000',
      upsert: false
    });
  
  if (error) throw error;
  
  // Get public URL
  const { data: urlData } = supabase.storage.from(bucket).getPublicUrl(data.path);
  return { path: data.path, publicUrl: urlData.publicUrl };
}

function mimeTypeToContentType(ext) {
  switch (ext.toLowerCase()) {
    case '.jpg': case '.jpeg': return 'image/jpeg';
    case '.png': return 'image/png';
    case '.webp': return 'image/webp';
    default: return 'image/jpeg';
  }
}

// Helper: Update product with image path
async function updateProductImage(productId, imagePath) {
  const { error } = await supabase
    .from('products')
    .update({ image_url: imagePath })
    .eq('id', productId);
  
  if (error) throw error;
  return true;
}

// Main function
async function findAndUploadProductImages() {
  console.log('🚀 Starting Product Image Search & Upload');
  console.log('==========================================\n');
  
  // 1. Fetch all products from database
  console.log('📦 Fetching products from database...');
  const { data: products, error: productsError } = await supabase
    .from('products')
    .select('id, sku, name, description, category_id, image_url, is_active')
    .eq('is_active', true)
    .order('name');
  
  if (productsError) {
    console.error('❌ Failed to fetch products:', productsError.message);
    process.exit(1);
  }
  
  console.log(`✅ Found ${products.length} active products\n`);
  
  // Fetch categories for better search
  const { data: categories } = await supabase
    .from('categories')
    .select('id, name');
  
  const categoryMap = new Map(categories?.map(c => [c.id, c.name]) || []);
  
  // Add category to products
  products.forEach(p => {
    p.category = categoryMap.get(p.category_id) ? { name: categoryMap.get(p.category_id) } : null;
  });
  
  // 2. Process each product
  let successCount = 0;
  let skipCount = 0;
  let errorCount = 0;
  
  for (const product of products) {
    console.log(`\n🔍 Processing: ${product.name} (${product.sku})`);
    
    // Skip if already has image
    if (product.image_url && product.image_url.startsWith('http')) {
      console.log(`   ⏭️  Already has image: ${product.image_url}`);
      skipCount++;
      continue;
    }
    
    // Generate search queries
    const queries = generateSearchQueries(product);
    let imageFound = false;
    
    for (const query of queries) {
      if (imageFound) break;
      
      console.log(`   🔎 Searching: "${query}"`);
      
      try {
        const imageUrls = await searchDuckDuckGoImages(query);
        
        if (imageUrls.length === 0) {
          console.log(`   ⚠️  No images found for query`);
          continue;
        }
        
        console.log(`   📸 Found ${imageUrls.length} candidate images`);
        
        // Try each image URL until one works
        for (const imageUrl of imageUrls) {
          try {
            console.log(`   ⬇️  Downloading: ${imageUrl.substring(0, 80)}...`);
            const { buffer, mimeType, extension } = await downloadImage(imageUrl);
            
            // Generate unique filename
            const hash = crypto.createHash('md5').update(product.id).digest('hex').slice(0, 8);
            const fileName = `products/${product.sku || product.id}-${hash}.${extension}`;
            
            console.log(`   ☁️  Uploading to Supabase: ${fileName}`);
            const { path: storagePath, publicUrl } = await uploadToSupabase(buffer, fileName);
            
            console.log(`   💾 Updating database...`);
            await updateProductImage(product.id, storagePath);
            
            console.log(`   ✅ Success! Image: ${publicUrl}`);
            successCount++;
            imageFound = true;
            break;
            
          } catch (downloadError) {
            console.log(`   ❌ Failed: ${downloadError.message}`);
            continue;
          }
        }
        
      } catch (searchError) {
        console.log(`   ❌ Search failed: ${searchError.message}`);
      }
      
      // Be respectful - delay between searches
      await sleep(SEARCH_DELAY_MS);
    }
    
    if (!imageFound) {
      console.log(`   ⚠️  No suitable image found for ${product.name}`);
      errorCount++;
    }
  }
  
  // Summary
  console.log('\n==========================================');
  console.log('📊 SUMMARY');
  console.log('==========================================');
  console.log(`✅ Successfully uploaded: ${successCount}`);
  console.log(`⏭️  Skipped (already had images): ${skipCount}`);
  console.log(`❌ Failed to find images: ${errorCount}`);
  console.log(`📦 Total products processed: ${products.length}`);
}

// Run the script
findAndUploadProductImages()
  .then(() => {
    console.log('\n🎉 Done!');
    process.exit(0);
  })
  .catch(err => {
    console.error('\n💥 Fatal error:', err);
    process.exit(1);
  });