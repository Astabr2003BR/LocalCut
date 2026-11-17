export const PORTABLE_PACKAGE_MIME = "application/vnd.localcut.package";

// Structural ZIP preflight only; Android still validates the manifest, CRCs,
// each asset SHA-256, recipe and media before extraction. Never decompress here.
export function assertPortablePackageDirectory(bytes: Buffer): void {
  const fail = () => { throw new Error("PACKAGE_ZIP_INVALID"); };
  if (bytes.length < 22 || bytes.length > 20 * 1024 * 1024) fail();
  const end = bytes.length - 22;
  const u16 = (n: number) => bytes.readUInt16LE(n);
  const u32 = (n: number) => bytes.readUInt32LE(n);
  if (u32(end) !== 0x06054b50 || u16(end + 4) !== 0 || u16(end + 6) !== 0 || u16(end + 20) !== 0) fail();
  const count = u16(end + 10), size = u32(end + 12), offset = u32(end + 16);
  if (count < 2 || count > 129 || u16(end + 8) !== count || size < 1 || size > 1_048_576 || offset < 1 || offset + size !== end) fail();
  const names = new Set<string>();
  let cursor = offset, total = 0;
  for (let i = 0; i < count; i++) {
    if (cursor + 46 > end || u32(cursor) !== 0x02014b50) fail();
    const flags = u16(cursor + 8), method = u16(cursor + 10);
    const compressed = u32(cursor + 20), expanded = u32(cursor + 24);
    const nameLength = u16(cursor + 28), extra = u16(cursor + 30), comment = u16(cursor + 32);
    const local = u32(cursor + 42), next = cursor + 46 + nameLength + extra + comment;
    if (next > end || nameLength < 1 || nameLength > 70 || u16(cursor + 34) !== 0 ||
        (flags & 1) !== 0 || (method !== 0 && method !== 8) || expanded < 1 ||
        compressed < 1 || compressed > offset || local + 30 > offset || u32(local) !== 0x04034b50) fail();
    const rawName = bytes.subarray(cursor + 46, cursor + 46 + nameLength);
    if (rawName.some(v => v > 127)) fail();
    const name = rawName.toString("ascii");
    if (names.has(name) || !(name === "manifest.json" || /^media\/[a-z0-9][a-z0-9_-]{0,63}$/.test(name))) fail();
    names.add(name);
    if (name === "manifest.json" ? expanded > 65_536 : expanded > 128 * 1024 * 1024) fail();
    if (name !== "manifest.json") total += expanded;
    if (total > 256 * 1024 * 1024) fail();
    const localNameLength = u16(local + 26), localExtra = u16(local + 28);
    const dataStart = local + 30 + localNameLength + localExtra;
    if (dataStart + compressed > offset || localNameLength !== nameLength ||
        !bytes.subarray(local + 30, local + 30 + localNameLength).equals(rawName) ||
        u16(local + 8) !== method || u16(local + 6) !== flags) fail();
    cursor = next;
  }
  if (cursor !== end || !names.has("manifest.json")) fail();
}
