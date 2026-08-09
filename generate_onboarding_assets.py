import os
import math
from PIL import Image, ImageDraw, ImageFont

res_dir = r"c:\Users\shrikant\.gemini\antigravity-ide\scratch\LocalLoop\app\src\main\res\drawable"
os.makedirs(res_dir, exist_ok=True)

GREEN_PRIMARY = (0, 138, 60, 255)        # #008A3C
GREEN_DARK = (0, 95, 42, 255)           # #005F2A
GREEN_LIGHT = (235, 247, 239, 255)      # #EBF7EF
GREEN_MID = (16, 185, 129, 255)         # #10B981
GREEN_SOFT = (167, 243, 208, 255)       # #A7F3D0
SKYLINE_COLOR = (215, 238, 224, 255)    # Faint skyline
DARK_TEXT = (25, 32, 50, 255)           # #192038
SLATE_GREY = (110, 124, 140, 255)       # #6E7C8C
WHITE = (255, 255, 255, 255)
TRANSPARENT = (0, 0, 0, 0)
SKIN_LIGHT = (255, 219, 186, 255)
SKIN_MID = (224, 172, 105, 255)
SKIN_DARK = (141, 85, 36, 255)
HAIR_DARK = (30, 30, 30, 255)

def draw_logo_pin(size=512):
    scale = 2
    s = size * scale
    img = Image.new("RGBA", (s, s), TRANSPARENT)
    draw = ImageDraw.Draw(img)
    
    cx, cy = s // 2, s // 2 - 25 * scale
    r = 175 * scale
    
    # 1. Outer green pin body (Circle + Tip)
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=GREEN_PRIMARY)
    pin_tip = [(cx - 85 * scale, cy + 90 * scale), (cx + 85 * scale, cy + 90 * scale), (cx, cy + 235 * scale)]
    draw.polygon(pin_tip, fill=GREEN_PRIMARY)
    
    # Dark green folded triangular accent at the bottom tip
    fold_tip = [(cx - 45 * scale, cy + 120 * scale), (cx + 45 * scale, cy + 120 * scale), (cx, cy + 205 * scale)]
    draw.polygon(fold_tip, fill=GREEN_DARK)

    # 2. White inner ring
    wr = 135 * scale
    draw.ellipse([cx - wr, cy - wr, cx + wr, cy + wr], fill=WHITE)

    # 3. Inner green circle
    gr = 110 * scale
    draw.ellipse([cx - gr, cy - gr, cx + gr, cy + gr], fill=GREEN_PRIMARY)

    # 4. Three White People Silhouettes inside the green circle
    # Center person (front)
    head_r = 28 * scale
    draw.ellipse([cx - head_r, cy - 52 * scale - head_r, cx + head_r, cy - 52 * scale + head_r], fill=WHITE)
    draw.chord([cx - 48 * scale, cy - 20 * scale, cx + 48 * scale, cy + 60 * scale], start=180, end=360, fill=WHITE)

    # Left person (behind center)
    l_cx = cx - 42 * scale
    l_cy = cy - 20 * scale
    l_head_r = 22 * scale
    draw.ellipse([l_cx - l_head_r, l_cy - 35 * scale - l_head_r, l_cx + l_head_r, l_cy - 35 * scale + l_head_r], fill=WHITE)
    draw.chord([l_cx - 38 * scale, l_cy - 10 * scale, l_cx + 38 * scale, l_cy + 50 * scale], start=180, end=360, fill=WHITE)

    # Right person (behind center)
    r_cx = cx + 42 * scale
    r_cy = cy - 20 * scale
    r_head_r = 22 * scale
    draw.ellipse([r_cx - r_head_r, r_cy - 35 * scale - r_head_r, r_cx + r_head_r, r_cy - 35 * scale + r_head_r], fill=WHITE)
    draw.chord([r_cx - 38 * scale, r_cy - 10 * scale, r_cx + 38 * scale, r_cy + 50 * scale], start=180, end=360, fill=WHITE)

    img = img.resize((size, size), Image.Resampling.LANCZOS)
    img.save(os.path.join(res_dir, "ic_aroundus_logo_pin_new.png"))
    print("Saved logo pin.")

