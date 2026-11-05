import { useEffect, useState } from "react";
import { Shield, ShieldCheck, ShieldAlert, Copy, Check, Loader2 } from "lucide-react";
import {
  get2faStatus,
  setup2fa,
  enable2fa,
  disable2fa,
  type TwoFactorSetupDTO,
} from "../services/twoFactorService";
import { getStoredToken } from "../lib/session";

export function TwoFactorSettings() {
  const token = getStoredToken();
  const [enabled, setEnabled] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Setup modal / flow state
  const [setupStep, setSetupStep] = useState<"idle" | "verify" | "backup_codes">("idle");
  const [setupData, setSetupData] = useState<TwoFactorSetupDTO | null>(null);
  const [verifyCode, setVerifyCode] = useState("");
  const [submittingCode, setSubmittingCode] = useState(false);
  const [backupCodes, setBackupCodes] = useState<string[]>([]);
  const [copiedCodes, setCopiedCodes] = useState(false);
  const [copiedSecret, setCopiedSecret] = useState(false);

  // Disable flow
  const [showDisableModal, setShowDisableModal] = useState(false);
  const [disablePassword, setDisablePassword] = useState("");
  const [disabling, setDisabling] = useState(false);

  useEffect(() => {
    async function checkStatus() {
      if (!token) return;
      try {
        setLoading(true);
        const res = await get2faStatus(token);
        setEnabled(res.enabled);
      } catch {
        // ignore
      } finally {
        setLoading(false);
      }
    }
    void checkStatus();
  }, [token]);

  async function startSetup() {
    if (!token) return;
    try {
      setError(null);
      setLoading(true);
      const data = await setup2fa(token);
      setSetupData(data);
      setSetupStep("verify");
      setVerifyCode("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to start 2FA setup");
    } finally {
      setLoading(false);
    }
  }

  async function handleVerify(e: React.FormEvent) {
    e.preventDefault();
    if (!token || !verifyCode.trim()) return;

    try {
      setSubmittingCode(true);
      setError(null);
      const res = await enable2fa(verifyCode.trim(), token);
      setBackupCodes(res.backupCodes);
      setSetupStep("backup_codes");
      setEnabled(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to verify 2FA code");
    } finally {
      setSubmittingCode(false);
    }
  }

  async function handleDisable(e: React.FormEvent) {
    e.preventDefault();
    if (!token || !disablePassword) return;

    try {
      setDisabling(true);
      setError(null);
      await disable2fa(disablePassword, token);
      setEnabled(false);
      setShowDisableModal(false);
      setDisablePassword("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to disable 2FA");
    } finally {
      setDisabling(false);
    }
  }

  async function copyBackupCodes() {
    await navigator.clipboard.writeText(backupCodes.join("\n"));
    setCopiedCodes(true);
    setTimeout(() => setCopiedCodes(false), 3000);
  }

  async function copySecret() {
    if (!setupData?.secret) return;
    await navigator.clipboard.writeText(setupData.secret);
    setCopiedSecret(true);
    setTimeout(() => setCopiedSecret(false), 3000);
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h3 className="text-xs font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500">
          Two-Factor Authentication (2FA)
        </h3>
        {enabled && (
          <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-emerald-600 dark:text-emerald-400">
            <ShieldCheck className="w-3.5 h-3.5" />
            Active
          </span>
        )}
      </div>

      <div className="p-4 rounded-xl border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900/60 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div
            className={`w-9 h-9 rounded-lg flex items-center justify-center shrink-0 ${
              enabled
                ? "bg-emerald-50 dark:bg-emerald-950/60 text-emerald-600 dark:text-emerald-400"
                : "bg-gray-100 dark:bg-gray-800 text-gray-500"
            }`}
          >
            <Shield className="w-5 h-5" />
          </div>
          <div>
            <div className="text-sm font-medium text-gray-900 dark:text-white">
              {enabled ? "Authenticator App (TOTP)" : "Protect Your Account"}
            </div>
            <div className="text-xs text-gray-500 dark:text-gray-400">
              {enabled
                ? "Require a 6-digit code or backup code when signing in"
                : "Add an extra layer of security with Google Authenticator or 1Password"}
            </div>
          </div>
        </div>

        {loading ? (
          <Loader2 className="w-4 h-4 animate-spin text-gray-400" />
        ) : enabled ? (
          <button
            type="button"
            onClick={() => {
              setError(null);
              setDisablePassword("");
              setShowDisableModal(true);
            }}
            className="px-3.5 py-1.5 text-xs font-semibold text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-950/40 rounded-full transition-colors border border-red-200 dark:border-red-900/60 self-start sm:self-auto"
          >
            Disable 2FA
          </button>
        ) : (
          <button
            type="button"
            onClick={startSetup}
            className="px-4 py-1.5 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-700 rounded-full transition-colors shadow-xs self-start sm:self-auto"
          >
            Enable 2FA
          </button>
        )}
      </div>

      {error && (
        <div role="alert" className="text-xs text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-950/30 p-2.5 rounded-xl border border-red-200 dark:border-red-900/60">
          {error}
        </div>
      )}

      {/* Setup Step Modal: Scan QR + Enter Code */}
      {setupStep === "verify" && setupData && (
        <div className="p-4 rounded-xl border border-indigo-200 dark:border-indigo-800 bg-indigo-50/20 dark:bg-indigo-950/20 space-y-4">
          <div className="text-xs font-bold text-gray-900 dark:text-white">
            1. Scan QR code in your authenticator app
          </div>
          <div className="flex flex-col sm:flex-row items-center gap-4 bg-white dark:bg-gray-900 p-3 rounded-xl border border-gray-200 dark:border-gray-800">
            {/* Display QR code using quickchart api or fallback */}
            <img
              src={`https://api.qrserver.com/v1/create-qr-code/?size=140x140&data=${encodeURIComponent(setupData.qrUri)}`}
              alt="2FA QR Code"
              className="w-32 h-32 rounded-lg border border-gray-200 dark:border-gray-700 bg-white p-1 shrink-0"
            />
            <div className="space-y-1 text-xs text-gray-600 dark:text-gray-300 min-w-0">
              <p className="font-medium text-gray-900 dark:text-white">Can't scan the QR code?</p>
              <p className="text-[11px] text-gray-500">Enter this secret key manually into your app:</p>
              <div className="flex items-center gap-2 pt-1">
                <code className="text-xs font-mono font-bold bg-gray-100 dark:bg-gray-800 px-2 py-1 rounded-md tracking-wider text-indigo-600 dark:text-indigo-400">
                  {setupData.secret}
                </code>
                <button
                  type="button"
                  onClick={copySecret}
                  className="p-1 rounded text-gray-400 hover:text-gray-700 dark:hover:text-gray-200"
                >
                  {copiedSecret ? <Check className="w-3.5 h-3.5 text-emerald-600" /> : <Copy className="w-3.5 h-3.5" />}
                </button>
              </div>
            </div>
          </div>

          <form onSubmit={handleVerify} className="space-y-3">
            <div className="text-xs font-bold text-gray-900 dark:text-white">
              2. Enter the 6-digit verification code from your app
            </div>
            <div className="flex gap-2">
              <input
                type="text"
                autoFocus
                placeholder="123456"
                maxLength={6}
                value={verifyCode}
                onChange={(e) => setVerifyCode(e.target.value.replace(/\D/g, ""))}
                className="w-36 text-center tracking-widest font-mono text-base font-bold rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 px-3 py-2 text-gray-900 dark:text-white focus:border-indigo-500 focus:outline-none"
              />
              <button
                type="submit"
                disabled={verifyCode.length !== 6 || submittingCode}
                className="px-4 py-2 text-xs font-semibold rounded-full bg-indigo-600 hover:bg-indigo-700 text-white shadow-xs disabled:opacity-50"
              >
                {submittingCode ? "Verifying..." : "Verify & Enable"}
              </button>
              <button
                type="button"
                onClick={() => setSetupStep("idle")}
                className="px-3 py-2 text-xs text-gray-500 hover:text-gray-700"
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Setup Step 2: Show Backup Codes */}
      {setupStep === "backup_codes" && (
        <div className="p-4 rounded-xl border border-emerald-200 dark:border-emerald-800/80 bg-emerald-50/20 dark:bg-emerald-950/20 space-y-3">
          <div className="flex items-center gap-2 text-xs font-bold text-emerald-800 dark:text-emerald-300">
            <ShieldCheck className="w-4 h-4 text-emerald-600" />
            2FA is now active! Save your backup codes:
          </div>
          <p className="text-xs text-gray-600 dark:text-gray-300">
            If you ever lose access to your authenticator device, each of these 8 single-use codes can be used once to sign in. Store them safely.
          </p>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 p-3 bg-white dark:bg-gray-900 rounded-xl border border-gray-200 dark:border-gray-800 font-mono text-xs font-bold text-center">
            {backupCodes.map((code, idx) => (
              <div key={idx} className="p-1.5 rounded-lg bg-gray-50 dark:bg-gray-800 text-gray-800 dark:text-gray-200">
                {code}
              </div>
            ))}
          </div>
          <div className="flex items-center justify-between pt-1">
            <button
              type="button"
              onClick={copyBackupCodes}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-full border border-gray-200 dark:border-gray-700 hover:bg-white dark:hover:bg-gray-800"
            >
              {copiedCodes ? <Check className="w-3.5 h-3.5 text-emerald-600" /> : <Copy className="w-3.5 h-3.5" />}
              {copiedCodes ? "Copied!" : "Copy All Codes"}
            </button>
            <button
              type="button"
              onClick={() => setSetupStep("idle")}
              className="px-4 py-1.5 text-xs font-semibold rounded-full bg-emerald-600 hover:bg-emerald-700 text-white"
            >
              I Saved My Codes
            </button>
          </div>
        </div>
      )}

      {/* Disable 2FA Modal */}
      {showDisableModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-xs">
          <form
            onSubmit={handleDisable}
            className="w-full max-w-sm bg-white dark:bg-gray-900 p-5 rounded-2xl border border-gray-200 dark:border-gray-800 shadow-2xl space-y-4"
          >
            <div className="flex items-center gap-2 text-sm font-bold text-red-600 dark:text-red-400">
              <ShieldAlert className="w-5 h-5" />
              Disable Two-Factor Authentication
            </div>
            <p className="text-xs text-gray-500 dark:text-gray-400">
              Enter your current account password to confirm disabling 2FA.
            </p>
            <input
              type="password"
              autoFocus
              placeholder="Your password"
              value={disablePassword}
              onChange={(e) => setDisablePassword(e.target.value)}
              className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50/50 dark:bg-gray-800/50 px-3.5 py-2 text-xs text-gray-900 dark:text-white focus:outline-none focus:ring-1 focus:ring-indigo-500"
            />
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowDisableModal(false)}
                className="px-3.5 py-1.5 text-xs font-medium rounded-full border border-gray-200 dark:border-gray-700"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={!disablePassword || disabling}
                className="px-4 py-1.5 text-xs font-semibold rounded-full bg-red-600 hover:bg-red-700 text-white disabled:opacity-50"
              >
                {disabling ? "Disabling..." : "Disable 2FA"}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}

