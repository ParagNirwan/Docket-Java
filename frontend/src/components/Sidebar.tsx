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
    { label: "Create New", path: "/create-first-tenant" },
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
        border-r border-[#3F1041] bg-[#4A154B] text-white shadow-xl
        ${isOpen ? "w-64" : "w-0 -translate-x-full"}
      `}
      aria-label="Sidebar navigation"
    >
      <div className={`h-full flex flex-col ${!isOpen && "invisible"}`}>
        {/* Sidebar Header */}
        <div className="h-16 flex items-center justify-between px-4 border-b border-[#3F1041]">
          <span className="font-bold text-white text-lg tracking-tight">Registry</span>
          <button 
            onClick={() => setIsOpen(false)}
            className="p-1.5 rounded-lg hover:bg-white/10 text-[#D1D2D3] hover:text-white transition-colors"
            aria-label="Close sidebar"
          >
            <CloseIcon className="w-5 h-5" />
          </button>
        </div>

        {/* Sidebar Content */}
        <div className="flex-1 overflow-y-auto p-3">
          {options.length > 0 ? (
            <nav className="space-y-1">
              <div className="px-3 py-1.5 text-xs font-bold uppercase tracking-wider text-[#D1D2D3]/70">
                Navigation
              </div>
              {options.map((item) => {
                const isActive = location.pathname === item.path;
                return (
                  <Link
                    key={item.path}
                    to={item.path}
                    className={`
                      block px-3 py-2 rounded-[12px] text-sm font-medium transition-colors
                      ${isActive
                        ? "bg-white/15 text-white font-bold"
                        : "text-[#D1D2D3] hover:bg-white/10 hover:text-white"}
                    `}
                  >
                    {item.label}
                  </Link>
                );
              })}
            </nav>
          ) : (
            <div className="text-xs text-[#D1D2D3] italic px-3 py-2">
              No registry options available for this section.
            </div>
          )}
        </div>
      </div>
    </aside>
  );
}
