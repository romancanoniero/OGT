Synthetic fixtures generated locally for regression tests; no user photographs.

rotated.jpg: deterministic color texture, JPEG quality 95, EXIF orientation 6.
transparent.webp: 320x200 solid RGBA, lossless WebP (44 bytes).
animated.webp: two copies of the transparent frame in a WebP animation container;
verified as two pages using sharp. Tests must preserve the complete sequence.
oversized-pixels.png: solid 6500x6500 PNG, small encoded size but >40 megapixels.

Only test resources. These files are never shipped in the production backend jar.
