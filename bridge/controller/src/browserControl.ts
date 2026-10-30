import { createHmac, randomBytes, timingSafeEqual } from "node:crypto";

const AUTH_REQUEST_TTL_MS = 5 * 60 * 1_000;
const SESSION_TTL_MS = 30 * 24 * 60 * 60 * 1_000;
const TOKEN_VERSION = 1;
const SESSION_DOMAIN = "LOCALCUT_BROWSER_SESSION_V1";
const BINDING_DOMAIN = "LOCALCUT_BROWSER_BINDING_V1";

export type BrowserControlSessionClaims = {
  deviceId: string;
  csrfToken: string;
  issuedAtEpochMs: number;
  expiresAtEpochMs: number;
  sessionId: string;
};

type PendingAuthorization = {
  bindingDigest: string;
  expiresAtEpochMs: number;
  approvedDeviceId: string | null;
};

export type BrowserControlSessionServiceOptions = {
  signingSecret: string;
  nowEpochMs?: () => number;
  randomToken?: () => string;
  isDeviceAuthorized(deviceId: string): boolean;
};

export class BrowserControlSessionService {
  private readonly signingSecret: string;
  private readonly nowEpochMs: () => number;
  private readonly randomToken: () => string;
  private readonly isDeviceAuthorized: (deviceId: string) => boolean;
  private readonly pending = new Map<string, PendingAuthorization>();

  constructor(options: BrowserControlSessionServiceOptions) {
    if (!options.signingSecret) throw new Error("REMOTE_SIGNING_SECRET_REQUIRED");
    this.signingSecret = options.signingSecret;
    this.nowEpochMs = options.nowEpochMs ?? Date.now;
    this.randomToken = options.randomToken ?? (() => randomBytes(24).toString("base64url"));
    this.isDeviceAuthorized = options.isDeviceAuthorized;
  }

  beginAuthorization(browserBinding: string): { requestId: string; expiresAtEpochMs: number } {
    if (!browserBinding || browserBinding.length > 512) throw new Error("REMOTE_BROWSER_BINDING_INVALID");
    this.prunePending();
    const requestId = this.randomToken();
    const expiresAtEpochMs = this.nowEpochMs() + AUTH_REQUEST_TTL_MS;
    this.pending.set(requestId, {
      bindingDigest: this.bindingDigest(browserBinding),
      expiresAtEpochMs,
      approvedDeviceId: null,
    });
    return { requestId, expiresAtEpochMs };
  }

  approveAuthorization(requestId: string, deviceId: string): void {
    const pending = this.getPending(requestId);
    if (!this.isDeviceAuthorized(deviceId)) throw new Error("REMOTE_DEVICE_UNAUTHORIZED");
    pending.approvedDeviceId = deviceId;
  }

  completeAuthorization(
    requestId: string,
    browserBinding: string,
  ): BrowserControlSessionClaims & { sessionToken: string } {
    const pending = this.getPending(requestId);
    if (!pending.approvedDeviceId) throw new Error("REMOTE_AUTH_NOT_APPROVED");
    if (!this.safeEqual(pending.bindingDigest, this.bindingDigest(browserBinding))) {
      throw new Error("REMOTE_BROWSER_BINDING_INVALID");
    }
    if (!this.isDeviceAuthorized(pending.approvedDeviceId)) throw new Error("REMOTE_DEVICE_UNAUTHORIZED");

    const now = this.nowEpochMs();
    const claims: BrowserControlSessionClaims = {
      deviceId: pending.approvedDeviceId,
      csrfToken: this.randomToken(),
      issuedAtEpochMs: now,
      expiresAtEpochMs: now + SESSION_TTL_MS,
      sessionId: this.randomToken(),
    };
    const sessionToken = this.signClaims(claims);
    this.pending.delete(requestId);
    return { ...claims, sessionToken };
  }

