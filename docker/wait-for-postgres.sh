#!/bin/sh
# wait-for-postgres.sh
# Waits for PostgreSQL to be ready before starting the application

set -e

host="$1"
shift
port="$1"
shift
cmd="$@"

until PGPASSWORD=$DB_PASSWORD psql -h "$host" -p "$port" -U "$DB_USERNAME" -d "$DB_NAME" -c '\q'; do
  >&2 echo "Postgres is unavailable - sleeping"
  sleep 1
done

>&2 echo "Postgres is up - executing command"
exec $cmd
