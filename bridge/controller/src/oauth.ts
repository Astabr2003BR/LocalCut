import { createHash, createHmac, randomBytes, timingSafeEqual } from "node:crypto";

const DEFAULT_PENDING_TTL_MS = 5 * 60 * 1_000;
const DEFAULT_CODE_TTL_MS = 2 * 60 * 1_000;
const DEFAULT_ACCESS_TTL_MS = 60 * 60 * 1_000;
const DEFAULT_REFRESH_TTL_MS = 30 * 24 * 60 * 60 * 1_000;
const SUPPORTED_SCOPES = new Set(["localcut:read", "localcut:write"]);
const CLIENT_ID = "localcut-chatgpt";
const REDIRECT_URI = "https://chatgpt.com/connector_platform_oauth_redirect";

type PendingAuthorization = {
  requestId: string;
  clientId: string;
  redirectUri: string;
  state: string;
  codeChallenge: string;
  resource: string;
  scopes: string[];
  expiresAtEpochMs: number;
  approvedDeviceId: string | null;
};

type AuthorizationCodeRecord = {
  code: string;
  clientId: string;
  redirectUri: string;
  codeChallenge: string;
  resource: string;
  scopes: string[];
  deviceId: string;
  expiresAtEpochMs: number;
};

type TokenClaims = {
  v: 1;
  typ: "access" | "refresh";
  sub: string;
  aud: string;
  clientId: string;
  scopes: string[];
  iat: number;
  exp: number;
  jti: string;
};

export type OAuthBeginAuthorizationInput = {
  responseType: string;
  clientId: string;
  redirectUri: string;
  state: string;
  codeChallenge: string;
  codeChallengeMethod: string;
  resource: string;
  scope: string;
};

export type OAuthAuthorizationCompletion = {
  code: string;
  state: string;
  redirectUri: string;
};

export type OAuthTokenResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  scope: string;
};

export type OAuthAccessClaims = {
  deviceId: string;
  scopes: string[];
  expiresAtEpochMs: number;
};

export type LocalCutOAuthServiceOptions = {
  issuerBaseUrl: string;
  signingSecret: string;
  isDeviceAuthorized(deviceId: string): boolean;
  nowEpochMs?: () => number;
  randomToken?: () => string;
  pendingTtlMs?: number;
  codeTtlMs?: number;
  accessTtlMs?: number;
  refreshTtlMs?: number;
};

export class LocalCutOAuthService {
  readonly issuerBaseUrl: string;
  readonly resource: string;
  readonly clientId = CLIENT_ID;
  readonly redirectUri = REDIRECT_URI;
  readonly scopes = ["localcut:read", "localcut:write"] as const;

  private readonly signingSecret: string;
  private readonly isDeviceAuthorized: (deviceId: string) => boolean;
  private readonly nowEpochMs: () => number;
  private readonly randomToken: () => string;
  private readonly pendingTtlMs: number;
  private readonly codeTtlMs: number;
  private readonly accessTtlMs: number;
  private readonly refreshTtlMs: number;
  private readonly pending = new Map<string, PendingAuthorization>();
  private readonly codes = new Map<string, AuthorizationCodeRecord>();

  constructor(options: LocalCutOAuthServiceOptions) {
    this.issuerBaseUrl = normalizeIssuer(options.issuerBaseUrl);
    this.resource = `${this.issuerBaseUrl}/mcp`;
    if (!options.signingSecret || options.signingSecret.length < 8) throw new Error("OAUTH_SIGNING_SECRET_REQUIRED");
    this.signingSecret = options.signingSecret;
    this.isDeviceAuthorized = options.isDeviceAuthorized;
    this.nowEpochMs = options.nowEpochMs ?? Date.now;
    this.randomToken = options.randomToken ?? (() => randomBytes(32).toString("base64url"));
    this.pendingTtlMs = options.pendingTtlMs ?? DEFAULT_PENDING_TTL_MS;
    this.codeTtlMs = options.codeTtlMs ?? DEFAULT_CODE_TTL_MS;
    this.accessTtlMs = options.accessTtlMs ?? DEFAULT_ACCESS_TTL_MS;
    this.refreshTtlMs = options.refreshTtlMs ?? DEFAULT_REFRESH_TTL_MS;
  }

  beginAuthorization(input: OAuthBeginAuthorizationInput): { requestId: string; expiresAtEpochMs: number } {
    this.prune();
    if (input.responseType !== "code") throw new Error("OAUTH_RESPONSE_TYPE_INVALID");
    if (input.clientId !== this.clientId) throw new Error("OAUTH_CLIENT_INVALID");
    if (input.redirectUri !== this.redirectUri) throw new Error("OAUTH_REDIRECT_INVALID");
    if (input.resource !== this.resource) throw new Error("OAUTH_RESOURCE_INVALID");
    if (input.codeChallengeMethod !== "S256" || !isPkceChallenge(input.codeChallenge)) {
      throw new Error("OAUTH_PKCE_REQUIRED");
    }
    if (!input.state || input.state.length > 2_048) throw new Error("OAUTH_STATE_INVALID");
    const scopes = parseScopes(input.scope);
    const now = this.nowEpochMs();
    const requestId = this.requireRandomToken(this.randomToken());
    const expiresAtEpochMs = now + this.pendingTtlMs;
    this.pending.set(requestId, {
      requestId,
      clientId: input.clientId,
      redirectUri: input.redirectUri,
      state: input.state,
      codeChallenge: input.codeChallenge,
      resource: input.resource,
      scopes,
      expiresAtEpochMs,
      approvedDeviceId: null,
    });
    return { requestId, expiresAtEpochMs };
  }

