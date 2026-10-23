# LocalCut Capability Matrix — CapCut + Alight Motion + useful Canva video/design

Date: 2026-10-23

Legend:
- LIVE: implemented and backed by real editor/renderer behavior with tests in current LocalCut architecture.
- PARTIAL: model/foundation or some backend exists, but the complete workflow is not yet proven.
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
| Lock/hide tracks | Canva/Alight | PARTIAL model/UI |
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
| Property animations | Alight | LIVE foundation |
| Crop Fit/Fill/Stretch | CapCut/Canva | LIVE |
| Mirror/flip | CapCut/Canva | PLANNED |
| Rectangle/ellipse/freeform masks | CapCut/Alight | LIVE |
| Mask feather/expand/invert | CapCut/Alight | LIVE backend where supported |
| Chroma key | CapCut | LIVE |
| Blend modes | CapCut/Alight | PARTIAL model; advanced renderer pending |
| Group/clipping masks | Alight/Canva | PLANNED |

## Animation / motion design

| Capability | Target inspiration | LocalCut status |
|---|---|---|
| Hold/linear/ease/bounce/elastic/steps | Alight | LIVE evaluator |
| Cubic bezier/custom curves | Alight | LIVE model/evaluator; UI PARTIAL |
| Seeded random/cyclic timing | Alight | LIVE evaluator |
| Parenting | Alight | PARTIAL graph |
| Groups/null controllers | Alight/Canva | PARTIAL graph/model |
| Camera objects/cuts | Alight | PARTIAL model |
| 2.5D depth/parallax | Alight | PARTIAL |
| Motion path | Alight | PARTIAL metadata |
| Motion blur | Alight | PARTIAL metadata; renderer PLANNED |
| Anime motion recipes | LocalCut | LIVE/PARTIAL recipes |

## Color / effects / filters

| Capability | Target inspiration | LocalCut status |
|---|---|---|
| Brightness/contrast/exposure/saturation | CapCut/Alight | LIVE |
| Temperature/tint | CapCut | LIVE backend |
| Gaussian blur | All | LIVE |
| Sharpen/vignette/grain | CapCut/Alight | LIVE shader backend |
| RGB split/mosaic/glow/ripple/displacement | Alight/CapCut effects | LIVE shader backend |
| HSL | CapCut | PARTIAL model/backend expansion needed |
| Curves | CapCut | PLANNED |
| LUT/imported filters | CapCut | PLANNED |
| Reusable effect stacks/presets | Alight | PARTIAL |
| 100+ composable effect building blocks | Alight | PLANNED incremental registry |

## Time tools

| Capability | Target inspiration | LocalCut status |
|---|---|---|
| Constant speed | CapCut/Canva | LIVE |
| Speed ramp | CapCut | LIVE backend |
| Custom speed curve editor | CapCut | PARTIAL model; UI PLANNED |
| Reverse | CapCut | PARTIAL model; renderer PLANNED |
| Freeze/frame hold | CapCut | PARTIAL model; renderer PLANNED |
| General source-time remap | Alight/pro editors | PARTIAL |

## Text / captions

| Capability | Target inspiration | LocalCut status |
|---|---|---|
| Text overlay | All | LIVE |
| Timed text editing | Canva/CapCut | LIVE |
| Manual caption blocks/timeline | Canva/CapCut | PARTIAL |
| Caption split/merge/restyle | Canva | PARTIAL model/history |
| Custom font metadata | Alight/Canva | PARTIAL |
| Stroke/shadow/background box | All | PARTIAL |
| Gradient/multi-border | Alight/Canva | PLANNED |
| Text entrance/exit/loop animations | CapCut/Canva | PARTIAL |
| Per-character animation | Alight | PARTIAL metadata |
| Automatic transcription | CapCut/Canva | EXCLUDED-AI |
| Text-to-speech | CapCut | EXCLUDED-AI |

## Audio

| Capability | Target inspiration | LocalCut status |
|---|---|---|
| Multiple audio tracks | All | LIVE/PARTIAL renderer depth |
| Volume/fades | All | LIVE |
| Pan | Alight/pro audio | PARTIAL |
| Normalization | CapCut/Canva | LIVE/PARTIAL DSP/render |
| EQ/compressor/limiter | Professional audio | LIVE/PARTIAL backend |
| Extract audio from video | CapCut | PLANNED command |
| Beat markers/local beat detection | CapCut/Canva rhythm workflows | LIVE foundation |
| Voice-over record/import | CapCut/Canva | PLANNED |
| Noise reduction/vocal isolation | CapCut | EXCLUDED-AI unless deterministic local DSP implementation is later approved |

## Design / canvas

| Capability | Target inspiration | LocalCut status |
|---|---|---|
| Basic vector shapes | Canva/Alight | LIVE |
| Editable path metadata | Alight/Canva | LIVE/PARTIAL UI |
| Fill/gradient/stroke/shadow | Canva/Alight | PARTIAL |
| Groups | Canva/Alight | PARTIAL graph |
| Layer ordering/z-index | Canva | LIVE/PARTIAL UI |
| Align/guides/snap | Canva | LIVE foundation |
| Distribute | Canva | PLANNED |
| Reusable local components | Canva/Alight | LIVE foundation |
| Local templates | Canva | PARTIAL components; browser PLANNED |
| Replace media preserving timing/layout | Canva | PLANNED |
| Canvas/aspect/social presets | Canva/CapCut | PLANNED UI |

## Tracking / analysis

| Capability | Target inspiration | LocalCut status |
|---|---|---|
| Motion tracking | CapCut/Alight parenting workflows | PARTIAL metadata/contracts |
| Stabilization | CapCut | PARTIAL metadata/contracts |
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
| Preview/export shared semantics | Professional requirement | PARTIAL with growing smoke coverage |

## Mobile UI / workflow

| Capability | Target inspiration | LocalCut status |
|---|---|---|
| Large preview + dominant timeline | CapCut/Canva | IN PROGRESS |
| Bottom contextual tool rail | CapCut | IN PROGRESS |
| Single contextual inspector sheet | CapCut/Alight | IN PROGRESS |
| Compact Bridge status | LocalCut | IN PROGRESS |
| Layer panel | Canva/Alight | PLANNED |
| Curve editor | Alight/CapCut | PLANNED |
| Effects browser | CapCut/Alight | PLANNED |
| Template/components browser | Canva | PLANNED |
| Selection-aware tools | All | IN PROGRESS |

## Research notes

Capability categories were refreshed on 2026-10-23 from official CapCut editing material, Alight Motion product/help documentation and Canva Video 2.0/help documentation. Public GitHub Android editors using Media3/Compose were inspected only for architectural patterns and ecosystem constraints; LocalCut must not copy their code or UI.
