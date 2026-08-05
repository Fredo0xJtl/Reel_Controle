# Application strictement locale : pas de règles réseau/analytics à préserver.

# WorkManager instancie les Worker par réflexion via le nom de classe stocké dans la base
# interne (WorkSpec) — sans cette règle, R8 peut renommer/supprimer la classe et le
# reverrouillage automatique planterait silencieusement au premier déclenchement après
# activation du minify (androidx.room et androidx.work fournissent déjà des règles consommateur
# pour leurs propres classes internes, mais pas pour NOS sous-classes de Worker).
-keep class com.focusreels.app.domain.RelockWorker { *; }

# Room utilise déjà des règles consommateur (consumer-rules.pro embarquées dans l'AAR) pour ses
# propres classes générées ; ligne de garde explicite conservée par prudence pour les entités,
# peu coûteuse et évite un crash silencieux si une future version de Room change ce contrat.
-keep class com.focusreels.app.data.db.** { *; }
