import "./globals.css";

export const metadata = {
  title: "Game AI Coach",
  description: "Central inteligente para evolução automática de contas de jogos",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="pt-BR">
      <body>{children}</body>
    </html>
  );
}
