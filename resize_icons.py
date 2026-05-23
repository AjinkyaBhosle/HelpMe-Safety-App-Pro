from PIL import Image
import os

# Icon sizes for different densities
sizes = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192
}

# Load source icon
source = Image.open('resources/icon.png')

# Generate icons for each density
for density, size in sizes.items():
    folder = f'android/app/src/main/res/mipmap-{density}'
    os.makedirs(folder, exist_ok=True)
    
    # Resize and save
    resized = source.resize((size, size), Image.LANCZOS)
    resized.save(f'{folder}/ic_launcher.png')
    resized.save(f'{folder}/ic_launcher_round.png')
    print(f'Generated {density}: {size}x{size}')

print('Icon generation complete!')
