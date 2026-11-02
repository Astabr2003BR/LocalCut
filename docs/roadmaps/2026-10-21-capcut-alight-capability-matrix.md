# LocalCut — CapCut + Alight Motion capability matrix

Date: 2026-10-21

Purpose: use current CapCut and Alight Motion capabilities as product research for an original LocalCut implementation. This is **capability parity**, not source/UI/asset copying. Proprietary code, APKs, bundled assets, effect presets, names/trade dress and private APIs are out of scope.

## Evidence reviewed

Official/current product and help material reviewed for this matrix:

- CapCut advanced editing overview: https://www.capcut.com/resource/how-to-use-capcut
- CapCut keyframes: https://www.capcut.com/tools/keyframe-animation
- CapCut background removal / chroma key: https://www.capcut.com/tools/video-background-remover
- CapCut auto captions help: https://www.capcut.com/help/auto-captions-in-capcut
- Alight Motion product capabilities: https://alightmotion.com/
- Alight Motion easing curves: https://support.alightmotion.com/hc/en-us/articles/10536934703889-Animation-Easing-Curves
- Alight Motion effects guide: https://support.alightmotion.com/hc/en-us/articles/10536991943569-Effects-Guide
- Alight Motion motion blur: https://support.alightmotion.com/hc/en-us/articles/10537118778129-Motion-Blur
- Alight Motion camera objects: https://support.alightmotion.com/hc/en-us/articles/10536993608977-Camera-Objects-Guide
- Alight Motion feature-guide index, including parenting/null objects: https://support.alightmotion.com/hc/en-us/categories/10536777021713-Effects-Feature-Guides

The matrix should be updated when official products change.

## Status legend

- ✅ LocalCut already has a validated base implementation.
- 🟡 Partial/base exists but lacks professional controls or parity.
- ⬜ Planned original implementation.
- ☁️ Optional network/AI module; never a dependency of LocalCut's offline editor core.

## 1. Timeline and core editing

| Capability | Reference apps | LocalCut |
| --- | --- | --- |
| Multitrack timeline | CapCut / Alight Motion | ✅ |
| Multiple visual/audio layers | CapCut / Alight Motion | ✅ |
| Import video/image/audio | Both | ✅ |
| Select/move/delete clips | Both | ✅ |
| Trim left/right | Both | ✅ |
| Split clips | Both | ✅ |
| Duplicate clips | Both | ✅ |
| Undo/redo | Both | ✅ |
| Clip/layer locking | Professional layer workflow | 🟡 model support; UI expansion planned |
| Hide/show visual layers | Layer workflow | 🟡 model support; UI expansion planned |
| Mute/solo audio tracks | Professional audio workflow | ✅ |
| Group layers | Alight Motion | ⬜ |
| Nested/pre-compose groups | Motion-design workflow | ⬜ |
| Reusable elements/templates | Alight Motion Elements | ⬜ |
| Ripple/insert/overwrite editing modes | Professional NLE parity | ⬜ |
| Timeline snapping/guides | Professional NLE parity | ⬜ |
| Markers/range selection | Professional NLE parity | ⬜ |

## 2. Keyframes, motion and easing

| Capability | Reference apps | LocalCut |
| --- | --- | --- |
| Position keyframes | Both | ✅ |
| Scale keyframes | Both | ✅ |
| Rotation keyframes | Both | ✅ |
| Opacity keyframes | Both | ✅ |
| Timeline keyframe markers | Both | ✅ |
| Previous/next keyframe navigation | Motion workflow | ✅ |
| Linear interpolation | Both | ✅ |
| Per-property keyframes | Alight Motion | 🟡 transform bundle today; per-property curves planned |
| Cubic Bézier easing | Alight Motion | ⬜ |
| Ease in / ease out / ease-in-out presets | Alight Motion | ⬜ |
| Custom curve editor | Alight Motion | ⬜ |
| Bounce / Elastic / Cyclic / Random / Steps easing | Alight Motion | ⬜ |
| Overshoot | Alight Motion | ⬜ |
| Copy/paste easing curves | Alight Motion | ⬜ |
| Keyframe value graph / velocity graph | Motion design | ⬜ |
| Keyframe selection/copy/paste | Both | ⬜ |
| Direct keyframe dragging | Both | ⬜ |
| Motion presets | Both | ⬜ |

## 3. Transform, compositing, masks and cutouts

| Capability | Reference apps | LocalCut |
| --- | --- | --- |
| Position/scale/rotation/opacity | Both | ✅ |
| Z-order/layer ordering | Both | ✅ base |
| Blend modes | CapCut / motion design | ⬜ |
| Geometric masks | CapCut / Alight Motion | ⬜ |
| Freeform masks | Both | ⬜ |
| Mask feather | Both | ⬜ |
| Mask expansion/erosion | Professional compositing | ⬜ |
| Animated masks | Both | ⬜ |
| Invert mask | Both | ⬜ |
| Group masking | Alight Motion | ⬜ |
| Chroma key / green screen | CapCut | ⬜ |
| Chroma strength/threshold | CapCut | ⬜ |
| Edge/shadow/refinement controls | CapCut | ⬜ |
| Automatic background removal | CapCut | ☁️ optional ML module |
| Manual cutout/refinement | CapCut | ⬜ |
| Crop/fit/fill | Both | ⬜ professional UI |

