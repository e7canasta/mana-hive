use crate::response::json_bytes;
use crate::{
    audit_handlers, build_router_from_routes, build_router_with_handlers, cobertura_handlers,
    cuidado_handlers, historia_handlers, identity_handlers, observation_handlers,
    poblacion_handlers, politica_handlers, residence_handlers, rust_handler, vigilancia_handlers,
    HubConfig, RouteTable, RouteTableError,
};
use axum::{http::StatusCode, Router};
use std::{collections::HashSet, net::SocketAddr, sync::Arc};
use tokio::{net::TcpListener, task::JoinHandle};

fn build_test_router(config: HubConfig) -> Router {
    let handlers = RouteTable::embedded()
        .unwrap()
        .routes()
        .iter()
        .filter(|route| route.sirve == crate::Serve::Rust)
        .map(|route| {
            (
                route.id.clone(),
                rust_handler(|_| async { json_bytes(StatusCode::OK, br#"{}"#) }),
            )
        })
        .collect();
    build_router_with_handlers(config, handlers).unwrap()
}

fn build_residence_router(config: HubConfig, state: Arc<mana_app::AppState>) -> Router {
    let routes = RouteTable::parse(
        r#"
            [[ruta]]
            id = "auth.login.post"
            metodo = "POST"
            patron = "/api/v1/auth/login"
            sirve = "rust"

            [[ruta]]
            id = "audit-log.list.get"
            metodo = "GET"
            patron = "/api/v1/audit-log"
            sirve = "rust"

            [[ruta]]
            id = "facilities.list.get"
            metodo = "GET"
            patron = "/api/v1/facilities"
            sirve = "rust"

            [[ruta]]
            id = "wings.list.get"
            metodo = "GET"
            patron = "/api/v1/wings"
            sirve = "rust"

            [[ruta]]
            id = "wings.rooms.get"
            metodo = "GET"
            patron = "/api/v1/wings/:wingId/rooms"
            sirve = "rust"

            [[ruta]]
            id = "facilities.create.post"
            metodo = "POST"
            patron = "/api/v1/facilities"
            sirve = "rust"

            [[ruta]]
            id = "facilities.wings.create.post"
            metodo = "POST"
            patron = "/api/v1/facilities/:facilityId/wings"
            sirve = "rust"

            [[ruta]]
            id = "wings.rooms.create.post"
            metodo = "POST"
            patron = "/api/v1/wings/:wingId/rooms"
            sirve = "rust"

            [[ruta]]
            id = "rooms.beds.get"
            metodo = "GET"
            patron = "/api/v1/rooms/:roomId/beds"
            sirve = "rust"

            [[ruta]]
            id = "rooms.beds.create.post"
            metodo = "POST"
            patron = "/api/v1/rooms/:roomId/beds"
            sirve = "rust"

            [[ruta]]
            id = "beds.list.get"
            metodo = "GET"
            patron = "/api/v1/beds"
            sirve = "rust"

            [[ruta]]
            id = "wings.planogram.get"
            metodo = "GET"
            patron = "/api/v1/wings/:wingId/planogram"
            sirve = "rust"

            [[ruta]]
            id = "wings.planogram.put"
            metodo = "PUT"
            patron = "/api/v1/wings/:wingId/planogram"
            sirve = "rust"

            [[ruta]]
            id = "rooms.privacy-regions.get"
            metodo = "GET"
            patron = "/api/v1/rooms/:roomId/privacy-regions"
            sirve = "rust"

            [[ruta]]
            id = "rooms.privacy-regions.put"
            metodo = "PUT"
            patron = "/api/v1/rooms/:roomId/privacy-regions"
            sirve = "rust"
        "#,
    )
    .unwrap();
    let mut handlers = identity_handlers(state.clone());
    handlers.extend(audit_handlers(state.clone()));
    handlers.extend(residence_handlers(state));
    build_router_from_routes(config, routes, handlers).unwrap()
}

fn build_poblacion_router(config: HubConfig, state: Arc<mana_app::AppState>) -> Router {
    let routes = RouteTable::parse(
        r#"
            [[ruta]]
            id = "auth.login.post"
            metodo = "POST"
            patron = "/api/v1/auth/login"
            sirve = "rust"

            [[ruta]]
            id = "audit-log.list.get"
            metodo = "GET"
            patron = "/api/v1/audit-log"
            sirve = "rust"

            [[ruta]]
            id = "facilities.create.post"
            metodo = "POST"
            patron = "/api/v1/facilities"
            sirve = "rust"

            [[ruta]]
            id = "facilities.wings.create.post"
            metodo = "POST"
            patron = "/api/v1/facilities/:facilityId/wings"
            sirve = "rust"

            [[ruta]]
            id = "wings.rooms.create.post"
            metodo = "POST"
            patron = "/api/v1/wings/:wingId/rooms"
            sirve = "rust"

            [[ruta]]
            id = "rooms.beds.create.post"
            metodo = "POST"
            patron = "/api/v1/rooms/:roomId/beds"
            sirve = "rust"

            [[ruta]]
            id = "residents.list.get"
            metodo = "GET"
            patron = "/api/v1/residents"
            sirve = "rust"

            [[ruta]]
            id = "residents.detail.get"
            metodo = "GET"
            patron = "/api/v1/residents/:residentId"
            sirve = "rust"

            [[ruta]]
            id = "residents.create.post"
            metodo = "POST"
            patron = "/api/v1/residents"
            sirve = "rust"

            [[ruta]]
            id = "residents.update.patch"
            metodo = "PATCH"
            patron = "/api/v1/residents/:residentId"
            sirve = "rust"

            [[ruta]]
            id = "residents.discharge.post"
            metodo = "POST"
            patron = "/api/v1/residents/:residentId/discharge"
            sirve = "rust"

            [[ruta]]
            id = "residents.assignments.get"
            metodo = "GET"
            patron = "/api/v1/residents/:residentId/assignments"
            sirve = "rust"

            [[ruta]]
            id = "residents.assignments.create.post"
            metodo = "POST"
            patron = "/api/v1/residents/:residentId/assignments"
            sirve = "rust"

            [[ruta]]
            id = "beds.assignment.delete"
            metodo = "DELETE"
            patron = "/api/v1/beds/:bedId/assignment"
            sirve = "rust"
        "#,
    )
    .unwrap();
    let mut handlers = identity_handlers(state.clone());
    handlers.extend(audit_handlers(state.clone()));
    handlers.extend(residence_handlers(state.clone()));
    handlers.extend(poblacion_handlers(state.clone()));
    handlers.extend(cobertura_handlers(state.clone()));
    handlers.extend(cuidado_handlers(state.clone()));
    handlers.extend(historia_handlers(state.clone()));
    handlers.extend(politica_handlers(state.clone()));
    handlers.extend(vigilancia_handlers(state.clone()));
    handlers.extend(observation_handlers(state));
    build_router_from_routes(config, routes, handlers).unwrap()
}

async fn spawn_hub(app: Router) -> (SocketAddr, JoinHandle<()>) {
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let address = listener.local_addr().unwrap();
    let task = tokio::spawn(async move {
        axum::serve(
            listener,
            app.into_make_service_with_connect_info::<SocketAddr>(),
        )
        .await
        .unwrap();
    });
    (address, task)
}

#[test]
fn embedded_routes_are_parseable_and_have_identity_handlers() {
    let routes = RouteTable::embedded().unwrap();
    assert_eq!(routes.rust(), 106);
    assert_eq!(routes.total(), routes.node() + routes.rust());
    // El comodin no le gana a una ruta con segmentos literales.
    assert_eq!(
        routes.find("OPTIONS", "/api/v1/wings").unwrap().id,
        "cors.preflight.options"
    );
    assert_eq!(
        routes.find("GET", "/api/v1/wings").unwrap().id,
        "wings.list.get"
    );
    assert!(routes.find("POST", "/internal/v1/events").is_some());
    assert!(routes.find("GET", "/api/v1/wings/wing-1/board").is_some());
    assert_eq!(
        routes
            .find("GET", "/api/v1/alarm-presets/catalog")
            .unwrap()
            .id,
        "alarm-presets.catalog.get"
    );
}

#[test]
fn rust_entries_without_handlers_fail_startup_validation() {
    let routes = RouteTable::parse(
        r#"
            [[ruta]]
            id = "future.read"
            metodo = "GET"
            patron = "/future/:id"
            sirve = "rust"
        "#,
    )
    .unwrap();
    let handlers = HashSet::new();
    let error = routes.validate_handlers(&handlers).unwrap_err();
    assert!(matches!(error, RouteTableError::MissingRustHandler { .. }));
}

/// Estado de prueba con catalogo de alarmas **vacio y explicito**.
///
/// `AppState::new` carga el catalogo del disco y falla si no esta; estos tests
/// corren con el cwd del crate, donde ese archivo no existe. Pedir el vacio a
/// mano deja escrito que estos casos no ejercitan politica de alarmas.
fn test_state() -> Arc<mana_app::AppState> {
    Arc::new(mana_app::AppState::with_catalog(":memory:", mana_app::AlarmCatalog::empty()).unwrap())
}

#[tokio::test]
async fn health_has_the_exact_contract_body() {
    let config = HubConfig::new("127.0.0.1", 0).unwrap();
    let app = build_test_router(config);
    let (address, task) = spawn_hub(app).await;
    let response = reqwest::get(format!("http://{address}/health"))
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::OK);
    assert_eq!(
        response.text().await.unwrap(),
        r#"{"ok":true,"service":"virtual-rounds-api","database":"sqlite"}"#
    );
    task.abort();
}

#[tokio::test]
async fn readiness_no_longer_probes_an_upstream() {
    let config = HubConfig::new("127.0.0.1", 0).unwrap();
    let app = build_test_router(config);
    let (address, hub_task) = spawn_hub(app).await;

    let response = reqwest::get(format!("http://{address}/__hub/ready"))
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::OK);
    let body: serde_json::Value = serde_json::from_str(&response.text().await.unwrap()).unwrap();
    assert_eq!(body["ok"], true);
    // El campo se conserva en `null` para que quien lo monitoreaba vea que el
    // fallback se retiro, y no que el campo se perdio.
    assert!(body["fallback"].is_null());

    hub_task.abort();
}

/* Node atendia el preflight antes del router y contestaba 204 para cualquier
 * path, existiera o no. La entrada comodin de la tabla y este handler conservan
 * ese comportamiento ahora que Node no esta. */
#[tokio::test]
async fn options_answers_204_on_any_path_inventoried_or_not() {
    let config = HubConfig::new("127.0.0.1", 0).unwrap();
    let app = build_test_router(config);
    let (address, hub_task) = spawn_hub(app).await;
    let client = reqwest::Client::new();

    for path in ["/api/v1/wings", "/health", "/no-existe-en-la-tabla"] {
        let response = client
            .request(reqwest::Method::OPTIONS, format!("http://{address}{path}"))
            .send()
            .await
            .unwrap();
        assert_eq!(
            response.status(),
            StatusCode::NO_CONTENT,
            "OPTIONS {path} tiene que ser 204"
        );
        assert_eq!(response.headers()["access-control-allow-origin"], "*");
    }

    hub_task.abort();
}

/* Sin upstream, una ruta fuera de la tabla es un 404 del contrato y no un 502.
 * Antes se proxeaba, que era lo correcto mientras Node existia. */
#[tokio::test]
async fn an_uninventoried_route_is_a_contract_404_and_is_counted() {
    let config = HubConfig::new("127.0.0.1", 0).unwrap();
    let app = build_test_router(config);
    let (address, hub_task) = spawn_hub(app).await;
    let client = reqwest::Client::new();

    let response = client
        .get(format!("http://{address}/no-inventariada"))
        .send()
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::NOT_FOUND);
    let body: serde_json::Value = serde_json::from_str(&response.text().await.unwrap()).unwrap();
    assert_eq!(body["error"]["code"], "NOT_FOUND");

    let counts: serde_json::Value = serde_json::from_str(
        &client
            .get(format!("http://{address}/__hub/rutas"))
            .send()
            .await
            .unwrap()
            .text()
            .await
            .unwrap(),
    )
    .unwrap();
    assert_eq!(counts["node"], 0);
    assert_eq!(
        counts["rutas_no_inventariadas"],
        serde_json::json!(["GET /no-inventariada"])
    );

    hub_task.abort();
}

