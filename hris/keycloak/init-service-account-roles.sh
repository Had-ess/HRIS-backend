#!/bin/sh
set -eu

server_url="http://localhost:8180"
realm="hris"
admin_user="${KEYCLOAK_ADMIN:-admin}"
admin_password="${KEYCLOAK_ADMIN_PASSWORD:-admin}"

/opt/keycloak/bin/kc.sh start-dev --import-realm &
kc_pid=$!

cleanup() {
  kill "$kc_pid" 2>/dev/null || true
}

trap cleanup INT TERM

until /opt/keycloak/bin/kcadm.sh config credentials \
  --server "$server_url" \
  --realm master \
  --user "$admin_user" \
  --password "$admin_password" >/dev/null 2>&1; do
  sleep 2
done

until /opt/keycloak/bin/kcadm.sh get clients -r "$realm" -q clientId=hris-backend --fields id --format csv --noquotes | grep -q .; do
  sleep 2
done

until /opt/keycloak/bin/kcadm.sh get users -r "$realm" -q username=service-account-hris-backend --fields username --format csv --noquotes | grep -q '^service-account-hris-backend$'; do
  sleep 2
done

for role in manage-users view-users query-users view-realm manage-realm; do
  /opt/keycloak/bin/kcadm.sh add-roles \
    -r "$realm" \
    --uusername service-account-hris-backend \
    --cclientid realm-management \
    --rolename "$role" >/dev/null 2>&1 || true
done

wait "$kc_pid"
