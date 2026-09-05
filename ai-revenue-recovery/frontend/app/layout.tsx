import type { Metadata } from "next";
import { IBM_Plex_Sans, IBM_Plex_Serif } from "next/font/google";
import Link from "next/link";
import "./globals.css";

const plexSans = IBM_Plex_Sans({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
  variable: "--font-plex-sans",
});

const plexSerif = IBM_Plex_Serif({
  subsets: ["latin"],
  weight: ["500", "600"],
  variable: "--font-plex-serif",
});

export const metadata: Metadata = {
  title: "Revenue Recovery — AI Revenue Recovery Platform",
  description: "Detects revenue at risk, proposes bounded recovery interventions, and proves what was recovered.",
};

const NAV_ITEMS = [
  { href: "/", label: "Overview", icon: "▦" },
  { href: "/cases", label: "Recovery", icon: "◔" },
  { href: "/simulator", label: "Simulator", icon: "◇" },
  { href: "/analytics", label: "Analytics", icon: "▥" },
  { href: "/settings", label: "Settings", icon: "⚙" },
];

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={`${plexSans.variable} ${plexSerif.variable}`}>
      <body>
        <div className="min-h-screen flex bg-paper">
              <aside className="app-sidebar">
                <Link href="/" className="brand-lockup">
                  <span className="brand-mark">◌</span>
                  <span><strong>ClearPath</strong><small>Revenue intelligence</small></span>
                </Link>
                <nav className="sidebar-nav" aria-label="Primary navigation">
                  <span className="nav-label">Workspace</span>
                  {NAV_ITEMS.map((item) => (
                    <Link key={item.href} href={item.href} className="nav-item">
                      <span className="nav-icon">{item.icon}</span>{item.label}
                    </Link>
                  ))}
                </nav>
                <div className="sidebar-bottom">
                  <div className="health-indicator"><span /> Systems healthy</div>
                  <div className="user-chip"><span className="avatar">JR</span><span><strong>Jordan Rao</strong><small>Operations</small></span></div>
                </div>
              </aside>
              <div className="app-content">
                <header className="topbar"><div><span className="eyebrow">Revenue operations</span><span className="topbar-title">AI Revenue Recovery</span></div><div className="topbar-actions"><button aria-label="Search">⌕</button><button aria-label="Notifications">♧</button></div></header>
                <main className="page-wrap">{children}</main>
              </div>
        </div>
      </body>
    </html>
  );
}
