.PHONY: up down restart-hub restart-bridge restart-hive test-e1 push-profile clean-db

# ── Services ──────────────────────────────────────────────
up:
	docker start mana-hub-pg 2>/dev/null || true
	./gradlew :bootstrap:bootRun --console=plain &
	./gradlew :event-bridge:bootRun --console=plain &
	./gradlew :engines:night-watch-runtime:bootRun --console=plain &

down:
	pkill -f "ManaHubApplication" 2>/dev/null || true
	pkill -f "EventBridgeApplication" 2>/dev/null || true
	pkill -f "NightWatchApplication" 2>/dev/null || true

restart-hub:
	pkill -f "ManaHubApplication" 2>/dev/null; sleep 2
	./gradlew :bootstrap:bootRun --console=plain &

restart-bridge:
	pkill -f "EventBridgeApplication" 2>/dev/null; sleep 2
	./gradlew :event-bridge:bootRun --console=plain &

restart-hive:
	pkill -f "NightWatchApplication" 2>/dev/null; sleep 2
	./gradlew :engines:night-watch-runtime:bootRun --console=plain &

# ── Database ──────────────────────────────────────────────
clean-db:
	docker exec mana-hub-pg psql -U postgres -d mana_hub -c "DELETE FROM resident_profiles;"
	docker exec mana-hub-pg psql -U postgres -d mana_hub -c "DELETE FROM episodes WHERE id LIKE 'd5%';"

# ── Profile ───────────────────────────────────────────────
push-profile-v3:
	curl -s -X PUT http://localhost:8080/api/profiles/jose \
	  -H 'Content-Type: application/json' \
	  -d @engines/night-watch-runtime/profiles/jose-e1-v3.json \
	  -w "\nHTTP %{http_code}\n"

push-profile-v2:
	curl -s -X PUT http://localhost:8080/api/profiles/jose \
	  -H 'Content-Type: application/json' \
	  -d @engines/night-watch-runtime/profiles/jose-e1-full.json \
	  -w "\nHTTP %{http_code}\n"

# ── Tests ─────────────────────────────────────────────────
test-e1:
	./gradlew :examples:jose-e1:run -Pmain=jose301.MainNatsScenarioE1Kt --console=plain

test-policy:
	./gradlew :examples:jose-e1:run -Pmain=jose301.MainPolicyChangeKt --console=plain

test-nvr:
	./gradlew :examples:jose-e1:run -Pmain=jose301.MainNvrSimulatorKt --console=plain

# ── Full E2E ──────────────────────────────────────────────
e2e-profile-change: clean-db restart-hive
	sleep 10
	$(MAKE) push-profile-v3
	sleep 2
	$(MAKE) test-e1