def draw_neighborhood_slide(w=1200, h=800):
    scale = 2
    sw, sh = w * scale, h * scale
    img = Image.new("RGBA", (sw, sh), TRANSPARENT)
    draw = ImageDraw.Draw(img)

    # 1. Soft mint background gradient sky
    for y in range(sh):
        factor = y / sh
        r_c = int(248 + (235 - 248) * factor)
        g_c = int(253 + (247 - 253) * factor)
        b_c = int(249 + (239 - 249) * factor)
        draw.line([(0, y), (sw, y)], fill=(r_c, g_c, b_c, 255))

    # Soft clouds in sky
    for cx_p, cy_p, cr in [(sw*0.2, sh*0.25, 90*scale), (sw*0.8, sh*0.28, 110*scale), (sw*0.5, sh*0.18, 70*scale)]:
        draw.ellipse([cx_p - cr*1.5, cy_p - cr*0.6, cx_p + cr*1.5, cy_p + cr*0.6], fill=(255, 255, 255, 180))

    # Faint flying birds
    for bx, by in [(sw*0.42, sh*0.2), (sw*0.46, sh*0.17)]:
        draw.arc([bx - 20*scale, by - 10*scale, bx, by + 10*scale], start=200, end=360, fill=(160, 190, 175, 255), width=3*scale)
        draw.arc([bx, by - 10*scale, bx + 20*scale, by + 10*scale], start=180, end=340, fill=(160, 190, 175, 255), width=3*scale)

    # 2. Background City Skyline (Faint Green Silhouettes)
    skyline_y = sh - 280 * scale
    buildings = [
        (60*scale, 200*scale, 120*scale),
        (200*scale, 280*scale, 140*scale),
        (370*scale, 180*scale, 150*scale),
        (550*scale, 310*scale, 130*scale),
        (710*scale, 230*scale, 160*scale),
        (900*scale, 260*scale, 140*scale),
        (1060*scale, 190*scale, 120*scale)
    ]
    for x, b_h, b_w in buildings:
        draw.rectangle([x, skyline_y - b_h, x + b_w, skyline_y + 150*scale], fill=SKYLINE_COLOR)
        # Windows on skyline buildings
        for wx in range(x + 15*scale, x + b_w - 15*scale, 22*scale):
            for wy in range(skyline_y - b_h + 20*scale, skyline_y - 20*scale, 30*scale):
                draw.rectangle([wx, wy, wx + 10*scale, wy + 15*scale], fill=WHITE)

    # Background Trees behind skyline
    for tx in [100*scale, 280*scale, 500*scale, 720*scale, 980*scale]:
        draw.ellipse([tx - 60*scale, skyline_y - 100*scale, tx + 60*scale, skyline_y + 30*scale], fill=(170, 225, 195, 255))

    # 3. Green Grass Hills
    draw.ellipse([-150*scale, sh - 420*scale, sw // 2 + 150*scale, sh + 300*scale], fill=(210, 243, 222, 255))
    draw.ellipse([sw // 2 - 150*scale, sh - 390*scale, sw + 150*scale, sh + 300*scale], fill=(195, 238, 210, 255))
    draw.ellipse([-100*scale, sh - 280*scale, sw + 100*scale, sh + 400*scale], fill=(155, 228, 180, 255))

    # 4. Center Winding Grass Path
    path_points = [
        (sw // 2 - 50*scale, sh - 250*scale),
        (sw // 2 + 50*scale, sh - 250*scale),
        (sw // 2 + 220*scale, sh),
        (sw // 2 - 220*scale, sh)
    ]
    draw.polygon(path_points, fill=(242, 247, 243, 255))

    # 5. Houses
    # Front Left Cottage (Main green house)
    hx, hy = sw // 2 - 380*scale, sh - 410*scale
    # Green Roof
    draw.polygon([(hx + 120*scale, hy - 80*scale), (hx - 20*scale, hy + 40*scale), (hx + 260*scale, hy + 40*scale)], fill=GREEN_PRIMARY)
    # White House Body
    draw.rectangle([hx + 10*scale, hy + 40*scale, hx + 230*scale, hy + 210*scale], fill=WHITE)
    # Green Door
    draw.rectangle([hx + 90*scale, hy + 110*scale, hx + 150*scale, hy + 210*scale], fill=GREEN_PRIMARY)
    # Windows with green frame
    for wx, wy in [(hx + 30*scale, hy + 70*scale), (hx + 170*scale, hy + 70*scale)]:
        draw.rectangle([wx, wy, wx + 35*scale, wy + 45*scale], fill=GREEN_MID)
        draw.rectangle([wx + 5*scale, wy + 5*scale, wx + 30*scale, wy + 40*scale], fill=WHITE)

    # House 2 (Behind left house)
    hx2, hy2 = sw // 2 - 520*scale, sh - 360*scale
    draw.polygon([(hx2 + 90*scale, hy2 - 50*scale), (hx2, hy2 + 30*scale), (hx2 + 180*scale, hy2 + 30*scale)], fill=GREEN_DARK)
    draw.rectangle([hx2 + 15*scale, hy2 + 30*scale, hx2 + 165*scale, hy2 + 160*scale], fill=WHITE)

    # Right Apartment Buildings
    hx3, hy3 = sw // 2 + 200*scale, sh - 460*scale
    draw.rectangle([hx3, hy3 + 30*scale, hx3 + 200*scale, hy3 + 280*scale], fill=WHITE)
    draw.polygon([(hx3 + 100*scale, hy3 - 40*scale), (hx3 - 15*scale, hy3 + 30*scale), (hx3 + 215*scale, hy3 + 30*scale)], fill=GREEN_DARK)
    for wx in [hx3 + 30*scale, hx3 + 120*scale]:
        for wy in range(hy3 + 60*scale, hy3 + 240*scale, 50*scale):
            draw.rectangle([wx, wy, wx + 50*scale, wy + 35*scale], fill=GREEN_PRIMARY)

    # 6. Foreground Vibrant Trees
    tree_coords = [
        (sw//2 - 480*scale, sh - 280*scale, 90*scale),
        (sw//2 - 220*scale, sh - 260*scale, 75*scale),
        (sw//2 + 140*scale, sh - 250*scale, 80*scale),
        (sw//2 + 420*scale, sh - 290*scale, 95*scale)
    ]
    for tx, ty, tr in tree_coords:
        # Trunk
        draw.rectangle([tx - 12*scale, ty + 10*scale, tx + 12*scale, ty + 80*scale], fill=(80, 110, 85, 255))
        # Foliage
        draw.ellipse([tx - tr, ty - tr, tx + tr, ty + tr*0.4], fill=GREEN_PRIMARY)
        draw.ellipse([tx - tr*0.7, ty - tr*1.2, tx + tr*0.7, ty - tr*0.2], fill=GREEN_MID)

    # 7. White Picket Fence
    fence_y = sh - 200*scale
    for fx in range(sw // 2 - 320*scale, sw // 2 + 320*scale, 30*scale):
        if abs(fx - sw // 2) > 80*scale:
            draw.rectangle([fx, fence_y, fx + 10*scale, fence_y + 70*scale], fill=WHITE)
            draw.polygon([(fx, fence_y), (fx + 5*scale, fence_y - 12*scale), (fx + 10*scale, fence_y)], fill=WHITE)
    draw.rectangle([sw // 2 - 320*scale, fence_y + 20*scale, sw // 2 + 320*scale, fence_y + 32*scale], fill=WHITE)

    img = img.resize((w, h), Image.Resampling.LANCZOS)
    img.save(os.path.join(res_dir, "img_onboarding_1.png"))
    print("Saved slide 1 illustration.")

def draw_connect_slide(w=1000, h=700):
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    draw = ImageDraw.Draw(img)
    draw.ellipse([100, 200, 350, 600], fill=GREEN_LIGHT)
    draw.ellipse([650, 200, 900, 600], fill=GREEN_LIGHT)
    img.save(os.path.join(res_dir, "img_onboarding_2.png"))
    print("Saved slide 2 illustration.")

def draw_stay_updated_slide(w=1000, h=700):
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    draw = ImageDraw.Draw(img)
    draw.ellipse([100, 200, 320, 600], fill=GREEN_MID)
    img.save(os.path.join(res_dir, "img_onboarding_3.png"))
    print("Saved slide 3 illustration.")

draw_logo_pin()
draw_neighborhood_slide()
draw_connect_slide()
draw_stay_updated_slide()
