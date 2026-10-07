<script setup lang="ts">
import { onMounted, ref } from 'vue'
import DeparturesView from './views/DeparturesView.vue'
import HomeView from './views/HomeView.vue'

type Stop = { id: string; name: string; code: string; latitude: number; longitude: number; type: string; parentStation: string; description: string }
type Station = { id: string; name: string; latitude: number; longitude: number; stops: Stop[]; lines: { name: string; color: string; textColor: string }[] }
type ApiStatus = { status: string; service: string; timestamp: string }
type Page = 'home' | 'departures'

const apiStatus = ref<ApiStatus | null>(null)
const apiError = ref('')
const page = ref<Page>('home')
const selectedStation = ref<Station | null>(null)
const favorites = ref<Station[]>([])

function readFavoriteIds(): string[] {
  const value = document.cookie.split('; ').find((cookie) => cookie.startsWith('favoriteStations='))?.split('=')[1]
  if (!value) return []
  try { return JSON.parse(decodeURIComponent(value)) as string[] } catch { return [] }
}

function saveFavorites() {
  const ids = favorites.value.map((station) => station.id)
  document.cookie = `favoriteStations=${encodeURIComponent(JSON.stringify(ids))}; max-age=31536000; path=/; SameSite=Lax`
}

function toggleFavorite(station: Station) {
  favorites.value = favorites.value.some((favorite) => favorite.id === station.id)
    ? favorites.value.filter((favorite) => favorite.id !== station.id)
    : [...favorites.value, station].slice(-5)
  saveFavorites()
}

function selectStation(station: Station) {
  selectedStation.value = station
  page.value = 'departures'
}

async function loadFavorites() {
  const ids = readFavoriteIds()
  const loaded = await Promise.all(ids.map(async (id) => {
    try {
      const response = await fetch(`/api/stations/${encodeURIComponent(id)}`)
      return response.ok ? await response.json() as Station : null
    } catch { return null }
  }))
  favorites.value = loaded.filter((station): station is Station => station !== null)
}

onMounted(async () => {
  await loadFavorites()
  try {
    const response = await fetch('/api/health')
    if (!response.ok) throw new Error()
    apiStatus.value = await response.json() as ApiStatus
  } catch { apiError.value = 'Connexion au backend impossible' }
})
</script>

<template>
  <main class="app-shell">
    <nav class="topbar">
      <button type="button" class="brand" aria-label="Stop Display accueil" @click="page = 'home'">
        <span class="brand-mark" aria-hidden="true">SD</span>
        <span class="brand-copy"><strong>Stop Display</strong><small>Montpellier · TaM</small></span>
      </button>
      <span class="environment"><span class="status-dot"></span>{{ apiStatus?.status === 'UP' ? 'Service en ligne' : 'Données théoriques' }}</span>
    </nav>

    <HomeView v-if="page === 'home'" :favorites="favorites" @select="selectStation" @remove-favorite="toggleFavorite" />
    <DeparturesView v-else-if="selectedStation" :station="selectedStation" :favorite="favorites.some((favorite) => favorite.id === selectedStation?.id)" @back="page = 'home'" @toggle-favorite="toggleFavorite" />
    <p v-if="apiError" class="api-error">{{ apiError }}</p>
  </main>
</template>