/* `/health` responde solo a GET. HEAD y POST dan 404 —que es lo que esta API
 * siempre emitio— y no el 405 de axum. */
#[tokio::test]
async fn health_only_owns_get() {
    let config = HubConfig::new("127.0.0.1", 0).unwrap();
    let app = build_test_router(config);
    let (address, hub_task) = spawn_hub(app).await;
    let client = reqwest::Client::new();

    for method in [reqwest::Method::POST, reqwest::Method::HEAD] {
        let response = client
            .request(method.clone(), format!("http://{address}/health"))
            .send()
            .await
            .unwrap();
        assert_eq!(
            response.status(),
            StatusCode::NOT_FOUND,
            "{method} /health tiene que ser 404, no 405"
        );
    }

    hub_task.abort();
}

/* La regla que cierra el estrangulamiento: la tabla ya no puede expresar un
 * fallback, asi que nadie puede reintroducirlo por descuido. */
#[test]
fn a_route_table_with_a_node_entry_no_longer_loads() {
    let table = RouteTable::parse(
        r#"
[[ruta]]
id = "legacy.get"
metodo = "GET"
patron = "/api/v1/legacy"
contexto = "plataforma"
sirve = "node"
"#,
    );
    assert!(matches!(
        table,
        Err(RouteTableError::NodeFallbackRetired { .. })
    ));
}

