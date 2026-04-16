# FinanceAI — Smart Bank Statement Dashboard

An AI-powered personal finance dashboard. Upload PDF or CSV bank statements, get automatic transaction categorization via Claude AI, visualize spending with charts, and download PDF/Excel reports.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Client | React 18, Tailwind CSS, Recharts, React Router |
| Server | Java 21, Spring Boot 3, Spring Security (JWT) |
| AI | Claude API (Anthropic) |
| PDF Parsing | Apache PDFBox |
| CSV Parsing | OpenCSV |
| Excel Export | Apache POI |
| PDF Export | OpenHTMLToPDF |
| Database | PostgreSQL (prod) / H2 (local dev) |
| Build | Gradle |
| Deploy | Vercel (Client) + Railway (Server + DB) |

---

## Local Development Setup

### Prerequisites
- Java 21
- Node.js 18+
- A Claude API key from [console.anthropic.com](https://console.anthropic.com)

### 1. Clone the repo
```bash
git clone https://github.com/yourusername/finance-dashboard.git
cd finance-dashboard
```

### 2. Start the Server
```bash
cd server

# Set your Claude API key (or add to application.properties)
export CLAUDE_API_KEY=your-key-here

# Run (uses H2 in-memory DB by default — no Postgres needed locally)
./gradlew bootRun
```

Client starts at `http://localhost:8080`.  
H2 console available at `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:financedb`).

### 3. Start the Client
```bash
cd client
npm install

# Create local env file
cp .env.example .env.local
# Edit .env.local: set REACT_APP_API_URL=http://localhost:8080

npm start
```

Client starts at `http://localhost:3000`.

---

## Deployment

### Server → Railway

