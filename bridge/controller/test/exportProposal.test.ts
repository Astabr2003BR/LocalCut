import assert from "node:assert/strict";
import test from "node:test";
import { validExportProposalPayload } from "../src/exportProposal.ts";
import { projectWriteCapabilities, revisionRequiredCapabilities } from "../src/protocol.ts";

const plan = {projectId:"p",expectedRevision:5,operations:[{op:"patchClip",clipId:"c",opacity:0.8}],
  review:{jobId:"job",sha256:"a".repeat(64),findings:"Amostras mostram opacidade incorreta."}};
const payload = (p: unknown = plan) => ({planJson:JSON.stringify(p)});
test("export proposal is revision-bound but not a project mutation",()=>{
  assert.equal(revisionRequiredCapabilities.has("submit_export_review_proposal"),true);
  assert.equal(projectWriteCapabilities.has("submit_export_review_proposal"),false);
  assert.equal(validExportProposalPayload(payload(),"p",5),true);
  assert.equal(validExportProposalPayload(payload(),"other",5),false);
  assert.equal(validExportProposalPayload(payload(),"p",6),false);
  assert.equal(validExportProposalPayload(payload(),"p",undefined),false);
  assert.equal(validExportProposalPayload(payload({...plan,operations:[{op:"replaceStillImage",clipId:"c",expectedAssetId:"old",replacementAssetId:"new"}]}),"p",5),true);
});
test("export proposal rejects destructive timing/audio edits, mismatched fields and excessive data",()=>{
  for(const p of [
    {...plan,operations:[{op:"deleteClip",clipId:"c"}]},
    {...plan,operations:[{op:"patchClip",clipId:"c",volume:0}]},
    {...plan,operations:[{op:"patchClip",clipId:"c",startUs:1}]},
    {...plan,operations:[]},
    {...plan,operations:Array(9).fill(plan.operations[0])},
    {...plan,review:{...plan.review,sha256:"bad"}},
    {...plan,review:{...plan.review,findings:""}},
    {...plan,review:{...plan.review,findings:"x".repeat(1001)}},
    {...plan,review:{...plan.review,extra:"bad"}},
    {...plan,review:undefined},
    {...plan,extra:true},
  ]) assert.equal(validExportProposalPayload(payload(p),"p",5),false);
  assert.equal(validExportProposalPayload({planJson:"x".repeat(8193)},"p",5),false);
  assert.equal(validExportProposalPayload({planJson:"{"},"p",5),false);
  assert.equal(validExportProposalPayload({...payload(),extra:true},"p",5),false);
});
