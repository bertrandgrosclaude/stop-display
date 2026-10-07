# Stop Display

Affichage des prochains départs d'une station de bus/tram basés sur les flux GTFS. Illustration avec la métropole de Montpellier.

# Stack 

Application web avec un frontend Vue 3/Vite et une API Java/Spring Boot 4 sans base de données interne, accès aux données via les flux GTFS 

## Prerequis

- Node.js 22+
- JDK 26+
- Maven Wrapper inclus dans `backend` (aucune installation Maven globale necessaire)

## Lancer le backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

L'API est disponible sur `http://localhost:8080`. Le endpoint `GET /api/health` renvoie l'etat du service.

La recherche et les favoris portent sur des stations, qui regroupent leurs arrets GTFS :

```text
GET /api/stations/search?q=comedie
GET /api/stations/{stationId}
GET /api/stations/{stationId}/departures
GET /api/stations/{stationId}/alerts
GET /api/gtfs/realtime/inspect?url=https%3A%2F%2Fgtfsproxy.e-tam.fr%2FCOMMON%2FAlert.pb
```

L'endpoint utilitaire `/api/gtfs/realtime/inspect` decode une ressource protobuf GTFS-Realtime et renvoie son en-tete ainsi que le contenu lisible de ses entites. Le parametre `limit` controle le nombre maximal d'entites retournees (100 au maximum). Pour eviter les requetes vers des hotes internes, seules les URL HTTPS en `.pb` dont l'hote figure dans `gtfs.realtime.inspect.allowed-hosts` sont acceptees; la reponse distante est limitee a 8 Mo.

La reponse des departs contient un bloc par arret enfant, avec quatre departs par defaut. Chaque bloc fournit la description GTFS de l'arret et `mainLineDirection`, calculee depuis la destination la plus frequente de la ligne active la plus desservie. Chaque depart fournit `time`, `realtime` (booleen) et `minutesRemaining`. Les mises a jour du flux GTFS-Realtime remplacent l'heure theorique quand elles sont disponibles; elles sont rafraichies toutes les 10 secondes et expirees apres 20 secondes. Si le flux est indisponible ou expire, l'API revient automatiquement aux horaires GTFS theoriques.

Les messages d'information proviennent de `Alert.pb` et sont filtres par arrets enfants et lignes desservies; les alertes sans cible explicite sont considerees globales. Le endpoint retourne les alertes actives de la station, rafraichies toutes les 10 secondes et mises en cache pendant 20 secondes.

La limite des departs et les caches temps reel sont configurables avec `gtfs.departures-limit`, `gtfs.trip-update.url`, `gtfs.trip-update.refresh-delay-ms`, `gtfs.trip-update.cache-ttl-ms`, `gtfs.alert.url`, `gtfs.alert.refresh-delay-ms` et `gtfs.alert.cache-ttl-ms` dans `backend/src/main/resources/application.properties`. L'interface affiche les minutes restantes jusqu'a 30 minutes, puis l'heure de depart, avec une mention distinguant le temps reel du theorique. Les messages d'information defilent a la suite sur la page des departs.

## Lancer le frontend

```powershell
cd frontend
npm run dev
```

Ouvrir `http://localhost:5173`.
