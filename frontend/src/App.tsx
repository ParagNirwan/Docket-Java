import { useState } from "react";
import { Sidebar } from "./components/Sidebar";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider } from "./context/AuthContext";
import { Header } from "./components/Header";
import { AccountSettingsModal } from "./components/AccountSettingsModal";
import { LoginPage } from "./pages/LoginPage";
import { RegisterPage } from "./pages/RegisterPage";
import { CreateFirstTenantPage } from "./pages/CreateFirstTenantPage";
import { TenantsPage } from "./pages/TenantsPage";
import { TenantWorkspace } from "./pages/TenantWorkspace";
import { SettingsPage } from "./pages/SettingsPage";
import { ProtectedRoute } from "./components/ProtectedRoute";
import { GuestRoute } from "./components/GuestRoute";
import { Toaster } from "sonner";
import "./App.css";

function App() {
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);

  return (
    <AuthProvider>
      <BrowserRouter>
        <Toaster richColors position="top-right" closeButton />
        <div className="min-h-screen flex flex-col bg-gray-50/50 dark:bg-gray-950 text-gray-900 dark:text-gray-100 transition-colors duration-200">
          {/* Header is always visible across all pages */}
          <Header />

          {/* Sidebar */}
          <Sidebar isOpen={isSidebarOpen} setIsOpen={setIsSidebarOpen} />

          {/* Main Page Area */}
          <main className={`flex-1 flex flex-col transition-all duration-150 ${isSidebarOpen ? "ml-64" : "ml-0"}`}>
            <Routes>
              {/* Public Routes */}
              <Route
                path="/register"
                element={
                  <GuestRoute>
                    <RegisterPage />
                  </GuestRoute>
                }
              />
              <Route
                path="/login"
                element={
                  <GuestRoute>
                    <LoginPage />
                  </GuestRoute>
                }
              />

              {/* Protected Routes */}
              <Route
                path="/create-first-tenant"
                element={
                  <ProtectedRoute>
                    <CreateFirstTenantPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/tenants"
                element={
                  <ProtectedRoute>
                    <TenantsPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/workspace/:slug"
                element={
                  <ProtectedRoute>
                    <TenantWorkspace />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/settings"
                element={
                  <ProtectedRoute>
                    <SettingsPage />
                  </ProtectedRoute>
                }
              />

              {/* Catch-all redirect */}
              <Route path="*" element={<Navigate to="/register" replace />} />
            </Routes>
          </main>

          {/* Global Interactive Account Settings Modal */}
          <AccountSettingsModal />
        </div>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
