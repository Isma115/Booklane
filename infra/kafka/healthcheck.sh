#!/usr/bin/env bash
set -euo pipefail

if [[ ! "${BOOKLANE_KAFKA_BROKER_PASSWORD:-}" =~ ^[0-9a-f]{64}$ ]]; then
    echo "Kafka health check is missing its broker credential." >&2
    exit 1
fi

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

/opt/kafka/bin/kafka-broker-api-versions.sh \
    --bootstrap-server 127.0.0.1:9092 \
    --command-config "$config" >/dev/null
