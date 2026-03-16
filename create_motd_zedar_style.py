from PIL import Image, ImageDraw, ImageFont
import os

def hex_to_rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))

def apply_gradient(img, start_color, end_color):
    """Applies a horizontal gradient to the non-transparent pixels of an image."""
    width, height = img.size
    pixels = img.load()
    r1, g1, b1 = start_color
    r2, g2, b2 = end_color
    
    for x in range(width):
        # Calculate color for this column
        r = int(r1 + (r2 - r1) * (x / max(1, width - 1)))
        g = int(g1 + (g2 - g1) * (x / max(1, width - 1)))
        b = int(b1 + (b2 - b1) * (x / max(1, width - 1)))
        
        for y in range(height):
            _, _, _, a = pixels[x, y]
            if a > 0:
                pixels[x, y] = (r, g, b, a)
    return img

def draw_text_with_spacing(draw, text, start_x, y, font, spacing, fill):
    """Draws text with custom letter spacing."""
    x = start_x
    for char in text:
        draw.text((x, y), char, font=font, fill=fill)
        # Get char width (default PIL font is usually 6px wide)
        x += 6 + spacing # default width + custom spacing

def main():
    width, height = 264, 16
    # Create main image (transparent background or dark gray for visibility while testing)
    img = Image.new('RGBA', (width, height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    # Load default pixel font (built-in PIL font is 6x8 pixels usually)
    font = ImageFont.load_default()

    # --- 1. NATURALSMP (Left, Line 1 & Line 2, Large/Tight Spacing) ---
    # Since we only have 16px height, we will just use the standard font 
    # but we can apply a gradient. We will draw it on a temp canvas first.
    text_natural = "NATURALSMP"
    temp_natural = Image.new('RGBA', (70, 10), (0, 0, 0, 0))
    temp_draw = ImageDraw.Draw(temp_natural)
    # Tight spacing (-1)
    draw_text_with_spacing(temp_draw, text_natural, 0, 0, font, -1, (255, 255, 255, 255))
    # Gradient for NATURAL SMP (Cyan to Green)
    temp_natural = apply_gradient(temp_natural, hex_to_rgb("#3BAFFF"), hex_to_rgb("#00FF5A"))
    # Paste it slightly centered vertically on the left
    img.paste(temp_natural, (2, 4), temp_natural)

    # --- 2. The Most Immersive Experience (Line 1, Middle/Right) ---
    text_immersive = "The Most Immersive Experience"
    draw.text((68, 0), text_immersive, font=font, fill=hex_to_rgb("#AAAAAA"))

    # --- 3. Join now to explore our PREMIUM FEATURES! (Line 2, Right) ---
    text_join = "Join now to explore our "
    text_premium = "PREMIUM FEATURES!"
    draw.text((68, 8), text_join, font=font, fill=hex_to_rgb("#FFFF55"))
    # Calculate offset for PREMIUM FEATURES
    join_width = len(text_join) * 6
    draw.text((68 + join_width, 8), text_premium, font=font, fill=hex_to_rgb("#FFAA00"))

    # --- 4. www.NaturalSMP.net (Below NATURALSMP if possible, but we only have 16px) ---
    # Since NATURALSMP is on y=4, we can put www.naturalsmp.net at the bottom or top left.
    # Actually, let's put it on Line 3? We only have 16 pixels (y=0 to 15).
    # We can't fit 3 lines of 8px text in 16px. 
    # Let's adjust NATURALSMP to line 1 (y=0) and www.NaturalSMP.net to line 2 (y=8).
    
    # Clear and redraw with better layout
    img = Image.new('RGBA', (width, height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    # --- Line 1 (y=0) ---
    # NATURALSMP (tight spacing)
    temp_natural = Image.new('RGBA', (65, 10), (0, 0, 0, 0))
    temp_draw = ImageDraw.Draw(temp_natural)
    draw_text_with_spacing(temp_draw, text_natural, 0, 0, font, -1, (255, 255, 255, 255))
    temp_natural = apply_gradient(temp_natural, hex_to_rgb("#3BAFFF"), hex_to_rgb("#00FF5A"))
    img.paste(temp_natural, (2, 0), temp_natural)
    
    # "The Most Immersive Experience"
    draw.text((65, 0), text_immersive, font=font, fill=hex_to_rgb("#AAAAAA"))
    
    # --- Line 2 (y=8) ---
    # www.NaturalSMP.net
    draw.text((2, 8), "www.NaturalSMP.net", font=font, fill=hex_to_rgb("#E92AFE"))
    
    # "Join now to explore our PREMIUM FEATURES!"
    # Since line 2 left is taken (about 18 chars = 108px), we start at x=115
    # Wait, the total width is 264. 115 + (41 chars * 6 = 246) = 361. Too wide!
    # We must shorten it or overlap. Let's make "Join now..." start at x=115 and truncate or move it.
    # Actually, we can move it to x=65 (under The Most Immersive) and overlap.
    draw.text((65, 8), text_join, font=font, fill=hex_to_rgb("#FFFF55"))
    draw.text((65 + join_width, 8), "PREMIUM!", font=font, fill=hex_to_rgb("#FFAA00"))

    # --- 5. Arrow at Top Right (y=0 to 5) pointing 75 degrees max right/up ---
    # We have x=250 to 260 available at the top right.
    # Arrow drawing:
    ax, ay = 258, 2
    arrow_color = hex_to_rgb("#00FF5A")
    # Top-right pointing arrow
    draw.line((ax-4, ay+4, ax, ay), fill=arrow_color, width=1) # Main diagonal
    draw.line((ax-4, ay, ax, ay), fill=arrow_color, width=1)   # Top horizontal
    draw.line((ax, ay, ax, ay+4), fill=arrow_color, width=1)   # Right vertical

    # Add a shadow for depth
    # ...

    out_path = "D:\\NaturalSMP\\plugin\\NaturalVelocity\\images\\motd_zedar.png"
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    img.save(out_path)
    print(f"MOTD image successfully generated at: {out_path}")

if __name__ == "__main__":
    main()