  approveAuthorization(requestId: string, deviceId: string): void {
    const request = this.pending.get(requestId);
    if (!request) throw new Error("OAUTH_REQUEST_INVALID");
    if (request.expiresAtEpochMs <= this.nowEpochMs()) {
      this.pending.delete(requestId);
      throw new Error("OAUTH_REQUEST_EXPIRED");
    }
    if (!deviceId || !this.isDeviceAuthorized(deviceId)) throw new Error("OAUTH_DEVICE_UNAUTHORIZED");
    request.approvedDeviceId = deviceId;
  }

  completeAuthorization(requestId: string): OAuthAuthorizationCompletion {
    const request = this.pending.get(requestId);
    if (!request) throw new Error("OAUTH_REQUEST_INVALID");
    if (request.expiresAtEpochMs <= this.nowEpochMs()) {
      this.pending.delete(requestId);
      throw new Error("OAUTH_REQUEST_EXPIRED");
    }
    if (!request.approvedDeviceId || !this.isDeviceAuthorized(request.approvedDeviceId)) {
      throw new Error("OAUTH_DEVICE_APPROVAL_REQUIRED");
    }
    const code = this.requireRandomToken(this.randomToken());
    this.codes.set(code, {
      code,
      clientId: request.clientId,
      redirectUri: request.redirectUri,
      codeChallenge: request.codeChallenge,
      resource: request.resource,
      scopes: [...request.scopes],
      deviceId: request.approvedDeviceId,
      expiresAtEpochMs: this.nowEpochMs() + this.codeTtlMs,
    });
    this.pending.delete(requestId);
    return { code, state: request.state, redirectUri: request.redirectUri };
  }

  exchangeAuthorizationCode(input: {
    grantType: string;
    code: string;
    clientId: string;
    redirectUri: string;
    codeVerifier: string;
    resource: string;
  }): OAuthTokenResponse {
    if (input.grantType !== "authorization_code") throw new Error("OAUTH_GRANT_INVALID");
    const record = this.codes.get(input.code);
    if (!record) throw new Error("OAUTH_CODE_INVALID");
    this.codes.delete(input.code);
    if (record.expiresAtEpochMs <= this.nowEpochMs()) throw new Error("OAUTH_CODE_EXPIRED");
    if (input.clientId !== record.clientId || input.clientId !== this.clientId) throw new Error("OAUTH_CLIENT_INVALID");
    if (input.redirectUri !== record.redirectUri || input.redirectUri !== this.redirectUri) {
      throw new Error("OAUTH_REDIRECT_INVALID");
    }
    if (input.resource !== record.resource || input.resource !== this.resource) throw new Error("OAUTH_RESOURCE_INVALID");
    if (!isPkceVerifier(input.codeVerifier) || !safeEqual(pkceS256Challenge(input.codeVerifier), record.codeChallenge)) {
      throw new Error("OAUTH_PKCE_INVALID");
    }
    if (!this.isDeviceAuthorized(record.deviceId)) throw new Error("OAUTH_DEVICE_UNAUTHORIZED");
    return this.issueTokens(record.deviceId, record.scopes);
  }

  exchangeRefreshToken(input: {
    grantType: string;
    refreshToken: string;
    clientId: string;
    resource: string;
  }): OAuthTokenResponse {
    if (input.grantType !== "refresh_token") throw new Error("OAUTH_GRANT_INVALID");
    if (input.clientId !== this.clientId) throw new Error("OAUTH_CLIENT_INVALID");
    if (input.resource !== this.resource) throw new Error("OAUTH_RESOURCE_INVALID");
    const claims = this.verifySignedToken(input.refreshToken, "refresh");
    if (claims.clientId !== input.clientId || claims.aud !== input.resource) throw new Error("OAUTH_TOKEN_INVALID");
    if (!this.isDeviceAuthorized(claims.sub)) throw new Error("OAUTH_DEVICE_UNAUTHORIZED");
    return this.issueTokens(claims.sub, claims.scopes);
  }

  verifyAccessToken(accessToken: string, requiredScopes: readonly string[] = []): OAuthAccessClaims {
    const claims = this.verifySignedToken(accessToken, "access");
    if (claims.clientId !== this.clientId || claims.aud !== this.resource) throw new Error("OAUTH_TOKEN_INVALID");
    if (!this.isDeviceAuthorized(claims.sub)) throw new Error("OAUTH_DEVICE_UNAUTHORIZED");
    for (const scope of requiredScopes) {
      if (!claims.scopes.includes(scope)) throw new Error("OAUTH_SCOPE_INSUFFICIENT");
    }
    return {
      deviceId: claims.sub,
      scopes: [...claims.scopes],
      expiresAtEpochMs: claims.exp,
    };
  }

