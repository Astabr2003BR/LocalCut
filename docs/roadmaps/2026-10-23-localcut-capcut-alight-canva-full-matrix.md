# LocalCut Capability Matrix — CapCut + Alight Motion + useful Canva video/design

Date: 2026-10-23
Last verified refresh: 2026-10-30 against `feature/localcut-bridge-v1`

Legend:
- LIVE: implemented and backed by real editor/renderer behavior with focused tests or an existing validated production path.
- LIVE/PARTIAL VALIDATION: real implementation exists and is reachable, but one or more Android CI/device/visual gates are still pending.
- PARTIAL: model/foundation or some backend exists, but the complete user workflow/backend is not yet proven.
- PLANNED: researched capability target, not yet exposed as complete.
- EXCLUDED-AI: intentionally deferred because it depends on generative/AI functionality.

## Timeline / editing

| Capability | Target inspiration | LocalCut status |
|---|---|---|
| Video/image import | CapCut/Canva | LIVE |
| Audio import | CapCut/Canva | LIVE |
| Overlay import | CapCut | LIVE |
| Multitrack timeline | All | LIVE/PARTIAL advanced lanes |
| Select/move/trim/split/delete/duplicate | CapCut/Canva | LIVE |
| Undo/redo | All | LIVE |
| Autosave/reopen | All | LIVE |
| Mute/solo | Pro editors | LIVE |
| Hide visual layer | Canva/Alight | LIVE through CompositionGraph + renderer |
| Lock layers/tracks | Canva/Alight | PARTIAL model; edit enforcement pending |
| Timeline zoom/ruler refinement | Canva/CapCut | PLANNED |
| Ripple/insert/overwrite editing modes | CapCut/pro NLE | PLANNED |
| Markers/ranges | Pro NLE/Canva timing | PARTIAL beat markers only |
| Audio waveform | Canva/CapCut | LIVE/PARTIAL refinement |
| Transitions | CapCut/Canva | LIVE basic fade; PLANNED library |

## Transform / compositing

| Capability | Target inspiration | LocalCut status |
|---|---|---|
| Position/scale/rotation/opacity | All | LIVE |
| Transform keyframes | CapCut/Alight | LIVE |
| Property animations | Alight | LIVE |
| Crop Fit/Fill/Stretch | CapCut/Canva | LIVE |
| Mirror/flip | CapCut/Canva | PLANNED backend/model path |
| Rectangle/ellipse/freeform masks | CapCut/Alight | LIVE |
| Mask feather/expand/invert | CapCut/Alight | LIVE backend where supported; dedicated UI refinement pending |
| Chroma key | CapCut | LIVE |
| Blend modes | CapCut/Alight | LIVE/PARTIAL VALIDATION — real multi-texture Media3 compositor + mobile UI + undo/redo; final Android gate pending |
| Group/clipping masks | Alight/Canva | PLANNED |

## Animation / motion design

| Capability | Target inspiration | LocalCut status |
|---|---|---|
| Hold/linear/ease/bounce/elastic/steps | Alight | LIVE evaluator + mobile UI |
| Cubic bezier/custom curves | Alight | LIVE evaluator + mobile Bézier editor |
| Seeded random/cyclic timing | Alight | LIVE evaluator + mobile presets |
| Parenting | Alight | LIVE planar parenting + mobile parent controls; broader camera/3D rig workflow pending |
| Groups/null controllers | Alight/Canva | LIVE/PARTIAL — real GROUP/NULL creation and parenting; advanced group operations pending |
| Camera objects/cuts | Alight | PARTIAL — camera transform pipeline exists; discrete camera cuts/3D plane workflow pending |
| 2.5D depth/parallax | Alight | PARTIAL |
| Motion path | Alight | PARTIAL metadata |
| Motion blur | Alight | LIVE/PARTIAL VALIDATION — real temporal GPU effect + mobile UI; final Android/physical visual gate pending |
| Anime motion recipes | LocalCut | LIVE/PARTIAL recipes |

## Color / effects / filters

