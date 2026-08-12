# Product

## Register

product

## Users

Utilisateurs techniques de type homelab : ils gèrent une bibliothèque Plex/Jellyfin,
sont à l'aise avec un NAS, des chemins Windows et une arborescence imposée, mais
découvrent Episort. Ils arrivent avec un dossier de téléchargements hétérogène
(noms de release, sous-dossiers incohérents, plusieurs séries mélangées) et veulent
en sortir une arborescence `Série/Season XX/Série - SXXEXX - Titre.ext` sans avoir à
renommer à la main ni à faire confiance aveuglément à l'outil.

Le contexte d'usage est une session courte et attentive, sur un écran de bureau,
avec l'explorateur Windows ouvert à côté. La tâche est irréversible côté disque :
l'utilisateur relit avant de valider.

## Product Purpose

Episort transforme un dossier brut en plan d'opérations fichier inspectable, puis
l'exécute. La chaîne est déterministe : scan → parsing par règles → résolution TMDB
→ revue d'identité → revue du plan exact → application journalisée.

Le succès n'est pas « l'app a rangé les fichiers » mais « l'utilisateur a compris
ce qui allait se passer avant que ça se passe, et a pu corriger en un clic ».
Deux validations explicites sont la colonne vertébrale du produit, pas une friction
à supprimer.

## Brand Personality

Précis, sûr, mécanique.

Ton factuel et vérifiable. L'interface énonce des faits — un chemin, un compte, un
statut — jamais une promesse. Pas d'emphase marketing, pas de félicitations, pas
d'anthropomorphisme. La confiance vient de la traçabilité : tout ce qui est affiché
provient d'un signal réel, et l'absence de donnée s'affiche `—` plutôt que d'être
comblée.

L'esthétique assumée est celle d'un poste de travail sombre : panneaux translucides
sur presque-noir, un unique accent orange et Instrument Sans pour toute
l'interface. Les chemins, codes et mesures se distinguent par le poids, la couleur
et les nombres tabulaires plutôt que par une seconde famille typographique.

## Anti-references

- **Consumer media app (Plex, Netflix, Jellyfin).** Episort ne présente pas un
  catalogue. Pas de grandes affiches, pas de carrousels, pas de tuiles de jaquettes,
  pas de fond flouté tiré d'un backdrop. Les visuels TMDB servent à *désambiguïser
  un match*, jamais à décorer. C'est un outil de traitement par lot, pas une
  vitrine de bibliothèque.
- Corollaires hérités des lois du design system : pas de dashboard SaaS générique
  (grilles de cartes identiques, gros chiffre décoratif), pas de second accent, pas
  de dialogue Windows natif, pas d'animation pulsante.

## Design Principles

1. **Rien n'est inventé.** Chaque valeur affichée trace vers un signal réel du
   view-model. Une donnée absente est `—`, jamais un texte plausible.
2. **Montrer avant d'agir.** L'utilisateur voit le plan exact source → destination
   avant toute écriture disque. Les deux portes de validation ne se contournent pas
   et ne se déguisent pas en étapes d'aperçu supplémentaires.
3. **Corriger un groupe coûte un clic.** L'unité de travail est le groupe détecté,
   pas le fichier. Corriger vingt-cinq fichiers doit coûter le même geste qu'un seul.
4. **Le refus passe par la désactivation, pas la disparition.** Une action
   indisponible reste visible et désactivée, avec la raison lisible à l'écran. On ne
   masque jamais un bouton pour empêcher une action.
5. **Un seul système visuel.** `docs/design-system.md` fait foi. Une nouvelle
   couleur, un nouvel espacement ou un nouveau composant se documentent dans le même
   changement, ou n'existent pas.

## Accessibility & Inclusion

Objectifs poursuivis au mieux, sans certification formelle :

- **Contraste WCAG AA** (4.5:1 texte courant, 3:1 grand texte) sur les fonds
  translucides réels, pas sur le fond nominal. Concerne en particulier
  `text.faint` et `text.muted`.
- **Navigation clavier complète** : indicateur de focus visible sur tout élément
  interactif, ordre de tabulation logique, `Échap` / `Entrée` cohérents dans les
  modales, aucun piège au clavier.
- **Mouvement réduit** : les transitions non essentielles se coupent quand le
  système signale une préférence de mouvement réduit. JavaFX n'a pas d'équivalent
  de `prefers-reduced-motion` ; le réglage se lit côté plateforme Windows.
- Interface bilingue FR/EN intégrale via `UiText`, sans chaîne codée en dur.
