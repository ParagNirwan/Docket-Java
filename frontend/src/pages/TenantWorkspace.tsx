import { useState, useEffect } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { hasPermission, type PermissionCode } from "../lib/permissions";
import {
  ArrowDownToLine,
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
  ShieldCheck,
  Trash2,
  UsersRound,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import "./TenantWorkspace.css";

// The possible sections a workspace can show.
type WorkspaceSection = "review" | "general" | "trash" | "partners" | "emails";

// Shape of each sidebar navigation item.
// The "permission" field controls visibility — if the user doesn't have
// that permission, the item won't appear in the sidebar at all.
interface WorkspaceNavItem {
  id: WorkspaceSection;
  label: string;
  icon: LucideIcon;
  permission: PermissionCode | null;
  count?: string;
}

// Sidebar items under the "Trays" group.
// "General" requires documents.read, "Trash" requires documents.delete.
const trayItems: WorkspaceNavItem[] = [
  { id: "general", label: "General", icon: Folder, permission: "documents.read", count: "12" },
  { id: "trash", label: "Trash", icon: Trash2, permission: "documents.delete" },
];

// Sample/placeholder data for the UI. These will be replaced with real
// data from the backend once the documents feature is built.
const documents = [
  { name: "Board resolution — Q3 approval", detail: "Corporate governance · PDF", date: "Today", status: "Needs review" },
  { name: "Master services agreement", detail: "Commercial · DOCX", date: "Yesterday", status: "Needs review" },
  { name: "Annual filing receipt", detail: "Company records · PDF", date: "Oct 10", status: "In progress" },
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

const sectionLabels: Record<WorkspaceSection, string> = {
  review: "Review",
  general: "General",
  trash: "Trash",
  partners: "Partners",
  emails: "Connected Emails",
};

// A single sidebar button. Shows an icon, label, and optional count badge.
// When the sidebar is collapsed (narrow), it hides the text and just shows the icon.
// The currently active item gets highlighted in indigo/purple.
function NavItem({
  item,
  collapsed,
  active,
  onClick,
}: {
  item: WorkspaceNavItem;
  collapsed: boolean;
  active: boolean;
  onClick: () => void;
}) {
  const Icon = item.icon;

  return (
    <button
      type="button"
      onClick={onClick}
      title={collapsed ? item.label : undefined}
      aria-label={item.label}
      aria-current={active ? "page" : undefined}
      className={`flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm font-medium transition-colors duration-200 ${
        active
          ? "bg-indigo-50 text-indigo-700 dark:bg-indigo-950/60 dark:text-indigo-300"
          : "text-gray-600 hover:bg-gray-100 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-100"
      } ${collapsed ? "justify-center px-0 max-md:w-10" : "max-md:w-auto max-md:shrink-0 max-md:px-3"}`}
    >
      <Icon className="h-[18px] w-[18px] shrink-0" strokeWidth={1.8} />
      {!collapsed && (
        <>
          <span className="min-w-0 flex-1 truncate">{item.label}</span>
          {item.count && (
            <span className={`rounded-md px-1.5 py-0.5 text-[11px] ${active ? "bg-white/80 text-indigo-600 dark:bg-indigo-900/70 dark:text-indigo-300" : "text-gray-400 dark:text-gray-500"}`}>
              {item.count}
            </span>
          )}
        </>
      )}
    </button>
  );
}

// The main workspace page. When a user opens an organization, this page:
// 1. Fetches the user's permissions from the backend
// 2. Shows only the sidebar items and buttons the user is allowed to see
// 3. Displays the content for whichever section is currently selected
export function TenantWorkspace() {
  // Get the org's URL name (slug) from the address bar, e.g. "/workspace/acme-corp"
  const { slug } = useParams();
  // Get the logged-in user's authentication token
  const { token } = useAuth();
  const location = useLocation();
  // If the user navigated here from the tenant list, the org name may have been
  // passed along so we can show it immediately without waiting for the API.
  const routeState = location.state as { tenantName?: string } | null;
  const [collapsed, setCollapsed] = useState(false);
  const [activeSection, setActiveSection] = useState<WorkspaceSection>("review");
  // Stores the user's permissions once fetched from the backend.
  // Starts in a "loading" state with an empty permission set.
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

  // As soon as the page opens, fetch two things from the backend at once:
  // 1. "What permissions does the logged-in user have in this org?"
  // 2. "What's this organization's info (name, etc.)?"
  // Doing both at the same time makes the page load faster.
  useEffect(() => {
    // AbortController lets us cancel the requests if the user navigates
    // away before they finish — prevents errors on a page you've left.
    const controller = new AbortController();
    if (!slug || !token) {
      return () => controller.abort();
    }

    const apiUrl = import.meta.env.VITE_API_URL || "/api";
    const encodedSlug = encodeURIComponent(slug);
    const headers = { Authorization: `Bearer ${token}` };
    Promise.all([
      fetch(`${apiUrl}/tenants/by-slug/${encodedSlug}/permissions`, {
        headers,
        signal: controller.signal,
      }),
      fetch(`${apiUrl}/tenants/by-slug/${encodedSlug}`, {
        headers,
        signal: controller.signal,
      }),
    ])
      // If either request failed (not a member, org doesn't exist, etc.), show an error.
      .then(async ([permissionsResponse, tenantResponse]) => {
        if (!permissionsResponse.ok || !tenantResponse.ok) {
          throw new Error("Unable to verify workspace access.");
        }
        // Read both responses and sanity-check the permissions data.
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
      // Everything went well — save the permissions and org name.
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
      // Something went wrong — save the error message to display.
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
  }, [slug, token]);

  // Main sidebar items — each requires a specific permission to be visible.
  // "emails" has permission: null, meaning it's always shown (no permission needed).
  const mainNavItems: WorkspaceNavItem[] = [
    { id: "review", label: "Review", icon: Inbox, permission: "documents.read", count: "6" },
    { id: "partners", label: "Partners", icon: UsersRound, permission: "members.read" },
    { id: "emails", label: "Connected Emails", icon: Mail, permission: null, count: "2" },
  ];

  // Make sure the fetched permissions actually belong to the current page.
  // If the user navigated to a different org, we shouldn't use stale permissions.
  const accessStateMatchesRoute = permissionState.slug === slug && permissionState.token === token;
  const permissionsLoading = !!slug && !!token && (!accessStateMatchesRoute || permissionState.loading);
  const permissionsError = !slug || !token
    ? "Unable to verify workspace access."
    : accessStateMatchesRoute
      ? permissionState.error
      : null;
  const permissions = accessStateMatchesRoute ? permissionState.permissions : new Set<string>();

  // THE KEY PART: Filter sidebar items based on permissions.
  // Only keep the items the user has permission for. A basic Member with only
  // "tenant.read" and "documents.read" will see "Review" and "General" but
  // not "Trash," "Partners," etc.
  const visibleMainNavItems = mainNavItems.filter(
    (item) => item.permission !== null && hasPermission(permissions, item.permission),
  );
  const visibleTrayItems = trayItems.filter(
    (item) => item.permission !== null && hasPermission(permissions, item.permission),
  );
  const visibleSections = [...visibleMainNavItems, ...visibleTrayItems].map((item) => item.id);

  // If the user was looking at a section they no longer have access to,
  // switch them to the first available section.
  const currentSection = visibleSections.includes(activeSection) ? activeSection : visibleSections[0];

  return (
    <div className="flex min-h-[calc(100svh-4rem)] flex-1 flex-col bg-gray-50/80 dark:bg-gray-950 md:flex-row">
      <aside
        className={`flex shrink-0 flex-col border-r border-gray-200 bg-white transition-[width] duration-300 ease-in-out dark:border-gray-800 dark:bg-gray-900 ${
          collapsed ? "w-full md:w-[72px]" : "w-full md:w-[252px]"
        }`}
        aria-label="Workspace navigation"
      >
        <div className={`flex h-[72px] items-center border-b border-gray-100 dark:border-gray-800 ${collapsed ? "justify-center px-2" : "justify-between px-5"}`}>
          {!collapsed && (
            <div className="flex min-w-0 items-center gap-3">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-400">
                <Building2 className="h-[18px] w-[18px]" />
              </div>
              <div className="min-w-0">
                <p className="truncate text-sm font-semibold text-gray-900 dark:text-white">{tenantName}</p>
                <p className="text-[11px] text-gray-500 dark:text-gray-400">Workspace</p>
              </div>
            </div>
          )}
          <button
            type="button"
            onClick={() => setCollapsed((previous) => !previous)}
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-white"
            aria-label={collapsed ? "Expand workspace sidebar" : "Collapse workspace sidebar"}
            aria-expanded={!collapsed}
            title={collapsed ? "Expand sidebar" : "Collapse sidebar"}
          >
            {collapsed ? <PanelLeftOpen className="h-[18px] w-[18px]" /> : <PanelLeftClose className="h-[18px] w-[18px]" />}
          </button>
        </div>

        <nav className={`flex-1 space-y-6 overflow-y-auto py-5 max-md:flex max-md:items-center max-md:gap-2 max-md:overflow-x-auto max-md:space-y-0 max-md:px-3 max-md:py-2 ${collapsed ? "px-2" : "px-3"}`}>
          {permissionsLoading ? (
            <p className="px-3 py-2 text-sm text-gray-500 dark:text-gray-400" role="status">
              Checking access...
            </p>
          ) : (
            <>
              {visibleMainNavItems.filter((item) => item.id === "review").map((item) => (
                <div key={item.id} className="space-y-1 max-md:flex max-md:shrink-0 max-md:items-center max-md:gap-1 max-md:space-y-0">
                  <NavItem item={item} collapsed={collapsed} active={currentSection === item.id} onClick={() => setActiveSection(item.id)} />
                </div>
              ))}

              {visibleTrayItems.length > 0 && (
                <div className="space-y-1 max-md:flex max-md:shrink-0 max-md:items-center max-md:gap-1 max-md:space-y-0">
                  {!collapsed && <p className="px-3 pb-2 text-[10px] font-semibold uppercase tracking-[0.14em] text-gray-400 dark:text-gray-500 max-md:hidden">Trays</p>}
                  {visibleTrayItems.map((item) => (
                    <NavItem key={item.id} item={item} collapsed={collapsed} active={currentSection === item.id} onClick={() => setActiveSection(item.id)} />
                  ))}
                </div>
              )}

              {visibleMainNavItems.filter((item) => item.id !== "review").map((item) => (
                <div key={item.id} className="space-y-1 max-md:flex max-md:shrink-0 max-md:items-center max-md:gap-1 max-md:space-y-0">
                  {!collapsed && <p className="px-3 pb-2 text-[10px] font-semibold uppercase tracking-[0.14em] text-gray-400 dark:text-gray-500 max-md:hidden">Workspace</p>}
                  <NavItem item={item} collapsed={collapsed} active={currentSection === item.id} onClick={() => setActiveSection(item.id)} />
                </div>
              ))}
            </>
          )}
        </nav>

        <div className={`border-t border-gray-100 py-4 dark:border-gray-800 ${collapsed ? "px-2" : "px-3"}`}>
          <Link
            to="/tenants"
            title={collapsed ? "All tenants" : undefined}
            className={`flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-white ${collapsed ? "justify-center px-0" : ""}`}
          >
            <ArrowLeft className="h-[18px] w-[18px] shrink-0" />
            {!collapsed && <span>All tenants</span>}
          </Link>
        </div>
      </aside>

      {/* Main content area — shows one of four states:
          1. Loading ("Checking workspace access...")
          2. Error (red box with the error message)
          3. No permissions ("You don't have permission to view any workspace sections.")
          4. The actual workspace content for the selected section */}
      <main className="min-w-0 flex-1">
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
        <div className="mx-auto w-full max-w-[1440px] px-5 py-7 sm:px-8 sm:py-9 lg:px-10">
          <div className="mb-8 flex flex-col gap-4 border-b border-gray-200 pb-6 dark:border-gray-800 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <div className="mb-2 flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
                <span>{tenantName}</span>
                <ChevronRight className="h-3.5 w-3.5" />
                <span className="font-medium text-indigo-600 dark:text-indigo-400">{sectionLabels[currentSection]}</span>
              </div>
              <h1 className="text-3xl font-semibold tracking-tight text-gray-900 dark:text-white">
                {currentSection === "review" ? "Good afternoon" : sectionLabels[currentSection]}
              </h1>
              <p className="mt-1.5 text-sm text-gray-500 dark:text-gray-400">{sectionDescription(currentSection)}</p>
            </div>
            {currentSection === "review" && (
              <div className="flex items-center gap-2 self-start rounded-xl border border-gray-200 bg-white px-3 py-2 text-xs font-medium text-gray-600 shadow-xs dark:border-gray-800 dark:bg-gray-900 dark:text-gray-300 sm:self-auto">
                <ShieldCheck className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
                Private workspace
              </div>
            )}
          </div>

          {/* Show the content for whichever section is selected.
              Some sections pass extra permission checks — e.g. the Partners
              page only shows "Add a partner" if you have members.invite,
              and the General tray only shows "Export" if you have documents.read. */}
          <div key={currentSection} className="workspace-content-enter">
            {currentSection === "review" && <ReviewContent />}
            {currentSection === "general" && <GeneralTrayContent canExport={hasPermission(permissions, "documents.read")} />}
            {currentSection === "trash" && <TrashContent />}
            {currentSection === "partners" && <PartnersContent canInvite={hasPermission(permissions, "members.invite")} />}
            {currentSection === "emails" && <ConnectedEmailsContent />}
          </div>
        </div>
        )}
      </main>
    </div>
  );
}

// Returns a short description for each section, shown below the heading.
function sectionDescription(section: WorkspaceSection) {
  const descriptions: Record<WorkspaceSection, string> = {
    review: "Here’s what needs your attention in this workspace.",
    general: "Documents and records in your General tray.",
    trash: "Items removed from this workspace appear here.",
    partners: "People and organizations you work with.",
    emails: "Email accounts connected to this workspace.",
  };
  return descriptions[section];
}

// The "Review" section — shows summary cards and a list of documents needing review.
function ReviewContent() {
  return (
    <>
      <div className="mb-7 grid gap-4 sm:grid-cols-3">
        <SummaryCard label="Waiting for review" value="6" icon={Inbox} detail="Across 3 matters" />
        <SummaryCard label="Reviewed this month" value="24" icon={Check} detail="You’re making great progress" />
        <SummaryCard label="Recently added" value="8" icon={Clock3} detail="In the last 7 days" />
      </div>

      <section className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-xs dark:border-gray-800 dark:bg-gray-900">
        <div className="flex flex-col gap-3 border-b border-gray-100 px-5 py-4 dark:border-gray-800 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <div>
            <h2 className="text-base font-semibold text-gray-900 dark:text-white">Needs your review</h2>
            <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">Documents recently added to your workspace</p>
          </div>
          <div className="flex items-center gap-2 rounded-lg border border-gray-200 px-3 py-2 text-xs text-gray-500 dark:border-gray-700 dark:text-gray-400">
            <Search className="h-3.5 w-3.5" />
            <span>Search documents</span>
          </div>
        </div>
        <div className="divide-y divide-gray-100 dark:divide-gray-800">
          {documents.map((document) => (
            <div key={document.name} className="flex flex-col gap-3 px-5 py-4 transition-colors hover:bg-gray-50/80 dark:hover:bg-gray-800/40 sm:flex-row sm:items-center sm:px-6">
              <div className="flex min-w-0 flex-1 items-center gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-400">
                  <FileText className="h-[18px] w-[18px]" />
                </div>
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-gray-900 dark:text-gray-100">{document.name}</p>
                  <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">{document.detail}</p>
                </div>
              </div>
              <div className="flex items-center justify-between gap-4 pl-[52px] sm:pl-0">
                <span className={`rounded-full px-2.5 py-1 text-[11px] font-medium ${document.status === "Needs review" ? "bg-amber-50 text-amber-700 dark:bg-amber-950/50 dark:text-amber-300" : "bg-blue-50 text-blue-700 dark:bg-blue-950/50 dark:text-blue-300"}`}>
                  {document.status}
                </span>
                <span className="w-20 text-right text-xs text-gray-400 dark:text-gray-500">{document.date}</span>
              </div>
            </div>
          ))}
        </div>
        <div className="flex items-center justify-between border-t border-gray-100 bg-gray-50/70 px-5 py-3 dark:border-gray-800 dark:bg-gray-900/70 sm:px-6">
          <span className="text-xs text-gray-500 dark:text-gray-400">Showing 3 of 6 items</span>
          <button type="button" className="text-xs font-semibold text-indigo-600 transition-colors hover:text-indigo-700 dark:text-indigo-400 dark:hover:text-indigo-300">View review queue <ChevronRight className="ml-0.5 inline h-3.5 w-3.5" /></button>
        </div>
      </section>
    </>
  );
}

// The "General" tray — shows documents in a list.
// The "Export" button only appears if canExport is true (user has documents.read).
function GeneralTrayContent({ canExport }: { canExport: boolean }) {
  return (
    <section className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-xs dark:border-gray-800 dark:bg-gray-900">
      <div className="flex items-center justify-between border-b border-gray-100 px-5 py-4 dark:border-gray-800 sm:px-6">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-400"><Folder className="h-[18px] w-[18px]" /></div>
          <div>
            <h2 className="text-sm font-semibold text-gray-900 dark:text-white">General</h2>
            <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">12 items · Updated recently</p>
          </div>
        </div>
        {canExport && (
          <button type="button" className="flex items-center gap-2 rounded-lg border border-gray-200 px-3 py-2 text-xs font-medium text-gray-600 transition-colors hover:bg-gray-50 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-gray-800">
            <ArrowDownToLine className="h-3.5 w-3.5" /> Export
          </button>
        )}
      </div>
      <div className="divide-y divide-gray-100 dark:divide-gray-800">
        {documents.map((document) => (
          <div key={document.name} className="flex items-center gap-3 px-5 py-4 sm:px-6">
            <FileText className="h-[18px] w-[18px] shrink-0 text-gray-400" />
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-gray-800 dark:text-gray-200">{document.name}</p>
              <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">{document.detail}</p>
            </div>
            <span className="hidden text-xs text-gray-400 dark:text-gray-500 sm:block">{document.date}</span>
          </div>
        ))}
      </div>
    </section>
  );
}

// The "Trash" section — currently shows an empty state placeholder.
function TrashContent() {
  return (
    <section className="flex min-h-[320px] flex-col items-center justify-center rounded-2xl border border-dashed border-gray-300 bg-white px-6 text-center dark:border-gray-700 dark:bg-gray-900">
      <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400">
        <Trash2 className="h-6 w-6" strokeWidth={1.6} />
      </div>
      <h2 className="text-base font-semibold text-gray-900 dark:text-white">Your trash is empty</h2>
      <p className="mt-1 max-w-sm text-sm text-gray-500 dark:text-gray-400">When you remove documents from this workspace, they’ll be kept here until you restore or permanently delete them.</p>
    </section>
  );
}

// The "Partners" section — shows partner cards in a grid.
// The "Add a partner" button only appears if canInvite is true (user has members.invite).
// An Owner or Admin sees it; a basic Member does not.
function PartnersContent({ canInvite }: { canInvite: boolean }) {
  return (
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      {partners.map((partner) => (
        <article key={partner.name} className="rounded-2xl border border-gray-200 bg-white p-5 shadow-xs transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md dark:border-gray-800 dark:bg-gray-900">
          <div className="flex items-start justify-between">
            <div className={`flex h-11 w-11 items-center justify-center rounded-xl text-sm font-semibold ${partner.color}`}>{partner.initials}</div>
            <span className="flex items-center gap-1 rounded-full bg-emerald-50 px-2.5 py-1 text-[11px] font-medium text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300"><span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />Active</span>
          </div>
          <h2 className="mt-5 text-sm font-semibold text-gray-900 dark:text-white">{partner.name}</h2>
          <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">{partner.role}</p>
          <div className="mt-5 flex items-center justify-between border-t border-gray-100 pt-4 text-xs text-gray-500 dark:border-gray-800 dark:text-gray-400">
            <span>Shared workspace access</span><ChevronRight className="h-4 w-4" />
          </div>
        </article>
      ))}
      {canInvite && (
        <button type="button" className="flex min-h-[190px] flex-col items-center justify-center rounded-2xl border border-dashed border-gray-300 bg-white p-5 text-sm font-medium text-gray-500 transition-colors hover:border-indigo-300 hover:bg-indigo-50/30 hover:text-indigo-600 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-400 dark:hover:border-indigo-800 dark:hover:bg-indigo-950/20 dark:hover:text-indigo-400">
          <span className="mb-3 flex h-10 w-10 items-center justify-center rounded-xl bg-gray-100 dark:bg-gray-800"><Plus className="h-5 w-5" /></span>
          Add a partner
        </button>
      )}
    </div>
  );
}

// The "Connected Emails" section — shows linked email accounts
// and a button to connect a new one.
function ConnectedEmailsContent() {
  return (
    <div className="space-y-4">
      {emailAccounts.map((account) => (
        <article key={account.address} className="flex flex-col gap-4 rounded-2xl border border-gray-200 bg-white p-5 shadow-xs dark:border-gray-800 dark:bg-gray-900 sm:flex-row sm:items-center">
          <div className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl text-sm font-bold ${account.color}`}>{account.initial}</div>
          <div className="min-w-0 flex-1">
            <h2 className="truncate text-sm font-semibold text-gray-900 dark:text-white">{account.address}</h2>
            <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">{account.provider} · Connected to this workspace</p>
          </div>
          <span className="flex items-center gap-1.5 self-start rounded-full bg-emerald-50 px-2.5 py-1 text-[11px] font-medium text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300 sm:self-auto"><span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />Connected</span>
        </article>
      ))}
      <button type="button" className="flex w-full items-center justify-center gap-2 rounded-2xl border border-dashed border-gray-300 bg-white px-5 py-4 text-sm font-medium text-gray-600 transition-colors hover:border-indigo-300 hover:text-indigo-600 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-300 dark:hover:border-indigo-800 dark:hover:text-indigo-400">
        <Plus className="h-4 w-4" /> Connect an email account
      </button>
    </div>
  );
}

// A small stats card used in the Review section (e.g. "Waiting for review: 6").
function SummaryCard({ label, value, icon: Icon, detail }: { label: string; value: string; icon: LucideIcon; detail: string }) {
  return (
    <article className="rounded-2xl border border-gray-200 bg-white p-5 shadow-xs transition-shadow duration-200 hover:shadow-sm dark:border-gray-800 dark:bg-gray-900">
      <div className="flex items-center justify-between">
        <span className="text-xs font-medium text-gray-500 dark:text-gray-400">{label}</span>
        <Icon className="h-4 w-4 text-gray-400 dark:text-gray-500" strokeWidth={1.8} />
      </div>
      <p className="mt-4 text-2xl font-semibold tracking-tight text-gray-900 dark:text-white">{value}</p>
      <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">{detail}</p>
    </article>
  );
}