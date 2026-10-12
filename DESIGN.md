# DESIGN.md — design de Tsuyu

## Pour qui, dans quel moment
On ouvre Tsuyu rarement. La première fois, pour l'activer. Ensuite, seulement quand une app demande l'accès ou quand quelque chose ne marche plus. On le fait depuis un canapé, à environ 3 m de l'écran, avec une télécommande à 5 touches, souvent sans savoir ce qu'est ADB.

Une réussite se mesure ainsi : l'utilisateur active Tsuyu une fois, en comprenant ce qu'il autorise, puis l'oublie. Quand une app demande l'accès, il sait qui demande et ce que ça permet. Quand ça casse, l'écran lui dit quoi faire.

## Principes
- **Une seule chose à faire par écran :** l'action principale porte le focus initial.
- **État lisible à distance :** Tsuyu est actif, arrêté, ou à configurer. On le comprend en un coup d'œil, sans lire de détail technique.
- **Honnêteté :**
  - dire ce qui va être modifié sur la TV avant de le faire (débogage, accès accordés à Tsuyu), et le montrer ensuite ;
  - les accès sont nommés par leur effet, pas par leur nom technique.
- **Jamais d'impasse :** chaque erreur dit la cause probable et propose l'action suivante, réessayer ou ouvrir le bon réglage. Retour ramène toujours à l'écran précédent.
- **Rien au repos :** pas d'animation ni de rafraîchissement quand rien ne change.

## Écrans
- **Accueil :** l'état du service et son action principale (activer, démarrer, réessayer), les apps autorisées, l'aide.
- **Activation guidée :**
  - activer les options pour les développeurs et le débogage ;
  - confirmer « Toujours autoriser » sur l'écran système ;
  - chaque étape montre où l'on en est et détecte toute seule quand elle est faite.
- **Demande d'autorisation :** s'ouvre par-dessus l'app qui demande. Elle affiche l'icône et le nom de l'app, ce que l'accès permet, puis « Autoriser » et « Refuser ». « Refuser » porte le focus initial.
- **Apps autorisées :** la liste des apps autorisées, avec la révocation de chacune.

Le périmètre exact de chaque écran se décide dans les changes OpenSpec.

## Navigation à la télécommande
- Chaque écran a un focus initial défini. Au retour d'un sous-écran, le focus revient sur l'élément qui l'a ouvert.
- Pas de boucle aux bords, pas d'appui long caché. Une action secondaire est visible à l'écran.
- L'élément focalisé se distingue nettement : changement d'échelle léger, contraste, ombre. Jamais sa taille dans la mise en page.
- Retour quitte un sous-écran. Sur l'accueil, il quitte l'app.

## Langage visuel
- Même famille que SygixOs : thème sombre uniquement, surfaces sobres, animations de 250 à 400 ms sans saut sec, respect de l'échelle d'animation du système.
- Couleur d'accent unique, froide, évoquant la rosée. Elle sert au focus et à l'état actif, et n'est jamais la seule porteuse d'une information.
- Texte lisible à 3 m : aucun texte courant sous 16 sp, titres nettement plus grands, au plus deux niveaux d'information par écran.
- Marges de sécurité TV respectées : aucun contenu interactif dans les bords coupés par l'overscan.
- Composants : Compose for TV. Les composants communs (surface, bouton, ligne de liste avec état, étape numérotée) se définissent une fois et se réutilisent.

## Textes
- Français, même ton que SygixOs, phrases courtes, verbe d'action sur les boutons.
- Termes techniques (ADB, shell, binder) seulement dans « Détails ».
