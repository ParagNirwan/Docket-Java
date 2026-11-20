#!/usr/bin/env bash
# Creates the per-region KMS keys and the US bucket for data residency, and sets each
# bucket's default key. Review before running; nothing here runs automatically.
#   PROJECT=docket-java ./scripts/setup-cmek.sh
set -euo pipefail

PROJECT="${PROJECT:?set PROJECT}"
EU_LOCATION="${EU_LOCATION:-europe-west3}"
US_LOCATION="${US_LOCATION:-us-central1}"
EU_BUCKET="${EU_BUCKET:-docket-java-uploads}"
US_BUCKET="${US_BUCKET:-docket-java-uploads-us}"
KEYRING="${KEYRING:-docket-storage}"
KEY="${KEY:-documents}"

GCS_SA="$(gcloud storage service-agent --project "$PROJECT")"

for pair in "eu:$EU_LOCATION:$EU_BUCKET" "us:$US_LOCATION:$US_BUCKET"; do
  IFS=: read -r region location bucket <<<"$pair"
  echo "== $region ($location)"
  gcloud kms keyrings describe "$KEYRING" --location "$location" --project "$PROJECT" >/dev/null 2>&1 \
    || gcloud kms keyrings create "$KEYRING" --location "$location" --project "$PROJECT"
  gcloud kms keys describe "$KEY" --keyring "$KEYRING" --location "$location" --project "$PROJECT" >/dev/null 2>&1 \
    || gcloud kms keys create "$KEY" --keyring "$KEYRING" --location "$location" --project "$PROJECT" \
         --purpose encryption --rotation-period 90d \
         --next-rotation-time "$(date -u -d '+90 days' +%Y-%m-%dT%H:%M:%SZ)"

  KEY_NAME="projects/$PROJECT/locations/$location/keyRings/$KEYRING/cryptoKeys/$KEY"
  # The Cloud Storage service agent encrypts/decrypts on our behalf.
  gcloud kms keys add-iam-policy-binding "$KEY" --keyring "$KEYRING" --location "$location" \
    --project "$PROJECT" --member "serviceAccount:$GCS_SA" --role roles/cloudkms.cryptoKeyEncrypterDecrypter

  gcloud storage buckets describe "gs://$bucket" --project "$PROJECT" >/dev/null 2>&1 \
    || gcloud storage buckets create "gs://$bucket" --project "$PROJECT" --location "$location" \
         --uniform-bucket-level-access --public-access-prevention
  gcloud storage buckets update "gs://$bucket" --default-encryption-key "$KEY_NAME" --versioning
  echo "GCS_KMS_KEY_${region^^}=$KEY_NAME"
done

echo "Set GCS_BUCKET_US=$US_BUCKET and the GCS_KMS_KEY_* values above on the backend."
echo "Re-apply lifecycle rules to the new bucket: scripts/apply-gcs-lifecycle.sh (bucket $US_BUCKET)."
