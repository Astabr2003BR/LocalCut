# LocalCut Studio + LocalCut Bridge — Design V1

Date: 2026-10-19
Status: Draft for user review
Scope: Android-first, personal-use editor with autonomous editing and bidirectional ChatGPT control

## 1. Product goal

Turn LocalCut from the current V0 editor into a mobile editor that covers the main capability classes of CapCut and Alight Motion while remaining an original implementation.

The target is capability parity by category, not copying proprietary code, branded assets, templates, effect packs, model weights, or exact UI layouts.

The product must support two modes:

1. Manual editing: the user edits directly in LocalCut.
2. Autonomous editing: the user gives a goal and LocalCut builds or modifies the project automatically, with a reversible edit plan.

A third layer, LocalCut Bridge, connects ChatGPT and LocalCut bidirectionally so ChatGPT can inspect projects, propose edits, execute supported editing operations, inspect errors, obtain preview frames, and request exports.

## 2. Current baseline

The current Android V0 already has:

- visual and audio import
- preview
- sequential visual/audio timeline
- trim
- split
- delete
- move
- undo/redo
- autosave
- final preview render
- MP4 export through Media3
- overlap validation
- stable debug signing and incrementing build version

This baseline remains functional during migration. New features must be added behind stable project-model contracts rather than replacing working flows all at once.

## 3. Reference capability classes

### 3.1 CapCut-class editing

LocalCut should implement equivalent categories of functionality:

- multi-track video and audio timeline
- overlays
- trim, split, delete, duplicate, ripple delete
- crop, rotate, scale, position, opacity
- speed changes and speed curves
- freeze frame
- reverse video
- transitions
- filters and color adjustment
- text layers and custom fonts
- captions/subtitles
- stickers/image overlays
- chroma key
- masks
- blend modes
- audio extraction
- audio fades
- volume envelopes
- basic audio cleanup
- reusable presets/templates created by the user
- H.264/HEVC export options where device codec support allows
- project aspect ratio presets and custom canvas sizes

### 3.2 Alight Motion-class motion design

LocalCut should implement equivalent categories of functionality:

- multiple visual/audio/graphics layers
- vector shapes
- bitmap/image layers
- text layers
- keyframes on transform and effect parameters
- position/scale/rotation/opacity keyframes
- cubic-Bezier easing and graph editor
- layer grouping
- masking
- parent-child layer relationships
- null/controller objects
- camera objects
- X/Y/Z transform model for 2.5D scenes
- depth-aware camera motion
- focus blur/fog later in the 2.5D milestone
- motion blur
- effect stack per layer
- reusable elements/presets
- custom font support
- project package import/export for backup

## 4. Project model V2

The existing sequential track model evolves into a composition graph while preserving backwards compatibility.

Core entities:

- Project
- Composition
- Track
- Layer
- Clip
- Asset
- EffectStack
- EffectInstance
- KeyframeTrack
- Keyframe
- Transition
- Group
- ParentLink
- Camera
- NullObject
- Mask
- AutomationEnvelope

Every editable property that may be animated uses a typed Animatable<T> abstraction:

- constant value, or
- ordered keyframe list

Supported initial types:

- Float
- Vec2
- Vec3
- Color
- Angle
- Boolean/enum where interpolation is not applicable

Timeline time remains integer microseconds.

## 5. Timeline architecture

The V2 timeline supports concurrent layers rather than only one sequential visual track.

Rules:

- visual layers may overlap
- audio layers may overlap
- overlap is invalid only inside tracks configured as exclusive/sequential
- clips have z-order/layer order
- ripple operations are explicit
- snapping is optional
- trim/move operations use collision policies defined by track type
- undo/redo stores atomic editing transactions

The UI remains mobile-first:

- pinch timeline zoom
- horizontal scroll
- vertical layer scroll
- large touch handles
- playhead-centered split
- contextual bottom toolbar
- inspector for selected layer/clip

## 6. Rendering pipeline

### 6.1 Preview

Two preview paths:

1. Fast interactive preview for editing.
2. High-quality render path for final preview/export.

Preview may reduce resolution or skip expensive effects to sustain interaction on mobile.

### 6.2 Export

Initial targets:

- MP4 H.264 + AAC
- MP4 HEVC + AAC where codec support is verified

Later targets:

- GIF
- PNG sequence
- image export

Every export is temp-first and committed only after success. Cancellation and failure must clean temporary output.

## 7. Effect system

Effects must not be hard-coded directly into screen logic.

Define an EffectDescriptor registry:

- stable effect id
- display name
- parameter schema
- parameter ranges/defaults
- keyframe support
- preview renderer implementation
- export renderer implementation
- GPU/CPU capability requirements

Initial effect families:

- brightness/contrast
- saturation/vibrance
- temperature/tint
- exposure/highlights/shadows
- blur
- sharpen
- vignette
- glow
- shadow
- transform
- crop
- mask
- chroma key
- shake
- oscillation
- RGB/chromatic split
- grain/noise
- simple distortion

