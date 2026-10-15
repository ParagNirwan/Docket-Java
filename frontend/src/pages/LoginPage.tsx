import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const navigate = useNavigate();
  const [error, setError] = useState("");
  const { login } = useAuth();

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError("");

    const apiUrl = import.meta.env.VITE_API_URL || "/api";
    const response = await fetch(`${apiUrl}/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });

    if (!response.ok) {
      setError("Login failed. Please check your credentials.");
      return;
    }

    const data = await response.json();
    login(data.token);

    const tenantsResp = await fetch(`${apiUrl}/users/list-tenants`, {
      method: "GET",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${data.token}`,
      },
    });

    const tenants = await tenantsResp.json();

    if (!tenants || tenants.length === 0) {
      navigate("/create-first-tenant");
      return;
    }

    navigate("/tenants");
  }

  return (
    <div className="w-full px-4 py-8 sm:px-6 lg:px-8">
      <div className="w-full max-w-md mx-auto">
        <h1 className="text-2xl font-semibold mb-6 text-[var(--color-ink)] text-center">
          Log in
        </h1>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div>
            <label className="block text-sm font-medium text-[var(--color-muted)] mb-1">
              Email
            </label>
            <input
              type="email"
              placeholder="email@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full border border-[var(--color-line)] rounded-md px-3 py-2 bg-[var(--color-paper)] text-[var(--color-ink)] focus:outline-none focus:ring-2 focus:ring-[var(--color-vault)]"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-[var(--color-muted)] mb-1">
              Password
            </label>
            <input
              type="password"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full border border-[var(--color-line)] rounded-md px-3 py-2 bg-[var(--color-paper)] text-[var(--color-ink)] focus:outline-none focus:ring-2 focus:ring-[var(--color-vault)]"
            />
          </div>
          {error && <p className="text-[var(--color-danger)] text-sm mt-1">{error}</p>}
          <button
            type="submit"
            className="bg-[var(--color-vault)] text-[var(--color-paper)] rounded-md px-4 py-2 hover:bg-[var(--color-vault-hover)] transition-colors duration-150 font-medium"
          >
            Log in
          </button>
        </form>
        <p className="mt-6 text-center text-sm text-[var(--color-muted)]">
          No account yet?{" "}
          <Link
            to="/register"
            className="text-[var(--color-vault)] hover:underline font-medium"
          >
            Register now.
          </Link>
        </p>
      </div>
    </div>
  );
}
