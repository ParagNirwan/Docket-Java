import { useState, useEffect, useRef } from "react";
import { Link, useNavigate, useLocation, useParams } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import {
  LogoIcon,
  MenuIcon,
  CloseIcon,
  SettingsIcon,
  LogOutIcon,
  BuildingIcon,
  PlusIcon,
  ShieldIcon,
  ChevronRightIcon,
} from "./icons";
import { Check, ChevronDown } from "lucide-react";
import "./Header.css";

interface TenantItem {
  id?: number | string;
  name?: string;
  tenantName?: string;
  slug?: string;
  tenantSlug?: string;
}

export function Header() {
  const { isAuthenticated, user, token, logout, openSettingsModal } = useAuth();
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [isMenuMounted, setIsMenuMounted] = useState(false);
  const [tenantMenuPath, setTenantMenuPath] = useState<string | null>(null);
  const [tenantResult, setTenantResult] = useState<{
    token: string | null;
    tenants: TenantItem[];
    error?: string;
  }>({ token: null, tenants: [] });
  const tenantMenuRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const location = useLocation();
  const { tenantId: routeTenantId } = useParams();
  const currentLocationKey = `${location.pathname}:${location.key}`;
  const isTenantMenuOpen = tenantMenuPath === currentLocationKey;
  const currentTenantResult =
    tenantResult.token === token ? tenantResult : { token, tenants: [] as TenantItem[] };
  const tenants = currentTenantResult.tenants;
  const tenantLoadError = currentTenantResult.error;

  function openMenu() {
    setIsMenuMounted(true);
    setIsMenuOpen(true);
  }

  function closeMenu() {
    setIsMenuOpen(false);
  }

  function handleDrawerAnimationEnd() {
    if (!isMenuOpen) {
      setIsMenuMounted(false);
    }
  }

  useEffect(() => {
    if (!isAuthenticated || !token) return;

    let cancelled = false;
    async function loadTenants() {
      try {
        const apiUrl = import.meta.env.VITE_API_URL || "/api";
        const response = await fetch(`${apiUrl}/users/list-tenants`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (!response.ok) {
          throw new Error("Unable to load tenants.");
        }
        const data: unknown = await response.json();
        if (!Array.isArray(data)) {
          throw new Error("Tenant list response was invalid.");
        }
        if (!cancelled) {
          setTenantResult({ token, tenants: data as TenantItem[] });
        }
      } catch {
        if (!cancelled) {
          setTenantResult({ token, tenants: [], error: "Unable to load tenants." });
        }
      }
    }
    void loadTenants();

    return () => {
      cancelled = true;
    };
  }, [isAuthenticated, token]);

  useEffect(() => {
    function handleOutsideClick(event: MouseEvent) {
      if (tenantMenuRef.current && !tenantMenuRef.current.contains(event.target as Node)) {
        setTenantMenuPath(null);
      }
    }
    function handleEscape(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setTenantMenuPath(null);
      }
    }
    document.addEventListener("mousedown", handleOutsideClick);
    window.addEventListener("keydown", handleEscape);
    return () => {
      document.removeEventListener("mousedown", handleOutsideClick);
      window.removeEventListener("keydown", handleEscape);
    };
  }, []);

  // Close on Escape key press
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape" && isMenuOpen) {
        setIsMenuOpen(false);
      }
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isMenuOpen]);

  // Prevent background scroll when drawer is open
  useEffect(() => {
    if (isMenuOpen) {
      document.body.style.overflow = "hidden";
    } else {
      document.body.style.overflow = "";
    }
    return () => {
      document.body.style.overflow = "";
    };
  }, [isMenuOpen]);

  function handleLogout() {
    closeMenu();
    logout();
    navigate("/login");
  }

  function handleOpenSettings() {
    closeMenu();
    openSettingsModal();
  }

  function toggleMenu() {
    if (isMenuOpen) {
      closeMenu();
    } else {
      openMenu();
    }
  }

  const email = user?.email || "";
  const initial = (email.charAt(0) || "U").toUpperCase();
  const role = user?.role || "Member";
  const routeState = location.state as { tenantName?: string } | null;
  const getTenantName = (tenant: TenantItem, index: number) =>
    tenant.tenantName || tenant.name || `Tenant #${tenant.id || index + 1}`;
  const getTenantId = (tenant: TenantItem, index: number) =>
    String(tenant.id ?? (tenant.tenantSlug || tenant.slug || index + 1));
  const activeTenant = tenants.find((tenant, index) => getTenantId(tenant, index) === routeTenantId);
  const activeTenantName = activeTenant
    ? getTenantName(activeTenant, tenants.indexOf(activeTenant))
    : routeState?.tenantName;
  const isCurrent = (path: string) => location.pathname === path;

  return (
    <>
      <header className="sticky top-0 z-40 w-full bg-white/90 dark:bg-gray-900/90 backdrop-blur-md border-b border-gray-200/80 dark:border-gray-800/80 shadow-xs transition-colors duration-200">
        <div className="w-full px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
          {/* Left: Brand / Logo */}
          <div className="flex items-center gap-8">
            <Link
              to={isAuthenticated ? "/tenants" : "/login"}
              className="flex items-center gap-2.5 group focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 rounded-lg p-1 -m-1"
              aria-label="Docket Home"
            >
              <div className="transition-transform duration-200 group-hover:scale-105">
                <LogoIcon className="w-8 h-8 drop-shadow-xs" />
              </div>
              <div className="flex items-baseline gap-1.5">
                <span className="text-xl font-bold tracking-tight text-gray-900 dark:text-white">
                  Docket
                </span>
                <span className="hidden sm:inline-block text-[10px] font-semibold tracking-wider uppercase px-1.5 py-0.5 rounded bg-indigo-50 dark:bg-indigo-950/70 text-indigo-600 dark:text-indigo-400 border border-indigo-200/50 dark:border-indigo-800/50">
                  Cloud
                </span>
              </div>
            </Link>

            {/* Desktop Navigation Links (when authenticated) */}
            {isAuthenticated && (
              <nav
                className="hidden md:flex items-center gap-1"
                aria-label="Main Navigation"
              >
                <div className="relative" ref={tenantMenuRef}>
                  <button
                    type="button"
                    onClick={() => setTenantMenuPath(isTenantMenuOpen ? null : currentLocationKey)}
                    className={`flex max-w-[260px] items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                      routeTenantId || location.pathname === "/tenants"
                        ? "bg-indigo-50 text-indigo-700 dark:bg-indigo-950/60 dark:text-indigo-300"
                        : "text-gray-600 hover:bg-gray-100/70 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-800/60 dark:hover:text-white"
                    }`}
                    aria-haspopup="listbox"
                    aria-expanded={isTenantMenuOpen}
                  >
                    <BuildingIcon className="h-4 w-4 shrink-0" />
                    <span className="truncate">{activeTenantName || "Switch tenant"}</span>
                    <ChevronDown className={`h-4 w-4 shrink-0 transition-transform duration-200 ${isTenantMenuOpen ? "rotate-180" : ""}`} />
                  </button>
                  {isTenantMenuOpen && (
                    <div className="absolute left-0 top-full z-50 mt-2 w-72 overflow-hidden rounded-xl border border-gray-200 bg-white p-1.5 shadow-xl animate-in fade-in slide-in-from-top-1 duration-150 dark:border-gray-700 dark:bg-gray-900">
                      <div className="px-3 py-2 text-[11px] font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500">
                        Switch workspace
                      </div>
                      {tenantLoadError ? (
                        <p className="px-3 py-2 text-sm text-red-600 dark:text-red-400">{tenantLoadError}</p>
                      ) : tenants.length ? (
                        tenants.map((tenant, index) => {
                          const id = getTenantId(tenant, index);
                          const name = getTenantName(tenant, index);
                          const selected = id === routeTenantId;
                          return (
                            <button
                              key={id}
                              type="button"
                              role="option"
                              aria-selected={selected}
                              onClick={() => {
                                navigate(`/workspace/${encodeURIComponent(id)}`, { state: { tenantName: name } });
                                setTenantMenuPath(null);
                              }}
                              className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left transition-colors hover:bg-gray-100 dark:hover:bg-gray-800"
                            >
                              <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-indigo-50 text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-400">
                                <BuildingIcon className="h-4 w-4" />
                              </span>
                              <span className="min-w-0 flex-1">
                                <span className="block truncate text-sm font-medium text-gray-800 dark:text-gray-100">{name}</span>
                                {(tenant.tenantSlug || tenant.slug) && <span className="block truncate text-xs text-gray-500 dark:text-gray-400">{tenant.tenantSlug || tenant.slug}</span>}
                              </span>
                              {selected && <Check className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />}
                            </button>
                          );
                        })
                      ) : (
                        <p className="px-3 py-2 text-sm text-gray-500 dark:text-gray-400">No tenants available.</p>
                      )}
                      <div className="mt-1 border-t border-gray-100 pt-1 dark:border-gray-800">
                        <Link
                          to="/tenants"
                          onClick={() => setTenantMenuPath(null)}
                          className="block rounded-lg px-3 py-2 text-sm font-medium text-gray-600 transition-colors hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
                        >
                          View all tenants
                        </Link>
                      </div>
                    </div>
                  )}
                </div>
              </nav>
            )}
          </div>

          {/* Right: Actions / Auth States / Hamburger Button */}
          <div className="flex items-center gap-3">
            {isAuthenticated ? (
              <>
                {/* Hamburger Menu Toggle Button */}
                <button
                  type="button"
                  onClick={toggleMenu}
                  className="p-2 rounded-xl text-gray-600 dark:text-gray-300 hover:text-gray-900 dark:hover:text-white hover:bg-gray-100 dark:hover:bg-gray-800 border border-gray-200 dark:border-gray-800 focus:outline-none focus:ring-2 focus:ring-indigo-500 transition-colors"
                  aria-label={isMenuOpen ? "Close menu" : "Open menu"}
                  aria-expanded={isMenuOpen}
                >
                  {isMenuOpen ? (
                    <CloseIcon className="w-5 h-5" />
                  ) : (
                    <MenuIcon className="w-5 h-5" />
                  )}
                </button>
              </>
            ) : (
              <>
                {/* Unauthenticated desktop actions */}
                <div className="hidden sm:flex items-center gap-2">
                  <Link
                    to="/login"
                    className="px-3.5 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:text-gray-900 dark:hover:text-white rounded-lg hover:bg-gray-100/70 dark:hover:bg-gray-800/60 transition-colors"
                  >
                    Log in
                  </Link>
                  <Link
                    to="/register"
                    className="px-4 py-2 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 rounded-lg shadow-sm transition-colors"
                  >
                    Register
                  </Link>
                </div>

                {/* Mobile Hamburger for unauthenticated users */}
                <button
                  type="button"
                  onClick={toggleMenu}
                  className="sm:hidden p-2 rounded-xl text-gray-600 dark:text-gray-300 hover:text-gray-900 dark:hover:text-white hover:bg-gray-100 dark:hover:bg-gray-800 border border-gray-200 dark:border-gray-800 focus:outline-none focus:ring-2 focus:ring-indigo-500 transition-colors"
                  aria-label="Open menu"
                  aria-expanded={isMenuOpen}
                >
                  {isMenuOpen ? (
                    <CloseIcon className="w-5 h-5" />
                  ) : (
                    <MenuIcon className="w-5 h-5" />
                  )}
                </button>
              </>
            )}
          </div>
        </div>
      </header>

      {/* Slide-over Hamburger Drawer & Backdrop */}
      {isMenuMounted && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="Navigation drawer"
          className="fixed inset-0 z-50 overflow-hidden"
        >
          {/* Backdrop */}
          <div
            onClick={closeMenu}
            aria-hidden="true"
            className={`fixed inset-0 bg-black/40 backdrop-blur-xs header-drawer-backdrop ${
              isMenuOpen ? "header-drawer-backdrop-open" : "header-drawer-backdrop-close"
            }`}
          />

          {/* Drawer Panel */}
          <div className="fixed inset-y-0 right-0 max-w-full flex pl-10">
            <div
              onAnimationEnd={handleDrawerAnimationEnd}
              className={`header-drawer-panel w-screen max-w-sm bg-white dark:bg-gray-900 border-l border-gray-200 dark:border-gray-800 shadow-2xl flex flex-col justify-between ${
                isMenuOpen ? "header-drawer-panel-open" : "header-drawer-panel-close"
              }`}
            >
              {/* Drawer Top */}
              <div>
                {/* Header */}
                <div className="px-6 py-5 border-b border-gray-100 dark:border-gray-800 flex items-center justify-between">
                  <div className="flex items-center gap-2.5">
                    <LogoIcon className="w-7 h-7" />
                    <span className="font-bold text-gray-900 dark:text-white">
                      Docket Menu
                    </span>
                  </div>
                  <button
                    type="button"
                    onClick={closeMenu}
                    className="p-1.5 rounded-lg text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
                    aria-label="Close menu"
                  >
                    <CloseIcon className="w-5 h-5" />
                  </button>
                </div>

                {/* Authenticated User Banner */}
                {isAuthenticated && (
                  <div className="p-6 border-b border-gray-100 dark:border-gray-800 bg-gray-50/60 dark:bg-gray-800/40">
                    <div className="flex items-center gap-3">
                      <div className="w-12 h-12 rounded-full bg-gradient-to-tr from-indigo-600 via-indigo-500 to-violet-500 flex items-center justify-center text-white text-lg font-bold shadow-sm">
                        {initial}
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="text-sm font-semibold text-gray-900 dark:text-white truncate">
                          {email}
                        </div>
                        <div className="flex items-center gap-2 mt-0.5">
                          <span className="inline-block w-2 h-2 rounded-full bg-emerald-500" />
                          <span className="text-xs text-gray-500 dark:text-gray-400 capitalize">
                            {role.toLowerCase()}
                          </span>
                        </div>
                      </div>
                    </div>
                  </div>
                )}

                {/* Navigation Items */}
                <div className="p-4 space-y-1">
                  {isAuthenticated ? (
                    <>
                      <div className="px-3 py-2 text-[11px] font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500">
                        Navigation
                      </div>

                      <Link
                        to="/tenants"
                        onClick={closeMenu}
                        className={`flex items-center justify-between px-3.5 py-2.5 rounded-xl text-sm font-medium transition-colors ${
                          isCurrent("/tenants")
                            ? "bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400"
                            : "text-gray-700 dark:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800"
                        }`}
                      >
                        <div className="flex items-center gap-3">
                          <BuildingIcon className="w-5 h-5 text-gray-400 dark:text-gray-500" />
                          <span>Tenants Dashboard</span>
                        </div>
                        <ChevronRightIcon className="w-4 h-4 text-gray-400" />
                      </Link>

                      <Link
                        to="/create-first-tenant"
                        onClick={closeMenu}
                        className={`flex items-center justify-between px-3.5 py-2.5 rounded-xl text-sm font-medium transition-colors ${
                          isCurrent("/create-first-tenant")
                            ? "bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400"
                            : "text-gray-700 dark:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800"
                        }`}
                      >
                        <div className="flex items-center gap-3">
                          <PlusIcon className="w-5 h-5 text-gray-400 dark:text-gray-500" />
                          <span>Create Tenant</span>
                        </div>
                        <ChevronRightIcon className="w-4 h-4 text-gray-400" />
                      </Link>

                      <div className="pt-4 px-3 py-2 text-[11px] font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500">
                        Preferences & Account
                      </div>

                      <button
                        type="button"
                        onClick={handleOpenSettings}
                        className="w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-sm font-medium text-gray-700 dark:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
                      >
                        <div className="flex items-center gap-3">
                          <SettingsIcon className="w-5 h-5 text-gray-400 dark:text-gray-500" />
                          <span>Account Settings</span>
                        </div>
                        <ChevronRightIcon className="w-4 h-4 text-gray-400" />
                      </button>

                      <button
                        type="button"
                        onClick={handleOpenSettings}
                        className="w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-sm font-medium text-gray-700 dark:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
                      >
                        <div className="flex items-center gap-3">
                          <ShieldIcon className="w-5 h-5 text-gray-400 dark:text-gray-500" />
                          <span>Security & Sessions</span>
                        </div>
                        <ChevronRightIcon className="w-4 h-4 text-gray-400" />
                      </button>
                    </>
                  ) : (
                    <>
                      <div className="px-3 py-2 text-[11px] font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500">
                        Get Started
                      </div>
                      <Link
                        to="/login"
                        onClick={closeMenu}
                        className="flex items-center justify-between px-3.5 py-2.5 rounded-xl text-sm font-medium text-gray-700 dark:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
                      >
                        <span>Log in</span>
                        <ChevronRightIcon className="w-4 h-4 text-gray-400" />
                      </Link>
                      <Link
                        to="/register"
                        onClick={closeMenu}
                        className="flex items-center justify-between px-3.5 py-2.5 rounded-xl text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 shadow-sm transition-colors mt-2"
                      >
                        <span>Create an Account</span>
                        <ChevronRightIcon className="w-4 h-4 text-white" />
                      </Link>
                    </>
                  )}
                </div>
              </div>

              {/* Drawer Bottom / Logout */}
              {isAuthenticated && (
                <div className="p-4 border-t border-gray-100 dark:border-gray-800 bg-gray-50/50 dark:bg-gray-900/50">
                  <button
                    type="button"
                    onClick={handleLogout}
                    className="w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-medium text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-950/40 transition-colors group"
                  >
                    <LogOutIcon className="w-5 h-5 transition-transform group-hover:-translate-x-0.5" />
                    <span>Log Out</span>
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
