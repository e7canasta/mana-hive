#!/usr/bin/env bash
# Corre todos los blueprints y reporta cuáles fallaron.
#
# Los blueprints son escenarios ejecutables: fallan barato, antes que el bus.
# Uno de ellos —nats-e2e— necesita un NATS con JetStream:
#
#     nats-server -p 4222 -m 8222 -js -sd /tmp/natsdata &
#
# Sin NATS ese blueprint sale con un mensaje claro y exit 1; los demás no lo necesitan.
set -uo pipefail
cd "$(dirname "$0")/.."

BPS="ana-e2e-standard jose-301-sitting-bed jose-301-sentinel-alerts jose-301-harbor-delivery
     jose-301-recording jose-301-e2e-pipeline level-night-wandering level-fall-risk
     level-critical two-residents-e2e nats-e2e"

LOGDIR="${TMPDIR:-/tmp}/manahive-blueprints"
mkdir -p "$LOGDIR"
fails=0

for bp in $BPS; do
  log="$LOGDIR/$bp.log"
  # timeout por blueprint: uno que no termina no puede llevarse a los once.
  timeout "${BP_TIMEOUT:-180}" ./gradlew ":blueprints:$bp:run" --console=plain > "$log" 2>&1
  rc=$?
  [ "$rc" = "124" ] && echo "  (timeout tras ${BP_TIMEOUT:-180}s)" >> "$log"
  ko=$(grep -c "❌" "$log" || true)
  sum=$(grep -oE "[0-9]+ checks?, [0-9]+ fallidos?" "$log" | tail -1)
  [ -z "$sum" ] && sum=$(grep -E "✅" "$log" | tail -1 | sed 's/^ *//' | cut -c1-52)
  if [ "$rc" != "0" ] || [ "$ko" != "0" ]; then
    st="ROTO"; fails=$((fails+1))
  else
    st="ok"
  fi
  printf "%-26s %-5s %s\n" "$bp" "$st" "$sum"
  [ "$st" = "ROTO" ] && echo "    log: $log"
done

echo
if [ "$fails" = "0" ]; then echo "todos los blueprints en verde"; else echo "$fails blueprints rotos"; fi
exit $fails
