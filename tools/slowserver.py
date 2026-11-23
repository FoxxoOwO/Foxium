"""Serves throttled test files so download progress and notifications can be observed.

Run: python tools/slowserver.py
  http://localhost:8100/small.bin  2 MiB, immediate
  http://localhost:8100/big.bin    40 MiB, ~2 MiB/s
  http://localhost:8100/pic.png     1x1 PNG with an image/png type
"""
import base64
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

BIG_SIZE = 40 * 1024 * 1024
SMALL_SIZE = 2 * 1024 * 1024
CHUNK = 1024 * 1024
PAUSE = 0.5
PIC = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg=="
)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _send_pic(self):
        self.send_response(200)
        self.send_header("Content-Type", "image/png")
        self.send_header("Content-Length", str(len(PIC)))
        self.send_header("Content-Disposition", 'attachment; filename="pic.png"')
        self.end_headers()
        self.wfile.write(PIC)

    def _send_file(self, name, size, pause):
        self.send_response(200)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Content-Length", str(size))
        self.send_header("Content-Disposition", 'attachment; filename="%s"' % name)
        self.end_headers()
        block = bytes(CHUNK)
        sent = 0
        while sent < size:
            chunk = min(CHUNK, size - sent)
            try:
                self.wfile.write(block[:chunk])
                self.wfile.flush()
            except (BrokenPipeError, ConnectionResetError):
                return
            sent += chunk
            if pause:
                time.sleep(PAUSE)

    def do_GET(self):
        if self.path.startswith("/small.bin"):
            self._send_file("small.bin", SMALL_SIZE, False)
        elif self.path.startswith("/big.bin"):
            self._send_file("big.bin", BIG_SIZE, True)
        elif self.path.startswith("/pic.png"):
            self._send_pic()
        else:
            self.send_error(404)

    def log_message(self, fmt, *args):
        pass


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 8100), Handler).serve_forever()
