#!/usr/bin/env bash
set -euo pipefail

if [[ ! "${BOOKLANE_KAFKA_BROKER_PASSWORD:-}" =~ ^[0-9a-f]{64}$ ]]; then
    echo "Kafka ACL bootstrap is missing its broker credential." >&2
    exit 1
fi

bootstrap="${BOOKLANE_KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}"
commands_topic=booklane.booking.commands.v1
events_topic=booklane.booking.events.v1
failed_topic=booklane.booking.failed.v1
api_group=booklane-api-events-v1
booking_group=booklane-booking-commands-v1
provisioner=User:booklane-provisioner

umask 077
config=$(mktemp)
trap 'rm -f "$config"' EXIT
cat > "$config" <<EOF
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="booklane-broker" password="${BOOKLANE_KAFKA_BROKER_PASSWORD}";
request.timeout.ms=5000
default.api.timeout.ms=5000
EOF

echo "Waiting for the authenticated Kafka broker..."
ready=false
for _ in $(seq 1 60); do
    if /opt/kafka/bin/kafka-broker-api-versions.sh \
        --bootstrap-server "$bootstrap" --command-config "$config" >/dev/null 2>&1; then
        ready=true
        break
    fi
    sleep 2
done
if [[ "$ready" != true ]]; then
    echo "Kafka did not become available for the bootstrap principal." >&2
    exit 1
fi

add_acl() {
    /opt/kafka/bin/kafka-acls.sh \
        --bootstrap-server "$bootstrap" \
        --command-config "$config" \
        --add "$@" >/dev/null
}

echo "Granting the minimum provisioning permissions."
for operation in Alter Describe; do
    add_acl --allow-principal "$provisioner" --cluster --operation "$operation"
done
for topic in "$commands_topic" "$events_topic" "$failed_topic"; do
    for operation in Create Describe DescribeConfigs AlterConfigs; do
        add_acl --allow-principal "$provisioner" --topic "$topic" --operation "$operation"
    done
done
add_acl --allow-principal "$provisioner" --topic "$failed_topic" --operation Read

echo "Kafka provisioning ACL bootstrap completed."
