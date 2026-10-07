<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ChevronLeft, ChevronRight, MapPin } from '@lucide/vue'
import type { DisplayThemeId } from '../displayThemes'

type Departure = { line: string; destination: string; time: string; realtime: boolean; minutesRemaining: number | null }
type TransitAlert = { id: string; title: string; message: string; effect: string }
type Stop = { id: string; name: string; code: string; latitude: number; longitude: number; type: string; parentStation: string; description: string }
type Station = { id: string; name: string; latitude: number; longitude: number; stops: Stop[]; lines: { name: string; color: string; textColor: string }[] }
type StopDepartures = { stop: Stop; mainLineDirection: string; departures: Departure[] }
type CarouselSlide = { block: StopDepartures; key: string; clone: boolean }

const props = defineProps<{ station: Station; favorite: boolean }>()
const emit = defineEmits<{
  back: []
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
  const line = props.station.lines.find((stationLine) => stationLine.name === lineName)
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
  return block.stop.description?.trim() || block.mainLineDirection
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
  try {
    const response = await fetch(`/api/stations/${encodeURIComponent(props.station.id)}/departures`)
    if (!response.ok) throw new Error()
    departures.value = await response.json() as StopDepartures[]
    lastUpdatedAt.value = new Date()
    error.value = ''
    currentStopIndex.value = Math.min(currentStopIndex.value, Math.max(0, departures.value.length - 1))
    await nextTick()
    if (stopCarousel.value) {
      const firstRealSlide = departures.value.length > 1 ? currentStopIndex.value + 1 : 0
      stopCarousel.value.scrollLeft = firstRealSlide * stopCarousel.value.clientWidth
    }
  } catch {
    error.value = 'Départs indisponibles pour cet arrêt.'
  }
}

async function loadAlerts() {
  try {
    const response = await fetch(`/api/stations/${encodeURIComponent(props.station.id)}/alerts`)
    if (!response.ok) throw new Error()
    alerts.value = await response.json() as TransitAlert[]
  } catch {
    alerts.value = []
  }
}

onMounted(() => {
  void loadDepartures()
  void loadAlerts()
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
      <div class="device-ridge" aria-hidden="true">
        <span class="device-brand">SD</span>
        <span class="device-ridge-label">INFORMATION VOYAGEURS · TaM</span>
        <span class="device-led"></span>
      </div>

      <div class="device-controls">
        <button type="button" class="back-button" @click="emit('back')"><ChevronLeft :size="19" aria-hidden="true" /> Recherche</button>
        <div class="device-heading">
          <h1 id="departures-title">{{ station.name }}</h1>
          <p>{{ station.stops.length }} arrêts · {{ station.lines.length }} lignes</p>
        </div>
        <button type="button" class="favorite-button" :class="{ active: favorite }" @click="emit('toggleFavorite', station)">{{ favorite ? '★ Favori' : '☆ Ajouter' }}</button>
      </div>

      <div class="departure-display" :class="`theme-${selectedTheme}`">
        <header class="display-header"><span>Prochains départs</span><span>{{ station.name }} · 4 passages par arrêt</span></header>
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
        <div v-if="departures.length && !error" class="stop-carousel-controls" role="group" aria-label="Navigation entre les arrêts">
           <button type="button" :disabled="departures.length < 2" aria-label="Arrêt précédent" @click="showStop(currentStopIndex - 1)"><ChevronLeft :size="20" aria-hidden="true" /></button>
          <span class="stop-carousel-position" aria-live="polite">{{ currentStopIndex + 1 }} / {{ departures.length }}</span>
           <button type="button" :disabled="departures.length < 2" aria-label="Arrêt suivant" @click="showStop(currentStopIndex + 1)"><ChevronRight :size="20" aria-hidden="true" /></button>
        </div>
        <p v-if="error" class="display-empty" role="alert">{{ error }}</p>
        <div v-else-if="departures.length" ref="stopCarousel" class="stop-carousel" aria-label="Arrêts desservis" @scroll.passive="syncCurrentStop">
          <section v-for="slide in carouselSlides" :key="slide.key" class="stop-carousel-slide" :aria-label="slide.clone ? undefined : slide.block.stop.name" :aria-hidden="slide.clone">
            <div class="stop-heading-row">
              <div class="stop-heading-copy">
                <h2>{{ slide.block.stop.name }}</h2>
                <p v-if="stopSubtitle(slide.block)" class="stop-direction">{{ stopSubtitle(slide.block) }}</p>
              </div>
              <a class="stop-map-link" :href="mapUrl(slide.block.stop)" :tabindex="slide.clone ? -1 : 0" target="_blank" rel="noopener noreferrer" :aria-label="`Ouvrir la carte pour ${slide.block.stop.name}`">
                <MapPin :size="19" :stroke-width="2" aria-hidden="true" />
              </a>
            </div>
            <p v-if="!slide.block.departures.length" class="display-empty">Aucun départ à venir.</p>
            <div v-for="departure in slide.block.departures" v-else :key="`${slide.block.stop.id}-${departure.line}-${departure.time}`" class="departure-row">
              <span class="line-badge line-blue" :style="lineBadgeStyle(departure.line)">{{ departure.line }}</span>
              <span class="departure-destination">{{ departure.destination }}</span>
              <span class="departure-arrival"><strong class="departure-time">{{ departureTimeLabel(departure) }}</strong><small class="departure-status" :class="{ 'is-realtime': departure.realtime }">{{ departure.realtime ? 'Temps réel' : 'Théorique' }}</small></span>
            </div>
          </section>
        </div>
        <p v-else class="display-empty">Aucun arrêt desservi.</p>
        <footer class="display-footer">
          <span>Dernière mise à jour</span>
          <time v-if="lastUpdatedAt" :datetime="lastUpdatedAt.toISOString()">{{ lastUpdatedLabel }}</time>
          <time v-else>En attente</time>
        </footer>
      </div>
      <footer class="device-footer"><span>ÉCRAN D’INFORMATION · {{ station.name }}</span><span class="device-footer-mark">TAM / 01</span></footer>
    </div>
  </section>
</template>
