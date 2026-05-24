# ⚡ ThorQueue

A full-stack project management application with role-based access control,
ticket-driven workflows, and built-in audit trail.

Every change flows through a ticket — like tasks through a queue, forged
by the hammer.

## Tech Stack

| Layer          | Technology             |
|----------------|------------------------|
| Frontend       | React                  |
| Backend        | Spring Boot (Java)     |
| Database       | PostgreSQL             |
| Auth           | Keycloak               |
| Deployment     | AWS (EC2 + RDS)        |

## Project Structure

```
thorqueue/
├── frontend/          # React application
├── backend/           # Spring Boot API
├── docs/              # Project documentation
├── infrastructure/    # Docker & deployment configs
└── docker-compose.yml # Local development environment
```

## Local Development

Prerequisites: Docker and Docker Compose.

```bash
# Set up environment
cp .env.example .env    # then edit .env with your values

# Start Postgres + Keycloak
docker compose up -d

# Postgres:  localhost:5432
# Keycloak:  localhost:8180
```

## License

This project is **not open source**. Source code is visible for viewing
and educational purposes only. See [LICENSE](LICENSE) for details.
