<script setup lang="ts">
import { onMounted, ref } from 'vue'
import DeparturesView from './views/DeparturesView.vue'
import NetworkSelectionView from './views/NetworkSelectionView.vue'
import CreditsView from './views/CreditsView.vue'
import StationSearchView from './views/StationSearchView.vue'

type Stop = { id: string; name: string; code: string; latitude: number; longitude: number; type: string; parentStation: string; description: string }
type Station = { id: string; name: string; latitude: number; longitude: number; stops: Stop[]; lines: { name: string; color: string; textColor: string }[] }
type Network = { id: string; name: string; acronym: string; website: string }
type ApiStatus = { status: string; service: string; timestamp: string }
type Page = 'networks' | 'departures' | 'credits'

const apiStatus = ref<ApiStatus | null>(null)
const apiError = ref('')
const networkError = ref('')
const networks = ref<Network[]>([])
const page = ref<Page>('networks')
const returnPage = ref<Page>('networks')
const selectedNetwork = ref<Network | null>(null)
const selectedStation = ref<Station | null>(null)
const favorites = ref<Station[]>([])
const searchOpen = ref(false)
const networkLoading = ref(true)

function readCookie(name: string): string | null {
  const value = document.cookie.split('; ').find((cookie) => cookie.startsWith(`${name}=`))?.slice(name.length + 1)
  return value ? decodeURIComponent(value) : null
}

function writeCookie(name: string, value: string) {
  document.cookie = `${name}=${encodeURIComponent(value)}; max-age=31536000; path=/; SameSite=Lax`
}

function favoriteCookieName(network: Network) {
  return `favoriteStations-${network.id}`
}

function readFavoriteIds(network: Network): string[] {
  const stored = readCookie(favoriteCookieName(network)) ?? (network.id === 'tam' ? readCookie('favoriteStations') : null)
  if (!stored) return []
  try {
    const ids: unknown = JSON.parse(stored)
    return Array.isArray(ids) ? ids.filter((id): id is string => typeof id === 'string') : []
  } catch {
    return []
  }
}

function saveFavorites() {
  if (!selectedNetwork.value) return
  writeCookie(favoriteCookieName(selectedNetwork.value), JSON.stringify(favorites.value.map((station) => station.id)))
}

function toggleFavorite(station: Station) {
  favorites.value = favorites.value.some((favorite) => favorite.id === station.id)
    ? favorites.value.filter((favorite) => favorite.id !== station.id)
    : [...favorites.value, station].slice(-5)
  saveFavorites()
}

function selectStation(station: Station) {
  selectedStation.value = station
  searchOpen.value = false
}

async function loadFavorites(network: Network) {
  const ids = readFavoriteIds(network)
  const loaded = await Promise.all(ids.map(async (id) => {
    try {
      const response = await fetch(`/api/stations/${encodeURIComponent(id)}`)
      return response.ok ? await response.json() as Station : null
    } catch {
      return null
    }
  }))
  favorites.value = loaded.filter((station): station is Station => station !== null)
  if (network.id === 'tam' && !readCookie(favoriteCookieName(network)) && favorites.value.length) saveFavorites()
  selectedStation.value = favorites.value[0] ?? null
  searchOpen.value = selectedStation.value === null
}

async function chooseNetwork(network: Network) {
  selectedNetwork.value = network
  selectedStation.value = null
  favorites.value = []
  searchOpen.value = false
  writeCookie('selectedNetwork', network.id)
  page.value = 'departures'
  await loadFavorites(network)
}

function showNetworkSelection() {
  page.value = 'networks'
}

function closeSearch() {
  if (selectedStation.value) searchOpen.value = false
}

function openCredits() {
  if (page.value !== 'credits') returnPage.value = page.value
  window.history.pushState(null, '', '/credits')
  page.value = 'credits'
  searchOpen.value = false
}

function closeCredits() {
  window.history.pushState(null, '', '/')
  page.value = selectedNetwork.value
    ? returnPage.value === 'credits' || returnPage.value === 'networks' ? 'departures' : returnPage.value
    : 'networks'
}

function handlePopState() {
  if (window.location.pathname === '/credits') {
    if (page.value !== 'credits') returnPage.value = page.value
    page.value = 'credits'
  } else {
    page.value = selectedNetwork.value ? 'departures' : 'networks'
  }
}

async function loadNetworks() {
  networkLoading.value = true
  networkError.value = ''
  try {
    const response = await fetch('/api/networks')
    if (!response.ok) throw new Error('Network request failed')
    networks.value = await response.json() as Network[]
  } catch {
    networkError.value = 'Impossible de charger les réseaux. Vérifiez la connexion au serveur et réessayez.'
  } finally {
    networkLoading.value = false
  }
}

onMounted(async () => {
  window.addEventListener('popstate', handlePopState)
  const isCreditsPath = window.location.pathname === '/credits'
  if (isCreditsPath) page.value = 'credits'

  const healthRequest = fetch('/api/health')
    .then(async (response) => {
      if (!response.ok) throw new Error('Backend unavailable')
      apiStatus.value = await response.json() as ApiStatus
    })
    .catch(() => {
      apiError.value = 'Connexion au backend impossible'
    })

  await Promise.all([loadNetworks(), healthRequest])
  const savedNetworkId = readCookie('selectedNetwork')
  const savedNetwork = networks.value.find((network) => network.id === savedNetworkId)
  if (savedNetwork && isCreditsPath) {
    selectedNetwork.value = savedNetwork
    returnPage.value = 'departures'
    await loadFavorites(savedNetwork)
    page.value = 'credits'
  } else if (savedNetwork) {
    await chooseNetwork(savedNetwork)
  }
})
</script>

<template>
  <main class="app-shell" :class="{ 'is-display': page === 'departures', 'is-credits': page === 'credits' }">
    <nav v-if="page === 'networks'" class="topbar">
      <span class="brand">
        <span class="brand-copy"><strong>Stop Display</strong><small>Écran d’information voyageurs</small></span>
      </span>
      <span class="environment"><span class="status-dot"></span>{{ apiStatus?.status === 'UP' ? 'Service en ligne' : 'Données théoriques' }}</span>
    </nav>

    <NetworkSelectionView
      v-if="page === 'networks'"
      :networks="networks"
      :selected-network-id="selectedNetwork?.id ?? ''"
      :loading="networkLoading"
      :error="networkError"
      :can-go-back="Boolean(selectedNetwork)"
      @select="chooseNetwork"
      @back="page = 'departures'"
      @retry="loadNetworks"
    />
    <CreditsView v-else-if="page === 'credits'" @back="closeCredits" />
    <template v-else-if="selectedNetwork">
      <DeparturesView
        :station="selectedStation"
        :network="selectedNetwork"
        :favorite="Boolean(selectedStation && favorites.some((favorite) => favorite.id === selectedStation?.id))"
        :api-status="apiStatus?.status ?? ''"
        @open-search="searchOpen = true"
        @select-network="showNetworkSelection"
        @toggle-favorite="toggleFavorite"
      />
      <StationSearchView
        :open="searchOpen"
        :can-close="Boolean(selectedStation)"
        :favorites="favorites"
        :network="selectedNetwork"
        @select="selectStation"
        @remove-favorite="toggleFavorite"
        @close="closeSearch"
        @select-network="showNetworkSelection"
      />
    </template>
    <p v-if="apiError && page === 'departures'" class="api-error display-api-error" role="status">{{ apiError }}</p>
    <footer class="app-footer">
      <span>Stop Display</span>
      <a href="/credits" @click.prevent="openCredits">Crédits</a>
    </footer>
  </main>
</template>
