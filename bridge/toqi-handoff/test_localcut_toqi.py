import subprocess
import sys
import unittest
from urllib.parse import parse_qs, urlsplit

from localcut_toqi import BRIDGE, MAX_URL_BYTES, criar_link, validar_url


class LocalCutTests(unittest.TestCase):
    def test_handoff_e_codificacao(self):
        url = "https://cdn.example.com/video.mp4?x=1&y=dois%20mais#trecho"
        link = criar_link(url)
        parts = urlsplit(link)
        self.assertEqual(parts.scheme + "://" + parts.netloc + parts.path, BRIDGE)
        self.assertEqual(parse_qs(parts.query), {
            "action": ["import_url"], "url": [url], "returnTo": ["editor"]
        })
        self.assertFalse(parts.fragment)

    def test_caracteres_unicode_no_caminho(self):
        url = "https://example.com/vídeo?nome=olá"
        self.assertEqual(parse_qs(urlsplit(criar_link(url)).query)["url"], [url])

    def test_hosts_sintaticamente_validos(self):
        for url in ("https://example.com/x", "https://cdn.example.com:8443/x",
                    "https://8.8.8.8/x", "https://[2606:4700:4700::1111]/x"):
            with self.subTest(url=url):
                self.assertEqual(validar_url(url), url)

    def test_rejeicoes(self):
        invalidas = [
            "", "http://example.com", "file:///tmp/a", "//example.com/x", "https:///x",
            "https://user:pass@example.com/x", "https://example.com@127.0.0.1/x",
            "https://example.com\\@127.0.0.1/x", "https://localhost/x", "https://foo.localhost/x",
            "https://router.local/x", "https://host.internal/x", "https://intranet/x",
            "https://127.0.0.1/x", "https://10.0.0.1/x", "https://172.16.1.1/x",
            "https://192.168.1.1/x", "https://169.254.169.254/x", "https://0.0.0.0/x",
            "https://[::1]/x", "https://[fe80::1]/x", "https://[::ffff:8.8.8.8]/x",
            "https://[fe80::1%25eth0]/x", "https://127.1/x", "https://2130706433/x",
            "https://0x7f000001/x", "https://0x7f.0.0.1/x", "https://0177.0.0.1/x",
            "https://example.com:/x", "https://example.com:99999/x", "https://example.com:0/x",
            "https://example.com:abc/x", "https://example.com./x", "https://-bad.example/x",
            "https://example..com/x", "https://[v1.test]/x", "https://exämple.com/x",
            "https://example.com/a b", "https://example.com/a\nb", "https://example.com/%zz",
        ]
        for url in invalidas:
            with self.subTest(url=url), self.assertRaises(ValueError):
                criar_link(url)

    def test_limite_bytes(self):
        prefix = "https://example.com/"
        url = prefix + "a" * (MAX_URL_BYTES - len(prefix))
        self.assertEqual(validar_url(url), url)
        with self.assertRaises(ValueError):
            validar_url(url + "a")
        with self.assertRaises(ValueError):
            validar_url(prefix + "é" * MAX_URL_BYTES)

    def test_injecao_query(self):
        url = "https://example.com/a?returnTo=evil&action=evil&url=x"
        query = parse_qs(urlsplit(criar_link(url)).query)
        self.assertEqual(query["action"], ["import_url"])
        self.assertEqual(query["returnTo"], ["editor"])
        self.assertEqual(query["url"], [url])

    def test_cli_sem_rede(self):
        run = subprocess.run([sys.executable, "localcut_toqi.py", "https://example.com/video.mp4"],
                             capture_output=True, text=True, check=False)
        self.assertEqual(run.returncode, 0)
        self.assertEqual(run.stdout.strip(), criar_link("https://example.com/video.mp4"))
        bad = subprocess.run([sys.executable, "localcut_toqi.py", "http://localhost/"],
                             capture_output=True, text=True, check=False)
        self.assertEqual(bad.returncode, 2)
        self.assertFalse(bad.stdout)
        self.assertIn("Erro:", bad.stderr)


if __name__ == "__main__":
    unittest.main()
