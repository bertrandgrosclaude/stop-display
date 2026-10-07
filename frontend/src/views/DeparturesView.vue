<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { ChevronLeft, ChevronRight, MapPin, Search } from '@lucide/vue'
import type { DisplayThemeId } from '../displayThemes'

type Departure = { line: string; destination: string; time: string; realtime: boolean; minutesRemaining: number | null }
type TransitAlert = { id: string; title: string; message: string; effect: string }
type Stop = { id: string; name: string; code: string; latitude: number; longitude: number; type: string; parentStation: string; description: string }
type Station = { id: string; name: string; latitude: number; longitude: number; stops: Stop[]; lines: { name: string; color: string; textColor: string }[] }
type Network = { id: string; name: string; acronym: string; website: string }
type StopDepartures = { stop: Stop; mainLineDirection: string; departures: Departure[] }
type CarouselSlide = { block: StopDepartures; key: string; clone: boolean }

const props = defineProps<{ station: Station | null; network: Network; favorite: boolean; apiStatus: string }>()
const emit = defineEmits<{
  openSearch: []
  selectNetwork: []
  toggleFavorite: [station: Station]
}>()

const departures = ref<StopDepartures[]>([])
const alerts = ref<TransitAlert[]>([])
const lastUpdatedAt = ref<Date | null>(null)
const error = ref('')
const selectedTheme = ref<DisplayThemeId>('lcd')
const stopCarousel = ref<HTMLDivElement | null>(null)
const currentStopIndex = ref(0)
let refreshTimer: ReturnType<typeof setInterval> | undefined
let carouselSettleTimer: ReturnType<typeof setTimeout> | undefined
const lastUpdatedLabel = computed(() => lastUpdatedAt.value
  ? new Intl.DateTimeFormat('fr-FR', { dateStyle: 'short', timeStyle: 'short' }).format(lastUpdatedAt.value)
  : 'En attente')
const carouselSlides = computed<CarouselSlide[]>(() => {
  if (departures.value.length < 2) {
    return departures.value.map((block) => ({ block, key: block.stop.id, clone: false }))
  }
  const first = departures.value[0]
  const last = departures.value[departures.value.length - 1]
  return [
    { block: last, key: `clone-last-${last.stop.id}`, clone: true },
    ...departures.value.map((block) => ({ block, key: block.stop.id, clone: false })),
    { block: first, key: `clone-first-${first.stop.id}`, clone: true },
  ]
})

function lineBadgeStyle(lineName: string) {
  const line = props.station?.lines.find((stationLine) => stationLine.name === lineName)
  return line
    ? { backgroundColor: line.color || '#3471ae', color: line.textColor || '#fff' }
    : undefined
}

function departureTimeLabel(departure: Departure) {
  return departure.minutesRemaining !== null && departure.minutesRemaining <= 30
    ? `${departure.minutesRemaining} min`
    : departure.time
}

function stopSubtitle(block: StopDepartures) {
  const description = block.stop.description?.trim()
  return description || (block.mainLineDirection ? `Direction : ${block.mainLineDirection}` : '')
}

function stopNameIsStation(block: StopDepartures) {
  return block.stop.name.trim().localeCompare(props.station?.name.trim() ?? '', 'fr', { sensitivity: 'base' }) === 0
}

function mapUrl(stop: Stop) {
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${stop.latitude},${stop.longitude}`)}`
}

function showStop(index: number) {
  const carousel = stopCarousel.value
  const count = departures.value.length
  if (!carousel || count === 0) return
  const targetIndex = (index + count) % count
  let slideIndex = count > 1 ? targetIndex + 1 : targetIndex
  if (count > 1 && currentStopIndex.value === count - 1 && index >= count) slideIndex = count + 1
  else if (count > 1 && currentStopIndex.value === 0 && index < 0) slideIndex = 0
  currentStopIndex.value = targetIndex
  carousel.scrollTo({ left: slideIndex * carousel.clientWidth, behavior: 'smooth' })
}

