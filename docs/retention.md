# Retention, legal hold and trash

What protects a document, how the pieces fit together, and what to check when something looks wrong.

## How a document is protected

`RetentionGuard.assertMutable` is the one check for deleting, purging, moving and renaming. New code that changes or removes a document, such as replacing its content (#64), must call it. Strongest first:

1. **Legal hold**: nobody can change the document until every hold covering it is released.
2. **Compliance**: nobody can until `retain_until`.
3. **Governance**: an Owner can, with `override=true` and a reason. That's written to the audit log as `LOCK_OVERRIDDEN`, and the lock is removed if the document is moved or deleted.
4. **Archived**: read-only, though it can be deleted once nothing else protects it. Unarchiving needs the lock to be over.

Behind that, the `documents_retention_no_delete` trigger refuses to delete a row that's locked or held. It also stops the `ON DELETE CASCADE` from folders and tenants, so anything that deletes an organization has to deal with its locked documents first. It's a backstop: services check first so people get a useful error.

Permissions and the frontend's disabled buttons only decide what to offer. A 423 can still arrive, if a hold lands between loading a page and clicking.

## Policies

- A policy sits on a folder, usually a tray. With `applies_to_subfolders` it covers everything below.
- `retain_until` only ever moves later, and Compliance is never downgraded while a lock is active. So when several policies cover a document, the strictest wins.
- `ARCHIVED_AT` counts from when the document was archived or filed under the policy (`retention_clock_at`). `UPLOADED_AT` counts from `created_at`. **An `UPLOADED_AT` policy with `AUTO_DELETE` will send documents that are already old to the Trash on the next run.**
- Creating or changing a policy updates existing documents straight away. Removing a Governance policy doesn't unlock anything: documents keep their dates.
- A Compliance policy can't be removed, shortened, downgraded, re-clocked or narrowed. A folder containing one can't be deleted.

The rule for a date lives in two places that must stay in step: `RetentionService.applyOnEntry` for one document, and `DocumentRepository.applyRetentionPolicy` for many.

## Trash

Deleting sets `folder_id` to the organization's Trash tray and remembers `original_folder_id`. Keeping deleted documents in a real, admin-only tray means the normal access check covers them, and deleting their old folder can't cascade to them. If the folder is gone, they restore to the Inbox.

Search and AI answers only read `ACTIVE` documents. A deleted document keeps its chunks, so restoring it needs no re-indexing.

Purging deletes the row first and the stored file after the commit (`StoredFileCleanup`). If the file delete fails, the file is orphaned and an error is logged, but no document ever points at a missing file.

## Legal holds

`legal_holds` and `legal_hold_documents` say what each hold covers. `documents.legal_hold` is derived from them, and is what the guard and the trigger read. Applying and releasing take turns under an advisory lock, so a release can't clear the flag on a document another hold has just covered.

A hold covers documents in the Trash too, and a folder hold includes those that came from that folder. A search hold covers what matches when it's applied, not documents added later.

## Scheduled jobs

| Job | Default (server time) | What it does |
| :--- | :--- | :--- |
| Retention expiry | 02:30 | Warns 30 days ahead, then notifies or moves to the Trash as the policy says. Skips held documents, and picks them up after release |
| Trash purge | 04:00 | Removes documents past the organization's trash period, unless locked |
| Daily digest | 07:00 | One email to each person who chose the digest |

Each takes a 20-hour lease in `job_locks`, so it runs on one pod per schedule. To run one again right away, for example after an outage:

```sql
DELETE FROM job_locks WHERE name = 'retention-expiry'; -- or 'trash-purge', 'notification-digest'
```

It then runs at its next scheduled time. They're safe to repeat: what's been handled is recorded on each document.

Automatic actions are in the audit log with the actor type `SYSTEM`.

## Not covered yet

- The lock lives in Docket's database. Someone with direct access to the storage bucket can still delete files until GCS object locks arrive (#54).
- There's no way to delete an organization yet. When there is, it must deal with locked documents, as the trigger will refuse.
- Notifications for shared documents and storage quotas arrive with those features (#71, #74).
