# Data residency and customer-managed encryption (CMEK)

## What it does

- **Region per organization.** Chosen when the organization is created (EU or US, default EU)
  and stored in `tenants.data_region` (migration V27). The JPA mapping is `updatable = false`
  and there's no API to change it: moving data between jurisdictions is an operational project.
- **Routing.** New objects are named `eu/<tenant>/<yyyy>/<m>/<file>` or `us/…`.
  `GcsStorageService` sends each object to its region's bucket. Names written before V27 have no
  prefix and stay in the EU bucket, so no data migration is needed.
- **Fail closed.** If a US organization uploads and `GCS_BUCKET_US` isn't set, the upload fails.
  It is never written to the EU bucket instead.
- **Envelope encryption (CMEK).** Each object is written with its region's Cloud KMS key
  (`kmsKeyName`). GCS encrypts the object with a data key and KMS wraps that data key with your key,
  so disabling or destroying the KMS key makes the region's documents unreadable. Keys rotate every
  90 days. Old versions keep decrypting old objects.

## Setup

```bash
PROJECT=docket-java ./scripts/setup-cmek.sh
```

This creates a key ring and key in each region, grants the Cloud Storage service agent
`cloudkms.cryptoKeyEncrypterDecrypter`, creates the US bucket (uniform access, public access
prevention, versioning), and sets each bucket's default key. Then set on the backend:

| Variable | Example |
| :--- | :--- |
| `GCS_BUCKET_US` | `docket-java-uploads-us` |
| `GCS_KMS_KEY_EU` | `projects/docket-java/locations/europe-west3/keyRings/docket-storage/cryptoKeys/documents` |
| `GCS_KMS_KEY_US` | `projects/docket-java/locations/us-central1/keyRings/docket-storage/cryptoKeys/documents` |

Apply the lifecycle rules (`scripts/apply-gcs-lifecycle.sh`) to the US bucket too.

Objects written before the EU key was set stay Google-encrypted. To re-encrypt them, run
`gcloud storage objects update gs://docket-java-uploads/** --encryption-key=<EU key>`.

## Scope and limits

- Covers **document files** (uploads and versions). The **database** (metadata, extracted text,
  embeddings, audit log) is still the single EU Cloud SQL instance. For strict US residency of
  metadata you'd need a second instance and tenant-level routing.
- **AI processing** (Vertex AI embeddings, Gemini) uses the configured `docket.ai` location.
  US organizations' text is processed there unless a per-region AI endpoint is added.
- Local development (no `gcp` profile) writes `eu/…` and `us/…` subdirectories under `uploads/`
  without encryption.