function syncCurrentStop() {
  const carousel = stopCarousel.value
  const count = departures.value.length
  if (!carousel || !carousel.clientWidth || count === 0) return
  const slideIndex = Math.round(carousel.scrollLeft / carousel.clientWidth)
  if (count < 2) {
    currentStopIndex.value = 0
    return
  }
  if (slideIndex === 0) currentStopIndex.value = count - 1
  else if (slideIndex === count + 1) currentStopIndex.value = 0
  else currentStopIndex.value = slideIndex - 1

  if (carouselSettleTimer) clearTimeout(carouselSettleTimer)
  carouselSettleTimer = setTimeout(() => {
    const settledSlideIndex = Math.round(carousel.scrollLeft / carousel.clientWidth)
    if (settledSlideIndex === 0) carousel.scrollLeft = count * carousel.clientWidth
    else if (settledSlideIndex === count + 1) carousel.scrollLeft = carousel.clientWidth
  }, 120)
}

async function loadDepartures() {
  const station = props.station
  if (!station) {
    departures.value = []
    error.value = ''
    return
  }
  try {
    const response = await fetch(`/api/stations/${encodeURIComponent(station.id)}/departures`)
    if (!response.ok) throw new Error()
    const loadedDepartures = await response.json() as StopDepartures[]
    if (props.station?.id !== station.id) return
    departures.value = loadedDepartures
    lastUpdatedAt.value = new Date()
    error.value = ''
    currentStopIndex.value = Math.min(currentStopIndex.value, Math.max(0, departures.value.length - 1))
    await nextTick()
    if (stopCarousel.value) {
      const firstRealSlide = departures.value.length > 1 ? currentStopIndex.value + 1 : 0
      stopCarousel.value.scrollLeft = firstRealSlide * stopCarousel.value.clientWidth
    }
  } catch {
    if (props.station?.id === station.id) error.value = 'Départs indisponibles pour cette station.'
  }
}

async function loadAlerts() {
  const station = props.station
  if (!station) {
    alerts.value = []
    return
  }
  try {
    const response = await fetch(`/api/stations/${encodeURIComponent(station.id)}/alerts`)
    if (!response.ok) throw new Error()
    const loadedAlerts = await response.json() as TransitAlert[]
    if (props.station?.id === station.id) alerts.value = loadedAlerts
  } catch {
    if (props.station?.id === station.id) alerts.value = []
  }
}

watch(() => props.station?.id, () => {
  void loadDepartures()
  void loadAlerts()
}, { immediate: true })