| Capability | Target inspiration | LocalCut status |
|---|---|---|
| Brightness/contrast/exposure/saturation | CapCut/Alight | LIVE + sliders |
| Temperature/tint | CapCut | LIVE + sliders |
| HSL global adjustment | CapCut | LIVE Media3 backend + mobile UI |
| Gaussian blur | All | LIVE |
| Sharpen/vignette/grain | CapCut/Alight | LIVE shader backend |
| RGB split/mosaic/glow/ripple/displacement | Alight/CapCut effects | LIVE shader backend |
| Original LocalCut filter presets | CapCut-style workflow | LIVE, implemented from LocalCut color parameters |
| RGB/curve editor | CapCut | PLANNED |
| LUT import | CapCut | PLANNED |
| Reusable effect stacks/presets | Alight | PARTIAL |
| Large composable effect library | Alight | PLANNED incremental registry |

## Time tools

| Capability | Target inspiration | LocalCut status |
|---|---|---|
| Constant speed | CapCut/Canva | LIVE |
| Speed ramp | CapCut | LIVE backend |
| Custom speed curve editor | CapCut | PARTIAL model; UI PLANNED |
| Reverse | CapCut | LIVE/PARTIAL VALIDATION — generated-media FFmpeg round trip + trim-safe replacement + history/UI; physical Android validation pending |
| Freeze/frame hold | CapCut | LIVE/PARTIAL VALIDATION — Media3 freeze planner + playhead UI/history + export compatibility guards |
| General source-time remap | Alight/pro editors | PARTIAL — shared TimeMap evaluator covers speed/ramp/reverse/freeze semantics; broader arbitrary remap UI pending |

## Text / captions

| Capability | Target inspiration | LocalCut status |
|---|---|---|
| Text overlay | All | LIVE |
| Timed text editing | Canva/CapCut | LIVE |
| Manual caption creation/editing | Canva/CapCut | LIVE via TextOverlay + CaptionMetadata |
| Caption split/merge/restyle | Canva | PARTIAL model/history; full UI pending |
| Custom font metadata/family | Alight/Canva | PARTIAL; renderer reads family metadata |
| Letter spacing | Alight/Canva | LIVE |
| Background box | All | LIVE |
| Shadow | All | LIVE |
| Stroke/multi-border | Alight/Canva | PARTIAL model; renderer completion pending |
| Text entrance/exit/loop animations | CapCut/Canva | LIVE for renderer-supported descriptors |
| Typewriter/per-character reveal | Alight/CapCut | LIVE renderer path |
| Automatic transcription | CapCut/Canva | EXCLUDED-AI |
| Text-to-speech | CapCut | EXCLUDED-AI |

## Audio

| Capability | Target inspiration | LocalCut status |
|---|---|---|
| Multiple audio tracks | All | LIVE/PARTIAL renderer depth |
| Volume/fades | All | LIVE |
| Pan | Alight/pro audio | LIVE DSP + mobile control |
| Gain/normalization | CapCut/Canva | LIVE DSP + mobile control |
| 3-band EQ | Professional audio | LIVE DSP + mobile control |
| Compressor | Professional audio | LIVE DSP + mobile toggle |
| Limiter | Professional audio | LIVE DSP + mobile toggle |
| Noise gate | Professional audio | LIVE DSP + mobile toggle |
| Extract audio from video | CapCut | PLANNED command — pending codebase audit before implementation |
| Beat markers/local beat detection | CapCut/Canva rhythm workflows | LIVE foundation; editor workflow PARTIAL |
| Voice-over record/import | CapCut/Canva | PLANNED |
| AI vocal isolation/enhancement | CapCut | EXCLUDED-AI |

## Design / canvas

| Capability | Target inspiration | LocalCut status |
|---|---|---|
| Basic vector shapes | Canva/Alight | LIVE |
| Editable path metadata | Alight/Canva | LIVE/PARTIAL UI |
| Fill/gradient/stroke/shadow | Canva/Alight | PARTIAL |
| Groups | Canva/Alight | PARTIAL graph |
| Layer ordering/z-index | Canva | LIVE for design elements |
| Bring to front/send to back | Canva | LIVE |
| Visual media layer show/hide | Canva/Alight | LIVE |
| Align/guides/snap | Canva | LIVE foundation |
| Distribute | Canva | PLANNED |
| Reusable local components | Canva/Alight | LIVE foundation |
| Local templates/browser | Canva | PARTIAL components; browser PLANNED |
| Replace media preserving timing/layout | Canva | PLANNED generic workflow; Reverse has its own trim-safe replacement path |
| Canvas/aspect/social presets | Canva/CapCut | PLANNED UI |
| Drawing symmetry — vertical/horizontal/both axes | Krita/Clip Studio/FlipaClip workflows | LIVE backend + DrawStrokePad UI (`Ambos`) |

