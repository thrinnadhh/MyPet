from pathlib import Path

path = Path("apps/super-admin-web/index.html")
text = path.read_text()
old = '    <script src="app.js"></script>\n'
new = '''    <script src="https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2"></script>\n    <script src="config.js"></script>\n    <script src="app.js"></script>\n    <script src="secure-admin.js"></script>\n'''
if old not in text:
    raise RuntimeError("Expected app.js script tag not found")
path.write_text(text.replace(old, new, 1))
