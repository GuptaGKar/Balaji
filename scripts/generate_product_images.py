#!/usr/bin/env python3
"""
Script to generate synthetic images for all 79 Balaji Provision Store products
since we don't have API access to search for real images.
"""

import os
import json
import re
from PIL import Image, ImageDraw, ImageFont
import random

# Configuration
PRODUCTS_FILE = "/home/linuai/Balaji/data/products.json"
OUTPUT_DIR = "/home/linuai/Balaji/scripts/generated_images"
COLORS = [
    (255, 105, 180),  # HotPink
    (135, 206, 235),  # SkyBlue
    (60, 179, 113),   # MediumSeaGreen
    (255, 140, 0),    # DarkOrange
    (138, 43, 226),   # DarkOrchid
    (0, 191, 255),    # DeepSkyBlue
    (255, 105, 180),  # HotPink
    (70, 130, 180),   # SteelBlue
    (255, 165, 0),    # Orange
    (154, 205, 50),   # MediumGreen
    (255, 192, 203),  # LightPink
    (221, 160, 221),  # Plum
    (176, 196, 222),  # LightSteelBlue
    (152, 251, 152),  # LightGreen
    (255, 250, 205),  # LemonChiffon
    (245, 222, 179),  # Wheat
    (255, 255, 240),  # FloralWhite
    (180, 220, 255),  # LightSkyBlue
    (255, 245, 238),  # MintCream
    (255, 255, 224),  # LightYellow
    (255, 215, 180),  # Moccasin
    (255, 235, 156),  # Khaki
    (255, 255, 255),  # White
    (240, 248, 255),  # AliceBlue
    (255, 255, 240),  # LightYellow
    (210, 105, 30),   # Sienna
    (176, 196, 222),  # LightSteelBlue
    (144, 238, 144),  # LightGreen
    (152, 251, 152),  # LightGreen
    (128, 0, 0),      # Maroon
    (255, 165, 0),    # Orange
    (255, 215, 0),    # Gold
    (128, 0, 128),    # Purple
    (0, 0, 128),      # Navy
    (255, 192, 203),  # Pink
    (173, 216, 230),  # LightBlue
    (255, 250, 205),  # LemonChiffon
    (250, 240, 245),  # LavenderBlush
    (245, 245, 220),  # Bisque
    (173, 216, 230),  # LightBlue
    (221, 175, 55),   # DarkGoldenrod
    (139, 118, 103),  # Saddlebrown
    (160, 32, 255),   # BlueViolet
    (154, 205, 50),   # LawnGreen
    (128, 0, 128),    # Purple
    (70, 130, 180),   # SteelBlue
    (255, 165, 0),    # OrangeRed
    (255, 218, 185),  # PeachPuff
    (154, 205, 50),   # LawnGreen
    (130, 130, 125),  # DarkGray
    (128, 0, 0),      # Maroon
    (255, 165, 0),    # Orange
    (255, 255, 0),    # Yellow
    (128, 0, 128),    # Purple
    (0, 128, 0),      # Green
    (0, 0, 255),      # Blue
    (255, 255, 255),  # White
]

def create_synthetic_image(product_name, product_id):
    """Create a synthetic image with product name and color"""
    # Create a blank image
    img = Image.new('RGB', (400, 200), color=(240, 240, 240))
    draw = ImageDraw.Draw(img)
    
    # Choose a random color
    color = random.choice(COLORS)
    
    try:
        font = ImageFont.truetype("arial.ttf", 24)
    except:
        try:
            font = ImageFont.truetype("DejaVuSans.ttf", 24)
        except:
            font = ImageFont.load_default()
    
    # Add product name text
    draw.text((20, 50), product_name, fill=color, font=font)
    
    # Add product ID
    draw.text((20, 150), f"ID: {product_id}", fill=(100, 100, 100), font=ImageFont.load_default())
    
    # Save image
    safe_name = re.sub(r'[^\w\s-]', '', product_name).replace(' ', '_')[:30]
    img_path = os.path.join(OUTPUT_DIR, f"{safe_name}_{product_id}.png")
    img.save(img_path)
    return img_path

def main():
    # Load products
    with open(PRODUCTS_FILE, 'r') as f:
        products = json.load(f)
    
    print(f"🔄 Processing {len(products)} products...")
    
    # Ensure output directory exists
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    for i, product in enumerate(products):
        product_name = product.get("name", "Unknown Product")
        product_id = product.get("id", f"unknown_{i}")
        
        print(f"🔍 Generating image for: {product_name} (ID: {product_id})")
        
        # Generate synthetic image
        img_path = create_synthetic_image(product_name, product_id)
        
        print(f"✅ Generated image: {img_path}")
        print("-" * 50)

if __name__ == "__main__":
    main()