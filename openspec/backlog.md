# Suivi : sujets non tranchés

Aucun de ces sujets n'est spécifié ni tranché. Chacun deviendra un change OpenSpec (`openspec/changes/<id>/`) ou sera rattaché à un change existant au moment où on le traite. Les pistes sont des hypothèses de travail, pas des décisions.

## 1. TV dont adbd n'écoute pas en TCP

**Constat** : sur la TV de référence, le firmware fixe le port TCP d'adbd à chaque démarrage. Sur une TV qui ne le fait pas, il ne reste que le débogage sans fil en TLS, dont l'appairage est impossible depuis la TV elle-même.

**Pistes** :
- appairage unique depuis un ordinateur ou un téléphone, avec une clé que Tsuyu peut ensuite utiliser ;
- service d'accessibilité qui lit le code et le port sur l'écran d'appairage, sans prendre le focus.

**À trancher** : la prise en charge de ces TV, la phase, et la piste retenue.

## 2. Activation du débogage USB

**Constat** : le débogage USB survit au redémarrage, contrairement au débogage sans fil. Activé, il laisse le port TCP d'adbd ouvert sur le réseau local en permanence, et seules les clés autorisées y accèdent.

**À trancher** : ce que Tsuyu demande d'activer, ce qu'il explique à l'utilisateur sur cette exposition, et s'il propose de la refermer quand le serveur tourne déjà.

## 3. Version d'API annoncée aux clients

**Constat** : Shizuku-API est en version 13.1.5 ; Shevery annonce 14.3.

**À trancher** : la version annoncée par Tsuyu et les méthodes de l'AIDL à implémenter réellement (UserService, remote process, etc.), après vérification des usages de SygixOs et de l'app de démo.
