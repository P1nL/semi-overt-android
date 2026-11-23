"""Generate sharp profile covers from the existing procedural artwork.
Run: python tools/generate_profile_covers.py (requires Pillow).
"""
from generate_category_covers import OUT, forest, architecture


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, make in [("forest", forest), ("architecture", architecture)]:
        target = OUT / f"profile_cover_{name}.webp"
        make().save(target, "WEBP", quality=94, method=6)
        print(target)
