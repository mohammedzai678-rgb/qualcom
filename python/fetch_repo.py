import urllib.request

def fetch_and_save(url, filename):
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    try:
        with urllib.request.urlopen(req) as response:
            content = response.read().decode('utf-8')
            with open(filename, 'w', encoding='utf-8') as f:
                f.write(content)
            print(f"Saved {url} to {filename}")
    except Exception as e:
        print(f"Error: {e}")

fetch_and_save("https://raw.githubusercontent.com/TRahulsingh/DeepfakeDetector/main/classify.py", "classify.py")
fetch_and_save("https://raw.githubusercontent.com/TRahulsingh/DeepfakeDetector/main/web-app.py", "web-app.py")
