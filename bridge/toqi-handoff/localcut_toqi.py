"""Gerador local de links de importação para o LocalCut, sem chamadas de rede."""
from __future__ import annotations

import argparse
import ipaddress
import re
import sys
from urllib.parse import urlencode, urlsplit

BRIDGE = "https://localcut-bridge-v1.onrender.com/open"
MAX_URL_BYTES = 2048
MAX_LINK_BYTES = 8192
_LABEL = re.compile(r"[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\Z")
_BAD_PERCENT = re.compile(r"%(?![0-9a-fA-F]{2})")
_NONPUBLIC_SUFFIXES = {"localhost", "local", "internal", "lan", "home", "invalid", "test"}


def validar_url(url: str) -> str:
    """Verifica sintaxe conservadora; não verifica DNS nem acessibilidade."""
    if not isinstance(url, str) or not url:
        raise ValueError("Informe uma URL HTTPS não vazia.")
    try:
        length = len(url.encode("utf-8"))
    except UnicodeEncodeError as exc:
        raise ValueError("A URL contém caracteres inválidos.") from exc
    if length > MAX_URL_BYTES:
        raise ValueError(f"A URL excede o limite de {MAX_URL_BYTES} bytes UTF-8.")
    if any(c.isspace() or ord(c) < 32 or ord(c) == 127 for c in url) or "\\" in url:
        raise ValueError("A URL não pode conter espaços, controles ou barras invertidas.")
    if _BAD_PERCENT.search(url):
        raise ValueError("A URL contém codificação percentual inválida.")
    try:
        parts = urlsplit(url)
        host = parts.hostname
        port = parts.port
    except ValueError as exc:
        raise ValueError("A URL ou porta é inválida.") from exc
    if parts.scheme.lower() != "https" or not parts.netloc or not host:
        raise ValueError("Use uma URL HTTPS absoluta com domínio ou IP público.")
    if parts.username is not None or parts.password is not None or "@" in parts.netloc:
        raise ValueError("URLs com credenciais não são aceitas.")
    if port is not None and not (1 <= port <= 65535):
        raise ValueError("Porta inválida.")
    if parts.netloc.endswith(":"):
        raise ValueError("Porta vazia não é aceita.")
    if not host.isascii() or "%" in host:
        raise ValueError("Use um domínio ASCII válido, sem escape ou zona IPv6.")
    try:
        address = ipaddress.ip_address(host)
    except ValueError:
        if ":" in host or "[" in parts.netloc or "]" in parts.netloc or host.endswith(".") or len(host) > 253:
            raise ValueError("Domínio inválido.")
        labels = host.lower().split(".")
        if len(labels) < 2 or any(not _LABEL.fullmatch(label) for label in labels):
            raise ValueError("Use um domínio completo válido.")
        if labels[-1] in _NONPUBLIC_SUFFIXES:
            raise ValueError("Domínios locais ou reservados não são aceitos.")
        # Evita formatos IP numéricos alternativos interpretados por alguns clientes.
        if not re.fullmatch(r"[a-z][a-z0-9-]*", labels[-1]):
            raise ValueError("Final de domínio inválido ou IP numérico ambíguo.")
        if all(re.fullmatch(r"(?:[0-9]+|0x[0-9a-f]+)", label) for label in labels):
            raise ValueError("Formato IP numérico ambíguo não é aceito.")
    else:
        if not address.is_global or (isinstance(address, ipaddress.IPv6Address) and address.ipv4_mapped is not None):
            raise ValueError("O IP precisa ser globalmente roteável e não mapeado.")
    return url


def criar_link(url: str) -> str:
    """Codifica a URL como um único parâmetro e solicita retorno ao editor."""
    validar_url(url)
    link = BRIDGE + "?" + urlencode({"action": "import_url", "url": url, "returnTo": "editor"})
    if len(link.encode("utf-8")) > MAX_LINK_BYTES:
        raise ValueError(f"O link gerado excede o limite de {MAX_LINK_BYTES} bytes.")
    return link


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Gera um link de importação LocalCut sem acessar a rede.")
    parser.add_argument("url", help="URL HTTPS pública do arquivo acessível pelo LocalCut")
    args = parser.parse_args(argv)
    try:
        print(criar_link(args.url))
    except ValueError as exc:
        print(f"Erro: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
