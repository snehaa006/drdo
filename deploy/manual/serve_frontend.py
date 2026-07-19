#!/usr/bin/env python3
"""
Offline frontend server for the NO-DOCKER (manual) setup.

Serves the pre-built Angular app in ./frontend-dist/ and reverse-proxies every
/api/ request to the backend (java -jar) on localhost:8080. This lets the
browser talk to one origin (http://localhost:4200), exactly like the nginx
container does in the Docker setup — so no CORS or code changes are needed.

Requires only the Python 3 standard library. Cross-platform (Windows/Linux/mac).

Usage:
    python3 serve_frontend.py                 # serves ./frontend-dist on :4200
    python3 serve_frontend.py 8090 8080 ./dist  # port, backend-port, dist-dir
"""
import sys, os, http.server, socketserver, urllib.request, urllib.error

PORT       = int(sys.argv[1]) if len(sys.argv) > 1 else 4200
BACKEND    = int(sys.argv[2]) if len(sys.argv) > 2 else 8080
DIST_DIR   = sys.argv[3] if len(sys.argv) > 3 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "frontend-dist")
BACKEND_URL = f"http://localhost:{BACKEND}"

if not os.path.isdir(DIST_DIR):
    print(f"ERROR: frontend dist folder not found: {DIST_DIR}")
    sys.exit(1)


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **k):
        super().__init__(*a, directory=DIST_DIR, **k)

    # ---- API reverse proxy ---------------------------------------------------
    def _proxy(self):
        length = int(self.headers.get("Content-Length", 0) or 0)
        body = self.rfile.read(length) if length else None
        req = urllib.request.Request(BACKEND_URL + self.path, data=body, method=self.command)
        for h in ("Content-Type", "Accept", "Range", "Authorization"):
            if h in self.headers:
                req.add_header(h, self.headers[h])
        try:
            with urllib.request.urlopen(req) as resp:
                self.send_response(resp.status)
                for k, v in resp.getheaders():
                    if k.lower() not in ("transfer-encoding", "connection"):
                        self.send_header(k, v)
                self.end_headers()
                self.wfile.write(resp.read())
        except urllib.error.HTTPError as e:
            self.send_response(e.code)
            for k, v in e.headers.items():
                if k.lower() not in ("transfer-encoding", "connection"):
                    self.send_header(k, v)
            self.end_headers()
            self.wfile.write(e.read())
        except Exception as e:
            self.send_error(502, f"Backend unreachable: {e}")

    def _is_api(self):
        return self.path.startswith("/api/")

    def do_GET(self):
        if self._is_api():
            return self._proxy()
        # SPA fallback: serve index.html for client-side routes (not real files)
        path = self.translate_path(self.path)
        if not os.path.exists(path) and "." not in os.path.basename(self.path):
            self.path = "/index.html"
        return super().do_GET()

    def do_POST(self):   self._proxy() if self._is_api() else self.send_error(404)
    def do_PUT(self):    self._proxy() if self._is_api() else self.send_error(404)
    def do_PATCH(self):  self._proxy() if self._is_api() else self.send_error(404)
    def do_DELETE(self): self._proxy() if self._is_api() else self.send_error(404)
    def do_OPTIONS(self):
        if self._is_api():
            return self._proxy()
        self.send_response(204); self.end_headers()

    def log_message(self, *a):  # keep the console quiet
        pass


class ThreadingServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True


if __name__ == "__main__":
    print(f"DRDO GIS frontend  →  http://localhost:{PORT}")
    print(f"  serving : {DIST_DIR}")
    print(f"  API proxy → {BACKEND_URL}/api/  (backend must be running)")
    print("Press Ctrl+C to stop.")
    ThreadingServer(("0.0.0.0", PORT), Handler).serve_forever()
