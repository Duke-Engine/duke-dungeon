"""Draws src/main/resources/effects/follow_diamond.png: a white diamond on a soft glow, tinted by its layer's Colour.

    py art/effects/follow_diamond.py
"""
from PIL import Image, ImageChops, ImageDraw, ImageFilter

S = 128
glow = Image.new("L", (S, S), 0)
ImageDraw.Draw(glow).polygon([(64, 6), (108, 64), (64, 122), (20, 64)], fill=110)
glow = glow.filter(ImageFilter.GaussianBlur(8))
core = Image.new("L", (S, S), 0)
ImageDraw.Draw(core).polygon([(64, 18), (98, 64), (64, 110), (30, 64)], fill=255)
core = core.filter(ImageFilter.GaussianBlur(1.2))
white = Image.new("L", (S, S), 255)
Image.merge("RGBA", [white, white, white, ImageChops.lighter(glow, core)]).save(
    "src/main/resources/effects/follow_diamond.png")
