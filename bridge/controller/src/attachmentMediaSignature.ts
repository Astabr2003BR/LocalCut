// Header recognition only: full decoding and codec validation belong to Android.
export function assertAttachmentMediaSignature(mime: string, bytes: Buffer): void {
  const at = (offset: number, value: string) => bytes.subarray(offset, offset + value.length).equals(Buffer.from(value, "latin1"));
  const iso = () => bytes.length >= 16 && at(4, "ftyp") && bytes.readUInt32BE(0) >= 16 && bytes.readUInt32BE(0) <= bytes.length;
  const riff = (kind: string) => bytes.length >= 12 && at(0, "RIFF") && at(8, kind) && bytes.readUInt32LE(4) + 8 === bytes.length;
  const mp3Frame = () => bytes.length >= 4 && bytes[0] === 0xff && (bytes[1] & 0xe0) === 0xe0 && (bytes[1] & 0x18) !== 0x08 && (bytes[1] & 0x06) !== 0 && (bytes[2] & 0xf0) !== 0 && (bytes[2] & 0xf0) !== 0xf0 && (bytes[2] & 0x0c) !== 0x0c;
  let valid: boolean;
  switch (mime) {
    case "image/png": valid = bytes.length >= 33 && at(0, "\x89PNG\r\n\x1a\n") && bytes.readUInt32BE(8) === 13 && at(12, "IHDR") && bytes.readUInt32BE(16) > 0 && bytes.readUInt32BE(20) > 0; break;
    case "image/jpeg": valid = bytes.length >= 4 && at(0, "\xff\xd8\xff") && at(bytes.length - 2, "\xff\xd9"); break;
    case "image/webp": valid = riff("WEBP") && bytes.length >= 20 && (at(12, "VP8 ") || at(12, "VP8L") || at(12, "VP8X")); break;
    case "image/gif": valid = bytes.length >= 14 && (at(0, "GIF87a") || at(0, "GIF89a")); break;
    case "audio/wav": case "audio/x-wav": case "audio/wave": valid = riff("WAVE"); break;
    case "audio/mpeg": case "audio/mp3": valid = mp3Frame() || (bytes.length >= 10 && at(0, "ID3") && bytes[3] >= 2 && bytes[3] <= 4 && bytes.subarray(6, 10).every((n) => n < 128)); break;
    case "audio/mp4": case "audio/x-m4a": case "video/mp4": case "video/quicktime": valid = iso(); break;
    case "audio/flac": case "audio/x-flac": valid = bytes.length >= 8 && at(0, "fLaC"); break;
    case "audio/ogg": case "video/ogg": valid = bytes.length >= 27 && at(0, "OggS") && bytes[4] === 0; break;
    case "audio/webm": case "video/webm": valid = bytes.length >= 8 && at(0, "\x1a\x45\xdf\xa3"); break;
    case "audio/aac": case "audio/aacp": valid = bytes.length >= 7 && bytes[0] === 0xff && (bytes[1] & 0xf6) === 0xf0; break;
    default: throw new Error("ATTACHMENT_MEDIA_SIGNATURE_UNSUPPORTED");
  }
  if (!valid) throw new Error("ATTACHMENT_MEDIA_SIGNATURE_MISMATCH");
}
