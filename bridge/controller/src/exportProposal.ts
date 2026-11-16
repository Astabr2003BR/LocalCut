import { validCorrectionOperations } from "./reviewCorrection.ts";

const record = (v: unknown): v is Record<string, unknown> => v !== null && typeof v === "object" && !Array.isArray(v);
const exact = (v: Record<string, unknown>, keys: string[]) => Object.keys(v).length === keys.length && keys.every(k => Object.hasOwn(v,k));
const id = (v: unknown): v is string => typeof v === "string" && v.trim().length > 0 && v.length <= 256;

// Only a proposal envelope. Android rechecks the immutable rendered evidence,
// operation semantics, ownership, locks and local approval before any edit.
export function validExportProposalPayload(payload: unknown, projectId: string, revision: number | undefined): boolean {
  if (!record(payload) || !exact(payload,["planJson"]) || typeof payload.planJson !== "string" ||
      Buffer.byteLength(payload.planJson,"utf8") > 8192 || !Number.isSafeInteger(revision) || revision! < 0) return false;
  try {
    const plan: unknown = JSON.parse(payload.planJson);
    if (!record(plan) || !exact(plan,["projectId","expectedRevision","operations","review"]) ||
        plan.projectId !== projectId || plan.expectedRevision !== revision || !record(plan.review)) return false;
    const r = plan.review;
    if (!exact(r,["jobId","sha256","findings"]) || !id(r.jobId) || r.jobId.length > 128 ||
        typeof r.sha256 !== "string" || !/^[a-f0-9]{64}$/.test(r.sha256) ||
        typeof r.findings !== "string" || !r.findings.trim() || r.findings.length > 1000) return false;
    const ops = plan.operations;
    return Array.isArray(ops) && ops.length >= 1 && ops.length <= 8 && ops.every(op => {
      if (record(op) && op.op === "replaceStillImage") {
        return exact(op,["op","clipId","expectedAssetId","replacementAssetId"]) &&
          id(op.clipId) && id(op.expectedAssetId) && id(op.replacementAssetId) &&
          op.expectedAssetId !== op.replacementAssetId;
      }
      return validCorrectionOperations([op]);
    });
  } catch { return false; }
}