## 4. Vector, shapes and motion graphics

| Capability | Reference apps | LocalCut |
| --- | --- | --- |
| Vector shape layers | Alight Motion | ⬜ |
| Bitmap/image layers | Both | ✅ |
| Freehand vector drawing | Alight Motion | ⬜ |
| Solid fills | Alight Motion | ⬜ |
| Gradient fills | Alight Motion | ⬜ |
| Strokes | Alight Motion | 🟡 text model supports stroke; generic vector stroke planned |
| Shadows | Alight Motion | 🟡 text model support; renderer parity expansion planned |
| Multiple borders | Alight Motion | ⬜ |
| Shape path editing | Motion design | ⬜ |
| Path animation | Motion design | ⬜ |
| Repeater / procedural shapes | Motion design | ⬜ |

## 5. Parenting, nulls, camera and anime/2.5D

| Capability | Reference apps | LocalCut |
| --- | --- | --- |
| Layer parenting | Alight Motion | ⬜ |
| Null/controller objects | Alight Motion | ⬜ |
| Camera objects | Alight Motion | ⬜ |
| Camera position/scale/rotation keyframes | Alight Motion | ⬜ |
| Multiple cameras/camera cuts | Alight Motion | ⬜ |
| Parent overlays to camera | Alight Motion | ⬜ |
| Parent camera to moving layer | Alight Motion | ⬜ |
| Simulated depth | Alight Motion | ⬜ |
| Focus blur/depth blur | Alight Motion | ⬜ |
| Fog/depth atmosphere | Alight Motion | ⬜ |
| 2.5D parallax scene | Anime target | ⬜ |
| Z-depth layers | Anime/motion target | ⬜ |
| Camera pan/zoom/shake presets | CapCut/Alight capabilities | ⬜ |
| Character rig helper | Anime target | ⬜ |
| Mouth/eye animation presets | Anime target | ⬜ |

## 6. Effects and color

| Capability | Reference apps | LocalCut |
| --- | --- | --- |
| Effect stack/registry | Alight Motion 100+ building blocks | ⬜ |
| Copy/paste effects | Alight Motion | ⬜ |
| Effect presets | Both | ⬜ |
| Brightness/contrast | Both | ⬜ |
| Saturation | Both | ⬜ |
| Exposure | CapCut | ⬜ |
| Temperature/tint | Color workflow | ⬜ |
| HSL adjustment | CapCut | ⬜ |
| RGB/luma curves | CapCut | ⬜ |
| LUT support | Professional color workflow | ⬜ |
| Sharpen/blur | Both | ⬜ |
| Glow | Motion design | ⬜ |
| Distortion/warp/ripple | Alight Motion effects | ⬜ |
| Noise/grain | Professional effects | ⬜ |
| Vignette | Color workflow | ⬜ |
| Velocity-based motion blur | Alight Motion | ⬜ |
| Motion blur controls for position/scale/angle | Alight Motion | ⬜ |
| Relight | CapCut | ☁️ optional ML/advanced module |
| Flicker removal | CapCut | ⬜ / optional analysis module |

## 7. Time and speed

| Capability | Reference apps | LocalCut |
| --- | --- | --- |
| Basic clip speed | CapCut | ⬜ |
| Speed ramp / speed curve | CapCut | ⬜ |
| Custom speed curves | CapCut | ⬜ |
| Reverse | CapCut-style editing | ⬜ |
| Freeze frame | CapCut-style editing | ⬜ |
| Frame hold | Professional NLE | ⬜ |
| Retiming marks | Alight Motion workflow | ⬜ |
| Time remapping by keyframes | Motion design | ⬜ |
| Frame interpolation | Advanced slow motion | ☁️/local-optional depending implementation |

## 8. Text, captions and typography

| Capability | Reference apps | LocalCut |
| --- | --- | --- |
| Text overlays | Both | ✅ |
| Text timing/z-index | Both | ✅ |
| Position/scale/rotation/opacity | Both | ✅ base |
| Font size/color | Both | ✅ model/base UI |
| Stroke/shadow model | Alight Motion | 🟡 |
| Custom fonts | Alight Motion | ⬜ |
| Text animation presets | Both | ⬜ |
| Per-character animation | Motion design | ⬜ |
| Auto captions | CapCut | ☁️ optional speech module |
| Manual caption editor | CapCut | ⬜ |
| Caption split/re-segmentation | CapCut | ⬜ |
| Caption style templates | CapCut | ⬜ |
| Transcript-based editing | CapCut-class workflow | ☁️ optional transcription module |
| Text-to-speech | CapCut | ☁️ optional provider/local TTS |

## 9. Audio

