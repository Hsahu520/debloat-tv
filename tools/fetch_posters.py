"""Download a poster for every title in the launcher's catalog (no API keys needed) into
launcher/app/src/main/assets/posters/<slug>.jpg, resized to 320x480.

Sources, in order: TVmaze (series), iTunes (films), Wikipedia's infobox image. Posters are copyrighted, so they are
fetched on your own computer and never committed (.gitignore). Titles without a poster get a lettered card in the
launcher instead. Rerun after editing catalog.json; posters you already have are kept.

Usage: python3 tools/fetch_posters.py      (needs Python 3; resizing uses macOS `sips` or, elsewhere, Pillow)
"""
import json
import os
import re
import shutil
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'launcher/app/src/main/assets')
OUT = os.path.join(ASSETS, 'posters')
UA = {'User-Agent': 'tvhome-posters/1.0 (personal TV launcher)'}


def slug(t):
    import unicodedata
    t = unicodedata.normalize('NFD', t)
    t = ''.join(c for c in t if unicodedata.category(c) != 'Mn')
    return re.sub(r'[^a-z0-9]+', '-', t.lower()).strip('-')


def norm(s):
    return re.sub(r'[^a-z0-9]', '', s.lower())


def get(url, tries=5):
    for i in range(tries):
        try:
            return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30).read()
        except urllib.error.HTTPError as e:
            if e.code != 429:
                raise
            time.sleep(10 * (i + 1))   # rate limited: back off
    raise RuntimeError('rate limited: ' + url)


def tvmaze(title, year):
    try:
        for r in json.loads(get('https://api.tvmaze.com/search/shows?q=' + urllib.parse.quote(title))):
            s = r['show']
            img = (s.get('image') or {}).get('original')
            prem = int((s.get('premiered') or '0')[:4] or 0)
            if img and norm(s['name']).startswith(norm(title)[:12]) and abs(prem - year) <= 1:
                return img
    except Exception:
        pass
    return None


def itunes(title, year):
    for country in ('in', 'us'):
        try:
            res = json.loads(get('https://itunes.apple.com/search?media=movie&limit=10&country=%s&term=%s'
                                 % (country, urllib.parse.quote(title))))['results']
        except Exception:
            continue
        for r in res:
            if norm(r.get('trackName', '')).startswith(norm(title)[:10]) and abs(int(r.get('releaseDate', '0')[:4]) - year) <= 1:
                return r['artworkUrl100'].replace('100x100bb', '600x900bb')
    return None


def wikipedia(page):
    if not page:
        return None
    try:
        q = json.loads(get('https://en.wikipedia.org/w/api.php?action=query&format=json&redirects=1&prop=pageimages'
                           '&piprop=original&pilicense=any&titles=' + urllib.parse.quote(page)))['query']['pages']
        return next(iter(q.values())).get('original', {}).get('source')
    except Exception:
        return None


def resize(src, dst):
    if shutil.which('sips'):
        subprocess.run(['sips', '-s', 'format', 'jpeg', '-s', 'formatOptions', '78', '-z', '480', '320', src, '--out', dst],
                       check=True, capture_output=True)
    else:
        from PIL import Image   # pip install pillow
        Image.open(src).convert('RGB').resize((320, 480)).save(dst, 'JPEG', quality=78)


def main():
    os.makedirs(OUT, exist_ok=True)
    catalog = json.load(open(os.path.join(ASSETS, 'catalog.json'), encoding='utf-8'))
    missing = []
    for it in catalog:
        dst = os.path.join(OUT, slug(it['t']) + '.jpg')
        if os.path.exists(dst):
            continue
        url = tvmaze(it['t'], it['y']) or itunes(it['t'], it['y']) or wikipedia(it.get('w'))
        time.sleep(1)
        if not url:
            missing.append(it['t'])
            continue
        raw = dst + '.src'
        try:
            open(raw, 'wb').write(get(url))
            resize(raw, dst)
            print('ok  ', it['t'])
        except Exception as e:
            missing.append(it['t'])
            print('fail', it['t'], e)
        finally:
            if os.path.exists(raw):
                os.remove(raw)
        time.sleep(2)
    have = len([f for f in os.listdir(OUT) if f.endswith('.jpg')])
    print('\n%d of %d titles have a poster.' % (have, len(catalog)))
    if missing:
        print('No poster found (they get a lettered card):', ', '.join(missing))


if __name__ == '__main__':
    main()
