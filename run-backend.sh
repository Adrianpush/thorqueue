#!/bin/bash
set -a
source .env
set +a
cd backend && ./mvnw spring-boot:run
