# Guide d'exploitation


## Micro-service pour les données de marchés

### Déploiement via Docker

#### Présentation

Market_Service est un micro-service de données de marchés pour l'application BrokerX. L’application est packagée sous forme d’image Docker, et peut être déployée avec un docker-compose.yml qui lance : - Le micro-service Spring Boot.

#### Prérequis
	- Docker d'installer sur la machine. (Windows utilisé docker desktop)

#### Configuration
Certaines variables doivent être définies avant le lancement (via un fichier .env) :

| Variable | Description    | Exemple |
|----------|---------------------|----------|
|JWT_SECRET|Secret pour les tokens JWT|changeme-secret|
|GATEWAY_SECRET|Secret pour les requêtes venant du gateway|changeme-secret|
|SERVICE_SECRET|Secret pour les requêtes venant d'autre service interne|changeme-secret|

Ces variables sont injectées automatiquement dans le conteneur app par docker-compose.yml

##### Lancement de l'appplication localement
docker compose up --build -d
