import { apiFetch } from "../lib/session";
import { useState, useEffect } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { useAuth } from "../context/useAuth";
import { hasPermission, type PermissionCode } from "../lib/permissions";
import {
  ArrowLeft,
  Building2,
  Check,
  ChevronRight,
  Clock3,
  FileText,
  Folder,
  Inbox,
  Mail,
  PanelLeftClose,
  PanelLeftOpen,
  Plus,
  Search,
  Settings,
  ShieldCheck,
  Sparkles,
  Trash2,
  UsersRound,
  History,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { CreateTrayModal } from "../components/CreateTrayModal";
import { AiChatPanel } from "../components/AiChatPanel";
import { WorkspaceAiSearch } from "../components/WorkspaceAiSearch";
import { FolderBrowser } from "../components/FolderBrowser";
import { TrashView } from "../components/TrashView";
import { TraysOverview } from "../components/TraysOverview";
import { ActivityLog } from "../components/ActivityLog";
import { OrganizationSettings } from "../components/OrganizationSettings";
import { notify } from "../lib/notifications";
import type { Tray, CreateTrayRequestDTO } from "../services/trayService";
import { getTenantTrays, createTray, deleteTray } from "../services/trayService";
import "./TenantWorkspace.css";

// Returns a time-appropriate greeting based on the current hour.
function getGreeting(): string {
  const hour = new Date().getHours();
  if (hour >= 5 && hour < 12) return "Good morning";
  if (hour >= 12 && hour < 18) return "Good afternoon";
  return "Good evening";
}

// The possible fixed sections a workspace can show (or a dynamic tray ID)
type WorkspaceSection = string;

// Shape of each sidebar navigation item.
interface WorkspaceNavItem {
  id: WorkspaceSection;
  label: string;
  icon: LucideIcon;
  permission: PermissionCode | null;
  count?: string;
}

// Initial fallback trays so sidebar and tests render immediately
const INITIAL_TRAYS: Tray[] = [];

// Sample placeholder data for review dashboard
const documents = [
  { name: "Board resolution — Q3 approval", detail: "Corporate governance · PDF", date: "Today", memberStatus: "Needs review" },
  { name: "Master services agreement", detail: "Commercial · DOCX", date: "Yesterday", memberStatus: "Needs review" },
  { name: "Annual filing receipt", detail: "Company records · PDF", date: "Oct 10", memberStatus: "In progress" },
];

const partners = [
  { name: "Morrison & Vale LLP", role: "Outside counsel", initials: "MV", color: "bg-indigo-100 text-indigo-700 dark:bg-indigo-950 dark:text-indigo-300" },
  { name: "Northstar Accounting", role: "Accounting partner", initials: "NA", color: "bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300" },
  { name: "Harbor Compliance", role: "Compliance partner", initials: "HC", color: "bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300" },
];

const emailAccounts = [
  { address: "legal@northstar.example", provider: "Google Workspace", initial: "G", color: "bg-red-50 text-red-600 dark:bg-red-950/50 dark:text-red-300" },
  { address: "corporate@northstar.example", provider: "Microsoft 365", initial: "M", color: "bg-blue-50 text-blue-600 dark:bg-blue-950/50 dark:text-blue-300" },
];

const sectionLabels: Record<string, string> = {
  review: "Review",
  "ai-search": "AI Search",
  trays: "Trays",
  general: "General",
  trash: "Trash",
  partners: "Partners",
  emails: "Connected Emails",
  activity: "Activity Log",
  settings: "Settings",
};

// A single sidebar button. Shows an icon, label, and optional count badge.
function NavItem({
  item,
  collapsed,
  active,
  onClick,
  isChannel,
  isDM,
}: {
  item: WorkspaceNavItem;
  collapsed: boolean;
  active: boolean;
  onClick: () => void;
  isChannel?: boolean;
  isDM?: boolean;
}) {
  const Icon = item.icon;

  return (
    <button
      type="button"
      onClick={onClick}
      title={collapsed ? item.label : undefined}
      aria-label={item.label}
      aria-current={active ? "page" : undefined}
      className={`group flex w-full items-center gap-2.5 rounded-[12px] px-3 py-1.5 text-left text-[15px] font-normal transition-colors duration-150 ${
        active
          ? "bg-[#1164A3] text-white font-bold"
          : item.count
            ? "text-white font-bold hover:bg-white/10"
            : "text-[#D1D2D3] hover:bg-white/10 hover:text-white"
      } ${collapsed ? "justify-center px-0 max-md:w-10" : "max-md:w-auto max-md:shrink-0 max-md:px-3"}`}
    >
      {isChannel ? (
        <span className={`text-[15px] font-bold w-4 text-center shrink-0 ${active ? "text-white" : "text-[#D1D2D3]/70"}`}>
          #
        </span>
      ) : isDM ? (
        <span className="relative flex h-2 w-2 shrink-0 items-center justify-center">
          <span className="h-2 w-2 rounded-full bg-[#2EB67D]" />
        </span>
      ) : (
        <Icon className={`h-[18px] w-[18px] shrink-0 ${active ? "text-white" : "text-[#D1D2D3]"}`} strokeWidth={1.8} />
      )}
      {!collapsed && (
        <>
          <span className="min-w-0 flex-1 truncate">{item.label}</span>
          {item.count && (
            <span
              className={`rounded-[4px] px-1.5 py-0.2 text-[11px] font-bold ${
                active ? "bg-white/20 text-white" : "bg-[#ECB22E] text-[#1D1C1D]"
              }`}
            >
              {item.count}
            </span>
          )}
        </>
      )}
    </button>
  );
}

// The main workspace page.
export function TenantWorkspace() {
  const { slug } = useParams();
  const { token } = useAuth();
  const location = useLocation();
  // A search result can send someone straight to the folder a document is in.
  const routeState = location.state as { tenantName?: string; openTrayId?: string; openFolderId?: string } | null;

  const [collapsed, setCollapsed] = useState(false);
  // Links in notifications and emails can point at a part of the workspace.
  const searchParams = new URLSearchParams(location.search);
  const linkedSection = searchParams.get("section");
  const linkedTab = searchParams.get("tab");
  const [activeSection, setActiveSection] = useState<WorkspaceSection>(routeState?.openTrayId ?? linkedSection ?? "review");

  // The workspace is already open when a link is followed from the bell.
  useEffect(() => {
    if (linkedSection) setActiveSection(linkedSection);
  }, [linkedSection, location.key]);
  // Bumped to load permissions again after they change, such as giving away ownership.
  const [permissionsVersion, setPermissionsVersion] = useState(0);
  const [trays, setTrays] = useState<Tray[]>(INITIAL_TRAYS);
  const [isCreateTrayModalOpen, setIsCreateTrayModalOpen] = useState(false);

  const [aiPanel, setAiPanel] = useState<{
    isOpen: boolean;
    documentId: string;
    documentName: string;
  }>({ isOpen: false, documentId: "", documentName: "" });

  function handleAskAi(docId: string, docName: string) {
    setAiPanel({ isOpen: true, documentId: docId, documentName: docName });
  }

  const [permissionState, setPermissionState] = useState<{
    slug: string | undefined;
    token: string | null;
    permissions: ReadonlySet<string>;
    loading: boolean;
    error: string | null;
  }>({
    slug: undefined,
    token: null,
    permissions: new Set(),
    loading: true,
    error: null,
  });

  const [tenantName, setTenantName] = useState(
    routeState?.tenantName ||
    slug?.replace(/[-_]/g, " ").replace(/\b\w/g, (letter) => letter.toUpperCase()) ||
    "Workspace"
  );

  // 1. Fetch permissions & tenant metadata
  useEffect(() => {
    const controller = new AbortController();
    if (!slug || !token) {
      return () => controller.abort();
    }

    const apiUrl = import.meta.env.VITE_API_URL || "/api";
    const encodedSlug = encodeURIComponent(slug);
    const headers = { Authorization: `Bearer ${token}` };

    Promise.all([
      apiFetch(`${apiUrl}/tenants/by-slug/${encodedSlug}/permissions`, {
        headers,
        signal: controller.signal,
      }),
      apiFetch(`${apiUrl}/tenants/by-slug/${encodedSlug}`, {
        headers,
        signal: controller.signal,
      }),
    ])
      .then(async ([permissionsResponse, tenantResponse]) => {
        if (!permissionsResponse.ok || !tenantResponse.ok) {
          throw new Error("Unable to verify workspace access.");
        }
        const [data, tenantData]: [unknown, unknown] = await Promise.all([
          permissionsResponse.json(),
          tenantResponse.json(),
        ]);
        if (
          !Array.isArray(data) ||
          !data.every((permission): permission is string => typeof permission === "string")
        ) {
          throw new Error("Workspace permissions response was invalid.");
        }
        return { permissions: data, tenant: tenantData };
      })
      .then(({ permissions: permissionCodes, tenant }) => {
        if (!controller.signal.aborted) {
          if (
            tenant &&
            typeof tenant === "object" &&
            "name" in tenant &&
            typeof tenant.name === "string"
          ) {
            setTenantName(tenant.name);
          }
          setPermissionState({
            slug,
            token,
            permissions: new Set(permissionCodes),
            loading: false,
            error: null,
          });
        }
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setPermissionState({
            slug,
            token,
            permissions: new Set(),
            loading: false,
            error: error instanceof Error ? error.message : "Unable to verify workspace access.",
          });
        }
      });

    return () => controller.abort();
  }, [slug, token, permissionsVersion]);

  // 2. Fetch Trays for this tenant
  useEffect(() => {
    let isCancelled = false;
    async function loadTrays() {
      if (!slug) return;
      try {
        /**
         * 💡 LEARNING NOTE: API Call to load Trays
         * Backend: GET /api/tenants/{slug}/trays
         * Controller: TenantController.java -> getTenantTrays()
         * Permission: TENANT_READ ("tenant.read")
         * 
         * When connecting to real backend, see getTenantTrays() in trayService.ts!
         */
        const loadedTrays = await getTenantTrays(slug, token);
        if (!isCancelled && loadedTrays.length > 0) {
          setTrays(loadedTrays);
        }
      } catch (err) {
        console.error("Failed to load workspace trays:", err);
      }
    }

    loadTrays();
    return () => {
      isCancelled = true;
    };
  }, [slug, token]);

  // Main sidebar items (Review, Partners, Emails)
  const mainNavItems: WorkspaceNavItem[] = [
    { id: "review", label: "Review", icon: Inbox, permission: "documents.read", count: "6" },
    { id: "ai-search", label: "AI Search", icon: Sparkles, permission: "documents.read" },
    { id: "partners", label: "Partners", icon: UsersRound, permission: "members.read" },
    { id: "emails", label: "Connected Emails", icon: Mail, permission: null, count: "2" },
    { id: "activity", label: "Activity Log", icon: History, permission: "audit.read" },
    { id: "settings", label: "Settings", icon: Settings, permission: "tenant.read" },
  ];

  const accessStateMatchesRoute = permissionState.slug === slug && permissionState.token === token;
  const permissionsLoading = !!slug && !!token && (!accessStateMatchesRoute || permissionState.loading);
  const permissionsError = !slug || !token
    ? "Unable to verify workspace access."
    : accessStateMatchesRoute
      ? permissionState.error
      : null;
  const permissions = accessStateMatchesRoute ? permissionState.permissions : new Set<string>();

  // Permission capabilities
  const canCreateTray =
    hasPermission(permissions, "trays.create") ||
    hasPermission(permissions, "tenant.update");
  const canDeleteTray =
    hasPermission(permissions, "trays.delete") ||
    hasPermission(permissions, "documents.delete");
  const canCreateFolder =
    hasPermission(permissions, "folders.create") ||
    hasPermission(permissions, "documents.create");
  const canDeleteFolder =
    hasPermission(permissions, "folders.delete") ||
    hasPermission(permissions, "documents.delete");
  const canUpload = hasPermission(permissions, "documents.create");

  // Filter visible main nav items
  const visibleMainNavItems = mainNavItems.filter(
    (item) => item.permission !== null && hasPermission(permissions, item.permission),
  );

  // Dynamic Tray Visibility Filter:
  // - ADMIN_ONLY trays require trays.create OR documents.delete OR trays.delete
  // - ALL_MEMBERS trays require documents.read
  const visibleTrays = trays.filter((tray) => {
    if (tray.visibility === "ADMIN_ONLY") {
      return (
        hasPermission(permissions, "trays.create") ||
        hasPermission(permissions, "documents.delete") ||
        hasPermission(permissions, "trays.delete")
      );
    }
    return hasPermission(permissions, "documents.read");
  });

  const visibleSections = [
    ...visibleMainNavItems.map((item) => item.id),
    ...(visibleTrays.length > 0 ? ["trays"] : []),
    ...visibleTrays.map((tray) => tray.id),
  ];

  // Current active section (falls back to first visible)
  const currentSection = visibleSections.includes(activeSection)
    ? activeSection
    : visibleSections[0] || "review";

  // Check if current section corresponds to a Tray
  const activeTray = trays.find(
    (t) => t.id === currentSection || t.name.toLowerCase() === currentSection.toLowerCase()
  );

  // Handler for creating a new Tray
  async function handleCreateTraySubmit(data: CreateTrayRequestDTO) {
    if (!slug) return;
    try {
      const newTray = await createTray(slug, data, token);
      setTrays((prev) => [...prev, newTray]);
      setActiveSection(newTray.id);
      notify.success("Tray created", `"${newTray.name}" is now ready`);
    } catch (err) {
      notify.error("Failed to create tray", err instanceof Error ? err.message : "An error occurred");
      throw err;
    }
  }

  // Handler for deleting a custom Tray
  async function handleDeleteTray(trayId: string) {
    if (!slug) return;
    const tray = trays.find((t) => t.id === trayId);
    try {
      await deleteTray(slug, trayId, token);
      setTrays((prev) => prev.filter((t) => t.id !== trayId));
      setActiveSection("review");
      notify.success("Tray deleted", tray ? `"${tray.name}" has been removed` : "Tray removed");
    } catch (err) {
      notify.error("Failed to delete tray", err instanceof Error ? err.message : "An error occurred");
      throw err;
    }
  }

  return (
    <div className="flex min-h-[calc(100svh-4rem)] flex-1 flex-col bg-white text-[#1D1C1D] md:flex-row">
      {/* Sidebar: Aubergine background, Slack brand anchor */}
      <aside
        className={`flex shrink-0 flex-col border-r border-[#3F1041] bg-[#4A154B] text-white transition-[width] duration-200 ease-in-out ${
          collapsed ? "w-full md:w-[72px]" : "w-full md:w-[252px]"
        }`}
        aria-label="Workspace navigation"
      >
        {/* Workspace Brand Header */}
        <div className={`flex h-[72px] items-center border-b border-[#3F1041] bg-[#4A154B] ${collapsed ? "justify-center px-2" : "justify-between px-4"}`}>
          {!collapsed && (
            <div className="flex min-w-0 items-center gap-2.5">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[8px] bg-white/10 text-white shadow-sm">
                <Building2 className="h-5 w-5" />
              </div>
              <div className="min-w-0">
                <p className="truncate text-[15px] font-bold text-white leading-tight">{tenantName}</p>
                <div className="flex items-center gap-1.5 mt-0.5">
                  <span className="h-2 w-2 rounded-full bg-[#2EB67D]" title="Active" />
                  <p className="text-[12px] text-[#D1D2D3] leading-none">Active workspace</p>
                </div>
              </div>
            </div>
          )}
          <button
            type="button"
            onClick={() => setCollapsed((previous) => !previous)}
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[8px] text-[#D1D2D3] transition-colors hover:bg-white/10 hover:text-white focus:outline-none focus-visible:ring-2 focus-visible:ring-[#36C5F0]"
            aria-label={collapsed ? "Expand workspace sidebar" : "Collapse workspace sidebar"}
            aria-expanded={!collapsed}
            title={collapsed ? "Expand sidebar" : "Collapse sidebar"}
          >
            {collapsed ? <PanelLeftOpen className="h-[18px] w-[18px]" /> : <PanelLeftClose className="h-[18px] w-[18px]" />}
          </button>
        </div>

        {/* Navigation Items (Sections: Channels, DMs, Apps) */}
        <nav className={`flex-1 space-y-4 overflow-y-auto py-3 max-md:flex max-md:items-center max-md:gap-2 max-md:overflow-x-auto max-md:space-y-0 max-md:px-3 max-md:py-2 ${collapsed ? "px-2" : "px-3"}`}>
          {permissionsLoading ? (
            <p className="px-3 py-2 text-xs text-[#D1D2D3]" role="status">
              Checking access...
            </p>
          ) : (
            <>
              {/* Section 1: Channels (Trays) */}
              {visibleTrays.length > 0 && (
                <div className="space-y-0.5 max-md:flex max-md:shrink-0 max-md:items-center max-md:gap-1 max-md:space-y-0">
                  {!collapsed && (
                    <div className="flex items-center justify-between px-3 py-1.5">
                      <button
                        type="button"
                        onClick={() => setActiveSection("trays")}
                        className={`text-[12px] font-bold uppercase tracking-wider transition-colors hover:text-white ${
                          currentSection === "trays"
                            ? "text-white font-extrabold"
                            : "text-[#D1D2D3]/80"
                        }`}
                        title="View all trays"
                        aria-label="All trays overview"
                      >
                        Channels
                      </button>
                      {/* + Button on the right of Channels header */}
                      {canCreateTray && (
                        <button
                          type="button"
                          onClick={() => setIsCreateTrayModalOpen(true)}
                          className="flex h-5 w-5 shrink-0 items-center justify-center rounded-[4px] text-[#D1D2D3] hover:bg-white/10 hover:text-white transition-colors"
                          title="Create new tray"
                          aria-label="Create tray"
                        >
                          <Plus className="h-3.5 w-3.5" />
                        </button>
                      )}
                    </div>
                  )}

                  {/* Render Tray Nav Items */}
                  {visibleTrays.map((tray) => {
                    const isTrash = tray.id === "trash" || tray.name.toLowerCase() === "trash";
                    const item: WorkspaceNavItem = {
                      id: tray.id,
                      label: tray.name,
                      icon: isTrash ? Trash2 : Folder,
                      permission: isTrash ? "documents.delete" : "documents.read",
                      count: tray.itemCount ? String(tray.itemCount) : undefined,
                    };
                    const isActive = currentSection === tray.id || currentSection === tray.name.toLowerCase();

                    return (
                      <NavItem
                        key={tray.id}
                        item={item}
                        collapsed={collapsed}
                        active={isActive}
                        isChannel={!isTrash}
                        onClick={() => setActiveSection(tray.id)}
                      />
                    );
                  })}
                </div>
              )}

              {/* Section 2: Direct Messages (Partners) */}
              {visibleMainNavItems.some((item) => item.id === "partners") && (
                <div className="space-y-0.5 max-md:flex max-md:shrink-0 max-md:items-center max-md:gap-1 max-md:space-y-0">
                  {!collapsed && (
                    <p className="px-3 py-1.5 text-[12px] font-bold uppercase tracking-wider text-[#D1D2D3]/80 max-md:hidden">
                      Direct messages
                    </p>
                  )}
                  {visibleMainNavItems
                    .filter((item) => item.id === "partners")
                    .map((item) => (
                      <NavItem
                        key={item.id}
                        item={item}
                        collapsed={collapsed}
                        active={currentSection === item.id}
                        isDM={true}
                        onClick={() => setActiveSection(item.id)}
                      />
                    ))}
                </div>
              )}

              {/* Section 3: Apps & Tools (Review, AI Search, Connected Emails, Activity Log, Settings) */}
              <div className="space-y-0.5 max-md:flex max-md:shrink-0 max-md:items-center max-md:gap-1 max-md:space-y-0">
                {!collapsed && (
                  <p className="px-3 py-1.5 text-[12px] font-bold uppercase tracking-wider text-[#D1D2D3]/80 max-md:hidden">
                    Apps
                  </p>
                )}
                {visibleMainNavItems
                  .filter((item) => item.id !== "partners")
                  .map((item) => (
                    <NavItem
                      key={item.id}
                      item={item}
                      collapsed={collapsed}
                      active={currentSection === item.id}
                      onClick={() => setActiveSection(item.id)}
                    />
                  ))}
              </div>
            </>
          )}
        </nav>

        {/* Back Link */}
        <div className={`border-t border-[#3F1041] py-3 ${collapsed ? "px-2" : "px-3"}`}>
          <Link
            to="/tenants"
            title={collapsed ? "All tenants" : undefined}
            className={`flex items-center gap-2.5 rounded-[12px] px-3 py-2 text-[14px] font-medium text-[#D1D2D3] transition-colors hover:bg-white/10 hover:text-white ${collapsed ? "justify-center px-0" : ""}`}
          >
            <ArrowLeft className="h-[18px] w-[18px] shrink-0" />
            {!collapsed && <span>All tenants</span>}
          </Link>
        </div>
      </aside>

      {/* Main Content Area: Pure Canvas (#FFFFFF) */}
      <main className="min-w-0 flex-1 flex flex-col bg-white">
        {permissionsLoading ? (
          <div className="mx-auto flex min-h-[320px] max-w-[1440px] items-center justify-center px-5 py-7 text-sm text-gray-500 dark:text-gray-400" role="status">
            Checking workspace access...
          </div>
        ) : permissionsError ? (
          <div className="mx-auto max-w-[1440px] px-5 py-7 sm:px-8 sm:py-9 lg:px-10">
            <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-900/60 dark:bg-red-950/30 dark:text-red-400" role="alert">
              {permissionsError}
            </div>
          </div>
        ) : visibleSections.length === 0 ? (
          <div className="mx-auto max-w-[1440px] px-5 py-7 sm:px-8 sm:py-9 lg:px-10">
            <div className="rounded-xl border border-gray-200 bg-white p-4 text-sm text-gray-600 dark:border-gray-800 dark:bg-gray-900 dark:text-gray-300" role="status">
              You don’t have permission to view any workspace sections.
            </div>
          </div>
        ) : (
          <div className="mx-auto w-full max-w-[1440px] px-5 py-6 sm:px-8 sm:py-8 lg:px-10 flex-1 flex flex-col">
            {/* Top Workspace Header (for non-tray views, not "trays" which has its own header) */}
            {!activeTray && currentSection !== "trays" && (
              <div className="mb-6 flex flex-col gap-4 border-b border-[#DDDDDD] pb-5 sm:flex-row sm:items-end sm:justify-between">
                <div>
                  <div className="mb-1.5 flex items-center gap-2 text-[12px] text-[#616061]">
                    <span>{tenantName}</span>
                    <ChevronRight className="h-3.5 w-3.5 text-[#DDDDDD]" />
                    <span className="font-semibold text-[#1164A3]">
                      {sectionLabels[currentSection] || currentSection}
                    </span>
                  </div>
                  <h1 className="text-[22px] font-bold tracking-tight text-[#1D1C1D] leading-tight">
                    {currentSection === "review" ? getGreeting() : sectionLabels[currentSection] || currentSection}
                  </h1>
                  <p className="mt-1 text-[14px] text-[#616061]">
                    {sectionDescription(currentSection)}
                  </p>
                </div>
                {currentSection === "review" && (
                  <div className="flex items-center gap-2 self-start rounded-[8px] border border-[#DDDDDD] bg-white px-3 py-1.5 text-[12px] font-medium text-[#1D1C1D] shadow-[0_1px_2px_rgba(0,0,0,0.05)] sm:self-auto">
                    <ShieldCheck className="h-4 w-4 text-[#2EB67D]" />
                    Private workspace
                  </div>
                )}
              </div>
            )}

            {/* Dynamic Content View */}
            <div key={currentSection} className="workspace-content-enter flex-1 flex flex-col">
              {currentSection === "review" && <ReviewContent />}
              {currentSection === "ai-search" && (
                <WorkspaceAiSearch slug={slug || ""} token={token} />
              )}
              {currentSection === "partners" && <PartnersContent canInvite={hasPermission(permissions, "members.invite")} />}
              {currentSection === "emails" && <ConnectedEmailsContent />}
              {currentSection === "activity" && <ActivityLog slug={slug || ""} token={token} />}
              {currentSection === "settings" && token && (
                <OrganizationSettings
                  slug={slug || ""}
                  token={token}
                  tenantName={tenantName}
                  permissions={permissions}
                  trays={visibleTrays}
                  initialTab={linkedTab}
                  onOwnershipTransferred={() => setPermissionsVersion((version) => version + 1)}
                />
              )}
              {currentSection === "trays" && (
                <TraysOverview
                  trays={visibleTrays}
                  onSelectTray={(trayId) => setActiveSection(trayId)}
                  canCreateTray={canCreateTray}
                  canDeleteTray={canDeleteTray}
                  onDeleteTray={handleDeleteTray}
                  onCreateTrayClick={() => setIsCreateTrayModalOpen(true)}
                />
              )}

              {/* If active section is a Tray, render FolderBrowser */}
              {activeTray && activeTray.isDefault && activeTray.name === "Trash" && token && (
                <TrashView slug={slug!} token={token} />
              )}
              {activeTray && !(activeTray.isDefault && activeTray.name === "Trash") && (
                <FolderBrowser
                  slug={slug!}
                  token={token}
                  activeTray={activeTray}
                  canCreateFolder={canCreateFolder}
                  canDeleteFolder={canDeleteFolder}
                  canDeleteTray={canDeleteTray}
                  canUpload={canUpload}
                  canUpdateDocuments={hasPermission(permissions, "documents.update")}
                  canCopyDocuments={hasPermission(permissions, "documents.create")}
                  canUpdateFolders={hasPermission(permissions, "folders.update")}
                  canDeleteDocuments={hasPermission(permissions, "documents.delete")}
                  canOverrideLocks={hasPermission(permissions, "retention.override")}
                  canApplyLegalHold={hasPermission(permissions, "retention.manage")}
                  trays={visibleTrays.filter((tray) => tray.name.toLowerCase() !== "trash")}
                  initialFolderId={activeTray.id === routeState?.openTrayId ? routeState.openFolderId : undefined}
                  onDeleteTray={handleDeleteTray}
                  onAskAi={handleAskAi}
                />
              )}
            </div>
          </div>
        )}
      </main>

      {/* Modal for creating a new Tray */}
      <CreateTrayModal
        isOpen={isCreateTrayModalOpen}
        onClose={() => setIsCreateTrayModalOpen(false)}
        onSubmit={handleCreateTraySubmit}
      />

      {/* AI Chat Panel */}
      <AiChatPanel
        isOpen={aiPanel.isOpen}
        onClose={() => setAiPanel((prev) => ({ ...prev, isOpen: false }))}
        slug={slug || ""}
        token={token}
        documentId={aiPanel.documentId}
        documentName={aiPanel.documentName}
      />
    </div>
  );
}

// Returns a short description for each section
function sectionDescription(section: string) {
  const descriptions: Record<string, string> = {
    review: "Here’s what needs your attention in this workspace.",
    "ai-search": "Search across all documents with AI-powered answers.",
    general: "Documents and records in your General tray.",
    trash: "Items removed from this workspace appear here.",
    partners: "People and organizations you work with.",
    emails: "Email accounts connected to this workspace.",
    settings: "People, retention and legal holds in this organization, and changes that are hard to undo.",
  };
  return descriptions[section] || "Manage workspace documents and folders.";
}

// The "Review" section
function ReviewContent() {
  return (
    <>
      <div className="mb-6 grid gap-4 sm:grid-cols-3">
        <SummaryCard label="Waiting for review" value="6" icon={Inbox} detail="Across 3 matters" />
        <SummaryCard label="Reviewed this month" value="24" icon={Check} detail="You’re making great progress" />
        <SummaryCard label="Recently added" value="8" icon={Clock3} detail="In the last 7 days" />
      </div>

      <section className="overflow-hidden rounded-[8px] border border-[#DDDDDD] bg-white shadow-[0_1px_2px_rgba(0,0,0,0.05)]">
        <div className="flex flex-col gap-3 border-b border-[#DDDDDD] px-5 py-3.5 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <div>
            <h2 className="text-[16px] font-bold text-[#1D1C1D]">Needs your review</h2>
            <p className="mt-0.5 text-[12px] text-[#616061]">Documents recently added to your workspace</p>
          </div>
          <div className="flex items-center gap-2 rounded-[6px] border border-[#DDDDDD] px-3 py-1.5 text-[12px] text-[#616061]">
            <Search className="h-3.5 w-3.5" />
            <span>Search documents</span>
          </div>
        </div>
        <div className="divide-y divide-[#DDDDDD]">
          {documents.map((document) => (
            <div key={document.name} className="flex flex-col gap-3 px-5 py-3.5 transition-colors hover:bg-[#F8F8F8] sm:flex-row sm:items-center sm:px-6">
              <div className="flex min-w-0 flex-1 items-center gap-3">
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[8px] bg-[#36C5F0]/15 text-[#1164A3]">
                  <FileText className="h-4 w-4" />
                </div>
                <div className="min-w-0">
                  <p className="truncate text-[14px] font-semibold text-[#1D1C1D]">{document.name}</p>
                  <p className="mt-0.5 text-[12px] text-[#616061]">{document.detail}</p>
                </div>
              </div>
              <div className="flex items-center justify-between gap-4 pl-[48px] sm:pl-0">
                <span className={`rounded-[4px] px-2 py-0.5 text-[11px] font-bold ${
                  document.memberStatus === "Needs review"
                    ? "bg-[#ECB22E]/20 text-[#8f6200] border border-[#ECB22E]/30"
                    : "bg-[#36C5F0]/20 text-[#0c7ea5] border border-[#36C5F0]/30"
                }`}>
                  {document.memberStatus}
                </span>
                <span className="w-20 text-right text-[12px] text-[#616061]">{document.date}</span>
              </div>
            </div>
          ))}
        </div>
        <div className="flex items-center justify-between border-t border-[#DDDDDD] bg-[#F8F8F8] px-5 py-2.5 sm:px-6">
          <span className="text-[12px] text-[#616061]">Showing 3 of 6 items</span>
          <button type="button" className="text-[12px] font-bold text-[#1164A3] transition-colors hover:underline">
            View review queue <ChevronRight className="ml-0.5 inline h-3.5 w-3.5" />
          </button>
        </div>
      </section>
    </>
  );
}

// The "Partners" section
function PartnersContent({ canInvite }: { canInvite: boolean }) {
  return (
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      {partners.map((partner) => (
        <article key={partner.name} className="rounded-[8px] border border-[#DDDDDD] bg-white p-5 shadow-[0_1px_2px_rgba(0,0,0,0.05)] transition-all duration-150 hover:bg-[#F8F8F8]">
          <div className="flex items-start justify-between">
            <div className={`flex h-10 w-10 items-center justify-center rounded-[8px] text-[13px] font-bold bg-[#1164A3] text-white`}>
              {partner.initials}
            </div>
            <span className="flex items-center gap-1 rounded-[4px] bg-[#2EB67D]/15 border border-[#2EB67D]/30 px-2 py-0.5 text-[11px] font-bold text-[#1b7a51]">
              <span className="h-1.5 w-1.5 rounded-full bg-[#2EB67D]" />
              Active
            </span>
          </div>
          <h2 className="mt-4 text-[15px] font-bold text-[#1D1C1D]">{partner.name}</h2>
          <p className="mt-1 text-[12px] text-[#616061]">{partner.role}</p>
          <div className="mt-4 flex items-center justify-between border-t border-[#DDDDDD] pt-3 text-[12px] text-[#616061]">
            <span>Shared workspace access</span>
            <ChevronRight className="h-4 w-4" />
          </div>
        </article>
      ))}
      {canInvite && (
        <button type="button" className="flex min-h-[170px] flex-col items-center justify-center rounded-[8px] border border-dashed border-[#DDDDDD] bg-white p-5 text-[14px] font-medium text-[#616061] transition-colors hover:border-[#1164A3] hover:text-[#1164A3]">
          <span className="mb-2 flex h-9 w-9 items-center justify-center rounded-[8px] bg-[#F8F8F8]">
            <Plus className="h-4 w-4" />
          </span>
          Add a partner
        </button>
      )}
    </div>
  );
}

// The "Connected Emails" section
function ConnectedEmailsContent() {
  return (
    <div className="space-y-3">
      {emailAccounts.map((account) => (
        <article key={account.address} className="flex flex-col gap-4 rounded-[8px] border border-[#DDDDDD] bg-white p-4 shadow-[0_1px_2px_rgba(0,0,0,0.05)] sm:flex-row sm:items-center">
          <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-[8px] text-[13px] font-bold bg-[#36C5F0]/20 text-[#1164A3]`}>
            {account.initial}
          </div>
          <div className="min-w-0 flex-1">
            <h2 className="truncate text-[15px] font-bold text-[#1D1C1D]">{account.address}</h2>
            <p className="mt-0.5 text-[12px] text-[#616061]">{account.provider} · Connected to this workspace</p>
          </div>
          <span className="flex items-center gap-1.5 self-start rounded-[4px] bg-[#2EB67D]/15 border border-[#2EB67D]/30 px-2 py-0.5 text-[11px] font-bold text-[#1b7a51] sm:self-auto">
            <span className="h-1.5 w-1.5 rounded-full bg-[#2EB67D]" />
            Connected
          </span>
        </article>
      ))}
      <button type="button" className="flex w-full items-center justify-center gap-2 rounded-[8px] border border-dashed border-[#DDDDDD] bg-white px-4 py-3 text-[14px] font-medium text-[#616061] transition-colors hover:border-[#1164A3] hover:text-[#1164A3]">
        <Plus className="h-4 w-4" /> Connect an email account
      </button>
    </div>
  );
}

// Summary card for review dashboard
function SummaryCard({ label, value, icon: Icon, detail }: { label: string; value: string; icon: LucideIcon; detail: string }) {
  return (
    <article className="rounded-[8px] border border-[#DDDDDD] bg-white p-4 shadow-[0_1px_2px_rgba(0,0,0,0.05)] transition-shadow duration-150 hover:bg-[#F8F8F8]">
      <div className="flex items-center justify-between">
        <span className="text-[12px] font-semibold text-[#616061]">{label}</span>
        <Icon className="h-4 w-4 text-[#616061]" strokeWidth={1.8} />
      </div>
      <p className="mt-3 text-[24px] font-extrabold tracking-tight text-[#1D1C1D] leading-none">{value}</p>
      <p className="mt-1.5 text-[12px] text-[#616061]">{detail}</p>
    </article>
  );
}