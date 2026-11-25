import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import { Navbar } from "@/components/Navbar";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "StayScale — Hotels, Resorts & Stays",
  description:
    "End-to-end Agoda-style travel search and booking platform powered by PostgreSQL, Spring Boot, and Next.js.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="en"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col bg-slate-50 text-slate-900">
        <Navbar />
        <main className="flex-1">{children}</main>
        <footer className="border-t border-slate-200 bg-white py-6 text-xs text-slate-500">
          <div className="mx-auto flex max-w-7xl flex-col items-center justify-between gap-2 px-4 sm:flex-row sm:px-6">
            <p>
              <span className="font-semibold text-slate-700">StayScale</span> · Part 1:
              PostgreSQL Date-Partitioned Inventory &amp; Search
            </p>
            <p>Next up: Part 2 (10-min Redis Holds + Postgres Row Locks + Checkout)</p>
          </div>
        </footer>
      </body>
    </html>
  );
}
