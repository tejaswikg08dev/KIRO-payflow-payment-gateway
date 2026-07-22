# PayFlow Developer Portal

A Stripe-docs-inspired API reference site for the PayFlow Payment Gateway.

## Tech Stack

- **React 18** with TypeScript
- **Vite** for fast development and building
- **Tailwind CSS** for styling
- **React Router v6** for client-side routing

## Pages

| Route | Description |
|-------|-------------|
| `/` | Home — Welcome page with quick links |
| `/getting-started` | Step-by-step guide to first payment |
| `/authentication` | API key usage, test vs live keys |
| `/api-reference` | Full endpoint reference (Orders, Payments, Merchants, Settlements, Webhooks) |
| `/webhooks` | Event types, payload format, HMAC verification examples |

## Getting Started

```bash
# Install dependencies
npm install

# Start dev server (port 3002)
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

## Project Structure

```
frontend-developer-portal/
├── index.html
├── package.json
├── vite.config.ts
├── tsconfig.json
├── tailwind.config.js
├── postcss.config.js
└── src/
    ├── main.tsx
    ├── App.tsx
    ├── index.css
    ├── components/
    │   └── Sidebar.tsx
    └── pages/
        ├── HomePage.tsx
        ├── GettingStartedPage.tsx
        ├── ApiReferencePage.tsx
        ├── AuthenticationPage.tsx
        └── WebhooksPage.tsx
```

## Development

The dev server runs on **port 3002** by default. Open [http://localhost:3002](http://localhost:3002) to view it in the browser.

## Design

- **Left sidebar**: Dark slate navigation with active state highlighting
- **Main content area**: Clean white background with professional typography
- **Code blocks**: Dark background with syntax-highlighted examples
- **Responsive**: Works on desktop and tablet viewports
