#!/usr/bin/env bash
set -euo pipefail

mode="${1:-full}"
bootstrap="${BOOKLANE_KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}"
marker="${T006_PERSISTENCE_MARKER:-booklane-t006-persistence}"
commands_topic=booklane.booking.commands.v1
events_topic=booklane.booking.events.v1
failed_topic=booklane.booking.failed.v1
api_group=booklane-api-events-v1
booking_group=booklane-booking-commands-v1

for name in BOOKLANE_KAFKA_API_PASSWORD BOOKLANE_KAFKA_BOOKING_PASSWORD \
    BOOKLANE_KAFKA_PROVISIONER_PASSWORD; do
    if [[ ! "${!name:-}" =~ ^[0-9a-f]{64}$ ]]; then
        echo "Kafka verification is missing $name." >&2
        exit 1
    fi
done

umask 077
workdir=$(mktemp -d)
trap 'rm -rf "$workdir"' EXIT

write_client_config() {
    local username=$1
    local password=$2
    local path=$3
    cat > "$path" <<EOF
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="$username" password="$password";
request.timeout.ms=5000
default.api.timeout.ms=5000
retries=0
EOF
}

write_client_config booklane-api "$BOOKLANE_KAFKA_API_PASSWORD" "$workdir/api.properties"
write_client_config booklane-booking "$BOOKLANE_KAFKA_BOOKING_PASSWORD" "$workdir/booking.properties"
write_client_config booklane-provisioner "$BOOKLANE_KAFKA_PROVISIONER_PASSWORD" "$workdir/provisioner.properties"
cat > "$workdir/anonymous.properties" <<EOF
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="anonymous" password="$(printf '0%.0s' $(seq 1 64))";
request.timeout.ms=5000
default.api.timeout.ms=5000
retries=0
EOF

kafka_topics() {
    /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$bootstrap" --command-config "$1" "${@:2}"
}

kafka_configs() {
    /opt/kafka/bin/kafka-configs.sh --bootstrap-server "$bootstrap" --command-config "$1" "${@:2}"
}

kafka_consumer() {
    local config=$1
    shift
    /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server "$bootstrap" --consumer.config "$config" "$@"
}

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

expect_success() {
    local description=$1
    shift
    if ! timeout 45 "$@" >/dev/null 2>&1; then
        fail "$description"
    fi
    echo "PASS: $description"
}

expect_topic_failure() {
    local description=$1
    local config=$2
    shift 2
    if timeout 45 /opt/kafka/bin/kafka-topics.sh \
        --bootstrap-server "$bootstrap" --command-config "$config" "$@" >/dev/null 2>&1; then
        fail "$description unexpectedly succeeded"
    fi
    echo "PASS: $description rejected"
}

write_marker() {
    local config=$1
    local topic=$2
    local value=$3
    if ! printf '%s\n' "$value" | timeout 45 /opt/kafka/bin/kafka-console-producer.sh \
        --bootstrap-server "$bootstrap" \
        --producer.config "$config" \
        --topic "$topic" \
        --producer-property acks=all \
        --producer-property retries=2147483647 \
        --producer-property enable.idempotence=true >/dev/null 2>&1; then
        fail "could not write $value to $topic"
    fi
}

read_topic_into() {
    local config=$1
    local topic=$2
    local output=$3
    : > "$output"
    for partition in 0 1 2; do
        timeout 30 /opt/kafka/bin/kafka-console-consumer.sh \
            --bootstrap-server "$bootstrap" \
            --consumer.config "$config" \
            --topic "$topic" \
            --partition "$partition" \
            --offset earliest \
            --timeout-ms 10000 >> "$output" 2>/dev/null || true
    done
}

verify_topics_and_acls() {
    echo "Checking topics, partitions and retention."
    for topic in "$commands_topic" "$events_topic" "$failed_topic"; do
        description=$(kafka_topics "$workdir/provisioner.properties" --describe --topic "$topic")
        [[ "$description" == *"PartitionCount: 3"* ]] || fail "$topic does not have three partitions"
        [[ "$description" == *"ReplicationFactor: 1"* ]] || fail "$topic does not have replication factor one"
        config=$(kafka_configs "$workdir/provisioner.properties" \
            --describe --entity-type topics --entity-name "$topic")
        [[ "$config" == *"retention.ms=604800000"* ]] || fail "$topic does not retain seven days"
        [[ "$config" == *"cleanup.policy=delete"* ]] || fail "$topic does not use delete cleanup"
        [[ "$config" == *"min.insync.replicas=1"* ]] || fail "$topic has an unexpected minimum ISR"
    done
    echo "PASS: three topics with three partitions and seven-day retention"

    acls=$(/opt/kafka/bin/kafka-acls.sh \
        --bootstrap-server "$bootstrap" \
        --command-config "$workdir/provisioner.properties" --list)
    for principal in User:booklane-api User:booklane-booking User:booklane-provisioner; do
        [[ "$acls" == *"$principal"* ]] || fail "missing ACL principal $principal"
    done
    if [[ "$acls" == *"User:*"* || "$acls" == *"ANONYMOUS"* || "$acls" == *"operation=All"* ]]; then
        fail "an ACL is broader than the documented principals"
    fi
    echo "PASS: ACL inventory contains only the documented principals"

    expect_topic_failure "anonymous topic listing" "$workdir/anonymous.properties" --list
    expect_topic_failure "api topic creation" "$workdir/api.properties" \
        --create --topic "booklane.t006.forbidden.$RANDOM" --partitions 1 --replication-factor 1
    expect_topic_failure "booking topic creation" "$workdir/booking.properties" \
        --create --topic "booklane.t006.forbidden.$RANDOM" --partitions 1 --replication-factor 1
}

