import { useLocation, Link } from "react-router-dom";
import { CloseIcon } from "./icons";

interface SidebarItem {
  label: string;
  path: string;
}

interface SidebarProps {
  isOpen: boolean;
  setIsOpen: (open: boolean) => void;
}

const SIDEBAR_CONFIG: Record<string, SidebarItem[]> = {
  "/tenants": [
    { label: "All Tenants", path: "/tenants" },
    { label: "Create New", path: "/tenants/create" },
  ],
  "/settings": [
    { label: "General Settings", path: "/settings" },
    { label: "Security", path: "/settings/security" },
  ],
};

export function Sidebar({ isOpen, setIsOpen }: SidebarProps) {
  const location = useLocation();

  const getPageOptions = (): SidebarItem[] => {
    const path = location.pathname;
    
    // Match exact path or start of path for grouped options
    for (const [route, options] of Object.entries(SIDEBAR_CONFIG)) {
      if (path.startsWith(route)) {
        return options;
      }
    }

    return [];
  };

  const options = getPageOptions();

  return (
    <aside 
      className={`
        fixed left-0 top-0 h-full z-50 transition-all duration-150 ease-in-out
        border-r border-[var(--color-line)] bg-[var(--color-paper)]
        ${isOpen ? "w-64" : "w-0 -translate-x-full"}
      `}
    >
      <div className={`h-full flex flex-col ${!isOpen && "invisible"}`}>
        {/* Sidebar Header */}
        <div className="h-16 flex items-center justify-between px-4 border-b border-[var(--color-line)]">
          <span className="serif font-semibold text-[var(--color-ink)] text-lg">Registry</span>
          <button 
            onClick={() => setIsOpen(false)}
            className="p-1 rounded-md hover:bg-[var(--color-line)] text-[var(--color-muted)] transition-colors"
            aria-label="Close sidebar"
          >
            <CloseIcon className="w-5 h-5" />
          </button>
        </div>

        {/* Sidebar Content */}
        <div className="flex-1 overflow-y-auto p-4">
          {options.length > 0 ? (
            <nav className="space-y-1">
              {options.map((item) => (
                <Link
                  key={item.path}
                  to={item.path}
                  className={`
                    block px-3 py-2 rounded-md text-sm font-medium transition-colors
                    ${location.pathname === item.path 
                      ? "bg-[var(--color-line)] text-[var(--color-vault)]" 
                      : "text-[var(--color-muted)] hover:bg-[var(--color-line)] hover:text-[var(--color-ink)]"}
                  `}
                >
                  {item.label}
                </Link>
              ))}
            </nav>
          ) : (
            <div className="text-sm text-[var(--color-muted)] italic px-3">
              No registry options available for this section.
            </div>
          )}
        </div>
      </div>
    </aside>
  );
}
