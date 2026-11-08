from PIL import Image

p = 'src/assets/steering_wheel_guide.webp'
im = Image.open(p)
print('mode:', im.mode, 'size:', im.size)
rgba = im.convert('RGBA')
w, h = rgba.size

alpha = rgba.getchannel(3)
print('alpha bbox (l,t,r,b):', alpha.getbbox())
hist = alpha.histogram()
print('alpha: transparent(<16)=%.3f  opaque(>240)=%.3f' % (sum(hist[:16]) / (w * h), sum(hist[240:]) / (w * h)))

# content profile on RGB assuming near-white bg
rgb = rgba.convert('RGB')
gray = rgb.convert('L')
mask = gray.point(lambda v: 255 if v < 240 else 0)
bbox = mask.getbbox()
print('rgb gray<240 bbox:', bbox)

# 10-band row/col density of non-white pixels
px = mask.load()
rows = [0] * h
cols = [0] * w
for y in range(0, h, 4):
    for x in range(0, w, 4):
        if px[x, y]:
            rows[y] += 1
            cols[x] += 1
print('row bands (every 10% of height, count/1000 samples):')
for i in range(10):
    seg = rows[i * h // 10:(i + 1) * h // 10]
    print('  %2d%%-%2d%%: %d' % (i * 10, (i + 1) * 10, sum(seg)))
print('col bands:')
for i in range(10):
    seg = cols[i * w // 10:(i + 1) * w // 10]
    print('  %2d%%-%2d%%: %d' % (i * 10, (i + 1) * 10, sum(seg)))