## Tracking / analysis

| Capability | Target inspiration | LocalCut status |
|---|---|---|
| Motion tracking | CapCut/Alight parenting workflows | LIVE/PARTIAL VALIDATION — VIDEO object tracking workflow + ROI + Termux/OpenCV + history; unsupported combinations fail closed; physical Android gate pending |
| Stabilization | CapCut | LIVE/PARTIAL VALIDATION — global-motion analysis + non-destructive property-animation render path + removal; physical Android gate pending |
| Auto-reframe | CapCut/Canva | PARTIAL metadata; analyzer PLANNED |
| Tracked parenting | Alight | PLANNED integration |
| Generative cutout/background removal | CapCut/Canva | EXCLUDED-AI |

## Project / export

| Capability | Target inspiration | LocalCut status |
|---|---|---|
| MP4 H.264 | All | LIVE |
| MP4 HEVC | Alight/CapCut | LIVE capability-dependent |
| Export profiles | CapCut | LIVE |
| GIF | Alight | PLANNED |
| PNG/image sequence | Alight | PLANNED |
| Project package | Alight | LIVE/PARTIAL archive contract |
| Stable APK upgrade signing | LocalCut | LIVE |
| Preview/export shared semantics | Professional requirement | PARTIAL with growing smoke coverage and shared Media3 paths for multiple advanced effects |
| Still-image + audio export regression | Screenshot-driven LocalCut requirement | instrumentation smoke added; final CI result pending |

## Mobile UI / workflow

| Capability | Target inspiration | LocalCut status |
|---|---|---|
| Large preview + dominant timeline | CapCut/Canva | LIVE in redesigned EditorScreen pending final build gate |
| Bottom contextual tool rail | CapCut | LIVE pending final build gate |
| Single contextual inspector surface | CapCut/Alight | LIVE pending final build gate |
| Compact Bridge status/details | LocalCut | LIVE pending final build gate |
| Selection-aware tools | All | LIVE policy/UI |
| Layer panel | Canva/Alight | LIVE show/hide/select |
| Easing/Bézier curve editor | Alight/CapCut | LIVE |
| Professional color panel | CapCut | LIVE |
| Professional audio panel | CapCut/Alight | LIVE |
| Text/caption styling and motion panel | CapCut/Alight/Canva | LIVE for renderer-supported properties |
| Effects browser/category UI | CapCut/Alight | PARTIAL current presets; full browser PLANNED |
| Template/components browser | Canva | PLANNED |

## Remaining major non-AI parity targets

The authorization remains broad, but completed implementations are no longer kept in this pending list. Current major targets are: lock enforcement; timeline zoom/ruler and ripple/insert/overwrite refinement; full camera cuts/3D rig workflow; RGB curves/LUT import; larger transitions/effects browser; voice-over; extract-audio command after codebase audit; auto-reframe analyzer; tracked-parenting integration; generic replace-media workflow; social canvas presets; GIF/image-sequence export; full template browser; and additional project-package workflows.

Implemented advanced blocks that still have validation gates are tracked separately rather than mislabeled as PLANNED: Reverse, Freeze Frame, Motion Blur, advanced Blend, Object Tracking and Stabilization.

## Verification notes — 2026-10-30

This refresh was reconciled against newer checkpoints and current branch code rather than the original 2026-10-23 assumptions. Historical checkpoints remain useful evidence, but newer checkpoints supersede stale capability statements when they conflict. Device/Android gates are intentionally left visible where they have not been proven.

## Research notes

Capability categories were originally refreshed on 2026-10-23 from official CapCut editing material, Alight Motion product/help documentation and Canva video/design documentation. Public GitHub Android editors using Media3/Compose were inspected only for architectural patterns and ecosystem constraints; LocalCut does not copy their code or UI.