  private issueTokens(deviceId: string, scopes: string[]): OAuthTokenResponse {
    const now = this.nowEpochMs();
    const access = this.signToken({
      v: 1,
      typ: "access",
      sub: deviceId,
      aud: this.resource,
      clientId: this.clientId,
      scopes: [...scopes],
      iat: now,
      exp: now + this.accessTtlMs,
      jti: this.requireRandomToken(this.randomToken()),
    });
    const refresh = this.signToken({
      v: 1,
      typ: "refresh",
      sub: deviceId,
      aud: this.resource,
      clientId: this.clientId,
      scopes: [...scopes],
      iat: now,
      exp: now + this.refreshTtlMs,
      jti: this.requireRandomToken(this.randomToken()),
    });
    return {
      accessToken: access,
      refreshToken: refresh,
      tokenType: "Bearer",
      expiresIn: Math.floor(this.accessTtlMs / 1_000),
      scope: scopes.join(" "),
    };
  }

  private signToken(claims: TokenClaims): string {
    const payload = Buffer.from(JSON.stringify(claims), "utf8").toString("base64url");
    const signature = createHmac("sha256", this.signingSecret).update(`lco1.${payload}`, "utf8").digest("base64url");
    return `lco1.${payload}.${signature}`;
  }

  private verifySignedToken(token: string, expectedType: TokenClaims["typ"]): TokenClaims {
    const parts = token.split(".");
    if (parts.length !== 3 || parts[0] !== "lco1") throw new Error("OAUTH_TOKEN_INVALID");
    const expected = createHmac("sha256", this.signingSecret).update(`lco1.${parts[1]}`, "utf8").digest("base64url");
    if (!safeEqual(expected, parts[2])) throw new Error("OAUTH_TOKEN_INVALID");
    let claims: unknown;
    try {
      claims = JSON.parse(Buffer.from(parts[1], "base64url").toString("utf8"));
    } catch {
      throw new Error("OAUTH_TOKEN_INVALID");
    }
    if (!isTokenClaims(claims) || claims.typ !== expectedType) throw new Error("OAUTH_TOKEN_INVALID");
    if (claims.exp <= this.nowEpochMs()) throw new Error("OAUTH_TOKEN_EXPIRED");
    for (const scope of claims.scopes) {
      if (!SUPPORTED_SCOPES.has(scope)) throw new Error("OAUTH_TOKEN_INVALID");
    }
    return claims;
  }

  private prune(): void {
    const now = this.nowEpochMs();
    for (const [key, value] of this.pending) if (value.expiresAtEpochMs <= now) this.pending.delete(key);
    for (const [key, value] of this.codes) if (value.expiresAtEpochMs <= now) this.codes.delete(key);
  }

  private requireRandomToken(value: string): string {
    if (!value || value.length < 16 || value.length > 256) throw new Error("OAUTH_RANDOM_INVALID");
    return value;
  }
}

export function pkceS256Challenge(verifier: string): string {
  return createHash("sha256").update(verifier, "ascii").digest("base64url");
}

function parseScopes(raw: string): string[] {
  const scopes = [...new Set(raw.trim().split(/\s+/).filter(Boolean))];
  if (scopes.length === 0 || scopes.some(scope => !SUPPORTED_SCOPES.has(scope))) throw new Error("OAUTH_SCOPE_INVALID");
  return scopes;
}

function isPkceChallenge(value: string): boolean {
  return /^[A-Za-z0-9_-]{43,128}$/.test(value);
}

function isPkceVerifier(value: string): boolean {
  return /^[A-Za-z0-9._~-]{43,128}$/.test(value);
}

function normalizeIssuer(value: string): string {
  const url = new URL(value);
  if (url.protocol !== "https:" || url.username || url.password || url.search || url.hash) {
    throw new Error("OAUTH_ISSUER_INVALID");
  }
  if (url.pathname !== "/" && url.pathname !== "") throw new Error("OAUTH_ISSUER_INVALID");
  return `${url.origin}`;
}

function safeEqual(left: string, right: string): boolean {
  const a = Buffer.from(left, "utf8");
  const b = Buffer.from(right, "utf8");
  return a.length === b.length && timingSafeEqual(a, b);
}

function isTokenClaims(value: unknown): value is TokenClaims {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const v = value as Record<string, unknown>;
  return v.v === 1 &&
    (v.typ === "access" || v.typ === "refresh") &&
    typeof v.sub === "string" && v.sub.length > 0 &&
    typeof v.aud === "string" && v.aud.length > 0 &&
    typeof v.clientId === "string" && v.clientId.length > 0 &&
    Array.isArray(v.scopes) && v.scopes.every(scope => typeof scope === "string") &&
    typeof v.iat === "number" && Number.isSafeInteger(v.iat) &&
    typeof v.exp === "number" && Number.isSafeInteger(v.exp) &&
    typeof v.jti === "string" && v.jti.length > 0;
}
