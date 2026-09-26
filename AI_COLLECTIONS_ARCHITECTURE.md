# Local AI Collections architecture

Status: design only, 2026-09-19. No model, background analysis, face database,
OCR, network service or AI metadata sync is implemented by this document.

## Product decision

Photos has local, useful collections rather than a cloud-gallery imitation:

- **People** — named people and unnamed face clusters.
- **Documents** — document-like photos, with a Receipt subtype when known.
- **Screenshots** — MediaStore screenshot paths, supplemented by local analysis.
- **Videos** — actual video media.

`Archive` is withdrawn from the product. It is a legacy Google Photos concept
and must be removed from the UI and private photo metadata in the implementation
slice that follows this design.

Documents is therefore an analysed Photos collection. It is not a copy of the
image and it does not move the original photo into `/sdcard/Drive/`.

## Non-negotiable boundary

1. Original photo/video bytes remain at their Android MediaStore locations.
2. Inference, embeddings, OCR text, review state and correction history remain
   on the user's device unless the user explicitly enables sync to a trusted
   paired device or local NAS.
3. There is no cloud AI API, image upload, hidden telemetry, account, or remote
   model download in the first implementation. The first quantized classifier
   is bundled in the APK so it works offline from first launch.
4. Face embeddings and OCR are sensitive metadata. Android Auto Backup remains
   disabled; the data stays in private app storage and never becomes a public
   MediaStore tag or EXIF rewrite.
5. AI suggests. It never moves, deletes, shares, hides or changes originals.

## Local analysis pipeline

The app detects new or changed MediaStore records while the user opens or
explicitly refreshes Photos. Background-library scanning is not part of the
first slice.

```
MediaStore record
  -> identity/fingerprint check
  -> compact local image classifier
  -> confidence policy
  -> local metadata + review queue when needed
  -> Documents / Screenshots / People / Videos collections and search
```

### Image type classification

Use one quantized, mobile-sized image classifier selected after an on-device
benchmark. Candidate runtimes are LiteRT/TensorFlow Lite and ONNX Runtime
Mobile; adding either requires a separate licence, APK-size, RAM, battery and
device-speed decision.

The initial label set is deliberately small:

| Type | Optional subtype | Collection/search use |
| --- | --- | --- |
| normal_photo | — | ordinary photo results |
| document | receipt | Documents; receipt search |
| screenshot | — | Screenshots, alongside source path evidence |
| meme | — | local search/filter only |
| other | chosen model label | local search/filter only |

The classifier records the best label and the complete compact score set needed
for review. It does not rewrite the photo's filename or folders.

### Faces

Faces are a separate pipeline, not a person-specific trained model:

```
local face detector -> local face embedding -> local person/cluster comparison
```

- A future detector and MobileFaceNet/ArcFace-style embedding model must both
  run locally and be quantized where that preserves usable accuracy.
- A named person owns several embeddings, collected only from images the user
  confirms. This covers pose, lighting and age variation without retraining a
  model per person.
- Unmatched embeddings form local clusters. A cluster is not silently named.
- The UI asks a bounded question such as `Is this Dimitris?`; a rejection is
  remembered and is not asked again for the same face/cluster evidence.
- People is opt-in before its first scan because face embeddings are biometric
  metadata. It must show an explanation and offer a local-data reset.

## Confidence and review queue

Every automatic result carries a model version, a confidence and a decision
policy version.

| Result | Behaviour |
| --- | --- |
| At or above its high-confidence threshold | Add the local classification automatically. |
| Below high confidence or close to the runner-up | Add one deduplicated review item; do not interrupt browsing. |
| User confirms/corrects | Save a verified correction and close the review item. |
| User rejects/ignores | Record the choice; do not ask the same question again. |

The review surface is a collection/card such as **Help organize 12 photos**.
It presents ranked options, for example Document 58%, Screenshot 31%, Photo
11%, with `Something else` available. It never presents repeated pop-ups while
the user is trying to browse photos.

Corrections improve the *local decision policy*: verified labels can override
model output, tune confidence thresholds after enough evidence, associate a
face with a person, and prevent repeated prompts. They do not claim to retrain
or personalise the underlying neural model in the first release.

## Private local data model

The eventual Room schema is private app data. Source MediaStore columns and
original bytes stay untouched.

```text
media_ai_record
  localMediaKey           device-local MediaStore identity
  sourceFingerprint       size, capture/modified time and content identity when available
  type, subtype
  classificationScores    compact local score map
  confidence, modelVersion, policyVersion
  userVerified
  reviewState             none | pending | answered | dismissed
  ocrText                 optional, local only

face_observation
  media_ai_record_id, faceBox, embedding, embeddingModelVersion
  person_id? , cluster_id? , confidence, userVerified

person
  person_id, displayName, createdLocally, localOnly

face_cluster
  cluster_id, representative face, reviewState

ai_correction
  media_ai_record_id, prior decision, user decision, answeredAt, policyVersion
```

For synced originals, a portable analysis record must identify its source using
an origin device/item identity plus a verified content identity where available.
It must never sync Android `content://` URIs, which are device-local.

Example record, stored locally before any future sync:

```json
{
  "type": "document",
  "subtype": "receipt",
  "faces": ["person_04"],
  "ocr": "LIDL...",
  "confidence": 0.91,
  "user_verified": true
}
```

## Sync contract (deferred implementation)

AI metadata is a separate, versioned catalog stream from original image bytes:

```text
original image | analysis metadata | face embeddings | people/clusters | corrections
```

This lets a trusted phone, tablet or local NAS reuse existing analysis instead
of rescanning every image. It does **not** authorize a peer to alter originals.

Classification labels and confidence may sync by default to a trusted paired
device. OCR text and face/people/clusters/embedding metadata are separate,
off-by-default consent categories. A user can enable either category for a
specific trusted peer later; pairing alone does not enable them.

Before this sync exists, the paired-device protocol must define:

1. explicit opt-in for sensitive OCR/face metadata;
2. schema/model-version compatibility and unknown-field preservation;
3. origin/content identity matching, conflict review and append-only correction
   events;
4. encryption/authentication through the already planned trusted-peer protocol;
5. per-device deletion/reset semantics that do not delete any original photo.

No AI metadata is sent by the current Android app.

## Delivery order and acceptance

1. **Data foundation:** replace Archive, add the private schema, source
   fingerprinting and a visible empty review queue. No AI dependency yet.
2. **Image classification:** benchmark one bundled quantized model on supported
   phones; add Document/Receipt/Screenshot review and corrections.
3. **Document understanding:** local OCR for confirmed documents and receipts;
   add controlled local search such as `Receipts from September`.
4. **People:** opt-in detector/embedding pipeline, clusters, names, correction
   memory and reset.
5. **Trusted metadata sync:** only after the broader paired sync protocol is
   implemented and a sensitive-metadata consent flow is accepted.

Each phase must prove on a physical phone that no original bytes were modified,
no network request was made for analysis, uncertain cases are batched, answered
cases do not recur, and a force-stop preserves private metadata.

## Decisions intentionally deferred

- LiteRT/TensorFlow Lite versus ONNX Runtime Mobile after a benchmark on the
  target phones. The selected first classifier will be bundled in the APK.
- Exact model files, licences, provenance, APK-size budget and update policy.
- OCR engine and language packs, including Greek receipt/document quality.
- Whether face embeddings and OCR metadata are encrypted at rest beyond Android
  private storage, and the recovery/reset design.
- Local NAS transport and the exact per-peer consent UI. Classification syncs
  by default; OCR and face metadata are separately opt-in.