#[tokio::test]
async fn identity_handlers_serve_the_first_slice_end_to_end() {
    let state = test_state();
    state.seed_demo().unwrap();
    let config = HubConfig::new("127.0.0.1", 0).unwrap();
    let mut handlers = identity_handlers(state.clone());
    handlers.extend(audit_handlers(state.clone()));
    handlers.extend(residence_handlers(state.clone()));
    handlers.extend(poblacion_handlers(state.clone()));
    handlers.extend(cobertura_handlers(state.clone()));
    handlers.extend(cuidado_handlers(state.clone()));
    handlers.extend(historia_handlers(state.clone()));
    handlers.extend(politica_handlers(state.clone()));
    handlers.extend(vigilancia_handlers(state.clone()));
    handlers.extend(observation_handlers(state));
    let app = build_router_with_handlers(config, handlers).unwrap();
    let (address, task) = spawn_hub(app).await;
    let client = reqwest::Client::new();
    let login = client
        .post(format!("http://{address}/api/v1/auth/login"))
        .header("content-type", "application/json")
        .body(r#"{"username":"gaston","password":"gaston-demo"}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(login.status(), StatusCode::OK);
    let login: serde_json::Value = serde_json::from_str(&login.text().await.unwrap()).unwrap();
    let token = login["token"].as_str().unwrap().to_owned();
    assert_eq!(login["user"]["username"], "gaston");

    let me = client
        .get(format!("http://{address}/api/v1/auth/me"))
        .bearer_auth(&token)
        .send()
        .await
        .unwrap();
    assert_eq!(me.status(), StatusCode::OK);
    let me: serde_json::Value = serde_json::from_str(&me.text().await.unwrap()).unwrap();
    assert_eq!(me["user"]["role"], "supervisor");

    let users = client
        .get(format!("http://{address}/api/v1/users?include_inactive=1"))
        .bearer_auth(&token)
        .send()
        .await
        .unwrap();
    assert_eq!(users.status(), StatusCode::OK);
    let users: serde_json::Value = serde_json::from_str(&users.text().await.unwrap()).unwrap();
    assert_eq!(users["users"].as_array().unwrap().len(), 2);

    let created = client
        .post(format!("http://{address}/api/v1/users"))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(r#"{"username":"audit-user","display_name":"Audit User","role":"staff","password":"secret1"}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(created.status(), StatusCode::CREATED);
    let created: serde_json::Value = serde_json::from_str(&created.text().await.unwrap()).unwrap();
    let created_id = created["user"]["id"].as_str().unwrap();

    let updated = client
        .patch(format!("http://{address}/api/v1/users/{created_id}"))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(r#"{"active":false}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(updated.status(), StatusCode::OK);

    let audit = client
        .get(format!(
            "http://{address}/api/v1/audit-log?entity_type=user&entity_id={created_id}"
        ))
        .bearer_auth(&token)
        .send()
        .await
        .unwrap();
    assert_eq!(audit.status(), StatusCode::OK);
    let audit: serde_json::Value = serde_json::from_str(&audit.text().await.unwrap()).unwrap();
    assert_eq!(audit["audit"].as_array().unwrap().len(), 2);
    assert_eq!(audit["audit"][0]["actor_name"], "Gaston");

    let logout = client
        .post(format!("http://{address}/api/v1/auth/logout"))
        .bearer_auth(&token)
        .send()
        .await
        .unwrap();
    assert_eq!(logout.status(), StatusCode::NO_CONTENT);

    let expired = client
        .get(format!("http://{address}/api/v1/auth/me"))
        .bearer_auth(token)
        .send()
        .await
        .unwrap();
    assert_eq!(expired.status(), StatusCode::UNAUTHORIZED);
    task.abort();
}

#[tokio::test]
async fn residence_handlers_serve_the_structure_slice_end_to_end() {
    let state = test_state();
    state.seed_demo().unwrap();
    let config = HubConfig::new("127.0.0.1", 0).unwrap();
    let app = build_residence_router(config, state);
    let (address, task) = spawn_hub(app).await;
    let client = reqwest::Client::new();

    let login = client
        .post(format!("http://{address}/api/v1/auth/login"))
        .header("content-type", "application/json")
        .body(r#"{"username":"gaston","password":"gaston-demo"}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(login.status(), StatusCode::OK);
    let login: serde_json::Value = serde_json::from_str(&login.text().await.unwrap()).unwrap();
    let token = login["token"].as_str().unwrap().to_owned();

    let facility = client
        .post(format!("http://{address}/api/v1/facilities"))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(r#"{"name":"Manantial","timezone":"UTC"}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(facility.status(), StatusCode::CREATED);
    let facility: serde_json::Value =
        serde_json::from_str(&facility.text().await.unwrap()).unwrap();
    let facility_id = facility["id"].as_str().unwrap();

    let wing = client
        .post(format!(
            "http://{address}/api/v1/facilities/{facility_id}/wings"
        ))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(r#"{"name":"Ala Norte","floor":"1","sort_order":1}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(wing.status(), StatusCode::CREATED);
    let wing: serde_json::Value = serde_json::from_str(&wing.text().await.unwrap()).unwrap();
    let wing_id = wing["id"].as_str().unwrap();

    let room = client
        .post(format!("http://{address}/api/v1/wings/{wing_id}/rooms"))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(r#"{"number":"118","type":"single","stream_key":"stream-118"}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(room.status(), StatusCode::CREATED);
    let room: serde_json::Value = serde_json::from_str(&room.text().await.unwrap()).unwrap();
    let room_id = room["id"].as_str().unwrap();

    let bed = client
        .post(format!("http://{address}/api/v1/rooms/{room_id}/beds"))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(r#"{"label":"Cama 1","monitor_key":"monitor-118-1"}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(bed.status(), StatusCode::CREATED);

    let beds = client
        .get(format!("http://{address}/api/v1/rooms/{room_id}/beds"))
        .bearer_auth(&token)
        .send()
        .await
        .unwrap();
    assert_eq!(beds.status(), StatusCode::OK);
    let beds: serde_json::Value = serde_json::from_str(&beds.text().await.unwrap()).unwrap();
    assert_eq!(beds["beds"][0]["monitor_key"], "monitor-118-1");

    let audit = client
        .get(format!(
            "http://{address}/api/v1/audit-log?entity_type=facility&entity_id={facility_id}"
        ))
        .bearer_auth(&token)
        .send()
        .await
        .unwrap();
    assert_eq!(audit.status(), StatusCode::OK);
    let audit: serde_json::Value = serde_json::from_str(&audit.text().await.unwrap()).unwrap();
    assert_eq!(audit["audit"].as_array().unwrap().len(), 1);

    task.abort();
}

#[tokio::test]
async fn residence_handlers_serve_planogram_privacy_and_beds_overview() {
    let state = test_state();
    state.seed_demo().unwrap();
    let config = HubConfig::new("127.0.0.1", 0).unwrap();
    let app = build_residence_router(config, state);
    let (address, task) = spawn_hub(app).await;
    let client = reqwest::Client::new();

    let login = client
        .post(format!("http://{address}/api/v1/auth/login"))
        .header("content-type", "application/json")
        .body(r#"{"username":"gaston","password":"gaston-demo"}"#)
        .send()
        .await
        .unwrap();
    let login: serde_json::Value = serde_json::from_str(&login.text().await.unwrap()).unwrap();
    let token = login["token"].as_str().unwrap().to_owned();

    let facility = client
        .post(format!("http://{address}/api/v1/facilities"))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(r#"{"name":"Manantial","timezone":"UTC"}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(facility.status(), StatusCode::CREATED);
    let facility: serde_json::Value =
        serde_json::from_str(&facility.text().await.unwrap()).unwrap();
    let facility_id = facility["id"].as_str().unwrap();

    let wing = client
        .post(format!(
            "http://{address}/api/v1/facilities/{facility_id}/wings"
        ))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(r#"{"name":"Ala Norte","floor":"1","sort_order":1}"#)
        .send()
        .await
        .unwrap();
    let wing: serde_json::Value = serde_json::from_str(&wing.text().await.unwrap()).unwrap();
    let wing_id = wing["id"].as_str().unwrap();

    let room = client
        .post(format!("http://{address}/api/v1/wings/{wing_id}/rooms"))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(r#"{"number":"118","type":"single","stream_key":"stream-118"}"#)
        .send()
        .await
        .unwrap();
    let room: serde_json::Value = serde_json::from_str(&room.text().await.unwrap()).unwrap();
    let room_id = room["id"].as_str().unwrap();

    let bed = client
        .post(format!("http://{address}/api/v1/rooms/{room_id}/beds"))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(r#"{"label":"Cama 1","monitor_key":"monitor-118-1"}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(bed.status(), StatusCode::CREATED);

    let wings = client
        .get(format!("http://{address}/api/v1/wings"))
        .bearer_auth(&token)
        .send()
        .await
        .unwrap();
    let wings: serde_json::Value = serde_json::from_str(&wings.text().await.unwrap()).unwrap();
    let wing = &wings["wings"][0];
    assert_eq!(wing["bed_count"], 1);

    let saved = client
        .put(format!("http://{address}/api/v1/wings/{wing_id}/planogram"))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(format!(
            r#"{{"placements":[{{"room_id":"{room_id}","x":0.5,"y":0.25,"sort_order":0}}]}}"#
        ))
        .send()
        .await
        .unwrap();
    assert_eq!(saved.status(), StatusCode::OK);
    let saved: serde_json::Value = serde_json::from_str(&saved.text().await.unwrap()).unwrap();
    assert_eq!(saved["placements"][0]["room_number"], "118");
    assert_eq!(saved["placements"][0]["x"], 0.5);

    let planogram = client
        .get(format!("http://{address}/api/v1/wings/{wing_id}/planogram"))
        .bearer_auth(&token)
        .send()
        .await
        .unwrap();
    assert_eq!(planogram.status(), StatusCode::OK);
    let planogram: serde_json::Value =
        serde_json::from_str(&planogram.text().await.unwrap()).unwrap();
    assert_eq!(planogram["placements"].as_array().unwrap().len(), 1);

    let duplicated = client
        .put(format!("http://{address}/api/v1/wings/{wing_id}/planogram"))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(format!(
            r#"{{"placements":[{{"room_id":"{room_id}","x":0.1,"y":0.1,"sort_order":0}},{{"room_id":"{room_id}","x":0.2,"y":0.2,"sort_order":1}}]}}"#
        ))
        .send()
        .await
        .unwrap();
    assert_eq!(duplicated.status(), StatusCode::CONFLICT);

    let privacy = client
        .put(format!(
            "http://{address}/api/v1/rooms/{room_id}/privacy-regions"
        ))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(r#"{"regions":[{"x":0.35,"y":0.2,"w":0.3,"h":0.6}]}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(privacy.status(), StatusCode::OK);
    let privacy: serde_json::Value = serde_json::from_str(&privacy.text().await.unwrap()).unwrap();
    assert_eq!(privacy["regions"][0]["w"], 0.3);

    let invalid = client
        .put(format!(
            "http://{address}/api/v1/rooms/{room_id}/privacy-regions"
        ))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(r#"{"regions":[{"x":0.9,"y":0.9,"w":0.5,"h":0.5}]}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(invalid.status(), StatusCode::UNPROCESSABLE_ENTITY);

    let beds = client
        .get(format!("http://{address}/api/v1/beds"))
        .bearer_auth(&token)
        .send()
        .await
        .unwrap();
    assert_eq!(beds.status(), StatusCode::OK);
    let beds: serde_json::Value = serde_json::from_str(&beds.text().await.unwrap()).unwrap();
    let bed = &beds["beds"][0];
    assert_eq!(bed["room_number"], "118");
    assert_eq!(bed["wing_name"], "Ala Norte");

    task.abort();
}

#[tokio::test]
async fn login_rate_limit_is_per_client_and_only_counts_bad_credentials() {
    let state = test_state();
    state.seed_demo().unwrap();
    let config = HubConfig::new("127.0.0.1", 0).unwrap();
    let mut handlers = identity_handlers(state.clone());
    handlers.extend(audit_handlers(state.clone()));
    handlers.extend(residence_handlers(state.clone()));
    handlers.extend(poblacion_handlers(state.clone()));
    handlers.extend(cobertura_handlers(state.clone()));
    handlers.extend(cuidado_handlers(state.clone()));
    handlers.extend(historia_handlers(state.clone()));
    handlers.extend(politica_handlers(state.clone()));
    handlers.extend(vigilancia_handlers(state.clone()));
    handlers.extend(observation_handlers(state));
    let app = build_router_with_handlers(config, handlers).unwrap();
    let (address, task) = spawn_hub(app).await;
    let client = reqwest::Client::new();
    for attempt in 0..5 {
        let response = client
            .post(format!("http://{address}/api/v1/auth/login"))
            .header("content-type", "application/json")
            .body(format!(
                r#"{{"username":"gaston","password":"wrong-{attempt}"}}"#
            ))
            .send()
            .await
            .unwrap();
        assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
    }
    let limited = client
        .post(format!("http://{address}/api/v1/auth/login"))
        .header("content-type", "application/json")
        .body(r#"{"username":"gaston","password":"wrong"}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(limited.status(), StatusCode::TOO_MANY_REQUESTS);
    task.abort();
}

#[tokio::test]
async fn poblacion_handlers_serve_residents_and_assignments_end_to_end() {
    let state = test_state();
    state.seed_demo().unwrap();
    let config = HubConfig::new("127.0.0.1", 0).unwrap();
    let app = build_poblacion_router(config, state);
    let (address, task) = spawn_hub(app).await;
    let client = reqwest::Client::new();

    let login = client
        .post(format!("http://{address}/api/v1/auth/login"))
        .header("content-type", "application/json")
        .body(r#"{"username":"gaston","password":"gaston-demo"}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(login.status(), StatusCode::OK);
    let login: serde_json::Value = serde_json::from_str(&login.text().await.unwrap()).unwrap();
    let token = login["token"].as_str().unwrap().to_owned();
    let actor_id = login["user"]["id"].as_str().unwrap().to_owned();

    let facility = client
        .post(format!("http://{address}/api/v1/facilities"))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(r#"{"name":"Manantial","timezone":"UTC"}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(facility.status(), StatusCode::CREATED);
    let facility: serde_json::Value =
        serde_json::from_str(&facility.text().await.unwrap()).unwrap();
    let facility_id = facility["id"].as_str().unwrap();

    let wing = client
        .post(format!(
            "http://{address}/api/v1/facilities/{facility_id}/wings"
        ))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(r#"{"name":"Ala Norte","floor":"1","sort_order":0}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(wing.status(), StatusCode::CREATED);
    let wing: serde_json::Value = serde_json::from_str(&wing.text().await.unwrap()).unwrap();
    let wing_id = wing["id"].as_str().unwrap();

    let room = client
        .post(format!("http://{address}/api/v1/wings/{wing_id}/rooms"))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(r#"{"number":"118","type":"single"}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(room.status(), StatusCode::CREATED);
    let room: serde_json::Value = serde_json::from_str(&room.text().await.unwrap()).unwrap();
    let room_id = room["id"].as_str().unwrap();

    let bed = client
        .post(format!("http://{address}/api/v1/rooms/{room_id}/beds"))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(r#"{"label":"Cama 1"}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(bed.status(), StatusCode::CREATED);
    let bed: serde_json::Value = serde_json::from_str(&bed.text().await.unwrap()).unwrap();
    let bed_id = bed["id"].as_str().unwrap();

    let bed2 = client
        .post(format!("http://{address}/api/v1/rooms/{room_id}/beds"))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(r#"{"label":"Cama 2"}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(bed2.status(), StatusCode::CREATED);
    let bed2: serde_json::Value = serde_json::from_str(&bed2.text().await.unwrap()).unwrap();
    let bed2_id = bed2["id"].as_str().unwrap();

    let created = client
        .post(format!("http://{address}/api/v1/residents"))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(
            r#"{"full_name":"Josefa Molina","external_id":"P-1001","birth_date":"1938-04-12","admission_date":"2025-01-15"}"#,
        )
        .send()
        .await
        .unwrap();
    assert_eq!(created.status(), StatusCode::CREATED);
    let created: serde_json::Value = serde_json::from_str(&created.text().await.unwrap()).unwrap();
    assert_eq!(created["resident"]["status"], "active");
    assert_eq!(created["resident"]["external_id"], "P-1001");
    let resident_id = created["resident"]["id"].as_str().unwrap();

    let invalid_date = client
        .post(format!("http://{address}/api/v1/residents"))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(r#"{"full_name":"Ana Perez","birth_date":"12-04-1938"}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(invalid_date.status(), StatusCode::UNPROCESSABLE_ENTITY);

    let missing_name = client
        .post(format!("http://{address}/api/v1/residents"))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(r#"{"full_name":""}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(missing_name.status(), StatusCode::UNPROCESSABLE_ENTITY);

    let detail = client
        .get(format!("http://{address}/api/v1/residents/{resident_id}"))
        .bearer_auth(&token)
        .send()
        .await
        .unwrap();
    assert_eq!(detail.status(), StatusCode::OK);
    let detail: serde_json::Value = serde_json::from_str(&detail.text().await.unwrap()).unwrap();
    assert_eq!(detail["resident"]["full_name"], "Josefa Molina");

    let missing_detail = client
        .get(format!("http://{address}/api/v1/residents/no-existe"))
        .bearer_auth(&token)
        .send()
        .await
        .unwrap();
    assert_eq!(missing_detail.status(), StatusCode::NOT_FOUND);

    let updated = client
        .patch(format!("http://{address}/api/v1/residents/{resident_id}"))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(r#"{"full_name":"Josefa Molina R."}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(updated.status(), StatusCode::OK);
    let updated: serde_json::Value = serde_json::from_str(&updated.text().await.unwrap()).unwrap();
    assert_eq!(updated["resident"]["full_name"], "Josefa Molina R.");

    let assignment = client
        .post(format!(
            "http://{address}/api/v1/residents/{resident_id}/assignments"
        ))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(format!(r#"{{"bed_id":"{bed_id}"}}"#))
        .send()
        .await
        .unwrap();
    assert_eq!(assignment.status(), StatusCode::CREATED);
    let assignment: serde_json::Value =
        serde_json::from_str(&assignment.text().await.unwrap()).unwrap();
    assert_eq!(assignment["assignment"]["bed_id"], bed_id);
    assert!(assignment["assignment"]["ends_at"].is_null());

    let padron = client
        .get(format!("http://{address}/api/v1/residents"))
        .bearer_auth(&token)
        .send()
        .await
        .unwrap();
    assert_eq!(padron.status(), StatusCode::OK);
    let padron: serde_json::Value = serde_json::from_str(&padron.text().await.unwrap()).unwrap();
    assert_eq!(padron["residents"].as_array().unwrap().len(), 1);
    assert_eq!(padron["residents"][0]["bed_id"], bed_id);
    assert_eq!(padron["residents"][0]["room"]["number"], "118");
    assert_eq!(padron["residents"][0]["room"]["wing_name"], "Ala Norte");

    let assignment2 = client
        .post(format!(
            "http://{address}/api/v1/residents/{resident_id}/assignments"
        ))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(format!(r#"{{"bed_id":"{bed2_id}"}}"#))
        .send()
        .await
        .unwrap();
    assert_eq!(assignment2.status(), StatusCode::CREATED);

    let assignments = client
        .get(format!(
            "http://{address}/api/v1/residents/{resident_id}/assignments"
        ))
        .bearer_auth(&token)
        .send()
        .await
        .unwrap();
    assert_eq!(assignments.status(), StatusCode::OK);
    let assignments: serde_json::Value =
        serde_json::from_str(&assignments.text().await.unwrap()).unwrap();
    assert_eq!(assignments["assignments"].as_array().unwrap().len(), 2);
    assert_eq!(assignments["assignments"][0]["bed_id"], bed_id);
    assert!(assignments["assignments"][0]["ends_at"].is_string());
    assert_eq!(assignments["assignments"][1]["bed_id"], bed2_id);
    assert!(assignments["assignments"][1]["ends_at"].is_null());

    let free_bed = client
        .delete(format!("http://{address}/api/v1/beds/{bed_id}/assignment"))
        .bearer_auth(&token)
        .send()
        .await
        .unwrap();
    assert_eq!(free_bed.status(), StatusCode::CONFLICT);

    let ghost_bed = client
        .post(format!(
            "http://{address}/api/v1/residents/{resident_id}/assignments"
        ))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(r#"{"bed_id":"cama-fantasma"}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(ghost_bed.status(), StatusCode::NOT_FOUND);

    let discharge = client
        .post(format!(
            "http://{address}/api/v1/residents/{resident_id}/discharge"
        ))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(r#"{"discharged_at":"2025-02-01"}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(discharge.status(), StatusCode::OK);
    let discharge: serde_json::Value =
        serde_json::from_str(&discharge.text().await.unwrap()).unwrap();
    assert_eq!(discharge["resident"]["status"], "discharged");
    assert_eq!(discharge["resident"]["discharged_at"], "2025-02-01");
    assert_eq!(discharge["resident"]["discharged_by"], actor_id);

    let closed_by_discharge = client
        .delete(format!("http://{address}/api/v1/beds/{bed2_id}/assignment"))
        .bearer_auth(&token)
        .send()
        .await
        .unwrap();
    assert_eq!(closed_by_discharge.status(), StatusCode::CONFLICT);

    let double_discharge = client
        .post(format!(
            "http://{address}/api/v1/residents/{resident_id}/discharge"
        ))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body("{}")
        .send()
        .await
        .unwrap();
    assert_eq!(double_discharge.status(), StatusCode::CONFLICT);

    let resident2 = client
        .post(format!("http://{address}/api/v1/residents"))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(r#"{"full_name":"Rosa Silva","admission_date":"2025-03-10"}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(resident2.status(), StatusCode::CREATED);
    let resident2: serde_json::Value =
        serde_json::from_str(&resident2.text().await.unwrap()).unwrap();
    let resident2_id = resident2["resident"]["id"].as_str().unwrap();

    let discharge_before_admission = client
        .post(format!(
            "http://{address}/api/v1/residents/{resident2_id}/discharge"
        ))
        .bearer_auth(&token)
        .header("content-type", "application/json")
        .body(r#"{"discharged_at":"2020-01-01"}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(
        discharge_before_admission.status(),
        StatusCode::UNPROCESSABLE_ENTITY
    );

    let audit = client
        .get(format!(
            "http://{address}/api/v1/audit-log?action=assignment.created"
        ))
        .bearer_auth(&token)
        .send()
        .await
        .unwrap();
    assert_eq!(audit.status(), StatusCode::OK);
    let audit: serde_json::Value = serde_json::from_str(&audit.text().await.unwrap()).unwrap();
    assert_eq!(audit["audit"].as_array().unwrap().len(), 2);

    task.abort();
}