1. Push your code to GitHub
2. Go to [railway.app](https://railway.app) → New Project → Deploy from GitHub
3. Select the `server` folder
4. Add a PostgreSQL plugin (Railway → New → Database → PostgreSQL)
5. Set these environment variables in Railway:

```
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=<Railway provides this automatically>
DATABASE_USER=<Railway provides this>
DATABASE_PASSWORD=<Railway provides this>
HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect
JWT_SECRET=<generate a 32+ char random string>
CLAUDE_API_KEY=<your Anthropic API key>
CORS_ORIGINS=https://your-app.vercel.app
```

6. Railway auto-deploys on every push to `main`

### Client → Vercel

1. Go to [vercel.com](https://vercel.com) → New Project → Import your GitHub repo
2. Set the **Root Directory** to `client`
3. Add this environment variable:
```
REACT_APP_API_URL=https://your-backend.up.railway.app
```
4. Click Deploy

After deploy, copy your Vercel URL and paste it into Railway's `CORS_ORIGINS` env var.

---

## Project Structure

```
finance-dashboard/
├── server/
│   ├── src/main/java/com/finance/
│   │   ├── FinanceApplication.java
│   │   ├── config/          SecurityConfig.java
│   │   ├── controller/      AuthController, UploadController,
│   │   │                    TransactionController, DashboardController
│   │   ├── dto/             Dtos.java (all request/response models)
│   │   ├── entity/          User, Transaction, StatementUpload
│   │   ├── repository/      JPA repositories
│   │   ├── security/        JwtUtils, JwtAuthFilter
│   │   └── service/         ClaudeService, StatementService,
│   │                        PdfParserService, CsvParserService,
│   │                        DashboardService, ExportService
│   ├── src/main/resources/
│   │   ├── application.properties        (local/H2)
│   │   └── application-prod.properties   (production/Postgres)
│   ├── build.gradle
│   ├── Dockerfile
│   └── railway.toml
│
└── client/
    ├── src/
    │   ├── App.jsx
    │   ├── index.js
    │   ├── hooks/           useAuth.js (AuthContext)
    │   ├── services/        api.js (all Axios calls)
    │   ├── components/      Layout.jsx (sidebar + routing shell)
    │   └── pages/
    │       ├── Login.jsx
    │       ├── Register.jsx
    │       ├── Dashboard.jsx   (charts, AI summary, export buttons)
    │       ├── Upload.jsx      (drag-and-drop, polling)
    │       └── Transactions.jsx (table, inline category editing)
    ├── package.json
    ├── tailwind.config.js
    └── vercel.json
```

---

## API Endpoints

### Auth
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Create account |
| POST | `/api/auth/login` | Login, returns JWT |

### Upload
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/upload` | Upload PDF or CSV (multipart) |
| GET | `/api/upload/status/{id}` | Poll processing status |
| GET | `/api/upload/history` | List all uploads |
| DELETE | `/api/upload/{id}` | Delete upload + transactions + Cloudinary file |

### Transactions
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/transactions?year=&month=` | List transactions |
| PUT | `/api/transactions/{id}/category` | Update category |
| DELETE | `/api/transactions/{id}` | Delete transaction |

### Dashboard
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/dashboard/summary?year=&month=` | Summary + AI insight |
| GET | `/api/dashboard/export/excel?year=&month=` | Download Excel |
| GET | `/api/dashboard/export/pdf?year=&month=` | Download PDF |

---

## Adding Support for a New Bank

The PDF parser uses regex patterns to detect transaction rows. Most Indian bank statements (HDFC, SBI, ICICI, Axis) are detected automatically. If your bank's format isn't recognized:

1. Open `PdfParserService.java`
2. Add a new `Pattern` to the `TRANSACTION_PATTERNS` list matching your bank's row format
3. Test with a sample statement

CSV exports from any bank should work automatically via the column auto-detection in `CsvParserService.java`.

---

## Environment Variables Reference

| Variable | Required | Description |
|----------|----------|-------------|
| `CLAUDE_API_KEY` | Yes | Your Anthropic API key |
| `JWT_SECRET` | Yes (prod) | Random 32+ char string for signing JWTs |
| `DATABASE_URL` | Yes (prod) | PostgreSQL JDBC URL |
| `DATABASE_USER` | Yes (prod) | Postgres username |
| `DATABASE_PASSWORD` | Yes (prod) | Postgres password |
| `CLOUDINARY_CLOUD_NAME` | No (optional) | Your Cloudinary cloud name |
| `CLOUDINARY_API_KEY` | No (optional) | Cloudinary API key |
| `CLOUDINARY_API_SECRET` | No (optional) | Cloudinary API secret |
| `CLOUDINARY_ENABLED` | No (optional) | Set to `true` in production to enable file storage |

---

## Cloudinary Setup (Optional but Recommended for Production)

Without Cloudinary, uploaded files are processed in memory and not stored permanently. This is fine for local dev. In production, enabling Cloudinary means users can re-download their original statements anytime from the Upload History page.

### Steps
1. Sign up free at [cloudinary.com](https://cloudinary.com) — free tier gives 10GB storage
2. Go to your Cloudinary Dashboard → copy **Cloud Name**, **API Key**, **API Secret**
3. Add them as Railway environment variables:

```
CLOUDINARY_CLOUD_NAME=your-cloud-name
CLOUDINARY_API_KEY=123456789012345
CLOUDINARY_API_SECRET=abcdefghijklmnopqrstuvwxyz
CLOUDINARY_ENABLED=true
```

Once enabled:
- Every uploaded PDF/CSV is stored in Cloudinary under `finance-statements/{userId}/`
- Users see a download button next to each upload in the Upload History page
- Deleting an upload also removes the file from Cloudinary automatically
- Cloudinary failure is non-fatal — if the upload to Cloudinary fails, transaction processing still continues

### Local dev with Cloudinary
By default `CLOUDINARY_ENABLED=false` locally so no credentials are needed. To test Cloudinary locally, add to `application.properties`:
```properties
app.cloudinary.cloud-name=your-cloud-name
app.cloudinary.api-key=your-api-key
app.cloudinary.api-secret=your-api-secret
app.cloudinary.enabled=true
```
