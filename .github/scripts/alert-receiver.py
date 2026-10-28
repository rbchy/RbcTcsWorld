"""CI only: a tiny webhook that writes every alert it receives to a file (one line each), on 127.0.0.1:8099."""
import http.server
import sys

OUT = sys.argv[1]


class Hook(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", 0))).decode("utf-8", "replace")
        with open(OUT, "a", encoding="utf-8") as f:
            f.write(body.replace("\n", " ") + "\n")
        self.send_response(200)
        self.end_headers()

    def log_message(self, *args):
        pass


http.server.HTTPServer(("127.0.0.1", 8099), Hook).serve_forever()
