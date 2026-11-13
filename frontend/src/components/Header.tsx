import { apiFetch } from "../lib/session";
import { useState, useEffect, useRef } from "react";
import { Link, matchPath, useNavigate, useLocation } from "react-router-dom";
import { useAuth } from "../context/useAuth";
import { GlobalSearchBar } from "./GlobalSearchBar";
import { NotificationBell } from "./NotificationBell";
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
import { Check, ChevronDown, Search } from "lucide-react";
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
  // The header sits outside the routes, so useParams can't see the workspace from here.
  const routeSlug = matchPath({ path: "/workspace/:slug", end: false }, location.pathname)?.params.slug;
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
        const response = await apiFetch(`${apiUrl}/users/list-tenants`, {
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
    // Loaded again on navigation, so joining or leaving an organization shows up.
  }, [isAuthenticated, token, location.pathname]);

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
  const getTenantSlug = (tenant: TenantItem) =>
    tenant.tenantSlug || tenant.slug || "";
  const activeTenant = tenants.find((tenant) => getTenantSlug(tenant) === routeSlug);
  const activeTenantName = activeTenant
    ? getTenantName(activeTenant, tenants.indexOf(activeTenant))
    : routeState?.tenantName;
  const isCurrent = (path: string) => location.pathname === path;

  return (
    <>
      <header className="sticky top-0 z-40 w-full bg-white border-b border-[#DDDDDD]">
        <div className="w-full px-4 sm:px-6 lg:px-8 h-14 flex items-center justify-between">
          {/* Left: Brand / Logo */}
          <div className="flex items-center gap-6">
            <Link
              to={isAuthenticated ? "/tenants" : "/login"}
              className="flex items-center gap-2 group focus:outline-none focus-visible:ring-2 focus-visible:ring-[#36C5F0] rounded-[8px] p-1 -m-1"
              aria-label="Docket Home"
            >
              <div className="transition-transform duration-200 group-hover:scale-105">
                <LogoIcon className="w-7 h-7 drop-shadow-xs" />
              </div>
              <div className="flex items-baseline gap-1.5">
                <span className="text-[20px] font-black tracking-tight text-[#1D1C1D]">
                  docket
                </span>
                <span className="hidden sm:inline-block text-[10px] font-bold tracking-wider uppercase px-1.5 py-0.5 rounded-[4px] bg-[#F8F8F8] border border-[#DDDDDD] text-[#616061]">
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
                    className={`flex max-w-[260px] items-center gap-2 rounded-[8px] border border-[#DDDDDD] px-3 py-1.5 text-[14px] font-medium transition-colors ${
                      routeSlug || location.pathname === "/tenants"
                        ? "bg-[#F8F8F8] text-[#1D1C1D] border-[#C1C1C1] font-semibold"
                        : "bg-white text-[#616061] hover:bg-[#F8F8F8] hover:text-[#1D1C1D]"
                    }`}
                    aria-haspopup="listbox"
                    aria-expanded={isTenantMenuOpen}
                  >
                    <BuildingIcon className="h-4 w-4 shrink-0 text-[#616061]" />
                    <span className="truncate">{activeTenantName || "Switch tenant"}</span>
                    <ChevronDown className={`h-4 w-4 shrink-0 text-[#616061] transition-transform duration-200 ${isTenantMenuOpen ? "rotate-180" : ""}`} />
                  </button>
                  {isTenantMenuOpen && (
                    <div className="absolute left-0 top-full z-50 mt-1.5 w-72 overflow-hidden rounded-[8px] border border-[#DDDDDD] bg-white p-1.5 shadow-[0_4px_12px_rgba(0,0,0,0.15)]">
                      <div className="px-3 py-1.5 text-[11px] font-bold uppercase tracking-wider text-[#616061]">
                        Switch workspace
                      </div>
                      {tenantLoadError ? (
                        <p className="px-3 py-2 text-xs text-[#E01E5A]">{tenantLoadError}</p>
                      ) : tenants.length ? (
                        tenants.map((tenant, index) => {
                          const slug = getTenantSlug(tenant);
                          const name = getTenantName(tenant, index);
                          const selected = slug === routeSlug;
                          return (
                            <button
                              key={slug || index}
                              type="button"
                              role="option"
                              aria-selected={selected}
                              onClick={() => {
                                navigate(`/workspace/${encodeURIComponent(slug)}`, { state: { tenantName: name } });
                                setTenantMenuPath(null);
                              }}
                              className="flex w-full items-center gap-2.5 rounded-[6px] px-3 py-2 text-left transition-colors hover:bg-[#F8F8F8]"
                            >
                              <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-[6px] bg-[#4A154B] text-white">
                                <BuildingIcon className="h-3.5 w-3.5" />
                              </span>
                              <span className="min-w-0 flex-1">
                                <span className="block truncate text-[14px] font-medium text-[#1D1C1D]">{name}</span>
                                {(tenant.tenantSlug || tenant.slug) && <span className="block truncate text-[11px] text-[#616061]">{tenant.tenantSlug || tenant.slug}</span>}
                              </span>
                              {selected && <Check className="h-4 w-4 text-[#2EB67D]" />}
                            </button>
                          );
                        })
                      ) : (
                        <p className="px-3 py-2 text-xs text-[#616061]">No tenants available.</p>
                      )}
                      <div className="mt-1 border-t border-[#DDDDDD] pt-1">
                        <Link
                          to="/tenants"
                          onClick={() => setTenantMenuPath(null)}
                          className="block rounded-[6px] px-3 py-1.5 text-[13px] font-medium text-[#1164A3] transition-colors hover:bg-[#F8F8F8]"
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
          <div className="flex items-center gap-2.5">
            {isAuthenticated ? (
              <>
                {routeSlug && (
                  <>
                    <GlobalSearchBar slug={routeSlug} />
                    <Link
                      to={`/workspace/${encodeURIComponent(routeSlug)}/search`}
                      aria-label="Search"
                      className="p-2 rounded-[8px] text-[#616061] border border-[#DDDDDD] hover:bg-[#F8F8F8] md:hidden"
                    >
                      <Search className="w-4 h-4" />
                    </Link>
                  </>
                )}

                {token && <NotificationBell token={token} />}

                {/* Hamburger Menu Toggle Button */}
                <button
                  type="button"
                  onClick={toggleMenu}
                  className="p-2 rounded-[8px] text-[#616061] hover:text-[#1D1C1D] hover:bg-[#F8F8F8] border border-[#DDDDDD] focus:outline-none focus:ring-2 focus:ring-[#36C5F0] transition-colors"
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
                    className="px-3 py-1.5 text-[14px] font-medium text-[#1D1C1D] hover:bg-[#F8F8F8] rounded-[8px] border border-[#DDDDDD] transition-colors"
                  >
                    Log in
                  </Link>
                  <Link
                    to="/register"
                    className="px-3.5 py-1.5 text-[14px] font-semibold text-white bg-[#007A5A] hover:bg-[#00664B] rounded-[8px] shadow-[0_1px_2px_rgba(0,0,0,0.1)] transition-colors"
                  >
                    Register
                  </Link>
                </div>

                {/* Mobile Hamburger for unauthenticated users */}
                <button
                  type="button"
                  onClick={toggleMenu}
                  className="sm:hidden p-2 rounded-[8px] text-[#616061] hover:text-[#1D1C1D] hover:bg-[#F8F8F8] border border-[#DDDDDD] focus:outline-none focus:ring-2 focus:ring-[#36C5F0] transition-colors"
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
              className={`header-drawer-panel w-screen max-w-sm bg-white border-l border-[#DDDDDD] shadow-[0_8px_30px_rgba(0,0,0,0.2)] flex flex-col justify-between ${
                isMenuOpen ? "header-drawer-panel-open" : "header-drawer-panel-close"
              }`}
            >
              {/* Drawer Top */}
              <div>
                {/* Header */}
                <div className="px-5 py-4 border-b border-[#DDDDDD] flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <LogoIcon className="w-6 h-6" />
                    <span className="font-bold text-[16px] text-[#1D1C1D]">
                      Docket Menu
                    </span>
                  </div>
                  <button
                    type="button"
                    onClick={closeMenu}
                    className="p-1.5 rounded-[8px] text-[#616061] hover:text-[#1D1C1D] hover:bg-[#F8F8F8] transition-colors"
                    aria-label="Close menu"
                  >
                    <CloseIcon className="w-5 h-5" />
                  </button>
                </div>

                {/* Authenticated User Banner */}
                {isAuthenticated && (
                  <div className="p-5 border-b border-[#DDDDDD] bg-[#F8F8F8]">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-[8px] bg-[#1164A3] flex items-center justify-center text-white text-base font-bold shadow-sm">
                        {initial}
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="text-[14px] font-bold text-[#1D1C1D] truncate">
                          {email}
                        </div>
                        <div className="flex items-center gap-1.5 mt-0.5">
                          <span className="inline-block w-2 h-2 rounded-full bg-[#2EB67D]" />
                          <span className="text-[12px] text-[#616061] capitalize">
                            {role.toLowerCase()} · Online
                          </span>
                        </div>
                      </div>
                    </div>
                  </div>
                )}

                {/* Navigation Items */}
                <div className="p-3 space-y-1">
                  {isAuthenticated ? (
                    <>
                      <div className="px-3 py-1.5 text-[11px] font-bold uppercase tracking-wider text-[#616061]">
                        Navigation
                      </div>

                      <Link
                        to="/tenants"
                        onClick={closeMenu}
                        className={`flex items-center justify-between px-3 py-2 rounded-[8px] text-[14px] font-medium transition-colors ${
                          isCurrent("/tenants")
                            ? "bg-[#1164A3]/10 text-[#1164A3] font-bold"
                            : "text-[#1D1C1D] hover:bg-[#F8F8F8]"
                        }`}
                      >
                        <div className="flex items-center gap-2.5">
                          <BuildingIcon className="w-4 h-4 text-[#616061]" />
                          <span>Tenants Dashboard</span>
                        </div>
                        <ChevronRightIcon className="w-4 h-4 text-[#616061]" />
                      </Link>

                      <Link
                        to="/create-first-tenant"
                        onClick={closeMenu}
                        className={`flex items-center justify-between px-3 py-2 rounded-[8px] text-[14px] font-medium transition-colors ${
                          isCurrent("/create-first-tenant")
                            ? "bg-[#1164A3]/10 text-[#1164A3] font-bold"
                            : "text-[#1D1C1D] hover:bg-[#F8F8F8]"
                        }`}
                      >
                        <div className="flex items-center gap-2.5">
                          <PlusIcon className="w-4 h-4 text-[#616061]" />
                          <span>Create Tenant</span>
                        </div>
                        <ChevronRightIcon className="w-4 h-4 text-[#616061]" />
                      </Link>

                      <div className="pt-3 px-3 py-1.5 text-[11px] font-bold uppercase tracking-wider text-[#616061]">
                        Preferences & Account
                      </div>

                      <button
                        type="button"
                        onClick={handleOpenSettings}
                        className="w-full flex items-center justify-between px-3 py-2 rounded-[8px] text-[14px] font-medium text-[#1D1C1D] hover:bg-[#F8F8F8] transition-colors"
                      >
                        <div className="flex items-center gap-2.5">
                          <SettingsIcon className="w-4 h-4 text-[#616061]" />
                          <span>Account Settings</span>
                        </div>
                        <ChevronRightIcon className="w-4 h-4 text-[#616061]" />
                      </button>

                      <button
                        type="button"
                        onClick={handleOpenSettings}
                        className="w-full flex items-center justify-between px-3 py-2 rounded-[8px] text-[14px] font-medium text-[#1D1C1D] hover:bg-[#F8F8F8] transition-colors"
                      >
                        <div className="flex items-center gap-2.5">
                          <ShieldIcon className="w-4 h-4 text-[#616061]" />
                          <span>Security & Sessions</span>
                        </div>
                        <ChevronRightIcon className="w-4 h-4 text-[#616061]" />
                      </button>
                    </>
                  ) : (
                    <>
                      <div className="px-3 py-1.5 text-[11px] font-bold uppercase tracking-wider text-[#616061]">
                        Get Started
                      </div>
                      <Link
                        to="/login"
                        onClick={closeMenu}
                        className="flex items-center justify-between px-3 py-2 rounded-[8px] text-[14px] font-medium text-[#1D1C1D] hover:bg-[#F8F8F8] transition-colors"
                      >
                        <span>Log in</span>
                        <ChevronRightIcon className="w-4 h-4 text-[#616061]" />
                      </Link>
                      <Link
                        to="/register"
                        onClick={closeMenu}
                        className="flex items-center justify-between px-3 py-2 rounded-[8px] text-[14px] font-bold text-white bg-[#007A5A] hover:bg-[#00664B] shadow-sm transition-colors mt-2"
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
                <div className="p-3 border-t border-[#DDDDDD] bg-[#F8F8F8]">
                  <button
                    type="button"
                    onClick={handleLogout}
                    className="w-full flex items-center gap-2.5 px-3 py-2 rounded-[8px] text-[14px] font-semibold text-[#E01E5A] hover:bg-[#E01E5A]/10 transition-colors"
                  >
                    <LogOutIcon className="w-4 h-4 text-[#E01E5A]" />
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