  verifySession(sessionToken: string): BrowserControlSessionClaims {
    const parts = sessionToken.split(".");
    if (parts.length !== 2 || !parts[0] || !parts[1]) throw new Error("REMOTE_SESSION_INVALID");
    const [payloadEncoded, signature] = parts;
    const expected = this.sign(payloadEncoded);
    if (!this.safeEqual(signature, expected)) throw new Error("REMOTE_SESSION_INVALID");

    let value: unknown;
    try {
      value = JSON.parse(Buffer.from(payloadEncoded, "base64url").toString("utf8"));
    } catch {
      throw new Error("REMOTE_SESSION_INVALID");
    }
    if (!isRecord(value) || value.v !== TOKEN_VERSION || !isRecord(value.claims)) {
      throw new Error("REMOTE_SESSION_INVALID");
    }
    const claims = parseClaims(value.claims);
    if (claims.expiresAtEpochMs <= this.nowEpochMs()) throw new Error("REMOTE_SESSION_EXPIRED");
    if (!this.isDeviceAuthorized(claims.deviceId)) throw new Error("REMOTE_DEVICE_UNAUTHORIZED");
    return claims;
  }

  assertCsrf(sessionToken: string, providedToken: string): BrowserControlSessionClaims {
    const claims = this.verifySession(sessionToken);
    if (!providedToken || !this.safeEqual(providedToken, claims.csrfToken)) {
      throw new Error("REMOTE_CSRF_INVALID");
    }
    return claims;
  }

  private getPending(requestId: string): PendingAuthorization {
    const pending = this.pending.get(requestId);
    if (!pending) throw new Error("REMOTE_AUTH_REQUEST_INVALID");
    if (pending.expiresAtEpochMs <= this.nowEpochMs()) {
      this.pending.delete(requestId);
      throw new Error("REMOTE_AUTH_REQUEST_EXPIRED");
    }
    return pending;
  }

  private prunePending(): void {
    const now = this.nowEpochMs();
    for (const [requestId, pending] of this.pending) {
      if (pending.expiresAtEpochMs <= now) this.pending.delete(requestId);
    }
  }

  private bindingDigest(browserBinding: string): string {
    return createHmac("sha256", this.signingSecret)
      .update(BINDING_DOMAIN)
      .update("\n")
      .update(browserBinding)
      .digest("base64url");
  }

  private signClaims(claims: BrowserControlSessionClaims): string {
    const payload = Buffer.from(JSON.stringify({ v: TOKEN_VERSION, claims }), "utf8").toString("base64url");
    return `${payload}.${this.sign(payload)}`;
  }

  private sign(payload: string): string {
    return createHmac("sha256", this.signingSecret)
      .update(SESSION_DOMAIN)
      .update("\n")
      .update(payload)
      .digest("base64url");
  }

  private safeEqual(a: string, b: string): boolean {
    const left = Buffer.from(a);
    const right = Buffer.from(b);
    return left.length === right.length && timingSafeEqual(left, right);
  }
}

function parseClaims(value: Record<string, unknown>): BrowserControlSessionClaims {
  const deviceId = value.deviceId;
  const csrfToken = value.csrfToken;
  const issuedAtEpochMs = value.issuedAtEpochMs;
  const expiresAtEpochMs = value.expiresAtEpochMs;
  const sessionId = value.sessionId;
  if (
    typeof deviceId !== "string" || deviceId.length === 0 || deviceId.length > 128 ||
    typeof csrfToken !== "string" || csrfToken.length < 20 || csrfToken.length > 512 ||
    typeof sessionId !== "string" || sessionId.length < 20 || sessionId.length > 512 ||
    typeof issuedAtEpochMs !== "number" || !Number.isSafeInteger(issuedAtEpochMs) ||
    typeof expiresAtEpochMs !== "number" || !Number.isSafeInteger(expiresAtEpochMs) ||
    expiresAtEpochMs <= issuedAtEpochMs
  ) {
    throw new Error("REMOTE_SESSION_INVALID");
  }
  return { deviceId, csrfToken, issuedAtEpochMs, expiresAtEpochMs, sessionId };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === "object" && !Array.isArray(value);
}