verify_positive_transport() {
    echo "Checking allowed producers and consumers."
    write_marker "$workdir/api.properties" "$commands_topic" "t006-command"
    output=$(kafka_consumer "$workdir/booking.properties" \
        --topic "$commands_topic" --group "$booking_group" --from-beginning \
        --max-messages 1 --timeout-ms 30000)
    [[ "$output" == *"t006-command"* ]] || fail "booking could not consume an api command"
    echo "PASS: api writes commands and booking consumes them"

    write_marker "$workdir/booking.properties" "$events_topic" "t006-event"
    output=$(kafka_consumer "$workdir/api.properties" \
        --topic "$events_topic" --group "$api_group" --from-beginning \
        --max-messages 1 --timeout-ms 30000)
    [[ "$output" == *"t006-event"* ]] || fail "api could not consume a booking event"
    echo "PASS: booking writes events and api consumes them"

    write_marker "$workdir/api.properties" "$failed_topic" "t006-failed-api"
    write_marker "$workdir/booking.properties" "$failed_topic" "t006-failed-booking"
    read_topic_into "$workdir/provisioner.properties" "$failed_topic" "$workdir/failed-output.txt"
    [[ "$(cat "$workdir/failed-output.txt")" == *"t006-failed-api"* ]] || fail "failed topic lacks the api diagnostic"
    [[ "$(cat "$workdir/failed-output.txt")" == *"t006-failed-booking"* ]] || fail "failed topic lacks the booking diagnostic"
    echo "PASS: api and booking write diagnostics to failed"
}

expect_producer_failure() {
    local description=$1
    local config=$2
    local topic=$3
    local value=$4
    local output
    output=$(printf '%s\n' "$value" | timeout 45 /opt/kafka/bin/kafka-console-producer.sh \
        --bootstrap-server "$bootstrap" \
        --producer.config "$config" \
        --topic "$topic" \
        --producer-property acks=all 2>&1) || true
    if [[ "$output" != *"TopicAuthorizationException"* ]]; then
        fail "$description was not rejected by topic authorization"
    fi
    echo "PASS: $description rejected"
}

expect_consumer_failure() {
    local description=$1
    local config=$2
    local topic=$3
    local output
    output=$(timeout 45 /opt/kafka/bin/kafka-console-consumer.sh \
        --bootstrap-server "$bootstrap" \
        --consumer.config "$config" \
        --topic "$topic" --partition 0 --offset earliest --max-messages 1 \
        --timeout-ms 10000 2>&1) || true
    if [[ "$output" != *"TopicAuthorizationException"* \
        && "$output" != *"GroupAuthorizationException"* \
        && "$output" != *"SaslAuthenticationException"* \
        && "$output" != *"Authentication failed"* ]]; then
        fail "$description was not rejected by authentication or authorization"
    fi
    echo "PASS: $description rejected"
}

expect_group_failure() {
    local config=$1
    local topic=$2
    local group=$3
    local output
    output=$(timeout 45 /opt/kafka/bin/kafka-console-consumer.sh \
        --bootstrap-server "$bootstrap" --consumer.config "$config" \
        --topic "$topic" --group "$group" --from-beginning \
        --max-messages 1 --timeout-ms 10000 2>&1) || true
    [[ "$output" == *"GroupAuthorizationException"* ]] || fail "unauthorized group $group was not rejected by group authorization"
    echo "PASS: unauthorized group $group rejected"
}

verify_negative_transport() {
    echo "Checking forbidden producers, consumers and anonymous access."
    expect_producer_failure "api publishing events" "$workdir/api.properties" "$events_topic" "t006-forbidden"
    expect_producer_failure "booking publishing commands" "$workdir/booking.properties" "$commands_topic" "t006-forbidden"
    expect_consumer_failure "api reading commands" "$workdir/api.properties" "$commands_topic"
    expect_consumer_failure "anonymous reading events" "$workdir/anonymous.properties" "$events_topic"
    expect_consumer_failure "booking reading events" "$workdir/booking.properties" "$events_topic"
    expect_group_failure "$workdir/api.properties" "$events_topic" "$booking_group"
    expect_group_failure "$workdir/booking.properties" "$commands_topic" "$api_group"
}

persistence_write() {
    write_marker "$workdir/api.properties" "$commands_topic" "$marker-commands"
    write_marker "$workdir/booking.properties" "$events_topic" "$marker-events"
    write_marker "$workdir/api.properties" "$failed_topic" "$marker-failed"
    echo "PASS: persistence markers written"
}

persistence_check() {
    read_topic_into "$workdir/booking.properties" "$commands_topic" "$workdir/commands-output.txt"
    [[ "$(cat "$workdir/commands-output.txt")" == *"$marker-commands"* ]] || fail "command marker did not survive the restart"
    read_topic_into "$workdir/api.properties" "$events_topic" "$workdir/events-output.txt"
    [[ "$(cat "$workdir/events-output.txt")" == *"$marker-events"* ]] || fail "event marker did not survive the restart"
    read_topic_into "$workdir/provisioner.properties" "$failed_topic" "$workdir/failed-output.txt"
    [[ "$(cat "$workdir/failed-output.txt")" == *"$marker-failed"* ]] || fail "failed marker did not survive the restart"
    echo "PASS: messages in all three topics survived the restart"
}

case "$mode" in
    full)
        verify_topics_and_acls
        verify_positive_transport
        verify_negative_transport
        echo "PASS: Kafka topics, accounts and ACLs match the documented permissions."
        ;;
    persistence-write)
        persistence_write
        ;;
    persistence-check)
        persistence_check
        ;;
    *)
        echo "Usage: $0 [full|persistence-write|persistence-check]" >&2
        exit 1
        ;;
esac