| Capability | Reference apps | LocalCut |
| --- | --- | --- |
| Multiple audio tracks | Both | ✅ |
| Waveform/cache | Professional audio editing | ✅ |
| Volume | Both | ✅ |
| Fade in/out | Both | ✅ |
| Track mute/solo | Both | ✅ |
| Audio extraction from video | Alight Motion / editors | ⬜ |
| Loudness normalization | CapCut | ⬜ |
| Noise reduction | CapCut | ⬜ / optional ML |
| Vocal isolation | CapCut | ☁️ optional ML |
| Voice enhancement | CapCut | ☁️ optional ML |
| EQ | Professional audio | ⬜ |
| Compressor/limiter | Professional audio | ⬜ |
| Pan/stereo controls | Professional audio | ⬜ |
| Audio effects | Both | ⬜ |
| Beat markers / beat detection | Creator workflow | ⬜ / optional analysis |
| Voice-over recording | Creator workflow | ⬜ |

## 10. Tracking, stabilization and reframing

| Capability | Reference apps | LocalCut |
| --- | --- | --- |
| Motion/object tracking | CapCut | ⬜ |
| Tracking position | CapCut | ⬜ |
| Tracking scale/distance | CapCut | ⬜ |
| Face/body/hand tracking targets | CapCut | ☁️ optional ML |
| Video stabilization | CapCut | ⬜ / optional local analysis |
| Auto reframe/aspect adaptation | CapCut | ☁️ optional analysis/ML |
| Track layer/camera to motion | Alight Motion parenting/camera | ⬜ |

## 11. Transitions

| Capability | Reference apps | LocalCut |
| --- | --- | --- |
| Transition model/timing | Both | ✅ |
| Fade to black | General editor | ✅ |
| Dissolve/crossfade visual | Both | 🟡 model exists; renderer planned |
| Wipes | Both | 🟡 model exists; renderer planned |
| Directional/shape transitions | Both | ⬜ |
| Blur/zoom/spin transitions | Creator editors | ⬜ |
| Custom transition presets | Motion workflow | ⬜ |
| Audio crossfade tied to transition | Professional NLE | ⬜ |

## 12. Preview, render and export

| Capability | Reference apps | LocalCut |
| --- | --- | --- |
| In-editor preview/player | Both | ✅ |
| Adaptive preview quality | Professional mobile editing | ✅ |
| Preview/export shared compiler | LocalCut architecture | ✅ |
| Local MP4 export | Both | ✅ |
| H.264 export | Both | ✅ |
| HEVC capability/fallback | Alight Motion | ✅ base |
| Resolution/FPS/bitrate profiles | Both | ✅ |
| Background/unattended export | Bridge requirement | 🟡 Bridge V1 in progress |
| GIF export | Alight Motion | ⬜ |
| PNG/image sequence export | Alight Motion | ⬜ |
| Alpha-capable export where codec supports | Motion workflow | ⬜ |
| Project package export/import | Alight Motion | ⬜ |
| Shareable editable project | Alight Motion | ⬜ |

## 13. Optional AI/automation layer

These features are separate from the offline editor core and must fail gracefully when no provider/model is configured.

| Capability | Reference apps | LocalCut |
| --- | --- | --- |
| Auto cutout/background removal | CapCut | ☁️ |
| Auto captions/transcription | CapCut | ☁️ |
| Text-to-speech | CapCut | ☁️ |
| AI stickers | CapCut | ☁️ |
| Vocal isolation/voice enhancement | CapCut | ☁️ |
| Auto reframe | CapCut | ☁️ |
| AI movement/camera motion assistance | CapCut | ☁️ |
| Generative image | CapCut ecosystem | ☁️ optional connector |
| Generative video | CapCut ecosystem | ☁️ optional connector; not core dependency |
| Lip sync/avatar workflows | CapCut ecosystem | ☁️ optional connector |
| EditScript planning from ChatGPT | LocalCut differentiator | ☁️ Bridge/Controller |

## 14. LocalCut-specific differentiators

The target is not a clone. LocalCut should exceed the references in areas that matter to this project:

1. Fully local editing/rendering as the default; no credit system for core editing.
2. Deterministic, serializable project model with preview/export parity.
3. Safe ChatGPT Bridge with revision preconditions, replay protection, idempotency and explicit pairing.
4. ChatGPT-readable project/timeline state and reversible remote commands.
5. Anime Mode built on ordinary editor primitives, not locked to one content style.
6. Scene recipes: 2.5D parallax, camera, rig helpers, eyes/mouth, shake, impact frames, speed lines and reusable animation presets.
7. Asset Inbox for securely transferring generated images/audio/video into LocalCut.
8. Offline-first behavior: Bridge/AI disabled must never break normal editing.

## Implementation order after Bridge V1

1. Asset Inbox.
2. Easing/curve engine + per-property keyframes.
3. Masks + blend modes + chroma key.
4. Camera 2.5D + Z depth + parenting/nulls.
5. Anime Mode presets/scene recipes.
6. Effect registry + core color/effect blocks + motion blur.
7. Speed/remapping/reverse/freeze.
8. Text/caption expansion + custom fonts.
9. Audio toolchain expansion.
10. Tracking/stabilization.
11. Vector/shapes/path animation.
12. Optional AI modules.
13. Additional export formats/project packages.

Every implementation category gets its own TDD plan and regression gate. Existing LocalCut behavior remains locked as a regression baseline.
