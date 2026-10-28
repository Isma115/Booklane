#!/usr/bin/env bash
set -euo pipefail

for name in BOOKLANE_KAFKA_BROKER_PASSWORD BOOKLANE_KAFKA_API_PASSWORD \
    BOOKLANE_KAFKA_BOOKING_PASSWORD BOOKLANE_KAFKA_PROVISIONER_PASSWORD; do
    if [[ ! "${!name:-}" =~ ^[0-9a-f]{64}$ ]]; then
        echo "Kafka configuration is incomplete: $name" >&2
        exit 1
    fi
done

umask 077
mkdir -p /tmp/booklane-kafka
jaas=/tmp/booklane-kafka/jaas.conf
cat > "$jaas" <<EOF
KafkaServer {
  org.apache.kafka.common.security.plain.PlainLoginModule required
  username="booklane-broker"
  password="${BOOKLANE_KAFKA_BROKER_PASSWORD}"
  user_booklane-broker="${BOOKLANE_KAFKA_BROKER_PASSWORD}"
  user_booklane-api="${BOOKLANE_KAFKA_API_PASSWORD}"
  user_booklane-booking="${BOOKLANE_KAFKA_BOOKING_PASSWORD}"
  user_booklane-provisioner="${BOOKLANE_KAFKA_PROVISIONER_PASSWORD}";
};
EOF

listener_jaas="org.apache.kafka.common.security.plain.PlainLoginModule required username=\"booklane-broker\" password=\"${BOOKLANE_KAFKA_BROKER_PASSWORD}\" user_booklane-broker=\"${BOOKLANE_KAFKA_BROKER_PASSWORD}\" user_booklane-api=\"${BOOKLANE_KAFKA_API_PASSWORD}\" user_booklane-booking=\"${BOOKLANE_KAFKA_BOOKING_PASSWORD}\" user_booklane-provisioner=\"${BOOKLANE_KAFKA_PROVISIONER_PASSWORD}\";"
export KAFKA_LISTENER_NAME_CLIENT_PLAIN_SASL_JAAS_CONFIG="$listener_jaas"
export KAFKA_LISTENER_NAME_CONTROLLER_PLAIN_SASL_JAAS_CONFIG="org.apache.kafka.common.security.plain.PlainLoginModule required username=\"booklane-broker\" password=\"${BOOKLANE_KAFKA_BROKER_PASSWORD}\" user_booklane-broker=\"${BOOKLANE_KAFKA_BROKER_PASSWORD}\";"
export KAFKA_LISTENER_NAME_HOST_PLAIN_SASL_JAAS_CONFIG="$listener_jaas"
export KAFKA_OPTS="-Djava.security.auth.login.config=$jaas"
exec /etc/kafka/docker/run
