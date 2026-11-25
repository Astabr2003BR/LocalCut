// Isolated simulation only: no server, route, transport, dispatch or response data.
// Not an integration or an authorization boundary. Never deploy this as one.
import { ExperimentalReadOnlyGuard } from './experimentalReadOnlyGuard.mjs';

const DISABLED = Object.freeze({ status: 'experimental_disabled' });
const BLOCKED = Object.freeze({ status: 'blocked_no_trusted_binding' });

export class ExperimentalBlockedRelay {
  #guard = null;
  #enabled = false;

  constructor({ optIn = false, authorityPublicKey } = {}) {
    // Exact, programmatic opt-in; no environment variable or production switch.
    if (optIn !== true) return;
    this.#enabled = true;
    try {
      this.#guard = new ExperimentalReadOnlyGuard({
        authorityPublicKey,
        // This is intentionally impossible to configure or override. There is
        // no trusted, consented live-binding implementation in this prototype.
        verifyLiveBinding: () => false,
      });
    } catch {
      // Invalid or absent authority is not a reason to open a path.
    }
  }

  evaluate(raw, authenticatedControllerId) {
    if (!this.#enabled) return DISABLED;
    try {
      // No raw input, IDs, accepted command, error or result is returned/logged.
      // Even an unexpectedly accepting guard cannot cause a dispatch here.
      this.#guard?.authorize(raw, authenticatedControllerId);
    } catch {
      // Keep the same sanitized, fail-closed status.
    }
    return BLOCKED;
  }
}