This registry lets new effects be added without redesigning the project model.

## 8. Keyframe and motion engine

Keyframes are first-class project data, not UI-only state.

V1 keyframed properties:

- position
- scale
- rotation
- opacity
- volume
- effect parameters marked animatable

Interpolation:

- hold
- linear
- ease-in
- ease-out
- ease-in-out
- cubic Bezier custom curve

Later:

- velocity graph
- value graph
- per-axis curves
- path motion
- motion blur from transform velocity

## 9. Text, captions, and graphics

Text layer properties:

- content
- font
- size
- weight
- alignment
- line spacing
- letter spacing
- fill
- stroke
- shadow
- background
- transform
- opacity
- keyframes

Graphics:

- rectangles
- circles
- stars
- polygons
- freehand/vector path
- gradient/solid/media fills

Captions:

- manual subtitle track first
- SRT import/export
- later automatic transcription as optional AI module

## 10. Audio subsystem

Core features:

- multi-track audio
- trim/split/move
- detach/extract video audio
- volume
- fade in/out
- gain envelope keyframes
- mute/solo
- waveform generation

Later:

- speed/pitch controls
- normalization
- noise reduction
- beat detection
- silence detection
- automatic beat markers

## 11. Autonomous Editor

The autonomous editor must not let an AI model mutate project files arbitrarily.

Use a deterministic EditScript pipeline:

User goal -> Planner -> EditScript -> Validator -> Transaction Executor -> Preview Validator -> Commit/Rollback

### 11.1 EditScript

JSON-like commands with stable schemas, examples:

- import_media
- add_clip
- add_audio
- move_clip
- trim_clip
- split_clip
- delete_clip
- duplicate_clip
- set_transform
- set_speed
- set_volume
- add_text
- add_transition
- add_effect
- set_effect_param
- add_keyframe
- add_mask
- create_group
- set_parent
- add_camera
- export

Every script has:

- script id
- project revision precondition
- ordered commands
- optional rationale text
- expected duration
- validation requirements

### 11.2 Autonomous editing examples

User: "Faça um vídeo de 30 segundos com essas 8 imagens, 3 a 4 segundos cada, zoom suave, música baixa e transições simples."

Planner produces a reversible EditScript.

User: "Faça esse vídeo parecer um anime trailer, com movimentos lentos, cortes no ritmo e títulos."

Planner may use only locally available capabilities unless an external generation service is explicitly configured.

### 11.3 Autonomous safety

Default policy for personal use:

- non-destructive source media
- project revision check before write
- one transaction per autonomous operation
- automatic checkpoint before applying script
- one-tap undo of entire AI operation
- no silent deletion of source files
- export never overwrites source media

## 12. LocalCut Bridge — architecture C

Bidirectional architecture:

ChatGPT/Plugin <-> LocalCut Bridge Relay <-> LocalCut Android Agent <-> Editor Engine

The Android app keeps an outbound authenticated connection to the relay. No inbound port is opened on the phone.

### 12.1 Why a relay

A phone is often behind carrier NAT and cannot safely expose a stable public endpoint. The relay gives the plugin a stable HTTPS/WSS endpoint while the phone establishes the connection outward.

### 12.2 Pairing

One-time pairing flow:

1. LocalCut creates a device key pair.
2. Private key remains in Android Keystore.
3. App displays short pairing code or QR.
4. Bridge account/plugin binds that device id to the user.
5. Relay stores only public identity/session metadata and encrypted command payloads where practical.

### 12.3 Connection states

- offline
- connecting
- paired
- online
- busy
- exporting
- error

LocalCut remains fully usable offline.

## 13. Bridge tool API

Read tools:

- get_device_status
- get_project_state
- get_timeline_summary
- list_tracks
- list_clips
- get_selected_clip
- get_playhead
- get_export_status
- get_errors
- get_preview_frame

Write tools:

- seek
- select_clip
- import_media_reference
- add_clip
- move_clip
- trim_clip
- split_clip
- delete_clip
- duplicate_clip
- set_transform
- set_volume
- set_speed
- add_text
- add_effect
- set_effect_param
- add_keyframe
- add_transition
- undo
- redo
- apply_edit_script
- start_export
- cancel_export

Long-running jobs return job ids and progress events.

## 14. ChatGPT integration

Preferred integration is a private LocalCut Controller plugin backed by the relay/tool API.

The plugin must never contain the device private key.

The plugin sees only the LocalCut tool surface, not arbitrary Android APIs.

Example conversation:

User: "Veja meu projeto atual."
Assistant -> get_project_state

User: "Corte os 2 primeiros segundos, abaixe a música e coloque uma transição entre as cenas."
Assistant -> prepare EditScript -> apply_edit_script

LocalCut returns project revision + command results + preview status.

Assistant may then request get_preview_frame for visual verification.

## 15. Autonomous mode inside the APK

