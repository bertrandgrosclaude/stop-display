# Stop Display

Affichage des prochains départs d'une station de bus/tram basés sur les flux GTFS. Illustration avec la métropole de Montpellier.

# Stack 

Application web avec un frontend Vue 3/Vite et une API Java/Spring Boot 4 sans base de données interne, accès aux données via les flux GTFS 

## Prerequis

- Node.js 22+
- JDK 26+
- Maven Wrapper inclus dans `backend` (aucune installation Maven globale necessaire)
- Pour Docker : Docker Desktop avec Docker Compose

## Lancer avec Docker (developpement)

Depuis la racine du projet, lancer les deux conteneurs :

```powershell
docker compose up --build
```

Ouvrir `http://localhost:5173`. Le frontend Vite utilise le rechargement a chaud et transmet les requetes `/api` au backend dans le reseau Docker. L'API est egalement accessible directement sur `http://localhost:8080`.

Arreter les conteneurs avec `Ctrl+C`, puis supprimer les conteneurs avec :

```powershell
docker compose down
```

## Lancer le backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

L'API est disponible sur `http://localhost:8080`. Le endpoint `GET /api/health` renvoie l'etat du service.

Le catalogue des reseaux est disponible via `GET /api/networks`. Chaque reseau expose son identifiant, son nom, son sigle et l'URL de son site web. Le frontend charge son logo depuis `frontend/public/networks/{id}.png`. TaM, la metropole de Montpellier, est le reseau configure actuellement.

La recherche et les favoris portent sur des stations, qui regroupent leurs arrets GTFS :

```text
GET /api/networks
GET /api/stations/search?q=comedie
GET /api/stations/{stationId}
GET /api/stations/{stationId}/departures
GET /api/stations/{stationId}/alerts
GET /api/gtfs/realtime/inspect?url=https%3A%2F%2Fgtfsproxy.e-tam.fr%2FCOMMON%2FAlert.pb
```

L'endpoint utilitaire `/api/gtfs/realtime/inspect` decode une ressource protobuf GTFS-Realtime et renvoie son en-tete ainsi que le contenu lisible de ses entites. Le parametre `limit` controle le nombre maximal d'entites retournees (100 au maximum). Pour eviter les requetes vers des hotes internes, seules les URL HTTPS en `.pb` dont l'hote figure dans `gtfs.realtime.inspect.allowed-hosts` sont acceptees; la reponse distante est limitee a 8 Mo.

La reponse des departs contient un bloc par arret enfant, avec quatre departs par defaut. Chaque bloc fournit la description GTFS de l'arret et `mainLineDirection`, calculee depuis la destination la plus frequente de la ligne active la plus desservie. Chaque depart fournit `time`, `realtime` (booleen) et `minutesRemaining`. Les mises a jour du flux GTFS-Realtime remplacent l'heure theorique quand elles sont disponibles; elles sont rafraichies toutes les 30 secondes et expirees apres 60 secondes. Si le flux est indisponible ou expire, l'API revient automatiquement aux horaires GTFS theoriques.

Les messages d'information proviennent de `Alert.pb` et sont filtres par arrets enfants et lignes desservies; les alertes sans cible explicite sont considerees globales. Le endpoint retourne les alertes actives de la station, rafraichies toutes les 30 secondes et mises en cache pendant 60 secondes.

La limite des departs et les caches temps reel sont configurables avec `gtfs.departures-limit`, `gtfs.trip-update.url`, `gtfs.trip-update.refresh-delay-ms`, `gtfs.trip-update.cache-ttl-ms`, `gtfs.alert.url`, `gtfs.alert.refresh-delay-ms` et `gtfs.alert.cache-ttl-ms` dans `backend/src/main/resources/application.properties`. Les flux temps reel sont interroges toutes les 30 secondes par defaut. En cas d'erreur HTTP 429, l'API respecte l'en-tete `Retry-After` lorsqu'il est present; sinon, elle applique un delai progressif jusqu'a 15 minutes. Le rafraichissement du GTFS statique est egalement retente apres echec.

Au premier acces, choisissez un reseau. Le choix est memorise dans le navigateur; au prochain acces, l'ecran des departs est affiche directement. Le premier favori disponible est selectionne automatiquement. Sans favori, la recherche s'ouvre en popup. L'ecran des departs occupe toute la fenetre; utilisez les commandes en haut de l'ecran pour rechercher une station ou changer de reseau. Le pied de page donne acces a la page statique des credits sur `/credits`. L'interface affiche les minutes restantes jusqu'a 30 minutes, puis l'heure de depart, avec une mention distinguant le temps reel du theorique. Les messages d'information defilent a la suite sur la page des departs.

## Lancer le frontend

```powershell
cd frontend
npm run dev
```

Ouvrir `http://localhost:5173`.