onMounted(() => {
  refreshTimer = setInterval(() => {
    void loadDepartures()
    void loadAlerts()
  }, 10000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  if (carouselSettleTimer) clearTimeout(carouselSettleTimer)
})
</script>

<template>
  <section class="departures-page" aria-labelledby="departures-title">
    <div class="departure-device">
      <div class="device-ridge">
        <img class="device-network-logo" :src="`/networks/${network.id}.png`" :alt="network.acronym" />
        <span class="device-network-name">{{ network.name }}</span>
        <button type="button" class="device-ridge-label" @click="emit('selectNetwork')">Changer de réseau</button>
        <span class="device-status"><span class="device-led"></span>{{ apiStatus === 'UP' ? 'En ligne' : 'Hors ligne' }}</span>
      </div>

      <div class="device-controls">
        <button type="button" class="back-button" @click="emit('openSearch')"><Search :size="19" aria-hidden="true" /> Stations</button>
        <div class="device-heading">
          <h1 id="departures-title">{{ station?.name ?? 'Choisissez une station' }}</h1>
          <p v-if="station">{{ station.stops.length }} arrêts · {{ station.lines.length }} lignes</p>
          <p v-else>Ouvrez la recherche pour afficher les prochains départs.</p>
        </div>
        <button v-if="station" type="button" class="favorite-button" :class="{ active: favorite }" @click="emit('toggleFavorite', station)">{{ favorite ? '★ Favori' : '☆ Ajouter' }}</button>
      </div>

      <div class="departure-display" :class="`theme-${selectedTheme}`">
        <header class="display-header"><span>Prochains départs</span><span>{{ station?.name ?? network.acronym }} · {{ station ? '4 passages par arrêt' : 'Réseau de transport' }}</span></header>
        <section v-if="alerts.length" class="service-alerts" aria-label="Messages d’information">
          <div class="alerts-heading"><span class="alerts-icon" aria-hidden="true">i</span><strong>Info réseau</strong></div>
          <div class="alerts-window">
            <div class="alerts-track">
              <div class="alerts-run" role="list">
                <span v-for="alert in alerts" :key="alert.id" class="alert-message" role="listitem">
                  <strong>{{ alert.title }}</strong><span v-if="alert.message">{{ alert.message }}</span>
                </span>
              </div>
              <div class="alerts-run" aria-hidden="true">
                <span v-for="alert in alerts" :key="`repeat-${alert.id}`" class="alert-message">
                  <strong>{{ alert.title }}</strong><span v-if="alert.message">{{ alert.message }}</span>
                </span>
              </div>
            </div>
          </div>
        </section>
        <p v-if="!station" class="display-empty">Choisissez une station pour commencer.</p>
        <p v-if="error" class="display-empty" role="alert">{{ error }}</p>
        <div v-else-if="station && departures.length" ref="stopCarousel" class="stop-carousel" aria-label="Arrêts desservis" @scroll.passive="syncCurrentStop">
          <section v-for="slide in carouselSlides" :key="slide.key" class="stop-carousel-slide" :aria-label="slide.clone ? undefined : slide.block.stop.name" :aria-hidden="slide.clone">
            <div class="stop-heading-row">
              <button v-if="departures.length > 1" type="button" class="stop-carousel-arrow" :tabindex="slide.clone ? -1 : 0" aria-label="Arrêt précédent" @click="showStop(currentStopIndex - 1)"><ChevronLeft :size="20" aria-hidden="true" /></button>
              <div class="stop-heading-copy">
                <h2 v-if="!stopNameIsStation(slide.block)">{{ slide.block.stop.name }}</h2>
                <p v-if="stopSubtitle(slide.block)" class="stop-direction">
                  <a class="stop-map-link" :href="mapUrl(slide.block.stop)" :tabindex="slide.clone ? -1 : 0" target="_blank" rel="noopener noreferrer" :aria-label="`Ouvrir la carte pour ${slide.block.stop.name}`">
                    <MapPin :size="16" :stroke-width="1.8" aria-hidden="true" />
                  </a>
                  <span>{{ stopSubtitle(slide.block) }}</span>
                </p>
              </div>
              <div class="stop-heading-tools">
                <span v-if="departures.length > 1" class="stop-carousel-position" aria-live="polite">{{ currentStopIndex + 1 }} / {{ departures.length }}</span>
              </div>
              <button v-if="departures.length > 1" type="button" class="stop-carousel-arrow" :tabindex="slide.clone ? -1 : 0" aria-label="Arrêt suivant" @click="showStop(currentStopIndex + 1)"><ChevronRight :size="20" aria-hidden="true" /></button>
            </div>
            <p v-if="!slide.block.departures.length" class="display-empty">Aucun départ à venir.</p>
            <div v-for="departure in slide.block.departures" v-else :key="`${slide.block.stop.id}-${departure.line}-${departure.time}`" class="departure-row">
              <span class="line-badge line-blue" :style="lineBadgeStyle(departure.line)">{{ departure.line }}</span>
              <span class="departure-destination">{{ departure.destination }}</span>
              <span class="departure-arrival"><strong class="departure-time">{{ departureTimeLabel(departure) }}</strong><small class="departure-status" :class="{ 'is-realtime': departure.realtime }">{{ departure.realtime ? 'Temps réel' : 'Théorique' }}</small></span>
            </div>
          </section>
        </div>
        <p v-else-if="station" class="display-empty">Aucun arrêt desservi.</p>
        <footer class="display-footer">
          <span>Dernière mise à jour</span>
          <time v-if="lastUpdatedAt" :datetime="lastUpdatedAt.toISOString()">{{ lastUpdatedLabel }}</time>
          <time v-else>En attente</time>
        </footer>
      </div>
      <footer class="device-footer"><span>ÉCRAN D’INFORMATION · {{ station?.name ?? network.name }}</span><span class="device-footer-mark">{{ network.acronym }}</span></footer>
    </div>
  </section>
</template>
