#!/usr/bin/env python3
"""
Image Search and Upload Script for Balaji Provision Store

This script searches for product images online and uploads them to Supabase storage.
"""

import os
import json
import requests
import sqlite3
from urllib.parse import quote
import urllib.parse
import re
from pathlib import Path
import time

# Add current directory to Python path to import our configuration
import sys
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

# Configuration
SUPABASE_URL = "https://czbwwnlnmhhzketyjnzt.supabase.co"
SUPABASE_SERVICE_KEY = os.environ.get("SUPABASE_SERVICE_KEY", "placeholder")
UPLOAD_TIMEOUT = 30
MAX_RETRIES = 3

class ImageSearchError(Exception):
    pass

class ImageSearchUploader:
    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
        })
        
        # Product data from JSON
        self.products = self._load_products()
        self.categories = self._load_categories()
        
        # Statistics
        self.stats = {
            'total_products': len(self.products),
            'found_images': 0,
            'failed_searches': 0,
            'uploaded_images': 0,
            'skipped_existing': 0
        }
    
    def _load_products(self):
        """Load products from the JSON file"""
        json_path = Path(__file__).parent / 'product-images.json'
        with open(json_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
        return data['products']
    
    def _load_categories(self):
        """Load categories from the JSON file"""
        json_path = Path(__file__).parent / 'product-images.json'
        with open(json_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
        return {cat['slug']: cat for cat in data['categories']}
    
    def google_image_search(self, query, num_results=3):
        """
        Search Google Images for a product
        Returns list of image URLs
        """
        search_url = "https://www.google.com/search"
        params = {
            'q': query,
            'tbm': 'isch',  # Image search
            'ijn': 0,
            'start': 0
        }
        
        try:
            response = self.session.get(search_url, params=params, timeout=10)
            response.raise_for_status()
            
            # Extract image URLs using regex
            image_urls = []
            # Look for common image patterns in the response
            patterns = [
                r'https?://[^"\'\s]+\.(?:jpg|jpeg|png|webp|gif)',
                r'https?://[^"\'\s]+\.(?:jpg|jpeg|png|webp|gif)\?[^"]*',
                r'"https?://[^"\'\s]+\.(?:jpg|jpeg|png|webp|gif)[^"]*"'
            ]
            
            for pattern in patterns:
                matches = re.findall(pattern, response.text, re.IGNORECASE)
                for match in matches:
                    # Clean up the URL
                    match = re.sub(r'\\[\"\']', '', match)
                    if any(x in match for x in ['google.com', 'googleusercontent.com', 'gstatic.com']):
                        continue  # Skip Google logos and ads
                    if match.startswith('//'):
                        match = 'https:' + match
                    if match not in image_urls:
                        image_urls.append(match)
                
                if image_urls:
                    break
            
            # Also try to extract from image data-src attributes
            if not image_urls:
                # Look for data-src or src attributes with image URLs
                data_src_matches = re.findall(r'data-src="([^"]+\.(?:jpg|jpeg|png|webp|gif))', response.text, re.IGNORECASE)
                for match in data_src_matches:
                    if match not in image_urls:
                        image_urls.append(match)
            
            return image_urls[:num_results]
            
        except Exception as e:
            print(f"Error searching for '{query}': {e}")
            return []
    
    def download_image(self, url, output_path):
        """
        Download an image from URL
        Returns True if successful
        """
        for attempt in range(MAX_RETRIES):
            try:
                response = self.session.get(url, timeout=UPLOAD_TIMEOUT, stream=True)
                response.raise_for_status()
                
                # Check if it's actually an image
                content_type = response.headers.get('content-type', '')
                if 'image' not in content_type:
                    print(f"  Skipping non-image content: {content_type}")
                    return False
                
                # Write image to file
                with open(output_path, 'wb') as f:
                    for chunk in response.iter_content(chunk_size=8192):
                        f.write(chunk)
                
                print(f"  Downloaded: {url[:80]}...")
                return True
                
            except Exception as e:
                print(f"  Attempt {attempt + 1} failed: {e}")
                time.sleep(2 ** attempt)  # Exponential backoff
                continue
        
        return False
    
    def upload_to_supabase(self, local_path, bucket, remote_path):
        """
        Upload a local file to Supabase storage
        Returns True if successful
        """
        url = f"{SUPABASE_URL}/storage/v1/object/{bucket}/{remote_path}"
        
        headers = {
            'Authorization': f'Bearer {SUPABASE_SERVICE_KEY}',
            'Content-Type': 'application/octet-stream'
        }
        
        try:
            with open(local_path, 'rb') as f:
                data = f.read()
            
            response = self.session.put(url, headers=headers, data=data, timeout=UPLOAD_TIMEOUT)
            response.raise_for_status()
            
            print(f"  Uploaded to Supabase: {remote_path}")
            return True
            
        except Exception as e:
            print(f"  Supabase upload failed: {e}")
            return False
    
    def search_and_upload_all(self):
        """
        Main method to search and upload all product images
        """
        print(f"Starting image search for {self.stats['total_products']} products")
        print("=" * 80)
        
        for product in self.products:
            if product.get('existing_image'):
                self.stats['skipped_existing'] += 1
                print(f"SKIP: {product['sku']} - {product['name']} (already has image)")
                continue
            
            print(f"\nProcessing: {product['sku']} - {product['name']}")
            print(f"  Category: {product['category']}")
            
            # Search for images
            image_urls = self.google_image_search(product['search_term'])
            
            if not image_urls:
                self.stats['failed_searches'] += 1
                print(f"  ❌ No images found")
                continue
            
            print(f"  ✅ Found {len(image_urls)} image(s)")
            
            # Download the best image (first one)
            image_url = image_urls[0]
            
            # Create local directory for product
            product_dir = Path('temp_images')
            product_dir.mkdir(exist_ok=True)
            
            # Determine file extension
            ext = image_url.split('.')[-1].split('?')[0]
            if ext not in ['jpg', 'jpeg', 'png', 'webp', 'gif']:
                ext = 'jpg'
            
            local_path = product_dir / f"{product['sku']}.{ext}"
            
            # Download image
            if self.download_image(image_url, local_path):
                # Upload to Supabase
                remote_path = f"products/{product['sku']}.{ext}"
                if self.upload_to_supabase(local_path, 'product-images', remote_path):
                    self.stats['found_images'] += 1
                    self.stats['uploaded_images'] += 1
                    
                    # Clean up local file
                    local_path.unlink()
                else:
                    self.stats['found_images'] += 1  # Count as found even if upload fails
            else:
                self.stats['failed_searches'] += 1
        
        # Upload category images
        print("\n" + "=" * 80)
        print("Uploading category images...")
        
        for category in self.categories.values():
            print(f"\nSearching for category: {category['name']}")
            image_urls = self.google_image_search(category['search_term'])
            
            if not image_urls:
                print(f"  ❌ No images found for {category['name']}")
                continue
            
            # Download the best image
            image_url = image_urls[0]
            
            ext = image_url.split('.')[-1].split('?')[0]
            if ext not in ['jpg', 'jpeg', 'png', 'webp', 'gif']:
                ext = 'jpg'
            
            local_path = Path('temp_images') / f"{category['slug']}.{ext}"
            
            if self.download_image(image_url, local_path):
                remote_path = f"categories/{category['slug']}.{ext}"
                if self.upload_to_supabase(local_path, 'category-images', remote_path):
                    print(f"  ✅ Uploaded category image: {category['name']}")
                    local_path.unlink()
                else:
                    print(f"  ⚠️  Failed to upload category image: {category['name']}")
                    local_path.unlink()
            else:
                print(f"  ❌ Failed to download category image: {category['name']}")
        
        # Clean up temp directory
        import shutil
        temp_dir = Path('temp_images')
        if temp_dir.exists():
            shutil.rmtree(temp_dir)
        
        # Print summary
        self.print_summary()
    
    def print_summary(self):
        print("\n" + "=" * 80)
        print("IMAGE SEARCH AND UPLOAD SUMMARY")
        print("=" * 80)
        print(f"Total products processed: {self.stats['total_products']}")
        print(f"Products with existing images: {self.stats['skipped_existing']}")
        print(f"Images found and uploaded: {self.stats['uploaded_images']}")
        print(f"Search failures: {self.stats['failed_searches']}")
        print(f"Total images found: {self.stats['found_images']}")
        
        # Calculate success rate
        successful = self.stats['uploaded_images'] / self.stats['total_products'] * 100
        print(f"\nSuccess rate: {successful:.1f}%")

if __name__ == '__main__':
    # Initialize and run the image search and upload
    uploader = ImageSearchUploader()
    uploader.search_and_upload_all()