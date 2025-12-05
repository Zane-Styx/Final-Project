from PIL import Image, ImageDraw, ImageFont

# Create a 128x128 transparent image
img = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
draw = ImageDraw.Draw(img)

# Try to use a monospace font
try:
    font = ImageFont.truetype("C:\\Windows\\Fonts\\consola.ttf", 10)
except:
    try:
        font = ImageFont.truetype("C:\\Windows\\Fonts\\arial.ttf", 8)
    except:
        font = ImageFont.load_default()

# Character grid: 8x10 pixels per character, 4 rows
# Row 4: a-p (y: 118-128)
chars_row1 = "abcdefghijklmnop"
for i, char in enumerate(chars_row1):
    x = (i % 16) * 8
    y = 118
    draw.text((x, y), char, fill=(255, 255, 255, 255), font=font)

# Row 3: q-z, 1-6 (y: 108-118)
chars_row2 = "qrstuvwxyz123456"
for i, char in enumerate(chars_row2):
    x = (i % 16) * 8
    y = 108
    draw.text((x, y), char, fill=(255, 255, 255, 255), font=font)

# Row 2: 7-0, symbols (y: 98-108)
chars_row3 = "7890#.!?:*%()-+"
for i, char in enumerate(chars_row3):
    x = (i % 16) * 8
    y = 98
    draw.text((x, y), char, fill=(255, 255, 255, 255), font=font)

# Row 1: symbols (y: 88-98)
chars_row4 = "\\=/><"
for i, char in enumerate(chars_row4):
    x = (i % 16) * 8
    y = 88
    draw.text((x, y), char, fill=(255, 255, 255, 255), font=font)

# Save the image
output_path = r"c:\Users\Styx\Desktop\Final-Project\Chromashift\assets\ui\ctm.uiskin.png"
img.save(output_path, 'PNG')
print(f"Created {output_path}")
