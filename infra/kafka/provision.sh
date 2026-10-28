#!/usr/bin/env bash
set -euo pipefail

if [[ ! "${BOOKLANE_KAFKA_PROVISIONER_PASSWORD:-}" =~ ^[0-9a-f]{64}$ ]]; then
    echo "Kafka provisioning is missing its provisioner credential." >&2
    exit 1
fi

bootstrap="${BOOKLANE_KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}"
commands_topic=booklane.booking.commands.v1
events_topic=booklane.booking.events.v1
failed_topic=booklane.booking.failed.v1
api_group=booklane-api-events-v1
booking_group=booklane-booking-commands-v1
api=User:booklane-api
booking=User:booklane-booking

umask 077
config=$(mktemp)
trap 'rm -f "$config"' EXIT
cat > "$config" <<EOF
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="booklane-provisioner" password="${BOOKLANE_KAFKA_PROVISIONER_PASSWORD}";
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
    echo "Kafka did not become available for the provisioner principal." >&2
    exit 1
fi

for topic in "$commands_topic" "$events_topic" "$failed_topic"; do
    /opt/kafka/bin/kafka-topics.sh \
        --bootstrap-server "$bootstrap" \
        --command-config "$config" \
        --create --if-not-exists \
        --topic "$topic" \
        --partitions 3 \
        --replication-factor 1 \
        --config retention.ms=604800000 \
        --config cleanup.policy=delete \
        --config min.insync.replicas=1 >/dev/null
    /opt/kafka/bin/kafka-configs.sh \
        --bootstrap-server "$bootstrap" \
        --command-config "$config" \
        --alter \
        --entity-type topics \
        --entity-name "$topic" \
        --add-config retention.ms=604800000,cleanup.policy=delete,min.insync.replicas=1 >/dev/null

    description=$(/opt/kafka/bin/kafka-topics.sh \
        --bootstrap-server "$bootstrap" \
        --command-config "$config" \
        --describe --topic "$topic")
    if [[ "$description" != *"PartitionCount: 3"* || "$description" != *"ReplicationFactor: 1"* ]]; then
        echo "Topic $topic does not have the required three partitions and replication factor one." >&2
        exit 1
    fi
done

add_acl() {
    /opt/kafka/bin/kafka-acls.sh \
        --bootstrap-server "$bootstrap" \
        --command-config "$config" \
        --add "$@" >/dev/null
}

add_topic_operations() {
    local principal=$1
    local topic=$2
    shift 2
    for operation in "$@"; do
        add_acl --allow-principal "$principal" --topic "$topic" --operation "$operation"
    done
}

echo "Granting the application transport permissions."
add_topic_operations "$api" "$commands_topic" Write Describe
add_topic_operations "$api" "$events_topic" Read Describe
add_topic_operations "$api" "$failed_topic" Write Describe
add_acl --allow-principal "$api" --group "$api_group" --operation Read
add_acl --allow-principal "$api" --cluster --operation IdempotentWrite

add_topic_operations "$booking" "$commands_topic" Read Describe
add_topic_operations "$booking" "$events_topic" Write Describe
add_topic_operations "$booking" "$failed_topic" Write Describe
add_acl --allow-principal "$booking" --group "$booking_group" --operation Read
add_acl --allow-principal "$booking" --cluster --operation IdempotentWrite

echo "Kafka topics and application ACLs are ready."
