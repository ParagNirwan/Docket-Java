#!/usr/bin/env bash
# Checks a deploy can work before anything in the cluster changes. The backend uses the
# Recreate strategy, so its old pod stops before the new one starts: a missing secret or
# setting found any later than this is an outage.
#
# Usage: preflight.sh <gcp-project> <rendered-manifests.yaml>
set -euo pipefail

project="$1"
manifests="$2"
failed=0

# Every secret the pod mounts must exist, or the pod can't start.
secrets=$(grep -oE 'projects/[^/]+/secrets/[^/]+/versions/latest' "$manifests" | cut -d/ -f4 | sort -u)
if [ -z "$secrets" ]; then
  echo "::error::No secrets found in the manifests, which means this check is broken"
  exit 1
fi
for secret in $secrets; do
  if gcloud secrets versions describe latest --secret="$secret" --project="$project" >/dev/null; then
    echo "OK: secret $secret"
  else
    echo "::error::Secret $secret is missing, or this account can't read it"
    failed=1
  fi
done

# The prod profile has no defaults for these, so the backend refuses to start without them.
for key in APP_BASE_URL EMAIL_FROM; do
  if grep -qE "^[[:space:]]+${key}:[[:space:]]*[^[:space:]#]" "$manifests"; then
    echo "OK: setting $key"
  else
    echo "::error::$key is not set in k8s/base/configmap.yaml"
    failed=1
  fi
done

# A real provider without its key would also stop the backend from starting.
if grep -qE '^[[:space:]]+EMAIL_PROVIDER:[[:space:]]*"?resend"?' "$manifests" \
    && ! grep -q 'secrets/resend-api-key/' "$manifests"; then
  echo "::error::EMAIL_PROVIDER is resend, but resend-api-key isn't in secret-provider-class.yaml"
  failed=1
fi

if [ "$failed" -ne 0 ]; then
  echo "::error::Stopping before the deploy. Production has not been changed."
fi
exit "$failed"
