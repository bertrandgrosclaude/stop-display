<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'

type Stop = { id: string; name: string; code: string; latitude: number; longitude: number; type: string; parentStation: string; description: string }
type Station = { id: string; name: string; latitude: number; longitude: number; stops: Stop[]; lines: { name: string; color: string; textColor: string }[] }
type Network = { id: string; name: string; acronym: string; website: string }

const props = defineProps<{ open: boolean; canClose: boolean; favorites: Station[]; network: Network }>()
const emit = defineEmits<{
  select: [station: Station]
  removeFavorite: [station: Station]
  close: []
  selectNetwork: []
}>()

const query = ref('')
const results = ref<Station[]>([])
const error = ref('')
const searchInput = ref<HTMLInputElement | null>(null)
let searchTimer: ReturnType<typeof setTimeout> | undefined

function lineBadgeStyle(line: Station['lines'][number]) {
  return { backgroundColor: line.color || '#d8e2dc', color: line.textColor || '#17352f' }
}

async function searchStops() {
  if (query.value.trim().length < 2) {
    results.value = []
    error.value = ''
    return
  }
  try {
    const response = await fetch(`/api/stations/search?q=${encodeURIComponent(query.value.trim())}`)
    if (!response.ok) throw new Error()
    results.value = await response.json() as Station[]
    error.value = ''
  } catch {
    error.value = 'Recherche indisponible pour le moment.'
  }
}

watch(query, () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(searchStops, 300)
})

watch(() => props.open, async (open) => {
  if (!open) return
  await nextTick()
  searchInput.value?.focus()
})

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.canClose) emit('close')
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
  if (props.open) void nextTick(() => searchInput.value?.focus())
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
  if (searchTimer) clearTimeout(searchTimer)
})
</script>

<template>
  <div v-if="open" class="search-modal-backdrop" @click.self="canClose && emit('close')">
    <section class="search-modal" role="dialog" aria-modal="true" aria-labelledby="home-title">
      <button v-if="canClose" type="button" class="modal-close" aria-label="Fermer la recherche" @click="emit('close')">×</button>
      <div class="search-modal-header">
        <img class="device-network-logo" :src="`/networks/${network.id}.png`" :alt="network.acronym" />
        <span class="search-network-name">{{ network.name }}</span>
        <button type="button" class="search-network-label" @click="emit('selectNetwork')">Changer de réseau</button>
      </div>
      <header class="page-intro">
        <p class="eyebrow"><span class="eyebrow-mark" aria-hidden="true"></span> Réseau {{ network.acronym }}</p>
        <h1 id="home-title">Choisissez une station</h1>
        <p class="intro">Recherchez son nom ou retrouvez-la dans vos favoris.</p>
      </header>

      <div class="home-layout">
        <section class="search-section" aria-labelledby="search-title">
          <div class="section-heading">
            <div><p class="eyebrow">Explorer le réseau</p><h2 id="search-title">Rechercher</h2></div>
          </div>
          <label class="search-box">
            <span class="search-icon" aria-hidden="true">⌕</span>
            <input ref="searchInput" v-model="query" type="search" placeholder="Nom d’une station" aria-label="Rechercher une station" />
          </label>

          <div v-if="results.length" class="search-results" aria-live="polite">
            <p class="results-count">{{ results.length }} résultat{{ results.length > 1 ? 's' : '' }}</p>
            <button v-for="station in results" :key="station.id" type="button" class="result-row" @click="emit('select', station)">
              <span class="result-copy">
                <strong>{{ station.name }}</strong>
                <span v-if="station.lines.length" class="result-lines">
                  <span v-for="line in station.lines" :key="`${station.id}-${line.name}`" class="result-line" :style="lineBadgeStyle(line)">{{ line.name }}</span>
                </span>
                <small v-else>Station · {{ station.stops.length }} arrêts</small>
              </span>
              <span class="result-arrow" aria-hidden="true">→</span>
            </button>
          </div>
          <p v-else-if="query.length > 1 && !error" class="empty-message">Aucune station trouvée.</p>
          <p v-if="error" class="api-error" role="alert">{{ error }}</p>
        </section>

        <section class="favorites-section" aria-labelledby="favorites-title">
          <div class="section-heading">
            <div><p class="eyebrow">Accès rapide</p><h2 id="favorites-title">Favoris</h2></div>
            <span class="favorite-count">{{ favorites.length }}<span>/5</span></span>
          </div>
          <p v-if="!favorites.length" class="empty-message">Vos stations enregistrées apparaîtront ici.</p>
          <div v-else class="favorite-list">
            <div v-for="station in favorites" :key="station.id" class="favorite-row">
              <button type="button" class="favorite-link" @click="emit('select', station)">
                <strong>{{ station.name }}</strong>
                <span v-if="station.lines.length" class="result-lines">
                  <span v-for="line in station.lines" :key="`${station.id}-${line.name}`" class="result-line" :style="lineBadgeStyle(line)">{{ line.name }}</span>
                </span>
                <small v-else>Station · {{ station.stops.length }} arrêts</small>
              </button>
              <button type="button" class="remove-favorite" :aria-label="`Supprimer ${station.name} des favoris`" @click="emit('removeFavorite', station)">×</button>
            </div>
          </div>
        </section>
      </div>
    </section>
  </div>
</template>
