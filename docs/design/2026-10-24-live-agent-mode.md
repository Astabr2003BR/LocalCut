# LocalCut Live Agent Mode — Design

## Intent
Enable a connected ChatGPT controller to operate LocalCut in a closed feedback loop: inspect the current project, see the rendered preview frame, issue edits, inspect the new result, and continue until the requested edit is complete.

## User flow
1. User keeps the LocalCut Bridge connected.
2. User gives a high-level command in ChatGPT (for example, assemble and animate an anime scene).
3. The controller reads current project state/revision and playhead.
4. The controller requests a preview frame from the APK.
5. The controller issues revision-guarded edit commands.
6. The controller requests state + preview again and iterates.
7. The user can stop the loop at any time by disconnecting/revoking the Bridge; manual edits are protected by revision conflicts.

## Architecture
Use the existing authenticated Bridge/MCP command path instead of adding a second remote-control channel. `GET_PREVIEW_FRAME`, which already exists in protocol v1 but currently returns `PREVIEW_FAILED`, will be backed by a real Android preview-frame port. The port captures the current `PlayerView` surface, compresses a bounded JPEG, and returns `{mimeType, base64, width, height, positionUs}` through the existing encrypted/authenticated command route.

The controller MCP adapter exposes successful preview-frame responses as both structured metadata and MCP image content, so an agent can visually inspect the frame. Existing `GET_PROJECT_STATE`, timeline/list/read commands and revision-required edit commands remain the source of truth for state and mutation.

## Safety / consistency
- No new unauthenticated endpoint.
- Preview capture is read-only and bounded in dimensions/quality.
- Project mutations keep `expectedRevision`; a manual edit causes `REVISION_CONFLICT` instead of silent overwrite.
- No continuous background autonomous job is introduced inside the APK. The controller drives each iteration explicitly.
- Disconnect/revoke immediately stops controller access.
- Media import remains locally authorized unless a future explicit permission design changes it.

## Approaches considered
1. **Screenshot the full Android window:** easiest but leaks unrelated UI and system chrome; rejected.
2. **Export a temporary frame through the render pipeline:** faithful but too expensive for an interactive loop; retained only as future fallback.
3. **Capture only the PlayerView video surface and return it through the existing Bridge:** selected. It is fast, scoped to the preview, and fits the current protocol.

## Non-goals for this block
- Generating source drawings/frames with an AI model inside LocalCut.
- Background editing while ChatGPT is not actively executing a turn.
- Remote access to arbitrary device files/screens.
- Bypassing the pairing/revision security model.

## Follow-up
After visual feedback is stable, extend Bridge mutations for Motion/Design tools (effects, masks, curves, design elements, camera/parenting as those backends become real).