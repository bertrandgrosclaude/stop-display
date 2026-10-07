<script setup lang="ts">
type Network = { id: string; name: string; acronym: string; website: string }

defineProps<{
  networks: Network[]
  selectedNetworkId: string
  loading: boolean
  error: string
  canGoBack: boolean
}>()

const emit = defineEmits<{
  select: [network: Network]
  back: []
  retry: []
}>()
</script>

<template>
  <section class="network-page" aria-labelledby="network-title">
    <button v-if="canGoBack" type="button" class="network-back" @click="emit('back')">← Retour aux départs</button>
    <header class="network-intro">
      <h1 id="network-title">Choisissez votre réseau</h1>
      <p class="intro">Sélectionnez un réseau pour afficher les prochains départs.</p>
    </header>

    <div v-if="loading" class="network-status" role="status">Chargement des réseaux…</div>
    <div v-else-if="error" class="network-load-error">
      <p class="api-error" role="alert">{{ error }}</p>
      <button type="button" class="network-select" @click="emit('retry')">Réessayer</button>
    </div>
    <div v-else class="network-list">
      <article v-for="network in networks" :key="network.id" class="network-card" :class="{ selected: network.id === selectedNetworkId }">
        <img class="network-logo" :src="`/networks/${network.id}.png`" :alt="`Logo ${network.acronym}`" />
        <div class="network-details">
          <p class="eyebrow">{{ network.acronym }}</p>
          <h2>{{ network.name }}</h2>
          <a :href="network.website" target="_blank" rel="noopener noreferrer">{{ network.website }}</a>
        </div>
        <button type="button" class="network-select" @click="emit('select', network)">
          {{ network.id === selectedNetworkId ? 'Continuer' : 'Choisir' }}
        </button>
      </article>
      <p v-if="!networks.length" class="network-status">Aucun réseau disponible pour le moment.</p>
    </div>
  </section>
</template>