LocalCut gets an "Auto Edit" screen.

Inputs:

- instruction text
- selected media set
- target duration
- aspect ratio
- style preset
- optional music

Outputs:

- proposed edit plan
- estimated result duration
- Apply button
- preview
- Undo AI edit

The planner can be provided by ChatGPT Bridge or by another configured model provider. The editor core itself remains provider-independent.

## 16. Anime creation path

LocalCut can create anime-style edited videos in two different senses.

### A. Anime assembly/animation from supplied media

Supported locally over time:

- still images as scenes
- camera pan/zoom
- parallax layers
- keyframed character/background motion
- motion blur
- text/dialogue
- subtitles
- sound effects
- music
- transitions
- color/effects
- 2.5D camera

This can be done without an external generative model once the animation engine is complete.

### B. Generate new anime scenes from prompts

This is a separate optional provider layer. LocalCut sends a generation request and imports the resulting image/video asset. Provider APIs, costs, limits, and licenses remain separate from the editor.

The project model must not depend on any specific provider.

## 17. Performance strategy for Android

- render preview at adaptive resolution
- cache thumbnails/waveforms
- background decode/render threads
- bounded memory caches
- low-memory recovery
- effect quality tiers
- proxy media support later
- avoid blocking main thread
- lifecycle-safe Media3 resources
- autosave serialization

## 18. Reliability and diagnostics

Add a Debug Center available only in development/personal builds:

- app version/build
- project revision
- renderer state
- recent non-sensitive errors
- export job state
- bridge state
- copy diagnostics
- optional local crash report persistence

Bridge can expose sanitized diagnostics through get_errors.

Do not expose raw file-system paths or access tokens in diagnostics.

## 19. Security

- Android Keystore device identity
- TLS for relay traffic
- short-lived access tokens
- nonce/replay protection for write commands
- project revision preconditions
- per-command authorization
- explicit capability allowlist
- no arbitrary shell execution
- no arbitrary file-system tool
- no embedded OpenAI secret in APK
- source files remain immutable

## 20. Implementation milestones

### Milestone 0 — stabilize V0

- finish current timeline collision behavior
- runtime import/preview/export validation on real device
- diagnostic error capture

### Milestone 1 — editor core V2

- multi-track/layer model
- overlays
- ripple operations
- timeline zoom/scroll improvements
- audio waveform
- project migration V1 -> V2

### Milestone 2 — motion engine

- keyframes
- easing curves
- transform animation
- opacity/volume animation
- effect registry
- initial effect set

### Milestone 3 — creative tools

- text
- vector shapes
- masks
- blend modes
- transitions
- chroma key
- filters/color
- speed/reverse/freeze

### Milestone 4 — Alight-style hierarchy and 2.5D

- groups
- parenting
- null objects
- camera objects
- Z axis
- parallax workflow
- motion blur

### Milestone 5 — Autonomous Editor local execution

- EditScript schema
- validator
- transaction executor
- checkpoint/rollback
- Auto Edit screen
- deterministic templates

### Milestone 6 — LocalCut Bridge

- Android bridge agent
- relay
- pairing
- read tools
- write tools
- preview frame transfer
- progress/events

### Milestone 7 — ChatGPT Controller

- private LocalCut Controller plugin
- tool mapping
- project inspection
- scripted editing
- preview verification loop
- export orchestration

### Milestone 8 — optional AI media modules

- transcription/captions
- scene detection
- beat/silence analysis
- background removal
- optional image/video generation providers

## 21. Acceptance criteria for autonomous editing V1

Given a project and local media, a single user instruction must be able to:

1. inspect available media
2. create or update a project
3. arrange clips in sequence
4. trim/split/move clips
5. set volume
6. add simple text
7. add simple transitions/effects
8. generate a preview
9. validate the timeline
10. export MP4
11. roll back the entire automated edit with one undo/checkpoint restore

No user source file may be modified or deleted.

## 22. Reference research

Official reference documentation used to define capability classes:

- Alight Motion: https://alightmotion.com/
- Alight Motion feature guides: https://support.alightmotion.com/hc/en-us/sections/10536976447377-Feature-Guides
- Alight Motion camera objects: https://support.alightmotion.com/hc/en-us/articles/10536993608977-Camera-Objects-Guide
- Alight Motion parenting/null objects: https://support.alightmotion.com/hc/en-us/articles/10536997444369-Layer-Parenting-and-Null-Objects
- CapCut help: https://www.capcut.com/help
- CapCut keyframes: https://www.capcut.com/tools/keyframe-animation
- CapCut transitions: https://www.capcut.com/help/transitions-in-capcut

## 23. Explicit non-goals

- copying CapCut or Alight Motion source code
- extracting/repackaging proprietary templates/assets/effects
- cloning trademarks or exact branded UI
- bypassing paid/protected functionality from other apps
- storing external provider secrets directly in the APK

LocalCut implements its own equivalent editing capabilities and user-owned presets/effects.
