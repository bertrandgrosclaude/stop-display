<script setup lang="ts">
const emit = defineEmits<{
  back: []
}>()
</script>

<template>
  <section class="credits-page" aria-labelledby="credits-title">
    <button type="button" class="network-back" @click="emit('back')">← Retour à l’application</button>
    <header class="network-intro">
      <p class="eyebrow"><span class="eyebrow-mark" aria-hidden="true"></span> Stop Display</p>
      <h1 id="credits-title">Crédits</h1>
      <p class="intro">Sources et services utilisés pour afficher les prochains départs.</p>
    </header>

    <dl class="credits-list">
      <div>
        <dt>Données de transport</dt>
        <dd>Réseau TaM, Métropole de Montpellier — horaires GTFS, temps réel et informations voyageurs.</dd>
        <dd><a href="https://www.tam-voyages.com/" target="_blank" rel="noopener noreferrer">tam-voyages.com</a></dd>
      </div>
      <div>
        <dt>Cartographie</dt>
        <dd>Les liens de localisation des arrêts ouvrent Google Maps.</dd>
        <dd><a href="https://maps.google.com/" target="_blank" rel="noopener noreferrer">Google Maps</a></dd>
      </div>
    </dl>
  </section>
</template>
