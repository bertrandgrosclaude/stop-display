<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

type Stop = { id: string; name: string; code: string; latitude: number; longitude: number; type: string; parentStation: string; description: string }
type Station = { id: string; name: string; latitude: number; longitude: number; stops: Stop[]; lines: { name: string; color: string; textColor: string }[] }

const props = defineProps<{ favorites: Station[] }>()
const emit = defineEmits<{
  select: [station: Station]
  removeFavorite: [station: Station]
}>()

const query = ref('')
const results = ref<Station[]>([])
const error = ref('')
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

onMounted(searchStops)
</script>

<template>
  <section class="home-page" aria-labelledby="home-title">
    <header class="page-intro">
      <p class="eyebrow"><span class="eyebrow-mark" aria-hidden="true"></span> Réseau TaM · Montpellier</p>
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
          <input v-model="query" type="search" placeholder="Nom d’une station" aria-label="Rechercher une station" />
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
</template>
