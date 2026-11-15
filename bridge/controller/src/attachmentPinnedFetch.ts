import { lookup } from "node:dns/promises";
import { request } from "node:https";
import { isIP } from "node:net";
import { Readable } from "node:stream";
import { checkServerIdentity } from "node:tls";

// Conservative IPv4-only policy: deny special-use ranges, including
// carrier NAT, documentation, benchmarking, multicast and reserved space.
export function isPublicAttachmentIPv4(address: string): boolean {
  if (isIP(address) !== 4) return false;
  const [a, b, c] = address.split(".").map(Number);
  return !(a === 0 || a === 10 || a === 127 || a >= 224 ||
    (a === 100 && b >= 64 && b <= 127) ||
    (a === 169 && b === 254) || (a === 172 && b >= 16 && b <= 31) ||
    (a === 192 && (b === 168 || (b === 0 && (c === 0 || c === 2)) ||
      (b === 88 && c === 99))) ||
    (a === 198 && (b === 18 || b === 19 || (b === 51 && c === 100))) ||
    (a === 203 && b === 0 && c === 113));
}

export async function resolveAttachmentIPv4(
  hostname: string,
  resolve: (host: string) => Promise<readonly string[]> = async (host) =>
    (await lookup(host, { family: 4, all: true })).map((entry) => entry.address),
): Promise<string> {
  const addresses = await resolve(hostname);
  if (!addresses.length || addresses.some((address) => !isPublicAttachmentIPv4(address))) {
    throw new Error("ATTACHMENT_SOURCE_NOT_ALLOWED");
  }
  return addresses[0]!;
}

// Connect to the exact validated address, not a second DNS lookup.
// Preserve the original hostname for TLS SNI, certificate validation and Host.
export async function fetchPinnedAttachment(input: string | URL, init?: RequestInit): Promise<Response> {
  const url = new URL(input);
  const signal = init?.signal ?? AbortSignal.timeout(30_000);
  const address = await new Promise<string>((resolve, reject) => {
    const abort = () => reject(new Error("ATTACHMENT_DOWNLOAD_FAILED"));
    if (signal.aborted) { abort(); return; }
    signal.addEventListener("abort", abort, { once: true });
    resolveAttachmentIPv4(url.hostname).then(resolve, reject).finally(() => {
      signal.removeEventListener("abort", abort);
    });
  });
  return new Promise<Response>((resolve, reject) => {
    const req = request({
      protocol: "https:", hostname: address, port: 443,
      path: url.pathname + url.search, method: "GET", agent: false,
      servername: url.hostname, rejectUnauthorized: true,
      checkServerIdentity: (_host, cert) => checkServerIdentity(url.hostname, cert),
      signal, headers: { host: url.host, accept: "image/*,audio/*,video/*,application/octet-stream",
        "accept-encoding": "identity" },
    }, (res) => {
      const status = res.statusCode ?? 502;
      if (status < 200 || status > 599 || (status >= 300 && status < 400) ||
          (res.headers["content-encoding"] && res.headers["content-encoding"] !== "identity")) {
        res.destroy();
        reject(new Error("ATTACHMENT_DOWNLOAD_FAILED"));
        return;
      }
      const headers = new Headers();
      for (const [key, value] of Object.entries(res.headers)) {
        if (Array.isArray(value)) for (const item of value) headers.append(key, item);
        else if (value !== undefined) headers.set(key, value);
      }
      if (status === 204 || status === 205) {
        res.resume();
        resolve(new Response(null, { status, headers }));
      } else {
        resolve(new Response(Readable.toWeb(res) as ReadableStream<Uint8Array>, { status, headers }));
      }
    });
    req.on("error", reject);
    req.end();
  });
}
