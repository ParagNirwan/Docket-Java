import { useState } from "react";
import { Sidebar } from "./components/Sidebar";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider } from "./context/AuthContext";
import { Header } from "./components/Header";
import { AccountSettingsModal } from "./components/AccountSettingsModal";
import { LoginPage } from "./pages/LoginPage";
import { RegisterPage } from "./pages/RegisterPage";
import { ForgotPasswordPage } from "./pages/ForgotPasswordPage";
import { ResetPasswordPage } from "./pages/ResetPasswordPage";
import { VerifyEmailPage } from "./pages/VerifyEmailPage";
import { AcceptInvitePage } from "./pages/AcceptInvitePage";
import { PublicSharePage } from "./pages/PublicSharePage";
import { SearchPage } from "./pages/SearchPage";
import { EmailVerificationBanner } from "./components/EmailVerificationBanner";
import { CreateFirstTenantPage } from "./pages/CreateFirstTenantPage";
import { TenantsPage } from "./pages/TenantsPage";
import { TenantWorkspace } from "./pages/TenantWorkspace";
import { SettingsPage } from "./pages/SettingsPage";
import { ProtectedRoute } from "./components/ProtectedRoute";
import { GuestRoute } from "./components/GuestRoute";
import { Toaster } from "sonner";
import { ThemeProvider } from "./context/ThemeProvider";
import { useTheme } from "./context/ThemeContext";

function App() {
  return (
    <ThemeProvider>
      <AppContent />
    </ThemeProvider>
  );
}

function AppContent() {
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);
  const { resolvedTheme } = useTheme();

  return (
    <AuthProvider>
      <BrowserRouter>
        <Toaster theme={resolvedTheme} richColors position="top-right" closeButton />
        <div className="min-h-screen flex flex-col bg-canvas text-ink">
          <Header />
          <EmailVerificationBanner />
          <Sidebar isOpen={isSidebarOpen} setIsOpen={setIsSidebarOpen} />

          <main className={`flex-1 flex flex-col transition-all duration-150 ${isSidebarOpen ? "ml-64" : "ml-0"}`}>
            <Routes>
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
              <Route
                path="/forgot-password"
                element={
                  <GuestRoute>
                    <ForgotPasswordPage />
                  </GuestRoute>
                }
              />
              <Route path="/reset-password" element={<ResetPasswordPage />} />
              <Route path="/verify-email" element={<VerifyEmailPage />} />
              <Route path="/invite" element={<AcceptInvitePage />} />
              <Route path="/share/:token" element={<PublicSharePage />} />
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
                path="/workspace/:slug/search"
                element={
                  <ProtectedRoute>
                    <SearchPage />
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
              <Route path="*" element={<Navigate to="/register" replace />} />
            </Routes>
          </main>
          <AccountSettingsModal />
        </div>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
