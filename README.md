# Smart Complaint System

A role-based complaint management system with:
- User and Admin login pages
- JWT-based authentication
- Complaint submission and tracking
- Admin complaint status updates and analytics
- Modern responsive UI
- MySQL-backed persistence

## Tech Stack

- Frontend: React (Create React App)
- Backend: Java HttpServer (standalone, JDBC)
- Database: MySQL
- Auth: JWT (HMAC SHA-256)

## Project Structure

- `frontend/` React application
- `backend/` Java backend server

## Demo Credentials

- User:
  - Username: `user`
  - Password: `user123`
- Admin:
  - Username: `admin`
  - Password: `admin123`

## Run Locally

### 1. Start Backend

From project root:

Set MySQL-related environment variables in the terminal before starting:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/smart_complaint_system?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:DB_USER = "root"
$env:DB_PASSWORD = "root"
$env:TOKEN_SECRET = "replace-with-strong-secret"
```

```powershell
powershell -ExecutionPolicy Bypass -File backend/start-backend.ps1
```

Backend runs on `http://localhost:8080` by default and uses `PORT` if provided.

### 2. Start Frontend

In a new terminal:

```powershell
cd frontend
npm install
npm start
```

Frontend runs on `http://localhost:3000`.

## Available Routes

- `/login/user`
- `/login/admin`
- `/user`
- `/admin`

## Main Features

### User
- Login as user
- Submit complaint with structured fields:
  - title, description, category, priority, location, incident date, preferred contact
- Save draft locally while filling the form
- View and track own complaints
- Search, filter, sort, and paginate complaints

### Admin
- Login as admin
- View all complaints
- Update complaint status (`OPEN`, `IN_PROGRESS`, `RESOLVED`, `REJECTED`)
- View complaint analytics dashboard cards

## API Endpoints

- `POST /api/auth/login`
- `GET /api/health`
- `GET /api/complaints`
- `POST /api/complaints`
- `PATCH /api/complaints/{id}/status`
- `GET /api/analytics`

## Notes

- This repository currently includes `frontend/build` artifacts from the latest build.
- If you want a source-only repository, remove `frontend/build` and keep it ignored in `.gitignore`.
- Backend now persists users and complaints in MySQL.
