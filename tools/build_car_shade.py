import cairosvg
from PIL import Image
from collections import deque
import numpy as np
import os

base = os.path.dirname(os.path.abspath(__file__))
svg_path = os.path.join(base, "car_trace_source.svg")
raw_path = os.path.join(base, "_car_shade_raw.png")
out_path = os.path.normpath(os.path.join(base, "..", "app", "res", "drawable-nodpi", "car_shade.png"))

cairosvg.svg2png(url=svg_path, write_to=raw_path, output_width=1100)

img = Image.open(raw_path).convert("RGBA")
arr = np.array(img)
h, w = arr.shape[:2]

def is_bg(px, tol=10):
    r, g, b = int(px[0]), int(px[1]), int(px[2])
    return abs(r-254) < tol and abs(g-254) < tol and abs(b-254) < tol

visited = np.zeros((h, w), dtype=bool)
q = deque()
for x in range(w):
    for y in (0, h-1):
        if is_bg(arr[y, x]) and not visited[y, x]:
            visited[y, x] = True
            q.append((x, y))
for y in range(h):
    for x in (0, w-1):
        if is_bg(arr[y, x]) and not visited[y, x]:
            visited[y, x] = True
            q.append((x, y))

while q:
    x, y = q.popleft()
    for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)):
        nx, ny = x+dx, y+dy
        if 0 <= nx < w and 0 <= ny < h and not visited[ny, nx] and is_bg(arr[ny, nx]):
            visited[ny, nx] = True
            q.append((nx, ny))

arr[visited, 3] = 0
out = Image.fromarray(arr)
os.makedirs(os.path.dirname(out_path), exist_ok=True)
out.save(out_path)
os.remove(raw_path)

print("DONE", out_path, out.size, "transparent_px=", int(visited.sum()), "/", h*w)
