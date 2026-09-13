from pathlib import Path
import runpy

try:
    runpy.run_path('.github/scripts/apply_live_ore_diagnostics.py', run_name='__main__')
except SystemExit as exc:
    message = str(exc)
    if 'pattern not found in README.md' not in message:
        raise

readme = Path('README.md')
text = readme.read_text()
old = '0.13.1 refines the true subtractive ore renderer: immutable deposits are now meshed as local 12m cache chunks on a dedicated background worker, so a cutter pass rebuilds only nearby ore instead of polygonising the whole deposit on the GL thread.'
new = '0.13.2 keeps the chunked subtractive ore renderer and adds coarse live ore remeshing while digging, plus exportable diagnostics with one-second samples and spike/mesh events.'
if old not in text:
    raise SystemExit('README 0.13.1 summary not found')
readme.write_text(text.replace(old, new, 1))
